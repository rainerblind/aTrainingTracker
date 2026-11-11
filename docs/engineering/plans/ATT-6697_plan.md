# Stage 3: Implementation Plan - ATT-6697: UI & Activities Layer Repository Encapsulation (Phase A Hardening)

**Ticket**: [ATT-6697](https://rainerblind.atlassian.net/browse/ATT-6697)  
**Sub-task**: `[Impl-Plan]` (Created on submission)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-46.2`  
**Requirement Mapping**: `REQ-UI-510` | **Test Mapping**: `TST-UI-510`  
**Branch**: `feature/ATT-6697` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
Phase A (`ATT-432`) eliminated direct database manager instantiations in four UI entry points. However, 8 residual call sites across `ui/` and `activities/` still instantiate SQLite managers via `*DatabaseManager.getInstance(...)` or `*DbHelper.getInstance(...)` directly. This plan routes all remaining presentation queries through domain repositories and enforces package-wide cleanliness via `RepositoryUiCleanlinessContractTest` (`docs/coding_standards.md` §2.5).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-510` (*Presentation Layer Repository Encapsulation & Boundary Enforcement*)
  * `REQ-UI-510.1`: Presentation layer sweep across 8 (+1) call sites.
  * `REQ-UI-510.2`: Domain repository delegation methods on `Dispatchers.IO`.
  * `REQ-UI-510.3`: Package-wide AST contract test across `ui/` and `activities/`.
* **Test Mapping**: `TST-UI-510` (`TST-UI-510.1` package AST, `TST-UI-510.2` targeted delegation, `TST-UI-510.3` ViewModel unit tests, `TST-UI-510.4` Tier 1 regression suite).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Behavioral / Schema Drift**: All SQLite queries, table constraints, and metrics remain 100% identical.
2. **Thread Safety**: All repository operations remain dispatched on `Dispatchers.IO` / designated single-thread workers.
3. **UI Consistency**: No UI changes (pure architectural presentation decoupling).
4. **Code Lock & Governance**: Production code lock enforced until Gate 3 PASS (`Rule 4`).

---

## 4. Proposed Architectural Changes
* **Repository Query Expansion**:
  * `KnownLocationsRepository`: Expose `upsertLocationByGeofence(...)`, `getHomeLocation()`, and `getAllLocations()`.
  * `WorkoutRepository`: Expose `getWorkoutTrainingLoadRecords()`, `getCardioPowerDynamicsFitnessTrendSamples(minR2)`, and `getAllWorkoutStartLocations()`.
  * `SegmentsRepository`: Expose `allSegmentSummaries: List<SegmentSummary>`.
  * `DeviceDataRepository`: Expose `getActiveDeviceIdsForWorkout(workoutId: Long)`.
* **Presentation Layer Refactoring**:
  * Inject/use domain repositories in `WorkoutClustersViewModel`, `TrackingTabsViewModel`, `AthleteProfileViewModel`, `StravaActivitySection`, `ExportStatusDataProvider`, `WorkoutDataMapper`, `MapInitialLocationResolver`, `ImportBackupTabsScreen`, `MainActivityWithNavigation`, and `EditWorkoutViewModel`.
* **Package-Wide AST Boundary Guard**:
  * `RepositoryUiCleanlinessContractTest`: Recursive directory walk of `ui/` and `activities/` verifying zero `*DatabaseManager.getInstance(...)` and `*DbHelper.getInstance(...)` calls (excluding `*Repository.kt` and `MapFragmentWithTrackViewModel.kt` allowlisted for `ATT-6692`).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Repository Delegates)**: Add delegates to `KnownLocationsRepository.kt`, `WorkoutRepository.kt`, `SegmentsRepository.kt`, and `DeviceDataRepository.kt`.
2. **Step 2 (ViewModel Decoupling)**: Refactor `WorkoutClustersViewModel.kt`, `TrackingTabsViewModel.kt`, and `AthleteProfileViewModel.kt` to call repository delegates.
3. **Step 3 (Map & Exporter Decoupling)**: Refactor `MapInitialLocationResolver.kt`, `ExportStatusDataProvider.kt`, `StravaActivitySection.kt`, and `WorkoutDataMapper.kt`.
4. **Step 4 (Activity & Screen Decoupling)**: Refactor `MainActivityWithNavigation.kt` (`devRepo.getActiveDeviceIdsForWorkout`), `ImportBackupTabsScreen.kt` (`EquipmentRepository`), and `EditWorkoutViewModel.kt` (remove redundant manager).
5. **Step 5 (Contract Test Upgrade)**: Implement `testPackageWideUiAndActivities_zeroDatabaseManagerGetInstance` in `RepositoryUiCleanlinessContractTest.kt`.
6. **Step 6 (Verification)**: Run targeted suite:
   `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.RepositoryUiCleanlinessContractTest" --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.activities.*" --tests "com.atrainingtracker.trainingtracker.build.*" --max-workers=2 && python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: AST cleanliness contract test + targeted unit and ViewModel regression suites.
* **Rollback**: Atomic revert of `feature/ATT-6697` leaves `sprint/2026-46.2` unaffected.
