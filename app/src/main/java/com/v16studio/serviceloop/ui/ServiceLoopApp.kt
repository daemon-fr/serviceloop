package com.v16studio.serviceloop.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.v16studio.serviceloop.domain.CompletionLine
import com.v16studio.serviceloop.domain.CustomerSummary
import com.v16studio.serviceloop.domain.EquipmentDetail
import com.v16studio.serviceloop.domain.EquipmentSummary
import com.v16studio.serviceloop.domain.FulfillmentEligibility
import com.v16studio.serviceloop.domain.HomeSummary
import com.v16studio.serviceloop.domain.InspectionDraft
import com.v16studio.serviceloop.domain.InspectionQuestion
import com.v16studio.serviceloop.domain.ResponseDisposition
import com.v16studio.serviceloop.domain.SaveStatus
import com.v16studio.serviceloop.ui.theme.LocalServiceLoopColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val HOME = "home"
private const val WORK = "work"
private const val CUSTOMERS = "customers"

private enum class RootDestination(val route: String, val label: String) {
    HOME("home", "Home"),
    WORK("work", "Work"),
    CUSTOMERS("customers", "Customers"),
}

internal enum class WorkTab(val label: String) {
    DUE_SERVICES("Due services"),
    VISITS("Visits"),
    FOLLOW_UPS("Follow-ups"),
}

@Composable
fun ServiceLoopApp(viewModel: ServiceLoopViewModel) {
    val state by viewModel.state.collectAsState()
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = HOME,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(HOME) {
            LaunchedEffect(Unit) { viewModel.refreshRootDataNonBlocking() }
            RootScaffold(nav, RootDestination.HOME) { padding ->
                ScreenState(state.loading && !state.rootDataReady, state.error.takeUnless { state.rootDataReady }, padding, "root-home") { HomeScreen(state.home, state.equipmentList, nav) }
            }
        }
        composable(WORK) {
            var workTab by rememberSaveable { mutableStateOf(WorkTab.DUE_SERVICES) }
            LaunchedEffect(Unit) { viewModel.refreshRootDataNonBlocking() }
            RootScaffold(nav, RootDestination.WORK) { padding ->
                ScreenState(state.loading && !state.rootDataReady, state.error.takeUnless { state.rootDataReady }, padding, "root-work") { WorkScreen(state.home, nav, workTab) { workTab = it } }
            }
        }
        composable(CUSTOMERS) {
            var equipmentMode by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) { viewModel.refreshRootDataNonBlocking() }
            RootScaffold(nav, RootDestination.CUSTOMERS) { padding ->
                ScreenState(state.loading && !state.rootDataReady, state.error.takeUnless { state.rootDataReady }, padding, "root-customers") { CustomersScreen(state.customerList, state.equipmentList, nav, equipmentMode) { equipmentMode = it } }
            }
        }
        composable("equipment/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            LaunchedEffect(id) { viewModel.loadEquipment(id) }
            DetailScaffold("Equipment", nav) { padding ->
                ScreenState(state.loading, state.error, padding) { state.equipment?.let { EquipmentScreen(it, nav) } }
            }
        }
        composable("inspection/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            LaunchedEffect(id) { viewModel.loadInspection(id) }
            DetailScaffold(state.inspection?.serviceName ?: "Inspection", nav) { padding ->
                ScreenState(state.loading, state.error, padding) { state.inspection?.let { InspectionScreen(it, state.saveStatus, viewModel, nav) } }
            }
        }
        composable("review/{visitId}") { entry ->
            val visitId = entry.arguments?.getString("visitId").orEmpty()
            LaunchedEffect(visitId) { viewModel.loadCompletion(visitId) }
            DetailScaffold("Review completion", nav) { padding -> CompletionReviewScreen(state.completionLines, padding) }
        }
        composable("scope/{title}") { entry ->
            DetailScaffold(entry.arguments?.getString("title") ?: "ServiceLoop", nav) { padding ->
                HonestPlaceholder(padding, "This foundation exposes the entry point without claiming the later workflow is complete.")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RootScaffold(nav: NavHostController, selected: RootDestination, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selected.label, style = MaterialTheme.typography.titleLarge) },
                actions = {
                    TextButton(onClick = { nav.navigate("scope/Search") }) { Text("Search") }
                    TextButton(onClick = { nav.navigate("scope/Settings") }) { Text("Settings") }
                },
            )
        },
        bottomBar = { RootNavigation(selected, nav::navigateToRoot) },
        floatingActionButton = { if (selected == RootDestination.WORK) OutlinedButton(onClick = { nav.navigate("scope/New visit") }) { Text("New visit") } },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScaffold(title: String, nav: NavHostController, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) }, navigationIcon = { TextButton(onClick = { nav.popBackStack() }) { Text("Back") } }) }, content = content)
}

