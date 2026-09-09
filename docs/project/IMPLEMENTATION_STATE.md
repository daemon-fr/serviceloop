# ServiceLoop — Implementation State

**Updated:** 2026-09-09

This file is the concise current-state summary. Detailed milestone evidence remains in milestone coverage documents and Git history; Dispatch implementation details remain in `docs/internal/DISPATCH_PACKAGES_PROTOTYPE.md` until the integration milestone banks them onto the authoritative development line.

## Current accepted / verified state

- **SL-1:** OWNER ACCEPTED.
- **SL-2:** OWNER ACCEPTED. Accepted user-facing implementation/review state: `d97a8c0013dcea924d91ace993a1325ac16cf5b3`.
- **SL-3:** OWNER ACCEPTED. Accepted production implementation/review state: `ad078faae3c73faa2a9bb02dd1251b1033237399`.
- **SL-4:** IMPLEMENTED and independently Phase-B verified on `codex/sl-4-history-recovery` at `5fc7a7383d6b6ad15cd45dce9ea5926a6ef0ac84`. The verification checkpoint includes retained migration/FK coverage, host/build/lint/release gates and bounded canonical rendered validation. SL-4 still requires deliberate banking/integration onto the next authoritative development line.
- **Dispatch:** OWNER APPROVED FOR PRODUCT INTEGRATION under B-015 after hands-on review through `prototype/dispatch-v2` at `813fed417330fc7d3e5e7bce68ced29edd6dfb23`.
- **B-014 durable Working inspection response drafts:** adopted and implemented on the Dispatch branch with Room v10; this behavior must survive integration independent of Dispatch.
- **B-008 real-technician pilot:** still outstanding and remains a release-validity gate.

## SL-4 capability awaiting banking

The verified SL-4 line contains the complete History/recovery milestone: correction and void workflows; immutable revision/rendition history; correction evidence ownership/integrity; conservative lifecycle/move dependency handling; global/scoped History; complete authenticated backup; staged replacement restore; incomplete-copy truthfulness; CSV export/directory import; erase/restricted-recovery handling; historical report recreation/void notices; and structural recovery validation against the actual Room schema.

No later work should weaken these semantics during integration.

## Adopted asynchronous Dispatch capability

Dispatch is now adopted product scope, but the prototype branch is **not** itself the authoritative production line. The next milestone must integrate it deliberately with SL-4.

Current accepted Dispatch checkpoint:

- branch: `prototype/dispatch-v2`
- owner-reviewed checkpoint: `813fed417330fc7d3e5e7bce68ced29edd6dfb23`
- parent v1 prototype remains: `e58e0345c78548ec899c5e9456c8d9c15ec85aaf`
- current branch schema: Room v10

Adopted Dispatch behavior includes:

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

Latest final verification reported for the combined checkpoint:

- 172 unit tests PASS;
- retained migration chains through Room v10 PASS with foreign-key checks;
- debug, debug-Android-test, lint and release builds PASS;
- `git diff --check` PASS;
- 13 focused device tests PASS on the canonical AVD;
- Android chooser handoff was separately validated to the system Share surface without claiming delivery;
- final owner hands-on review found the module working well enough to move on.

## B-014 — durable inspection response drafts

Room v10 adds Working-state draft retention for inspection response modes:

- Issue-found description draft;
- Not-applicable reason draft;
- existing text/number Value draft.

Switching disposition is non-destructive. Only the current disposition and its applicable detail participate in checklist validity, finalization and customer-facing final snapshots. Inactive drafts remain Working-state convenience data only. The canned Not-applicable reason and ordinary destructive-switch warning were removed; N/A reason is technician-editable. Migration 9→10 truthfully backfills the active v9 reason into the matching draft field.

This is adopted core ServiceLoop behavior and must not be treated as merely Dispatch-specific during integration.

## Known current time-refresh gap

The accepted Due-services architecture observes Room changes correctly, but due/overdue bucketing depends on the current business date. Passage of time alone does not invalidate Room, so an app left open across midnight can retain stale date-derived Home/Work classification until another refresh/re-entry occurs.

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

1. **Integration / banking milestone**
   - reconcile verified SL-4 with adopted Dispatch/Room-v10 work;
   - preserve B-014 response-draft semantics;
   - establish one authoritative development HEAD;
   - run complete host, migration, recovery, build/lint/release and bounded device regression gates;
   - update implementation/coverage docs at the resulting authoritative checkpoint.

2. **Stage 5 — functional product completion/hardening**
   - Romanian localization/UI/report copy;
   - reminders/notifications;
   - remaining ordinary-workflow placeholder/prototype cleanup;
   - time-aware Home/Work invalidation at date/time boundaries and resume;
   - Calendar integration after final semantics are designed/adopted;
   - larger-data, recovery and platform hardening;
   - production hardening of Dispatch without expanding its no-backend boundary.

3. **Dedicated whole-product UI/UX milestone — B-013**
   - coherent hierarchy/task clarity;
   - reusable components and action/navigation patterns;
   - typography, spacing/density and semantic colors;
   - complete light/dark themes;
   - loading/empty/error states;
   - accessibility, contrast and touch-target review;
   - removal of the current semi-default/semi-incremental Compose appearance.

4. **B-008 real-technician pilot**
   - pilot-ready Romanian-localized build;
   - no known ordinary-workflow placeholders;
   - representative customer/site/equipment/dispatch-or-local-visit/documentation/report flow evaluated by at least one real technician/trade user.

5. **Pilot fixes / final hardening**
6. **Release preparation and submission**

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

## Verification/acceptance boundaries

- Automated or AI review is not owner acceptance unless explicitly recorded.
- Dispatch is owner-approved product scope under B-015 but still awaits integration onto the authoritative development line.
- SL-4 is independently verified and awaits deliberate banking/integration.
- B-008 cannot be satisfied by emulator/AI/owner-only review.
- B-013 must occur before B-008 so the pilot evaluates a coherent finished-looking product rather than known presentation debt.
