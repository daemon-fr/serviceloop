# Dispatch packages prototype v2

**EXPERIMENTAL — NOT PRODUCT BASELINE — NOT APPROVED FOR MERGE**

This isolated branch tests asynchronous coordinator-to-technician work handoff through readable, unsigned `.slwork` JSON files and Android sharing. Technician IDs provide deterministic normal import routing, not authentication or tamper resistance. It adds no backend, accounts, live synchronization, shared database, status-return protocol, messaging, presence, cross-device locking, or separate Desk product.

## Identity and coordinator directory

Every dataset has a stable opaque Technician ID in Room, initialized once from the Business Profile technician name when available. Renaming the display name does not change the ID. Settings can copy the ID or share a small `.sltech` file through a narrowly scoped cache directory. Complete backup/restore includes the identity; erase removes it so a genuinely new dataset can receive a new identity.

Coordinator tools remain off by default. Their durable directory stores technicians by Technician ID. Teams are many-to-many, and each membership independently carries `isLeader`, so a team may have zero, one, or multiple leaders and a technician may belong to several teams.

## Durable outbox and generations

The coordinator outbox is Room-backed and does not create normal planner Visits. Every outbox Visit and item receives a stable `dispatchVisitId` or `dispatchItemId`. Selected teams expand to a deduplicated participant set and leader set at export time; the package freezes IDs and names.

Generation belongs to the Visit. First export is generation 1. Re-exporting an unchanged material snapshot keeps its generation; exporting after a material change advances exactly once. `packageId` is transport provenance only and never participates in Visit/item identity.

## `.slwork` format 2

Format 2 carries explicit local appointment time plus IANA zone, frozen team/participant/leader snapshots, stable item IDs, and per-item assignees. An empty assignee list means every participating technician. Assignees must belong to the selected-team participant union. The codec bounds bytes, counts and strings and validates dates, times, zones, uniqueness, membership, and directory references. V1 files are rejected.

## Import and updates

Preview is read-only and shows the local identity, directory classifications, recipient role, generation state, and conflicts. Ordinary technicians receive explicitly assigned/everyone items; selected-team leaders receive every item, with unassigned items marked `LEADER_VISIBLE`. A participant with no applicable items gets no local Visit unless leader visibility applies.

`DispatchVisitBinding` is keyed by `dispatchVisitId`. Same generation/same material is current; same generation/different material conflicts; older generations do not roll back; higher generations update the same still-unmodified Booked local Visit. Local rescheduling conflicts conservatively. Working, finalized, cancelled, or participation-complete Visits reject automatic rewriting and do not duplicate.

Imported work never claims recurrence. After Start, choosing **Document this item** attempts the strict local current-plan/obligation match; a safe match claims locally, otherwise the item remains one-off. Leader-only items default to observe-only and do not block completion.

## Documentation handoff and reports

Handoff records only that this installation will not document the item. Eligible targets are other assignees (or participants for everyone items) plus Visit leaders. It does not notify the target, prove acceptance, reassign physical work, or establish exclusivity. Substantive local draft/evidence requires explicit discard; undo is available while Working. If all assigned items are deferred and no item is locally documented, **Finish my involvement** moves the Visit to `PARTICIPATION_COMPLETE` without a final record, PDF, fake Not performed result, or recurrence effect.

Finalization includes only `DOCUMENT_LOCAL` items. Deferred and leader-observe items are omitted, not converted into failures. Immutable final snapshot tables retain dispatch Visit/item IDs, generation, manager reference, sender, documenting Technician identity, assignment meaning, and assigned-name/ID snapshots. The customer PDF adds a restrained Dispatch section. Independent devices may therefore produce valid parallel reports for the same dispatch item without local duplicate suppression.

Completed eligible PDFs may still use **Send to office** or ordinary Share through Android’s chooser. This does not prove delivery, and the B-003 void/superseded restrictions remain authoritative.

## Recovery and prototype boundary

Room schema v7 adds identity, directory/team, outbox, imported binding/disposition, and immutable final-dispatch tables. They participate in dirty tracking, complete backup, isolated restore validation, foreign keys, and erase. Temporary `.slwork`/`.sltech` cache output is excluded.

The UI is intentionally compact and tester-oriented. Before product adoption it needs a dedicated design pass for full outbox editing, explicit possible-duplicate branch decisions, richer generation diffs, durable pending-generation reconciliation, and accessibility/polish. Dispatch remains outside the adopted ServiceLoop product baseline.
