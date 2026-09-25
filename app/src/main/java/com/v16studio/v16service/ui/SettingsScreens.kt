package com.v16studio.v16service.ui

import com.v16studio.v16service.BuildConfig
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
internal fun SettingsScreen(state: UiState, padding: PaddingValues, nav: NavHostController, appearancePreferences: AppearancePreferences) {
    val appearanceMode by appearancePreferences.mode.collectAsState()
    val context = LocalContext.current
    LazyColumn(Modifier.padding(padding).testTag("settings-screen"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.lg)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.sm)) {
                SettingsSectionTitle("V16 Service app", Modifier.testTag("settings-section-app"))
                V16ServiceDenseNavigableRow("Appearance", context = appearanceMode.label, leadingIcon = V16ServiceIcons.Appearance, modifier = Modifier.testTag("settings-appearance")) { nav.navigate("appearance") }
                V16ServiceDenseNavigableRow("Version ${BuildConfig.VERSION_NAME}", context = "Build ${BuildConfig.VERSION_CODE}", leadingIcon = V16ServiceIcons.Info, modifier = Modifier.testTag("settings-about-version")) {}
                V16ServiceDenseNavigableRow("Report a bug", leadingIcon = V16ServiceIcons.Bug, modifier = Modifier.testTag("settings-report-bug")) {
                    val body = "V16 Service version: ${BuildConfig.VERSION_NAME}\nVersion code: ${BuildConfig.VERSION_CODE}\nAndroid: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}"
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:placeholder@mail.com")).apply {
                            putExtra(Intent.EXTRA_SUBJECT, "V16 Service ${BuildConfig.VERSION_NAME} bug report")
                            putExtra(Intent.EXTRA_TEXT, body)
                        })
                    }.onFailure { Toast.makeText(context, "No email app is available", Toast.LENGTH_SHORT).show() }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.sm)) {
                SettingsSectionTitle("Data & automation", Modifier.testTag("settings-section-data"))
                V16ServiceDenseNavigableRow("Reminders", context = state.reminderRuntimeState.label, leadingIcon = V16ServiceIcons.Time, modifier = Modifier.testTag("settings-reminders")) { nav.navigate("reminders") }
                V16ServiceDenseNavigableRow("Calendar integration", context = state.calendarRuntimeState.label, leadingIcon = V16ServiceIcons.Calendar, modifier = Modifier.testTag("settings-calendar")) { nav.navigate("calendar") }
                V16ServiceDenseNavigableRow("Image cleanup", context = "Keep report-quality copies of finalized photos", leadingIcon = V16ServiceIcons.Photo, modifier = Modifier.testTag("settings-image-cleanup")) { nav.navigate("image-cleanup") }
                V16ServiceDenseNavigableRow("History", leadingIcon = V16ServiceIcons.History, modifier = Modifier.testTag("settings-history")) { nav.navigate("history/global") }
                V16ServiceDenseNavigableRow("Backup and recovery", leadingIcon = V16ServiceIcons.Backup, modifier = Modifier.testTag("settings-backup-recovery")) { nav.navigate("data-recovery") }
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = LocalV16ServiceTokens.current.action,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
internal fun AppearanceSettingsScreen(padding: PaddingValues, appearancePreferences: AppearancePreferences) {
    val selected by appearancePreferences.mode.collectAsState()
    LazyColumn(
        Modifier.padding(padding).testTag("appearance-settings"),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.sm),
    ) {
        item {
            Text("Appearance", style = MaterialTheme.typography.titleLarge)
            Text("Choose how V16 Service should appear. Changes apply immediately; no restart is required.")
        }
        item {
            V16ServiceChoiceGroup(
                options = AppearanceMode.entries.map { mode -> mode to "${mode.label} — ${mode.meaning}" },
                selected = selected,
                onSelected = appearancePreferences::setMode,
                testTagPrefix = "appearance",
            )
        }
    }
}

