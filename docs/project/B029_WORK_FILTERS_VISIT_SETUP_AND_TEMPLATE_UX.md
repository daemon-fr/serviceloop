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
