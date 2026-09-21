# B044 — Visit setup harmonization and Dispatch extension refactor

**Status:** DESIGN / IMPLEMENTATION AUTHORITY PROPOSAL  
**Prepared:** 2026-09-21  
**Target branch:** `codex/b043-five-role-workspace` or its verified successor  
**Observed B043 owner-review HEAD at design time:** `acc193bb53befe2803b9a050c35cf2eb4df0536a`  
**Purpose:** Replace the separate ordinary-Visit and Dispatch-Visit setup experiences with one shared Visit setup implementation, layer Dispatch-only assignment and coordination concerns on top, and remove duplicate Customer-creation UI/domain logic so every “New customer” path uses the same form, validation, and Customer + first-Site creation semantics.

---

## 1. Executive summary

ServiceLoop currently has two independent Visit setup experiences:

1. ordinary local Visit creation in `NewVisitScreen(...)`; and
2. coordinator/Team Leader Dispatch Visit creation in `DispatchVisitEditorScreen(...)`.

They represent the same underlying user concept — preparing a service Visit — but have independently evolved different:

- customer/site selection;
- Existing/New/One-time terminology;
- date/time entry;
- work selection;
- task creation;
- inspection/checklist handling;
- layout;
- dirty-state behavior;
- validation;
- adaptive behavior.

This duplication is now a product and maintenance defect.

The solution is **not inheritance**. Compose does not benefit from an object-oriented “DispatchVisitForm extends NewVisitForm” design.

The adopted direction should be **composition around one shared Visit setup form**:

> **One common Visit setup draft + one common Visit setup UI + thin persistence/action hosts.**

Ordinary Visit creation uses the shared form and saves to the local technician-side Visit model.

Dispatch creation/editing uses the same shared form, adds Dispatch-only assignment/coordination sections, and saves to the Dispatch Outbox model.

The shared form must not know about Room Dispatch entities, package export, Team membership persistence, or coordinator Outbox lifecycle.

The Dispatch adapter must not reimplement Customer/Site, schedule, task, plan, checklist, or dirty-state UI.

---

## 2. Product principle

A Visit is one product concept.

Whether it is:

- created locally by a Solo/Subcontractor/Team Leader; or
- prepared for technicians by a Team Leader/Coordinator,

the technician/business user is still describing:

- who and where;
- when;
- what work should be done;
- which equipment/service/checklist applies.

Dispatch adds:

- who receives the work;
- how work is divided;
- coordinator-facing reference/instructions;
- Outbox/package lifecycle.

Those are **extensions to Visit setup**, not a separate Visit UX.

---

## 2A. DRY rule: Customer creation is one product flow

The owner has explicitly adopted a stronger DRY rule:

> Creating a Customer from Add Customer, ordinary New Visit, or Dispatch New Visit must use the same Customer-creation form and produce the same Customer + first Site result.

This is not merely visual similarity.

There must be one shared creation form, one shared draft/input meaning, one shared validation model, and one shared low-level Customer + first-Site creation implementation.

The hosts differ only in what happens **after** that Customer/Site exists:

- **Add Customer** → save Customer + first Site and open the Customer.
- **Ordinary New Visit** → transactionally create Customer + first Site + local Visit.
- **Dispatch New Visit** → transactionally create Customer + first Site + Dispatch Outbox Visit.

Do not maintain three slightly different field sets.

Do not keep a Dispatch-only “one-time customer” form.

Do not keep the reduced ordinary New-Visit Customer form.

### Canonical Customer creation fields

Use the same fields and labels as the ordinary manual Add Customer workflow:

#### Customer
- Customer name · Required
- Main contact
- Phone
- Email
- Private customer note
- One-time customer (no contract)

#### First site
- Site name · Required
- Site address

The first Site is the default Site.

The existing New Visit labels:

- `Location label`
- `Service address`

must not survive as a separate Customer-creation vocabulary. They become the canonical:

- `Site name · Required`
- `Site address`

when the user is creating a new Customer.

### Shared UI components

Recommended decomposition:

```text
CustomerCreationDraft
CustomerIdentityFields
CustomerFirstSiteFields
CustomerCreationForm
```

`CustomerEditorScreen(existing = null)` must render `CustomerCreationForm`.

`VisitSetupForm(mode = NEW)` must render the same `CustomerCreationForm`.

