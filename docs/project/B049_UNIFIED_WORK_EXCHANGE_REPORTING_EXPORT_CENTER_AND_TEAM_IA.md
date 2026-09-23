# B049 — Unified Work Exchange, Reporting, Export Center, and Team IA

**Status:** OWNER-APPROVED DESIGN / IMPLEMENTATION AUTHORITY

The owner-approved B049 assignment is the implementation authority for this branch. New Dispatch work uses canonical WorkingVisit/WorkItem records as Visit truth, with Dispatch as an assignment/package/result overlay. ServiceLoop remains local-first and file-based, with immutable final history and exactly-once business effects. The milestone targets app 1.3.0/code 5, Room v19, Recovery v18, `.slsync` envelope v2, and FULL_WORKSPACE register v3.

## Locked business meaning

The canonical chain remains Customer → Site → Equipment → Service Plan/current obligation → Visit → work/checklist/evidence → completion review → immutable final history → report/next obligation/follow-up. Booking is not service, dispatch is not delivery, export is not receipt, finalization is not PDF generation, and local save is not backup. PERFORMED fulfills an eligible captured recurring obligation automatically; PARTLY_PERFORMED requires Fulfill or Keep due; NOT_PERFORMED never fulfills and needs a reason. Checklist completeness is derived from the captured snapshot and current answers. Corrections append immutable revisions; void retains truth. No backend, accounts, live sync, billing, inventory, customer portal, or unrelated enterprise scope is approved.

## Workspace and UI

One root tab header fixes geometry across Home, Work, and Register. Team separates full-width daily actions from dense configuration rows. Employee/Subcontractor import assignments and export results; Team Leader adds dispatch, result import, and report generation; Coordinator dispatches, imports results, and generates reports; Solo has no daily exchange actions. The management row order is Business and report identity, role, contextual ID, Trusted IDs, Technicians, Teams and leaders, Import shared data, Export data, with the exact role visibility matrix from the assignment. Stable local ID never changes on role change and is routing identity, not authentication. Trusted IDs remain local security configuration, excluded from FULL_WORKSPACE and included in Recovery.

The durable Team action visibility matrix is:

| Role | Import work | Export results | Dispatch work | Import results | Generate report | Business identity | Role | ID / Trusted IDs | Technicians / Teams | Import shared data | Export data |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Solo | No | No | No | No | No | Yes | Yes | No | No | No | No |
| Subcontractor | Yes | Yes | No | No | No | Yes | Yes | Yes | No | Yes | Yes |
| Employee | Yes | Yes | No | No | No | No | Yes | Yes | No | Yes | No |
| Team Leader | Yes | Yes | Yes | Yes | Yes | Yes | Yes | Yes | Yes | Yes | Yes |
| Coordinator | No | No | Yes | Yes | Yes | Yes | Yes | Yes | Yes | Yes | Yes |

Visibility does not imply implementation. At the initial UI checkpoint, result exchange and aggregate report actions remain disabled until their domain workflows exist. Route capability checks must enforce the same matrix.

Settings has ServiceLoop app (Appearance, Version, Report a bug) and Data & automation (Reminders, Calendar integration, Image cleanup, History, Backup and recovery). Business/report identity moves to Team, includes validated searchable IANA time zone and report-copy recipient, and saves explicitly with an unsaved Back guard. Customer contacts move to a dedicated ordered manager with internal notes. Record Contact Other uses Phosphor webcam. Equipment detail uses a Customer › Site breadcrumb and suppresses absent serials.

## Canonical Visit and Dispatch

Room 18→19 must be non-destructive and add contact order/notes, unique nullable canonical Dispatch Visit/item links, result receipt/provenance/application state, aggregate report/source/rendition state, and image-retention metadata. Recovery 17→18 must preserve coherent B049 state. Legacy B048 unlinked Dispatch records remain usable. Coordinator can create and Book canonical Visits but cannot Start or perform technician field work; Team Leader can Book and Start; Employee cannot create arbitrary Visits. One New Visit form owns the common Customer/Site/date/time/work/template inputs, with an Assignment section for Team Leader/Coordinator. Saving assigned work atomically persists canonical Visit and WorkItems plus Dispatch overlay. Dispatch work package manages assignment and generation/export, using the actual shared Due Services card primitive and urgency classifier. Transport-relevant canonical edits change material identity/generation safely.

## WORK_RESULT exchange

WORK_RESULT is a new versioned structured `.slsync` purpose, not PDF transport. Imported WORK_ASSIGNMENT persists its envelope exporterId as assignment issuer. Each result package targets exactly one issuer; its outer exporterId identifies the technician workspace. Target ID must match the importer and exporter trust checks remain in force. Stable resultId identifies the logical result; sourceFinalRevisionId identifies the exact immutable revision. Re-export of the same revision is idempotent; a correction retains lineage without overwriting history.

Only finalized assigned work is exportable. The result preserves dispatch Visit/item IDs, generation/material identity, documenting technician identity/name/designation, public and relevant private final facts, outcomes, recurrence effect, checklist, findings, parts, follow-ups, and all finalized photos with privacy/report flags. Transport photos are derivatives of owned originals: orientation honored, bounded decode, longest edge at most 1200px, JPEG quality at most 75, unnecessary metadata removed, size/hash measured on the derivative. Originals remain unchanged. Purpose-specific package bounds must be selected by representative measurement. Import validates the complete package before mutation, adopts imported evidence into Coordinator-owned storage, records exact receipts, handles partial results and stale conflicts explicitly, and applies recurrence at most once. Received remote work must never look like locally performed Coordinator work.

## Export, reporting, and images

One Customer → Site → Equipment scope filter with optional From/To dates serves Export Center and report selection; changing a parent clears incompatible children. Export Center offers Customer data, Work performed, Image Archive, and Custom family selection, outputting readable CSV/ZIP that is neither Backup nor arbitrary import. DATA_TRANSFER declares family versions and initially supports only safely mergeable inspection templates. TEMPLATE_SHARE stays readable but is no longer emitted.

Team Leader/Coordinator can select one Customer and one or more concluded Visits for one aggregate customer PDF. A unified reportable final-revision projection includes local and imported truth. The generator freezes exact effective non-voided source revision IDs and renders from immutable models, with current workspace branding and immutable documenting-technician attribution. New report/source/rendition rows retain scope, source order, hash, size, page count, status, and identity snapshots. Later corrections do not change prior aggregate PDFs.

Image cleanup defaults to Never and offers 1/3/6 months or 1 year for full-resolution originals. Finalized historical photos require a validated retained report-quality derivative before original deletion; working photos never expire automatically. Imported Coordinator-owned derivatives remain distinct. Image Archive is a separate deliberate external export, using the original if present and retained derivative otherwise.

## Implementation record

**Continuation status (2026-09-23):** B049 is implemented and verified on `codex/b049-work-exchange-reporting-export-center` for owner review. The checkpoint below is historical.

