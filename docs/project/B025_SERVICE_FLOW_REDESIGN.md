# B-025 Service Flow Redesign

Status: Bundle 1 + Service Flow 2A implemented

This milestone establishes the ServiceLoop service-flow foundation for the Bundle 1 redesign. It keeps the product loop intact:

Customer → site → equipment → service plan → due work/arrangements → working visit → service record/report → next obligation/follow-up.

## Adopted foundation

- Working facts are saved continuously through durable raw input buffers plus canonical repository writes. Raw buffers are separate from canonical facts and are never copied into final history or reports.
- Restored raw buffers remain authoritative unsaved Working edit state until the field-aware ViewModel/coordinator reconciles them; an unregistered durable buffer cannot be treated as clean.
- Inspection completeness is derived from the current immutable checklist snapshot and active Working responses. The legacy `checklistReviewed` Room field remains a compatibility/cache field only.
- Issue-found descriptions and Not-applicable reasons retain inactive Working drafts without leaking them into active or final output. Private guidance remains technician-only.
- Blank Issue-found and Not-applicable descriptions are valid persistence operations that clear the active and preserved reason, while the resulting inspection remains incomplete.
- Outcome and current-obligation fulfillment are separate decisions. Fulfillment is tri-state while unresolved; only an explicit true decision advances a current recurring obligation.
- A true eligible fulfillment calculates the next due date immediately from the actual service date and captured interval. A different date requires an explicit override reason.
- Response changes recompute inspection completeness and invalidate an obsolete true fulfillment atomically when the current inspection is no longer complete.
- Finalization recomputes completeness and eligibility directly, flushes pending service draft work, and never relies on cached review state or raw input buffers.

## Storage and recovery

Room schema 14 adds exactly one `working_input_buffers` table keyed by `(workItemId, fieldKey)` with a cascading WorkItem foreign key. Migration 13→14 normalizes legacy fulfillment and due-date combinations without rewriting Final* history. Portable backup/recovery preserves the table and inserts an empty table for older supported packages without inferring rows. Finalization is conservatively blocked for every documented WorkItem with any unresolved durable raw input buffer; those buffers are retained for reconciliation.

## Service Flow 2A — unified Service workspace

The Working Visit path is now Start → Service → Review visit. Service is one continuous workspace per WorkItem with grouped Site work, known Equipment work, and separate unidentified Equipment work. Derived entry status is Not started, In progress, Needs attention, or Ready; Ready means Service-entry work has no remaining checklist issue, not that the Visit is finalized.

Work performed, the optional private work note, checklist widgets, issue findings, Not applicable reasons, and TEXT/NUMBER answers use continuous autosave with durable raw-input recovery and no ordinary per-field Save buttons. Private operational context remains technician-only. Next Service follows same-group then forward then wrap-around Visit order, and session last-active context supports Resume service.

Dispatch documentation mode remains authoritative: local work is editable, pending choices require the Visit assignment, leader-observe work is visible but not documented locally, and handed-off work is read-only. Parts & photos remains a transitional separate action. Outcome, fulfillment, and recurrence decisions remain outside this 2A workspace; Review/finalization and PDF behavior remain later scope.

Owner feedback on 2A clarified the transitional UI: successful Service saves use one compact status; READY appears as “Ready for outcome” until 2B; the current Service row is selected and non-navigable; Service uses “Back to visit” alongside “Review visit”; Visit relationship actions follow the uninterrupted Visit identity; and task selection calls a reusable template an “Inspection checklist.”

## Scope boundary

This document covers Bundle 1 and Service Flow 2A. Service Flow 2B, Review/finalization/PDF bundles, localization, and unrelated product expansion remain outside this milestone.
