package com.v16studio.serviceloop.ui

import android.content.Intent
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.v16studio.serviceloop.domain.ReminderPreferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.v16studio.serviceloop.domain.CompletionLine
import com.v16studio.serviceloop.domain.CustomerSummary
import com.v16studio.serviceloop.domain.CustomerType
import com.v16studio.serviceloop.domain.EquipmentDetail
import com.v16studio.serviceloop.domain.EquipmentSummary
import com.v16studio.serviceloop.domain.FulfillmentEligibility
import com.v16studio.serviceloop.domain.HomeSummary
import com.v16studio.serviceloop.domain.InspectionDraft
import com.v16studio.serviceloop.domain.InspectionQuestion
import com.v16studio.serviceloop.domain.ResponseDisposition
import com.v16studio.serviceloop.domain.SaveStatus
import com.v16studio.serviceloop.domain.ServiceDraftFieldKeys
import com.v16studio.serviceloop.domain.BusinessProfile
import com.v16studio.serviceloop.domain.FinalRecordDetail
import com.v16studio.serviceloop.domain.RecordVersionSummary
import com.v16studio.serviceloop.domain.ReportVersionSummary
import com.v16studio.serviceloop.domain.PublicPhoto
import com.v16studio.serviceloop.domain.VisitSummary
import com.v16studio.serviceloop.domain.SiteRegisterSummary
import com.v16studio.serviceloop.domain.CompletionBlockerKind
import com.v16studio.serviceloop.domain.FollowUpDateFilter
import com.v16studio.serviceloop.domain.FollowUpStatusFilter
import com.v16studio.serviceloop.domain.VisitDateFilter
import com.v16studio.serviceloop.domain.VisitStatusFilter
import com.v16studio.serviceloop.domain.WorkSubjectType
import com.v16studio.serviceloop.domain.filterFollowUps
import com.v16studio.serviceloop.domain.filterVisits
import com.v16studio.serviceloop.ui.theme.LocalServiceLoopColors
import com.v16studio.serviceloop.ui.theme.AppearanceMode
import com.v16studio.serviceloop.ui.theme.AppearancePreferences
import com.v16studio.serviceloop.ui.designsystem.LocalServiceLoopTokens
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrivateLabel
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopLongTextEditor
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopNotice
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopNoticeKind
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSurfaceCard
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrimaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSecondaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopBrandStrip
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopDetailToolbar
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopContentTabs
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopChoiceGroup
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPresetChoiceGroup
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSelectionOption
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopDayToggle
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopFilterSelector
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopFilterSelectorRow
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSavedStatus
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopEntityRecord
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSectionDivider
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopActionStack
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextField
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopDenseNavigableRow
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopDashboardGateway
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopAttentionRow
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopVersionRow
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopIconAction
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopButtonAdapter as Button
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopOutlinedButtonAdapter as OutlinedButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextButtonAdapter as TextButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextFieldAdapter as OutlinedTextField
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopCardAdapter as Card
import com.v16studio.serviceloop.ui.designsystem.serviceLoopAdaptiveScaffoldPadding
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcon
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcons
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun HomeScreen(state: UiState, home: HomeSummary?, equipment: List<EquipmentSummary>, visits: List<VisitSummary>, attention: List<com.v16studio.serviceloop.domain.AttentionItem>, nav: NavHostController, viewModel: ServiceLoopViewModel) {
    LaunchedEffect(Unit) { viewModel.loadAttention() }
    if (home == null) { LazyColumn(contentPadding = PaddingValues(16.dp)) { item { CoordinatorHomeActions(nav) }; item { Text("Add a customer to create your first service obligation.") } }; return }
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { CoordinatorHomeActions(nav) }
        if (home.workingVisitId != null) item {
            SectionTitle("Unfinished visits · ${home.workingVisitCount}")
            AccentCard {
                Text(home.workingSite.orEmpty(), style = MaterialTheme.typography.titleMedium)
                Text("${home.workingVisitReference} · Working", color = LocalServiceLoopColors.current.workflowInk)
                home.savedAtEpochMillis?.let { ServiceLoopSavedStatus(it, iconSize = ServiceLoopUiTokens.Size.iconSmall) }
                Button(onClick = {
                    val sessionItem = state.activeServiceWorkItemId?.takeIf { state.activeServiceVisitId == home.workingVisitId && state.serviceProgress?.items?.any { item -> item.workItemId == it } == true }
                    val target = sessionItem ?: home.inspectionWorkItemId
                    if (target != null) nav.navigate("inspection/$target") else nav.navigate("visit/${home.workingVisitId}")
                }, modifier = Modifier.fillMaxWidth().testTag("resume-service")) { Text("Resume service") }
                TextButton(onClick = { nav.navigate(workRoute(WorkTab.VISITS, "WORKING")) }) { Text("View all unfinished") }
            }
        }
        item {
            SectionTitle("Booked visits · ${home.bookedVisitCount}")
            val bookedVisit = visits.firstOrNull { it.state == "BOOKED" && it.reference == home.bookedVisitReference }
            if (bookedVisit == null) Text("No booked visits")
            else ServiceLoopEntityRecord(bookedVisit.reference, bookedVisit.siteName, bookedVisit.actualServiceDate, bookedVisit.state) { nav.navigate("visit/${bookedVisit.id}") }
        }
        item { ServiceLoopDashboardGateway("Overdue services",home.overdueCount,"Booked work remains due until fulfilled",ServiceLoopIcons.Warning,true){nav.navigate(workRoute(WorkTab.DUE_SERVICES, "OVERDUE"))} }
        items(equipment.take(3)) { item -> ServiceLoopEntityRecord("${item.technicianIdentifier ?: item.reference} · ${item.name}",metadata="Due ${item.nearestDueDate ?: "not scheduled"}"){nav.navigate("equipment/${item.id}")} }
        item { ServiceLoopDashboardGateway("Due soon",home.dueSoonCount,"Next ${home.dueSoonHorizonDays} business-local days",ServiceLoopIcons.Time){nav.navigate(workRoute(WorkTab.DUE_SERVICES, "DUE_SOON"))} }
        item { ServiceLoopDashboardGateway("Follow-ups due",home.dueFollowUpCount,listOfNotNull(home.dueFollowUpReference, home.dueFollowUpTitle).joinToString(" · ").ifBlank { "No follow-ups due" },ServiceLoopIcons.Work){nav.navigate(workRoute(WorkTab.FOLLOW_UPS))} }
        item { SectionTitle("Records needing attention · ${attention.size}"); if(attention.isEmpty()) Text("No correction or report-file attention needed.") }
        items(attention) { item -> ServiceLoopAttentionRow(item.title,item.detail){nav.navigate(item.route)} }
        item { Button(onClick = { nav.navigate("visit/new") }, modifier = Modifier.fillMaxWidth().testTag("new-visit-home")) { Text("New visit") } }
    }
}

