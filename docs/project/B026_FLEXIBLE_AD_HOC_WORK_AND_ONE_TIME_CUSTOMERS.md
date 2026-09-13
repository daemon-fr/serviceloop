# B026 — Flexible ad-hoc work and one-time customers

**Status:** adopted implementation slice on `codex/b013-ui-overhaul`.

B026 extends the local-first ServiceLoop loop without turning the product into a generic task manager. A customer is explicitly `STANDARD` or `ONE_TIME`; recurring service remains Standard-only, while local one-time visits and flexible work are persisted as ordinary ServiceLoop history.

## B026A foundation

## Work subject forms

Every working and final work item has a strict `SITE` or `EQUIPMENT` subject:

- `SITE` work is site-scoped and has no Equipment identity, Equipment description, recurring plan, captured obligation, or recurrence effect.
- Known `EQUIPMENT` work stores the Equipment ID plus frozen name/reference and identification snapshots. Later Equipment edits or moves do not rewrite issued history.
- Unidentified `EQUIPMENT` work has no registered Equipment identity or identification snapshots. It may carry only a trimmed description of at most 500 characters and cannot carry a plan or obligation.

Recurring plans remain Equipment-backed and may only belong to `STANDARD` customers. Creation, update, Visit creation, and Visit start enforce this rule with the stable failure message `Recurring service requires a Standard customer`.

## Persistence and recovery

Room is schema v15. `MIGRATION_14_15` adds the customer type and rebuilds only the tables whose B026A flexible-subject columns require it, preserving legacy rows as Standard customers and known Equipment work. `service_plans` is retained with its existing mandatory Equipment foreign key. The migration finishes with an explicit foreign-key check. Recovery format v2 remains unchanged; schema versions 9–15 are accepted, and old backups receive only the B026A default columns during validation.

ServicePlan.equipmentId remains mandatory: every recurring ServicePlan belongs to real Equipment. Flexible subject nullability belongs to WorkItem, FinalWorkItem, and Dispatch work, not ServicePlan. Moving Equipment with any ServicePlans to a ONE_TIME Customer is rejected; Equipment with no ServicePlans may belong to a ONE_TIME Customer.

`.slwork` remains current format v4 and intentionally has no B026 fields. Current v4 import creates Standard customers and known Equipment rows. Export refuses future-shaped flexible dispatch rows with `Flexible dispatch work requires the current dispatch format`.

## Output and history

Directory CSV adds `customer_type` with exact enum values. Records exports include `subject_type` and `equipment_description` for working and final work rows. History groups known Equipment by `EQUIPMENT:<id>` and site/unidentified work by `WORK:<finalWorkItemId>`. Public reports omit fake Equipment identity for site and unidentified work.

## B026B local workflows

The local B026B slice is implemented without accounts, sync, dispatch, invoicing, or backend services:

- New Visit supports Existing customer/site and One-time customer modes. One-time visits atomically create one `ONE_TIME` customer, one default site, and one or more local tasks; retrying creates a separate visit and does not reuse a previous customer.
- An existing `ONE_TIME` customer/site may already have registered Equipment, so its local ad-hoc work can use known Equipment. A newly quick-created one-time branch starts without registered Equipment; its first tasks can use the site or an unidentified equipment description.
- Existing-site selection and changing the selected site use the same deliberate discovery rule: a blank query shows Standard sites, while a nonblank matching query may reveal a matching one-time site.
- Existing visits can add local tasks while `BOOKED` or `WORKING`, including `SITE`, known `EQUIPMENT`, and unidentified equipment-description subjects. Planned services remain separate, current, and claim-safe.
- Reusable checklist templates are selected per local task. Ad-hoc task templates are snapshotted when the task is created; starting a booked visit does not replace that snapshot. Recurring plan snapshots retain their existing start-time refresh rules.
- Working unidentified equipment tasks expose a same-site equipment picker and an add-equipment form. Linking existing equipment or creating and linking equipment is transactional, same-site constrained, and unavailable outside `WORKING` visits or for tasks that are not awaiting equipment identification.
- One-time customer, site, equipment, visit, and search projections identify the one-time status. The register hides one-time rows by default behind `Show one-time customers`. Customer details offer `Make Standard`; Equipment details hide recurring-plan creation until promotion and explain `Recurring service requires a Standard customer`.
- All task creation, one-time creation, promotion, and equipment linking preserve durable-save semantics and use row insertion order for visit/task display ordering.

The B026B UI is intentionally local-first and does not start B026C or any later dispatch/bundle work.
