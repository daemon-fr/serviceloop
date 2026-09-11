# B023 — Unified Visit lifecycle and Coordinator cancellation

Status: owner-amended implementation reference for the R2 Batch 1 inspection correction.

## Canonical local Visit lifecycle

The only current persisted Technician Visit states are `BOOKED`, `WORKING`, `COMPLETED`, and `CANCELED`. Their labels are Booked, Working, Completed, and Canceled. `FINALIZED`, `PARTICIPATION_COMPLETE`, `DISPATCH_WITHDRAWN`, and `CANCELLED` are migration/compatibility inputs only; they normalize to Completed or Canceled during Room 11→12 migration.

Completed is a lifecycle outcome, not proof that a local final record or PDF exists. A full local documentation path creates the durable final record. Dispatch documentation handoff and parallel work may complete the local Visit without manufacturing a record, PDF, failed result, recurrence effect, or coordinator status return.

Documentation ownership remains item-level metadata. The technician action is **Complete visit**, and its existing preconditions remain: no locally documented assigned item and every assigned item is explicitly deferred. Completion releases local claims and preserves dispatch provenance.

Cancellation requires a nonblank reason. Local cancellation applies to an untouched Booked Visit, releases claims, leaves the obligation due, and may be restored only by the existing conservative restore checks. Coordinator cancellation or final assignment removal may transition Booked or Working to Canceled while preserving drafts, work items, attachments, and immutable history. Those origins cannot be restored by the ordinary local Restore action. A local Completed Visit stays Completed when coordinator cancellation arrives; provenance is recorded without rewriting a final record/report.

## Coordinator Outbox

The current Outbox states are Draft, Dispatched, Canceled, and Concluded. Draft and Dispatched can be canceled with a nonblank reason. A canceled Draft is stored in history and is not exported. Canceling a previously Dispatched Visit requires a newer `.slwork` export; no delivered/received claim is made. Concluded remains coordinator-only and never changes a technician device.

## `.slwork` v3 transport

The app reads valid v2 packages as active Visits and emits v3 packages. v3 adds per-Visit `transportLifecycle` (`ACTIVE` or `CANCELED`) and a required nonblank cancellation reason for canceled Visits. Lifecycle and reason participate in the material hash. The first export is generation 1; unchanged material keeps its generation; a cancellation after an active export increments exactly once; unchanged cancellation is idempotent.

Import is generation-safe: older packages never roll state backward and same-generation material conflicts remain conflicts. A canceled package transitions local Booked/Working state to Canceled, releases claims, does not advance recurrence, and preserves work/evidence/provenance. Completed remains Completed with provenance only. An active package that removes the final applicable assignment uses the same Canceled state and preserves started local work.

## Persistence and scope

Room version 12 adds durable cancellation-origin/reason fields without destructive fallback. Recovery and migration preserve records, evidence, bindings, export generations, and history. ServiceLoop remains local-first: no backend, accounts, live sync, dispatch acknowledgement, status-return transport, or localization pass is part of B023.
