package com.v16studio.v16service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.v16studio.v16service.ui.theme.AppearanceMode
import com.v16studio.v16service.ui.theme.AppearancePreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearancePreferencesInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clearSavedMode() {
        context.getSharedPreferences("v16service_appearance", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun restoreSystemDefault() {
        context.getSharedPreferences("v16service_appearance", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun defaultAndSelectionPersistAcrossControllerInstances() {
        val first = AppearancePreferences(context)
        assertEquals(AppearanceMode.SYSTEM, first.mode.value)
        first.setMode(AppearanceMode.LIGHT)
        assertEquals(AppearanceMode.LIGHT, first.mode.value)
        assertEquals(AppearanceMode.LIGHT, AppearancePreferences(context).mode.value)
        first.setMode(AppearanceMode.DARK)
        assertEquals(AppearanceMode.DARK, AppearancePreferences(context).mode.value)
    }
}
