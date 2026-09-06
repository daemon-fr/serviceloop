# ServiceLoop — Source of Truth

**Status:** Adopted for implementation on 2026-09-06.

## Authority order

1. Explicit owner decisions and approved amendments recorded in `docs/project/BASELINE_DECISIONS.md`.
2. `docs/product/ServiceLoop_Conceptual_App_Map_v0_1.md` — adopted semantic/functional baseline.
3. `docs/product/ServiceLoop_Conceptual_UI_UX_Design_v0_1.md` — adopted UI/UX baseline, including the adopted FC-01/FC-02/FC-03 additions.
4. `docs/product/ServiceLoop_UI_UX_Action_Coverage_v0_1.md` — adopted traceability/coverage companion to the UI/UX baseline.
5. Current code, tests, and verification evidence establish what is actually implemented; divergence from the documents is not automatically a new requirement.
6. `docs/reference/ServiceLoop_Complete_Functional_App_Map_v0_1.md`, research/context, and the historical functional-map prompt are reference/rationale only unless a later owner amendment explicitly adopts a named policy from them.

Do not silently merge competing policies from the conceptual and complete functional maps. When a conflict is not resolved by the adopted baseline/amendments, stop only if it materially blocks the authorized work and bring one recommended resolution to the owner.

Matching HTML editions are reading formats and are intentionally not retained in the repository as independent requirements.
