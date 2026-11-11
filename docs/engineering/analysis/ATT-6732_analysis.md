# Stage 1 Analysis: ATT-6732 - Replace hallucinated method/DTO signatures and Retrofit claim in interfaces.md with real source contracts

**Ticket**: [ATT-6732](https://rainerblind.atlassian.net/browse/ATT-6732)  
**Sub-task**: [ATT-6884](https://rainerblind.atlassian.net/browse/ATT-6884) (`[Analysis]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.2`  
**Branch**: `feature/ATT-6732`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
During the Sprint 2026-46.1 code-grounding audit of ATT-6180 (*Specify software interfaces in architecture*, related to ATT-6727), automated symbol and method verification against `app/src/main/` exposed significant LLM hallucination and specification drift in `docs/architecture/interfaces.md` and Section 5 of `docs/architecture.md`:
1. **9 of 19 Documented Method Signatures Do Not Exist in `app/src/main/`**:
   - `IF-SWE-02`: `getWorkout(workoutId: Long): Workout?` and `observeTelemetry()` (actual `WorkoutRepository` uses `WorkoutData`, `workouts: StateFlow<List<WorkoutData>>`, `getWorkoutById`, etc.).
   - `IF-SWE-05`: `findOrCreateCluster(track: Polyline): ClusterResult` (actual `WorkoutClusterEngine` exposes `suggestCluster(latLngs: List<LatLng>, ...)`, `findApexFromPoints(...)`).
   - `IF-SWE-06`: `insertSamplesBatch(samples: List<SampleRecord>)` and `updateWorkoutSummary(summary: WorkoutSummary)` (actual `WorkoutSamplesDatabaseManager` and `WorkoutRepository` use `recordLiveSample(...)`, `updateWorkoutData(...)`).
   - `IF-SWE-07`: `writeFitFile(...)`, `writeTcxFile(...)`, `writeGpxFile(...)` (actual file exporters extend `BaseExporter` / `BaseFileWriter` with `export(exportInfo: ExportInfo)` / `exportWorkoutTo(...)`).
   - `IF-SWE-08`: `syncHealthConnect(...)` (actual `HealthConnectManager` exposes `syncWorkout(context: Context, workoutData: WorkoutData)`).
2. **7 Non-Existent Class/DTO Identifiers & False `Retrofit` Dependency Claim**:
   - `ClustersDatabaseManager` (actual: `WorkoutClusterDatabaseManager`), `AftermathViewModel`, `TrackingUiState`, `AftermathUiState`, `LocationUiState`, `ExtremaValues`, and `WorkoutDetailScreen` do not exist in `app/src/main/`.
   - Both `docs/architecture/interfaces.md` (lines 20, 145) and `docs/architecture.md` (line 251) claim `REST (OkHttp/Retrofit)`, whereas `Retrofit` is not in `app/build.gradle`.
3. **Structural-Only Contract Test**:
   - `AspiceSweAuditContractTest.testArchitectureDocument_softwareInterfaceSpecifications()` only verifies headings and `IF-SWE-01..08` token presence without validating that documented method names and types resolve to real declarations in `app/src/main/`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Code-Grounding Audit**:
  - *IF-SWE-01*: Verified real UI states in `TrackingViewModel` are `TrackingScreenState`, `TrackingMapState`, and `AftermathMapUIState` in `TrackOnMapAftermathViewModel`. Real screens are `TrackingTabsScreen`, `TrackOnMapScreen`, `WorkoutSummariesTabbedScreen`. Real action methods include `updateScreenMode(mode: ScreenMode)`, `onStartTracking()`, `onPauseTracking()`.
  - *IF-SWE-02*: Verified repository operations return `WorkoutData?` and `Flow<WorkoutData?>`. Hot telemetry updates use `allFilteredSensorData: StateFlow<List<FilteredSensorData<*>>>` on `BANALServiceRepository`. Extrema operations use `ExtremaUpdate` and `ExtremaType`.
  - *IF-SWE-05*: Verified domain engines: `AutoPauseManager.evaluateTick(...)` returns `AutoPauseTransition`; `PowerPhysiologyCalculator.calculateNormalizedPower(...)` computes Coggan NP; `PowerHrDynamicsCalculator.calculate(...)` produces `CardioPowerDynamicsRecord?`; `WorkoutClusterEngine.suggestCluster(...)` and `findApexFromPoints(...)` perform spatial route analysis.
  - *IF-SWE-06*: Verified persistence layer: `WorkoutClusterDatabaseManager`, `WorkoutSamplesDatabaseManager` (`createNewTable`), `WorkoutSummariesDatabaseManager` (`updateWorkoutData`), and `WorkoutRepository.recordLiveSample(...)`.
  - *IF-SWE-07*: Verified exporter hierarchy: `FitFileWriter`, `TCXFileWriter`, `GPXFileWriter` extend `BaseFileWriter` / `BaseExporter` exposing `export(exportInfo: ExportInfo): ExportResult`.
  - *IF-SWE-08*: Verified cloud integration: `StravaUploader` (OkHttp REST multipart), `DropboxUploader` (BaseExporter), `HealthConnectManager.syncWorkout(...)`, and `HealthConnectHrvImporter.importRecentHrv(...)`.
  - *Retrofit Dependency Audit*: Inspected `app/build.gradle` and confirmed only `com.squareup.okhttp3:okhttp` is configured; Retrofit does not exist in any dependency configuration.

---

## 3. Scope Grounding & Non-Goals
* **In-Scope Goals**:
  - Align all 8 interface specifications (`IF-SWE-01` through `IF-SWE-08`) in `docs/architecture/interfaces.md` with 100% authentic production classes, DTOs, method signatures, threading models, and error handling.
  - Update Section 5 of `docs/architecture.md` to remove `Retrofit` and `AftermathViewModel`, replacing them with `REST (OkHttp) & HealthConnectClient` and `TrackOnMapAftermathViewModel`.
  - Expand `AspiceSweAuditContractTest.testArchitectureDocument_softwareInterfaceSpecifications()` to dynamically extract documented `fun <name>(` declarations and backticked component/DTO types from `docs/architecture/interfaces.md`, verifying their existence in `app/src/main/` against an allowlist of framework types (`OutputStream`, `LocationListener`, `ExerciseSessionRecord`, etc.).
  - Maintain 100% synchronization of living docs (`req_process_stability.md`, `tst_process_stability.md`).
* **Out-of-Scope Non-Goals**:
  - Introducing new runtime dependencies (e.g. Retrofit).
  - Refactoring production method signatures in `app/src/main/`.

---

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-044` (*ASPICE SWE.2 Software Interface Specification IF-SWE-01..IF-SWE-08*), refining `REQ-PRO-034` (*SWE.1/2/4/6 Hardening*) under Epic `ATT-6142`. Targets `docs/architecture/interfaces.md`, `docs/architecture.md` Section 5, and `AspiceSweAuditContractTest.kt`.
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-46.1 under ticket ATT-6180 to address ASPICE pre-assessment finding C-06 (*absent software interface definitions for inter-subsystem boundaries*).
* **Root Reason for Existing Formulation**: ATT-6180 drafted initial specifications based on conceptual abstractions rather than direct AST extraction from live sources, inadvertently generating synthetic method names (`findOrCreateCluster`, `writeFitFile`), placeholder DTOs (`WorkoutDetailScreen`, `ClustersDatabaseManager`), and assuming a standard Android Retrofit stack.
* **Preservation of Core Invariants**: Correcting interface specifications preserves all SWE.2 architectural boundaries, Mermaid diagrams, subsystem allocations, and 100% test pass rate while eliminating specification drift.

---

## 5. System Invariants & Risk Assessment
* **Core Invariants**:
  - Architecture documents must strictly reflect production reality (Code is the Single Source of Truth).
  - Zero modification to production runtime bytecode or behavioral regressions in `app/src/main/`.
  - All unit and contract tests in `./gradlew testDebugUnitTest` must pass cleanly.
* **Risk Assessment**:
  - *Risk*: Dynamic contract test over-matching formatting or Markdown syntax in `interfaces.md`.
  - *Mitigation*: Design precise regex extraction targeting `fun <name>(` signatures and specific interface backticked symbols, validated against indexed class declarations in `app/src/main/`.
