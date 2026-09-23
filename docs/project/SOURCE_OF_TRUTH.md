# ServiceLoop — Source of Truth

**Status:** Adopted authority order from 2026-09-06; implementation-state pointers updated 2026-09-22.

## Authority order

1. Explicit owner decisions and approved amendments recorded in `docs/project/BASELINE_DECISIONS.md`.
2. `docs/product/ServiceLoop_Conceptual_App_Map_v0_1.md` — adopted semantic/functional baseline.
3. `docs/product/ServiceLoop_Conceptual_UI_UX_Design_v0_1.md` — adopted UI/UX baseline, including the adopted FC-01/FC-02/FC-03 additions.
4. `docs/product/ServiceLoop_UI_UX_Action_Coverage_v0_1.md` — adopted traceability/coverage companion to the UI/UX baseline.
5. Current code, tests, and verification evidence establish what is actually implemented; divergence from the documents is not automatically a new requirement.
6. `docs/reference/ServiceLoop_Complete_Functional_App_Map_v0_1.md`, research/context, and the historical functional-map prompt are reference/rationale only unless a later owner amendment explicitly adopts a named policy from them.

Do not silently merge competing policies from the conceptual and complete functional maps. When a conflict is not resolved by the adopted baseline/amendments, stop only if it materially blocks the authorized work and bring one recommended resolution to the owner.

Matching HTML editions are reading formats and are intentionally not retained in the repository as independent requirements.

## AI development process authority

Product authority and AI-development process authority are intentionally separate.

For orchestration, Codex delegation, correction prompts, independent-review continuity, and new-thread handoffs, use:

1. `AGENTS.md` — persistent repository execution rules and environment/Git/verification constraints.
2. `docs/project/DELEGATED_AI_DEVELOPMENT_AND_ORCHESTRATION_v1_0.md` — owner/orchestrator/Codex responsibility model.
3. `docs/project/AI_CODEX_PROMPT_AND_HANDOFF_STANDARD.md` — mandatory execution-level prompt and handoff authoring standard.

Before writing any substantial Codex prompt or continuation handoff, the orchestrator must read the prompt/handoff standard. It is process authority only; it does not override product requirements or later owner decisions.

A stronger implementation model is not a reason to reduce prompt detail. Prompts should be executable by the least expensive intended model, normally Luna High, without forcing that model to reconstruct avoidable architecture/product decisions.

For the current branch/SHA/version/milestone pointer, read `IMPLEMENTATION_STATE.md` and verify Git state directly. Do not infer current implementation state from an older milestone paragraph in this file.

## Accepted baseline and active implementation line

SL-4 and Dispatch were deliberately reconciled/banked; SL-5A added time-aware work state and local reminders; SL-5B added the adopted one-way Android Calendar projection; SL-5C completed the final broad functional hardening pass.

Accepted/banked baseline:

- authoritative branch: `master`
- SL-5C functional-freeze production checkpoint: `bf55b0bd027fa25c48fc2dfd930d257688088ecb`
- Room schema at that historical SL-5C checkpoint: v11

`master` remains the protected owner baseline, currently `1fd51131b040ab62af3874c1106615b75f3008fe`. B043/B044 and the later milestone descriptions below are historical implementation evidence; they are not the current-HEAD pointer. At the B048 checkpoint from which the AI-process documentation update was created, the latest implemented review branch was `codex/b048-team-trust-ux-hardening` at `cadc0668fe2ba5172d17a86a157323303a71d83f`, with app `1.2.0` / code `4`, Room v18, Recovery schema v17 and `.slsync` envelope v2. B049 work is a later separately authorized milestone and must be verified from current Git/docs rather than assumed from this snapshot. See `IMPLEMENTATION_STATE.md` for the maintained current pointer. The historical SL-5C figures below describe that checkpoint only.

B-015 remains the adopted asynchronous Dispatch boundary. B-016 records the optional one-way Android Calendar projection semantics. B-017 explicitly defers localization until after B-013; UI corrections may continue during implementation and review. `docs/internal/DISPATCH_PACKAGES_PROTOTYPE.md` remains the detailed implementation/verification reference for adopted Dispatch semantics despite its historical filename.

## Historical accepted state and active branch context

- **SL-1:** owner accepted.
- **SL-2:** owner accepted on 2026-09-06. Accepted user-facing implementation/review state: `d97a8c0013dcea924d91ace993a1325ac16cf5b3`.
- **SL-3:** owner accepted on 2026-09-08. Accepted production implementation/review state: `ad078faae3c73faa2a9bb02dd1251b1033237399`.
- **SL-4:** complete History/recovery implementation independently Phase-B verified and **VERIFIED AND BANKED**.
- **Dispatch:** owner approved under B-015 and deliberately integrated/banked as product scope within the local-first/file-based boundary.
- **B-014:** durable Working inspection-response drafts remain adopted core behavior through the later schema versions.
- **SL-5A — time-aware work state and local reminders:** implemented/reviewed and retained on the current line.
- **SL-5B — optional Android Calendar integration:** implemented, corrected, source-reviewed and banked under B-016.
- **SL-5C — final functional completion/hardening:** implemented and reviewed at `bf55b0bd027fa25c48fc2dfd930d257688088ecb`. No known ordinary-workflow functional placeholder remains before B-013.
- **B-008 pilot:** still outstanding. Release preparation is not product-valid until a coherent finished-looking, Romanian-localized build is evaluated by at least one real technician/trade user.