Because Dispatch New Visit renders the same `VisitSetupForm`, it automatically renders the same `CustomerCreationForm`.

Editing an existing Customer may reuse `CustomerIdentityFields`, but it does not show the “First site” creation section.

### Shared canonical inputs

The domain/data boundary should use existing canonical types wherever possible:

```text
CustomerInput
SiteInput
```

Avoid introducing another semantically overlapping input such as a new reduced Customer DTO unless it is only a thin UI draft converted directly into `CustomerInput` + `SiteInput`.

`CustomerInput.customerType` is the source of truth for Standard vs One-time.

The first `SiteInput` is created with `isDefault = true`.

## 3. Current divergence

### 3.1 Ordinary Visit setup currently provides

`NewVisitScreen(...)` already owns the stronger, more mature UX:

- full-width **Choose a customer** band;
- **Existing / New** tabs;
- Customer/Site search;
- selected Customer/Site summary with square Search/change action;
- Standard vs One-time customer behavior;
- current planned services at the selected Site;
- normal Date and optional Time controls;
- local task editor;
- Site vs Equipment subject;
- Equipment selection;
- optional equipment description;
- reusable inspection checklist selection for roles that can create local work;
- dirty-state protection;
- route-preselected plan support;
- Create Visit / Start-now behavior;
- current responsive visual language.

### 3.2 Dispatch Visit setup currently reimplements

`DispatchVisitEditorScreen(...)` separately owns:

- **Dispatch type**;
- **Existing / One-time** selection;
- a custom Customer/Site picker;
- custom date dialog;
- custom time dialog;
- an exposed IANA time-zone editor;
- custom work-item dialog;
- manually typed Service Plan reference;
- manually typed due date;
- Team selection;
- technician assignment;
- manager/reference metadata;
- instructions;
- Outbox save/cancel/conclude/reopen lifecycle.

The result is a different form, different wording, different field controls, and duplicated logic.

---

## 4. Adopted target architecture

Create one shared Visit setup feature family.

Recommended production structure:

```text
VisitSetupDraft
VisitSetupBaseline
VisitSetupMode
VisitSetupTaskDraft

VisitSetupForm
  VisitSetupCustomerSection
  VisitSetupPlannedWorkSection
  VisitSetupScheduleSection
  VisitSetupTaskEditor
  VisitSetupTaskList
  extensionContent
  actionContent
```

Thin hosts:

```text
NewVisitScreen
  -> owns/loads local Visit data
  -> adapts to VisitSetupDraft
  -> renders VisitSetupForm
  -> saves through local Visit repository/ViewModel functions
```

```text
DispatchVisitEditorScreen
  -> owns/loads Outbox + Teams/Technicians
  -> adapts Outbox draft to VisitSetupDraft
  -> renders VisitSetupForm
  -> inserts DispatchAssignmentSection
  -> inserts DispatchCoordinationDetails
  -> saves through DispatchPackageService
```

Do not create two copies of `VisitSetupForm`.

Do not keep Dispatch-specific replacements for shared sections after the refactor.

---

## 5. Shared draft model

The shared form needs one UI-level draft representation.

Recommended shape:

```kotlin
internal data class VisitSetupDraft(
    val mode: VisitSetupMode,
    val siteId: String?,
    val selectedPlanIds: Set<String>,
    val serviceDate: String,
    val appointmentTime: String,

    val newCustomerName: String,
    val newCustomerPhone: String,
    val newCustomerEmail: String,
    val newLocationLabel: String,
    val newAddress: String,
    val newCustomerType: CustomerType,

    val tasks: List<VisitSetupTaskDraft>,
)
```

`VisitSetupMode`:

```kotlin
enum class VisitSetupMode {
    EXISTING,
    NEW,
}
```

`VisitSetupTaskDraft` should represent the common conceptual task:

```kotlin
data class VisitSetupTaskDraft(
    val stableUiId: String,
    val taskName: String,
    val subjectType: WorkSubjectType,
    val equipmentId: String?,
    val equipmentDescription: String,
    val reusableTemplateId: String?,
)
```

For an existing local Visit workflow, the current `NewVisitTaskDraft` can either:

- be renamed/moved into this shared model; or
- remain as a compatibility type temporarily if refactoring risk is lower.

Do not introduce persistence annotations or Dispatch entities into the common draft.

---

## 6. Shared baseline / dirty-state model

The shared form should also own the **meaning** of whether Visit setup has changed.