@Composable
private fun RootNavigation(selected: RootDestination, onNavigate: (RootDestination) -> Unit) {
    NavigationBar {
        RootDestination.entries.forEach { destination ->
            NavigationBarItem(selected = selected == destination, onClick = { onNavigate(destination) }, icon = { Text(if (destination == RootDestination.HOME) "⌂" else if (destination == RootDestination.WORK) "✓" else "◎") }, label = { Text(destination.label) })
        }
    }
}

private fun NavHostController.navigateToRoot(destination: RootDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun ScreenState(loading: Boolean, error: String?, padding: PaddingValues, contentTag: String? = null, content: @Composable () -> Unit) {
    when {
        loading -> Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Text("Reading saved service book", Modifier.padding(16.dp)) }
        error != null -> HonestPlaceholder(padding, "Cannot read saved data. $error")
        else -> Surface(Modifier.fillMaxSize().padding(padding).then(if (contentTag == null) Modifier else Modifier.testTag(contentTag)), color = MaterialTheme.colorScheme.background) { content() }
    }
}

@Composable
private fun HomeScreen(home: HomeSummary?, equipment: List<EquipmentSummary>, nav: NavHostController) {
    if (home == null) return HonestPlaceholder(PaddingValues(), "Add a customer to create your first service obligation.")
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (home.workingVisitId != null) item {
            SectionTitle("Unfinished visits · 1")
            AccentCard {
                Text(home.workingSite.orEmpty(), style = MaterialTheme.typography.titleMedium)
                Text("${home.workingVisitReference} · Working", color = LocalServiceLoopColors.current.workflowInk)
                Text("Saved on this device · ${formatTime(home.savedAtEpochMillis)}", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { home.inspectionWorkItemId?.let { nav.navigate("inspection/$it") } }, modifier = Modifier.fillMaxWidth()) { Text("Resume visit") }
            }
        }
        item {
            SectionTitle("Booked visits · ${if (home.bookedVisitReference == null) 0 else 1}")
            SummaryRow(home.bookedVisitReference?.let { "$it · ${home.bookedVisitDate}" } ?: "No booked visits", "Open") { nav.navigateToRoot(RootDestination.WORK) }
        }
        item { SectionTitle("Overdue services · ${home.overdueCount}"); Text("Booked service remains due until its obligation is explicitly fulfilled.", style = MaterialTheme.typography.bodyMedium) }
        items(equipment.take(3)) { item -> SummaryRow("${item.technicianIdentifier ?: item.reference} · ${item.name}\nDue ${item.nearestDueDate ?: "not scheduled"}", "Open") { nav.navigate("equipment/${item.id}") } }
        item { SectionTitle("Due soon · ${home.dueSoonCount}"); SummaryRow("Next 14 business-local days", "View all") { nav.navigateToRoot(RootDestination.WORK) } }
        item { SectionTitle("Follow-ups due · ${home.dueFollowUpCount}"); SummaryRow(listOfNotNull(home.dueFollowUpReference, home.dueFollowUpTitle).joinToString(" · ").ifBlank { "No follow-ups due" }, "All open") { nav.navigateToRoot(RootDestination.WORK) } }
        item { SectionTitle("Records needing attention"); Text("No report or correction failures in this fixture.") }
        item { OutlinedButton(onClick = { nav.navigate("scope/New visit") }, modifier = Modifier.fillMaxWidth()) { Text("New visit") } }
    }
}

