# AGENTS.md — V16 Service

Persistent implementation rules for this repository.

## Authority

Read `docs/project/SOURCE_OF_TRUTH.md` first. Explicit owner decisions in `docs/project/BASELINE_DECISIONS.md` outrank adopted product/UI documents. Current code and tests establish implementation state, not new requirements. Do not combine adopted requirements with conflicting unadopted proposals.

When acting as orchestrator/reviewer or authoring a substantial implementation prompt or handoff, read `docs/project/AI_CODEX_PROMPT_AND_HANDOFF_STANDARD.md`. Name source/data hotspots, exact semantics and failure cases, concrete tests/runtime evidence, and an exhaustive acceptance checklist.

## Product boundary

V16 Service is a local-first service book for solo technicians and small service companies. Preserve the loop: Customer → site → equipment → service plan → due work/arrangements → working Visit → service record/report → next obligation/follow-up. Dispatch and the five-role workspace are adopted local/file-based workflows. Backend/accounts, live/cloud/team sync, billing/accounting, inventory, customer portal, and unrelated enterprise scope require explicit adoption.

## Implementation discipline

Own routine engineering decisions in the authorized scope. Prefer dependable vertical slices and avoid speculative frameworks. Authorized substantial work runs to completion; commits, pushed checkpoints, passing stages, or remaining work size are not stopping points. Repair ordinary low-risk defects whose behavior is established. Stop only for an external execution blocker, unsafe repository discrepancy, destructive action, or genuine owner/data-integrity/security decision outside existing authority.

History, durable drafts, attachment ownership, migration safety, and truthful save/finalize semantics are foundation concerns.

## Android/toolchain

Current baseline: minSdk 29; compile/target SDK 37; AGP 9.3.2; Gradle 9.5.0; Gradle JVM/project JDK 20; Java 11; Kotlin 2.2.10; Compose BOM 2026.02.01. Do not casually change working tooling. Keep the full Gradle wrapper distribution filename. Do not reuse other apps' business logic, visual identity, scheduler assumptions, or permissions without an explicit requirement.

The canonical emulator is identified by display name `Pixel 10a V16 Service`. Use `tools/resolve-canonical-avd.ps1` to match its display-name metadata to its AVD ID and running serial on every run. Every device-directed command uses `adb -s <resolved-serial>`. Never substitute another emulator or a physical device; do not wipe/recreate the AVD without explicit authorization. Build APKs first and install with `adb -s <resolved-serial> install -r <apk>`; do not use `installDebug` as the normal evidence path.

## Architecture and integrity

Default to one Android module, Kotlin/Compose/Material 3, Room as business persistence, DataStore for preferences/platform-request state, ViewModels over repositories/domain services, simple manual injection, injectable `java.time` business clock/time zone, and app-private owned attachments. No backend abstraction for hypothetical sync.

Never report save/finalize success before durable persistence. Failed saves remain visibly failed and preserve the last durable checkpoint. Master-data/template edits cannot rewrite historical report meaning. Keep customer-report data structurally separate from private/access/internal content. Completion, recurrence, PDF, correction, import, and restore retries must not repeat business effects or replace good data. Never use destructive Room migration fallback over real data.

## Verification

For relevant changes use established checks, normally `gradlew.bat :app:testDebugUnitTest`, `:app:assembleDebug`, and `:app:lintDebug`; add instrumentation/rendered evidence where behavior requires it. Report each check PASS, FAIL, or NOT RUN accurately. Tests validate adopted meaning. Diagnose stale selectors, lazy composition, synchronization, or IME interference before changing production code for UI-test failure.

Prefer stable Compose tags/content descriptions and semantic actions to coordinate taps or `adb input text`. Scroll the owning lazy list before interacting with off-screen items. System surfaces such as picker/Sharesheet require actual handoff evidence when that handoff matters. Never claim evidence that did not occur.

## Git and handoff

Before substantial work inspect status, branch, starting revision, remote parity, protected refs, and worktree state. Preserve unrelated edits. Keep diffs reviewable; do not rewrite history, force-push, destructively reset, publish, or merge the protected baseline unless explicitly authorized. Ordinary staging, commits, fetching, and push of a specifically authorized branch are permitted.

At final acceptance check required evidence, `git diff --check`, exact local/upstream SHA, `0 0` divergence, clean task worktree, and unmoved protected refs. Report starting/ending revision, material changes, behavior, actual evidence, gaps/deviations, and exact review branch/commit. Distinguish TESTED, DOMAIN-INSTRUMENTED, UI-INSTRUMENTED, SYSTEM-HANDOFF, HUMAN/RENDERED, and NOT RUN.

## Risk-based evidence validity

Use focused checks during editing: prior evidence is invalidated only when its inputs or behavior are affected. Broad suites are not required after every edit or commit; repeat them when shared or high-risk inputs genuinely invalidate broad evidence. Follow section 23 of the prompt and handoff standard at docs/project/AI_CODEX_PROMPT_AND_HANDOFF_STANDARD.md.
