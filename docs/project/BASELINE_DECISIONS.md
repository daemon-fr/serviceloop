# ServiceLoop — Adopted Baseline Decisions

**Adopted:** 2026-09-06; amendments through 2026-09-21

The owner approved the orchestrator's recommended working baseline.

## B-001 — Functional and UI/UX authority

- `ServiceLoop_Conceptual_App_Map_v0_1.md` (C) is the adopted semantic/functional baseline.
- `ServiceLoop_Conceptual_UI_UX_Design_v0_1.md` plus `ServiceLoop_UI_UX_Action_Coverage_v0_1.md` are the adopted presentation, interaction, and coverage baseline.
- The UI/UX document's explicitly labelled **FC-01**, **FC-02**, and **FC-03** additions are adopted:
  - **FC-01:** global history/report-attention discoverability via Work → More → History and Home records-needing-attention links, without adding a new root or analytics feature.
  - **FC-02:** narrow save-failure rescue actions: Open device storage settings and Copy unsaved text, with privacy limits and no false recovery promises.
  - **FC-03:** equipment moves are blocked while a related correction draft is open; the draft must be opened/resolved rather than silently retargeted.
- `ServiceLoop_Complete_Functional_App_Map_v0_1.md` (F) remains non-authoritative reference unless a later explicit amendment adopts a named F policy.

## B-002 — Saved contact notes

Once a contact note has been saved as history, it is not hard-deleted as an ordinary correction mechanism. A mistaken saved note is retained and marked **Entered in error** with a reason. This preserves historical meaning without creating a broader compliance/audit subsystem.

## B-003 — Voided report sharing

A voided original report remains viewable/exportable as historical evidence, but ordinary customer **Share** is disabled for that voided version. The current correction/void notice is the customer-facing handoff route. Superseded-but-not-voided versions retain the warning behavior defined by the adopted UI/UX design.

## B-004 — Scope boundary

Preserve the local-first solo-technician product boundary and the adopted CORE/LATER/OUT scope. Do not add a backend, accounts, team synchronization, dispatch, invoicing/accounting, inventory management, customer portals, or other excluded systems without a separate owner scope decision.

## B-005 — Development task sizing

Prefer substantial, coherent Codex assignments that deliver demonstrable workflow or stage outcomes. Do not fragment capable implementation work into many small prompts. Split only at meaningful workflow, integrity, platform-risk, or review boundaries, and always leave a reviewable checkpoint.

## B-006 — Recovery and import boundary

- ServiceLoop has one authoritative local dataset/device at a time; it is not a multi-device merge/sync product.
- The product recovery contract is an explicit portable **passphrase-protected complete backup** containing the relevant database records and owned files with version/integrity metadata.
- If source media/files are already missing, an **incomplete recovery copy** may be created only when explicitly labelled as incomplete; it must not be represented as a verified complete backup.
- Restore is a staged, verified **replacement** operation, not an automatic merge with the current dataset. Failed validation/restore must not silently replace good data.
- Directory-style import is create-only/repeatable under the adopted validation/privacy rules; it is not a hidden data merge or destructive overwrite mechanism.
- Working visits and correction drafts are durable state. Recovery architecture must account for them rather than assuming only finalized records matter.
- Standard Android/OS backup or OEM transfer must not be presented as the complete ServiceLoop recovery contract. Configure business data/files consistently with the adopted app-level recovery model.

## B-007 — Service obligation, history, and lifecycle semantics

- Adopt C's recurrence rule: the next due date is derived from the qualifying completion date plus the plan interval, subject to the adopted explicit override/correction rules.
- Each active plan has one current obligation identity at a time. Consuming/finalizing that obligation must be exactly-once even across retries.
- `Performed` completes the eligible captured recurring obligation automatically, with the normal next due date calculated from the actual service date and captured interval. `Partly performed` requires an explicit **Fulfill** or **Keep due** choice; `Not performed` never fulfills and requires a reason. Outcome and fulfillment remain separately persisted where historical/output semantics require it, but they are not always separate technician decisions.
- One visit has one site and one actual service date. Multiple machines/plans may be handled in the visit, and partial/unperformed work preserves the appropriate outstanding obligation.
- Master-data/template edits must not silently rewrite historical inspection/report meaning. Finalized history uses immutable snapshots/versioned correction rather than in-place semantic mutation.
- Use C's conservative archive/retire/move rules plus adopted FC-03: resolve named blockers individually; do not cascade-cancel or silently retarget dependent history/drafts.
- For the corrective-task description ambiguity: the public finding is the required corrective description. A follow-up itself requires title/date/context and may also carry private planning notes; do not invent a second mandatory public description field.

