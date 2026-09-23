# B048 — Team, Trusted Exchange, and Post-B047 UX Hardening

**Status:** IMPLEMENTED; required automated gates and focused canonical AVD checks passed.

## Adopted behavior

Home has **Dashboard | Agenda | Team**. Team is the local workspace for role and identity, Trusted IDs, ordinary file exchange, and capability-gated coordinator tools. It is not automatic synchronization: ServiceLoop remains local-first, and users deliberately share readable `.slsync` files.

Each workspace keeps one stable local ServiceLoop ID, backed by the existing `technician_identity.technicianId`. The Team tab labels it **Coordinator ID** for the Coordinator role and **Technician ID** for field-capable roles. Role and business-profile changes do not create another ID. An ID identifies a source in this local trust model; it does not prove who possesses it.

Trusted IDs are device-local configuration. A trusted row has a normalized ID and editable friendly name. The local ID is implicitly trusted; it cannot be added as a separate row. Trust is required for `.slsync` mutation at both the UI and service/import boundary. FULL_WORKSPACE replacement preserves local identity, Trusted IDs, reminder preferences, and recovery metadata. Recovery packages include Trusted IDs and identity; Room 17→18 creates an empty trust table while retaining existing identity. Recovery schema advances from 16 to 17.

## `.slsync` protocol

New files use outer envelope v2 and require `exporterId` for `FULL_WORKSPACE`, `WORK_ASSIGNMENT`, and `TEMPLATE_SHARE`. The wire exporter ID is the stable local ServiceLoop ID; friendly names remain local. Envelope v1 remains structurally readable for verification, is presented as an older file with no source ID, and cannot be imported. Dispatch payload version 5 and the Inspection Template payload version remain unchanged.

FULL_WORKSPACE v2 retains the established section family ordering. `register` section v2 adds repeatable customer contacts, and `team` section v2 adds technician notes. Earlier section versions remain readable as empty optional contacts and notes. Trusted IDs are never part of portable workspace data.

## UX scope

Home operational Dashboard and Agenda rows use one semantic entity icon registry (Visit/list-checks, Service/wrench, Follow-up/flag). Agenda bands use the full content width and centered accessible section headings. Settings no longer exposes Team role or daily Import/export entry points. Customer Contacts are managed from Edit Customer while quick-contact actions remain on Customer detail. Disabled one-time controls use disabled styling. Persistent “Saved on this device” copy and reminder diagnostics were removed. Service Checklist and completion stripes use the full content width, with outcome/work-required labels derived from shared completion rules.

Service → Photos has a dedicated working-evidence gallery, multi-select report-visibility actions, confirmed batch deletion, and a fit/zoom/pan viewer with previous/next controls. Batch operations use the shared photo metadata/finalization serialization. Deletion validates ownership and hashes before filesystem changes and restores deleted files if the database transaction fails; finalized history remains immutable.

The Due Services selection checkbox now uses the same glyph-only `ServiceLoopCheckbox` as other selection controls, with its invisible 48dp target and disabled semantics. Selection is exposed only to roles that can create local work. Checklist and Service-completion bands span the full Service viewport; the Checklist wrapper explicitly fills the list width. `Outcome · Required` and `Work performed · Required` are derived from `ServiceWorkEvaluator`. The former permanent “Saved on this device” copy is removed; the useful save-state affordance is `Saved · <time>`. The Record Contact Other icon is a camera, redundant channel helper copy is removed, and reminder controls no longer show healthy-state channel diagnostics or generic local-storage copy.

The Due Services runtime mismatch was a capability mismatch: selectable rows were visible to roles such as Coordinator while the Book/Start action bar was correctly gated by `canCreateLocalWork`. Those rows could be checked with no available actions. Row selection now uses the same capability gate, clears stale selections when capability changes, and the supported roles retain the pinned selection actions.

## Local trust and future API boundary

B048 provides peer identification and a device-local trust list. A ServiceLoop ID is not cryptographic proof of possession or authentication. Any future web/API integration still needs an actual possession/authentication mechanism before it can be treated as secure. No networking, account, or backend implementation is part of B048.

## Verification evidence

### Automated gates

- PASS — `:app:testDebugUnitTest --no-parallel --console=plain`: 477 tests, 0 failures, 0 errors.
- PASS — `:app:assembleDebug --no-parallel --console=plain`.
- PASS — `:app:assembleDebugAndroidTest --no-parallel --console=plain`.
- PASS — `:app:lintDebug --no-parallel --console=plain`.
- PASS — `git diff --check` (final Git gate).

### Canonical AVD instrumentation

The AVD display name `Pixel 10a ServiceLoop` was resolved dynamically for each device run; adb resolved it to `emulator-5554` during this verification. The app was installed with `install -r`, preserving its data. These focused instrumentation cases passed:

- Due Services actual selection shows a pinned action area and enables Book selected / Start selected / Clear; a Coordinator sees no selection control without local-work capability.
- Agenda renders and navigates its sections and rows.
- Shared selection checkbox remains independently interactive, reports checked state, and keeps the 24dp glyph clear of title/context.
- Service Checklist / Service completion band containers span the viewport. The same test verifies `Outcome · Required` before choosing an outcome and `Work performed · Required` after Performed.
- Room 17→18 preserves local technician identity and creates an empty Trusted IDs table.
- MainActivity Team tab exposes role/ID, Trusted IDs, and Share & receive entry points.
- Customer Editor Contacts section exposes add-contact fields and a disabled one-time control with its reason.
- Photo management opens the viewer, supports multi-selection and report-visibility actions, and presents batch-delete confirmation; the test covers cancellation and a simulated repository deletion failure, verifies the confirmation becomes usable again, and confirms fixture files remain.

The installed app was launched and stayed open for more than five seconds. A Dashboard screenshot was captured from the canonical AVD and inspected; Dashboard, Agenda, and Team tabs, the unchanged Home / Work / Register bottom navigation, and the adopted Visit / Service / Follow-up icons were visible. The screenshot is a local ignored build artifact at `app/build/b048-final-screen.png`.

### Not run / evidence limits

- The Phosphor generator script was unavailable because neither supported Python launcher (`python` / `py -3`) was installed in the environment. The four exact source-derived vector assets were used instead; the generator itself is NOT RUN.
- Physical multitouch zoom is NOT RUN. The emulator UI test opens the fit viewer and exercises navigation/selection; it does not simulate a physical pinch gesture.
- Android picker/sharesheet handoff, an end-to-end file export/import, destructive FULL_WORKSPACE replacement, and successful durable batch deletion were NOT RUN on the preserved app dataset. Protocol, trust, replacement-preservation, and deletion-integrity paths have unit coverage; the photo UI test covers confirmation, cancellation, and failure recovery.
- Manual rendered inspection covered the Dashboard screenshot. Agenda, Team, Due Services, Customer Contacts, and Service/photo flows have UI-instrumented evidence as listed above; no claim is made that every owner-review surface received a separate human visual review.

### Branch handoff

- Branch: `codex/b048-team-trust-ux-hardening`.
- Starting revision: `527fa83f7c5f56cd33b08a10200c5cb20d0418b9`.
- Before commit, `origin/codex/b047-post-pilot-ui-hardening` matched the starting revision. Local and remote `master` both remained at protected baseline `1fd51131b040ab62af3874c1106615b75f3008fe`.
- App version: `1.2.0` / code `4`; Room `18`; Recovery schema `17`; `.slsync` envelope `2`; FULL_WORKSPACE `register` / `team` sections `2`.
- The implementation and verification evidence above belong to this milestone branch; final commit and remote parity are reported in the Git handoff.
