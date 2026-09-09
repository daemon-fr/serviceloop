# ServiceLoop — Implementation State

**Updated:** 2026-09-09

This file is the concise current-state summary. Detailed milestone evidence remains in the milestone coverage documents and Git history; experimental Dispatch details remain in `docs/internal/DISPATCH_PACKAGES_PROTOTYPE.md`.

## Current accepted/product state

- **SL-1:** OWNER ACCEPTED.
- **SL-2:** OWNER ACCEPTED. Accepted user-facing implementation/review state: `d97a8c0013dcea924d91ace993a1325ac16cf5b3`.
- **SL-3:** OWNER ACCEPTED. Accepted production implementation/review state: `ad078faae3c73faa2a9bb02dd1251b1033237399`.
- **SL-4:** IMPLEMENTED and independently Phase-B verified on `codex/sl-4-history-recovery` at `5fc7a7383d6b6ad15cd45dce9ea5926a6ef0ac84`, but **NOT YET OWNER ACCEPTED**. The Phase-B checkpoint added retained v5→v6 migration/FK coverage, passed 126 host tests, debug/debug-Android-test/lint/release builds, and a non-destructive canonical rendered check. It does not claim exhaustive provider/system-handoff/lifecycle-race or whole-surface rendered verification.
- **B-008 real-technician pilot:** still outstanding and remains a release-validity gate.

## SL-4 implemented capability

The SL-4 branch contains the complete History/recovery milestone: correction and void workflows; immutable revision/rendition history; correction evidence ownership/integrity; conservative lifecycle/move dependency handling; global/scoped History; complete authenticated backup; staged replacement restore; incomplete-copy truthfulness; CSV export/directory import; erase/restricted-recovery handling; historical report recreation/void notices; and recovery structural validation against the actual Room schema.

Owner review is still required before SL-4 is banked as accepted product state.

## Experimental Dispatch v2 checkpoint

The owner separately authorized the asynchronous Dispatch experiment under B-012. It remains **EXPERIMENTAL — NOT PRODUCT BASELINE — NOT APPROVED FOR MERGE**.

Current prototype branch:

- branch: `prototype/dispatch-v2`
- implementation checkpoint reviewed by the orchestrator: `cdca7c460bb7271654c5ed3da4e9c95e11688eb3`
- parent v1 prototype remains: `e58e0345c78548ec899c5e9456c8d9c15ec85aaf`
- prototype schema: Room v9; this schema number belongs to the experimental branch and does not replace the accepted/SL-4 production-line schema state.

The current prototype demonstrates:

- stable local Technician identity and `.sltech` sharing;
- coordinator Technician directory;
- many-to-many Teams with zero/one/multiple leaders;
- durable coordinator outbox independent of normal planner Visits;
- Visit-level Team selection;
- stable `dispatchVisitId` and per-item `dispatchItemId`;
- per-item assignment to none/one/many participating Technicians, with none meaning Everyone;
- leader visibility without mandatory documentation;
- readable unsigned `.slwork` format v2 with per-Visit generations and explicit appointment ZoneId;
- recipient-scoped import and directory projection;
- safe Booked-generation update, older/same-generation conflict handling, assignment withdrawal, and started/historical rewrite protection;
- recurrence claim only after a technician explicitly chooses to document locally;
- non-exclusive documentation handoff to eligible colleagues/leaders with claim release and rollback-safe evidence cleanup;
- `PARTICIPATION_COMPLETE` without fake cancellation/report/recurrence effect;
- parallel independent reports carrying immutable dispatch provenance;
- optional Office PDF recipient using the ordinary Android chooser and existing B-003 report-share restrictions;
- batch selection/export of up to 100 outbox Visits into one `.slwork`;
- Today/Tomorrow/ISO-week/Next-7-days/custom/all date scopes plus Active/Draft/Dispatched/Concluded/All outbox scopes;
- truthful export sequencing: prepare → verified cache file → transactionally revalidated export metadata → chooser;
- coordinator-side derived **Draft → Dispatched → Concluded** bookkeeping, with individual and atomic bulk conclude/reopen;
- consolidated many-Visit technician preview and **Apply N safe Visits**, leaving independent conflicts/review items unapplied;
- complete prototype backup/restore coverage including Dispatch lifecycle state.

Latest reported/accepted technical checkpoint at `cdca7c46`:

- 161/161 host unit tests passed;
- retained v1→v9 migrations passed with `PRAGMA foreign_key_check`;
- focused emulator instrumentation passed 8/8;
- debug, debug-Android-test, lint and release builds passed;
- `git diff --check` passed;
- a non-destructive rendered Dispatch outbox capture was inspected;
- actual `.slwork` chooser handoff was not exercised in the latest pass, and no delivery claim is made.

