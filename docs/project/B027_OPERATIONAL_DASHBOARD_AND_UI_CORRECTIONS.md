# B-027 — Operational Dashboard and UI Corrections

**Implementation branch:** `codex/b013-ui-overhaul`
**Starting revision:** `f9f1fc16b7878015232ecfb687b514a50e7f34bf`
**Status:** Implemented; verification evidence is recorded below after the final acceptance run.

## Scope and invariants

B-027 adds one derived operational work projection without persisting dashboard state or changing Room schema. It preserves local-first storage, Visit lifecycle and recurring-obligation truth, historical/report boundaries, and the separation of private content from customer-facing results. A booked Visit does not fulfill its Service obligation.

## Shared operational model

`OperationalWorkKind` contains `VISIT`, `SERVICE`, and `FOLLOW_UP`. `OperationalWorkState` contains `IN_PROGRESS`, `OVERDUE`, `DUE_SOON`, and `BOOKED`. `OperationalWorkClassifier` is the shared source for the dashboard, contextual Work filters, and live Visit/Service/Follow-up row styling. It uses the business-local date, configured due-soon horizon, business zone, and exact appointment instant where present.

Visit classification:

- `Visits - In progress`: lifecycle is `WORKING`.
- `Visits - Overdue`: lifecycle is `BOOKED` and its exact appointment instant is before now, or it has no exact appointment and its service date is before business-local today.
- `Visits - Due soon`: lifecycle is `BOOKED`, not overdue, and its appointment-local or date-only service date is today through the configured horizon, inclusive.
- `Visits - Booked`: lifecycle is `BOOKED`, not overdue, and outside the due-soon horizon.
- Completed, canceled, invalid, and other non-live lifecycle states are excluded.

Service classification:

- `Services - Overdue`: an outstanding projected due service has a due date before business-local today.
- `Services - Due soon`: its due date is today through the configured horizon, inclusive.
- Due services remain outstanding while attached to a booked Visit; services beyond the horizon do not create a dashboard bucket. There is no `Services - Booked` bucket.

Follow-up classification uses persisted `OPEN` as the only actionable state; `RESOLVED` and `CANCELLED` are excluded. Open follow-ups before today are `Follow-ups - Overdue`, through the inclusive horizon are `Follow-ups - Due soon`, and later dates are `Follow-ups - Booked`.

Urgency is shared and ordered `OVERDUE`, `DUE_SOON`, `IN_PROGRESS`, `BOOKED`. Customer gateways use the most urgent state across that customer's projected items. Customer scope filters by `customerId`.

## Dashboard projection and interaction

`OperationalDashboardProjector` creates only non-empty sections in this order:

1. Visits — In progress.
2. Visits, Services, Follow-ups — Overdue.
3. Visits, Services, Follow-ups — Due soon.
4. Visits, Follow-ups — Booked.

Section titles use the exact `<Type> - <State>` grammar, with the item count displayed in the header. Overdue items sort oldest date/time first; due-soon and booked items sort nearest date/time first; Working Visits sort most recently modified first, with stable reference/ID tie-breakers.

The Home and Customer dashboard use the same `OperationalDashboard` composable. It initially expands Visits - In progress when present, otherwise the first section; sections toggle independently and their state is saveable per scope. An expanded section previews at most five items and adds `View all N ...` when more exist. Contextual Work destinations apply the same classifier to preserve the selected bucket meaning.

## Colors and live work rows

`OperationalWorkColors` maps state, independent of work kind, in both appearances:

| State | Light accent / container | Dark accent / container |
|---|---|---|
| In progress | `#315F96` / `#EAF0FA` | `#B8D7FF` / `#20384F` |
| Overdue | `#A32D35` / `#FBE9E8` | `#FFC0C4` / `#49282D` |
| Due soon | `#875100` / `#FFF3DD` | `#FFDA97` / `#493519` |
| Booked | `#08666B` / `#E5F3F2` | `#A0E4DF` / `#163B3E` |

Outer dashboard sections use the semantic tint and full-height left rail. Child rows retain normal surface color and use the matching restrained dashed border. Live Visits, Due Services, Follow-ups, and Customer follow-up summaries use the same state-to-color design mapping. Customer, Site, Equipment, checklist, form, and historical rows do not receive operational borders.

## Customer detail

Customer detail shows one compact `Work items` gateway only when its customer-scoped projection is non-empty. The gateway displays the total count, has a semantic rail and disclosure affordance, and uses the shared urgency priority. Its destination uses the same dashboard body with `WorkScope.Customer(customerId)` and the customer name in the detail toolbar.

The gateway is part of the customer summary, after identity/contact/actions and before the `Sites` / `Equipment` tabs. It uses the same pale semantic container tint as dashboard sections while remaining a single card without dashboard child-row treatment.

