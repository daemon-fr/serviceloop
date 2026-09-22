package com.v16studio.serviceloop.data

import androidx.room.withTransaction
import com.v16studio.serviceloop.domain.*
import java.time.LocalDate
import java.time.ZoneId
import java.math.BigDecimal
import java.util.UUID
import java.io.File
import java.math.RoundingMode
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.sync.withLock

internal fun validateNewBookedVisitDate(state: String, serviceDate: String, businessDate: LocalDate) {
    val parsed = LocalDate.parse(serviceDate)
    require(state != "BOOKED" || !parsed.isBefore(businessDate)) {
        "A booked visit cannot be scheduled in the past. Choose today or a future date, or record past work."
    }
}

interface ServiceLoopRepository {
    suspend fun home(): HomeSummary
    suspend fun operationalDashboard(scope: WorkScope): OperationalDashboardProjection {
        val fallbackBusinessTime = ClockBusinessTime()
        val now = fallbackBusinessTime.instant()
        return OperationalDashboardProjector.project(
            scope = scope,
            visits = visits(),
            dueServices = dueServices(),
            followUps = followUps(),
            today = now.atZone(fallbackBusinessTime.zoneId).toLocalDate(),
            now = now,
            businessZone = fallbackBusinessTime.zoneId,
            dueSoonHorizonDays = 14,
        )
    }
    fun observeOperationalDashboard(scope: WorkScope): Flow<OperationalDashboardProjection> = flow { emit(operationalDashboard(scope)) }
    suspend fun equipment(id: String): EquipmentDetail?
    suspend fun equipmentList(): List<EquipmentSummary>
    suspend fun customerList(): List<CustomerSummary>
    suspend fun siteList(): List<SiteRegisterSummary> = emptyList()
    suspend fun inspection(workItemId: String): InspectionDraft?
    suspend fun serviceVisitProgress(visitId: String): VisitServiceProgress = error("Service progress unavailable")
    suspend fun completionLines(visitId: String): List<CompletionLine>
    /**
     * Reads the facts needed by the active Service editor as one projection.
     * Test/fake repositories may inherit this compatibility composition; the Room
     * repository provides the consistent optimized implementation.
     */
    suspend fun serviceWorkspace(workItemId: String): ServiceWorkspace? {
        val draft = inspection(workItemId) ?: return null
        val progress = try {
            serviceVisitProgress(draft.visitId)
        } catch (_: IllegalStateException) {
            null
        }
        return ServiceWorkspace(draft, progress, completionLines(draft.visitId))
    }
    suspend fun checklistCompleteness(workItemId: String): ChecklistCompleteness = error("Checklist unavailable")
    suspend fun saveResponse(workItemId: String, questionId: String, disposition: ResponseDisposition, value: String?, reason: String?): Long
    /** Persists a response while explicitly carrying inactive Issue/N/A reason drafts. */
    suspend fun saveResponseWithInactiveDrafts(
        workItemId: String,
        questionId: String,
        disposition: ResponseDisposition,
        value: String?,
        reason: String?,
        issueFoundReasonDraft: String? = null,
        notApplicableReasonDraft: String? = null,
    ): Long = saveResponse(workItemId, questionId, disposition, value, reason)
    /** Persists a question transition and clears its obsolete raw input family atomically. */
    suspend fun saveQuestionTransition(
        workItemId: String,
        questionId: String,
        disposition: ResponseDisposition,
        value: String?,
        reason: String?,
        issueFoundReasonDraft: String?,
        notApplicableReasonDraft: String?,
    ): Long = saveResponseWithInactiveDrafts(
        workItemId,
        questionId,
        disposition,
        value,
        reason,
        issueFoundReasonDraft,
        notApplicableReasonDraft,
    )
    suspend fun saveWorkingInputBuffer(workItemId: String, fieldKey: String, rawValue: String): Long = error("Working input buffer unavailable")
    suspend fun clearWorkingInputBuffer(workItemId: String, fieldKey: String): Long = error("Working input buffer unavailable")
    suspend fun workingInputBuffers(workItemId: String): Map<String, String> = emptyMap()
    suspend fun serviceDraftWorkItemIds(visitId: String): List<String> = emptyList()
    suspend fun visits(): List<VisitSummary> = emptyList()
    suspend fun businessProfile(): BusinessProfile? = null
    suspend fun visitReportIdentity(visitId: String): BusinessProfile? = null
    suspend fun refreshVisitReportIdentity(visitId: String): Long = error("Visit report identity unavailable")
    suspend fun saveBusinessProfile(profile: BusinessProfile): Long = error("Business profile unavailable")
    suspend fun savePublicWork(workItemId: String, text: String): Long = error("Work note unavailable")
    suspend fun savePrivateNote(workItemId: String, text: String): Long = error("Private note unavailable")
    @Deprecated("Checklist completeness is derived from the current snapshot and responses")
    suspend fun markChecklistReviewed(workItemId: String): Long = error("Checklist review unavailable")
    suspend fun saveCompletionDraft(workItemId: String, outcome: String?, fulfills: Boolean?, reason: String?, nextDue: String?, calculated: Boolean?, overrideReason: String?): Long = error("Completion draft unavailable")
    suspend fun recoverMissingCalculatedNextDue(request: NextDueRecoveryRequest): NextDueRecoveryResult = error("Next-due recovery unavailable")
    suspend fun finalizeVisit(visitId: String): FinalizeResult = FinalizeResult.Blocked("Finalization unavailable")
    suspend fun finalRecord(recordId: String): FinalRecordDetail? = null
    suspend fun finalRecordRevision(recordId: String, revisionId: String, renditionId: String? = null): FinalRecordDetail? = null
    suspend fun customer(id: String): CustomerDetail? = null
    suspend fun site(id: String): SiteDetail? = null
    suspend fun dueServices(): List<DueService> = emptyList()
    fun observeDueServices(): Flow<List<DueService>> = flow { emit(dueServices()) }
    fun observeRootInvalidations(): Flow<Unit> = emptyFlow()
    suspend fun visitSites(): List<VisitSiteOption> = emptyList()
    suspend fun plan(id: String): PlanDetail? = null
    suspend fun templates(): List<TemplateSummary> = emptyList()
    suspend fun template(id: String): TemplateDetail? = null
    suspend fun visit(id: String): VisitDetail? = null
    suspend fun followUps(): List<FollowUpDetail> = emptyList()
    suspend fun followUp(id: String): FollowUpDetail? = null
    suspend fun parts(workItemId: String): List<PartEntry> = emptyList()
    suspend fun photos(workItemId: String): List<PhotoEntry> = emptyList()
    suspend fun search(query: String): List<SearchTarget> = emptyList()
    suspend fun createCustomer(input: CustomerInput): String = error("Customer editor unavailable")
    suspend fun createCustomerWithFirstSite(customer: CustomerInput, site: SiteInput): Pair<String, String> = error("Customer and first-site editor unavailable")
    suspend fun updateCustomer(id: String, input: CustomerInput): Long = error("Customer editor unavailable")
    suspend fun createSite(customerId: String, input: SiteInput): String = error("Site editor unavailable")
    suspend fun updateSite(id: String, input: SiteInput): Long = error("Site editor unavailable")
    suspend fun createEquipment(siteId: String, input: EquipmentInput): String = error("Equipment editor unavailable")
    suspend fun updateEquipment(id: String, input: EquipmentInput): Long = error("Equipment editor unavailable")
    suspend fun createPlan(equipmentId: String, input: PlanInput): String = error("Plan editor unavailable")
    suspend fun updatePlan(id: String, input: PlanInput): Long = error("Plan editor unavailable")
    suspend fun createTemplate(name: String, items: List<TemplateItemDraft>): String = error("Template editor unavailable")
    suspend fun reviseTemplate(id: String, name: String, items: List<TemplateItemDraft>): Long = error("Template editor unavailable")
    suspend fun templateRevisions(id: String): List<TemplateRevisionDetail> = emptyList()
    suspend fun templateServicePlanReferenceCount(id: String): Int = 0
    suspend fun setTemplateState(id: String, state: String): Long = error("Template state unavailable")
    suspend fun deleteTemplate(id: String): Long = error("Template deletion unavailable")
    suspend fun cloneTemplate(id: String): String = error("Template clone unavailable")
    suspend fun createVisit(planIds: List<String>, state: String, serviceDate: String, scheduledAtEpochMillis: Long? = null): String = error("Visit setup unavailable")
    suspend fun createVisitForSite(siteId: String, planIds: List<String>, adHocWork: List<AdHocWorkInput>, state: String, serviceDate: String, scheduledAtEpochMillis: Long? = null): String = error("Visit setup unavailable")
    @Suppress("DEPRECATION")
    suspend fun createNewCustomerVisit(input: CustomerWithFirstSiteInput, adHocWork: List<AdHocWorkInput>, state: String, serviceDate: String, scheduledAtEpochMillis: Long? = null): String =
        createNewCustomerVisit(
            NewCustomerVisitInput(
                customerName = input.customer.name,
                phone = input.customer.phone,
                email = input.customer.email,
                locationLabel = input.site.name,
                address = input.site.address,
                customerType = input.customer.customerType,
            ),
            adHocWork,
            state,
            serviceDate,
            scheduledAtEpochMillis,
        )
    @Deprecated("Use createNewCustomerVisit(CustomerWithFirstSiteInput, ...)")
    suspend fun createNewCustomerVisit(input: NewCustomerVisitInput, adHocWork: List<AdHocWorkInput>, state: String, serviceDate: String, scheduledAtEpochMillis: Long? = null): String = error("Visit setup unavailable")
    @Deprecated("Use createNewCustomerVisit(CustomerWithFirstSiteInput, ...)")
    suspend fun createOneTimeVisit(oneTime: OneTimeVisitInput, adHocWork: List<AdHocWorkInput>, state: String, serviceDate: String, scheduledAtEpochMillis: Long? = null): String = error("Visit setup unavailable")
    suspend fun addAdHocWork(visitId: String, input: AdHocWorkInput): String = error("Ad-hoc work unavailable")
    @Deprecated("Use updateCustomer with CustomerInput.customerType")
    suspend fun makeCustomerStandard(customerId: String): Long = error("Customer promotion unavailable")
    suspend fun equipmentLinkContext(workItemId: String): EquipmentLinkContext = error("Equipment linking unavailable")
    suspend fun linkWorkItemEquipment(workItemId: String, equipmentId: String): Long = error("Equipment linking unavailable")
    suspend fun createAndLinkEquipment(workItemId: String, input: EquipmentInput): String = error("Equipment linking unavailable")
    suspend fun startVisit(id: String): Long = error("Visit unavailable")
    suspend fun rescheduleVisit(id: String, serviceDate: String, scheduledAtEpochMillis: Long?, reason: String): Long = error("Visit unavailable")
    suspend fun cancelVisit(id: String, reason: String): Long = error("Visit unavailable")
    suspend fun restoreVisit(id: String, serviceDate: String, scheduledAtEpochMillis: Long? = null): Long = error("Visit unavailable")
    suspend fun addPart(workItemId: String, description: String, quantity: String, unit: String): String = error("Part entry unavailable")
    suspend fun updatePart(workItemId: String, partId: String, description: String, quantity: String, unit: String): Long = error("Part update unavailable")
    suspend fun removePart(workItemId: String, partId: String): Long = error("Part removal unavailable")
    suspend fun savePhoto(workItemId: String, bytes: ByteArray, displayName: String?, mimeType: String, includeInReport: Boolean, caption: String?): String = error("Photo intake unavailable")
    suspend fun updatePhoto(workItemId: String, photoId: String, caption: String?, includeInReport: Boolean): Long = error("Photo update unavailable")
    suspend fun savePhotoCaption(workItemId: String, photoId: String, caption: String): Long = error("Photo caption unavailable")
    suspend fun setPhotoReportInclusion(workItemId: String, photoId: String, includeInReport: Boolean): Long = error("Photo report choice unavailable")
    suspend fun removePhoto(workItemId: String, photoId: String): Long = error("Photo removal unavailable")
    suspend fun createContactNote(input: ContactNoteInput): String = error("Contact note unavailable")
    suspend fun contactNote(id: String): ContactNoteDetail? = null
    suspend fun markContactNoteEnteredInError(id: String, reason: String): Long = error("Contact note unavailable")
    suspend fun createFollowUp(input: FollowUpInput): String = error("Follow-up unavailable")
    suspend fun updateFollowUp(id: String, title: String, dueDate: String, privateNote: String, reason: String): Long = error("Follow-up unavailable")
    suspend fun createCorrectiveFollowUp(workItemId: String, title: String, dueDate: String, privateNote: String): String = error("Corrective follow-up unavailable")
    suspend fun correctiveFollowUps(workItemId: String): List<FollowUpDetail> = emptyList()
    suspend fun changeFollowUpState(id: String, state: String, reason: String, newDueDate: String? = null): Long = error("Follow-up unavailable")
    suspend fun history(query: HistoryQuery): List<HistoryEntry> = emptyList()
    suspend fun attention(): List<AttentionItem> = emptyList()
    suspend fun recordVersions(recordId: String): Pair<List<RecordVersionSummary>, List<ReportVersionSummary>> = emptyList<RecordVersionSummary>() to emptyList()
    suspend fun openCorrection(recordId: String): CorrectionDraft = error("Correction unavailable")
    suspend fun saveCorrection(value: CorrectionDraft): Long = error("Correction unavailable")
    suspend fun addCorrectionEvidence(recordId: String, correctionWorkItemId: String, bytes: ByteArray, displayName: String?, mimeType: String, caption: String?): Long = error("Correction evidence unavailable")
    suspend fun commitCorrection(recordId: String): String = error("Correction unavailable")
    suspend fun discardCorrection(recordId: String): Boolean = false
    suspend fun voidRecord(recordId: String, publicReason: String, privateReason: String?): Boolean = error("Void unavailable")
    suspend fun lifecycleReview(subjectType: String, id: String, action: String): LifecycleReview = error("Lifecycle unavailable")
    suspend fun applyLifecycle(subjectType: String, id: String, action: String, reason: String): Boolean = error("Lifecycle unavailable")
    suspend fun moveReview(equipmentId: String): MoveReview = error("Move unavailable")
    suspend fun moveEquipment(equipmentId: String, destinationSiteId: String, effectiveDate: String, reason: String, acknowledged: Boolean): Boolean = error("Move unavailable")
    suspend fun datasetSummary(): DatasetSummary = error("Recovery unavailable")
    suspend fun setBackupReminder(days: Int) = Unit
    suspend fun createBackup(passphrase: CharArray, incompleteAcknowledged: Boolean = false): BackupResult = error("Backup unavailable")
    fun inspectBackup(bytes: ByteArray, passphrase: CharArray): BackupInspection = error("Backup unavailable")
    suspend fun recordVerifiedBackup(result: BackupResult, destination: String) = Unit
    suspend fun restoreBackup(inspection: BackupInspection, confirmation: String, incompleteAcknowledged: Boolean): Unit = error("Restore unavailable")
    suspend fun directoryCsv(includeInactive: Boolean, includePrivate: Boolean, customerId: String? = null): ByteArray = error("Export unavailable")
    suspend fun recordsCsvPackage(includeInactive: Boolean, includePrivate: Boolean, includePreviousRevisions: Boolean, customerId: String? = null): ByteArray = error("Export unavailable")
    suspend fun validateDirectoryCsv(bytes: ByteArray): CsvImportPreview = error("Import unavailable")
    suspend fun importDirectory(preview: CsvImportPreview, createSeparate: Set<String> = emptySet(), skippedBranches: Set<String> = emptySet()): ImportResult = error("Import unavailable")
    suspend fun importServiceLoopSync(value: ServiceLoopSyncPackage): SyncImportResult = error("ServiceLoop sync import unavailable")
    suspend fun erase(acknowledged: Boolean, confirmation: String): Unit = error("Erase unavailable")
    suspend fun reminderPreferences(): ReminderPreferences = ReminderPreferences()
    suspend fun saveReminderPreferences(value: ReminderPreferences): Long = error("Reminder settings unavailable")
    suspend fun setAppointmentReminderLead(visitId: String, minutes: Int?): Long = error("Appointment reminder unavailable")
}

data class NextDueRecoveryRequest(
    val workItemId: String,
    val visitId: String,
    val expectedOutcome: String,
    val expectedCapturedObligationId: String,
    val expectedCalculatedDate: String,
)

sealed interface NextDueRecoveryResult {
    data class Applied(val savedAtEpochMillis: Long) : NextDueRecoveryResult
    data object Superseded : NextDueRecoveryResult
}

fun interface DraftWriteGate { suspend fun beforeWrite() }
fun interface FinalizationWriteGate { suspend fun beforeCommit() }

internal fun ReminderPreferencesEntity.toDomain() = ReminderPreferences(
    dailySummaryEnabled, summaryHour, summaryMinute, summaryDaysMask, dueSoonHorizonDays,
    includeDueServices, includeVisits, includeFollowUps, includeUnfinishedVisits,
    includeBackupReminder, appointmentAlertsEnabled, defaultAppointmentLeadMinutes,
)

internal fun ReminderPreferences.toEntity() = ReminderPreferencesEntity(
    dailySummaryEnabled = dailySummaryEnabled, summaryHour = summaryHour, summaryMinute = summaryMinute,
    summaryDaysMask = summaryDaysMask, dueSoonHorizonDays = dueSoonHorizonDays,
    includeDueServices = includeDueServices, includeVisits = includeVisits,
    includeFollowUps = includeFollowUps, includeUnfinishedVisits = includeUnfinishedVisits,
    includeBackupReminder = includeBackupReminder, appointmentAlertsEnabled = appointmentAlertsEnabled,
    defaultAppointmentLeadMinutes = defaultAppointmentLeadMinutes,
)

