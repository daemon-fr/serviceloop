# V16 Service — AI Codex Prompt, Execution, Review, and Handoff Standard

**Version:** 2.1
**Status:** ACTIVE PROCESS AUTHORITY  
**Project:** V16 Service
**Purpose:** Define how V16 Service AI orchestrators turn adopted product intent and current repository reality into execution-level Codex assignments, how Codex is expected to execute them, how evidence is classified, how completed work is independently reviewed, and how interrupted or fresh-thread work is handed off without owner reconstruction.

---

## 1. Prime directive

The central rule is:

> **Do architecture and product reasoning in Chat. Give Codex execution decisions, not architecture homework.**

The prompt author is responsible for reducing avoidable ambiguity before delegation.

A strong V16 Service Codex prompt should allow the least expensive approved execution model reasonably intended for the task — normally **Luna High** — to execute correctly without having to rediscover product architecture, infer business meaning, reconstruct accepted decisions, or ask the owner routine implementation questions.

A stronger execution model may be used, but stronger model capability is not permission to write a weaker prompt.

The standard pattern for every significant change is:

> **CURRENT SOURCE STATE → REQUIRED CHANGE → EXACT IMPLEMENTATION SEMANTICS → CODE/DATA HOTSPOTS → COMPATIBILITY / FAILURE RULES → TESTS → RUNTIME ACCEPTANCE → GIT / HANDOFF**

If a prompt merely says “fix these things,” “improve this screen,” “make export better,” “handle persistence,” or “add tests,” it is not execution-level enough.

---

## 2. Relationship to other V16 Service authority

This document is **process authority**. It governs delegation, execution, review, evidence, and continuity.

It does not define product behavior by itself and does not outrank product/source-of-truth authority.

Use the repository authority order defined by V16 Service, normally:

1. current repository + authoritative `/docs`;
2. explicit owner decisions and accepted amendments;
3. `AGENTS.md` and current task/milestone contracts;
4. current-thread transient context;
5. older conversations only for missing history.

Important distinction:

- **Repo/docs beat the prompt for factual repository state** if a prompt contains stale implementation facts.
- **An explicit owner decision in the task defines new intended behavior** even if current code/docs still reflect the old behavior.
- Current code/tests establish implementation state; they do not silently create new product requirements.
- Historical docs explain rationale but do not revive superseded behavior.

Never silently reconcile conflicting sources by invention. If a real consequential conflict cannot be resolved from authority, escalate it with one recommended resolution.

---

## 3. Roles and responsibility split

### 3.1 Orchestrator / reviewer

The ChatGPT orchestration role owns:

- product interpretation inside adopted scope;
- architecture decisions that can reasonably be settled from repo/docs;
- UX decisions that do not require owner intervention;
- source-state inspection before delegation;
- identification of authoritative classes, tables, routes, primitives, and invariants;
- task boundaries;
- execution-level prompt authoring;
- independent review after Codex handoff;
- consolidated correction prompts;
- acceptance recommendations;
- continuity and new-thread handoffs.

The orchestrator should not routinely push ordinary decisions back to the owner.

### 3.2 Codex implementation agent

Codex owns:

- implementation inside the authorized task;
- ordinary code structure and refactoring choices;
- diagnosis of normal build/test/device failures;
- safe transparent tooling fallbacks;
- concrete tests;
- runtime/device verification where required;
- coherent commits and authorized pushes;
- self-review against the acceptance checklist;
- truthful final handoff.

Codex must not treat a substantial assignment as a suggestion to implement only the first convenient tranche.

### 3.3 Owner

The owner should be involved only for genuinely consequential decisions such as:

- destructive/data-loss semantics;
- new product meaning;
- changed recurrence/obligation meaning;
- privacy/security promise changes;
- authentication model;
- backend/accounts/live sync;
- new paid/licensed dependency;
- material scope expansion;
- publication/release authorization;
- destructive real-data operations;
- unresolved product conflicts not answerable from authority.

Do **not** ask the owner to decide:

- ordinary Compose layout;
- exact spacing;
- helper location;
- variable names;
- migration SQL mechanics;
- ordinary copy refinements;
- test file placement;
- routine refactors;
- standard error handling.

---

## 4. Task sizing: substantial, coherent, unattended

Execution-level does **not** mean tiny.

Prefer one substantial coherent assignment when the task has one product/architecture meaning and can be executed safely end-to-end.

Do not fragment work merely because it is large.

Split only at meaningful boundaries such as:

- data-integrity risk;
- architecture transition;
- protocol/migration boundary;
- platform-risk boundary;
- destructive-operation boundary;
- independent-review gate;
- owner decision gate.

The objective is fewer owner handoffs, not fewer internal implementation/test/repair iterations.

### 4.1 One large prompt is better than prompt shuttling

A coherent task may contain stages:

A. persistence  
B. domain  
C. UI  
D. migration  
E. protocol  
F. tests  
G. runtime  
H. docs  
I. Git

But those are internal execution stages, not automatic handoff points.

Do not make the owner repeatedly shuttle “continue” prompts merely because Codex reached a clean commit.

---

## 5. Non-negotiable run-to-completion rule

