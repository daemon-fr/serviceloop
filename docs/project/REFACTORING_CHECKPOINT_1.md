# Refactoring checkpoint 1 — completion pass

**Branch:** `codex/b013-ui-overhaul`
**Starting revision:** `77d78e4210e1970b55de8a819f5baad44dbab98e`
**Protected `master` baseline:** `1fd51131b040ab62af3874c1106615b75f3008fe`

This completion pass finishes Checkpoint 1 structural maintenance. It preserves the adopted product boundary, routes, persistence semantics, visual behavior, and the existing debug fixture. No Checkpoint 2 redesign was started.

## Structural changes

### Daily operations responsibility map

The former `DailyOperationsUi.kt` implementation was mechanically split by responsibility. Package-level APIs, routes, saveable-state keys, BackHandler behavior, test semantics, and user-visible behavior remain unchanged.

- `CustomerSiteUi.kt`: customer and site detail/editor surfaces.
- `EquipmentPlanUi.kt`: equipment editors, plan detail/editor, and site selection.
- `DueVisitUi.kt`: due services, new-visit draft/editor, ad-hoc work, and visit detail.
- `TemplateUi.kt`: inspection template list/detail/editor surfaces.
- `EvidenceUi.kt`: equipment links, field evidence, service evidence, and photo cards.
- `FollowUpContactUi.kt`: follow-up list/detail/editor and contact-note editing.
- `SearchUi.kt`: search surface.
- `DailyOperationsUi.kt`: shared editors, guards, layout/typography helpers, private blocks, handoff helpers, and bounded photo input.

### Service responsibility map

The former `ServiceScreen.kt` implementation was mechanically split while retaining the existing root routing and focus behavior.

- `ServiceScreen.kt`: service route entry, inspection compatibility route, list-index helpers, and focus behavior.
- `ServiceCompletionUi.kt`: completion controls and completion-state presentation.
- `ServiceChecklistUi.kt`: checklist/question presentation and answer controls.
- `ServiceProgressUi.kt`: service progress navigator, overview, group toggles, summaries, badges, and fallback progress.
- `ServiceWorkspaceUi.kt`: service identity, save state, documentation/private work context, and completion landmarks.

### Dispatch compatibility vectors

`DispatchGoldenVectorTest.kt` adds literal vectors derived from the pre-refactor data-class `toString()` codec at `041a6c2`. The values are asserted as constants rather than calculated from the current implementation at test time. The current `DispatchTransport.kt` hash source remains source-equivalent to that checkpoint; no production hash algorithm change was introduced.

- Ordinary visit: `f7bab8b823145a6445c4693d94431b926aa8fd4c2e37d9e7ee5b7dabe5b028c9`
- Multi-technician/team/work visit: `16928ad751fc7e4c1edfe3c9d354db5f7c08863ebb23fea1ffceb9ed98f8b040`
- Inspection snapshot: `e1f714104acf98d8a67756db7b41257bec5eedc69611660e1a5b57f0a21ce3cc`
- Canceled visit: `8207f83d256c6b4da8bbd694c42debfbd5954fcc1b73162019cacf1e15d84fb6`
- Content-addressed snapshot ID: `snapshot-d895bfc1abcf8713356fbc38`

The multi-collection vector also verifies normalized collection order, so technician/team/work ordering remains compatible.

### Stage B fixture repair

`StageBRenderMatrixTest` now creates a test-owned finalized visit (`V-STAGE-B`) through the real repository finalization path when needed, then renders that durable record. It no longer depends on the debug fixture's intentionally working `V-001`. The debug fixture itself was not changed, and the production app was not altered to bypass finalization preconditions.

The source-contract tests were updated to locate semantic markers across the production source tree instead of coupling assertions to the old oversized filenames. Two Stage 3 selectors were also corrected for current wording and lazy-list composition; these are harness corrections, not product behavior changes.

## Verification evidence

Local gates after the production/test edits:

- `:app:testDebugUnitTest`: **PASS**, 385 tests, 0 failures, 0 errors, 0 skipped, across 31 XML suites.
- `:app:assembleDebug`: **PASS**.
- `:app:assembleRelease`: **PASS**.
- `:app:lintDebug`: **PASS**, 0 errors.
- `:app:assembleDebugAndroidTest`: **PASS**.
- `git diff --check`: **PASS**; only expected Git line-ending normalization warnings were reported.

Connected evidence used the canonical `Pixel_10a_ServiceLoop` AVD, whose serial was resolved dynamically and passed explicitly to every device command with `adb -s`. APK and test APK installation used separate explicit `adb -s <serial> install -r` commands.

- Stage B render matrix: **PASS**, 2 tests.
- Corrected Stage 3 plus Service Attention run: **PASS**, 21 tests; six system-handoff cases were expected assumption skips because `systemHandoff=true` was not enabled.
- Completion semantics isolated rerun: **PASS**, 33 tests.
- Full corrected matrix: 102 scheduled; all behavior assertions passed, with expected assumption skips for disabled system handoff and canonical persistent-state preconditions. One combined-run ActivityScenario teardown failure was reproduced as a runner lifecycle failure (`PAUSED` during teardown) and cleared by the isolated 33-test rerun; it was not a product assertion failure.

This checkpoint does not claim system-handoff, notification delivery, Calendar-provider mutation, localization, human visual review, or any Checkpoint 2 work.
