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

## Dispatch adoption and integrated development line

B-015 adopts the completed asynchronous file-based Dispatch design for ServiceLoop product integration after owner hands-on review. The adopted design remains strictly local-first/file-based and does not authorize a backend, accounts/login, live/cloud synchronization, push dispatch, chat, presence, a shared central database, centralized report ingestion, server acknowledgement, or automatic cross-device conflict resolution.

The integration/banking milestone is complete on:

- branch: `codex/integrate-sl4-dispatch`
- integrated technical checkpoint: `132a0586969dfe7e7fbe88501ba6f413ce6ef724`
- Room schema: v10

This integrated branch is now the authoritative technical development starting point for Stage 5. `master` remains an older protected reference and is not the current implementation authority.

`docs/internal/DISPATCH_PACKAGES_PROTOTYPE.md` remains the detailed implementation/verification reference for adopted Dispatch semantics, despite its historical filename.

## Current accepted / verified implementation state

- **SL-1:** owner accepted.
- **SL-2:** owner accepted on 2026-09-06. Accepted user-facing implementation/review state: `d97a8c0013dcea924d91ace993a1325ac16cf5b3`.
- **SL-3:** owner accepted on 2026-09-08. Accepted production implementation/review state: `ad078faae3c73faa2a9bb02dd1251b1033237399`.
- **SL-4:** the complete History/recovery implementation is independently Phase-B verified and is now **VERIFIED AND BANKED INTO THE INTEGRATED DEVELOPMENT LINE** at `132a0586969dfe7e7fbe88501ba6f413ce6ef724`. This does not retroactively claim a separate earlier standalone owner-acceptance event for SL-4.
- **Dispatch:** owner approved under B-015 and deliberately integrated/banked into the same authoritative technical line at `132a0586969dfe7e7fbe88501ba6f413ce6ef724`.
- **B-014 durable Working inspection-response drafts:** adopted core behavior and integrated in Room v10.
- **B-008 pilot:** still outstanding. The product is not product-valid for release preparation until a pilot-ready Romanian-localized build with no known ordinary-workflow placeholders is evaluated by at least one real technician/trade user.

## Integration verification boundary

The integration milestone deliberately reconciled the Dispatch/Room-v10 line with the final unique SL-4 verification intent rather than blindly merging/cherry-picking old milestone history. The shared merge base was `8052cdca8a1a87806d371347a99aeabae250a1b4`; no missing SL-4 production implementation was found.

The integrated checkpoint verified, among other evidence:

- coherent retained Room v1→v10 migration coverage;
- representative populated v5→v10 and v6→v10 preservation with zero `PRAGMA foreign_key_check` violations;
- integrated SL-4 + Dispatch + B-014 replacement backup/restore;
- Dispatch/Working-response dirty tracking and erase semantics in isolated fixtures;
- active-response-only final record/report content;
- ordinary non-Dispatch and adopted Dispatch regression;
- canonical non-destructive AVD smoke/history/report navigation.

Two focused integration production corrections were made in `RecoveryPackage.kt`: new backups now identify schema v10 while retaining schema-v9 compatibility, and backup-triggered Technician identity initialization uses the canonical `SLT-XXXX-XXXX-XXXX-CC` format rather than creating new legacy 32-hex IDs.

## Current forward sequence

1. **Stage 5 — functional product completion/hardening**
   - Romanian localization and report/UI copy;
   - reminders/notifications;
   - remaining ordinary-workflow cleanup and placeholder removal;
   - time-aware Home/Work recomputation at relevant date/time boundaries and app resume rather than polling;
   - design/implement Calendar integration after final semantics are adopted;
   - larger-data, recovery and platform hardening;
   - production hardening of adopted Dispatch without expanding its no-backend boundary.

2. **Dedicated whole-product UI/UX milestone — B-013**
   - one coherent design system and interaction/presentation pass across ServiceLoop;
   - reusable components/primitives, hierarchy, typography, spacing/density, semantic colors, light/dark themes, loading/empty/error states, accessibility, contrast and touch targets;
   - the current semi-default/incremental Compose presentation is not the final visual baseline.

3. **B-008 real-technician pilot** on the coherent Romanian pilot-ready build.
4. **Pilot findings / final hardening.**
5. **Release preparation and submission.**

Read `IMPLEMENTATION_STATE.md` for the concise current implementation summary and remaining known gaps. Read milestone coverage files and the Dispatch implementation reference for detailed verification evidence.
