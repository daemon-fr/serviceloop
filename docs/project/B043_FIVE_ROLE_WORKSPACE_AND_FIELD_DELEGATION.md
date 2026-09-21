# B043 — Five-role workspace, field delegation, and coordinator conclusion

**Status:** OWNER-ADOPTED DOCUMENTATION AUTHORITY, 2026-09-21.

This document records the B043 owner amendment for the next implementation task. It supersedes conflicting older user-facing role/workspace assumptions while preserving their historical evidence and the adopted asynchronous, local/file-based Dispatch transport semantics. This documentation commit does not implement the model.

## Owner decision summary

ServiceLoop remains a local-first service book for solo technicians and small service companies. The role is the normal workspace model. There is no separate “Hide Register” preference: Register availability is determined by role, while operational Visit context may still be visible where field work requires it.

The exact user-facing roles, descriptions, and order are:

1. **Solo** — “I manage and perform my own service work.”
2. **Subcontractor** — “I manage my own work and also receive tasks from others.”
3. **Employee** — “I work on tasks assigned by a coordinator.”
4. **Team Leader** — “I perform assigned work and coordinate other technicians.”
5. **Coordinator** — “I plan, assign, and oversee work for technicians.”

Do not introduce `Member` or `Team Member` as a new user-facing role. Legacy `MEMBER` is retained only as a stored-value compatibility concern and maps explicitly to `SUBCONTRACTOR`.

## Capability model

The role order is product meaning, not merely presentation:

independent technician → independent technician receiving outside work → company field technician → field technician with coordination/delegation → coordination/oversight only.

Future role UI must preserve the order above unless the owner explicitly changes it.

| Role | Register | Receive assigned work | Perform field work | Delegate / assign | Coordinator tools | Conclude others' work |
| --- | --- | --- | --- | --- | --- | --- |
| Solo | Yes | No | Yes | No | No | No |
| Subcontractor | Yes | Yes | Yes | No | No | No |
| Employee | No | Yes | Yes | No | No | No |
| Team Leader | Yes | Yes | Yes | Yes | Yes | Yes |
| Coordinator | Yes | No | No | Yes | Yes | Yes |

### Solo

Solo has Register access; manages their own Customers, Sites, Equipment, Plans, reusable templates, and local work; and performs field work. Solo does not receive coordinator-assigned Dispatch work, delegate work, use coordinator tools, or conclude work performed by others. Solo represents an independent technician/business using ServiceLoop as their own local service book.

### Subcontractor

Subcontractor has the full Solo management and field-work capability and also receives coordinator-assigned Dispatch work. Subcontractor does not delegate work, use coordinator tools, or conclude work performed by others. This is the closest semantic successor to current `MEMBER` behavior: full ordinary Register access plus technician-side Dispatch receiving.

### Employee

Employee is execution-focused. Employee does not receive the Register root or ordinary Register bookkeeping/master-data creation, editing, archiving, moving, or reusable-template-management workflows. Employee receives coordinator-assigned work and performs assigned field work, but cannot delegate, use coordinator tools, or conclude others' work.

Employee must still see the Customer/Site/Equipment/service/checklist context required by an assigned Visit. “No Register” does not mean “no operational directory information.” This is a local workspace model, not a security permission system.

### Team Leader

Team Leader is intentionally a hybrid role, not Employee plus a leader badge. It combines full Register/master-data and local-work capability with technician-side Dispatch receiving and field execution, plus coordinator-side planning, delegation, and conclusion.

On the receiver side, Team Leader has a stable technician identity, can receive/import assigned `.slwork` packages, perform assigned work, and participate in the existing leader-visible/documentation semantics. On the coordinator side, Team Leader can manage Technicians and Teams, prepare and assign Dispatch Visits, export work packages, use Outbox, and administratively conclude delegated work.

The role is not mutually exclusive between receiver and coordinator. Assignment truth still comes from the Visit/package. Being a Team Leader does not make the user leader of every imported Team or Visit; preserve package/team-leader IDs and per-Visit assignment provenance.