## B-008 — Pilot and report validation boundary

- Keep the fixed general customer report design as the implementation target unless later amended.
- The real-technician pilot is a **late-stage external validation gate**, not a blocker for SL-4/SL-5 implementation.
- Before release preparation is considered product-valid, a **pilot-ready Romanian-localized build with no known ordinary-workflow placeholders** must be evaluated by at least one reachable real technician/trade user through a representative service workflow and customer report.
- Agreement between AI agents, automated tests, emulator evidence, or owner/developer review is not a substitute for that external pilot evidence.
- Localization infrastructure, Romanian UI/report copy, and ordinary functional completeness are prerequisites for a meaningful Romanian pilot; the pilot should occur early enough in the final hardening/release phase that terminology, workflow, report, and usability findings can still be incorporated.

## B-009 — Inline inspection finding description

- For an ordinary inspection **Issue found** response, edit and view the primary public finding description directly beneath the inspection item rather than requiring navigation to another screen.
- The field is a compact multiline editor by default with a visible control that expands the same field for long descriptions and collapses it again without changing its content.
- This inline description remains the public corrective description source under B-007. Do not invent a second mandatory public description; later richer finding metadata, photographs, disposition, corrective-task planning, and lifecycle features remain permitted where adopted.

## B-010 — Expanded multiline editing pattern

- Keep the ordinary **Issue found** description compact and inline by default.
- Its expand action opens the same text in a dedicated full-page or modal editing surface for long-form entry rather than merely making the inline box taller.
- Longer multiline inputs use one reusable editing component/pattern with an icon-only bottom-right expand affordance where appropriate. The glyph remains modest (roughly mid-20dp) inside a comfortably accessible touch target (roughly 48dp); it must not use a field-covering text-labelled button.
- This pattern is implemented in SL-3 and does not change explicit parent Save semantics.

## B-011 — SL-3 owner-review daily-operations corrections

- The Customers root contains peer **Customers**, **Sites**, and **Equipment** registers; a customer detail contains **Sites** and **Equipment**, defaulting to Sites.
- A cancelled booking may be restored only conservatively into the same Visit after transaction-local validation that every captured recurring obligation remains active, current, unconsumed, and unclaimed. Restoration reacquires all claims or none, preserves cancellation history, and does not fulfill or alter a due date.
- The selected visit date determines the visually primary action: future Book, today Start, and past Record past.

## B-012 — Experimental asynchronous Dispatch scope

- The owner separately authorized an **isolated experimental Dispatch prototype** as the narrow exception contemplated by B-004. This did not by itself amend the adopted product baseline or authorize merge into the main development line.
- The experiment is strictly asynchronous and file-based: local coordinator definitions → portable `.slwork` package → Android sharing → technician import/local work → customer PDF sharing back. It may include local Technician identities, Teams/leaders, item-level assignments, generations, documentation handoff, batch packages, and coordinator bookkeeping where implemented on the prototype branch.
- The experiment does **not** authorize a backend, accounts/login, cloud/live synchronization, push dispatch, chat, presence, centralized report ingestion, automatic cross-device conflict resolution, or a shared central database.
- B-012 remains the historical scope boundary for the prototype phase; B-015 records the later owner decision to adopt the completed asynchronous Dispatch design for product integration.

## B-013 — Dedicated pre-pilot UI/UX milestone

- After functional completion/hardening and before the B-008 real-technician pilot, ServiceLoop requires a **dedicated whole-product UI/UX pass**. It must not be reduced to incidental styling during feature implementation or deferred until release preparation.
- The pass must establish a coherent finished-product presentation across the app: information hierarchy, task clarity, interaction consistency, navigation/action hierarchy, reusable components, typography, spacing/density, colors and semantic state usage, light and dark themes, loading/empty/error states, accessibility, contrast, and touch targets.
- The objective is to remove the current semi-default/semi-incremental Compose appearance and make the pilot build feel like one deliberately designed product. The pilot should evaluate that coherent build rather than spend its feedback budget on presentation problems already known before external testing.

## B-014 — Durable Working inspection response drafts

- Working inspection response modes retain their own saved detail drafts. Switching disposition is non-destructive: Issue-found descriptions, Not-applicable reasons, and text/number Values survive switching and ordinary switching requires no destructive confirmation.
- Only the currently selected disposition and its applicable detail determine checklist validity, finalization, and customer-facing history/report content. Inactive drafts remain Working-state convenience data and do not satisfy or leak into the active answer.
- Selecting Not applicable does not inject a canned reason; its reason is technician-editable and remains subject to the existing active-answer validity rules.