@Composable
internal fun WorkScreen(state: UiState, nav: NavHostController, tab: WorkTab, viewModel: ServiceLoopViewModel, contextualFilter: String? = null, onTabSelected: (WorkTab) -> Unit) {
    val initialVisitStatus = VisitStatusFilter.entries.firstOrNull { it.name == contextualFilter } ?: VisitStatusFilter.ALL
    val initialVisitDate = VisitDateFilter.entries.firstOrNull { it.name == contextualFilter }
        ?: if (initialVisitStatus == VisitStatusFilter.ALL) VisitDateFilter.TODAY else VisitDateFilter.ALL
    val initialFollowUpDate = FollowUpDateFilter.entries.firstOrNull { it.name == contextualFilter } ?: FollowUpDateFilter.ALL
    val colors = LocalServiceLoopTokens.current
    val compactRootActions = LocalDensity.current.fontScale >= ServiceLoopUiTokens.Layout.badgeFontScaleStackThreshold ||
        LocalConfiguration.current.screenWidthDp.dp < ServiceLoopUiTokens.Size.narrowThreshold
    val inlineNewVisit: (@Composable () -> Unit)? = if (compactRootActions) {
        {
            ServiceLoopPrimaryButton(
                "New visit",
                { nav.navigate("visit/new") },
                Modifier.fillMaxWidth().testTag("new-visit-work"),
                leadingIcon = { ServiceLoopIcon(ServiceLoopIcons.Add, null, Modifier.size(ServiceLoopUiTokens.Size.icon)) },
            )
        }
    } else null
    Column(Modifier.fillMaxSize().background(colors.canvas)) {
        Box(Modifier.fillMaxWidth().background(colors.surface).padding(top = 12.dp)) {
            ServiceLoopContentTabs(WorkTab.entries.map { it to it.label }, tab, onTabSelected)
        }
        when (tab) {
            WorkTab.DUE_SERVICES -> {
                val contextualDueBucket = runCatching { com.v16studio.serviceloop.domain.DueBucket.valueOf(contextualFilter.orEmpty()) }.getOrNull()
                DueServicesScreen(state.dueServices, PaddingValues(), state, viewModel, nav, Modifier.weight(1f), contextualDueBucket, inlineNewVisit)
            }
            WorkTab.VISITS -> VisitsWorkScreen(
                values = state.visits,
                businessDate = state.businessDate,
                padding = PaddingValues(),
                nav = nav,
                modifier = Modifier.weight(1f),
                initialDateFilter = initialVisitDate,
                initialStatusFilter = initialVisitStatus,
                topContent = inlineNewVisit,
            )
            WorkTab.FOLLOW_UPS -> FollowUpsWorkScreen(
                values = state.followUps,
                businessDate = state.businessDate,
                padding = PaddingValues(),
                nav = nav,
                modifier = Modifier.weight(1f),
                initialDateFilter = initialFollowUpDate,
                topContent = inlineNewVisit,
            )
        }
    }
}

