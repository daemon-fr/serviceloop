# B-019 — ServiceLoop UI/UX Reference v1.0 and single-branch B-013 execution

**Owner decision:** 2026-09-09  
**Applies to:** B-013 whole-product UI/UX overhaul

This file records the explicit owner amendment for the in-progress B-013 working branch. It is intended to be consolidated into `BASELINE_DECISIONS.md` when B-013 is finally accepted and folded into `master`.

## Decision

1. The owner-supplied **ServiceLoop UI/UX Reference v1.0** package is adopted as the canonical **presentation implementation authority** for B-013.
2. `ServiceLoop_UI_UX_Implementation_Reference_v1_0.md` is the canonical manual. `ServiceLoop_UI_Tokens_v1_0.json` and `ServiceLoop_UI_Coverage_v1_0.csv` are normative mechanical companions. Audit/manifest files define evidence/package boundaries. The HTML edition is a reading aid, and the four PNG mockups define visual character rather than an authoritative px→dp scale.
3. Explicit owner/business semantics in the existing baseline continue to outrank visual emphasis. The UI reference does not silently authorize business-rule, persistence, Dispatch, reminder, Calendar, recovery or toolchain changes beyond adopted requirements.
4. Named reference discrepancies **E03–E09** must be checked against actual source and truthfully reconciled before UI parity is claimed. They cannot be hidden by styling, silently waived by previous functional-freeze wording, or expanded into unrelated new product scope.
5. B-013 is implemented on one long-lived branch:

   `codex/b013-ui-overhaul`

   No B-013 sub-branches are to be created. Stage A, all Stage B screen-family migrations, Stage C adaptation/accessibility work, final consistency, and owner-review corrections remain on that same branch using coherent commits/checkpoints.
6. `master` remains the accepted authoritative product baseline while B-013 is in progress. The B-013 working branch is not folded into `master` until the complete UI overhaul is finished, independently reviewed and explicitly accepted by the owner.
7. Localization remains deferred under B-017. B-013 freezes the final English UI/copy structure first; Romanian localization/final-copy implementation follows only after the UI branch is accepted into `master`.

See `docs/project/B013_UI_IMPLEMENTATION_PLAN.md` for execution detail and `docs/ui-reference/v1.0/README.md` for package intake/hash requirements.
