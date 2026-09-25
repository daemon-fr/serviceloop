package com.v16studio.v16service

import android.app.Application
import com.v16studio.v16service.data.RoomV16ServiceRepository
import com.v16studio.v16service.data.V16ServiceDatabase
import com.v16studio.v16service.data.V16ServiceRepository
import com.v16studio.v16service.data.ImageCleanupService
import com.v16studio.v16service.data.AggregateReportService
import com.v16studio.v16service.domain.BusinessDateSignal
import com.v16studio.v16service.domain.MutableBusinessTime
import com.v16studio.v16service.reminders.ReminderCoordinator
import com.v16studio.v16service.calendar.AndroidCalendarGateway
import com.v16studio.v16service.calendar.CalendarCoordinator
import com.v16studio.v16service.calendar.CalendarDeviceStore
import com.v16studio.v16service.report.AndroidReportService
import com.v16studio.v16service.report.ReportService
import com.v16studio.v16service.ui.theme.AppearancePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.time.ZoneId

class V16ServiceApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val database = V16ServiceDatabase.open(this)
        val restrictedRecoveryState = runBlocking(Dispatchers.IO) { database.v16ServiceDao().recoveryMetadata()?.restrictedRecoveryState == true }
        val savedZone = runBlocking(Dispatchers.IO) { database.v16ServiceDao().businessProfile()?.zoneId }
        val businessTime = MutableBusinessTime(initialZoneId = runCatching { ZoneId.of(savedZone) }.getOrDefault(ZoneId.systemDefault()))
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val businessDateSignal = BusinessDateSignal(businessTime, applicationScope)
        val startup = applicationScope.async(Dispatchers.IO) {
            if (!restrictedRecoveryState) {
                FixtureSeederFactory.create(database).seedIfNeeded()
                runCatching { AggregateReportService(database, RoomV16ServiceRepository(database, businessTime, attachmentRoot = filesDir), filesDir).reconcile() }
                runCatching { ImageCleanupService(this@V16ServiceApplication, database).runIfDue() }
            }
        }
        val repository = RoomV16ServiceRepository(database, businessTime, attachmentRoot = filesDir, businessDateSignal = businessDateSignal)
        val reminders = ReminderCoordinator(this, database, businessTime, applicationScope)
        val calendar = CalendarCoordinator(this, database, AndroidCalendarGateway(this), CalendarDeviceStore(this), applicationScope)
        val appearancePreferences = AppearancePreferences(this)
        container = AppContainer(database, repository, AndroidReportService(this, database, repository), startup, restrictedRecoveryState, applicationScope, businessDateSignal, reminders, calendar, appearancePreferences)
        businessDateSignal.start()
        reminders.start()
        calendar.start()
    }
}

data class AppContainer(
    val database: V16ServiceDatabase,
    val repository: V16ServiceRepository,
    val reportService: ReportService,
    val startup: Deferred<Unit>,
    val restrictedRecoveryState: Boolean = false,
    val applicationScope: CoroutineScope,
    val businessDateSignal: BusinessDateSignal,
    val reminderCoordinator: ReminderCoordinator,
    val calendarCoordinator: CalendarCoordinator,
    val appearancePreferences: AppearancePreferences,
)

fun interface StartupSeeder { suspend fun seedIfNeeded() }

class NoOpStartupSeeder : StartupSeeder {
    override suspend fun seedIfNeeded() = Unit
}
