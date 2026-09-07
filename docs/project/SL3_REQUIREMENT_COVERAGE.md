# ServiceLoop — SL-3 Requirement Coverage

**Status:** Phase-B technically verified and ready for owner review; not owner accepted, product-valid, or release-ready.

## Implemented

| Area | Coverage |
|---|---|
| Room v4 | Additive v3→4 migration, v1→4/v2→4 chains, fresh v4, exported schema, no destructive fallback. Existing active Booked/Working work-item obligations are backfilled into exclusive visit claims. |
| Directory | Real customer/site/equipment rows, typed detail routes, create/edit forms, stable references, private notes, contact overrides, default site, maps handoff, and equipment-with-no-plan support. |
| Plans | Create/edit positive day/week/month/year recurrence; create atomically adds one current obligation; due-date edit updates that same unconsumed obligation and records a required reason. Optional reusable-template link. |
| Templates | Append-only master/revision/item model; ordered STATUS/TEXT/NUMBER items, Required/Optional, number unit, private guidance; accessible staged Move up/down publishes only into a new revision. Booked visits capture the current reusable revision at Start, while existing Working/final snapshots remain fixed. |
| Booked v4 compatibility | Start validates and reuses a pre-correction deterministic booking-time snapshot when it exactly matches the still-current immutable reusable revision. A changed current revision gets a distinct new snapshot; the prior snapshot stays untouched. Plans with no current template clear the Working snapshot reference. |
| Due work and booking | Deterministic joined due list, filters, one-site multi-select, and full visit setup. Record past creates Working History-only recurring lines with plan/schedule snapshots but no obligation capture/claim or recurrence effect. Booked Start transactionally verifies the exact obligation/claim, reloads current master/report/template details, refreshes legacy v4 booking snapshots, then transitions. Reschedule/cancel/start are transaction-local state checked. |
| Working/final | Existing SL-2 checklist/outcome/fulfillment/finalization remains authoritative. Multi-line visits are grouped by visit; partial/unperformed behavior remains explicit. Claims release only after cancellation/finalization. |
| Parts/photos | Positive finite textual part quantity; machine/work ownership; Android Photo Picker and camera staging; bounded intake; orientation correction/metadata-stripping re-encode; 2,560-pixel longest edge and 20-machine/100-visit caps; app-private bytes, SHA-256 and size; explicit report selection; selected-file integrity gate; final immutable part/photo projections and PDF image pages. Unselected photos do not enter `PublicReportModel`. |
| Contact/follow-up | Dialer/SMS/email/maps intents, manual contact notes, Entered-in-error retention, contact/corrective follow-ups, source visit/work linkage, edit/reschedule and resolve/cancel/reopen history events. No service-plan effect. |
| Search/filter/navigation | Typed local union query across directory, plan, visit/final record and follow-up references/names, including equipment make/model. Global Add equipment uses a real active customer/site chooser before the equipment editor. Result routing remains type-specific. |
| Integrity races | Booked transitions, Working parts/photos/public work/checklist responses/review/completion, and Follow-up edits/transitions re-read authoritative state inside the committing Room transaction. Finalization winning rejects later evidence writes; failed photo metadata commits remove prepared owned files. |
| Unsaved forms | A reusable D01 guard intercepts app-bar and system Back for changed explicit-save directory, plan, template, follow-up, contact-note, and visit-setup buffers, offering Keep editing / Discard changes without claiming durability. Successful Save navigation is direct. |
| Long text | One shared compact three-line editor with a bottom-right accessible expand/resize affordance opens the same buffer in a full-screen surface. Issue found uses this one mechanism; Done does not persist, and the parent Save remains authoritative. |
| Report/privacy | Final records expose textual parts and selected photo entries; PDF/text include part and photo captions. Private/access/internal and unselected-photo fields remain structurally absent from the public model. PDF failure remains independent of business finalization. |

## Phase-B verification evidence

- TESTED — host gate: 91 unit tests, 0 failures/errors/skips; `assembleDebug`, `assembleRelease`, and `assembleDebugAndroidTest` pass; lint reports 0 errors, 12 warnings, and 2 lower-severity findings.
- UI-INSTRUMENTED — expanded normal canonical suite: 27 tests discovered in 341.863 seconds, with 20 passes and 7 deliberate assumption skips for the separately gated persistent/system-handoff cases. This covers v1→4, v2→4, and v3→4 migrations; retained SL-2 finalization/report/root behavior; D01 create/edit behavior; same-buffer long text; global equipment site selection; selected-photo PDF rendering; ordinary directory/plan UI; and lazy-list inspection continuation.
- TESTED / DOMAIN-INSTRUMENTED — focused integrity coverage remains green for History-only finalization, latest/reused Booked snapshots, state races, post-finalization write rejection, photo cleanup, template revision ordering, make/model search, recurrence, finalization idempotency, and report privacy.
- UI-INSTRUMENTED — gated persistent canonical journey: PASS on the final APK in 86.931 seconds against preserved data. The prior failure was an invalid test wait for an off-screen `LazyColumn` child; a later intermittent branch also used ambiguous plan text instead of the existing stable visit-line tag. The corrected test uses list-aware navigation and stable identity. Template, directory, plan, booking/reschedule/cancel/rebook/start, finding/expanded editor, parts, corrective follow-up, completion/finalization, search, and contextual filtering complete successfully.
- HUMAN/RENDERED — latest canonical V-001 final-record, PDF preview, and structured-text report screenshots were inspected at 1080×2424. Visible content is unclipped, headings and public/private meaning are coherent, report controls are reachable, and no visual defect requiring a code change was found. The remaining workflow screens were exercised through real Compose UI assertions; owner product/visual acceptance remains separate.
- SYSTEM-HANDOFF — PASS for Android Photo Picker, Camera, Dialer, SMS composer, Email composer, Maps, and Sharesheet. Tests used fictional data, opened the installed handler/system surface, did not send or place communications, and verified no automatic contact/follow-up/photo effects.
- Canonical preservation — after the final persistent journey, the V-001 historical PDF remained 65,369 bytes with SHA-256 `2cc923ef53f53fb6d0c8e314e7b854009708c5107af4f7ad4bee58f01c71b1b8`; installs used `-r` and no data clear/uninstall/reset occurred.

## Intentionally deferred

- Stage 4: correction/void workflow, archive/retire/move blockers, full record-version/history UI, complete backup/restore/recovery copy/import/export/erase-device.
- Stage 5: reminders/notifications, broad accessibility/device matrix, larger-data performance hardening, and release polish.
- B-008 real-technician pilot remains outstanding.
