# Stage 2: Requirement & Test Specification - ATT-6697: UI & Activities Layer Repository Encapsulation (Phase A Hardening)

**Ticket**: [ATT-6697](https://rainerblind.atlassian.net/browse/ATT-6697) | **Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-46.2` | **Branch**: `feature/ATT-6697`  
**Requirement Mapping**: `REQ-UI-510` | **Test Spec ID**: `TST-UI-510`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-UI-510)

### 1.1 Problem Statement & Rationale
Phase A (`ATT-432`) eliminated direct database manager instantiations in four UI entry points, but residual call sites remained in `ui/` and `activities/` (`WorkoutClustersViewModel`, `TrackingTabsViewModel`, `AthleteProfileViewModel`, `StravaActivitySection`, `ExportStatusDataProvider`, `WorkoutDataMapper`, `MapInitialLocationResolver`, `ImportBackupTabsScreen`, `MainActivityWithNavigation`, `EditWorkoutViewModel`). Presentation components must route all database queries and mutations through domain repositories, and `RepositoryUiCleanlinessContractTest` must enforce this package-wide (`docs/coding_standards.md` §2.5).

### 1.2 Functional & Architectural Requirements
* **REQ-UI-510.1** (*Presentation Layer DatabaseManager Sweep*): `WorkoutClustersViewModel.kt`, `TrackingTabsViewModel.kt`, `AthleteProfileViewModel.kt`, `StravaActivitySection.kt`, `ExportStatusDataProvider.kt`, `WorkoutDataMapper.kt`, `MapInitialLocationResolver.kt`, `ImportBackupTabsScreen.kt`, `MainActivityWithNavigation.kt`, and `EditWorkoutViewModel.kt` SHALL route all database queries and mutations through domain repositories (`KnownLocationsRepository`, `WorkoutRepository`, `SegmentsRepository`, `ExportStatusRepository`, `EquipmentRepository`, `DeviceDataRepository`, `SportTypesRepository`), completely eliminating direct calls to `*DatabaseManager.getInstance(...)` and `*DbHelper.getInstance(...)`.
* **REQ-UI-510.2** (*Domain Repository Presentation Delegation*): Domain repositories SHALL expose all necessary query and mutation delegates (`upsertLocationByGeofence`, `updateLocation`, `getMyLocation` on `KnownLocationsRepository`; `getWorkoutTrainingLoadRecords`, `getCardioPowerDynamicsFitnessTrendSamples` on `WorkoutRepository`; `allSegmentSummaries` on `SegmentsRepository`; `getExportRows` on `ExportStatusRepository`; `equipmentItems` on `EquipmentRepository`; `getActiveDeviceIdsForWorkout` on `DeviceDataRepository`; default sport resolution on `SportTypesRepository`).
* **REQ-UI-510.3** (*Package-Wide AST Presentation Cleanliness Contract*): `RepositoryUiCleanlinessContractTest` SHALL recursively inspect all source files under `app/src/main/java/.../ui/` and `app/src/main/java/.../activities/` (excluding `*Repository.kt` and `MapFragmentWithTrackViewModel.kt` reserved for `ATT-6692`), verifying zero occurrences of `\b\w*(DatabaseManager|DbHelper)\.getInstance\(`.

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* *Given* presentation layer files across `ui/` and `activities/`,
* *When* audited by `RepositoryUiCleanlinessContractTest`,
* *Then* zero direct `*DatabaseManager.getInstance` or `*DbHelper.getInstance` invocations exist.
* *Given* athletes viewing workout clusters, athlete profiles, Strava sections, tracking tabs, or export statuses,
* *When* loading presentation data,
* *Then* all metrics, locations, and device records load with 100% data fidelity via repository delegates on `Dispatchers.IO`.
* *Invariants*: Zero SQLite schema alterations; all repository I/O dispatched off main thread; 100% test pass rate across UI and ViewModel tests.

---

## 2. Test Specification (TST-UI-510)

### Test Case 1: Package-Wide UI AST Cleanliness Contract Test (`TST-UI-510.1`)
* **Scope**: Automated AST Contract Test (`RepositoryUiCleanlinessContractTest.kt`)
* **Assertion**: Recursively scan `ui/` and `activities/` (excluding `*Repository.kt` and `MapFragmentWithTrackViewModel.kt`) asserting zero `*DatabaseManager.getInstance` or `*DbHelper.getInstance` calls.

### Test Case 2: Targeted Presentation Cleanliness Assertions (`TST-UI-510.2`)
* **Scope**: Automated Unit Assertions (`RepositoryUiCleanlinessContractTest.kt`)
* **Assertion**: Verify explicit repository delegation across all 8 target components (`WorkoutClustersViewModel`, `TrackingTabsViewModel`, `AthleteProfileViewModel`, `StravaActivitySection`, `ExportStatusDataProvider`, `WorkoutDataMapper`, `MapInitialLocationResolver`, `ImportBackupTabsScreen`, `MainActivityWithNavigation`, `EditWorkoutViewModel`).

### Test Case 3: ViewModel & Presentation Unit Regression Test (`TST-UI-510.3`)
* **Scope**: ViewModel Unit Tests (`WorkoutClustersViewModelTest`, `AthleteProfileViewModelTest`, `TrackingTabsViewModelTest`)
* **Assertion**: Verify 100% functional and numeric parity when loading data through domain repositories.

### Test Case 4: Tier 1 Targeted Regression Suite (`TST-UI-510.4`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.RepositoryUiCleanlinessContractTest" --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.*" --tests "com.atrainingtracker.trainingtracker.activities.*"`
* **Assertion**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-510.1` | Contract (AST) | `RepositoryUiCleanlinessContractTest.kt` | `REQ-UI-510.3` | Specified |
| `TST-UI-510.2` | Contract (AST) | `RepositoryUiCleanlinessContractTest.kt` | `REQ-UI-510.1` | Specified |
| `TST-UI-510.3` | Unit (VM) | `WorkoutClustersViewModelTest.kt`, `AthleteProfileViewModelTest.kt` | `REQ-UI-510.2` | Specified |
| `TST-UI-510.4` | Regression | `ui.clusters.*`, `ui.athleteprofile.*`, `ui.tracking.*`, `activities.*` | `REQ-PRO-001` | Specified |
