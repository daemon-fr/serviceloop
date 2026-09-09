package com.v16studio.serviceloop

import android.app.Application
import com.v16studio.serviceloop.data.RoomServiceLoopRepository
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import com.v16studio.serviceloop.data.ServiceLoopRepository
import com.v16studio.serviceloop.domain.BusinessDateSignal
import com.v16studio.serviceloop.domain.MutableBusinessTime
import com.v16studio.serviceloop.reminders.ReminderCoordinator
import com.v16studio.serviceloop.calendar.AndroidCalendarGateway
import com.v16studio.serviceloop.calendar.CalendarCoordinator
import com.v16studio.serviceloop.calendar.CalendarDeviceStore
import com.v16studio.serviceloop.report.AndroidReportService
import com.v16studio.serviceloop.report.ReportService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.time.ZoneId

class ServiceLoopApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val database = ServiceLoopDatabase.open(this)
        val restrictedRecoveryState = runBlocking(Dispatchers.IO) { database.serviceLoopDao().recoveryMetadata()?.restrictedRecoveryState == true }
        val savedZone = runBlocking(Dispatchers.IO) { database.serviceLoopDao().businessProfile()?.zoneId }
        val businessTime = MutableBusinessTime(initialZoneId = runCatching { ZoneId.of(savedZone) }.getOrDefault(ZoneId.systemDefault()))
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val businessDateSignal = BusinessDateSignal(businessTime, applicationScope)
        val startup = applicationScope.async(Dispatchers.IO) {
            if (!restrictedRecoveryState) {
                FixtureSeederFactory.create(database).seedIfNeeded()
            }
        }
        val repository = RoomServiceLoopRepository(database, businessTime, attachmentRoot = filesDir, businessDateSignal = businessDateSignal)
        val reminders = ReminderCoordinator(this, database, businessTime, applicationScope)
        val calendar = CalendarCoordinator(this, database, AndroidCalendarGateway(this), CalendarDeviceStore(this), applicationScope)
        container = AppContainer(database, repository, AndroidReportService(this, database, repository), startup, restrictedRecoveryState, applicationScope, businessDateSignal, reminders, calendar)
        businessDateSignal.start()
        reminders.start()
        calendar.reconcileAsync()
    }
}

data class AppContainer(
    val database: ServiceLoopDatabase,
    val repository: ServiceLoopRepository,
    val reportService: ReportService,
    val startup: Deferred<Unit>,
    val restrictedRecoveryState: Boolean = false,
    val applicationScope: CoroutineScope,
    val businessDateSignal: BusinessDateSignal,
    val reminderCoordinator: ReminderCoordinator,
    val calendarCoordinator: CalendarCoordinator,
)

fun interface StartupSeeder { suspend fun seedIfNeeded() }

class NoOpStartupSeeder : StartupSeeder {
    override suspend fun seedIfNeeded() = Unit
}
