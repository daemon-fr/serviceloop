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
internal fun CompletionReviewScreen(visitId: String, lines: List<CompletionLine>, profile: BusinessProfile?, state: UiState, padding: PaddingValues, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    LazyColumn(Modifier.padding(padding).testTag("completion-review-list"), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Review the service details before finalizing.", style = MaterialTheme.typography.titleMedium) }
        if (state.visitReportIdentity?.ready != true) item { AccentCard { Text("This visit needs a captured report identity before finalization."); if (profile?.ready == true) Button(onClick = { viewModel.refreshVisitReportIdentity(visitId) }, modifier = Modifier.fillMaxWidth().testTag("capture-report-identity")) { Text("Use current business identity for this visit") } else Button(onClick = { nav.navigate("business-profile") }, modifier = Modifier.fillMaxWidth()) { Text("Set business identity") } } }
        else item { AccentCard { Text("Report identity", style = MaterialTheme.typography.titleMedium); Text("${state.visitReportIdentity.businessName} · ${state.visitReportIdentity.technicianName}"); TextButton(onClick = { viewModel.refreshVisitReportIdentity(visitId) }, modifier = Modifier.testTag("refresh-report-identity")) { Text("Update from current profile") } } }
        items(lines) { line -> CompletionLineCard(visitId, line, state.saveStatus is SaveStatus.Saving, viewModel, nav) }
        item { ServiceLoopSurfaceCard { Text("Customer report review", style = MaterialTheme.typography.titleMedium); Text("Review what will appear in the customer record. Private notes are never included.") } }
        state.error?.let { message -> item { Text("Finalization failed — $message", color = MaterialTheme.colorScheme.error) } }
        item { ServiceLoopPrimaryButton(label = if (state.finalizing) "Finalizing record" else "Finalize record", onClick = { viewModel.finalizeVisit(visitId) }, enabled = state.visitReportIdentity?.ready == true && lines.isNotEmpty() && lines.all { it.blockers.isEmpty() }, busy = state.finalizing, modifier = Modifier.fillMaxWidth().testTag("finalize-record").semantics { contentDescription = "Finalize record" }) }
    }
}

@Composable
private fun CompletionLineCard(visitId: String, line: CompletionLine, saving: Boolean, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    AccentCard {
        Text(serviceLoopSubjectLabel(line.subjectType, line.equipmentName, line.equipmentReference, line.equipmentDescription), style = MaterialTheme.typography.labelLarge)
        Text(line.serviceName, style = MaterialTheme.typography.titleMedium)
        Text("Outcome · " + when (line.outcome) {
            "PERFORMED" -> "Performed"
            "PARTLY_PERFORMED" -> "Partly performed"
            "NOT_PERFORMED" -> "Not performed"
            else -> "Not chosen"
        })
        if (line.workPerformed.isNotBlank()) Text(line.workPerformed)
        if (line.checklistResults.isNotEmpty()) {
            Text("Checklist", style = MaterialTheme.typography.labelLarge)
            line.checklistResults.forEachIndexed { index, item ->
                Text("${item.label} — ${item.result}", modifier = Modifier.testTag("review-checklist-item-${line.workItemId}-$index"))
            }
        }
        if (!line.notPerformedReason.isNullOrBlank()) Text("Reason · ${line.notPerformedReason}")
        when (line.fulfillsCurrentObligation) {
            true -> Text("Next due · ${line.confirmedNextDueDate?.let(::formatServiceLoopDate) ?: "Needs attention"}")
            false -> if (line.currentObligationOutstanding && line.dueDate != null) Text("Remains due · ${formatServiceLoopDate(line.dueDate)}")
            null -> if (line.fulfillmentEligibility == FulfillmentEligibility.ELIGIBLE) Text("Due-service decision needed")
        }
        if (line.fulfillmentEligibility == FulfillmentEligibility.CURRENT_OBLIGATION_CHANGED) Text("Current service obligation changed — this Service cannot advance the current due date.")
        if (line.fulfillmentEligibility == FulfillmentEligibility.PLAN_INELIGIBLE) Text("The service plan is no longer active — this Service cannot advance the due date.")
        line.blockers.forEach { blocker ->
            TextButton(onClick = {
                viewModel.focusInspection(blocker.kind, blocker.questionId, line.workItemId)
                nav.navigate("inspection/${line.workItemId}")
            }, modifier = Modifier.fillMaxWidth().testTag("completion-blocker-${line.workItemId}-${blocker.kind}-${blocker.questionId.orEmpty()}")) {
                Text(blocker.message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