The current code targets app `1.3.0`/code `5`, Room v19, Recovery v18, FULL_WORKSPACE register v3, and `.slsync` envelope v2. New assigned work uses one canonical Visit/WorkItem transaction plus linked Dispatch overlay; legacy unlinked Outbox remains usable. WORK_RESULT v1 carries structured immutable final truth and bounded JPEG derivatives, validates issuer/trust/target and retry identity, and stores remote final receipts separately from local execution. Stale results do not advance recurrence. A representative 12-photo result package measured 6,233,353 compressed bytes, with 1200×800, 519,452-byte derivatives from 1,126,524-byte source JPEGs. WORK_RESULT limits are 64 MiB compressed, 96 MiB expanded, and 128 ZIP entries.

DATA_TRANSFER emits explicit template family/version metadata; legacy TEMPLATE_SHARE remains readable. Export Center produces scoped Customer Data, Work Performed, Image Archive, and Custom CSV/ZIP output. Aggregate reports freeze exact local/remote final sources and produce a new customer PDF with current branding, immutable technician provenance, and rendition metadata. Image cleanup defaults to Never and removes finalized originals only after verified retained derivatives exist. Ordered contacts, Team/Settings changes, owner UI corrections, and Room/Recovery/register compatibility are included.

Verification after the last production edit: 492 JVM tests passed with no failures or skips; debug APK, Android test APK, lintDebug, and release APK assembled successfully. The canonical `Pixel 10a ServiceLoop` AVD was used without clearing data; its prior development database was backed up and integrity-checked before additive B049 schema repair, preserving customer/contact rows. A combined affected device suite passed 49 tests, followed after the prior export edit by seven focused migration/PDF/export/picker/Service-return tests. The direct assignment import action opened Android DocumentsUI and cancel did not relaunch it. Two Services returned from Manage photos to the exact saved scroll index/offset in Compose instrumentation. Home/Work/Register, Team, Outbox, Export Center, and image cleanup screenshots were captured and inspected in light/dark appearances. After the final import correction, the final APK remained alive after an eight-second delayed startup smoke with no sampled fatal logcat line. Final commit IDs and remote parity belong in the final handoff.

**Historical checkpoint status:** First UI checkpoint only. The text below records that earlier source state; it does not describe the current continuation. Protected `master` starts at `1fd51131b040ab62af3874c1106615b75f3008fe` and must remain there.

Implemented in this checkpoint: shared Home/Work/Register root tab header; Team daily-action and management zones with role visibility; dedicated contextual ID and Trusted IDs routes; five-role selected check icon; Business/report identity moved to Team with explicit Save, searchable IANA timezone, report-copy recipient, and unsaved Back guard; Settings category reorganization (Image cleanup awaits its real retention implementation); Equipment breadcrumb/blank-serial behavior; Record Contact Other Phosphor webcam source-vector fallback. Existing identity designation editing remains on the dedicated ID screen. New WORK_RESULT and aggregate-report buttons are visibly disabled until their domain workflows exist.

Current executable versions remain app `1.2.0`/code `4`, Room v18, Recovery v17, `.slsync` envelope v2, and FULL_WORKSPACE register v2. The B049 target app/Room/Recovery/register versions and all new purpose schemas are NOT IMPLEMENTED. No WORK_RESULT package bound has been selected because no representative result package has been measured.

Verification at this checkpoint: Kotlin compilation PASS; 477 JVM tests PASS (0 failures/errors/skips); debug APK assembly PASS; lintDebug PASS; `git diff --check` PASS. The debug APK was replace-installed on the dynamically identified `Pixel 10a ServiceLoop` AVD without data clearing; the process remained alive after an eight-second wait and no fatal logcat line appeared in the sampled buffer. One Home screenshot was inspected in light appearance. This is a narrow runtime/rendered smoke, not full B049 UI evidence.

NOT RUN: B049-specific domain/instrumented tests, Room/Recovery migrations (not implemented), full UI-instrumented role/contact/Dispatch/report journeys, dark screenshots, result/package/PDF tests, system picker/sharesheet handoff, and actual retention cleanup. The Phosphor generator could not run because `py -3` reported no installed Python; the webcam VectorDrawable was transcribed from the vendored `webcam-fill.svg` path without icon substitution.

Remaining sequence: complete Stage 1 contacts and image-cleanup UI with their persistence; implement non-destructive v19/v18 state; converge new Dispatch work on canonical Visits; implement WORK_RESULT round trip and exactly-once ingestion; implement scope filter, Export Center, DATA_TRANSFER, aggregate reporting, and safe image retention; then run the full device/rendered/system and final Git gates. No B049 completion or owner acceptance is claimed at this checkpoint.


## Detailed adopted owner amendment — 2026-09-23

The following owner-approved contract is retained from the B049 continuation assignment. Its CURRENT sections describe the starting checkpoint at 7609f336, not necessarily the latest implementation state. The implementation record above and subsequent Git history describe what has actually shipped. All REQUIRED and OWNER DECISION sections remain the target until implemented and verified.

**Continuation checkpoint status:** The first correction slice changes Dashboard Visit date/label, shared root-tab height, Team role/ID copy presentation, direct assignment picker route, always-visible Visit progress, silent clean Service save state, peer photo actions, Outcome selected check, and route-scoped Service scroll checkpoint. This is source/compilation state only until the final unit, device, rendered, and system-handoff gates are reported. All other B049 target architecture and version changes remain outstanding.

**Capability checkpoint status:** The Visit-creation and field-execution gates are now separate. Coordinator can access canonical Visit setup and Book due Services while Start remains omitted, and a focused canonical-AVD Compose case verifies the Due Services action bar. Canonical Dispatch links, assignment extension, result exchange, reporting, export, migrations, and retention remain outstanding. The prior Service scroll defect still needs a return-journey UI test and rendered confirmation.

7. DASHBOARD — REMOVE DUPLICATED VISIT DATE
===========================================

## CURRENT

`OperationalDashboardProjector` creates Visit:

```
displayContext =
    customerName + actualServiceDate
```

`OperationalWorkRow` then renders:

```
Due <dueDate>
```

This duplicates the date.

## REQUIRED

For Visit rows:

* remove service date from `displayContext`;
* retain Customer/context information;
* render exactly ONE visible service-date line:

  ```
  Due <date>
  ```

Keep the literal word:

```
Due
```

Do NOT remove that word.

Do not alter Due Service or Follow-up semantics unintentionally.

## TIMED VISIT LABEL

Current:

```
Scheduled appointment
```

Change exact text to:

```
Booked visit
```

Make it clearly visible and bold.

Use established typography/tokens.

Recommended:

* `ServiceLoopUiTokens.Type.label` or equivalent;
* `FontWeight.Bold`;
* normal/high readable foreground or appropriate operational accent.

Do NOT style it like tiny muted metadata.

Do not add another copy of the date.

Add a focused Compose test with a timed Visit asserting:

* date occurs only once;
* `Due` remains;
* `Scheduled appointment` absent;
* `Booked visit` displayed.

======================================================================
8. ROOT TAB HEADER — HALVE THE EXCESS SPACE
===========================================

CURRENT:

```
ServiceLoopRootSecondaryTabs
height(80.dp)
```

The alignment fix is good.

The vertical blank area is not.

## OWNER DECISION

Use:

```
64.dp
```

for the root secondary-tab wrapper height.

Preserve:

* identical geometry on Home;
* identical geometry on Work;
* identical geometry on Register;
* tab minimum touch target;
* active tab visual;
* baseline;
* theme behavior.

Do not independently tune the three root callers.

Add/update source tests.

Add focused rendered/instrumented comparison so switching:

```
Home → Work → Register
```

does not vertically move the tab strip.

======================================================================
9. TEAM — YOUR TEAM ROLE ROW
============================

CURRENT:

```
Your team role
Coordinator
```

or equivalent context.

OWNER DECISION:

Root management row should simply say:

```
Your team role
```

Do NOT show the current role underneath.

The following contextual ID row already communicates role:

```
Your Coordinator ID
Your Team Leader ID
Your technician ID
```

Keep the role visible INSIDE the role chooser itself.

======================================================================
10. TEAM ID — COPY CONTROL
==========================

Replace the current plain:

```
IconButton
```

with the exact:

```
ServiceLoopFieldAction
```

visual primitive used by New Visit date/time picker actions.

Use:

```
Copy icon
accessibleName = "Copy ID"
```

Preserve:

* 48dp target;
* enabled only once local ID exists;
* clipboard behavior;
* ID itself centered.

Do not create another button style.

Also remove the duplicate inner ID screen heading.

The DetailScaffold title is the one screen heading.

======================================================================
11. IMPORT WORK PACKAGE — DIRECTLY OPEN FILE BROWSER
====================================================

## OWNER DECISION — EXACT

Tapping:

```
Team → Import work package
```

must immediately launch Android's file/document browser.

The user must NOT first land on another screen and then press:

```
Choose ServiceLoop file
```

## CURRENT

Team currently routes ordinary import through generic import behavior.

There is already an assignment-specific route:

```
dispatch/import
```

and:

```
ImportDispatchPackageScreen
```

That screen currently owns an `OpenDocument` launcher and a manual chooser button.

## IMPLEMENT

Keep assignment import separate from generic:

```
Import shared data
```

Preferred implementation:

1. Add an explicit auto-picker entry mode to the assignment import surface.

For example:

```
ImportDispatchPackageScreen(
    ...,
    openPickerImmediately = true/false
)
```

or a clean route equivalent.

2. Add/use an assignment-specific route such as:

   dispatch/import/pick

or a route argument that unambiguously requests auto-pick.

3. Team action:

   Import work package

navigates to that assignment-import mode.

4. On first composition for that back-stack entry:

   OpenDocument.launch(...)

fires automatically.

5. Launch it ONCE per entry.

Use a `rememberSaveable` / route-entry guard so recomposition does not reopen the picker repeatedly.

6. File types:

At minimum accept the existing supported ServiceLoop package types already used by assignment import:

```
SERVICE_LOOP_SYNC_MIME
application/zip
application/octet-stream
```

Do not broaden this into an unrelated media browser.

7. After a file is picked:

* validate `.slsync`;
* require purpose `WORK_ASSIGNMENT`;
* assess exporter trust;
* show existing preview/review UI;
* import remains explicit.

8. If the user CANCELS the browser:

* remain on the assignment-import screen;
* do not mutate data;
* show the normal chooser button so they can try again;
* do not automatically relaunch in a loop;
* Back still returns normally.

9. Generic Team:

   Import shared data

remains the generic purpose-aware importer.

Do not route generic workspace/template import through the work-package-only screen.

10. External ACTION_VIEW/ACTION_SEND entry remains unchanged unless required.

Add a Compose/navigation test that:

* taps Team → Import work package;
* verifies the assignment picker launcher is invoked immediately;
* does not require tapping another app button first.

======================================================================
12. OUTBOX — USE THE ACTUAL DUE SERVICES CARD PRIMITIVE
=======================================================

This is a repeated owner correction.

Do not approximate it again.

## CURRENT

`DispatchOutboxScreen` currently implements:

```
ServiceLoopSurfaceCard
  Row
    Checkbox
    Column
```

Delete/replace that duplicated selectable-card composition.

## REQUIRED

Use:

```
ServiceLoopEntityRecord
```

or extract a shared selectable operational-record primitive FROM
`ServiceLoopEntityRecord` and make BOTH Due Services and Outbox use it.

Outbox Visit rows must inherit exactly:

* dashed perimeter;
* urgency semantic accent;
* same corner radius;
* same title spacing;
* same top-left checkbox target;
* same visible check-square glyph;
* same title-only clearance;
* same card click semantics;
* same selection behavior.

Do NOT create:

```
DispatchOutboxCard
```

that visually imitates it.

## URGENCY

Outbox must use the same operational date semantics.

Do not invent Dispatch colors.

Where an Outbox row is still not yet linked to a canonical Visit, extract/reuse the same scheduling classification calculation used by operational Visits rather than duplicating rules.

At minimum classify from:

* service date;
* appointment time if present;
* Business date/timezone;
* current time.

Use the same:

```
OperationalWorkState
OperationalWorkColors
```

family.

Do not change Dispatch lifecycle status badges.

Preserve:

* Draft;
* Dispatched;
* Canceled;
* Concluded;
* generation/version;
* work-item count;
* cancellation pending/exported copy.

Add actual UI test:

1. render Outbox populated;
2. assert dashed urgency border;
3. assert selection glyph at same geometry as Due Services;
4. select row;
5. selection remains;
6. batch actions visible;
7. card navigation remains separate from checkbox.

======================================================================
13. SERVICE PHOTOS — THREE PEER ACTIONS
=======================================

CURRENT Photos card:

* Manage photos in heading;
* Choose photo;
* Take photo.

OWNER DECISION:

Heading:

```
Photos
```

Actions beneath content/heading:

```
Choose photo
Take photo
Manage photos
```

All three are peer regular-width actions.

Use:

```
ServiceLoopAdaptiveActionRow
```

or the canonical peer-action primitive already used by the product.

Manage photos must be the THIRD action.

Do NOT make Manage photos:

* heading toolbar action;
* giant full-width button by itself;
* tiny text link.

At narrow width / large text, normal adaptive wrapping is allowed.

Retain routes and semantics.

======================================================================
14. SERVICE — PRESERVE EXACT SCROLL POSITION ON RETURN
======================================================

This is a high-priority UX defect.

CURRENT:

```
ServiceScreen
val listState = rememberLazyListState()
```

Owner runtime review proves that is not enough.

Do NOT close this based on source inspection.

## REQUIRED

When leaving one Service for an ordinary child destination and coming Back, restore:

* exact first visible item;
* exact pixel/dp scroll offset within that item.

Examples:

```
Service
  → Manage photos
  → Back

Service
  → Link equipment
  → Back
```

and other ordinary nested Service journeys.

The page may be very long.

The user must return to where they were.

## IMPLEMENTATION GUIDANCE