## B-015 — Asynchronous Dispatch adopted for product integration

- After implementation, technical verification, rendered review, system-chooser validation, and owner hands-on review through `prototype/dispatch-v2` commit `813fed417330fc7d3e5e7bce68ced29edd6dfb23`, the owner approved the asynchronous file-based Dispatch module for **ServiceLoop product integration**.
- This adoption supersedes only B-012's temporary "experimental/not product baseline" status. The hard architectural boundary remains unchanged: no backend, login/accounts, cloud/live synchronization, push dispatch, chat/presence, centralized report ingestion, automatic cross-device conflict resolution, or shared central database.
- Adopted Dispatch semantics include stable local Technician identity; checksum-valid human-friendly IDs for new installations with legacy-ID compatibility; `.sltech` identity sharing; coordinator Technician/Team/leader directory; list-first Outbox; stable Visit/item dispatch identities; item-level assignments and Everyone semantics; leader visibility; per-Visit generations; batch `.slwork` export/import; Draft/Dispatched/Concluded coordinator bookkeeping; safe recipient-scoped import/update/withdrawal; documentation handoff; participation completion; parallel independent technician reports; office/customer PDF sharing through Android system handoff; and complete local backup/recovery coverage.
- Dispatch remains asynchronous and local-first. "Dispatched" means a usable package artifact was created, not delivered or received; documentation handoff does not prove acceptance; coordinator conclusion does not alter technician devices.
- Product adoption authorizes a **deliberate integration/banking milestone**, not a blind merge of the prototype branch. Integration must reconcile the verified SL-4 branch and the Dispatch/Room-v10 lineage, preserve migration/recovery integrity, run the full regression gate, and establish one authoritative development HEAD.
- The current Dispatch interaction structure is adopted functionally, but its prototype visual styling is **not** a new whole-app visual baseline. B-013 remains mandatory before the real-technician pilot.

## B-016 — Optional one-way Android Calendar projection

- ServiceLoop may optionally project eligible timed **Booked** Visits into one user-selected writable Android Calendar using the local `CalendarContract` provider. Integration is Off by default and Calendar permissions are requested only after deliberate user action.
- ServiceLoop remains authoritative. Calendar edits never alter ServiceLoop scheduling or business state; Calendar failure never rolls back Book, reschedule, cancel, Start, Finalize, Dispatch import/update, or withdrawal.
- Calendar selection, external event IDs, Visit-event bindings, suppression, fingerprints, pending provider work, and provider state are **device-local** and excluded from portable ServiceLoop backup/restore. They are dataset-scoped and reset Off on restore, dataset replacement, or erase without deleting external events.
- Only timed Booked local Visits qualify automatically. Date-only bookings do not become all-day events. Events use the stored appointment instant/ZoneId, a fixed 60-minute display block, restrained operational content, no Calendar reminder rows, and no private notes/findings/checklists/contacts/Dispatch instructions/report content/opaque Technician IDs.
- One local Visit has at most one managed event link. ServiceLoop updates the same linked event after relevant ServiceLoop changes, does not import external edits, and treats externally deleted events as **missing** until deliberate Recreate. Per-Visit Remove creates local suppression so automatic reconciliation does not recreate it; Add clears suppression.
- Global Disable keeps existing external events and bindings but stops active synchronization. Changing the preferred Calendar affects new/unlinked events only; existing links remain tied to their original Calendar.
- Future linked events are deleted when a Visit becomes **Canceled** where provider access permits; deletion failure is retained as provider work needing retry and never changes the committed ServiceLoop state. **Working** and **Completed** events remain as historical appointment evidence.
- Calendar reconciliation observes relevant Room changes (`working_visits`, `customers`, `sites`) plus startup/resume/manual triggers; it uses no polling service. Dispatch import, generation update, and assignment withdrawal therefore reconcile automatically through persisted local Visit changes.
- This is not Google Calendar API integration: no Google OAuth, backend, network account logic, push/calendar sync engine, two-way scheduling, attendees, or cloud-delivery claim is authorized.

## B-017 — Localization follows the whole-product UI overhaul

