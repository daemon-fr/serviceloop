# V16 Service — Report and Output Artifact Contract

Every output is truthful about scope, provenance, privacy, and delivery. Creating or sharing a file does not prove another person or system received it.

| Artifact | Data boundary and provenance | Retry and acknowledgement |
|---|---|---|
| Customer PDF | Public report model only; frozen source/revision identity, Visit/reference, date, rendition, correction/void state. Private notes/access/internal content stay out. `Generated with V16 Service`. | Reuse a valid rendition; regeneration creates a new rendition. Android share is a handoff only. |
| Office/email share | Same PDF and public message; recipient is an optional preference, not a delivery service. | Intent launch does not prove sending or receipt; existing guards protect superseded/voided output. |
| Directory CSV | Customer/site/equipment data under explicit private-field selection; UTF-8 header-first table. | Local file preparation only; not a report or backup. |
| Records CSV ZIP | Human-readable/data-oriented records with explicit scope/privacy selection. README identifies generator, time, scope, flags, and archive contents. | Not authenticated Recovery or delivery acknowledgement. |
| Recovery package | Complete private dataset and owned files in encrypted/authenticated `.v16backup`. | Staged, graph-validated restore with journal/rollback; no destructive fallback. |
| Native `.v16service` | One of four supported purposes with exact version, provenance, and bounded entries. | Decoder, trust, preview, and apply are separate gates. Sharing does not claim receipt. |
| Calendar event | Minimal schedule/Visit data with local link and dataset identity. | Provider state is reconciled; failures remain pending rather than shown as success. |
| Reminder notification | Aggregate operational counts and configured horizon; no private notes or contact details. | Local platform posting is not service completion or external delivery. |

`BusinessProfile.technicianName` is the report's authoritative actual technician name. Stable Technician ID and optional team designation are separate Dispatch identity fields; they do not replace the report name.

Photo visibility and customer-report inclusion are independent: only PUBLIC evidence whose separate inclusion flag is set appears in customer output. Internal/private source facts stay structurally separate. Voided records remain explicit history and cannot be resurrected as performed work through inactive-master selection.
