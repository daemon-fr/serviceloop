package com.v16studio.serviceloop.data

import com.v16studio.serviceloop.domain.CustomerType
import com.v16studio.serviceloop.domain.WorkSubjectType

/**
 * One validation boundary for the persisted flexible-work subject forms.
 * It deliberately rejects malformed combinations instead of normalizing them.
 */
object WorkSubjectValidator {
    fun validateWorkItem(
        item: WorkItemEntity,
        customerType: CustomerType? = null,
        servicePlanEquipmentId: String? = null,
    ) {
        when (WorkSubjectType.fromCode(item.subjectType)) {
            WorkSubjectType.SITE -> {
                require(item.equipmentId == null) { "SITE work cannot reference Equipment" }
                require(item.equipmentDescriptionSnapshot == null) { "SITE work cannot have an Equipment description" }
                require(item.equipmentNameSnapshot == null && item.equipmentReferenceSnapshot == null) { "SITE work cannot have Equipment identity" }
                require(item.equipmentIdentifierSnapshot == null && item.equipmentMakeSnapshot == null && item.equipmentModelSnapshot == null && item.equipmentSerialSnapshot == null) { "SITE work cannot have Equipment identification" }
                require(item.servicePlanId == null && item.capturedObligationId == null) { "SITE work cannot carry a service plan" }
                require(item.planReferenceSnapshot == null && item.dueDateSnapshot == null && item.intervalCountSnapshot == null && item.intervalUnitSnapshot == null) { "SITE work cannot carry obligation data" }
                require(item.fulfillsCurrentObligation != true && item.confirmedNextDueDate == null && item.nextDueDateCalculated == null && item.nextDueOverrideReason == null) { "SITE work cannot fulfill a recurring obligation" }
            }
            WorkSubjectType.EQUIPMENT -> if (item.equipmentId != null) {
                require(!item.equipmentNameSnapshot.isNullOrBlank() && !item.equipmentReferenceSnapshot.isNullOrBlank()) { "Known Equipment work needs a frozen Equipment identity" }
                require(item.equipmentDescriptionSnapshot == null) { "Known Equipment work cannot have an unidentified description" }
                if (item.servicePlanId == null) require(item.capturedObligationId == null && item.fulfillsCurrentObligation != true) { "Ad-hoc Equipment work cannot carry recurrence" }
                if (item.servicePlanId != null) {
                    require(customerType == CustomerType.STANDARD) { "Recurring service requires a Standard customer" }
                    require(servicePlanEquipmentId == item.equipmentId) { "Service plan does not belong to this Equipment" }
                }
            } else {
                require(item.equipmentNameSnapshot == null && item.equipmentReferenceSnapshot == null) { "Unidentified Equipment work cannot have registered Equipment identity" }
                require(item.equipmentIdentifierSnapshot == null && item.equipmentMakeSnapshot == null && item.equipmentModelSnapshot == null && item.equipmentSerialSnapshot == null) { "Unidentified Equipment work cannot have Equipment identification" }
                require(item.servicePlanId == null && item.capturedObligationId == null) { "Unidentified Equipment work cannot carry a service plan" }
                require(item.planReferenceSnapshot == null && item.dueDateSnapshot == null && item.intervalCountSnapshot == null && item.intervalUnitSnapshot == null) { "Unidentified Equipment work cannot carry obligation data" }
                require(item.fulfillsCurrentObligation != true && item.confirmedNextDueDate == null && item.nextDueDateCalculated == null && item.nextDueOverrideReason == null) { "Unidentified Equipment work cannot fulfill a recurring obligation" }
                item.equipmentDescriptionSnapshot?.let { description ->
                    require(description.trim().isNotBlank() && description.trim().length <= 500) { "Equipment description must be 500 characters or fewer" }
                }
            }
        }
    }

    fun validateFinal(item: FinalWorkItemEntity) {
        when (WorkSubjectType.fromCode(item.subjectType)) {
            WorkSubjectType.SITE -> {
                require(item.equipmentId == null && item.equipmentName == null && item.equipmentReference == null && item.equipmentDescription == null) { "SITE final history cannot have Equipment identity" }
                require(item.equipmentIdentifier == null && item.equipmentMake == null && item.equipmentModel == null && item.equipmentSerial == null) { "SITE final history cannot have Equipment identification" }
                require(item.planId == null && item.capturedObligationId == null && !item.fulfilledObligation) { "SITE final history cannot carry recurrence" }
            }
            WorkSubjectType.EQUIPMENT -> if (item.equipmentId != null) {
                require(!item.equipmentName.isNullOrBlank() && !item.equipmentReference.isNullOrBlank()) { "Known Equipment final history needs a frozen Equipment identity" }
                require(item.equipmentDescription == null) { "Known Equipment final history cannot have an unidentified description" }
                if (item.planId == null) require(item.capturedObligationId == null && !item.fulfilledObligation) { "Ad-hoc Equipment final history cannot carry recurrence" }
            } else {
                require(item.equipmentName == null && item.equipmentReference == null) { "Unidentified Equipment final history cannot have registered identity" }
                require(item.equipmentIdentifier == null && item.equipmentMake == null && item.equipmentModel == null && item.equipmentSerial == null) { "Unidentified Equipment final history cannot have Equipment identification" }
                require(item.planId == null && item.capturedObligationId == null && !item.fulfilledObligation) { "Unidentified Equipment final history cannot carry recurrence" }
                item.equipmentDescription?.let { description -> require(description.trim().isNotBlank() && description.trim().length <= 500) { "Equipment description must be 500 characters or fewer" } }
            }
        }
    }
}
