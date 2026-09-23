# ServiceLoop — Implementation State

**Updated:** 2026-09-23

This file is the concise current-state summary. Detailed milestone evidence remains in Git history and focused coverage/tests; detailed Dispatch semantics remain in `docs/internal/DISPATCH_PACKAGES_PROTOTYPE.md`.

## Current working-state pointer

This file is the maintained first-stop implementation pointer. A fresh AI should read this section before historical milestone detail, then verify Git directly.

Current B049 implementation line:

- active branch: `codex/b049-work-exchange-reporting-export-center`
- current remote checkpoint before this process-doc integration: `ac6c0d64d34c7f371f3c257225ea5252756c121e`
- B049 source/base: B048 `cadc0668fe2ba5172d17a86a157323303a71d83f`
- protected `master`: `1fd51131b040ab62af3874c1106615b75f3008fe`
- current executable at the partial B049 checkpoint: `1.2.0` / versionCode `4`
- current Room: v18
- current Recovery: schema v17
- B049 remains in progress; see the B049 milestone authority/checkpoint record for implemented vs remaining stages.

For every new substantial Codex prompt, correction prompt, or fresh-thread handoff, first read and apply:

    docs/project/AI_CODEX_PROMPT_AND_HANDOFF_STANDARD.md

After this process-doc integration, verify the branch HEAD directly rather than treating the pre-integration checkpoint SHA above as the current tip.

## Accepted baseline and active implementation line

Protected accepted baseline:

- branch: `master` at `1fd51131b040ab62af3874c1106615b75f3008fe`
- SL-5C functional-freeze production checkpoint: `bf55b0bd027fa25c48fc2dfd930d257688088ecb`
- Room schema at the historical SL-5C checkpoint: v11

The B043 implementation line is `codex/b043-five-role-workspace`. This production task started from `7d4382683253c7c9e680b3cd0add5b34bfe16377`; its implementation commits are `967e889`, `e6cd912`, and `1cd2103`. The earlier B043 authority commit is `309d9f9b5c0454c3fc6b8dc210ecbaf3a43203a4`. B043 is implemented and verified for owner review but does not advance protected `master`. The preceding `codex/b013-ui-overhaul` line, Room v15, and its B-025/B-026 implementation evidence remain historical branch context. Historical SL-5C results below remain evidence for their stated revision only.

## Historical accepted state and active branch context

- **SL-1:** OWNER ACCEPTED.
- **SL-2:** OWNER ACCEPTED. Accepted user-facing implementation/review state: `d97a8c0013dcea924d91ace993a1325ac16cf5b3`.
- **SL-3:** OWNER ACCEPTED. Accepted production implementation/review state: `ad078faae3c73faa2a9bb02dd1251b1033237399`.
- **SL-4:** IMPLEMENTED, independently Phase-B verified, and **VERIFIED AND BANKED**.
- **Dispatch:** OWNER APPROVED under B-015 and deliberately integrated/banked as real product scope within the local-first/file-based boundary.
- **B-014 durable Working inspection-response drafts:** adopted and preserved through Room v11.
- **B-013 output / identity / inspection-transport pass:** implemented on `codex/b013-ui-overhaul`; the output boundary and evidence matrix is in `B013_OUTPUT_ARTIFACT_MATRIX.md`. Final branch acceptance evidence is recorded at handoff.
- **SL-5A — time-aware work state + local reminders:** IMPLEMENTED and independently reviewed.
- **SL-5B — optional Android Calendar integration:** IMPLEMENTED, corrected, source-reviewed and banked under B-016.
- **SL-5C — final functional completion/hardening:** IMPLEMENTED and reviewed at `bf55b0bd027fa25c48fc2dfd930d257688088ecb`; no known ordinary-workflow functional placeholder remains before B-013.
- **B-008 real-technician pilot:** outstanding and still a release-validity gate.

## B-027 — operational dashboard and UI corrections

Implemented on `codex/b013-ui-overhaul` from `f9f1fc16b7878015232ecfb687b514a50e7f34bf`. The shared derived operational classifier/projection now drives Home, Customer Work items, contextual Work filters, and state-colored live work rows. Home uses expandable non-empty buckets with the adopted urgency/order, full-height semantic rails, normal-surface child rows, dashed state borders, and five-item previews. Customer detail has an ID-scoped Work items gateway and shared customer dashboard route. Visit creation/Add Task, Due Services actions, reminders, Visit Progress, service stripes, Review checklist projection/privacy, and technician-facing copy were corrected without changing business invariants or Room schema. Detailed behavior and verification scope are documented in `B027_OPERATIONAL_DASHBOARD_AND_UI_CORRECTIONS.md`.

