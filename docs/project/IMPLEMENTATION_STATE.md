# ServiceLoop — Implementation State

**Updated:** 2026-09-06 — SL-2 independent-review correction developer validation

## Current state

- SL-1 is OWNER ACCEPTED and banked on `master` at `2052ef07c5dc20c515b86574631cc3b75648166f`.
- SL-2 development branch: `codex/sl-2-first-complete-service-loop`, started exactly from the accepted SL-1 SHA.
- SL-2 implementation commit: `208f4ebd9bab22a74fc9a9bd4f938582bb406ff9`.
- SL-2 is implemented and developer-validated, but is not owner accepted and is not Stage 2 product-valid until the B-008 real-technician pilot occurs.

## SL-2 independent-review correction pass

**IMPLEMENTED / TESTED**

- Room schema version 3, committed `3.json`, additive `MIGRATION_2_3`, retained `MIGRATION_1_2`, and verified v1→2→3 chaining. Legacy working equipment/customer/site snapshots are best-effort backfilled once; existing final records/report renditions are preserved and no destructive fallback exists.
- Working items now own equipment identifier/make/model/serial start snapshots. Working visits own customer/site references and a business/report identity snapshot; finalization reads only these captured fields. Missing legacy visit identity is an explicit blocker with Use current identity and explicit Refresh actions.
- Checklist semantics distinguish valid answers, Reviewed state, and attendance outcome. Any Issue found needs public detail; Performed with a checklist needs legitimate review; Partly performed/Not performed may finalize incomplete entries as explicit Not checked/Unanswered warnings. NUMBER values use a locale-independent finite signed-decimal contract.
- Completion persistence canonically clears incompatible outcome/fulfillment/due fields, derives calculated-versus-override provenance from actual dates, requires an override reason, treats identical normalized saves as no-ops, and revalidates provenance inside finalization.
- Successful writes publish Saved before post-write rereads. A failed reread remains a separate content-refresh problem. Successful report generation similarly remains successful if the screen reread fails.
- Work → Visits carries each Working row's own resume work-item id. Public final/report models retain plan versus one-off identity, plan reference, customer/site references, outcome/reason/due semantics, and structured Text parity.
- READY metadata is distinguished from current file presence: a missing PDF is labelled File missing, Text view remains usable, and Share is unavailable. A failure between file adoption and READY metadata removes the uncommitted orphan.
- Real concurrent finalization coverage starts two coroutine callers and proves one record/revision, one consumption/advance, and a shared record id. Semantic Compose instrumentation drives the real outcome, fulfillment, calculated-date, Finalize, and resulting-record controls.

**RUNTIME EVIDENCE**

- Canonical `Pixel 10a ServiceLoop` (`Pixel_10a_ServiceLoop`, dynamically resolved `emulator-5556`) upgraded in place v2→v3 with `install -r`; V-001 remained Finalized, P-001 remained due `2026-12-05`, and the existing rendition remained READY.
- Existing PDF `ec1c0f1c-a227-37ff-b12d-82e0863a8810` remained 65,369 bytes with SHA-256 `2cc923ef53f53fb6d0c8e314e7b854009708c5107af4f7ad4bee58f01c71b1b8`. HUMAN/RENDERED inspection covered Work → Visits, Final Record, PDF preview, and structured Text view. UI-INSTRUMENTED evidence covers completion-button wiring; DOMAIN-INSTRUMENTED evidence covers finalization/migration/integrity behavior.
- Owner visual/report review and the B-008 real-technician pilot remain outstanding. SL-2 is not owner accepted.

- Repository: `daemon-fr/serviceloop`; protected owner baseline remains `master`.
- SL-1 development branch: `codex/sl-1-foundation-visual-proof`.
- Required and verified starting revision: `33d350fbd6e46161616b630d766f46c4cf3b8dd7`.
- SL-1 implementation commit: `e92af2b8b715f5adcf97ddc31e9db1392470a52f`.
- SL-1 final technical closure implementation commit: `2e9e074a0dcb1d1fd00484d9c3bb1a37fd8498db`.
- SL-1 owner visual correction starting revision: `884184eb9b53baefa67fd5c67c6ba92efbc48743`.
- SL-1 owner visual correction implementation commit: `e90ce7f9e7e59d59601452034458cd2a52a61cda`.
- SL-1 visual-atomicity correction starting revision: `c9b5e49ad16f8d865ac99f99c15ecd57b4586fcd`.
- SL-1 visual-atomicity implementation commit: `9f782e7c821860261cee8c97c625d618e339491c`.
- SL-1 root-freshness correction starting revision: `ef7ec02b97829dfa1a329bc2933e4f5384e5be41`.
- SL-1 root-freshness implementation commit: `ffd1977a4606b1454d9c1b598717f03a0d154953`.
- Package/application ID remains `com.v16studio.serviceloop`.
- The former generated Compose starter is replaced by a runnable ServiceLoop shell, Room persistence, debug-only representative fixtures, state holders, and representative Home, Equipment detail, Inspection, and Completion Review screens.
- This is a Stage 1 foundation/semantic proof. It is not a completed service loop and does not implement the Stage 2 finalization transaction.
- The prior sentence describes the accepted SL-1 baseline. SL-2 now implements the first complete local service-recording loop described below.

