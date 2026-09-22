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
fun ServiceLoopApp(viewModel: ServiceLoopViewModel, notificationRoute: String? = null, incomingWorkPackage: String? = null, incomingWorkPackageEvent: Int = 0, incomingInspectionTemplates: String? = null, incomingInspectionTemplatesEvent: Int = 0, incomingServiceLoopSync: String? = null, incomingServiceLoopSyncEvent: Int = 0, appearancePreferences: AppearancePreferences? = null) {
    val state by viewModel.state.collectAsState()
    if (!state.recoveryCheckComplete) return HonestPlaceholder(PaddingValues(), "Checking local recovery state")
    val nav = rememberNavController()
    val context = LocalContext.current
    val appearanceController = appearancePreferences ?: remember { AppearancePreferences(context) }
    LaunchedEffect(notificationRoute, state.restrictedRecoveryState) {
        if (!state.restrictedRecoveryState && notificationRoute != null) nav.navigate(notificationRoute) { launchSingleTop = true }
    }
    ServiceLoopNavGraph(
        nav = nav,
        state = state,
        viewModel = viewModel,
        appearanceController = appearanceController,
        incomingWorkPackage = incomingWorkPackage,
        incomingWorkPackageEvent = incomingWorkPackageEvent,
        incomingInspectionTemplates = incomingInspectionTemplates,
        incomingInspectionTemplatesEvent = incomingInspectionTemplatesEvent,
        incomingServiceLoopSync = incomingServiceLoopSync,
        incomingServiceLoopSyncEvent = incomingServiceLoopSyncEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RootScaffold(nav: NavHostController, selected: RootDestination, content: @Composable (PaddingValues) -> Unit) {
    val capabilities = LocalWorkspaceCapabilities.current
    val windowWidth = LocalConfiguration.current.screenWidthDp.dp
    val compactRootActions = windowWidth < ServiceLoopUiTokens.Size.narrowThreshold ||
        LocalDensity.current.fontScale >= ServiceLoopUiTokens.Layout.badgeFontScaleStackThreshold
    val layoutDirection = LocalLayoutDirection.current
    Scaffold(
        topBar = {
            Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                ServiceLoopBrandStrip()
                if (compactRootActions) {
                    Surface(color = LocalServiceLoopTokens.current.surface) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = ServiceLoopUiTokens.Space.md, vertical = ServiceLoopUiTokens.Space.xs),
                            verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs),
                        ) {
                            Text(
                                selected.label,
                                style = ServiceLoopUiTokens.Type.screenTitle,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = ServiceLoopUiTokens.Space.xs),
                            )
                            TextButton(
                                onClick = { nav.navigate("search") },
                                modifier = Modifier.fillMaxWidth().heightIn(min = ServiceLoopUiTokens.Size.touchMin),
                            ) {
                                ServiceLoopIcon(ServiceLoopIcons.Search, null, Modifier.size(ServiceLoopUiTokens.Size.icon))
                                Spacer(Modifier.width(ServiceLoopUiTokens.Space.xs))
                                Text("Search")
                            }
                            TextButton(
                                onClick = { nav.navigate("settings") },
                                modifier = Modifier.fillMaxWidth().heightIn(min = ServiceLoopUiTokens.Size.touchMin),
                            ) {
                                ServiceLoopIcon(ServiceLoopIcons.Settings, null, Modifier.size(ServiceLoopUiTokens.Size.icon))
                                Spacer(Modifier.width(ServiceLoopUiTokens.Space.xs))
                                Text("Settings")
                            }
                        }
                    }
                } else {
                    TopAppBar(
                        windowInsets = WindowInsets(0),
                        title = {
                            Text(selected.label, style = MaterialTheme.typography.titleLarge)
                        },
                        actions = {
                            TextButton(onClick = { nav.navigate("search") }) { ServiceLoopIcon(ServiceLoopIcons.Search, null, Modifier.size(ServiceLoopUiTokens.Size.icon)); Spacer(Modifier.width(ServiceLoopUiTokens.Space.xs)); Text("Search") }
                            TextButton(onClick = { nav.navigate("settings") }) { ServiceLoopIcon(ServiceLoopIcons.Settings, null, Modifier.size(ServiceLoopUiTokens.Size.icon)); Spacer(Modifier.width(ServiceLoopUiTokens.Space.xs)); Text("Settings") }
                        },
                    )
                }
            }
        },
        bottomBar = { RootNavigation(selected, capabilities, nav::navigateToRoot) },
        content = { padding -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { Box(Modifier.widthIn(max = ServiceLoopUiTokens.Size.contentMaxWidth).fillMaxSize()) { content(serviceLoopAdaptiveScaffoldPadding(padding, windowWidth, layoutDirection)) } } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailScaffold(title: String, nav: NavHostController, topAction: (@Composable RowScope.() -> Unit)? = null, content: @Composable (PaddingValues) -> Unit) {
    val interceptor=remember { mutableStateOf<(() -> Unit)?>(null) }
    val windowWidth = LocalConfiguration.current.screenWidthDp.dp
    val layoutDirection = LocalLayoutDirection.current
    CompositionLocalProvider(LocalDetailBackInterceptor provides interceptor) { Scaffold(containerColor=LocalServiceLoopTokens.current.canvas,topBar = {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            ServiceLoopBrandStrip()
            Surface(color=LocalServiceLoopTokens.current.surface) {
                ServiceLoopDetailToolbar(title, { interceptor.value?.invoke() ?: nav.popBackStack() }, topAction)
            }
        }
    }, content = { padding -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { Box(Modifier.widthIn(max = ServiceLoopUiTokens.Size.contentMaxWidth).fillMaxSize()) { content(serviceLoopAdaptiveScaffoldPadding(padding, windowWidth, layoutDirection)) } } }) }
}

