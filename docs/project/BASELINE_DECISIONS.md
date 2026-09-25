# V16 Service — Adopted Baseline Decisions

This file records the current owner-approved product and implementation boundaries. The adopted product maps and linked specialist contracts supply detailed behavior.

## Product and authority

V16 Service is a local-first service book for solo technicians and small service companies. Preserve the workflow from Customer/site through equipment, plans, due work, Visits, service history/reports, and future obligations/follow-ups. It is not a generic task manager or enterprise field-service suite.

The authority order is this file, the Conceptual App Map, the Conceptual UI/UX Design, its Action Coverage companion, then current implementation evidence. Unadopted proposals do not silently amend product behavior.

## Local-first boundary

Dispatch and the five-role workspace are local and file-based. Backend/accounts, live/cloud/team synchronization, billing/accounting, inventory, customer portals, central acknowledgement, and unrelated enterprise features require explicit later adoption.

## Durable lifecycle and history

- A scheduled or booked Visit is not evidence that work was performed. Performed work does not automatically fulfill an obligation; finalization, recurrence, report generation, and delivery are separate events.
- Working inspection answers persist as durable drafts. A successful save/finalize state appears only after persistence succeeds.
- Finalized records and source snapshots are immutable. Corrections append revisions; voiding preserves history. A valid obligation is fulfilled exactly once.
- Captured Visit, assignment, template, and source identity remain stable against later master-data edits where history depends on them.
- Follow-ups and corrections retain source/revision lineage. Retries cannot duplicate effects or replace a good durable checkpoint.

See the [lifecycle contract](../product/SERVICE_LIFECYCLE_AND_VISITS.md).

## Role and Dispatch

Roles are Solo, Subcontractor, Employee, Team Leader, and Coordinator. Register access, assigned-work receiving, technician execution, delegation, coordinator tools, and administrative conclusion follow the adopted capability matrix. Coordinator is not a technician performer; Team Leader combines receiving and coordinating. Roles define local workspace behavior, not authentication or a security boundary.

Dispatch remains asynchronous and file-based. Export does not claim receipt/delivery. Import requires validation, issuer trust review, preview, and explicit application. Administrative conclusion does not claim technician performance or central acceptance.

See the [role and Dispatch contract](../product/ROLE_AND_DISPATCH_WORKFLOWS.md).

## Exchange, privacy, and reporting

Native `.v16service` files support exactly FULL_WORKSPACE, DATA_TRANSFER, WORK_ASSIGNMENT, and WORK_RESULT. Recovery is the separate encrypted `.v16backup` contract. Customer-report inclusion requires public photo visibility and the separate inclusion flag. Private/internal data stays out of customer reports. Sharing is a handoff, not delivery.

See the [exchange/reporting contract](../product/WORK_EXCHANGE_REPORTING_AND_OUTPUTS.md) and [artifact contract](REPORT_OUTPUT_ARTIFACT_CONTRACT.md).

## Current pre-release data contract

Room, Recovery, immutable source snapshots, and exchange payloads are current-only version 1. Room is fresh-create only; there is no migration chain, old-format reader, compatibility adapter, automatic legacy rescue, or destructive fallback. No unreleased data or packages must remain readable.

See [`PERSISTENCE_DATA_CONTRACT.md`](PERSISTENCE_DATA_CONTRACT.md) for exact markers, required fields, and rejection rules.

## Presentation and platform integrations

The adopted UI reference and Phosphor Fill icon system govern presentation. Product meaning, privacy, history, and persistence outrank visual emphasis. Reminders are local and privacy-safe. Calendar is an optional one-way local projection and does not imply cloud synchronization.

## B051 identity amendment

The adopted identity is V16 Service under V16 Studio. Current app, database, native file, Recovery, Technician ID, trust-table, and AVD identifiers are listed in [the B051 cutover record](B051_V16_SERVICE_IDENTITY_CUTOVER.md). App version remains `1.3.0` / code `5`; persistence and interchange remain version `1`. This is not a release or pilot-acceptance decision.
