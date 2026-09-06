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

## Git and handoff

Inspect `git status`, branch and starting revision before substantial work. Keep diffs reviewable, preserve unrelated behavior, do not rewrite history, force-push, destructively reset known-good work, publish, or merge into the protected owner baseline unless explicitly authorized.

At handoff report starting/ending revision, files materially changed, behavior implemented, verification actually run, known gaps, deviations, and the exact branch/commit/diff for independent review.
