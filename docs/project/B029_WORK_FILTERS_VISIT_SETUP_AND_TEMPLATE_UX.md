# B029 — Work, Visit setup, filters, and inspection templates

## Scope

B029 polishes the daily Work queues and restructures technician-created Visit setup without changing the ServiceLoop lifecycle or its local-first boundary. It also makes reusable inspection templates understandable as logical masters with immutable revisions.

## Work action and filter memory

- The shared Work “New visit” action starts unresolved, resolves from the first meaningful list layout, and transitions only from floating to docked. A short list docks directly; a long list gets the bottom-right floating action above the weighted due-services list/action area.
- The reserved slot uses the real full-width button measurement. While floating, that button is visually hidden, disabled, non-clickable, and removed from accessibility semantics.
- Due Services remembers Due date + Visit, Visits remembers Date + Status, and Follow-ups remembers Due date + Status. Customers remembers Show one-time customers.
- These values use one application-private SharedPreferences file. They are presentation preferences only and are not included in Room business data or backup/export.
- Contextual Work routes seed a temporary entry preset and do not overwrite the remembered values.

## Create Visit

The Create visit screen keeps its toolbar title and presents the guidance “Choose a customer.” Existing and New are `ServiceLoopContentTabs`. New-customer fields precede the shared “Set up visit” controls; Existing presents customer/site and planned work first. Shared controls use a hierarchy of Set date, an editable `YYYY-MM-DD` appointment row with a native date picker, Add task, task list, and final actions. Inspection checklist selection offers active template versions, an explicit None option, and an inline create action that returns the created template ID to the draft and selects it.

Start now still writes the business date, and one-time customer/task semantics remain unchanged.

## Inspection templates

- The normal list shows one row per non-deleted master, active before disabled, with `IT-### · Name (vN)`, item count, and status.
- Detail shows paired Delete/Clone and Disable/Enable/Edit actions. Disable prevents new assignments but does not invalidate existing plan execution or historical snapshots. Delete is a soft tombstone and is blocked while a service plan references the master.
- Editing is an in-memory draft. Save creates exactly one new immutable revision; cancel/back uses the standard unsaved-change guard. Revisions are shown newest first as read-only history and are never reverted in place.
- Checklist item editing supports label, response type, number unit, required, private guidance, reorder, and removal. New item type choices use the three educational STATUS/TEXT/NUMBER descriptions from the B029 brief.
- Existing `.slinsp` exchange remains compatible with the current master/revision model; deleted masters are not ordinary export candidates.

## Verification intent

The acceptance gate is the repository’s B029 unit/build/lint/android-test suite plus targeted UI/runtime evidence where available. Reports must distinguish automated tests, domain instrumentation, UI instrumentation, system handoff, and rendered/human inspection.

## B029 correction and visual acceptance pass

The initial Work action decision now waits for meaningful `LazyListLayoutInfo`: `totalItemsCount > 0` and a non-empty `visibleItemsInfo`. The pure reducer keeps `UNRESOLVED` while layout is not ready, resolves a meaningful short page directly to `DOCKED`, resolves a meaningful long page to `FLOATING`, and permits only the one-way `FLOATING -> DOCKED` transition. This prevents the pre-layout `UNRESOLVED -> FLOATING -> DOCKED` flash.

Work list bottom spacing is state-aware. `FLOATING` keeps only the temporary floating-action clearance needed to scroll the last record above the floater; `DOCKED` uses normal bottom spacing beyond the real full-width reserved slot. Short, non-scrollable pages fill only the unused viewport before that docked slot. Due Services keeps `WorkNewVisitDueBottomInset = 0.dp` and its floater remains inside the weighted list area, above the persistent Book selected / Start selected actions.

Template detail checklist rows now use the existing disclosure caret treatment, as does Version history, while command buttons remain command actions. Draft checklist items expose `Edit` while collapsed and `Done` while expanded; movement, Remove, private guidance, response type, number unit, Required, and save-as-one-new-revision semantics remain unchanged.

Automated and connected evidence for this correction:

- Source-reviewed: `WorkNewVisitAction.kt`, the three Work list screens, `TemplateUi.kt`, the disclosure component, and the focused B026 UI tests.
- Unit: 415 tests, 0 failures, 0 errors.
- Local gate: `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleRelease`, `:app:lintDebug`, and `:app:assembleDebugAndroidTest` all PASS; `git diff --check` PASS.
- Focused connected UI instrumentation on the dynamically resolved canonical `Pixel_10a_ServiceLoop` AVD: 22 tests, 22 PASS. Coverage includes short/long Work docking, Due Services floater geometry, checklist disclosure routing, and Edit/Done semantics.
- Broader connected coverage previously run: 20 tests, 19 PASS and 1 FAIL. The isolated failure is the pre-existing `WorkFilterSelectorUiTest` expectation that the remembered Due visit filter starts at `All`; the canonical emulator retained another valid SharedPreferences filter and the isolated rerun reproduced the same setup-sensitive failure. No app data was cleared.

Rendered/human evidence was captured from the canonical AVD and inspected in light and dark themes: Home, Work Due floating, Work Due docked, short Visits, template detail, and the expanded template editor. The isolated fixture rendering was used for template screens because the preserved pilot dataset has no saved inspection templates; no canonical business data was mutated. Short Visits and Follow-ups were checked for direct docked presentation and compact bottom spacing; the long Due page was scrolled to confirm docking and one-way behavior. A representative Work screen was also inspected at approximately 320dp width with font scale 2.0, then the emulator was restored to 1080x2424 and font scale 1.0. Large text reflows the Work header/actions and filter controls without overlap; template action pairs remain vertically adaptable at narrow widths.
