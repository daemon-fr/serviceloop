# V16 Service — Persistence and Data Contract

**Status:** Current pre-release contract; fresh-install version 1 only.

## Authority and release boundary

This document carries forward the authorized clean-slate pre-release contract. App version remains `1.3.0` / code `5`. No pre-release business rows, backup packages, or exchange files need to remain readable. Release compatibility promises begin only with an owner-designated release.

## Current version matrix

| Surface | Current contract | Rejected behavior |
|---|---|---|
| Room | Schema version 1; generated current schema only. Fresh creation initializes local identity, recovery tracking, and defaults. | No migration registration, version backfill, historical entity adapter, or destructive fallback. |
| Recovery | Encrypted container and database schema version 1, exact current table/column/value shapes, ASCII magic `V16B`, suffix `.v16backup`. | No old marker/schema reader, normalization, column adapter, incomplete shape, or unencrypted native-file import. |
| Native envelope | `V16ServiceFile`, version 1, MIME `application/vnd.v16studio.v16service+zip`, suffix `.v16service`, mandatory canonical exporter identity. | No older envelope or source-less mutation package. |
| Native purposes | FULL_WORKSPACE, DATA_TRANSFER, WORK_ASSIGNMENT, WORK_RESULT only. | No fifth purpose or TEMPLATE_SHARE route. |
| FULL_WORKSPACE | Payload and each section are strict version 1; declared section names/versions exactly match the current contract. | No older section adapter or omitted-field backfill. |
| DATA_TRANSFER | Metadata and families use strict version 1; templates use the inspection-template family. | No legacy family adapter or template-only public format. |
| WORK_RESULT | Body and results section use version 1. Origin workspace, source Visit/work-item, source revision identity/number, and source `recordedAt` are required. | No reduced record shape, inferred lineage, or package-generation chronology fallback. |
| Dispatch payloads | The v1 WorkPackage codec is inside WORK_ASSIGNMENT; the inspection-template codec is inside DATA_TRANSFER. Technician IDs use manual/Copy ID and directory identity semantics; no standalone Technician identity payload codec exists. | No purpose-specific public file extensions or new native purpose. |
| Immutable snapshots | Wrapper, source payload, and fingerprint are strict version 1; exact shape and purpose are checked before use. | No raw-record fallback, version negotiation, replay backfill, or inferred fingerprint. |

## Room and Recovery agreement

The generated Room schema at `app/schemas/com.v16studio.v16service.data.V16ServiceDatabase/1.json` is the source for Recovery table/column declarations. Recovery validates exact table sets, column order/name/type, required values, JSON shapes, graph links, and owned-file declarations before replacement. Empty tables are declared and validated. A rejected package leaves durable data untouched. Replacement uses current staging, transactions, operational journals, and rollback rules. Transient journal files coordinate safe replacement and are not portable business archive entries.

The trust entity/table is `TrustedV16ServiceIdEntity` / `trusted_v16_service_ids`. Room v1 uses a fresh `v16service.db`; this is not a rename migration.

## Durable source truth

- `final_work_items.followUpsSnapshotJson` is non-null and captures the revision-time follow-up set, including an explicit empty set.
- Imported and remote final result rows retain non-null immutable source snapshots whenever current writers create them.
- Result chronology comes from required source `recordedAt`. Package generation/import times describe transport and receipt only.
- Corrections retain revision number, source revision ID, supersession link, and correction/public-note values. Conflicting immutable revision identities cannot silently select a winner.
- Snapshot decode requires the exact current wrapper fields and purpose/version pair. Recovery cross-checks source identity against indexed Room columns.
- Retries preserve identity, never rewrite finalized snapshots, repeat recurrence, invent source history, or fill missing source facts.
- Public report facts remain structurally separate from private/internal source facts. Transfer privacy choices are explicit; omission does not erase receiver values.

## File ownership and retry safety

Durable evidence is app-owned. Recovery includes the current owned-file registry and adoption journal. Restore validates and stages files before replacement, then uses the journal for retry and rollback. A verified retained image derivative may satisfy Recovery ownership after deliberate original cleanup; the derivative remains required.

Completion, import, report generation, correction, image cleanup, and restore retain current transaction/file-lock ordering and idempotency. A failed operation preserves the last durable checkpoint and remains visibly failed.
