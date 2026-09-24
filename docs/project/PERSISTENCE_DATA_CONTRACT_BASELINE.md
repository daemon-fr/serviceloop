# ServiceLoop Persistence and Data-Contract Baseline

**Date:** 2026-09-24
**State:** Implemented and tested candidate for independent review; not owner-accepted, released, or merged to a protected branch.

## Lineage and authority

- Reviewed B049 source/evidence base: `codex/b049-work-exchange-reporting-export-center` at `13552b917d62a453f1840a5a8121742c9b11e420`.
- Repair branch: `codex/persistence-web-foundation-audit`, based on that B049 descendant. The final R05/R07 implementation and R11 focused-test checkpoint is `546e2d50480c7ad2423def9ce985f5652746b56b`.
- Protected refs were verified unchanged after fetch: local/remote `master` at `1fd51131b040ab62af3874c1106615b75f3008fe` and local/remote B049 at `13552b917d62a453f1840a5a8121742c9b11e420`. The repair branch did not yet have an origin ref at this pre-push check; its authorized push creates that ref, with final parity recorded in the handoff.
- [Acceptance ledger](PERSISTENCE_REPAIR_ACCEPTANCE_LEDGER.md) is the detailed R01–R14 method/assertion and evidence index. Earlier B049 verification remains historical evidence for its own checkpoint; this document records the persistence follow-on and does not rewrite it.
- [AI Codex Prompt, Execution, Review, and Handoff Standard](AI_CODEX_PROMPT_AND_HANDOFF_STANDARD.md) v2.1 and `AGENTS.md` contain the 2026-09-24 risk-based verification amendment: later edits invalidate only affected evidence.

## R01–R14 crosswalk

This is the persistence assignment's candidate baseline, not a claim that live owner data was found damaged. Source-supported product gaps were repaired or qualified; the named tests and evidence are in the ledger.

| Requirement | Concrete contract now present | Evidence / qualification |
| --- | --- | --- |
| R01 / D01 | Safe populated Room 14→15 migration; additive Room 19→20. | Five canonical AVD migration tests preserve child rows, binding identity, hashes, FK validity, nullable columns, and indexes. |
| R02 / F01,D02 | Recovery format 2 reads schema 9–19, validates source version/table declarations before normalization, and authenticates the complete archive. | Synthetic 9–19 projections and malformed/tamper/rollback tests pass. Genuine historical `.slbackup` execution is unavailable; see Compatibility limits. |
| R03 / F02,D03 | Recovery/erase inventory covers all six app-owned business roots and distinguishes required, missing, corrupt, shared, and external bytes. | Root replacement, coalescing, incomplete/corrupt rendition, cleanup, and repeat-backup tests pass. |
| R04 / D04,D12 | Android extraction allowlists appearance only; device-local reminder, Calendar, and cleanup bindings are scoped to dataset/adoption identity. | OS allowlist and rollback/replacement tests pass. Calendar provider mutation was not run; the fake provider test is not represented as a live-provider result. |
| R05 / F02,D05 | Result/native file adoption is journaled and reconciled; native, readable, and WORK_RESULT exports capture one Room snapshot and encode outside its read transaction. | Four adoption-journal tests, exporter/importer regressions, backup-race check, WORK_RESULT Android class, and final shared gate pass. |
| R06 / F03,D07 | Canonical final-photo derivatives stay byte-stable across restart, cleanup, Recovery, and relay; originals remain separately owned. | Existing hash-preservation JVM and Android codec/relay evidence passes. |
| R07 / F04,F06,D07 | Revision-time follow-ups and complete v2 source records use a shared versioned projection, privacy-equivalent cross-route identity, and exact-replay representation recovery. | Cross-route public/private equivalence, contradiction rejection, redaction, canonical JSON, native replay recovery, and WORK_RESULT replay tests pass. |
| R08 / F07,D06 | Result semantics, captured recurrence, issuer/trust/author/assignment, lifecycle, and transactional eligibility are checked before effects. | Negative semantic, stale/canceled, independent-author, correction, and applied/replay assertions pass; the Android result journey passes. |
| R09 / F05,F10,D07,D08 | Replay compares immutable photo facts/bytes; committed outcome/status supplies import feedback. | Mixed committed feedback, transfer retry conflict, and Android applied-then-replayed assertions pass. |
| R10 / D09 | Native source identity is typed by entity kind; nulls, optional refs, hierarchy, aliases, and conflicts preserve their domain meaning. | Same-token multi-type, literal `null`, nullable-reference, hierarchy, and no-partial-write tests pass. |
| R11 / F02,D10 | Recovery explicitly validates local, remote, transferred, evidence, report-owner, and rendition-owner links; missing/corrupt files remain truthfully incomplete. | Existing graph/file tests plus the new exact REMOTE, TRANSFERRED, evidence-result, and rendition-owner rejection test pass. No new production defect was found in this final coverage addition. |
| R12 / F08,D10 | Evidence and immutable history associate by exact source execution whichever arrives first, including across Recovery. | JVM both-order/retry/hash assertions and final canonical AVD both-order-after-Recovery run pass. |
| R13 / F09,D11,D12 | Report line identity and provenance remain stable; source chronology, void handling, frozen aggregates, and rendition retry retain prior truth. | Existing distinct-line, chronology, void, frozen-rendition, and visually inspected Android PDF evidence passes. The PDF journey was not repeated. |
| R14 / D12 | CSV, business-zone bounds, coherent exports, deterministic contracts, and representative scale limits have bounded evidence. | Final broad gate passes. Previously completed 250-branch/120-booking, 12-photo codec, CSV, and golden-fixture evidence remains applicable. Unallocated extreme byte/expanded-ZIP/million-row cases remain explicit. |

