# Refactoring checkpoint 1 — structural change safety

**Line:** `codex/b013-ui-overhaul`, starting at `041a6c2c09eab31cc4628102ede3f04a3e773276`. The protected `master` baseline is `1fd51131b040ab62af3874c1106615b75f3008fe`. This checkpoint is structural maintenance only; it does not change the product boundary, navigation routes, persistence semantics, or UI styling.

## Scope completed

### Location-independent regression contracts

Source-contract tests no longer depend on the original implementation filenames. `ProductionSourceTestSupport.kt` locates production Kotlin by source tree and semantic markers, so contracts continue to follow moved components and screens without turning file ownership into a product requirement. The owner runtime harness was corrected to use the current `service-list`/visit-detail flow, current evidence surface, and a test-owned in-memory finalized record fixture.

### Application shell and feature screens

`ServiceLoopApp.kt` now owns application composition and shared scaffolding while navigation is in `ServiceLoopNavigation.kt`. Home/work, directory, completion, settings, business-profile, and record/report screens are in responsibility-oriented files. Routes, route arguments, effects, saveable-state keys, BackHandler behavior, and existing semantics were preserved.

### Design-system responsibilities

The oversized component file was split mechanically into brand, actions, and editors while retaining the same package-level API and visual contracts. The remaining shared components continue to use the existing token, icon, and adapter system.

### Dispatch transport boundary

Transport MIME constants, package snapshots/codecs, validators, previews, and transport status representations are in `DispatchTransport.kt`. The existing `DispatchPackageService` remains the DB-backed operations surface in `DispatchPackage.kt`; same-package declarations preserve existing callers and compatibility.

No Checkpoint 2 redesign was started: service completeness/state ownership, workspace projections, repository/ViewModel boundaries, and autosave coordinator architecture remain unchanged.

## Verification evidence

After the final code/test edits:

- `:app:testDebugUnitTest`: **PASS**, 382 tests, 0 failures, 0 errors, 0 skipped across 30 XML suites.
- `:app:assembleDebug`: **PASS**.
- `:app:assembleRelease`: **PASS**.
- `:app:lintDebug`: **PASS**, 0 errors, 56 warnings, 7 hints.
- `:app:assembleDebugAndroidTest`: **PASS**.
- `git diff --check`: **PASS**.
- Canonical `Pixel_10a_ServiceLoop` AVD, dynamically resolved and targeted with explicit `adb -s`: owner runtime **4/4**, adaptive UI **5/5**, completion semantics **33/33**.

The Stage B render matrix remains pre-existing test-fixture debt: both tests stop before rendering because the debug fixture intentionally leaves `V-001` working while the harness requires a finalized `V-001` record. No production behavior was changed to hide or bypass that precondition.

This checkpoint does not claim a full connected suite, notification delivery, Calendar-provider mutation, localization, or any Checkpoint 2 work.
