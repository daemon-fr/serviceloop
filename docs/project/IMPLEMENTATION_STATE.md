# V16 Service — Implementation State

## Current working-state pointer

The current pre-release implementation candidate is on `codex/b051-v16-service-identity`, based on the authorized B050 checkpoint. Verify repository root, branch, full HEAD SHA, remote parity, and protected refs directly with Git. The B051 identity/data contract is in [`B051_V16_SERVICE_IDENTITY_CUTOVER.md`](B051_V16_SERVICE_IDENTITY_CUTOVER.md).

## Product and data state

- Product identity: V16 Service by V16 Studio.
- Android application ID: `com.v16studio.v16service`.
- App version: `1.3.0` / code `5`.
- Room: fresh schema v1 only; no migration chain or destructive fallback.
- Recovery: encrypted current container/schema v1.
- Native exchange: `.v16service`, strict envelope v1 and four adopted purposes.
- Canonical emulator display: `Pixel 10a V16 Service`.

This implementation remains pre-release. Owner acceptance, a real-technician pilot, release authorization, and protected-baseline integration are separate gates. The repository/location transition is pending.

## Verification record

## B051 execution record

Evidence in this section is the original B051 implementation checkpoint and its impact-scoped reruns. It remains previously reported evidence and was not rerun for the documentation/test-only closeout below.

- Authorized base: `9432a02f897df3fdee4e065135018a5cd768b440`; branch: `codex/b051-v16-service-identity`. The final branch tip, upstream parity, and protected-ref SHAs are reported from a fresh Git inspection at handoff.
- Identity values are implemented as listed above. The user-facing app and report wordmarks are both `V16 Service`; code and files contain no legacy product alias.
- `python tools/phosphor/test_phosphor_tool.py`: PASS, 10 tests. The current icon manifest had three explicit alias pairs/groups missing from the test's old allowlist; the allowlist now states the existing manifest groups.
- Focused JVM identity/exchange/recovery/backup/routing checkpoint: PASS, 91 tests across 11 classes. The single planned broad Gradle invocation passed `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:lintDebug`, and `:app:assembleRelease`.
- A rendered identity review found the old split wordmark in the shared header and PDF report renderer. After updating those two labels, the affected report tests passed (8 tests), matching debug/release/lint/test artifacts were rebuilt, and the wordmark, identity surface, Sharesheet/FileProvider/DocumentsUI, and Recovery picker journeys were rerun. This was an impact-scoped rerun; the earlier broad gate remains the checkpoint for unrelated persistence and exchange behavior.
- DOMAIN-INSTRUMENTED Android evidence: Fresh Room baseline (2), Recovery journal (1), DATA_TRANSFER (1), and WORK_RESULT (2), all passing on the canonical AVD. Previously reported SYSTEM-HANDOFF/UI-INSTRUMENTED evidence includes the outgoing .v16service Sharesheet/FileProvider export and user-selected DocumentsUI import, plus Recovery, Technician ID, and wordmark journeys. The opaque-URI generic-MIME ACTION_VIEW/ACTION_SEND segments in B049SystemHandoffUiTest called MainActivity.onNewIntent directly; they were handler checks only and did not prove PackageManager resolution or Android activity delivery.
- HUMAN/RENDERED review: the new app identity screen shows `V16 Service` and a valid `V16S-…` identifier without clipping. The launcher icon artwork remains intact; its app label resolves to `V16 Service`. The three retained reference screenshots were visually reviewed; the obsolete fourth screenshot that visibly used the retired suffix was removed.
- The canonical AVD's `avd.ini.displayname` alone changed to `Pixel 10a V16 Service`; its `AvdId`, hardware configuration, and system image were preserved. It was not wiped or recreated. Only the exact legacy app/test package IDs were uninstalled; no physical device was used.
- The finite documentation disposition covers all 72 B050 documentation paths: 9 retained, 25 renamed/consolidated, and 38 removed. Seven current specialist contracts were added; local Markdown link check found no missing targets.
- Final path/content/full-byte scan covered all 9,463 staged tracked files, including binaries; unreadable files: 0; legacy identity/marker/package/MIME/table/database/AVD/source-host matches: 0; disallowed additional public-purpose suffix/MIME matches: 0. The sole current Room schema asset is schema v1 at the V16Service database class path.
- Final empty-data startup passed: the exact legacy app/test packages were absent, the new production DB passed the empty business-table and integrity check after the authorized app-data clear, and `MainActivity` launched cold and remained foreground. `git diff --check` passed.