### Defect, evidence, and external-evidence separation

- **Product behavior repaired:** the assignment's source-supported migration, Recovery/versioning, owned-file, retry, result validation/replay, typed-identity, snapshot/privacy, association, and reporting gaps are implemented on the repair branch. This records code behavior, not observed loss in a live owner database.
- **Coverage closed without a product change:** the final R11 exact-owner graph cases were absent from existing assertions, so one focused synthetic Recovery regression was added. R14's shared-code gate was then run once after stabilization. Previously completed migration, workload, rendered-PDF, and system-handoff journeys were carried forward where their inputs were unchanged.
- **External evidence limitation:** no genuine historical schema 9–18 `.slbackup` package was supplied or exists in the repository fixtures. The 9–19 projection test is synthetic compatibility evidence only. It is not historical-package verification.
- **Not-run boundaries:** no live writable Calendar provider, physical-device notification delivery, owner-data anomaly repair, or maximum-size archive/photo/row allocation is claimed. These are accurately described in the ledger; none was substituted with a fake provider or synthetic package claim.

## Persistence versions and migrations

| Surface | Candidate version and compatibility |
| --- | --- |
| App | `1.3.0`, versionCode `5` (unchanged) |
| Room | v20; ordinary additive 19→20 migration; generated `20.json` matches fresh/migrated schema |
| Encrypted Recovery | Container format 2; schema 19 writer and explicit schema 9–19 readers |
| `.slsync` envelope / DATA_TRANSFER metadata | Version 2 / version 2 |
| WORK_RESULT | Emit v2; accept valid v1 and v2 |
| PERFORMED_WORK | Emit v2; accept valid v1 and v2 |
| Other DATA_TRANSFER families | Version 1; prior readers retained |
| FULL_WORKSPACE / Dispatch | Register v3 with v2 read compatibility / Dispatch v5 |

Room 20 adds exactly four nullable columns: `final_work_items.followUpsSnapshotJson TEXT`, `remote_final_results.sourcePayloadJson TEXT`, `transferred_final_results.sourcePayloadJson TEXT`, and `retained_images.originalDeletionRequestedAtEpochMillis INTEGER`. A null legacy snapshot means unavailable history, never a known-empty list. Existing rows, IDs, paths, aggregate references, and unrelated indexes are preserved. The old two-column unique indexes are replaced by unique `(exporterId,resultId,sourceFinalRevisionId)` and `(technicianId,resultId,sourceFinalRevisionId)` indexes; no deterministic receiver ID is substituted for author identity.

The 14→15 migration first discovers and asserts the expected inbound foreign-key dependency closure, snapshots rebuilt parents and dependent child tables to temporary tables, drops dependent tables child-first, rebuilds the parent tables with their accepted v15 definitions, restores preserved values, and recreates child definitions/indexes. It does not disable foreign-key enforcement inside the migration transaction. The Room migration tests compare populated business values and exact dependent child/binding rows, then validate foreign keys. Migration 19→20 is additive and has an explicit Room registration; destructive fallback remains prohibited.

Recovery schema validation first checks the declared supported source version, table order/uniqueness and source-era shapes. It normalizes only after that validation, then checks graph and file declarations before adoption. Unknown newer schema/envelope/family versions reject before mutation. Null source/follow-up columns remain null; no guessed backfill occurs.