@Composable
internal fun VisitsWorkScreen(
    values: List<VisitSummary>,
    businessDate: LocalDate,
    padding: PaddingValues,
    nav: NavHostController,
    modifier: Modifier = Modifier,
    initialDateFilter: VisitDateFilter = VisitDateFilter.TODAY,
    initialStatusFilter: VisitStatusFilter = VisitStatusFilter.ALL,
    topContent: (@Composable () -> Unit)? = null,
) {
    var dateFilter by rememberSaveable(initialDateFilter) { mutableStateOf(initialDateFilter) }
    var statusFilter by rememberSaveable(initialStatusFilter) { mutableStateOf(initialStatusFilter) }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = filterVisits(values, dateFilter, statusFilter, businessDate, query)
    LazyColumn(
        modifier.padding(padding).testTag("work-visits-list"),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        topContent?.let { action -> item { action() } }
        item {
            ServiceLoopTextField(query, { query = it }, "Search visits", modifier = Modifier.testTag("visit-search"))
            Spacer(Modifier.height(ServiceLoopUiTokens.Space.lg))
            ServiceLoopFilterSelectorRow(
                first = {
                    ServiceLoopFilterSelector(
                        label = "Date",
                        selected = dateFilter,
                        options = VisitDateFilter.entries.map { it to it.label },
                        onSelected = { dateFilter = it },
                        testTag = "visit-date-selector",
                    )
                },
                second = {
                    ServiceLoopFilterSelector(
                        label = "Status",
                        selected = statusFilter,
                        options = VisitStatusFilter.entries.map { it to it.label },
                        onSelected = { statusFilter = it },
                        testTag = "visit-status-selector",
                    )
                },
            )
        }
        if (filtered.isEmpty()) item { Text("No visits match these filters.") }
        items(filtered) { visit ->
            ServiceLoopEntityRecord(visit.reference, visit.siteName, visit.actualServiceDate, visit.state) {
                if (visit.finalRecordId != null) nav.navigate("record/${visit.finalRecordId}") else nav.navigate("visit/${visit.id}")
            }
        }
    }
}

@Composable
internal fun FollowUpsWorkScreen(
    values: List<com.v16studio.serviceloop.domain.FollowUpDetail>,
    businessDate: LocalDate,
    padding: PaddingValues,
    nav: NavHostController,
    modifier: Modifier = Modifier,
    initialDateFilter: FollowUpDateFilter = FollowUpDateFilter.ALL,
    initialStatusFilter: FollowUpStatusFilter = FollowUpStatusFilter.OPEN,
    topContent: (@Composable () -> Unit)? = null,
) {
    var dateFilter by rememberSaveable(initialDateFilter) { mutableStateOf(initialDateFilter) }
    var statusFilter by rememberSaveable(initialStatusFilter) { mutableStateOf(initialStatusFilter) }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = filterFollowUps(values, dateFilter, statusFilter, businessDate, query)
    LazyColumn(
        modifier.padding(padding).testTag("work-follow-ups-list"),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        topContent?.let { action -> item { action() } }
        item {
            ServiceLoopTextField(query, { query = it }, "Search follow-ups", modifier = Modifier.testTag("follow-up-search"))
            Spacer(Modifier.height(ServiceLoopUiTokens.Space.lg))
            ServiceLoopFilterSelectorRow(
                first = {
                    ServiceLoopFilterSelector(
                        label = "Due date",
                        selected = dateFilter,
                        options = FollowUpDateFilter.entries.map { it to it.label },
                        onSelected = { dateFilter = it },
                        testTag = "follow-up-date-selector",
                    )
                },
                second = {
                    ServiceLoopFilterSelector(
                        label = "Status",
                        selected = statusFilter,
                        options = FollowUpStatusFilter.entries.map { it to it.label },
                        onSelected = { statusFilter = it },
                        testTag = "follow-up-status-selector",
                    )
                },
            )
        }
        if (filtered.isEmpty()) item { Text("No follow-ups match these filters.") }
        items(filtered) { follow ->
            ServiceLoopEntityRecord(
                "${follow.reference} · ${follow.title}",
                listOfNotNull(follow.customerName, follow.siteName, follow.equipmentName).filter { it.isNotBlank() }.joinToString(" · "),
                "Due ${follow.dueDate}",
                follow.state,
            ) { nav.navigate("follow-up/${follow.id}") }
        }
    }
}