## B051 publication and protected refs

- The owner published the two existing B051 commits unchanged: implementation commit 10ad8adccdf57b5ab5866b0482466e542683bd57 and existing handoff tip b6dab502e4bd249cacaa0c10bb2a4580e934e3c1. B051 is published; the earlier publication blocker is closed.
- Protected refs remain master 1fd51131b040ab62af3874c1106615b75f3008fe, B049 13552b917d62a453f1840a5a8121742c9b11e420, persistence audit 6760ea9c9c8b64d2dd5dc42fb7c111770af85de5, and B050 9432a02f897df3fdee4e065135018a5cd768b440.
- B051 remains pre-release. Owner acceptance, the Romanian technician pilot, and release authorization are not claimed. B052 and B053 were not performed.

## B051 independent-review closeout — 2026-09-25

- Starting revision: b6dab502e4bd249cacaa0c10bb2a4580e934e3c1. Contract and test correction commit: e1f902d3133e024c45abd95158b24bc396fb6cda on codex/b051-v16-service-identity.
- Restored explicit lifecycle outcome/readiness/draft rules, canonical Visit/Dispatch truth and the full Team action matrix, named history/flexible-work/pilot safeguards, stale WORK_RESULT evidence boundaries, Calendar provider/privacy/adoption-token rules, persistence codec and journal wording, conceptual-map precedence, and risk-based evidence validity.
- Added supported vendor-MIME PackageManager resolution and Android startActivity delivery coverage for ACTION_VIEW and ACTION_SEND in B049SystemHandoffUiTest.supportedVendorMimeViewAndSendResolveAndDeliverThroughAndroid. Both cases show the one-customer Register preview and confirm that preview does not insert the Customer before Apply. The existing generic-MIME direct onNewIntent segments are labeled handler-only.
- Production sources, debug/release sources, resources, schemas, and Gradle configuration are unchanged. The importer, manifest filters, versions, and public purposes were not changed.
- Relative links in the changed Markdown documents: PASS. git diff --check: PASS.

Build and device evidence:
- TESTED: gradlew.bat :app:assembleDebugAndroidTest --no-parallel --console=plain — PASS. Android-test APK SHA-256: 429A740498E3BDD9EE42E71903B250C557952E264C47034AC06042DA08837574.
- TESTED: gradlew.bat :app:assembleDebug --no-parallel --console=plain — PASS; this established the matching production APK. APK SHA-256: 5963637970524CB64C8CA3E3C0C0CE29270549731D26ED84C6C0DF0AAB1059B2.
- Canonical device resolution: tools/resolve-canonical-avd.ps1 returned display Pixel 10a V16 Service, AVD ID Pixel_10a_ServiceLoop, and serial emulator-5554 for this run.
- Both artifacts were installed with explicit serial commands: adb -s emulator-5554 install -r .\app\build\outputs\apk\debug\app-debug.apk and adb -s emulator-5554 install -r .\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk.
- SYSTEM-HANDOFF / UI-INSTRUMENTED: adb -s emulator-5554 shell am instrument -w -r -e class com.v16studio.v16service.B049SystemHandoffUiTest#supportedVendorMimeViewAndSendResolveAndDeliverThroughAndroid com.v16studio.v16service.test/androidx.test.runner.AndroidJUnitRunner — PASS, OK (1 test). PackageManager resolved MainActivity through each declared filter; Android delivered each package-scoped implicit intent via startActivity; the preview remained unapplied. This same-app test does not establish every third-party application's cross-UID URI grant behavior.
- The new test reuses the existing exact teardown for its temporary MediaStore URI and peer-trust entry and restores the previous role. The earlier actual Sharesheet/FileProvider export and DocumentsUI picker evidence remains previously reported; the broader B049 system tour was not rerun.

Evidence scope:
- The original B051 broad checkpoint and its later impact-scoped reruns remain as previously reported evidence above; they were not freshly rerun for this documentation/test-only closeout.
- NOT RUN for this closeout: full JVM suite, lint, release assembly, whole-app screenshot campaign, migration/Recovery campaign, Calendar-provider mutation, notification delivery, and the complete existing system tour. These inputs were not changed or required by the finite review assignment.
- HUMAN/RENDERED: NOT RUN for this closeout. No new screenshot or rendered claim is made.
- Final Git state after publication: codex/b051-v16-service-identity and origin/codex/b051-v16-service-identity were verified at parity (0 ahead / 0 behind); protected refs above remained unchanged. The exact final branch tip is reported in the task handoff.