A substantial authorized task is expected to run to completion unless a genuine external blocker exists.

The following are **not valid reasons to stop**:

- “this is enough for one run”;
- “the remaining work is large”;
- “the next stage is architecture-heavy”;
- “this is a good clean checkpoint”;
- “the branch is pushed”;
- “the tests are green so far”;
- “I want to reduce risk”;
- “I decided to continue in another run.”

A clean commit is a **recovery point**, not a handoff point.

After each coherent commit:

> **Continue immediately to the next authorized stage.**

Before any final response, Codex must re-read the task acceptance checklist.

If any required implementation item remains incomplete and there is no genuine external blocker:

> **Do not return a final handoff. Continue working.**

Valid early-stop conditions are limited to:

1. the execution environment itself prevents further progress;
2. repository state materially differs in a way that cannot be safely reconciled;
3. a destructive/data-integrity/security consequence requires owner authorization;
4. an actual unresolved product/architecture decision lies outside existing authority.

If an early stop is unavoidable, Codex must state:

- the exact external blocker;
- the exact last completed sub-step;
- the exact partial state;
- modified/generated files;
- tests run and their results;
- what remains;
- the precise continuation point.

Do not retrospectively justify a voluntary stop as if it were platform-enforced.

---

## 6. Mandatory pre-prompt state verification

Before authoring a substantial Codex prompt, inspect enough current state to avoid stale instructions.

Establish, as far as available tools permit:

- repository path;
- remote;
- active branch;
- exact local/remote HEAD if available;
- protected baseline branch/SHA;
- divergence/upstream state;
- worktree cleanliness or known noise;
- app version/code;
- Room schema version;
- Recovery schema version;
- relevant transport/package versions;
- current milestone status;
- implemented vs unimplemented portions;
- latest Codex commits and review status.

Never guess a SHA.

If local worktree access is unavailable, say so and make Codex verify it before edits.

### 6.1 Exact repo-state block

Every significant prompt should normally contain:

```text
Repository:
    <repository root from git rev-parse --show-toplevel>

Remote:
    <URL from git remote get-url origin>

Expected branch:
    <exact>

Expected starting SHA:
    <exact>

Protected master:
    <exact>
```

and an initial verification sequence appropriate to the task.

### 6.2 Preserve known IDE noise correctly

Known Android Studio noise must be treated according to current repo instructions.

Do not:

- stage IDE/device-selection noise;
- blindly discard materially different `.idea` changes;
- enter repeated restore loops while Android Studio rewrites the same file.

State known noise explicitly when it matters.

---

## 7. Mandatory authority/read set in every substantial prompt

Every prompt must tell Codex exactly what to read.

Normally include:

- `AGENTS.md`;
- `docs/project/SOURCE_OF_TRUTH.md`;
- `docs/project/BASELINE_DECISIONS.md`;
- `docs/project/IMPLEMENTATION_STATE.md`;
- the current milestone/task authority;
- earlier milestone docs only where semantics remain active;
- current source files/classes/services/entities/routes/tests that materially govern the task.

Do not make Codex reread all project history when a focused set is enough.

Do not omit relevant source merely because an older milestone document described it.

---

## 8. Explain current truth before requested change

Cheap models perform best when the prompt first explains **why the current code exists and what it currently does**.

Use the pattern:

### CURRENT

State verified implementation facts.

Example:

```text
CURRENT:
- Coordinator canCreateVisits = true.
- Coordinator canPerformFieldWork = false.
- visit/new is gated by canCreateVisits.
- inspection/{id} is gated by canPerformFieldWork.
```

### REQUIRED / OWNER DECISION

State the adopted target.

```text
OWNER DECISION:
- Coordinator may create and Book canonical Visits.
- Coordinator must not Start or perform field Service execution.
```

### NON-GOAL

State what must remain impossible.

```text
NON-GOAL:
- Do not allow Coordinator technician execution.
```

This is far safer than telling Codex only:

> “Fix Coordinator permissions.”

---

## 9. State product invariants repeatedly where they matter

When a task touches Visits, service completion, Dispatch, reports, import/export, recurrence, history, photos, or recovery, repeat the relevant invariants directly in the task.

Do not assume Codex will correctly reconstruct them from distant docs.

Core examples:

- booked != serviced;
- performed != blindly fulfilled;
- finalized != PDF generated;
- dispatched != delivered;
- exported != received;
- locally saved != backed up;
- remote imported work != local Coordinator execution;
- immutable history must remain immutable;
- corrections append rather than rewrite;
- void preserves historical truth;
- recurrence/business effects must occur exactly once;
- assignment generation/provenance must remain truthful;
- report provenance must remain frozen;
- private/internal evidence must not leak to customer output.

Repetition of critical business meaning is cheaper than repairing semantic corruption later.

---

## 10. Separate product layers explicitly

For every nontrivial change, identify which layer(s) are affected:

- visual only;
- interaction/navigation;
- domain behavior;
- persistence/schema;
- transport protocol;
- reporting;
- migration/recovery;
- test harness;
- Android/system handoff.

Never allow a model to hide behavioral changes inside “UI cleanup.”

### 10.1 If Room changes