## B-028 — navigation, Work actions, and customer-type UX

Implemented on `codex/b013-ui-overhaul` from the B-013 UI-overhaul line. This pass adds the reusable disclosure navigation button, Calendar-off settings remedy, shared Visit Progress numbering, Service Plan action spacing, one-way Work New-visit floating-to-docked behavior across all three Work tabs, and consistent Standard/One-time Customer type UX across New Visit, Register Add, and Edit Customer. The transactional downgrade guard rejects Standard → One-time when any recurring ServicePlan exists while preserving Sites, Equipment, history, obligations, and Register filtering. Detailed scope and evidence are documented in `B028_NAVIGATION_WORK_ACTIONS_AND_CUSTOMER_TYPE_UX.md`.

## Functional freeze status

The current product is functionally frozen for the purpose of beginning B-013.

This means the adopted functional structure is considered coherent enough that the next milestone should redesign the complete interface rather than continue piecemeal feature work.

It does **not** mean:

- current wording is final;
- localization is complete;
- the present UI is the target design;
- B-008 pilot evidence exists;
- the product is release-ready.

B-017 explicitly defers localization/final-copy work until after B-013 stabilizes the user-facing interface and wording.

## Integrated SL-4 / Dispatch baseline still in force

The current line preserves the verified History/recovery milestone and adopted Dispatch module, including corrections/voids/history, lifecycle/move safeguards, complete authenticated backup and staged replacement restore, CSV boundaries, canonical Technician identity, Teams/leaders, batch `.slwork`, generation-safe import/update/withdrawal, handoff/participation-complete semantics and immutable Dispatch provenance. The hard no-backend/accounts/live-sync/push/chat/shared-database boundary remains unchanged.

## B-014 — durable inspection response drafts

Working inspection response modes retain separate saved drafts for Issue-found description, Not-applicable reason and text/number Value. Switching disposition is non-destructive. Only the selected disposition and applicable detail participate in checklist validity, finalization and customer-facing snapshots.

## SL-5A — time-aware work state and reminders

The midnight-staleness gap remains fixed. Date-derived Home/Work state uses an injectable business clock/zone plus an observable business-date token and one suspended wait to the next business-local midnight. Foreground/resume and relevant clock/date/timezone/business-zone changes invalidate immediately; no polling/service/manufactured Room write is used.

Room v11 persists the adopted reminder preferences and bounded per-Visit appointment lead override. Defaults remain: daily summary 08:00, all days, 14-day shared Due-soon horizon, all summary categories enabled, appointment alerts Off, default lead 3 hours. The current UI presets are 1h, 3h, 6h, 12h, 24h, and 48h; the prior 2-hour value remains readable for existing saved data and is replaced when the user chooses a new preset. Device-local reminder delivery is Off initially and excluded from portable recovery.

Reminder scheduling remains one-shot approximate `AlarmManager.setWindow` with stable Work-summary and Appointment-reminder channels, contextual notification permission, privacy-safe content, duplicate-summary suppression, stale appointment suppression, reboot/time/process reconciliation and dataset-scoped PendingIntent identity. Exact alarms, foreground service and battery exemption are not used.

**Verification boundary:** actual Android notification delivery remains unclaimed on the preserved canonical dataset because local reminder delivery / `POST_NOTIFICATIONS` remained Off/denied during validation.

## SL-5B — optional Android Calendar projection

B-016 remains implemented through Android `CalendarContract`, Calendar Provider and `ContentResolver`; it does not use Google OAuth/API, backend or network account logic.

Calendar integration remains Off by default. Selected Calendar, event IDs, Visit-event links, fingerprints, suppression and pending deletion state are device-local under `noBackupFilesDir`, scoped to the current dataset and excluded from portable recovery.

Automatic creation applies only to timed Booked local Visits. Normal reschedule/public Site/Customer changes update the same event ID. External Calendar edits never update ServiceLoop. External deletion becomes Missing until deliberate Recreate. Per-Visit Remove adds suppression; Add clears it. Global Disable retains existing external events/links. Cancelled and `DISPATCH_WITHDRAWN` future events are removed where possible; provider failure leaves retryable `DELETE_PENDING`; Working, Finalized and `PARTICIPATION_COMPLETE` events remain historical evidence.

The Calendar coordinator observes `working_visits`, `customers`, and `sites`; this covers ordinary booking changes plus Dispatch import, generation update and withdrawal. SL-5C additionally hardened its device-state file replacement against transient/concurrent Windows contention using serialized access, unique temporary files, bounded retries and last-good-file preservation.

