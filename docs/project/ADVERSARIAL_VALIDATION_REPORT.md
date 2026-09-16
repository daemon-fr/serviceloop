# ServiceLoop Adversarial Validation Report

Date: 2026-09-16

Branch: `codex/b013-ui-overhaul`

Starting SHA: `2546b86e84a5102385b20bd4b31708fbbb486e8c`

Final validated source/test SHA: `b7eaa7408a41389cae4e3428da5733d8f683a0a9`

Protected `master` SHA: `1fd51131b040ab62af3874c1106615b75f3008fe`

The validated source/test SHA contains the campaign tests and harness corrections. A later documentation-only commit adds this report and the final matrix; the delivery handoff records the final branch HEAD after that commit.

## Result

No production defect was confirmed. The source review, isolated fault-injection tests, complete local suite, ordinary connected suite, focused connected attacks, and separate system handoffs found no reproducible violation of the adopted lifecycle, recurrence, finalization, Dispatch, history, or privacy rules.

**Pilot readiness: ready for a bounded pilot of the tested local single-technician workflows.** The report’s coverage gaps and provider limitations remain visible below; this is not a claim that every listed hostile permutation or external provider configuration was exercised.

## Authority and Git

- Repository: `D:\Repeater\ServiceLoop`
- Starting branch/SHA/upstream: `codex/b013-ui-overhaul` / `2546b86e84a5102385b20bd4b31708fbbb486e8c` / `origin/codex/b013-ui-overhaul`; starting divergence was `0 0`.
- Protected `master` remained at `1fd51131b040ab62af3874c1106615b75f3008fe`.
- Campaign test commit: `b7eaa7408a41389cae4e3428da5733d8f683a0a9` (`Add adversarial integrity and UI coverage`).
- The matrix and this report are committed separately as documentation. The final pushed HEAD and upstream parity are in the delivery handoff.
- Known IDE-generated `.idea` changes were kept unstaged and uncommitted.

## Campaign size and execution

The matrix has **35 attack rows** across Visit lifecycle, recurrence, completion truth, checklist state, autosave, finalization, Dispatch input/generation/lifecycle, immutable history, backup/restore/import/export, attachments, business time, large datasets, providers, system handoff, and adaptive UI. It was derived from the required product documents, Room model and DAO, repository, autosave coordinator, finalization/recovery/Dispatch/report paths, and the starting test inventory.

| Evidence group | Result |
|---|---|
| Local unit tests | **400 passed**, 0 failures, 0 errors, 0 skipped, 32 suites |
| `:app:assembleDebug` | PASS |
| `:app:assembleRelease` | PASS |
| `:app:lintDebug` | PASS |
| `:app:assembleDebugAndroidTest` | PASS |
| Ordinary connected inventory | **179 total: 170 passed, 8 explicit gate skips, 1 historical persistent-fixture failure** |
| Focused connected adversarial set | **6/6 passed** |
| Isolated Dispatch UI class after AVD recovery | **3/3 passed** |
| Separate system-handoff opt-ins after AVD recovery | **6/6 passed** |
| `git diff --check` | PASS |

The eight ordinary connected skips were the opt-in gates for `preservedDue`, `sl3RuntimeJourney`, and the six `systemHandoff` cases. The connected runner reported 179 tests and one failure; those gate conditions and the known fixture classification account for the remaining cases. Opt-in handoffs were run separately with test-owned fixtures.

The six focused connected adversarial cases were:

1. Checklist answer/reason switching with Room-backed saved and raw-draft assertions.
2. Failed calculated next-due recovery followed by retry after choosing PARTLY/Keep due.
3. Finalization through UI with durable final-record persistence before UI publication.
4. Service A/B navigation and return to the Visit overview.
5. Invalid numeric draft restored over the canonical numeric value.
6. Dispatch editor selection of known one-time registered equipment.

The separate system-handoff cases were dialer, maps, email, SMS, camera, and photo picker. Each used a single-case invocation, verified the external surface, and asserted that the corresponding contact/follow-up/photo business-row counts did not change.

## Adversarial test changes

Four deterministic unit cases were added to `Sl2IntegrityTest.kt`:

- One recurrence boundary table covering month-end clipping, leap years, year changes, DAYS/WEEKS/MONTHS/YEARS, large valid intervals, and invalid intervals.
- Three SQLite-trigger fault cases after finalization writes begin: after final work insertion, during Plan advancement, and after Plan advancement when Visit completion is blocked. Each checks rollback of records, revisions, final work, Plan, obligation, claim, and Visit state, then retries and checks exact-once results.