## Stores, ownership, and crash behavior

| Store | Authority and recovery contract |
| --- | --- |
| `serviceloop.db` / Room | Authoritative business state: directory, plans/obligations, drafts, final revisions, receipts/results, transfer provenance, rendition descriptors, retention markers, and Recovery metadata. |
| `filesDir/attachments` | Original working/final evidence bytes. A finalized copy and its working source retain separate immutable ownership where the contract requires it. |
| `filesDir/reports` | Final-record PDF renditions referenced by Room. Missing/corrupt descriptors do not claim READY bytes. |
| `filesDir/retained-images` | Verified customer-report derivative used before an original can be removed. Deletion intent is durable before unlink. |
| `filesDir/remote-results` | Imported WORK_RESULT photo bytes, separately referenced from local execution and keyed by author/result/revision/photo identity. |
| `filesDir/aggregate-reports` | Aggregate PDF renditions linked to frozen report/source records. A good prior rendition remains preserved on failed replacement. |
| `filesDir/transferred-evidence` | Native DATA_TRANSFER evidence bytes, linked to exact origin/Visit/WorkItem/revision facts. |
| `filesDir/recovery` | Operational encrypted-backup stage/rollback journals and business-file adoption journal; not portable business history and excluded from OS extraction. |
| Device-local preferences/provider links | Appearance is the only OS-extracted preference. Reminder, Calendar, cleanup, role, search/filter, URI-grant, and handoff state remains local and dataset-scoped as applicable. |

`BusinessFileCoordinator.mutex` is the outer serialization boundary for operations that coordinate Room and owned bytes. Within it, services stage/verify or precompute bounded photo derivatives, capture/update Room state in a transaction, then finalize/encode. No new service starts a file mutation by entering Room and then acquiring the file mutex. Native/readable/result exporters bind prepared derivatives to the captured Room hashes; a mismatch rejects the export. Package/ZIP encoding follows the coherent Room capture.

New remote-result and transferred-evidence adoption writes an atomic journal containing format version, UUID operation ID, dataset ID, adoption token, and each owned target/stage path, SHA-256, and byte size. Targets are restricted to `remote-results/` and `transferred-evidence/`. Bytes are flushed, verified, then atomically moved before Room commits references. A committed reference plus matching target survives reconciliation after process death; an uncommitted adoption removes only journal-owned matching staged/target bytes. A malformed, cross-dataset, ambiguous, or content-mismatched journal is preserved and sets restricted Recovery state instead of deleting evidence. Database startup resolves Recovery replacement first, then reconciles business-file adoption before normal app use. Failed imports reconcile under `NonCancellable` while preserving committed references.

Recovery replacement retains its separate commit/rollback journal. Backup capture rechecks the source Room snapshot before claiming a complete archive. Owned-root inventory coalesces only identical descriptors, reports missing/corrupt owned bytes as incomplete, and never removes external/unowned files. Original-image cleanup records deletion intent, honors the current dataset and `Never` policy, and does not expire working evidence.

## Immutable exchange, equality, and privacy

Author, assignment, and source-execution namespaces stay distinct:

- **Author:** normalized exporting/technician ServiceLoop ID; receipt and result uniqueness includes author.
- **Assignment:** issuing Coordinator ID plus Dispatch Visit/item, generation/material identity, target, and trust decision. Assignment tokens are not authorship proof.
- **Source execution:** original workspace, source Visit, source WorkItem, and immutable final revision. Receiver-local directory IDs, package IDs, randomized section names, and local import time are not source identity.

WORK_RESULT v2 and PERFORMED_WORK v2 preserve a versioned `FinalSourceSnapshot`: recorded chronology, source refs, captured subject/work/recurrence, ordered checklist/findings/parts/follow-ups, photo facts, correction lineage, and separate private work/revision notes. A result record carries immutable source-photo identity and descriptors independently of transported derivative bytes. Native re-export uses that saved semantic record; projected Room fields remain indexes/report inputs. The detailed accepted field map is in the ledger's “Frozen v2 source field map.”

`FinalSourceProjectionV2` compares common public source facts across direct WORK_RESULT and relayed PERFORMED_WORK. Private facts are compared when both representations carry them. A redacted public relay is compatible with a complete private source, but is not interpreted as empty/private-null; contradictory private facts conflict when both sides retain them. Transfer-time receiver links and section names do not enter immutable equality. Unknown legacy null remains unknown.

