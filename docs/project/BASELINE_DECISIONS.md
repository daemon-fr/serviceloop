# ServiceLoop — Adopted Baseline Decisions

**Adopted:** 2026-09-06

The owner approved the orchestrator's recommended working baseline.

## B-001 — Functional and UI/UX authority

- `ServiceLoop_Conceptual_App_Map_v0_1.md` (C) is the adopted semantic/functional baseline.
- `ServiceLoop_Conceptual_UI_UX_Design_v0_1.md` plus `ServiceLoop_UI_UX_Action_Coverage_v0_1.md` are the adopted presentation, interaction, and coverage baseline.
- The UI/UX document's explicitly labelled **FC-01**, **FC-02**, and **FC-03** additions are adopted:
  - **FC-01:** global history/report-attention discoverability via Work → More → History and Home records-needing-attention links, without adding a new root or analytics feature.
  - **FC-02:** narrow save-failure rescue actions: Open device storage settings and Copy unsaved text, with privacy limits and no false recovery promises.
  - **FC-03:** equipment moves are blocked while a related correction draft is open; the draft must be opened/resolved rather than silently retargeted.
- `ServiceLoop_Complete_Functional_App_Map_v0_1.md` (F) remains non-authoritative reference unless a later explicit amendment adopts a named F policy.

## B-002 — Saved contact notes

Once a contact note has been saved as history, it is not hard-deleted as an ordinary correction mechanism. A mistaken saved note is retained and marked **Entered in error** with a reason. This preserves historical meaning without creating a broader compliance/audit subsystem.

## B-003 — Voided report sharing

A voided original report remains viewable/exportable as historical evidence, but ordinary customer **Share** is disabled for that voided version. The current correction/void notice is the customer-facing handoff route. Superseded-but-not-voided versions retain the warning behavior defined by the adopted UI/UX design.

## B-004 — Scope boundary

Preserve the local-first solo-technician product boundary and the adopted CORE/LATER/OUT scope. Do not add a backend, accounts, team synchronization, dispatch, invoicing/accounting, inventory management, customer portals, or other excluded systems without a separate owner scope decision.

## B-005 — Development task sizing

Prefer substantial, coherent Codex assignments that deliver demonstrable workflow or stage outcomes. Do not fragment capable implementation work into many small prompts. Split only at meaningful workflow, integrity, platform-risk, or review boundaries, and always leave a reviewable checkpoint.

## B-006 — Recovery and import boundary

- ServiceLoop has one authoritative local dataset/device at a time; it is not a multi-device merge/sync product.
- The product recovery contract is an explicit portable **passphrase-protected complete backup** containing the relevant database records and owned files with version/integrity metadata.
- If source media/files are already missing, an **incomplete recovery copy** may be created only when explicitly labelled as incomplete; it must not be represented as a verified complete backup.
- Restore is a staged, verified **replacement** operation, not an automatic merge with the current dataset. Failed validation/restore must not silently replace good data.
- Directory-style import is create-only/repeatable under the adopted validation/privacy rules; it is not a hidden data merge or destructive overwrite mechanism.
- Working visits and correction drafts are durable state. Recovery architecture must account for them rather than assuming only finalized records matter.
- Standard Android/OS backup or OEM transfer must not be presented as the complete ServiceLoop recovery contract. Configure business data/files consistently with the adopted app-level recovery model.

## B-007 — Service obligation, history, and lifecycle semantics

- Adopt C's recurrence rule: the next due date is derived from the qualifying completion date plus the plan interval, subject to the adopted explicit override/correction rules.
- Each active plan has one current obligation identity at a time. Consuming/finalizing that obligation must be exactly-once even across retries.
- `Performed`/outcome and **Fulfills current obligation** are separate decisions. Performing work does not automatically mean the current scheduled obligation is fulfilled.
- One visit has one site and one actual service date. Multiple machines/plans may be handled in the visit, and partial/unperformed work preserves the appropriate outstanding obligation.
- Master-data/template edits must not silently rewrite historical inspection/report meaning. Finalized history uses immutable snapshots/versioned correction rather than in-place semantic mutation.
- Use C's conservative archive/retire/move rules plus adopted FC-03: resolve named blockers individually; do not cascade-cancel or silently retarget dependent history/drafts.
- For the corrective-task description ambiguity: the public finding is the required corrective description. A follow-up itself requires title/date/context and may also carry private planning notes; do not invent a second mandatory public description field.

## B-008 — Pilot and report validation boundary

- Keep the fixed general customer report design as the implementation target unless later amended.
- A reachable real technician/trade must evaluate the first complete working service loop/report before Stage 2 is considered product-valid and before release preparation. Agreement between AI agents is not a substitute for that pilot evidence.

## B-009 — Inline inspection finding description

- For an ordinary inspection **Issue found** response, edit and view the primary public finding description directly beneath the inspection item rather than requiring navigation to another screen.
- The field is a compact multiline editor by default with a visible control that expands the same field for long descriptions and collapses it again without changing its content.
- This inline description remains the public corrective description source under B-007. Do not invent a second mandatory public description; later richer finding metadata, photographs, disposition, corrective-task planning, and lifecycle features remain permitted where adopted.
