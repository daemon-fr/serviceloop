package com.v16studio.serviceloop.data

/** Column declarations exported by each supported Recovery producer, sourced from frozen Room schemas. */
internal object RecoverySourceShapes {
    private val shapes: Map<Int, Map<String, Set<String>>> = buildMap {
        var shape = mapOf(
            "attachments" to "id,ownerType,ownerId,storedRelativePath,sha256,originalDisplayName,mimeType,includedInCustomerReport,availability,byteSize,caption".split(",").toSet(),
            "business_profiles" to "id,businessName,technicianName,phone,email,postalAddress,zoneId,modifiedAtEpochMillis".split(",").toSet(),
            "change_entries" to "id,subjectType,subjectId,changeType,eventDate,recordedAtEpochMillis,reason,oldValue,newValue,customerId,siteId,equipmentId,recordId,customerNameSnapshot,siteNameSnapshot,equipmentNameSnapshot".split(",").toSet(),
            "checklist_item_snapshots" to "id,templateSnapshotId,position,label,responseType,unit,required,privateGuidance".split(",").toSet(),
            "contact_notes" to "id,reference,customerId,siteId,equipmentId,channel,occurredAtEpochMillis,outcome,privateNote,createdAtEpochMillis,editedAtEpochMillis,enteredInError,errorReason".split(",").toSet(),
            "correction_drafts" to "id,recordId,baseRevisionId,reason,actualServiceDate,customerName,siteName,siteAddress,businessName,technicianName,publicNote,privateNote,scheduleAcknowledged,createdAtEpochMillis,modifiedAtEpochMillis,commitToken,followUpEffectsJson,newFollowUpsJson".split(",").toSet(),
            "correction_work_items" to "id,draftId,sourceFinalWorkItemId,position,outcome,publicWorkNote,notPerformedReason,fulfilledObligation,proposedNextDueDate,checklistJson,partsJson,photosJson,nextDueDateCalculated,nextDueOverrideReason".split(",").toSet(),
            "customers" to "id,reference,name,contactName,phone,email,privateNote,state".split(",").toSet(),
            "dispatch_item_bindings" to "dispatchVisitId,dispatchItemId,localWorkItemId,equipmentReferenceSnapshot,taskNameSnapshot,servicePlanReferenceSnapshot,dueDateSnapshot,assignedTechniciansJson,assignmentMeaning,localRole,documentationDisposition,deferredToTechnicianId,deferredToName".split(",").toSet(),
            "dispatch_outbox_item_assignees" to "dispatchItemId,technicianId".split(",").toSet(),
            "dispatch_outbox_items" to "dispatchItemId,dispatchVisitId,position,equipmentId,taskName,servicePlanReference,dueDateSnapshot".split(",").toSet(),
            "dispatch_outbox_visit_teams" to "dispatchVisitId,teamId".split(",").toSet(),
            "dispatch_outbox_visits" to "dispatchVisitId,managerReference,siteId,serviceDate,appointmentLocalTime,appointmentZoneId,instructions,lastExportedGeneration,lastExportedMaterialHash,lastExportedAtEpochMillis,createdAtEpochMillis,modifiedAtEpochMillis,concludedAtEpochMillis".split(",").toSet(),
            "dispatch_team_members" to "teamId,technicianId,isLeader".split(",").toSet(),
            "dispatch_teams" to "id,name,createdAtEpochMillis,modifiedAtEpochMillis".split(",").toSet(),
            "dispatch_technicians" to "technicianId,displayName,createdAtEpochMillis,modifiedAtEpochMillis".split(",").toSet(),
            "dispatch_visit_bindings" to "dispatchVisitId,localVisitId,appliedGeneration,packageId,senderLabel,managerReference,instructionsSnapshot,participantSnapshotJson,leaderIdsJson,teamSnapshotJson,appliedMaterialHash,controlledFingerprint,importedAtEpochMillis,updatedAtEpochMillis".split(",").toSet(),
            "equipment" to "id,siteId,reference,technicianIdentifier,name,make,model,serialNumber,privateNotes,state".split(",").toSet(),
            "equipment_moves" to "id,equipmentId,oldSiteId,newSiteId,effectiveDate,reason,recordedAtEpochMillis".split(",").toSet(),
            "final_checklist_items" to "id,finalWorkItemId,position,templateSnapshotId,templateRevision,label,responseType,unit,required,disposition,textValue,numberValue,reason".split(",").toSet(),
            "final_dispatch_items" to "finalWorkItemId,dispatchItemId,assignedTechniciansJson,assignmentMeaning,localDocumentationRole".split(",").toSet(),
            "final_dispatch_visits" to "revisionId,dispatchVisitId,generation,managerReference,senderLabel,documentingTechnicianId,documentingTechnicianName".split(",").toSet(),
            "final_part_entries" to "id,finalWorkItemId,position,description,quantity,unit".split(",").toSet(),
            "final_photo_entries" to "id,finalWorkItemId,position,sourceAttachmentId,storedRelativePath,sha256,byteSize,mimeType,caption,addedInCorrection,addedAtEpochMillis".split(",").toSet(),
            "final_record_revisions" to "id,recordId,revisionNumber,visitReference,actualServiceDate,recordedAtEpochMillis,customerName,siteName,siteAddress,businessName,technicianName,businessPhone,businessEmail,businessAddress,businessZoneId,privateInternalNote,customerReference,siteReference,supersedesRevisionId,correctionReason,publicNote".split(",").toSet(),
            "final_records" to "id,visitId,currentRevisionId,createdAtEpochMillis,voided,voidedAtEpochMillis,publicVoidReason,privateVoidReason".split(",").toSet(),
            "final_work_items" to "id,revisionId,position,sourceWorkItemId,equipmentId,equipmentName,equipmentReference,equipmentIdentifier,equipmentMake,equipmentModel,equipmentSerial,serviceName,planId,planReference,outcome,publicWorkNote,notPerformedReason,fulfilledObligation,oldDueDate,nextDueDate,intervalCount,intervalUnit,capturedObligationId,privateInternalNote,nextDueDateCalculated,nextDueOverrideReason".split(",").toSet(),
            "follow_up_events" to "id,followUpId,eventType,occurredAtEpochMillis,reason,dueDate".split(",").toSet(),
            "follow_ups" to "id,reference,type,title,dueDate,state,customerId,siteId,equipmentId,privatePlanningNote,sourceVisitId,sourceWorkItemId,updatedAtEpochMillis,closedAtEpochMillis,closureReason".split(",").toSet(),
            "part_entries" to "id,workItemId,description,quantity,unit,modifiedAtEpochMillis".split(",").toSet(),
            "plan_schedule_changes" to "id,planId,oldDueDate,newDueDate,reason,changedAtEpochMillis".split(",").toSet(),
            "recovery_metadata" to "id,datasetId,firstBusinessWriteAtEpochMillis,lastBusinessWriteAtEpochMillis,lastBackupAttemptAtEpochMillis,lastVerifiedFullBackupAtEpochMillis,lastVerifiedSnapshotAtEpochMillis,lastVerifiedDestination,lastVerifiedSize,backupReminderDays,restoredFromIncompleteCopy,restrictedRecoveryState,adoptionToken".split(",").toSet(),
            "report_renditions" to "id,revisionId,versionNumber,generatedAtEpochMillis,relativePath,sha256,byteSize,pageCount,status,kind,failureMessage".split(",").toSet(),
            "reusable_template_items" to "id,revisionId,position,label,responseType,unit,required,privateGuidance".split(",").toSet(),
            "reusable_template_revisions" to "id,templateId,revisionNumber,nameSnapshot,createdAtEpochMillis".split(",").toSet(),
            "reusable_templates" to "id,reference,name,currentRevisionId,state,modifiedAtEpochMillis".split(",").toSet(),
            "service_obligations" to "id,planId,sequence,dueDate,createdAtEpochMillis,consumedAtEpochMillis,consumedByRevisionId".split(",").toSet(),
            "service_plans" to "id,equipmentId,reference,name,intervalCount,intervalUnit,currentDueDate,state,currentObligationId,lastCountedCompletionDate,lastCountedRevisionId,reusableTemplateId".split(",").toSet(),
            "sites" to "id,customerId,reference,name,address,privateAccessNotes,contactName,phone,email,isDefault,state".split(",").toSet(),
            "technician_identity" to "id,technicianId,displayName,createdAtEpochMillis,modifiedAtEpochMillis".split(",").toSet(),
            "template_snapshots" to "id,sourceTemplateId,templateName,revision,capturedAtEpochMillis".split(",").toSet(),
            "visit_claims" to "obligationId,visitId,claimedAtEpochMillis".split(",").toSet(),
            "visit_schedule_events" to "id,visitId,eventType,oldServiceDate,newServiceDate,oldScheduledAtEpochMillis,newScheduledAtEpochMillis,reason,occurredAtEpochMillis".split(",").toSet(),
            "work_item_private_drafts" to "workItemId,internalNote".split(",").toSet(),
            "work_item_public_drafts" to "workItemId,workPerformed".split(",").toSet(),
            "work_items" to "id,visitId,equipmentId,servicePlanId,capturedObligationId,templateSnapshotId,equipmentNameSnapshot,equipmentReferenceSnapshot,serviceNameSnapshot,planReferenceSnapshot,dueDateSnapshot,intervalCountSnapshot,intervalUnitSnapshot,checklistReviewed,outcome,fulfillsCurrentObligation,notPerformedReason,confirmedNextDueDate,nextDueDateCalculated,nextDueOverrideReason,equipmentIdentifierSnapshot,equipmentMakeSnapshot,equipmentModelSnapshot,equipmentSerialSnapshot".split(",").toSet(),
            "working_responses" to "id,workItemId,checklistItemSnapshotId,disposition,textValue,numberValue,reason,modifiedAtEpochMillis".split(",").toSet(),
            "working_visits" to "id,reference,customerId,siteId,actualServiceDate,customerNameSnapshot,siteNameSnapshot,siteAddressSnapshot,state,modifiedAtEpochMillis,customerReferenceSnapshot,siteReferenceSnapshot,reportBusinessNameSnapshot,reportTechnicianNameSnapshot,reportPhoneSnapshot,reportEmailSnapshot,reportPostalAddressSnapshot,reportZoneIdSnapshot,scheduledAtEpochMillis,appointmentZoneId,scheduleChangeReason,cancellationReason,cancelledAtEpochMillis".split(",").toSet(),
        )
        put(9, shape)
        shape = shape + mapOf(
            "working_responses" to "id,workItemId,checklistItemSnapshotId,disposition,textValue,numberValue,reason,modifiedAtEpochMillis,issueFoundReasonDraft,notApplicableReasonDraft".split(",").toSet(),
        )
        put(10, shape)
        shape = shape + mapOf(
            "reminder_preferences" to "id,dailySummaryEnabled,summaryHour,summaryMinute,summaryDaysMask,dueSoonHorizonDays,includeDueServices,includeVisits,includeFollowUps,includeUnfinishedVisits,includeBackupReminder,appointmentAlertsEnabled,defaultAppointmentLeadMinutes".split(",").toSet(),
            "working_visits" to "id,reference,customerId,siteId,actualServiceDate,customerNameSnapshot,siteNameSnapshot,siteAddressSnapshot,state,modifiedAtEpochMillis,customerReferenceSnapshot,siteReferenceSnapshot,reportBusinessNameSnapshot,reportTechnicianNameSnapshot,reportPhoneSnapshot,reportEmailSnapshot,reportPostalAddressSnapshot,reportZoneIdSnapshot,scheduledAtEpochMillis,appointmentZoneId,scheduleChangeReason,cancellationReason,cancelledAtEpochMillis,appointmentReminderLeadMinutes".split(",").toSet(),
        )
        put(11, shape)
        shape = shape + mapOf(
            "dispatch_outbox_visits" to "dispatchVisitId,managerReference,siteId,serviceDate,appointmentLocalTime,appointmentZoneId,instructions,lastExportedGeneration,lastExportedMaterialHash,lastExportedAtEpochMillis,createdAtEpochMillis,modifiedAtEpochMillis,concludedAtEpochMillis,canceledAtEpochMillis,cancellationReason,lastExportedCancellationAtEpochMillis".split(",").toSet(),
            "working_visits" to "id,reference,customerId,siteId,actualServiceDate,customerNameSnapshot,siteNameSnapshot,siteAddressSnapshot,state,modifiedAtEpochMillis,customerReferenceSnapshot,siteReferenceSnapshot,reportBusinessNameSnapshot,reportTechnicianNameSnapshot,reportPhoneSnapshot,reportEmailSnapshot,reportPostalAddressSnapshot,reportZoneIdSnapshot,scheduledAtEpochMillis,appointmentZoneId,scheduleChangeReason,cancellationReason,cancelledAtEpochMillis,cancellationOrigin,appointmentReminderLeadMinutes".split(",").toSet(),
        )
        put(12, shape)
        shape = shape + mapOf(
            "dispatch_technicians" to "technicianId,displayName,createdAtEpochMillis,modifiedAtEpochMillis,designation".split(",").toSet(),
            "technician_identity" to "id,technicianId,displayName,createdAtEpochMillis,modifiedAtEpochMillis,designation".split(",").toSet(),
        )
        put(13, shape)
        shape = shape + mapOf(
            "working_input_buffers" to "workItemId,fieldKey,rawValue,modifiedAtEpochMillis".split(",").toSet(),
        )
        put(14, shape)
        shape = shape + mapOf(
            "customers" to "id,reference,name,contactName,phone,email,privateNote,state,customerType".split(",").toSet(),
            "dispatch_item_bindings" to "dispatchVisitId,dispatchItemId,localWorkItemId,equipmentReferenceSnapshot,taskNameSnapshot,servicePlanReferenceSnapshot,dueDateSnapshot,assignedTechniciansJson,assignmentMeaning,localRole,documentationDisposition,deferredToTechnicianId,deferredToName,subjectType,equipmentDescriptionSnapshot".split(",").toSet(),
            "dispatch_outbox_items" to "dispatchItemId,dispatchVisitId,position,equipmentId,taskName,servicePlanReference,dueDateSnapshot,subjectType,equipmentDescription".split(",").toSet(),
            "final_work_items" to "id,revisionId,position,sourceWorkItemId,equipmentId,equipmentName,equipmentReference,equipmentIdentifier,equipmentMake,equipmentModel,equipmentSerial,serviceName,planId,planReference,outcome,publicWorkNote,notPerformedReason,fulfilledObligation,oldDueDate,nextDueDate,intervalCount,intervalUnit,capturedObligationId,privateInternalNote,nextDueDateCalculated,nextDueOverrideReason,subjectType,equipmentDescription".split(",").toSet(),
            "work_items" to "id,visitId,equipmentId,servicePlanId,capturedObligationId,templateSnapshotId,equipmentNameSnapshot,equipmentReferenceSnapshot,serviceNameSnapshot,planReferenceSnapshot,dueDateSnapshot,intervalCountSnapshot,intervalUnitSnapshot,checklistReviewed,outcome,fulfillsCurrentObligation,notPerformedReason,confirmedNextDueDate,nextDueDateCalculated,nextDueOverrideReason,equipmentIdentifierSnapshot,equipmentMakeSnapshot,equipmentModelSnapshot,equipmentSerialSnapshot,subjectType,equipmentDescriptionSnapshot".split(",").toSet(),
        )
        put(15, shape)
        shape = shape + mapOf(
            "customer_contacts" to "id,customerId,personName,channel,value,createdAtEpochMillis,modifiedAtEpochMillis".split(",").toSet(),
            "dispatch_outbox_items" to "dispatchItemId,dispatchVisitId,position,equipmentId,taskName,servicePlanReference,dueDateSnapshot,subjectType,equipmentDescription,reusableTemplateId".split(",").toSet(),
            "dispatch_technicians" to "technicianId,displayName,createdAtEpochMillis,modifiedAtEpochMillis,designation,notes".split(",").toSet(),
            "final_record_revisions" to "id,recordId,revisionNumber,visitReference,actualServiceDate,recordedAtEpochMillis,customerName,siteName,siteAddress,businessName,technicianName,businessPhone,businessEmail,businessAddress,businessZoneId,privateInternalNote,customerReference,siteReference,supersedesRevisionId,correctionReason,publicNote,technicianDesignation".split(",").toSet(),
        )
        put(16, shape)
        shape = shape + mapOf(
            "trusted_service_loop_ids" to "peerId,name,createdAtEpochMillis,modifiedAtEpochMillis".split(",").toSet(),
        )
        put(17, shape)
        shape = shape + mapOf(
            "aggregate_report_renditions" to "id,aggregateReportId,relativePath,sha256,byteSize,pageCount,generatedAtEpochMillis,status,failureReason".split(",").toSet(),
            "aggregate_report_sources" to "aggregateReportId,sourceOrder,sourceFinalRevisionId,sourceKind,visitId,sourceEntityId".split(",").toSet(),
            "aggregate_reports" to "id,customerId,customerSnapshotJson,siteId,equipmentId,fromDate,throughDate,businessSnapshotJson,createdAtEpochMillis,status".split(",").toSet(),
            "attachments" to "id,ownerType,ownerId,storedRelativePath,sha256,originalDisplayName,mimeType,includedInCustomerReport,availability,byteSize,caption,visibility".split(",").toSet(),
            "customer_contacts" to "id,customerId,personName,channel,value,createdAtEpochMillis,modifiedAtEpochMillis,notes,position".split(",").toSet(),
            "data_transfer_bindings" to "id,originWorkspaceId,entityType,sourceEntityId,localEntityId,appliedSourceFingerprint,importedAtEpochMillis".split(",").toSet(),
            "dispatch_outbox_items" to "dispatchItemId,dispatchVisitId,position,equipmentId,taskName,servicePlanReference,dueDateSnapshot,subjectType,equipmentDescription,reusableTemplateId,localWorkItemId".split(",").toSet(),
            "dispatch_outbox_visits" to "dispatchVisitId,managerReference,siteId,serviceDate,appointmentLocalTime,appointmentZoneId,instructions,lastExportedGeneration,lastExportedMaterialHash,lastExportedAtEpochMillis,createdAtEpochMillis,modifiedAtEpochMillis,concludedAtEpochMillis,canceledAtEpochMillis,cancellationReason,lastExportedCancellationAtEpochMillis,localVisitId".split(",").toSet(),
            "dispatch_visit_bindings" to "dispatchVisitId,localVisitId,appliedGeneration,packageId,senderLabel,managerReference,instructionsSnapshot,participantSnapshotJson,leaderIdsJson,teamSnapshotJson,appliedMaterialHash,controlledFingerprint,importedAtEpochMillis,updatedAtEpochMillis,assignmentIssuerId".split(",").toSet(),
            "final_dispatch_visits" to "revisionId,dispatchVisitId,generation,managerReference,senderLabel,documentingTechnicianId,documentingTechnicianName,assignmentMaterialHash,assignmentIssuerId".split(",").toSet(),
            "final_photo_entries" to "id,finalWorkItemId,position,sourceAttachmentId,storedRelativePath,sha256,byteSize,mimeType,caption,addedInCorrection,addedAtEpochMillis,includedInCustomerReport,visibility".split(",").toSet(),
            "remote_final_results" to "id,resultId,sourceFinalRevisionId,dispatchVisitId,dispatchItemId,localVisitId,localWorkItemId,technicianId,technicianName,technicianDesignation,customerId,customerSnapshotJson,siteSnapshotJson,subjectSnapshotJson,serviceDate,outcome,workPerformed,notPerformedReason,checklistJson,findingsJson,partsJson,internalNotes,followUpsJson,recurrenceJson,provenanceJson,importedAtEpochMillis,voidedAtEpochMillis".split(",").toSet(),
            "remote_result_photos" to "id,remoteFinalResultId,sourcePhotoId,relativePath,sha256,byteSize,width,height,mimeType,caption,dispatchItemId,includeInReport,visibility".split(",").toSet(),
            "retained_images" to "id,sourceKind,sourceId,originalRelativePath,originalDeletedAtEpochMillis,derivativeRelativePath,derivativeSha256,derivativeByteSize,derivativeWidth,derivativeHeight,derivativeMimeType,createdAtEpochMillis".split(",").toSet(),
            "transferred_evidence" to "id,sourceIdentityKey,originWorkspaceId,sourcePhotoId,sourceVisitId,sourceWorkItemId,sourceFinalRevisionId,transferredFinalResultId,relayExporterId,localCustomerId,localSiteId,localEquipmentId,serviceDate,visitReference,serviceName,relativePath,sha256,byteSize,width,height,mimeType,caption,visibility,includedInCustomerReport,importedAtEpochMillis,provenanceJson".split(",").toSet(),
            "transferred_final_results" to "id,originWorkspaceId,sourceVisitId,sourceWorkItemId,sourceFinalRevisionId,logicalResultId,relayExporterId,importedAtEpochMillis,localCustomerId,localSiteId,localEquipmentId,customerSnapshotJson,siteSnapshotJson,subjectSnapshotJson,visitReference,serviceDate,technicianId,technicianName,technicianDesignation,serviceName,outcome,workPerformed,notPerformedReason,checklistJson,findingsJson,partsJson,internalNotes,recurrenceJson,payloadSha256,provenanceJson,voidedAtEpochMillis".split(",").toSet(),
            "transferred_history_entries" to "id,originWorkspaceId,family,sourceEntityId,sourceRevisionId,sourceRevisionKey,relayExporterId,localCustomerId,localSiteId,localEquipmentId,eventDateTime,payloadJson,payloadSha256,importedAtEpochMillis".split(",").toSet(),
            "work_result_receipts" to "id,packageId,resultId,sourceFinalRevisionId,assignmentIssuerId,exporterId,dispatchVisitId,dispatchItemId,assignmentGeneration,assignmentMaterialHash,receivedAtEpochMillis,payloadSha256,status,conflictReason,appliedAtEpochMillis,recurrenceAppliedAtEpochMillis".split(",").toSet(),
        )
        put(18, shape)
        shape = shape + mapOf(
            "final_work_items" to "id,revisionId,position,sourceWorkItemId,equipmentId,equipmentName,equipmentReference,equipmentIdentifier,equipmentMake,equipmentModel,equipmentSerial,serviceName,planId,planReference,outcome,publicWorkNote,notPerformedReason,fulfilledObligation,oldDueDate,nextDueDate,intervalCount,intervalUnit,capturedObligationId,privateInternalNote,nextDueDateCalculated,nextDueOverrideReason,subjectType,equipmentDescription,followUpsSnapshotJson".split(",").toSet(),
            "remote_final_results" to "id,resultId,sourceFinalRevisionId,dispatchVisitId,dispatchItemId,localVisitId,localWorkItemId,technicianId,technicianName,technicianDesignation,customerId,customerSnapshotJson,siteSnapshotJson,subjectSnapshotJson,serviceDate,outcome,workPerformed,notPerformedReason,checklistJson,findingsJson,partsJson,internalNotes,followUpsJson,recurrenceJson,provenanceJson,importedAtEpochMillis,voidedAtEpochMillis,sourcePayloadJson".split(",").toSet(),
            "retained_images" to "id,sourceKind,sourceId,originalRelativePath,originalDeletedAtEpochMillis,derivativeRelativePath,derivativeSha256,derivativeByteSize,derivativeWidth,derivativeHeight,derivativeMimeType,createdAtEpochMillis,originalDeletionRequestedAtEpochMillis".split(",").toSet(),
            "transferred_final_results" to "id,originWorkspaceId,sourceVisitId,sourceWorkItemId,sourceFinalRevisionId,logicalResultId,relayExporterId,importedAtEpochMillis,localCustomerId,localSiteId,localEquipmentId,customerSnapshotJson,siteSnapshotJson,subjectSnapshotJson,visitReference,serviceDate,technicianId,technicianName,technicianDesignation,serviceName,outcome,workPerformed,notPerformedReason,checklistJson,findingsJson,partsJson,internalNotes,recurrenceJson,payloadSha256,provenanceJson,voidedAtEpochMillis,sourcePayloadJson".split(",").toSet(),
        )
        put(19, shape)
    }
    fun columns(version: Int, table: String): Set<String> = shapes[version]?.get(table)
        ?: error("No historical Recovery shape for $version/$table")
}
