# ServiceLoop — B-013 Whole-Product UI/UX Implementation Plan

**Status:** Owner-authorized implementation plan, 2026-09-09  
**Working branch:** `codex/b013-ui-overhaul`  
**Base:** authoritative `master` at `1fd51131b040ab62af3874c1106615b75f3008fe`  
**Functional freeze represented by the base:** SL-5C plus accepted documentation/baseline maintenance  
**Localization:** explicitly deferred by B-017 until after B-013

## 1. Purpose

B-013 is the dedicated whole-product UI/UX overhaul required before localization and the B-008 real-technician pilot. The objective is to implement the complete supplied ServiceLoop UI/UX reference faithfully against the functionally frozen product without changing adopted business meaning merely to fit a visual design.

This is not an ordinary styling task. The reference package contains exact foundations/tokens, Material mappings, semantic-state mappings, reusable component contracts, layout/interaction patterns, screen-by-screen contracts, action/input traceability, light/dark guidance, accessibility/adaptation requirements, verification scenarios, and named reconciliation exceptions.

## 2. Single working branch policy

The owner explicitly chose one long-lived B-013 branch rather than one branch per phase.

All B-013 work stays on:

`codex/b013-ui-overhaul`

The branch covers:

1. reference-package ingestion and validation;
2. Stage A design-system/primitives/proof screens;
3. Stage B migration of every coherent screen family;
4. E03–E09 source/contract reconciliation required for truthful UI parity;
5. Stage C adaptation/accessibility/final-consistency work;
6. final B-013 conformance audit and owner review corrections.

Do **not** create B-013 sub-branches. Use coherent commits/checkpoints on this branch instead. Do **not** merge/fold this branch into `master` until the complete B-013 UI overhaul has passed final independent review and the owner explicitly accepts it.

`master` remains the authoritative accepted product baseline while B-013 is in progress. The B-013 branch is an implementation/review staging line, not a second accepted product authority.

After full B-013 acceptance, the branch may be folded into `master` once, after which localization/final-copy work proceeds under B-017.

## 3. UI reference package authority

The owner supplied **ServiceLoop UI/UX Reference v1.0**, pinned to source commit:

`1fd51131b040ab62af3874c1106615b75f3008fe`

The package is to be committed under:

`docs/ui-reference/v1.0/`

The package contract is:

- `ServiceLoop_UI_UX_Implementation_Reference_v1_0.md` — **canonical B-013 UI implementation manual**.
- `ServiceLoop_UI_Tokens_v1_0.json` — normative mechanical companion for literal tokens, Material mapping, semantic adapters and calculated pairs.
- `ServiceLoop_UI_Coverage_v1_0.csv` — normative mechanical acceptance/traceability ledger for enumerated actions, variants and inputs.
- `ServiceLoop_UI_Audit_v1_0.json` — package-validation/evidence-boundary record.
- `ServiceLoop_Source_Manifest_v1_0.json` — pinned source/evidence manifest.
- `ServiceLoop_UI_UX_Implementation_Reference_v1_0.html` — self-contained reading edition; useful for human review but not an independent design authority.
- `references/*.png` — the four supplied reference mockups used for visual character/proof; they do not define an authoritative px→dp scale.
- `README.txt` — package navigation and source/acceptance boundary.

Business semantics and explicit owner amendments in `BASELINE_DECISIONS.md` still outrank visual emphasis. The new reference supersedes the older UI document's visual language where the package says so, but it does not silently authorize changes to business rules, toolchain, recovery semantics, Dispatch boundaries, reminder semantics or Calendar semantics.

## 4. Faithful-implementation rule

For presentation decisions, implement the canonical manual **as specified**, not by taste.

Where the manual supplies a token, role, component contract, layout rule, interaction rule, semantic-state mapping, accessibility behavior or screen contract, do not substitute an undocumented alternative merely because it is easier or more familiar.

"To the letter" does **not** mean pixel-copying the 618×1328 mockup images. The manual explicitly states that no authoritative px→dp scale exists. Use the exact dp/sp/token/minimum/adaptation rules and reference-screen character instead of fictional OS chrome or literal screenshot pixel geometry.

No broad visual deviation is accepted silently. A necessary deviation must be recorded in the working discrepancy inventory with:

- reference ID/section;
- affected surface/component;
- reason;
- source/business constraint;
- proposed handling;
- evidence;
- whether owner resolution is required.

## 5. E03–E09 reconciliation gate

The reference explicitly identifies E03–E09 as source/adopted-behavior discrepancies or evidence gaps, not styling choices.

They must be checked against the actual working source before claiming UI parity:

- **E03:** adopted surfaces not fully exposed;
- **E04:** appointment date-only/timed/business-zone semantics in current shortcuts;
- **E05:** explicit Save versus durable-autosave truthfulness;
- **E06:** contact inheritance and creation-field coverage;
- **E07:** report/evidence display, including caption wrapping and adopted logo exposure boundary;
- **E08:** first-N truncation / full-list reachability;
- **E09:** truthful Due/status labels and optional provider evidence.

For each item classify the actual source state as:

- already reconciled / false positive at current source;
- presentation-only repair;
- bounded adopted functional repair;
- consequential unresolved discrepancy requiring owner decision.

Do not draw dead controls for pending functionality and call them implemented. Do not use previous "functional freeze" prose to waive a named adopted action. Conversely, do not use a pending reference entry to invent out-of-scope architecture.

## 6. B-013 execution sequence on the single branch

### Stage A — design system, adapters and representative proof

Create a small `ui/designsystem/` layer matching chapter 15 of the canonical manual:

- `ServiceLoopUiTokens`;
- typography/geometry constants;
- complete Material 3 mapping;
- semantic state adapters;
- shared C01–C36 primitives actually needed;
- thin adapters replacing duplicate presentation primitives without moving business decisions into generic components.

Prove first, in both Light and Dark:

1. Outbox — unselected;
2. Outbox — selected;
3. Dispatch Visit editor;
4. Review export;
5. Working checklist stress proof with Issue/N/A retained buffers and failed-save state.

Stage A is a review checkpoint, not the end of B-013. Continue on the same branch only after the proof system is accepted/reconciled.

### Stage B — coherent screen-family migration

Migrate in this order:

1. navigation / directory / setup;
2. fieldwork / finalization;
3. History / correction / recovery / report;
4. remaining Dispatch / Calendar / reminders / settings states.

Use the coverage CSV as the acceptance ledger. Every relevant row must end either implemented/tested or explicitly retained in the discrepancy inventory with a truthful disposition.

### Stage C — adaptation, accessibility and final consistency

Run the manual's width/font/IME/theme/accessibility matrix and V01–V19 implementation verification families as applicable. Perform the complete representative journeys described in chapter 15. B-008/V20 remains external evidence and cannot be satisfied by AI/emulator/owner-only review.

### Stage D boundary

B-013 ends with UI/copy structure frozen in English. Only then does B-017 localization migrate/finalize strings and Romanian app/report/notification/Calendar/handoff copy. Localization is not part of this branch's current B-013 implementation scope unless the owner explicitly amends B-017.

## 7. Regression boundary

Throughout B-013 preserve established semantics, including at minimum:

- Customer → Site → Equipment → Plan → obligation → Visit → record/report → next obligation;
- B-002 entered-in-error contact-note history;
- B-003 voided/superseded report sharing rules;
- B-007 recurrence/fulfillment separation;
- B-009/B-010 inline + expanded long-text behavior;
- B-011 booking/restore semantics;
- B-014 separate durable response buffers and active-only final content;
- B-015 file-based Dispatch/generation/handoff/participation semantics;
- B-016 one-way device-local Calendar projection;
- SL-4 History/correction/recovery integrity;
- SL-5A time/reminder behavior;
- SL-5C functional-hardening fixes and release no-fixture behavior.

A generic UI primitive must never decide business eligibility from colors or labels. Repository/domain validation remains authoritative at commit.

## 8. Git/checkpoint discipline

Before every substantial B-013 bundle:

- verify branch = `codex/b013-ui-overhaul`;
- verify it descends from the accepted prior checkpoint on the same branch;
- verify remote parity before editing;
- inspect worktree and known IDE noise;
- do not switch to or move `master`;
- do not create another milestone branch.

Use coherent commits on this same branch. Push the branch after each accepted/reviewable checkpoint. No force-push or history rewrite.

The owner/orchestrator may independently inspect/review each checkpoint without merging it.

## 9. Evidence discipline

Keep implementation evidence categories distinct:

- SOURCE-REVIEWED;
- UNIT TESTED;
- DOMAIN-INSTRUMENTED;
- UI-INSTRUMENTED;
- SYSTEM-HANDOFF;
- HUMAN/RENDERED;
- NOT RUN.

Reference-package HTML rendering/contrast calculations are document checks only; they are not Android runtime evidence.

Do not overclaim real notification delivery, real Calendar provider mutation, TalkBack behavior or B-008 technician-pilot evidence unless actually performed.

## 10. Current immediate task

The immediate coding checkpoint is **B-013 Stage A — Design System + Representative Proof** on the existing single branch.

Before source implementation, install and validate the exact supplied reference package under `docs/ui-reference/v1.0/`, verify its pinned commit/hash inventory, then read chapters 1, 3, 5, 6, 8, 9, 10, the proof-surface contracts in chapter 11, chapter 14 and chapter 15.

## 11. Owner correction — Work filter system (adopted 2026-09-11)

The three Work queues use one compact, temporal-first filtering pattern:

1. Search;
2. two equal peer filter selectors in one row where width and text scale permit;
3. matching result records.

Each selector presents its stable group label, current value, and a single-choice anchored menu. `All` clears only that dimension. The two dimensions and search query compose with logical AND; a valid empty result is shown normally rather than disabling combinations.

| Work tab | Temporal selector | State / relationship selector | Defaults |
| --- | --- | --- | --- |
| Due services | `Due date`: All, Overdue, Today, Due soon, Upcoming | `Visit`: All, No visit, Has visit | All + All |
| Visits | `Date`: All, Overdue, Today, Upcoming, Past 30 days | `Status`: All, Booked, Working, Completed, Canceled | Today + All |
| Follow-ups | `Due date`: All, Overdue, Today, Upcoming | `Status`: All, Open, Closed | All + Open |

For Due services, `No visit` and `Has visit` use the current obligation-to-Visit claim relationship only. They do not label an obligation as Booked and do not change its due date, fulfillment, recurrence, or Visit lifecycle.

Visit `Overdue` means a scheduled date before the current ServiceLoop business date while the Visit is still `BOOKED` or `WORKING`. Historical `COMPLETED` and `CANCELED` Visits are not overdue. `Past 30 days` is `today - 30 days <= scheduled date < today`. The default Visit view is intentionally Today; selecting All remains the deliberate route to the full retained Visit population. No `ARCHIVED` Visit lifecycle is introduced and no Visit retention behavior changes.

### Filter selector and notebook-tab owner inspection correction

The compact selectors are deliberately white controls in both appearances. Their fixed dark-on-white ink contract covers labels, values, chevrons, menu text, and selected checkmarks; the menu is also white. Selected menu rows use a pale teal treatment with teal ink/checkmark, and the dedicated selector-menu checkmark is 16dp. The dropdown affordance remains the vendored Phosphor Fill `CaretDown`; Unicode/font arrows are not used.

`ServiceLoopContentTabs` provides the shared notebook-tab treatment. The upper tab section is white in light appearance (and uses the corresponding dark surface in dark appearance), while the active tab matches the lower canvas/content surface. Its pale-neutral 1.5dp border and baseline reach the full available width, with the active tab interrupting the baseline. Work, Register, customer detail, and report callers use the same primitive; the customer-detail identity/contact/action/handoff block is explicitly part of the upper section through the tab boundary.

The third root destination is labelled **Register**. Its route and `CUSTOMERS` identifier remain unchanged, as do Customer domain language, Customer detail, and the nested Customers/Sites/Equipment tabs.

### Visit scheduled-date truth for Work filters

`working_visits.actualServiceDate` remains the actual service date and continues to be replaced with the business date when a Booked Visit starts. To preserve the final booked date without a new lifecycle or schema column, the Booked → Working transition atomically appends a `visit_schedule_events` row with event type `STARTED`; its `oldServiceDate` is the booked date immediately before Start and its `newServiceDate` is the actual service/start date. Existing Rescheduled/Restored events still update the current Booked date before Start.

The Work-list Visit summary projection uses that durable `STARTED.oldServiceDate` while a Visit is `WORKING`, falling back to persisted `actualServiceDate` when no Start booking history exists (for example a direct Start-now/Working Visit). This keeps `Overdue`, `Today`, `Upcoming`, and `Past 30 days` operationally tied to the booking date for active booked-origin work while preserving actual service date truth in the persisted Visit, completion, recurrence, record, report, Calendar, and Dispatch paths. No Room schema, `.slwork` package, lifecycle, retention, or Calendar-eligibility change is introduced.

### Owner inspection correction R3 — controls, filters, and action spacing

- Shared filter selectors remain white in either appearance, with fixed dark-on-white ink and a restrained `#67B9B8` pale-teal outline; menus retain pale-teal selected rows and 16dp checks.
- Shared notebook tabs explicitly suppress touch ripple/pressed indication while preserving tab role, selected semantics, focus support, and immediate selection.
- Full-width vertical command groups use `ServiceLoopActionStack` and the 8dp button-gap token. Service-plan, create-visit, equipment-management, customer/site management, and Dispatch editor command stacks use it.
- Service-plan End plan is a purpose-specific danger-tonal command; concise Customer/Site lifecycle copy now says Archive/Restore only. Customer detail has an upper-section 16dp buffer before its notebook tabs and uses the heading Active follow-ups.
- Equipment detail renders a nonblank private note in a private, non-report card.
- Due-service record selection uses generated Phosphor `SelectionEmpty`/`SelectionChecked` aliases (`square`/`check-square`) rather than Material Checkbox UI.
- History now uses the responsive Type + Sort selector row, retaining its existing date validation. Dispatch Outbox Status/Service date and CSV-import Rows are the additional actual list-filter dimensions migrated to the shared selector.
