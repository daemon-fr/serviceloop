# ServiceLoop — Implementation State

**Updated:** 2026-09-09

This file is the concise current-state summary. Detailed milestone evidence remains in milestone coverage documents and Git history; detailed Dispatch semantics remain in `docs/internal/DISPATCH_PACKAGES_PROTOTYPE.md`.

## Authoritative technical development line

Current development branch:

- branch: `codex/sl-5-time-reminders`
- SL-5A implementation/verification checkpoint: `d133a97171d05d4110204e62c69e423c49ba3bd5`
- Room schema: v11

The previous integrated SL-4/Dispatch branch remains banked history. The SL-5A branch is now the technical starting point for subsequent Stage-5 work. `master` remains an older protected reference and is not current implementation authority.

## Current accepted / verified state

- **SL-1:** OWNER ACCEPTED.
- **SL-2:** OWNER ACCEPTED. Accepted user-facing implementation/review state: `d97a8c0013dcea924d91ace993a1325ac16cf5b3`.
- **SL-3:** OWNER ACCEPTED. Accepted production implementation/review state: `ad078faae3c73faa2a9bb02dd1251b1033237399`.
- **SL-4:** IMPLEMENTED, independently Phase-B verified, and **VERIFIED AND BANKED** into the integrated development line.
- **Dispatch:** OWNER APPROVED under B-015 and deliberately integrated/banked as real product scope within the local-first/file-based boundary.
- **B-014 durable Working inspection-response drafts:** adopted and preserved through Room v11.
- **SL-5A — time-aware work state + local reminders:** IMPLEMENTED and independently reviewed at `d133a97171d05d4110204e62c69e423c49ba3bd5`.
- **B-008 real-technician pilot:** outstanding and still a release-validity gate.

## Integrated SL-4 / Dispatch baseline still in force

The current line preserves the complete verified History/recovery milestone and adopted Dispatch module, including:

- correction/void workflows and immutable report/history revisions;
- lifecycle/move blockers and FC-03;
- complete authenticated backup, staged replacement restore, incomplete-copy truthfulness, CSV export/import and erase/restricted recovery;
- canonical Technician identity plus legacy compatibility;
- Teams/leaders, list-first coordinator Outbox, batch `.slwork` export/import, independent Visit generations and Draft/Dispatched/Concluded bookkeeping;
- recipient-scoped import, assignment withdrawal, handoff/participation-complete semantics and immutable Dispatch provenance;
- no backend/accounts/live sync/push/chat/presence/shared central database.

## B-014 — durable inspection response drafts

Working inspection response modes retain separate saved drafts for:

- Issue-found description;
- Not-applicable reason;
- text/number Value.

Switching disposition is non-destructive. Only the current selected disposition and its applicable detail participate in checklist validity, finalization and customer-facing final snapshots. Inactive drafts remain Working-state convenience data only.

## SL-5A — time-aware work-state architecture

The previous midnight-staleness gap is now IMPLEMENTED/FIXED.

The app uses:

- an injectable business clock/time source;
- a mutable stored-business-zone authority;
- an observable business-date token;
- one suspended wait targeting the next business-local midnight;
- immediate invalidation on foreground/resume and relevant clock/date/timezone/business-zone changes.

Date-derived Home/Work state combines Room observation with the business-date token, so Overdue/Due today/Due soon can change when time passes without any Room write.

There is no second/minute polling loop, permanent background service, foreground service or manufactured business-data write used to refresh the UI.

## SL-5A — reminder settings and persistence

Room v11 adds durable Reminder preferences and the bounded per-Visit appointment lead override.

Adopted defaults are implemented:

- daily summary enabled;
- summary time 08:00 in the business zone;
- all seven days selected;
- Due-soon horizon 14 days, with choices 0/7/14/30;
- Due services / Visits / Follow-ups / Unfinished visits / Backup content enabled;
- approximate appointment alerts disabled;
- default appointment lead 2 hours, with 1 day as the other supported default.

The Due-soon horizon is shared by Home, Work/Due services and reminder summary calculations; it does not mutate business due dates.

Portable saved Reminder preferences are distinct from this installation's local delivery intent. Local delivery is Off initially and is excluded from backup/restore portability.

## SL-5A — Android reminder delivery

Reminder delivery uses stable channels:

- `serviceloop_work_summaries`
- `serviceloop_appointment_reminders`

Android 13+ notification permission is requested only after deliberate enablement. UI/runtime state distinguishes local delivery intent, actual Android permission/app state, channel availability and scheduling errors.

Scheduling uses one-shot `AlarmManager.setWindow` requests with a 15-minute window. There is:

- no exact-alarm permission;
- no `setExact*` use;
- no persistent foreground service;
- no battery-exemption request;
- no high-frequency polling.

Daily summaries read current saved state at execution time, use aggregate/privacy-safe content, omit empty categories, post at most once per dataset/business date, and do not replay a storm of missed summaries after downtime.

