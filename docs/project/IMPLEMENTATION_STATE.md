# ServiceLoop — Implementation State

**Updated:** 2026-09-08 — SL-3 owner-review corrections implemented; independent verification remains in progress

## Current state

- SL-1 is OWNER ACCEPTED.
- SL-2 is OWNER ACCEPTED for continued development. The accepted user-facing implementation/review state is `d97a8c0013dcea924d91ace993a1325ac16cf5b3`; later documentation-only commits record acceptance and deferred UX direction without changing product behavior.
- SL-2 final safety/save closure before owner review: `b56dd2edbcfd31f910e510ddce5df5d3c347778b`.
- SL-2 owner-review usability correction: `d97a8c0013dcea924d91ace993a1325ac16cf5b3`.
- B-008 real-technician pilot remains outstanding. This does not block Stage-3 development, but Stage 2 is not product-valid for release preparation until that pilot occurs.
- SL-3 is implemented on `codex/sl-3-complete-daily-operations` as a developer candidate for independent review; it is not owner accepted.
- The independent-review corrections are implemented as a development checkpoint: Record past is History-only, Booked work refreshes authoritative Working snapshots at Start, state-sensitive writes revalidate within their Room transaction, explicit-save forms guard unsaved changes, and the reviewed SL-3 completeness/long-text issues are corrected. Testing confirmed the saved-reschedule acknowledgement needed its own layout row and corrected a persistent-journey readiness wait; exhaustive correction-specific runtime/regression verification remains pending. This checkpoint is not technical or owner acceptance.
- Backward compatibility for pre-correction v4 Booked rows is closed: Start safely validates and reuses an already-captured deterministic snapshot when its immutable template revision is still current, or selects a newly captured current revision without rewriting the old snapshot.
- Phase-B host verification passes with 91 unit tests, debug/release/APK assembly, and lint at 0 errors (12 warnings and 2 lower-severity findings). The expanded normal canonical instrumentation suite discovers 27 tests and completes with 20 passes plus 7 deliberate assumption skips for separately gated persistent/system-handoff cases.
- The earlier persistent-journey blocker was confirmed as a test-harness error: the test incorrectly waited for an off-screen lazy child before asking its owning `inspection-list` to compose and scroll to it. After replacing lazy/ambiguous locators with their owning list or stable visit-line tag, the corrected gated journey passes end-to-end on the final APK in 86.931 seconds without a production change.
- Safe fictional-data system-handoff instrumentation reached the Android Photo Picker, camera, Dialer, SMS composer, Email composer, Maps, and Sharesheet. Returning/teardown produced no automatic contact note, follow-up, obligation, or photo business effect.
- Owner hands-on review found bounded SL-3 corrections in root navigation, directory recall, reschedule feedback, cancelled-booking recovery, long-text discoverability, completion-blocker reachability, and visit-action hierarchy. The development correction checkpoint implements these changes with focused host/device validation; exhaustive testing-AI verification and owner re-review remain pending.
- SL-3 is not owner accepted, product-valid, or release-ready. B-008 and the later Stage-4/Stage-5 boundaries remain outstanding.

## SL-3 implementation candidate

- Room schema version 4 with registered additive `MIGRATION_3_4`, retained v1→2 and v2→3 migrations, and exported `4.json`.
- Editable Customer → Site → Equipment → Service Plan directory with stable references, explicit saves, private field labelling, one default site, and plan creation that transactionally creates exactly one current obligation.
- Append-only reusable inspection-template masters/revisions/items; working visits copy the then-current revision into the existing immutable snapshot model.
- Due-service search/buckets/booked scope, one-site multi-selection, a real New visit setup with Book/Start now/Record past and one-off work, transactionally exclusive obligation claims, append-only reasoned reschedule/cancel provenance, cancellation claim release, and claim-ownership stale-start protection.
- Contact handoffs for dialer/SMS/email/maps remain separate from manually saved contact outcomes. Saved notes can be retained as Entered in error. Contact and corrective follow-ups support reasoned edit/resolve/cancel/reopen without recurrence effects.
- Textual parts and bounded app-owned photo intake through Android Photo Picker/camera staging, orientation-corrected metadata-stripped optimization, explicit customer-report inclusion, selected-file integrity checks, final-record part/photo snapshots, PDF photo pages, and public-model privacy selection.
- Global local search routes customers, sites, equipment, plans, visits/final records, and follow-ups to their typed destinations.
- One reusable compact multiline pattern with a bottom-right expand affordance and full-screen same-buffer editor, including Issue found without competing expansion controls. Parent explicit Save remains authoritative.
- Template revision items have staged accessible Move up/Move down controls; the global Equipment-register Add action now opens a real active-site chooser; equipment search includes make and model; explicit-save directory, plan, template, follow-up, contact-note, and visit-setup forms protect changed local buffers on app-bar and system Back.
- Home contextual actions enter explicit Work due/visit/follow-up filters rather than restoring an unrelated previous tab; Work includes visit state/date-window and follow-up due/open/closed filters.

Canonical `Pixel 10a ServiceLoop` was upgraded in place from v3 to v4 with `install -r`; V-001/P-001/report preservation and all registered migration chains were validated. The historical V-001 PDF remained 65,369 bytes with SHA-256 `2cc923ef53f53fb6d0c8e314e7b854009708c5107af4f7ad4bee58f01c71b1b8`.

The persistent canonical SL-3 journey passed on the preserved production database: template; customer, site, equipment, and plan; booking, rescheduling, cancellation, and start; inspection/finding; parts; corrective follow-up; finalization; search; and contextual due filtering. The normal canonical suite passed all 18 tests (the separately gated persistent journey is intentionally skipped in that normal run). Final local verification passed 82 unit tests, debug and release assembly, debug lint (0 errors; existing/toolchain and API-usage warnings only), and debug Android-test assembly.

Stage 4 corrections/voiding, lifecycle/move dependency workflows, complete backup/restore/import/export, and full history presentation remain deferred. Stage 5 reminders, broad device/accessibility/performance hardening, and release work remain deferred. B-008 remains outstanding.

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
