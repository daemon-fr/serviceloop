# ServiceLoop — SL-3 Requirement Coverage

**Status:** independent-review correction implementation candidate; not technically or owner accepted. Exhaustive verification is pending Testing AI Model Phase B.

## Implemented

| Area | Coverage |
|---|---|
| Room v4 | Additive v3→4 migration, v1→4/v2→4 chains, fresh v4, exported schema, no destructive fallback. Existing active Booked/Working work-item obligations are backfilled into exclusive visit claims. |
| Directory | Real customer/site/equipment rows, typed detail routes, create/edit forms, stable references, private notes, contact overrides, default site, maps handoff, and equipment-with-no-plan support. |
| Plans | Create/edit positive day/week/month/year recurrence; create atomically adds one current obligation; due-date edit updates that same unconsumed obligation and records a required reason. Optional reusable-template link. |
| Templates | Append-only master/revision/item model; ordered STATUS/TEXT/NUMBER items, Required/Optional, number unit, private guidance; accessible staged Move up/down publishes only into a new revision. Booked visits capture the current reusable revision at Start, while existing Working/final snapshots remain fixed. |
| Due work and booking | Deterministic joined due list, filters, one-site multi-select, and full visit setup. Record past creates Working History-only recurring lines with plan/schedule snapshots but no obligation capture/claim or recurrence effect. Booked Start transactionally verifies the exact obligation/claim, reloads current master/report/template details, refreshes legacy v4 booking snapshots, then transitions. Reschedule/cancel/start are transaction-local state checked. |
| Working/final | Existing SL-2 checklist/outcome/fulfillment/finalization remains authoritative. Multi-line visits are grouped by visit; partial/unperformed behavior remains explicit. Claims release only after cancellation/finalization. |
| Parts/photos | Positive finite textual part quantity; machine/work ownership; Android Photo Picker and camera staging; bounded intake; orientation correction/metadata-stripping re-encode; 2,560-pixel longest edge and 20-machine/100-visit caps; app-private bytes, SHA-256 and size; explicit report selection; selected-file integrity gate; final immutable part/photo projections and PDF image pages. Unselected photos do not enter `PublicReportModel`. |
| Contact/follow-up | Dialer/SMS/email/maps intents, manual contact notes, Entered-in-error retention, contact/corrective follow-ups, source visit/work linkage, edit/reschedule and resolve/cancel/reopen history events. No service-plan effect. |
| Search/filter/navigation | Typed local union query across directory, plan, visit/final record and follow-up references/names, including equipment make/model. Global Add equipment uses a real active customer/site chooser before the equipment editor. Result routing remains type-specific. |
| Integrity races | Booked transitions, Working parts/photos/public work/checklist responses/review/completion, and Follow-up edits/transitions re-read authoritative state inside the committing Room transaction. Finalization winning rejects later evidence writes; failed photo metadata commits remove prepared owned files. |
| Unsaved forms | A reusable D01 guard intercepts app-bar and system Back for changed explicit-save directory, plan, template, follow-up, contact-note, and visit-setup buffers, offering Keep editing / Discard changes without claiming durability. Successful Save navigation is direct. |
| Long text | One shared compact three-line editor with a bottom-right accessible expand/resize affordance opens the same buffer in a full-screen surface. Issue found uses this one mechanism; Done does not persist, and the parent Save remains authoritative. |
| Report/privacy | Final records expose textual parts and selected photo entries; PDF/text include part and photo captions. Private/access/internal and unselected-photo fields remain structurally absent from the public model. PDF failure remains independent of business finalization. |

## Verification evidence

- Independent-review correction development checks add focused Robolectric coverage for History-only creation/finalization, Start-time latest-template capture plus Working/final freeze, stale obligation blocking, deterministic transition/evidence/follow-up races and photo cleanup, template reorder revision isolation, and make/model search. Compose source coverage covers D01 app-bar/system Back, same-buffer long text, and the global equipment site chooser. Full Phase-B runtime/regression acceptance remains pending.

- Accepted baseline before edits: `testDebugUnitTest`, `assembleDebug`, `lintDebug`, `assembleDebugAndroidTest` PASS.
- Unit coverage includes directory identity/edits, exactly-one obligation creation, same-obligation due edit, template revision snapshot stability, exclusive/concurrent claims, cancellation release, one-site rejection, contact/follow-up non-effects, parts/photo privacy/final snapshots, and typed search.
- Canonical AVD instrumentation includes v1→4, v2→4, v3→4 migrations; SL-2 finalization/report/root regressions; normal UI directory→site→equipment→plan creation; and inline finding semantics.
- Gated persistent canonical journey: PASS (1 test, 99.6 seconds), using the preserved production database. It covered template, directory and plan setup, booking/reschedule/cancel/start, inspection/finding, parts, corrective follow-up, finalization, typed search, and contextual due filtering.
- Normal canonical suite: PASS (18 tests, 160.336 seconds). The persistent journey is gated behind `sl3RuntimeJourney` and is an expected assumption skip in the normal suite so it cannot mutate owner-review data during routine runs.
- Final local release gate: PASS — `:app:testDebugUnitTest` (82 tests), `:app:assembleDebug`, `:app:lintDebug` (0 errors, 12 warnings, 2 hints), `:app:assembleRelease`, and `:app:assembleDebugAndroidTest`.
- SYSTEM-HANDOFF for picker/camera/dialer/SMS/email/maps was not exhaustively executed. Intent/contract integration is implemented; delivery, capture, message sending, call completion, or navigation success is never claimed.
- HUMAN/RENDERED Stage-3 owner review was not performed in this implementation pass.

## Intentionally deferred

- Stage 4: correction/void workflow, archive/retire/move blockers, full record-version/history UI, complete backup/restore/recovery copy/import/export/erase-device.
- Stage 5: reminders/notifications, broad accessibility/device matrix, larger-data performance hardening, and release polish.
- B-008 real-technician pilot remains outstanding.
