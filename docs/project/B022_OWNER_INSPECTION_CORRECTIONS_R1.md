# B-022 — Owner inspection corrections, round 1

**Date:** 10 September 2026  
**Status:** Owner-directed correction scope; NOT implemented or verified by this document  
**Inspected production checkpoint:** `2f7fe66f0a24dffbc10837f7e7d3e375b5f66bcf`  
**Working branch:** `codex/b013-ui-overhaul`  
**Protected master:** `1fd51131b040ab62af3874c1106615b75f3008fe`

## Authority and inspection outcome

Felix has personally inspected the application and supplied a blocking navigation report, twenty numbered corrections and eleven screenshots. The inspection was stopped because inaccessible/stuck flows prevented further review. Earlier AI Stage-B acceptance is not owner acceptance or evidence that these defects are absent. B-013 is reopened for this owner correction round.

These owner directions supersede conflicting B-013 presentation decisions, including the old dense unboxed entity-row treatment, command-button treatment of navigation tabs, and B-020's Fill-only restriction for the explicitly excepted Back icon. Keep the frozen v1.0 reference byte-identical. Unchanged business, privacy, persistence and platform requirements remain authoritative.

## B-021 owner-inspection gate

The required owner inspection has now occurred. It authorizes this correction scope and the AI review/fix iterations needed to complete it, not an unrestricted restart of development.

After the corrected build is independently reviewed by AI, STOP for another owner inspection and correction list. No automatic Stage C, localization, unrelated cleanup, feature work, master movement or merge. AI cannot waive this gate through unattended authority, convenience or an earlier acceptance statement. Read-only analysis remains permitted.

## Numbered owner requirements

**Blocking issue and 18 — Due Services / Create Visit.** Home → Work → Customers → Work can leave Work indefinitely on `Reading due services`; Home → New visit can become stuck on the same dependency. Fix the underlying data-flow/lifecycle defect first. Prove real launch/resume and concurrent-read journeys, not only seeded composables. No timeout-as-fix, fake empty data, polling, repeated navigation refresh or renamed loader.

**1 — Brand band.** One narrow full-width light-teal band with centered white ServiceLoop text, above page chrome and below the safe status/camera-cutout region. Use shared chrome across app-owned full pages, at minimum Home/Work/Customers. Remove the old duplicate eyebrow. Do not paint over OS chrome, external activities or retained PDF bytes.

**2 — Content-aware navigation widths.** Work and Customers category groups give longer labels more width and distribute spare width to fill the available page width. Apply to the proper tabs required by item 10.

**3 — Compact saved status.** Show `Saved on this device · <time>` in one ordinary line with the freestanding `check-fat` Fill icon, not a boxed check or separate title/body rows. This is status, not a button. Allow natural wrapping at enlarged text; retain Saving/Failed/last-checkpoint truth.

**4 — Bottom-right expansion.** All expandable multiline fields place the 24dp expand glyph in its 48dp target at the bottom-right of the editable field, not vertically centered. Reserve genuine text-free space. Retain field-specific names, shared buffer, cursor/focus restoration and parent-save semantics.

**5 — Field/action spacing.** Every action group below an input is separated from the COMPLETE field block, including helper/error/privacy text. Apply everywhere, including Save work performed and Restore booking.

**6 — Back icon.** Use the actual vendored Phosphor arrow-left BOLD asset with exactly the Back label tint in both appearances. This is the explicit B-020 style exception. Update manifest/generator; do not hand-edit generated output or fake Bold by thickening Fill.

**7 — Centered title.** Every page with Back centers its generic page title across the full page width, not just leftover space. Reserve balanced collision space for actual side controls. Back remains reachable at large text.

**8 — Responsive filters.** Follow-up filters must not split Closed into Close + d. Measure full labels and wrap whole controls when necessary; never shrink text to preserve an overfull row.

**9 — Equal peer heights.** All controls sharing a visual row take the measured maximum peer height, regardless of selected/disabled/emphasis/multiline state. Wrap or stack responsibly when needed. No selected-state size jump.

**10 — Real tabs.** Find ALL controls that switch a major content pane. Render proper tabs, connected to a horizontal baseline, selected tab open at the bottom, line continuing to the edge. This includes Work, directory and Customer-detail categories. Classify by callback: current Edit/Maps, Edit/Record contact and Equipment Edit/Start or resume are commands or workflow navigation, not same-page tabs. Keep those as consistently styled equal-size command peers; never give Tab semantics to starting work, saving or launching Maps. A different occurrence that truly switches panes belongs to the tab family.

**11 — No title icons.** Remove all icons prepended to page titles. Keep functional Back/Search/Settings actions and root-navigation icons. Remove title-substring-based icon guessing.

**12 — Generic titles.** Toolbar titles name the page/workflow, never the associated record name or ID. Customer, Site, Equipment, Visit, Service, History and Final service record are examples. Put business identity once in the content identity block.

**13 — History sort.** Sort choices form one compact horizontal group at ordinary widths, with whole-control responsive wrapping only when genuinely needed.