## SL-2 implemented

**IMPLEMENTED / TESTED**

- Room schema version 3 with explicit additive `MIGRATION_1_2` and `MIGRATION_2_3`; all prior business, draft, final-record, and rendition data is preserved.
- Required Business and report identity editor with explicit truthful Save, stored business zone, debug-only positively-recognized compatibility seed, and finalization blocker when issuer/technician identity is incomplete.
- Editable durable public work performed field, required-checklist validation, explicit Mark checklist reviewed, and atomic invalidation of Reviewed when a saved answer/finding changes.
- Durable Performed / Partly performed / Not performed outcomes, required non-performance reason, explicit recurring fulfillment, calculated completion-date-based next due, explicit confirmation, and reasoned manual override.
- Central recurrence calculator for days, weeks, months, and years using `java.time` calendar clipping and no hidden anchor/catch-up behavior.
- One authoritative Room finalization transaction. It returns an existing record idempotently, reloads and validates live state, compares the exact captured/current obligation identity, conditionally consumes/advances, inserts immutable revision-1 snapshots, and marks the visit Finalized only after all effects succeed. Partial, not-performed, unfulfilled, and one-off lines have no recurrence effect.
- Read-only Final Service Record with public line/checklist/outcome/due effects and a visibly separate Internal / Not in customer report section. Work → Visits lists and reopens Working, Booked, and Finalized rows.
- Structurally public-only `PublicReportModel`; Android-native fixed A4 PDF with deterministic pagination, app-private persistent storage, SHA-256/size/page metadata, retry-safe version 1, PdfRenderer page preview, structured Text view, and restricted FileProvider sharesheet handoff.

**DEVELOPER-RUNTIME-VALIDATED**

- Canonical AVD display name `Pixel 10a ServiceLoop`, internal AVD id `Pixel_10a_ServiceLoop`, dynamically resolved serial `emulator-5556`, adb 37.0.1.
- Existing accepted SL-1 database upgraded in place from v1 to v2 with `install -r`; no clear/reset/recreation. V-001, customer/site/equipment, saved responses, and the user-edited public belt finding survived.
- Representative V-001 finalized once: plan P-001 advanced from 2026-09-01 to 2026-12-05; the partial, performed-but-unfulfilled, and not-performed lines retained their obligations. Immediate retry returned the same final record.
- Cold reopen removed V-001 from Home unfinished work, reduced overdue count from four to three, and Work → Visits reopened V-001 as Finalized while retaining V-002 Booked.
- Real owned PDF rendition `ec1c0f1c-a227-37ff-b12d-82e0863a8810`, 65,369 bytes, SHA-256 `2cc923ef53f53fb6d0c8e314e7b854009708c5107af4f7ad4bee58f01c71b1b8`, one page, stored below `files/reports/a6590fcd-7398-308a-8c72-64fa7c23db0e/`; PdfRenderer view, public-only Text view, and Android Sharesheet handoff were exercised. A synthetic long report produced and reopened multiple pages.
- Focused instrumentation passed for synthetic v1→v2 migration, actual canonical migrated-data preservation, native multipage PDF, representative loop execution/idempotent retry, and post-finalization root visual atomicity.

**AUTOMATED**

- `:app:testDebugUnitTest`: PASS — 38 tests.
- `:app:assembleDebug`, `:app:lintDebug`, `:app:assembleRelease`, `:app:assembleDebugAndroidTest`: PASS.
- Tests cover recurrence boundaries, checklist review/invalidation, completion persistence semantics, stale obligation blocking, rollback, repeated/reconstructed idempotence, non-effect outcomes/one-off work, independent plans, immutable captured identity, privacy sentinels, PDF failure/retry isolation, content hash/size, and native multipage rendering.

**DEFERRED / OWNER VALIDATION REQUIRED**

- Corrections/voiding, photos/parts, full CRUD/editors, scheduling, backup/restore/import/export, reminders, search, signatures, layout design, and the other later-stage workflows remain deferred.
- A reachable real technician/trade pilot and owner visual/report review remain required by B-008 before Stage 2 can be considered product-valid or release preparation begins.
- The owner visual correction pass makes Home/Work/Customers deterministic sibling roots, preserves saveable Work-tab return context, and replaces the deferred finding page with a compact/expandable inline public finding editor.
- The root-switch visual-atomicity pass removes destination crossfades and redundant root-entry loads so root changes render as immediate, single-root replacements without a shared loading-screen flash.
- The root-freshness follow-up quietly refreshes the combined Home/Work/Customers projections on meaningful root re-entry, retaining usable content during reads and atomically publishing only a complete successful refresh.