Use one semantic baseline model.

The existing owner decision remains:

### Not dirty by itself

- opening Visit setup;
- typing Customer/Site search;
- selecting a Customer/Site;
- changing Customer/Site before meaningful work is configured;
- switching Existing/New when neither branch contains meaningful data;
- focus/picker open/close;
- arriving through a preselected Site/Plan route without modifying the preselection.

### Dirty

- selected planned-service set differs from baseline;
- date differs from baseline;
- time differs from baseline;
- new-customer business data is entered;
- task added/edited/removed;
- task subject/equipment/checklist content changes;
- other actual Visit-definition content changes.

### Important

The Dispatch host must use this same semantic dirty logic for the shared Visit portion.

Dispatch-only assignment/reference/instruction changes are then OR'ed into the host's dirty state.

Do not maintain separate definitions of “Visit setup dirty” between local and Dispatch screens.

---

## 7. Exact shared UX

### 7.1 Upper band

Same for local and Dispatch creation:

```text
Choose a customer

Existing | New
```

Requirements:

- same edge-to-edge surface;
- same heading inset;
- same tabs;
- same spacing;
- no Dispatch-specific “WHERE & WHEN” heading;
- no Dispatch-specific “Dispatch type”.

### 7.2 Existing customer/site

Same shared UI:

```text
Customer / site

Customer Name · Site Name                       [Search]
```

or, before selection:

```text
Customer / site

[ Find customer or site ]
```

The Search/change action is the same square `ServiceLoopFieldAction` used in ordinary Visit setup.

### 7.3 New customer

Use the existing ordinary Visit branch.

Fields remain:

- Customer name · Required
- Phone
- Email
- Location label
- Service address
- One-time customer (no contract)

Dispatch must not retain a separate “One-time” top-level mode.

The shared form uses:

**Existing | New**

and the **New** branch contains the customer-type choice.

This supports both:

- a new Standard customer; and
- a new One-time customer.

---

## 8. Planned work

For an existing Standard customer/site, both local and Dispatch Visit setup should use the same planned-work list.

Display current unclaimed planned services from ServiceLoop truth.

Each selected plan already provides the data Dispatch currently asks the coordinator to type manually:

- Equipment;
- Plan reference;
- service/plan name;
- current due date;
- reusable inspection-template identity/revision.

### Required change

Remove manual Dispatch fields:

- `Local Service Plan reference · Optional`
- `Due date when assigned · Optional YYYY-MM-DD`

for work based on a real ServiceLoop plan.

Those values must come from the selected current Plan/obligation.

Do not ask users to retype data the app already owns.

For one-off/ad-hoc tasks, no Plan is required.

---

## 9. Shared schedule section

Both local and Dispatch Visit setup use the same controls:

### Date
- same editable date field;
- same weekday treatment;
- same square Calendar action.

### Time · optional
- same editable HH:mm field;
- same square Time action;
- blank remains valid.

No Dispatch-specific text dialogs.

No custom duplicate date/time picker implementation.

---

## 10. Time-zone behavior

Do not show a free-form IANA `ZoneId` field in normal Visit setup.

The package may still carry `appointmentZoneId`.

Use the same resolved business/device zone used by the ordinary Visit workflow.

Recommended adapter behavior:

```text
shared VisitSetupDraft
+
current businessZoneId
->
DispatchOutboxEditorDraft.appointmentZoneId
```

Existing Dispatch drafts with a stored zone should preserve their saved zone when edited unless the product explicitly changes the appointment context.

For new Dispatch Visits, use the current ServiceLoop business zone.

Do not silently alter old Dispatch Visit zones on unrelated edits.

A future explicit cross-time-zone feature would require separate product design.

---

## 11. Shared task editor

The existing ordinary task editor becomes the common task editor.

Shared task fields:

- Task name · Required
- Subject: Site / Equipment
- existing Equipment selector where available
- optional Equipment description where no registered Equipment is selected
- reusable inspection checklist selector when the host/capability permits local work definition
- Add/Update task

The visual component must be one implementation.

### Checklist behavior

For local Visit creators:

- Solo
- Subcontractor
- Team Leader

the current reusable checklist selector/create behavior remains.

For Dispatch creation:

the same selector can be used for local Register-backed work definitions.

When the coordinator selects a planned Service Plan, the plan's checklist identity should flow automatically into the outgoing snapshot. Do not require a second checklist selection unless the user is adding an ad-hoc task.