class RoomServiceLoopRepository(
    private val database: ServiceLoopDatabase,
    private val businessTime: BusinessTime,
    private val writeGate: DraftWriteGate = DraftWriteGate {},
    private val finalizationWriteGate: FinalizationWriteGate = FinalizationWriteGate {},
    private val attachmentRoot: File? = null,
    private val businessDateSignal: BusinessDateSignal? = null,
) : ServiceLoopRepository {
    private val dao = database.serviceLoopDao()
    private val dispatchDao = database.dispatchDao()
    init {
        database.openHelper.writableDatabase.execSQL("INSERT OR IGNORE INTO technician_identity(id,technicianId,displayName,createdAtEpochMillis,modifiedAtEpochMillis) SELECT 'primary', ?, COALESCE(NULLIF(TRIM((SELECT technicianName FROM business_profiles WHERE id='primary')),''), 'Technician'), CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER), CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)", arrayOf(TechnicianIdCodec.generate()))
    }
    private val stage4 by lazy { Stage4Service(database, businessTime, requireNotNull(attachmentRoot) { "App-owned storage is unavailable" }) }

    override suspend fun history(query: HistoryQuery) = stage4.history(query)
    override suspend fun attention() = stage4.attention()
    override suspend fun recordVersions(recordId: String) = stage4.versions(recordId)
    override suspend fun openCorrection(recordId: String) = stage4.openCorrection(recordId)
    override suspend fun saveCorrection(value: CorrectionDraft) = stage4.saveCorrection(value)
    override suspend fun addCorrectionEvidence(recordId: String, correctionWorkItemId: String, bytes: ByteArray, displayName: String?, mimeType: String, caption: String?) = stage4.addCorrectionEvidence(recordId, correctionWorkItemId, bytes, displayName, mimeType, caption)
    override suspend fun commitCorrection(recordId: String) = stage4.commitCorrection(recordId)
    override suspend fun discardCorrection(recordId: String) = stage4.discardCorrection(recordId)
    override suspend fun voidRecord(recordId: String, publicReason: String, privateReason: String?) = stage4.voidRecord(recordId, publicReason, privateReason)
    override suspend fun lifecycleReview(subjectType: String, id: String, action: String) = stage4.lifecycleReview(subjectType, id, action)
    override suspend fun applyLifecycle(subjectType: String, id: String, action: String, reason: String) = stage4.applyLifecycle(subjectType, id, action, reason)
    override suspend fun moveReview(equipmentId: String) = stage4.moveReview(equipmentId)
    override suspend fun moveEquipment(equipmentId: String, destinationSiteId: String, effectiveDate: String, reason: String, acknowledged: Boolean) = stage4.moveEquipment(equipmentId, destinationSiteId, effectiveDate, reason, acknowledged)
    override suspend fun datasetSummary() = stage4.datasetSummary()
    override suspend fun setBackupReminder(days: Int) = stage4.setBackupReminder(days)
    override suspend fun createBackup(passphrase: CharArray, incompleteAcknowledged: Boolean) = stage4.createBackup(passphrase, incompleteAcknowledged)
    override fun inspectBackup(bytes: ByteArray, passphrase: CharArray) = stage4.inspectBackup(bytes, passphrase)
    override suspend fun recordVerifiedBackup(result: BackupResult, destination: String) = stage4.recordVerifiedBackup(result, destination)
    override suspend fun restoreBackup(inspection: BackupInspection, confirmation: String, incompleteAcknowledged: Boolean) {
        stage4.restoreBackup(inspection, confirmation, incompleteAcknowledged)
        val zone = dao.businessProfile()?.zoneId?.let(ZoneId::of) ?: ZoneId.systemDefault()
        (businessTime as? MutableBusinessTime)?.updateZone(zone)
        businessDateSignal?.invalidate()
    }
    override suspend fun directoryCsv(includeInactive: Boolean, includePrivate: Boolean, customerId: String?) = stage4.directoryCsv(includeInactive, includePrivate, customerId)
    override suspend fun recordsCsvPackage(includeInactive: Boolean, includePrivate: Boolean, includePreviousRevisions: Boolean, customerId: String?) = stage4.recordsCsvPackage(includeInactive, includePrivate, includePreviousRevisions, customerId)
    override suspend fun validateDirectoryCsv(bytes: ByteArray) = stage4.validateDirectoryCsv(bytes)
    override suspend fun importDirectory(preview: CsvImportPreview, createSeparate: Set<String>, skippedBranches: Set<String>) = stage4.importDirectory(preview, createSeparate, skippedBranches)
    override suspend fun importServiceLoopSync(value: ServiceLoopSyncPackage): SyncImportResult {
        val result = ServiceLoopSyncImporter(database, businessTime, writeGate).apply(value)
        (businessTime as? MutableBusinessTime)?.updateZone(ZoneId.of(result.zoneId))
        businessDateSignal?.invalidate()
        return result
    }
    override suspend fun erase(acknowledged: Boolean, confirmation: String) {
        stage4.erase(acknowledged, confirmation)
        (businessTime as? MutableBusinessTime)?.updateZone(ZoneId.systemDefault())
        businessDateSignal?.invalidate()
    }

    override suspend fun home(): HomeSummary {
        val today = businessTime.today(); val visit = dao.latestWorkingVisit(); val booked = dao.nextBookedVisit(); val followUp = dao.firstDueFollowUp(today.toString()); val horizon = reminderPreferences().dueSoonHorizonDays
        val resume = visit?.let { serviceVisitProgress(it.id).preferredResumeItem()?.workItemId ?: dao.firstWorkItemId(it.id) }
        return HomeSummary(visit?.id, visit?.reference, visit?.siteNameSnapshot, visit?.modifiedAtEpochMillis, resume, booked?.reference, booked?.actualServiceDate, dao.dueFollowUpCount(today.toString()), followUp?.reference, followUp?.title, dao.overdueCount(today.toString()), dao.dueSoonCount(today.toString(), today.plusDays(horizon.toLong()).toString()), dao.workingVisitCount(), dao.bookedVisitCount(), horizon)
    }

    override suspend fun visits() = dao.visits().map { row ->
        val state = VisitLifecycleState.normalize(row.state)
        val resume = if (state == VisitLifecycleState.WORKING.code) serviceVisitProgress(row.id).preferredResumeItem()?.workItemId ?: row.resumeWorkItemId else row.resumeWorkItemId
        VisitSummary(
            id = row.id,
            reference = row.reference,
            siteName = row.siteName,
            actualServiceDate = row.actualServiceDate,
            state = state,
            finalRecordId = row.finalRecordId,
            resumeWorkItemId = resume,
            customerId = row.customerId,
            customerName = row.customerName,
            siteId = row.siteId,
            equipmentId = row.equipmentId,
            scheduledAtEpochMillis = row.scheduledAtEpochMillis,
            modifiedAtEpochMillis = row.modifiedAtEpochMillis,
        )
    }

    override suspend fun operationalDashboard(scope: WorkScope): OperationalDashboardProjection {
        val preferences = reminderPreferences()
        val visits = visits()
        val dueServices = mapDueServices(dao.dueServices(), preferences.dueSoonHorizonDays)
        val followUps = dao.followUps().map { followUpDetail(it) }
        return OperationalDashboardProjector.project(
            scope = scope,
            visits = visits,
            dueServices = dueServices,
            followUps = followUps,
            today = businessTime.today(),
            now = businessTime.instant(),
            businessZone = businessTime.zoneId,
            dueSoonHorizonDays = preferences.dueSoonHorizonDays,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeOperationalDashboard(scope: WorkScope): Flow<OperationalDashboardProjection> = flow {
        val invalidations = database.invalidationTracker.createFlow(
            "working_visits", "work_items", "service_plans", "service_obligations", "visit_claims",
            "follow_ups", "equipment", "sites", "customers", "reminder_preferences", emitInitialState = true,
        )
        val dateChanges = businessDateSignal?.tokens ?: flowOf(null)
        merge(invalidations.map { Unit }, dateChanges.map { Unit }).onStart { emit(Unit) }.transformLatest {
            val projection = operationalDashboard(scope)
            emit(projection)
            val nowMillis = businessTime.instant().toEpochMilli()
            val nextAppointment = projection.sections.asSequence()
                .flatMap { it.items.asSequence() }
                .filter { it.kind == OperationalWorkKind.VISIT && it.scheduledAtEpochMillis != null }
                .mapNotNull { it.scheduledAtEpochMillis }
                .filter { it > nowMillis }
                .minOrNull()
            if (nextAppointment == null) awaitCancellation()
            delay((nextAppointment - nowMillis).coerceAtLeast(1L))
        }.collect { emit(it) }
    }
    override suspend fun equipmentList() = dao.equipmentList().map { EquipmentSummary(it.id, it.name, it.reference, it.technicianIdentifier, it.siteName, it.customerName, it.nearestDueDate, CustomerType.fromCode(it.customerType)) }
    override suspend fun customerList() = dao.customerList().map { CustomerSummary(it.id, it.name, it.reference, it.siteCount, it.equipmentCount, CustomerType.fromCode(it.customerType)) }
    override suspend fun siteList() = dao.activeVisitSites().map { site -> SiteRegisterSummary(site.id, site.reference, site.name, site.customerName, site.address.orEmpty(), dao.equipmentForSite(site.id).size, CustomerType.fromCode(site.customerType)) }

    override suspend fun customer(id: String): CustomerDetail? {
        val customer = dao.customer(id) ?: return null
        val siteEntities = dao.sitesForCustomer(id)
        val sites = siteEntities.map { site -> SiteSummary(site.id, site.reference, site.name, site.address.orEmpty(), dao.equipmentForSite(site.id).size, site.isDefault) }
        val customerType = CustomerType.fromCode(customer.customerType)
        val equipment = siteEntities.flatMap { site -> dao.equipmentForSite(site.id).map { item -> EquipmentSummary(item.id, item.name, item.reference, item.technicianIdentifier, site.name, customer.name, dao.plansForEquipment(item.id).filter { it.state == "ACTIVE" }.minOfOrNull { it.currentDueDate }, customerType) } }
        val followUps = dao.followUpsForCustomer(id).filter { it.state == "OPEN" }.map { followUpDetail(it) }
        val contacts = dao.contactNotesForCustomer(id).take(5).map { ContactNoteDetail(it.id, it.reference, it.channel, it.occurredAtEpochMillis, it.outcome, it.privateNote.orEmpty(), it.enteredInError, it.errorReason) }
        val hasServicePlans = dao.servicePlanCountForCustomer(id) > 0
        return CustomerDetail(customer.id, customer.reference, customer.name, customer.contactName.orEmpty(), customer.phone.orEmpty(), customer.email.orEmpty(), customer.privateNote.orEmpty(), sites, equipment, followUps, contacts, customer.state, customerType, canMarkOneTime = customerType == CustomerType.ONE_TIME || !hasServicePlans, oneTimeBlockReason = if (customerType == CustomerType.STANDARD && hasServicePlans) "This customer has recurring service plans and cannot be marked one-time." else null)
    }

    override suspend fun site(id: String): SiteDetail? {
        val site = dao.site(id) ?: return null
        val customer = dao.customer(site.customerId) ?: return null
        val customerType = CustomerType.fromCode(customer.customerType)
        val equipment = dao.equipmentForSite(id).map { item ->
            EquipmentSummary(item.id, item.name, item.reference, item.technicianIdentifier, site.name, customer.name, dao.plansForEquipment(item.id).filter { it.state == "ACTIVE" }.minOfOrNull { it.currentDueDate }, customerType)
        }
        return SiteDetail(site.id, customer.id, customer.name, site.reference, site.name, site.address.orEmpty(), site.contactName.orEmpty(), site.phone.orEmpty(), site.email.orEmpty(), site.privateAccessNotes.orEmpty(), site.isDefault, equipment, site.state, customer.contactName.orEmpty(), customer.phone.orEmpty(), customer.email.orEmpty(), CustomerType.fromCode(customer.customerType))
    }

    override suspend fun dueServices(): List<DueService> {
        return mapDueServices(dao.dueServices(), reminderPreferences().dueSoonHorizonDays)
    }

    override fun observeDueServices(): Flow<List<DueService>> = combine(
        dao.observeDueServices(),
        dao.observeReminderPreferences(),
        businessDateSignal?.tokens ?: flowOf(null),
    ) { rows, preferences, _ -> mapDueServices(rows, preferences?.dueSoonHorizonDays ?: 14) }

    override fun observeRootInvalidations(): Flow<Unit> = database.invalidationTracker.createFlow(
        "working_visits", "service_plans", "service_obligations", "follow_ups", "equipment",
        "sites", "customers", "business_profiles", "reminder_preferences", emitInitialState = false,
    ).map { Unit }

    private fun mapDueServices(rows: List<DueServiceRow>, horizonDays: Int = 14): List<DueService> {
        val today = businessTime.today()
        return rows.map { row ->
            val due = LocalDate.parse(row.dueDate)
            val bucket = DueClassifier.bucket(due, today, horizonDays)
            DueService(row.planId, row.planReference, row.planName, row.dueDate, row.obligationId, row.equipmentId, row.equipmentReference, row.equipmentName, row.siteId, row.siteName, row.customerId, row.customerName, row.claimedVisitId, bucket)
        }
    }

    override suspend fun visitSites(): List<VisitSiteOption> = dao.activeVisitSites().map { site ->
        val customerType = CustomerType.fromCode(site.customerType)
        val activeEquipment = dao.equipmentForSite(site.id).filter { it.state == "ACTIVE" }
        val activePlansByEquipment = activeEquipment.associate { equipment ->
            equipment.id to dao.plansForEquipment(equipment.id).filter { it.state == "ACTIVE" }
        }
        VisitSiteOption(
            site.id,
            site.reference,
            site.name,
            site.customerName,
            activeEquipment.map { item ->
                EquipmentSummary(
                    item.id,
                    item.name,
                    item.reference,
                    item.technicianIdentifier,
                    site.name,
                    site.customerName,
                    activePlansByEquipment[item.id].orEmpty().minOfOrNull { it.currentDueDate },
                    customerType,
                )
            },
            customerType,
            activePlansByEquipment.mapValues { (_, plans) -> plans.mapNotNull { it.reusableTemplateId }.toSet() },
        )
    }

    override suspend fun plan(id: String): PlanDetail? {
        val plan = dao.plan(id) ?: return null; val equipment = dao.equipment(plan.equipmentId) ?: return null
        return PlanDetail(plan.id, equipment.id, equipment.name, plan.reference, plan.name, plan.intervalCount, plan.intervalUnit, plan.currentDueDate, plan.state, plan.reusableTemplateId)
    }

    override suspend fun templates(): List<TemplateSummary> = dao.reusableTemplates()
        .filter { it.state != "DELETED" }
        .sortedWith(compareBy<ReusableTemplateEntity> { it.state != "ACTIVE" }.thenBy { it.name.lowercase() }.thenBy { it.reference })
        .map { template ->
        val revision = dao.reusableTemplateRevision(template.currentRevisionId)
        TemplateSummary(template.id, template.reference, template.name, revision?.revisionNumber ?: 0, revision?.let { dao.reusableTemplateItems(it.id).size } ?: 0, template.state)
    }

    override suspend fun template(id: String): TemplateDetail? {
        val template = dao.reusableTemplate(id) ?: return null
        val revision = dao.reusableTemplateRevision(template.currentRevisionId) ?: return null
        return TemplateDetail(template.id, template.reference, template.name, revision.revisionNumber, template.state, dao.reusableTemplateItems(revision.id).map { TemplateItemDraft(it.label, it.responseType, it.unit.orEmpty(), it.required, it.privateGuidance.orEmpty()) })
    }

    override suspend fun visit(id: String): VisitDetail? {
        val visit = dao.visit(id) ?: return null
        return VisitDetail(visit.id, visit.reference, VisitLifecycleState.normalize(visit.state), visit.customerId, visit.customerNameSnapshot, visit.siteId, visit.siteNameSnapshot, visit.siteAddressSnapshot.orEmpty(), visit.actualServiceDate, visit.scheduledAtEpochMillis, visit.appointmentZoneId, dao.visitWorkItems(id).map { item -> VisitLine(item.id, item.equipmentNameSnapshot, item.equipmentReferenceSnapshot, item.serviceNameSnapshot, item.dueDateSnapshot, item.outcome, WorkSubjectType.fromCode(item.subjectType), item.equipmentId, item.equipmentDescriptionSnapshot) }, visit.cancellationReason, visit.cancellationOrigin, visit.appointmentReminderLeadMinutes, dao.customer(visit.customerId)?.let { CustomerType.fromCode(it.customerType) } ?: CustomerType.STANDARD)
    }

    override suspend fun followUps() = dao.followUps().map { followUpDetail(it) }
    override suspend fun followUp(id: String) = dao.followUp(id)?.let { followUpDetail(it) }
    override suspend fun parts(workItemId: String) = dao.parts(workItemId).map { PartEntry(it.id, it.description, it.quantity, it.unit) }
    override suspend fun photos(workItemId: String) = dao.workItemAttachments(workItemId).map { PhotoEntry(it.id, it.storedRelativePath, it.mimeType, it.byteSize, it.includedInCustomerReport, it.caption) }
    override suspend fun search(query: String): List<SearchTarget> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return emptyList()
        return dao.search("%${normalized.replace("%", "\\%").replace("_", "\\_")}%").map {
            SearchTarget(
                type = it.type,
                id = it.id,
                reference = it.reference,
                title = it.title,
                subtitle = it.subtitle,
                customerType = CustomerType.fromCode(it.customerType),
                status = it.status,
                revisionNumber = it.revisionNumber,
                itemCount = it.itemCount,
            )
        }
    }

    override suspend fun createCustomer(input: CustomerInput): String {
        validateCustomerInput(input); writeGate.beforeWrite(); val id = UUID.randomUUID().toString()
        dao.insertCustomers(listOf(CustomerEntity(id, ordinaryReference("CU", dao.customerCount() + 1), input.name.trim(), normalizedOptional(input.contactName), normalizedOptional(input.phone), normalizedOptional(input.email), normalizedOptional(input.privateNote), customerType = input.customerType.code)))
        return id
    }

    override suspend fun createCustomerWithFirstSite(customer: CustomerInput, site: SiteInput): Pair<String, String> {
        writeGate.beforeWrite()
        val created = database.withTransaction {
            createCustomerWithFirstSiteInTransaction(dao, CustomerWithFirstSiteInput(customer, site))
        }
        return created.customer.id to created.site.id
    }

    override suspend fun updateCustomer(id: String, input: CustomerInput): Long {
        validateCustomerInput(input)
        writeGate.beforeWrite()
        val now = businessTime.instant().toEpochMilli()
        database.withTransaction {
            val old = dao.customer(id) ?: error("Customer no longer exists")
            val oldType = CustomerType.fromCode(old.customerType)
            if (oldType == CustomerType.STANDARD && input.customerType == CustomerType.ONE_TIME) {
                require(dao.servicePlanCountForCustomer(id) == 0) { "A customer with recurring service plans cannot be marked one-time" }
            }
            val value = old.copy(name = input.name.trim(), contactName = clean(input.contactName), phone = clean(input.phone), email = clean(input.email), privateNote = clean(input.privateNote), customerType = input.customerType.code)
            if (value != old) dao.updateCustomer(value)
        }
        return now
    }

    override suspend fun createSite(customerId: String, input: SiteInput): String {
        validateSiteInput(input); val customer = dao.customer(customerId) ?: error("Customer no longer exists"); require(customer.state == "ACTIVE")
        writeGate.beforeWrite(); val id = UUID.randomUUID().toString(); val existing = dao.sitesForCustomer(customerId)
        val value = SiteEntity(id, customerId, ordinaryReference("ST", dao.siteCount() + 1), input.name.trim(), normalizedOptional(input.address), normalizedOptional(input.privateAccessNote), normalizedOptional(input.contactName), normalizedOptional(input.phone), normalizedOptional(input.email), input.isDefault || existing.isEmpty())
        database.withTransaction { if (value.isDefault) existing.filter { it.isDefault }.forEach { dao.updateSite(it.copy(isDefault = false)) }; dao.insertSites(listOf(value)) }
        return id
    }

    override suspend fun updateSite(id: String, input: SiteInput): Long {
        validateSiteInput(input); val old = dao.site(id) ?: error("Site no longer exists")
        val value = old.copy(name = input.name.trim(), address = normalizedOptional(input.address), contactName = normalizedOptional(input.contactName), phone = normalizedOptional(input.phone), email = normalizedOptional(input.email), privateAccessNotes = normalizedOptional(input.privateAccessNote), isDefault = input.isDefault)
        if (value == old) return businessTime.instant().toEpochMilli(); writeGate.beforeWrite()
        database.withTransaction { if (value.isDefault) dao.sitesForCustomer(old.customerId).filter { it.id != id && it.isDefault }.forEach { dao.updateSite(it.copy(isDefault = false)) }; dao.updateSite(value) }
        return businessTime.instant().toEpochMilli()
    }

    override suspend fun createEquipment(siteId: String, input: EquipmentInput): String {
        validateEquipment(input); val site = dao.site(siteId) ?: error("Site no longer exists"); require(site.state == "ACTIVE")
        writeGate.beforeWrite(); val id = UUID.randomUUID().toString(); dao.insertEquipment(listOf(EquipmentEntity(id, siteId, reference("EQ", dao.equipmentCount() + 1), clean(input.technicianIdentifier), input.name.trim(), clean(input.make), clean(input.model), clean(input.serialNumber), clean(input.privateNote))))
        return id
    }

    override suspend fun updateEquipment(id: String, input: EquipmentInput): Long {
        validateEquipment(input); val old = dao.equipment(id) ?: error("Equipment no longer exists")
        val value = old.copy(name = input.name.trim(), technicianIdentifier = clean(input.technicianIdentifier), make = clean(input.make), model = clean(input.model), serialNumber = clean(input.serialNumber), privateNotes = clean(input.privateNote))
        if (value == old) return businessTime.instant().toEpochMilli(); writeGate.beforeWrite(); dao.updateEquipment(value); return businessTime.instant().toEpochMilli()
    }

    override suspend fun createPlan(equipmentId: String, input: PlanInput): String {
        validatePlan(input); val equipment = dao.equipment(equipmentId) ?: error("Equipment no longer exists"); require(equipment.state == "ACTIVE")
        val site = dao.site(equipment.siteId) ?: error("Site no longer exists")
        val customer = dao.customer(site.customerId) ?: error("Customer no longer exists")
        require(CustomerType.fromCode(customer.customerType) == CustomerType.STANDARD) { "Recurring service requires a Standard customer" }
        input.reusableTemplateId?.let { templateId -> require(dao.reusableTemplate(templateId)?.state == "ACTIVE") { "Template is unavailable" } }
        writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli(); val id = UUID.randomUUID().toString(); val obligationId = UUID.randomUUID().toString()
        database.withTransaction { dao.insertPlans(listOf(ServicePlanEntity(id, equipmentId, reference("P", dao.planCount() + 1), input.name.trim(), input.intervalCount, input.intervalUnit, input.dueDate, "ACTIVE", obligationId, reusableTemplateId = input.reusableTemplateId))); dao.insertObligations(listOf(ServiceObligationEntity(obligationId, id, 1, input.dueDate, now))) }
        return id
    }

    override suspend fun updatePlan(id: String, input: PlanInput): Long {
        validatePlan(input); val old = dao.plan(id) ?: error("Plan no longer exists"); val oldEquipment = dao.equipment(old.equipmentId) ?: error("Equipment no longer exists"); val oldSite = dao.site(oldEquipment.siteId) ?: error("Site no longer exists"); val oldCustomer = dao.customer(oldSite.customerId) ?: error("Customer no longer exists")
        require(CustomerType.fromCode(oldCustomer.customerType) == CustomerType.STANDARD) { "Recurring service requires a Standard customer" }
        val obligation = old.currentObligationId?.let { dao.obligation(it) } ?: error("Current obligation missing")
        input.reusableTemplateId?.let { templateId ->
            val template = dao.reusableTemplate(templateId)
            require(template?.state == "ACTIVE" || (templateId == old.reusableTemplateId && template?.state == "DISABLED")) { "Template is unavailable" }
        }
        if (input.dueDate != old.currentDueDate) require(input.dueDateChangeReason.trim().isNotEmpty()) { "Explain why the due date changed" }
        require(obligation.consumedAtEpochMillis == null); val value = old.copy(name = input.name.trim(), intervalCount = input.intervalCount, intervalUnit = input.intervalUnit, currentDueDate = input.dueDate, reusableTemplateId = input.reusableTemplateId)
        if (value == old) return businessTime.instant().toEpochMilli(); writeGate.beforeWrite(); val now=businessTime.instant().toEpochMilli(); database.withTransaction { check(dao.updateCurrentObligationDueDate(obligation.id, input.dueDate) == 1); dao.updatePlan(value); if(input.dueDate!=old.currentDueDate) dao.insertPlanScheduleChange(PlanScheduleChangeEntity(UUID.randomUUID().toString(),id,old.currentDueDate,input.dueDate,input.dueDateChangeReason.trim(),now)) }; return now
    }

    override suspend fun createTemplate(name: String, items: List<TemplateItemDraft>): String {
        validateTemplate(name, items); writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli(); val id = UUID.randomUUID().toString(); val revisionId = UUID.randomUUID().toString()
        database.withTransaction { dao.insertReusableTemplate(ReusableTemplateEntity(id, reference("IT", dao.reusableTemplateCount() + 1), name.trim(), revisionId, modifiedAtEpochMillis = now)); dao.insertReusableTemplateRevision(ReusableTemplateRevisionEntity(revisionId, id, 1, name.trim(), now)); dao.insertReusableTemplateItems(items.mapIndexed { index, item -> reusableItem(revisionId, index, item) }) }
        return id
    }

    override suspend fun reviseTemplate(id: String, name: String, items: List<TemplateItemDraft>): Long {
        validateTemplate(name, items); val template = dao.reusableTemplate(id) ?: error("Template no longer exists"); require(template.state != "DELETED") { "Deleted templates cannot be revised" }; val revisionNumber = (dao.reusableTemplateRevisions(id).maxOfOrNull { it.revisionNumber } ?: 0) + 1
        writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli(); val revisionId = UUID.randomUUID().toString(); database.withTransaction { dao.insertReusableTemplateRevision(ReusableTemplateRevisionEntity(revisionId, id, revisionNumber, name.trim(), now)); dao.insertReusableTemplateItems(items.mapIndexed { index, item -> reusableItem(revisionId, index, item) }); check(dao.publishTemplateRevision(template.id, name.trim(), revisionId, now) == 1) }; return now
    }

    override suspend fun templateRevisions(id: String): List<TemplateRevisionDetail> = dao.reusableTemplateRevisions(id).map { revision ->
        TemplateRevisionDetail(
            id = revision.id,
            revisionNumber = revision.revisionNumber,
            name = revision.nameSnapshot,
            createdAtEpochMillis = revision.createdAtEpochMillis,
            items = dao.reusableTemplateItems(revision.id).map { item ->
                TemplateItemDraft(item.label, item.responseType, item.unit.orEmpty(), item.required, item.privateGuidance.orEmpty())
            },
        )
    }

    override suspend fun templateServicePlanReferenceCount(id: String): Int = dao.servicePlanCountForTemplate(id)

    override suspend fun setTemplateState(id: String, state: String): Long {
        require(state == "ACTIVE" || state == "DISABLED") { "Unsupported template state" }
        val template = dao.reusableTemplate(id) ?: error("Template no longer exists")
        require(template.state != "DELETED") { "Deleted templates cannot be enabled" }
        if (template.state == state) return businessTime.instant().toEpochMilli()
        writeGate.beforeWrite()
        val now = businessTime.instant().toEpochMilli()
        check(dao.updateTemplateState(id, template.state, state, now) == 1) { "Template changed — reload it before updating" }
        return now
    }

    override suspend fun deleteTemplate(id: String): Long {
        val template = dao.reusableTemplate(id) ?: error("Template no longer exists")
        require(template.state != "DELETED") { "Template is already deleted" }
        require(dao.servicePlanCountForTemplate(id) == 0) { "This template is still used by a service plan. Remove it from those plans before deleting it." }
        writeGate.beforeWrite()
        val now = businessTime.instant().toEpochMilli()
        check(dao.updateTemplateState(id, template.state, "DELETED", now) == 1) { "Template changed — reload it before deleting" }
        return now
    }

    override suspend fun cloneTemplate(id: String): String {
        val source = dao.reusableTemplate(id) ?: error("Template no longer exists")
        require(source.state != "DELETED") { "Deleted templates cannot be cloned" }
        val revision = dao.reusableTemplateRevision(source.currentRevisionId) ?: error("Template revision missing")
        val items = dao.reusableTemplateItems(revision.id)
        val clonedName = "${revision.nameSnapshot} copy"
        validateTemplate(clonedName, items.map { TemplateItemDraft(it.label, it.responseType, it.unit.orEmpty(), it.required, it.privateGuidance.orEmpty()) })
        writeGate.beforeWrite()
        val now = businessTime.instant().toEpochMilli()
        val newId = UUID.randomUUID().toString()
        val newRevisionId = UUID.randomUUID().toString()
        database.withTransaction {
            dao.insertReusableTemplate(ReusableTemplateEntity(newId, reference("IT", dao.reusableTemplateCount() + 1), clonedName, newRevisionId, modifiedAtEpochMillis = now))
            dao.insertReusableTemplateRevision(ReusableTemplateRevisionEntity(newRevisionId, newId, 1, clonedName, now))
            dao.insertReusableTemplateItems(items.mapIndexed { index, item -> reusableItem(newRevisionId, index, TemplateItemDraft(item.label, item.responseType, item.unit.orEmpty(), item.required, item.privateGuidance.orEmpty())) })
        }
        return newId
    }

    override suspend fun createVisit(planIds: List<String>, state: String, serviceDate: String, scheduledAtEpochMillis: Long?): String = database.withTransaction {
        require(planIds.isNotEmpty()) { "Select at least one due service" }; require(state in setOf("BOOKED", "WORKING", "HISTORICAL")); validateNewBookedVisitDate(state, serviceDate, businessTime.today())
        val historical = state == "HISTORICAL"
        val plans = planIds.distinct().map { dao.plan(it) ?: error("Service plan no longer exists") }; val equipment = plans.map { dao.equipment(it.equipmentId) ?: error("Equipment no longer exists") }; val sites = equipment.map { it.siteId }.distinct(); require(sites.size == 1) { "One visit can contain work at one site only" }
        val site = dao.site(sites.single()) ?: error("Site no longer exists"); val customer = dao.customer(site.customerId) ?: error("Customer no longer exists")
        require(CustomerType.fromCode(customer.customerType) == CustomerType.STANDARD) { "Recurring service requires a Standard customer" }
        val obligations = plans.map { plan -> dao.obligation(plan.currentObligationId ?: error("Plan has no current obligation")) ?: error("Current obligation missing") }
        require(plans.all { it.state == "ACTIVE" } && obligations.all { it.consumedAtEpochMillis == null }) { "A selected obligation is no longer current" }
        writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli(); val id = UUID.randomUUID().toString(); val profile = dao.businessProfile(); val zone = businessTime.zoneId.id
        dao.insertVisits(listOf(WorkingVisitEntity(id, reference("V", dao.visitCount() + 1), customer.id, site.id, serviceDate, customer.name, site.name, site.address, if (historical) "WORKING" else state, now, customer.reference, site.reference, profile?.businessName, profile?.technicianName, profile?.phone, profile?.email, profile?.postalAddress, profile?.zoneId, scheduledAtEpochMillis, if (scheduledAtEpochMillis != null) zone else profile?.zoneId)))
        plans.forEachIndexed { index, plan ->
            val eq = equipment[index]; val obligation = obligations[index]
            if (!historical) dao.insertVisitClaim(VisitClaimEntity(obligation.id, id, now))
            val snapshotId = if (state == "BOOKED") null else captureTemplateSnapshot(plan.reusableTemplateId, id, plan.id, now, allowDisabled = true)
            val workId = UUID.randomUUID().toString(); dao.insertWorkItems(listOf(WorkItemEntity(workId, id, eq.id, plan.id, if (historical) null else obligation.id, snapshotId, eq.name, eq.reference, plan.name, plan.reference, obligation.dueDate, plan.intervalCount, plan.intervalUnit, false, null, null, equipmentIdentifierSnapshot = eq.technicianIdentifier, equipmentMakeSnapshot = eq.make, equipmentModelSnapshot = eq.model, equipmentSerialSnapshot = eq.serialNumber))); dao.insertPublicDrafts(listOf(WorkItemPublicDraftEntity(workId, ""))); dao.insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity(workId, "")))
        }
        return@withTransaction id
    }

    override suspend fun createVisitForSite(siteId: String, planIds: List<String>, adHocWork: List<AdHocWorkInput>, state: String, serviceDate: String, scheduledAtEpochMillis: Long?): String {
        writeGate.beforeWrite()
        return database.withTransaction { createVisitForSiteInTransaction(siteId, planIds, adHocWork, state, serviceDate, scheduledAtEpochMillis, allowKnownEquipment = true) }
    }

    override suspend fun createNewCustomerVisit(input: CustomerWithFirstSiteInput, adHocWork: List<AdHocWorkInput>, state: String, serviceDate: String, scheduledAtEpochMillis: Long?): String {
        require(adHocWork.isNotEmpty()) { "Add at least one task" }
        require(state in setOf("BOOKED", "WORKING", "HISTORICAL"))
        LocalDate.parse(serviceDate)
        writeGate.beforeWrite()
        return database.withTransaction {
            val created = createCustomerWithFirstSiteInTransaction(dao, input)
            createVisitForSiteInTransaction(created.site.id, emptyList(), adHocWork, state, serviceDate, scheduledAtEpochMillis, allowKnownEquipment = false)
         }
    }

    @Deprecated("Use createNewCustomerVisit(CustomerWithFirstSiteInput, ...)")
    override suspend fun createNewCustomerVisit(input: NewCustomerVisitInput, adHocWork: List<AdHocWorkInput>, state: String, serviceDate: String, scheduledAtEpochMillis: Long?): String =
        createNewCustomerVisit(
            CustomerWithFirstSiteInput(
                CustomerInput(input.customerName, phone = input.phone, email = input.email, customerType = input.customerType),
                SiteInput(input.locationLabel.trim().ifBlank { input.address.trim().ifBlank { input.customerName } }, input.address, isDefault = true),
            ), adHocWork, state, serviceDate, scheduledAtEpochMillis,
        )

    override suspend fun createOneTimeVisit(oneTime: OneTimeVisitInput, adHocWork: List<AdHocWorkInput>, state: String, serviceDate: String, scheduledAtEpochMillis: Long?): String =
        createNewCustomerVisit(NewCustomerVisitInput(oneTime.customerName, oneTime.phone, oneTime.email, oneTime.locationLabel, oneTime.address, CustomerType.ONE_TIME), adHocWork, state, serviceDate, scheduledAtEpochMillis)

    override suspend fun addAdHocWork(visitId: String, input: AdHocWorkInput): String {
        writeGate.beforeWrite()
        val now = businessTime.instant().toEpochMilli()
        return database.withTransaction {
            val visit = dao.visit(visitId) ?: error("Visit no longer exists")
            require(visit.state in setOf("BOOKED", "WORKING")) { "Visit no longer accepts work" }
            require(dispatchDao.visitBindingForLocalVisit(visitId) == null) { "Dispatched visits cannot add local tasks in this version" }
            val normalized = validateAdHocInput(input, visit.siteId, allowKnownEquipment = true)
            val workItemId = insertAdHocWorkInTransaction(visitId, normalized, now)
            dao.touchVisit(visitId, now)
            workItemId
        }
    }

    @Deprecated("Use updateCustomer with CustomerInput.customerType")
    override suspend fun makeCustomerStandard(customerId: String): Long {
        val customer = dao.customer(customerId) ?: error("Customer no longer exists")
        if (CustomerType.fromCode(customer.customerType) == CustomerType.STANDARD) return businessTime.instant().toEpochMilli()
        writeGate.beforeWrite()
        val now = businessTime.instant().toEpochMilli()
        database.withTransaction {
            val current = dao.customer(customerId) ?: error("Customer no longer exists")
            require(CustomerType.fromCode(current.customerType) == CustomerType.ONE_TIME) { "Customer type cannot be changed" }
            dao.updateCustomer(current.copy(customerType = CustomerType.STANDARD.code))
        }
        return now
    }

    override suspend fun equipmentLinkContext(workItemId: String): EquipmentLinkContext = database.withTransaction {
        val item = validateLinkableWorkItem(workItemId)
        val visit = dao.visit(item.visitId) ?: error("Visit no longer exists")
        val site = dao.site(visit.siteId) ?: error("Site no longer exists")
        EquipmentLinkContext(item.id, visit.id, site.id, site.name, dao.equipmentForSite(site.id).filter { it.state == "ACTIVE" }.map { equipmentSummary(it, site, dao.customer(site.customerId)!!) })
    }

    override suspend fun linkWorkItemEquipment(workItemId: String, equipmentId: String): Long {
        writeGate.beforeWrite()
        val now = businessTime.instant().toEpochMilli()
        database.withTransaction {
            val item = validateLinkableWorkItem(workItemId)
            val visit = dao.visit(item.visitId) ?: error("Visit no longer exists")
            val equipment = dao.equipment(equipmentId) ?: error("Equipment no longer exists")
            require(equipment.state == "ACTIVE") { "Equipment is not active" }
            require(equipment.siteId == visit.siteId) { "Equipment must belong to this visit site" }
            val updated = item.copy(
                equipmentId = equipment.id,
                equipmentNameSnapshot = equipment.name,
                equipmentReferenceSnapshot = equipment.reference,
                equipmentIdentifierSnapshot = equipment.technicianIdentifier,
                equipmentMakeSnapshot = equipment.make,
                equipmentModelSnapshot = equipment.model,
                equipmentSerialSnapshot = equipment.serialNumber,
                equipmentDescriptionSnapshot = null,
            )
            WorkSubjectValidator.validateWorkItem(updated, CustomerType.fromCode(dao.customer(visit.customerId)?.customerType ?: error("Customer no longer exists")))
            dao.updateWorkItem(updated)
            dao.touchVisit(visit.id, now)
        }
        return now
    }

    override suspend fun createAndLinkEquipment(workItemId: String, input: EquipmentInput): String {
        validateEquipment(input)
        writeGate.beforeWrite()
        return database.withTransaction {
            val item = validateLinkableWorkItem(workItemId)
            val visit = dao.visit(item.visitId) ?: error("Visit no longer exists")
            val site = dao.site(visit.siteId) ?: error("Site no longer exists")
            val customer = dao.customer(site.customerId) ?: error("Customer no longer exists")
            val equipmentId = UUID.randomUUID().toString()
            val equipment = EquipmentEntity(equipmentId, site.id, reference("EQ", dao.equipmentCount() + 1), clean(input.technicianIdentifier), input.name.trim(), clean(input.make), clean(input.model), clean(input.serialNumber), clean(input.privateNote))
            dao.insertEquipment(listOf(equipment))
            val updated = item.copy(
                equipmentId = equipment.id,
                equipmentNameSnapshot = equipment.name,
                equipmentReferenceSnapshot = equipment.reference,
                equipmentIdentifierSnapshot = equipment.technicianIdentifier,
                equipmentMakeSnapshot = equipment.make,
                equipmentModelSnapshot = equipment.model,
                equipmentSerialSnapshot = equipment.serialNumber,
                equipmentDescriptionSnapshot = null,
            )
            WorkSubjectValidator.validateWorkItem(updated, CustomerType.fromCode(customer.customerType))
            dao.updateWorkItem(updated)
            dao.touchVisit(visit.id, businessTime.instant().toEpochMilli())
            equipmentId
        }
    }

    private suspend fun createVisitForSiteInTransaction(
        siteId: String,
        planIds: List<String>,
        adHocWork: List<AdHocWorkInput>,
        state: String,
        serviceDate: String,
        scheduledAtEpochMillis: Long?,
        allowKnownEquipment: Boolean,
    ): String {
        require(state in setOf("BOOKED", "WORKING", "HISTORICAL"))
        validateNewBookedVisitDate(state, serviceDate, businessTime.today())
        require(planIds.isNotEmpty() || adHocWork.isNotEmpty()) { "Select planned work or add a task" }
        val site = dao.site(siteId) ?: error("Site no longer exists")
        val customer = dao.customer(site.customerId) ?: error("Customer no longer exists")
        val plans = planIds.distinct().map { planId -> dao.plan(planId) ?: error("Service plan no longer exists") }
        val planEquipment = plans.map { plan -> dao.equipment(plan.equipmentId) ?: error("Equipment no longer exists") }
        require(planEquipment.all { it.siteId == site.id }) { "All planned work must belong to the selected site" }
        if (plans.isNotEmpty()) require(CustomerType.fromCode(customer.customerType) == CustomerType.STANDARD) { "Recurring service requires a Standard customer" }
        val obligations = plans.map { plan ->
            require(plan.state == "ACTIVE") { "A selected obligation is no longer current" }
            val obligation = dao.obligation(plan.currentObligationId ?: error("Plan has no current obligation")) ?: error("Current obligation missing")
            require(obligation.consumedAtEpochMillis == null && dao.claimForObligation(obligation.id) == null) { "A selected obligation is no longer current" }
            obligation
        }
        val normalizedAdHoc = adHocWork.map { validateAdHocInput(it, site.id, allowKnownEquipment) }
        val now = businessTime.instant().toEpochMilli()
        val historical = state == "HISTORICAL"
        val profile = dao.businessProfile()
        val visitId = UUID.randomUUID().toString()
        dao.insertVisits(listOf(WorkingVisitEntity(
            id = visitId,
            reference = reference("V", dao.visitCount() + 1),
            customerId = customer.id,
            siteId = site.id,
            actualServiceDate = serviceDate,
            customerNameSnapshot = customer.name,
            siteNameSnapshot = site.name,
            siteAddressSnapshot = site.address,
            state = if (historical) "WORKING" else state,
            modifiedAtEpochMillis = now,
            customerReferenceSnapshot = customer.reference,
            siteReferenceSnapshot = site.reference,
            reportBusinessNameSnapshot = profile?.businessName,
            reportTechnicianNameSnapshot = profile?.technicianName,
            reportPhoneSnapshot = profile?.phone,
            reportEmailSnapshot = profile?.email,
            reportPostalAddressSnapshot = profile?.postalAddress,
            reportZoneIdSnapshot = profile?.zoneId,
            scheduledAtEpochMillis = scheduledAtEpochMillis,
            appointmentZoneId = if (scheduledAtEpochMillis != null) businessTime.zoneId.id else profile?.zoneId,
        )))
        plans.forEachIndexed { index, plan ->
            val equipment = planEquipment[index]
            val obligation = obligations[index]
            if (!historical) dao.insertVisitClaim(VisitClaimEntity(obligation.id, visitId, now))
            val snapshotId = if (state == "BOOKED") null else captureTemplateSnapshot(plan.reusableTemplateId, visitId, plan.id, now, allowDisabled = true)
            val workId = UUID.randomUUID().toString()
            val item = WorkItemEntity(
                id = workId,
                visitId = visitId,
                equipmentId = equipment.id,
                servicePlanId = plan.id,
                capturedObligationId = obligation.id.takeUnless { historical },
                templateSnapshotId = snapshotId,
                equipmentNameSnapshot = equipment.name,
                equipmentReferenceSnapshot = equipment.reference,
                serviceNameSnapshot = plan.name,
                planReferenceSnapshot = plan.reference,
                dueDateSnapshot = obligation.dueDate,
                intervalCountSnapshot = plan.intervalCount,
                intervalUnitSnapshot = plan.intervalUnit,
                checklistReviewed = false,
                outcome = null,
                fulfillsCurrentObligation = null,
                equipmentIdentifierSnapshot = equipment.technicianIdentifier,
                equipmentMakeSnapshot = equipment.make,
                equipmentModelSnapshot = equipment.model,
                equipmentSerialSnapshot = equipment.serialNumber,
            )
            WorkSubjectValidator.validateWorkItem(item, CustomerType.fromCode(customer.customerType), plan.equipmentId)
            dao.insertWorkItems(listOf(item))
            dao.insertPublicDrafts(listOf(WorkItemPublicDraftEntity(workId, "")))
            dao.insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity(workId, "")))
        }
        normalizedAdHoc.forEach { input -> insertAdHocWorkInTransaction(visitId, input, now) }
        return visitId
    }

    private suspend fun insertAdHocWorkInTransaction(visitId: String, input: AdHocWorkInput, now: Long): String {
        val visit = dao.visit(visitId) ?: error("Visit no longer exists")
        val equipment = input.equipmentId?.let { dao.equipment(it) }
        val workId = UUID.randomUUID().toString()
        val item = WorkItemEntity(
            id = workId,
            visitId = visitId,
            equipmentId = equipment?.id,
            servicePlanId = null,
            capturedObligationId = null,
            templateSnapshotId = captureTemplateSnapshot(input.reusableTemplateId, visitId, workId, now),
            equipmentNameSnapshot = equipment?.name,
            equipmentReferenceSnapshot = equipment?.reference,
            serviceNameSnapshot = input.taskName,
            planReferenceSnapshot = null,
            dueDateSnapshot = null,
            intervalCountSnapshot = null,
            intervalUnitSnapshot = null,
            checklistReviewed = false,
            outcome = null,
            fulfillsCurrentObligation = null,
            equipmentIdentifierSnapshot = equipment?.technicianIdentifier,
            equipmentMakeSnapshot = equipment?.make,
            equipmentModelSnapshot = equipment?.model,
            equipmentSerialSnapshot = equipment?.serialNumber,
            subjectType = input.subjectType.code,
            equipmentDescriptionSnapshot = input.equipmentDescription.takeIf { it.isNotBlank() }.takeUnless { equipment != null },
        )
        WorkSubjectValidator.validateWorkItem(item, CustomerType.fromCode(dao.customer(visit.customerId)?.customerType ?: error("Customer no longer exists")))
        dao.insertWorkItems(listOf(item))
        dao.insertPublicDrafts(listOf(WorkItemPublicDraftEntity(workId, "")))
        dao.insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity(workId, "")))
        return workId
    }

    private suspend fun validateAdHocInput(input: AdHocWorkInput, targetSiteId: String, allowKnownEquipment: Boolean): AdHocWorkInput {
        val taskName = input.taskName.trim()
        require(taskName.isNotBlank()) { "Task name is required" }
        require(taskName.length <= 200) { "Task name must be 200 characters or fewer" }
        val description = input.equipmentDescription.trim()
        input.reusableTemplateId?.let { templateId ->
            val template = dao.reusableTemplate(templateId)
            require(template?.state == "ACTIVE") { "Template is unavailable" }
        }
        when (input.subjectType) {
            WorkSubjectType.SITE -> {
                require(input.equipmentId == null) { "SITE work cannot reference Equipment" }
                require(description.isEmpty()) { "SITE work cannot have an Equipment description" }
            }
            WorkSubjectType.EQUIPMENT -> {
                if (input.equipmentId != null) {
                    require(allowKnownEquipment) { "One-time work cannot select registered Equipment" }
                    val equipment = dao.equipment(input.equipmentId) ?: error("Equipment no longer exists")
                    require(equipment.state == "ACTIVE") { "Equipment is not active" }
                    require(equipment.siteId == targetSiteId) { "Equipment must belong to this visit site" }
                    require(description.isEmpty()) { "Known Equipment work cannot have an unidentified description" }
                } else {
                    require(description.length <= 500) { "Equipment description must be 500 characters or fewer" }
                }
            }
        }
        return input.copy(taskName = taskName, equipmentDescription = description)
    }

    private suspend fun validateLinkableWorkItem(workItemId: String): WorkItemEntity {
        val item = dao.workItem(workItemId) ?: error("Work item no longer exists")
        val visit = dao.visit(item.visitId) ?: error("Visit no longer exists")
        require(visit.state == "WORKING") { "Equipment can be linked only while the visit is Working" }
        require(WorkSubjectType.fromCode(item.subjectType) == WorkSubjectType.EQUIPMENT && item.equipmentId == null && item.servicePlanId == null) { "This task is not awaiting equipment identification" }
        WorkSubjectValidator.validateWorkItem(item, CustomerType.fromCode(dao.customer(visit.customerId)?.customerType ?: error("Customer no longer exists")))
        return item
    }

    private suspend fun equipmentSummary(equipment: EquipmentEntity, site: SiteEntity, customer: CustomerEntity): EquipmentSummary =
        EquipmentSummary(equipment.id, equipment.name, equipment.reference, equipment.technicianIdentifier, site.name, customer.name, dao.plansForEquipment(equipment.id).filter { it.state == "ACTIVE" }.minOfOrNull { it.currentDueDate }, CustomerType.fromCode(customer.customerType))

    override suspend fun startVisit(id: String): Long { writeGate.beforeWrite(); return database.withTransaction {
        val visit = dao.visit(id) ?: error("Visit no longer exists"); require(visit.state == "BOOKED") { "Only a booked visit can be started" }
        val workItems=dao.visitWorkItems(id); require(workItems.isNotEmpty()){ "Add at least one service line before starting" }
        val site=dao.site(visit.siteId)?:error("Site missing"); val customer=dao.customer(site.customerId)?:error("Customer missing"); val profile=dao.businessProfile(); val now = businessTime.instant().toEpochMilli()
        workItems.filter { it.servicePlanId != null }.forEach { item ->
            require(CustomerType.fromCode(customer.customerType) == CustomerType.STANDARD) { "Recurring service requires a Standard customer" }
            val plan = dao.plan(item.servicePlanId!!) ?: error("Plan missing")
            val captured = item.capturedObligationId ?: error("Booked recurring work has no captured obligation")
            val obligation = dao.obligation(captured)
            require(
                plan.currentObligationId == captured &&
                    obligation?.planId == plan.id &&
                    obligation.consumedAtEpochMillis == null &&
                    dao.visitOwnsClaim(id, captured) == 1
            ) { "${item.equipmentReferenceSnapshot} · ${item.serviceNameSnapshot} is stale — rebook this work" }
        }
        workItems.forEach { item ->
            val plan=item.servicePlanId?.let{dao.plan(it) ?: error("Plan missing")}
            WorkSubjectValidator.validateWorkItem(item, CustomerType.fromCode(customer.customerType), plan?.equipmentId)
            val eq=item.equipmentId?.let { dao.equipment(it) ?: error("Equipment missing") }
            if (eq != null) require(eq.siteId==visit.siteId)
            val snapshot = if (plan == null) {
                item.templateSnapshotId
            } else {
                val existingSnapshot = item.templateSnapshotId?.let { snapshotId -> dao.templateSnapshot(snapshotId) }
                val currentRevision = plan.reusableTemplateId?.let { templateId -> dao.reusableTemplate(templateId)?.let { master -> dao.reusableTemplateRevision(master.currentRevisionId) } }
                val preserveSnapshot = existingSnapshot?.takeIf { snapshot ->
                    snapshot.sourceTemplateId == null || (snapshot.sourceTemplateId == plan.reusableTemplateId && snapshot.revision == currentRevision?.revisionNumber && snapshot.templateName == currentRevision.nameSnapshot)
                }?.id
                preserveSnapshot ?: captureTemplateSnapshot(plan.reusableTemplateId, id, plan.id, now, allowDisabled = true)
            }
            if (eq != null) {
                check(dao.refreshWorkItemSnapshot(item.id,snapshot,eq.name,eq.reference,eq.technicianIdentifier,eq.make,eq.model,eq.serialNumber,plan?.name ?: item.serviceNameSnapshot,plan?.reference,plan?.currentDueDate,item.intervalCountSnapshot?.let{plan?.intervalCount},item.intervalUnitSnapshot?.let{plan?.intervalUnit})==1)
            }
        }
        check(dao.startBookedVisit(id,businessTime.today().toString(),customer.name,customer.reference,site.name,site.reference,site.address,profile?.businessName,profile?.technicianName,profile?.phone,profile?.email,profile?.postalAddress,profile?.zoneId,now)==1){"Only a booked visit can be started"}; now
    } }

    override suspend fun rescheduleVisit(id: String, serviceDate: String, scheduledAtEpochMillis: Long?, reason: String): Long {
        LocalDate.parse(serviceDate); require(reason.trim().isNotEmpty()); writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli(); database.withTransaction { val visit=dao.visit(id)?:error("Visit no longer exists"); require(visit.state=="BOOKED"){"Only a booked visit can be rescheduled"}; dao.updateVisit(visit.copy(actualServiceDate = serviceDate, scheduledAtEpochMillis = scheduledAtEpochMillis, appointmentZoneId = scheduledAtEpochMillis?.let { businessTime.zoneId.id } ?: visit.appointmentZoneId, scheduleChangeReason = reason.trim(), modifiedAtEpochMillis = now)); dao.insertVisitScheduleEvent(VisitScheduleEventEntity(UUID.randomUUID().toString(),id,"RESCHEDULED",visit.actualServiceDate,serviceDate,visit.scheduledAtEpochMillis,scheduledAtEpochMillis,reason.trim(),now)) }; return now
    }

    override suspend fun cancelVisit(id: String, reason: String): Long {
        require(reason.trim().isNotEmpty()); writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli(); database.withTransaction { val visit=dao.visit(id)?:error("Visit no longer exists"); require(visit.state=="BOOKED"){"Only a booked visit can be cancelled"}; dao.updateVisit(visit.copy(state = VisitLifecycleState.CANCELED.code, cancellationReason = reason.trim(), cancellationOrigin = VisitCancellationOrigin.LOCAL.code, cancelledAtEpochMillis = now, modifiedAtEpochMillis = now)); dao.insertVisitScheduleEvent(VisitScheduleEventEntity(UUID.randomUUID().toString(),id,"CANCELED",visit.actualServiceDate,null,visit.scheduledAtEpochMillis,null,reason.trim(),now)); dao.releaseVisitClaims(id) }; return now
    }

    override suspend fun restoreVisit(id: String, serviceDate: String, scheduledAtEpochMillis: Long?): Long {
        LocalDate.parse(serviceDate); require(!LocalDate.parse(serviceDate).isBefore(businessTime.today())) { "Choose today or a future appointment date" }
        writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli()
        database.withTransaction {
            val visit = dao.visit(id) ?: error("Visit no longer exists")
            require(visit.state == VisitLifecycleState.CANCELED.code) { "Only a canceled visit can be restored" }
            require(visit.cancellationOrigin !in setOf(VisitCancellationOrigin.COORDINATOR.code, VisitCancellationOrigin.ASSIGNMENT_REMOVAL.code)) { "Coordinator-canceled visits cannot be restored locally" }
            val recurring = dao.visitWorkItems(id).filter { it.servicePlanId != null }
            recurring.forEach { item ->
                val plan = dao.plan(item.servicePlanId!!) ?: error("The old booking can no longer be restored because its service obligation changed or is already claimed")
                val obligationId = item.capturedObligationId ?: error("The old booking can no longer be restored because its service obligation changed or is already claimed")
                val obligation = dao.obligation(obligationId)
                require(plan.state == "ACTIVE" && plan.currentObligationId == obligationId && obligation?.planId == plan.id && obligation.consumedAtEpochMillis == null && dao.claimForObligation(obligationId) == null) { "The old booking can no longer be restored because its service obligation changed or is already claimed" }
            }
            recurring.forEach { dao.insertVisitClaim(VisitClaimEntity(it.capturedObligationId!!, id, now)) }
            dao.updateVisit(visit.copy(state = "BOOKED", actualServiceDate = serviceDate, scheduledAtEpochMillis = scheduledAtEpochMillis, appointmentZoneId = scheduledAtEpochMillis?.let { businessTime.zoneId.id } ?: visit.appointmentZoneId, cancellationOrigin = null, cancellationReason = visit.cancellationReason, modifiedAtEpochMillis = now))
            dao.insertVisitScheduleEvent(VisitScheduleEventEntity(UUID.randomUUID().toString(), id, "RESTORED", visit.actualServiceDate, serviceDate, visit.scheduledAtEpochMillis, scheduledAtEpochMillis, "Restored booking", now))
        }
        return now
    }

    suspend fun restoreVisit(id: String, serviceDate: String): Long = restoreVisit(id, serviceDate, null)

    override suspend fun addPart(workItemId: String, description: String, quantity: String, unit: String): String {
        require(description.trim().isNotEmpty() && description.length <= 200); require(unit.trim().isNotEmpty() && unit.length <= 30); val numeric = runCatching { BigDecimal(quantity.trim()) }.getOrNull(); require(numeric != null && numeric > BigDecimal.ZERO) { "Quantity must be a finite positive number" }
        writeGate.beforeWrite(); val id = UUID.randomUUID().toString(); val now = businessTime.instant().toEpochMilli(); database.withTransaction { val item=workingItem(workItemId); dao.insertPart(PartEntryEntity(id, workItemId, description.trim(), numeric.stripTrailingZeros().toPlainString(), unit.trim(), now)); dao.touchVisit(item.visitId, now) }; return id
    }

    override suspend fun updatePart(workItemId: String, partId: String, description: String, quantity: String, unit: String): Long {
        require(description.trim().isNotEmpty() && description.length <= 200)
        require(unit.trim().isNotEmpty() && unit.length <= 30)
        val numeric = runCatching { BigDecimal(quantity.trim()) }.getOrNull()
        require(numeric != null && numeric > BigDecimal.ZERO) { "Quantity must be a finite positive number" }
        writeGate.beforeWrite()
        val now = businessTime.instant().toEpochMilli()
        database.withTransaction {
            val item = workingItem(workItemId)
            check(dao.part(partId)?.workItemId == workItemId) { "Part no longer belongs to this Service" }
            check(dao.updatePart(partId, workItemId, description.trim(), numeric.stripTrailingZeros().toPlainString(), unit.trim(), now) == 1)
            dao.touchVisit(item.visitId, now)
        }
        return now
    }

    override suspend fun removePart(workItemId: String, partId: String): Long {
        writeGate.beforeWrite()
        val now = businessTime.instant().toEpochMilli()
        database.withTransaction {
            val item = workingItem(workItemId)
            check(dao.deletePart(partId, workItemId) == 1) { "Part no longer belongs to this Service" }
            dao.touchVisit(item.visitId, now)
        }
        return now
    }

    override suspend fun updatePhoto(workItemId: String, photoId: String, caption: String?, includeInReport: Boolean): Long = BusinessFileCoordinator.mutex.withLock {
        require(caption == null || caption.length <= 500) { "Caption is too long" }
        writeGate.beforeWrite()
        val now = businessTime.instant().toEpochMilli()
        database.withTransaction {
            val item = workingItem(workItemId)
            check(dao.updateWorkPhoto(photoId, workItemId, clean(caption), includeInReport) == 1) { "Photo no longer belongs to this Service" }
            dao.touchVisit(item.visitId, now)
        }
        now
    }

    override suspend fun savePhotoCaption(workItemId: String, photoId: String, caption: String): Long = BusinessFileCoordinator.mutex.withLock {
        require(caption.length <= 500) { "Caption is too long" }
        writeGate.beforeWrite()
        val now = businessTime.instant().toEpochMilli()
        database.withTransaction {
            val item = workingItem(workItemId)
            check(dao.updateWorkPhotoCaption(photoId, workItemId, clean(caption)) == 1) { "Photo no longer belongs to this Service" }
            dao.touchVisit(item.visitId, now)
        }
        now
    }

    override suspend fun setPhotoReportInclusion(workItemId: String, photoId: String, includeInReport: Boolean): Long = BusinessFileCoordinator.mutex.withLock {
        writeGate.beforeWrite()
        val now = businessTime.instant().toEpochMilli()
        database.withTransaction {
            val item = workingItem(workItemId)
            check(dao.updateWorkPhotoInclusion(photoId, workItemId, includeInReport) == 1) { "Photo no longer belongs to this Service" }
            dao.touchVisit(item.visitId, now)
        }
        now
    }

    override suspend fun removePhoto(workItemId: String, photoId: String): Long = BusinessFileCoordinator.mutex.withLock {
        val root = attachmentRoot ?: error("Attachment storage unavailable")
        val photo = dao.attachment(photoId)?.takeIf { it.ownerType == "WORK_ITEM" && it.ownerId == workItemId }
            ?: error("Photo no longer belongs to this Service")
        val file = File(root, photo.storedRelativePath)
        val bytes = file.takeIf { it.isFile }?.readBytes() ?: error("Saved photo file is missing")
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        require(bytes.size.toLong() == photo.byteSize && hash == photo.sha256) { "Saved photo failed integrity check" }
        writeGate.beforeWrite()
        val now = businessTime.instant().toEpochMilli()
        try {
            check(file.delete()) { "Stored photo could not be deleted" }
            database.withTransaction {
                val item = workingItem(workItemId)
                check(dao.deleteAttachment(photoId) == 1)
                dao.touchVisit(item.visitId, now)
            }
            file.parentFile?.takeIf { it.listFiles().isNullOrEmpty() }?.delete()
            now
        } catch (failure: Throwable) {
            if (!file.isFile) {
                file.parentFile?.mkdirs()
                runCatching { file.writeBytes(bytes) }.onFailure { failure.addSuppressed(it) }
            }
            throw failure
        }
    }

    override suspend fun savePhoto(workItemId: String, bytes: ByteArray, displayName: String?, mimeType: String, includeInReport: Boolean, caption: String?): String = BusinessFileCoordinator.mutex.withLock { savePhotoUnlocked(workItemId, bytes, displayName, mimeType, includeInReport, caption) }

    private suspend fun savePhotoUnlocked(workItemId: String, bytes: ByteArray, displayName: String?, mimeType: String, includeInReport: Boolean, caption: String?): String {
        require(mimeType.startsWith("image/")); val root = attachmentRoot ?: error("Attachment storage unavailable")
        val normalized = AppOwnedImageNormalizer.normalize(bytes)
        writeGate.beforeWrite(); val id = UUID.randomUUID().toString(); val relative = "attachments/$id/original"; val target = File(root, relative); val temp = File(target.parentFile, "incoming.tmp"); target.parentFile?.mkdirs(); try { temp.writeBytes(normalized.bytes); require(temp.length() == normalized.bytes.size.toLong()); if (!temp.renameTo(target)) { temp.copyTo(target, overwrite = false); temp.delete() }; val hash = MessageDigest.getInstance("SHA-256").digest(normalized.bytes).joinToString("") { "%02x".format(it) }; database.withTransaction { val item=workingItem(workItemId); require(item.equipmentId?.let { dao.machinePhotoCount(item.visitId,it) } ?: 0 < 20); require(dao.visitPhotoCount(item.visitId)<100); dao.insertAttachments(listOf(AttachmentEntity(id,"WORK_ITEM",workItemId,relative,hash,displayName,normalized.mimeType,includeInReport,"PRESENT",normalized.bytes.size.toLong(),clean(caption)))); dao.touchVisit(item.visitId,businessTime.instant().toEpochMilli()) }; return id } catch (failure: Throwable) { temp.delete(); target.delete(); throw failure }
    }

    override suspend fun createContactNote(input: ContactNoteInput): String {
        require(input.outcome.trim().isNotEmpty() && input.outcome.length <= 2000); require(input.channel in setOf("CALL", "SMS", "EMAIL", "IN_PERSON", "OTHER")); dao.customer(input.customerId) ?: error("Customer no longer exists"); writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli(); val id = UUID.randomUUID().toString(); dao.insertContactNote(ContactNoteEntity(id, reference("CN", dao.contactNoteCount() + 1), input.customerId, input.siteId, input.equipmentId, input.channel, now, input.outcome.trim(), clean(input.privateNote), now)); return id
    }

    override suspend fun contactNote(id: String): ContactNoteDetail? = dao.contactNote(id)?.let { ContactNoteDetail(it.id, it.reference, it.channel, it.occurredAtEpochMillis, it.outcome, it.privateNote.orEmpty(), it.enteredInError, it.errorReason) }

    override suspend fun markContactNoteEnteredInError(id: String, reason: String): Long { require(reason.trim().isNotEmpty()); writeGate.beforeWrite(); val now=businessTime.instant().toEpochMilli(); check(dao.markContactNoteEnteredInError(id,reason.trim(),now)==1){"Contact note is unavailable or already entered in error"}; return now }

    override suspend fun createFollowUp(input: FollowUpInput): String {
        require(input.type in setOf("CONTACT", "CORRECTIVE")); require(input.title.trim().isNotEmpty() && input.title.length <= 200); LocalDate.parse(input.dueDate); dao.customer(input.customerId) ?: error("Customer no longer exists"); writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli(); val id = UUID.randomUUID().toString(); dao.insertFollowUp(FollowUpEntity(id, reference("FU", dao.followUpCount() + 1), input.type, input.title.trim(), input.dueDate, "OPEN", input.customerId, input.siteId, input.equipmentId, clean(input.privatePlanningNote), input.sourceVisitId, input.sourceWorkItemId, now)); return id
    }

    override suspend fun updateFollowUp(id: String, title: String, dueDate: String, privateNote: String, reason: String): Long {
        require(title.trim().isNotEmpty()&&title.length<=200); LocalDate.parse(dueDate); writeGate.beforeWrite(); val now=businessTime.instant().toEpochMilli(); database.withTransaction { val old=dao.followUp(id)?:error("Follow-up no longer exists"); require(old.state=="OPEN"){"Only an open follow-up can be edited"}; if(dueDate!=old.dueDate) require(reason.trim().isNotEmpty()){ "Explain why the follow-up date changed" }; dao.updateFollowUp(old.copy(title=title.trim(),dueDate=dueDate,privatePlanningNote=clean(privateNote),updatedAtEpochMillis=now)); if(dueDate!=old.dueDate) dao.insertFollowUpEvent(FollowUpEventEntity(UUID.randomUUID().toString(),id,"RESCHEDULED",now,reason.trim(),dueDate)) }; return now
    }

    override suspend fun createCorrectiveFollowUp(workItemId: String, title: String, dueDate: String, privateNote: String): String {
        val item = dao.workItem(workItemId) ?: error("Work item no longer exists"); val visit = dao.visit(item.visitId) ?: error("Visit no longer exists")
        return createFollowUp(FollowUpInput("CORRECTIVE", title, dueDate, visit.customerId, visit.siteId, item.equipmentId, privateNote, visit.id, item.id))
    }
    override suspend fun correctiveFollowUps(workItemId: String): List<FollowUpDetail> = dao.followUpsForWorkItem(workItemId).map { followUpDetail(it) }

    override suspend fun changeFollowUpState(id: String, state: String, reason: String, newDueDate: String?): Long {
        require(state in setOf("RESOLVED", "CANCELLED", "OPEN")); require(reason.trim().isNotEmpty()); newDueDate?.let(LocalDate::parse)
        writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli(); database.withTransaction { val old=dao.followUp(id)?:error("Follow-up no longer exists"); if(state=="OPEN") require(old.state!="OPEN"&&newDueDate!=null){"Only a closed follow-up can be reopened"} else require(old.state=="OPEN"){"Only an open follow-up can be closed"}; dao.updateFollowUp(old.copy(state = state, dueDate = newDueDate ?: old.dueDate, updatedAtEpochMillis = now, closedAtEpochMillis = if (state == "OPEN") null else now, closureReason = if (state == "OPEN") null else reason.trim())); dao.insertFollowUpEvent(FollowUpEventEntity(UUID.randomUUID().toString(), id, state, now, reason.trim(), newDueDate)) }; return now
    }

    override suspend fun equipment(id: String): EquipmentDetail? {
        val equipment=dao.equipment(id)?:return null; val site=dao.site(equipment.siteId)?:return null; val customer=dao.customer(site.customerId)?:return null
        return EquipmentDetail(equipment.id,equipment.name,equipment.reference,equipment.technicianIdentifier,listOfNotNull(equipment.make,equipment.model).joinToString(" "),equipment.serialNumber,site.name,customer.name,
            dao.plansForEquipment(id).map { EquipmentPlan(it.id,it.name,it.reference,"Every ${it.intervalCount} ${it.intervalUnit.lowercase()}",it.currentDueDate,it.state,it.currentObligationId,LocalDate.parse(it.currentDueDate).isBefore(businessTime.today())) },dao.workingItemId(id),equipment.make.orEmpty(),equipment.model.orEmpty(),equipment.privateNotes.orEmpty(),equipment.state,customer.id,CustomerType.fromCode(customer.customerType),site.id)
    }

    override suspend fun inspection(workItemId: String): InspectionDraft? {
        val row = dao.inspection(workItemId) ?: return null
        val items = row.templateSnapshotId?.let { dao.checklistItems(it) }.orEmpty()
        val responseRows = dao.responses(workItemId)
        val completeness = checklistCompleteness(items, responseRows)
        val rawInputs = dao.workingInputBuffers(workItemId).associate { it.fieldKey to it.rawValue }
        return inspectionDraft(
            row = row,
            items = items,
            responseRows = responseRows,
            completeness = completeness,
            rawInputs = rawInputs,
            templateRevision = row.templateSnapshotId?.let { dao.templateSnapshot(it)?.revision },
        )
    }

    private fun inspectionDraft(
        row: InspectionRow,
        items: List<ChecklistItemSnapshotEntity>,
        responseRows: List<WorkingResponseEntity>,
        completeness: ChecklistCompleteness,
        rawInputs: Map<String, String>,
        templateRevision: Int?,
    ): InspectionDraft {
        val responses = responseRows.associateBy { it.checklistItemSnapshotId }
        return InspectionDraft(
            workItemId = row.workItemId,
            visitId = row.visitId,
            visitReference = row.visitReference,
            siteName = row.siteNameSnapshot,
            equipmentName = row.equipmentNameSnapshot,
            equipmentReference = row.equipmentReferenceSnapshot,
            serviceName = row.serviceNameSnapshot,
            dueDate = row.dueDateSnapshot,
            interval = row.intervalCountSnapshot?.let { "Every $it ${row.intervalUnitSnapshot?.lowercase()}" },
            templateRevision = templateRevision,
            workPerformed = row.workPerformed,
            privateInternalNote = row.privateInternalNote,
            checklistReviewed = row.checklistReviewed,
            outcome = row.outcome,
            fulfillsCurrentObligation = row.fulfillsCurrentObligation,
            modifiedAtEpochMillis = row.modifiedAtEpochMillis,
            questions = items.map { item ->
                val response = responses[item.id]
                InspectionQuestion(
                    responseId = response?.id ?: stableId("response", row.workItemId, item.id),
                    snapshotItemId = item.id,
                    position = item.position,
                    label = item.label,
                    responseType = item.responseType,
                    unit = item.unit,
                    required = item.required,
                    disposition = response?.disposition?.let(ResponseDisposition::valueOf) ?: if (item.responseType == "STATUS") ResponseDisposition.NOT_CHECKED else ResponseDisposition.UNANSWERED,
                    textValue = response?.textValue,
                    numberValue = response?.numberValue,
                    reason = response?.reason,
                    issueFoundReasonDraft = response?.issueFoundReasonDraft,
                    notApplicableReasonDraft = response?.notApplicableReasonDraft,
                    privateGuidance = item.privateGuidance,
                )
            },
            checklistComplete = completeness.complete,
            requiredComplete = completeness.requiredComplete,
            requiredTotal = completeness.requiredTotal,
            issueMissingDescription = completeness.issueMissingDescription,
            invalidExplicitAnswers = completeness.invalidExplicitAnswers,
            rawInputs = rawInputs,
            subjectType = WorkSubjectType.fromCode(row.subjectType),
            equipmentId = row.equipmentId,
            equipmentDescription = row.equipmentDescriptionSnapshot,
            customerName = row.customerName.orEmpty(),
            siteAccessNote = row.siteAccessNote.orEmpty(),
            equipmentPrivateNote = if (row.equipmentId != null) row.equipmentPrivateNote.orEmpty() else "",
            dispatchInstructions = row.dispatchInstructions,
            dispatchLocalRole = row.dispatchLocalRole,
            dispatchDocumentationDisposition = row.dispatchDocumentationDisposition,
        )
    }

    private data class LoadedServiceWork(
        val item: WorkItemEntity,
        val checklistItems: List<ChecklistItemSnapshotEntity>,
        val responses: List<WorkingResponseEntity>,
        val checklist: ChecklistCompleteness?,
        val rawInputs: List<WorkingInputBufferEntity>,
        val publicWork: String,
        val privateNote: String,
        val plan: ServicePlanEntity?,
        val obligation: ServiceObligationEntity?,
        val dispatchBinding: DispatchItemBindingEntity?,
        val partCount: Int,
        val attachmentCount: Int,
    )

    private data class LoadedServiceVisit(
        val visit: WorkingVisitEntity,
        val items: List<LoadedServiceWork>,
        val dispatchBinding: DispatchVisitBindingEntity?,
        val dispatchItems: List<DispatchItemBindingEntity>,
    )

    override suspend fun serviceWorkspace(workItemId: String): ServiceWorkspace? = database.withTransaction {
        val row = dao.inspection(workItemId) ?: return@withTransaction null
        val loaded = loadServiceVisit(row.visitId) ?: return@withTransaction null
        val target = loaded.items.firstOrNull { it.item.id == workItemId } ?: return@withTransaction null
        val lines = completionLines(loaded)
        ServiceWorkspace(
            inspection = inspectionDraft(
                row = row,
                items = target.checklistItems,
                responseRows = target.responses,
                completeness = target.checklist ?: ChecklistCompleteness(true, 0, 0, emptyList(), emptyList()),
                rawInputs = target.rawInputs.associate { it.fieldKey to it.rawValue },
                templateRevision = row.templateSnapshotId?.let { dao.templateSnapshot(it)?.revision },
            ),
            serviceProgress = serviceProgress(loaded, lines),
            completionLines = lines,
        )
    }

    override suspend fun serviceVisitProgress(visitId: String): VisitServiceProgress = database.withTransaction {
        val loaded = loadServiceVisit(visitId) ?: error("Visit no longer exists")
        val lines = completionLines(loaded)
        serviceProgress(loaded, lines)
    }

    override suspend fun completionLines(visitId: String): List<CompletionLine> = database.withTransaction {
        val loaded = loadServiceVisit(visitId) ?: return@withTransaction emptyList()
        completionLines(loaded)
    }

    private suspend fun loadServiceVisit(visitId: String): LoadedServiceVisit? {
        val visit = dao.visit(visitId) ?: return null
        val dispatchBinding = dispatchDao.visitBindingForLocalVisit(visitId)
        val dispatchItems = dispatchBinding?.let { dispatchDao.itemBindings(it.dispatchVisitId) }.orEmpty()
        val bindingByWorkItem = dispatchItems.filter { it.localWorkItemId != null }.associateBy { it.localWorkItemId }
        val items = dao.visitWorkItems(visitId).map { item ->
            val checklistItems = item.templateSnapshotId?.let { dao.checklistItems(it) }.orEmpty()
            val responses = dao.responses(item.id)
            LoadedServiceWork(
                item = item,
                checklistItems = checklistItems,
                responses = responses,
                checklist = item.templateSnapshotId?.let { checklistCompleteness(checklistItems, responses) },
                rawInputs = dao.workingInputBuffers(item.id),
                publicWork = dao.publicDraft(item.id)?.workPerformed.orEmpty(),
                privateNote = dao.privateDraft(item.id)?.internalNote.orEmpty(),
                plan = item.servicePlanId?.let { dao.plan(it) },
                obligation = item.capturedObligationId?.let { dao.obligation(it) },
                dispatchBinding = bindingByWorkItem[item.id],
                partCount = dispatchDao.partCount(item.id),
                attachmentCount = dispatchDao.attachmentCount(item.id),
            )
        }
        return LoadedServiceVisit(visit, items, dispatchBinding, dispatchItems)
    }

    private fun completionLines(loaded: LoadedServiceVisit): List<CompletionLine> {
        val documentIds = loaded.dispatchBinding?.let { loaded.dispatchItems.filter { it.documentationDisposition == "DOCUMENT_LOCAL" }.mapNotNull { it.localWorkItemId }.toSet() }
        return loaded.items.filter { documentIds == null || it.item.id in documentIds }.map { work ->
            val item = work.item
            val evaluation = ServiceWorkEvaluator.evaluate(work.evaluationInput(loaded.visit))
            CompletionLine(
                workItemId = item.id,
                equipmentName = item.equipmentNameSnapshot,
                equipmentReference = item.equipmentReferenceSnapshot,
                serviceName = item.serviceNameSnapshot,
                outcome = item.outcome,
                fulfillmentEligibility = evaluation.fulfillmentEligibility,
                fulfillsCurrentObligation = evaluation.projectedFulfillsCurrentObligation,
                dueDate = item.dueDateSnapshot,
                proposedNextDueDate = evaluation.calculatedNextDueDate,
                workPerformed = work.publicWork,
                checklistReviewed = item.checklistReviewed,
                notPerformedReason = item.notPerformedReason,
                calculatedNextDueDate = evaluation.calculatedNextDueDate,
                confirmedNextDueDate = item.confirmedNextDueDate.takeIf { evaluation.projectedFulfillsCurrentObligation == true },
                nextDueDateCalculated = item.nextDueDateCalculated.takeIf { evaluation.projectedFulfillsCurrentObligation == true },
                nextDueOverrideReason = item.nextDueOverrideReason.takeIf { evaluation.projectedFulfillsCurrentObligation == true },
                blockers = evaluation.completionBlockers,
                checklistComplete = work.checklist?.complete ?: true,
                subjectType = WorkSubjectType.fromCode(item.subjectType),
                equipmentId = item.equipmentId,
                equipmentDescription = item.equipmentDescriptionSnapshot,
                currentObligationOutstanding = evaluation.currentObligationOutstanding,
                capturedObligationId = item.capturedObligationId,
                checklistResults = work.reviewChecklistResults(),
            )
        }
    }

    private fun LoadedServiceWork.reviewChecklistResults(): List<ReviewChecklistResult> {
        val answers = responses.associateBy { it.checklistItemSnapshotId }
        return checklistItems.mapNotNull { question ->
            val answer = answers[question.id]
            val disposition = answer?.disposition ?: "UNANSWERED"
            val reason = answer?.reason?.trim()?.takeIf(String::isNotEmpty)
            val result = when (question.responseType) {
                "STATUS" -> when (disposition) {
                    "OK" -> "OK"
                    "NOT_CHECKED" -> "Not checked"
                    "UNANSWERED" -> if (question.required) "Not answered" else null
                    "ISSUE_FOUND" -> reason?.let { "Issue found: $it" } ?: "Issue found"
                    "NOT_APPLICABLE" -> reason?.let { "Not applicable: $it" } ?: "Not applicable"
                    else -> null
                }
                "TEXT" -> when (disposition) {
                    "VALUE" -> answer?.textValue?.trim()?.takeIf(String::isNotEmpty)
                    "NOT_APPLICABLE" -> reason?.let { "Not applicable: $it" } ?: "Not applicable"
                    "UNANSWERED" -> if (question.required) "Not answered" else null
                    else -> null
                }
                "NUMBER" -> when (disposition) {
                    "VALUE" -> answer?.numberValue?.trim()?.takeIf(String::isNotEmpty)?.let { value ->
                        value + question.unit?.trim()?.takeIf(String::isNotEmpty)?.let { " $it" }.orEmpty()
                    }
                    "NOT_APPLICABLE" -> reason?.let { "Not applicable: $it" } ?: "Not applicable"
                    "UNANSWERED" -> if (question.required) "Not answered" else null
                    else -> null
                }
                else -> null
            }
            result?.let { ReviewChecklistResult(question.label, it) }
        }
    }

    private fun serviceProgress(loaded: LoadedServiceVisit, completionLines: List<CompletionLine>): VisitServiceProgress {
        val completionByItem = completionLines.associateBy { it.workItemId }
        val progressItems = loaded.items.mapIndexed { index, work ->
            val item = work.item
            val hasActivity = work.publicWork.isNotBlank() || work.privateNote.isNotBlank() || work.responses.isNotEmpty() ||
                work.rawInputs.isNotEmpty() || work.partCount > 0 || work.attachmentCount > 0 ||
                item.outcome != null || item.notPerformedReason.isNullOrBlank().not() ||
                item.confirmedNextDueDate != null || item.nextDueDateCalculated != null || item.nextDueOverrideReason != null
            val completion = completionByItem[item.id]
            val status = serviceEntryStatus(
                hasActivity = hasActivity,
                hasUnresolvedRawBuffer = work.rawInputs.isNotEmpty(),
                hasMissingIssueDescription = work.checklist?.issueMissingDescription.orEmpty().isNotEmpty(),
                hasInvalidExplicitAnswer = work.checklist?.invalidExplicitAnswers.orEmpty().isNotEmpty(),
                completionReady = completion?.blockers?.isEmpty() == true,
            )
            val binding = work.dispatchBinding
            ServiceProgressItem(
                workItemId = item.id,
                position = index + 1,
                subjectType = WorkSubjectType.fromCode(item.subjectType),
                equipmentId = item.equipmentId,
                equipmentName = item.equipmentNameSnapshot,
                equipmentReference = item.equipmentReferenceSnapshot,
                equipmentDescription = item.equipmentDescriptionSnapshot,
                serviceName = item.serviceNameSnapshot,
                status = status,
                documentationMode = serviceDocumentationMode(binding?.localRole, binding?.documentationDisposition),
                dispatchLocalRole = binding?.localRole,
                dispatchDocumentationDisposition = binding?.documentationDisposition,
            )
        }
        return VisitServiceProgress(
            visitId = loaded.visit.id,
            visitReference = loaded.visit.reference,
            customerName = loaded.visit.customerNameSnapshot,
            siteName = loaded.visit.siteNameSnapshot,
            serviceDate = loaded.visit.actualServiceDate,
            items = progressItems,
            groups = serviceProgressGroups(progressItems),
        )
    }

    private fun LoadedServiceWork.evaluationInput(visit: WorkingVisitEntity) = serviceEvaluationInput(
        item = item,
        visit = visit,
        plan = plan,
        obligation = obligation,
        publicWork = publicWork,
        checklist = checklist,
        checklistQuestionLabels = checklistItems.associate { it.id to it.label },
    )

    private fun serviceEvaluationInput(
        item: WorkItemEntity,
        visit: WorkingVisitEntity,
        plan: ServicePlanEntity?,
        obligation: ServiceObligationEntity?,
        publicWork: String = "",
        checklist: ChecklistCompleteness? = null,
        checklistQuestionLabels: Map<String, String> = emptyMap(),
    ) = ServiceWorkEvaluationInput(
        servicePlanId = item.servicePlanId,
        capturedObligationId = item.capturedObligationId,
        outcome = item.outcome,
        fulfillsCurrentObligation = item.fulfillsCurrentObligation,
        dueDateSnapshot = item.dueDateSnapshot,
        intervalCountSnapshot = item.intervalCountSnapshot,
        intervalUnitSnapshot = item.intervalUnitSnapshot,
        actualServiceDate = LocalDate.parse(visit.actualServiceDate),
        publicWork = publicWork,
        notPerformedReason = item.notPerformedReason,
        confirmedNextDueDate = item.confirmedNextDueDate,
        nextDueDateCalculated = item.nextDueDateCalculated,
        nextDueOverrideReason = item.nextDueOverrideReason,
        plan = plan?.let { ServicePlanFacts(it.id, it.state, it.currentObligationId) },
        obligation = obligation?.let { ServiceObligationFacts(it.id, it.planId, it.dueDate, it.consumedAtEpochMillis) },
        checklist = checklist,
        checklistQuestionLabels = checklistQuestionLabels,
    )

    override suspend fun businessProfile(): BusinessProfile? = dao.businessProfile()?.let { BusinessProfile(it.businessName, it.technicianName, it.phone.orEmpty(), it.email.orEmpty(), it.postalAddress.orEmpty(), it.zoneId, it.modifiedAtEpochMillis) }

    override suspend fun visitReportIdentity(visitId: String): BusinessProfile? = dao.visit(visitId)?.let { visit ->
        val business = visit.reportBusinessNameSnapshot ?: return@let null
        val technician = visit.reportTechnicianNameSnapshot ?: return@let null
        val zone = visit.reportZoneIdSnapshot ?: return@let null
        BusinessProfile(business, technician, visit.reportPhoneSnapshot.orEmpty(), visit.reportEmailSnapshot.orEmpty(), visit.reportPostalAddressSnapshot.orEmpty(), zone)
    }

    override suspend fun refreshVisitReportIdentity(visitId: String): Long {
        val profile = dao.businessProfile() ?: error("Set Business and report identity first")
        require(profile.businessName.isNotBlank() && profile.technicianName.isNotBlank()) { "Set Business and report identity first" }
        val visit = dao.visit(visitId) ?: error("Visit no longer exists")
        val normalized = listOf(profile.businessName, profile.technicianName, profile.phone, profile.email, profile.postalAddress, profile.zoneId)
        val current = listOf(visit.reportBusinessNameSnapshot, visit.reportTechnicianNameSnapshot, visit.reportPhoneSnapshot, visit.reportEmailSnapshot, visit.reportPostalAddressSnapshot, visit.reportZoneIdSnapshot)
        if (normalized == current) return visit.modifiedAtEpochMillis
        writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli()
        check(dao.updateVisitReportIdentity(visitId, profile.businessName, profile.technicianName, profile.phone, profile.email, profile.postalAddress, profile.zoneId, now) == 1)
        return now
    }

    override suspend fun saveBusinessProfile(profile: BusinessProfile): Long {
        val businessName = profile.businessName.trim(); val technicianName = profile.technicianName.trim(); val phone = profile.phone.trim().ifBlank { null }; val email = profile.email.trim().ifBlank { null }; val address = profile.postalAddress.trim().ifBlank { null }; val zoneId = profile.zoneId.trim()
        require(businessName.isNotBlank() && technicianName.isNotBlank()) { "Business and technician names are required" }; require(zoneId.isNotBlank()) { "Business time zone is required" }; val parsedZone = ZoneId.of(zoneId)
        dao.businessProfile()?.let { existing -> if (existing.businessName == businessName && existing.technicianName == technicianName && existing.phone == phone && existing.email == email && existing.postalAddress == address && existing.zoneId == zoneId) return existing.modifiedAtEpochMillis }
        writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli()
        database.withTransaction {
            dao.upsertBusinessProfile(BusinessProfileEntity(businessName = businessName, technicianName = technicianName, phone = phone, email = email, postalAddress = address, zoneId = zoneId, modifiedAtEpochMillis = now))
            dispatchDao.technicianIdentity()?.let { identity ->
                val synced = identity.copy(displayName = technicianName, modifiedAtEpochMillis = now)
                dispatchDao.updateTechnicianIdentity(synced)
                dispatchDao.technician(identity.technicianId)?.let { directory ->
                    dispatchDao.updateTechnician(directory.copy(displayName = technicianName, modifiedAtEpochMillis = now))
                }
            }
        }
        (businessTime as? MutableBusinessTime)?.updateZone(parsedZone)
        businessDateSignal?.invalidate()
        return now
    }

    override suspend fun reminderPreferences(): ReminderPreferences = (dao.reminderPreferences() ?: ReminderPreferencesEntity()).toDomain()

    override suspend fun saveReminderPreferences(value: ReminderPreferences): Long {
        value.validate()
        val current = reminderPreferences()
        if (current == value) return businessTime.instant().toEpochMilli()
        writeGate.beforeWrite()
        dao.upsertReminderPreferences(value.toEntity())
        return businessTime.instant().toEpochMilli()
    }

    override suspend fun setAppointmentReminderLead(visitId: String, minutes: Int?): Long {
        require(minutes == null || minutes == 0 || minutes in ReminderPreferences.APPOINTMENT_LEADS) { "Choose Default, Off, or a supported appointment lead" }
        writeGate.beforeWrite()
        check(dao.updateAppointmentReminderLead(visitId, minutes) == 1) { "Only a booked Visit can change its appointment reminder" }
        return businessTime.instant().toEpochMilli()
    }

    override suspend fun checklistCompleteness(workItemId: String): ChecklistCompleteness {
        val item = dao.workItem(workItemId) ?: error("Working item no longer exists")
        val snapshotId = item.templateSnapshotId ?: return ChecklistCompleteness(true, 0, 0, emptyList(), emptyList())
        return checklistCompleteness(dao.checklistItems(snapshotId), dao.responses(workItemId))
    }

    override suspend fun serviceDraftWorkItemIds(visitId: String): List<String> = dao.visitWorkItems(visitId).map { it.id }

    override suspend fun workingInputBuffers(workItemId: String): Map<String, String> =
        dao.workingInputBuffers(workItemId).associate { it.fieldKey to it.rawValue }

    override suspend fun saveWorkingInputBuffer(workItemId: String, fieldKey: String, rawValue: String): Long {
        require(ServiceDraftFieldKeys.isSupported(fieldKey)) { "Unsupported service draft field" }
        require(rawValue.length <= 10_000) { "Service draft input is too long" }
        val item = workingItem(workItemId)
        writeGate.beforeWrite()
        val now = businessTime.instant().toEpochMilli()
        database.withTransaction {
            workingItem(workItemId)
            dao.upsertWorkingInputBuffer(WorkingInputBufferEntity(workItemId, fieldKey, rawValue, now))
            dao.touchVisit(item.visitId, now)
        }
        return now
    }

    override suspend fun clearWorkingInputBuffer(workItemId: String, fieldKey: String): Long {
        require(ServiceDraftFieldKeys.isSupported(fieldKey)) { "Unsupported service draft field" }
        val item = dao.workItem(workItemId) ?: error("Work item no longer exists")
        val existing = dao.workingInputBuffer(workItemId, fieldKey) ?: return dao.visit(item.visitId)?.modifiedAtEpochMillis ?: error("Visit no longer exists")
        writeGate.beforeWrite()
        val now = businessTime.instant().toEpochMilli()
        database.withTransaction {
            if (dao.deleteWorkingInputBuffer(workItemId, fieldKey) == 1) dao.touchVisit(item.visitId, now)
        }
        return now
    }

    override suspend fun savePublicWork(workItemId: String, text: String): Long {
        val row = dao.inspection(workItemId) ?: error("Working item no longer exists"); val normalizedText = text.trim()
        if(row.workPerformed==normalizedText) return database.withTransaction { workingItem(workItemId); dao.inspection(workItemId)?.modifiedAtEpochMillis ?: error("Working item no longer exists") }
        writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli(); database.withTransaction { val item=workingItem(workItemId); check(dao.updatePublicWork(workItemId, normalizedText) == 1); dao.touchVisit(item.visitId, now) }; return now
    }

    override suspend fun savePrivateNote(workItemId: String, text: String): Long {
        val row = dao.privateDraft(workItemId) ?: error("Working item no longer exists")
        val normalizedText = text.trim()
        require(normalizedText.length <= 5_000) { "Private note is too long" }
        if (row.internalNote == normalizedText) return database.withTransaction { workingItem(workItemId); dao.inspection(workItemId)?.modifiedAtEpochMillis ?: error("Working item no longer exists") }
        writeGate.beforeWrite()
        val now = businessTime.instant().toEpochMilli()
        database.withTransaction {
            val item = workingItem(workItemId)
            dispatchDao.updatePrivateDraftNote(workItemId, normalizedText)
            dao.touchVisit(item.visitId, now)
        }
        return now
    }

    @Deprecated("Checklist completeness is derived from the current snapshot and responses")
    override suspend fun markChecklistReviewed(workItemId: String): Long {
        val completeness = checklistCompleteness(workItemId)
        require(completeness.complete) { "Complete all required checklist questions" }
        writeGate.beforeWrite()
        val now = businessTime.instant().toEpochMilli()
        database.withTransaction {
            val item = workingItem(workItemId)
            check(dao.updateChecklistReviewed(workItemId, true) == 1)
            dao.touchVisit(item.visitId, now)
        }
        return now
    }

    override suspend fun saveCompletionDraft(workItemId: String, outcome: String?, fulfills: Boolean?, reason: String?, nextDue: String?, calculated: Boolean?, overrideReason: String?): Long {
        require(outcome == null || outcome in setOf("PERFORMED", "PARTLY_PERFORMED", "NOT_PERFORMED"))
        val item = dao.workItem(workItemId) ?: error("Working item no longer exists")
        val visit = dao.visit(item.visitId) ?: error("Visit no longer exists")
        val plan = item.servicePlanId?.let { dao.plan(it) }
        val obligation = item.capturedObligationId?.let { dao.obligation(it) }
        val candidate = item.copy(outcome = outcome)
        val candidateFacts = serviceEvaluationInput(candidate, visit, plan, obligation)
        val eligibility = ServiceWorkEvaluator.fulfillmentEligibility(candidateFacts)
        val normalizedReason = reason?.trim()?.ifBlank { null }?.takeIf { outcome == "NOT_PERFORMED" }
        val transitionedToPerformed = item.outcome != "PERFORMED" && outcome == "PERFORMED"
        val transitionedToPartly = item.outcome != "PARTLY_PERFORMED" && outcome == "PARTLY_PERFORMED"
        val normalizedFulfills: Boolean? = when (outcome) {
            null -> null
            "NOT_PERFORMED" -> false
            "PERFORMED" -> eligibility == FulfillmentEligibility.ELIGIBLE
            "PARTLY_PERFORMED" -> if (eligibility == FulfillmentEligibility.ELIGIBLE) {
                // Entering Partly performed always starts a new explicit decision;
                // only a choice made while already in Partly is retained.
                if (transitionedToPartly) null else fulfills
            } else false
            else -> null
        }
        var normalizedNextDue: String? = null
        var normalizedCalculated: Boolean? = null
        var normalizedOverrideReason: String? = null
        if (outcome in setOf("PERFORMED", "PARTLY_PERFORMED") && eligibility == FulfillmentEligibility.ELIGIBLE && normalizedFulfills == true) {
            val evaluatedCandidate = ServiceWorkEvaluator.evaluate(candidateFacts.copy(fulfillsCurrentObligation = normalizedFulfills))
            val calculatedDate = evaluatedCandidate.calculatedNextDueDate ?: error("Review the confirmed next due date")
            val actual = LocalDate.parse(visit.actualServiceDate)
            // A transition into Performed restores the standard automatic result;
            // a deliberate override can be applied again after that transition.
            val suppliedDate = nextDue?.trim()?.ifBlank { null }.takeUnless { transitionedToPerformed }
            if (suppliedDate == null || suppliedDate == calculatedDate) {
                normalizedNextDue = calculatedDate
                normalizedCalculated = true
            } else {
                require(calculated == false) { "Manual override must be marked explicitly" }
                val parsed = runCatching { LocalDate.parse(suppliedDate) }.getOrElse { throw IllegalArgumentException("Next due must be a valid date") }
                require(parsed.isAfter(actual)) { "Next due must be after the service date" }
                normalizedOverrideReason = overrideReason?.trim()?.ifBlank { null }
                require(!normalizedOverrideReason.isNullOrBlank()) { "Override reason is required" }
                normalizedNextDue = suppliedDate
                normalizedCalculated = false
            }
        }
        if (item.outcome == outcome && item.fulfillsCurrentObligation == normalizedFulfills && item.notPerformedReason == normalizedReason && item.confirmedNextDueDate == normalizedNextDue && item.nextDueDateCalculated == normalizedCalculated && item.nextDueOverrideReason == normalizedOverrideReason) {
            return item.visitId.let { dao.visit(it)?.modifiedAtEpochMillis ?: error("Visit no longer exists") }
        }
        writeGate.beforeWrite()
        val now = businessTime.instant().toEpochMilli()
        database.withTransaction {
            val current = workingItem(workItemId)
            check(dao.updateCompletionDraft(workItemId, outcome, normalizedFulfills, normalizedReason, normalizedNextDue, normalizedCalculated, normalizedOverrideReason) == 1)
            dao.touchVisit(current.visitId, now)
        }
        return now
    }

    override suspend fun recoverMissingCalculatedNextDue(request: NextDueRecoveryRequest): NextDueRecoveryResult {
        require(request.expectedOutcome in setOf("PERFORMED", "PARTLY_PERFORMED"))
        if (!database.withTransaction { nextDueRecoveryApplicable(request) }) return NextDueRecoveryResult.Superseded
        writeGate.beforeWrite()
        return database.withTransaction {
            if (!nextDueRecoveryApplicable(request)) return@withTransaction NextDueRecoveryResult.Superseded
            val now = businessTime.instant().toEpochMilli()
            check(dao.saveCalculatedNextDueRecovery(request.workItemId, request.expectedCalculatedDate) == 1)
            dao.touchVisit(request.visitId, now)
            NextDueRecoveryResult.Applied(now)
        }
    }

    private suspend fun nextDueRecoveryApplicable(request: NextDueRecoveryRequest): Boolean {
        val item = dao.workItem(request.workItemId) ?: return false
        val visit = dao.visit(item.visitId) ?: return false
        val binding = dispatchDao.itemBindingForWorkItem(item.id)
        val plan = item.servicePlanId?.let { dao.plan(it) }
        val obligation = item.capturedObligationId?.let { dao.obligation(it) }
        val calculatedDate = if (item.intervalCountSnapshot != null && item.intervalUnitSnapshot != null) {
            RecurrenceCalculator.nextDate(LocalDate.parse(visit.actualServiceDate), item.intervalCountSnapshot, item.intervalUnitSnapshot).toString()
        } else null
        return item.visitId == request.visitId &&
            visit.state == "WORKING" &&
            serviceDocumentationMode(binding?.localRole, binding?.documentationDisposition) == ServiceDocumentationMode.LOCAL &&
            item.outcome == request.expectedOutcome &&
            item.outcome in setOf("PERFORMED", "PARTLY_PERFORMED") &&
            item.fulfillsCurrentObligation == true &&
            item.capturedObligationId == request.expectedCapturedObligationId &&
            item.confirmedNextDueDate == null &&
            dao.workingInputBuffers(item.id).none { it.fieldKey in setOf(ServiceDraftFieldKeys.OVERRIDE_DATE, ServiceDraftFieldKeys.OVERRIDE_REASON) } &&
            ServiceWorkEvaluator.fulfillmentEligibility(serviceEvaluationInput(item, visit, plan, obligation)) == FulfillmentEligibility.ELIGIBLE &&
            calculatedDate == request.expectedCalculatedDate
    }

    override suspend fun saveResponse(workItemId: String, questionId: String, disposition: ResponseDisposition, value: String?, reason: String?): Long = saveResponseInternal(
        workItemId = workItemId,
        questionId = questionId,
        disposition = disposition,
        value = value,
        reason = reason,
    )

    override suspend fun saveResponseWithInactiveDrafts(
        workItemId: String,
        questionId: String,
        disposition: ResponseDisposition,
        value: String?,
        reason: String?,
        issueFoundReasonDraft: String?,
        notApplicableReasonDraft: String?,
    ): Long = saveResponseInternal(
        workItemId = workItemId,
        questionId = questionId,
        disposition = disposition,
        value = value,
        reason = reason,
        issueFoundReasonDraftOverride = issueFoundReasonDraft,
        notApplicableReasonDraftOverride = notApplicableReasonDraft,
    )

    override suspend fun saveQuestionTransition(
        workItemId: String,
        questionId: String,
        disposition: ResponseDisposition,
        value: String?,
        reason: String?,
        issueFoundReasonDraft: String?,
        notApplicableReasonDraft: String?,
    ): Long {
        // Validate through the same normalization used by the ordinary response writers
        // before invoking the write gate. The transaction repeats that helper so the
        // persisted response is derived from the transaction's current database state.
        normalizeResponse(
            workItemId = workItemId,
            questionId = questionId,
            disposition = disposition,
            value = value,
            reason = reason,
            issueFoundReasonDraftOverride = issueFoundReasonDraft,
            notApplicableReasonDraftOverride = notApplicableReasonDraft,
        )
        writeGate.beforeWrite()
        val now = businessTime.instant().toEpochMilli()
        return database.withTransaction {
            val normalized = normalizeResponse(
                workItemId = workItemId,
                questionId = questionId,
                disposition = disposition,
                value = value,
                reason = reason,
                issueFoundReasonDraftOverride = issueFoundReasonDraft,
                notApplicableReasonDraftOverride = notApplicableReasonDraft,
            )
            val changed = !normalized.matchesExisting()
            if (changed) dao.persistResponse(normalized.entity(now), normalized.visitId)

            val cleared = questionBufferKeys(questionId).sumOf { fieldKey ->
                dao.deleteWorkingInputBuffer(workItemId, fieldKey)
            }
            if (!changed && cleared > 0) dao.touchVisit(normalized.visitId, now)

            if (changed || cleared > 0) now
            else dao.visit(normalized.visitId)?.modifiedAtEpochMillis ?: error("Visit no longer exists")
        }
    }

    private suspend fun saveResponseInternal(
        workItemId: String,
        questionId: String,
        disposition: ResponseDisposition,
        value: String?,
        reason: String?,
        issueFoundReasonDraftOverride: String? = null,
        notApplicableReasonDraftOverride: String? = null,
    ): Long {
        val normalized = normalizeResponse(
            workItemId = workItemId,
            questionId = questionId,
            disposition = disposition,
            value = value,
            reason = reason,
            issueFoundReasonDraftOverride = issueFoundReasonDraftOverride,
            notApplicableReasonDraftOverride = notApplicableReasonDraftOverride,
        )
        if (normalized.matchesExisting()) return database.withTransaction { workingItem(workItemId); dao.inspection(workItemId)?.modifiedAtEpochMillis ?: error("Working item no longer exists") }
        writeGate.beforeWrite()
        val now = businessTime.instant().toEpochMilli()
        database.withTransaction { workingItem(workItemId); dao.persistResponse(normalized.entity(now), normalized.visitId) }; return now
    }

    private data class NormalizedResponse(
        val existing: WorkingResponseEntity?,
        val responseId: String,
        val workItemId: String,
        val questionId: String,
        val visitId: String,
        val disposition: ResponseDisposition,
        val textValue: String?,
        val numberValue: String?,
        val reason: String?,
        val issueFoundReasonDraft: String?,
        val notApplicableReasonDraft: String?,
    ) {
        fun matchesExisting(): Boolean = existing != null &&
            existing.disposition == disposition.name &&
            existing.textValue == textValue &&
            existing.numberValue == numberValue &&
            existing.reason == reason &&
            existing.issueFoundReasonDraft == issueFoundReasonDraft &&
            existing.notApplicableReasonDraft == notApplicableReasonDraft

        fun entity(modifiedAtEpochMillis: Long) = WorkingResponseEntity(
            responseId,
            workItemId,
            questionId,
            disposition.name,
            textValue,
            numberValue,
            reason,
            modifiedAtEpochMillis,
            issueFoundReasonDraft,
            notApplicableReasonDraft,
        )
    }

    private suspend fun normalizeResponse(
        workItemId: String,
        questionId: String,
        disposition: ResponseDisposition,
        value: String?,
        reason: String?,
        issueFoundReasonDraftOverride: String? = null,
        notApplicableReasonDraftOverride: String? = null,
    ): NormalizedResponse {
        val inspection = dao.inspection(workItemId) ?: error("Working item no longer exists")
        val item = dao.checklistItems(inspection.templateSnapshotId ?: error("Checklist no longer exists"))
            .firstOrNull { it.id == questionId } ?: error("Checklist item no longer exists")
        val existing = dao.responses(workItemId).firstOrNull { it.checklistItemSnapshotId == questionId }
        val normalizedValue = value?.trim()?.takeIf { disposition == ResponseDisposition.VALUE }
        val suppliedReason = reason?.trim()?.ifBlank { null }
        val normalizedReason = when (disposition) {
            ResponseDisposition.ISSUE_FOUND -> if (reason != null) suppliedReason else existing?.issueFoundReasonDraft
            ResponseDisposition.NOT_APPLICABLE -> if (reason != null) suppliedReason else existing?.notApplicableReasonDraft
            else -> null
        }
        val allowed = if (item.responseType == "STATUS") {
            setOf(ResponseDisposition.OK, ResponseDisposition.ISSUE_FOUND, ResponseDisposition.NOT_APPLICABLE, ResponseDisposition.NOT_CHECKED)
        } else {
            setOf(ResponseDisposition.UNANSWERED, ResponseDisposition.NOT_APPLICABLE, ResponseDisposition.VALUE)
        }
        require(disposition in allowed)
        if (disposition == ResponseDisposition.VALUE) require(!normalizedValue.isNullOrBlank())
        if (item.responseType == "NUMBER" && disposition == ResponseDisposition.VALUE) {
            require(isFiniteSignedDecimal(normalizedValue!!)) { "Enter a signed decimal number, for example -12.5" }
        }
        val textDraft = when {
            item.responseType == "TEXT" && disposition == ResponseDisposition.VALUE -> normalizedValue
            item.responseType == "TEXT" && disposition == ResponseDisposition.UNANSWERED -> null
            else -> existing?.textValue
        }
        val numberDraft = when {
            item.responseType == "NUMBER" && disposition == ResponseDisposition.VALUE -> normalizedValue
            item.responseType == "NUMBER" && disposition == ResponseDisposition.UNANSWERED -> null
            else -> existing?.numberValue
        }
        val issueDraft = when {
            issueFoundReasonDraftOverride != null -> issueFoundReasonDraftOverride.trim().ifBlank { null }
            disposition == ResponseDisposition.ISSUE_FOUND && reason != null -> suppliedReason
            else -> existing?.issueFoundReasonDraft
        }
        val notApplicableDraft = when {
            notApplicableReasonDraftOverride != null -> notApplicableReasonDraftOverride.trim().ifBlank { null }
            disposition == ResponseDisposition.NOT_APPLICABLE && reason != null -> suppliedReason
            else -> existing?.notApplicableReasonDraft
        }
        return NormalizedResponse(
            existing = existing,
            responseId = existing?.id ?: stableId("response", workItemId, item.id),
            workItemId = workItemId,
            questionId = questionId,
            visitId = inspection.visitId,
            disposition = disposition,
            textValue = textDraft,
            numberValue = numberDraft,
            reason = normalizedReason,
            issueFoundReasonDraft = issueDraft,
            notApplicableReasonDraft = notApplicableDraft,
        )
    }

    private fun questionBufferKeys(questionId: String): List<String> = listOf(
        ServiceDraftFieldKeys.questionValue(questionId),
        ServiceDraftFieldKeys.questionIssue(questionId),
        ServiceDraftFieldKeys.questionNotApplicable(questionId),
    )

    override suspend fun finalizeVisit(visitId: String): FinalizeResult = database.withTransaction {
        dao.finalRecordForVisit(visitId)?.let { return@withTransaction FinalizeResult.Success(it.id) }
        val visit = dao.visit(visitId) ?: return@withTransaction FinalizeResult.Blocked("Working visit no longer exists"); if (visit.state != "WORKING") return@withTransaction FinalizeResult.Blocked("Visit is not working")
        val profile = visitReportIdentity(visitId)
        if (profile == null || profile.businessName.trim().isEmpty() || profile.technicianName.trim().isEmpty() || profile.zoneId.trim().isEmpty() || visit.customerReferenceSnapshot.isNullOrBlank() || visit.siteReferenceSnapshot.isNullOrBlank()) return@withTransaction FinalizeResult.Blocked("Visit/report identity is incomplete — review it before finalizing")
        val dispatchBinding = dispatchDao.visitBindingForLocalVisit(visitId)
        val dispatchItems = dispatchBinding?.let { dispatchDao.itemBindings(it.dispatchVisitId) }.orEmpty()
        if (dispatchBinding != null && dispatchItems.any { it.localRole == "ASSIGNED" && it.documentationDisposition == "PENDING" }) return@withTransaction FinalizeResult.Blocked("Choose who will document each assigned dispatch item")
        val documentedIds = dispatchItems.filter { it.documentationDisposition == "DOCUMENT_LOCAL" }.mapNotNull { it.localWorkItemId }.toSet()
        val items = dao.visitWorkItems(visitId).filter { dispatchBinding == null || it.id in documentedIds }; if (items.isEmpty()) return@withTransaction FinalizeResult.Blocked("Choose at least one dispatch item to document locally")
        if (items.any { dao.workingInputBuffers(it.id).isNotEmpty() }) return@withTransaction FinalizeResult.Blocked("Unsaved service edits need attention")
        data class Prepared(val item: WorkItemEntity, val work: String, val privateNote: String, val plan: ServicePlanEntity?, val oldObligation: ServiceObligationEntity?, val nextDue: String?)
        val prepared = mutableListOf<Prepared>()
        for (item in items) {
            val row = dao.inspection(item.id) ?: return@withTransaction FinalizeResult.Blocked("Saved work is incomplete"); val outcome = item.outcome ?: return@withTransaction FinalizeResult.Blocked("Choose an outcome for every line")
            for (photo in dao.workItemAttachments(item.id).filter { it.includedInCustomerReport }) {
                val file = attachmentRoot?.let { File(it, photo.storedRelativePath) }
                if (photo.availability != "PRESENT" || file?.isFile != true || file.length() != photo.byteSize || sha256(file) != photo.sha256) return@withTransaction FinalizeResult.Blocked("A selected customer-report photograph is missing or changed")
            }
            val checklistItems = item.templateSnapshotId?.let { dao.checklistItems(it) }.orEmpty()
            val responseRows = item.templateSnapshotId?.let { dao.responses(item.id) }.orEmpty()
            val completeness = item.templateSnapshotId?.let { checklistCompleteness(checklistItems, responseRows) }
            val plan = item.servicePlanId?.let { dao.plan(it) }
            val obligation = item.capturedObligationId?.let { dao.obligation(it) }
            WorkSubjectValidator.validateWorkItem(item, CustomerType.fromCode(dao.customer(visit.customerId)?.customerType ?: error("Customer no longer exists")), plan?.equipmentId)
            val evaluation = ServiceWorkEvaluator.evaluate(
                serviceEvaluationInput(
                    item = item,
                    visit = visit,
                    plan = plan,
                    obligation = obligation,
                    publicWork = row.workPerformed,
                    checklist = completeness,
                    checklistQuestionLabels = checklistItems.associate { it.id to it.label },
                ),
            )
            if (evaluation.completionBlockers.any { it.kind == CompletionBlockerKind.WORK_PERFORMED }) return@withTransaction FinalizeResult.Blocked("Work performed is required for $outcome work")
            if (evaluation.completionBlockers.any { it.kind == CompletionBlockerKind.NOT_PERFORMED_REASON }) return@withTransaction FinalizeResult.Blocked("Reason is required for Not performed work")
            if (evaluation.completionBlockers.any { it.kind == CompletionBlockerKind.FINDING_DESCRIPTION }) return@withTransaction FinalizeResult.Blocked("Issue found needs a public description")
            if (evaluation.completionBlockers.any { it.kind == CompletionBlockerKind.CHECKLIST_INCOMPLETE }) return@withTransaction FinalizeResult.Blocked("Complete all required checklist questions")
            val eligibility = evaluation.fulfillmentEligibility
            val fulfills = item.fulfillsCurrentObligation
            if (fulfills == true && eligibility != FulfillmentEligibility.ELIGIBLE) return@withTransaction FinalizeResult.Blocked("Current service obligation changed — review this line before finalizing")
            if (outcome == "PERFORMED" && eligibility == FulfillmentEligibility.ELIGIBLE && evaluation.projectedFulfillsCurrentObligation != true) return@withTransaction FinalizeResult.Blocked("Performed work must fulfill the current due service")
            if (outcome == "PARTLY_PERFORMED" && eligibility == FulfillmentEligibility.ELIGIBLE && evaluation.projectedFulfillsCurrentObligation == null) return@withTransaction FinalizeResult.Blocked("Choose whether this completes the due service")
            if (fulfills == true) {
                val eligiblePlan = plan ?: return@withTransaction FinalizeResult.Blocked("Current service obligation changed — review this line before finalizing")
                val actual = LocalDate.parse(visit.actualServiceDate); if (eligiblePlan.lastCountedCompletionDate?.let(LocalDate::parse)?.let { !actual.isAfter(it) } == true) return@withTransaction FinalizeResult.Blocked("Service date must be after the latest counted completion")
                val next = item.confirmedNextDueDate ?: return@withTransaction FinalizeResult.Blocked("Confirm the next due date before finalizing"); if (!LocalDate.parse(next).isAfter(actual)) return@withTransaction FinalizeResult.Blocked("Next due must be after the service date")
                val calculatedNext = evaluation.calculatedNextDueDate ?: return@withTransaction FinalizeResult.Blocked("Review the confirmed next due date")
                if (item.nextDueDateCalculated != (next == calculatedNext) || (next == calculatedNext && item.nextDueOverrideReason != null) || (next != calculatedNext && item.nextDueOverrideReason.isNullOrBlank())) return@withTransaction FinalizeResult.Blocked("Review the confirmed next due date")
            } else if (item.confirmedNextDueDate != null || item.nextDueDateCalculated != null || item.nextDueOverrideReason != null) {
                return@withTransaction FinalizeResult.Blocked("Review the confirmed next due date")
            }
            prepared += Prepared(item, row.workPerformed, row.privateInternalNote, plan, obligation, item.confirmedNextDueDate)
        }
        val dispatchIdentity = if (dispatchBinding != null) {
            dispatchDao.technicianIdentity() ?: return@withTransaction FinalizeResult.Blocked("Technician identity is unavailable")
        } else null
        finalizationWriteGate.beforeCommit(); val now = businessTime.instant().toEpochMilli(); val recordId = stableId("record", visitId); val revisionId = stableId("revision-1", visitId)
        dao.insertFinalRecord(FinalRecordEntity(recordId, visitId, revisionId, now)); dao.insertFinalRevision(FinalRecordRevisionEntity(revisionId, recordId, 1, visit.reference, visit.actualServiceDate, now, visit.customerNameSnapshot, visit.siteNameSnapshot, visit.siteAddressSnapshot, profile.businessName, profile.technicianName, profile.phone.ifBlank { null }, profile.email.ifBlank { null }, profile.postalAddress.ifBlank { null }, profile.zoneId, null, visit.customerReferenceSnapshot, visit.siteReferenceSnapshot))
        if (dispatchBinding != null) {
            val identity = checkNotNull(dispatchIdentity)
            dispatchDao.insertFinalDispatchVisit(FinalDispatchVisitEntity(revisionId, dispatchBinding.dispatchVisitId, dispatchBinding.appliedGeneration, dispatchBinding.managerReference, dispatchBinding.senderLabel, identity.technicianId, identity.displayName))
        }
        prepared.forEachIndexed { index, p ->
            val finalItemId = stableId("final-work", revisionId, p.item.id); val fulfills = p.item.fulfillsCurrentObligation == true
            val finalItem = FinalWorkItemEntity(finalItemId, revisionId, index + 1, p.item.id, p.item.equipmentId, p.item.equipmentNameSnapshot, p.item.equipmentReferenceSnapshot, p.item.equipmentIdentifierSnapshot, p.item.equipmentMakeSnapshot, p.item.equipmentModelSnapshot, p.item.equipmentSerialSnapshot, p.item.serviceNameSnapshot, p.item.servicePlanId, p.item.planReferenceSnapshot, p.item.outcome!!, p.work.takeIf(String::isNotBlank), p.item.notPerformedReason, fulfills, p.item.dueDateSnapshot, p.nextDue.takeIf { fulfills }, p.item.intervalCountSnapshot, p.item.intervalUnitSnapshot, p.item.capturedObligationId, p.privateNote.takeIf(String::isNotBlank), p.item.nextDueDateCalculated.takeIf { fulfills }, p.item.nextDueOverrideReason.takeIf { fulfills }, p.item.subjectType, p.item.equipmentDescriptionSnapshot)
            WorkSubjectValidator.validateFinal(finalItem)
            dao.insertFinalWorkItems(listOf(finalItem))
            dispatchItems.find { it.localWorkItemId == p.item.id }?.let { source -> dispatchDao.insertFinalDispatchItems(listOf(FinalDispatchItemEntity(finalItemId, source.dispatchItemId, source.assignedTechniciansJson, source.assignmentMeaning, source.localRole))) }
            p.item.templateSnapshotId?.let { snapshotId -> val responseById = dao.responses(p.item.id).associateBy { it.checklistItemSnapshotId }; val template = dao.templateSnapshot(snapshotId); dao.insertFinalChecklistItems(dao.checklistItems(snapshotId).map { q -> val a = responseById[q.id]; val disposition=a?.disposition ?: if (q.responseType == "STATUS") "NOT_CHECKED" else "UNANSWERED"; FinalChecklistItemEntity(stableId("final-check", finalItemId, q.id), finalItemId, q.position, snapshotId, template?.revision, q.label, q.responseType, q.unit, q.required, disposition, a?.textValue.takeIf { disposition=="VALUE"&&q.responseType=="TEXT" }, a?.numberValue.takeIf { disposition=="VALUE"&&q.responseType=="NUMBER" }, a?.reason.takeIf { disposition in setOf("ISSUE_FOUND","NOT_APPLICABLE") }) }) }
            dao.insertFinalParts(dao.parts(p.item.id).mapIndexed { partIndex, part -> FinalPartEntryEntity(stableId("final-part", finalItemId, part.id), finalItemId, partIndex + 1, part.description, part.quantity, part.unit) })
            dao.insertFinalPhotos(dao.workItemAttachments(p.item.id).filter { it.includedInCustomerReport && it.availability == "PRESENT" }.mapIndexed { photoIndex, photo -> FinalPhotoEntryEntity(stableId("final-photo", finalItemId, photo.id), finalItemId, photoIndex + 1, photo.id, photo.storedRelativePath, photo.sha256, photo.byteSize, photo.mimeType, photo.caption) })
            if (fulfills) { val plan = p.plan!!; val old = p.oldObligation!!; val nextId = stableId("obligation", revisionId, plan.id); check(dao.consumeObligation(old.id, plan.id, now, revisionId) == 1) { "Current service obligation changed — review this line before finalizing" }; dao.insertObligations(listOf(ServiceObligationEntity(nextId, plan.id, old.sequence + 1, p.nextDue!!, now))); check(dao.advancePlan(plan.id, old.id, p.nextDue, nextId, visit.actualServiceDate, revisionId) == 1) { "Current service obligation changed — review this line before finalizing" } }
        }
        check(dao.finalizeVisit(visitId, now) == 1); dao.releaseVisitClaims(visitId); FinalizeResult.Success(recordId)
    }

    override suspend fun finalRecord(recordId: String): FinalRecordDetail? {
        val record = dao.finalRecord(recordId) ?: return null
        return finalRecordRevision(recordId, record.currentRevisionId)
    }

    override suspend fun finalRecordRevision(recordId: String, revisionId: String, renditionId: String?): FinalRecordDetail? {
        val record = dao.finalRecord(recordId) ?: return null; val revision = dao.finalRevision(revisionId)?.takeIf { it.recordId == recordId } ?: return null; val items = dao.finalWorkItems(revision.id)
        val dispatchItemByFinalId = dispatchDao.finalDispatchItems(revision.id).associateBy { it.finalWorkItemId }
        val publicLines = items.map { item -> val dispatchItem=dispatchItemByFinalId[item.id]; PublicWorkLine(item.position, item.equipmentName, item.equipmentReference, listOfNotNull(item.equipmentIdentifier, item.equipmentMake, item.equipmentModel, item.equipmentSerial).joinToString(" · ").ifBlank { if (item.equipmentId == null) null else "Not recorded" }, item.serviceName, item.outcome, item.publicWorkNote, item.notPerformedReason, item.fulfilledObligation, item.oldDueDate, item.nextDueDate, dao.finalChecklistItems(item.id).map { q -> PublicChecklistItem(q.position, q.label, q.responseType, q.unit, q.required, q.disposition, q.textValue ?: q.numberValue, q.reason) }, item.planReference, item.planId != null, dao.finalParts(item.id).map { PublicPart(it.description, it.quantity, it.unit) }, dao.finalPhotos(item.id).map { PublicPhoto(it.storedRelativePath, it.sha256, it.byteSize, it.mimeType, it.caption, it.addedInCorrection, it.addedAtEpochMillis) }, historyOnly=item.planId!=null&&item.capturedObligationId==null, dispatchItemId=dispatchItem?.dispatchItemId, dispatchAssignment=dispatchItem?.let { if(it.assignmentMeaning=="EVERYONE") "Everyone" else { val people=DispatchPackageService(database).parseTech(it.assignedTechniciansJson);val duplicateNames=people.groupingBy{x->x.name}.eachCount();people.joinToString { t -> if(duplicateNames[t.name]!!>1) "${t.name} (${t.technicianId.take(8)})" else t.name } } }, dispatchDocumentationRole=dispatchItem?.localDocumentationRole, subjectType=WorkSubjectType.fromCode(item.subjectType), equipmentDescription=item.equipmentDescription) }
        val finalDispatch = dispatchDao.finalDispatchVisit(revision.id)?.let { PublicDispatchProvenance(it.dispatchVisitId,it.generation,it.managerReference,it.senderLabel,it.documentingTechnicianId,it.documentingTechnicianName) }
        val model = PublicReportModel(record.id, revision.id, revision.revisionNumber, revision.visitReference, revision.actualServiceDate, revision.recordedAtEpochMillis, revision.businessName, revision.technicianName, listOfNotNull(revision.businessPhone, revision.businessEmail, revision.businessAddress).joinToString(" · "), revision.customerName, revision.siteName, revision.siteAddress, publicLines, revision.customerReference, revision.siteReference, record.voided, record.publicVoidReason, revision.publicNote, finalDispatch)
        val renditionEntity = renditionId?.let { dao.reportRenditionById(it)?.takeIf { row -> row.revisionId == revision.id } } ?: dao.reportRendition(revision.id)
        val rendition = renditionEntity?.let { ReportRendition(it.id, it.revisionId, it.versionNumber, it.generatedAtEpochMillis, it.relativePath, it.sha256, it.byteSize, it.pageCount, it.status, it.failureMessage, it.kind) }
        return FinalRecordDetail(model, items.mapNotNull { it.privateInternalNote }, rendition, record.voided, record.publicVoidReason)
    }

    private fun stableId(vararg parts: String) = UUID.nameUUIDFromBytes(parts.joinToString(":").toByteArray()).toString()

    private suspend fun workingItem(workItemId: String): WorkItemEntity {
        val item=dao.workItem(workItemId)?:error("Work item no longer exists")
        require(dao.visit(item.visitId)?.state=="WORKING"){"Visit is no longer working"}
        return item
    }

    private fun clean(value: String?): String? = normalizedOptional(value)
    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    private fun reference(prefix: String, sequence: Int) = ordinaryReference(prefix, sequence)
    private fun validateEquipment(input: EquipmentInput) { require(input.name.trim().isNotEmpty() && input.name.length <= 200); require(input.technicianIdentifier.length <= 100 && input.make.length <= 100 && input.model.length <= 100 && input.serialNumber.length <= 150 && input.privateNote.length <= 5000) }
    private fun validatePlan(input: PlanInput) { require(input.name.trim().isNotEmpty() && input.name.length <= 200); require(input.intervalCount > 0); require(input.intervalUnit in setOf("DAYS", "WEEKS", "MONTHS", "YEARS")); LocalDate.parse(input.dueDate) }
    private fun validateTemplate(name: String, items: List<TemplateItemDraft>) { require(name.trim().isNotEmpty() && name.length <= 200); require(items.isNotEmpty()); items.forEach { require(it.label.trim().isNotEmpty() && it.label.length <= 300); require(it.responseType in setOf("STATUS", "TEXT", "NUMBER")); require(it.unit.length <= 30 && it.privateGuidance.length <= 2000) } }
    private fun reusableItem(revisionId: String, index: Int, item: TemplateItemDraft) = ReusableTemplateItemEntity(UUID.randomUUID().toString(), revisionId, index + 1, item.label.trim(), item.responseType, clean(item.unit).takeIf { item.responseType == "NUMBER" }, item.required, clean(item.privateGuidance))
    private suspend fun followUpDetail(value: FollowUpEntity) = FollowUpDetail(value.id, value.reference, value.type, value.title, value.dueDate, value.state, value.customerId, value.siteId, value.equipmentId, value.privatePlanningNote.orEmpty(), value.closureReason, dao.customer(value.customerId)?.name.orEmpty(), value.siteId?.let { dao.site(it)?.name }, value.equipmentId?.let { dao.equipment(it)?.name }, value.updatedAtEpochMillis)

    private suspend fun captureTemplateSnapshot(templateId: String?, visitId: String, planId: String, now: Long, allowDisabled: Boolean = false): String? {
        val master = templateId?.let { dao.reusableTemplate(it) } ?: return null
        require(master.state == "ACTIVE" || (allowDisabled && master.state == "DISABLED")) { "Template is unavailable" }
        val revision = dao.reusableTemplateRevision(master.currentRevisionId) ?: error("Template revision missing")
        val revisionItems=dao.reusableTemplateItems(revision.id)
        val content = DispatchInspectionSnapshot("", revision.nameSnapshot, master.reference, revision.revisionNumber, revisionItems.map { item -> DispatchInspectionItem(item.position,item.label,item.responseType,item.unit,item.required,item.privateGuidance) })
        val snapshotId = DispatchPackageCodec.contentAddressedSnapshotId(content)
        dao.templateSnapshot(snapshotId)?.let { existing ->
            require(existing.sourceTemplateId==master.id&&existing.templateName==revision.nameSnapshot&&existing.revision==revision.revisionNumber){"Existing template snapshot does not match the current immutable revision"}
            val expected=revisionItems.map { item -> ChecklistItemSnapshotEntity(stableId("snapshot-item",snapshotId,item.id),snapshotId,item.position,item.label,item.responseType,item.unit,item.required,item.privateGuidance) }
            val actual=dao.checklistItems(snapshotId)
            require(actual==expected){"Existing checklist snapshot does not match the current immutable revision"}
            return snapshotId
        }
        dao.insertTemplateSnapshots(listOf(TemplateSnapshotEntity(snapshotId, master.id, revision.nameSnapshot, revision.revisionNumber, now)))
        dao.insertChecklistItems(revisionItems.map { item -> ChecklistItemSnapshotEntity(stableId("snapshot-item", snapshotId, item.id), snapshotId, item.position, item.label, item.responseType, item.unit, item.required, item.privateGuidance) })
        return snapshotId
    }

}
