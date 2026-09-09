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

## Current authoritative technical development line

SL-4 and Dispatch were deliberately reconciled/banked, SL-5A added time-aware work state and local reminders, and SL-5B added the adopted device-local Android Calendar projection.

Current technical line:

- branch: `codex/sl-5-calendar`
- SL-5B implementation/correction checkpoint: `2f8faf466c7ffd960c362078abbc6de83cc9e722`
- Room schema: v11

This branch is now the authoritative technical starting point for the remaining Stage-5 work. `master` and earlier milestone branches remain protected historical/reference heads rather than current implementation authority.

B-015 remains the adopted asynchronous Dispatch boundary. B-016 records the optional one-way Android Calendar projection semantics. `docs/internal/DISPATCH_PACKAGES_PROTOTYPE.md` remains the detailed implementation/verification reference for adopted Dispatch semantics despite its historical filename.

## Current accepted / verified implementation state

- **SL-1:** owner accepted.
- **SL-2:** owner accepted on 2026-09-06. Accepted user-facing implementation/review state: `d97a8c0013dcea924d91ace993a1325ac16cf5b3`.
- **SL-3:** owner accepted on 2026-09-08. Accepted production implementation/review state: `ad078faae3c73faa2a9bb02dd1251b1033237399`.
- **SL-4:** complete History/recovery implementation independently Phase-B verified and **VERIFIED AND BANKED**.
- **Dispatch:** owner approved under B-015 and deliberately integrated/banked as product scope within the local-first/file-based boundary.
- **B-014:** durable Working inspection-response drafts remain adopted core behavior through current Room v11.
- **SL-5A — time-aware work state and local reminders:** implemented/reviewed at `d133a97171d05d4110204e62c69e423c49ba3bd5`; current line retains those semantics.
- **SL-5B — optional Android Calendar integration:** implemented, corrected and independently source-reviewed through `2f8faf466c7ffd960c362078abbc6de83cc9e722`; B-016 now records the adopted semantics.
- **B-008 pilot:** still outstanding. Release preparation is not product-valid until a pilot-ready Romanian-localized build with no known ordinary-workflow placeholders is evaluated by at least one real technician/trade user.

## SL-5A verification boundary

The current line retains:

- injectable business-time/date invalidation at the next business-local midnight and on foreground/time/zone changes, without polling;
- one shared persisted Due-soon horizon for Home, Due services and reminders;
- Room-v11 reminder preferences with device-local reminder-delivery intent separated from portable recovery;
- approximate one-shot `AlarmManager.setWindow` scheduling, privacy-safe summaries/appointment alerts, duplicate/stale suppression and dataset-scoped PendingIntents;
- reboot/package-replacement/time/timezone/process reconciliation;
- restore semantics that preserve reminder preferences while resetting local delivery Off;
- retained Room v1→v11 migration coverage and schema-9/10 backup compatibility.

Actual system notification delivery remains unclaimed on the preserved canonical dataset because notification permission/local delivery were deliberately left Off during validation.

## SL-5B Calendar boundary

B-016 is implemented as a one-way local projection using Android `CalendarContract`/Calendar Provider. Calendar selection and managed event identity live in `noBackupFilesDir`, are scoped to the current ServiceLoop dataset ID, are excluded from portable backup, and reset Off on dataset replacement/erase without deleting old external events.

Implemented behavior includes:

- integration Off by default;
- deliberate `READ_CALENDAR` / `WRITE_CALENDAR` permission request;
- writable Calendar enumeration and truthful unavailable-selection state;
- automatic creation only for timed Booked local Visits;
- fixed 60-minute Calendar display block using the stored appointment instant and ZoneId;
- restrained event content with private ServiceLoop fields excluded;
- exactly one managed link per Visit, same-event update after reschedule/public Site/Customer changes, external-delete → Missing, deliberate Recreate, per-Visit Remove/suppression/Add;
- global Disable retaining existing events/links and changing preferred Calendar affecting only new/unlinked Visits;
- future-event deletion for Cancelled / `DISPATCH_WITHDRAWN` Visits where provider access permits, with retryable `DELETE_PENDING` on failure;
- historical event retention for Working / Finalized / `PARTICIPATION_COMPLETE` Visits;
- Room-driven reconciliation observing only `working_visits`, `customers`, and `sites`, plus startup/resume/manual triggers; no polling/service;
- automatic Dispatch import creation, generation update of the same event ID, and assignment-withdrawal deletion through persisted local Visit changes.

Real provider mutation was **NOT RUN** because the canonical AVD had both Calendar permissions denied and no safely disposable writable Calendar was established. No Google/cloud synchronization or delivery claim is made. Deterministic fake-provider tests and bounded canonical state/UI validation establish the current technical checkpoint.

## Current forward sequence

1. **SL-5C — final functional completion/hardening**
   - Romanian localization for UI, reports, notifications and relevant user-visible system/share text;
   - remove remaining ordinary-workflow placeholders, stale prototype/experimental copy and obvious functional rough edges;
   - larger-data and bounded platform/recovery hardening where current implementation reveals concrete risk;
   - full functional-completeness inventory/gate without initiating the whole-product visual redesign.

2. **Dedicated whole-product UI/UX milestone — B-013**
   - implement the forthcoming all-encompassing visual/UI design authority after Stage-5 functional structure is frozen;
   - establish reusable components/primitives, hierarchy, typography, spacing/density, semantic colors, light/dark themes, loading/empty/error states, accessibility, contrast and touch targets;
   - the current incremental/default Compose presentation is not the final visual baseline.

3. **B-008 real-technician pilot** on the coherent Romanian pilot-ready build.
4. **Pilot findings / final hardening.**
5. **Release preparation and submission.**

Read `IMPLEMENTATION_STATE.md` for the concise current implementation summary and remaining known gaps. Read milestone coverage files and the Dispatch reference for detailed verification evidence.