@Composable
private fun WorkScreen(home: HomeSummary?, nav: NavHostController, tab: WorkTab, onTabSelected: (WorkTab) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { WorkTab.entries.forEach { option -> if (tab == option) Button(onClick = {}, modifier = Modifier.weight(1f)) { Text(option.label) } else OutlinedButton(onClick = { onTabSelected(option) }, modifier = Modifier.weight(1f)) { Text(option.label) } } } }
        when (tab) {
            WorkTab.DUE_SERVICES -> item { SectionTitle("Due services · ${(home?.overdueCount ?: 0) + (home?.dueSoonCount ?: 0)}"); Text("Each plan retains its own obligation and due date.") }
            WorkTab.VISITS -> {
                item { SectionTitle("Visits") }
                item { SummaryRow("${home?.workingVisitReference ?: "No working visit"} · ${home?.workingSite.orEmpty()}", "Resume") { home?.inspectionWorkItemId?.let { nav.navigate("inspection/$it") } } }
                home?.bookedVisitReference?.let { reference -> item { SummaryRow("$reference · Booked ${home.bookedVisitDate}", "Open") { nav.navigate("scope/Booked visit") } } }
            }
            WorkTab.FOLLOW_UPS -> {
                item { SectionTitle("Follow-ups · ${home?.dueFollowUpCount ?: 0}") }
                item { SummaryRow(listOfNotNull(home?.dueFollowUpReference, home?.dueFollowUpTitle).joinToString(" · ").ifBlank { "No follow-ups due" }, "Open") { nav.navigate("scope/Follow-up") } }
            }
        }
        item { SummaryRow("History and records needing attention", "Open") { nav.navigate("scope/History") } }
    }
}

@Composable
private fun CustomersScreen(customers: List<CustomerSummary>, equipment: List<EquipmentSummary>, nav: NavHostController, equipmentMode: Boolean, onEquipmentModeChanged: (Boolean) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { if (!equipmentMode) Button(onClick = {}) { Text("Customers") } else OutlinedButton(onClick = { onEquipmentModeChanged(false) }) { Text("Customers") }; if (equipmentMode) Button(onClick = {}) { Text("Equipment") } else OutlinedButton(onClick = { onEquipmentModeChanged(true) }) { Text("Equipment") } }; Text(if (equipmentMode) "Equipment register" else "Customer register", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
        if (!equipmentMode) {
            if (customers.isEmpty()) item { Text("Add a customer to begin.") }
            items(customers) { item -> SummaryRow("${item.name}\n${item.reference} · ${item.siteCount} site · ${item.equipmentCount} equipment", "Open") { nav.navigate("scope/Customer detail") } }
        } else {
            if (equipment.isEmpty()) item { Text("Add an equipment item to begin.") }
            items(equipment) { item -> SummaryRow("${item.technicianIdentifier ?: item.reference} · ${item.name}\n${item.customerName} · ${item.siteName}\nNext due ${item.nearestDueDate ?: "not scheduled"}", "Open") { nav.navigate("equipment/${item.id}") } }
        }
        item { OutlinedButton(onClick = { nav.navigate("scope/Add customer") }, modifier = Modifier.fillMaxWidth()) { Text("Add customer") } }
    }
}

