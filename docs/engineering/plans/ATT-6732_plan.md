# Stage 3: Implementation Plan - ATT-6732: Replace hallucinated method/DTO signatures and Retrofit claim in interfaces.md with real source contracts

**Ticket**: [ATT-6732](https://rainerblind.atlassian.net/browse/ATT-6732)  
**Sub-task**: [ATT-6890](https://rainerblind.atlassian.net/browse/ATT-6890) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.2`  
**Requirement Mapping**: `REQ-PRO-044` (*ASPICE SWE.2 Software Interface Specification IF-SWE-01..IF-SWE-08*)  
**Test Mapping**: `TST-PRO-044` (*ASPICE SWE.2 Interface Conformance & Code Grounding Contract Suite*)  
**Branch**: `feature/ATT-6732`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background
Sprint 2026-46.1 review identified severe specification drift and LLM hallucination in `docs/architecture/interfaces.md`: 9 non-existent method signatures across IF-SWE-02..08, 7 hallucinated component/DTO types (`ClustersDatabaseManager`, `AftermathViewModel`, `TrackingUiState`, `AftermathUiState`, `LocationUiState`, `ExtremaValues`, `WorkoutDetailScreen`), and false `Retrofit` claims (not in `app/build.gradle`). Furthermore, `AspiceSweAuditContractTest.kt` checked only headers without dynamic source code grounding.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-044` (`REQ-PRO-044.1` grounded `interfaces.md`, `REQ-PRO-044.2` Section 5 alignment & Retrofit removal, `REQ-PRO-044.3` dynamic source grounding contract test)
* **Test Mapping**: `TST-PRO-044` (`TST-PRO-044.1..3` structure & matrix, `TST-PRO-044.4` dynamic code grounding, `TST-PRO-044.5` governance parity)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime Code Modification**: Changes are strictly confined to architecture documentation, requirement living docs, and contract tests in `app/src/test/`.
2. **Single Source of Truth**: Production Kotlin/Java sources in `app/src/main/` define canonical contracts.
3. **Continuous SWE.2 Parity**: All 8 interface boundaries (`IF-SWE-01`..`IF-SWE-08`), subsystem matrix mappings, and 100% test pass rate remain fully preserved.

---

## 4. Proposed Architectural Changes
### Component 1: `docs/architecture/interfaces.md`
* Correct `IF-SWE-01`: Replace hallucinated states and screens with `TrackingScreenState`, `TrackingMapState`, `AftermathMapUIState`, `TrackingTabsScreen`, `TrackOnMapScreen`, `WorkoutSummariesTabbedScreen`. Document authentic methods (`updateScreenMode(mode: ScreenMode)`, `onStartTracking()`, `onPauseTracking()`).
* Correct `IF-SWE-02`: Replace `getWorkout(...)`, `saveWorkout(workout: Workout)`, `observeTelemetry()` with `loadWorkout(id: Long)`, `getWorkoutById(id: Long): Flow<WorkoutData?>`, `saveWorkout(userEditedWorkout: WorkoutData?)`, `updateExtremaValue(workoutId: Long, extremaUpdate: ExtremaUpdate)`. DTOs: `WorkoutData`, `LapData`, `ExtremaUpdate`, `ExtremaType`, `SportType`, `FilteredSensorData`.
* Correct `IF-SWE-03`: Align `BANALServiceComm` signatures (`getAllFilteredSensorData()`, `getBestSensorData()`, `getFilteredSensorData()`).
* Correct `IF-SWE-04`: Align HAL callbacks (`onCharacteristicChanged`, `onLocationChanged`).
* Correct `IF-SWE-05`: Align domain signatures in `AutoPauseManager` (`evaluateTick`), `PowerPhysiologyCalculator` (`calculateNormalizedPower`), `PowerHrDynamicsCalculator` (`calculate`), and `WorkoutClusterEngine` (`suggestCluster`, `findApexFromPoints`).
* Correct `IF-SWE-06`: Replace `ClustersDatabaseManager` with `WorkoutClusterDatabaseManager`. Document authentic signatures (`recordLiveSample`, `updateWorkoutData`, `insertCluster`, `createNewTable`).
* Correct `IF-SWE-07`: Document `BaseFileWriter` / `BaseExporter` hierarchy: `export(exportInfo: ExportInfo): ExportResult` on `FitFileWriter`, `TCXFileWriter`, `GPXFileWriter`, and `exportWorkoutTo(workoutId: Long, fileFormat: FileFormat)` on `WorkoutRepository`.
* Correct `IF-SWE-08`: Remove `Retrofit`. Document `export(exportInfo: ExportInfo): ExportResult` on `StravaUploader` (OkHttp) and `DropboxUploader` (BaseExporter); `syncWorkout(...)` on `HealthConnectManager`; `importRecentHrv(...)` on `HealthConnectHrvImporter`.

### Component 2: `docs/architecture.md` Section 5
* Update Section 5.1 table: replace `AftermathViewModel` with `TrackOnMapAftermathViewModel` and `REST (OkHttp/Retrofit)` with `REST (OkHttp) & HealthConnectClient`.

### Component 3: `AspiceSweAuditContractTest.kt`
* Add `testArchitectureDocument_softwareInterfaceGrounding()`:
  - Dynamically extracts all `fun <name>(` declarations from `interfaces.md` and verifies each exists as a method declaration in `app/src/main/`.
  - Dynamically extracts backticked component/DTO types in Provider/Consumer/Payload lines and verifies each exists in `app/src/main/` (allowing standard Android/JDK framework types).
  - Asserts negative tripwires: `Retrofit`, `ClustersDatabaseManager`, `AftermathViewModel`, `TrackingUiState`, `AftermathUiState`, `LocationUiState`, `ExtremaValues`, and `WorkoutDetailScreen` must NOT exist in `interfaces.md`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Gate 3 Check)**: Run `python3 tools/jira_util.py check-gate <IMPL_PLAN_KEY>`.
2. **Step 2 (Document Grounding)**: Update `docs/architecture/interfaces.md` and `docs/architecture.md` Section 5 with authentic signatures, types, and removal of Retrofit.
3. **Step 3 (Contract Test Hardening)**: Update `AspiceSweAuditContractTest.kt` with dynamic AST/regex method and type grounding assertions and negative tripwires.
4. **Step 4 (Test Execution & Governance)**: Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"` and `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: `AspiceSweAuditContractTest` passes with 100% success; `verify_requirement_governance.py` passes with 100% parity.
* **Rollback**: `git checkout -- .` to restore pristine state.
