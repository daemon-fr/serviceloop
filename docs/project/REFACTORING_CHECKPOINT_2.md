# ServiceLoop Refactoring Checkpoint 2

## Scope

Checkpoint 2 consolidates Service completeness/evaluation and active Service workspace reads. It does not redesign the UI, persistence schema, autosave coordinator, finalization workflow, or next-due recovery choreography.

- Start SHA: `f58d9feda33a6abd3749baea9e4621ed896a0a6d`
- End SHA (implementation): `639f27cd76e5a36646f4a93793ced7ebe44b59ae`
- Branch: `codex/b013-ui-overhaul`
- Protected master baseline: `1fd51131b040ab62af3874c1106615b75f3008fe`

## Duplication found and removed

Before CP2, `completionLines()` independently derived fulfillment eligibility, projected fulfillment, calculated next due, current-obligation outstanding state, and completion blockers. `serviceVisitProgress()` called `completionLines()` and then reread Visit WorkItems, checklist responses/items, raw buffers, drafts, and dispatch item facts to rebuild progress status. The ViewModel also loaded `inspection()`, `serviceVisitProgress()`, and `completionLines()` separately on initial Service load, while canonical-write refresh used a different three-read path.

CP2 now loads a working Visit once for the Room Service projection and evaluates each loaded work item once for completion facts. `serviceVisitProgress()` and `completionLines()` share that loaded-facts path; `serviceWorkspace()` publishes inspection, progress, and completion lines from the same Room read transaction. The stable individual repository APIs remain available for callers that need them.

## Pure evaluator

`data/ServiceWorkEvaluation.kt` contains the value-only `ServiceWorkEvaluationInput`, compact persisted Plan/obligation facts, `ServiceWorkEvaluation`, and `ServiceWorkEvaluator`. It owns the shared pure derivations for:

- fulfillment eligibility and current-obligation identity;
- projected fulfillment, including PERFORMED automatic fulfillment and PARTLY_PERFORMED nullable choice;
- calculated next due from the actual service date and captured interval;
- current-obligation outstanding state;
- Service completion blockers/readiness.

`ChecklistSemantics.kt` remains the authoritative checklist projection. The evaluator consumes its `ChecklistCompleteness`; it does not reimplement STATUS/TEXT/NUMBER/N/A/Issue-found rules. `serviceEntryStatus()` remains a UI progress function because it also considers presentation/activity state.

## Workspace and ViewModel ownership

`domain/ServiceWorkspace.kt` defines the coherent workspace projection. The Room repository loads the target inspection row, the shared Visit WorkItems, checklist snapshots/responses/completeness, raw buffers, public/private drafts, Plan/obligation facts, dispatch ownership, and progress support counts in one read transaction. The default interface implementation composes existing APIs for simple fakes; the Room implementation overrides it with the optimized read.

`UiState` now owns a `ServiceContext` containing the workspace plus visit-only progress/resume identity, and a separate `CompletionContext` for Review when no active Service workspace is being shown. Existing `inspection`, `serviceProgress`, `activeServiceVisitId`, `activeServiceWorkItemId`, `completionLines`, and `completionVisitId` properties remain compatibility getters for the CP1 UI. Initial Service load, Service refresh, and Service inspection canonical-write refresh all publish through `serviceWorkspace()`.

Review completion loading remains separate. Photos, follow-ups, field evidence, report identity, and other deliberately independent lifecycles remain separate. The direct `ServiceScreen` test harness was updated to preload the workspace now that the screen no longer initiates a second completion read.

## Mutation boundaries preserved

`saveCompletionDraft()` shares eligibility and calculated-next-due evaluation where the input is equivalent, without changing its write ordering, normalization, raw-draft handling, or timestamps. `finalizeVisit()` still enters the existing Room transaction, performs the final-record idempotency check, rereads Visit/WorkItem/Plan/obligation/checklist/report/Dispatch facts inside that transaction, applies the pure evaluator to those fresh facts, and only then writes immutable history, recurrence, and finalization state. It never consumes a ViewModel or workspace evaluation.

`recoverMissingCalculatedNextDue()` retains its request-token, cancellation/join, supersession, conditional transactional reread, override-preservation, and retry behavior. `ServiceDraftAutosaveCoordinator` was not redesigned; per-field state, exact raw buffers, failed-write Retry, supersession, flush, ON_STOP/navigation flush, and choice transitions remain in place.

## Tests and evidence

Added or extended characterization/evidence coverage includes:

- pure evaluator matrix for no recurring work, history-only, inactive/stale Plan facts, PERFORMED, PARTLY unanswered/Fulfill/Keep due, NOT_PERFORMED reason, checklist blockers, missing Issue description, no checklist, calculated next due, missing confirmation, and manual override;
- Room workspace parity with retained individual projections;
- ViewModel one-workspace-load call-count evidence;
- stale Service load A/B protection and stale refresh after moving between Services;
- existing checklist completeness, recurrence, autosave/raw-draft, finalization/idempotence, and Dispatch regressions.

Verification completed so far: `:app:testDebugUnitTest` passed 396 tests with 0 failures/errors/skips; `:app:assembleDebug`, `:app:assembleRelease`, `:app:lintDebug`, and `:app:assembleDebugAndroidTest` passed. On the dynamically resolved `Pixel_10a_ServiceLoop` emulator (`emulator-5554`), Service workspace core passed 13/13, Attention/focus passed 9/9 after a harness-only preload correction, raw-draft and Completion semantic coverage passed, Dispatch instrumentation passed 2/2, and Stage A rendered proof passed 3/3. The persistent canonical exact-once test was not counted as a product pass because its existing finalized fixture expected `2026-09-01` while the non-reset device already held `2027-03-05`; app data was not cleared.

## Deferred cleanup

No schema migration, broad state-architecture rewrite, UI recombination, autosave rewrite, recovery rewrite, performance framework, or adversarial campaign is part of CP2.
