package com.v16studio.serviceloop

import com.v16studio.serviceloop.data.ServiceLoopDatabase

object FixtureSeederFactory {
    fun create(@Suppress("UNUSED_PARAMETER") database: ServiceLoopDatabase): StartupSeeder = NoOpStartupSeeder()
}
