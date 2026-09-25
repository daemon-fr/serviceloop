package com.v16studio.v16service.domain

/** One atomically published read projection for the active Service work item. */
data class ServiceWorkspace(
    val inspection: InspectionDraft,
    val serviceProgress: VisitServiceProgress?,
    val completionLines: List<CompletionLine>,
)

/** Service-route state published as one unit, including the visit-only resume context. */
data class ServiceContext(
    val workspace: ServiceWorkspace? = null,
    val progress: VisitServiceProgress? = null,
    val activeVisitId: String? = null,
    val activeWorkItemId: String? = null,
)

/** Review owns completion rows separately when no active Service workspace is shown. */
data class CompletionContext(
    val visitId: String,
    val lines: List<CompletionLine>,
)
