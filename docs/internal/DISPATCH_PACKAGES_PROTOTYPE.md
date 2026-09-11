# Dispatch packages v3 — accepted integration reference

**OWNER APPROVED UNDER B-015 — BANKED ON THE AUTHORITATIVE TECHNICAL DEVELOPMENT LINE**

This document remains the detailed implementation reference for the asynchronous coordinator-to-technician Dispatch design first developed on `prototype/dispatch-v2` and now deliberately integrated with verified SL-4 on `codex/integrate-sl4-dispatch`.

Current integrated technical checkpoint:

`132a0586969dfe7e7fbe88501ba6f413ce6ef724`

Product adoption does not change the hard architectural boundary: Dispatch remains local-first and file-based. It adds no backend, accounts/login, live synchronization, push dispatch, shared database, chat, presence, centralized report ingestion, server acknowledgement, or automatic cross-device conflict resolution.

## Identity and coordinator directory

Every new dataset has a stable canonical Technician ID in Room using:

`SLT-XXXX-XXXX-XXXX-CC`

The twelve payload characters use Crockford Base32 and 60 random bits from `SecureRandom`; the two checksum characters use the first ten SHA-256 bits of `SLT:` plus the uppercase payload. The checksum detects transcription/format errors only. It is not authentication, authorization, identity proof, or server registration.

Canonical manual input tolerates case, omitted hyphens and surrounding whitespace but does not silently substitute ambiguous characters. Existing UUID and 32-hex legacy identities remain byte-for-byte preserved and routable through stored rows, `.sltech`, `.slwork`, history and recovery. Manual coordinator entry accepts only checksum-valid canonical IDs; `.sltech` remains the preferred identity handoff and legacy-compatible route.

The Technician identity screen shows the stable ID prominently with Copy/Share actions. Renaming the display name never changes the ID.

Coordinator tools remain off by default. Settings configures Technician identity, the enabled preference, explanatory copy, Office report recipient and other real settings; it is not the coordinator workspace. When enabled, Home reacts without restart and exposes equally weighted subdued filled **Technicians**, **Teams** and **Outbox** actions.

The coordinator Technician directory stores technicians by Technician ID. Same-ID/new-name import requires explicit keep/update handling. Teams are many-to-many; each membership independently records leader state, so a Team may have zero/one/multiple leaders and a technician may belong to several Teams. The Teams matrix explicitly labels **Member**, **Technician** and **Leader** columns; leader selection requires membership.

## Outbox and Visit editing

The coordinator Outbox is Room-backed and does not create normal planner Visits merely by composing/exporting packages. It is list-first: existing Dispatch Visits, search, compact status/date filters, visible checkboxes and **New visit** appear without an inline Site directory or giant creation form.

Search covers manager reference, Site reference/name and Customer reference/name. Rows sort by service date, appointment time, creation time and stable Visit ID. Empty/no-match states are explicit.

**New visit** and Visit-row taps navigate to a dedicated editor for New, Draft, Dispatched and read-only Concluded Visits. The editor uses a searchable on-demand Site picker and on-demand Team selection. Site changes that invalidate staged work require explicit confirmation and clear the affected staged items instead of silently retargeting Equipment.

Work items are staged before first persistence, use Equipment from the chosen Site, preserve stable `dispatchItemId` on edit and retain Everyone versus explicit eligible-Technician assignment semantics. Cards show actual technician names; empty assignment is shown as **Everyone**. One transactional Save validates Site, Teams, Equipment and assignees, checks optimistic editor freshness/Concluded state, and writes the Visit, Team links, items and assignees coherently.

An itemless Draft may be saved truthfully but is clearly non-export-ready. Missing prerequisites are reported explicitly rather than hidden behind a mysterious disabled primary action. Unsaved editor changes use the established ServiceLoop guard pattern.

## Dispatch identity and generation semantics

Every coordinator Visit has a stable `dispatchVisitId`; every work item has a stable `dispatchItemId`. Package transport identity never replaces those domain identities.

Generation belongs to the Visit:

- first successful export → generation 1;
- unchanged re-export → same generation;
- material edit followed by export → next generation exactly once.

A single `.slwork` package may contain many Visits with different generations.

## Coordinator lifecycle

The Outbox derives exactly four office-side states:

- **Draft** — never successfully exported into a usable `.slwork`;
- **Dispatched** — a usable package artifact containing the Visit was successfully created and export metadata committed;
- **Canceled** — coordinator cancellation is stored with a required reason. A canceled Draft is history-only; a canceled Dispatched Visit requires a newer package export;
- **Concluded** — coordinator-side administrative closure only.

Dispatched does not mean delivered, received, imported, accepted, started or completed. Concluded does not alter technician devices. Reopen returns Concluded → Dispatched and preserves prior export evidence/generation.

Batch lifecycle actions require homogeneous eligible selections and apply atomically. Draft cannot be concluded manually; Draft/Dispatched can be canceled with a reason; Canceled and Concluded are read-only.

## Selection and batch export

The active Outbox hides Concluded work by default and supports:

- status scopes: Active / Draft / Dispatched / Canceled / Concluded / All;
- date scopes: Today / Tomorrow / ISO calendar week / Next 7 days / Custom range / All dates.

Every visible Visit can be selected. **Select all** changes to **Deselect all** when every currently visible result is selected; hidden rows are not silently targeted.

One **Export selected (N)** action produces one `.slwork` containing exactly the selected Draft/Dispatched Visits, up to the 100-Visit package bound.

Export has an explicit review step showing count, date range, total work-item count, sender and per-Visit New/Unchanged/Updated generation meaning. Review itself is non-mutating.

The final export sequence remains truthful:

1. prepare selected Visit snapshots/generations without durable export mutation;
2. review the exact preparation;
3. write and verify the cache artifact;
4. transactionally revalidate Visit material and referenced directory snapshot;
5. commit export generation/hash/time;
6. hand the file to the Android system chooser.

If the preparation becomes stale, export is rejected and must be reviewed again. File/write/verification/commit failure does not falsely advance status or generation. Cancelling the Android chooser after successful artifact creation still leaves the Visit Dispatched because the artifact exists, but ServiceLoop never calls that delivered or received.

## `.slwork` v3

Format v2 remains readable unsigned JSON and is interpreted as active transport. New exports use v3, which adds per-Visit `transportLifecycle` (`ACTIVE`/`CANCELED`) and a required cancellation reason for canceled Visits. Lifecycle and reason are included in the material hash. It carries explicit local appointment time plus IANA ZoneId, frozen Team/participant/leader snapshots, stable Visit/item identities and per-item assignees. Empty assignee list means Everyone among the selected Visit Teams. Leaders receive visibility according to the adopted role semantics.

The codec bounds bytes/counts/strings and validates dates, times, zones, global Visit/item uniqueness, Team uniqueness, membership/leader consistency, participant unions, Technician-name consistency, assignment membership and directory references. Earlier v1 prototype packages are rejected.

## Technician import and generation updates

Preview is read-only and recipient-scoped. Ordinary technicians receive directory/equipment branches only for applicable work; leaders receive leader-visible items without that becoming mandatory local documentation.

If every Visit is genuinely `NOT_ASSIGNED` to the local identity, preview prominently warns:

**No work in this package is assigned to this Technician.**

The screen explains that the coordinator may not have included the technician or may hold the wrong Technician ID, and shows the local Technician name/ID for comparison. This warning is not shown for already-current, older-generation, conflict, blocked, assignment-removal or leader-visible work because those remain applicable provenance/work states for the local technician.

Possible-directory duplicates require explicit **Create separate** or **Skip branch** decisions. Exact-reference conflicts remain hard conflicts. One conflicting Visit does not block unrelated safe Visits unless they depend on the same conflicting directory branch.

`DispatchVisitBinding` is keyed by `dispatchVisitId`:

- same generation + same material → already current;
- same generation + different material → conflict;
- older generation → no rollback;
- higher generation → update the same untouched Booked local Visit if safe;
- locally changed/started/terminal Visit → no silent rewrite.

If a newer generation removes the local technician's final applicable assignment, the local Booked or Working Visit becomes `CANCELED`; local work/evidence is preserved and Completed remains Completed with provenance only.

A many-Visit package has one consolidated preview and **Apply N safe Visits**. Safe new/update/withdrawal work applies in one rollback-capable transaction; blocked/conflicting/no-op/unassigned work remains unapplied and is reported honestly.