## Correctness correction after independent review

Operational `View all` routing carries the dashboard's `WorkScope`. Global Home routes remain unscoped; Customer dashboard routes carry the real `customerId` as an optional Work query argument. Visits, Due Services, and Follow-ups apply that ID scope before their operational bucket filter, and Work tab changes retain the scope until the destination is left. Scoped Work shows the loaded customer name as a compact scope indication.

Work does not resample Visit timing. The repository's business-time-derived `OperationalDashboardProjection` is the authoritative state lookup for Work operational filters and row colors, including exact appointment boundaries. Derived urgency remains in memory only and is not persisted.

## UI corrections

- Home's previous operational summary and Records needing attention presentation were removed; unrelated coordinator and New visit actions remain. No attention/problem replacement was added.
- Visit Progress rows no longer use a selected teal fill or extra horizontal inset. Service rows use separators between rows with no trailing divider. Checklist and Service completion share a flat, centered `ServiceSectionStripe`; checklist completeness remains below its stripe.
- Add Task presents Site and Equipment as one responsive paired choice. Inspection checklist selection lists only active templates; with none available it is informational text rather than an inert selector.
- Create Visit presents Existing and One-time as a paired choice. Switching modes with selected planned work or tasks requires confirmation; Cancel preserves the current state.
- Due Services always shows Book selected and Start selected actions. They remain disabled without a valid selection and retain existing site and claimed-Visit rules.
- Booked Visit reminders use one selector with inherited default, Off, and supported presets. The resolved global default is displayed; legacy two-hour overrides remain readable and replaceable without becoming a normal preset.
- Review lines receive checklist results from the same repository read model as the completion line. Explicit status, text, number, issue, and not-applicable results are rendered in natural language. Required unanswered questions show `Not answered`; optional unanswered questions are omitted. Private guidance, private notes, inactive reason drafts, and working input buffers are not included. Existing blockers remain, while the generic `Open service` action is removed from ready lines.
- Technician-facing wording was reviewed for implementation leakage. The exact old Start/reload explanation is absent. Backup, template-version, due-date, and changed-due copy was rewritten in operational terms where appropriate; useful privacy, save, and consequence guidance remains.

## Components and files

Shared domain projection: `domain/OperationalWork.kt`, with the Visit summary and follow-up timestamps added to `domain/Models.kt` and `domain/DailyOperationsModels.kt`. Repository projection and observation, including exact appointment-time refresh, live in `data/ServiceLoopRepository.kt`; the Visit SQL projection is in `data/ServiceLoopDao.kt`. View state and route observation are in `ui/ServiceLoopViewModel.kt` and `ui/ServiceLoopNavigation.kt`.

Shared presentation components are in `ui/designsystem/OperationalWorkComponents.kt`; paired choices, record borders, and section stripe are in `ServiceLoopDesignSystemEditors.kt` and `ServiceLoopComponents.kt`. Screen updates are in `ServiceLoopWorkScreens.kt`, `CustomerSiteUi.kt`, `DueVisitUi.kt`, `ServiceProgressUi.kt`, `ServiceChecklistUi.kt`, `ServiceWorkspaceUi.kt`, and `CompletionScreens.kt`. Copy corrections are also in `TemplateUi.kt`, `Stage4Ui.kt`, `DispatchCoordinatorUiV2.kt`, and `ServiceCompletionUi.kt`.

No dependency, toolchain, Room schema, or migration changes were made.

## Tests and verification

Domain tests are in `app/src/test/java/com/v16studio/serviceloop/OperationalWorkTest.kt`. They cover Visit, Service, and Follow-up classifications; outstanding claimed Services; urgency; section order and empty buckets; customer ID scope; and empty projections. A Room-backed test in `VisitWorkFilterScheduleTruthTest.kt` verifies exact appointment classification from injected business time. Compose instrumentation coverage in `OperationalDashboardUiTest.kt`, `CustomerOperationalGatewayUiTest.kt`, `OperationalWorkRoutingUiTest.kt`, and `WorkFilterSelectorUiTest.kt` verifies expansion semantics, independent toggles, five-item preview/View all, ID-scoped customer Visit/Service routing with global Home coverage, deterministic business-time Visit filtering, empty state, customer gateway order and light/dark pale containers, and Due Services action enablement.

Final command results, exact test counts, focused/broad connected results, emulator identity, appearance, adaptive dimensions, screens actually inspected, Git commit, and upstream parity are recorded at handoff. Any visual or connected item not performed is marked **NOT RUN**.

## Known issues

No known production issue is currently recorded. Any remaining verification gap is listed as **NOT RUN** in the final evidence rather than presented as a product defect.
