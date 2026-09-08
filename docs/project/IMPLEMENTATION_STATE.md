# ServiceLoop — Implementation State

**Updated:** 2026-09-08 — SL-4 development checkpoint prepared on `codex/sl-4-history-recovery`; independent testing-AI-model verification and owner review remain outstanding

## Current state

- SL-4 correction pass is IMPLEMENTED and FOCUSED-TESTED as a development checkpoint: additive Room v6, startup-gated crash recovery, transaction-specific restore adoption identity, exact owned-file replacement/erase, snapshot-derived backup manifests, complete correction content and schedule reconciliation, void notices, retained revision/rendition navigation, complete readable records export, and bounded background SAF I/O are reachable in the product. See `SL4_REQUIREMENT_COVERAGE.md`.
- SL-4 is not owner accepted and not release-ready. Exhaustive testing-AI-model verification, owner review, Stage 5 hardening/reminders, and B-008 remain.
- B-008 is explicitly a later Romanian-localized real-technician pilot against a pilot-ready build. It is no longer described as an immediate pre-Stage-4 blocker, and no current automated or owner review is represented as satisfying it.

### SL-4 correction checkpoint (2026-09-08)

- Recovery format 2 rejects the earlier unaccepted development format. A coherent Room snapshot defines its own file set; DB references, manifest entries, hashes/sizes, missing declarations, and normalized availability states are cross-validated before replacement.
- Restore/erase file adoption is journaled per path, including an in-progress path, and a random adoption token commits with the database transaction. Startup resolves a valid journal before seeding/repository use; a damaged journal enters a restricted recovery surface and can only be superseded by an explicitly inspected replacement.
- The owned business roots are exactly `attachments/` and `reports/`. Old-only files participate in rollback and are absent after successful replacement; erase does not report success while rollback/private cleanup remains unresolved.
- Correction drafts now retain corrected checklist answers/findings, parts, selected and newly added evidence, public/private notes, identity/date/work/outcome fields, due-date calculation/override meaning, and explicit follow-up effects. Original revisions and evidence remain immutable.
- Record/report history binds historical routes to the requested revision/rendition. Missing historical PDFs recreate from that fixed snapshot; void notices are distinct handoff artifacts; voided originals cannot use ordinary Share and superseded nonvoid reports require acknowledgement.
- Host/domain focused suites are passing at this checkpoint. Full host gates, bounded canonical instrumentation/smoke, independent testing-AI-model Phase B, and owner review remain required before any acceptance claim.

