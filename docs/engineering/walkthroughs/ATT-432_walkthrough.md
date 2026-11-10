# ATT-432 Walkthrough: Access to Database only via Repository (Phase A: Residual UI & ViewModel Cleanup)

**Ticket**: [ATT-432](https://rainerblind.atlassian.net/browse/ATT-432)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Sprint**: `2026-46.1` | **Target Release**: `V5.0.0` | **Status**: Verified  
**Requirement Mapping**: `REQ-UI-507` | **Test Mapping**: `TST-UI-507`  

---

## 1. Executive Summary

Historically, presentation files (`MainActivityWithNavigation.kt`, `ImportBackupTabsScreen.kt`, `EquipmentViewModel.kt`, `ControlTrackingViewModel.kt`) directly called SQLite `*DatabaseManager.getInstance()`. This bypassed the domain repository layer and violated Clean Architecture (SWE.2).

Under `ATT-432` (Phase A, governed by `REQ-UI-507` / `TST-UI-507`):
- **Repository Query Delegates (`REQ-UI-507.5`)**: Added delegates to `WorkoutRepository`, `DeviceDataRepository`, `SportTypesRepository`, and `TrackingViewsRepository` for unfinished workouts, critical battery sensors, paired status, sport UI names, and tracking view filters.
- **MainActivity Decoupling (`REQ-UI-507.1`)**: Replaced 6 direct `DatabaseManager` calls with `WorkoutRepository`, `DeviceDataRepository`, and `TrackingViewsRepository` calls on `Dispatchers.IO`. Removed all database manager imports.
- **ImportBackupTabsScreen Decoupling (`REQ-UI-507.2`)**: Routed all sport UI name and base sport type resolutions through `SportTypesRepository`. Removed all database manager imports.
- **EquipmentViewModel Decoupling (`REQ-UI-507.3`)**: Refactored primary constructor to consume `SportTypesRepository`, `DeviceDataRepository`, and `WorkoutRepository`. Removed direct `.getInstance()` manager calls while preserving secondary constructor compatibility for unit test harnesses.
- **ControlTrackingViewModel Decoupling (`REQ-UI-507.4`)**: Replaced direct manager calls with `DeviceDataRepository` for cold-start sensor seeding and lazy `WorkoutRepository` for unfinished workout recovery. Removed database manager imports.
- **AST Cleanliness Verification (`TST-UI-507.1`)**: Authored `RepositoryUiCleanlinessContractTest.kt` asserting zero `*DatabaseManager` references in `MainActivityWithNavigation`, `ImportBackupTabsScreen`, and `ControlTrackingViewModel`, and zero `.getInstance()` calls in `EquipmentViewModel`.

---

## 2. Traceability Matrix

| Requirement | Test Specification | Production Code | Verification Tests | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-507.1` | `TST-UI-507.1` | `MainActivityWithNavigation.kt` | `RepositoryUiCleanlinessContractTest`, `MainActivityLifecycleTest` | **Verified** |
| `REQ-UI-507.2` | `TST-UI-507.1` | `ImportBackupTabsScreen.kt` | `RepositoryUiCleanlinessContractTest` | **Verified** |
| `REQ-UI-507.3` | `TST-UI-507.2` | `EquipmentViewModel.kt` | `RepositoryUiCleanlinessContractTest`, `EquipmentViewModelLifecycleTest` | **Verified** |
| `REQ-UI-507.4` | `TST-UI-507.3` | `ControlTrackingViewModel.kt` | `RepositoryUiCleanlinessContractTest`, `ControlTrackingViewModelTest` | **Verified** |
| `REQ-UI-507.5` | `TST-UI-507.4` | Target Repositories | Targeted Unit Test Suite | **Verified** |

---

## 3. Verification & Test Execution

- **Tier 1 Targeted Test Scope (Rule 60)**:
  - Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --max-workers=2 --tests "com.atrainingtracker.trainingtracker.ui.RepositoryUiCleanlinessContractTest" --tests "com.atrainingtracker.trainingtracker.activities.MainActivityLifecycleTest" --tests "com.atrainingtracker.trainingtracker.ui.equipment.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.*"`
  - Scope: Architectural AST cleanliness contract test, activity lifecycle and IO dispatcher tests, equipment state flow tests, control tracking permission/quota tests, theme color centralization audit, and core components.
  - Result: **100% PASS** (233 tests, 0 failures, 0 errors, 0 regressions).
- **Physical Device Execution**:
  - `adb devices` checked: no physical devices connected. Per Rule 79, emulator launch was omitted; verified via pure JUnit and Robolectric test harnesses.
