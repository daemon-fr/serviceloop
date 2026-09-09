# ServiceLoop — Implementation State

**Updated:** 2026-09-09

This file is the concise current-state summary. Detailed milestone evidence remains in Git history and focused coverage/tests; detailed Dispatch semantics remain in `docs/internal/DISPATCH_PACKAGES_PROTOTYPE.md`.

## Authoritative technical development line

Current development branch:

- branch: `codex/sl-5-functional-hardening`
- SL-5C functional-freeze checkpoint: `bf55b0bd027fa25c48fc2dfd930d257688088ecb`
- Room schema: v11

This branch is now the technical starting point for the B-013 whole-product UI/UX overhaul. `master` and earlier milestone branches are protected historical/reference heads rather than current implementation authority.

## Current accepted / verified state

- **SL-1:** OWNER ACCEPTED.
- **SL-2:** OWNER ACCEPTED. Accepted user-facing implementation/review state: `d97a8c0013dcea924d91ace993a1325ac16cf5b3`.
- **SL-3:** OWNER ACCEPTED. Accepted production implementation/review state: `ad078faae3c73faa2a9bb02dd1251b1033237399`.
- **SL-4:** IMPLEMENTED, independently Phase-B verified, and **VERIFIED AND BANKED**.
- **Dispatch:** OWNER APPROVED under B-015 and deliberately integrated/banked as real product scope within the local-first/file-based boundary.
- **B-014 durable Working inspection-response drafts:** adopted and preserved through Room v11.
- **SL-5A — time-aware work state + local reminders:** IMPLEMENTED and independently reviewed.
- **SL-5B — optional Android Calendar integration:** IMPLEMENTED, corrected, source-reviewed and banked under B-016.
- **SL-5C — final functional completion/hardening:** IMPLEMENTED and reviewed at `bf55b0bd027fa25c48fc2dfd930d257688088ecb`; no known ordinary-workflow functional placeholder remains before B-013.
- **B-008 real-technician pilot:** outstanding and still a release-validity gate.

## Functional freeze status

The current product is functionally frozen for the purpose of beginning B-013.

This means the adopted functional structure is considered coherent enough that the next milestone should redesign the complete interface rather than continue piecemeal feature work.

It does **not** mean:

- current wording is final;
- localization is complete;
- the present UI is the target design;
- B-008 pilot evidence exists;
- the product is release-ready.

B-017 explicitly defers localization/final-copy work until after B-013 stabilizes the user-facing interface and wording.

## Integrated SL-4 / Dispatch baseline still in force

The current line preserves the verified History/recovery milestone and adopted Dispatch module, including corrections/voids/history, lifecycle/move safeguards, complete authenticated backup and staged replacement restore, CSV boundaries, canonical Technician identity, Teams/leaders, batch `.slwork`, generation-safe import/update/withdrawal, handoff/participation-complete semantics and immutable Dispatch provenance. The hard no-backend/accounts/live-sync/push/chat/shared-database boundary remains unchanged.

## B-014 — durable inspection response drafts

Working inspection response modes retain separate saved drafts for Issue-found description, Not-applicable reason and text/number Value. Switching disposition is non-destructive. Only the selected disposition and applicable detail participate in checklist validity, finalization and customer-facing snapshots.

## SL-5A — time-aware work state and reminders

The midnight-staleness gap remains fixed. Date-derived Home/Work state uses an injectable business clock/zone plus an observable business-date token and one suspended wait to the next business-local midnight. Foreground/resume and relevant clock/date/timezone/business-zone changes invalidate immediately; no polling/service/manufactured Room write is used.

Room v11 persists the adopted reminder preferences and bounded per-Visit appointment lead override. Defaults remain: daily summary 08:00, all days, 14-day shared Due-soon horizon, all summary categories enabled, appointment alerts Off, default lead 2 hours. Device-local reminder delivery is Off initially and excluded from portable recovery.

Reminder scheduling remains one-shot approximate `AlarmManager.setWindow` with stable Work-summary and Appointment-reminder channels, contextual notification permission, privacy-safe content, duplicate-summary suppression, stale appointment suppression, reboot/time/process reconciliation and dataset-scoped PendingIntent identity. Exact alarms, foreground service and battery exemption are not used.

**Verification boundary:** actual Android notification delivery remains unclaimed on the preserved canonical dataset because local reminder delivery / `POST_NOTIFICATIONS` remained Off/denied during validation.

## SL-5B — optional Android Calendar projection

B-016 remains implemented through Android `CalendarContract`, Calendar Provider and `ContentResolver`; it does not use Google OAuth/API, backend or network account logic.

Calendar integration remains Off by default. Selected Calendar, event IDs, Visit-event links, fingerprints, suppression and pending deletion state are device-local under `noBackupFilesDir`, scoped to the current dataset and excluded from portable recovery.

Automatic creation applies only to timed Booked local Visits. Normal reschedule/public Site/Customer changes update the same event ID. External Calendar edits never update ServiceLoop. External deletion becomes Missing until deliberate Recreate. Per-Visit Remove adds suppression; Add clears it. Global Disable retains existing external events/links. Cancelled and `DISPATCH_WITHDRAWN` future events are removed where possible; provider failure leaves retryable `DELETE_PENDING`; Working, Finalized and `PARTICIPATION_COMPLETE` events remain historical evidence.