@Composable
private fun EquipmentScreen(detail: EquipmentDetail, nav: NavHostController) {
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(detail.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(listOfNotNull(detail.technicianIdentifier, detail.reference).joinToString(" · "), style = MaterialTheme.typography.titleMedium)
            Text(if (detail.makeModel.isBlank()) "Make/model not supplied" else detail.makeModel)
            Text(detail.serialNumber?.let { "Serial $it" } ?: "Serial not supplied", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${detail.customerName}\n${detail.siteName}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { nav.navigate("scope/Book visit") }, Modifier.weight(1f)) { Text("Book visit") }; Button(onClick = { detail.workingItemId?.let { nav.navigate("inspection/$it") } }, Modifier.weight(1f), enabled = detail.workingItemId != null) { Text("Start / resume") } } }
        item { SectionTitle("Service plans") }
        items(detail.plans) { plan ->
            AccentCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(plan.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); StatusChip(if (plan.isOverdue) "Overdue" else "Due soon", urgency = plan.isOverdue) }
                Text("${plan.reference} · ${plan.interval}")
                Text("Due ${plan.dueDate}", fontWeight = FontWeight.Medium)
                Text(if (plan.state == "ACTIVE") "Current service remains due until explicitly fulfilled" else "Plan ${plan.state.lowercase()}", style = MaterialTheme.typography.bodySmall)
            }
        }
        item { SummaryRow("Corrective follow-ups", "Add follow-up") { nav.navigate("scope/Add follow-up") }; SummaryRow("Equipment history", "Open") { nav.navigate("scope/History") }; Text("Private equipment notes", style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
private fun InspectionScreen(draft: InspectionDraft, saveStatus: SaveStatus, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    viewModel.state.collectAsState().value.pendingResponseTransition?.let { transition ->
        AlertDialog(
            onDismissRequest = viewModel::cancelResponseTransition,
            title = { Text("Discard saved response detail?") },
            text = { Text("Changing this answer will discard the ${transition.detailBeingDiscarded}.") },
            confirmButton = { TextButton(onClick = viewModel::confirmResponseTransition) { Text("Discard and change") } },
            dismissButton = { TextButton(onClick = viewModel::cancelResponseTransition) { Text("Cancel") } },
        )
    }
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("${draft.equipmentReference} · ${draft.equipmentName}", style = MaterialTheme.typography.titleMedium)
            Text("${draft.visitReference} · ${draft.siteName}", style = MaterialTheme.typography.bodyMedium)
            SaveStateBanner(saveStatus)
            Text("Due ${draft.dueDate} · ${draft.interval} · Checklist revision ${draft.templateRevision}", style = MaterialTheme.typography.bodyMedium)
        }
        item { LabelledValue("Work performed · Customer report", draft.workPerformed.ifBlank { "Not recorded" }, public = true); LabelledValue("Private — not in customer report", draft.privateInternalNote.ifBlank { "Not recorded" }, public = false) }
        item { SectionTitle("Inspection responses"); Text("Unanswered and Not checked are never treated as OK.") }
        items(draft.questions, key = { it.snapshotItemId }) { question -> QuestionBlock(question, saveStatus is SaveStatus.Saving, viewModel) }
        item { StatusChip(if (draft.checklistReviewed) "Reviewed" else "Needs review", urgency = false); Text("Reviewed describes the checklist workflow, not equipment safety or obligation fulfillment.", style = MaterialTheme.typography.bodyMedium) }
        item { Button(onClick = { nav.navigate("review/${draft.visitId}") }, modifier = Modifier.fillMaxWidth(), enabled = saveStatus !is SaveStatus.Saving && saveStatus !is SaveStatus.Failed) { Text("Review completion") } }
    }
}

@Composable
private fun QuestionBlock(question: InspectionQuestion, saving: Boolean, viewModel: ServiceLoopViewModel) {
    AccentCard {
        Text("${question.position}. ${question.label}", style = MaterialTheme.typography.titleMedium)
        Text("${question.responseType.lowercase().replaceFirstChar { it.uppercase() }} · ${if (question.required) "Required" else "Optional"}", style = MaterialTheme.typography.bodySmall)
        when (question.responseType) {
            "STATUS" -> listOf(ResponseDisposition.OK to "OK", ResponseDisposition.ISSUE_FOUND to "Issue found", ResponseDisposition.NOT_APPLICABLE to "Not applicable", ResponseDisposition.NOT_CHECKED to "Not checked").forEach { (value, label) ->
                Row(Modifier.fillMaxWidth().testTag("response-${question.snapshotItemId}-${value.name}").selectable(selected = question.disposition == value, enabled = !saving, onClick = { viewModel.requestResponseChange(question.snapshotItemId, value, reason = if (value == ResponseDisposition.NOT_APPLICABLE) "Not applicable during this visit" else null) }).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = question.disposition == value, onClick = null); Text(label) }
            }
            else -> ValueQuestion(question, saving, viewModel)
        }
        if (question.disposition != ResponseDisposition.ISSUE_FOUND) question.reason?.takeIf { it.isNotBlank() }?.let { Text("Reason: $it", style = MaterialTheme.typography.bodyMedium) }
        if (question.disposition == ResponseDisposition.ISSUE_FOUND) {
            InlineFindingEditor(question, saving, viewModel)
        }
    }
}

