package com.v16studio.serviceloop.domain

data class CustomerDetail(
    val id: String, val reference: String, val name: String, val contactName: String,
    val phone: String, val email: String, val privateNote: String,
    val sites: List<SiteSummary>, val equipment: List<EquipmentSummary>, val openFollowUps: List<FollowUpDetail>,
    val recentContacts: List<ContactNoteDetail>, val state: String = "ACTIVE",
)

data class SiteSummary(val id: String, val reference: String, val name: String, val address: String, val equipmentCount: Int, val isDefault: Boolean)
data class SiteRegisterSummary(val id: String, val reference: String, val name: String, val customerName: String, val address: String, val equipmentCount: Int)

data class VisitSiteOption(
    val id: String,
    val reference: String,
    val name: String,
    val customerName: String,
    val equipment: List<EquipmentSummary>,
)

data class SiteDetail(
    val id: String, val customerId: String, val customerName: String, val reference: String,
    val name: String, val address: String, val contactName: String, val phone: String,
    val email: String, val privateAccessNote: String, val isDefault: Boolean,
    val equipment: List<EquipmentSummary>, val state: String = "ACTIVE",
)

data class CustomerInput(val name: String, val contactName: String = "", val phone: String = "", val email: String = "", val privateNote: String = "")
data class SiteInput(val name: String, val address: String, val contactName: String = "", val phone: String = "", val email: String = "", val privateAccessNote: String = "", val isDefault: Boolean = false)
data class EquipmentInput(val name: String, val technicianIdentifier: String = "", val make: String = "", val model: String = "", val serialNumber: String = "", val privateNote: String = "")
data class PlanInput(val name: String, val intervalCount: Int, val intervalUnit: String, val dueDate: String, val reusableTemplateId: String? = null, val dueDateChangeReason: String = "")
data class PlanDetail(val id: String, val equipmentId: String, val equipmentName: String, val reference: String, val name: String, val intervalCount: Int, val intervalUnit: String, val dueDate: String, val state: String, val reusableTemplateId: String?)

data class DueService(
    val planId: String, val planReference: String, val planName: String, val dueDate: String,
    val obligationId: String, val equipmentId: String, val equipmentReference: String,
    val equipmentName: String, val siteId: String, val siteName: String,
    val customerId: String, val customerName: String, val claimedVisitId: String?, val bucket: DueBucket,
)

enum class DueBucket { OVERDUE, TODAY, DUE_SOON, UPCOMING }

data class TemplateSummary(val id: String, val reference: String, val name: String, val revisionNumber: Int, val itemCount: Int, val state: String)
data class TemplateItemDraft(val label: String, val responseType: String, val unit: String = "", val required: Boolean = false, val privateGuidance: String = "")
data class TemplateDetail(val id: String, val reference: String, val name: String, val revisionNumber: Int, val state: String, val items: List<TemplateItemDraft>)

data class VisitDetail(
    val id: String, val reference: String, val state: String, val customerId: String,
    val customerName: String, val siteId: String, val siteName: String, val siteAddress: String,
    val serviceDate: String, val scheduledAtEpochMillis: Long?, val appointmentZoneId: String?,
    val lines: List<VisitLine>, val cancellationReason: String?,
    val appointmentReminderLeadMinutes: Int? = null,
)
data class VisitLine(val workItemId: String, val equipmentName: String, val equipmentReference: String, val serviceName: String, val dueDate: String?, val outcome: String?)

data class PartEntry(val id: String, val description: String, val quantity: String, val unit: String)
data class PhotoEntry(val id: String, val relativePath: String, val mimeType: String, val byteSize: Long, val includedInReport: Boolean, val caption: String?)

data class FollowUpDetail(
    val id: String, val reference: String, val type: String, val title: String, val dueDate: String,
    val state: String, val customerId: String, val siteId: String?, val equipmentId: String?,
    val privatePlanningNote: String, val closureReason: String?, val customerName: String = "",
    val siteName: String? = null, val equipmentName: String? = null,
)
data class FollowUpInput(val type: String, val title: String, val dueDate: String, val customerId: String, val siteId: String? = null, val equipmentId: String? = null, val privatePlanningNote: String = "", val sourceVisitId: String? = null, val sourceWorkItemId: String? = null)

data class ContactNoteDetail(val id: String, val reference: String, val channel: String, val occurredAtEpochMillis: Long, val outcome: String, val privateNote: String, val enteredInError: Boolean, val errorReason: String? = null)
data class ContactNoteInput(val customerId: String, val siteId: String? = null, val equipmentId: String? = null, val channel: String, val outcome: String, val privateNote: String = "")

data class SearchTarget(val type: String, val id: String, val reference: String, val title: String, val subtitle: String)

enum class VisitFilter { ALL, BOOKED, WORKING, FINALIZED, CANCELLED, PARTICIPATION_COMPLETE, DISPATCH_WITHDRAWN }
enum class VisitDateWindow { ALL_DATES, PAST_30_DAYS, NEXT_30_DAYS }
enum class FollowUpFilter { DUE_OR_OVERDUE, UPCOMING, ALL_OPEN, CLOSED }