The orchestrator accepts this as a **technical prototype checkpoint suitable for owner/rendered evaluation**. Dispatch still requires an explicit owner product-adoption decision before authoritative product docs or the main development line are changed to include it.

## Known current time-refresh gap

The accepted Due-services architecture correctly observes Room changes, but due/overdue bucketing depends on the current business date. Passage of time alone does not invalidate Room, so an app left open across midnight can retain stale date-derived Home/Work classification until another refresh/re-entry occurs.

Planned hardening direction:

- do **not** add a one-second polling service;
- refresh/recompute at the next relevant business-date/time boundary;
- refresh on app foreground/resume and relevant timezone/date changes;
- continue using Room observation for actual persisted-data changes.

This is not yet implemented.

## Planned Calendar integration direction

A future Stage-5 candidate is optional Android Calendar integration for booked ServiceLoop Visits, using the Android Calendar Provider rather than a ServiceLoop backend.

If adopted, the intended shape is:

- integration off by default;
- user chooses a writable device calendar;
- create a calendar event for a Booked appointment;
- store the created calendar/event identity locally;
- reschedule/generation changes update the same event where safe;
- cancellation/dispatch withdrawal before work starts may remove it;
- started/finalized historical appointments remain rather than being erased merely because work completed;
- no private checklist/findings/internal-note leakage to calendar content;
- no Google OAuth/backend requirement merely to use a Google-synchronized Android calendar.

This direction is feasible but **not yet an adopted/implemented product requirement**; exact permission, failure, user-edit and reconciliation semantics must be designed before implementation.

## Current forward sequence

1. **Owner/rendered Dispatch review and product decision**
   - decide whether the file-based Dispatch module becomes real ServiceLoop scope;
   - if not adopted, leave it isolated;
   - if adopted, freeze semantics before production integration.

2. **Formal closure/banking of SL-4 and adopted Dispatch work**
   - owner acceptance of SL-4;
   - clean deliberate integration onto the real development line rather than treating the experimental branch as automatically authoritative.

3. **Stage 5 — functional product completion/hardening**
   - Romanian localization/UI/report copy;
   - reminders/notifications;
   - remove remaining ordinary-workflow placeholders/prototype shortcuts;
   - time-aware Home/Work invalidation at date/time boundaries and resume;
   - Calendar integration if explicitly adopted;
   - regression, recovery, larger-data and platform hardening;
   - productionize Dispatch if adopted.

4. **Dedicated whole-product UI/UX milestone — B-013**
   - coherent visual hierarchy and task clarity;
   - consistent components/navigation/action hierarchy;
   - typography, spacing and density;
   - deliberate color/semantic-state system;
   - complete light and dark themes;
   - loading/empty/error presentation;
   - accessibility, contrast and touch-target review;
   - remove the current semi-default/semi-incremental Compose appearance.

5. **B-008 real-technician pilot**
   - pilot-ready Romanian-localized build;
   - no known ordinary-workflow placeholders;
   - representative customer/site/equipment/visit/documentation/report flow evaluated by at least one real technician/trade user.

6. **Pilot fixes / final hardening**

7. **Release preparation**
   - final regression/device validation;
   - release configuration/versioning/signing;
   - Play listing/privacy/support material;
   - production build and submission.

## Accepted SL-3 architecture still in force

- Customer → Site → Equipment → Service Plan local domain with stable references.
- One current obligation per active plan and exactly-once fulfillment semantics.
- One-site Visits containing multiple service lines.
- Room-observed authoritative Due-services projection.
- reusable immutable inspection template snapshots;
- explicit Performed / Partly performed / Not performed separate from Fulfills current obligation;
- app-owned normalized photographs and immutable final evidence snapshots;
- fixed local PDF/report flow with B-003 historical-share safety;
- global search, filters, contact/follow-up workflow, cancellation restoration safeguards, and reusable expanded multiline editor pattern.

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
- canonical AVD display name: `Pixel 10a ServiceLoop`; resolve the adb serial dynamically every run

## Verification/acceptance boundaries

- Automated or AI review is not owner acceptance unless explicitly recorded as such.
- Dispatch is technically reviewed but remains experimental.
- SL-4 is independently verified but remains owner-unaccepted.
- B-008 cannot be satisfied by emulator/AI/owner-only review.
- The dedicated B-013 UI/UX pass must happen before B-008 so the pilot evaluates a coherent product rather than known presentation debt.