- Romanian localization is deliberately deferred until **after** the B-013 whole-product UI/UX overhaul because user-visible wording, component structure, labels, hierarchy, and interaction copy are not considered fully settled before that pass.
- The final pre-pilot sequence is therefore: **functional completion/hardening → B-013 whole-product UI/UX overhaul → Romanian localization/final copy freeze → B-008 real-technician pilot**.
- The functional-completion milestone immediately preceding B-013 must remove known ordinary-workflow functional placeholders and harden the implemented product, but it must **not** perform broad string-resource migration, Romanian translation, report localization, notification localization, or Calendar-copy localization.
- B-008 still requires a Romanian-localized pilot-ready build. Deferring localization changes sequencing only; it does not remove the localization requirement or lower the pilot gate.

## B-018 — `master` is the single authoritative development branch

- The owner designated `master` as the single authoritative ServiceLoop development baseline after the Stage-5 functional freeze.
- `master` is advanced only through explicitly accepted/reviewed work or direct owner-authorized documentation/baseline maintenance. New substantial milestone branches start from the current `master` HEAD unless the owner explicitly authorizes another base.
- Milestone/prototype branches are temporary implementation/review vehicles rather than long-lived authorities. After their accepted work is represented on `master`, they may be deleted to keep repository topology simple.
- Historical verification meaning is preserved by commit SHA and, where useful, archive tags; keeping obsolete branches is not required merely to preserve history.

## B-023 — Unified Visit lifecycle and Coordinator cancellation

- The current Technician Visit lifecycle is exactly `BOOKED`, `WORKING`, `COMPLETED`, `CANCELED`; legacy finalization/participation/withdrawal spellings are migration or compatibility inputs only.
- Completed does not imply a local final record or PDF. Documentation ownership remains item-level, and **Complete visit** may complete a local Visit without fabricating a record, report, recurrence effect, or Coordinator status return.
- Coordinator Outbox states are Draft, Dispatched, Canceled, and Concluded. Cancellation reasons are durable and required. A canceled Draft is retained but not exported; a canceled Dispatched Visit requires a newer package export, without claiming delivery or receipt.
- `.slwork` v2 remains readable as active transport; new exports are v3 with active/canceled lifecycle and reason in the material hash. Generation and import rules are monotonic and retry-safe.
- Room 11→12 normalizes legacy Visit states and adds cancellation provenance fields without destructive fallback. Coordinator/assignment cancellation preserves local work/evidence and does not permit ordinary local Restore.
- B023 explicitly excludes Stage C, broad localization, backend/live sync, status-return transport, and changes to the frozen UI reference package.

## B-013 — Output, identity, and inspection transport pass

- The B013 milestone branch may replace unreleased `.sltech`, `.slinsp`, and `.slwork` transport formats with the current version required by the pass; those decoders are current-only and do not retain pre-release compatibility branches.
- `BusinessProfile.technicianName` is the authoritative actual report identity. Dispatch identity retains its stable installation ID and an optional team designation/badge as separate data; the Member screen does not edit the actual report name.
- Work packages carry immutable inspection snapshots at package level and bind imported work to local immutable snapshots. Reusable template edits never rewrite issued Visit/report meaning.
- Output producers, public/private boundaries, provenance, and acknowledgement semantics are recorded in `docs/project/B013_OUTPUT_ARTIFACT_MATRIX.md`.

## B-025 — Service Flow Redesign Bundle 1 foundation

- Bundle 1 adopts durable Working service-flow autosave foundations: raw edit buffers are stored separately from canonical facts, writes are versioned against stale async work, and raw buffers never become final history/report content.
- Inspection completeness is derived from the current immutable checklist snapshot and active Working responses. `checklistReviewed` remains Room compatibility/cache state only; manual “Mark checklist reviewed” is no longer the authority for readiness.
- Completion semantics are outcome-dependent: eligible `PERFORMED` work automatically fulfills the captured current obligation; eligible `PARTLY_PERFORMED` work keeps fulfillment nullable until the technician explicitly chooses Fulfill or Keep due; `NOT_PERFORMED` persists false and requires a reason. Only finalized true fulfillment consumes the captured obligation and advances recurrence.
- Eligible true fulfillment calculates the next due date immediately from actual service date and captured interval. A different confirmed date is a deliberate override requiring a nonblank reason; there is no separate second confirmation of the calculated date.
- Response mutation recomputes derived completeness in the same transaction. An incomplete checklist blocks Review/finalization for every outcome but does not erase an automatic Performed fulfillment or an explicit Partly decision. Finalization recomputes directly and flushes draft work before creating final history.
- This amendment supersedes the earlier outcome/fulfillment interaction and blanket checklist-gate wording for B-025: Performed is automatic, Partly is explicit, Not performed is non-fulfilling, and checklist completeness is required before Review/finalization for all outcomes. Immutable snapshots, B-014 Working response drafts, recurrence safeguards, finalization, correction/history, and the existing B-009/B-010 inline editing decisions remain in force.

