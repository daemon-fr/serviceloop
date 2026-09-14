# B-025 Service Flow Redesign

Status: Bundle 1 + Service Flow 2A and 2B implemented

This milestone establishes the ServiceLoop service-flow foundation for the Bundle 1 redesign. It keeps the product loop intact:

Customer → site → equipment → service plan → due work/arrangements → working visit → service record/report → next obligation/follow-up.

## Adopted foundation

- Working facts are saved continuously through durable raw input buffers plus canonical repository writes. Raw buffers are separate from canonical facts and are never copied into final history or reports.
- Restored raw buffers remain authoritative unsaved Working edit state until the field-aware ViewModel/coordinator reconciles them; an unregistered durable buffer cannot be treated as clean.
- Inspection completeness is derived from the current immutable checklist snapshot and active Working responses. The legacy `checklistReviewed` Room field remains a compatibility/cache field only.
- Issue-found descriptions and Not-applicable reasons retain inactive Working drafts without leaking them into active or final output. Private guidance remains technician-only.
- Blank Issue-found and Not-applicable descriptions are valid persistence operations that clear the active and preserved reason, while the resulting inspection remains incomplete.
- Outcome controls the technician decision: eligible `Performed` automatically fulfills the captured current recurring obligation; eligible `Partly performed` keeps fulfillment nullable until an explicit `Fulfill` or `Keep due` choice; `Not performed` persists false and requires a reason. Only finalized true fulfillment consumes the current obligation and advances recurrence.
- A true eligible fulfillment calculates the next due date immediately from the actual service date and captured interval. A different date requires an explicit override reason.
- Response changes recompute inspection completeness without erasing automatic Performed fulfillment or an explicit Partly choice. Incomplete checklist answers block Review/finalization for every outcome; the checklist count is the technician-facing explanation.
- Finalization recomputes completeness and eligibility directly, flushes pending service draft work, and never relies on cached review state or raw input buffers.

## Storage and recovery

Room schema 14 adds exactly one `working_input_buffers` table keyed by `(workItemId, fieldKey)` with a cascading WorkItem foreign key. Migration 13→14 normalizes legacy fulfillment and due-date combinations without rewriting Final* history. Portable backup/recovery preserves the table and inserts an empty table for older supported packages without inferring rows. Finalization is conservatively blocked for every documented WorkItem with any unresolved durable raw input buffer; those buffers are retained for reconciliation.

## Service Flow 2A and 2B — unified Service workspace

The Working Visit path is Start → Service → Review visit → Finalize. Service is one continuous workspace per WorkItem with grouped Site work, known Equipment work, and separate unidentified Equipment work. Derived entry status is Not started, In progress, Needs attention, or Ready for review. Ready for review means all applicable Service requirements are resolved according to the canonical completion blockers, with no unresolved raw Service input; it does not finalize the Visit. Every outcome requires its applicable checklist to be complete before Review/finalization. Invalid explicit answers, missing Issue-found descriptions, unresolved raw input, and every other applicable completion blocker still prevent readiness.

Work performed, the optional private work note, checklist widgets, issue findings, Not applicable reasons, and TEXT/NUMBER answers use continuous autosave with durable raw-input recovery and no ordinary per-field Save buttons. Private operational context remains technician-only. Next Service follows same-group then forward then wrap-around Visit order, and session last-active context supports Resume service.

Service also owns Working parts, photos, explicit corrective follow-up creation, Outcome, fulfillment, and recurrence consequence. New photos start private; report inclusion and caption are deliberate saved photo facts. Removing ServiceLoop's owned photo requires confirmation and coordinated file/metadata cleanup. A saved Private note survives a Working app/process restart independently of Outcome, Review, or finalization.

Outcome has no default. An eligible Performed outcome automatically fulfills the captured current recurring obligation and persists the calculated next due from actual service date plus captured interval. An eligible Partly performed outcome shows the single dependent `Does this complete the due service?` choice with no default; Fulfill calculates the next due, while Keep due leaves the captured obligation outstanding. Not performed requires a reason and remains due. One-off and history-only work do not show recurring decisions. Change next due is subordinate and commits a valid later date and reason together; half-entered text remains only draft input. Review visit summarizes these facts and links blockers back to their Service, with finalization/report changes reserved for Bundle 3.

Dispatch documentation mode remains authoritative: local work is editable, pending choices require the Visit assignment, leader-observe work is visible but not documented locally, and handed-off work is read-only. The transitional FieldEvidence route is retained only for pre-existing connected/visual test callers; normal Service navigation no longer uses it.

Successful Service saves use one compact status; the current Service row is selected and non-navigable; Service uses “Back to visit” alongside “Review visit”; Visit relationship actions follow the uninterrupted Visit identity; and task selection calls a reusable template an “Inspection checklist.”

## Scope boundary

This document covers Bundle 1 and Service Flow 2A/2B. Bundle 3 finalization/report redesign, localization, and unrelated product expansion remain outside this milestone.