### Coordinator

Coordinator has full Register and coordination capability, including Technician/Team management, Dispatch preparation and assignment, Outbox, and administrative conclusion. Coordinator does not receive technician assignments for personal field execution and does not perform technician field work. Coordinator may inspect Visits, work, and results for oversight and may use operational planning actions, but must not start technician service work, answer technician inspection/checklist fields, or record technician completion as though the Coordinator performed it.

## Register and operational context

Register is a management workspace, not the only place directory information may be viewed.

Employee therefore:

- does not receive the Register root;
- does not receive ordinary Customer/Site/Equipment/Plan/template bookkeeping workflows;
- may see Customer, Site, Equipment, service, and checklist context from assigned Visits;
- may use operational actions that do not amount to Register bookkeeping where existing semantics permit them.

Implementation should reuse capability-aware existing detail screens where practical. Show operational/read-only context and structurally omit master-data management actions for Employee. Do not create a duplicate Employee database or a second directory model.

## Legacy `MEMBER` compatibility

Existing stored `TeamRole.MEMBER` installations must not silently fall back to `SOLO`. The explicit compatibility direction is:

`MEMBER` → `SUBCONTRACTOR`

The reason is capability preservation: current Member combines full ordinary Register access with technician-side Dispatch receiving. The implementation must use an explicit parser/migration path. It must not rely on a generic `valueOf(stored)` fallback to Solo after `MEMBER` is removed. This is a preference-value migration; no Room migration is required merely for the role preference.

## Completion and conclusion are different

ServiceLoop must keep the following meanings separate.

**Technician completion** is the field role completing local involvement/work under the existing Visit, service, and finalization semantics. It may include performed, partly performed, or not performed outcomes; checklist completion; evidence; Visit lifecycle changes; final records; valid recurrence fulfillment; and local immutable history.

**Coordinator / Team Leader conclusion** is administrative coordination-side closure of work delegated to others. It does not mean that the concluding person performed the service; it does not rewrite technician evidence, replace technician final history, fulfill recurrence a second time, generate a PDF/report, or prove report delivery. A local Outbox `CONCLUDED` row is not central acceptance.

The current Dispatch Outbox `CONCLUDED` concept may be reused and refined. B043 does not invent a false return or synchronization channel. If the current file-based Dispatch lacks technician-to-coordinator result transport, conclusion may remain an administrative local coordinator state. No live sync or backend is authorized.

## Visit Maps direction

All operational field roles—Solo, Subcontractor, Employee, and Team Leader—must be able to launch Maps from a Visit when a usable address exists. Coordinator may also use the same handoff while reviewing or planning a Visit when the Visit detail action is naturally available.

The implementation must use the Visit's captured site/address snapshot where available. It must not silently substitute a later-edited current Site address for a historical or booked Visit. A missing/blank address disables or omits the action. Maps is an external Intent handoff only: no Maps SDK, no location permission, and no ServiceLoop state mutation. Launching Maps does not imply contact, attendance, completion, or service delivery.

## Current implementation seams

These are the important seams for the later production implementation; none is changed by B043 documentation work.

