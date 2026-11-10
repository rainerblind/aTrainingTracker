# Stage 3: Implementation Plan - ATT-432: Repository-Only UI Database Access Layer (Phase A)

**Ticket**: [ATT-432](https://rainerblind.atlassian.net/browse/ATT-432) | **Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) | **Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.1` | **Branch**: `feature/ATT-432` | **Req/Test**: `REQ-UI-507` / `TST-UI-507`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Architecture Rationale
Residual direct SQLite database manager calls (`*DatabaseManager.getInstance()`) in 4 presentation files (`MainActivityWithNavigation.kt`, `ImportBackupTabsScreen.kt`, `EquipmentViewModel.kt`, `ControlTrackingViewModel.kt`) bypass domain repositories and violate Clean Architecture (SWE.2). We replace these direct calls with delegates on existing domain repositories (`WorkoutRepository`, `DeviceDataRepository`, `SportTypesRepository`, `TrackingViewsRepository`), preserving 100% functional and concurrency parity.

---

## 2. System Invariants & Preserved Behavior
1. **Phase A Strict Bounding**: Phase B (`ATT-6426`: Engines/Exporters) and Phase C (`ATT-6427`: Services) strictly untouched.
2. **Database Schema & Helper Stability**: Zero schema modifications; `SQLiteOpenHelper` databases preserved without Room retrofitting.
3. **Thread Safety**: All repository database queries execute on `Dispatchers.IO`.
4. **Harness Compatibility**: Secondary constructors in `EquipmentViewModel` preserved for unit tests.

---

## 3. UI Consistency (Design Guidelines)
* **Architectural Decoupling**: No visual or layout changes; all sensor battery alerts, dialogs, and equipment sync flows operate identically.

---

## 4. Step-by-Step Implementation Sequence

### Step 1: Repository Query Delegates (`REQ-UI-507.5`)
* **Files**: `WorkoutRepository.kt`, `DeviceDataRepository.kt`, `SportTypesRepository.kt`, `TrackingViewsRepository.kt`
* **Changes**:
  - `WorkoutRepository`: expose `hasUnfinishedWorkout(): Boolean`, `discardOrFinishUnfinishedWorkout()`.
  - `DeviceDataRepository`: expose `getCriticalBatteryDevices()`, `isPaired()`, `getDeviceName()`, `hasPairedRemoteDevices()`, `hasPairedBluetoothDevices()`, `allRemoteSensors`, `getSensorsForSportType()`.
  - `SportTypesRepository`: expose `getUIName()`, `getBSportType()`, `getSportTypeId()`, `getSportTypes()`.
  - `TrackingViewsRepository`: expose `getAllFilterData(DeviceDataRepository)`.

### Step 2: Refactor MainActivityWithNavigation (`REQ-UI-507.1`)
* **File**: `activities/MainActivityWithNavigation.kt`
* **Changes**: Replace 6 direct `DatabaseManager.getInstance` calls with `DeviceDataRepository`, `TrackingViewsRepository`, and `WorkoutRepository` calls on `Dispatchers.IO`.

### Step 3: Refactor ImportBackupTabsScreen (`REQ-UI-507.2`)
* **File**: `migration/ImportBackupTabsScreen.kt`
* **Changes**: Replace 4 direct `SportTypeDatabaseManager` calls with `SportTypesRepository.getInstance(context)` calls.

### Step 4: Refactor EquipmentViewModel (`REQ-UI-507.3`)
* **File**: `ui/equipment/EquipmentViewModel.kt`
* **Changes**: Update primary constructor to consume repositories (`SportTypesRepository`, `DeviceDataRepository`, `WorkoutRepository`). Retain secondary constructor with mock managers for test backwards compatibility.

### Step 5: Refactor ControlTrackingViewModel (`REQ-UI-507.4`)
* **File**: `ui/tracking/controltracking/ControlTrackingViewModel.kt`
* **Changes**: Replace direct `DevicesDatabaseManager` calls with `devicesRepository` initializers; replace `WorkoutSummariesDatabaseManager.hasUnfinishedWorkout()` with `workoutRepository.hasUnfinishedWorkout()`.

### Step 6: AST Cleanliness & Regression Test Suite (`TST-UI-507.1` – `TST-UI-507.4`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/RepositoryUiCleanlinessContractTest.kt`
* **Changes**: Assert zero `*DatabaseManager` references in the 4 target presentation files; verify test suites pass.

### Step 7: Living Documentation Verification (`REQ-UI-507`, `TST-UI-507`)
* **Files**: `docs/requirements/req_ui_design_system.md`, `docs/tests/tst_ui_design_system.md`
* **Changes**: Update status tags from `[Planned]` to `[Verified]`.
