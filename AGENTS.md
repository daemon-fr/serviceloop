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

History, durable drafts, attachment ownership, migration safety, and truthful save/finalize semantics are architectural concerns from the foundation onward even when their full user workflows arrive later.

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

Tests must validate adopted business meaning rather than merely mirror implementation.

## Execution/tooling fallbacks

- If the managed patch/filesystem helper first fails with a sandbox refresh, access, or path-helper error, switch immediately to the functioning approved local shell/editing path. Do not investigate sandbox internals or retry path spellings; inspect `git diff` after shell edits and run `git diff --check` before commit.
- For ServiceLoop-owned Compose UI, prefer stable `testTag`, `contentDescription`, or visible-label semantics and Compose actions such as `performClick`, `performTextInput`, `performTextReplacement`, `performTextClearance`, `performScrollTo`, and `performImeAction` over coordinate tapping or `adb input text`.
- If handwriting, stylus, or IME UI intercepts coordinate text entry, switch promptly to semantic Compose instrumentation. Do not reset or globally reconfigure the canonical AVD for text entry. A production repository/domain instrumentation path is valid DOMAIN-INSTRUMENTED evidence, but remains distinct from UI-INSTRUMENTED and HUMAN/RENDERED evidence.
- System surfaces such as the Sharesheet, picker, settings, or another app require actual system-handoff evidence when the handoff itself matters; a domain substitution is not equivalent.

## Git and handoff

Inspect `git status`, branch and starting revision before substantial work. Keep diffs reviewable, preserve unrelated behavior, do not rewrite history, force-push, destructively reset known-good work, publish, or merge into the protected owner baseline unless explicitly authorized.

On an explicitly authorized milestone branch, ordinary Git staging, coherent commits, and pushing that designated branch do not require separate owner approval. This authority does not permit force-push, destructive reset/history rewrite, deletion or movement of protected accepted history, merging into master, or moving master merely because a milestone branch is complete.

At handoff report starting/ending revision, files materially changed, behavior implemented, verification actually run, known gaps, deviations, and the exact branch/commit/diff for independent review.
