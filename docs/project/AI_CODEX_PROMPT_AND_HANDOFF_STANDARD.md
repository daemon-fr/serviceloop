# ServiceLoop — AI Codex Prompt and Handoff Standard

**Version:** 1.0  
**Status:** ACTIVE PROCESS AUTHORITY  
**Purpose:** Define how ServiceLoop AI orchestrators prepare implementation prompts, correction prompts, independent-review handoffs, and new-thread continuation handoffs so substantial work can be executed reliably by the least expensive intended Codex model without unnecessary owner involvement.

This document governs **AI-to-AI work delegation and continuity**. It does not define product behavior and does not outrank product/source-of-truth authority.

## 1. Mandatory use

An orchestrator/reviewer must read and internalize this document:

1. before writing any substantial Codex implementation prompt;
2. before writing any substantial Codex correction/recovery prompt;
3. when reconstructing a fresh ServiceLoop thread that will author Codex work;
4. before producing a new-thread handoff/continuation prompt;
5. whenever a prior prompt was too architectural, vague, fragmented, or dependent on a stronger model inferring missing implementation detail.

A stronger implementation model does **not** justify a less precise prompt.

The default authoring target is:

> Write the assignment so that the least expensive approved execution model reasonably intended for the task — normally Luna High — can execute it without reconstructing product architecture, making avoidable product decisions, or asking the owner routine implementation questions.

Sol/Terra or another stronger model may execute the same prompt. Prompt quality must not depend on the stronger model supplying missing analysis.

Implementation Codex does not need to read this prompt-authoring document merely to execute a finished assignment unless the assignment explicitly tells it to author subsequent prompts. It must read and obey `AGENTS.md`, the finished task contract, and the relevant product/milestone authority named by that contract.

## 2. Core principle: front-load orchestration intelligence

The orchestrator is responsible for converting product intent and repository reality into an execution contract.

Do not hand Codex a product brief and expect it to rediscover:

- which current class owns the behavior;
- which capability is overloaded;
- which table is authoritative;
- which older milestone is superseded;
- which UI primitive should be reused;
- whether a migration is additive or replacement;
- which business effect must be idempotent;
- which runtime evidence distinguishes a real fix from source inspection.

Before delegating, inspect enough current source/docs to answer those questions wherever reasonably possible.

The required pattern for each significant change is:

> **CURRENT SOURCE STATE → REQUIRED CHANGE → EXACT IMPLEMENTATION SEMANTICS → CODE/DATA HOTSPOTS → COMPATIBILITY/FAILURE RULES → TESTS → RUNTIME ACCEPTANCE**

Do not substitute a high-level architectural paragraph for that chain.

## 3. Verify state before authoring the prompt

Before writing a substantial implementation prompt, establish the actual work base as far as available tools permit:

- repository and remote;
- source branch;
- exact source SHA;
- protected baseline SHA;
- upstream/divergence;
- worktree cleanliness or known noise;
- app version;
- Room schema;
- Recovery schema;
- relevant transport/package versions;
- current implemented/reviewed/unreviewed milestone state.

If local worktree state is inaccessible, say so and require Codex to verify it before editing. Never invent local cleanliness from remote state.

Use current repository/docs as durable truth. Distinguish:

- adopted;
- implemented;
- Codex-reported;
- independently source-reviewed;
- runtime/device-tested;
- owner-reviewed;
- proposed;
- superseded/historical.

The prompt must not blur those states.

## 4. Required authority/read set

Every substantial prompt must name the exact authority files Codex must read.

At minimum, normally include:

- `AGENTS.md`;
- `docs/project/SOURCE_OF_TRUTH.md`;
- `docs/project/BASELINE_DECISIONS.md`;
- `docs/project/IMPLEMENTATION_STATE.md`;
- the current/relevant milestone authority;
- earlier milestone docs only when their semantics remain active;
- current source files/services/entities/tests that materially govern the task.

Do not make Codex reread every historical document when a focused set is sufficient.

Conversely, do not omit a relevant source file merely because a milestone document describes its old implementation.

## 5. Prompt detail standard

A substantial execution prompt should contain the following sections when applicable.

### A. Role and execution mode

State that Codex is the execution-level Android developer for the task.

State whether it must:

- execute the complete milestone;
- continue autonomously through internal stages;
- repair ordinary failures;
- commit/push;
- stop only at a genuine owner/data/security boundary.

Do not leave autonomy implicit.

### B. Exact start state

Provide verified:

- repo path;
- remote;
- source branch;
- required starting SHA;
- target branch;
- protected branch/SHA;
- known workspace noise;
- required initial Git commands.

Require Codex to stop/escalate only if the required starting point materially differs and cannot be safely reconciled.

### C. Product and semantic invariants

List the business truths the task is most likely to damage.

Examples:

- booked != serviced;
- performed != blindly fulfilled;
- finalization != PDF generation;
- dispatched != delivered;
- exported != received;
- imported remote execution != local execution;
- immutable history/correction/void truth;
- recurrence exactly once;
- public/private evidence boundaries.

Do not copy unrelated invariants merely to make a long prompt.

### D. Current implementation diagnosis

For each major change, tell Codex what exists now.

When known from source inspection, name:

- files;
- classes;
- composables;
- routes;
- data classes/entities;
- DAO/service functions;
- capability fields;
- serializers/codecs;
- tests;
- design-system primitives.

Explain the current defect or architectural mismatch.

Example quality:

Bad:
> Split the Coordinator creation capability.

Good:
> `WorkspacePolicy.kt` currently gives `WorkspaceCapabilities` one `canCreateLocalWork` flag; Coordinator is false. `ServiceLoopNavigation.kt` gates `visit/new` with that flag, while field routes already use `canPerformFieldWork`. Due Services selection/action visibility also depends on the overloaded flag. Introduce `canCreateVisits` (or repository-consistent equivalent), route Visit creation/Book through it, retain Start/field execution under `canPerformFieldWork`, and update the role matrix/tests accordingly.

The second form is the required standard.

### E. Exact required behavior

Specify:

- labels/copy when adopted;
- states and role differences;
- navigation placement;
- lifecycle consequences;
- persistence meaning;
- positive cases;
- negative cases;
- disabled/conflict/error behavior.

For UI tasks, specify the rendered/interactive result, not only modifier-level intent.

### F. Implementation guidance

Where current inspection provides enough information, tell Codex:

- what to extract/reuse;
- what remains authoritative;
- which duplicate implementation should disappear;
- transaction boundaries;
- idempotency keys/identity;
- mapping rules;
- expected schema fields;
- migration defaults;
- state ownership;
- package/serializer boundaries;
- which Android dependencies must stay out of domain code.

Do not dictate fragile syntax line-by-line when current source may evolve, but do not force Codex to rediscover architecture already known to the orchestrator.

Avoid vague phrases such as:

- “as appropriate”;
- “handle persistence”;
- “make this robust”;
- “use repository conventions”;
- “add tests”;
- “refactor if needed”;

unless immediately followed by concrete required meaning.

### G. Compatibility and migration

For any schema/protocol/history change, specify:

- old version;
- new version;
- additive/destructive rule;
- legacy decode/read behavior;
- fallback/default values;
- how old rows are ordered/mapped;
- what must remain byte/value compatible;
- rollback/retry behavior;
- Recovery implications;
- whether old user-facing exports continue to be emitted or only decoded.

Never leave migration policy for Codex to infer from a version bump.

### H. Explicit non-goals

State nearby things that must not be added.

This is especially important around:

- backend/API;
- accounts/authentication;
- live sync;
- billing;
- inventory;
- generic frameworks;
- new toolchain/dependencies;
- speculative server DTOs;
- unrelated UI redesign.

### I. Exact tests

Do not merely request “coverage.”

List scenarios and expected facts.

For domain/persistence work, specify:

- setup;
- action/retry/conflict;
- exact persisted consequence;
- exact absence of duplicate/forbidden effects.

For UI work, specify an actual interaction sequence where runtime behavior matters.

If a bug was observed in the rendered app, require a test that reproduces the interaction rather than a source-string assertion alone.

### J. Runtime/device acceptance

When appropriate, give the exact canonical AVD policy and interaction journey.

State what evidence must be:

- UI-INSTRUMENTED;
- DOMAIN-INSTRUMENTED;
- SYSTEM-HANDOFF;
- HUMAN/RENDERED;
- or may remain NOT RUN.

For launch stability, do not accept `am start` as proof. Require a delay/process/logcat check where startup regressions are plausible.

### K. Tooling fallbacks

Carry forward recurring environment knowledge:

- explicit canonical AVD targeting;
- adb PATH workaround;
- preserved-data install with `install -r`;
- Compose semantic interaction over coordinate tapping;
- lazy-list composition behavior;
- IME/stylus fallback;
- optional helper failure policy;
- Phosphor generator fallback;
- no encoded/obfuscated/security-bypass commands.

Do not make every Codex session rediscover known environment behavior.