Use route/back-stack scoped presentation state keyed by:

```
workItemId
```

Do NOT put scroll position in Room/business data.

A robust implementation is:

1. In `ServiceLoopNavigation.kt`, retain the `NavBackStackEntry` for:

   ```
   inspection/{id}
   ```

2. Pass its:

   ```
   SavedStateHandle
   ```

   or a narrow scroll-state adapter to `ServiceScreen`.

3. Store:

   ```
   firstVisibleItemIndex
   firstVisibleItemScrollOffset
   ```

   under work-item-specific keys.

4. `ServiceScreen` initializes its `LazyListState` from those values.

5. Observe:

   ```
   listState.firstVisibleItemIndex
   listState.firstVisibleItemScrollOffset
   ```

   using `snapshotFlow`.

6. Persist them continuously or before navigation.

7. Automatic focus/bring-into-view movements should also update the saved checkpoint naturally.

8. Switching to a DIFFERENT Service uses a different workItemId and must not reuse the previous Service position.

9. Process recreation while the entry remains saveable should also remain coherent.

10. Do not introduce a global singleton scroll cache.

Before changing architecture, reproduce the owner-observed failure on the canonical AVD to determine whether the cause is:

* content recreation through `ScreenState`;
* navigation SaveableState lifetime;
* route replacement;
* inspection reload;
* or another actual source.

Fix the correct layer.

MANDATORY UI TEST:

1. open a real populated Service;
2. scroll until a known deep semantic node is near the top;
3. record/identify the anchor;
4. open Manage photos;
5. Back;
6. assert the same deep anchor is displayed at approximately the same position;
7. repeat at least one second nested route if practical.

A unit test for saved integers alone is NOT sufficient.

======================================================================
15. SERVICE — OUTCOME SELECTED CHECK-FAT
========================================

Current Outcome uses:

```
ServiceLoopChoiceGroup
```

Owner wants the newer large selected check treatment.

For:

```
Performed
Partly performed
Not performed
```

provide a fixed leading slot.

Selected option:

```
Phosphor CheckFat
approx 24dp
ServiceLoop accent
```

Unselected option:

```
same reserved slot
no icon
```

Therefore text never shifts.

Preserve:

* single-choice semantics;
* existing selected fill/border;
* accessibility;
* enabled/disabled behavior;
* existing outcome business logic.

Do NOT add a second radio circle/checkmark.

Prefer a reusable optional treatment on:

```
ServiceLoopSelectionOption
```

or a small shared checked-selection wrapper.

If safe, make Team role and Outcome share the same selected-check primitive instead of duplicating code.

======================================================================
16. SERVICE — REMOVE NORMAL "SAVED · TIME"
==========================================

Current:

```
ServiceSaveState
```

clean branch renders:

```
Saved · 17:14
```

or similar.

Remove the clean-state label completely.

Normal durable clean state should be silent.

KEEP meaningful state:

```
Saving…
Needs attention
Not saved — retry
```

and existing retry action.

Do NOT remove failure visibility.

Do NOT weaken autosave integrity.

Update tests/source guards accordingly.

======================================================================
17. VISIT PROGRESS — ALWAYS SHOW SERVICES
=========================================

Current:

```
expandedGroupKeys
ServiceProgressGroupToggle
Show services (N)
Hide services (N)
```

OWNER DECISION:

Remove the entire collapse/expand mechanism.

Visit progress remains grouped:

```
Equipment / Site
  Service 1
  Service 2

Equipment / Site
  Service 3
```

All services are always listed.

Remove:

* Show services;
* Hide services;
* caret toggle;
* expanded-group state.

Preserve:

* group headings;
* Service position number;
* Service name;
* status badge;
* current Service selection;
* cross-Service navigation;
* current-Service non-navigation behavior;
* attention/status semantics.

Remove obsolete toggle tests and replace them with tests proving:

* all groups visible;
* all Service rows visible immediately;
* ordering unchanged.

======================================================================
18. EQUIPMENT BREADCRUMB TOUCH TARGET
=====================================

The breadcrumb visual is accepted.

Current implementation uses clickable `Text`.

Retain visual:

```
Customer › Site
```

but provide >=48dp touch height/target for both clickable segments.

Do not turn them back into giant buttons.

Preserve:

* Customer navigation;
* Site navigation;
* semibold hierarchy;
* no blank serial placeholder.

======================================================================
19. TEAMS — DISABLED LEADER CHECKBOX CONTRAST
=============================================

Current Leader checkbox uses shared disabled treatment and still lacks the owner-requested visual distinction.

Ensure these four states are visibly distinct:

* enabled checked;
* enabled unchecked;
* disabled checked;
* disabled unchecked.

Disabled Leader controls must be noticeably lighter/more muted.

Do not weaken actual enabled state logic:

```
Leader disabled when Technician is not a member.
```

Keep 48dp target/semantics.

======================================================================
20. SETTINGS IA — COMPLETE IT
=============================

Current headings:

```
ServiceLoop app
Data & automation
```

are correct.

Final order:

## SERVICELOOP APP

```
Appearance
Version
Report a bug
```

## DATA & AUTOMATION

```
Reminders
Calendar integration
Image cleanup
History
Backup and recovery
```

`Image cleanup` is currently missing.

Add it when the actual retention setting/path is implemented in Stage G below.

Do not add a dead placeholder row.

######################################################################
STAGE B — CUSTOMER CONTACT MANAGER + REMAINING STAGE-1 WORK
######################################################################

======================================================================
21. CUSTOMER CONTACT MANAGER
============================

Implement the adopted B049 redesign.

Edit Customer must NOT permanently dump all repeatable contacts inline.

Replace with equal peer actions:

```
List contacts
Add contact
```

## ADD CONTACT

Dialog/editor fields:

```
Person or label (optional)
Channel
Contact value · Required
Notes (optional)
```

Channel selector:

```
Phone
SMS
WhatsApp
Email
Other
```

Notes are internal/private.

## LIST CONTACTS

Dedicated screen.

Reuse the Inspection Template item-card interaction language.

Each contact shows:

* person/label;
* channel;
* value;
* Notes if present.

Actions:

```
Delete
Move up
Move down
Edit
```

Order is explicit.

Do NOT infer order from timestamps.

FULL_WORKSPACE register v3 must eventually transport:

* notes;
* explicit order.

======================================================================
22. RECORD CONTACT OTHER ICON
=============================

Keep the B049 webcam correction:

```
ContactOtherWebcam
```

Do not revert to camera/pencil.

Use the generated/source-derived Phosphor webcam asset.

######################################################################
STAGE C — ROLE CAPABILITY SPLIT + ONE CANONICAL NEW VISIT
######################################################################

======================================================================
23. REPLACE canCreateLocalWork OVERLOAD
=======================================

File:

```
WorkspacePolicy.kt
```

Current overloaded flag:

```
canCreateLocalWork
```

must be split.

Adopt explicit capability names, e.g.:

```
canCreateVisits
canPerformFieldWork
canAssignWork
canReceiveAssignedWork
canUseCoordinatorTools
canConcludeDelegatedWork
canManageRegister
showRegister
canManageTemplates
canExchangeTemplates
```

Keep names clean and repository-consistent.

## REQUIRED MATRIX

SOLO:
canCreateVisits = true
canPerformFieldWork = true
canAssignWork = false
canReceiveAssignedWork = false

SUBCONTRACTOR:
canCreateVisits = true
canPerformFieldWork = true
canAssignWork = false
canReceiveAssignedWork = true

EMPLOYEE:
canCreateVisits = false
canPerformFieldWork = true
canAssignWork = false
canReceiveAssignedWork = true

TEAM LEADER:
canCreateVisits = true
canPerformFieldWork = true
canAssignWork = true
canReceiveAssignedWork = true

COORDINATOR:
canCreateVisits = true
canPerformFieldWork = false
canAssignWork = true
canReceiveAssignedWork = false

## UPDATE ALL GATES

Audit current uses of:

```
canCreateLocalWork
```

including:

* Home New visit;
* `visit/new`;
* Due Services selection;
* Book selected;
* Start selected;
* Visit detail local task creation;
* field routes;
* other workspace actions.

Exact rule:

BOOK / CREATE VISIT:

```
canCreateVisits
```

START / FIELD EXECUTION:

```
canPerformFieldWork
```

Coordinator therefore:

* sees + New visit;
* can Book selected Due Services;
* cannot Start selected;
* cannot execute Service/checklist field work.

Do not grant execution accidentally.

======================================================================
24. ONE CANONICAL NEW VISIT
===========================

User-facing concept:

```
New Visit
```

not:

```
New Dispatch Visit
```

## CURRENT

Canonical route:

```
visit/new
```

Separate Dispatch:

```
dispatch/visit/new
```

with:

```
DispatchVisitEditorScreen
```

## B049 TARGET

Use the canonical Visit setup for:

Solo:
normal Visit form

Subcontractor:
normal Visit form

Team Leader:
normal Visit form + Assignment section

Coordinator:
normal Visit form + Assignment section

Employee:
no arbitrary Visit creation

## COMMON FORM OWNS

* existing/new Customer;
* Site;
* date;
* optional time;
* planned Services;
* ad-hoc work;
* template choice;
* work list;
* validation.

## ASSIGNMENT EXTENSION OWNS

* Teams;
* Technicians;
* leaders;
* documentation assignment;
* coordinator instructions/reference;
* item-level assignment semantics.

Preserve B043 Dispatch semantics.

Do not invent simplified assignment meaning.

######################################################################
STAGE D — ROOM v19 / RECOVERY v18 / CANONICAL DISPATCH LINKS
######################################################################

======================================================================
25. VERSION TARGETS
===================

Set only once the corresponding implementation exists:

```
versionName = "1.3.0"
versionCode = 5
```

Room:

```
18 → 19
```

Recovery:

```
17 → 18
```

FULL_WORKSPACE:

```
register v3
```

Outer `.slsync`:

```
remains formatVersion 2
```

WORK_ASSIGNMENT:

```
DispatchPackageCodec remains v5 unless an unavoidable proven reason exists.
```

======================================================================
26. ROOM v19 — NON-DESTRUCTIVE
==============================

Add persistence for:

## CUSTOMER CONTACT

```
notes
position / sortOrder
```

Migration:

* existing contacts retain all data;
* assign deterministic stable ordering;
* no destructive fallback.

## CANONICAL DISPATCH LINKS

Equivalent:

```
dispatch_outbox_visits.localVisitId
```

nullable for legacy but mandatory for new B049 dispatch work.

And:

```
dispatch_outbox_items.localWorkItemId
```

same rule.

Use uniqueness/FK constraints where appropriate and safe.

## WORK RESULT RECEIPT

Persist sufficient truth for:

* stable result ID;
* source final revision ID;
* assignment issuer ID;
* exporter/Technician ID;
* dispatch Visit/item;
* assignment generation/material identity;
* package/result hash;
* receipt/import status;
* stale/conflict status;
* recurrence/application state;
* imported evidence ownership.

## AGGREGATE REPORT

Equivalent tables:

```
aggregate_reports
aggregate_report_sources
aggregate_report_renditions
```

Persist exact source final revision IDs and stable order.

## IMAGE RETENTION

Persist:

* retention preference;
* retained derivative metadata;
* original/derivative ownership/state where required.

======================================================================
27. LEGACY DISPATCH COMPATIBILITY
=================================

Legacy B048 Outbox rows may have no canonical links.

Do NOT destructively rewrite them.

Keep them usable.

Allow safe transactional/lazy adoption where appropriate.

All NEW B049 assigned Visit creation must create canonical links.

======================================================================
28. RECOVERY v18
================

Recovery must preserve new B049 state coherently.

Test:

```
17 → 18
```

Do not invent missing trusted IDs.

Preserve:

* local ServiceLoop identity;
* Trusted IDs;
* business data;
* new result/report/contact/retention state.

No destructive restore shortcut.

######################################################################
STAGE E — CANONICAL VISIT / DISPATCH CONVERGENCE
######################################################################

======================================================================
29. CANONICAL TRUTH
===================

For NEW B049 dispatch work:

```
working_visits
work_items
```

are authoritative Visit/work truth.

Dispatch becomes overlay only.

## WHEN BOOKING/SAVING ASSIGNED WORK

Persist atomically:

1. canonical Visit;
2. canonical WorkItems;
3. captured plan/obligation/checklist semantics;
4. Dispatch overlay/bindings.

Dispatch overlay owns only:

* assignees;
* teams;
* leader/documentation semantics;
* coordinator instructions/reference;
* dispatch IDs;
* generation;
* material hash;
* exported state;
* cancellation/conclusion transport state;
* result-receipt state.

Do NOT persist a second competing Site/date/task universe for new B049 work.

======================================================================
30. CANONICAL EDITS + GENERATION
================================

Transport-relevant canonical edits must change the next assignment material/generation safely.

Preserve:

* generation monotonicity;
* material hash;
* retry-safe same logical export;
* stale generation rules;
* cancellation semantics.

Booked is still not serviced.

######################################################################
STAGE F — WORK_RESULT ROUND TRIP
######################################################################

======================================================================
31. ADD WORK_RESULT PURPOSE
===========================

Add `.slsync` purpose:

```
WORK_RESULT
```

Outer format:

```
v2
```

Payload:

```
v1
```

unless current naming convention warrants an equivalent explicit name.

It is structured data.

It is NOT PDF transport.

======================================================================
32. ASSIGNMENT ISSUER / RESULT DESTINATION
==========================================

On WORK_ASSIGNMENT import, persist envelope:

```
exporterId
```

as:

```
assignmentIssuerId
originWorkspaceId
```

or clear equivalent.

One WORK_RESULT package belongs to exactly ONE issuer.

Never mix results originating from different issuers in one result package.

Outer WORK_RESULT:

```
exporterId = technician/exporting workspace
```

Payload also declares:

```
target/origin issuer ServiceLoop ID
```