---

## 12. Dispatch extension: Assignment

Dispatch adds a section **after the common Visit/work setup**.

Title:

## Assignment

### Teams · Required

Use the existing Team chooser.

Summary shows:

- selected Team names;
- participant count.

### Per-work-item assignment

Every shared task/planned-work item in the Dispatch host gets an assignment projection.

Default:

**Everyone on selected teams**

This is important: coordinators should not need to individually assign every work item.

Allow override:

**Change assignment**

which opens technician selection for that individual work item.

The Dispatch-specific assignment model remains:

- empty assignee list = Everyone on selected Teams;
- explicit technician IDs = only those technicians.

Do not add assignment controls to ordinary local Visit setup.

---

## 13. Dispatch extension: Coordination details

Keep current Dispatch-only metadata but place it under one collapsed optional section:

## Coordination details · Optional

Fields:

- Reference
- Instructions

Reference maps to current `managerReference`.

Instructions map to current Dispatch instructions snapshot.

Do not put these fields into the common Visit draft unless a later product decision makes them universal Visit data.

They belong to the Dispatch host extension state.

---

## 14. Dispatch persistence adapter

The shared form must not save itself.

Create an adapter layer in the Dispatch host.

Conceptually:

```text
VisitSetupDraft
+
DispatchAssignmentDraft
+
DispatchCoordinationDraft
+
appointmentZoneId
->
DispatchOutboxEditorDraft
```

### Existing site

Shared:

- siteId
- date
- time
- tasks
- selected plans

Dispatch-specific:

- teamIds
- per-task assigned technician IDs
- manager reference
- instructions

### New customer — resolved design

Source inspection confirms the current Dispatch save path already creates a Customer + default Site transactionally for its old one-time-only branch. The limitation is the duplicated narrow input/model, not a storage limitation.

Source inspection also confirms the `.slwork` package already transports `CustomerType`, so a locally created Standard Customer does not require a new transport concept.

Therefore the adopted target is:

- Dispatch New Visit supports both Standard and One-time new Customers through the SAME shared `CustomerCreationForm`;
- saving a Dispatch draft with a new Customer creates the same ordinary Customer + default Site master records as manual Add Customer;
- only after those records are created does the same transaction persist the Outbox Visit against the new Site;
- the resulting Customer/Site are ordinary Register records, not Dispatch-only shadows;
- no Room schema change is required;
- no `.slwork` format change is required merely to support Standard vs One-time Customer type.

Replace the old Dispatch-specific `oneTimeSite: OneTimeVisitInput?` concept with a canonical new-Customer/first-Site payload based on `CustomerInput` + `SiteInput` (or one thin wrapper containing exactly those two canonical inputs).

Do not keep an alternate Dispatch Customer creation path after migration.

---

## 14A. Shared Customer + first-Site persistence primitive

Current source duplicates Customer/Site row construction in at least:

- `ServiceLoopRepository.createCustomerWithFirstSite(...)`;
- `ServiceLoopRepository.createNewCustomerVisit(...)`;
- `DispatchPackageService.saveOutboxVisit(...)` for the one-time branch.

B044 should remove that duplication.

Create one small data-layer primitive that:

1. validates canonical `CustomerInput`;
2. validates canonical `SiteInput`;
3. allocates Customer ID/reference;
4. allocates Site ID/reference;
5. creates the Customer row using every canonical Customer field;
6. creates the first Site row using every canonical first-Site field;
7. marks the first Site default;
8. inserts both rows;
9. returns the created Customer/Site IDs/entities required by the calling transaction.

Recommended conceptual shape:

```kotlin
internal data class CreatedCustomerSite(
    val customer: CustomerEntity,
    val site: SiteEntity,
)

internal suspend fun createCustomerWithFirstSiteInTransaction(
    dao: ServiceLoopDao,
    customer: CustomerInput,
    site: SiteInput,
): CreatedCustomerSite
```

Exact placement/name may follow current data-layer conventions.

Important:

- this primitive must be usable **inside an existing Room transaction**;
- it must not start an independent unrelated transaction that breaks atomic Visit/Outbox saves;
- `writeGate` remains the responsibility of repository operations that already use it;
- reference generation and validation must not remain separately reimplemented in Dispatch.

Callers:

### Manual Add Customer

