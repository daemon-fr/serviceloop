package com.v16studio.serviceloop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.v16studio.serviceloop.domain.CompletionLine
import com.v16studio.serviceloop.domain.FulfillmentEligibility
import com.v16studio.serviceloop.domain.InspectionDraft
import com.v16studio.serviceloop.domain.ServiceDraftFieldKeys
import com.v16studio.serviceloop.ui.designsystem.LocalServiceLoopTokens
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopChoiceGroup
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrimaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSelectionOption
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSurfaceCard
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextAction
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens
import java.time.LocalDate

@Composable
internal fun ServiceCompletionSection(line: CompletionLine, draft: InspectionDraft, viewModel: ServiceLoopViewModel, editingEnabled: Boolean, outcomeRequester: BringIntoViewRequester, reasonRequester: BringIntoViewRequester, fulfillmentRequester: BringIntoViewRequester, nextDueRequester: BringIntoViewRequester) {
    val visitId = draft.visitId
    val workItemId = draft.workItemId
    ServiceLoopSurfaceCard(modifier = Modifier.fillMaxWidth().testTag("service-outcome")) {
        Text("Outcome", style = MaterialTheme.typography.titleLarge)
        ServiceLoopChoiceGroup(
            options = listOf("PERFORMED" to "Performed", "PARTLY_PERFORMED" to "Partly performed", "NOT_PERFORMED" to "Not performed"),
            selected = line.outcome.orEmpty(),
            onSelected = { viewModel.chooseOutcome(workItemId, visitId, it) },
            enabled = editingEnabled,
            testTagPrefix = "outcome-$workItemId",
            modifier = Modifier.bringIntoViewRequester(outcomeRequester),
        )
        if (line.outcome == "NOT_PERFORMED") {
            val initial = draft.rawInputs[ServiceDraftFieldKeys.NOT_PERFORMED_REASON] ?: line.notPerformedReason.orEmpty()
            var reason by remember(workItemId, initial) { mutableStateOf(initial) }
            OutlinedTextField(reason, { reason = it; viewModel.scheduleNotPerformedReason(workItemId, visitId, it) }, label = { Text("Not performed reason") }, enabled = editingEnabled, modifier = Modifier.fillMaxWidth().bringIntoViewRequester(reasonRequester).testTag("not-performed-reason"))
        }
        if (line.outcome != null) {
            when (line.fulfillmentEligibility) {
                FulfillmentEligibility.ELIGIBLE -> {
                    if (line.outcome == "PARTLY_PERFORMED") {
                        Text("Does this complete the due service?", style = MaterialTheme.typography.titleMedium)
                        Column(Modifier.bringIntoViewRequester(fulfillmentRequester), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm)) {
                            ServiceLoopSelectionOption(
                                selected = line.fulfillsCurrentObligation == true,
                                onClick = { viewModel.chooseFulfillment(workItemId, visitId, true) },
                                label = "Fulfill — advance next due",
                                enabled = editingEnabled,
                                modifier = Modifier.testTag("fulfill-$workItemId"),
                            )
                            ServiceLoopSelectionOption(
                                selected = line.fulfillsCurrentObligation == false,
                                onClick = { viewModel.chooseFulfillment(workItemId, visitId, false) },
                                label = "Keep due — service remains outstanding",
                                enabled = editingEnabled,
                                modifier = Modifier.testTag("keep-due-$workItemId"),
                            )
                        }
                    }
                }
                FulfillmentEligibility.HISTORY_ONLY -> Text("History only — current due date is unchanged.")
                FulfillmentEligibility.NO_CURRENT_OBLIGATION -> Text("No recurring due date for this Service.")
                FulfillmentEligibility.CHECKLIST_INCOMPLETE -> Text("Complete all required checklist questions")
                FulfillmentEligibility.PLAN_INELIGIBLE -> Text("This plan is no longer active; it cannot advance the due date.")
                FulfillmentEligibility.CURRENT_OBLIGATION_CHANGED -> Text("The service due date has changed. Review this Service before finalizing.")
                FulfillmentEligibility.OUTCOME_INELIGIBLE -> Unit
            }
            if (line.fulfillsCurrentObligation == true && line.confirmedNextDueDate != null) {
                Text("Next due · ${formatServiceLoopDate(line.confirmedNextDueDate)}", style = MaterialTheme.typography.titleMedium)
                if (line.nextDueDateCalculated == true) Text("Calculated next due")
                else Text("Manual override · ${line.nextDueOverrideReason.orEmpty()}")
                var changeDue by rememberSaveable(workItemId) { mutableStateOf(false) }
                if (!changeDue && editingEnabled) ServiceLoopTextAction("Change next due", { changeDue = true }, Modifier.testTag("change-next-due"))
                if (changeDue && editingEnabled) {
                    val initialDate = draft.rawInputs[ServiceDraftFieldKeys.OVERRIDE_DATE] ?: line.confirmedNextDueDate
                    val initialReason = draft.rawInputs[ServiceDraftFieldKeys.OVERRIDE_REASON] ?: line.nextDueOverrideReason.orEmpty()
                    var date by remember(workItemId, initialDate) { mutableStateOf(initialDate) }
                    var reason by remember(workItemId, initialReason) { mutableStateOf(initialReason) }
                    OutlinedTextField(date, { date = it; viewModel.scheduleRecurrenceOverrideDate(workItemId, it) }, label = { Text("Next due (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth().testTag("override-date"))
                    OutlinedTextField(reason, { reason = it; viewModel.scheduleRecurrenceOverrideReason(workItemId, it) }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth().testTag("override-reason"))
                    val validDate = runCatching { LocalDate.parse(date).isAfter(LocalDate.parse(viewModel.state.value.serviceProgress?.serviceDate ?: "")) }.getOrDefault(false)
                    ServiceLoopPrimaryButton("Apply override", { viewModel.applyRecurrenceOverride(workItemId, visitId, date, reason); changeDue = false }, Modifier.fillMaxWidth().testTag("apply-override"), enabled = validDate && reason.isNotBlank())
                    ServiceLoopTextAction("Cancel", { changeDue = false })
                }
            } else if (line.fulfillsCurrentObligation == true && line.confirmedNextDueDate == null) {
                val hasOverrideDraft = draft.rawInputs.containsKey(ServiceDraftFieldKeys.OVERRIDE_DATE) ||
                    draft.rawInputs.containsKey(ServiceDraftFieldKeys.OVERRIDE_REASON)
                Column(
                    Modifier.fillMaxWidth().bringIntoViewRequester(nextDueRequester).testTag("missing-next-due-resolution"),
                    verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm),
                ) {
                    Text("Next due needs confirmation", style = MaterialTheme.typography.titleMedium)
                    line.calculatedNextDueDate?.let { Text("Calculated date · ${formatServiceLoopDate(it)} (not saved)") }
                        ?: Text("A calculated date is not available for this Service.")
                    if (hasOverrideDraft && editingEnabled) {
                        var date by remember(workItemId, draft.rawInputs[ServiceDraftFieldKeys.OVERRIDE_DATE]) {
                            mutableStateOf(draft.rawInputs[ServiceDraftFieldKeys.OVERRIDE_DATE].orEmpty())
                        }
                        var reason by remember(workItemId, draft.rawInputs[ServiceDraftFieldKeys.OVERRIDE_REASON]) {
                            mutableStateOf(draft.rawInputs[ServiceDraftFieldKeys.OVERRIDE_REASON].orEmpty())
                        }
                        OutlinedTextField(date, { date = it; viewModel.scheduleRecurrenceOverrideDate(workItemId, it) }, label = { Text("Next due (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth().testTag("override-date"))
                        OutlinedTextField(reason, { reason = it; viewModel.scheduleRecurrenceOverrideReason(workItemId, it) }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth().testTag("override-reason"))
                        val validDate = runCatching { LocalDate.parse(date).isAfter(LocalDate.parse(viewModel.state.value.serviceProgress?.serviceDate ?: "")) }.getOrDefault(false)
                        ServiceLoopPrimaryButton("Apply override", { viewModel.applyRecurrenceOverride(workItemId, visitId, date, reason) }, Modifier.fillMaxWidth().testTag("apply-override"), enabled = validDate && reason.isNotBlank())
                    } else if (editingEnabled && line.calculatedNextDueDate != null) {
                        ServiceLoopTextAction(
                            "Use calculated date",
                            { viewModel.useCalculatedNextDue(workItemId, visitId) },
                            Modifier.testTag("use-calculated-next-due"),
                        )
                    }
                }
            } else if (line.currentObligationOutstanding && line.dueDate != null) {
                Text("Remains due · ${formatServiceLoopDate(line.dueDate)}")
            }
        }
    }
}
