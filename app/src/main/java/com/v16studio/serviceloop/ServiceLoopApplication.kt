package com.v16studio.serviceloop

import android.app.Application
import com.v16studio.serviceloop.data.RoomServiceLoopRepository
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import com.v16studio.serviceloop.data.ServiceLoopRepository
import com.v16studio.serviceloop.domain.ClockBusinessTime
import com.v16studio.serviceloop.report.AndroidReportService
import com.v16studio.serviceloop.report.ReportService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

class ServiceLoopApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val database = ServiceLoopDatabase.open(this)
        val restrictedRecoveryState = runBlocking(Dispatchers.IO) { database.serviceLoopDao().recoveryMetadata()?.restrictedRecoveryState == true }
        val businessTime = ClockBusinessTime()
        val startup = CoroutineScope(SupervisorJob() + Dispatchers.IO).async {
            if (!restrictedRecoveryState) {
                FixtureSeederFactory.create(database).seedIfNeeded()
            }
        }
        val repository = RoomServiceLoopRepository(database, businessTime, attachmentRoot = filesDir)
        container = AppContainer(database, repository, AndroidReportService(this, database, repository), startup, restrictedRecoveryState)
    }
}

data class AppContainer(
    val database: ServiceLoopDatabase,
    val repository: ServiceLoopRepository,
    val reportService: ReportService,
    val startup: Deferred<Unit>,
    val restrictedRecoveryState: Boolean = false,
)

fun interface StartupSeeder { suspend fun seedIfNeeded() }

class NoOpStartupSeeder : StartupSeeder {
    override suspend fun seedIfNeeded() = Unit
}