**Provider-validation boundary:** REAL CALENDAR PROVIDER MUTATION remains **NOT RUN** because no safely disposable writable Calendar was established on the canonical AVD.

## SL-5C — final functional hardening

Concrete production fixes at `bf55b0bd027fa25c48fc2dfd930d257688088ecb`:

- removed stale user-facing `Experimental` wording from adopted Coordinator tools;
- removed an unreachable legacy foundation-placeholder route;
- Home, Work and Customers now surface post-load root refresh failures while preserving the last durable result and provide a functioning Retry action;
- Calendar device-state persistence hardened against concurrent/transient Windows replacement contention;
- relevant Dispatch UI selectors/tests updated to match current production wording/state.

The milestone also added bounded larger-data coverage using 250 Customer/Site/Equipment/Plan/Obligation branches and 120 Booked Visits across principal registers, Due services, Home totals, Visits and search. No blocking scaling issue was found.

Release behavior remains intentionally clean: release `FixtureSeederFactory` continues to return `NoOpStartupSeeder`, so development sample data is not auto-created in release builds.

## SL-5C verification checkpoint

Final production checkpoint:

`bf55b0bd027fa25c48fc2dfd930d257688088ecb`

Reported evidence:

- full unit suite: **201 PASS**;
- focused Calendar contention / Dispatch observation / larger-data suites: PASS;
- instrumentation: **19 PASS**;
- retained Room v1→v11 migration/FK coverage: PASS;
- Dispatch import/generation regression: PASS;
- completion/history/report semantics: PASS;
- reminder regression: PASS;
- Calendar settings/regression: PASS;
- Due-services projection: PASS;
- canonical persistence/PDF rendering: PASS;
- `:app:assembleDebug`: PASS;
- `:app:assembleDebugAndroidTest`: PASS;
- `:app:lintDebug`: PASS;
- `:app:assembleRelease`: PASS;
- `git diff --check`: PASS;
- Room was v11 at this checkpoint; B013 adds the non-destructive v12→v13 migration;
- release fixture seeder remains no-op;
- final debug APK installed explicitly with `adb -s <resolved> install -r` on canonical `Pixel_10a_ServiceLoop`;
- canonical business dataset preserved and representative Home/Work/Customers/Settings/Reminders/Calendar/report surfaces inspected non-destructively;
- no security-command block encountered.

Execution-environment recovery evidence: one exact-match managed patch helper failure used the approved shell/file-edit fallback, and Gradle sandbox cache access recovered through the approved runner path without turning the task into a false project blocker.

## Forward sequence recorded at SL-5C

1. **B-013 — dedicated whole-product UI/UX overhaul**
   - implement the comprehensive visual/UI authority against the functionally frozen product;
   - professional customer PDF/share metadata, authoritative BusinessProfile technician name, optional dispatch designation, current-only `.sltech`/`.slinsp`/`.slwork` transport and immutable inspection snapshots;
   - coherent hierarchy/task clarity;
   - reusable components/primitives and action/navigation patterns;
   - typography, spacing/density and semantic colors;
   - complete light/dark themes;
   - loading/empty/error states;
   - accessibility, contrast and touch-target review;
   - remove the current semi-default/semi-incremental Compose appearance.

2. **Localization / final copy freeze — B-017 sequencing**
   - after B-013 settles labels, dialogs, helper text and interaction wording;
   - implement Android localization infrastructure and Romanian UI/report/notification/Calendar/system-handoff copy.

3. **B-008 real-technician pilot**
   - coherent finished-looking Romanian-localized build;
   - no known ordinary-workflow placeholders;
   - representative customer/site/equipment/dispatch-or-local-visit/documentation/report flow evaluated by at least one real technician/trade user.

4. **Pilot fixes / final hardening**
5. **Release preparation and submission**

## B029 implementation state

- Work queues use the one-way unresolved/floating/docked New visit action state and device-local SharedPreferences filter memory described in `B029_WORK_FILTERS_VISIT_SETUP_AND_TEMPLATE_UX.md`.
- Reusable inspection templates retain immutable revisions and support active/disabled/deleted master state. Disabled assignments already held by a plan remain executable and capture the current immutable revision; new assignments require Active.
- Current branch work is intentionally unaccepted and remains on `codex/b013-ui-overhaul` until the B029 review gate is complete.

## B030 implementation state