State:

- old Room version;
- target Room version;
- exact new/changed fields/tables;
- migration direction;
- additive vs destructive rule;
- defaults/backfill;
- indexes/constraints;
- Recovery consequences;
- `.v16service` consequences;
- generated schema implications;
- preserved-device implications.

### 10.2 If transport changes

State:

- outer envelope version;
- purpose;
- payload/family version;
- legacy decode behavior;
- new emission behavior;
- trust boundary;
- target/source identity;
- integrity/hash behavior;
- idempotency identity;
- package limits;
- failure behavior.

### 10.3 If reporting changes

State:

- authoritative source revision;
- local vs imported provenance;
- customer/public filters;
- source freezing;
- correction behavior;
- rendition/history behavior.

---

## 11. Use explicit role matrices

When visibility or capability depends on role, provide a role matrix or explicit per-role list.

Do not scatter role conditions across prose.

A role table should distinguish at least:

- visibility;
- route access;
- domain capability;
- field execution;
- assignment/coordination;
- import/export;
- report generation;
- management actions.

Hidden UI alone is never sufficient if a direct route/domain call would bypass the role rule.

---

## 12. Tell Codex exactly what must be reused

When the product owner or current design requires reuse of an existing primitive, write it explicitly.

Example:

> Use the actual current Due Services selectable card primitive. Extract/refactor the shared primitive if necessary and make both screens instantiate it. Do not visually imitate it and do not copy its modifiers into a new bespoke card.

This rule applies to:

- design-system components;
- selection rows;
- card primitives;
- status/urgency classifiers;
- date/time controls;
- import preview mechanisms;
- report renderers;
- persistence services;
- codecs;
- domain evaluators.

Approximation drift is a defect when exact reuse is the requirement.

---

## 13. Include explicit “DO NOT” boundaries

Negative boundaries materially improve execution reliability.

Typical V16 Service examples:

- no backend;
- no accounts;
- no live/cloud sync;
- no speculative API abstraction;
- no billing/accounting;
- no inventory;
- no customer portal;
- no toolchain churn;
- no opportunistic dependencies;
- no destructive Room migration;
- no fake local execution for imported work;
- no recurrence on historical import;
- no clearing canonical AVD data;
- no physical-phone substitution;
- no hidden production-code changes to compensate for a broken test harness;
- no fake runtime claims;
- no force-push;
- no protected-master movement.

Tailor the list to the task.

---

## 14. Give current source facts where they save rediscovery

The orchestrator should inspect source enough to tell Codex what is already known.

Useful prompt facts include:

- exact class/service owning behavior;
- current schema/version;
- current route guard;
- current raw Checkbox usage;
- existing report renderer capabilities;
- current evaluator/blocker kinds;
- current package version;
- current AVD name;
- exact current defect;
- relevant DAO/query;
- known test that is stale.

Do not dump giant source excerpts unnecessarily.

Prefer:

> “`WorkspacePolicy.kt` currently owns X; `V16ServiceNavigation.kt` gates Y with Z.”

over pasting hundreds of lines.

---

## 15. Define the UX, not merely labels

For UI work, specify the actual rendered and interactive result.

Include as relevant:

- hierarchy;
- visual prominence;
- spacing intent;
- card vs settings-row semantics;
- button hierarchy;
- touch targets;
- full-width vs gutters;
- wrapping behavior;
- empty state;
- loading state;
- disabled state;
- conflict/error state;
- accessibility semantics;
- font-scale adaptation;
- light/dark behavior;
- orientation/adaptive behavior;
- stable test tags where needed.

Example:

> “Show the ID in a centered field-like container with larger teal text and an embedded trailing Copy action using the existing field-action primitive; remove the separate full-width Copy CTA.”

is execution-level.

> “Improve ID display.”

is not.

---

## 16. Exact implementation semantics beat vague robustness language

Avoid phrases such as:

- “handle persistence”;
- “make it robust”;
- “support migration”;
- “fix conflicts”;
- “use repository conventions”;
- “add proper tests”;
- “refactor if needed.”

If such a phrase appears, immediately define the required meaning.

For data-integrity work, specify:

- authoritative table/entity;
- transaction boundary;
- exact state transition;
- idempotency key;
- duplicate/retry behavior;
- stale/conflict behavior;
- rollback behavior;
- immutable source identity;
- error case;
- negative side effects that must not happen.

---

## 17. Compatibility and migration must be explicit

For schema/protocol/history changes, state all relevant compatibility facts:

- old version;
- new version;
- migration direction;
- whether old format remains readable;
- whether old format remains emitted;
- fallback/default values;
- deterministic ordering/backfill;
- unsupported-version behavior;
- retry behavior;
- Recovery behavior;
- whether old files remain byte-compatible;
- whether accepted history can be rewritten.

Never leave migration policy for Codex to infer merely from a version bump.

---

## 18. Test the actual bug, not a proxy

If the owner reports a runtime defect, the acceptance test should reproduce the interaction that failed.

Example owner report:

> “Selecting a Due Service does nothing.”

Correct test:

1. render actual populated Due Services screen;
2. tap actual selection control;
3. assert selected state;
4. assert action surface is displayed;
5. assert expected actions are displayed/enabled.

Insufficient proxy:

- asserting an action composable exists in source;
- checking a state variable in isolation when the screen failed to render it.

Source presence is not runtime proof.

---

## 19. Evidence taxonomy is mandatory

Always distinguish evidence types.

Use these labels consistently:

### IMPLEMENTED
Source changes exist.

### SOURCE-REVIEWED
A reviewer inspected relevant source/diff.

### JVM-TESTED
Host/local unit/domain tests actually ran.

### MIGRATION-TESTED
Actual Room migration test ran.

### DOMAIN-INSTRUMENTED
Android instrumentation exercised repository/domain behavior, but not necessarily real UI/system interaction.

### UI-INSTRUMENTED
Compose/UI instrumentation exercised actual UI semantics/interactions.

### SYSTEM-HANDOFF
Android system surface or another app was actually invoked where the handoff itself matters:
- picker;
- Sharesheet;
- Maps;
- Settings;
- Calendar provider;
- etc.

### HUMAN/RENDERED
A real rendered screenshot/screen/report was visually inspected.

### OWNER-REVIEWED
Owner actually reviewed the implementation/result.

### OWNER-ACCEPTED
Owner explicitly accepted it.

### PILOT-VERIFIED
External real-user pilot evidence exists.

### NOT RUN
The evidence was not executed.

Do not imply one evidence class proves another.

Examples:

- PDF layout unit test != rendered PDF inspection.
- Codec test != system picker handoff.
- source inspection != emulator runtime.
- screenshot != persistence correctness.
- emulator behavior != physical-device behavior.
- AI agreement != pilot evidence.

If a required evidence type was not run, say so plainly.

---

## 20. Runtime acceptance must exercise touched surfaces

A runtime smoke is not merely launching the app.

For substantial changes, enumerate the screens/actions whose behavior or inputs changed. Choose the cheapest evidence layer that actually proves each requirement. Run actual device, rendered, or system handoff checks where those behaviors matter; do not repeat an unaffected screen tour merely because a later file changed.

Potential examples:

- Home root tabs;
- Work root tabs;
- Register;
- Team;
- role picker;
- ID copy;
- Trusted IDs;
- canonical New Visit;
- Assignment section;
- Dispatch package;
- result export/import;
- aggregate report;
- customer contacts;
- Equipment breadcrumb;
- business timezone;
- Export Center;
- `.v16service` native transfer;
- image cleanup;
- Settings IA.

Use the actual task subset, not every screen mechanically.

### 20.1 Canonical AVD

Canonical display name:

> `Pixel 10a V16 Service`

Resolve the adb serial dynamically every run.

Never assume `emulator-5554`.

Every device command must use:

```text
adb -s <resolved-serial> ...
```

Do not substitute:

- another emulator;
- physical phone.

Do not reset/recreate/wipe the canonical AVD without explicit owner authorization.

Build first, then install using:

```text
adb -s <serial> install -r <apk>
```

For launch stability:

- launch;
- wait long enough to catch delayed startup crashes;
- verify process alive;
- inspect relevant fatal log output.

`am start` success alone is not proof.

---

## 21. Tool failure behavior is part of the task contract

Codex must not stop because:

- a helper script fails;
- a branch helper fails;
- sandbox path helper fails;
- adb serial selection is awkward;
- emulator test flakes;
- optional icon generator fails;
- a test selector is stale;
- a lazy-list item is not yet composed;
- IME/stylus interferes with coordinate text entry.

Required behavior:

1. diagnose;
2. identify whether failure is product, test harness, or environment;
3. use a safe transparent fallback;
4. inspect the result;
5. rerun affected verification;
6. continue;
7. report the workaround.

Do not:

- spend half the task repairing an optional helper when a safe fallback exists;
- modify production behavior to satisfy a broken harness;
- weaken correct product behavior to make a stale test pass;
- abandon a well-defined task on first environmental error.

---

## 22. Known V16 Service tooling fallbacks

Carry these into relevant prompts so every Codex run does not rediscover them.

### 22.1 File/patch helper failure

If a managed patch/filesystem helper fails with sandbox/access/path-helper trouble:

- switch promptly to the functioning approved shell/edit path;
- inspect `git diff`;
- run `git diff --check`.

Do not investigate sandbox internals unnecessarily.

### 22.2 Interrupted processes

If an interrupted run may have left processes:

- inspect worktree/diff;
- inspect relevant running processes;
- terminate only clearly orphaned task-specific processes.

Do not indiscriminately kill:

- adb server;
- Android Studio;
- Gradle;
- Git;
- unrelated processes.

### 22.3 Git lock

Never delete `.git/index.lock` blindly.

Verify no Git operation is active first.

### 22.4 Compose automation

Prefer:

- `testTag`;
- `contentDescription`;
- visible-label semantics;
- `performClick`;
- `performTextInput`;
- `performTextReplacement`;
- `performTextClearance`;
- `performScrollTo`;
- `performScrollToNode`;
- `performImeAction`.

Avoid coordinate tapping when semantic interaction is available.

### 22.5 Lazy containers

