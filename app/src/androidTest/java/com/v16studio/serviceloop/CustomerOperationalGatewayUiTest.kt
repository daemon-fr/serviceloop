package com.v16studio.serviceloop

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
import com.v16studio.serviceloop.data.RoomServiceLoopRepository
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import com.v16studio.serviceloop.data.ServiceLoopRepository
import com.v16studio.serviceloop.domain.BusinessTime
import com.v16studio.serviceloop.domain.CustomerDetail
import com.v16studio.serviceloop.domain.OperationalDashboardProjection
import com.v16studio.serviceloop.domain.OperationalDashboardSection
import com.v16studio.serviceloop.domain.OperationalWorkClassifier
import com.v16studio.serviceloop.domain.OperationalWorkItem
import com.v16studio.serviceloop.domain.OperationalWorkKind
import com.v16studio.serviceloop.domain.OperationalWorkState
import com.v16studio.serviceloop.domain.SiteSummary
import com.v16studio.serviceloop.domain.WorkScope
import com.v16studio.serviceloop.ui.CustomerDetailScreen
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
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
    private lateinit var database: ServiceLoopDatabase
    private lateinit var repository: ServiceLoopRepository
    private lateinit var viewModel: ServiceLoopViewModel

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        val time = object : BusinessTime {
            override val zoneId = ZoneId.of("Europe/Bucharest")
            override fun instant() = Instant.parse("2026-09-16T10:00:00Z")
        }
        repository = RoomServiceLoopRepository(database, time, attachmentRoot = context.filesDir)
        viewModel = ServiceLoopViewModel(repository) {}
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
            ServiceLoopTheme(darkTheme = darkTheme) {
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