- Template editor polish is implemented: explicit item-type cards with radio semantics, compact private technician guidance, collapsed `Remove | Edit` rows, expanded `Remove | Move up | Move down | Done` controls, synchronized Activate/Disable state actions, quiet checklist rows, text-only detail Delete, and `Version history (N)` navigation.
- Work Visits, Follow-ups, and Due Services now keep a real active natural `New visit` slot. The floating action is conditional on the natural slot being offscreen after meaningful layout and dismisses when any portion becomes visible; no synthetic spacer or layout feedback loop remains.
- Site action rows use content-aware widths, and navigation-button labels are centered across their full buttons with the disclosure caret trailing. Work filter instrumentation clears its presentation preferences between tests.
- Local verification for B030: 415 unit tests passed; debug/release APK assembly, lint, and Android-test APK compilation passed. Focused canonical AVD UI instrumentation passed 29/29, and the retained B013 owner render suite passed 3/3. Rendered template and Work surfaces were inspected in light/dark appearances. Detailed evidence is in `B030_TEMPLATE_WORK_AND_NAVIGATION_POLISH.md`.
- This is implemented review-branch work only. Protected `master` remains unchanged, and owner acceptance plus the B-008 technician pilot remain separate gates.

## B031 implementation state

- Register now contains the discoverable Customers / Sites / Equipment / Templates tab set. Templates uses the shared library body, loads on demand, hides the one-time filter, retains current lifecycle/detail/create behavior, and leaves `template/list` available for `.slinsp` import/deep links. Settings no longer presents the ordinary duplicate library entry.
- Master Search is grouped in fixed category order with counts, persisted expansion state, local ten-query history, IME/result/suggestion recording, clear-history affordance, template-master search support, cleaned context projection, and global-only operational urgency state. Search preference data remains presentation-only SharedPreferences.
- Template editor tools use generated layered Phosphor circle/foreground compositions without an external circular container. Detailed scope and verification evidence are in `B031_REGISTER_TEMPLATES_AND_GROUPED_SEARCH.md`.
- B031 verification: 420 unit tests passed; debug/release APK assembly, lint, and Android-test APK assembly passed; the two focused canonical-AVD B031 Compose tests passed, and controlled Register/Templates plus grouped Search/recent-history renders were visually inspected. The standalone Phosphor helper suite retains two pre-existing baseline assertion failures documented in the B031 evidence note.
- This is implemented review-branch work only. Protected `master` remains unchanged, and owner acceptance plus the B-008 technician pilot remain separate gates.

## B032 implementation state

- Inspection-template draft rows now use the shared larger icon-only action layout: Delete at left, adjacent Move up/Move down controls in the middle, and Edit or Save item changes at right. Accessible labels and first/last movement disabled semantics remain explicit. The template detail checklist stays quiet and non-navigating.
- Generated Phosphor mappings/assets cover `trash`, `arrow-fat-line-up`, `arrow-fat-line-down`, `check-fat`, and `pencil-simple`. The shared navigation-button primitive now reserves trailing caret space so Site and Equipment redirect labels remain centered and visually separated from their carets.
- The new-template item-type spacing and Equipment information-to-action spacing were tightened by small fixed design-system gaps; compact private guidance was preserved.
- B032 verification: required unit, debug/release assembly, lint, and Android-test assembly gates passed. Focused canonical-AVD tests for action rows, movement, semantics, caret direction, form spacing, and Site/Equipment layouts passed individually. Representative light/dark renders were inspected. The retained full B026 UI class had one unrelated pre-existing fixture/search failure (26/27); the six relevant tests passed.
- Detailed scope and evidence are in `B032_TEMPLATE_ACTION_AND_NAVIGATION_SPACING_POLISH.md`. A separate approximately 320dp run was not performed.
- This is implemented review-branch work only. Protected `master` remains unchanged and owner acceptance plus the B-008 technician pilot remain separate gates.

## B036 / ServiceLoop 1.0.1 implementation state

- Implemented on `codex/v1.0.1-agenda-polish` from start SHA `74c129dfa0c7ec4bf5f7b564498c69d6c0a572d0`. Android metadata is `versionCode 2` / `versionName "1.0.1"`; no release tag or protected-branch change was made.
- The effective ServiceLoop appearance now centrally drives light status-bar and navigation-bar icon contrast through the window passed into `ServiceLoopTheme`, including runtime Light/Dark changes while edge-to-edge remains enabled.
- Shared preset pills center their content; field-adjacent template, date, and time actions use the common 48dp square treatment. Inspection-template creation uses generated Phosphor `plus` Bold (`PlusBold`), not `plus-square`.
- Create Visit now separates customer selection, Set up visit, appointment date/time, task editing/listing, and visit actions. Follow-up Contact/Corrective choices remain equal-width and side-by-side at large font scale.
- Optional appointment time reuses `WorkingVisitEntity.scheduledAtEpochMillis` and `appointmentZoneId`; no Room migration was needed. Booking, rescheduling, restore, detail display, and same-date queue ordering preserve date-only semantics and never manufacture midnight.
- Home retains Dashboard as its default tab and adds an in-memory Agenda projection with exact `Unresolved (N)` and `Upcoming (N)` sections. Agenda rows are compact, max two lines, timed Visits first on a date, and navigate to Visit, Follow-up, or Service Plan truth.
- B036 verification is documented in `B036_V1_0_1_AGENDA_APPOINTMENT_TIME_AND_UI_POLISH.md`: 427 JVM tests passed, debug/release assembly, lint, and Android-test APK assembly passed, and focused canonical AVD UI/system-bar coverage passed 5/5. This is implemented development-branch work for owner review, not a released version.

