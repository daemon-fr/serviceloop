# AGENTS.md — ServiceLoop

This file defines persistent implementation-agent rules for the ServiceLoop repository.

## Authority

Read `docs/project/SOURCE_OF_TRUTH.md` first. Explicit owner amendments in `docs/project/BASELINE_DECISIONS.md` outrank the adopted product/UI documents. Current code and tests establish implementation state, not new requirements.

Do not silently combine the adopted Conceptual App Map with conflicting policies from the reference Complete Functional App Map.

## Product boundary

ServiceLoop is a local-first service book for an independent technician servicing customers' equipment. Preserve the loop:

Customer → site → equipment → service plan → due work/arrangements → working visit → service record/report → next obligation/follow-up.

Do not turn it into a generic task manager or full field-service-management suite. No backend/accounts/team sync/dispatch/invoicing/accounting/inventory/customer portal unless explicitly authorized.

## Implementation discipline

Own ordinary engineering decisions inside the authorized scope. Prefer simple dependable implementations and coherent vertical slices. Do not build speculative frameworks or generic engines.

Task sizing: Codex is trusted with substantial coherent assignments. Do not fragment work merely because it is large. Split only at meaningful workflow, integrity, platform-risk, or review boundaries. Do not cross the explicitly authorized task boundary.

Substantial authorized Codex tasks are intended to run unattended. Use reasonable engineering judgment and repair ordinary, low-risk scope-adjacent defects whose correct behavior is already established instead of pausing for routine approval.

Ordinary build, test, adb, helper, selector, synchronization, and other execution failures are not reasons to stop when the task remains well-defined. Reproduce, diagnose, fix the correct layer, rerun the affected check, and continue. Escalate only for genuinely consequential ambiguity, unexpected repository state, destructive action, or an architectural/data-integrity problem outside the task's authority.

History, durable drafts, attachment ownership, migration safety, and truthful save/finalize semantics are architectural concerns from the foundation onward even when their full user workflows arrive later.

For intensive milestones, separate responsibilities where practical: a development AI model owns architecture-heavy implementation to a clean checkpoint; a testing AI model owns exhaustive regression/runtime/debugging and may repair localized defects. Architectural redesign discovered during verification returns to the development role. Prompts should describe these roles rather than attempting to switch model/configuration from inside the task.

## Android/toolchain

Current generated baseline:

- minSdk 29
- compileSdk/targetSdk 37
- AGP 9.3.2
- Gradle 9.5.0
- Gradle JVM/project JDK 20
- Java source/target 11
- Kotlin 2.2.10
- Compose BOM 2026.02.01

Do not casually modernize or downgrade working tooling. Keep the exact full Gradle wrapper distribution filename. ServiceLoop may reuse proven development practices from Routine Repeater, but never its application logic, visual identity, scheduler assumptions, or permissions without an explicit ServiceLoop requirement.

For device testing, the canonical ServiceLoop AVD is the one whose display name is `Pixel 10a ServiceLoop`. Resolve its current adb serial dynamically from that AVD identity on every run; never assume `emulator-5554` or another fixed serial. Every device-directed adb command must use explicit `-s <resolved-serial>`. Do not substitute another emulator or a physical phone, and do not reset or recreate the AVD without explicit owner authorization.

Build the APK first, then install it explicitly with `adb -s <resolved-serial> install -r <apk>`. Do not use `installDebug` as the normal validation path when separate build/install evidence is available.

## Architecture defaults

Unless current accepted code establishes a better equivalent within scope:

- single Android app module initially;
- Kotlin + Jetpack Compose + Material 3;
- Room as authoritative business persistence;
- DataStore only for preferences/platform-request state;
- ViewModels/state holders over repositories/domain services;
- simple constructor/manual application-container injection before adding a DI framework;
- `java.time` behind an injectable business clock/time-zone provider for deterministic rules;
- app-private persistent ownership for durable attachments;
- versioned/append-style historical records rather than silently mutating issued history.

No network/backend abstraction merely for hypothetical future sync.

## Integrity rules

Never show a successful save/finalize state before durable persistence succeeds. A failed save must stay visibly failed and preserve the last durable checkpoint.

Do not allow master-data/template edits to rewrite historical inspection/report meaning. Use snapshots/version identity where the adopted model requires historical stability.

Keep public/customer report data structurally separated from private/access/internal content; privacy must not depend only on hiding fields in UI.

Completion/finalization, recurrence advancement, PDF generation, correction, import, and restore must be designed so retries cannot repeat business effects or silently replace good data.

Do not use destructive Room migration fallback over real data.

