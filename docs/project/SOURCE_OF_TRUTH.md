# ServiceLoop — Source of Truth

**Status:** Adopted for implementation on 2026-09-06; current through 2026-09-09.

## Authority order

1. Explicit owner decisions and approved amendments recorded in `docs/project/BASELINE_DECISIONS.md`.
2. `docs/product/ServiceLoop_Conceptual_App_Map_v0_1.md` — adopted semantic/functional baseline.
3. `docs/product/ServiceLoop_Conceptual_UI_UX_Design_v0_1.md` — adopted UI/UX baseline, including the adopted FC-01/FC-02/FC-03 additions.
4. `docs/product/ServiceLoop_UI_UX_Action_Coverage_v0_1.md` — adopted traceability/coverage companion to the UI/UX baseline.
5. Current code, tests, and verification evidence establish what is actually implemented; divergence from the documents is not automatically a new requirement.
6. `docs/reference/ServiceLoop_Complete_Functional_App_Map_v0_1.md`, research/context, and the historical functional-map prompt are reference/rationale only unless a later owner amendment explicitly adopts a named policy from them.

Do not silently merge competing policies from the conceptual and complete functional maps. When a conflict is not resolved by the adopted baseline/amendments, stop only if it materially blocks the authorized work and bring one recommended resolution to the owner.

Matching HTML editions are reading formats and are intentionally not retained in the repository as independent requirements.

## Dispatch adoption and integrated development line

B-015 adopts the completed asynchronous file-based Dispatch design for ServiceLoop product integration after owner hands-on review. The adopted design remains strictly local-first/file-based and does not authorize a backend, accounts/login, live/cloud synchronization, push dispatch, chat, presence, a shared central database, centralized report ingestion, server acknowledgement, or automatic cross-device conflict resolution.

SL-4 and Dispatch were deliberately reconciled and banked on `codex/integrate-sl4-dispatch`. SL-5A subsequently advanced the authoritative technical development line to:

- branch: `codex/sl-5-time-reminders`
- SL-5A implementation/verification checkpoint: `d133a97171d05d4110204e62c69e423c49ba3bd5`
- Room schema: v11

This branch is now the authoritative technical starting point for subsequent Stage-5 work. `master` and the earlier milestone branches remain protected historical/reference heads rather than current implementation authority.

`docs/internal/DISPATCH_PACKAGES_PROTOTYPE.md` remains the detailed implementation/verification reference for adopted Dispatch semantics, despite its historical filename.

## Current accepted / verified implementation state

- **SL-1:** owner accepted.
- **SL-2:** owner accepted on 2026-09-06. Accepted user-facing implementation/review state: `d97a8c0013dcea924d91ace993a1325ac16cf5b3`.
- **SL-3:** owner accepted on 2026-09-08. Accepted production implementation/review state: `ad078faae3c73faa2a9bb02dd1251b1033237399`.
- **SL-4:** the complete History/recovery implementation is independently Phase-B verified and **VERIFIED AND BANKED INTO THE INTEGRATED DEVELOPMENT LINE**. This does not retroactively claim a separate earlier standalone owner-acceptance event for SL-4.
- **Dispatch:** owner approved under B-015 and deliberately integrated/banked into the authoritative technical line.
- **B-014 durable Working inspection-response drafts:** adopted core behavior and retained through current Room v11.
- **SL-5A — time-aware work state and local reminders:** implemented and independently reviewed at `d133a97171d05d4110204e62c69e423c49ba3bd5`. The milestone implements business-date invalidation without polling, adopted S32 reminder settings, approximate daily summaries/appointment reminders, Room-v11 preference persistence, recovery semantics and platform reconciliation. Actual system notification delivery was deliberately not exercised on the canonical dataset because local delivery and Android permission remained Off; no delivery claim is made.
- **B-008 pilot:** still outstanding. The product is not product-valid for release preparation until a pilot-ready Romanian-localized build with no known ordinary-workflow placeholders is evaluated by at least one real technician/trade user.

## SL-5A verification boundary

The SL-5A checkpoint established:

- an injectable business-time/date signal that recomputes date-derived Home/Work state at the next business-local midnight and on foreground/time/zone invalidation, without continuous polling or manufactured database writes;
- one shared persisted due-soon horizon for Home, Due services and reminder calculations;
- Room-v11 reminder preferences with adopted defaults and device-local reminder-delivery intent kept separate from portable backup state;
- stable Work-summary and Appointment-reminder notification channels plus contextual Android notification permission handling;
- one-shot approximate `AlarmManager.setWindow` scheduling without exact-alarm permission, foreground service or battery-exemption demands;
- current-state revalidation, privacy-safe notification text, duplicate-summary suppression, stale-appointment suppression and dataset-scoped PendingIntent identity;
- reboot/package-replacement/time/timezone/process reconciliation;
- restore semantics that preserve reminder preferences while resetting local delivery Off;
- retained Room v1→v11 migration coverage and schema-9/10 backup compatibility.

Recorded final evidence at the implementation checkpoint includes 184 host unit tests, 16 focused instrumentation tests, one preserved-canonical due-services regression, retained migration/FK and recovery coverage, debug/debug-Android-test/lint/release builds, `git diff --check`, explicit install-r on the canonical AVD and non-destructive rendered Home/Reminders inspection.

## Current forward sequence

1. **Remaining Stage 5 — functional product completion/hardening**
   - finalize and implement optional Calendar integration semantics;
   - Romanian localization and report/UI copy;
   - remaining ordinary-workflow cleanup and placeholder removal;
   - larger-data, recovery and platform hardening;
   - production hardening of adopted Dispatch without expanding its no-backend boundary.

2. **Dedicated whole-product UI/UX milestone — B-013**
   - implement the forthcoming all-encompassing visual/UI design authority across ServiceLoop only after Stage-5 functional work is stable;
   - establish reusable components/primitives, hierarchy, typography, spacing/density, semantic colors, light/dark themes, loading/empty/error states, accessibility, contrast and touch targets;
   - the current semi-default/incremental Compose presentation is not the final visual baseline.

3. **B-008 real-technician pilot** on the coherent Romanian pilot-ready build.
4. **Pilot findings / final hardening.**
5. **Release preparation and submission.**

Read `IMPLEMENTATION_STATE.md` for the concise current implementation summary and remaining known gaps. Read milestone coverage files and the Dispatch implementation reference for detailed verification evidence.