## B037 / ServiceLoop 1.0.1 owner-review UI corrections

- Implemented on `codex/v1.0.1-agenda-polish` from start SHA `eea7ca54e7a66d79e29fd7d4def2ebd800d18489`. Version remains `versionCode 2` / `versionName "1.0.1"`, Room remains v15, no release tag was created, and protected `master` remains unchanged.
- Plan interval units now use one compact horizontal band with balanced editor spacing. Shared field-adjacent actions use the solid semantic teal selection treatment with 48dp targets and the common field/action gap across inspection template, visit date, visit time, reschedule, and restore.
- Create Visit now has plain `Choose a customer` and `Set up visit` headings, editable date/time controls with strict optional-time validation, picker actions, manual editing/clearing, and the same appointment family in reschedule/restore. Read-only visit detail remains read-only.
- New Visit back protection compares a meaningful draft snapshot to its initial route-aware baseline; search, mode, focus, picker dismissal, and restored values remain clean. The requested Follow-up explanatory sentence was removed without changing Contact/Corrective or private-content semantics.
- B037 evidence is documented in `B037_V1_0_1_OWNER_REVIEW_UI_CORRECTIONS.md`. Final JVM unit tests: 431 PASS; required debug/release assembly, lint, and Android-test APK gates: PASS; focused canonical AVD checks: B036 field/action 3/3, interval layout 2/2, B037 render/back guard 2/2, and relevant B026 Create Visit 3/3. Fresh light/dark render screenshots were visually inspected.
- This remains implemented owner-review work only. No release tag, protected-branch merge, backend, accounts, sync, invoicing, accounting, inventory, or customer portal scope was introduced.

## B043 — FIVE-ROLE WORKSPACE / FIELD DELEGATION

**Status:** IMPLEMENTED AND VERIFIED / READY FOR OWNER REVIEW

- Branch: `codex/b043-five-role-workspace`
- Production start SHA: `7d4382683253c7c9e680b3cd0add5b34bfe16377`
- Implementation commits: `967e889`, `e6cd912`, and `1cd2103`; authority commit: `309d9f9b5c0454c3fc6b8dc210ecbaf3a43203a4`.
- Room remains v15 and Android metadata remains version code 2 / version name 1.0.1. No Room, manifest, Gradle, resource, `.slwork`, `.slinsp`, or backup-format change was required.
- Adopted roles, in exact order, are Solo, Subcontractor, Employee, Team Leader, and Coordinator. Solo and Subcontractor manage their own Register/work; Employee receives and performs assigned work without the Register root; Team Leader combines Register, receiver, performer, coordinator, delegation, and conclusion capability; Coordinator has Register and coordination/conclusion capability but no technician field-work execution. Legacy `MEMBER` maps explicitly to Subcontractor, and conclusion remains distinct from technician completion.
- Centralized capabilities drive reactive root navigation, Home actions, master-data mutations, Search, templates, CSV import, equipment linking/creation, local Visit creation, field execution, incoming file handling, and coordinator conclusion. Employee keeps assigned operational context without ordinary Register bookkeeping; Team Leader retains both receiver/performer and coordinator paths; Coordinator is excluded from technician execution.
- Visit detail adds a Maps handoff from the captured Visit address. Blank addresses disable the action; no location permission, SDK, or state mutation was added.
- Final verification: 439 JVM tests PASS; debug/release builds, lint, Android-test APK, and `git diff --check` PASS; seven focused tests PASS on canonical `Pixel 10a ServiceLoop`; actual Google Maps system handoff PASS; eight light/dark/role-specific renders inspected.
- Next action: independent owner review and, if accepted, separately authorized protected-baseline integration.

### B043 owner-review correction pass