## Verification

Use truthful evidence. For relevant changes run the repository's established commands, normally including:

- `gradlew.bat :app:testDebugUnitTest`
- `gradlew.bat :app:assembleDebug`
- `gradlew.bat :app:lintDebug`

Use instrumented/device tests and actual rendered-screen inspection when the behavior requires them. Report each important check PASS, FAIL, or NOT RUN with a reason. Never claim device/emulator/rendered/PDF evidence that did not occur.

Tests must validate adopted business meaning rather than merely mirror implementation. Do not weaken a correct requirement to make a test pass. When a legacy test assumption is obsolete, replace it with a stable semantic assertion that preserves the intended coverage.

Before changing production code for a UI-test failure, determine whether the problem is actually a stale selector, lazy-list composition, timing/synchronization, IME interference, or a real product defect. A test-harness failure is not automatically a production failure.

At final acceptance gates, rerun affected checks after the last production/test edit. Do not rely on an earlier green run that predates later changes.

## Execution/tooling fallbacks

- If the managed patch/filesystem helper first fails with a sandbox refresh, access, or path-helper error, switch immediately to the functioning approved local shell/editing path. Do not investigate sandbox internals or retry path spellings; inspect `git diff` after shell edits and run `git diff --check` before commit.
- If an interrupted run may have left processes alive, inspect the worktree/diff and running processes before resuming. Terminate only a clearly orphaned task-specific build/test process when necessary; do not kill the adb server, Android Studio, Gradle, Git, or unrelated processes indiscriminately.
- Treat `.idea/deploymentTargetSelector.xml` and `.idea/gradle.xml` as known Android Studio noise only when their diffs contain the previously observed IDE-generated device-selection/Gradle-migration metadata. Restore or ignore those exact known diffs for the task, never stage/commit them, and do not enter a repeated restore loop. Any materially different content remains significant.
- Never delete `.git/index.lock` blindly. First verify no Git operation is actively using this ServiceLoop repository. If the lock is clearly stale, remove only that lock file; otherwise wait or report the genuine permission/process problem.
- For ServiceLoop-owned Compose UI, prefer stable `testTag`, `contentDescription`, or visible-label semantics and Compose actions such as `performClick`, `performTextInput`, `performTextReplacement`, `performTextClearance`, `performScrollTo`, `performScrollToNode`, and `performImeAction` over coordinate tapping or `adb input text`.
- Lazy containers are special: an off-screen `LazyColumn` child may not yet exist in the semantics tree. Scroll the owning lazy list with `performScrollToNode(...)` using a stable semantic matcher, then interact with the child. Do not first wait for an off-screen child to be composed.
- If handwriting, stylus, or IME UI intercepts coordinate text entry, switch promptly to semantic Compose instrumentation. Do not reset or globally reconfigure the canonical AVD for text entry. A production repository/domain instrumentation path is valid DOMAIN-INSTRUMENTED evidence, but remains distinct from UI-INSTRUMENTED and HUMAN/RENDERED evidence.
- Use stable domain-backed semantic anchors for persistent journeys (for example IDs/testTags tied to a visit/work item) rather than ambiguous text that may appear multiple times.
- System surfaces such as the Sharesheet, picker, settings, or another app require actual system-handoff evidence when the handoff itself matters; a domain substitution is not equivalent.

## Git and handoff

Inspect `git status`, branch and starting revision before substantial work. Verify the exact authorized branch/SHA, remote parity/divergence, protected baseline revision, and worktree state before edits. Keep diffs reviewable, preserve unrelated behavior, do not rewrite history, force-push, destructively reset known-good work, publish, or merge into the protected owner baseline unless explicitly authorized.

On an explicitly authorized milestone branch, ordinary Git staging, coherent commits, fetching, status/diff inspection, and pushing that designated branch do not require separate owner approval. This authority does not permit force-push, destructive reset/history rewrite, deletion or movement of protected accepted history, merging into master, or moving master merely because a milestone branch is complete.

At the final Git gate, verify the required tests/build/lint for the task, `git diff --check`, local HEAD equals the designated remote branch, divergence is `0 0`, the real source/docs worktree is clean, and protected baseline branches remain unmoved.

At handoff report starting/ending revision, files materially changed, behavior implemented, verification actually run, known gaps, deviations, and the exact branch/commit/diff for independent review. Distinguish IMPLEMENTED, TESTED, DOMAIN-INSTRUMENTED, UI-INSTRUMENTED, SYSTEM-HANDOFF, HUMAN/RENDERED, and NOT RUN rather than overstating evidence.