## B-026A — Flexible ad-hoc work and one-time customer foundation

- Customers now carry strict `STANDARD` or `ONE_TIME` identity; existing creation/import paths remain `STANDARD` until the separately scoped B026B UI.
- Working/final work subjects are strict `SITE` or `EQUIPMENT`. Known Equipment freezes identity snapshots; unidentified Equipment may carry only a trimmed <=500-character description; SITE and unidentified work cannot carry recurring plan/obligation effects.
- Recurring plans remain Equipment-backed and Standard-customer-only. Room schema v15, recovery compatibility 9–15, current CSV projections, history grouping, and public-report omission rules preserve this meaning. Current `.slwork` v4 remains field-stable and rejects future-shaped flexible dispatch rows on export.
- B026A contains the persistence/domain/output foundation only. B026B customer/work-entry UI is not authorized by this amendment.

## B043 — Five-role workspace, field delegation, and coordinator conclusion

- The product roles, in exact visible order, are **Solo** — “I manage and perform my own service work.”; **Subcontractor** — “I manage my own work and also receive tasks from others.”; **Employee** — “I work on tasks assigned by a coordinator.”; **Team Leader** — “I perform assigned work and coordinate other technicians.”; and **Coordinator** — “I plan, assign, and oversee work for technicians.”
- The capability matrix is authoritative: Solo `Register Yes / Receive No / Perform Yes / Delegate No / Coordinator tools No / Conclude others No`; Subcontractor `Yes / Yes / Yes / No / No / No`; Employee `No / Yes / Yes / No / No / No`; Team Leader `Yes / Yes / Yes / Yes / Yes / Yes`; Coordinator `Yes / No / No / Yes / Yes / Yes`.
- There is no “Hide Register” preference. Employee has no Register root or ordinary master-data management workflows but may see operational Visit context. Team Leader has full hybrid Register, receiver, performer, coordinator, delegation, and conclusion capability. Coordinator has Register and coordinator/conclusion capability but no technician field-work execution.
- Team Leader and Coordinator may conclude work delegated to others. Conclusion is administrative coordination-side closure and is not technician completion, evidence rewrite, final-history replacement, recurrence fulfillment, PDF generation, report delivery, or proof of central acceptance.
- Existing stored `MEMBER` maps explicitly to **Subcontractor**, not Solo. This is preference compatibility; no Room migration is required merely for the role value.
- Visit Maps uses the captured Visit site/address snapshot where available, with external Intent handoff only, no Maps SDK or location permission, no ServiceLoop state mutation, and no implication of contact, attendance, completion, or delivery.
- B043 remains local-first and file-based with no accounts, backend, cloud/live sync, live reassignment, web coordinator, generic security/RBAC boundary, or second master-data database. It supersedes conflicting older role/workspace statements while preserving historical evidence and unchanged Dispatch transport semantics.

## B048 — Team and trusted local ServiceLoop exchange

- Home adopts **Dashboard | Agenda | Team**. Team contains role and stable identity, Trusted IDs, ordinary import/export/verification entry points, and coordinator tools only when the existing capability matrix allows them. Settings retains application configuration and recovery/history, not daily role or exchange controls.
- Every workspace has one stable local ServiceLoop ID backed by existing `technician_identity`. Display **Coordinator ID** for Coordinator and **Technician ID** for field roles; role/business-name changes do not regenerate it. An ID is a local identification/routing value and is not proof of possession or cryptographic authentication.
- Trusted IDs are device-local configuration, are included in Recovery, and are not FULL_WORKSPACE data. Own identity is implicitly trusted and cannot be redundantly registered. FULL_WORKSPACE replacement preserves identity, Trusted IDs, reminder preferences, and recovery metadata.
- Newly exported `.slsync` files use envelope v2 with a required normalized `exporterId` for FULL_WORKSPACE, WORK_ASSIGNMENT, and TEMPLATE_SHARE. Structurally valid v1 files remain verifiable but are not importable. Each mutation path rechecks that the source ID is own or trusted immediately before mutation. Dispatch v5 and inspection-template payload contracts remain unchanged.
- FULL_WORKSPACE `register` section v2 carries repeatable customer contacts; `team` section v2 carries technician notes. Trusted IDs and local identity are never exported in the workspace sections. Room advances 17→18; Recovery package schema advances 16→17.
- B048 adds a Service photo management workspace for working evidence, including batch report visibility, confirmed batch deletion, and a fit/zoom/pan viewer. Batch photo metadata updates share finalization serialization. Finalized evidence and reports remain immutable.