@Composable
private fun InlineFindingEditor(question: InspectionQuestion, saving: Boolean, viewModel: ServiceLoopViewModel) {
    var text by rememberSaveable(question.snapshotItemId, question.reason) { mutableStateOf(question.reason.orEmpty()) }
    var expanded by rememberSaveable(question.snapshotItemId) { mutableStateOf(false) }
    val changed = text != question.reason.orEmpty()
    Surface(color = LocalServiceLoopColors.current.errorTint, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Finding details · Customer report", color = LocalServiceLoopColors.current.errorInk, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.testTag("finding-expand-${question.snapshotItemId}").semantics { contentDescription = if (expanded) "Collapse finding field" else "Expand finding field" },
                ) { Text(if (expanded) "Collapse" else "Expand") }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Public finding description") },
                placeholder = { Text("Describe what was found") },
                supportingText = { Text("May appear in the customer service record and report") },
                minLines = if (expanded) 10 else 3,
                maxLines = if (expanded) 10 else 3,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().testTag("finding-field-${question.snapshotItemId}"),
            )
            Button(
                onClick = { viewModel.requestResponseChange(question.snapshotItemId, ResponseDisposition.ISSUE_FOUND, reason = text) },
                enabled = !saving && changed,
                modifier = Modifier.fillMaxWidth().testTag("finding-save-${question.snapshotItemId}"),
            ) { Text("Save finding") }
        }
    }
}

@Composable
private fun ValueQuestion(question: InspectionQuestion, saving: Boolean, viewModel: ServiceLoopViewModel) {
    var value by remember(question.snapshotItemId, question.textValue, question.numberValue) { mutableStateOf(question.textValue ?: question.numberValue.orEmpty()) }
    OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text(if (question.responseType == "NUMBER") "Recorded value" else "Response") }, supportingText = { question.unit?.let { Text(it) } }, enabled = !saving, modifier = Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { viewModel.requestResponseChange(question.snapshotItemId, ResponseDisposition.VALUE, value = value) }, enabled = !saving && value.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Save response") }
        OutlinedButton(onClick = { viewModel.requestResponseChange(question.snapshotItemId, ResponseDisposition.NOT_APPLICABLE, reason = "Not applicable during this visit") }, enabled = !saving, modifier = Modifier.weight(1f)) { Text("Not applicable") }
    }
    if (question.disposition == ResponseDisposition.UNANSWERED) Text("Not recorded", color = LocalServiceLoopColors.current.errorInk)
}

