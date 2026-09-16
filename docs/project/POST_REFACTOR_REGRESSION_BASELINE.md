# ServiceLoop Post-Refactor Regression Baseline

Date: 2026-09-16  
Status: CONDITIONAL PASS — ordinary regression green; one persistent-fixture block and eight expected assumption skips remain.

## Scope and authority

This gate covers the post-refactor ServiceLoop implementation on branch `codex/b013-ui-overhaul`. The acceptance boundary was taken from:

- `docs/project/SOURCE_OF_TRUTH.md`
- `docs/project/BASELINE_DECISIONS.md`
- `docs/project/IMPLEMENTATION_STATE.md`
- `docs/project/REFACTORING_CHECKPOINT_0.md`
- `docs/project/REFACTORING_CHECKPOINT_1.md`
- `docs/project/REFACTORING_CHECKPOINT_2.md`

The gate preserves the ServiceLoop loop: customer → site → equipment → service plan → due work/arrangements → working visit → service record/report → next obligation/follow-up. It does not add backend, account, team-sync, dispatch, invoicing, accounting, inventory, or customer-portal scope.

## Git and device boundary

- Starting revision: `54e729513f0ac91cb9bea428bb4999cbebddc42f`
- Implementation correction commit: `e816208` (`test: close post-refactor regression harness gaps`)
- Branch: `codex/b013-ui-overhaul`
- Protected `master` was not changed.
- Canonical AVD: `Pixel_10a_ServiceLoop` (display identity: `Pixel 10a ServiceLoop`)
- The serial was resolved dynamically from the AVD identity on each device run. The final run resolved it to `emulator-5554`; this is evidence for that run, not a hard-coded device rule.
- The physical device and unrelated emulator were not targeted.
- The canonical AVD was stopped and restarted once after an Android framework crash. It was not wiped, reset, recreated, or uninstalled.

APK artifacts built and explicitly installed with the resolved serial:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

Observed SHA-256 hashes:

```text
app-debug.apk              AE8816F6EC47274E76BB123EC185BD4DF8983F5E9059ECF7E25E8626FCCB5FA2
app-debug-androidTest.apk  71128BC5B769CFD97A205216871B0FF7A3B6B055E4ED0AF7125BA42E323B26CD
```

## Inventory

- 33 Kotlin source files under `app/src/androidTest/java`.
- 179 `@Test` declarations in the source inventory.
- 34 test suites in the final connected-test XML.
- The XML is authoritative for the final count: 179 test cases, 170 passed, 8 skipped, 1 failed, 0 errors. The runner's terminal event counter displayed 187 and was not used for acceptance totals.

## Verification commands

| Check | Result | Evidence |
|---|---|---|
| Local unit/build/lint gate | PASS | `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug :app:assembleDebugAndroidTest` completed successfully. |
| Post-commit Android-test compilation | PASS | `:app:assembleDebugAndroidTest` completed successfully after the final source commit. |
| Explicit debug APK install | PASS | `adb -s <resolved-serial> install -r app/build/outputs/apk/debug/app-debug.apk` |
| Explicit Android-test APK install | PASS | `adb -s <resolved-serial> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` |
| Canonical exact-once execution | PASS | `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.v16studio.serviceloop.CanonicalServiceLoopExecutionTest`; 1/1 passed. |
| Focused corrected UI/runtime classes | PASS | DispatchCoordinator 16/16; OwnerR1 2/2; OwnerVisual 4/4; PrivateNoteRestart 2/2; B026 flexible-work link methods 2/2. |
| Full connected regression | FAIL (classified below) | 179 cases: 170 passed, 8 skipped, 1 persistent-fixture failure; 0 errors. |
| `git diff --check` | PASS | No whitespace errors. |

Final connected XML:

```text
app/build/outputs/androidTest-results/connected/debug/TEST-Pixel_10a_ServiceLoop(AVD) - 17-_app-.xml
```

