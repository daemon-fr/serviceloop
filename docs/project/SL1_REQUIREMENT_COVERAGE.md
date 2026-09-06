# SL-1 Requirement Coverage

**Branch:** `codex/sl-1-foundation-visual-proof`
**Starting revision:** `33d350fbd6e46161616b630d766f46c4cf3b8dd7`
**Implementation commit:** `e92af2b8b715f5adcf97ddc31e9db1392470a52f`

| SL-1 responsibility | Status | Evidence |
|---|---|---|
| Proven Android toolchain and exact `gradle-9.5.0-bin.zip` wrapper | DEVELOPER-VALIDATED | Debug/release APKs, unit tests, and lint pass using the wrapper. |
| Stable Customer → Site → Equipment → ServicePlan graph | IMPLEMENTED / TESTED | Room entities, restrictive foreign keys, generated references separate from primary keys; `customerSiteEquipmentAndPlansPersistWithIndependentStableIdentity`. |
| One persisted current-obligation identity per plan | IMPLEMENTED / TESTED | `ServicePlanEntity.currentObligationId` uniquely points at the intended `ServiceObligationEntity`; WorkItem captures that exact ID; reload/identity test. Full consume/advance is deferred. |
| Durable working visit/work item | IMPLEMENTED / TESTED | Room visit, work item, public/private draft, and response rows; reconstruction and emulator force-stop/reopen evidence. |
| Historical/captured semantics | IMPLEMENTED / TESTED | Captured visit, equipment/service, due/interval, template revision, item label/type/unit/requiredness; master rename test proves snapshots do not refresh silently. |
| Attachment ownership foundation | IMPLEMENTED / TESTED | Stable metadata ID, owner type/ID, app-relative owned path, hash, availability/report-selection; no external URI or display name as identity. Byte intake is deferred. |
| Room schema/migration posture | IMPLEMENTED | Exported version-1 JSON committed; no destructive fallback or empty-on-open-failure behavior. Later migrations are deferred. |
| OS backup boundary | SOURCE-REVIEWED / IMPLEMENTED | Cloud/device-transfer rules exclude database and owned attachment/report paths; no OEM/OS completeness promise. Explicit backup package is deferred. |
| Debug-only representative fixtures | IMPLEMENTED / TESTED | Deterministic fixture seeder exists only in `src/debug`; `src/release` is no-op; source-boundary test and release assembly pass. |
| ServiceLoop shell and visual language | IMPLEMENTED / RUNTIME-VALIDATED | Home/Work/Customers, contextual detail navigation, adopted color/status roles, typography/spacing, native controls, custom launcher; compact emulator inspection. |
| Home and Equipment detail | IMPLEMENTED / RUNTIME-VALIDATED | Persisted counts/visits/follow-up/equipment rows; per-plan due and obligation rows; emulator navigation/screens. |
| Working Inspection | IMPLEMENTED / RUNTIME-VALIDATED | Status/Text/Number, explicit Not checked/NA, public/private labels, finding affordance, Saving/Saved/Failed model; persisted answer survives force-stop/reopen. |
| Completion Review semantic proof | IMPLEMENTED / RUNTIME-VALIDATED | Per-line Outcome and separate fulfillment state; due preservation/proposal; report placement; Finalize disabled and non-operative. |
| Truthful save failure | IMPLEMENTED / TESTED | Controlled write gate and ViewModel failure test prove no optimistic Saved state or persisted answer. Cancellation is explicitly rethrown. |
| Stage 2 boundary | DEFERRED | No finalization transaction, obligation consumption, next-obligation creation, finalized record, PDF, or success handoff exists. |

Automated evidence: 10 JVM/Robolectric tests, 0 failures/errors/skips; `testDebugUnitTest`, `assembleDebug`, `lintDebug`, and `assembleRelease` pass. Runtime evidence used adb 37.0.1 with explicit serial `emulator-5554` at `sw411dp`.