## B049 — Unified work exchange, reporting, Export Center, and Team IA

- The detailed adopted contract and acceptance checklist are in `B049_UNIFIED_WORK_EXCHANGE_REPORTING_EXPORT_CENTER_AND_TEAM_IA.md`. B049 remains in progress on its milestone branch; target versions are app 1.3.0/code 5, Room v19, Recovery v18, FULL_WORKSPACE register v3, and `.slsync` envelope v2.
- Split Visit creation/booking from technician field execution. Coordinator may create/book and assign Visits but may not Start or document Service work. Team Leader may do both. Employee may execute assigned work but may not create arbitrary Visits. New assigned work must converge on canonical `working_visits` and `work_items`, with Dispatch as a linked overlay; legacy unlinked Dispatch remains usable.
- Adopt WORK_RESULT v1 as a trusted, one-issuer `.slsync` round trip with stable logical result/revision identities, item-level partial receipts, immutable remote provenance, stale-generation review, and exactly-once recurrence. Adopt DATA_TRANSFER for new inspection-template exchange while retaining legacy TEMPLATE_SHARE reading. Adopt readable scoped Export Center, immutable aggregate customer reports with frozen effective source revision IDs, and safe original-image retention with a validated derivative prerequisite. None of these architecture targets is implied to be implemented by this decision record alone.

## 2026-09-24 — Persistence repair execution amendment

For the authorized persistence repair candidate, WORK_RESULT and DATA_TRANSFER PERFORMED_WORK retain their v1 readers and emit v2 source records. The v2 record carries immutable source identity, chronology, captured work/recurrence/follow-up/photo facts, and explicit private/public meaning. Room advances additively from v19 to v20; Recovery writes schema 19 and explicitly reads 9–19. The exact candidate contract, compatibility rules, tests, and evidence limits are recorded in [PERSISTENCE_DATA_CONTRACT_BASELINE.md](PERSISTENCE_DATA_CONTRACT_BASELINE.md) and [PERSISTENCE_REPAIR_ACCEPTANCE_LEDGER.md](PERSISTENCE_REPAIR_ACCEPTANCE_LEDGER.md).

This amendment authorizes implementation and candidate verification on `codex/persistence-web-foundation-audit`. It does not claim independent review, owner acceptance, pilot readiness, release, or integration into `master`/B049.


## 2026-09-25 — B050 pre-release clean-slate baseline

- The owner authorizes a clean-slate pre-release release candidate. No prior ServiceLoop business rows, Recovery backups, or unreleased exchange packages need to remain readable. Current Room, Recovery, and unreleased transport formats reset to one strict version 1 contract; retired pre-release versions are rejected without normalization, backfill, or chronology inference.
- Room is schema 1 with a fresh-create schema export only and no migration chain, migration registration, or destructive migration fallback. Recovery container and database schema are both version 1 and must match the current Room table, column, and value shapes exactly.
- `.slsync`, each FULL_WORKSPACE section, DATA_TRANSFER metadata/families, WORK_RESULT body/section, Dispatch WorkPackage, and technician identity `.sltech` are current-only v1. Every `.slsync` mutation file requires a valid exporter ID. Inspection-template sharing is encoded as DATA_TRANSFER v1; TEMPLATE_SHARE is not a supported purpose.
- WORK_RESULT v1 is the complete adopted source record: source visit/work/revision identity and `recordedAt` are mandatory; source snapshots, payloads, and fingerprints are strict v1; package `generatedAt` never substitutes for result chronology. Final follow-up and imported-result source snapshots are durable required values where current writers guarantee them.
- The app package/version code does not change for B050 because no release-version amendment was adopted. This decision authorizes the exact B050 branch, repository changes, verification, clearing only `com.v16studio.serviceloop` on the canonical `Pixel 10a ServiceLoop` AVD before fresh-state validation and once after instrumentation for a clean final launch. It does not authorize AVD wipe/recreation, publishing, merging to `master`, or release.
- This clean-slate contract supersedes conflicting pre-release version/compatibility statements in B048/B049 and the 2026-09-24 persistence-repair execution amendment. Their implementation and evidence descriptions remain historical at their named revisions; they are not accepted as the B050 current format.
