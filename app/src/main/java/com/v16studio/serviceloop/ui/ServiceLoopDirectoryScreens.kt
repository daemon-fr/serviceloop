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
import com.v16studio.serviceloop.domain.WorkScope
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
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopNavigationButton
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
import java.net.URLEncoder
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun CustomersScreen(customers: List<CustomerSummary>, sites: List<SiteRegisterSummary>, equipment: List<EquipmentSummary>, nav: NavHostController) {
    var tab by rememberSaveable { mutableStateOf("CUSTOMERS") }
    var showOneTime by rememberSaveable { mutableStateOf(false) }
    val colors = LocalServiceLoopTokens.current
    val visibleCustomers = customers.filter { showOneTime || it.customerType == CustomerType.STANDARD }
    val visibleSites = sites.filter { showOneTime || it.customerType == CustomerType.STANDARD }
    val visibleEquipment = equipment.filter { showOneTime || it.customerType == CustomerType.STANDARD }
    LazyColumn(contentPadding = PaddingValues(0.dp, 0.dp, 0.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Column(Modifier.fillMaxWidth().background(colors.surface)) { Spacer(Modifier.height(12.dp)); ServiceLoopContentTabs(listOf("CUSTOMERS" to "Customers", "SITES" to "Sites", "EQUIPMENT" to "Equipment"),tab,{tab=it}) } }
        item { Text("${tab.lowercase().replaceFirstChar { it.uppercase() }} register", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp, top = ServiceLoopUiTokens.Space.section, end = 16.dp)) }
        item { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(showOneTime, { showOneTime = it }, modifier = Modifier.testTag("show-one-time-customers")); Text("Show one-time customers") } }
        item {
            when (tab) { "CUSTOMERS" -> ServiceLoopPrimaryButton("Add customer",{ nav.navigate("customer/new") },Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("add-customer")); "EQUIPMENT" -> ServiceLoopPrimaryButton("Add equipment",{ nav.navigate("equipment/select-site") },Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("add-equipment-from-register")); else -> Unit }
        }
        when (tab) {
            "CUSTOMERS" -> {
                if (visibleCustomers.isEmpty()) item { Text(if (customers.isEmpty()) "Add a customer to begin." else "No standard customers match this filter.", Modifier.padding(horizontal = 16.dp)) }
                items(visibleCustomers) { item ->
                    val metadata = buildString { if (item.customerType == CustomerType.ONE_TIME) append("One-time · "); append("${item.siteCount} site · ${item.equipmentCount} equipment") }
                    ServiceLoopEntityRecord(item.name, item.reference, metadata, modifier = Modifier.padding(horizontal = 16.dp)) { nav.navigate("customer/${item.id}") }
                }
            }
            "SITES" -> {
                if (visibleSites.isEmpty()) item { Text(if (sites.isEmpty()) "No sites yet." else "No standard customer sites match this filter.", Modifier.padding(horizontal = 16.dp)) }
                items(visibleSites) { item ->
                    val metadata = buildString { if (item.customerType == CustomerType.ONE_TIME) append("One-time · "); append("${item.address.ifBlank { "No address" }} · ${item.equipmentCount} equipment") }
                    ServiceLoopEntityRecord("${item.reference} · ${item.name}", item.customerName, metadata, modifier = Modifier.padding(horizontal = 16.dp)) { nav.navigate("site/${item.id}") }
                }
            }
            else -> {
                if (visibleEquipment.isEmpty()) item { Text(if (equipment.isEmpty()) "Add an equipment item to begin." else "No standard customer equipment matches this filter.", Modifier.padding(horizontal = 16.dp)) }
                items(visibleEquipment) { item ->
                    val metadata = buildString { if (item.customerType == CustomerType.ONE_TIME) append("One-time · "); append("Next due ${item.nearestDueDate ?: "not scheduled"}") }
                    ServiceLoopEntityRecord("${item.technicianIdentifier ?: item.reference} · ${item.name}", "${item.customerName} · ${item.siteName}", metadata, modifier = Modifier.padding(horizontal = 16.dp)) { nav.navigate("equipment/${item.id}") }
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
internal fun EquipmentScreen(detail: EquipmentDetail, nav: NavHostController, businessDate: LocalDate, dueSoonHorizonDays: Int, viewModel: ServiceLoopViewModel) {
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(detail.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(listOfNotNull(detail.technicianIdentifier, detail.reference).joinToString(" · "), style = MaterialTheme.typography.titleMedium)
            Text(if (detail.makeModel.isBlank()) "Make/model not supplied" else detail.makeModel)
            Text(detail.serialNumber?.let { "Serial $it" } ?: "Serial not supplied", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${detail.customerName}\n${detail.siteName}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ServiceLoopNavigationButton("Customer", { nav.navigate("customer/${detail.customerId}") }, Modifier.weight(1f).testTag("equipment-customer-link"))
                ServiceLoopNavigationButton("Site", { nav.navigate("site/${detail.siteId}") }, Modifier.weight(1f).testTag("equipment-site-link"), enabled = detail.siteId.isNotBlank())
            }
            if (detail.customerType == CustomerType.ONE_TIME) Text("One-time customer", color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.testTag("one-time-customer-label"))
            if(detail.privateNote.isNotBlank()) Column(Modifier.padding(top=ServiceLoopUiTokens.Space.lg).testTag("equipment-private-note"),verticalArrangement=Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs)) {
                ServiceLoopPrivateLabel("Private equipment note", style = ServiceLoopUiTokens.Type.label)
                ServiceLoopPrivateLabel("PRIVATE · Not included in customer report", style = ServiceLoopUiTokens.Type.meta)
                Text(detail.privateNote,style=ServiceLoopUiTokens.Type.body)
            }
        }
        item { Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("equipment-actions"), horizontalArrangement = Arrangement.spacedBy(8.dp)) { ServiceLoopSecondaryButton("Edit",{nav.navigate("equipment/edit/${detail.id}")},Modifier.weight(1f).fillMaxHeight()); ServiceLoopSecondaryButton("Start / resume",{detail.workingItemId?.let{nav.navigate("inspection/$it")}},Modifier.weight(1f).fillMaxHeight(),enabled=detail.workingItemId!=null) } }
        item { SectionTitle("Service plans") }
        items(detail.plans) { plan ->
            val dueLabel = servicePlanDueLabel(plan.dueDate, businessDate, dueSoonHorizonDays)
            ServiceLoopEntityRecord("${plan.reference} · ${plan.name}",plan.interval,"Due ${plan.dueDate} · $dueLabel",plan.state){nav.navigate("plan/${plan.id}")}
        }
        item {
            if (detail.customerType == CustomerType.ONE_TIME) {
                Text("Recurring service requires a Standard customer.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Button(onClick = { nav.navigate("plan/new/${detail.id}") }, modifier = Modifier.fillMaxWidth().testTag("add-service-plan")) { Text("Add service plan") }
            }
            ServiceLoopSectionDivider(); SectionTitle("History and management"); Spacer(Modifier.height(ServiceLoopUiTokens.Space.md)); ServiceLoopActionStack { ServiceLoopNavigationButton("Equipment history",{nav.navigate("history/EQUIPMENT/${detail.id}")},Modifier.fillMaxWidth()); ServiceLoopSecondaryButton("Move equipment",{nav.navigate("equipment/move/${detail.id}")},Modifier.fillMaxWidth()); ServiceLoopSecondaryButton(if(detail.state=="ACTIVE") "Retire equipment" else "Return equipment to service",{nav.navigate("lifecycle/EQUIPMENT/${detail.id}/${if(detail.state=="ACTIVE")"RETIRE" else "RETURN"}")},Modifier.fillMaxWidth()) }
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