The Calendar coordinator observes `working_visits`, `customers`, and `sites`; this covers ordinary booking changes plus Dispatch import, generation update and withdrawal. SL-5C additionally hardened its device-state file replacement against transient/concurrent Windows contention using serialized access, unique temporary files, bounded retries and last-good-file preservation.

**Provider-validation boundary:** REAL CALENDAR PROVIDER MUTATION remains **NOT RUN** because no safely disposable writable Calendar was established on the canonical AVD.

## SL-5C — final functional hardening

Concrete production fixes at `bf55b0bd027fa25c48fc2dfd930d257688088ecb`:

- removed stale user-facing `Experimental` wording from adopted Coordinator tools;
- removed an unreachable legacy foundation-placeholder route;
- Home, Work and Customers now surface post-load root refresh failures while preserving the last durable result and provide a functioning Retry action;
- Calendar device-state persistence hardened against concurrent/transient Windows replacement contention;
- relevant Dispatch UI selectors/tests updated to match current production wording/state.

The milestone also added bounded larger-data coverage using 250 Customer/Site/Equipment/Plan/Obligation branches and 120 Booked Visits across principal registers, Due services, Home totals, Visits and search. No blocking scaling issue was found.

Release behavior remains intentionally clean: release `FixtureSeederFactory` continues to return `NoOpStartupSeeder`, so development sample data is not auto-created in release builds.

## SL-5C verification checkpoint

Final production checkpoint:

`bf55b0bd027fa25c48fc2dfd930d257688088ecb`

Reported evidence:

- full unit suite: **201 PASS**;
- focused Calendar contention / Dispatch observation / larger-data suites: PASS;
- instrumentation: **19 PASS**;
- retained Room v1→v11 migration/FK coverage: PASS;
- Dispatch import/generation regression: PASS;
- completion/history/report semantics: PASS;
- reminder regression: PASS;
- Calendar settings/regression: PASS;
- Due-services projection: PASS;
- canonical persistence/PDF rendering: PASS;
- `:app:assembleDebug`: PASS;
- `:app:assembleDebugAndroidTest`: PASS;
- `:app:lintDebug`: PASS;
- `:app:assembleRelease`: PASS;
- `git diff --check`: PASS;
- Room remains v11;
- release fixture seeder remains no-op;
- final debug APK installed explicitly with `adb -s <resolved> install -r` on canonical `Pixel_10a_ServiceLoop`;
- canonical business dataset preserved and representative Home/Work/Customers/Settings/Reminders/Calendar/report surfaces inspected non-destructively;
- no security-command block encountered.

Execution-environment recovery evidence: one exact-match managed patch helper failure used the approved shell/file-edit fallback, and Gradle sandbox cache access recovered through the approved runner path without turning the task into a false project blocker.

## Current forward sequence

1. **B-013 — dedicated whole-product UI/UX overhaul**
   - implement the comprehensive visual/UI authority against the functionally frozen product;
   - coherent hierarchy/task clarity;
   - reusable components/primitives and action/navigation patterns;
   - typography, spacing/density and semantic colors;
   - complete light/dark themes;
   - loading/empty/error states;
   - accessibility, contrast and touch-target review;
   - remove the current semi-default/semi-incremental Compose appearance.

2. **Localization / final copy freeze — B-017 sequencing**
   - after B-013 settles labels, dialogs, helper text and interaction wording;
   - implement Android localization infrastructure and Romanian UI/report/notification/Calendar/system-handoff copy.

3. **B-008 real-technician pilot**
   - coherent finished-looking Romanian-localized build;
   - no known ordinary-workflow placeholders;
   - representative customer/site/equipment/dispatch-or-local-visit/documentation/report flow evaluated by at least one real technician/trade user.

4. **Pilot fixes / final hardening**
5. **Release preparation and submission**

## Toolchain / environment baseline

- package/application ID: `com.v16studio.serviceloop`
- minSdk 29
- compileSdk / targetSdk 37
- AGP 9.3.2
- Gradle 9.5.0; wrapper must remain exact `gradle-9.5.0-bin.zip`
- Gradle JVM/project JDK 20
- Java source/target 11
- Kotlin 2.2.10
- Compose BOM 2026.02.01
- Room 2.8.4
- Room schema v11
- canonical AVD display name: `Pixel 10a ServiceLoop`; resolve adb serial dynamically every run

## Verification / acceptance boundaries

- Automated/AI review is not equivalent to B-008 external pilot evidence.
- SL-4 is technically verified and banked; do not claim an earlier standalone owner-acceptance event that did not occur.
- Dispatch is owner-approved product scope and integrated.
- SL-5A is implemented/reviewed; actual canonical notification delivery remains unclaimed.
- SL-5B is implemented/reviewed; real Calendar provider mutation remains unclaimed.
- SL-5C establishes the functional freeze for B-013; it does not claim current copy/UI is final.
- Localization is intentionally deferred under B-017.
- B-008 cannot be satisfied by emulator/AI/owner-only review.