- SL-1 is OWNER ACCEPTED.
- SL-2 is OWNER ACCEPTED for continued development. The accepted user-facing implementation/review state is `d97a8c0013dcea924d91ace993a1325ac16cf5b3`; later documentation-only commits record acceptance and deferred UX direction without changing product behavior.
- SL-2 final safety/save closure before owner review: `b56dd2edbcfd31f910e510ddce5df5d3c347778b`.
- SL-2 owner-review usability correction: `d97a8c0013dcea924d91ace993a1325ac16cf5b3`.
- B-008 real-technician pilot remains outstanding. This does not block continued development, but the product is not product-valid for release preparation until that pilot occurs.
- SL-3 is OWNER ACCEPTED at `ad078faae3c73faa2a9bb02dd1251b1033237399` after technical verification and final owner re-review.
- The independent-review corrections are implemented and accepted: Record past is History-only, Booked work refreshes authoritative Working snapshots at Start, state-sensitive writes revalidate within their Room transaction, explicit-save forms guard unsaved changes, and the reviewed SL-3 completeness/long-text issues are corrected.
- Backward compatibility for pre-correction v4 Booked rows is closed: Start safely validates and reuses an already-captured deterministic snapshot when its immutable template revision is still current, or selects a newly captured current revision without rewriting the old snapshot.
- Phase-B host verification passes with 91 unit tests, debug/release/APK assembly, and lint at 0 errors (12 warnings and 2 lower-severity findings). The expanded normal canonical instrumentation suite discovers 27 tests and completes with 20 passes plus 7 deliberate assumption skips for separately gated persistent/system-handoff cases.
- The earlier persistent-journey blocker was confirmed as a test-harness error: the test incorrectly waited for an off-screen lazy child before asking its owning `inspection-list` to compose and scroll to it. After replacing lazy/ambiguous locators with their owning list or stable visit-line tag, the corrected gated journey passes end-to-end on the final APK in 86.931 seconds without a production change.
- Safe fictional-data system-handoff instrumentation reached the Android Photo Picker, camera, Dialer, SMS composer, Email composer, Maps, and Sharesheet. Returning/teardown produced no automatic contact note, follow-up, obligation, or photo business effect.
- Owner hands-on review found bounded SL-3 corrections in root navigation, directory recall, reschedule feedback, cancelled-booking recovery, long-text discoverability, completion-blocker reachability, visit-action hierarchy, and Due-services state ownership. Those corrections were implemented, technically verified, and finally re-reviewed and accepted by the owner.
- SL-3 remains the latest owner-accepted baseline. The newer SL-4 branch is a development checkpoint awaiting independent verification and owner review; B-008 and Stage 5 remain outstanding.
- The earlier Work-route consolidation fixed route identity but did not fix Due-services ownership. Commit `32dee7c` then added cancellable generation-based refreshes, but owner evidence proved that correction insufficient: retained cards could render before the Work-entry effect set loading, and a cancelled current owner had no terminal write, allowing the blocking `Refreshing due services` state to remain.
- Due services are now one Room-observed projection owned by the ViewModel, not by Work navigation or mutation callbacks. The first successful emission atomically establishes availability and rows; later database invalidations replace that snapshot, claimed unconsumed obligations remain present, and collection failure preserves the last good rows with an explicit error. Work re-entry and New Visit consume the same coherent projection without initiating reads or clearing availability.
- On the preserved canonical dataset, Room, DAO, repository, ViewModel, and rendered UI agreed on 40 rows. P-002/P-003/P-004 were ACTIVE through customer/site/equipment/plan, referenced current unconsumed obligations `obl-002`/`obl-003`/`obl-004`, and had no VisitClaim. Focused canonical instrumentation repeatedly switched Home/Work/Customers, Work subtabs, contextual Home → Work, and New Visit → Back without a card glimpse, blocking refresh, empty transition, or wedge. The owner then manually re-tested the problematic flow and accepted the final behavior.
- Final gates after the production change passed 104 host tests, debug APK assembly, debug Android-test assembly, and lint with 0 errors (12 warnings, 2 hints). Focused in-memory Room/UI and preserved-dataset Due-services tests passed. The established persistent SL-3 journey exposed one ambiguous `Working` text wait after the visit claim was already durable; replacing it with the stable visit-line tag corrected the harness, and the journey then passed in 38.911 seconds. A final rendered screen showed P-004/P-002/P-003 populated under All with no loading or false-empty state.

## Accepted SL-3 implementation

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

Stage 4 corrections/voiding, lifecycle/move dependency workflows, report versions, backup/restore, CSV import/export, erase, and full history presentation are implemented on the SL-4 development branch and await independent verification and owner review. Stage 5 reminders, broad device/accessibility/performance hardening, and release work remain deferred. B-008 remains outstanding as the later Romanian-localized pilot.

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

Stage 3 completes the remaining ordinary daily-operation workflows while preserving the accepted SL-2 integrity model. According to the delegated development plan, this includes:

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

B-010 records a reusable multiline editing pattern:

- compact inline text entry by default;
- the expand affordance is icon-only with a modest visible glyph inside a larger accessible touch target;
- Expand opens the same text in a dedicated full-page/modal editing surface;
- longer multiline inputs should converge on a reusable component with the same visible expand affordance where appropriate.

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

## SL-3 Due-services state correction

- The Work-route-only diagnosis and the first refresh-ordering correction at `32dee7c` both proved insufficient. The latter still made navigation start a new imperative read, split projection truth across rows/loading/error fields, exposed retained rows before `LaunchedEffect` ran, and rethrew cancellation without terminalizing the Boolean set before launch. Its broad Work-entry/Visit-setup/post-mutation fanout therefore retained a reachable no-owner loading state.
- The final correction removes Due-services refresh generations, cancellation ownership, Work-entry reads, Visit-setup reads, and post-mutation refresh fanout. Room observes the complete active/unconsumed obligation join (including optional VisitClaim), the repository maps each emission to business-date buckets, and one ViewModel-lifetime collector publishes an atomic authoritative snapshot.
- Before the first result, the projection is explicitly unresolved; an authoritative empty emission alone enables the empty UI. After a result, navigation never makes it unresolved again. A later collection error retains the last good rows and reports the update failure instead of manufacturing an empty result or permanent spinner.

## Historical provenance

Earlier detailed SL-1/SL-2 implementation and verification lineage remains available in Git history and `SL1_REQUIREMENT_COVERAGE.md` / `SL2_REQUIREMENT_COVERAGE.md`. This current-state file intentionally summarizes the accepted present baseline rather than duplicating every prior task diary.
