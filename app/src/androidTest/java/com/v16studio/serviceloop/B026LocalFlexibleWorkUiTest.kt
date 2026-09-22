package com.v16studio.serviceloop.ui

import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.data.ServiceLoopRepository
import com.v16studio.serviceloop.domain.*
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import com.v16studio.serviceloop.calendar.VisitCalendarState

@RunWith(AndroidJUnit4::class)
class B026LocalFlexibleWorkUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun resetSearchUiPreferences() {
        val preferences = SearchUiPreferences(InstrumentationRegistry.getInstrumentation().targetContext)
        preferences.clearRecentQueries()
        listOf("CUSTOMER", "SITE", "EQUIPMENT", "TEMPLATE", "PLAN", "VISIT", "FOLLOW_UP", "FINAL_RECORD")
            .forEach { preferences.setCategoryExpanded(it, true) }
    }

    @After
    fun disposeContentAfterTest() {
        compose.runOnUiThread { compose.activity.setContent {} }
        compose.waitForIdle()
    }

    @Test fun registerHidesOneTimeRowsByDefaultAcrossAllPanes() {
        showRegister()
        compose.onNodeWithText("Standard customer").assertIsDisplayed()
        assertAbsentText("One-time customer")
        compose.onNodeWithTag("content-tab-Sites").performClick()
        compose.onNodeWithText("Standard site", substring = true).assertIsDisplayed()
        assertAbsentText("One-time site")
        compose.onNodeWithTag("content-tab-Equipment").performClick()
        compose.onNodeWithText("Standard equipment", substring = true).assertIsDisplayed()
        assertAbsentText("One-time equipment")
    }

    @Test fun registerRevealShowsMarkedOneTimeRowsAndUncheckingHidesThem() {
        showRegister()
        compose.onNodeWithTag("show-one-time-customers").performClick()
        compose.onNodeWithText("One-time customer").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("One-time", substring = true).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithTag("content-tab-Sites").performClick()
        compose.onNodeWithText("One-time site", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("content-tab-Equipment").performClick()
        compose.onNodeWithText("One-time equipment", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("show-one-time-customers").performClick()
        assertAbsentText("One-time customer")
        assertAbsentText("One-time site")
        assertAbsentText("One-time equipment")
    }

    @Test fun searchFindsOneTimeRecordWithoutRegisterReveal() {
        val result = SearchTarget("CUSTOMER", "one-time-customer", "CU-OT", "Unique one-time customer", "Unique one-time site", CustomerType.ONE_TIME)
        val viewModel = ServiceLoopViewModel(FakeRepository()) {}
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    SearchScreen(listOf(result), PaddingValues(), viewModel, rememberNavController())
                }
            }
        }
        compose.onNodeWithText("Unique one-time customer", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("field-search-names-and-references").performTextInput("Unique one-time")
        compose.onNodeWithText("Unique one-time customer", substring = true).assertIsDisplayed()
        compose.onNodeWithText("One-time", substring = true).assertIsDisplayed()
        assertAbsentTag("show-one-time-customers")
    }

    @Test fun liveSearchResultPromotesPartialQueryBeforeNavigationAndReentry() {
        val result = SearchTarget("CUSTOMER", "one-time-customer", "CU-OT", "Unique one-time customer", "Unique one-time site", CustomerType.ONE_TIME)
        val viewModel = ServiceLoopViewModel(FakeRepository()) {}
        lateinit var nav: androidx.navigation.NavHostController
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    nav = rememberNavController()
                    NavHost(nav, "search") {
                        composable("search") {
                            SearchScreen(listOf(result), PaddingValues(), viewModel, nav)
                        }
                        composable("customer/one-time-customer") {
                            Button(onClick = { nav.navigate("search") }, modifier = Modifier.testTag("return-to-search")) { Text("Return to search") }
                        }
                    }
                }
            }
        }

        compose.onNodeWithTag("field-search-names-and-references").performTextInput("Unique one-time")
        compose.onNodeWithTag("search-result-CUSTOMER-one-time-customer").performClick()
        compose.onNodeWithTag("return-to-search").performClick()
        compose.onNodeWithTag("field-search-names-and-references").performClick()
        compose.onNodeWithTag("search-recent-0").assertTextContains("Unique one-time")
    }

    @Test fun typingWithoutSearchActionDoesNotCreateRecentQuery() {
        showSearch()
        compose.onNodeWithTag("field-search-names-and-references").performTextInput("harb")
        assertTrue(SearchUiPreferences(InstrumentationRegistry.getInstrumentation().targetContext).recentQueries().isEmpty())
    }

    @Test fun imeSearchPromotesTheCurrentPartialQuery() {
        showSearch()
        compose.onNodeWithTag("field-search-names-and-references").performTextInput("harb")
        compose.onNodeWithTag("field-search-names-and-references").performImeAction()
        assertEquals(listOf("harb"), SearchUiPreferences(InstrumentationRegistry.getInstrumentation().targetContext).recentQueries())
    }

    @Test fun selectingRecentQueryPromotesItWithoutDuplicatingHistory() {
        val preferences = SearchUiPreferences(InstrumentationRegistry.getInstrumentation().targetContext)
        preferences.recordQuery("customer")
        preferences.recordQuery("harbor")
        showSearch()
        compose.onNodeWithTag("field-search-names-and-references").performClick()
        compose.onNodeWithTag("search-recent-1").performClick()
        assertEquals(listOf("customer", "harbor"), preferences.recentQueries())
    }

    @Test fun liveResultClickDeduplicatesCaseInsensitiveExistingQuery() {
        val preferences = SearchUiPreferences(InstrumentationRegistry.getInstrumentation().targetContext)
        preferences.recordQuery("Harbor")
        showSearch(listOf(SearchTarget("CUSTOMER", "customer-1", "CU-1", "Harbor customer", "Primary contact")))
        compose.onNodeWithTag("field-search-names-and-references").performTextInput("harbor")
        compose.onNodeWithTag("search-result-CUSTOMER-customer-1").performClick()
        assertEquals(listOf("harbor"), preferences.recentQueries())
    }

    @Test fun b031RegisterExposesTemplatesAsFourthTabWithoutOneTimeFilter() {
        val template = TemplateSummary("template-1", "TPL-1", "Pressure inspection", 2, 3, "ACTIVE")
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    CustomersScreen(
                        customers = emptyList(),
                        sites = emptyList(),
                        equipment = emptyList(),
                        nav = rememberNavController(),
                        templates = listOf(template),
                    )
                }
            }
        }
        compose.onNodeWithTag("content-tab-Templates").performClick()
        compose.onNodeWithText("Inspection templates").assertIsDisplayed()
        compose.onNodeWithText("TPL-1 · Pressure inspection (v2)").assertIsDisplayed()
        compose.onNodeWithText("3 items").assertIsDisplayed()
        compose.onNodeWithTag("create-inspection-template").assertIsDisplayed()
        assertAbsentTag("show-one-time-customers")
        captureRendered("b031-register-templates.png")
    }

    @Test fun b031SearchUsesFixedGroupsAndPersistsRecentQueries() {
        val preferences = SearchUiPreferences(InstrumentationRegistry.getInstrumentation().targetContext)
        preferences.clearRecentQueries()
        listOf("CUSTOMER", "TEMPLATE", "VISIT").forEach { preferences.setCategoryExpanded(it, true) }
        preferences.recordQuery("Boiler  12")
        val results = listOf(
            SearchTarget("CUSTOMER", "customer-1", "CU-1", "Boiler customer", "Primary contact"),
            SearchTarget("TEMPLATE", "template-1", "TPL-1", "Pressure inspection", "", status = "ACTIVE", revisionNumber = 2, itemCount = 3),
            SearchTarget("VISIT", "visit-1", "V-1", "Boiler visit", "Customer · Site"),
        )
        val viewModel = ServiceLoopViewModel(FakeRepository()) {}
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    SearchScreen(results, PaddingValues(), viewModel, rememberNavController())
                }
            }
        }
        compose.onNodeWithText("Customers (1)").assertIsDisplayed()
        compose.onNodeWithText("Templates (1)").assertIsDisplayed()
        compose.onNodeWithText("Visits (1)").assertIsDisplayed()
        compose.onAllNodesWithText("3 items").assertCountEquals(1)
        compose.onNodeWithTag("search-category-CUSTOMER").performClick()
        assertAbsentTag("search-result-CUSTOMER-customer-1")
        compose.onNodeWithTag("search-result-TEMPLATE-template-1").assertIsDisplayed()
        compose.onNodeWithTag("field-search-names-and-references").performClick()
        compose.onNodeWithTag("search-recent-panel").assertIsDisplayed()
        compose.onNodeWithText("Boiler 12").assertIsDisplayed()
        captureRendered("b031-search-groups-and-history.png")
    }

    @Test fun newVisitRemainsUsableWhenDueServicesFail() {
        val standard = site("standard-site", "Standard site", CustomerType.STANDARD, equipment = listOf(equipment("standard-equipment", "Standard equipment")))
        val due = due(standard, "plan-unavailable")
        val state = UiState(
            loading = false,
            dueServicesProjection = DueServicesProjection.Unavailable("Due service projection failed"),
            templates = emptyList(),
            businessDate = LocalDate.of(2026, 9, 5),
            businessZoneId = "Europe/Bucharest",
        )
        showNewVisit(listOf(standard), listOf(due), state, listOf(due.planId))
        compose.onNodeWithText("Set up visit").assertIsDisplayed()
        compose.onNodeWithTag("visit-mode-NEW").assertIsDisplayed()
        compose.onNodeWithText("Planned services are unavailable.").assertIsDisplayed()
        compose.onNodeWithTag("new-visit-form").performScrollToNode(hasTestTag("field-task-name-required")).assertIsDisplayed()
        assertAbsentText("Unable to read due services")
    }

    @Test fun newVisitUsesExistingAndNewModesAndNewCustomerTypeCheckboxDefaultsOff() {
        showNewVisit(emptyList(), emptyList())

        compose.onNodeWithTag("visit-mode-EXISTING").assertIsDisplayed()
        compose.onNodeWithTag("visit-mode-NEW").assertIsDisplayed().performClick()
        compose.onNodeWithText("One-time customer (no contract)").assertIsDisplayed()
        val checkbox = compose.onNodeWithTag("new-visit-one-time-customer")
        assertEquals(ToggleableState.Off, checkbox.fetchSemanticsNode().config[SemanticsProperties.ToggleableState])
        checkbox.performClick()
        assertEquals(ToggleableState.On, checkbox.fetchSemanticsNode().config[SemanticsProperties.ToggleableState])
    }

    @Test fun newVisitSavesRequestedCustomerTypeThroughTheNewCustomerFlow() {
        val repository = FakeRepository()
        val viewModel = ServiceLoopViewModel(repository) {}
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    CompositionLocalProvider(LocalDetailBackInterceptor provides remember { mutableStateOf<(() -> Unit)?>(null) }) {
                        NewVisitScreen(emptyList(), emptyList(), PaddingValues(), UiState(loading = false, businessDate = LocalDate.of(2026, 9, 5), businessZoneId = "Europe/Bucharest"), viewModel, rememberNavController())
                    }
                }
            }
        }

        compose.onNodeWithTag("visit-mode-NEW").performClick()
        compose.onNodeWithTag("new-visit-form").performScrollToNode(hasTestTag("field-customer-name-required"))
        compose.onNodeWithTag("field-customer-name-required").performTextInput("New customer")
        compose.onNodeWithTag("field-task-name-required").performTextInput("Initial inspection")
        compose.onNodeWithTag("field-customer-name-required").assertTextContains("New customer")
        compose.onNodeWithTag("field-task-name-required").assertTextContains("Initial inspection")
        compose.onNodeWithTag("add-task").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("new-visit-one-time-customer").performClick()
        compose.onNodeWithTag("new-visit-form").performScrollToNode(hasTestTag("field-appointment-service-date-yyyy-mm-dd"))
        compose.onNodeWithTag("field-appointment-service-date-yyyy-mm-dd").performTextReplacement("2026-09-06")
        compose.onNodeWithTag("field-appointment-service-date-yyyy-mm-dd").assertTextContains("2026-09-06")
        compose.onNodeWithTag("new-visit-form").performScrollToNode(hasTestTag("primary-visit-action-BOOKED"))
        compose.onNodeWithTag("primary-visit-action-BOOKED").assertIsEnabled().performClick()

        compose.waitUntil(10_000) { repository.createdCustomerType == CustomerType.ONE_TIME }
        assertEquals(CustomerType.ONE_TIME, repository.createdCustomerType)
    }

    @Test fun calendarOffOffersSettingsNavigationAndUsesTechnicianCopy() {
        val repository = FakeRepository()
        val viewModel = ServiceLoopViewModel(repository) {}
        val detail = VisitDetail("visit-calendar", "V-CAL", "BOOKED", "customer", "Customer", "site", "Site", "Address", "2026-09-06", null, null, emptyList(), null)
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    val nav = rememberNavController()
                    NavHost(nav, "visit") {
                        composable("visit") { VisitDetailScreen(detail, PaddingValues(), UiState(loading = false, visitCalendarState = VisitCalendarState("Calendar integration is off")), viewModel, nav) }
                        composable("calendar") { androidx.compose.material3.Text("Calendar settings", modifier = androidx.compose.ui.Modifier.testTag("calendar-settings-destination")) }
                    }
                }
            }
        }
        compose.onNodeWithTag("visit-calendar-status").assertTextContains("Calendar integration is off")
        compose.onNodeWithTag("visit-calendar-settings").assertIsDisplayed().performClick()
        compose.onNodeWithTag("calendar-settings-destination").assertIsDisplayed()
    }

    @Test fun navigationOnlyButtonShowsDisclosureButCommandsDoNot() {
        var navigated = false
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    androidx.compose.foundation.layout.Column {
                        com.v16studio.serviceloop.ui.designsystem.ServiceLoopNavigationButton("Customer", { navigated = true }, androidx.compose.ui.Modifier.testTag("relationship-navigation"))
                        com.v16studio.serviceloop.ui.designsystem.ServiceLoopSecondaryButton("Edit", {}, androidx.compose.ui.Modifier.testTag("command-edit"))
                        com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrimaryButton("Create visit", {}, androidx.compose.ui.Modifier.testTag("command-create"))
                    }
                }
            }
        }
        compose.onNodeWithTag("service-loop-disclosure-icon", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithTag("service-loop-disclosure-icon", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag("relationship-navigation").performClick()
        compose.onNodeWithTag("command-edit").assertIsDisplayed()
        compose.onNodeWithTag("command-create").assertIsDisplayed()
        assertTrue(navigated)
        val navigationBounds = compose.onNodeWithTag("relationship-navigation").fetchSemanticsNode().boundsInRoot
        val customerLabelBounds = compose.onNodeWithText("Customer", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val disclosureBounds = compose.onNodeWithTag("service-loop-disclosure-icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Navigation labels should reserve the caret area", customerLabelBounds.center.x < navigationBounds.center.x)
        assertTrue("Disclosure caret should have a clear trailing gap", disclosureBounds.left - customerLabelBounds.right >= 8f * compose.activity.resources.displayMetrics.density - 1f)
        assertTrue(disclosureBounds.center.x > customerLabelBounds.center.x)
        captureRendered("b032-navigation-button.png")
    }

    @Test fun siteActionsGiveLongerNavigationLabelsMoreWidth() {
        val detail = SiteDetail(
            id = "site-layout",
            customerId = "customer-layout",
            customerName = "Customer layout",
            reference = "ST-LAYOUT",
            name = "Main site",
            address = "123 Main Street",
            contactName = "",
            phone = "",
            email = "",
            privateAccessNote = "",
            isDefault = true,
            equipment = emptyList(),
        )
        val viewModel = ServiceLoopViewModel(FakeRepository(site = detail)) {}
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme { SiteDetailScreen(detail, PaddingValues(), rememberNavController(), viewModel) }
            }
        }
        val customer = compose.onNodeWithTag("site-customer-link").fetchSemanticsNode().boundsInRoot
        val edit = compose.onNodeWithTag("site-edit-link").fetchSemanticsNode().boundsInRoot
        val maps = compose.onNodeWithTag("site-maps-link").fetchSemanticsNode().boundsInRoot
        assertTrue("Customer action should receive the widest natural slot", customer.width >= edit.width)
        assertTrue("Customer action should receive the widest natural slot", customer.width >= maps.width)
        assertTrue(customer.right <= edit.left)
        assertTrue(edit.right <= maps.left)
        captureRendered("b032-site-top-actions.png")
    }

    @Test fun customerEditShowsTheSameCheckboxAndExplainsAPlanBlock() {
        val repository = FakeRepository()
        val viewModel = ServiceLoopViewModel(repository) {}
        val blocked = repository.customerDetail.copy(
            customerType = CustomerType.STANDARD,
            canMarkOneTime = false,
            oneTimeBlockReason = "This customer has recurring service plans and cannot be marked one-time.",
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    CompositionLocalProvider(LocalDetailBackInterceptor provides remember { mutableStateOf<(() -> Unit)?>(null) }) {
                        CustomerEditorScreen(blocked, PaddingValues(), UiState(loading = false), viewModel, rememberNavController())
                    }
                }
            }
        }
        val checkbox = compose.onNodeWithTag("customer-one-time-checkbox")
        assertEquals(ToggleableState.Off, checkbox.fetchSemanticsNode().config[SemanticsProperties.ToggleableState])
        checkbox.assertIsNotEnabled()
        compose.onNodeWithTag("customer-one-time-block-reason").assertIsDisplayed()
        compose.onNodeWithText("This customer has recurring service plans and cannot be marked one-time.").assertIsDisplayed()
    }

    @Test fun servicePlanDetailLeavesARealGapBeforeTheActionStack() {
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    PlanDetailScreen(PlanDetail("plan", "P-1", "Annual", "1", "Annual service", 1, "YEARS", "2026-09-06", "ACTIVE", null), PaddingValues(), rememberNavController())
                }
            }
        }
        val explanationBottom = compose.onNodeWithText("Current obligation remains separate from bookings and contact.").fetchSemanticsNode().boundsInRoot.bottom
        val firstActionTop = compose.onNodeWithText("Edit plan").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Plan actions need breathing room", firstActionTop - explanationBottom >= 16f)
    }

    @Test fun shortAndLongVisitsListsKeepNaturalNewVisitStableAndFloatOnlyForLongLists() {
        val short = listOf(VisitSummary("short", "V-SHORT", "Site", "2026-09-05", "BOOKED", null, customerId = "customer", customerName = "Customer"))
        val long = (1..24).map { index -> VisitSummary("visit-$index", "V-$index", "Site $index", "2026-09-05", "BOOKED", null, customerId = "customer", customerName = "Customer") }
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    VisitsWorkScreen(short, LocalDate.of(2026, 9, 5), PaddingValues(), rememberNavController(), initialDateFilter = VisitDateFilter.ALL, initialStatusFilter = VisitStatusFilter.ALL)
                }
            }
        }
        compose.onNodeWithTag("new-visit-work-bottom").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTag("new-visit-work-floating").fetchSemanticsNodes().isEmpty())
        val shortButtonTop = compose.onNodeWithTag("new-visit-work-bottom").fetchSemanticsNode().boundsInRoot.top
        compose.waitForIdle()
        assertEquals(shortButtonTop, compose.onNodeWithTag("new-visit-work-bottom").fetchSemanticsNode().boundsInRoot.top, 0.5f)

        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    VisitsWorkScreen(long, LocalDate.of(2026, 9, 5), PaddingValues(), rememberNavController(), initialDateFilter = VisitDateFilter.ALL, initialStatusFilter = VisitStatusFilter.ALL)
                }
            }
        }
        compose.onNodeWithTag("new-visit-work-floating").assertIsDisplayed()
        compose.onNodeWithTag("work-visits-list").performScrollToNode(hasTestTag("new-visit-slot"))
        compose.onNodeWithTag("new-visit-work-bottom").assertIsDisplayed()
        compose.onAllNodesWithTag("new-visit-work-bottom").assertCountEquals(1)
        assertTrue(compose.onAllNodesWithTag("new-visit-work-floating").fetchSemanticsNodes().isEmpty())
    }

    @Test fun dueServicesFloatingActionSitsAbovePersistentSelectionActions() {
        val site = site("due-site", "Due site", CustomerType.STANDARD, equipment = listOf(equipment("due-equipment", "Due equipment")))
        val dueRows = (1..24).map { index -> due(site, "due-plan-$index") }
        val state = UiState(
            loading = false,
            dueServicesProjection = DueServicesProjection.Available(dueRows),
            businessDate = LocalDate.of(2026, 9, 5),
            businessZoneId = "Europe/Bucharest",
        )
        val viewModel = ServiceLoopViewModel(FakeRepository()) {}
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    DueServicesScreen(dueRows, PaddingValues(), state, viewModel, rememberNavController(), initialBucket = DueBucket.TODAY)
                }
            }
        }

        compose.onNodeWithTag("new-visit-work-floating").assertIsDisplayed()
        val density = compose.activity.resources.displayMetrics.density
        val floating = compose.onNodeWithTag("new-visit-work-floating").fetchSemanticsNode().boundsInRoot
        val selectionTop = compose.onNodeWithTag("book-selected-services").fetchSemanticsNode().boundsInRoot.top
        val list = compose.onNodeWithTag("due-services-list").fetchSemanticsNode().boundsInRoot
        assertTrue("Due Services floater must not overlap selection actions", floating.bottom <= selectionTop)
        assertTrue("Due Services floater should sit just above selection actions", selectionTop - floating.bottom <= 32f * density)
        assertTrue("Due Services floater should remain near the list right edge", list.right - floating.right <= 32f * density)
    }

    @Test fun templateDetailChecklistRowsAreQuietReadOnlyRowsAndOnlyHistoryNavigates() {
        val detail = TemplateDetail(
            "template-1",
            "IT-001",
            "Safety checks",
            2,
            "ACTIVE",
            listOf(TemplateItemDraft("Belt tension", "NUMBER", "mm", required = true)),
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    val nav = rememberNavController()
                    NavHost(nav, "template") {
                        composable("template") { TemplateDetailScreen(detail, PaddingValues(), nav, versions = listOf(TemplateRevisionDetail("revision-2", 2, "Safety checks", 1L, detail.items))) }
                        composable("template/history/{id}") { androidx.compose.material3.Text("Version history destination", modifier = androidx.compose.ui.Modifier.testTag("template-history-destination")) }
                        composable(
                            "template/edit/{id}?focusItem={focusItem}",
                            arguments = listOf(
                                navArgument("id") { type = NavType.StringType },
                                navArgument("focusItem") { type = NavType.IntType },
                            ),
                        ) { entry ->
                            androidx.compose.material3.Text("Focused item ${entry.arguments?.getInt("focusItem")}", modifier = androidx.compose.ui.Modifier.testTag("template-editor-destination"))
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("template-detail-item-0", useUnmergedTree = true).assertHasNoClickAction()
        compose.onAllNodesWithTag("service-loop-disclosure-icon", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag("template-version-history").assertIsDisplayed().performClick()
        compose.onNodeWithTag("template-history-destination").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTag("template-editor-destination").fetchSemanticsNodes().isEmpty())
    }

    @Test fun templateItemUsesIconOnlyGroupedActionsInViewAndEditModes() {
        val detail = TemplateDetail(
            "template-1",
            "IT-001",
            "Safety checks",
            2,
            "ACTIVE",
            listOf(
                TemplateItemDraft("Belt tension", "NUMBER", "mm", required = true),
                TemplateItemDraft("Filter condition", "STATUS", required = false),
            ),
        )
        val viewModel = ServiceLoopViewModel(FakeRepository()) {}
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    val nav = rememberNavController()
                    NavHost(nav, "editor") {
                        composable("editor") {
                            CompositionLocalProvider(LocalDetailBackInterceptor provides remember { mutableStateOf<(() -> Unit)?>(null) }) {
                                TemplateEditorScreen(detail, PaddingValues(), UiState(loading = false), viewModel, nav)
                            }
                        }
                    }
                }
            }
        }

        val delete = compose.onNodeWithTag("template-item-delete-0").fetchSemanticsNode().boundsInRoot
        val moveUp = compose.onNodeWithTag("template-item-move-up-0").fetchSemanticsNode().boundsInRoot
        val moveDown = compose.onNodeWithTag("template-item-move-down-0").fetchSemanticsNode().boundsInRoot
        val edit = compose.onNodeWithTag("template-item-edit-0").fetchSemanticsNode().boundsInRoot
        assertTrue("Delete must be left of the movement pair", delete.right <= moveUp.left)
        assertTrue("Movement controls must be adjacent", moveUp.right <= moveDown.left)
        assertTrue("Edit must be right of the movement pair", moveDown.right <= edit.left)
        assertTrue("Icon actions must be larger than the minimum touch target", delete.height >= 56f * compose.activity.resources.displayMetrics.density - 1f)
        compose.onAllNodesWithContentDescription("Delete item").assertCountEquals(2)
        compose.onNodeWithTag("template-item-move-up-0").assertIsNotEnabled()
        compose.onNodeWithTag("template-item-move-down-0").assertIsEnabled().performClick()
        compose.onNodeWithText("1. Filter condition").assertIsDisplayed()
        compose.onNodeWithText("2. Belt tension").assertIsDisplayed()
        captureRendered("b032-template-item-view.png")
        assertTrue(compose.onAllNodesWithText("Remove").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Move up").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Move down").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Done").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("template-item-edit-0").performClick()
        compose.onNodeWithTag("template-item-caret-down-0", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Save item changes").assertIsDisplayed()
        compose.onNodeWithTag("template-item-move-up-0").assertIsNotEnabled()
        compose.onNodeWithTag("template-item-move-down-0").assertIsEnabled().performClick()
        compose.onNodeWithText("1. Belt tension").assertIsDisplayed()
        compose.onNodeWithText("2. Filter condition").assertIsDisplayed()
        captureRendered("b032-template-item-edit.png")
        compose.onNodeWithContentDescription("Save item changes").performClick()
        compose.onNodeWithTag("template-item-caret-right-1", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Done").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Edit").fetchSemanticsNodes().isEmpty())
    }

    @Test fun templateStateActionReloadsSynchronousPairWithoutChangingRevision() {
        val repository = FakeRepository()
        val viewModel = ServiceLoopViewModel(repository) {}
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    val state by viewModel.state.collectAsState()
                    LaunchedEffect(Unit) { viewModel.loadTemplate("template-state") }
                    TemplateDetailScreen(state.template, PaddingValues(), rememberNavController(), viewModel, planReferenceCount = state.templatePlanReferenceCount ?: 0)
                }
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Disable").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Disable").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Activate").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodesWithText("Enable").fetchSemanticsNodes().isEmpty())
        assertEquals("DISABLED", repository.templateDetail.state)
        assertEquals(2, repository.templateDetail.revisionNumber)
    }

    @Test fun newTemplateShowsItemTypeCardsWithRadioSelectionAndCompactGuidance() {
        val viewModel = ServiceLoopViewModel(FakeRepository()) {}
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    CompositionLocalProvider(LocalDetailBackInterceptor provides remember { mutableStateOf<(() -> Unit)?>(null) }) {
                        TemplateEditorScreen(null, PaddingValues(), UiState(loading = false), viewModel, rememberNavController())
                    }
                }
            }
        }

        compose.onNodeWithTag("new-template-item-type-label").assertIsDisplayed()
        val typeLabel = compose.onNodeWithTag("new-template-item-type-label").fetchSemanticsNode().boundsInRoot
        listOf("STATUS", "TEXT", "NUMBER").forEach { type ->
            compose.onNodeWithTag("new-template-type-$type").assertIsDisplayed()
            compose.onNodeWithText(
                when (type) {
                    "STATUS" -> "Record whether the check is satisfactory, needs attention, or cannot be completed."
                    "TEXT" -> "Record a written observation, note, or result."
                    else -> "Record a measured value, with a unit when needed."
                },
            ).assertIsDisplayed()
        }
        val firstType = compose.onNodeWithTag("new-template-type-STATUS").fetchSemanticsNode().boundsInRoot
        assertTrue("Item type needs a small separation before the first option", firstType.top - typeLabel.bottom >= 4f * compose.activity.resources.displayMetrics.density - 1f)
        compose.onNodeWithTag("new-template-type-STATUS").assertIsSelected()
        compose.onNodeWithTag("long-text-private-technician-guidance-expand", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("new-template-type-TEXT").performClick()
        compose.onNodeWithTag("new-template-type-TEXT").assertIsSelected()
        compose.onNodeWithTag("new-template-type-STATUS").assertIsNotSelected()
        captureRendered("b030-template-new.png")
    }

    @Test fun startNowCreatesWorkingVisitAndOpensItsFirstService() {
        val standard = site("standard-site", "Standard site", CustomerType.STANDARD)
        val due = due(standard, "plan-start")
        val repository = FakeRepository().apply {
            workingProgress = VisitServiceProgress(
                visitId = "created-working-visit",
                visitReference = "V-START",
                customerName = "Customer",
                siteName = "Standard site",
                serviceDate = "2026-09-05",
                items = listOf(ServiceProgressItem(
                    workItemId = "created-work-item",
                    position = 1,
                    subjectType = WorkSubjectType.SITE,
                    equipmentId = null,
                    equipmentName = null,
                    equipmentReference = null,
                    equipmentDescription = null,
                    serviceName = "Started service",
                    status = ServiceEntryStatus.NOT_STARTED,
                )),
                groups = emptyList(),
            )
        }
        val state = UiState(
            loading = false,
            dueServicesProjection = DueServicesProjection.Available(listOf(due)),
            businessDate = LocalDate.of(2026, 9, 5),
            businessZoneId = "Europe/Bucharest",
        )
        val viewModel = ServiceLoopViewModel(repository) {}

        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    CompositionLocalProvider(LocalDetailBackInterceptor provides remember { mutableStateOf<(() -> Unit)?>(null) }) {
                        val nav = rememberNavController()
                        NavHost(nav, "visit/new") {
                            composable("visit/new") { NewVisitScreen(listOf(standard), listOf(due), PaddingValues(), state, viewModel, nav, listOf(due.planId)) }
                            composable("visit/{visitId}") { androidx.compose.material3.Text("Visit overview", modifier = androidx.compose.ui.Modifier.testTag("visit-overview")) }
                            composable("inspection/{workItemId}") { androidx.compose.material3.Text("Service destination", modifier = androidx.compose.ui.Modifier.testTag("service-destination")) }
                        }
                    }
                }
            }
        }

        compose.onNodeWithTag("new-visit-form").performScrollToNode(hasTestTag("field-task-name-required"))
        compose.onNodeWithTag("field-task-name-required").performTextInput("Start maintenance")
        compose.onNodeWithTag("add-task").performClick()
        compose.onNodeWithTag("new-visit-form").performScrollToNode(hasTestTag("field-appointment-service-date-yyyy-mm-dd"))
        compose.onNodeWithTag("field-appointment-service-date-yyyy-mm-dd").performTextReplacement("2026-09-05")
        compose.onNodeWithTag("primary-visit-action-WORKING").performScrollTo().assertIsEnabled().performClick()

        compose.waitUntil(10_000) { repository.createdVisitId != null }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("service-destination").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("service-destination").assertIsDisplayed()
    }

    @Test fun existingSiteSearchDeliberatelyRevealsMatchingOneTimeSite() {
        val standard = site("standard-site", "Standard site", CustomerType.STANDARD)
        val oneTime = site("one-time-site", "Unique one-time site", CustomerType.ONE_TIME)
        showNewVisit(listOf(standard, oneTime), emptyList())
        compose.onNodeWithTag("visit-site-standard-site").assertIsDisplayed()
        assertAbsentText("Unique one-time site")
        compose.onNodeWithTag("field-find-customer-or-site").performTextInput("Unique one-time")
        compose.onNodeWithTag("visit-site-one-time-site").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("One-time", substring = true).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithTag("visit-site-one-time-site-select").performClick()
        compose.onNodeWithTag("visit-site-continue").performClick()
        compose.onNodeWithTag("new-visit-form").performScrollToNode(hasTestTag("field-task-name-required")).assertIsDisplayed()
    }

    @Test fun siteSelectionIsStagedUntilContinueAndUsesSeparateCheckAction() {
        val first = site("first-site", "First site", CustomerType.STANDARD)
        val second = site("second-site", "Second site", CustomerType.STANDARD)
        val checklist = TemplateSummary("smoke-template", "IT-SMOKE", "Smoke checklist", 1, 1, "ACTIVE")
        showNewVisit(
            listOf(first, second),
            emptyList(),
            state = UiState(
                loading = false,
                dueServicesProjection = DueServicesProjection.Available(emptyList()),
                businessDate = LocalDate.of(2026, 9, 5),
                businessZoneId = "Europe/Bucharest",
                templates = listOf(checklist),
            ),
        )
        captureRendered("b044-site-chooser.png")

        compose.onNodeWithTag("visit-site-continue").assertIsNotEnabled()
        compose.onAllNodesWithTag("field-appointment-service-date-yyyy-mm-dd").assertCountEquals(0)
        compose.onAllNodesWithTag("field-task-name-required").assertCountEquals(0)
        compose.onNodeWithTag("visit-site-first-site").assertHasNoClickAction()
        compose.onNodeWithTag("visit-site-first-site-select").assertIsNotSelected().performClick()
        compose.onNodeWithTag("visit-site-first-site-select").assertIsSelected()
        compose.onNodeWithTag("visit-site-second-site-select").assertIsNotSelected()
        compose.onNodeWithTag("visit-site-continue").assertIsEnabled()
        compose.onAllNodesWithTag("field-appointment-service-date-yyyy-mm-dd").assertCountEquals(0)

        compose.onNodeWithTag("visit-site-second-site-select").performClick()
        compose.onNodeWithTag("visit-site-first-site-select").assertIsNotSelected()
        compose.onNodeWithTag("visit-site-second-site-select").assertIsSelected()
        compose.onNodeWithTag("visit-site-continue").performClick()
        compose.onNodeWithTag("field-appointment-service-date-yyyy-mm-dd").assertIsDisplayed()
        compose.onNodeWithText("Customer second-site · Second site").assertIsDisplayed()
        compose.onNodeWithTag("field-task-name-required").performTextInput("Inspect site")
        compose.onNodeWithTag("task-template-selector").assertIsDisplayed()
        compose.onNodeWithTag("task-template-suggestion").assertTextContains("Only active inspection checklist", substring = true)
        compose.runOnUiThread {
            val manager = compose.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            manager.hideSoftInputFromWindow(compose.activity.currentFocus?.windowToken, 0)
        }
        compose.waitForIdle()
        captureRendered("b044-site-configured.png")
    }

    @Test fun changingSiteProtectsTaskDraftsAndUsesTheSameOneTimeDiscoveryRule() {
        val standard = site("standard-site", "Standard site", CustomerType.STANDARD)
        val oneTime = site("one-time-site", "Unrelated one-time site", CustomerType.ONE_TIME)
        showNewVisit(listOf(standard, oneTime), emptyList())
        compose.onNodeWithTag("visit-site-standard-site-select").performClick()
        compose.onNodeWithTag("visit-site-continue").performClick()
        compose.onNodeWithTag("new-visit-form").performScrollToNode(hasTestTag("field-task-name-required"))
        compose.onNodeWithTag("field-task-name-required").performTextInput("Draft task")
        compose.onNodeWithTag("add-task").performClick()
        compose.onNodeWithTag("visit-change-customer-site").performClick()
        compose.onNodeWithTag("visit-site-standard-site-select").assertIsSelected()
        compose.onNodeWithText("Unrelated one-time site").assertDoesNotExist()
        compose.onNodeWithText("Keep current").assertDoesNotExist()
        compose.onNodeWithTag("visit-site-continue").assertIsEnabled()
        compose.onNodeWithText("Change customer or site?").assertDoesNotExist()
        compose.onNodeWithTag("visit-site-continue").performClick()
        compose.onNodeWithTag("visit-task-0").assertIsDisplayed()
        compose.onNodeWithTag("visit-change-customer-site").performClick()
        assertAbsentText("Unrelated one-time site")
        compose.onNodeWithTag("field-find-customer-or-site").performTextInput("Unrelated one-time")
        compose.onNodeWithTag("visit-site-one-time-site").assertIsDisplayed()
        compose.onNodeWithTag("visit-site-one-time-site-select").performClick()
        compose.onNodeWithTag("visit-site-continue").performClick()
        compose.onNodeWithText("Change customer or site?").assertIsDisplayed()
        compose.onNodeWithText("Planned and ad-hoc work added for this site will be cleared.").assertIsDisplayed()
        compose.onNodeWithText("Keep current").performClick()
        compose.onNodeWithTag("visit-task-0").assertIsDisplayed()
        compose.onNodeWithTag("visit-change-customer-site").performClick()
        compose.onNodeWithTag("field-find-customer-or-site").performTextInput("Unrelated one-time")
        compose.onNodeWithTag("visit-site-one-time-site-select").performClick()
        compose.onNodeWithTag("visit-site-continue").performClick()
        compose.onNodeWithText("Change").performClick()
        compose.onNodeWithTag("visit-task-0").assertDoesNotExist()
    }

    @Test fun existingOneTimeVisitCanAddKnownRegisteredEquipment() {
        val registered = equipment("one-time-equipment", "Registered one-time equipment")
        val site = site("one-time-site", "One-time site", CustomerType.ONE_TIME, listOf(registered))
        val visit = visit(site, "visit-one-time")
        val repository = FakeRepository(site = site.toDetail(), visit = visit)
        val viewModel = ServiceLoopViewModel(repository) {}
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    val state by viewModel.state.collectAsState()
                    LaunchedEffect(Unit) { viewModel.loadVisit(visit.id) }
                    VisitDetailScreen(detail = state.visit ?: visit, padding = PaddingValues(), state = state.copy(site = state.site ?: site.toDetail()), viewModel = viewModel, nav = rememberNavController())
                }
            }
        }
        compose.onNodeWithTag("visit-task-subject-EQUIPMENT").performClick()
        compose.onNodeWithTag("visit-task-equipment-null").assertIsDisplayed()
        compose.onNodeWithTag("visit-task-equipment-one-time-equipment").assertIsDisplayed()
        compose.onNodeWithText("Registered one-time equipment", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("field-task-name-required").performTextInput("Inspect registered unit")
        compose.onNodeWithTag("visit-task-equipment-one-time-equipment").performClick()
        compose.onNodeWithTag("add-visit-task").assertIsEnabled().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("visit-line-known-equipment").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Registered one-time equipment", substring = true).assertIsDisplayed()
        assertEquals("known-equipment", repository.lastAddedWorkItemId)
    }

    @Test fun oneTimeCustomerDetailHasNoSpecialPromotionActionAndKeepsEdit() {
        val repository = FakeRepository()
        val viewModel = ServiceLoopViewModel(repository) {}
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    val state by viewModel.state.collectAsState()
                    LaunchedEffect(Unit) { viewModel.loadCustomer("one-time-customer") }
                    CustomerDetailScreen(state.customer ?: repository.customerDetail, PaddingValues(), rememberNavController(), viewModel)
                }
            }
        }
        assertAbsentTag("make-standard-customer")
        compose.onNodeWithText("Edit").assertIsDisplayed()
        compose.onNodeWithTag("one-time-customer-label").assertIsDisplayed()
    }

    @Test fun oneTimeEquipmentHasNoSpecialPromotionAction() {
        val repository = FakeRepository()
        val viewModel = ServiceLoopViewModel(repository) {}
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    val nav = rememberNavController()
                    NavHost(nav, "equipment") {
                        composable("equipment") { EquipmentScreen(repository.equipmentDetail, nav, LocalDate.of(2026, 9, 5), 14, viewModel) }
                        composable("plan/new/one-time-equipment") { androidx.compose.material3.Text("Add service plan") }
                    }
                }
            }
        }
        assertAbsentTag("make-standard-and-add-plan")
        compose.onNodeWithText("Recurring service requires a Standard customer.").assertIsDisplayed()
        compose.onNodeWithTag("equipment-actions").assertIsDisplayed()
        val equipmentInfo = compose.onNodeWithText("Promoted customer\nOne-time site", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val equipmentCustomer = compose.onNodeWithTag("equipment-customer-link").fetchSemanticsNode().boundsInRoot
        assertTrue("Equipment relationship actions need separation from the info block", equipmentCustomer.top - equipmentInfo.bottom >= 8f * compose.activity.resources.displayMetrics.density - 1f)
        captureRendered("b032-equipment-top-actions.png")
    }

    @Test fun linkExistingEquipmentReturnsToSameServiceWithTheSameWorkItem() {
        val repository = FakeRepository()
        val viewModel = ServiceLoopViewModel(repository) {}
        renderServiceAndLink(repository, viewModel)
        compose.onNodeWithTag("link-equipment").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("link-equipment-known-equipment").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("link-equipment-known-equipment").performClick()
        compose.waitUntil(10_000) { viewModel.state.value.inspection?.equipmentName == "Known equipment" }
        compose.waitForIdle()
        compose.onNodeWithTag("service-identity").assert(hasAnyDescendant(hasText("Known equipment", substring = true)))
        assertEquals("working-unidentified", repository.lastInspectedWorkItemId)
        assertEquals("known-equipment", repository.inspectedDraft?.equipmentId)
    }

    @Test fun createAndLinkEquipmentReturnsToSameServiceWithoutCreatingAPlan() {
        val repository = FakeRepository()
        val viewModel = ServiceLoopViewModel(repository) {}
        renderServiceAndLink(repository, viewModel)
        compose.onNodeWithTag("link-equipment").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("link-add-equipment").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("link-add-equipment").performClick()
        compose.onNodeWithTag("field-equipment-name-required").performTextInput("Created equipment")
        compose.onNodeWithTag("save-and-link-equipment").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(10_000) { repository.createdEquipment && repository.inspectedDraft?.equipmentId == "created-equipment" }
        compose.waitUntil(10_000) { viewModel.state.value.inspection?.equipmentName == "Created equipment" }
        compose.waitForIdle()
        compose.onNodeWithTag("service-identity").assert(hasAnyDescendant(hasText("Created equipment", substring = true)))
        assertTrue(repository.createdEquipment)
        assertTrue(repository.createdEquipmentPlanCount == 0)
    }

    private fun showRegister() {
        val standardCustomer = CustomerSummary("standard-customer", "Standard customer", "CU-STD", 1, 1, CustomerType.STANDARD)
        val oneTimeCustomer = CustomerSummary("one-time-customer", "One-time customer", "CU-OT", 1, 1, CustomerType.ONE_TIME)
        val standardSite = SiteRegisterSummary("standard-site", "ST-STD", "Standard site", "Standard customer", "", 1, CustomerType.STANDARD)
        val oneTimeSite = SiteRegisterSummary("one-time-site", "ST-OT", "One-time site", "One-time customer", "", 1, CustomerType.ONE_TIME)
        val standardEquipment = equipment("standard-equipment", "Standard equipment")
        val oneTimeEquipment = equipment("one-time-equipment", "One-time equipment").copy(customerType = CustomerType.ONE_TIME)
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    CustomersScreen(listOf(standardCustomer, oneTimeCustomer), listOf(standardSite, oneTimeSite), listOf(standardEquipment, oneTimeEquipment), rememberNavController())
                }
            }
        }
    }

    private fun showSearch(results: List<SearchTarget> = emptyList()) {
        val viewModel = ServiceLoopViewModel(FakeRepository()) {}
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    val nav = rememberNavController()
                    NavHost(nav, "search") {
                        composable("search") { SearchScreen(results, PaddingValues(), viewModel, nav) }
                        composable("customer/{id}") { Text("Customer destination") }
                    }
                }
            }
        }
    }

    private fun showNewVisit(sites: List<VisitSiteOption>, due: List<DueService>, state: UiState = UiState(loading = false, dueServicesProjection = DueServicesProjection.Available(due), businessDate = LocalDate.of(2026, 9, 5), businessZoneId = "Europe/Bucharest"), initialPlanIds: List<String> = emptyList()) {
        val viewModel = ServiceLoopViewModel(FakeRepository()) {}
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    CompositionLocalProvider(LocalDetailBackInterceptor provides remember { mutableStateOf<(() -> Unit)?>(null) }) {
                        NewVisitScreen(sites, due, PaddingValues(), state, viewModel, rememberNavController(), initialPlanIds)
                    }
                }
            }
        }
    }

    private fun renderServiceAndLink(repository: FakeRepository, viewModel: ServiceLoopViewModel) {
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    val nav = rememberNavController()
                    val state by viewModel.state.collectAsState()
                    LaunchedEffect(Unit) { viewModel.loadInspection(repository.inspectedDraft!!.workItemId) }
                    NavHost(nav, "service") {
                        composable("service") { TestServiceRoute(repository, viewModel, nav) }
                        composable("work/{workItemId}/link-equipment") { entry ->
                            val id = entry.arguments?.getString("workItemId").orEmpty()
                            LaunchedEffect(id) { viewModel.loadEquipmentLinkContext(id) }
                            EquipmentLinkScreen(id, state.equipmentLinkContext, PaddingValues(), state, viewModel, nav)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun TestServiceRoute(repository: FakeRepository, viewModel: ServiceLoopViewModel, nav: androidx.navigation.NavHostController) {
        val state by viewModel.state.collectAsState()
        val draft = state.inspection ?: repository.inspectedDraft ?: return
        key(draft.equipmentId) { InspectionScreen(draft, state.saveStatus, state.inspectionFocus, viewModel, nav) }
    }

    private fun assertAbsentText(text: String) = assertTrue(compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty())
    private fun assertAbsentTag(tag: String) = assertTrue(compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty())

    private fun captureRendered(name: String) {
        try {
            compose.waitForIdle()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.waitForIdleSync()
            Thread.sleep(500)
            val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "b030-rendered")
            check(directory.exists() || directory.mkdirs())
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            FileOutputStream(File(directory, name)).use { check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            screenshot.recycle()
        } catch (failure: Throwable) {
            android.util.Log.w("ServiceLoopRenderEvidence", "Best-effort artifact capture failed for $name", failure)
        }
    }

    private fun equipment(id: String, name: String) = EquipmentSummary(id, name, "EQ-$id", "ID-$id", "Site", "Customer", null, CustomerType.STANDARD)
    private fun site(id: String, name: String, type: CustomerType, equipment: List<EquipmentSummary> = emptyList()) = VisitSiteOption(id, "ST-$id", name, "Customer $id", equipment, type)
    private fun VisitSiteOption.toDetail() = SiteDetail(id, "customer-$id", customerName, reference, name, "", "", "", "", "", true, equipment, customerType = customerType)
    private fun visit(site: VisitSiteOption, id: String) = VisitDetail(id, "V-$id", "WORKING", "customer-${site.id}", site.customerName, site.id, site.name, "", "2026-09-05", null, null, emptyList(), null, customerType = site.customerType)
    private fun due(site: VisitSiteOption, planId: String) = DueService(planId, "P-$planId", "Planned service", "2026-09-05", "obligation-$planId", site.equipment.firstOrNull()?.id ?: "equipment", site.equipment.firstOrNull()?.reference ?: "EQ", site.equipment.firstOrNull()?.name ?: "Equipment", site.id, site.name, "customer-${site.id}", site.customerName, null, DueBucket.TODAY)

    private class FakeRepository(
        private val site: SiteDetail? = null,
        private var visit: VisitDetail? = null,
    ) : ServiceLoopRepository {
        var customerDetail = CustomerDetail("one-time-customer", "CU-OT", "Promoted customer", "", "", "", "", emptyList(), emptyList(), emptyList(), emptyList(), customerType = CustomerType.ONE_TIME)
        var equipmentDetail = EquipmentDetail("one-time-equipment", "One-time equipment", "EQ-OT", "ID-OT", "Maker Model", "SN-OT", "One-time site", "Promoted customer", emptyList(), null, "Maker", "Model", customerType = CustomerType.ONE_TIME, siteId = "one-time-site")
        var inspectedDraft: InspectionDraft? = InspectionDraft("working-unidentified", "visit-link", "V-LINK", "Site", null, null, "Identify equipment", null, null, null, "", "", false, null, null, 0L, emptyList(), subjectType = WorkSubjectType.EQUIPMENT, equipmentDescription = "Unknown equipment")
        var linkContext = EquipmentLinkContext("working-unidentified", "visit-link", "site-link", "Site", listOf(EquipmentSummary("known-equipment", "Known equipment", "EQ-KNOWN", "ID-KNOWN", "Site", "Customer", null, CustomerType.STANDARD)))
        var lastAddedWorkItemId: String? = null
        var lastInspectedWorkItemId: String? = null
        var createdEquipment = false
        var createdEquipmentPlanCount = 0
        var workingProgress: VisitServiceProgress? = null
        @Volatile var createdVisitId: String? = null
        @Volatile var createdCustomerType: CustomerType? = null
        var templateDetail = TemplateDetail("template-state", "IT-STATE", "State template", 2, "ACTIVE", listOf(TemplateItemDraft("Guard", "STATUS", required = true)))

        override suspend fun home() = HomeSummary(null, null, null, null, null, null, null, 0, null, null, 0, 0)
        override suspend fun equipment(id: String) = equipmentDetail
        override suspend fun equipmentList() = listOf(EquipmentSummary(equipmentDetail.id, equipmentDetail.name, equipmentDetail.reference, equipmentDetail.technicianIdentifier, equipmentDetail.siteName, equipmentDetail.customerName, null, customerDetail.customerType))
        override suspend fun customerList() = listOf(customerSummary())
        override suspend fun inspection(workItemId: String) = inspectedDraft?.also { lastInspectedWorkItemId = it.workItemId }
        override suspend fun completionLines(visitId: String) = emptyList<CompletionLine>()
        override suspend fun saveResponse(workItemId: String, questionId: String, disposition: ResponseDisposition, value: String?, reason: String?) = 0L
        override suspend fun customer(id: String) = customerDetail
        override suspend fun site(id: String) = site
        override suspend fun visit(id: String) = visit
        override suspend fun serviceVisitProgress(visitId: String) = workingProgress ?: error("No progress")
        override suspend fun templates() = emptyList<TemplateSummary>()
        override suspend fun template(id: String) = templateDetail.takeIf { it.id == id }
        override suspend fun templateServicePlanReferenceCount(id: String) = 0
        override suspend fun setTemplateState(id: String, state: String): Long {
            templateDetail = templateDetail.copy(state = state)
            return 1L
        }
        override suspend fun equipmentLinkContext(workItemId: String) = linkContext
        override suspend fun linkWorkItemEquipment(workItemId: String, equipmentId: String): Long {
            inspectedDraft = inspectedDraft?.copy(equipmentId = equipmentId, equipmentName = "Known equipment", equipmentReference = "EQ-KNOWN", equipmentDescription = null)
            return 1L
        }
        override suspend fun createAndLinkEquipment(workItemId: String, input: EquipmentInput): String {
            createdEquipment = true
            inspectedDraft = inspectedDraft?.copy(equipmentId = "created-equipment", equipmentName = input.name, equipmentReference = "EQ-CREATED", equipmentDescription = null)
            return "created-equipment"
        }
        override suspend fun addAdHocWork(visitId: String, input: AdHocWorkInput): String {
            lastAddedWorkItemId = "known-equipment"
            visit = visit?.copy(lines = visit!!.lines + VisitLine("known-equipment", "Registered one-time equipment", "EQ-one-time-equipment", input.taskName, null, null, WorkSubjectType.EQUIPMENT, "one-time-equipment", null))
            return "known-equipment"
        }
        override suspend fun createVisitForSite(siteId: String, planIds: List<String>, adHocWork: List<AdHocWorkInput>, state: String, serviceDate: String, scheduledAtEpochMillis: Long?): String {
            createdVisitId = "created-working-visit"
            return createdVisitId!!
        }
        override suspend fun createNewCustomerVisit(input: NewCustomerVisitInput, adHocWork: List<AdHocWorkInput>, state: String, serviceDate: String, scheduledAtEpochMillis: Long?): String {
            createdCustomerType = input.customerType
            createdVisitId = "created-new-customer-visit"
            return createdVisitId!!
        }
        override suspend fun makeCustomerStandard(customerId: String): Long {
            customerDetail = customerDetail.copy(customerType = CustomerType.STANDARD)
            equipmentDetail = equipmentDetail.copy(customerType = CustomerType.STANDARD)
            return 1L
        }
        fun customerSummary() = CustomerSummary(customerDetail.id, customerDetail.name, customerDetail.reference, 0, 1, customerDetail.customerType)
    }
}
