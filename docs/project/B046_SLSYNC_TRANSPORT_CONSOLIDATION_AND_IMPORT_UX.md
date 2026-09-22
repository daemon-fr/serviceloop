# B046 — `.slsync` Transport Consolidation and Import UX

**Status:** IMPLEMENTED on `codex/b046-slsync-transport-consolidation` for owner review  
**Start SHA:** `9e189905d0fedb795639938af4e521ae2be8ea63`

## Result

`.slsync` is now the only ordinary ServiceLoop exchange extension. Its v1 manifest routes three purpose-specific payload contracts:

- `FULL_WORKSPACE` — the unchanged B045 seven-section workspace contract;
- `WORK_ASSIGNMENT` — the existing v5 `DispatchPackageCodec` JSON wrapped as `working.json`;
- `TEMPLATE_SHARE` — the existing `InspectionTemplateCodec` JSON wrapped as `inspections.json`.

`ServiceLoopSyncEnvelopeCodec` owns ZIP safety limits, duplicate/unsafe-entry rejection, manifest validation, and declared-section access. The mature Dispatch and template payload codecs remain internal and their preview/import/conflict semantics are unchanged.

## User-facing consolidation

- Home exposes one full-width `Import` action for normal roles.
- Settings → Data is ordered `Import / export data`, `History`, `Backup and recovery`.
- One unified Import screen decodes the selected file once, shows populated entity-family counts, applies role gating, and routes by manifest purpose.
- FULL_WORKSPACE selection is replacement semantics: all visible families start selected, Business profile is locked, dependency parents are selected transitively, and unchecked file content is omitted rather than preserved locally.
- Work assignments use the mature incremental Dispatch importer; generation updates do not become workspace replacement or duplicate Visits.
- Template exchange moved to the ordinary Export data/Import flow. Inspection Templates is management-only and no longer has exchange controls or export-selection checkboxes.
- Backup and recovery contains recovery actions only, human-readable backup status, a single-row Off/1 day/7 days/30 days reminder preset, and the adopted Backup/CheckCircle/Restore action icons.
- Verify ServiceLoop file reads and validates a `.slsync` without business writes.

Dispatch export now wraps the already-prepared v5 bytes in a verified WORK_ASSIGNMENT envelope and writes `.slsync` before `commitPreparedExport`. Dispatch generation, material/source hashes, stale-preparation protection, and status meaning are unchanged. Old `.slwork`/`.slinsp` production filters, generation, and import entry points are retired; legacy payload MIME constants remain only for source/test compatibility and are not used by current external flows.

Generic update-in-place, merge/conflict synchronization, server push/pull, and complete immutable-history FULL_WORKSPACE export remain deferred. Room v16, recovery backup format, application identity, and release metadata are unchanged.

## Verification

- **TEST-VERIFIED:** focused B046 JVM gate — `B045SlsyncTest`, `DispatchPackageTest`, and `TeamRoleAndImportEntrySourceTest` passed.
- **BUILD-VERIFIED:** `:app:compileDebugKotlin` passed.
- **NOT RUN:** full regression, connected Android suite, canonical AVD smoke, and rendered owner review in this time-boxed presentation pass.
