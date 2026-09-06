# ServiceLoop — Implementation State

**Updated:** 2026-09-06 — SL-2 owner acceptance and Stage-3 handoff

## Current state

- SL-1 is OWNER ACCEPTED.
- SL-2 is OWNER ACCEPTED for continued development. The accepted user-facing implementation/review state is `d97a8c0013dcea924d91ace993a1325ac16cf5b3`; later documentation-only commits record acceptance and deferred UX direction without changing product behavior.
- SL-2 final safety/save closure before owner review: `b56dd2edbcfd31f910e510ddce5df5d3c347778b`.
- SL-2 owner-review usability correction: `d97a8c0013dcea924d91ace993a1325ac16cf5b3`.
- B-008 real-technician pilot remains outstanding. This does not block Stage-3 development, but Stage 2 is not product-valid for release preparation until that pilot occurs.
- Next milestone: **Stage 3 / SL-3 — Complete daily operations**.

## Accepted SL-2 outcome

SL-2 implements the first complete local service-recording loop:

- Room schema version 3 with additive migrations and no destructive fallback.
- Customer/site/equipment/service-plan/obligation persistence sufficient for the complete loop.
- Durable Working visits and checklist responses with truthful save states.
- Inline public Issue found description with compact editor and long-text expand control.
- Explicit Performed / Partly performed / Not performed outcomes and separate Fulfills current obligation decision.
- Exact current-obligation comparison and exactly-once Room finalization transaction.
- Completion-date-based recurrence and immutable final record/revision snapshots.
- Structural public/private report separation.
- Native offline PDF generation, app-private persistence, hash/size/page metadata, PDF/Text preview, and Sharesheet handoff.
- Work → Visits reopening of Working/Booked/Finalized records.
- Root navigation visual atomicity and non-blocking freshness.
- Owner-review fixes: Equipment-mode Add action labelled Add equipment; Home shows subtle ServiceLoop identity and booked-site context; debug-only V-003 Working inspection enables direct owner review without modifying finalized V-001.

## Accepted verification evidence

Before the final owner-review usability correction:

- `:app:testDebugUnitTest`: PASS — 67 tests.
- Canonical `Pixel 10a ServiceLoop` instrumentation: PASS — 13 tests.
- `:app:assembleDebug`, `:app:lintDebug`, `:app:assembleRelease`, `:app:assembleDebugAndroidTest`: PASS.
- Canonical V-001 remained Finalized and P-001 remained due `2026-12-05` with exactly-once recurrence provenance.
- Historical PDF remained 65,369 bytes with SHA-256 `2cc923ef53f53fb6d0c8e314e7b854009708c5107af4f7ad4bee58f01c71b1b8`.

The later owner-review usability correction was independently source-reviewed and owner-validated visually. It did not alter finalization, recurrence, report history, Room schema, or production fixture boundaries.

## Stage-3 boundary

Stage 3 must complete the remaining ordinary daily-operation workflows while preserving the accepted SL-2 integrity model. According to the delegated development plan, this includes:

- customer/site/equipment/service-plan ordinary creation and editing;
- booking/contact arrangements and visit setup/start flows;
- reusable inspection-template management;
- multiple machines/plans within ordinary visits;
- partial work and corrective follow-ups as normal reachable workflows;
- required search and filtering;
- removal of ordinary placeholder dead ends for daily-operation paths.

Stage 3 does **not** absorb Stage-4 history/recovery work merely because related records exist. Corrections/voids, lifecycle/move rules, report-version workflows, complete backup/restore, import/export, and their failure paths remain Stage 4 unless a narrow dependency is unavoidable.

Stage 5 remains whole-product hardening/reminders/accessibility/larger datasets/release configuration. Stage 6 remains real-user pilot and release preparation.

## Deferred accepted UX direction

B-010 records a later reusable multiline editing pattern:

- compact inline text entry by default;
- Expand opens the same text in a dedicated full-page/modal editing surface;
- longer multiline inputs should converge on a reusable component with a visible resize/expand affordance where appropriate.

This is not a Stage-2 blocker and should be incorporated when Stage-3 touches reusable long-text editing without forcing unrelated redesign.

## Toolchain / environment baseline

- package/application ID `com.v16studio.serviceloop`
- minSdk 29
- compileSdk / targetSdk 37
- AGP 9.3.2
- Gradle 9.5.0 exact wrapper filename `gradle-9.5.0-bin.zip`
- Gradle JVM/project JDK 20
- Java source/target 11
- Kotlin 2.2.10
- Compose BOM 2026.02.01
- Room 2.8.4
- canonical AVD display name: `Pixel 10a ServiceLoop`; resolve adb serial dynamically every run

## Historical provenance

Earlier detailed SL-1/SL-2 implementation and verification lineage remains available in Git history and `SL1_REQUIREMENT_COVERAGE.md` / `SL2_REQUIREMENT_COVERAGE.md`. This current-state file intentionally summarizes the accepted present baseline rather than duplicating every prior task diary.
