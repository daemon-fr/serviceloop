# B051 — V16 Service Identity Cutover

**Status:** Pre-release implementation task on the existing repository's authorized B051 branch. This record does not claim release, pilot acceptance, or protected-baseline integration.

## Identity and versions

| Surface | Current value |
|---|---|
| Product / studio | V16 Service / V16 Studio |
| Android namespace and application ID | `com.v16studio.v16service` |
| Gradle root project | `V16Service` |
| Room database | `v16service.db`, schema 1 |
| Native exchange | `.v16service`, `application/vnd.v16studio.v16service+zip`, marker `V16ServiceFile`, `generatedWith: V16 Service` |
| Recovery | encrypted `.v16backup`, magic `V16B`, container/schema 1 |
| Public Technician/workspace ID | `V16S-XXXX-XXXX-XXXX-CC`, checksum namespace `V16S:` |
| Trusted identity table | `trusted_v16_service_ids` |
| Canonical AVD display | `Pixel 10a V16 Service` |
| App version | `1.3.0`, version code `5` |

The native envelope supports FULL_WORKSPACE, DATA_TRANSFER, WORK_ASSIGNMENT, and WORK_RESULT only. Assignment and inspection-template codecs stay within those payloads. No compatibility reader, alias, migration, old-ID translator, or rescue path is added.

## Retained product contract

B051 changes identity while retaining the current-only v1 data contract. Room has a generated fresh-create schema only and no destructive migration fallback. Recovery remains encrypted and validates the current database/file graph. Native import remains bounded, trust-reviewed, previewed, and explicitly applied. Visit, result, privacy, provenance, recurrence, retry, and reporting semantics remain in the linked current contracts.

B052 repository creation/root commit and B053 old-location retirement remain pending and were not performed in this task.

## Execution record

Authorized branch: `codex/b051-v16-service-identity`. The final SHA, focused and broad checks, runtime/device evidence, residue scan, visual asset inspection, AVD state, remote parity, and protected-ref checks are recorded in `IMPLEMENTATION_STATE.md` after completion. This file defines the identity contract; it does not substitute for execution evidence.