- The correction pass preserves the five-role capability matrix and clarifies Employee as execution-only: no local Visits, ad-hoc tasks, reusable templates, or Register/master records; assigned-work documentation and history remain available.
- Owner-review UI corrections cover role-option hierarchy, Employee equipment/search/work surfaces, Work-style Dispatch Outbox New Visit actions, Contact Note entry/detail/customer summary flows, Customer gateway spacing, and Create Visit alignment, stale-message suppression, dirty-state semantics, and customer/site reselection action.
- Focused verification: B037/B043 JVM/source contracts PASS; `:app:assembleDebug` PASS; `:app:assembleDebugAndroidTest` PASS; focused UI-INSTRUMENTED coverage PASS (7/7: five B043 role/search/equipment tests, the Outbox natural/floating/batch action test, and the Create Visit back-guard test) on canonical `Pixel_10a_ServiceLoop`, resolved serial `emulator-5554`. HUMAN/RENDERED inspection was not run in this correction pass. Room v15, version `1.0.1` / code 2, file formats/provenance, recurrence, immutable history, and B040/B042 editor composition remain unchanged.

## B044 — VISIT SETUP HARMONIZATION AND DISPATCH EXTENSION

**Status: IMPLEMENTED AND VERIFIED / READY FOR OWNER REVIEW**

- Branch: `codex/b044-visit-setup-harmonization`; production start SHA: `acc193bb53befe2803b9a050c35cf2eb4df0536a`.
- Documentation checkpoint: `3b1d346`; implementation commit: `7dd23d0`.
- Add Customer, ordinary New Visit, and Dispatch New Visit share the same customer-creation draft, validation, canonical inputs, and transactional Customer + default Site primitive. Shared `VisitSetupForm` now owns common local and Dispatch setup; Dispatch keeps assignment, coordination, Outbox persistence, and lifecycle extensions.
- Dispatch preserves unmatched planned items, stable task identity, reusable inspection-template selection, and existing Outbox lifecycle semantics. Employee remains excluded from Visit creation through the existing role capability boundary.
- Room is v16 with only nullable `dispatch_outbox_items.reusableTemplateId`; `.slwork`, `.slinsp`, and backup formats remain unchanged. Android metadata remains version `1.0.1` / code 2.
- Verification: 441 JVM unit tests PASS; `:app:assembleDebug`, `:app:lintDebug`, `:app:assembleDebugAndroidTest`, and `git diff --check` PASS. Domain-instrumented `Migration1To2Test` PASS (11/11) on the dynamically resolved canonical `Pixel 10a ServiceLoop` emulator. Full legacy UI suite and HUMAN/RENDERED owner review were not run for B044.

### B044 owner-review correction pass — 2026-09-22

- Implemented on `codex/b044-visit-setup-harmonization` from correction start SHA `8e1154854d23d2f5df4cd72df10a58f8cbbf8c59`; final SHA is recorded at the Git handoff gate.
- Corrected Dispatch plan-backed identity and snapshot authority, shared read-only semantics, explicit Due Services projection states, full-width Visit header/tabs, progressive target disclosure, selectable site/equipment rows, BOOKED-date validation, required new-task checklist behavior, template return/auto-selection, and requested spacing.
- Template persistence investigation was source/schema plus read-only canonical-device inspection: Room v16 preserves the reusable-template tables, the app ID is unchanged, the on-device `serviceloop.db` exists, and no clear/uninstall script or persistence defect was found. `sqlite3` was unavailable in the device shell, so no row-count claim was made.
- Verification after the last production edit: 446 JVM unit tests PASS; `:app:assembleDebug` PASS; `:app:lintDebug` PASS; `:app:assembleDebugAndroidTest` PASS. Canonical-only B037 owner-render/back-guard instrumentation PASS 2/2 and harmonized B026 Dispatch UI instrumentation PASS 3/3. B037 generated light/dark Visit captures were visually inspected. HUMAN/RENDERED evidence is limited to those focused captures; no broader owner acceptance is implied.
- A connected Gradle invocation was halted after it selected both the canonical AVD and an attached physical Pixel 6 Pro; no physical-device result is claimed. No Room/schema/version, manifest/application ID, `.slwork`, `.slinsp`, backup-format, release-tag, protected-branch, or merge change was made.

### B044 aftermath correction — 2026-09-22

- Implemented on `codex/b044-visit-setup-harmonization` from aftermath start SHA `d8c6ab11590036780de459aeea3111ac9d7cbf31`; status is internally verified and ready for owner review, not OWNER ACCEPTED.
- Visit setup top/bottom padding, conditional Dispatch prelude emission, and `rememberSaveable` task-editor scratch-state restoration were corrected without Room/schema or file-format changes. Coordinator UI tests were updated to current semantic/lazy-list behavior, and the rendered fixture now persists its active template rows before exercising save/export surfaces.
- Final JVM gate: 447 tests PASS with 0 failures/errors/skips. Canonical UI-INSTRUMENTED evidence: coordinator 16/16, B026 Dispatch 3/3, and B037 owner-review render/back-guard 2/2. The debug APK was built before `adb -s emulator-5554 install -r`; the pre-existing active `IT-001 · test (v1)` reusable template remained visible after replacement installation without data clearing.
- No Room/schema/version, manifest/application ID, `.slwork`, `.slinsp`, backup-format, release tag, protected-branch, merge, or owner-acceptance change was made. Final commit SHA is recorded at the Git handoff gate.

