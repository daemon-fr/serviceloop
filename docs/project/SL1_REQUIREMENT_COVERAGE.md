# SL-1 Requirement Coverage

**Branch:** `codex/sl-1-foundation-visual-proof`
**Starting revision:** `33d350fbd6e46161616b630d766f46c4cf3b8dd7`
**Implementation commit:** `e92af2b8b715f5adcf97ddc31e9db1392470a52f`
**Final technical closure implementation commit:** `2e9e074a0dcb1d1fd00484d9c3bb1a37fd8498db`

| SL-1 responsibility | Status | Evidence |
|---|---|---|
| Proven Android toolchain and exact `gradle-9.5.0-bin.zip` wrapper | DEVELOPER-VALIDATED | Debug/release APKs, unit tests, and lint pass using the wrapper. |
| Stable Customer → Site → Equipment → ServicePlan graph | IMPLEMENTED / TESTED | Room entities, restrictive foreign keys, generated references separate from primary keys; `customerSiteEquipmentAndPlansPersistWithIndependentStableIdentity`. |
| One persisted current-obligation identity per plan | FOUNDATION IMPLEMENTED / TESTED | `ServicePlanEntity.currentObligationId` stores the current-obligation pointer/identity and WorkItem captures an existing obligation through its foreign key; reload/identity test. Stage 2 must implement and verify the exact compare/consume/advance transaction. |
| Durable working visit/work item | IMPLEMENTED / TESTED | Room visit, work item, public/private draft, and response rows; reconstruction and emulator force-stop/reopen evidence. |
| Historical/captured semantics | IMPLEMENTED / TESTED | Captured visit, equipment/service, due/interval, template revision, item label/type/unit/requiredness; master rename test proves snapshots do not refresh silently. |
| Attachment ownership foundation | IMPLEMENTED / TESTED | Stable metadata ID, owner type/ID, app-relative owned path, hash, availability/report-selection; no external URI or display name as identity. Byte intake is deferred. |
| Room schema/migration posture | IMPLEMENTED | Exported version-1 JSON committed; no destructive fallback or empty-on-open-failure behavior. Later migrations are deferred. |
| OS backup boundary | SOURCE-REVIEWED / IMPLEMENTED | Cloud/device-transfer rules exclude database and owned attachment/report paths; no OEM/OS completeness promise. Explicit backup package is deferred. |
| Debug-only representative fixtures | IMPLEMENTED / TESTED | Deterministic fixture seeder exists only in `src/debug`; `src/release` is no-op; source-boundary test and release assembly pass. |
| ServiceLoop shell and visual language | IMPLEMENTED / RUNTIME-VALIDATED | Home/Work/Customers, contextual detail navigation, adopted color/status roles, typography/spacing, native controls, custom launcher; compact emulator inspection. |
| Home and Equipment detail | IMPLEMENTED / RUNTIME-VALIDATED | Persisted counts/visits/follow-up/equipment rows; per-plan due and obligation rows; emulator navigation/screens. |
| Working Inspection | IMPLEMENTED / TESTED / RUNTIME-VALIDATED | Status/Text/Number, explicit Not checked/NA, public/private labels, truthful save states, confirmation before destructive disposition changes, and repository-cleared incompatible fields. Same semantic NA/OK/VALUE requests are no-ops that preserve saved detail/checkpoints; changed VALUE and genuine NA transitions persist. Cancel/confirm, issue-reason, numeric-value and cold-reopen cases pass. |
| Finding affordance | FOUNDATION DESTINATION / RUNTIME-VALIDATED | Finding / issue details opens an explicitly deferred SL-1 destination and creates no finding or follow-up. The full editor remains deferred. |
| Completion Review semantic proof | IMPLEMENTED / TESTED / PARTIAL RUNTIME-VALIDATION | Eligibility requires a recurring plan, captured obligation, Performed outcome, and reviewed/no checklist. One-off work derives `NO_CURRENT_OBLIGATION`; stale true fulfillment is sanitized false and no recurring date is proposed. Recurring cases remain runtime-validated; no representative one-off runtime line exists, so that projection is repository-tested/source-reviewed. Finalize remains disabled/non-operative. |
| Technician-facing identity boundary | IMPLEMENTED / RUNTIME-VALIDATED | Equipment Detail shows plan reference/interval/due/status without displaying `currentObligationId`; internal persisted identities remain available for later integrity work. |
| Truthful save failure | IMPLEMENTED / TESTED | Controlled write gate and ViewModel failure test prove no optimistic Saved state or persisted answer. Cancellation is explicitly rethrown. |
| Stage 2 boundary | DEFERRED | No finalization transaction, obligation consumption, next-obligation creation, finalized record, PDF, or success handoff exists. |

Automated closure evidence: 23 JVM/Robolectric tests, 0 failures/errors/skips; `testDebugUnitTest`, `assembleDebug`, `lintDebug`, and `assembleRelease` pass. Targeted runtime evidence used adb 37.0.1 on canonical AVD `Pixel 10a ServiceLoop` (internal ID `Pixel_10a_ServiceLoop`), dynamically resolved as `emulator-5556` for this run with every device-directed command explicitly targeted. Already-selected NA reason/checkpoint preservation, genuine NA transition, cold-reopen persistence, and recurring Completion Review regression passed. One-off runtime was not run because the representative route has no one-off line; focused automated evidence covers it.