The full run was invoked with `-Pandroid.testResultsDir=app/build/outputs/androidTest-results/finalGate` for result isolation; the observed AGP output remained under the connected/debug path above.

## Canonical exact-once harness

`CanonicalServiceLoopExecutionTest` now owns its complete data setup in an isolated `Room.inMemoryDatabaseBuilder` database. It uses the real `RoomServiceLoopRepository`, the real completion/finalization path, an injectable deterministic Bucharest business clock, and the real `AndroidReportService`.

The test creates a business profile, customer, site, equipment, a three-month plan, a due obligation, and a working visit. It then:

1. saves public work and a performed completion draft;
2. finalizes once and verifies the final record, visit completion, recurrence advancement, old-obligation consumption/linkage, next-obligation creation, final counts, and claim release;
3. finalizes the same visit a second time and verifies the same record and unchanged counts/obligations, with exactly one final work item and no claims;
4. generates the report and verifies READY status, bytes, hash, page count, and output-file metadata.

The canonical persistent database is not opened, cleared, erased, deleted, or mutated by this harness. The generated report file is removed after its assertions because it is a derived test artifact; no business data is removed.

## Final connected-suite results

The following is the final XML suite inventory. `PASS` means all cases in that suite passed. `SKIP` is an explicit test assumption. `BLOCKED` is the one persistent canonical-fixture mismatch.

| Suite | Cases | Result |
|---|---:|---|
| AppearancePreferencesInstrumentedTest | 1 | PASS |
| B013R31OwnerSurfaceRenderTest | 3 | PASS |
| B013R31SelectionAndActionStackUiTest | 5 | PASS |
| B025RawDraftRestorationUiTest | 4 | PASS |
| B026DispatchV5UiTest | 3 | PASS |
| CalendarSettingsUiTest | 1 | PASS |
| CanonicalDueServicesProjectionTest | 1 | SKIP |
| CanonicalServiceLoopExecutionTest | 1 | PASS |
| CanonicalStage3DailyOperationsTest | 1 | SKIP |
| CompletionUiSemanticTest | 33 | PASS |
| DispatchCoordinatorUiTest | 16 | PASS |
| DispatchPackageInstrumentedTest | 2 | PASS |
| DueServicesProjectionUiTest | 1 | PASS |
| ExampleInstrumentedTest | 1 | PASS |
| InspectionStatusChoiceGeometryTest | 15 | PASS |
| Migration1To2Test | 11 | PASS |
| OwnerR1CorrectionEvidenceTest | 2 | PASS |
| OwnerReviewFixtureTest | 1 | PASS |
| OwnerVisualRuntimeTest | 4 | PASS |
| PortraitOnlyRuntimeTest | 1 | PASS |
| PrivateNoteRestartUiTest | 2 | PASS |
| ReminderSettingsUiTest | 2 | PASS |
| ServiceLoopAdaptiveTabsTest | 5 | PASS |
| ServiceLoopPresetChoiceLayoutTest | 1 | PASS |
| ServiceQuestionUiCorrectionTest | 5 | PASS |
| ServiceWorkspaceCoreUiTest | 13 | PASS |
| Sl2RuntimeIntegrityTest | 2 | BLOCKED: 1 pass, 1 fail |
| Stage3DailyOperationsUiTest | 12 | 6 PASS, 6 SKIP |
| StageAVisualProofTest | 3 | PASS |
| StageBRenderMatrixTest | 2 | PASS |
| StageC1PortraitImeTest | 1 | PASS |
| WorkFilterSelectorUiTest | 3 | PASS |
| ui.B026LocalFlexibleWorkUiTest | 12 | PASS |
| ui.ServiceAttentionOrderUiTest | 9 | PASS |

## Classification

### PASS

All ordinary local checks, the isolated exact-once business-flow test, the corrected focused classes, and 170 of the 179 final connected cases passed. No product failure was observed in those checks.

### Expected assumption skips — 8

These are explicit opt-in gates, not failed assertions:

- `CanonicalDueServicesProjectionTest.preservedProjectionRemainsStableAcrossAdversarialRootNavigation` — requires instrumentation argument `preservedDue=true`.
- `CanonicalStage3DailyOperationsTest.canonicalPersistentDailyOperationsJourney` — requires instrumentation argument `sl3RuntimeJourney=true`.
- `Stage3DailyOperationsUiTest.cameraHandoffReachesSystemSurface` — requires `systemHandoff=true`.
- `Stage3DailyOperationsUiTest.smsHandoffHasNoBusinessEffect` — requires `systemHandoff=true`.
- `Stage3DailyOperationsUiTest.photoPickerHandoffReachesSystemSurface` — requires `systemHandoff=true`.
- `Stage3DailyOperationsUiTest.dialerHandoffHasNoBusinessEffect` — requires `systemHandoff=true`.
- `Stage3DailyOperationsUiTest.mapsHandoffHasNoBusinessEffect` — requires `systemHandoff=true`.
- `Stage3DailyOperationsUiTest.emailHandoffHasNoBusinessEffect` — requires `systemHandoff=true`.

No system-handoff evidence is claimed in this baseline. No human/rendered visual evidence is claimed; screenshot-producing tests were treated as instrumentation assertions only.

### Environment/persistent blocked — 1

`Sl2RuntimeIntegrityTest.canonicalMigratedFixturePreservesFinalVisitAnswersIdentityAndRecurrence` failed at `Sl2RuntimeIntegrityTest.kt:28`: the persistent canonical fixture returned visit `V-001` as `WORKING` while the test expects `COMPLETED`.

This is a persistent fixture/environment block, not a product failure. The exact-once correction deliberately stopped mutating the canonical database, so satisfying this stale fixture expectation would violate the gate's data-ownership rule. The test remains visible and must be resolved by an owner-approved fixture policy or a separately authorized fixture refresh.

### Harness flake / environment disturbance

An earlier broad attempt encountered an AndroidX ActivityScenario cleanup timeout (`PAUSED` instead of `DESTROYED`) followed by an emulator framework outage (`Can't find service: activity`). The same canonical AVD was restarted without wipe/reset/recreation. The final broad rerun completed without those disturbances; they are recorded as harness/environment flakes and are not part of the final XML failure count.

### Product failure

None observed. The Gradle task is red only because the one persistent SL2 fixture assertion remains blocked.

## Corrections included

- `CanonicalServiceLoopExecutionTest.kt`: test-owned in-memory Room fixture and real idempotent finalize/recurrence/report assertions.
- `DispatchCoordinatorUiTest.kt`: lazy-list synchronization and a stable site-picker scroll anchor.
- `DispatchCoordinatorUiV2.kt`: stable `dispatch-site-picker-list` test tag only.
- `OwnerR1CorrectionEvidenceTest.kt`: durable-save and follow-up-list synchronization plus corrected current semantics/tags.
- `OwnerVisualRuntimeTest.kt`: lazy-list synchronization and durable N/A response/reason assertions.
- `PrivateNoteRestartUiTest.kt`: durable-save synchronization and scoped isolated test database.
- `B026LocalFlexibleWorkUiTest.kt`: state-driven inspection route and equipment identity assertions.
- `FollowUpContactUi.kt`: stable follow-up-detail list test tag only.

These changes are test/harness reliability and stable semantic anchoring. They do not redesign the product or alter business behavior.

## Acceptance verdict

CONDITIONAL PASS. The post-refactor ordinary regression surface is green, the canonical exact-once business flow is isolated and passing, and no product failure was found. Acceptance is not unconditionally green because one pre-existing persistent SL2 fixture expectation is blocked and eight explicitly opt-in assumption tests were not run.

## Git handoff

- Implementation commit: `e816208`
- Baseline-document commit: the commit introducing this file; record its full SHA in the final task handoff.
- Push target: `origin/codex/b013-ui-overhaul` only.
- Protected baseline: `master` remains unchanged.
- IDE-generated `.idea` device/Gradle metadata was preserved as known workspace noise and was not staged.
