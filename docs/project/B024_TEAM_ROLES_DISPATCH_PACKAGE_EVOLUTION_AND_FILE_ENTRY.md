# B-024 — Team roles, dispatch package evolution, and Android file entry

**Status:** Implemented UI/file-entry decision and bounded package-design recommendation, 2026-09-12.

## Local role model

ServiceLoop persists one device-local role: `SOLO`, `MEMBER`, or `COORDINATOR`. The former `coordinator_enabled` preference migrates once: enabled becomes `COORDINATOR`; disabled or absent becomes `SOLO`. No account, remote team, or synchronization meaning is introduced.

- Coordinator retains the Home gateways for Technicians, Teams, and Outbox.
- Member receives the Home gateway for **Import work package**.
- Solo receives neither gateway.
- Import is intentionally not exposed from Work or Settings.

## Android work-package entry

The app declares bounded `ACTION_VIEW` support for the ServiceLoop work-package MIME type and `.slwork` content/file URIs, plus `ACTION_SEND` support for the custom MIME type. A received URI opens the existing import validation/review surface for a Member. It never silently applies a package. Providers that erase both the custom MIME type and filename may not resolve to ServiceLoop; broad `application/json` association is deliberately rejected because it would claim unrelated JSON files.

## Current package audit

`.slwork` v3 already carries the minimum directory snapshots needed to identify assigned work: customer name/reference, site name/address/reference, equipment name/reference/identifier/make/model/serial, visit timing/instructions, participants/teams, and item assignment/provenance. It does not carry contact details, access notes, equipment private instructions, attachments, or inspection checklist snapshots.

Reusable template revisions and working template snapshots have different ownership semantics. A reusable template is editable master data; a template snapshot is immutable visit meaning. Imported dispatched work currently creates local work items without an approved checklist snapshot. Automatically creating reusable local templates would therefore blur provenance and permit later local edits to appear equivalent to Coordinator-approved content.

## Recommended package v4

Template transport should piggyback on the main `.slwork` package, not use a separate export. The checklist is part of the assigned work's meaning and must share its generation, material hash, validation, retry, cancellation, and recipient-scoping boundary.

Recommended bounded model:

1. Add a package-level deduplicated `inspectionSnapshots` collection with a transport snapshot ID, display name, source template reference/revision as informational provenance, and ordered immutable items (`label`, `responseType`, `unit`, `required`, and private guidance only if explicitly approved for the recipient).
2. Add an optional `inspectionSnapshotId` to each work item.
3. Export v4 only after snapshot resolution succeeds for every referenced item; include all snapshot content and item references in the canonical material hash.
4. Continue reading v2/v3 with no checklist. Validate limits, unique IDs, response types, ordering, references, and text sizes before preview.
5. On import, create deterministic local `template_snapshots` and `checklist_item_snapshots` owned by the imported Visit/work item. Do not create `reusable_templates` or revisions. Preserve the Coordinator/package provenance in the dispatch binding so update review can compare snapshot changes.
6. Treat a changed checklist in a newer generation like other Coordinator-controlled material. Apply it only while the local Visit remains safely updateable; otherwise classify it as an update conflict and preserve local answers/evidence.

This pass does not implement v4 because it requires coordinated codec, canonical-hash, preview-diff, persistence, update-conflict, migration-compatibility, and recovery tests. Adding only JSON fields would create false checklist authority without safe update semantics.

## Additional entity assessment

- **Customer/site contact and access context:** useful operationally, but current package directory models do not distinguish public contact, private access, and internal notes strongly enough for automatic export. Add only an explicit minimal recipient-safe site-access snapshot in a later privacy-reviewed package version; do not export contact-note history or unrestricted private notes.
- **Equipment instructions/context:** potentially useful. Prefer a deliberately selected dispatch instruction snapshot per item, separate from local private equipment notes. Do not export all equipment notes.
- **Reusable templates:** do not export as editable master data. Export immutable, referenced inspection snapshots as described above.
- **Attachments/photos/history/reports/follow-ups:** not justified as automatic assigned-work context. They remain excluded unless a later explicit handoff workflow defines selection, ownership, size, privacy, and retry behavior.