@Composable
private fun CompletionReviewScreen(lines: List<CompletionLine>, padding: PaddingValues) {
    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Outcome and fulfillment are separate decisions.", style = MaterialTheme.typography.titleMedium); Text("Stage 2 finalization is not implemented in this milestone.") }
        items(lines) { line -> CompletionLineCard(line) }
        item { Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) { Column(Modifier.padding(16.dp)) { Text("Customer report review", style = MaterialTheme.typography.titleMedium); Text("Public work, explicit unanswered responses, due effects, findings, and selected photos will be reviewed here. Private notes stay excluded.") } } }
        item { Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Finalize record — available in Stage 2") }; Text("No record, due date, task, or success message is created from this proof screen.", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun CompletionLineCard(line: CompletionLine) {
    AccentCard {
        Text("${line.equipmentReference} · ${line.equipmentName}", style = MaterialTheme.typography.labelLarge)
        Text(line.serviceName, style = MaterialTheme.typography.titleMedium)
        Text("Outcome", style = MaterialTheme.typography.labelLarge)
        Text(line.outcome?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Choose outcome")
        when (line.fulfillmentEligibility) {
            FulfillmentEligibility.ELIGIBLE -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = line.fulfillsCurrentObligation, onCheckedChange = null); Column { Text("Fulfills current obligation", fontWeight = FontWeight.Medium); Text(if (line.fulfillsCurrentObligation) "Explicitly selected" else "Eligible, not selected — outstanding obligation is preserved", style = MaterialTheme.typography.bodySmall) } }
            FulfillmentEligibility.NO_CURRENT_OBLIGATION -> Text("Fulfillment unavailable — one-off work has no recurring obligation to fulfill.", style = MaterialTheme.typography.bodyMedium)
            FulfillmentEligibility.OUTCOME_INELIGIBLE -> Text("Fulfillment unavailable — ${line.outcome?.replace('_', ' ')?.lowercase()} work cannot fulfill the current obligation.", style = MaterialTheme.typography.bodyMedium)
            FulfillmentEligibility.CHECKLIST_NOT_REVIEWED -> Text("Fulfillment unavailable — review the assigned checklist first.", style = MaterialTheme.typography.bodyMedium)
        }
        when {
            line.fulfillmentEligibility == FulfillmentEligibility.NO_CURRENT_OBLIGATION -> Text("No recurring due date changes")
            line.fulfillsCurrentObligation && line.dueDate != null && line.proposedNextDueDate != null -> Text("Due before ${line.dueDate} → Proposed next due ${line.proposedNextDueDate}")
            line.fulfillsCurrentObligation -> Text("Recurring due-date proposal unavailable", color = LocalServiceLoopColors.current.urgencyInk)
            line.dueDate != null -> Text("Remains due ${line.dueDate}", color = LocalServiceLoopColors.current.urgencyInk)
            else -> Text("Current recurring obligation remains outstanding", color = LocalServiceLoopColors.current.urgencyInk)
        }
        if (line.workPerformed.isNotBlank()) Text(line.workPerformed, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SaveStateBanner(status: SaveStatus) {
    val colors = LocalServiceLoopColors.current
    val (text, background, foreground) = when (status) {
        SaveStatus.Idle -> Triple("Not saved", colors.errorTint, colors.errorInk)
        SaveStatus.Saving -> Triple("Saving…", colors.workflowTint, colors.workflowInk)
        is SaveStatus.Saved -> Triple("Saved on this device · ${formatTime(status.atEpochMillis)}", colors.confirmedTint, colors.confirmedInk)
        is SaveStatus.Failed -> Triple("Not saved — ${status.message}. Last saved ${formatTime(status.lastSavedAtEpochMillis)}", colors.errorTint, colors.errorInk)
    }
    Text(text, color = foreground, modifier = Modifier.fillMaxWidth().background(background, MaterialTheme.shapes.small).padding(12.dp), style = MaterialTheme.typography.labelLarge)
}

@Composable private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })

@Composable private fun AccentCard(content: @Composable ColumnScope.() -> Unit) = Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }

@Composable private fun SummaryRow(text: String, action: String, onClick: () -> Unit) { Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) { Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(text, Modifier.weight(1f)); Spacer(Modifier.width(8.dp)); Text(action, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium) } } }

@Composable private fun StatusChip(text: String, urgency: Boolean) { val colors = LocalServiceLoopColors.current; Text(text, color = if (urgency) colors.urgencyInk else colors.workflowInk, modifier = Modifier.background(if (urgency) colors.urgencyTint else colors.workflowTint, MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium) }

@Composable private fun LabelledValue(label: String, value: String, public: Boolean) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (public) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant); Text(value); Spacer(Modifier.height(8.dp)); HorizontalDivider() }

@Composable private fun HonestPlaceholder(padding: PaddingValues, message: String) { Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text(message, style = MaterialTheme.typography.titleMedium) } }

private fun formatTime(epochMillis: Long?): String = epochMillis?.let { DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it)) } ?: "not yet"