- `app/src/main/java/com/v16studio/serviceloop/ui/DispatchUi.kt`: current `TeamRole` enum (`SOLO`, `MEMBER`, `COORDINATOR`); `Context.teamRole()` and `Context.setTeamRole()`; `DispatchSettings()` choices and technician identity; legacy `COORDINATOR_ENABLED` migration; current Member-specific receiving logic.
- `app/src/main/java/com/v16studio/serviceloop/ui/DispatchCoordinatorUiV2.kt`: `CoordinatorHomeActions`; Technician, Team, and Outbox actions; Dispatch Visit editing/export; Outbox `concludeOutboxVisits` behavior.
- `app/src/main/java/com/v16studio/serviceloop/ui/ServiceLoopNavigation.kt`: static `Register` root; incoming `.slwork` and `.slinsp` handling; role-specific import/dialog routes; directory/editor routes; coordinator routes.
- `app/src/main/java/com/v16studio/serviceloop/ui/ServiceLoopApp.kt`: root navigation composition and `RootScaffold`/`RootNavigation`.
- Customer, Site, Equipment, and Plan screens: separate management actions from operational/read-only detail context.
- `app/src/main/java/com/v16studio/serviceloop/ui/DueVisitUi.kt`: Visit detail, Customer/Site contextual entry, technician start/resume/review flows, and the future Visit Maps insertion point. The existing Visit detail and captured site-address data are the basis for preserving booked/historical address meaning.
- `app/src/main/java/com/v16studio/serviceloop/ui/TemplateUi.kt`: reusable-template management and `.slinsp` exchange/import paths.
- `app/src/main/java/com/v16studio/serviceloop/ui/SearchUi.kt`: Register-style grouped discovery and operational search projections.
- Stage 4/Data Recovery: CSV master-data import remains a bookkeeping path and must not become an Employee workspace substitute.
- `app/src/main/java/com/v16studio/serviceloop/ui/EvidenceUi.kt`: equipment linking versus equipment creation during operational work.
- `app/src/main/java/com/v16studio/serviceloop/data/DispatchPackage.kt`: role-neutral `.slwork` transport/domain semantics, package generation, assignment, and provenance. Role/workspace behavior must not be encoded by changing transport meaning.

## Scope constraints

B043 does not authorize user accounts, authentication, backend or cloud authorization, central policy enforcement, live synchronization, live task reassignment, a web coordinator, a generic RBAC engine, billing, inventory, chat, AI, or a second master-data database. ServiceLoop remains local-first; adopted Dispatch remains file-based. Role behavior is a local product/workspace model, not a security boundary. Existing `.slwork` and `.slinsp` formats, Room version, and transport/provenance rules remain unchanged unless separately adopted.

## Implementation outline

The next production task should:

1. replace the user-facing role taxonomy with the five-role order and exact descriptions, while adding explicit stored `MEMBER` → `SUBCONTRACTOR` compatibility;
2. introduce one capability-aware workspace projection so root navigation, Register management actions, operational details, template exchange, Search, CSV, and equipment actions follow the role model without duplicating directory data;
3. preserve Team Leader's receiver and coordinator sides together, and keep Coordinator planning/conclusion separate from technician execution;
4. retain existing `.slwork` identity, generation, assignment, snapshot, provenance, retry, and file-handoff semantics;
5. add Visit Maps from captured Visit address meaning through a platform Intent with no state mutation; and
6. add semantic tests for each capability row, legacy preference parsing, Team Leader dual paths, Coordinator field-work exclusion, completion/conclusion separation, address-snapshot selection, and unchanged file transport.

## Verification expectations

The implementation task must report evidence separately for unit/domain tests, UI instrumentation, system handoff, and human/rendered inspection. At minimum, verify all five role rows, root/Register visibility, assigned operational context for Employee, Team Leader receiver plus coordinator paths, Coordinator absence of technician execution, explicit legacy parsing, conclusion idempotence/no duplicate business effects, Maps Intent behavior and blank-address handling, and unchanged `.slwork`/`.slinsp` semantics. A passing test must not be treated as proof of a security boundary or live delivery.

This B043 documentation stage performs no Gradle, Android, emulator, device, or lint verification. Its validation is documentation review, targeted contradiction/search review, and `git diff --check` only.

## Superseded assumptions and implementation status

B043 supersedes conflicting older user-facing Solo/Member/Coordinator and four-role-plus-visibility assumptions. Older documents remain historical evidence; they are not retrospectively rewritten. In particular, there is no new “Hide Register” preference, Employee is not a Register role, Team Leader is not coordinator-only or receiver-only, and Coordinator is not a technician performer.

Production implementation has **NOT** begun in this documentation commit. No Kotlin, Java, manifest, Gradle, Room schema, test, or resource file is changed by B043 documentation work.
