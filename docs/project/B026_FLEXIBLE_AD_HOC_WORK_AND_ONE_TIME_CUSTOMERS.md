# B026A — Flexible ad-hoc work and one-time customers

**Status:** adopted implementation slice on `codex/b013-ui-overhaul`.

B026A extends the local-first ServiceLoop loop without turning the product into a generic task manager. A customer is explicitly `STANDARD` or `ONE_TIME`; existing creation paths remain `STANDARD` and the one-time type is stored for later B026B UI work.

## Work subject forms

Every working and final work item has a strict `SITE` or `EQUIPMENT` subject:

- `SITE` work is site-scoped and has no Equipment identity, Equipment description, recurring plan, captured obligation, or recurrence effect.
- Known `EQUIPMENT` work stores the Equipment ID plus frozen name/reference and identification snapshots. Later Equipment edits or moves do not rewrite issued history.
- Unidentified `EQUIPMENT` work has no registered Equipment identity or identification snapshots. It may carry only a trimmed description of at most 500 characters and cannot carry a plan or obligation.

Recurring plans remain Equipment-backed and may only belong to `STANDARD` customers. Creation, update, Visit creation, and Visit start enforce this rule with the stable failure message `Recurring service requires a Standard customer`.

## Persistence and recovery

Room is schema v15. `MIGRATION_14_15` adds the customer type and rebuilds only the tables whose B026A flexible-subject columns require it, preserving legacy rows as Standard customers and known Equipment work. `service_plans` is retained with its existing mandatory Equipment foreign key. The migration finishes with an explicit foreign-key check. Recovery format v2 remains unchanged; schema versions 9–15 are accepted, and old backups receive only the B026A default columns during validation.

ServicePlan.equipmentId remains mandatory: every recurring ServicePlan belongs to real Equipment. Flexible subject nullability belongs to WorkItem, FinalWorkItem, and Dispatch work, not ServicePlan. Moving Equipment with any ServicePlans to a ONE_TIME Customer is rejected; Equipment with no ServicePlans may belong to a ONE_TIME Customer.

`.slwork` remains current format v4 and intentionally has no B026A fields. Current v4 import creates Standard customers and known Equipment rows. Export refuses future-shaped flexible dispatch rows with `Flexible dispatch work requires the current dispatch format`.

## Output and history

Directory CSV adds `customer_type` with exact enum values. Records exports include `subject_type` and `equipment_description` for working and final work rows. History groups known Equipment by `EQUIPMENT:<id>` and site/unidentified work by `WORK:<finalWorkItemId>`. Public reports omit fake Equipment identity for site and unidentified work.

B026B UI for creating or editing one-time customers and flexible subjects is deliberately out of scope for this slice.
