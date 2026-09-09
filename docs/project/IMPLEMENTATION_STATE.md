# ServiceLoop — Implementation State

**Updated:** 2026-09-09

This file is the concise current-state summary. Detailed milestone evidence remains in Git history and focused coverage/tests; detailed Dispatch semantics remain in `docs/internal/DISPATCH_PACKAGES_PROTOTYPE.md`.

## Authoritative technical development line

Current development branch:

- branch: `codex/sl-5-calendar`
- SL-5B implementation/correction checkpoint: `2f8faf466c7ffd960c362078abbc6de83cc9e722`
- Room schema: v11

This branch is now the technical starting point for the remaining Stage-5 work. `master` and earlier milestone branches are protected historical/reference heads rather than current implementation authority.

## Current accepted / verified state

- **SL-1:** OWNER ACCEPTED.
- **SL-2:** OWNER ACCEPTED. Accepted user-facing implementation/review state: `d97a8c0013dcea924d91ace993a1325ac16cf5b3`.
- **SL-3:** OWNER ACCEPTED. Accepted production implementation/review state: `ad078faae3c73faa2a9bb02dd1251b1033237399`.
- **SL-4:** IMPLEMENTED, independently Phase-B verified, and **VERIFIED AND BANKED**.
- **Dispatch:** OWNER APPROVED under B-015 and deliberately integrated/banked as real product scope within the local-first/file-based boundary.
- **B-014 durable Working inspection-response drafts:** adopted and preserved through Room v11.
- **SL-5A — time-aware work state + local reminders:** IMPLEMENTED and independently reviewed.
- **SL-5B — optional Android Calendar integration:** IMPLEMENTED, corrected, source-reviewed and banked under B-016 through `2f8faf466c7ffd960c362078abbc6de83cc9e722`.
- **B-008 real-technician pilot:** outstanding and still a release-validity gate.

## Integrated SL-4 / Dispatch baseline still in force

The current line preserves the verified History/recovery milestone and adopted Dispatch module, including corrections/voids/history, lifecycle/move safeguards, complete authenticated backup and staged replacement restore, CSV boundaries, canonical Technician identity, Teams/leaders, batch `.slwork`, generation-safe import/update/withdrawal, handoff/participation-complete semantics and immutable Dispatch provenance. The hard no-backend/accounts/live-sync/push/chat/shared-database boundary remains unchanged.

## B-014 — durable inspection response drafts

Working inspection response modes retain separate saved drafts for Issue-found description, Not-applicable reason and text/number Value. Switching disposition is non-destructive. Only the selected disposition and applicable detail participate in checklist validity, finalization and customer-facing snapshots.

## SL-5A — time-aware work state and reminders

The midnight-staleness gap is fixed. Date-derived Home/Work state uses an injectable business clock/zone plus an observable business-date token and one suspended wait to the next business-local midnight. Foreground/resume and relevant clock/date/timezone/business-zone changes invalidate immediately; no polling/service/manufactured Room write is used.

Room v11 persists the adopted reminder preferences and bounded per-Visit appointment lead override. Defaults remain: daily summary 08:00, all days, 14-day shared Due-soon horizon, all summary categories enabled, appointment alerts Off, default lead 2 hours. Device-local reminder delivery is Off initially and excluded from portable recovery.

Reminder scheduling remains one-shot approximate `AlarmManager.setWindow` with stable Work-summary and Appointment-reminder channels, contextual notification permission, privacy-safe content, duplicate-summary suppression, stale appointment suppression, reboot/time/process reconciliation and dataset-scoped PendingIntent identity. Exact alarms, foreground service and battery exemption are not used.

**Verification boundary:** actual Android notification delivery remains unclaimed on the preserved canonical dataset because local reminder delivery / `POST_NOTIFICATIONS` remained Off/denied during validation.

## SL-5B — optional Android Calendar projection

B-016 defines the adopted Calendar semantics. The implementation uses Android `CalendarContract`, Calendar Provider and `ContentResolver`; it does not use Google OAuth/API, backend or network account logic.

### Device-local ownership

Calendar integration is Off by default. Selected Calendar, event IDs, Visit-event links, fingerprints, per-Visit suppression, pending deletion state and provider status are stored only in an atomic app-private file under `noBackupFilesDir`, scoped to the current `recovery_metadata.datasetId`.

These values are excluded from the authoritative Room v11 dataset and portable ServiceLoop backup. Dataset replacement/restore/erase resets local Calendar integration Off and discards bindings without deleting external events.

### Provider permissions and selection

`READ_CALENDAR` and `WRITE_CALENDAR` are requested only after deliberate user Calendar actions. The app enumerates visible calendars with contributor-or-better access, supports one preferred Calendar for new links, reports no-writable-calendar/permission/unavailable-selection states truthfully, and does not auto-select/move existing linked events when the preferred Calendar changes.