```text
write gate
transaction
  shared createCustomerWithFirstSiteInTransaction(...)
commit
```

### Ordinary New Visit

```text
write gate
transaction
  shared createCustomerWithFirstSiteInTransaction(...)
  create local Visit against returned Site
commit
```

### Dispatch New Visit

```text
transaction
  shared createCustomerWithFirstSiteInTransaction(...)
  create Outbox Visit against returned Site
commit
```

This ensures the three paths produce the same directory result.

### Transport privacy remains unchanged

“Same Customer creation result” refers to the local ServiceLoop Customer/Site master records.

Do not expand `.slwork` merely to transmit every Customer field.

Current Dispatch package transport may continue to carry only its adopted Customer/Site snapshot subset. Private Customer notes and other non-transported fields remain local unless separately authorized.

## 15. Planned-work Dispatch adapter

When a current Service Plan is selected in the shared form, convert it into the existing Dispatch work truth.

For each selected Plan:

- subject type = Equipment;
- equipmentId = plan's Equipment;
- task name = service/plan name according to current ordinary Visit semantics;
- servicePlanReference = actual Plan reference;
- dueDateSnapshot = actual current due date;
- inspection snapshot = derived from the Plan's current reusable template revision;
- assignedTechnicianIds = default empty / Everyone unless explicitly overridden.

This removes manual Plan reference/due-date typing.

The existing `snapshotForPlanReference(...)` logic should remain the source for Dispatch inspection snapshot materialization unless a better equivalent already exists.

---

## 16. Ad-hoc Dispatch tasks

For ad-hoc tasks created in the shared task editor:

- `servicePlanReference = null`;
- `dueDateSnapshot = null`;
- subject/equipment/description comes directly from common task draft;
- checklist snapshot, if selected, must be materialized consistently;
- technician assignment comes from Dispatch extension state.

Do not create a fake Service Plan to support ad-hoc Dispatch work.

---

## 17. Edit existing Dispatch Visit

Editing a Draft/Dispatched Outbox Visit must use the same shared form too.

Load:

```text
DispatchOutboxVisitEntity
+
site/customer/equipment directory
+
outbox items
+
team IDs
+
assignees
```

and adapt into:

```text
VisitSetupDraft
+
DispatchAssignmentDraft
+
DispatchCoordinationDraft
```

### Read-only states

Canceled and Concluded Outbox Visits may continue to use the same screen structure in read-only mode.

Shared form fields render disabled/read-only through the host's `editable` parameter.

Do not maintain a separate edit-only Dispatch form.

---

## 18. Shared form API

Recommended conceptual API:

```kotlin
@Composable
internal fun VisitSetupForm(
    draft: VisitSetupDraft,
    baseline: VisitSetupBaseline,
    sites: List<VisitSiteOption>,
    dueServices: List<DueService>,
    templates: List<TemplateSummary>,
    businessDate: LocalDate,
    businessZoneId: String,
    editable: Boolean,
    allowLocalWorkDefinition: Boolean,
    onDraftChange: (VisitSetupDraft) -> Unit,
    onCreateTemplate: (() -> Unit)?,
    extensionContent: @Composable ColumnScope.() -> Unit = {},
    actionContent: @Composable ColumnScope.() -> Unit,
)
```

Exact signature may vary to avoid a monolithic callback surface, but preserve these architecture rules:

- one shared renderer;
- no Dispatch service inside;
- no Room entities inside;
- no NavController requirement unless unavoidable for existing template return handling;
- host owns persistence/action consequences.

If callback count becomes unwieldy, use a small `VisitSetupActions` holder rather than injecting ViewModel/Dispatch service.

---

## 19. Shared state holder

A dedicated state holder is preferred over keeping dozens of local variables in two screens.

Possible form:

```kotlin
@Stable
internal class VisitSetupState(...)
```

or a data-state + event reducer model.

However, do not introduce a speculative generic form framework.

The goal is only:

- common Visit setup state;
- common dirty/baseline behavior;
- common UI.

A straightforward remembered state holder is sufficient.

---

## 20. What remains host-specific

### Local Visit host

Owns:

- local Visit save mode;
- Booked vs Working primary action;
- repository/ViewModel local Visit creation;
- local route/back behavior;
- ordinary completion of setup.

### Dispatch host

Owns:

- selected Teams;
- participants;
- per-task assignees;
- reference;
- instructions;
- Outbox Draft/Dispatched/Canceled/Concluded lifecycle;
- Save draft / Save changes;
- Cancel;
- Mark concluded;
- Reopen;
- export-generation semantics.

These host-specific actions may appear below the same shared form.

---

## 21. Validation model

The shared form owns common validation:

- valid Date;
- valid optional Time;
- selected existing Site OR valid New-customer branch;
- at least one selected planned service or task where required;
- task validity;
- equipment relationship validity.

Local host adds no Dispatch requirements.

Dispatch host adds:

- at least one Team;
- at least one participant across selected Teams;
- per-item explicit assignees must belong to selected-Team participants;
- Outbox lifecycle editability.

Do not duplicate common validation in both hosts.

---

## 22. Terminology harmonization

Dispatch must adopt ordinary Visit terminology.

Remove Dispatch-specific setup labels where they duplicate the same concept:

- remove `WHERE & WHEN`;
- remove `Dispatch type`;
- remove `Existing / One-time` top-level selector;
- remove `Choose a Site` terminology when the shared form says `Customer / site`;
- remove manual `Local Service Plan reference`;
- remove manual `Due date when assigned`;
- remove visible `IANA ZoneId`.

Keep Dispatch-specific terms only where they are genuinely Dispatch concepts:

- Assignment;
- Teams;
- Technicians;
- Everyone on selected teams;
- Reference;
- Instructions;
- Outbox;
- Dispatched;
- Concluded.

---

## 23. New Customer / one-time semantics

The shared product UX is exactly:

```text
Existing | New
```

Under New, the shared `CustomerCreationForm` contains:

```text
One-time customer (no contract)
```

This is the single product language.

Unchecked:

```text
CustomerType.STANDARD
```

Checked:

```text
CustomerType.ONE_TIME
```

The Customer + default Site created from this form must be equivalent regardless of entry path:

- Add Customer;
- local New Visit;
- Dispatch New Visit.

Do not preserve:

- a Dispatch-only top-level `One-time` mode;
- `Location label` / `Service address` as an alternate creation vocabulary;
- a reduced Customer input for Visit setup;
- a one-time-only Dispatch persistence branch.


---

## 24. Role behavior

### Solo

Ordinary local Visit setup only.

No Dispatch Outbox.

### Subcontractor

Ordinary local Visit setup.

Can import assigned Dispatch work.

No coordinator Dispatch creation.

### Employee

No Visit creation form.

Receives assigned work only.

This refactor must not accidentally expose the shared Visit setup form to Employee.

### Team Leader

May use:

- ordinary local New Visit;
- Dispatch New Visit.

Both use the same common form.

Dispatch version adds Assignment + Coordination details.

### Coordinator

Uses Dispatch New Visit only.

Does not receive ordinary technician-local New Visit creation.

The shared form must therefore be reusable without implying that Coordinator is performing the field work.

---

## 25. New Dispatch Visit visual structure

Target:

```text
New dispatch visit

Choose a customer
[ Existing | New ]

Customer / site
[ shared selection/search ]

Planned work
[ shared plan list ]

Set up visit

Date
[ shared Date control ] [calendar]

Time · optional
[ shared Time control ] [clock]

Tasks
[ shared task editor/list ]

Assignment

Teams · Required
[ choose teams ]

Task assignments
[ per-item summaries / Change assignment ]

Coordination details · Optional
[ collapsed ]
  Reference
  Instructions

Save draft
```

No separate Dispatch visual language for the common sections.

---

## 26. Existing Dispatch Visit visual structure

Same form initialized from saved Outbox state:

```text
Dispatch visit

[status/provenance notice if needed]

<same shared Visit setup form>

Assignment
...

Coordination details
...

Save changes
Cancel visit
Mark concluded / Reopen
```

Read-only Outbox states use the same content structure with disabled editing.

---

## 27. Migration / storage implications

Source inspection resolves the earlier Standard-customer uncertainty.

B044 should require:

- Room v16, and only for Dispatch ad-hoc reusable-template persistence: add nullable
  `reusableTemplateId TEXT` to `dispatch_outbox_items` through the 15→16 migration;
- no `.slwork` format change;
- no `.slinsp` change;
- no backup-format change.

