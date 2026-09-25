package com.v16studio.v16service.ui

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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import com.v16studio.v16service.ui.designsystem.V16ServiceCheckbox as Checkbox
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
import com.v16studio.v16service.domain.ReminderPreferences
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
import com.v16studio.v16service.domain.CompletionLine
import com.v16studio.v16service.domain.CustomerSummary
import com.v16studio.v16service.domain.CustomerType
import com.v16studio.v16service.domain.EquipmentDetail
import com.v16studio.v16service.domain.EquipmentSummary
import com.v16studio.v16service.domain.FulfillmentEligibility
import com.v16studio.v16service.domain.HomeSummary
import com.v16studio.v16service.domain.InspectionDraft
import com.v16studio.v16service.domain.InspectionQuestion
import com.v16studio.v16service.domain.ResponseDisposition
import com.v16studio.v16service.domain.SaveStatus
import com.v16studio.v16service.domain.ServiceDraftFieldKeys
import com.v16studio.v16service.domain.BusinessProfile
import com.v16studio.v16service.domain.FinalRecordDetail
import com.v16studio.v16service.domain.RecordVersionSummary
import com.v16studio.v16service.domain.ReportVersionSummary
import com.v16studio.v16service.domain.PublicPhoto
import com.v16studio.v16service.domain.VisitSummary
import com.v16studio.v16service.domain.SiteRegisterSummary
import com.v16studio.v16service.domain.TemplateSummary
import com.v16studio.v16service.domain.CompletionBlockerKind
import com.v16studio.v16service.domain.FollowUpDateFilter
import com.v16studio.v16service.domain.FollowUpStatusFilter
import com.v16studio.v16service.domain.VisitDateFilter
import com.v16studio.v16service.domain.VisitStatusFilter
import com.v16studio.v16service.domain.WorkSubjectType
import com.v16studio.v16service.domain.WorkScope
import com.v16studio.v16service.domain.filterFollowUps
import com.v16studio.v16service.domain.filterVisits
import com.v16studio.v16service.ui.theme.LocalV16ServiceColors
import com.v16studio.v16service.ui.theme.AppearanceMode
import com.v16studio.v16service.ui.theme.AppearancePreferences
import com.v16studio.v16service.ui.designsystem.LocalV16ServiceTokens
import com.v16studio.v16service.ui.designsystem.V16ServicePrivateLabel
import com.v16studio.v16service.ui.designsystem.V16ServiceLongTextEditor
import com.v16studio.v16service.ui.designsystem.V16ServiceNotice
import com.v16studio.v16service.ui.designsystem.V16ServiceNoticeKind
import com.v16studio.v16service.ui.designsystem.V16ServiceSurfaceCard
import com.v16studio.v16service.ui.designsystem.V16ServiceUiTokens
import com.v16studio.v16service.ui.designsystem.V16ServicePrimaryButton
import com.v16studio.v16service.ui.designsystem.V16ServiceSecondaryButton
import com.v16studio.v16service.ui.designsystem.V16ServiceNavigationButton
import com.v16studio.v16service.ui.designsystem.V16ServiceBrandStrip
import com.v16studio.v16service.ui.designsystem.V16ServiceDetailToolbar
import com.v16studio.v16service.ui.designsystem.V16ServiceContentTabs
import com.v16studio.v16service.ui.designsystem.V16ServiceRootSecondaryTabs
import com.v16studio.v16service.ui.designsystem.V16ServiceChoiceGroup
import com.v16studio.v16service.ui.designsystem.V16ServicePresetChoiceGroup
import com.v16studio.v16service.ui.designsystem.V16ServiceSelectionOption
import com.v16studio.v16service.ui.designsystem.V16ServiceDayToggle
import com.v16studio.v16service.ui.designsystem.V16ServiceFilterSelector
import com.v16studio.v16service.ui.designsystem.V16ServiceFilterSelectorRow
import com.v16studio.v16service.ui.designsystem.V16ServiceSavedStatus
import com.v16studio.v16service.ui.designsystem.V16ServiceEntityRecord
import com.v16studio.v16service.ui.designsystem.V16ServiceSectionDivider
import com.v16studio.v16service.ui.designsystem.V16ServiceActionStack
import com.v16studio.v16service.ui.designsystem.V16ServiceTextField
import com.v16studio.v16service.ui.designsystem.V16ServiceDenseNavigableRow
import com.v16studio.v16service.ui.designsystem.V16ServiceDashboardGateway
import com.v16studio.v16service.ui.designsystem.V16ServiceAttentionRow
import com.v16studio.v16service.ui.designsystem.V16ServiceVersionRow
import com.v16studio.v16service.ui.designsystem.V16ServiceIconAction
import com.v16studio.v16service.ui.designsystem.V16ServiceButtonAdapter as Button
import com.v16studio.v16service.ui.designsystem.V16ServiceOutlinedButtonAdapter as OutlinedButton
import com.v16studio.v16service.ui.designsystem.V16ServiceTextButtonAdapter as TextButton
import com.v16studio.v16service.ui.designsystem.V16ServiceTextFieldAdapter as OutlinedTextField
import com.v16studio.v16service.ui.designsystem.V16ServiceCardAdapter as Card
import com.v16studio.v16service.ui.designsystem.v16ServiceAdaptiveScaffoldPadding
import com.v16studio.v16service.ui.icons.V16ServiceIcon
import com.v16studio.v16service.ui.icons.V16ServiceIcons
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.net.URLEncoder
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun CustomersScreen(
    customers: List<CustomerSummary>,
    sites: List<SiteRegisterSummary>,
    equipment: List<EquipmentSummary>,
    nav: NavHostController,
    templates: List<TemplateSummary> = emptyList(),
    viewModel: V16ServiceViewModel? = null,
) {
    var tab by rememberSaveable { mutableStateOf("CUSTOMERS") }
    val context = LocalContext.current
    val filterPreferences = remember(context) { UiFilterPreferences(context) }
    var showOneTime by rememberSaveable { mutableStateOf(filterPreferences.showOneTimeCustomers()) }
    val colors = LocalV16ServiceTokens.current
    val visibleCustomers = customers.filter { showOneTime || it.customerType == CustomerType.STANDARD }
    val visibleSites = sites.filter { showOneTime || it.customerType == CustomerType.STANDARD }
    val visibleEquipment = equipment.filter { showOneTime || it.customerType == CustomerType.STANDARD }
    LaunchedEffect(tab) { if (tab == "TEMPLATES") viewModel?.loadTemplates() }
    LazyColumn(contentPadding = PaddingValues(0.dp, 0.dp, 0.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { V16ServiceRootSecondaryTabs(listOf("CUSTOMERS" to "Customers", "SITES" to "Sites", "EQUIPMENT" to "Equipment", "TEMPLATES" to "Templates"), tab, { tab = it }) }
        item { Text(if (tab == "TEMPLATES") "Inspection templates" else "${tab.lowercase().replaceFirstChar { it.uppercase() }} register", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp, top = V16ServiceUiTokens.Space.section, end = 16.dp)) }
        if (tab != "TEMPLATES") item { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(showOneTime, { showOneTime = it; filterPreferences.saveShowOneTimeCustomers(it) }, modifier = Modifier.testTag("show-one-time-customers")); Text("Show one-time customers") } }
        item {
            when (tab) {
                "CUSTOMERS" -> V16ServicePrimaryButton("Add customer",{ nav.navigate("customer/new") },Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("add-customer"))
                "EQUIPMENT" -> V16ServicePrimaryButton("Add equipment",{ nav.navigate("equipment/select-site") },Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("add-equipment-from-register"))
                "TEMPLATES" -> InspectionTemplateLibraryContent(templates, PaddingValues(horizontal = 16.dp), nav, viewModel, showHeading = false, createReturnTo = "customers")
                else -> Unit
            }
        }
        when (tab) {
            "CUSTOMERS" -> {
                if (visibleCustomers.isEmpty()) item { Text(if (customers.isEmpty()) "Add a customer to begin." else "No standard customers match this filter.", Modifier.padding(horizontal = 16.dp)) }
                items(visibleCustomers) { item ->
                    val metadata = buildString { if (item.customerType == CustomerType.ONE_TIME) append("One-time · "); append("${item.siteCount} site · ${item.equipmentCount} equipment") }
                    V16ServiceEntityRecord(item.name, item.reference, metadata, modifier = Modifier.padding(horizontal = 16.dp)) { nav.navigate("customer/${item.id}") }
                }
            }
            "SITES" -> {
                if (visibleSites.isEmpty()) item { Text(if (sites.isEmpty()) "No sites yet." else "No standard customer sites match this filter.", Modifier.padding(horizontal = 16.dp)) }
                items(visibleSites) { item ->
                    val metadata = buildString { if (item.customerType == CustomerType.ONE_TIME) append("One-time · "); append("${item.address.ifBlank { "No address" }} · ${item.equipmentCount} equipment") }
                    V16ServiceEntityRecord("${item.reference} · ${item.name}", item.customerName, metadata, modifier = Modifier.padding(horizontal = 16.dp)) { nav.navigate("site/${item.id}") }
                }
            }
            else -> {
                if (tab == "EQUIPMENT") {
                    if (visibleEquipment.isEmpty()) item { Text(if (equipment.isEmpty()) "Add an equipment item to begin." else "No standard customer equipment matches this filter.", Modifier.padding(horizontal = 16.dp)) }
                    items(visibleEquipment) { item ->
                        val metadata = buildString { if (item.customerType == CustomerType.ONE_TIME) append("One-time · "); append("Next due ${item.nearestDueDate ?: "not scheduled"}") }
                        V16ServiceEntityRecord("${item.technicianIdentifier ?: item.reference} · ${item.name}", "${item.customerName} · ${item.siteName}", metadata, modifier = Modifier.padding(horizontal = 16.dp)) { nav.navigate("equipment/${item.id}") }
                    }
                }
            }
        }
    }
}

