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

## Current accepted implementation baseline

- **SL-1:** owner accepted.
- **SL-2:** owner accepted on 2026-09-06. The accepted user-facing implementation/review state is `d97a8c0013dcea924d91ace993a1325ac16cf5b3`; subsequent documentation-only commits bank that acceptance and deferred UX direction without changing product behavior.
- **SL-3:** owner accepted on 2026-09-08. The accepted production implementation/review state is `ad078faae3c73faa2a9bb02dd1251b1033237399`; subsequent acceptance-documentation commits do not change product behavior.
- **B-008 pilot:** still outstanding. SL-3 acceptance authorizes continued development, but the product is not yet product-valid evidence for release preparation until a reachable real technician/trade evaluates the first complete service loop/report.
- **Next milestone:** Stage 4 / SL-4 — Complete history and recovery.

Read `IMPLEMENTATION_STATE.md` for the current implementation summary and stage boundary, and the milestone coverage files for detailed evidence.
