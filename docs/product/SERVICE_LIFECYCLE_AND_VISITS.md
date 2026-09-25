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

## Adopted outcome, readiness, and draft rules

For eligible captured recurring work, PERFORMED selects fulfillment automatically. PARTLY_PERFORMED retains an undecided fulfillment value until the technician explicitly chooses Fulfill or Keep due. NOT_PERFORMED never fulfills and requires a reason. Non-recurring work cannot manufacture an obligation; only known Equipment under a STANDARD customer may carry recurring plans and obligations. Only finalized eligible true fulfillment consumes the captured obligation and advances its plan, and retries do not repeat that effect. The next due date is calculated from the actual service date and captured interval. A different date requires a nonblank override reason; the ordinary calculated date needs no second confirmation.

Checklist readiness is derived from the captured checklist and active Working responses. Incomplete work blocks Review and finalization for every outcome. The compatibility checklistReviewed value and a manual Mark checklist reviewed action are not readiness authority. Incompleteness does not erase the stored outcome or fulfillment choice.

Raw autosave buffers remain separate from canonical facts and are versioned so stale asynchronous writes cannot replace newer input. Raw buffers never become final history or report content. Saved drafts for inactive answer dispositions survive switching, but only the selected disposition and its applicable detail satisfy the active answer and enter final history. Before finalization, draft work is flushed and completeness and eligibility are recomputed against the captured context before history is frozen. These are the adopted B-007, B-014, and B-025 overrides to the starting map.

## Retained history, cancellation, flexible work, and pilot rules

A mistaken saved contact note is retained as history and marked Entered in error with a reason; it is not ordinarily hard-deleted. Restoring a locally canceled booking is allowed only when its cancellation provenance permits restoration. In the same Visit, the operation revalidates and reacquires every captured active, current, unconsumed, unclaimed obligation atomically, or reacquires none. It preserves cancellation history and does not fulfill work or change due dates.

Work subjects distinguish SITE, known EQUIPMENT, and unidentified EQUIPMENT. Known Equipment retains its identity snapshot; unidentified Equipment carries its bounded description. Only known Equipment for a STANDARD customer may have a recurring plan or obligation. SITE and unidentified Equipment work remain flexible/ad-hoc and cannot gain recurrence through completion.

The late release/pilot gate remains: a pilot-ready, Romanian-localized, functionally complete representative workflow and customer report must be evaluated by a real technician/trade user. AI, owner/developer, automated, and emulator agreement does not satisfy that external evidence. This records the existing gate; it does not schedule localization or a pilot.