Off-screen LazyColumn/LazyGrid children may not exist yet in the semantics tree.

Scroll the owning lazy container with a stable matcher first.

Do not wait forever for an uncomposed child.

### 22.6 IME / stylus interference

Switch to semantic Compose instrumentation.

Do not reset or globally reconfigure the canonical AVD merely to type text.

### 22.7 System surfaces

When picker/Sharesheet/Settings/Maps/etc. matter, require actual SYSTEM-HANDOFF evidence.

A domain substitute is not equivalent.

---

## 23. Risk-based verification and evidence validity

Felix's 2026-09-24 process amendment requires judicious test selection. Derive the check set from the changed code, data contracts, failure risks, and required acceptance scenarios. During development, use focused tests and builds. A shared or high-risk integration assignment normally warrants one planned broad pass after stabilization, with focused migration, domain, UI, rendered, and system evidence where those behaviors actually require them.

Available established checks include:

```text
gradlew.bat :app:testDebugUnitTest --no-parallel --console=plain
gradlew.bat :app:assembleDebug --no-parallel --console=plain
gradlew.bat :app:assembleDebugAndroidTest --no-parallel --console=plain
gradlew.bat :app:lintDebug --no-parallel --console=plain
gradlew.bat :app:assembleRelease --no-parallel --console=plain
git diff --check
```

Select the commands that cover the task's actual risks; do not run every command by ritual. A release APK must match final production source when release assembly is a deliverable. Rebuild an Android-test APK when its inputs change.

After an edit, invalidate only evidence whose inputs or behavior it can affect. Run focused affected checks. Repeat a broad suite when shared schema, locking, serialization, or an unbounded impact genuinely requires it. Documentation-only and test-selector-only edits do not invalidate production build or runtime evidence. A broad checkpoint plus documented impact-scoped reruns can be current final evidence; never describe it as a fresh full run on final HEAD.

Report exact commands, outcomes, counts, checkpoint SHA or diff, subsequent changes, and the reason each earlier result remains valid. A known failing required scenario still needs repair or a genuine external blocker.

---

## 24. Git instructions must be explicit

Every substantial prompt should state:

- source branch;
- exact start SHA;
- protected branch/SHA;
- whether commits are authorized;
- whether push is authorized;
- target remote branch;
- prohibited destructive actions.

If the owner has pre-approved ordinary Git operations, say so explicitly.

A robust Git authorization block should distinguish:

### Authorized when task says so

- status;
- branch/SHA inspection;
- fetch;
- fast-forward pull;
- diff/log;
- staging task files;
- coherent commits;
- push to designated milestone branch.

### Still prohibited

- force-push;
- rebase published history;
- destructive reset;
- deleting accepted branches;
- merging protected master without explicit authorization;
- moving master;
- discarding unknown owner work.

Final Git gate should normally verify:

- correct branch;
- final local HEAD;
- remote HEAD;
- divergence `0 0`;
- source/docs worktree clean except documented IDE noise;
- `git diff --check`;
- protected baseline unchanged.

---

## 25. Mandatory final handoff contents

Codex final report should include, as applicable:

### START
- branch;
- starting SHA;
- protected baseline SHA.

### COMMITS
- SHA/message for each coherent commit.

### VERSIONS
- app/versionCode;
- Room;
- Recovery;
- transport/package versions.

### IMPLEMENTED
- concise behavior list.

### MIGRATIONS / DATA
- schema changes;
- migration behavior;
- legacy compatibility;
- preserved data semantics.

### VERIFICATION
- exact commands;
- test counts;
- migration tests;
- instrumentation;
- build/lint/release;
- AVD runtime;
- system handoffs;
- rendered inspection.
- broad checkpoint and subsequent impact-scoped reruns, with evidence carried forward only when its inputs remain valid.

### WORKAROUNDS
- actual environment/tool fallbacks.

### NOT RUN
- every important evidence item not executed.

### RISKS / UNRESOLVED
- only real remaining risks.

### GIT
- final HEAD;
- remote parity;
- worktree state;
- protected branch unchanged.

Avoid vague:

> “Everything passed.”

Enumerate evidence.

---

## 26. Acceptance checklist is mandatory

Every substantial prompt should end with a concrete checkbox list covering every owner requirement and every high-risk technical invariant.

Good:

```text
[ ] Coordinator can Book but cannot Start
[ ] duplicate WORK_RESULT import does not advance recurrence twice
[ ] legacy register v2 imports with deterministic contact order
[ ] old aggregate report keeps exact source revision IDs
[ ] system picker opens directly from Import work package
[ ] canonical AVD survives delayed startup smoke
[ ] protected master unchanged
```

Bad:

```text
[ ] implementation complete
[ ] tests added
[ ] UI looks good
```

Codex must use the checklist before declaring completion.

---

## 27. Correction-prompt rules

After independent review, do not send findings one at a time unless a newly discovered blocker truly requires immediate isolation.

Prefer one consolidated correction prompt.

A correction prompt should include:

1. exact reviewed start SHA;
2. status of prior implementation;
3. each defect with severity/semantic impact;
4. current source root cause;
5. exact corrected behavior;
6. files/hotspots;
7. migration/protocol consequence;
8. regression test that would fail on old SHA;
9. runtime acceptance;
10. run-to-completion rule;
11. Git authorization/state;
12. final checklist.

Do not reopen architecture that passed review.

State explicitly:

> Preserve all already-correct behavior; change only what is required to resolve these findings and their regressions.

---

## 28. Independent review protocol

Codex’s final report is evidence to inspect, not proof.

After every substantial Codex handoff:

1. verify branch and SHA;
2. compare against starting SHA;
3. inspect changed-file scope;
4. inspect high-risk domain/data files;
5. inspect migration;
6. inspect protocol/trust gates;
7. inspect role capability changes;
8. inspect finalization/recurrence/report logic;
9. inspect tests for whether they prove the actual requirement;
10. inspect route-level enforcement;
11. inspect repo hygiene;
12. distinguish Codex-reported tests from independently rerun evidence.
13. check that verification selection and late reruns follow section 23's risk and input rule.

Look specifically for:

- omissions;
- semantics that only look correct in UI;
- UI hiding without route/domain gating;
- stale tests;
- current-state data incorrectly treated as history;
- history incorrectly treated as current truth;
- duplicate business effects;
- privacy leakage;
- backup/cleanup incompatibility;
- misleading completion claims;
- scope creep;
- brittle test-only implementations.

The review is not independent if it merely repeats Codex’s summary.

---

## 29. Review classification

Use explicit state labels.

Examples:

### ADOPTED
Product decision exists.

### IMPLEMENTED
Source exists.

### CODEX-REPORTED TESTED
Codex reports tests ran.

### INDEPENDENTLY SOURCE-REVIEWED
Reviewer inspected source.

### INDEPENDENTLY RUNTIME-VERIFIED
Reviewer/device evidence independently confirms runtime.

### OWNER-REVIEWED
Owner saw/reviewed result.

### OWNER-ACCEPTED
Owner explicitly accepted.

### PARTIAL
Only a defined subset is complete.

### SUPERSEDED / HISTORICAL
No longer current authority.

Never collapse these into one word like “done.”

---

## 30. When to escalate to Felix

Escalate only when a decision materially changes:

- product meaning;
- destructive/data-loss behavior;
- recurrence/obligation meaning;
- privacy/security promise;
- authentication model;
- scope;
- external service/payment dependency;
- protected history;
- publication/release.

When escalating:

- state the decision;
- give one recommended choice;
- explain consequence;
- explain what is blocked.

Do not send a questionnaire of routine options.

---

## 31. New-thread handoff standard

When the owner asks for:

- “the handoff prompt”;
- “new-thread handoff”;
- “continuation prompt”;
- equivalent wording;

produce one READY-TO-PASTE prompt for a fresh thread.

It must allow a competent successor to resume without Felix reconstructing the old conversation.

### 31.1 Required sections

Include all applicable:

- role / authority;
- repo + branch + exact SHA state;
- worktree / remote parity / protected baseline;
- current task;
- what just happened;
- accepted/reviewed/unreviewed/partial/superseded work;
- unfinished work;
- exact continuation point;
- latest Codex objective;
- latest commits;
- Codex-reported tests;
- independent-review status;
- bugs/findings/causes;
- fragile tests;
- tooling hacks/workarounds;
- AVD/device evidence;
- owner decisions;
- rejected directions worth preserving;
- files/assets/artifacts;
- documentation gaps;
- immediate next actions;
- near-term queue.

Omit empty sections.

### 31.2 Continuation instruction

End with an instruction equivalent to:

> Reconstruct current state from this handoff, Project Instructions, memory, and repo/docs; verify repository state first; treat repo/docs as durable truth and this handoff as transient context; do not restart discovery, relitigate accepted decisions, or ask Felix to repeat known context; surface discrepancies and continue from the stated next action.

### 31.3 Active-work interruption

If interrupted mid-task, include:

- exact task;
- last completed sub-step;
- partial implementation/analysis;
- commands/tools/results;
- modified/generated files;
- validated assumptions;
- unresolved errors;
- precise next continuation point.

Never reduce an active interruption to:

> “Continue milestone X.”

---

## 32. Process documentation discipline

The repository should retain one coherent process authority rather than accumulating competing prompt manuals.

Preferred durable structure:

- `AGENTS.md` — concise persistent implementation-agent rules;
- `DELEGATED_AI_DEVELOPMENT_AND_ORCHESTRATION...` — broad orchestration model;
- `AI_CODEX_PROMPT_AND_HANDOFF_STANDARD.md` — detailed prompt/execution/review/handoff authority.

Do not create new permanent process docs for every lesson if the current standard can absorb them.

When a new lesson is proven:

1. update the canonical standard;
2. update `AGENTS.md` only if implementation-agent behavior must change;
3. update delegation doc only if broad orchestration philosophy changed;
4. avoid duplicate/conflicting instructions.

---

## 33. Prompt-authoring anti-patterns

### 33.1 Vague architecture handoff

Bad:

> “Implement native sharing cleanly.”

Better:

- current purposes;
- exact new purpose/version;
- family list;
- merge semantics;
- provenance identity;
- migration consequence;
- tests;
- runtime.

