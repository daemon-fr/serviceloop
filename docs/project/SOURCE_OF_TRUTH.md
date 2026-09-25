# V16 Service — Source of Truth

**Status:** Current authority map for the pre-release implementation line.

## Product authority

1. Explicit owner decisions and amendments in [`BASELINE_DECISIONS.md`](BASELINE_DECISIONS.md).
2. [`../product/V16Service_Conceptual_App_Map_v0_1.md`](../product/V16Service_Conceptual_App_Map_v0_1.md) — adopted functional baseline.
3. [`../product/V16Service_Conceptual_UI_UX_Design_v0_1.md`](../product/V16Service_Conceptual_UI_UX_Design_v0_1.md) — adopted UI/UX baseline.
4. [`../product/V16Service_UI_UX_Action_Coverage_v0_1.md`](../product/V16Service_UI_UX_Action_Coverage_v0_1.md) — adopted traceability companion.
5. Current specialist contracts linked below define persistence, lifecycle, roles, exchange, reports, platform, presentation, and quality details.
6. Current code, tests, and verification establish implementation; divergence alone creates no new requirement.

The adopted Conceptual App Map remains the starting functional baseline. Where a topic is specifically amended, the recorded owner decision and the current specialist contract carrying that amendment override conflicting map text; see the [lifecycle](../product/SERVICE_LIFECYCLE_AND_VISITS.md), [role/Dispatch](../product/ROLE_AND_DISPATCH_WORKFLOWS.md), [exchange/output](../product/WORK_EXCHANGE_REPORTING_AND_OUTPUTS.md), [Calendar](PLATFORM_REMINDERS_AND_CALENDAR.md), [persistence](PERSISTENCE_DATA_CONTRACT.md), and [quality](ARCHITECTURE_AND_QUALITY_CONTRACT.md) contracts. Elsewhere the map controls. Unadopted proposals do not become authority through detail or length. Matching HTML is a reading format, not a separate requirement.

## Current specialist contracts

- [Persistence and data contract](PERSISTENCE_DATA_CONTRACT.md)
- [Service lifecycle and Visit contract](../product/SERVICE_LIFECYCLE_AND_VISITS.md)
- [Role and Dispatch workflows](../product/ROLE_AND_DISPATCH_WORKFLOWS.md)
- [Work exchange, reporting, and outputs](../product/WORK_EXCHANGE_REPORTING_AND_OUTPUTS.md)
- [Report and output artifacts](REPORT_OUTPUT_ARTIFACT_CONTRACT.md)
- [Reminder and Calendar integration](PLATFORM_REMINDERS_AND_CALENDAR.md)
- [UI style and icon system](UI_STYLE_AND_ICON_CONTRACT.md)
- [Architecture and quality](ARCHITECTURE_AND_QUALITY_CONTRACT.md)

## Process authority

For execution and review, use [AGENTS.md](../../AGENTS.md), [delegation and orchestration](DELEGATED_AI_DEVELOPMENT_AND_ORCHESTRATION_v1_0.md), and the mandatory [prompt and handoff standard](AI_CODEX_PROMPT_AND_HANDOFF_STANDARD.md). Process documents do not override product requirements.

## Current implementation pointer

Read [IMPLEMENTATION_STATE.md](IMPLEMENTATION_STATE.md) and verify Git directly for branch/SHA. The B051 record is [here](B051_V16_SERVICE_IDENTITY_CUTOVER.md). The app is pre-release; pilot acceptance and release authorization are separate owner gates.