internal val LocalDetailBackInterceptor = compositionLocalOf<MutableState<(() -> Unit)?>> { error("Detail back interceptor unavailable") }

@Composable
private fun RootNavigation(selected: RootDestination, capabilities: WorkspaceCapabilities, onNavigate: (RootDestination) -> Unit) {
    NavigationBar {
        RootDestination.entries.filter { it != RootDestination.CUSTOMERS || capabilities.showRegister }.forEach { destination ->
            val icon = when (destination) {
                RootDestination.HOME -> ServiceLoopIcons.Home
                RootDestination.WORK -> ServiceLoopIcons.Work
                RootDestination.CUSTOMERS -> ServiceLoopIcons.Customers
            }
            NavigationBarItem(selected = selected == destination, onClick = { onNavigate(destination) }, icon = { ServiceLoopIcon(icon, null, Modifier.size(ServiceLoopUiTokens.Size.icon)) }, label = { Text(destination.label) }, modifier = Modifier.testTag("root-nav-${destination.name.lowercase()}"))
        }
    }
}

internal fun NavHostController.navigateToRoot(destination: RootDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = false }
        launchSingleTop = true
        restoreState = false
    }
}

@Composable
internal fun ScreenState(loading: Boolean, error: String?, padding: PaddingValues, contentTag: String? = null, refreshError: String? = null, onRetry: (() -> Unit)? = null, content: @Composable () -> Unit) {
    when {
        loading -> Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Text("Reading saved service book", Modifier.padding(16.dp)) }
        error != null -> HonestPlaceholder(padding, "Cannot read saved data. $error")
        else -> Column(Modifier.fillMaxSize().padding(padding).then(if (contentTag == null) Modifier else Modifier.testTag(contentTag))) {
            refreshError?.let { message ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Saved data could not be refreshed — $message", color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    onRetry?.let { TextButton(onClick = it) { Text("Retry") } }
                }
            }
            Surface(Modifier.fillMaxWidth().weight(1f), color = MaterialTheme.colorScheme.background) { content() }
        }
    }
}





@Composable
internal fun ReportPhotoThumbnail(photo: PublicPhoto, index: Int, context: android.content.Context) {
    val colors = LocalServiceLoopTokens.current
    val bitmap = remember(photo.relativePath, photo.sha256) {
        BitmapFactory.decodeFile(File(context.filesDir, photo.relativePath).absolutePath, BitmapFactory.Options().apply { inSampleSize = 4 })
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(88.dp).background(colors.photoMat, MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
            if (bitmap != null) Image(bitmap.asImageBitmap(), photo.caption ?: "Customer report photograph ${index + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else Text("Image unavailable", color = colors.errorInk, style = MaterialTheme.typography.bodySmall)
        }
        Column(Modifier.weight(1f)) {
            Text("Photograph ${index + 1}", fontWeight = FontWeight.SemiBold)
            Text(photo.caption?.takeIf(String::isNotBlank) ?: "No caption", style = MaterialTheme.typography.bodyMedium)
            if (photo.addedInCorrection) Text("Added in correction", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable internal fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })

@Composable internal fun AccentCard(content: @Composable ColumnScope.() -> Unit) = ServiceLoopSurfaceCard(content = content)

@Composable internal fun StatusChip(text: String, urgency: Boolean) { val colors = LocalServiceLoopTokens.current; Text(text, color = if (urgency) colors.warningInk else colors.infoInk, modifier = Modifier.background(if (urgency) colors.warningContainer else colors.infoContainer, MaterialTheme.shapes.extraSmall).padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium) }

@Composable internal fun LabelledValue(label: String, value: String, public: Boolean) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (public) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant); Text(value); Spacer(Modifier.height(8.dp)); HorizontalDivider() }

@Composable internal fun HonestPlaceholder(padding: PaddingValues, message: String) { Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text(message, style = MaterialTheme.typography.titleMedium) } }

internal fun formatTime(epochMillis: Long?): String = epochMillis?.let { DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it)) } ?: "not yet"
internal fun formatServiceLoopDate(persisted: String): String = runCatching {
    LocalDate.parse(persisted).format(DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH))
}.getOrDefault(persisted)
internal fun formatRecordedOn(epochMillis: Long): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMillis))

private fun signedDecimal(value: String): Boolean = Regex("^[+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)$").matches(value.trim())

internal fun serviceLoopSubjectLabel(subjectType: WorkSubjectType, equipmentName: String?, equipmentReference: String?, equipmentDescription: String?): String = when {
    subjectType == WorkSubjectType.SITE -> "Site task"
    else -> listOfNotNull(
        equipmentReference?.trim()?.takeIf(String::isNotBlank),
        equipmentName?.trim()?.takeIf(String::isNotBlank),
    ).joinToString(" · ").ifBlank { equipmentDescription?.trim()?.takeIf(String::isNotBlank) ?: "Equipment not specified" }
}

internal fun dueEffect(line: com.v16studio.serviceloop.domain.PublicWorkLine): String = when {
    line.historyOnly -> "Recurring historical work · History only — no current due-date effect"
    !line.isRecurringPlan -> "One-off work — no recurring due date effect"
    line.fulfilledObligation -> "Due ${line.oldDueDate} → ${line.nextDueDate}"
    else -> "Current service remains due ${line.oldDueDate}"
}