This is a source-reviewed execution amendment to the original schema-neutral design.
`DispatchOutboxItemEntity.reusableTemplateId: String? = null` has no foreign key and
existing rows migrate to `NULL`. The field exists only so Dispatch can preserve the
shared task form's reusable inspection-template selection and materialize the same
inspection snapshot during export. `.slwork` remains unchanged, and all other B044
semantics remain as designed.

Current Dispatch already creates a Customer + default Site transactionally for its narrow one-time branch, and `.slwork` already carries `CustomerType`.

B044 generalizes that creation path to the shared canonical Customer/Site creation primitive.

Important save semantics:

- merely filling the New-customer form creates nothing;
- Customer + Site are created only when the host's save succeeds;
- local New Visit: Customer + Site + Visit are one transaction;
- Dispatch New Visit: Customer + Site + Outbox Visit are one transaction;
- failed save must not leave orphan Customer/Site records.


---

## 28. File-level implementation direction

### `ui/DueVisitUi.kt`

Refactor:

- `NewVisitScreen`
- `NewVisitTaskDraft`
- `NewVisitDraftSnapshot`
- `NewVisitTaskEditor`
- selected Site section
- planned work section
- date/time section

into reusable Visit setup components/state.

Keep ordinary local host thin.

### New recommended files

`ui/VisitSetupUi.kt`

and, unless an existing customer-form file is a cleaner home:

`ui/CustomerCreationUi.kt`

`VisitSetupUi.kt` owns:

- common Visit draft/baseline;
- common Visit form;
- shared Visit sections;
- shared task editor;
- shared Visit validation helpers;
- shared Visit dirty-state logic.

`CustomerCreationUi.kt` (or equivalent shared location) owns:

- `CustomerCreationDraft`;
- Customer identity fields;
- First-site fields;
- Standard/One-time choice;
- conversion to canonical `CustomerInput` + `SiteInput`;
- shared creation-form validation/presentation.

`CustomerEditorScreen(existing = null)` and `VisitSetupForm(mode = NEW)` must both call that same Customer creation UI.

Do not create a giant unrelated utility file.

### `ui/DispatchCoordinatorUiV2.kt`

Remove duplicated Visit setup UI from `DispatchVisitEditorScreen`.

Retain:

- Team picker;
- technician assignment picker;
- Outbox lifecycle;
- coordinator metadata;
- Dispatch persistence adapter.

Render shared `VisitSetupForm`.

### `data/DispatchPackage.kt`

Preserve transport/domain semantics.

Adapt shared selected plans/tasks into existing Outbox work-item truth.

Avoid transport changes unless the new-Standard-customer case proves impossible otherwise.

### `ui/ServiceLoopViewModel.kt`

Expose/reuse the minimal data needed by shared Visit setup.

Avoid duplicating loaders solely for Dispatch if common repository queries can be reused safely.

---

## 29. Tests

### Customer creation DRY tests

Verify the same shared Customer creation implementation is used by:

- Add Customer;
- ordinary New Visit → New;
- Dispatch New Visit → New.

At minimum:

- same field labels;
- same Standard/One-time control;
- same validation;
- Main contact persists from all three paths;
- Phone persists from all three paths;
- Email persists from all three paths;
- Private customer note persists from all three paths;
- Site name/address persist from all three paths;
- first Site is default;
- Standard remains Standard;
- One-time remains One-time;
- failed local Visit save leaves no orphan Customer/Site;
- failed Dispatch save leaves no orphan Customer/Site.

Add a source/architecture assertion that the three hosts do not each contain their own Customer creation field implementation.

### Shared form tests

One test family should verify both hosts render the same common surfaces.

Required:

- Existing/New tabs identical;
- Customer/Site section identical;
- selected Site change action identical;
- Date/Time controls identical;
- planned work presentation identical;
- task editor identical;
- dirty-state semantics identical.

### Local host

Preserve:

- Book/Start local Visit;
- route-preselected plans;
- New customer creation;
- one-time local customer;
- task/checklist behavior.

### Dispatch host

Verify:

- same common form;
- Teams section present;
- assignment controls present;
- coordination details present;
- Save draft persists Outbox only;
- selected Plan becomes correct Outbox work snapshot;
- per-item assignments preserved;
- default Everyone semantics;
- existing Dispatch edit initializes same shared form;
- Concluded/Canceled remain read-only;
- no direct local technician Visit is created when saving Outbox draft.

### Regression

Verify no duplicate UI implementation remains for:

- Customer/Site selection;
- Date/Time;
- common task editing;
- planned work.