### L. Git and handoff

Specify:

- allowed branch creation;
- commit/push authority;
- no protected-branch merge;
- no force-push;
- required final status/divergence/diff checks;
- exact report contents.

### M. Exhaustive acceptance checklist

Every substantial prompt should end with a checklist covering **every authorized requirement** that could otherwise be forgotten.

Use concrete checkboxes such as:

    [ ] Coordinator can Book but cannot Start
    [ ] legacy v2 register contacts import with deterministic order
    [ ] duplicate WORK_RESULT import does not advance recurrence twice
    [ ] old aggregate report keeps exact source revision IDs
    [ ] canonical AVD startup remains alive after delayed smoke
    [ ] protected master unchanged

The checklist is not decorative. Codex must use it before declaring the task complete.

## 6. Model-neutrality and lower-cost execution

Prompt authors must assume that execution may be performed by Luna High unless a task explicitly requires a stronger architecture/reasoning model.

Therefore:

- do architecture/product reasoning in Chat/orchestration first;
- give Codex stable decisions, not unresolved alternatives;
- include known code hotspots;
- include expected data transformations;
- include exact tests and failure cases;
- include runtime acceptance;
- include the final checklist.

A stronger Codex model may make additional sound implementation decisions within scope, but the prompt must not rely on that additional intelligence for correctness.

Do not write a vague prompt for Sol and a detailed prompt for Luna. Maintain one high execution-level standard.

## 7. Task sizing

“Execution-level” does not mean “tiny.”

Prefer substantial coherent assignments that a capable implementation model can execute unattended.

Split only at meaningful boundaries such as:

- data-integrity risk;
- architecture transition;
- protocol/migration boundary;
- independent review gate;
- environment/runtime validation boundary.

Do not fragment one coherent milestone into dozens of prompts merely to make each prompt shorter.

For very large milestones, one prompt may contain internal stages. Require Codex to continue through them without owner handoff unless the prompt explicitly establishes a gate.

## 8. High-risk domain requirements

For recurrence, immutable history, migration, recovery, transport, result import, file ownership, or reports, prompts must explicitly describe:

- authoritative identity;
- idempotency key;
- transaction boundary;
- retry behavior;
- duplicate behavior;
- stale/conflict behavior;
- process-restart behavior where relevant;
- rollback/failure state;
- historical provenance;
- public/private boundary;
- test fixtures proving the invariant.

Do not accept “make it idempotent” without saying what must be idempotent and what exact effect must happen at most once.

## 9. UI/UX execution requirements

When an owner-observed visual or interaction defect exists, include:

1. the observed defect;
2. the source diagnosis if known;
3. plausible causes if diagnosis is incomplete;
4. the exact required rendered behavior;
5. the existing primitive/style that should be reused;
6. what visual workaround is rejected;
7. accessibility/touch-target requirements;
8. an interaction/render test;
9. HUMAN/RENDERED inspection when material.

Do not close a runtime UI defect solely because source code appears correct.

## 10. Correction prompts

After independent review, correction prompts should be consolidated.

A correction prompt must include:

- exact reviewed branch/SHA;
- verified findings, ranked by severity;
- affected source paths;
- expected correction;
- regression areas;
- tests to add/rerun;
- runtime evidence required;
- unchanged scope;
- Git expectations.

Do not send one prompt per small defect when they can be repaired coherently.

Do not reopen accepted design while fixing implementation.

## 11. Independent review boundary

Codex's final report is evidence of what Codex **reported**, not independent verification.

The orchestrator should, where tools permit:

- verify branch/SHA;
- inspect diff/current source;
- check required files;
- inspect tests;
- compare implementation against the prompt;
- distinguish reported test results from independently rerun results;
- identify incomplete or misleading claims;
- issue one consolidated correction prompt if needed.

Do not bank architecture-sensitive work solely from a summary.

Priority independent-review areas include:

- recurrence/obligation effects;
- immutable final history;
- migration/recovery;
- Dispatch generation/provenance;
- WORK_RESULT/result ingestion;
- aggregate report provenance;
- photo/file ownership and deletion;
- role capability enforcement.

## 12. New-thread handoff standard

A handoff is a lossless continuation contract, not a short summary.

When the owner asks for a handoff/continuation prompt, include all applicable:

### ROLE / AUTHORITY
- successor role;
- authority order;
- mandatory process docs;
- instruction not to restart discovery.

### REPO + SHA STATE
- repo;
- active branch;
- exact HEAD;
- upstream/divergence;
- protected branch;
- worktree state if verified;
- known noise if not clean.

