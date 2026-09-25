# V16 Service — Work Exchange, Reporting, and Outputs

## Native exchange identity

Native local/file exchange uses suffix `.v16service`, MIME `application/vnd.v16studio.v16service+zip`, outer marker `V16ServiceFile`, and `generatedWith: V16 Service`. All are strict version 1. The four supported purposes are FULL_WORKSPACE, DATA_TRANSFER, WORK_ASSIGNMENT, and WORK_RESULT. No template-only purpose or standalone work, technician, or template file format is exposed.

The envelope validates its current purpose, declared JSON entries, versions, sizes, checksums, and semantic payload before mutation. MIME or filename only recognizes a candidate. Unknown MIME/name, unreadable URI, unsupported version, wrong marker/purpose, malformed entries, and trust failure result in an ordinary failure/preview outcome without partial business writes.

## FULL_WORKSPACE

A full workspace package replaces the portable workspace only through the deliberate import/replace gate. Payload and declared sections use strict v1 shapes. It is separate from encrypted Recovery: local-only preferences and platform state retain their adopted portability boundaries.

## DATA_TRANSFER

DATA_TRANSFER is an additive merge; absence never deletes receiver data. Current v1 families are REGISTER, SERVICE_PLANS, INSPECTION_TEMPLATES, PERFORMED_WORK, FOLLOW_UPS, CONTACT_NOTES, CHANGE_HISTORY, and EVIDENCE. Family identities, versions, sections, and binary entries are validated together. The exporter closes selected history/evidence over required directory, plan, template, and immutable context while excluding unrelated branches.

Import classifies trust/conflicts before one atomic merge. Current Customer/site/equipment/plans use source-workspace bindings and conservative conflict handling; source changes or local divergence do not silently overwrite obligations. Imported work, corrections, follow-ups, notes, changes, and evidence keep origin/revision provenance and do not fabricate local Visits, technician execution, or recurrence fulfillment. Exact retries are idempotent; changed immutable identities conflict. Relays preserve original origin.

Templates travel inside DATA_TRANSFER as an inspection-template family. Its inner marker is `V16ServiceInspectionTemplates` and generator is `V16 Service`; this codec is not a standalone template file flow.

## WORK_ASSIGNMENT

WORK_ASSIGNMENT carries a generated coordinator-to-technician WorkPackage with stable Visit/item identity, assignment, captured snapshot, generation, and provenance. Import validates the whole package, issuer/trust, context, and generation before preview/application. It never claims local performance, receipt, central acceptance, or delivery. Editing follows existing generation and freshness rules.

## WORK_RESULT

WORK_RESULT returns immutable delegated results under one trusted issuer. It carries stable logical result/revision identities, source Visit/work-item, source revision, required source `recordedAt`, and captured checklist/work/recurrence/follow-up/photo facts with explicit privacy meaning. Partial receipts are item-level. Exact retry is idempotent, immutable conflicts are rejected, and source chronology stays explicit. Import does not create local technician execution or fulfill recurrence a second time.

Direct WORK_RESULT and relayed PERFORMED_WORK preserve exact source identity and facts. Corrections append revisions. Changed bytes or immutable metadata conflict before any row/file replacement. Package `generatedAt` is transport chronology only.

## Export, reports, and privacy

Export Center provides the adopted shared selection for families, Customer/site/equipment scope, inclusive dates, privacy, inactive rows, and revisions. Native output targets another V16 Service workspace. Readable ZIP is human/data-oriented and is not an authenticated Recovery backup.

Aggregate customer reports freeze selected effective source revisions. Public report data is structurally distinct from internal data. A photo appears in a customer report only when visibility is PUBLIC and the independent inclusion flag is true. Outputs honor privacy selection; omission does not erase receiver data. Void events stay explicit history and do not return as performed work through inactive-directory selection. A verified retained image derivative may satisfy Recovery ownership after original cleanup; the derivative remains required.

## Recovery and Android handoff

Recovery is encrypted `.v16backup` with `V16B` magic, authentication, schema, manifest, validation, staged replacement, journal, rollback, and retry rules. It is not a fifth native purpose or native-import route.

VIEW/SEND/GetContent/DocumentsUI and FileProvider handoffs use one-time URI access. For content URIs, routing checks declared/resolved MIME and provider `OpenableColumns.DISPLAY_NAME`; URI path is not treated as the display name. Query failure is an ordinary unsupported/unreadable outcome. Repeated `onNewIntent` deliveries are handled without persistent grants. Decoder validation, trust review, preview, and explicit apply remain required.

## Stale result evidence and customer sharing

Package validation rejects malformed, unsupported, untrusted, wrong-target input and contradictory contents for one immutable revision before business mutation. A structurally valid trusted WORK_RESULT can still be stale or conflicting against the current assignment or recurrence state. Such a result may retain its immutable source, owned evidence, receipt, and status, while an ineligible recurrence or completion effect is not applied. An exact retry does not duplicate history or business effects.

A voided original PDF remains viewable and exportable as historical evidence, but ordinary customer Share is disabled for that voided version. The current correction or void notice is the customer handoff. A superseded but not voided version retains its warning.

## External Android filter boundary

The app matches only its supported vendor-MIME VIEW and SEND filters. Android cannot use an opaque content provider DISPLAY_NAME to match a generic-MIME URI before delivering it; the in-app picker can still accept a generic-provider file and check its name after selection. Direct generic-MIME handler checks do not prove PackageManager filter matching or Android delivery.