Appointment reminders use the stored appointment instant plus Visit/default lead. Before posting, current Visit state is re-read; alerts are suppressed for passed/ineligible/started/finalized/cancelled/withdrawn/participation-complete work and for explicit override Off. Dispatch appointment generation updates reconcile the same local Visit; withdrawal removes eligibility.

Notification content never completes/reschedules work and does not expose customer/Site/address/access/finding/private-note content.

## SL-5A — reboot/time/recovery semantics

Reminder reconciliation occurs after relevant business changes and on startup/process reconstruction, reboot, package replacement and clock/date/timezone changes.

Pending reminder identity is dataset-scoped so old-dataset work cannot operate on a replaced dataset.

Room/recovery behavior:

- schema v11 is the current database schema;
- retained v1→v11 migrations are covered with foreign-key validation;
- older supported schema-9/schema-10 backups remain compatible;
- older backups without Reminder preferences receive the adopted defaults;
- saved Reminder preferences survive complete backup/restore;
- dataset replacement resets local delivery Off and invalidates old notification/alarm ownership;
- Reminder preference changes participate in changed-since-backup tracking, while runtime scheduling/permission state does not.

## SL-5A verification checkpoint

Reported final evidence at `d133a97171d05d4110204e62c69e423c49ba3bd5`:

- host unit tests: **184 passed, 0 failed, 0 skipped**;
- focused instrumentation: **16/16 passed**;
- canonical preserved-data Due-services regression: **1/1 passed**;
- deterministic business-midnight/resume/zone tests: PASS;
- Due-soon horizon 0/7/14/30 tests: PASS;
- summary category/privacy/empty/identity/downtime tests: PASS;
- appointment lead/reschedule/state/delay tests: PASS;
- retained Room migrations/FK and recovery compatibility: PASS;
- `:app:testDebugUnitTest`: PASS;
- `:app:assembleDebug`: PASS;
- `:app:assembleDebugAndroidTest`: PASS;
- `:app:lintDebug`: PASS;
- `:app:assembleRelease`: PASS;
- `git diff --check`: PASS;
- final APK installed explicitly with `adb -s <resolved> install -r` on canonical `Pixel_10a_ServiceLoop`;
- canonical dataset preserved; Home/Reminders rendered inspection performed without changing settings;
- no security-command block encountered.

**Verification boundary:** actual Android notification delivery was NOT RUN on the preserved canonical dataset because local reminder delivery and `POST_NOTIFICATIONS` remained Off/denied. Scheduling, eligibility, privacy and platform-state behavior were verified through deterministic/unit/instrumentation coverage; no delivery claim is made.

## Planned Calendar integration direction

The next functional Stage-5 item is optional Android Calendar integration for booked ServiceLoop Visits using the Android Calendar Provider rather than a ServiceLoop backend.

Current intended direction still needs one final semantic freeze before implementation:

- integration Off by default;
- choose one writable device calendar;
- create an event for an eligible Booked appointment;
- store the calendar/event identity locally;
- reschedule/update the same event where safe;
- remove a future appointment on cancellation/dispatch withdrawal where appropriate;
- keep started/finalized appointments as historical calendar evidence;
- exclude private checklist/findings/internal-note content;
- no Google OAuth/backend requirement merely to use a Google-synchronized device calendar;
- define behavior for external event deletion/edit, calendar disappearance, permission loss and restore/device replacement before coding.

## Current forward sequence

1. **Remaining Stage 5 — functional completion/hardening**
   - finalize and implement Calendar integration semantics;
   - Romanian localization/UI/report copy;
   - remaining ordinary-workflow placeholder/prototype cleanup;
   - larger-data, recovery and platform hardening;
   - production hardening of Dispatch without expanding its no-backend boundary.

2. **Dedicated whole-product UI/UX milestone — B-013**
   - implement the forthcoming comprehensive UI/visual authority only after functional structure is stable;
   - coherent hierarchy/task clarity;
   - reusable components/primitives and action/navigation patterns;
   - typography, spacing/density and semantic colors;
   - complete light/dark themes;
   - loading/empty/error states;
   - accessibility, contrast and touch-target review;
   - remove the current semi-default/semi-incremental Compose appearance.

3. **B-008 real-technician pilot**
   - pilot-ready Romanian-localized build;
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
- canonical AVD display name: `Pixel 10a ServiceLoop`; resolve adb serial dynamically every run

## Verification / acceptance boundaries

- Automated/AI review is not equivalent to B-008 external pilot evidence.
- SL-4 is technically verified and banked; do not claim an earlier standalone owner-acceptance event that did not occur.
- Dispatch is owner-approved product scope and integrated.
- SL-5A is technically implemented/reviewed; actual canonical notification delivery remains unclaimed because notification permission/local delivery were deliberately left Off.
- B-008 cannot be satisfied by emulator/AI/owner-only review.
- B-013 must occur after Stage-5 functional completion and before B-008 so the pilot evaluates a coherent finished-looking product rather than known presentation debt.