Exact replay of an identical v2 source can recover a missing legacy `sourcePayloadJson` representation only after fingerprint/identity equality is rechecked in the transaction. It does not create another receipt, revision, recurrence effect, local visit, or history row. New readers must retain the v2 snapshots and privacy semantics to relay losslessly; older v1 readers cannot be claimed to accept a v2 payload.

Canonical fixtures are synthetic and contain no owner data: `app/src/test/resources/contracts/work-result-v2-record.json`, `work-result-v2-canonical.json`, and `work-result-v2.sha256`. Tests pin ordered UTF-8 canonical bytes and digest, v2 decode/encode behavior, and invalid DONE semantics. Generated package/section IDs remain random in production.

## Exports and reports

Native DATA_TRANSFER is additive and conflict-aware: omission does not delete receiver state; current bound data that changed is not overwritten silently. Readable Export Center ZIP is explicitly unencrypted, scoped, CSV/image output and not Recovery. CSV text is escaped/quoted, including formula-like text; dates use the captured business zone and inclusive boundaries. Private fields require explicit selection. Customer report models include only PUBLIC and explicitly included photos.

Aggregate reports bind stable source kind/entity/revision and work-line identity, preserving separate same-label services and technician attribution. Source/branding snapshots remain frozen. A verified prior PDF is reused; missing/corrupt/incomplete/failed rendition state stays explicit and never claims bytes are READY. The previously generated one-page Android PDF was rendered with Poppler and visually inspected for two distinct `Annual service` lines and Alice/Bob attribution; this rendering was not repeated during closeout.

## Verification record

The final production/test source checkpoint is `546e2d50480c7ad2423def9ce985f5652746b56b`. Documentation-only edits after it do not invalidate application evidence.

| Evidence | Exact check and result |
| --- | --- |
| Focused JVM closeout | `:app:testDebugUnitTest --tests '*BusinessFileAdoptionJournalTest*' --tests '*B049WorkResultExportTest*' --tests '*B049DataTransferRoundTripTest*' --tests '*RecoveryLegacyCompatibilityTest*' --offline`: 40 tests, 0 failures/errors/skips. |
| Planned broad gate | `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline`: 579 JVM tests, 0 failures/errors/skips; both APK tasks and lint PASS. |
| Canonical device | Final debug APK SHA-256 `8B1AEB8899A93D406785BFA623050687D6F93A0EDC7394532CF1873CAFD79574`; explicitly installed with `adb -s <dynamically resolved serial> install -r` on the existing `Pixel 10a ServiceLoop` AVD. Delayed startup PID and sampled AndroidRuntime fatal-log check PASS; no AVD reset/data clear. |
| Android association | `B049DataTransferEndToEndInstrumentedTest.twoWorkspacesMergeSafeCurrentStateAndImmutableHistoryThenRelayOriginalOrigin`: 1 canonical AVD test PASS on Android 17; both evidence/history orders after Recovery, exact links and one report photo. |
| Android WORK_RESULT | `B049WorkResultEndToEndInstrumentedTest`: 2 canonical AVD tests PASS; real Android image codec/result export/import/replay and partial-to-complete flow. |
| Earlier qualifying evidence carried forward | Five canonical AVD migration cases; Recovery/device/file and bounded workload cases; actual Sharesheet/Picker/DocumentsUI handoff; rendered PDF visual inspection; 250-branch/120-booking and 12-photo workload. These journeys were not repeated because their inputs were unchanged. |

Gradle's normal worktree launch could not access the shared Gradle file-hash lock. The lock and process ACLs were inspected read-only; it was not deleted. The same targeted and broad commands then passed via the approved elevated PowerShell runner. The first AVD config lookup checked the wrong metadata file and stopped before device access; the display name was then resolved from each AVD's own `config.ini`. A first startup check used a PowerShell automatic variable name for PID and therefore was not credited; the corrected `appPid` check passed. A device listing also showed a connected physical Pixel phone, which was not used.

## Compatibility limits and owner gates

- Genuine historical Recovery packages from schema 9–18 remain **unavailable**; the synthetic compatibility projections prove modeled transformations only. No historical verification is claimed and no further search is pending.
- Live writable Calendar-provider mutation, physical-device notification delivery, and maximum-size archive/photo/row extremes were not run. Fake-provider coverage remains labeled fake; count boundaries and representative workloads are the evidence actually run.
- No live owner data was repaired or discarded. Any real anomaly discovered later must be retained and assessed before correction.
- Independent review, owner acceptance, the B-008 pilot, protected-branch merge, release, and any future web adapter remain separate gates. `master` and B049 are not modified by this candidate.