### 33.2 Product question disguised as implementation task

Bad:

> “Figure out the best architecture for aggregate reports.”

Better: decide first in Chat:

- one Customer;
- Visit-level selection;
- exact source revisions;
- immutable aggregate sources;
- derived PDF;
- current business branding;
- frozen technician attribution.

Then tell Codex to implement.

### 33.3 Visual imitation

Bad:

> “Make Outbox look like Due Services.”

Better:

> “Use/extract the actual shared Due Services selectable record primitive.”

### 33.4 Proxy test

Bad:

> “Assert the action bar composable exists.”

Better:

> render actual screen, tap actual item, assert action surface displayed.

### 33.5 Hidden route hole

Bad:

> hide a button for disallowed roles.

Better:

> hide button **and** gate route/domain capability.

### 33.6 Clean-checkpoint stop

Bad:

> commit, push, report remaining work.

Better:

> commit/push as recovery point, then continue.

### 33.7 Evidence inflation

Bad:

> “Verified on emulator” after only compiling an instrumentation APK.

Better:

> distinguish assembled, executed, rendered, handoff, and owner-reviewed evidence.

---

## 34. Canonical substantial-prompt skeleton

Use this skeleton as the starting structure for future significant V16 Service Codex assignments.

```text
V16 SERVICE — <TASK NAME>
=========================

ROLE / EXECUTION MODE
---------------------
You are the execution-level Android developer for V16 Service.
Complete the full authorized assignment.
Do not restart product discovery.
Do not ask the owner routine implementation questions.
Repair ordinary failures and continue.

RUN-TO-COMPLETION RULE
----------------------
A clean commit, pushed checkpoint, passing test stage, or large amount of
remaining work is not permission to stop.
Continue until all required acceptance items are complete or a genuine external
blocker exists.

GIT AUTHORIZATION
-----------------
<state exact authorized operations and prohibited destructive operations>

VERIFIED START STATE
--------------------
Repository:
Remote:
Branch:
Starting SHA:
Protected master:
App version:
Room:
Recovery:
Relevant protocol versions:
Known IDE noise:

INITIAL COMMANDS
----------------
<exact repo/branch/SHA/parity/status verification>

MANDATORY AUTHORITY
-------------------
<exact docs and source files>

LOCKED PRODUCT INVARIANTS
-------------------------
<task-relevant invariants>

CURRENT SOURCE STATE
--------------------
<verified current implementation facts>

OWNER DECISION / REQUIRED CHANGE
--------------------------------
<exact adopted behavior>

NON-GOALS
---------
<explicit exclusions>

IMPLEMENTATION SEMANTICS
------------------------
<state transitions, persistence, identity, transaction, UI, role matrix>

CODE / DATA HOTSPOTS
--------------------
<files/classes/entities/routes/tests>

COMPATIBILITY / MIGRATION
-------------------------
<old/new version, defaults, legacy behavior, recovery>

FAILURE / RETRY / CONFLICT RULES
--------------------------------
<negative paths and idempotency>

UI / INTERACTION
----------------
<rendered hierarchy, controls, accessibility, adaptive behavior>

TESTS
-----
<exact scenarios and expected persisted/rendered facts>

RUNTIME / DEVICE ACCEPTANCE
---------------------------
<canonical AVD and exact interaction journey>

TOOLING FALLBACKS
-----------------
<known relevant fallbacks>

DOCUMENTATION
-------------
<files to update, implemented vs verified truth>

GIT FINALIZATION
----------------
<commit/push/final parity/diff-check expectations>

FINAL REPORT FORMAT
-------------------
<start/end/commits/versions/implementation/tests/not-run/workarounds/risks/git>

FINAL ACCEPTANCE CHECKLIST
--------------------------
[ ] ...
[ ] ...
[ ] ...

FINAL STOP RULE
---------------
If a required implementation item is incomplete and no genuine external blocker
exists, do not return a final handoff. Continue working.
```

---

## 35. Canonical correction-prompt skeleton

```text
V16 SERVICE — <MILESTONE> INDEPENDENT-REVIEW CORRECTION
=======================================================

START
-----
Reviewed SHA:
Branch:
Protected master:

STATUS
------
<implemented / Codex-tested / independently source-reviewed / not accepted>

PRESERVE
--------
List already-correct architecture that must not be reopened.

FINDING 1 — <TITLE>
-------------------
Severity:
Current source cause:
Contract violated:
Required correction:
Hotspots:
Migration/protocol impact:
Regression test:
Runtime acceptance:

FINDING 2 — ...
-----------------

RUN-TO-COMPLETION
-----------------
All findings belong to this correction pass.
A clean checkpoint is not permission to stop.

FINAL GATES
-----------
<risk-selected unit/build/lint/instrumentation/runtime/git checks, one planned broad pass when justified, and impact-scoped late reruns>

CHECKLIST
---------
[ ] finding 1 fixed
[ ] regression test would fail on old SHA
[ ] finding 2 fixed
...
[ ] protected master unchanged
```

---

## 36. Quality gate for prompt authors

