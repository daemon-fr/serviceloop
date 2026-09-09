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

## Dispatch adoption status

B-015 adopts the completed asynchronous file-based Dispatch design for ServiceLoop product integration after owner hands-on review through `prototype/dispatch-v2` commit `813fed417330fc7d3e5e7bce68ced29edd6dfb23`.

`docs/internal/DISPATCH_PACKAGES_PROTOTYPE.md` remains the detailed implementation/verification reference for the accepted Dispatch design until the integration milestone banks that work onto the authoritative development line. It does not override B-015 or silently expand Dispatch beyond the adopted asynchronous/local-first boundary.

## Current accepted / verified implementation state

- **SL-1:** owner accepted.
- **SL-2:** owner accepted on 2026-09-06. Accepted user-facing implementation/review state: `d97a8c0013dcea924d91ace993a1325ac16cf5b3`.
- **SL-3:** owner accepted on 2026-09-08. Accepted production implementation/review state: `ad078faae3c73faa2a9bb02dd1251b1033237399`.
- **SL-4:** implementation and bounded independent Phase-B verification are complete on `codex/sl-4-history-recovery` at `5fc7a7383d6b6ad15cd45dce9ea5926a6ef0ac84`. SL-4 is technically verified but still awaits deliberate closure/banking onto the next authoritative development line.
- **Dispatch:** owner approved for product integration under B-015 at `prototype/dispatch-v2` commit `813fed417330fc7d3e5e7bce68ced29edd6dfb23`. That branch currently carries Room v10 because B-014's durable Working inspection-response drafts were implemented there alongside Dispatch. This does not make the prototype branch itself the authoritative production line.
- **B-008 pilot:** still outstanding. The product is not product-valid for release preparation until a pilot-ready Romanian-localized build with no known ordinary-workflow placeholders is evaluated by at least one real technician/trade user.

## Current forward sequence

1. **Integration / banking milestone**
   - deliberately reconcile the verified SL-4 line with the owner-approved Dispatch/Room-v10 work;
   - preserve all migration/recovery guarantees and B-014 Working-response draft semantics;
   - establish one authoritative development HEAD;
   - run full host, migration, recovery, build/lint/release and bounded device regression gates.

2. **Stage 5 — functional product completion/hardening**
   - Romanian localization and report/UI copy;
   - reminders/notifications;
   - remaining ordinary-workflow cleanup and placeholder removal;
   - time-aware Home/Work recomputation at relevant date/time boundaries and app resume rather than polling;
   - design/implement Calendar integration if adopted in its final semantics;
   - larger-data, recovery and platform hardening;
   - production hardening of adopted Dispatch without expanding its no-backend boundary.

3. **Dedicated whole-product UI/UX milestone — B-013**
   - one coherent design system and interaction/presentation pass across ServiceLoop;
   - reusable components/primitives, hierarchy, typography, spacing/density, semantic colors, light/dark themes, loading/empty/error states, accessibility, contrast and touch targets;
   - the current prototype/semi-default visual treatment is not the final visual baseline.

4. **B-008 real-technician pilot** on the coherent Romanian pilot-ready build.
5. **Pilot findings / final hardening.**
6. **Release preparation and submission.**

Read `IMPLEMENTATION_STATE.md` for the concise current implementation summary and remaining known gaps. Read milestone coverage files and the Dispatch implementation reference for detailed verification evidence.
