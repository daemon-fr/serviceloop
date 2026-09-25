package com.v16studio.v16service

import com.v16studio.v16service.data.V16ServiceDatabase

object FixtureSeederFactory {
    fun create(@Suppress("UNUSED_PARAMETER") database: V16ServiceDatabase): StartupSeeder = NoOpStartupSeeder()
}
