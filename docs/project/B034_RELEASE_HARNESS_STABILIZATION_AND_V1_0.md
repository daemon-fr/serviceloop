# B034 — Final Harness Stabilization and v1.0 Release Gate

Status: **BLOCKED — v1.0 was not promoted**

Date: 2026-09-18

Branch: `codex/b013-ui-overhaul`

Protected release refs were left untouched. No `master` fast-forward, `v1.0` tag, or GitHub Release object was created.

## Scope

B034 was limited to Android/JVM test-harness stabilization and release evidence. No production Kotlin, resources, manifest, schema, or migration files were changed.

The harness changes address:

- deterministic geometry assertions at representative widths and font scales;
- explicit Compose content disposal before in-memory Room teardown;
- opt-in render-evidence and historical-fixture gates;
- non-blocking render artifact capture in semantic suites;
- IME/lazy-list synchronization;
- isolation of persisted UI-filter preferences in the Due Services projection test;
- a pure JVM regression check for the inspection-status pair allocation threshold.

## Evidence

### Local Gradle gate

All required local checks passed after the final harness edit:

- **PASS** `gradlew.bat :app:testDebugUnitTest` — 423 JVM tests, 0 failures/errors.
- **PASS** `gradlew.bat :app:assembleDebug`.
- **PASS** `gradlew.bat :app:assembleRelease`.
- **PASS** `gradlew.bat :app:lintDebug`.
- **PASS** `gradlew.bat :app:assembleDebugAndroidTest`.
- **PASS** `git diff --check` — only normal LF/CRLF conversion warnings were reported.

### Canonical AVD inventory

The canonical `Pixel_10a_ServiceLoop` AVD was resolved dynamically as `emulator-5554` and used with explicit `-s` arguments.

A complete default-argument instrumentation inventory completed before the final Stage C lazy-list correction:

- 192 observed instrumentation statuses;
- 169 passed;
- 21 expected opt-in skips;
- 2 failures.

The two failures were:

1. `StageC1PortraitImeTest#portraitImeKeepsLowerServiceEditorAndPrimaryActionReachable` — the test waited for an off-screen lazy-list child without first scrolling its owning list. The test was corrected afterward to use `performScrollToNode(...)`, but the corrected version could not be rerun because the AVD became unusable.
2. `B026LocalFlexibleWorkUiTest#registerRevealShowsMarkedOneTimeRowsAndUncheckingHidesThem` — `ActivityScenario` teardown remained `PAUSED` and failed to reach `DESTROYED`.

The Due Services projection test passed after its persisted UI-filter reset was added. The earlier cross-test failure was caused by a remembered `Has visit`/`No visit` filter, not by the Room projection. The final full inventory could not be repeated after the last Stage C edit.

### AVD infrastructure blocker

After the long inventory, the canonical AVD lost its Android package service. Two explicit reboots and bounded readiness waits left `ro.boot.qemu.avd_name=Pixel_10a_ServiceLoop` responding, but `sys.boot_completed` empty and the package service absent. A framework restart was rejected with `Must be root`; adb-root was unavailable on the production build. No wipe, reset, recreation, substitute emulator, or physical device was used.

Therefore the corrected Stage C test and the remaining B026 teardown behavior have no final on-device rerun evidence.

## Evidence classification

- **IMPLEMENTED** — harness-only changes listed above.
- **TESTED** — local Gradle gate and 423 JVM tests.
- **DOMAIN-INSTRUMENTED** — partial canonical-AVD evidence, including Due Services, dispatch, completion, private-note, owner, and Stage 3 paths.
- **UI-INSTRUMENTED** — not a clean final gate: the pre-final-correction inventory had the two failures above.
- **SYSTEM-HANDOFF** — not run as a release gate; handoff cases remained opt-in/guarded.
- **HUMAN/RENDERED** — not run. Native Android Studio/emulator visual control was unavailable in the connected computer-use surface, and the canonical AVD became unavailable before screenshot inspection.

## Known gaps and intentionally excluded actions

- `sl3RuntimeJourney=true` was not run.
- Historical fixture validation was not enabled in the default inventory. Its known V-001 mismatch remains: actual migrated visit state `WORKING` versus fixture expectation `COMPLETED`. This is a **KNOWN HISTORICAL FIXTURE GAP — NOT A V1.0 PRODUCT FAILURE**; the fixture was not changed.
- Real Calendar provider mutation was not run.
- Notifications were not exercised.
- System sharesheet, picker, camera, dialer, email, maps, and photo-picker handoffs were not used as release evidence.
- No release promotion was attempted after the gate became blocked.

## Required continuation

Recover the same canonical `Pixel_10a_ServiceLoop` AVD without wiping or recreating it, then:

1. rerun the corrected Stage C test;
2. repair and rerun the B026 `ActivityScenario` teardown failure;
3. rerun the complete default instrumentation inventory;
4. run the opt-in render suite with `-e renderEvidence true`;
5. run the historical fixture check with `-e historicalFixture true` and record the known V-001 mismatch;
6. rerun the final local gate after the last test edit;
7. only then consider pushing `master` and creating annotated tag `v1.0`.
