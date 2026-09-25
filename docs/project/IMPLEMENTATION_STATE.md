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

- Authorized base: `9432a02f897df3fdee4e065135018a5cd768b440`; branch: `codex/b051-v16-service-identity`. The final branch tip, upstream parity, and protected-ref SHAs are reported from a fresh Git inspection at handoff.
- Identity values are implemented as listed above. The user-facing app and report wordmarks are both `V16 Service`; code and files contain no legacy product alias.
- `python tools/phosphor/test_phosphor_tool.py`: PASS, 10 tests. The current icon manifest had three explicit alias pairs/groups missing from the test's old allowlist; the allowlist now states the existing manifest groups.
- Focused JVM identity/exchange/recovery/backup/routing checkpoint: PASS, 91 tests across 11 classes. The single planned broad Gradle invocation passed `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:lintDebug`, and `:app:assembleRelease`.
- A rendered identity review found the old split wordmark in the shared header and PDF report renderer. After updating those two labels, the affected report tests passed (8 tests), matching debug/release/lint/test artifacts were rebuilt, and the wordmark, identity surface, Sharesheet/FileProvider/DocumentsUI, and Recovery picker journeys were rerun. This was an impact-scoped rerun; the earlier broad gate remains the checkpoint for unrelated persistence and exchange behavior.
- DOMAIN-INSTRUMENTED Android evidence: Fresh Room baseline (2), Recovery journal (1), DATA_TRANSFER (1), and WORK_RESULT (2), all passing on the canonical AVD. SYSTEM-HANDOFF/UI-INSTRUMENTED evidence: actual `.v16service` share/import through Sharesheet/FileProvider/DocumentsUI with opaque provider URI and ACTION_VIEW/ACTION_SEND routing (2 methods); Recovery `.v16backup` CreateDocument filename and restore picker (1); `V16S` technician ID screen (1); centered `V16 Service` wordmark (1). The final branded app passed all affected journeys. FileProvider bytes, display name, provider MIME, and outgoing vendor MIME are asserted.
- HUMAN/RENDERED review: the new app identity screen shows `V16 Service` and a valid `V16S-…` identifier without clipping. The launcher icon artwork remains intact; its app label resolves to `V16 Service`. The three retained reference screenshots were visually reviewed; the obsolete fourth screenshot that visibly used the retired suffix was removed.
- The canonical AVD's `avd.ini.displayname` alone changed to `Pixel 10a V16 Service`; its `AvdId`, hardware configuration, and system image were preserved. It was not wiped or recreated. Only the exact legacy app/test package IDs were uninstalled; no physical device was used.
- The finite documentation disposition covers all 72 B050 documentation paths: 9 retained, 25 renamed/consolidated, and 38 removed. Seven current specialist contracts were added; local Markdown link check found no missing targets.
- Final path/content/full-byte scan covered all 9,463 staged tracked files, including binaries; unreadable files: 0; legacy identity/marker/package/MIME/table/database/AVD/source-host matches: 0; disallowed additional public-purpose suffix/MIME matches: 0. The sole current Room schema asset is schema v1 at the V16Service database class path.
- Final empty-data startup passed: the exact legacy app/test packages were absent, the new production DB passed the empty business-table and integrity check after the authorized app-data clear, and `MainActivity` launched cold and remained foreground. Final `git diff --check`, upstream parity, and protected-ref verification are recorded at handoff. `B052` and `B053` have not been executed.