internal fun workRoute(tab: WorkTab, filter: String? = null, scope: WorkScope = WorkScope.Global): String =
    buildString {
        append("work?tab=")
        append(tab.name)
        if (filter != null) {
            append("&filter=")
            append(filter)
        }
        (scope as? WorkScope.Customer)?.customerId?.takeIf(String::isNotBlank)?.let { customerId ->
            append("&customerId=")
            append(URLEncoder.encode(customerId, Charsets.UTF_8.name()).replace("+", "%20"))
        }
    }

@Composable
internal fun EquipmentScreen(detail: EquipmentDetail, nav: NavHostController, businessDate: LocalDate, dueSoonHorizonDays: Int, viewModel: V16ServiceViewModel) {
    val capabilities = LocalWorkspaceCapabilities.current
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(detail.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(listOfNotNull(detail.technicianIdentifier, detail.reference).joinToString(" · "), style = MaterialTheme.typography.titleMedium)
            Text(if (detail.makeModel.isBlank()) "Make/model not supplied" else detail.makeModel)
            detail.serialNumber?.takeIf { it.isNotBlank() }?.let {
                Text("Serial $it", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(detail.customerName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.heightIn(min = V16ServiceUiTokens.Size.touchMin).wrapContentHeight(Alignment.CenterVertically)
                        .clickable { nav.navigate("customer/${detail.customerId}") }.testTag("equipment-customer-link"))
                Text(" › ", style = MaterialTheme.typography.titleMedium)
                Text(detail.siteName, style = MaterialTheme.typography.titleMedium,
                    color = if (detail.siteId.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.heightIn(min = V16ServiceUiTokens.Size.touchMin).wrapContentHeight(Alignment.CenterVertically)
                        .clickable(enabled = detail.siteId.isNotBlank()) { nav.navigate("site/${detail.siteId}") }.testTag("equipment-site-link"))
            }
            if (detail.customerType == CustomerType.ONE_TIME) Text("One-time customer", color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.testTag("one-time-customer-label"))
            if(detail.privateNote.isNotBlank()) Column(Modifier.padding(top=V16ServiceUiTokens.Space.lg).testTag("equipment-private-note"),verticalArrangement=Arrangement.spacedBy(V16ServiceUiTokens.Space.xs)) {
                V16ServicePrivateLabel("Private equipment note", style = V16ServiceUiTokens.Type.label)
                Text(detail.privateNote,style=V16ServiceUiTokens.Type.body)
            }
        }
        if(capabilities.canManageRegister) item { Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("equipment-actions"), horizontalArrangement = Arrangement.spacedBy(8.dp)) { V16ServiceSecondaryButton("Edit",{nav.navigate("equipment/edit/${detail.id}")},Modifier.weight(1f).fillMaxHeight()); if(capabilities.canPerformFieldWork) V16ServiceSecondaryButton("Start / resume",{detail.workingItemId?.let{nav.navigate("inspection/$it")}},Modifier.weight(1f).fillMaxHeight(),enabled=detail.workingItemId!=null) } }
        item { SectionTitle("Service plans") }
        items(detail.plans) { plan ->
            val dueLabel = servicePlanDueLabel(plan.dueDate, businessDate, dueSoonHorizonDays)
            V16ServiceEntityRecord("${plan.reference} · ${plan.name}",plan.interval,"Due ${plan.dueDate} · $dueLabel",plan.state){nav.navigate("plan/${plan.id}")}
        }
        item {
            if (!capabilities.canManageRegister) {
                Unit
            } else if (detail.customerType == CustomerType.ONE_TIME) {
                Text("Recurring service requires a Standard customer.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Button(onClick = { nav.navigate("plan/new/${detail.id}") }, modifier = Modifier.fillMaxWidth().testTag("add-service-plan")) { Text("Add service plan") }
            }
            V16ServiceSectionDivider(); SectionTitle(if(capabilities.canManageRegister) "History and management" else "History"); Spacer(Modifier.height(V16ServiceUiTokens.Space.md)); V16ServiceActionStack { V16ServiceNavigationButton("Equipment history",{nav.navigate("history/EQUIPMENT/${detail.id}")},Modifier.fillMaxWidth()); if(capabilities.canManageRegister) { V16ServiceSecondaryButton("Move equipment",{nav.navigate("equipment/move/${detail.id}")},Modifier.fillMaxWidth()); V16ServiceSecondaryButton(if(detail.state=="ACTIVE") "Retire equipment" else "Return equipment to service",{nav.navigate("lifecycle/EQUIPMENT/${detail.id}/${if(detail.state=="ACTIVE")"RETIRE" else "RETURN"}")},Modifier.fillMaxWidth()) } }
        }
    }
}

internal fun servicePlanDueLabel(dueDate: String, businessDate: LocalDate, dueSoonHorizonDays: Int): String {
    val due = runCatching { LocalDate.parse(dueDate) }.getOrNull() ?: return "State unavailable"
    return when {
        due.isBefore(businessDate) -> "Overdue"
        due == businessDate -> "Due today"
        !due.isAfter(businessDate.plusDays(dueSoonHorizonDays.toLong())) -> "Due soon"
        else -> "Upcoming"
    }
}