**14 — Helper/action separation.** Apply item 5 after privacy labels, especially Follow-up Save/Resolve/Cancel and Visit reschedule/cancel/restore/Add one-off line.

**15 — Action hierarchy.** Dominant page/form/section commands use white-on-solid theme action color. Standalone Save follow-up changes, Record past visit and Add follow-up must not be faint outlined defaults. Add a distinct tonal secondary command using light teal background/teal text and corresponding dark-theme colors. Equal command peers such as Customer Edit/Record contact receive equal treatment. Keep primary commands, utility commands, tabs, filters, entity navigation and destructive final confirmations distinct.

**16 — Management is not a record.** Site/Equipment history, archive/dependency review, move/retire and equivalent commands belong in separately spaced History/Management groups, using explicit secondary/text actions, not entity-record containers.

**17 — Consistent Open.** Customer/Site/Equipment/Visit records share a visible teal Open affordance. Body/Open activate the same destination once without duplicate accessibility actions. Checkboxes remain independent.

**19 — Rounded dashed entity containers.** Replace rejected table-like entity rows with softly rounded containers and thick light-teal DASHED borders. Apply to navigable Customers/Sites/Equipment/Visits across registers/contextual lists and equivalent record selectors. Related Plan/Follow-up records follow that role consistently. Maintain theme-aware colors, a separate focus/selection indication, textual status and Open. Do not apply this style to management actions, fields, all nested sections or report paper. Pure history events are not automatically entity cards; actual navigable record representations are.

**20 — Whole-app section audit.** Identify identity/context, status, input/work, related-record, primary-action, utility and management sections on EVERY page. Separate using consistent spacing, headings and selectively padded horizontal dividers. Fix local field/action gaps and larger section boundaries; a global page-padding change alone is insufficient. Include coordinator, recovery, settings, reminders, Calendar, empty/error and read-only surfaces.

## Orchestrator implementation defaults

These are explicit implementation choices, not measurements inferred from cropped screenshots:

- Section gap 24dp; heading/content 12dp; complete field/helper/privacy block to action group at least 12dp, normally 16dp; peers 8dp. Assign spacing to shared layout owners without doubled margins.
- Entity container: radius 12dp, dashed stroke 2dp, initial dash/gap 6dp/4dp, neutral surface. Define light/dark border tokens centrally; pale border is decorative, not the sole interactive/selected signal.
- Brand band: 28dp minimum, grows with text; centered white 14/20 semibold. Choose a lighter-than-primary teal still providing at least 4.5:1 contrast for white. Safe top inset handled once.
- Tabs: stable typography/weight, measured natural widths plus distributed spare room, equal heights, rounded top corners, selected open-bottom baseline. Filters/sorts are separate whole-control wrapping primitives.
- Toolbar: generic centered title, no entity icon, left Back/Bold arrow with matching tint, balanced side slots.
- Secondary commands: tonal teal rather than outlined white. True destructive final confirmations retain safeguards and warning treatment.

## Source-review leads, not runtime-confirmed diagnoses

At the inspected checkpoint ServiceLoopViewModel uses assignments such as `_state.value = _state.value.copy(visits = repository.visits())`, including visit setup, customer/site and settings reads. Kotlin evaluates the copy receiver before suspend-call arguments. A suspended unrelated read can publish an old whole-state copy after Due Services becomes Available, potentially restoring Unresolved. This is a concrete lost-update hazard and the leading investigation path, not proof that the owner's exact runtime sequence has already been reproduced.

Read suspend results first, then atomically merge into CURRENT state through a pure reducer/update. Preserve target/dataset/generation checks. Never put repository work, navigation or other side effects inside a retriable update lambda. Audit observation ownership, cancellation and shared loading flags as well.

The generated ServiceLoopIcon wrapper defaults to Color.Unspecified: do not assume inheritance of the parent label tint. Correct the generation template/API and explicit semantic overrides.

The supplied Bold arrow contains stroked line/polyline elements rather than filled paths. Preserve/convert strokes, caps and joins in the generator or fail explicitly; selecting a Bold filename is not sufficient.

## Execution and evidence

One correction assignment, three independently reviewable internal checkpoints: state-regression diagnosis/fix/tests; shared chrome/control/status/icon/entity corrections; whole-app semantic section census and regression. Continue between checkpoints without routine owner interruptions.

Preserve canonical AVD and dataset. Use isolated fixtures for mutation/failure injection. Verify cold/resume/rapid and settled Home → Work → Customers → Work and Home → New visit with controlled delayed reads and no artificial Room write to wake observation.

Produce actual Light/Dark before/after evidence for the numbered findings. Assert destination, dataset/state and relevant content before capture. A route string plus a wait is not proof. Keep evidence and occurrence-level audit available for independent review; do not classify all requirements by assigning them to a generic screen.

Run final host/build/lint/release and affected production-wired instrumentation after the last edit. Distinguish capture harness limits from product/navigation failures. Return the corrected build to the owner; do not automatically proceed to another stage or merge.
