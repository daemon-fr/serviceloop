# ServiceLoop — Implementation State

**Updated:** 2026-09-09

This file is the concise current-state summary. Detailed milestone evidence remains in milestone coverage documents and Git history; detailed Dispatch semantics remain in `docs/internal/DISPATCH_PACKAGES_PROTOTYPE.md`.

## Authoritative technical development line

Current integrated development branch:

- branch: `codex/integrate-sl4-dispatch`
- integrated implementation checkpoint: `132a0586969dfe7e7fbe88501ba6f413ce6ef724`
- Room schema: v10

This branch is now the authoritative technical starting point for Stage 5. `master` remains an older protected reference and is not the current implementation authority.

## Current accepted / verified state

- **SL-1:** OWNER ACCEPTED.
- **SL-2:** OWNER ACCEPTED. Accepted user-facing implementation/review state: `d97a8c0013dcea924d91ace993a1325ac16cf5b3`.
- **SL-3:** OWNER ACCEPTED. Accepted production implementation/review state: `ad078faae3c73faa2a9bb02dd1251b1033237399`.
- **SL-4:** IMPLEMENTED, independently Phase-B verified, and now **VERIFIED AND BANKED INTO THE INTEGRATED DEVELOPMENT LINE**. The banking/integration checkpoint is `132a0586969dfe7e7fbe88501ba6f413ce6ef724`. This does not retroactively claim a separate standalone owner-acceptance event for SL-4.
- **Dispatch:** OWNER APPROVED FOR PRODUCT INTEGRATION under B-015 and now deliberately integrated/banked into the authoritative technical line at the same checkpoint.
- **B-014 durable Working inspection-response drafts:** adopted core behavior and integrated in Room v10.
- **B-008 real-technician pilot:** still outstanding and remains a release-validity gate.

## Integrated SL-4 capability

The integrated line contains the complete verified History/recovery milestone:

- correction and void workflows;
- immutable revision/rendition history;
- correction evidence ownership/integrity;
- conservative lifecycle/move dependency handling including FC-03;
- global/scoped History;
- complete authenticated backup;
- staged replacement restore;
- incomplete-copy truthfulness;
- CSV export / create-only directory import;
- erase / restricted-recovery handling;
- historical report recreation / void notices;
- recovery structural validation against the actual current Room schema.

The final unique SL-4 verification commit was not blindly merged. Its populated migration/FK intent was carried forward in current v10 form, while obsolete v6-specific test naming/documentation was superseded by stronger retained v1→v10 coverage.

## Adopted asynchronous Dispatch capability

Dispatch is now real product scope under B-015 and is present on the integrated line.

Adopted behavior includes:

- stable local Technician identity;
- canonical new Technician IDs in `SLT-XXXX-XXXX-XXXX-CC` form with checksum validation and legacy UUID/32-hex compatibility;
- `.sltech` identity sharing and manual canonical-ID validation;
- coordinator Technician directory and many-to-many Teams with explicit leaders;
- Home coordinator entry points with Settings remaining configuration-only;
- list-first Outbox with searchable/filterable Visits and separate new/edit Visit workflow;
- searchable Site picker, on-demand Team selection, named work-item assignees and Everyone semantics;
- stable `dispatchVisitId` / `dispatchItemId` identities;
- item-level none/one/many assignments, with none meaning Everyone;
- leader visibility without mandatory local documentation;
- readable unsigned `.slwork` format v2 with explicit appointment ZoneId and independent per-Visit generations;
- batch selection/export of up to 100 Visits into one package;
- Draft → Dispatched → Concluded coordinator bookkeeping with individual and atomic bulk conclude/reopen;
- truthful export review/prepare → verified file → transactionally revalidated metadata commit → Android chooser;
- recipient-scoped import, safe generation updates, older/same-generation conflict handling, assignment withdrawal and started/history rewrite protection;
- zero-applicability warning when a package contains no work routed to the local Technician identity;
- consolidated many-Visit preview and **Apply N safe Visits**, leaving unrelated conflicts/review items unapplied;
- recurrence claim only after a technician explicitly chooses to document locally;
- non-exclusive documentation handoff to eligible colleagues/leaders with claim release and rollback-safe evidence cleanup;
- `PARTICIPATION_COMPLETE` without fake cancellation/report/recurrence effects;
- parallel independent technician reports retaining immutable Dispatch provenance;
- office/customer PDF sharing through Android system handoff without delivery/receipt overclaim;
- complete backup/restore participation in the local authoritative dataset.

The hard boundary remains unchanged: no backend, accounts/login, live/cloud synchronization, push dispatch, chat, presence, centralized report ingestion, shared central database, server acknowledgement, or automatic cross-device conflict merging.

## B-014 — durable inspection response drafts

Room v10 retains Working-state draft detail independently for:

- Issue-found description;
- Not-applicable reason;
- text/number Value.

Switching disposition is non-destructive. Only the current disposition and its applicable detail participate in checklist validity, finalization and customer-facing final snapshots. Inactive drafts remain Working-state convenience data only. The canned Not-applicable reason and ordinary destructive-switch warning were removed; N/A reason is technician-editable. Migration 9→10 truthfully backfills the active v9 reason into the matching draft field.

## Integrated migration/recovery evidence

The integration milestone established one coherent Room v10 lineage:

- retained schemas v1–v9 open at v10;
- populated v5→v10 recovery-era preservation;
- representative populated v6→v10 preservation of SL-4-era directory, plan/obligation, Working/finalized Visit, record/report, correction, History, recovery metadata and Working-response state;
- `PRAGMA foreign_key_check` zero violations;
- new Dispatch tables begin truthfully empty/compatible after v6 migration;
- canonical Technician identity initializes safely/idempotently;
- v9→v10 backfills only the active Issue-found/N/A reason draft;
- malformed Dispatch foreign-key structure is rejected by recovery inspection;
- combined SL-4 + Dispatch + B-014 replacement backup/restore preserves integrated business state;
- erase removes integrated authoritative state in isolated fixtures;
- dirty tracking detects Dispatch/Working-response changes while retaining metadata-only backup bookkeeping semantics.

Two focused production corrections were made during integration in `RecoveryPackage.kt`:

1. newly created backups now identify schema v10 while retaining compatibility with existing schema-v9 backups;
2. backup-triggered Technician identity initialization now uses canonical `SLT-XXXX-XXXX-XXXX-CC` IDs rather than generating new legacy 32-hex IDs.

No other production behavior changed during the integration milestone.

## Integration verification checkpoint

Reported final evidence at `132a0586969dfe7e7fbe88501ba6f413ce6ef724`:

- host unit tests: **175 passed, 0 failed, 0 skipped**;
- retained migration instrumentation: **8/8 passed**;
- focused Dispatch/coordinator/B-014 instrumentation: **6/6 passed**;
- canonical non-destructive instrumentation: **2/2 passed**;
- `:app:assembleDebug`: PASS;
- `:app:assembleDebugAndroidTest`: PASS;
- `:app:lintDebug`: PASS;
- `:app:assembleRelease`: PASS;
- `git diff --check`: PASS;
- final APK installed explicitly with `adb -s <resolved> install -r` on canonical `Pixel_10a_ServiceLoop`;
- Home/Work/Customers switching and existing final History/report navigation: PASS;
- no canonical uninstall, clear-data, reset, erase or restore was performed;
- no security-command block encountered.

## Known current time-refresh gap

Due-services observe Room changes correctly, but due/overdue bucketing depends on the current business date. Passage of time alone does not invalidate Room, so an app left open across midnight can retain stale date-derived Home/Work classification until another refresh/re-entry occurs.

Planned Stage-5 hardening:

- no one-second polling service;
- recompute at the next relevant business-date/time boundary;
- refresh on foreground/resume and relevant date/timezone changes;
- continue using Room observation for actual persisted-data changes.

This is not yet implemented.

## Planned Calendar integration direction

A future Stage-5 functional item is optional Android Calendar integration for booked ServiceLoop Visits using the Android Calendar Provider rather than a ServiceLoop backend.

Current intended direction, still requiring final semantics before implementation:

- off by default;
- choose a writable device calendar;
- create an event for a Booked appointment;
- store calendar/event identity locally;
- reschedule/update the same event where safe;
- remove a future appointment on cancellation/dispatch withdrawal where appropriate;
- keep started/finalized appointments as historical calendar evidence;
- exclude private checklist/findings/internal-note content;
- no Google OAuth/backend requirement merely to use a Google-synchronized device calendar.

## Current forward sequence

1. **Stage 5 — functional product completion/hardening**
   - Romanian localization/UI/report copy;
   - reminders/notifications;
   - remaining ordinary-workflow placeholder/prototype cleanup;
   - time-aware Home/Work invalidation at date/time boundaries and resume;
   - Calendar integration after final semantics are designed/adopted;
   - larger-data, recovery and platform hardening;
   - production hardening of Dispatch without expanding its no-backend boundary.

2. **Dedicated whole-product UI/UX milestone — B-013**
   - coherent hierarchy/task clarity;
   - reusable components and action/navigation patterns;
   - typography, spacing/density and semantic colors;
   - complete light/dark themes;
   - loading/empty/error states;
   - accessibility, contrast and touch-target review;
   - removal of the current semi-default/semi-incremental Compose appearance.

3. **B-008 real-technician pilot**
   - pilot-ready Romanian-localized build;
   - no known ordinary-workflow placeholders;
   - representative customer/site/equipment/dispatch-or-local-visit/documentation/report flow evaluated by at least one real technician/trade user.

4. **Pilot fixes / final hardening**
5. **Release preparation and submission**

## Accepted SL-3 architecture still in force

- Customer → Site → Equipment → Service Plan local domain with stable references.
- One current obligation per active plan and exactly-once fulfillment semantics.
- One-site Visits containing multiple service lines.
- Room-observed authoritative Due-services projection.
- Reusable immutable inspection template snapshots.
- Explicit Performed / Partly performed / Not performed separate from Fulfills current obligation.
- App-owned normalized photographs and immutable final evidence snapshots.
- Fixed local PDF/report flow with B-003 historical-share safety.
- Global search, filters, contact/follow-up workflow, cancellation restoration safeguards and reusable expanded multiline editor pattern.

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

- Automated or AI review is not equivalent to B-008 external pilot evidence.
- SL-4 is technically verified and banked; do not claim an earlier standalone owner-acceptance event that did not occur.
- Dispatch is owner-approved product scope and integrated on the authoritative technical line.
- B-008 cannot be satisfied by emulator/AI/owner-only review.
- B-013 must occur before B-008 so the pilot evaluates a coherent finished-looking product rather than known presentation debt.
