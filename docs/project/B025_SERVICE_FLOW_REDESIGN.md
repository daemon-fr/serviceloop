# B-025 Service Flow Redesign

Status: Adopted foundation / Bundle 1 implemented

This milestone establishes the ServiceLoop service-flow foundation for the Bundle 1 redesign. It keeps the product loop intact:

Customer → site → equipment → service plan → due work/arrangements → working visit → service record/report → next obligation/follow-up.

## Adopted foundation

- Working facts are saved continuously through durable raw input buffers plus canonical repository writes. Raw buffers are separate from canonical facts and are never copied into final history or reports.
- Inspection completeness is derived from the current immutable checklist snapshot and active Working responses. The legacy `checklistReviewed` Room field remains a compatibility/cache field only.
- Issue-found descriptions and Not-applicable reasons retain inactive Working drafts without leaking them into active or final output. Private guidance remains technician-only.
- Outcome and current-obligation fulfillment are separate decisions. Fulfillment is tri-state while unresolved; only an explicit true decision advances a current recurring obligation.
- A true eligible fulfillment calculates the next due date immediately from the actual service date and captured interval. A different date requires an explicit override reason.
- Response changes recompute inspection completeness and invalidate an obsolete true fulfillment atomically when the current inspection is no longer complete.
- Finalization recomputes completeness and eligibility directly, flushes pending service draft work, and never relies on cached review state or raw input buffers.

## Storage and recovery

Room schema 14 adds exactly one `working_input_buffers` table keyed by `(workItemId, fieldKey)` with a cascading WorkItem foreign key. Migration 13→14 normalizes legacy fulfillment and due-date combinations without rewriting Final* history. Portable backup/recovery preserves the table and inserts an empty table for older supported packages without inferring rows.

## Scope boundary

This document covers Bundle 1 only. Bundles 2–4, the broad UI overhaul, localization, and unrelated product expansion are outside this milestone.
