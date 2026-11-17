# Stage 1 Analysis: ATT-8160 - Architecture & Interface Documentation Drift

**Ticket**: [ATT-8160](https://issue-tracker/ATT-8160) | **Parent Epic**: [ATT-8138](https://issue-tracker/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)
**Branch**: `feature/ATT-8160` | **Sprint**: `sprint/2026-11-17_01_Früh` | **Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-17

## 1. Problem Statement & Motivation
ASPICE 4.0 Audit Finding `Minor (SWE.2)` (`SWE.2 BP1/BP2/BP4`): `docs/architecture.md` and `docs/architecture/interfaces.md` drifted from live `app/src/main/` code. Existing `AspiceSweAuditContractTest.testArchitectureDocument_softwareInterfaceGrounding` only regex-checks method *names* (`fun <name>(`), missing parameter counts, types, and nullability, while `ArchitectureConformanceContractTest` hardcodes brittle `TrackerService.java:842-857` line numbers.

## 2. Root Cause Analysis & Independent Drift Inventory
Audited `docs/architecture/interfaces.md` (159 lines), `docs/architecture.md` (392 lines), `app/src/main/`, and contract tests:
* **`docs/architecture/interfaces.md` Signature Drifts**:
  * `IF-SWE-02` `WorkoutRepository.updateExtremaValue`: doc `(workoutId: Long, extremaUpdate: ExtremaUpdate)` vs code `(workoutId: Long, sensorType: SensorType, extremaType: ExtremaType, value: Double, position: LatLng? = null)` (+ 6-param overload with `count: Int?`); `ExtremaUpdate` is `TrackerService.ExtremaUpdate` used in `recordLiveSample`.
  * `IF-SWE-03` `BANALServiceComm`: `getAllFilteredSensorData()` returns `List<FilteredSensorData<?>>` (doc omits `<?>`); `getBestSensorData(SensorType)` returns raw `SensorData` (doc writes `SensorData<?>`).
  * `IF-SWE-05` `AutoPauseManager.evaluateTick`: doc 2 params `(Double?, Long)` vs code 7 params `(Double?, Long, Long, BSportType, AutoPauseConfig, Boolean, Boolean = ...)`.
  * `IF-SWE-05` `PowerPhysiologyCalculator.calculateNormalizedPower`: doc `(List<Double>, Double): Double` vs code `(List<Double?>, Double = 1.0): Double?` (nullable elements & nullable return).
  * `IF-SWE-05` `PowerHrDynamicsCalculator.calculate`: doc 4 params `(List<Double>, List<Double>, Long, Double)` vs code 5 params `(List<Double?>?, List<Double?>?, Long = 0L, Double = 1.0, List<Long>? = null): CardioPowerDynamicsRecord?` (param `dt`, not `dtSec`).
  * `IF-SWE-05` `WorkoutClusterEngine.suggestCluster`: doc 5 params vs code 11 params (two `@JvmOverloads` methods taking `Set<BSportType>` or `BSportType` plus optional name/alt/cache/threshold/polyline).
  * `IF-SWE-05` `ClimbDetector.detectClimbs`: doc 1 param `(List<PathPoint>)` vs code 6 params with defaults `(List<PathPoint>, Double, Double, Double, Long?, Context?)`.
  * `IF-SWE-06` `WorkoutRepository.recordLiveSample`: doc 4 params `Unit` return vs code 8 params `(String, Long, ContentValues, ContentValues, List<TrackerService.ExtremaUpdate>, LiveWorkoutSession.StreamIncrement?, Map<String, SensorValueType>?, LiveSampleFailureCallback? = null): Boolean`.
  * `IF-SWE-08` `HealthConnectManager.syncWorkout`: doc 2 params vs code 3 params `(Context, WorkoutData, Boolean = false)`.
  * `IF-SWE-08` `HealthConnectHrvImporter.importRecentHrv`: doc 2 params vs code 4 params `(Context, Int = 14, HrvReadinessRepository = ..., HealthConnectClient? = null)`.
* **`docs/architecture.md` Drifts**:
  * **Line Citation Drift (L308)**: Cites `TrackerService.java:842-857` for `PARTIAL_WAKE_LOCK` (`aTrainingTracker:TrackingLock`); live `acquireWakeLock()` is at L1079-1092 and `releaseWakeLock()` at L1094-1102. Replace `:line` citation with symbol anchors (`TrackerService#acquireWakeLock` / `releaseWakeLock`).
  * **Allocation Table Missing Prefixes (L89)**: `Post-Workout Analytics & Periods` lists only `PER`, `PERF`; omits `SPT` (also `HRV`, `RAD`, `WKO` in `req_aftermath_periods.md`; `CLM`/`RAD` in `req_maps_navigation.md`; `RAD` in `req_data_migration_export.md`).
  * **ASPICE 3.1 vs 4.0 BP Label Mix**: `architecture.md` (L3, L7, L82, L100, L253, L297-298, L317-319) and `interfaces.md` (L1-3) mix ASPICE 3.1/4.0 (`v3.1 / v4.0`, `BP.1..BP.5`, `BP2` for interfaces, `BP3` for allocation, `BP4` for both dynamic behavior and budgets). Align to **ASPICE 4.0 SWE.2**: `BP1` Static & Interfaces, `BP2` Dynamic Behavior, `BP3` Analyze Architecture & Resource/Timing Budgets, `BP4` Consistency & Bidirectional Traceability/Allocation, `BP5` Communicate Agreed Architecture.
  * **Recent §3.5 / §5.4 Grounding**: §3.5 (`ATT-8159`, `detailed_design/*.md`, `DetailedDesignParityContractTest`) and §5.4 (`ATT-8579`, `sensor_units.md`, 67 `SensorType` entries, `SensorTypeUnitsContractTest`) are grounded. Note §3.3.3/§3.3.4 subheading numbering inside §3.

## 3. Scope Grounding & Non-Goals
* **In-Scope**: Correct all signature/type/nullability drifts in `docs/architecture/interfaces.md`; fix `TrackerService` symbol anchor, missing prefixes (`SPT` etc.), and ASPICE 4.0 SWE.2 BP labels in `docs/architecture.md`; update `ArchitectureConformanceContractTest` L352 brittle line check; add a **JVM-reflection-based** signature-drift contract test (`app/src/test/`) verifying documented `interfaces.md` method names, parameter counts/arity, parameter/return types & nullability against compiled `app/src/main/` classes; update `REQ-PRO-044`/`REQ-PRO-052`/`TST-PRO-044`/`TST-PRO-052` as needed.
* **Non-Goals**: Zero edits to `app/src/main/` or `ANT-Android-SDKs` (`0e8cea714a231fb9565170d4ed01eafa868f9798`). No `.readText()` on `app/src/main/` in new tests (`REQ-PRO-049` / `SourceTextReadingAuditContractTest`).

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-044` (`interfaces.md` & `AspiceSweAuditContractTest.kt`), `REQ-PRO-052` (`docs/architecture.md` §4.9 & `ArchitectureConformanceContractTest.kt`), `REQ-PRO-049` (`SourceTextReadingAuditContractTest.kt`).
* **Historical Origin & Commit Trace**: `ATT-6180`/`ATT-6732` (`REQ-PRO-044` added regex method-name checks); `0777baa6` (`ATT-6735`, `REQ-PRO-052` hardcoded `TrackerService.java:842-857` in both `architecture.md` and `ArchitectureConformanceContractTest.kt` L352); `ATT-6186` (`REQ-PRO-049` bans unallowlisted `src/main` file reading in tests).
* **Root Reason for Existing Formulation**: Regex-only name checks could not catch arity/nullability drift; line numbers were cited before `TrackerService.java` grew; source-text reading was prohibited to prevent brittle AST-free test coupling.
* **Preservation of Core Invariants**: Preserve all `AspiceSweAuditContractTest` Section 2/5/6 checks, all `ArchitectureConformanceContractTest` NFR/WakeLock/thread attributions (`PARTIAL_WAKE_LOCK`, `mScheduler`, `mDbExecutor`, `REQ-NFR-003..014`), `DetailedDesignParityContractTest` anchors, and `SourceTextReadingAuditContractTest` counts (`22` guards, `105` baseline).

## 5. Change Impact Analysis
* **Affected Requirements**: Amend `REQ-PRO-044` (signature/arity/nullability drift check) & `REQ-PRO-052` (symbol anchor replacing `:842-857`); add/extend `TST-PRO-044`/`TST-PRO-052` or net-new `REQ-PRO-*`/`TST-PRO-*` in `docs/requirements/req_process_stability.md` and `docs/tests/tst_process_stability.md`.
* **Affected Architecture Elements**: `docs/architecture.md`, `docs/architecture/interfaces.md`.
* **Verification Impact & Test Surface**: Update `ArchitectureConformanceContractTest.kt` (`testArchitectureDoc_definesResourceAndTimingBudgets`), add JVM-reflection `InterfaceSignatureDriftContractTest.kt` (inspecting compiled classes via `Class.forName` / `declaredMethods` / `@Nullable`/`@NotNull` without reading `src/main/` text), keep `AspiceSweAuditContractTest`, `DetailedDesignParityContractTest`, and `SourceTextReadingAuditContractTest` 100% green.
* **Risk Assessment & Invariants**: **LOW** — strictly docs, living-doc specs, and JVM-reflection contract tests; zero `app/src/main/` changes; `verify_requirement_governance.py` must stay green.