### B044 owner-review selection and checklist-preset pass — 2026-09-22

- Staged site selection is now explicit in the shared Visit setup: a non-clickable dashed site card, separate CheckFat RadioButton control, pinned Continue commit, and destructive confirmation only when changing a site that already has work. Existing Dispatch edits open directly in configuration.
- Active Service Plan projections provide contextual reusable-checklist suggestions by equipment/site, while explicit checklist choices, explicit None, and the Create Template return remain authoritative. No schema, version, or file-format change was made.
- Focused evidence: `B044VisitSetupTest` 12/12 PASS; debug and Android-test APK assembly PASS; lint PASS; one canonical `Pixel 10a ServiceLoop` UI smoke 1/1 PASS with chooser/configured screenshots inspected. Broad regression suites and the full Create Template round-trip were intentionally not run under the time-boxed verification policy.

## B045 — `.slsync` UNIVERSAL CONTAINER AND INITIAL WORKSPACE IMPORT

**Status: IMPLEMENTED / READY FOR OWNER REVIEW**

- Branch: `codex/b045-slsync-initial-import`; start SHA: `5a947da7ea6e61e32040f1afddaeba865473b2d5`.
- B045 is a new explicit owner-adopted decision. The authoritative milestone document is `B045_SLSYNC_UNIVERSAL_CONTAINER_AND_INITIAL_WORKSPACE_IMPORT.md`; absence of a pre-existing B045 document was expected and did not limit implementation.
- `.slsync` is a semantic ZIP container with MIME `application/vnd.serviceloop.sync+zip`, manifest v1, exactly seven semantic JSON sections, FULL_WORKSPACE purpose, bounded decode, exact-entry validation, and no binary payload, hashes, signatures, conflict engine, or selective-sync scope.
- The trusted initial importer decodes fully before mutation, serializes through `BusinessFileCoordinator`, and performs a one-transaction Room replacement. Register, current templates, active plans/open obligations, booked local visits/claims, dispatch directory/drafts, follow-ups, and contact notes are mapped to the existing v16 entities. Technician identity, reminder preferences, role/appearance/device settings, Android permissions, and Calendar preference remain device-local.
- Recovery metadata is renewed with a new dataset UUID and import timestamps; backup verification/attempt fields are reset, the reminder interval is preserved, and incomplete/restricted/adoption state is cleared on success. Old attachment/report files are not cleaned up yet and may remain orphaned; cleanup is deferred.
- Settings import, Android document picker, ACTION_VIEW/ACTION_SEND `.slsync` routing, preview/replacement warning, success navigation, and safe failure state are implemented. Existing S37 CSV import, `.slwork`, `.slinsp`, and Recovery Backup behavior remain separate and unchanged.
- Focused verification: `B045SlsyncTest` 3/3 PASS; `:app:compileDebugKotlin` PASS. Final assembleDebug and `git diff --check` are the B045 handoff gates. Broad regression/device/rendered suites are intentionally not run under the time-boxed policy.
- Room remains v16 and Android metadata remains `1.0.1` / code `2`. No demo dataset, merge, tag, protected-master movement, or future selective export architecture was added.

## B046 — `.slsync` transport consolidation and import UX — 2026-09-22

- Implemented on `codex/b046-slsync-transport-consolidation` from `9e189905d0fedb795639938af4e521ae2be8ea63`.
- `.slsync` is now the sole ordinary exchange container with `FULL_WORKSPACE`, `WORK_ASSIGNMENT`, and `TEMPLATE_SHARE` purposes. B045 FULL_WORKSPACE v1 entries and semantics remain wire-compatible.
- A shared envelope codec owns ZIP/manifest safety. Existing v5 Dispatch and template codecs remain internal payload codecs; Dispatch incremental generation/import and template exact/conflict behavior are preserved.
- Home and Settings use one unified purpose-aware Import flow. Settings Data is `Import / export data`, `History`, `Backup and recovery`; template management no longer exposes exchange controls. Backup/recovery no longer contains ordinary import/export actions.
- Old `.slwork`/`.slinsp` production filters and external generation/import entry points were removed. Dispatch export finalizes a verified WORK_ASSIGNMENT `.slsync` before committing Dispatched state.
- Focused B046 tests passed; Kotlin compile passed. Full regression, connected-device, AVD smoke, rendered owner review, and owner acceptance remain separate gates.

