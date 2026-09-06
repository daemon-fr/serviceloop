# ServiceLoop — Implementation State

**Updated:** 2026-09-06 — SL-1 final technical closure correction pass

## Current state

- Repository: `daemon-fr/serviceloop`; protected owner baseline remains `master`.
- SL-1 development branch: `codex/sl-1-foundation-visual-proof`.
- Required and verified starting revision: `33d350fbd6e46161616b630d766f46c4cf3b8dd7`.
- SL-1 implementation commit: `e92af2b8b715f5adcf97ddc31e9db1392470a52f`.
- SL-1 final technical closure implementation commit: `2e9e074a0dcb1d1fd00484d9c3bb1a37fd8498db`.
- Package/application ID remains `com.v16studio.serviceloop`.
- The former generated Compose starter is replaced by a runnable ServiceLoop shell, Room persistence, debug-only representative fixtures, state holders, and representative Home, Equipment detail, Inspection, and Completion Review screens.
- This is a Stage 1 foundation/semantic proof. It is not a completed service loop and does not implement the Stage 2 finalization transaction.
- The independent-review correction pass now stages destructive inspection transitions for confirmation, enforces coherent response fields at the repository boundary, routes Finding details to an honest deferred-workflow destination, derives explicit fulfillment eligibility, and removes internal obligation identity from technician-facing Equipment Detail.

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
- Android automatic backup/device-transfer rules exclude the Room database and owned attachment/report paths so an OS subset is not represented as the future complete ServiceLoop recovery package.

**DEBUG-FIXTURE-DRIVEN**

- The representative Harbor Fitness dataset, V-001/V-002, five plan obligations, captured checklist revision, working answers/outcomes, follow-up, and attachment metadata exist only under `src/debug` and are inserted through the same Room DAO/repository paths.
- `src/release` supplies `NoOpStartupSeeder`; production sources contain no fictional customer dataset.
- Search, Settings, new-visit creation, customer detail/CRUD, report history, finding editor, and follow-up detail remain labelled foundation destinations rather than fake completed workflows.

## Verification evidence

**DEVELOPER-VALIDATED — automated**

- `gradlew.bat :app:testDebugUnitTest`: PASS — 23 tests, 0 failures/errors/skips, including same-answer/checkpoint preservation, changed-value persistence, destructive-transition staging/coherence, one-off sanitization, and recurring fulfillment eligibility/recurrence cases.
- `gradlew.bat :app:assembleDebug`: PASS.
- `gradlew.bat :app:lintDebug`: PASS.
- `gradlew.bat :app:assembleRelease`: PASS, including compilation of the release no-op fixture factory.
- Room schema version 1 generated at `app/schemas/com.v16studio.serviceloop.data.ServiceLoopDatabase/1.json`.

**DEVELOPER-VALIDATED — canonical emulator targeted correction regression**

- AVD display name: `Pixel 10a ServiceLoop`; resolved internal identifier: `Pixel_10a_ServiceLoop`; dynamically resolved serial for this run: `emulator-5556`, state `device`.
- adb: `C:\Users\daemo\AppData\Local\Android\Sdk\platform-tools\adb.exe` 37.0.1. The exact debug APK was installed with explicit `-s emulator-5556`; no other emulator or physical device was used.
- PASS: saved Belt condition issue detail prompted before Issue found → OK. Cancel retained Issue found, its reason, and the prior Saved checkpoint. Confirm wrote OK, cleared the issue reason, and showed Saved only after persistence; force-stop/cold-reopen retained coherent OK with no stale detail.
- PASS: saved numeric VALUE → Not applicable prompted before discard; confirm cleared the numeric value and retained only the new NA reason.
- PASS: Finding / issue details opened the labelled SL-1 foundation destination, which stated that no finding or follow-up was created.
- PASS: Completion Review rendered Performed/unfulfilled as eligible and remaining due, Performed/fulfilled with the captured-interval proposed date, and Partly performed/Not performed as fulfillment-unavailable and remaining due. Finalize remained disabled with no Stage-2 business effect.
- PASS: Equipment Detail retained plan reference, interval, due date and status while exposing no raw current-obligation identity.
- PASS: tapping the already-selected Optional accessory NA response retained the specific `Not fitted` reason and did not advance the visible 13:51 Saved checkpoint; force-stop/cold-reopen retained it. A genuine TEXT transition into NA saved the required generic reason.
- PASS: recurring Completion Review cases continued to render eligible/unfulfilled, eligible/fulfilled with proposed date, and partly performed/ineligible states without null due wording.
- NOT RUN: one-off Completion Review runtime rendering because the existing representative SL-1 runtime route has no one-off work line; focused repository tests and source review provide this correction evidence without distorting the fixture.

**NOT VALIDATED / OWNER OR DEVICE FOLLOW-UP**

- No physical phone was used. TalkBack, 200% font, multi-window/foldable, light-theme, locale/RTL, and broader device-matrix inspection were not run.
- Camera/gallery/file intake, actual owned attachment bytes, storage-exhaustion rescue actions, migrations beyond initial version 1, explicit backup/restore, final records/PDFs, and Stage 2 transaction retry behavior are not implemented or validated.

## Next boundary

Stop at SL-1. Final independent orchestrator review is required. `ServicePlan.currentObligationId` is the persisted current-obligation pointer/identity foundation; Stage 2 remains unauthorized and must implement and verify the exact live compare/consume/advance transaction. Actual finalization, obligation consumption/advancement, final records, recurrence effects, corrective-task creation, and report/PDF generation are deferred.
