package com.v16studio.v16service

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.v16service.data.RoomV16ServiceRepository
import com.v16studio.v16service.data.V16ServiceDatabase
import com.v16studio.v16service.data.V16ServiceRepository
import com.v16studio.v16service.domain.BusinessTime
import com.v16studio.v16service.domain.CustomerDetail
import com.v16studio.v16service.domain.OperationalDashboardProjection
import com.v16studio.v16service.domain.OperationalDashboardSection
import com.v16studio.v16service.domain.OperationalWorkClassifier
import com.v16studio.v16service.domain.OperationalWorkItem
import com.v16studio.v16service.domain.OperationalWorkKind
import com.v16studio.v16service.domain.OperationalWorkState
import com.v16studio.v16service.domain.SiteSummary
import com.v16studio.v16service.domain.WorkScope
import com.v16studio.v16service.ui.CustomerDetailScreen
import com.v16studio.v16service.ui.V16ServiceViewModel
import com.v16studio.v16service.ui.theme.V16ServiceTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomerOperationalGatewayUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var database: V16ServiceDatabase
    private lateinit var repository: V16ServiceRepository
    private lateinit var viewModel: V16ServiceViewModel

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, V16ServiceDatabase::class.java).allowMainThreadQueries().build()
        val time = object : BusinessTime {
            override val zoneId = ZoneId.of("Europe/Bucharest")
            override fun instant() = Instant.parse("2026-09-16T10:00:00Z")
        }
        repository = RoomV16ServiceRepository(database, time, attachmentRoot = context.filesDir)
        viewModel = V16ServiceViewModel(repository) {}
    }

    @After fun close() {
        compose.runOnUiThread { compose.activity.setContent {} }
        compose.waitForIdle()
        database.close()
    }

    @Test fun gatewayAppearsBeforeCustomerTabsWithPaleOverdueContainerInLightTheme() {
        render(darkTheme = false, state = OperationalWorkState.OVERDUE)

        val gateway = compose.onNodeWithTag("operational-work-gateway")
        val tabs = compose.onNodeWithTag("content-tab-Sites")
        gateway.assertIsDisplayed()
        tabs.assertIsDisplayed()
        assertTrue(gateway.fetchSemanticsNode().boundsInRoot.top < tabs.fetchSemanticsNode().boundsInRoot.top)
    }

    @Test fun gatewayKeepsPaleOverdueContainerAndOrderingInDarkTheme() {
        render(darkTheme = true, state = OperationalWorkState.OVERDUE)

        val gateway = compose.onNodeWithTag("operational-work-gateway")
        val tabs = compose.onNodeWithTag("content-tab-Sites")
        gateway.assertIsDisplayed()
        tabs.assertIsDisplayed()
        assertTrue(gateway.fetchSemanticsNode().boundsInRoot.top < tabs.fetchSemanticsNode().boundsInRoot.top)
    }

    @Test fun renderEvidenceKeepsPaleOverdueContainerInLightTheme() {
        assumeRenderEvidenceSuite()
        render(darkTheme = false, state = OperationalWorkState.OVERDUE)
        assertEquals(android.graphics.Color.rgb(251, 233, 232), gatewayPixel("operational-work-gateway"))
    }

    @Test fun renderEvidenceKeepsPaleOverdueContainerInDarkTheme() {
        assumeRenderEvidenceSuite()
        render(darkTheme = true, state = OperationalWorkState.OVERDUE)
        assertEquals(android.graphics.Color.rgb(73, 40, 45), gatewayPixel("operational-work-gateway"))
    }

    private fun gatewayPixel(tag: String): Int {
        var lastFailure: Throwable? = null
        repeat(2) {
            try {
                compose.waitForIdle()
                val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
                return bitmap.getPixel(bitmap.width - 2, bitmap.height / 2)
            } catch (failure: Throwable) {
                lastFailure = failure
                compose.runOnUiThread { compose.activity.window.decorView.invalidate() }
            }
        }
        throw AssertionError("Unable to capture gateway render evidence after one retry", lastFailure)
    }

    private fun render(darkTheme: Boolean, state: OperationalWorkState) {
        compose.setContent {
            V16ServiceTheme(darkTheme = darkTheme) {
                CustomerDetailScreen(
                    detail = CustomerDetail(
                        id = "customer-a",
                        reference = "CU-001",
                        name = "Gateway customer",
                        contactName = "Dana",
                        phone = "555-0100",
                        email = "dana@example.invalid",
                        privateNote = "",
                        sites = listOf(SiteSummary("site-a", "ST-001", "Main site", "1 Test Road", 1, true)),
                        equipment = emptyList(),
                        openFollowUps = emptyList(),
                        recentContacts = emptyList(),
                    ),
                    padding = PaddingValues(0.dp),
                    nav = rememberNavController(),
                    viewModel = viewModel,
                    workDashboard = OperationalDashboardProjection(
                        scope = WorkScope.Customer("customer-a"),
                        sections = listOf(
                            OperationalDashboardSection(
                                kind = OperationalWorkKind.VISIT,
                                state = state,
                                title = OperationalWorkClassifier.sectionTitle(OperationalWorkKind.VISIT, state),
                                items = listOf(
                                    OperationalWorkItem(
                                        kind = OperationalWorkKind.VISIT,
                                        state = state,
                                        recordId = "visit-a",
                                        customerId = "customer-a",
                                        siteId = "site-a",
                                        equipmentId = null,
                                        displayReference = "V-001",
                                        displayTitle = "Booked visit",
                                        displayContext = "Main site",
                                        dueDate = LocalDate.parse("2026-09-15"),
                                        scheduledAtEpochMillis = null,
                                        modifiedAtEpochMillis = 1L,
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
            }
        }
        compose.waitForIdle()
    }
}