Before sending a substantial Codex prompt, the orchestrator must ask:

### State
- Did I verify branch/SHA rather than guess?
- Did I distinguish remote truth from inaccessible local state?
- Did I identify versions/schema/protocols?

### Authority
- Did I name the right docs?
- Did I separate current factual repo state from the new owner decision?
- Did I avoid reviving superseded requirements?

### Architecture
- Did I settle the product/architecture questions that Chat can settle?
- Did I identify the authoritative code/data hotspots?
- Did I define state transitions and idempotency?
- Did I define migration/legacy semantics?

### Scope
- Did I state explicit non-goals?
- Did I avoid hidden backend/sync/enterprise expansion?
- Is the task coherent rather than arbitrarily fragmented?

### UX
- If UI is involved, did I define the actual interaction/result?
- Did I require reuse of exact primitives where necessary?
- Did I include accessibility/adaptation when relevant?

### Tests
- Do tests reproduce actual bugs rather than proxies?
- Did I separate JVM/migration/domain/UI/system/rendered evidence?
- Did I define negative/retry/conflict cases?
- Is the check set proportional to changed inputs and risk, with a planned broad pass only where justified?

### Runtime
- Did I name the canonical AVD rule?
- Did I require touched-surface interaction rather than mere launch?
- Did I require system handoff where the handoff matters?

### Execution
- Did I include safe fallback behavior?
- Did I explicitly prevent voluntary clean-checkpoint stopping?

### Git
- Did I state allowed/prohibited operations?
- Did I define final parity/diff/master checks?

### Handoff
- Did I prescribe a concrete final report?
- Is the acceptance checklist exhaustive?

If several answers are “no,” the prompt is not ready.

---

## 37. Quality gate for independent reviewers

Before accepting Codex work, the reviewer must ask:

- Does remote HEAD match the handoff?
- Did protected master stay unchanged?
- Does changed-file scope match the task?
- Are data migrations genuinely non-destructive?
- Are trust/identity checks at mutation boundaries?
- Can retry duplicate a business effect?
- Are stale/conflicting inputs retained truthfully?
- Did UI hiding leave an ungated route?
- Is imported work falsely represented as local execution?
- Are customer-facing and private/internal data structurally separated?
- Can cleanup break recovery?
- Can readable export contradict native/history semantics?
- Are correction/void revisions immutable?
- Do tests prove the actual requirement?
- Are all affected checks current after later edits, with broad checkpoint and impact-scoped reruns classified honestly?
- Which claims are Codex-reported versus independently verified?
- Is owner review still outstanding?

Do not accept merely because the suite is green.

---

## 38. Stable V16 Service business safeguards for prompt reuse

When relevant, copy these directly into implementation prompts.

### Service truth
- Booked is not serviced.
- Performed is not automatically fulfilled unless adopted recurrence rules say so.
- PARTLY_PERFORMED requires explicit fulfillment choice.
- NOT_PERFORMED never fulfills and requires reason.

### Visit lifecycle
- A Visit may be booked without being started.
- Remote result ingestion must not fake technician execution.
- Coordinator planning must not imply Coordinator field work.

### History
- Final history is immutable.
- Correction appends a revision.
- Void retains historical truth.
- Existing issued report provenance must remain frozen.

### Recurrence
- Apply at most once.
- Retry cannot advance again.
- Stale/conflicting remote truth must not silently advance due state.

### Dispatch
- Dispatch is assignment/transport truth, not proof of service.
- Export is not delivery.
- Imported assignment provenance must remain stable.
- Generation/material identity must be monotonic and retry-safe.

### Reports
- Finalized does not mean PDF generated.
- PDF failure must not undo business completion.
- Aggregate reports freeze exact source revisions.
- Customer output only uses explicitly customer-visible evidence.

### Evidence
- Privacy and report inclusion are distinct.
- Private/internal evidence must not leak through reports or exports whose privacy option excludes it.
- Image cleanup must not destroy historical usability.

### Recovery
- Local save is not backup.
- Cleanup and retention must remain compatible with complete recovery.
- No destructive migration fallback over real data.

---

## 39. Versioning this process standard

When this document changes in the repository:

- bump the process version;
- record only meaningful process changes;
- avoid duplicating the whole document in changelogs;
- keep `AGENTS.md` concise and point to this file;
- keep the delegation document broad and point to this file;
- preserve old task/milestone evidence as history rather than rewriting it to match new process rules retroactively.

---

## 40. Closing rule

V16 Service’s AI workflow succeeds when:

- Chat resolves meaning;
- prompts remove ambiguity;
- Codex executes substantial work autonomously;
- ordinary failures are repaired rather than escalated;
- business invariants survive;
- evidence is classified truthfully;
- independent review catches omissions;
- corrections are consolidated;
- owner involvement is reserved for consequential decisions;
- repo/docs preserve durable truth;
- a fresh thread can continue without Felix rebuilding context.

The practical standard is:

> **Preserve the accepted product. Verify current state. Decide routine architecture before delegation. Write for Luna High. Name the real code and data boundaries. Test the actual bug. Require truthful runtime evidence. Treat commits as checkpoints, not stopping points. Independently review the result.**