Import requires target == local ServiceLoop ID.

Exporter still must be trusted.

ID remains routing/trust identity, not authentication.

======================================================================
33. RESULT IDENTITY
===================

Use stable:

```
resultId
```

for the logical result.

Use:

```
sourceFinalRevisionId
```

for the exact immutable transmitted revision.

Re-export same revision:

* same logical result;
* safe retry;
* no new business result.

A new `.slsync` package may get a new package ID.

Package ID != result ID.

Correction:

* same result lineage;
* new immutable revision ID;
* never overwrite old imported historical truth.

======================================================================
34. RESULT EXPORT ELIGIBILITY
=============================

Only finalized assigned work.

Never export:

* working drafts;
* pending autosave;
* half-completed Service;
* PDF-only artifact.

Multiple Visits are allowed if same issuer.

======================================================================
35. RESULT PAYLOAD
==================

Preserve:

* dispatchVisitId;
* dispatchItemId;
* generation;
* assignment material identity;
* assignment issuer;
* stable resultId;
* sourceFinalRevisionId;
* documenting Technician ID;
* Technician name;
* designation;
* service date;
* Customer snapshot;
* Site snapshot;
* Equipment/subject snapshot;
* outcome;
* public work;
* NOT_PERFORMED reason;
* fulfillment result;
* next-due result/provenance;
* checklist answers;
* findings;
* parts;
* relevant finalized private/internal notes;
* follow-ups;
* all finalized evidence photos plus semantic flags.

======================================================================
36. RESULT PHOTOS
=================

Transport derivative:

```
max longest edge = 1200 px
JPEG quality <= 75%
```

Required:

1. read ServiceLoop-owned source;
2. validate ownership;
3. honor EXIF orientation;
4. bounded decode;
5. preserve aspect ratio;
6. downscale only if needed;
7. encode normal photographic evidence as JPEG;
8. strip unnecessary metadata;
9. hash/size DERIVED artifact;
10. package derivative.

Original remains unchanged.

Coordinator adopts its own durable local derivative.

Transport ALL finalized evidence photos, retaining:

* private/public semantics;
* report inclusion;
* caption;
* source association.

Customer PDF still renders only customer-report-eligible photos.

======================================================================
37. PURPOSE-AWARE SLSYNC LIMITS
===============================

Current global limits are too small for realistic result photos.

Refactor to centralized bounded purpose-aware limits.

Do NOT make files unlimited.

Measure representative result packages.

Document chosen WORK_RESULT limits.

Still reject:

* unsafe paths;
* duplicate entries;
* undeclared entries;
* invalid hashes;
* excessive expanded size;
* excessive entry count.

======================================================================
38. REMOTE RESULT INGESTION
===========================

Create dedicated domain path equivalent to:

```
ingestRemoteFinalResult(...)
```

Do NOT fake local Coordinator execution.

Never manufacture:

* Start event;
* local technician completion;
* Coordinator-authored field work;
* local photo capture.

Persist immutable remote final truth with documenting-Technician provenance.

Then independently evaluate recurrence application.

======================================================================
39. RESULT IMPORT IDEMPOTENCY
=============================

Re-import same result revision:

* no duplicate final revision;
* no duplicate photo;
* no duplicate follow-up;
* no duplicate receipt;
* no duplicate recurrence effect.

Exactly-once recurrence remains safe across:

* same package retry;
* same result in another package;
* process death;
* future API retry;
* partial returns.

======================================================================
40. PARTIAL RESULTS
===================

Receipt is item-level.

Until every expected external result-bearing item has returned:

```
Awaiting results
```

must be derived/displayed.

Keep already received evidence.

Canonical Visit remains non-final until complete.

When all required item results arrive:

* Dispatch Visit can become Concluded;
* canonical Visit becomes complete;
* reportable projection becomes available.

======================================================================
41. STALE RESULT
================

If result references older materially superseded generation:

* preserve evidence;
* mark conflict/review;
* do not silently apply recurrence;
* do not discard result.

No blind overwrite.

######################################################################
STAGE G — SCOPE FILTER + EXPORT CENTER + DATA_TRANSFER
######################################################################

======================================================================
42. REUSABLE SCOPE FILTER
=========================

Create one reusable model/component:

```
customerId: String?
siteId: String?
equipmentId: String?
fromDate: LocalDate?
toDate: LocalDate?
```

Use for:

* Export Center;
* Generate report;
* result selection where applicable.

## HIERARCHY

All customers:

* Site fixed/disabled at All sites;
* Equipment fixed/disabled at All equipment.

One Customer:

* Site enabled and constrained.

One Site:

* Equipment enabled and constrained.

Parent change clears incompatible child.

Dates inclusive.

Reject From > To.

Provide Clear/reset.

======================================================================
43. EXPORT CENTER — REPLACE CURRENT DataExportScreen
====================================================

Current old screen is NOT sufficient.

Replace it with B049 Export Center.

## PRESETS

```
Customer Data
Work Performed
Image Archive
Custom
```

All presets seed ONE shared selection model.

## CUSTOMER DATA

Preselect:

* Customers;
* Customer contacts;
* Sites;
* Equipment;
* Service Plans.

Date filter irrelevant while only current-state families selected.

## WORK PERFORMED

Preselect:

* concluded Visits;
* final Service records;
* work lines;
* checklist answers;
* findings;
* parts;
* Follow-ups;
* relevant Contact notes/history.

## IMAGE ARCHIVE

Export files + readable metadata.

Use original if retained.

Else retained derivative.

Metadata CSV/README should identify:

* Customer;
* Site;
* Equipment;
* Visit;
* Service;
* date;
* caption;
* privacy/report inclusion;
* original vs derivative.

## CUSTOM

Logical families:

Directory:
Customers
Customer contacts
Sites
Equipment
Service Plans

Work:
Visits
Service records
Checklist & findings
Parts
Follow-ups
Contact notes
History / changes

Evidence:
Photo metadata
Image files

Templates:
Inspection templates

## PRIVACY OPTIONS

```
Include private/internal information
Include inactive/archived/retired
Include previous record revisions
```

## OUTPUT

Readable:

```
CSV
ZIP
```

This is NOT Backup.

Do not implement arbitrary filtered FULL_WORKSPACE re-import.

======================================================================
44. EXPORT ROUTE CAPABILITY ENFORCEMENT
=======================================

Current Team visibility hides Export data from roles that should not have it, but underlying routes are not consistently gated.

Do not rely only on hidden UI.

Gate export route families according to adopted role capability.

Solo/Employee must not obtain unavailable export workflows by direct internal navigation.

This is local product/capability enforcement, not secure authentication.

======================================================================
45. DATA_TRANSFER
=================

Add `.slsync`:

```
DATA_TRANSFER
```

Initially support safely mergeable:

```
INSPECTION_TEMPLATES
```

Stop creating NEW:

```
TEMPLATE_SHARE
```

but continue reading legacy TEMPLATE_SHARE.

No template-specific trust bypass.

Do not imply arbitrary CSV imports.

