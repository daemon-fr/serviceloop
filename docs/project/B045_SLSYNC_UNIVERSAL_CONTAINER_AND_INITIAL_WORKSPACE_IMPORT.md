# B045 — `.slsync` Universal Container and Initial Workspace Import

**Status:** IMPLEMENTED on `codex/b045-slsync-initial-import` for owner review
**Start SHA:** `5a947da7ea6e61e32040f1afddaeba865473b2d5`
**Adoption:** new explicit owner decision for B045; this document records the implementation of that decision.

## Boundary

B045 adds a semantic ServiceLoop interchange container for a trusted initial full-workspace replacement import. It does not replace or reinterpret the existing S37 CSV directory import, `.slwork`, `.slinsp`, Recovery Backup, or any existing local-first workflow.

The container is an ordinary ZIP with MIME `application/vnd.serviceloop.sync+zip` and exactly these v1 entries:

`manifest.json`, `business.json`, `register.json`, `inspections.json`, `plans.json`, `team.json`, `visits.json`, and `followups.json`.

The manifest format is `ServiceLoopSync`, version `1`, purpose `FULL_WORKSPACE`, and declares exactly the seven semantic sections. Decoder limits are 16 MiB compressed input, 32 MiB expanded content, and 16 entries. Duplicate, unsafe, unexpected, missing, malformed, or semantically unsupported entries are rejected before Room mutation. No hashes, signatures, conflict engine, binary files, or selective sync are part of B045.

## Imported semantic state

- Business profile is imported as the `primary` profile with the import timestamp.
- Customers, Sites, and Equipment preserve supplied IDs and references.
- Current reusable-template revisions are imported with deterministic revision/item IDs; revision history is not imported.
- ACTIVE Service Plans receive one new open obligation, with the imported due date, sequence `1`, deterministic obligation ID, and no consumption. This keeps Due Services immediately functional.
- Imported dispatch technician/team directory rows are created, including leader membership. The device-local Technician Identity row is preserved.
- Booked local Visits are imported as `BOOKED` with customer/site/business snapshots, canonical appointment epoch conversion, plan claims, immutable ad-hoc template snapshots, and empty public/private drafts. Claims never consume obligations during import.
- Dispatch drafts are created as never-exported active outbox drafts. Plan items derive Equipment, task, plan reference, and due-date snapshots without copying the recurring template ID. Team and item-assignee bindings are imported.
- Initial OPEN follow-ups and contact notes are imported; contact-note instants are converted to epoch milliseconds and no follow-up history event is created.

All decoded content is validated before mutation. The importer serializes with `BusinessFileCoordinator.mutex` and applies replacement in one Room transaction. It clears portable business tables in the authoritative `RecoveryPackage.TABLE_ORDER` order, inserts the imported workspace, and renews recovery metadata. The preserved rows are `technician_identity`, `reminder_preferences`, and the recovery singleton itself. A successful import receives a new dataset UUID, resets backup verification/attempt fields, preserves the backup reminder interval, clears restricted/incomplete/adoption state, and records the import timestamp as the first and last business write.

Physical attachment/report cleanup is intentionally deferred. Old files may remain orphaned after their business rows are replaced; no Recovery journal is required for this pre-release initial importer.

## User entry and legacy coexistence

Settings → Data and recovery exposes **Import ServiceLoop sync** and uses the Android document picker with the sync MIME plus ZIP/octet-stream fallbacks. `ACTION_VIEW` and `ACTION_SEND` accept `.slsync`; MainActivity classifies sync files before `.slinsp` and existing `.slwork` files. The import screen previews title, purpose/date, concise section counts, and the replacement warning, then offers **Import workspace** and **Cancel**. Restricted recovery remains a hard block. Success refreshes root projections and returns Home; failure stays on the import screen with a safe message.

No production export UI is included in B045. `ServiceLoopSyncCodec.encode()` exists for round-trip and future producer coverage.

## Verification

- `B045SlsyncTest`: 3/3 PASS — codec round-trip, successful replacement/preservation/recovery reset, and invalid-reference rollback.
- `:app:compileDebugKotlin`: PASS.
- `:app:assembleDebug`: required final gate for this milestone.
- `git diff --check`: required final gate.
- Full regression, instrumented/device, system-handoff, PDF, and rendered owner-review suites are intentionally not part of the time-boxed B045 gate.

Room remains v16; Android version metadata remains `1.0.1` / code `2`. No merge into `master`, tag, force-push, or changes to `.slwork`, `.slinsp`, Recovery Backup, or S37 CSV behavior are part of B045.