The connected inventory remains 179 declarations; no new instrumentation declaration was needed. Existing connected tests were strengthened to verify durable checklist fields after navigation and rapid disposition changes, wait for a durable final-record row before checking UI publication, wait for the current email/map destination control, and use the actual stable Dispatch/Service tags plus lazy-list scrolling.

## Confirmed product defects

**None.** No production source was changed.

## Harness and environment findings

- A Dispatch UI test attempted to click a matching site row while the search IME covered it and before the lazy-list row was composed. The test now dismisses the IME, scrolls the owning picker list to the stable row tag, and then clicks it. The isolated class passed 3/3 after recovery; the full ordinary run also completed this class.
- An older handoff test used obsolete `Resume visit` and `open-field-evidence` selectors. It now follows the actual `resume-service` and Service photo controls. A readiness wait prevents the email control from being clicked before the customer detail finishes loading. The prior missing-node failure was a test synchronization issue; all six handoff tests passed after recovery.
- The checklist UI test had a transient full-run failure around a rapid N/A-to-ISSUE transition. An isolated rerun passed; the test now verifies persisted response/reason values and waits for the ViewModel projection before continuing. The strengthened case passed in the final ordinary run.
- A finalization UI test once exceeded a 5-second ViewModel publication wait although finalization had completed. It now first observes the durable Room row and then allows the UI projection up to 15 seconds. The strengthened case passed in the final ordinary run and focused rerun.
- A PixelCopy timeout was not reproduced; the exact render case passed in isolation and in the final ordinary run’s visual matrices.
- The first full instrumentation attempt aborted with `INSTRUMENTATION_ABORTED: System has crashed` during `B026DispatchV5UiTest`; this was an Android system/AVD failure, not a reported product assertion. A safe reboot was attempted. The canonical AVD disconnected, so the verified `Pixel_10a_ServiceLoop` was relaunched with its existing data and default emulator settings, without a wipe. Fresh APKs were explicitly installed to the newly resolved serial. The affected Dispatch class then passed 3/3 in isolation, followed by the complete ordinary run. A separate `Pixel 10a API 3.7` emulator was identified and left untouched.

Logs for the final local, ordinary connected, isolated connected, and provider runs are under the ignored `app/build/reports/adversarial/` directory; no logs, screenshots, APKs, or generated reports were staged.

## Historical fixture and opt-in status

- `Sl2RuntimeIntegrityTest.canonicalMigratedFixturePreservesFinalVisitAnswersIdentityAndRecurrence` remains the accepted historical persistent-fixture block. It still reports expected Visit state `COMPLETED`, actual `WORKING`. Its canonical data was not edited, and this result is not counted as a product failure.
- A separate `preservedDue=true` attempt remained a persistent fixture mismatch: expected due list `[P-004, P-002, P-003]`, observed `[P-004, P-001, P-002]`. No business data was changed.
- `sl3RuntimeJourney=true` was **NOT RUN** because it mutates the canonical persistent business dataset.
- All six `systemHandoff=true` cases passed after recovery. Email initially had a stale-screen selector race, fixed by waiting for the destination control; the corrected isolated run passed.
- Real Calendar mutation was **NOT RUN** because no disposable writable Calendar provider was available. Calendar fake-provider semantics ran in the ordinary suite. Notification permission-denial/unavailable-provider behavior was not exercised on-device.

## Remaining risk and scope

- The explicit oversized Dispatch payload and duplicate-Visit package cases remain uncovered by new campaign tests. Existing malformed, duplicate work/team/reference, unsupported-version, golden-vector, generation, and retry tests ran in the local suite.
- A deterministic concurrent attachment caption/include-versus-remove race was not added. Existing file ownership, failed-write/removal recovery, late-photo/finalization, and report-snapshot tests ran in the local suite.
- The canonical SL2 fixture and `preservedDue` fixture remain unchanged and separately classified as above.
- No disposable real Calendar provider or device permission-denial scenario was available; no result is claimed for those external provider states.

## Device and readiness

- Device: canonical AVD with display name **Pixel 10a ServiceLoop**.
- The serial was resolved from that AVD identity before each device run. The final post-recovery run resolved to `emulator-5554`; this value is a run result, not a fixed selector.
- The AVD was rebooted/relaunched without wiping data. Fresh debug and androidTest APKs were explicitly installed before the post-recovery test runs.
- **Pilot readiness: ready for a bounded pilot of the tested local workflows.** The campaign found no reproducible product integrity defect. Keep the explicit import-boundary, attachment-race, real-provider, and historical-fixture gaps in view during pilot planning.