######################################################################
STAGE H — AGGREGATE CUSTOMER REPORTS
######################################################################

======================================================================
46. GENERATE REPORT
===================

Team Leader + Coordinator.

Flow:

1. choose one Customer;
2. optional Site;
3. optional Equipment;
4. optional From/To;
5. show concluded reportable Visits;
6. multi-select;
7. generate one aggregate PDF.

One Customer REQUIRED.

Cross-Customer selection prohibited.

======================================================================
47. UNIFIED FINAL-REVISION PROJECTION
=====================================

Reporting must treat:

* local finalized work;
* imported WORK_RESULT truth;

through one reportable immutable final-revision projection.

Imported truth retains:

* documenting Technician;
* exporter;
* issuer;
* dispatch IDs/generation;
* result provenance.

Never imply Coordinator performed it.

======================================================================
48. EFFECTIVE REVISION
======================

Default source:

```
current effective non-voided final revision
```

At aggregate generation:

* resolve exact revision IDs;
* freeze them;
* persist them in aggregate_report_sources.

Later corrections:

* do not mutate old PDF;
* new report may use newer effective revisions.

======================================================================
49. AGGREGATE PDF
=================

Render new document from immutable models.

Do NOT concatenate old PDFs.

Hierarchy:

Customer report
→ scope/date
→ Visit
→ Site
→ Technician
→ Services
→ evidence pages

Header branding:

```
current Coordinator/Team Leader Business identity
```

Visit attribution:

```
immutable documenting Technician
```

######################################################################
STAGE I — IMAGE RETENTION
######################################################################

======================================================================
50. IMAGE CLEANUP SETTING
=========================

Settings:

```
Data & automation
  Image cleanup
```

Options:

```
Never
1 month
3 months
6 months
1 year
```

Default:

```
Never
```

UI copy:

```
Keep full-resolution photos for
[ ... ]

ServiceLoop keeps a report-quality copy for finalized work.
```

======================================================================
51. RETENTION TRUTH
===================

Distinguish:

1. full-resolution ServiceLoop-owned original;
2. retained history/report derivative;
3. imported Coordinator-owned derivative.

Before deleting original:

* required derivative exists;
* derivative verified;
* hash/size valid.

If derivative fails:

* keep original.

Working/unfinalized photo:

* NEVER automatically delete.

Cleanup:

* bounded;
* opportunistic;
* no exact alarm;
* no foreground service.



## Required verification and acceptance

######################################################################
STAGE J — TESTING / RUNTIME / FINAL GATES
######################################################################

======================================================================
52. OWNER-REVIEW CORRECTION TESTS
=================================

Add focused tests for:

DASHBOARD

* Visit date appears once.
* visible line retains `Due`.
* timed Visit shows bold/displayed `Booked visit`.
* `Scheduled appointment` absent.

ROOT TABS

* Home/Work/Register use same primitive.
* wrapper = 64dp.
* rendered top geometry matches.

TEAM

* Your team role has no role context subtitle.
* ID screen only one contextual heading.
* Copy uses `ServiceLoopFieldAction`.

IMPORT WORK PACKAGE

* Team action opens file picker immediately.
* picker cancel remains on assignment import.
* no automatic relaunch loop.
* assignment purpose enforced.

OUTBOX

* actual shared record primitive.
* dashed urgency border.
* same checkbox placement.
* selection independent from navigation.

SERVICE

* clean state has no Saved/time label.
* saving/error states remain.
* Visit progress always lists Services.
* no Show/Hide services text.
* Outcome selected option has CheckFat.
* photo actions are Choose / Take / Manage in peer row.
* exact Service scroll restored after Manage photos → Back.

EQUIPMENT

* breadcrumb segments maintain >=48dp touch target.

TEAMS

* disabled Leader checkbox visibly muted.

======================================================================
53. DOMAIN TESTS
================

Add/retain tests for:

* capability matrix;
* Coordinator Book but no Start;
* canonical Visit + Dispatch links;
* Room 18→19;
* Recovery 17→18;
* legacy unlinked Dispatch;
* register v3 contacts;
* DATA_TRANSFER;
* TEMPLATE_SHARE legacy read;
* WORK_RESULT stable identity;
* one-issuer package restriction;
* trusted source;
* target issuer match;
* partial returns;
* stale generation;
* duplicate import;
* recurrence exactly once;
* photo derivative;
* package limits;
* aggregate exact revision provenance;
* image cleanup safety.

======================================================================
54. BUILD GATES
===============

After final source/test edits run:

```
gradlew.bat :app:testDebugUnitTest --no-parallel --console=plain

gradlew.bat :app:assembleDebug --no-parallel --console=plain

gradlew.bat :app:assembleDebugAndroidTest --no-parallel --console=plain

gradlew.bat :app:lintDebug --no-parallel --console=plain
```

Also run:

```
gradlew.bat :app:assembleRelease --no-parallel --console=plain
```

if current milestone practice/environment permits.

Then:

```
git diff --check
```

Never cite an earlier green run after later edits.

======================================================================
55. CANONICAL AVD
=================

Use ONLY display-name:

```
Pixel 10a ServiceLoop
```

Resolve serial dynamically.

Every adb command:

```
adb -s <resolved-serial> ...
```

Do NOT:

* target attached physical phones;
* assume emulator-5554;
* wipe;
* recreate;
* uninstall;
* clear app data.

Build APK first.

Install:

```
adb -s <serial> install -r <apk>
```

## STARTUP SMOKE

After launch:

* wait >=5–8 seconds;
* verify process remains;
* inspect logcat fatal/crash patterns.

`am start` success alone is not evidence.

======================================================================
56. REQUIRED RUNTIME OWNER-REVIEW JOURNEY
=========================================

On canonical AVD verify at minimum:

1. Home Dashboard:

   * Visit date not duplicated;
   * Due retained;
   * Booked visit prominent.

2. Home tabs:

   * reduced vertical gap.

3. Work tabs:

   * same vertical geometry.

4. Register tabs:

   * same vertical geometry.

5. Team:

   * role row no repeated role;
   * ID copy control matches date/time field action.

6. Import work package:

   * tap once;
   * Android file picker opens immediately.

7. Outbox:

   * urgency dashed cards;
   * same selection glyph placement as Due Services.

8. Service:

   * no Saved time label;
   * all Visit-progress Services always visible;
   * Outcome CheckFat;
   * Photos has three peer actions.

9. SERVICE POSITION:

   * scroll well down page;
   * Manage photos;
   * Back;
   * exact previous position restored.

10. Equipment breadcrumb:

    * visual retained;
    * both links usable.

11. Export data:

    * B049 presets and scope filter present.

Use light and dark where materially visual.

Screenshots from this new run are valuable because the owner’s previous screenshots were lost.

======================================================================
57. SYSTEM HANDOFF EVIDENCE
===========================

Actually exercise, where safe:

* Import work package Android document picker;
* representative `.slsync` share/picker flow;
* readable Export Center file creation;
* WORK_RESULT picker/share if implemented.

Distinguish:

```
SYSTEM-HANDOFF
```

from domain instrumentation.

If not run, say NOT RUN.

======================================================================
58. TOOLING FALLBACKS
=====================

Follow AGENTS.md.

Known useful behaviors:

* use explicit adb serial;
* use semantic Compose interaction;
* off-screen LazyColumn children may need `performScrollToNode`;
* avoid coordinate text entry if IME/stylus interferes;
* if optional helper fails, use safe fallback and continue;
* do not alter production code for broken harness;
* no encoded/obfuscated commands;
* no AV/AMSI/security bypass.

Phosphor generator:

```
tools/phosphor/phosphor_tool.py
```

If Python launcher remains unavailable, source-derived transparent vector fallback is accepted only when exact source asset is verified and the workaround is reported.

======================================================================

63. FINAL ACCEPTANCE CHECKLIST
==============================

Do not call B049 complete until all applicable items are checked.

## OWNER-REVIEW UI

[ ] Dashboard Visit date shown once
[ ] surviving date says Due
[ ] Scheduled appointment removed
[ ] Booked visit prominent/bold
[ ] root tabs use one shared primitive
[ ] root tab wrapper reduced to 64dp
[ ] Home/Work/Register tab vertical position identical
[ ] Your team role has no role subtitle
[ ] ID screen has one heading
[ ] Copy ID uses ServiceLoopFieldAction
[ ] Equipment breadcrumb links have >=48dp targets
[ ] disabled Leader checkbox clearly muted

## IMPORT WORK

[ ] Team Import work package opens file browser immediately
[ ] no intermediate Choose-file tap required
[ ] assignment-specific import path
[ ] cancel leaves user on import screen
[ ] no relaunch loop
[ ] generic Import shared data remains separate

## OUTBOX

[ ] uses actual shared selectable operational record primitive
[ ] dashed urgency perimeter
[ ] shared operational color system
[ ] same checkbox geometry as Due Services
[ ] checkbox and card navigation remain independent
[ ] Dispatch lifecycle/status truth retained

## SERVICE

[ ] clean Saved/time label removed
[ ] Saving state retained
[ ] failure/retry state retained
[ ] Visit progress always-expanded
[ ] Show services absent
[ ] Hide services absent
[ ] all services listed under Equipment/Site
[ ] Outcome selected CheckFat
[ ] no text shift in Outcome choices
[ ] Choose photo / Take photo / Manage photos are peer actions
[ ] Manage photos is third action
[ ] Service → Manage photos → Back restores exact scroll position
[ ] second nested Service journey restores exact scroll position

## CONTACTS

[ ] List contacts
[ ] Add contact
[ ] contact notes
[ ] explicit order
[ ] Edit
[ ] Delete
[ ] Move up/down
[ ] register v3 transport

## CAPABILITIES

[ ] canCreateLocalWork overload removed/replaced
[ ] Solo can create/start
[ ] Subcontractor can create/start
[ ] Employee cannot create Visit
[ ] Employee can perform assigned work
[ ] Team Leader can create/book/start/assign
[ ] Coordinator can create/book/assign
[ ] Coordinator cannot Start
[ ] Coordinator cannot field-execute Service

## CANONICAL VISIT / DISPATCH

[ ] one New Visit product concept
[ ] Team Leader assignment section
[ ] Coordinator assignment section
[ ] new assigned Visit uses canonical working_visits
[ ] new assigned work uses canonical work_items
[ ] Dispatch overlay linked
[ ] no duplicate new Visit truth
[ ] legacy B048 rows preserved
[ ] canonical edits affect material generation

## ROOM / RECOVERY

[ ] app 1.3.0 code 5
[ ] Room v19
[ ] non-destructive 18→19 migration
[ ] Recovery v18
[ ] Recovery 17→18
[ ] local identity preserved
[ ] Trusted IDs preserved
[ ] contact order preserved

## WORK_RESULT

[ ] purpose added
[ ] envelope remains v2
[ ] one issuer per package
[ ] target local issuer checked
[ ] trusted exporter checked
[ ] stable resultId
[ ] sourceFinalRevisionId
[ ] same revision re-export stable
[ ] duplicate import idempotent
[ ] remote final ingestion does not fake local execution
[ ] partial results explicit
[ ] Awaiting results explicit
[ ] stale generation conflict safe
[ ] recurrence exactly once
[ ] received evidence retained on conflict

## RESULT PHOTOS

[ ] longest edge <=1200px
[ ] JPEG quality <=75
[ ] orientation honored
[ ] metadata stripped
[ ] derivative hash/size
[ ] original unchanged
[ ] all finalized evidence photos transported
[ ] privacy/report flags preserved
[ ] Coordinator owns imported derivative
[ ] bounded package limits measured/documented

## EXPORT CENTER

[ ] Customer Data preset
[ ] Work Performed preset
[ ] Image Archive preset
[ ] Custom preset
[ ] one shared selection model
[ ] hierarchical Customer/Site/Equipment filter
[ ] parent clears child
[ ] From/To inclusive
[ ] invalid range blocked
[ ] privacy toggles
[ ] inactive toggle
[ ] revision toggle
[ ] readable CSV/ZIP
[ ] Image Archive metadata
[ ] not labelled Backup
[ ] export routes capability-gated

## DATA_TRANSFER

[ ] purpose added
[ ] inspection templates supported
[ ] new template exports no longer TEMPLATE_SHARE
[ ] legacy TEMPLATE_SHARE readable
[ ] trust enforced

## AGGREGATE REPORT

[ ] Team Leader action
[ ] Coordinator action
[ ] one Customer required
[ ] multi-Visit selection
[ ] multi-Site supported
[ ] cross-Customer blocked
[ ] unified local/imported reportable projection
[ ] effective non-voided revision selected
[ ] exact source revision IDs frozen
[ ] old aggregate report immutable after correction
[ ] documenting Technician attribution
[ ] current workspace branding
[ ] only customer-report photos rendered
[ ] rendition hash/size/page count/status

## IMAGE CLEANUP

[ ] Settings row exists
[ ] Never default
[ ] 1m / 3m / 6m / 1y
[ ] working photos never auto-deleted
[ ] derivative required before original deletion
[ ] failed derivative preserves original
[ ] imported derivative preserved
[ ] Image Archive separate from cleanup

## QUALITY

[ ] full unit suite PASS
[ ] assembleDebug PASS
[ ] assembleDebugAndroidTest PASS
[ ] lintDebug PASS
[ ] release build PASS or truthful NOT RUN
[ ] Room migration tests PASS
[ ] Recovery tests PASS
[ ] focused UI instrumentation PASS
[ ] Service scroll-return journey PASS
[ ] direct Import work picker SYSTEM-HANDOFF PASS
[ ] representative export handoff checked
[ ] canonical AVD startup stable
[ ] light rendered review
[ ] dark rendered review
[ ] git diff --check PASS
[ ] branch pushed
[ ] remote parity 0 0
[ ] master unchanged

Execute the authorized B049 continuation without waiting for another owner prompt.
