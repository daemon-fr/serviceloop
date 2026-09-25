# V16 Service — Role and Dispatch Workflows

## Workspace roles

The five user-facing roles and order are:

1. **Solo** — manages and performs their own service work.
2. **Subcontractor** — manages their own work and also receives tasks from others.
3. **Employee** — works on tasks assigned by a coordinator.
4. **Team Leader** — performs assigned work and coordinates other technicians.
5. **Coordinator** — plans, assigns, and oversees technician work.

| Role | Register | Receive assigned work | Perform technician work | Delegate/assign | Coordinator tools | Conclude delegated work |
|---|---:|---:|---:|---:|---:|---:|
| Solo | Yes | No | Yes | No | No | No |
| Subcontractor | Yes | Yes | Yes | No | No | No |
| Employee | No | Yes | Yes | No | No | No |
| Team Leader | Yes | Yes | Yes | Yes | Yes | Yes |
| Coordinator | Yes | No | No | Yes | Yes | Yes |

Employee may see Customer/site/equipment/service/checklist context required by an assigned Visit and record history from that assignment. Employee does not receive Register bookkeeping/master-data or reusable-template management, nor create local work definitions. “No Register” does not mean “no operational directory context.”

Team Leader is both receiver and coordinator. Assignment comes from each package and Visit; the role does not make the user leader of every Team or Visit. Coordinator may plan, inspect, and conclude but must not start technician work, answer technician checklist fields, or claim to have performed service. Roles define local workspace behavior, not authentication or a security boundary.

## Technician identity and trust

Each workspace has a stable public Technician ID of form `V16S-XXXX-XXXX-XXXX-CC`. The 12-character payload uses the current alphabet and secure randomness; the two-character checksum hashes `V16S:` plus uppercase payload. Normalization accepts case, omitted separators, and surrounding whitespace; invalid checksum fails. A checksum detects transcription errors, not authentication or identity proof.

A receiver can review and trust an issuer explicitly. Trust is stored locally in `trusted_v16_service_ids`. A trusted ID does not bypass package validation or per-operation review. A display-name change does not change the stable ID.

## Coordinator workflow and generation

Technicians are keyed by stable ID; same-ID/new-name changes require an explicit keep/update choice. Team membership is many-to-many; leadership is stored per membership, with zero/one/multiple leaders allowed. A Team Leader must be a member before selection as leader.

The coordinator Outbox is local Room state. Preparing a Dispatch Visit does not create ordinary planner Visits merely by composing a package. An itemless saved draft is real draft state but not export-ready. Export validates Site, Teams, Equipment, eligible assignees, editor freshness, and non-concluded state before generating a package.

Assignments preserve stable work-item IDs, explicit Everyone versus eligible-Technician assignment, captured checklist/template content, source workspace, generation, sender, and per-Visit provenance. Editing after export creates a new generation when material content changes. Same-generation retries are safe; stale generations require review. Export/share does not claim receipt, delivery, or acceptance.

## Receiver workflow

The receiver selects a `.v16service` file. MIME/name only identifies a candidate. The bounded decoder checks the exact outer marker, version, purpose, declared entries, sizes, checksums, and payload before mutation. The receiver reviews issuer trust and preview, then explicitly applies. Malformed, unsupported, wrong-target, untrusted, unreadable packages, and contradictory contents under one immutable revision are rejected before business mutation. A structurally valid trusted WORK_RESULT with a stale or conflicting assignment/recurrence application may retain its immutable source, owned evidence, receipt, and status, but cannot apply an ineligible completion or recurrence effect. Exact retries do not duplicate history or business effects.

Imported work remains remote assignment context. It becomes locally performed only when the receiver records technician work and finalizes through the normal lifecycle. Results preserve source identity, revision, chronology, evidence, and provenance. Administrative conclusion is distinct from technician completion.

## Platform and scope

Dispatch uses local files and Android one-time picker/share handoffs. There is no backend, account, live sync, central acknowledgement, automatic conflict resolution, or cloud delivery claim. FileProvider authority stays derived from `${applicationId}.reports`, and URI access remains limited to the handoff.

## Canonical Visit and Dispatch truth

Persisted technician Visit states are BOOKED, WORKING, COMPLETED, and CANCELED. Finalization and PDF creation are separate; Dispatch Draft, Dispatched, Canceled, and Concluded are coordinator-side states. COMPLETED alone does not prove that a local final record or PDF exists.

Creating and assigning new work saves canonical working_visits and work_items together with their linked Dispatch overlay in one atomic operation. The overlay is not a second mutable Visit truth, and composing or exporting a package does not mean a technician performed work. Coordinator may create, book, and assign, but cannot Start or document field work. Employee cannot create arbitrary Visits. Team Leader can perform technician work and coordinate it.

A Dispatch-linked canonical Visit is not marked COMPLETED by result handling, and its Outbox is not Concluded, until every relevant Dispatch item has either eligible applied remote result truth or locally finalized work. Manual Outbox conclusion enforces the same item-level test. Partial or stale receipts leave that Visit non-complete; retries do not reapply recurrence or completion. The local technician Complete visit action remains a separate status action and does not fabricate a service record, PDF, recurrence effect, or Coordinator status return. Administrative conclusion never fabricates technician performance or a local final record.

## Detailed Team action visibility

This action matrix is narrower than the general role capability table above and preserves the separately adopted daily-action and management actions.

| Role | Import work | Export results | Dispatch work | Import results | Generate report | Business identity | Role | ID / Trusted IDs | Technicians / Teams | Import shared data | Export data |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Solo | No | No | No | No | No | Yes | Yes | No | No | No | No |
| Subcontractor | Yes | Yes | No | No | No | Yes | Yes | Yes | No | Yes | Yes |
| Employee | Yes | Yes | No | No | No | No | Yes | Yes | No | Yes | No |
| Team Leader | Yes | Yes | Yes | Yes | Yes | Yes | Yes | Yes | Yes | Yes | Yes |
| Coordinator | No | No | Yes | Yes | Yes | Yes | Yes | Yes | Yes | Yes | Yes |

Visibility does not imply that an unfinished workflow is implemented. Import/export results, generic shared-data import/export, and Business identity management are distinct actions and keep the matrix above.
