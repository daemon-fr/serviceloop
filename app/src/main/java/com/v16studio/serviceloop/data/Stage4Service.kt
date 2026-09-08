package com.v16studio.serviceloop.data

import androidx.room.withTransaction
import com.v16studio.serviceloop.domain.*
import java.io.File
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Stage4Service(
    private val database: ServiceLoopDatabase,
    private val businessTime: BusinessTime,
    private val fileRoot: File,
) {
    private val dao = database.serviceLoopDao()
    private val recovery = RecoveryPackage(database, fileRoot)

    suspend fun history(query: HistoryQuery): List<HistoryEntry> {
        val rows = mutableListOf<HistoryEntry>()
        dao.allFinalRecords().forEach { record ->
            val visit = dao.visit(record.visitId) ?: return@forEach
            val revision = dao.finalRevision(record.currentRevisionId) ?: return@forEach
            dao.finalWorkItems(revision.id).groupBy { it.equipmentId }.forEach { (equipmentId, work) ->
                rows += HistoryEntry(record.id, HistoryType.SERVICE_RECORDS, if (record.voided) "VOIDED_SERVICE" else "SERVICE", "${revision.visitReference} · ${work.joinToString { it.serviceName }}", "${revision.customerName} · ${revision.siteName}${if (record.voided) " · Voided" else ""}", revision.actualServiceDate, revision.recordedAtEpochMillis, "RECORD", record.id, visit.customerId, visit.siteId, equipmentId)
            }
        }
        dao.allVisits().filter { it.state == "CANCELLED" }.forEach { visit ->
            dao.visitWorkItems(visit.id).map { it.equipmentId }.distinct().ifEmpty { listOf(null) }.forEach { equipmentId ->
                rows += HistoryEntry("cancel-${visit.id}-${equipmentId.orEmpty()}", HistoryType.CHANGES, "CANCELLATION", "${visit.reference} cancelled", visit.cancellationReason.orEmpty(), visit.actualServiceDate, visit.cancelledAtEpochMillis ?: visit.modifiedAtEpochMillis, "VISIT", visit.id, visit.customerId, visit.siteId, equipmentId)
            }
        }
        dao.allContactNotes().forEach { note ->
            rows += HistoryEntry(note.id, HistoryType.CONTACTS, "CONTACT", "${note.channel.replace('_', ' ')} · ${note.outcome.replace('_', ' ')}", note.reference + if (note.enteredInError) " · Entered in error" else "", epochDate(note.occurredAtEpochMillis), note.createdAtEpochMillis, "CONTACT", note.id, note.customerId, note.siteId, note.equipmentId)
        }
        val followUps = dao.allFollowUps().associateBy { it.id }
        followUps.values.forEach { item -> rows += HistoryEntry("follow-${item.id}", HistoryType.FOLLOW_UPS, "FOLLOW_UP", item.title, "${item.reference} · ${item.state}", item.dueDate, item.updatedAtEpochMillis, "FOLLOW_UP", item.id, item.customerId, item.siteId, item.equipmentId) }
        dao.allFollowUpEvents().forEach { event -> followUps[event.followUpId]?.let { item -> rows += HistoryEntry(event.id, HistoryType.FOLLOW_UPS, "FOLLOW_UP_${event.eventType}", item.title, event.reason, event.dueDate ?: item.dueDate, event.occurredAtEpochMillis, "FOLLOW_UP", item.id, item.customerId, item.siteId, item.equipmentId) } }
        dao.allChangeEntries().forEach { change -> rows += HistoryEntry(change.id, HistoryType.CHANGES, change.changeType, changeTitle(change), listOfNotNull(change.oldValue?.let { "From $it" }, change.newValue?.let { "to $it" }, change.reason).joinToString(" · "), change.eventDate, change.recordedAtEpochMillis, "CHANGE", change.id, change.customerId, change.siteId, change.equipmentId) }
        val normalizedSearch = query.search.trim().lowercase()
        return rows.asSequence()
            .filter { query.type == HistoryType.ALL || it.type == query.type }
            .filter { query.from == null || it.eventDate >= query.from }
            .filter { query.to == null || it.eventDate <= query.to }
            .filter { normalizedSearch.isEmpty() || listOf(it.title, it.subtitle, it.eventKind).any { text -> normalizedSearch in text.lowercase() } }
            .filter { row -> when (query.scope.type) { HistoryScopeType.GLOBAL -> true; HistoryScopeType.CUSTOMER -> row.customerId == query.scope.id; HistoryScopeType.SITE -> row.siteId == query.scope.id; HistoryScopeType.EQUIPMENT -> row.equipmentId == query.scope.id } }
            .sortedWith(when (query.sort) { HistorySort.EVENT_NEWEST -> compareByDescending<HistoryEntry> { it.eventDate }.thenByDescending { it.recordedAtEpochMillis }; HistorySort.EVENT_OLDEST -> compareBy<HistoryEntry> { it.eventDate }.thenBy { it.recordedAtEpochMillis }; HistorySort.RECORDED_NEWEST -> compareByDescending { it.recordedAtEpochMillis } })
            .toList()
    }

    suspend fun attention(): List<AttentionItem> {
        val result = dao.correctionDrafts().map { AttentionItem(it.id, AttentionKind.CORRECTION_DRAFT, "Resume correction", "Saved ${epochDate(it.modifiedAtEpochMillis)}", "correction/${it.recordId}") }.toMutableList()
        dao.allReportRenditions().forEach { rendition ->
            val recordId = dao.finalRevision(rendition.revisionId)?.recordId ?: return@forEach
            if (rendition.status == "FAILED") result += AttentionItem(rendition.id, AttentionKind.REPORT_FAILED, "Report generation failed", rendition.failureMessage.orEmpty(), "record/$recordId")
            if (rendition.status == "READY" && !File(fileRoot, rendition.relativePath).isFile) result += AttentionItem(rendition.id, AttentionKind.REPORT_MISSING, "Report file missing", rendition.relativePath, "record/$recordId")
        }
        return result
    }

    suspend fun versions(recordId: String): Pair<List<RecordVersionSummary>, List<ReportVersionSummary>> {
        val record = dao.finalRecord(recordId) ?: return emptyList<RecordVersionSummary>() to emptyList()
        val revisions = dao.finalRevisions(recordId)
        return revisions.map { RecordVersionSummary(it.id, it.revisionNumber, it.recordedAtEpochMillis, it.correctionReason, it.id == record.currentRevisionId) } to revisions.flatMap { revision -> dao.reportRenditions(revision.id).map { ReportVersionSummary(it.id, it.revisionId, it.versionNumber, it.generatedAtEpochMillis, if (it.status == "READY" && !File(fileRoot, it.relativePath).isFile) "MISSING" else it.status, it.kind, it.relativePath, revision.id == record.currentRevisionId) } }
    }

    suspend fun openCorrection(recordId: String): CorrectionDraft = database.withTransaction {
        dao.correctionDraftForRecord(recordId)?.let { return@withTransaction correctionDomain(it) }
        val record = dao.finalRecord(recordId) ?: error("Final record no longer exists")
        require(!record.voided) { "A voided record cannot be corrected" }
        val revision = dao.finalRevision(record.currentRevisionId) ?: error("Current record revision is missing")
        val now = businessTime.instant().toEpochMilli(); val id = UUID.randomUUID().toString()
        val draft = CorrectionDraftEntity(id, record.id, revision.id, "", revision.actualServiceDate, revision.customerName, revision.siteName, revision.siteAddress, revision.businessName, revision.technicianName, null, revision.privateInternalNote, false, now, now, UUID.randomUUID().toString())
        dao.insertCorrectionDraft(draft)
        dao.insertCorrectionWorkItems(dao.finalWorkItems(revision.id).map { item -> CorrectionWorkItemEntity(UUID.randomUUID().toString(), id, item.id, item.position, item.outcome, item.publicWorkNote, item.notPerformedReason, item.fulfilledObligation, item.nextDueDate) })
        correctionDomain(draft)
    }

    suspend fun saveCorrection(value: CorrectionDraft): Long = database.withTransaction {
        val current = dao.correctionDraft(value.id) ?: error("Correction draft no longer exists")
        require(current.recordId == value.recordId && current.baseRevisionId == value.baseRevisionId) { "Correction subject cannot be changed" }
        require(value.actualServiceDate.isNotBlank() && !LocalDate.parse(value.actualServiceDate).isAfter(businessTime.today()))
        val now = businessTime.instant().toEpochMilli()
        dao.updateCorrectionDraft(current.copy(reason = value.reason.trim(), actualServiceDate = value.actualServiceDate, customerName = value.customerName.trim(), siteName = value.siteName.trim(), siteAddress = value.siteAddress.trim().ifBlank { null }, businessName = value.businessName.trim(), technicianName = value.technicianName.trim(), privateNote = value.privateNote.trim().ifBlank { null }, scheduleAcknowledged = value.scheduleAcknowledged, modifiedAtEpochMillis = now))
        val stored = dao.correctionWorkItems(value.id).associateBy { it.id }
        value.items.forEach { item ->
            val existing = stored[item.id] ?: error("Correction work subject cannot be changed")
            require(existing.sourceFinalWorkItemId == item.sourceFinalWorkItemId && item.outcome in setOf("PERFORMED", "PARTLY_PERFORMED", "NOT_PERFORMED"))
            dao.updateCorrectionWorkItem(existing.copy(outcome = item.outcome, publicWorkNote = item.publicWorkNote.trim().ifBlank { null }, notPerformedReason = item.notPerformedReason.trim().ifBlank { null }, fulfilledObligation = item.fulfilledObligation, proposedNextDueDate = item.proposedNextDueDate))
        }
        now
    }

    suspend fun discardCorrection(recordId: String): Boolean = database.withTransaction { dao.correctionDraftForRecord(recordId)?.let { dao.deleteCorrectionDraft(it.id) == 1 } ?: false }

    suspend fun commitCorrection(recordId: String): String = database.withTransaction {
        val record = dao.finalRecord(recordId) ?: error("Final record no longer exists")
        val draft = dao.correctionDraftForRecord(recordId) ?: return@withTransaction record.currentRevisionId
        require(!record.voided && record.currentRevisionId == draft.baseRevisionId) { "The current record changed; review the correction again" }
        require(draft.reason.isNotBlank()) { "Correction reason is required" }
        val base = dao.finalRevision(draft.baseRevisionId) ?: error("Base revision is missing")
        val sourceItems = dao.finalWorkItems(base.id).associateBy { it.id }
        val proposedItems = dao.correctionWorkItems(draft.id)
        require(proposedItems.size == sourceItems.size)
        val scheduleChange = proposedItems.any { proposed -> val source = sourceItems.getValue(proposed.sourceFinalWorkItemId); source.fulfilledObligation && (draft.actualServiceDate != base.actualServiceDate || proposed.fulfilledObligation != source.fulfilledObligation || proposed.proposedNextDueDate != source.nextDueDate) }
        if (scheduleChange) require(draft.scheduleAcknowledged) { "Review and acknowledge schedule reconciliation" }
        proposedItems.filter { sourceItems.getValue(it.sourceFinalWorkItemId).fulfilledObligation }.forEach { proposed ->
            val source = sourceItems.getValue(proposed.sourceFinalWorkItemId); val plan = source.planId?.let { dao.plan(it) }
            if (plan?.lastCountedRevisionId == base.id) {
                require(dao.claimForObligation(plan.currentObligationId.orEmpty()) == null) { "Booked or Working work blocks this schedule-changing correction" }
                val next = proposed.proposedNextDueDate ?: source.nextDueDate
                require(!proposed.fulfilledObligation || (next != null && LocalDate.parse(next).isAfter(LocalDate.parse(draft.actualServiceDate))))
                if (proposed.fulfilledObligation && next != null && next != plan.currentDueDate) {
                    dao.updateCurrentObligationDueDate(plan.currentObligationId!!, next)
                    dao.updatePlan(plan.copy(currentDueDate = next, lastCountedCompletionDate = draft.actualServiceDate))
                }
            }
        }
        val now = businessTime.instant().toEpochMilli(); val revisionNumber = base.revisionNumber + 1; val revisionId = stable("correction", draft.commitToken)
        dao.insertFinalRevision(base.copy(id = revisionId, revisionNumber = revisionNumber, actualServiceDate = draft.actualServiceDate, recordedAtEpochMillis = now, customerName = draft.customerName, siteName = draft.siteName, siteAddress = draft.siteAddress, businessName = draft.businessName, technicianName = draft.technicianName, privateInternalNote = draft.privateNote, supersedesRevisionId = base.id, correctionReason = draft.reason))
        proposedItems.forEach { proposed ->
            val source = sourceItems.getValue(proposed.sourceFinalWorkItemId); val newItemId = stable("correction-work", revisionId, source.id)
            dao.insertFinalWorkItems(listOf(source.copy(id = newItemId, revisionId = revisionId, outcome = proposed.outcome, publicWorkNote = proposed.publicWorkNote, notPerformedReason = proposed.notPerformedReason, fulfilledObligation = proposed.fulfilledObligation, nextDueDate = proposed.proposedNextDueDate)))
            dao.insertFinalChecklistItems(dao.finalChecklistItems(source.id).map { it.copy(id = stable("correction-check", newItemId, it.id), finalWorkItemId = newItemId) })
            dao.insertFinalParts(dao.finalParts(source.id).map { it.copy(id = stable("correction-part", newItemId, it.id), finalWorkItemId = newItemId) })
            dao.insertFinalPhotos(dao.finalPhotos(source.id).map { it.copy(id = stable("correction-photo", newItemId, it.id), finalWorkItemId = newItemId) })
        }
        check(dao.advanceRecordRevision(record.id, base.id, revisionId) == 1) { "The current record changed; review the correction again" }
        val visit = dao.visit(record.visitId)!!
        dao.insertChangeEntry(change("FINAL_RECORD", record.id, "CORRECTION", draft.actualServiceDate, now, draft.reason, "Revision ${base.revisionNumber}", "Revision $revisionNumber", visit.customerId, visit.siteId, null, record.id, draft.customerName, draft.siteName, null))
        dao.deleteCorrectionDraft(draft.id)
        revisionId
    }

    suspend fun voidRecord(recordId: String, publicReason: String, privateReason: String?): Boolean = database.withTransaction {
        require(publicReason.trim().isNotEmpty()) { "Customer-facing void explanation is required" }
        val record = dao.finalRecord(recordId) ?: error("Final record no longer exists")
        if (record.voided) return@withTransaction false
        require(dao.correctionDraftForRecord(recordId) == null) { "Discard or commit the open correction first" }
        val revision = dao.finalRevision(record.currentRevisionId)!!; val visit = dao.visit(record.visitId)!!; val now = businessTime.instant().toEpochMilli()
        check(dao.markRecordVoided(recordId, now, publicReason.trim(), privateReason?.trim()?.ifBlank { null }) == 1)
        dao.insertChangeEntry(change("FINAL_RECORD", recordId, "VOID", revision.actualServiceDate, now, publicReason.trim(), "Valid service record", "Voided", visit.customerId, visit.siteId, null, recordId, revision.customerName, revision.siteName, null))
        true
    }

    suspend fun lifecycleReview(subjectType: String, id: String, action: String): LifecycleReview {
        val blockers = mutableListOf<LifecycleBlocker>(); val label: String
        when (subjectType) {
            "CUSTOMER" -> { val item = dao.customer(id) ?: error("Customer missing"); label = item.name; if (action == "ARCHIVE") { if (dao.activeSiteCount(id) > 0) blockers += LifecycleBlocker("SITE", id, "Active sites must be archived", "customer/$id"); if (dao.openCustomerFollowUpCount(id) > 0) blockers += LifecycleBlocker("FOLLOW_UP", id, "Open customer follow-up", "follow-up/list"); if (dao.activeVisitCountForCustomer(id) > 0) blockers += LifecycleBlocker("VISIT", id, "Booked or Working visit", "work?tab=VISITS") } }
            "SITE" -> { val item = dao.site(id) ?: error("Site missing"); label = item.name; if (action == "ARCHIVE") { if (dao.activeEquipmentCount(id) > 0) blockers += LifecycleBlocker("EQUIPMENT", id, "Equipment must be retired or moved", "site/$id"); if (dao.openSiteFollowUpCount(id) > 0) blockers += LifecycleBlocker("FOLLOW_UP", id, "Open site follow-up", "follow-up/list"); if (dao.activeVisitCountForSite(id) > 0) blockers += LifecycleBlocker("VISIT", id, "Booked or Working visit", "work?tab=VISITS"); if (item.isDefault) blockers += LifecycleBlocker("DEFAULT_SITE", id, "Reassign or clear the default site", "site/edit/$id") } }
            "EQUIPMENT" -> { val item = dao.equipment(id) ?: error("Equipment missing"); label = item.name; if (action == "RETIRE") { if (dao.nonEndedPlanCount(id) > 0) blockers += LifecycleBlocker("PLAN", id, "All service plans must be Ended", "equipment/$id"); if (dao.openEquipmentFollowUpCount(id) > 0) blockers += LifecycleBlocker("FOLLOW_UP", id, "Open equipment follow-up", "follow-up/list"); dao.workingItemId(id)?.let { blockers += LifecycleBlocker("VISIT", it, "Booked or Working visit selection", "inspection/$it") } } }
            "PLAN" -> { val item = dao.plan(id) ?: error("Plan missing"); label = item.name; item.currentObligationId?.let { obligation -> dao.claimForObligation(obligation)?.let { blockers += LifecycleBlocker("VISIT", it, "Booked or Working claim", "visit/$it") } } }
            else -> error("Unsupported lifecycle subject")
        }
        return LifecycleReview(action, id, label, blockers, listOf("History and issued records are preserved", "Dependent records are not changed automatically"))
    }

    suspend fun applyLifecycle(subjectType: String, id: String, action: String, reason: String): Boolean = database.withTransaction {
        require(reason.trim().isNotEmpty()) { "Reason is required" }
        val review = lifecycleReview(subjectType, id, action); require(review.allowed) { "Resolve the named blockers first" }
        val now = businessTime.instant().toEpochMilli(); val today = businessTime.today().toString()
        val changed = when (subjectType) {
            "CUSTOMER" -> dao.customer(id)!!.let { dao.changeCustomerState(id, it.state, if (action == "ARCHIVE") "ARCHIVED" else "ACTIVE") }
            "SITE" -> dao.site(id)!!.let { dao.changeSiteState(id, it.state, if (action == "ARCHIVE") "ARCHIVED" else "ACTIVE") }
            "EQUIPMENT" -> { val equipment = dao.equipment(id)!!; if (action == "RETURN") { val site = dao.site(equipment.siteId)!!; require(site.state == "ACTIVE" && dao.customer(site.customerId)?.state == "ACTIVE") { "Restore the parent site and customer first" } }; dao.changeEquipmentState(id, equipment.state, if (action == "RETIRE") "RETIRED" else "ACTIVE") }
            "PLAN" -> { val plan = dao.plan(id)!!; when (action) { "PAUSE" -> dao.changePlanState(id, "ACTIVE", "PAUSED", plan.currentObligationId); "RESUME" -> { require(plan.state == "PAUSED"); val equipment = dao.equipment(plan.equipmentId)!!; val site = dao.site(equipment.siteId)!!; require(equipment.state == "ACTIVE" && site.state == "ACTIVE" && dao.customer(site.customerId)?.state == "ACTIVE"); dao.changePlanState(id, "PAUSED", "ACTIVE", plan.currentObligationId) }; "END" -> dao.changePlanState(id, plan.state, "ENDED", null); else -> 0 } }
            else -> 0
        }
        check(changed == 1) { "Lifecycle state changed; review and try again" }
        dao.insertChangeEntry(change(subjectType, id, action, today, now, reason.trim(), null, action, null, null, if (subjectType == "EQUIPMENT") id else null, null, null, null, null))
        true
    }

    suspend fun moveReview(equipmentId: String): MoveReview {
        val equipment = dao.equipment(equipmentId) ?: error("Equipment missing"); val oldSite = dao.site(equipment.siteId)!!; val customer = dao.customer(oldSite.customerId)!!
        val blockers = mutableListOf<LifecycleBlocker>()
        dao.workingItemId(equipmentId)?.let { blockers += LifecycleBlocker("VISIT", it, "Booked or Working visit selection", "inspection/$it") }
        if (dao.openEquipmentFollowUpCount(equipmentId) > 0) blockers += LifecycleBlocker("FOLLOW_UP", equipmentId, "Open equipment follow-up", "follow-up/list")
        dao.correctionRecordForEquipment(equipmentId)?.let { blockers += LifecycleBlocker("CORRECTION", it, "Open correction involving this equipment", "correction/$it") }
        val plans = dao.plansForEquipment(equipmentId).map { PlanDetail(it.id, it.equipmentId, equipment.name, it.reference, it.name, it.intervalCount, it.intervalUnit, it.currentDueDate, it.state, it.reusableTemplateId) }
        return MoveReview(equipmentId, "${equipment.reference} · ${equipment.name}", oldSite.id, "${customer.name} · ${oldSite.name}", dao.activeVisitSites().filter { it.id != oldSite.id }.map { VisitSiteOption(it.id, it.reference, it.name, it.customerName, emptyList()) }, plans, blockers)
    }

    suspend fun moveEquipment(equipmentId: String, destinationSiteId: String, effectiveDate: String, reason: String, acknowledged: Boolean): Boolean = database.withTransaction {
        require(reason.trim().isNotEmpty() && acknowledged); val date = LocalDate.parse(effectiveDate); require(!date.isAfter(businessTime.today()))
        val review = moveReview(equipmentId); require(review.blockers.isEmpty()) { "Resolve the named blockers first" }
        val destination = dao.site(destinationSiteId) ?: error("Destination site missing"); val destinationCustomer = dao.customer(destination.customerId)!!; require(destination.state == "ACTIVE" && destinationCustomer.state == "ACTIVE")
        val equipment = dao.equipment(equipmentId)!!; val oldSite = dao.site(equipment.siteId)!!; val oldCustomer = dao.customer(oldSite.customerId)!!; val now = businessTime.instant().toEpochMilli()
        check(dao.moveEquipment(equipment.id, oldSite.id, destination.id) == 1) { "Equipment location changed; review and try again" }
        val moveId = UUID.randomUUID().toString(); dao.insertEquipmentMove(EquipmentMoveEntity(moveId, equipment.id, oldSite.id, destination.id, effectiveDate, reason.trim(), now))
        dao.insertChangeEntry(change("EQUIPMENT", equipment.id, "MOVE", effectiveDate, now, reason.trim(), "${oldCustomer.name} · ${oldSite.name}", "${destinationCustomer.name} · ${destination.name}", oldCustomer.id, oldSite.id, equipment.id, null, oldCustomer.name, oldSite.name, equipment.name))
        dao.insertChangeEntry(change("EQUIPMENT", equipment.id, "MOVE_IN", effectiveDate, now, reason.trim(), "${oldCustomer.name} · ${oldSite.name}", "${destinationCustomer.name} · ${destination.name}", destinationCustomer.id, destination.id, equipment.id, null, destinationCustomer.name, destination.name, equipment.name))
        true
    }

    suspend fun datasetSummary(): DatasetSummary {
        val metadata = ensureMetadata(); val attachments = dao.allAttachments(); val reports = dao.allReportRenditions(); val lastWrite = metadata.lastBusinessWriteAtEpochMillis
        val bytes = attachments.sumOf { it.byteSize } + reports.sumOf { it.byteSize ?: 0 }
        return DatasetSummary(metadata.datasetId, dao.customerCount(), dao.siteCount(), dao.equipmentCount(), dao.planCount(), dao.visitCount(), dao.workingVisitCount() + dao.bookedVisitCount(), attachments.size, reports.size, bytes, fileRoot.usableSpace, metadata.lastBackupAttemptAtEpochMillis, metadata.lastVerifiedFullBackupAtEpochMillis, metadata.lastVerifiedSnapshotAtEpochMillis, metadata.lastVerifiedDestination, metadata.lastVerifiedSize, lastWrite != null && (metadata.lastVerifiedSnapshotAtEpochMillis == null || lastWrite > metadata.lastVerifiedSnapshotAtEpochMillis), metadata.backupReminderDays, metadata.restrictedRecoveryState)
    }

    suspend fun setBackupReminder(days: Int) { require(days in setOf(0, 1, 7, 30)); dao.upsertRecoveryMetadata(ensureMetadata().copy(backupReminderDays = days)) }
    suspend fun createBackup(passphrase: CharArray, incompleteAcknowledged: Boolean = false): BackupResult { val now = businessTime.instant().toEpochMilli(); dao.upsertRecoveryMetadata(ensureMetadata().copy(lastBackupAttemptAtEpochMillis = now)); return recovery.create(passphrase, incompleteAcknowledged) }
    fun inspectBackup(bytes: ByteArray, passphrase: CharArray): BackupInspection = recovery.inspect(bytes, passphrase)
    suspend fun recordVerifiedBackup(result: BackupResult, destination: String) { require(result.complete); dao.upsertRecoveryMetadata(ensureMetadata().copy(lastVerifiedFullBackupAtEpochMillis = businessTime.instant().toEpochMilli(), lastVerifiedSnapshotAtEpochMillis = result.snapshotAtEpochMillis, lastVerifiedDestination = destination.take(200), lastVerifiedSize = result.bytes.size.toLong())) }
    suspend fun restoreBackup(inspection: BackupInspection, confirmation: String, incompleteAcknowledged: Boolean) { require(confirmation == "REPLACE"); require(inspection.complete || incompleteAcknowledged); recovery.restore(inspection); if (!inspection.complete) dao.upsertRecoveryMetadata(ensureMetadata().copy(restoredFromIncompleteCopy = true)) }

    suspend fun directoryCsv(includeInactive: Boolean, includePrivate: Boolean, customerId: String? = null): ByteArray {
        val customers = dao.allCustomers().filter { (includeInactive || it.state == "ACTIVE") && (customerId == null || it.id == customerId) }.associateBy { it.id }
        val sites = dao.allSites().filter { it.customerId in customers && (includeInactive || it.state == "ACTIVE") }.associateBy { it.id }
        val equipment = dao.allEquipment().filter { it.siteId in sites && (includeInactive || it.state == "ACTIVE") }
        val rows = mutableListOf<List<String>>()
        customers.values.forEach { customer ->
            val customerSites = sites.values.filter { it.customerId == customer.id }
            if (customerSites.isEmpty()) rows += directoryRow(customer, null, null, includePrivate)
            customerSites.forEach { site ->
                val siteEquipment = equipment.filter { it.siteId == site.id }
                if (siteEquipment.isEmpty()) rows += directoryRow(customer, site, null, includePrivate)
                siteEquipment.forEach { rows += directoryRow(customer, site, it, includePrivate) }
            }
        }
        return (listOf(CSV_HEADERS) + rows).joinToString("\r\n") { it.joinToString(",", transform = ::csv) }.plus("\r\n").toByteArray(Charsets.UTF_8)
    }

    suspend fun recordsCsvPackage(includeInactive: Boolean, includePrivate: Boolean, includePreviousRevisions: Boolean): ByteArray {
        val revisions = dao.allFinalRecords().flatMap { record ->
            (if (includePreviousRevisions) dao.finalRevisions(record.id) else listOfNotNull(dao.finalRevision(record.currentRevisionId))).map { record to it }
        }
        fun sheet(headers: List<String>, rows: List<List<String>>) = (listOf(headers) + rows).joinToString("\r\n") { row -> row.joinToString(",", transform = ::csv) }.plus("\r\n").toByteArray()
        val entries = linkedMapOf<String, ByteArray>()
        entries["README.txt"] = "ServiceLoop readable records export. UTF-8 quoted CSV. Formula-like values are prefixed with an apostrophe. This archive is unencrypted and is not a backup: it contains no PDF or photograph bytes.\r\n".toByteArray()
        entries["directory.csv"] = directoryCsv(includeInactive, includePrivate)
        entries["service_records.csv"] = sheet(listOf("record_id", "revision_id", "revision_number", "visit_reference", "service_date", "recorded_at", "customer", "site", "voided", "correction_reason", "private_internal_note"), revisions.map { (record, revision) -> listOf(record.id, revision.id, revision.revisionNumber.toString(), revision.visitReference, revision.actualServiceDate, revision.recordedAtEpochMillis.toString(), revision.customerName, revision.siteName, record.voided.toString(), revision.correctionReason.orEmpty(), if (includePrivate) revision.privateInternalNote.orEmpty() else "") })
        entries["history.csv"] = sheet(listOf("event_id", "category", "event_kind", "event_date", "recorded_at", "title", "detail", "customer_id", "site_id", "equipment_id"), history(HistoryQuery()).map { listOf(it.id, it.type.name, it.eventKind, it.eventDate, it.recordedAtEpochMillis.toString(), it.title, it.subtitle, it.customerId.orEmpty(), it.siteId.orEmpty(), it.equipmentId.orEmpty()) })
        entries["report_index.csv"] = sheet(listOf("rendition_id", "revision_id", "version", "status", "kind", "generated_at", "relative_path", "sha256", "byte_size"), dao.allReportRenditions().map { listOf(it.id, it.revisionId, it.versionNumber.toString(), it.status, it.kind, it.generatedAtEpochMillis?.toString().orEmpty(), it.relativePath, it.sha256.orEmpty(), it.byteSize?.toString().orEmpty()) })
        entries["attachment_index.csv"] = sheet(listOf("attachment_id", "owner_type", "owner_id", "availability", "included_in_report", "relative_path", "sha256", "byte_size", "caption"), dao.allAttachments().map { listOf(it.id, it.ownerType, it.ownerId, it.availability, it.includedInCustomerReport.toString(), it.storedRelativePath, it.sha256, it.byteSize.toString(), if (includePrivate) it.caption.orEmpty() else "") })
        return ByteArrayOutputStream().also { output -> ZipOutputStream(output).use { zip -> entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } } }.toByteArray()
    }

    suspend fun validateDirectoryCsv(bytes: ByteArray): CsvImportPreview {
        require(bytes.size <= 20 * 1024 * 1024) { "CSV is larger than 20 MiB" }
        val rows = parseCsv(bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")); require(rows.isNotEmpty()) { "CSV is empty" }
        val header = rows.first(); require(header.size == CSV_HEADERS.size && header.toSet() == CSV_HEADERS.toSet()) { "CSV must contain exactly the fixed ServiceLoop headers" }
        require(rows.size - 1 <= 10_000) { "CSV contains more than 10,000 data rows" }
        val indexes = header.withIndex().associate { it.value to it.index }; val seen = mutableMapOf<String, Map<String, String>>(); val output = mutableListOf<CsvImportRow>()
        val existingCustomers = dao.allCustomers().associateBy { it.reference }; val existingSites = dao.allSites().associateBy { it.reference }; val existingEquipment = dao.allEquipment().associateBy { it.reference }
        rows.drop(1).forEachIndexed { index, raw ->
            val values = CSV_HEADERS.associateWith { importedCell(raw.getOrElse(indexes.getValue(it)) { "" }) }; val messages = mutableListOf<String>(); val refs = listOf("customer_ref", "site_ref", "equipment_ref")
            refs.filter { values.getValue(it).isNotBlank() && !REF.matches(values.getValue(it)) }.forEach { messages += "$it must contain 1–64 letters, digits, hyphens, or underscores" }
            if (values.getValue("customer_ref").isBlank() || values.getValue("customer_name").isBlank()) messages += "Customer reference and name are required"
            val hasSite = listOf("site_ref", "site_name", "site_address", "site_contact_name", "site_phone", "site_email", "site_access_notes_internal").any { values.getValue(it).isNotBlank() }
            val hasEquipment = CSV_HEADERS.dropWhile { it != "equipment_ref" }.any { values.getValue(it).isNotBlank() }
            if ((hasSite || hasEquipment) && (values.getValue("site_ref").isBlank() || values.getValue("site_name").isBlank())) messages += "Site reference and name are required for site/equipment rows"
            if (hasEquipment && (values.getValue("equipment_ref").isBlank() || values.getValue("equipment_name").isBlank())) messages += "Equipment reference and name are required"
            refs.forEach { key -> values.getValue(key).takeIf { it.isNotBlank() }?.let { ref -> val identity = "$key:$ref"; val projection = referenceProjection(key, values); val prior = seen[identity]; if (prior != null && prior != projection) messages += "Conflicting repeated $key $ref" else seen[identity] = projection } }
            var status = if (messages.isEmpty()) "NEW" else "ERROR"
            existingCustomers[values.getValue("customer_ref")]?.let { existing -> if (normalize(existing.name) != normalize(values.getValue("customer_name"))) messages += "HARD CONFLICT: customer reference has different fields" else if (status != "ERROR") status = "EXISTING_UNCHANGED" }
            values.getValue("site_ref").takeIf { it.isNotBlank() }?.let { ref -> existingSites[ref]?.let { existing -> val customer = existingCustomers[values.getValue("customer_ref")]; if (customer == null || existing.customerId != customer.id || normalize(existing.name) != normalize(values.getValue("site_name"))) messages += "HARD CONFLICT: site reference has different parent/fields" else if (status != "ERROR") status = "EXISTING_UNCHANGED" } }
            values.getValue("equipment_ref").takeIf { it.isNotBlank() }?.let { ref -> existingEquipment[ref]?.let { existing -> val site = existingSites[values.getValue("site_ref")]; if (site == null || existing.siteId != site.id || normalize(existing.name) != normalize(values.getValue("equipment_name"))) messages += "HARD CONFLICT: equipment reference has different parent/fields" else if (status != "ERROR") status = "EXISTING_UNCHANGED" } }
            if (messages.any { it.startsWith("HARD CONFLICT") }) status = "ERROR"
            if (status == "NEW") {
                val similarCustomer = existingCustomers.values.any { normalize(it.name) == normalize(values.getValue("customer_name")) }
                val similarEquipment = values.getValue("equipment_name").isNotBlank() && existingEquipment.values.any { normalize(it.name) == normalize(values.getValue("equipment_name")) || (!it.serialNumber.isNullOrBlank() && normalize(it.serialNumber) == normalize(values.getValue("serial_number"))) }
                if (similarCustomer || similarEquipment) { status = "WARNING"; messages += "Possible duplicate — review Create separate or Skip row/branch" }
            }
            output += CsvImportRow(index + 2, values, status, messages, values.getValue("customer_ref"))
        }
        val newRows = output.filter { it.status in setOf("NEW", "WARNING") }
        return CsvImportPreview(output, newRows.map { it.values.getValue("customer_ref") }.distinct().size, newRows.mapNotNull { it.values.getValue("site_ref").ifBlank { null } }.distinct().size, newRows.mapNotNull { it.values.getValue("equipment_ref").ifBlank { null } }.distinct().size, output.count { it.status == "ERROR" }, output.count { it.status == "WARNING" })
    }

    suspend fun importDirectory(preview: CsvImportPreview, createSeparate: Set<String> = emptySet(), skippedBranches: Set<String> = emptySet()): ImportResult = database.withTransaction {
        require(preview.errors == 0); val rows = preview.rows.filterNot { it.branchKey in skippedBranches }; var cc = 0; var sc = 0; var ec = 0; var unchanged = 0
        require(rows.filter { it.status == "WARNING" }.all { it.branchKey in createSeparate }) { "Review every possible duplicate: Create separate or Skip row/branch" }
        val customers = dao.allCustomers().associateBy { it.reference }.toMutableMap(); val sites = dao.allSites().associateBy { it.reference }.toMutableMap(); val equipment = dao.allEquipment().associateBy { it.reference }.toMutableMap()
        rows.groupBy { it.values.getValue("customer_ref") }.forEach { (ref, group) ->
            val first = group.first().values; val existing = customers[ref]
            val customer = existing ?: CustomerEntity(UUID.randomUUID().toString(), ref, first.getValue("customer_name"), clean(first["customer_contact_name"]), clean(first["customer_phone"]), clean(first["customer_email"]), clean(first["customer_notes_internal"]), "ACTIVE").also { dao.insertCustomers(listOf(it)); customers[ref] = it; cc++ }
            if (existing != null) { require(normalize(existing.name) == normalize(first.getValue("customer_name"))) { "Hard conflict for customer reference $ref" }; unchanged++ }
            group.filter { it.values.getValue("site_ref").isNotBlank() }.groupBy { it.values.getValue("site_ref") }.forEach { (siteRef, siteRows) ->
                val v = siteRows.first().values; val existingSite = sites[siteRef]
                val site = existingSite ?: SiteEntity(UUID.randomUUID().toString(), customer.id, siteRef, v.getValue("site_name"), clean(v["site_address"]), clean(v["site_access_notes_internal"]), clean(v["site_contact_name"]), clean(v["site_phone"]), clean(v["site_email"]), dao.sitesForCustomer(customer.id).isEmpty(), "ACTIVE").also { dao.insertSites(listOf(it)); sites[siteRef] = it; sc++ }
                if (existingSite != null) { require(existingSite.customerId == customer.id && normalize(existingSite.name) == normalize(v.getValue("site_name"))) { "Hard conflict for site reference $siteRef" }; unchanged++ }
                siteRows.filter { it.values.getValue("equipment_ref").isNotBlank() }.forEach { row ->
                    val e = row.values; val equipmentRef = e.getValue("equipment_ref"); val existingEquipment = equipment[equipmentRef]
                    if (existingEquipment == null) { val item = EquipmentEntity(UUID.randomUUID().toString(), site.id, equipmentRef, clean(e["internal_identifier"]), e.getValue("equipment_name"), clean(e["make"]), clean(e["model"]), clean(e["serial_number"]), clean(e["equipment_notes_internal"]), "ACTIVE"); dao.insertEquipment(listOf(item)); equipment[equipmentRef] = item; ec++ }
                    else { require(existingEquipment.siteId == site.id && normalize(existingEquipment.name) == normalize(e.getValue("equipment_name"))) { "Hard conflict for equipment reference $equipmentRef" }; unchanged++ }
                }
            }
        }
        ImportResult(cc, sc, ec, unchanged)
    }

    suspend fun erase(acknowledged: Boolean, confirmation: String) {
        require(acknowledged && confirmation == "ERASE")
        database.withTransaction {
            val db = database.openHelper.writableDatabase
            RecoveryPackage.TABLE_ORDER.asReversed().filterNot { it == "recovery_metadata" }.forEach { db.execSQL("DELETE FROM `$it`") }
            dao.upsertRecoveryMetadata(RecoveryMetadataEntity(datasetId = UUID.randomUUID().toString(), firstBusinessWriteAtEpochMillis = null, lastBusinessWriteAtEpochMillis = null, lastBackupAttemptAtEpochMillis = null, lastVerifiedFullBackupAtEpochMillis = null, lastVerifiedSnapshotAtEpochMillis = null, lastVerifiedDestination = null, lastVerifiedSize = null))
        }
        listOf("attachments", "reports").forEach { File(fileRoot, it).deleteRecursively() }
    }

    private suspend fun correctionDomain(entity: CorrectionDraftEntity): CorrectionDraft {
        val source = dao.finalWorkItems(entity.baseRevisionId).associateBy { it.id }
        return CorrectionDraft(entity.id, entity.recordId, entity.baseRevisionId, entity.reason, entity.actualServiceDate, entity.customerName, entity.siteName, entity.siteAddress.orEmpty(), entity.businessName, entity.technicianName, entity.privateNote.orEmpty(), entity.scheduleAcknowledged, entity.modifiedAtEpochMillis, dao.correctionWorkItems(entity.id).map { item -> val base = source.getValue(item.sourceFinalWorkItemId); CorrectionWorkDraft(item.id, item.sourceFinalWorkItemId, item.position, base.equipmentName, base.serviceName, item.outcome, item.publicWorkNote.orEmpty(), item.notPerformedReason.orEmpty(), item.fulfilledObligation, base.oldDueDate, item.proposedNextDueDate) })
    }
    private suspend fun ensureMetadata(): RecoveryMetadataEntity = dao.recoveryMetadata() ?: RecoveryMetadataEntity(datasetId = UUID.randomUUID().toString(), firstBusinessWriteAtEpochMillis = businessTime.instant().toEpochMilli(), lastBusinessWriteAtEpochMillis = businessTime.instant().toEpochMilli(), lastBackupAttemptAtEpochMillis = null, lastVerifiedFullBackupAtEpochMillis = null, lastVerifiedSnapshotAtEpochMillis = null, lastVerifiedDestination = null, lastVerifiedSize = null).also { dao.upsertRecoveryMetadata(it) }
    private fun change(subjectType: String, subjectId: String, type: String, date: String, at: Long, reason: String, old: String?, new: String?, customerId: String?, siteId: String?, equipmentId: String?, recordId: String?, customer: String?, site: String?, equipment: String?) = ChangeEntryEntity(UUID.randomUUID().toString(), subjectType, subjectId, type, date, at, reason, old, new, customerId, siteId, equipmentId, recordId, customer, site, equipment)
    private fun changeTitle(change: ChangeEntryEntity) = listOfNotNull(change.equipmentNameSnapshot, change.siteNameSnapshot, change.customerNameSnapshot, change.changeType.replace('_', ' ')).joinToString(" · ")
    private fun epochDate(value: Long) = java.time.Instant.ofEpochMilli(value).atZone(ZoneOffset.UTC).toLocalDate().toString()
    private fun stable(vararg values: String) = UUID.nameUUIDFromBytes(values.joinToString(":").toByteArray()).toString()
    private fun clean(value: String?) = value?.trim()?.ifBlank { null }
    private fun importedCell(value: String): String = value.trim().let { cell -> if (cell.length > 1 && cell[0] == '\'' && cell[1] in "=+-@") cell.drop(1) else cell }
    private fun normalize(value: String) = value.trim().lowercase().replace(Regex("\\s+"), " ")
    private fun referenceProjection(key: String, values: Map<String, String>): Map<String, String> = when (key) {
        "customer_ref" -> CSV_HEADERS.take(6)
        "site_ref" -> listOf("customer_ref") + CSV_HEADERS.subList(6, 14)
        else -> listOf("site_ref") + CSV_HEADERS.subList(14, CSV_HEADERS.size)
    }.associateWith { values.getValue(it) }
    private fun csv(value: String): String { val safe = if (value.startsWith('=') || value.startsWith('+') || value.startsWith('-') || value.startsWith('@')) "'$value" else value; return "\"${safe.replace("\"", "\"\"")}\"" }
    private fun directoryRow(c: CustomerEntity, s: SiteEntity?, e: EquipmentEntity?, private: Boolean) = listOf(c.reference, c.name, c.contactName.orEmpty(), c.phone.orEmpty(), c.email.orEmpty(), if (private) c.privateNote.orEmpty() else "", s?.reference.orEmpty(), s?.name.orEmpty(), s?.address.orEmpty(), (s?.let { it.contactName.isNullOrBlank() && it.phone.isNullOrBlank() && it.email.isNullOrBlank() } ?: true).toString(), s?.contactName.orEmpty(), s?.phone.orEmpty(), s?.email.orEmpty(), if (private) s?.privateAccessNotes.orEmpty() else "", e?.reference.orEmpty(), e?.name.orEmpty(), "", e?.make.orEmpty(), e?.model.orEmpty(), e?.serialNumber.orEmpty(), e?.technicianIdentifier.orEmpty(), if (private) e?.privateNotes.orEmpty() else "")

    companion object {
        val CSV_HEADERS = listOf("customer_ref", "customer_name", "customer_contact_name", "customer_phone", "customer_email", "customer_notes_internal", "site_ref", "site_name", "site_address", "site_use_customer_contact", "site_contact_name", "site_phone", "site_email", "site_access_notes_internal", "equipment_ref", "equipment_name", "equipment_type", "make", "model", "serial_number", "internal_identifier", "equipment_notes_internal")
        val REF = Regex("^[A-Za-z0-9_-]{1,64}$")
        fun blankTemplate() = (CSV_HEADERS.joinToString(",") { "\"$it\"" } + "\r\n").toByteArray()
        fun workedExample() = (CSV_HEADERS.joinToString(",") { "\"$it\"" } + "\r\n" + listOf("C-FICTION", "Fictional Bakery", "Ana Example", "+40 700 000 000", "ana@example.invalid", "", "S-FICTION", "Main workshop", "1 Example Street", "true", "", "", "", "", "EQ-FICTION-1", "Fictional oven", "Oven", "ExampleCo", "Model A", "SERIAL-EXAMPLE", "OVEN-01", "").joinToString(",") { "\"$it\"" } + "\r\n").toByteArray()
        fun parseCsv(text: String): List<List<String>> {
            val rows = mutableListOf<MutableList<String>>(); var row = mutableListOf<String>(); val cell = StringBuilder(); var quoted = false; var i = 0
            while (i < text.length) { val ch = text[i]; when { quoted && ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }; ch == '"' -> quoted = !quoted; !quoted && ch == ',' -> { row += cell.toString(); cell.clear() }; !quoted && (ch == '\n' || ch == '\r') -> { if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++; row += cell.toString(); cell.clear(); if (row.any { it.isNotEmpty() }) rows += row; row = mutableListOf() }; else -> cell.append(ch) }; i++ }
            require(!quoted) { "Unclosed quoted CSV field" }; if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }; return rows
        }
    }
}
