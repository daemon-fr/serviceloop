# V16 Service — Architecture and Quality Contract

## Architecture defaults

- One Android app module initially; Kotlin, Jetpack Compose, and Material 3.
- Room is authoritative business persistence. DataStore is for preferences/platform-request state only.
- ViewModels/state holders sit above repositories and domain services. Prefer simple manual construction before DI.
- Business time uses `java.time` behind an injectable clock/time-zone provider.
- Durable attachments are app-private and app-owned. Historical records use versioned or append-style identities.
- No network abstraction for hypothetical future sync.

## Integrity and privacy

Durable persistence is the success boundary for save/finalize. A failure preserves the last durable checkpoint. Master-data/template edits cannot rewrite issued historical meaning. Public/customer report facts are structurally separate from private/access/internal facts.

Completion, recurrence advancement, report generation, correction, import, image cleanup, and restore are retry-safe. Preserve transaction/file-lock ordering and do not silently replace good data. No destructive Room migration fallback is allowed.

## Verification and evidence

Tests assert adopted business meaning, not just implementation structure. Use focused unit/domain tests for deterministic rules and instrumentation for app/database/runtime behavior. Use actual system surfaces when picker, Sharesheet, or another-app handoff itself matters. Visual claims require rendered evidence and inspection.

Report evidence accurately: TESTED, DOMAIN-INSTRUMENTED, UI-INSTRUMENTED, SYSTEM-HANDOFF, HUMAN/RENDERED, and NOT RUN. Use the toolchain in [`AGENTS.md`](../../AGENTS.md). Discover the canonical AVD by display name `Pixel 10a V16 Service` and resolve its adb serial for every run. Never direct device commands to an implicit/default serial.

## Late Romanian technician pilot gate

Before release preparation is product-valid, a Romanian-localized, functionally complete representative service workflow and customer report must be evaluated by a real technician/trade user. AI agreement, owner/developer review, automated checks, and emulator evidence do not replace this external evidence. The existing sequence remains functional completion and hardening, whole-product UI/UX overhaul, Romanian localization and copy freeze, then the real-technician pilot. This gate does not authorize doing localization or running the pilot as part of unrelated corrections.
