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
import com.v16studio.v16service.domain.CompletionBlockerKind
import com.v16studio.v16service.domain.FollowUpDateFilter
import com.v16studio.v16service.domain.FollowUpStatusFilter
import com.v16studio.v16service.domain.VisitDateFilter
import com.v16studio.v16service.domain.VisitStatusFilter
import com.v16studio.v16service.domain.WorkSubjectType
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
import com.v16studio.v16service.ui.designsystem.V16ServiceBrandStrip
import com.v16studio.v16service.ui.designsystem.V16ServiceDetailToolbar
import com.v16studio.v16service.ui.designsystem.V16ServiceContentTabs
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
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun BusinessProfileScreen(profile: BusinessProfile?, saveStatus: SaveStatus, padding: PaddingValues, viewModel: V16ServiceViewModel, nav: NavHostController) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(DISPATCH_PREFS, 0) }
    var zone by rememberSaveable(profile) { mutableStateOf(profile?.zoneId ?: ZoneId.systemDefault().id) }
    var zoneSearch by rememberSaveable { mutableStateOf("") }
    var selectingZone by remember { mutableStateOf(false) }
    var reportCopyEmail by rememberSaveable(profile) { mutableStateOf(prefs.getString(OFFICE_EMAIL, "").orEmpty()) }
    var pendingEmail by remember { mutableStateOf<String?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    var business by rememberSaveable(profile) { mutableStateOf(profile?.businessName.orEmpty()) }; var technician by rememberSaveable(profile) { mutableStateOf(profile?.technicianName.orEmpty()) }
    var phone by rememberSaveable(profile) { mutableStateOf(profile?.phone.orEmpty()) }; var email by rememberSaveable(profile) { mutableStateOf(profile?.email.orEmpty()) }; var address by rememberSaveable(profile) { mutableStateOf(profile?.postalAddress.orEmpty()) }
    val changed = business != profile?.businessName.orEmpty() || technician != profile?.technicianName.orEmpty() || phone != profile?.phone.orEmpty() || email != profile?.email.orEmpty() || address != profile?.postalAddress.orEmpty() || zone != (profile?.zoneId ?: ZoneId.systemDefault().id) || reportCopyEmail != prefs.getString(OFFICE_EMAIL, "").orEmpty()
    UnsavedChangesGuard(changed, nav)
    LaunchedEffect(saveStatus) {
        if (saveStatus is SaveStatus.Saved && pendingEmail != null) {
            if (!prefs.edit().putString(OFFICE_EMAIL, pendingEmail!!.trim()).commit()) localError = "Report-copy recipient could not be saved on this device"
            pendingEmail = null
        } else if (saveStatus is SaveStatus.Failed) pendingEmail = null
    }
    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.section)) {
        item { Text("These details are frozen into each finalized record.") }
        item { OutlinedTextField(business, { business = it }, label = { Text("Business/display name · Required") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(technician, { technician = it }, label = { Text("Technician name · Required") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(address, { address = it }, label = { Text("Postal address") }, minLines = 2, modifier = Modifier.fillMaxWidth()) }
        item {
            Spacer(Modifier.height(8.dp))
            Text("Business time zone", style = MaterialTheme.typography.titleMedium)
            V16ServiceDenseNavigableRow(zone, leadingIcon = V16ServiceIcons.Time, modifier = Modifier.testTag("business-zone-selector")) { selectingZone = true }
            OutlinedTextField(reportCopyEmail, { reportCopyEmail = it }, label = { Text("Send report copies to (optional)") }, modifier = Modifier.fillMaxWidth().testTag("business-office-email"))
            if (saveStatus is SaveStatus.Saving) Text("Saving…")
            if (saveStatus is SaveStatus.Failed) Text(saveStatus.message, color = MaterialTheme.colorScheme.error)
            localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { pendingEmail = reportCopyEmail; localError = null; viewModel.saveBusinessProfile(BusinessProfile(business, technician, phone, email, address, zone)) }, enabled = business.isNotBlank() && technician.isNotBlank() && saveStatus !is SaveStatus.Saving, modifier = Modifier.fillMaxWidth().testTag("save-business-profile")) { Text("Save") }
        }
    }
    if (selectingZone) AlertDialog(
        onDismissRequest = { selectingZone = false },
        title = { Text("Business time zone") },
        text = { Column { OutlinedTextField(zoneSearch, { zoneSearch = it }, label = { Text("Search time zones") }, modifier = Modifier.fillMaxWidth().testTag("business-zone-search")); LazyColumn(Modifier.heightIn(max = 360.dp)) { items(ZoneId.getAvailableZoneIds().asSequence().filter { it.contains(zoneSearch.trim(), ignoreCase = true) }.sorted().take(100).toList()) { candidate -> TextButton({ zone = candidate; selectingZone = false }, Modifier.fillMaxWidth().testTag("business-zone-${candidate.replace('/', '-')}") ) { Text(candidate) } } } } },
        confirmButton = { TextButton({ selectingZone = false }) { Text("Cancel") } },
    )
}

@Composable
internal fun SaveStateBanner(status: SaveStatus) {
    when (status) {
        SaveStatus.Idle -> V16ServiceNotice("Not saved", "Changes are saved only after the named Save action succeeds.", V16ServiceNoticeKind.Warning)
        SaveStatus.Saving -> V16ServiceNotice("Saving…", "The last durable checkpoint remains in place until this finishes.", V16ServiceNoticeKind.Working)
        is SaveStatus.Saved -> V16ServiceSavedStatus(status.atEpochMillis)
        is SaveStatus.Failed -> V16ServiceNotice("Not saved — action needed", "${status.message}. Your input is still here. Last saved ${formatTime(status.lastSavedAtEpochMillis)}", V16ServiceNoticeKind.Error)
    }
}
