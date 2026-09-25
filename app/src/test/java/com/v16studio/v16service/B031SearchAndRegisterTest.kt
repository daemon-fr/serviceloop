package com.v16studio.v16service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.v16studio.v16service.ui.SearchUiPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class B031SearchAndRegisterTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setUp() {
        context.getSharedPreferences(SearchUiPreferences.FILE_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun tearDown() {
        context.getSharedPreferences(SearchUiPreferences.FILE_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun searchHistoryNormalizesDeduplicatesAndKeepsNewestTen() {
        val preferences = SearchUiPreferences(context)
        assertEquals(emptyList<String>(), preferences.recordQuery("   "))
        assertEquals(listOf("harbor"), preferences.recordQuery("  harbor  "))
        assertEquals(listOf("customer", "harbor"), preferences.recordQuery("customer"))
        assertEquals(listOf("Harbor", "customer"), preferences.recordQuery(" Harbor "))
        assertEquals(1, preferences.recentQueries().count { it.equals("harbor", ignoreCase = true) })

        (1..11).forEach { preferences.recordQuery("query $it") }

        assertEquals(10, preferences.recentQueries().size)
        assertEquals("query 11", preferences.recentQueries().first())
        assertFalse(preferences.recentQueries().contains("query 1"))
    }

    @Test fun categoryVisibilityDefaultsExpandedAndPersistsByStableKey() {
        val first = SearchUiPreferences(context)
        assertTrue(first.isCategoryExpanded("CUSTOMER"))
        first.setCategoryExpanded("CUSTOMER", false)

        val reentered = SearchUiPreferences(context)
        assertFalse(reentered.isCategoryExpanded("CUSTOMER"))
        assertTrue(reentered.isCategoryExpanded("Customers"))
    }
}
