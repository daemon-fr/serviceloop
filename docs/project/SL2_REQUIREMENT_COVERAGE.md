# ServiceLoop — SL-2 Requirement Coverage

**Status:** Developer implementation and validation complete on `codex/sl-2-first-complete-service-loop`; owner acceptance and B-008 pilot remain outstanding.

Original implementation commit: `208f4ebd9bab22a74fc9a9bd4f938582bb406ff9`.

Independent-review correction implementation: `52b59a8d86bbb98bdb3e1c7cb634f877d601f1a5`; pushed correction/evidence state: `1ba9ad5403d460ddb3f3f519e14671fcfecd305a`.

Final technical closure implementation: `45d3856e3a9fa447c2f9d171236419f86db9adb5`; the evidence-documentation commit containing this file is the final branch HEAD. Current verification totals are 63 unit tests and 13 canonical-AVD instrumentation tests, all passing.

## Traceability

| Area | Status | Evidence |
|---|---|---|
| Room v1→v2→v3 additive migration | DEVELOPER-RUNTIME-VALIDATED | Exported `1.json`/`2.json`/`3.json`; explicit retained migrations; synthetic v1→3 and v2→3 instrumentation; canonical v2→3 `install -r` with finalized V-001, P-001 due state, provenance, rendition metadata, and PDF hash retained. |
| Public work and checklist review | IMPLEMENTED / TESTED | Deliberate normalized work-note save; all explicitly saved answers are type-valid before any outcome can finalize. Performed requires legitimate review; Partly/Not performed permit missing, Not checked, or Unanswered entries but reject malformed VALUE/NA/Issue data. Answer edits atomically invalidate Reviewed. |
| Completion draft | IMPLEMENTED / TESTED | Durable outcome, fulfillment, not-performed reason, confirmed calculated/overridden next date and override reason. |
| Recurrence | IMPLEMENTED / TESTED | Pure completion-date-based days/weeks/months/years calculator; month-end and leap clipping tests. |
| Atomic finalization | IMPLEMENTED / TESTED | Single Room transaction, nonblank captured issuer/zone and customer/site-reference validation, exact captured obligation comparison, active/ownership/unconsumed/latest-counted guards, conditional consumption/pointer update, deterministic next obligation, rollback gate, visit finalized last. |
| Idempotence and concurrency safety | TESTED | An actual simultaneous coroutine pair returns the same record id with one record/revision, one consumption/advance, and one next obligation. Unique constraints and deterministic ids remain final guards; report generation retains a process-wide mutex plus unique revision/version row. |
| Non-effect lines | TESTED / DEVELOPER-RUNTIME-VALIDATED | Performed-unfulfilled, partial, not-performed, and one-off history do not advance recurrence; two plans remain independent. |
| Immutable final record / R05 capture | IMPLEMENTED / TESTED | Stable record plus append-ready revision 1 and line/checklist snapshots; equipment identity, customer/site references, and visit report identity come from working snapshots. Master edits do not rewrite history; explicit pre-finalization identity refresh is tested. |
| Public/private boundary | IMPLEMENTED / TESTED | PDF and Text view receive only `PublicReportModel`; access/internal sentinel test; private record section remains structurally separate. |
| PDF pipeline | IMPLEMENTED / TESTED / DEVELOPER-RUNTIME-VALIDATED | Native offline renderer, measured wrapping including long tokens, height-aware pagination/footer reservation, non-orphan section headings, temp→persistent adoption, hash/size/page metadata, failed retry isolation, PdfRenderer view, restricted FileProvider share. Synthetic multi-page output reopened every page and rendered the final page. |
| Reopen and projection freshness | DEVELOPER-RUNTIME-VALIDATED | Cold reopen; Home unfinished removal/count refresh; VisitSummary has visit-specific resume identity; Work → Visits finalized row; final record, PDF, and structured Text reopen. Successful writes remain Saved when a post-write reread fails. |
| Report semantics and availability | IMPLEMENTED / TESTED / HUMAN-RENDERED | Public model retains recurring/one-off and plan-reference semantics, customer/site references, outcome/reason/due effects, and Text parity. READY+missing derives File missing, keeps Text usable, disables Share, and never silently recreates v1. Metadata failure cleans an adopted orphan. Existing canonical bytes were not regenerated. |
| Completion UI wiring | UI-INSTRUMENTED | Isolated deterministic Compose test uses semantic controls for outcome, fulfillment, calculated date, Finalize, and resulting Final Service Record navigation. Domain tests are reported separately; canonical history was not altered for this evidence. |
| Stale obligation projection | IMPLEMENTED / TESTED / UI-INSTRUMENTED | Completion Review exposes `CURRENT_OBLIGATION_CHANGED`, sanitizes fulfillment and next-date effects, hides the control, and explains the required review. Finalization independently blocks the stale state. |
| Save truth and counts | IMPLEMENTED / TESTED / UI-INSTRUMENTED | Response/public-work values normalize before comparison/storage; semantic no-ops bypass the write gate. Business Profile uses its own persisted timestamp/status. Home renders actual Working/Booked totals with deterministic preview identities. |
| Missing report and recorded time | IMPLEMENTED / TESTED / UI-INSTRUMENTED | “View report text” opens with Text selected immediately and Share disabled when READY bytes are absent. Normal present reports still open PDF. Final Record displays the full local recorded date/time. |
| SL-1 navigation/atomicity regression | TESTED | No-transition NavHost retained; focused post-finalization canonical root-switch test passed. |

## Residual scope

The milestone intentionally does not implement general master-data CRUD, plan editing, booking/rescheduling, richer findings/photos/parts, correction/void flows, recovery/import/export, reminders, search, signatures, or broader report design/customization. SL-2 is not owner accepted. B-008 requires a real technician/trade pilot before Stage 2 product validity or release preparation.