## Known generated toolchain baseline

- minSdk 29
- compileSdk 37
- targetSdk 37
- AGP 9.3.2
- Gradle 9.5.0
- Gradle JVM/project JDK 20
- Java source/target compatibility 11
- Kotlin 2.2.10
- Compose BOM 2026.02.01

Android Studio generated newer ordinary AndroidX libraries than Routine Repeater. Preserve the ServiceLoop-generated versions unless a concrete compatibility reason requires change. Add Room/DataStore/KSP only when the persistence foundation needs them.

## SL-1 implemented

**IMPLEMENTED**

- A single-module Compose/Material 3 application with Home, Work, and Customers roots; Search, Settings, New visit, history, and incomplete-work entry points remain honest foundation destinations where later workflows are not implemented.
- Room version 1 with exported schema for Customer → Site → Equipment → ServicePlan, stable ServiceObligation identity, durable WorkingVisit/WorkItem state, captured template/checklist and identification snapshots, structurally separate public/private work drafts, explicit response dispositions, follow-ups, and stable attachment metadata/ownership.
- No destructive migration fallback and no database-open-to-empty recovery behavior.
- Manual application-container injection, repository/state-holder separation, and an injectable business clock/zone abstraction.
- Save state distinguishes Saving, Saved, and Not saved. Saved is emitted only after the Room transaction returns; cancellation is rethrown before ordinary failure handling.
- Saved issue/NA/value detail is not discarded until the technician confirms an incompatible disposition change. Cancel leaves the durable response and visible selection unchanged; confirmed writes clear fields incompatible with the new disposition in the same Room transaction.
- Re-activating an already-selected semantic response is a no-op: a specific saved NA reason is preserved instead of being replaced by the generic transition reason, an identical VALUE does not write, and the durable Saved checkpoint does not advance. Genuinely changed values still persist normally.
- Completion Review exposes fulfillment as eligible only for recurring plan work that captured a specific obligation, is Performed, and has a reviewed assigned checklist (or no checklist). One-off work has `NO_CURRENT_OBLIGATION`, no checkbox or recurring due-date effect, and any stale persisted fulfillment flag is sanitized to false in the derived review model. Partly performed and Not performed remain due.
- ServiceLoop visual tokens, status roles, typography hierarchy, non-dynamic color identity, launcher artwork, accessible headings/native controls, and compact single-column layouts.
- Root navigation uses Navigation Compose start-destination save/restore semantics. Repeated root taps are single-top, detail destinations return to their originating root, and Work's typed Due services/Visits/Follow-ups selection is saveable session/navigation state rather than Room data.
- The simple SL-1 NavHost explicitly uses no enter/exit or pop transitions. Home, equipment-list, and customer-list data are loaded together during the one initial blocking read. Later root activations start one cancellable non-blocking combined refresh; current root content remains visible, older refreshes cannot overwrite newer results, and failure preserves all prior projections without changing a successful business save. Root semantics tags support the invariant that exactly one settled root is present.
- Once root data is usable, unrelated detail loading/errors cannot replace a visible root with the global blocking/error screen; this also protects a quick Back from a detail load.
- Issue found exposes its persisted public description inline: approximately three lines by default, approximately ten when expanded, explicit expand/collapse accessibility semantics, and a deliberate Save finding action. Unchanged issue text is a no-op; changed or cleared working-draft text persists through the existing truthful save path. The obsolete `foundation/finding` route is removed.
- Android automatic backup/device-transfer rules exclude the Room database and owned attachment/report paths so an OS subset is not represented as the future complete ServiceLoop recovery package.

**DEBUG-FIXTURE-DRIVEN**

- The representative Harbor Fitness dataset, V-001/V-002, five plan obligations, captured checklist revision, working answers/outcomes, follow-up, and attachment metadata exist only under `src/debug` and are inserted through the same Room DAO/repository paths.
- `src/release` supplies `NoOpStartupSeeder`; production sources contain no fictional customer dataset.
- Search, Settings, new-visit creation, customer detail/CRUD, report history, richer finding lifecycle, and follow-up detail remain labelled foundation destinations rather than fake completed workflows.

## Verification evidence

**DEVELOPER-VALIDATED — automated**

- `gradlew.bat :app:testDebugUnitTest`: PASS — 30 tests, 0 failures/errors/skips, including initial root loading, in-flight quiet-refresh visibility, atomic fresh projection replacement, refresh-failure preservation/save separation, and all prior inspection/completion cases.
- `gradlew.bat :app:assembleDebug`: PASS.
- `gradlew.bat :app:lintDebug`: PASS.
- `gradlew.bat :app:assembleRelease`: PASS, including compilation of the release no-op fixture factory.
- `gradlew.bat :app:assembleDebugAndroidTest`: PASS; both focused canonical-AVD Compose runtime tests passed together with 0 failures.
- Room schema version 1 generated at `app/schemas/com.v16studio.serviceloop.data.ServiceLoopDatabase/1.json`.

