# ServiceLoop — Implementation State

**Updated:** 2026-09-06 — SL-1 implementation handoff

## Current state

- Repository: `daemon-fr/serviceloop`; protected owner baseline remains `master`.
- SL-1 development branch: `codex/sl-1-foundation-visual-proof`.
- Required and verified starting revision: `33d350fbd6e46161616b630d766f46c4cf3b8dd7`.
- SL-1 implementation commit: `e92af2b8b715f5adcf97ddc31e9db1392470a52f`.
- Package/application ID remains `com.v16studio.serviceloop`.
- The former generated Compose starter is replaced by a runnable ServiceLoop shell, Room persistence, debug-only representative fixtures, state holders, and representative Home, Equipment detail, Inspection, and Completion Review screens.
- This is a Stage 1 foundation/semantic proof. It is not a completed service loop and does not implement the Stage 2 finalization transaction.

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
- ServiceLoop visual tokens, status roles, typography hierarchy, non-dynamic color identity, launcher artwork, accessible headings/native controls, and compact single-column layouts.
- Android automatic backup/device-transfer rules exclude the Room database and owned attachment/report paths so an OS subset is not represented as the future complete ServiceLoop recovery package.

**DEBUG-FIXTURE-DRIVEN**

- The representative Harbor Fitness dataset, V-001/V-002, five plan obligations, captured checklist revision, working answers/outcomes, follow-up, and attachment metadata exist only under `src/debug` and are inserted through the same Room DAO/repository paths.
- `src/release` supplies `NoOpStartupSeeder`; production sources contain no fictional customer dataset.
- Search, Settings, new-visit creation, customer detail/CRUD, report history, finding editor, and follow-up detail remain labelled foundation destinations rather than fake completed workflows.

## Verification evidence

**DEVELOPER-VALIDATED — automated**

- `gradlew.bat :app:testDebugUnitTest`: PASS — 10 tests, 0 failures/errors/skips.
- `gradlew.bat :app:assembleDebug`: PASS.
- `gradlew.bat :app:lintDebug`: PASS.
- `gradlew.bat :app:assembleRelease`: PASS, including compilation of the release no-op fixture factory.
- Room schema version 1 generated at `app/schemas/com.v16studio.serviceloop.data.ServiceLoopDatabase/1.json`.

**DEVELOPER-VALIDATED — emulator**

- adb: `C:\Users\daemo\AppData\Local\Android\Sdk\platform-tools\adb.exe` 37.0.1.
- Explicit target: `emulator-5554`, state `device`, 1080×2424 at 420 dpi (`sw411dp`, compact phone).
- Exact debug APK installed with `adb -s emulator-5554 install -r`.
- PASS: cold launch; Home; Work and Customers roots; Equipment detail; Inspection; Completion Review.
- PASS: changed Guard fixing from OK to Not checked; UI reported Saved at 11:22 only after the write; force-stop/cold-reopen retained Not checked and the same durable checkpoint.
- PASS: Completion Review displayed Performed independently from an unchecked Fulfills current obligation, displayed another independently checked line, and exposed Finalize as disabled with explicit Stage 2 copy.
- The final post-review APK was installed and cold launch returned `Status: ok`; a final hierarchy capture was interrupted when another emulator app took foreground focus. Earlier targeted SL-1 scenarios remain the recorded runtime evidence.

**NOT VALIDATED / OWNER OR DEVICE FOLLOW-UP**

- No physical phone was used. TalkBack, 200% font, multi-window/foldable, light-theme, locale/RTL, and broader device-matrix inspection were not run.
- Camera/gallery/file intake, actual owned attachment bytes, storage-exhaustion rescue actions, migrations beyond initial version 1, explicit backup/restore, final records/PDFs, and Stage 2 transaction retry behavior are not implemented or validated.

## Next boundary

Stop at SL-1. Independent orchestrator review is required. Stage 2 remains unauthorized in this branch: actual finalization, obligation consumption/advancement, final records, recurrence effects, corrective-task creation, and report/PDF generation are deferred.