## Toolchain / environment baseline

- package/application ID: `com.v16studio.serviceloop`
- minSdk 29
- compileSdk / targetSdk 37
- AGP 9.3.2
- Gradle 9.5.0; wrapper must remain exact `gradle-9.5.0-bin.zip`
- Gradle JVM/project JDK 20
- Java source/target 11
- Kotlin 2.2.10
- Compose BOM 2026.02.01
- Room 2.8.4
- Room schema v18 on the B048 branch (v17 on B047; earlier historical milestones retain their recorded schema versions)
- canonical AVD display name: `Pixel 10a ServiceLoop`; resolve adb serial dynamically every run

## Verification / acceptance boundaries

- Automated/AI review is not equivalent to B-008 external pilot evidence.
- SL-4 is technically verified and banked; do not claim an earlier standalone owner-acceptance event that did not occur.
- Dispatch is owner-approved product scope and integrated.
- SL-5A is implemented/reviewed; actual canonical notification delivery remains unclaimed.
- SL-5B is implemented/reviewed; real Calendar provider mutation remains unclaimed.
- SL-5C establishes the functional freeze for B-013; it does not claim current copy/UI is final.
- Localization is intentionally deferred under B-017.
- B-008 cannot be satisfied by emulator/AI/owner-only review.
# B047 post-pilot hardening

The B047 UI/UX and narrow persistence hardening is implemented on `codex/b047-post-pilot-ui-hardening`. See [B047_POST_PILOT_UI_HARDENING.md](B047_POST_PILOT_UI_HARDENING.md) for the scoped changes and transport boundary.

## B048 — Team, Trusted Exchange, and post-B047 UX hardening

Implemented on `codex/b048-team-trust-ux-hardening` from B047 SHA `527fa83f7c5f56cd33b08a10200c5cb20d0418b9`. App version target is 1.2.0 (code 4), Room v18, and Recovery schema v17. Home adds Dashboard / Agenda / Team. The existing durable `technician_identity` row remains the one stable local ServiceLoop ID; its Team label is Coordinator ID or Technician ID by role. Trusted IDs are device-local, included in Recovery, preserved by FULL_WORKSPACE replacement, and required at each `.slsync` mutation boundary. New envelope v2 files require `exporterId`; v1 files remain readable for verification and are verify-only. `register` section v2 carries repeatable customer contacts and `team` section v2 carries technician notes; Dispatch v5 and template payload semantics remain unchanged. The 477-test unit suite, debug APK, instrumentation APK, lint, and focused canonical AVD cases passed; detailed scope, evidence limits, and runtime evidence are in [B048_TEAM_TRUST_AND_UX_HARDENING.md](B048_TEAM_TRUST_AND_UX_HARDENING.md).

## B049 active implementation line — partial UI checkpoint

The active implementation branch is `codex/b049-work-exchange-reporting-export-center`, created from B048 SHA `cadc0668fe2ba5172d17a86a157323303a71d83f`. [The B049 milestone authority and checkpoint record](B049_UNIFIED_WORK_EXCHANGE_REPORTING_EXPORT_CENTER_AND_TEAM_IA.md) describe the approved target and the first implemented UI slice. B049 is **in progress**: canonical Dispatch/Visit convergence, Room v19, Recovery v18, WORK_RESULT, DATA_TRANSFER, Export Center, aggregate reports, contact order/notes, and image retention are not yet implemented. The executable version remains 1.2.0/code 4 with Room v18 and Recovery v17. No protected-baseline integration is implied.

The 2026-09-23 owner-review continuation adds a partial correction slice on this branch: one Dashboard Visit date with a prominent Booked visit label, 64dp shared root tabs, Team ID and assignment-picker corrections, always-visible Visit progress, silent clean Service save state, peer photo actions, selected Outcome check, and route-scoped Service scroll state. This does not complete B049; the milestone document banks the detailed adopted contract and remaining acceptance checklist. Final branch SHA and evidence are recorded in the Git history and handoff.

A following capability checkpoint replaces the overloaded `canCreateLocalWork` gate with explicit `canCreateVisits` and `canAssignWork` fields. Coordinator can enter canonical Visit creation and Book due Services but cannot Start from its action bar or New Visit form; field Service routes remain guarded by `canPerformFieldWork`. This is a capability step toward, not completion of, canonical Visit/Dispatch convergence. The first focused Coordinator booking/no-Start UI test passed on the canonical AVD.