A source-level architecture contract is acceptable here.

---

## 30. Rendered acceptance

Owner review should compare:

### Local New Visit

and

### New Dispatch Visit

The common portions should look intentionally identical.

A reviewer should be able to cover the Dispatch-only sections with a sheet of paper and see the same Visit setup UX underneath.

This is the visual acceptance test.

---

## 31. Non-goals

Do not:

- merge local Visit persistence with Dispatch Outbox persistence;
- turn Outbox Draft into a technician Visit;
- add backend/sync;
- create a generic form inheritance framework;
- make Team assignment universal Visit data;
- change Dispatch conclusion semantics;
- change completion/recurrence semantics;
- change immutable history;
- change report generation semantics;
- redesign the entire Coordinator workspace;
- add duration/conflict/route planning.

---

## 32. Recommended implementation sequence

### Stage 1 — Extract shared Visit setup

From current ordinary `NewVisitScreen`, extract:

- common draft/baseline;
- Customer/Site section;
- planned-work section;
- schedule section;
- task editor/list;
- dirty-state logic.

Rebuild ordinary `NewVisitScreen` as a thin host using the extracted implementation.

**Gate:** ordinary New Visit must be visually and behaviorally unchanged.

### Stage 2 — Move Dispatch onto shared form

Replace duplicated Dispatch sections with `VisitSetupForm`.

Add:

- Assignment section;
- Coordination details;
- Outbox persistence adapter.

Remove duplicate:

- Dispatch type;
- custom Site picker presentation;
- custom Date/Time UI;
- manual Plan reference/due fields;
- visible ZoneId editor;
- duplicate task form.

**Gate:** common local/Dispatch portions are the same UI.

### Stage 3 — Existing Dispatch edit + lifecycle

Initialize the shared form from an existing Outbox Visit.

Preserve:

- Draft/Dispatched edits;
- cancel;
- conclude;
- reopen;
- read-only states;
- generation/export semantics.

### Stage 4 — Focused verification and owner render review

Verify common-form parity, Dispatch extension behavior, and no storage/transport drift.

---

## 33. Main implementation risks

1. Accidentally changing local New Visit behavior while extracting it.
2. Dispatch save creating a local technician Visit instead of Outbox truth.
3. Losing assignment IDs while converting common tasks.
4. Treating manually typed Plan reference as equivalent to selected real Plan after refactor.
5. Checklist snapshot drift.
6. Existing Outbox Visit edit failing to reconstruct selected plans/tasks.
7. Dirty-state divergence between local and Dispatch host.
8. Time-zone overwrite on editing existing Dispatch Visits.
9. New Standard-customer Dispatch semantics exceeding current persistence model.
10. Reintroducing Employee creation paths through the new shared form.
11. Creating a “shared” component that still branches internally on `isDispatch` everywhere.
12. Leaving three separate Customer creation validators/entity builders after visually sharing the form.
13. Creating Customer/Site before Visit/Outbox validation succeeds and leaving orphan master records.
14. Accidentally transporting private Customer data in `.slwork` while unifying local creation.

Avoid #11–#14 explicitly.

The common form should expose extension points and capability/data differences, not become a huge `if (dispatch)` implementation.

---

## 34. Acceptance criteria

This refactor is complete when:

- there is one production implementation of common Visit setup UI;
- ordinary New Visit uses it;
- Dispatch New/Edit uses it;
- Dispatch common sections visually match ordinary New Visit;
- Existing/New behavior is shared;
- one-time customer is represented through the common New-customer branch;
- Add Customer, ordinary New Visit, and Dispatch New Visit use the same Customer creation form;
- those three paths use the same canonical Customer/Site inputs, validation and low-level creation primitive;
- new Standard and One-time Customers created from any of those paths produce ordinary equivalent Register records;
- no failed Visit/Dispatch save leaves orphan Customer/Site records;
- Date/Time controls are shared;
- planned work is selected from ServiceLoop truth;
- Dispatch no longer asks users to manually type Plan reference or due date for planned work;
- task editing is shared;
- Dispatch adds only Assignment and Coordination concerns;
- ordinary local Visit persistence remains distinct from Outbox persistence;
- Team/technician assignments remain Dispatch-only;
- no Room/package-format change occurs unless explicitly approved for the identified new-Standard-customer constraint;
- five-role B043 behavior remains unchanged.
