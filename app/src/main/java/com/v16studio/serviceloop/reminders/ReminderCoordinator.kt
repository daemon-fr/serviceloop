package com.v16studio.serviceloop.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.v16studio.serviceloop.MainActivity
import com.v16studio.serviceloop.ServiceLoopApplication
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import com.v16studio.serviceloop.data.WorkingVisitEntity
import com.v16studio.serviceloop.data.toDomain
import com.v16studio.serviceloop.domain.BusinessTime
import com.v16studio.serviceloop.domain.DailySummaryCounts
import com.v16studio.serviceloop.domain.DailySummaryText
import com.v16studio.serviceloop.domain.ReminderPreferences
import com.v16studio.serviceloop.domain.ReminderRuntimeState
import com.v16studio.serviceloop.domain.ReminderScheduleRules
import com.v16studio.serviceloop.domain.AppointmentReminderRules
import com.v16studio.serviceloop.domain.DailySummaryGate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReminderCoordinator(
    private val context: Context,
    private val database: ServiceLoopDatabase,
    private val businessTime: BusinessTime,
    private val scope: CoroutineScope,
) {
    private val device = context.getSharedPreferences(DEVICE_PREFS, Context.MODE_PRIVATE)
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    fun start() {
        scope.launch {
            database.invalidationTracker.createFlow(
                "working_visits", "service_plans", "service_obligations", "follow_ups",
                "recovery_metadata", "reminder_preferences", "business_profiles",
                "dispatch_visit_bindings", emitInitialState = true,
            ).collect { reconcileSafely() }
        }
    }

    fun deliveryRequested(): Boolean = device.getBoolean(KEY_REQUESTED, false)

    fun runtimeState(): ReminderRuntimeState {
        val permission = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val appEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        fun channelEnabled(id: String) = Build.VERSION.SDK_INT < 26 || notificationManager.getNotificationChannel(id)?.importance?.let { it != NotificationManager.IMPORTANCE_NONE } ?: true
        return ReminderRuntimeState(deliveryRequested(), permission && appEnabled, channelEnabled(CHANNEL_SUMMARIES), channelEnabled(CHANNEL_APPOINTMENTS), device.getString(KEY_SCHEDULING_ERROR, null))
    }

    fun setDeliveryRequested(value: Boolean) {
        device.edit().putBoolean(KEY_REQUESTED, value).apply()
        if (!value) cancelAllAndWithdraw()
        reconcileAsync()
    }

    /** Restore/erase starts a fresh device binding and invalidates old notification ownership. */
    fun resetForDatasetReplacement() {
        device.edit().putBoolean(KEY_REQUESTED, false).remove(KEY_LAST_SUMMARY).apply()
        cancelAllAndWithdraw()
    }

    fun reconcileAsync() { scope.launch { reconcileSafely() } }

    private suspend fun reconcileSafely() {
        try { reconcile(); device.edit().remove(KEY_SCHEDULING_ERROR).apply() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { device.edit().putString(KEY_SCHEDULING_ERROR, failure.message ?: "Android scheduling failed").apply() }
    }

    suspend fun reconcile() = withContext(Dispatchers.IO) {
        if (!deliveryRequested()) { cancelAllAndWithdraw(); return@withContext }
        ensureChannels()
        val state = runtimeState()
        val dao = database.serviceLoopDao()
        val preferences = (dao.reminderPreferences() ?: return@withContext).toDomain()
        val metadata = dao.recoveryMetadata() ?: return@withContext
        if (!state.permissionGranted) { cancelScheduled(dao.reminderVisits(), metadata.datasetId); return@withContext }
        if (state.summariesChannelEnabled) scheduleSummary(preferences, metadata.datasetId) else cancelSummary(metadata.datasetId)
        val visits = dao.reminderVisits()
        visits.forEach { visit ->
            val eligible = state.appointmentsChannelEnabled && AppointmentReminderRules.eligible(visit.state, visit.scheduledAtEpochMillis, businessTime.instant().toEpochMilli(), visit.appointmentReminderLeadMinutes, preferences)
            if (eligible) scheduleAppointment(visit, metadata.datasetId, effectiveLead(visit, preferences)) else cancelAppointment(visit.id, metadata.datasetId)
        }
    }

    suspend fun executeSummary(expectedDataset: String, expectedDate: String) = withContext(Dispatchers.IO) {
        try {
            val dao = database.serviceLoopDao(); val metadata = dao.recoveryMetadata() ?: return@withContext
            if (metadata.datasetId != expectedDataset || !deliveryRequested() || !runtimeState().permissionGranted) return@withContext
            val preferences = (dao.reminderPreferences() ?: return@withContext).toDomain()
            val today = businessTime.today()
            if (today.toString() != expectedDate || !preferences.dailySummaryEnabled || !preferences.includes(today.dayOfWeek)) return@withContext
            val rows = dao.dueServices(); val horizon = today.plusDays(preferences.dueSoonHorizonDays.toLong())
            val visits = dao.reminderVisits()
            val counts = DailySummaryCounts(
                dueServices = rows.count { !LocalDate.parse(it.dueDate).isAfter(horizon) },
                visits = visits.count { it.state == "BOOKED" && !LocalDate.parse(it.actualServiceDate).isAfter(today.plusDays(1)) },
                followUps = dao.allFollowUps().count { it.state == "OPEN" && !LocalDate.parse(it.dueDate).isAfter(today) },
                unfinishedVisits = visits.count { it.state == "WORKING" },
                backupDue = backupDue(metadata.lastBusinessWriteAtEpochMillis, metadata.lastVerifiedSnapshotAtEpochMillis, metadata.lastVerifiedFullBackupAtEpochMillis, metadata.backupReminderDays),
            )
            val text = DailySummaryText.build(preferences, counts) ?: return@withContext
            val gate = DailySummaryGate({ device.getString(KEY_LAST_SUMMARY, null) }) { device.edit().putString(KEY_LAST_SUMMARY, it).commit() }
            if (!gate.claim(expectedDataset, today)) return@withContext
            post(NOTIFICATION_SUMMARY, CHANNEL_SUMMARIES, "ServiceLoop", text, homeIntent(expectedDataset))
        } finally { reconcileSafely() }
    }

    suspend fun executeAppointment(expectedDataset: String, visitId: String) = withContext(Dispatchers.IO) {
        val dao = database.serviceLoopDao(); val metadata = dao.recoveryMetadata() ?: return@withContext
        if (metadata.datasetId != expectedDataset || !deliveryRequested() || !runtimeState().permissionGranted) return@withContext
        val visit = dao.visit(visitId) ?: return@withContext
        val start = visit.scheduledAtEpochMillis ?: return@withContext
        if (visit.state != "BOOKED" || start <= businessTime.instant().toEpochMilli()) return@withContext
        val zone = runCatching { ZoneId.of(visit.appointmentZoneId) }.getOrDefault(businessTime.zoneId)
        val time = Instant.ofEpochMilli(start).atZone(zone).toLocalTime().truncatedTo(ChronoUnit.MINUTES)
        post(appointmentNotificationId(visit.id), CHANNEL_APPOINTMENTS, "ServiceLoop appointment", "Visit scheduled for $time", visitIntent(expectedDataset, visit.id))
    }

    fun sendTestNotification(): Boolean {
        if (!deliveryRequested() || !runtimeState().permissionGranted || !runtimeState().summariesChannelEnabled) return false
        ensureChannels()
        post(NOTIFICATION_TEST, CHANNEL_SUMMARIES, "ServiceLoop test", "Test notification requested from this device. Delivery may be delayed or blocked by Android.", homeIntent(null))
        return true
    }

    fun openAndroidSettingsIntent(): Intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)

    private fun scheduleSummary(p: ReminderPreferences, datasetId: String) {
        cancelSummary(datasetId)
        if (!p.dailySummaryEnabled) return
        val target = ReminderScheduleRules.nextSummaryAfter(businessTime.instant(), businessTime.zoneId, p) ?: return
        val date = target.toLocalDate()
        device.edit().putString(KEY_SCHEDULED_SUMMARY_DATE, date.toString()).apply()
        setWindow(target.toInstant().toEpochMilli(), receiverIntent(TYPE_SUMMARY, datasetId, date.toString()), summaryRequestCode(datasetId))
    }

    private fun scheduleAppointment(visit: WorkingVisitEntity, datasetId: String, lead: Int) {
        val start = visit.scheduledAtEpochMillis ?: return
        val trigger = AppointmentReminderRules.triggerAt(start, lead)
        cancelAppointment(visit.id, datasetId)
        if (trigger > businessTime.instant().toEpochMilli()) setWindow(trigger, receiverIntent(TYPE_APPOINTMENT, datasetId, visit.id), appointmentRequestCode(visit.id))
    }

    private fun setWindow(trigger: Long, intent: Intent, requestCode: Int) {
        val pending = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.setWindow(AlarmManager.RTC_WAKEUP, trigger, WINDOW_MILLIS, pending)
    }

    private fun cancelScheduled(visits: List<WorkingVisitEntity>, datasetId: String) { cancelSummary(datasetId); visits.forEach { cancelAppointment(it.id, datasetId) } }
    private fun cancelSummary(datasetId: String) {
        val date = device.getString(KEY_SCHEDULED_SUMMARY_DATE, null) ?: return
        cancel(receiverIntent(TYPE_SUMMARY, datasetId, date), summaryRequestCode(datasetId))
        device.edit().remove(KEY_SCHEDULED_SUMMARY_DATE).apply()
    }
    private fun cancelAppointment(visitId: String, datasetId: String) = cancel(receiverIntent(TYPE_APPOINTMENT, datasetId, visitId), appointmentRequestCode(visitId))
    private fun cancel(intent: Intent, requestCode: Int) { PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { alarmManager.cancel(it); it.cancel() } }

    private fun cancelAllAndWithdraw() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_SUMMARY); NotificationManagerCompat.from(context).cancel(NOTIFICATION_TEST)
        scope.launch(Dispatchers.IO) { database.serviceLoopDao().recoveryMetadata()?.let { metadata -> val visits=database.serviceLoopDao().reminderVisits(); cancelScheduled(visits, metadata.datasetId); visits.forEach { NotificationManagerCompat.from(context).cancel(appointmentNotificationId(it.id)) } } }
    }

    private fun receiverIntent(type: String, datasetId: String, item: String) = Intent(context, ReminderReceiver::class.java).apply {
        data = Uri.Builder().scheme("serviceloop").authority("reminder").appendPath(datasetId).appendPath(type).appendPath(item).build()
    }
    private fun homeIntent(datasetId: String?) = contentIntent(datasetId, "home", 70)
    private fun visitIntent(datasetId: String, visitId: String) = contentIntent(datasetId, "visit/$visitId", appointmentRequestCode(visitId))
    private fun contentIntent(datasetId: String?, route: String, requestCode: Int) = PendingIntent.getActivity(context, requestCode, Intent(context, MainActivity::class.java).putExtra(EXTRA_DATASET, datasetId).putExtra(EXTRA_ROUTE, route).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun post(id: Int, channel: String, title: String, text: String, content: PendingIntent) {
        if (!runtimeState().permissionGranted) return
        val notification = NotificationCompat.Builder(context, channel).setSmallIcon(com.v16studio.serviceloop.R.mipmap.ic_launcher).setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(content).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT).build()
        try { NotificationManagerCompat.from(context).notify(id, notification) } catch (_: SecurityException) { }
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        notificationManager.createNotificationChannels(listOf(
            NotificationChannel(CHANNEL_SUMMARIES, "Work summaries", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Approximate daily ServiceLoop work summaries" },
            NotificationChannel(CHANNEL_APPOINTMENTS, "Appointment reminders", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Approximate ServiceLoop appointment reminders" },
        ))
    }

    private fun effectiveLead(visit: WorkingVisitEntity, p: ReminderPreferences) = AppointmentReminderRules.effectiveLead(visit.appointmentReminderLeadMinutes, p)
    private fun backupDue(lastWrite: Long?, lastSnapshot: Long?, lastBackup: Long?, days: Int): Boolean {
        if (days == 0 || lastWrite == null || lastSnapshot != null && lastSnapshot >= lastWrite) return false
        val anchor = lastBackup ?: lastWrite
        return businessTime.instant().toEpochMilli() - anchor >= days * 86_400_000L
    }
    private fun summaryRequestCode(dataset: String) = 10_000 + (dataset.hashCode() and 0x3fff)
    private fun appointmentRequestCode(id: String) = 30_000 + (id.hashCode() and 0x3fffffff) % 900_000
    private fun appointmentNotificationId(id: String) = 100_000 + (id.hashCode() and 0x3fffffff) % 900_000

    companion object {
        const val CHANNEL_SUMMARIES = "serviceloop_work_summaries"
        const val CHANNEL_APPOINTMENTS = "serviceloop_appointment_reminders"
        const val EXTRA_ROUTE = "serviceloop.reminder.ROUTE"
        const val EXTRA_DATASET = "serviceloop.reminder.DATASET"
        private const val DEVICE_PREFS = "serviceloop_reminder_device"
        private const val KEY_REQUESTED = "delivery_requested"
        private const val KEY_LAST_SUMMARY = "last_summary_identity"
        private const val KEY_SCHEDULED_SUMMARY_DATE = "scheduled_summary_date"
        private const val KEY_SCHEDULING_ERROR = "scheduling_error"
        private const val TYPE_SUMMARY = "summary"
        private const val TYPE_APPOINTMENT = "appointment"
        private const val WINDOW_MILLIS = 15 * 60_000L
        private const val NOTIFICATION_SUMMARY = 2001
        private const val NOTIFICATION_TEST = 2002
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val segments = intent.data?.pathSegments ?: return
        if (segments.size != 3) return
        val pending = goAsync()
        val app = context.applicationContext as ServiceLoopApplication
        app.container.applicationScope.launch {
            try {
                when (segments[1]) {
                    "summary" -> app.container.reminderCoordinator.executeSummary(segments[0], segments[2])
                    "appointment" -> app.container.reminderCoordinator.executeAppointment(segments[0], segments[2])
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { }
            finally { pending.finish() }
        }
    }
}

class ReminderReconcileReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as ServiceLoopApplication
        app.container.businessDateSignal.invalidate()
        app.container.reminderCoordinator.reconcileAsync()
    }
}