### Event lifecycle

Automatic creation applies only to timed **BOOKED** local Visits. Date-only bookings do not become all-day events.

Event content is restricted to:

- title `ServiceLoop · <Site name>`;
- Site address when available;
- description containing Visit reference and Customer name;
- stored appointment instant/ZoneId;
- fixed 60-minute Calendar display block.

Private notes, contacts, findings, checklists, Dispatch instructions, report content, passphrases and opaque Technician IDs are excluded. No Calendar reminder rows are created.

One local Visit has at most one managed event link. Normal reschedule/public Site/Customer changes update the same event ID. External Calendar edits never update ServiceLoop. External deletion changes the link to Missing and requires deliberate Recreate. Per-Visit Remove deletes the exact bound event and adds local suppression; Add clears suppression and creates a new link. Global Disable leaves existing external events/links in place and stops active synchronization.

Future linked events are deleted for **CANCELLED** and **DISPATCH_WITHDRAWN** Visits when possible. Provider deletion failure leaves the ServiceLoop state committed and retains retryable `DELETE_PENDING`. **WORKING**, **FINALIZED** and **PARTICIPATION_COMPLETE** events remain as historical appointment evidence.

### Room-driven reconciliation

The Calendar coordinator installs one idempotent application-lifetime Room invalidation observer over exactly:

- `working_visits`;
- `customers`;
- `sites`.

Initial emission performs startup reconciliation. Subsequent persisted changes automatically drive Calendar create/update/delete without every caller needing to remember a manual hook. Existing explicit/resume reconciliation remains safe because the coordinator serializes through its mutex/link ownership model. Provider failures on one invalidation do not terminate the long-lived observer; `CancellationException` is propagated.

This wiring covers ordinary booking/reschedule/cancel plus Dispatch import, newer generation appointment updates, and assignment withdrawal. Dispatch creation produces one Calendar event; generation update changes the same event ID; `DISPATCH_WITHDRAWN` deletes the managed future event/link or retains retryable deletion state on provider failure.

## SL-5B verification checkpoint

Final corrected checkpoint:

`2f8faf466c7ffd960c362078abbc6de83cc9e722`

Reported evidence:

- full unit suite: **198 PASS**;
- focused Calendar tests: **14 PASS**;
- automatic Room-driven ordinary Visit update/cancel tests: PASS;
- automatic Dispatch create/generation-update/withdrawal projection tests: PASS;
- withdrawal deletion-failure retry test: PASS;
- Calendar idempotency/update/external-delete/suppression/global-disable/lifecycle/privacy/dataset-replacement/corruption tests: PASS;
- Dispatch and SL-5A regressions: PASS;
- `:app:assembleDebug`: PASS;
- `:app:assembleDebugAndroidTest`: PASS;
- `:app:lintDebug`: PASS;
- `:app:assembleRelease`: PASS;
- `git diff --check`: PASS;
- final debug APK installed explicitly with `adb -s <resolved> install -r` on canonical `Pixel_10a_ServiceLoop`;
- canonical Calendar permissions/provider state remained unchanged.

**Provider-validation boundary:** REAL CALENDAR PROVIDER MUTATION — **NOT RUN**. No safely disposable writable Calendar was established and both Calendar permissions remained denied on the canonical AVD. No owner Calendar event was created, changed or deleted; no Google/cloud synchronization claim is made.

## Current forward sequence

1. **SL-5C — final functional completion/hardening**
   - Romanian localization of UI, reports, notifications and relevant user-visible share/system copy;
   - inventory and remove remaining ordinary-workflow placeholders, obsolete prototype/experimental wording and obvious functional rough edges;
   - bounded larger-data/recovery/platform hardening where current code reveals concrete risk;
   - full functional-completeness gate while avoiding the later visual redesign.

2. **Dedicated whole-product UI/UX milestone — B-013**
   - apply the comprehensive visual/UI authority only after functional structure is stable;
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
- Room schema v11
- canonical AVD display name: `Pixel 10a ServiceLoop`; resolve adb serial dynamically every run

## Verification / acceptance boundaries

- Automated/AI review is not equivalent to B-008 external pilot evidence.
- SL-4 is technically verified and banked; do not claim an earlier standalone owner-acceptance event that did not occur.
- Dispatch is owner-approved product scope and integrated.
- SL-5A is implemented/reviewed; actual canonical notification delivery remains unclaimed.
- SL-5B is implemented/reviewed; real Calendar provider mutation remains unclaimed because no safe disposable writable Calendar was used.
- B-008 cannot be satisfied by emulator/AI/owner-only review.
- B-013 occurs after Stage-5 functional completion and before B-008 so the pilot evaluates a coherent finished-looking product rather than known presentation debt.