## SL-5A retained boundary

The current line retains:

- injectable business-time/date invalidation at the next business-local midnight and on foreground/time/zone changes, without polling;
- one shared persisted Due-soon horizon for Home, Due services and reminders;
- Room-v11 reminder preferences with device-local reminder-delivery intent separated from portable recovery;
- approximate one-shot `AlarmManager.setWindow` scheduling, privacy-safe summaries/appointment alerts, duplicate/stale suppression and dataset-scoped PendingIntents;
- reboot/package-replacement/time/timezone/process reconciliation;
- restore semantics that preserve reminder preferences while resetting local delivery Off;
- retained Room v1→v11 migration coverage and schema-9/10 backup compatibility.

Actual system notification delivery remains unclaimed on the preserved canonical dataset because notification permission/local delivery were deliberately left Off during validation.

## SL-5B retained Calendar boundary

B-016 remains implemented as a one-way local projection using Android `CalendarContract`/Calendar Provider. Calendar selection and managed event identity live in `noBackupFilesDir`, are scoped to the current ServiceLoop dataset ID, are excluded from portable backup, and reset Off on dataset replacement/erase without deleting old external events.

The current line retains Off-by-default integration, deliberate `READ_CALENDAR` / `WRITE_CALENDAR` permission request, writable-calendar selection, timed-Booked-only creation, exactly one managed link per Visit, same-event update, external-delete → Missing, deliberate Recreate, per-Visit Remove/suppression/Add, historical retention for Working/Finalized/Participation-complete Visits, Cancelled/`DISPATCH_WITHDRAWN` deletion where possible, and Room-driven reconciliation over `working_visits`, `customers`, and `sites`.

Real provider mutation remains **NOT RUN** because no safely disposable writable Calendar was established on the canonical AVD. No Google/cloud synchronization claim is made.

## SL-5C functional-freeze boundary

SL-5C completed the final broad functional audit before B-013. The checkpoint:

- removed stale user-facing Experimental wording from adopted Coordinator tools;
- removed an unreachable legacy foundation-placeholder route;
- made root refresh failures visible on Home, Work, and Customers with a functioning Retry while preserving the last durable result;
- hardened Calendar device-state file replacement against transient/concurrent Windows contention using serialized access, unique temporary files, bounded retries, and last-good-file preservation;
- added larger-data regression coverage spanning 250 Customer/Site/Equipment/Plan/Obligation branches and 120 Booked Visits across Home, registers, Due services, Visits, and search;
- retained release fixture seeding as no-op and preserved debug/release separation;
- retained Room v11 unchanged;
- preserved SL-4/History/recovery, B-014, Dispatch, SL-5A reminders/time, and SL-5B Calendar semantics.

Reported evidence at `bf55b0bd027fa25c48fc2dfd930d257688088ecb` includes 201 passing unit tests, 19 passing instrumentation tests, retained Room v1→v11 migration/FK coverage, Dispatch/reminder/Calendar regressions, larger-data hardening, debug/debug-Android-test/lint/release builds, `git diff --check`, and a non-destructive preserved-dataset canonical AVD review.

The supported statement after SL-5C is:

> There is no known ordinary-workflow functional placeholder or unfinished adopted functional path that should be fixed before the B-013 whole-product UI/UX overhaul begins.

This is a **functional freeze**, not a claim that the current interface/copy is final, localized, pilot-valid, or release-ready.

## Forward sequence recorded at SL-5C

1. **B-013 — dedicated whole-product UI/UX overhaul**
   - apply the forthcoming all-encompassing visual/UI design authority to the functionally frozen product;
   - establish reusable components/primitives, hierarchy, task clarity, typography, spacing/density, semantic colors, complete light/dark themes, loading/empty/error states, accessibility, contrast and touch targets;
   - the current incremental/default Compose presentation is explicitly not the final visual baseline.

2. **Localization / final copy freeze — B-017 sequencing**
   - after B-013 stabilizes final labels, helper copy, dialogs, component structure and interaction wording;
   - then implement proper Android localization infrastructure and Romanian UI/report/notification/Calendar/system-handoff copy.

3. **B-008 real-technician pilot** on the coherent finished-looking Romanian-localized build.
4. **Pilot findings / final hardening.**
5. **Release preparation and submission.**

Read `IMPLEMENTATION_STATE.md` for the concise current implementation summary and remaining validation boundaries. Read milestone coverage files, the Dispatch reference, and `B026_FLEXIBLE_AD_HOC_WORK_AND_ONE_TIME_CUSTOMERS.md` for the B026A persistence/output boundary and detailed semantics.

## B043 — Later owner amendment: five-role workspace and field delegation

B043 is the later owner amendment governing the `TeamRole` taxonomy, Register availability by role, technician receiver roles, the Team Leader hybrid role, Coordinator's restriction from technician field-work execution, and coordinator/Team Leader conclusion semantics. It supersedes conflicting role/workspace statements in older documents while preserving their historical evidence and unchanged Dispatch package, transport, assignment, generation, and provenance semantics. B043 does not claim that its production implementation is complete.
