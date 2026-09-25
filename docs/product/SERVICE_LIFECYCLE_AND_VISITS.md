# V16 Service — Service Lifecycle and Visit Contract

## Service obligations and due work

A Service Plan and its recurrence define future obligations. Due work is a projection of obligations and the business clock; arranging a Visit does not perform service. Date/time decisions use the business-local date and injected clock/time-zone rules. Optional appointment time is distinct from scheduled service date.

Recurring work, flexible/ad-hoc work, and one-time customers follow the adopted Conceptual App Map. A one-time customer has no recurring plan contract and is blocked while recurring plans remain. Ad-hoc tasks do not fabricate recurring obligations.

## Visit setup and execution

Ordinary and Dispatch Visit setup share canonical Customer/site, schedule, task/checklist, and draft semantics. Staged setup validates references and assignment as one operation. A site change that invalidates staged equipment/work requires deliberate confirmation; content is not silently retargeted. Saved drafts are explicit and are not export-ready until required work and assignment data exist.

Booked, dispatched, started, Working, completed, finalized, cancelled, and concluded states keep separate meanings. A booked Visit can be edited under current lifecycle rules. A dispatched package records a generation and assignment snapshot; a later edit does not rewrite an exported generation.

Working inspection answers persist as a durable draft. A failed save remains visibly failed and preserves the last saved checkpoint. Imported assignment snapshots preserve ordered checklist identity/content against later template edits.

## Completion, finalization, and history

Technician performance records performed, partly performed, or not-performed outcomes with the required checklist, evidence, and service facts. Finalization, recurrence fulfillment, report generation, report sharing, and delivery are separate events. A valid obligation is fulfilled exactly once, including when completion/finalize is retried.

Finalized source records are immutable. Corrections append a numbered revision linked to the exact source revision. Voids retain the source and explicit void history. Later export, report, restore, or retry never silently overwrites corrected/voided history. Follow-ups are captured in an immutable revision-time snapshot, including an explicit empty snapshot.

Coordinator/Team Leader conclusion is administrative closure of delegated work. It does not claim the coordinator performed service, rewrite technician evidence, fulfill recurrence again, generate a report, prove delivery, or imply central acceptance.

## Captured context and privacy

Booked/working Visits retain relevant Customer/site/address, assignment, and checklist snapshots so later master-data/template changes do not rewrite history. Visit Maps uses the captured address where available; it is an external Intent handoff without state mutation or permission request.

Customer reports include public facts only. Private notes, access/internal details, and private/internal evidence stay out. Photo report inclusion is an independent explicit choice and is valid only for PUBLIC evidence.

## Failure and retry

Durable persistence is the success boundary. No UI success precedes the durable write. Completion, finalization, recurrence, import, report rendition, image ownership, and correction retries are idempotent. Recovery and failed-file operations preserve the last known-good dataset. Exact v1 shapes and rejection rules are in the [persistence contract](../project/PERSISTENCE_DATA_CONTRACT.md).