@Composable
internal fun CalendarSettingsScreen(state:UiState,padding:PaddingValues,viewModel:V16ServiceViewModel){
    val context=LocalContext.current
    val runtime=state.calendarRuntimeState
    val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){result->if(result[Manifest.permission.READ_CALENDAR]==true&&result[Manifest.permission.WRITE_CALENDAR]==true)viewModel.setCalendarEnabled(true)else viewModel.loadCalendarSettings()}
    LazyColumn(Modifier.padding(padding).testTag("calendar-settings"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(V16ServiceUiTokens.Space.section)){
        item{Text("One-way Calendar integration",style=MaterialTheme.typography.titleLarge);Text("V16 Service remains the source of truth. Calendar changes do not change V16 Service. Only Booked Visits with an appointment time are automatically synchronized.");Text("Status: ${runtime.label}",modifier=Modifier.testTag("calendar-status"));Row(verticalAlignment=Alignment.CenterVertically){Checkbox(runtime.enabled,{enabled->if(enabled&&!runtime.hasPermissions)permissions.launch(arrayOf(Manifest.permission.READ_CALENDAR,Manifest.permission.WRITE_CALENDAR))else viewModel.setCalendarEnabled(enabled)},Modifier.testTag("calendar-enabled"));Text("Enable Calendar integration")};if(!runtime.hasPermissions)OutlinedButton({permissions.launch(arrayOf(Manifest.permission.READ_CALENDAR,Manifest.permission.WRITE_CALENDAR))},Modifier.fillMaxWidth().testTag("calendar-request-permission")){Text("Allow Calendar access")}}
        if(runtime.hasPermissions)item{Text("Preferred calendar",fontWeight=FontWeight.Bold);if(runtime.writableCalendars.isEmpty())Text("No writable calendar is available on this device.");runtime.writableCalendars.forEach{calendar->V16ServiceSelectionOption(runtime.selectedCalendarLabel==calendar.label,{viewModel.selectCalendar(calendar)},listOfNotNull(calendar.label,calendar.accountLabel).joinToString(" · "),Modifier.testTag("calendar-${calendar.id}"))};if(runtime.linkedFutureCount>0)Text("Changing this choice affects new events only; existing linked events stay in their original calendar.")}
        item{Text("${runtime.linkedFutureCount} linked future Visits · ${runtime.problemCount} need attention");if(!runtime.hasPermissions)OutlinedButton({context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:${context.packageName}")))},Modifier.fillMaxWidth()){Text("Open Android app permission settings")};state.operationMessage?.let{Text(it)};state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}}
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReminderSettingsScreen(state: UiState, padding: PaddingValues, viewModel: V16ServiceViewModel, nav: NavHostController) {
    val saved = state.reminderPreferences ?: return HonestPlaceholder(padding, "Reading reminder settings")
    val context = LocalContext.current
    var draft by remember(saved) { mutableStateOf(saved) }
    var requested by rememberSaveable(saved, state.reminderRuntimeState.deliveryRequested) { mutableStateOf(state.reminderRuntimeState.deliveryRequested) }
    var timeText by rememberSaveable(saved) { mutableStateOf("%02d:%02d".format(saved.summaryHour, saved.summaryMinute)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.loadReminderSettings() }
    val parsedTime = runCatching { java.time.LocalTime.parse(timeText) }.getOrNull()
    val normalized = parsedTime?.let { draft.copy(summaryHour = it.hour, summaryMinute = it.minute) } ?: draft
    val changed = normalized != saved || requested != state.reminderRuntimeState.deliveryRequested
    UnsavedChangesGuard(changed, nav)
    fun toggleDelivery(value: Boolean) {
        requested = value
        if (value && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LazyColumn(Modifier.padding(padding).testTag("reminder-settings"), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.section)) {
        item {
            Text("Local reminders", style = MaterialTheme.typography.titleLarge)
            val blockedChannel = requested && ((draft.dailySummaryEnabled && !state.reminderRuntimeState.summariesChannelEnabled) || (draft.appointmentAlertsEnabled && !state.reminderRuntimeState.appointmentsChannelEnabled))
            if (requested && !state.reminderRuntimeState.permissionGranted) V16ServiceNotice("Notifications are blocked in Android settings", "Allow notifications for V16 Service to receive reminders.", V16ServiceNoticeKind.Warning)
            else if (blockedChannel || state.reminderRuntimeState.schedulingError != null) V16ServiceNotice("Local reminders need attention", "Review Android notification settings and try again.", V16ServiceNoticeKind.Warning)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(requested, ::toggleDelivery, modifier = Modifier.testTag("reminder-delivery")); Text("Request local reminders") }
            if (changed && requested) Text("Android permission changes are external and are not undone by Cancel.", style = MaterialTheme.typography.bodySmall)
        }
        item { HorizontalDivider(); Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(draft.dailySummaryEnabled, { draft = draft.copy(dailySummaryEnabled = it) }); Text("Daily work summary") }; OutlinedTextField(timeText, { timeText = it }, label = { Text("Summary time · HH:mm") }, modifier = Modifier.fillMaxWidth().testTag("summary-time")); Text("Summary days"); SummaryDayChoices(draft){draft=it}; if (draft.dailySummaryEnabled && draft.summaryDaysMask and ReminderPreferences.ALL_DAYS == 0) Text("Select at least one summary day", color = MaterialTheme.colorScheme.error) }
        item { Text("Due-soon horizon", fontWeight = FontWeight.Bold); V16ServicePresetChoiceGroup(listOf(1 to "1 day", 7 to "7 days", 14 to "14 days", 30 to "30 days"),draft.dueSoonHorizonDays,{draft=draft.copy(dueSoonHorizonDays=it)},testTagPrefix="due-horizon", singleRow = true) }
        item { Text("Summary content", fontWeight = FontWeight.Bold); ReminderToggle("Due services", draft.includeDueServices) { draft = draft.copy(includeDueServices = it) }; ReminderToggle("Visits", draft.includeVisits) { draft = draft.copy(includeVisits = it) }; ReminderToggle("Follow-ups", draft.includeFollowUps) { draft = draft.copy(includeFollowUps = it) }; ReminderToggle("Unfinished visits", draft.includeUnfinishedVisits) { draft = draft.copy(includeUnfinishedVisits = it) }; ReminderToggle("Backup reminder", draft.includeBackupReminder) { draft = draft.copy(includeBackupReminder = it) } }
        item { HorizontalDivider(); ReminderToggle("Approximate appointment alerts", draft.appointmentAlertsEnabled) { draft = draft.copy(appointmentAlertsEnabled = it) }; Text("Default appointment lead", fontWeight = FontWeight.Bold); V16ServicePresetChoiceGroup(ReminderPreferences.APPOINTMENT_LEAD_PRESETS, draft.defaultAppointmentLeadMinutes, { draft = draft.copy(defaultAppointmentLeadMinutes = it) }, testTagPrefix = "appointment-lead", singleRow = true); if (draft.defaultAppointmentLeadMinutes == ReminderPreferences.LEGACY_APPOINTMENT_LEAD_MINUTES) Text("Current saved lead: 2h. Choose a new preset to replace it.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("legacy-default-appointment-lead")) }
        item { V16ServiceActionStack { OutlinedButton({ context.startActivity((context.applicationContext as com.v16studio.v16service.V16ServiceApplication).container.reminderCoordinator.openAndroidSettingsIntent()) }, Modifier.fillMaxWidth()) { Text("Open Android notification settings") }; OutlinedButton(viewModel::sendTestNotification, Modifier.fillMaxWidth().testTag("send-test-notification")) { Text("Send test notification") } }; state.operationMessage?.let { Text(it) } }
        item { SaveStateBanner(state.reminderSaveStatus); Spacer(Modifier.height(V16ServiceUiTokens.Space.lg)); V16ServiceActionStack { V16ServicePrimaryButton(if (state.reminderSaveStatus is SaveStatus.Saving) "Saving reminder settings" else "Save reminder settings", { if (parsedTime != null) viewModel.saveReminderSettings(normalized, requested) }, enabled = parsedTime != null && (!draft.dailySummaryEnabled || draft.summaryDaysMask and ReminderPreferences.ALL_DAYS != 0), busy = state.reminderSaveStatus is SaveStatus.Saving, modifier = Modifier.fillMaxWidth().testTag("save-reminders")); TextButton({ nav.popBackStack() }, Modifier.fillMaxWidth()) { Text("Cancel / Back") } } }
    }
}

@Composable
private fun ReminderToggle(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, onChecked); Text(label) }

internal val summaryDayGap = 4.dp
internal fun summaryDaysUseSingleRow(maxWidth: Dp, fontScale: Float): Boolean =
    fontScale < 1.3f && maxWidth >= V16ServiceUiTokens.Size.touchMin * 7 + summaryDayGap * 6

@Composable
private fun SummaryDayChoices(value: ReminderPreferences, onChanged: (ReminderPreferences) -> Unit) {
    val days = java.time.DayOfWeek.entries
    @Composable fun RowScope.Day(day: java.time.DayOfWeek) = V16ServiceDayToggle(
        label = day.name.take(2),
        selected = value.includes(day),
        onSelectedChange = { onChanged(value.copy(summaryDaysMask = value.summaryDaysMask xor (1 shl (day.value - 1)))) },
        modifier = Modifier.weight(1f).testTag("summary-day-${day.name.lowercase()}"),
    )
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("summary-days")) {
        if (summaryDaysUseSingleRow(maxWidth, LocalDensity.current.fontScale)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(summaryDayGap)) { days.forEach { Day(it) } }
        else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { days.take(4).forEach { Day(it) } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { days.drop(4).forEach { Day(it) } }
        }
    }
}
