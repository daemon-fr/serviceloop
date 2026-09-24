# B050 — Pre-release clean-slate persistence and data-contract baseline

**Date:** 2026-09-25
**Status:** Owner-authorized implementation candidate on `codex/b050-legacy-data-cleanup`; acceptance and release remain separate gates.

## Authority and scope

The adopted source contract is the B050 amendment in [BASELINE_DECISIONS.md](BASELINE_DECISIONS.md). B050 starts from `6760ea9c9c8b64d2dd5dc42fb7c111770af85de5` and establishes one fresh-install pre-release baseline. No pre-release ServiceLoop business rows, Recovery backups, or exchange files need to remain readable. B048, B049, and the 2026-09-24 persistence-repair candidate remain historical evidence for their named revisions.

The app package version remains `1.3.0` / code `5`; B050 changes persistence and file-format contracts only. The owner authorized clearing `com.v16studio.serviceloop` on the existing canonical AVD before fresh-state validation and once more after instrumentation for the final empty-state launch. This does not authorize wiping or recreating the AVD.

## Current-only version contracts

| Surface | Current v1 contract | Rejected behavior |
| --- | --- | --- |
| Room | Fresh schema version 1; generated schema `1.json` only. Fresh creation initializes the existing local identity, recovery tracking, and reminder defaults. | No migration objects or registrations, version backfills, historical entity adapters, or destructive fallback. |
| Recovery | Container format 1 and database schema 1. The snapshot declares the exact current table columns/types and current row shapes; all required tables, columns, JSON types, graph links, and file declarations are validated before replacement. | No schema 9–19 table sets, row normalization, column adapters, old singleton/photo/trust/backfill readers, or incomplete-shape acceptance. |
| `.slsync` | Envelope version 1 with a mandatory canonical exporter ID. Only FULL_WORKSPACE, WORK_ASSIGNMENT, DATA_TRANSFER, and WORK_RESULT purposes are supported. | No source-less envelope, OLDER_FILE import/verification state, or TEMPLATE_SHARE purpose. |
| FULL_WORKSPACE | Payload and every section are version 1. The declared section names and versions must exactly match the current contract. | No section-specific older versions or omitted-field backfills. |
| DATA_TRANSFER | Metadata and every family use version 1. Template exchange is encoded through the current DATA_TRANSFER family contract. | No old family adapters or TEMPLATE_SHARE route. |
| WORK_RESULT | Body and results section are version 1 with complete current semantics. `originWorkspaceId`, `sourceVisitId`, `sourceWorkItemId`, source revision identity/number, and source `recordedAt` are mandatory. | No former reduced v1 record shape and no `generatedAt` chronology fallback. |
| Dispatch | WorkPackage `.slwork` and technician identity `.sltech` current format version 1. | No pre-release WorkPackage versions. |
| Immutable source snapshots | Wrapper format, source payload, and fingerprint are each version 1. Exact wrapper shape and purpose are validated before the source record is used. | No raw-record fallback, older version negotiation, replay backfill, or fingerprint inference. |

The exported Room schema is the source for Recovery table declarations. Recovery rejects an imported snapshot whose table set, column order/name/type, required values, or ownership graph differs from the current Room contract. Replacement remains staged and transactionally validated; rejected packages leave the durable dataset untouched.

## Durable source truth

- `final_work_items.followUpsSnapshotJson` is non-null and captures follow-ups at revision time, including an explicit empty snapshot.
- `remote_final_results.sourcePayloadJson` and `transferred_final_results.sourcePayloadJson` are non-null immutable v1 source snapshots whenever their current writers create those rows.
- Result chronology comes from the source result's required `recordedAt`. Package generation/import times are transport/receipt facts and do not choose report truth.
- Corrected results retain explicit revision number, source revision ID, supersession link, and correction/public-note values. Conflicting source revision identities cannot silently select a report winner.
- Snapshot decode requires exactly the current wrapper fields and current purpose/version pair. Recovery also checks each wrapper source identity against its indexed Room columns.
- Retry paths preserve immutable identity and never rewrite a finalized snapshot, invent source history, replay recurrence, or backfill a missing source representation.
- Public report facts remain structurally separate from private/internal source facts. DATA_TRANSFER privacy choices remain explicit, and omission does not erase receiver values.

## Historical B049 behavior carried forward

B050 resets the unreleased version numbers while retaining the adopted B049 business meaning: stable source execution and correction lineage; exact source identity across direct WORK_RESULT and relayed PERFORMED_WORK; typed checklist/finding/part/follow-up/photo facts; captured recurrence agreement; privacy-aware immutable comparison; trusted additive imports; idempotent retry; ordered evidence association regardless of arrival order; and graph-validated Recovery replacement. B049 evidence remains recorded in its milestone report and tests, but no B049 transport reader is used as a B050 compatibility path.

The pre-release repair acceptance ledger was removed after retaining the still-current persistence, privacy, source-identity, chronology, graph-integrity, and retry invariants above. Its branch-specific run counts and device observations remain historical evidence only and are not inherited as B050 verification.

## B050 verification record

The final broad gate passed after the last production/test change:

- **PASS — JVM-TESTED:** `:app:testDebugUnitTest`, 572 tests; 0 failures, 0 errors, 0 skipped.
- **PASS — BUILD:** `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:lintDebug`, and `:app:assembleRelease` in the one broad final Gradle invocation.
- **PASS — DOMAIN-INSTRUMENTED:** `FreshRoomBaselineInstrumentedTest` 2/2; `RecoveryJournalInstrumentedTest#atomicJournalRestoresCommittedSnapshotOnDevice` 1/1; `B049DataTransferEndToEndInstrumentedTest#twoWorkspacesMergeSafeCurrentStateAndImmutableHistoryThenRelayOriginalOrigin` 1/1; `B049WorkResultEndToEndInstrumentedTest` 2/2.
- **PASS — SOURCE AUDIT:** no production/test matches for migration registrations, old Recovery schema sets, TEMPLATE_SHARE, older-file state, legacy snapshot-recovery setters, legacy follow-up/chronology states, old template fingerprint handling, or legacy Technician ID recognition. The only Room schema export is regenerated `1.json`; `git diff --check` passed.
- **PASS — DEVICE STARTUP:** dynamically resolved the existing `Pixel 10a ServiceLoop` AVD to its live serial, installed the debug and Android-test APKs with explicit `adb -s`, and cleared only the ServiceLoop app package before testing. Fresh Room v1 created the actual app database with no business rows; table/column/index, write/read, integrity, and foreign-key assertions passed.
- **PASS — FINAL DEVICE STATE:** after instrumentation, cleared only `com.v16studio.serviceloop` again and launched `MainActivity`. The process remained alive, a new `serviceloop.db` was present, and no matching fatal startup log appeared in the sampled buffer.
- **NOT RUN — UI-INSTRUMENTED / SYSTEM-HANDOFF / HUMAN-RENDERED:** screenshot, picker/Sharesheet, workload, and performance suites were intentionally excluded by the B050 test plan.
- **NOT APPLICABLE BY OWNER DECISION:** historical Room migration and old Recovery/transport compatibility testing. Those pre-release formats were never supported; no old backups or exchange files are required.

## Remaining gates

Independent review, owner acceptance, the B-008 real-technician pilot, release, and protected `master` integration remain separate. B050 does not authorize publishing or merging.