Never guess.

### CURRENT STATE / TASK
- current milestone;
- adopted vs implemented vs reviewed status;
- exact continuation point.

### WHAT JUST HAPPENED
- latest implementation/review;
- commits;
- corrections;
- important findings.

### UNFINISHED WORK
- what is done;
- what remains;
- why it remains;
- exact next action.

### LATEST CODEX HANDOFF
- objective;
- constraints;
- commits;
- reported tests;
- workarounds;
- independent-review status.

### FINDINGS / RISKS
- bugs/root causes;
- integrity risks;
- fragile tests;
- incomplete evidence.

### TEST / DEVICE EVIDENCE
- unit/build/lint;
- domain instrumentation;
- UI instrumentation;
- system handoff;
- rendered evidence;
- NOT RUN boundaries.

### TOOLING / ENVIRONMENT
- adb/AVD;
- Gradle;
- Compose/IME;
- helper/script;
- Git/Windows;
- security constraints.

### OWNER DECISIONS
- chat-only decisions that are not yet durable;
- preferences that affect execution;
- superseded decisions.

### REJECTED / SUPERSEDED DIRECTIONS
- alternatives a fresh thread might otherwise revive.

### FILES / ASSETS
- required files;
- artifacts;
- missing/re-upload status where relevant.

### IMMEDIATE NEXT ACTIONS
- exact first actions for successor.

### NEAR-TERM QUEUE
- only real upcoming work.

### CONTINUATION INSTRUCTION
End by telling the successor to:

- reconstruct state from the handoff, Project Instructions, current memory/context, repo/docs;
- verify repository state;
- treat repo/docs as durable truth and the handoff as transient context;
- read this prompt/handoff standard before writing new Codex work;
- not restart discovery or ask the owner to repeat known context;
- surface discrepancies;
- continue from the stated next action.

If active work is interrupted, additionally record:

- exact task;
- last completed sub-step;
- commands/tools/results;
- modified/generated files;
- validated assumptions;
- unresolved error;
- precise continuation point.

## 13. Do not duplicate durable process rules into every handoff

New-thread handoffs should point to this file rather than reproduce its complete content.

The handoff still needs task-specific transient context.

Likewise, implementation prompts should reference the relevant durable product docs but must include enough exact task semantics that Codex does not need to infer the intended change from dozens of files.

Balance:

- durable process rules live here;
- durable product truth lives in product/project authority docs;
- transient thread/task truth lives in the handoff;
- execution instructions live in the Codex prompt.

## 14. Prompt-author preflight quality gate

Before giving a substantial Codex prompt to the owner, the orchestrator must answer YES to all applicable items:

- [ ] I verified the source branch/SHA rather than assuming it.
- [ ] I know which product/milestone docs govern this task.
- [ ] I inspected the current source for every architecture-sensitive change.
- [ ] The prompt distinguishes current behavior from desired behavior.
- [ ] Known files/classes/functions/tables/routes are named where useful.
- [ ] Persistence/transaction/idempotency meaning is explicit.
- [ ] Migration/legacy behavior is explicit.
- [ ] UI defects specify actual rendered/interactive acceptance.
- [ ] Negative/failure cases are stated.
- [ ] Non-goals prevent nearby scope creep.
- [ ] Test cases specify expected facts, not “add coverage.”
- [ ] Runtime/device evidence is distinguished from source/unit evidence.
- [ ] Known environment workarounds are carried forward.
- [ ] Git start/end state and protected-branch rules are explicit.
- [ ] The prompt ends with an exhaustive acceptance checklist.
- [ ] A Luna High-class execution model should not need to make an avoidable consequential product/architecture decision.
- [ ] A stronger model would benefit from the same prompt rather than require a different one.

If any applicable answer is NO, improve the prompt before handing it off.

## 15. Handoff-author preflight quality gate

Before producing a new-thread handoff, confirm it preserves:

- [ ] exact branch/SHA;
- [ ] unfinished work;
- [ ] unreviewed Codex work;
- [ ] reported vs independently verified tests;
- [ ] recurring workarounds;
- [ ] accepted/rejected choices;
- [ ] device/emulator evidence;
- [ ] security/isolation constraints;
- [ ] missing files/assets;
- [ ] exact next action;
- [ ] instruction to read this standard before authoring further Codex prompts.

The standard is:

> A fresh competent AI should be able to continue without the owner reconstructing the old thread, and a Luna High-class Codex model should receive enough implementation detail to execute the next task without rediscovering decisions the orchestrator could have resolved first.
