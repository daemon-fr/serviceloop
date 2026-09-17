# B-033 — Search final corrections and v1.0 release acceptance

**Status:** Search correction implemented; ServiceLoop v1.0 release gate **BLOCKED**.

**Start SHA:** `81f26fdf350ea757ad42da3b54662a4028a7c22d`

## Search corrections

### Live-result query history

The live-result path wrote the current query to preferences, but the IME path and recent-selection path had separate behavior. That made the meaningful promotion rule easy to diverge and left the observed live-click workflow without one authoritative state/persistence path.

`SearchUi.kt` now centralizes promotion in `recordCurrentQuery(value: String = query)`. IME Search, live-result clicks, and recent-query selection all use it. The live-result callback promotes the normalized active query and updates the in-memory recent list before navigation. Keystrokes remain unsaved until one of those meaningful actions occurs.

Focused B026 coverage now covers:

- partial query → live result → normal navigation/re-entry → recent query;
- typing without Search or a result click does not create history;
- IME Search promotes the current partial query;
- recent selection moves the selected query to the top without duplication;
- case-insensitive live-click deduplication.

### Template item-count duplication

The DAO Search projection encoded `"N items"` in the Template subtitle while `SearchResultRow` independently rendered the structured `itemCount` as metadata. The Template subtitle is now blank and `itemCount` remains the structured integer. The UI therefore renders the count once while preserving current revision, status, route, and logical-template grouping.

`B033SearchCorrectionTest` verifies the current revision, reference, title/version, blank subtitle, and structured item count. The B026 Register/Search assertion verifies exactly one visible `3 items` result.

### Retained B026 Search failure

`searchFindsOneTimeRecordWithoutRegisterReveal` reproduced in isolation because prior Search UI tests persisted category-collapse state in the application-private `serviceloop_search_ui_preferences`. The test then entered Search with the Customer category collapsed. This was test isolation contamination, not a production Search defect. B026 now clears recent history and seeds all Search categories expanded before each test. The corrected B026 class passed **32/32** on the canonical `Pixel_10a_ServiceLoop` AVD.

The one-time Search path remains global and independent of the Register one-time-customer filter.

## Verification

### Local gate — PASS

The final local command passed:

```text
:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug :app:assembleDebugAndroidTest
```

- Unit tests: **422 passed, 0 failures, 0 errors, 0 skipped** across 37 XML suites.
- Debug APK: built.
- Release APK: built as `app/build/outputs/apk/release/app-release-unsigned.apk`.
- Android-test APK: built.
- Lint: passed.
- `git diff --check`: passed before release review.
- Build identity remains `versionCode = 1`, `versionName = "1.0"`.

### Focused canonical connected coverage — PASS

The canonical AVD display name was resolved dynamically to `emulator-5554` for each device run, and device commands used explicit `adb -s emulator-5554`. The physical phone and unrelated emulator were not used.

- B026 Search/Register/flexible-work class: **32/32 passed**.
- Home operational-count semantic correction: **1/1 passed**.
- Stage 3 unsaved create/edit/back protection: **1/1 passed**.
- OwnerVisual response-draft switching/restoration: **1/1 passed**.
- Representative large-font geometry: **1/1 passed**.
- PixelCopy large-text completion case: **1/1 passed in isolation**.
- Template projection unit test and B031 Search/Register unit coverage: passed.

### Broad connected gate — BLOCKED

The first complete broad run reached **207 tests** and reported five failures. The Search B026 class was green. Three failures were stale test assumptions corrected in this change: the Home test still expected pre-operational-dashboard labels, OwnerVisual still expected the retired Home → “Resume service” route, and Stage 3 used a direct lazy-child scroll/ambiguous Edit selector. The fourth was an ActivityScenario teardown state flake; its affected geometry/cleanup cases passed when isolated where the case completed. The fifth is the documented historical SL2 fixture mismatch (`WORKING` versus expected `COMPLETED`) and was not changed.

Two fresh broad attempts did not produce a clean final inventory:

1. One run stopped at coordinator case 64 when ActivityScenario teardown remained `PAUSED`; the exact coordinator case passed **1/1** in isolation.
2. A clean rerun passed the previous coordinator point but stalled in the existing width-sampling geometry case `selectionDoesNotSwitchPairModeNearAllocationThreshold`. The exact case stalled twice in isolation after clean app/test-process starts. A separate representative large-font bounds case passed **1/1**. The same rerun also observed a PixelCopy capture flake (the exact case passed in isolation) and a gateway `captureToImage()` redraw timeout.

These are existing connected-test/render-harness failures outside the Search correction, but the required broad gate does not have a clean completed inventory with zero unexplained connected failures. Production geometry and unrelated visual tests were deliberately not changed to force this narrow Search release through.

Because the broad release gate remains blocked, `master`, the `v1.0` tag, and their remotes were left untouched. `IMPLEMENTATION_STATE.md` was not updated to claim v1.0.

### Rendered smoke — PARTIAL

The existing canonical test-owned light captures were pulled and visually inspected locally: grouped Search with Recent searches, and Register Templates both show the template item count once. An existing dark Home capture was also inspected and showed the operational dashboard with readable state colors and controls. A dedicated dark Search capture and the complete Home/Work/Register/Create Visit/Template/Visit/Settings smoke matrix were **NOT RUN** after this correction because the connected release gate was blocked.

## Git handoff

- Correction branch: `codex/b013-ui-overhaul`.
- Protected `master` and `v1.0` tag: unchanged during this blocked acceptance.
- `.idea/deviceManager.xml` was left unstaged as Android Studio workspace noise.
- This note records the exact blocker and the safe next step: resolve or quarantine the existing geometry/render instrumentation stall, then rerun the complete canonical suite before master/tag promotion.