## Documentation ownership and handoff

Imported recurring work does not claim the local Service Plan obligation at import time. After Start, **Document this item** attempts the strict current-plan/current-obligation match; otherwise the work remains one-off.

Documentation handoff is local documentation responsibility only. It does not prove recipient acceptance, reassign central work or establish exclusivity. Eligible recipients are the other applicable assignees/participants plus Visit leaders according to the adopted assignment semantics.

A successful handoff releases that item's local recurrence claim/link and clears recurrence draft state without advancing recurrence. If substantive local evidence/draft content exists, destructive cleanup requires explicit confirmation and uses rollback-safe app-owned file/database coordination. Undo does not silently reclaim recurrence.

If all assigned items are handed off and no item is locally documented, **Complete visit** moves the local Visit to `COMPLETED` without a final record, PDF, fake Not performed result, central cancellation or recurrence effect.

Parallel independent technician reports for the same dispatch item are valid.

## Final records and sharing

Finalization includes only items with local documentation ownership. Deferred/leader-observe items are omitted rather than converted into fake failures.

Immutable final Dispatch snapshots retain Visit/item IDs, generation, manager reference, sender, documenting Technician identity and assignment meaning. Customer-facing PDF output shows restrained Dispatch provenance and avoids dumping full opaque Technician IDs where a name/short stable reference is sufficient.

Completed eligible PDFs may use **Send to office** or ordinary Share through the Android chooser. This proves handoff to the chooser only, not delivery/receipt. B-003 void/superseded restrictions remain authoritative.

## Recovery, Room v12 and B-014

The integrated development line is Room **v10**.

Dispatch lifecycle/state introduced through v9 remains part of complete backup/restore and erase semantics. Room v12 additionally normalizes the unified Visit lifecycle, persists cancellation origin/reason, and retains adopted B-014 Working inspection-response draft retention:

- `issueFoundReasonDraft`;
- `notApplicableReasonDraft`;
- retained text/number Value drafts.

Migration 9→10 backfills only the active v9 Issue-found/N/A reason into the matching draft column. Inactive Working drafts survive switching and recovery, but only the current selected disposition/detail participates in checklist validity, finalization and customer-facing snapshots.

The integration milestone also corrected recovery-version truthfulness: newly created backups identify schema v10 while existing schema-v9 backups remain accepted under the established compatibility contract. Any backup-triggered creation of a previously absent Technician identity now uses the canonical SLT generator rather than a legacy 32-hex form.

Temporary `.slwork` / `.sltech` cache output is not part of the complete recovery payload.

## Integrated verification checkpoint

Current integrated technical checkpoint:

`132a0586969dfe7e7fbe88501ba6f413ce6ef724`

Recorded integration evidence includes:

- 175 host unit tests PASS;
- retained migration instrumentation 8/8 PASS through v10;
- representative populated v5→v10 and v6→v10 preservation with zero foreign-key violations;
- focused Dispatch/coordinator/B-014 instrumentation 6/6 PASS;
- canonical non-destructive instrumentation 2/2 PASS;
- debug, debug-Android-test, lint and release builds PASS;
- `git diff --check` PASS;
- combined SL-4 + Dispatch + B-014 replacement backup/restore PASS in isolated fixtures;
- malformed Dispatch foreign-key recovery structure rejected;
- integrated erase and dirty-tracking semantics verified in isolated fixtures;
- canonical AVD Home/Work/Customers and existing History/report navigation PASS without destructive dataset operations;
- no security-command block encountered.

The integration audit found no missing SL-4 production implementation. The one unique post-implementation SL-4 verification commit was semantically banked in current v10 test form rather than blindly merged/cherry-picked.

## Current status and next boundary

B-015 adopts this asynchronous Dispatch design, and the integration milestone has now banked it together with verified SL-4 and B-014 on the authoritative technical development line.

The next milestone is Stage 5 functional completion/hardening. Dispatch may be hardened as part of that stage, but its no-backend/local-first boundary remains fixed unless the owner explicitly changes it.

Current UI styling remains intentionally provisional. B-013 requires the later whole-product UI/UX pass before the real-technician pilot; Dispatch adoption/integration does not make the current appearance the final visual baseline.