**DEVELOPER-VALIDATED — canonical emulator owner-feedback regression**

- AVD display name: `Pixel 10a ServiceLoop`; resolved internal identifier: `Pixel_10a_ServiceLoop`; dynamically resolved serial for this run: `emulator-5556`, state `device`.
- adb: `C:\Users\daemo\AppData\Local\Android\Sdk\platform-tools\adb.exe` 37.0.1. The exact debug APK was installed with explicit `-s emulator-5556`; no other emulator or physical device was used.
- PASS: saved Belt condition issue detail prompted before Issue found → OK. Cancel retained Issue found, its reason, and the prior Saved checkpoint. Confirm wrote OK, cleared the issue reason, and showed Saved only after persistence; force-stop/cold-reopen retained coherent OK with no stale detail.
- PASS: saved numeric VALUE → Not applicable prompted before discard; confirm cleared the numeric value and retained only the new NA reason.
- PASS: all required root sequences rendered the requested root with its matching selected item: Home → Work → Home; Home → Customers → Home; Work → Customers → Home; Customers → Work → Home; Home → Work → Customers → Work; Home → Customers → Work → Customers. A repeated Customers tap produced no visible navigation oddity.
- PASS: after disabling NavHost transitions and consolidating initial root reads, repeated Home → Work → Customers → Home, Home → Customers → Work → Home, and rapid Work ↔ Customers switches showed one complete requested root in captured post-switch frames, the matching selected item, and no `Reading saved service book` flash. The instrumented test repeated switching and asserted exactly one root semantics container after every settled change.
- PASS: after a real durable inline-finding edit, Home's working-visit checkpoint advanced from the previously observed 16:36 to 17:07. Home and Work used the refreshed `HomeSummary` without a blocking loading flash; Work → Visits → Resume → Back returned to Visits. The strengthened device tests reject `Reading saved service book` on each settled root and after post-save Back.
- PASS: Work retained Visits after Resume working visit → Back and Booked visit → Back, Follow-ups after Follow-up → Back, and Due services after an available child destination → Back. Visits → Follow-ups → Visits also retained the correct visible selection.
- PASS: the Belt condition public finding rendered inline at about three lines, expanded to about ten lines, collapsed without content loss, and exposed `Expand finding field` / `Collapse finding field` semantics. Edited text saved, survived leave/reopen, and was restored to a useful Issue found state after the regression.
- PASS: Issue found → OK still prompted. Cancel retained issue text; confirm atomically cleared it with the new response. A focused instrumented test exercised edit/save/reopen plus both destructive-transition outcomes on the canonical AVD.
- PASS: Completion Review rendered Performed/unfulfilled as eligible and remaining due, Performed/fulfilled with the captured-interval proposed date, and Partly performed/Not performed as fulfillment-unavailable and remaining due. Finalize remained disabled with no Stage-2 business effect.
- PASS: Equipment Detail retained plan reference, interval, due date and status while exposing no raw current-obligation identity.
- PASS: tapping the already-selected Optional accessory NA response retained the specific `Not fitted` reason and did not advance the visible 13:51 Saved checkpoint; force-stop/cold-reopen retained it. A genuine TEXT transition into NA saved the required generic reason.
- PASS: recurring Completion Review cases continued to render eligible/unfulfilled, eligible/fulfilled with proposed date, and partly performed/ineligible states without null due wording.
- NOT RUN: one-off Completion Review runtime rendering because the existing representative SL-1 runtime route has no one-off work line; focused repository tests and source review provide this correction evidence without distorting the fixture.

**NOT VALIDATED / OWNER OR DEVICE FOLLOW-UP**

- No physical phone was used. TalkBack, 200% font, multi-window/foldable, light-theme, locale/RTL, and broader device-matrix inspection were not run.
- Camera/gallery/file intake, richer finding metadata/lifecycle/photos/disposition/corrective-task planning, actual owned attachment bytes, storage-exhaustion rescue actions, migrations beyond initial version 1, explicit backup/restore, final records/PDFs, and Stage 2 transaction retry behavior are not implemented or validated.

## Next boundary

Stop at SL-1. Final independent orchestrator review is required. `ServicePlan.currentObligationId` is the persisted current-obligation pointer/identity foundation; Stage 2 remains unauthorized and must implement and verify the exact live compare/consume/advance transaction. Actual finalization, obligation consumption/advancement, final records, recurrence effects, corrective-task creation, and report/PDF generation are deferred.
