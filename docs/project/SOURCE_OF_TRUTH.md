# ServiceLoop — Source of Truth

**Status:** Adopted for implementation on 2026-09-06; current through 2026-09-09.

## Authority order

1. Explicit owner decisions and approved amendments recorded in `docs/project/BASELINE_DECISIONS.md`.
2. `docs/product/ServiceLoop_Conceptual_App_Map_v0_1.md` — adopted semantic/functional baseline.
3. `docs/product/ServiceLoop_Conceptual_UI_UX_Design_v0_1.md` — adopted UI/UX baseline, including the adopted FC-01/FC-02/FC-03 additions.
4. `docs/product/ServiceLoop_UI_UX_Action_Coverage_v0_1.md` — adopted traceability/coverage companion to the UI/UX baseline.
5. Current code, tests, and verification evidence establish what is actually implemented; divergence from the documents is not automatically a new requirement.
6. `docs/reference/ServiceLoop_Complete_Functional_App_Map_v0_1.md`, research/context, and the historical functional-map prompt are reference/rationale only unless a later owner amendment explicitly adopts a named policy from them.

Do not silently merge competing policies from the conceptual and complete functional maps. When a conflict is not resolved by the adopted baseline/amendments, stop only if it materially blocks the authorized work and bring one recommended resolution to the owner.

Matching HTML editions are reading formats and are intentionally not retained in the repository as independent requirements.

## Experimental material

`docs/internal/DISPATCH_PACKAGES_PROTOTYPE.md` describes the separately authorized asynchronous Dispatch experiment. Under B-012 it is intentionally **non-authoritative product material** until the owner explicitly adopts Dispatch into the product baseline. Prototype code/evidence establishes what the experiment currently does, but does not silently amend Map C or the adopted UI/UX maps.

## Current accepted implementation baseline

- **SL-1:** owner accepted.
- **SL-2:** owner accepted on 2026-09-06. The accepted user-facing implementation/review state is `d97a8c0013dcea924d91ace993a1325ac16cf5b3`; subsequent documentation-only commits bank that acceptance and deferred UX direction without changing product behavior.
- **SL-3:** owner accepted on 2026-09-08. The accepted production implementation/review state is `ad078faae3c73faa2a9bb02dd1251b1033237399`.
- **SL-4:** implementation and bounded independent Phase-B verification are complete on `codex/sl-4-history-recovery` at `5fc7a7383d6b6ad15cd45dce9ea5926a6ef0ac84`. SL-4 is still **not owner accepted** and no broader system-handoff/lifecycle campaign is implied beyond the recorded verification evidence.
- **Dispatch prototype v2:** technically implemented and source-reviewed through `prototype/dispatch-v2` commit `cdca7c460bb7271654c5ed3da4e9c95e11688eb3`, including batch package workflow and coordinator Draft/Dispatched/Concluded bookkeeping. This is accepted as an **experimental prototype checkpoint suitable for owner/rendered evaluation**, not as product-baseline adoption and not as merge approval.
- **B-008 pilot:** still outstanding. The product is not product-valid for release preparation until a pilot-ready Romanian-localized build with no known ordinary-workflow placeholders is evaluated by at least one real technician/trade user.

## Current forward sequence

1. Owner/rendered evaluation of Dispatch and explicit product-adoption decision.
2. Formal owner closure/banking of SL-4 and, if adopted, deliberate integration of Dispatch into the real development line.
3. Stage 5 functional product completion/hardening: localization, reminders/notifications, remaining ordinary-workflow cleanup, regression/recovery hardening, and other explicitly adopted functional integrations.
4. Dedicated whole-product UI/UX milestone under B-013.
5. B-008 real-technician pilot on the coherent Romanian pilot-ready build.
6. Pilot findings/final hardening.
7. Release preparation and submission.

Read `IMPLEMENTATION_STATE.md` for the current implementation summary, experimental status, verification boundaries, and planned functional work. Read milestone coverage files for detailed accepted/verified evidence.
