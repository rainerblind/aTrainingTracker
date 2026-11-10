# Stage 2: Requirement & Test Specification - ATT-432: Repository-Only UI Database Access Layer (Phase A)

**Ticket**: [ATT-432](https://rainerblind.atlassian.net/browse/ATT-432)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-46.1` | **Branch**: `feature/ATT-432`  
**Requirement Mapping**: `REQ-UI-507` (`docs/requirements/req_ui_design_system.md`)  
**Test Spec ID**: `TST-UI-507` (`docs/tests/tst_ui_design_system.md`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-UI-507)

### 1.1 Problem Statement & Rationale
Residual direct SQLite database manager access (`*DatabaseManager.getInstance()`) remains in 4 presentation files (`MainActivityWithNavigation.kt`, `ImportBackupTabsScreen.kt`, `EquipmentViewModel.kt`, `ControlTrackingViewModel.kt`). This violates Clean Architecture (SWE.2). The system SHALL route all presentation-layer database interactions through domain repositories (`WorkoutRepository`, `DeviceDataRepository`, `SportTypesRepository`, `TrackingViewsRepository`).

### 1.2 Functional & Architectural Requirements
* **REQ-UI-507.1** (*MainActivityWithNavigation Database Decoupling*): Replace direct references to `DevicesDatabaseManager`, `TrackingViewsDatabaseManager`, and `WorkoutSummariesDatabaseManager` with repository calls. Low-battery alerts, filter generation, and unfinished workout flows SHALL execute via repository delegates on `Dispatchers.IO`.
* **REQ-UI-507.2** (*ImportBackupTabsScreen Database Decoupling*): Route all sport name and UI name queries in `ImportBackupTabsScreen.kt` through `SportTypesRepository`.
* **REQ-UI-507.3** (*EquipmentViewModel Repository Encapsulation*): Refactor `EquipmentViewModel.kt` to consume `SportTypesRepository`, `DeviceDataRepository`, and `WorkoutRepository` directly. Remove direct manager instantiation while preserving backward-compatible secondary constructors for unit test harnesses.
* **REQ-UI-507.4** (*ControlTrackingViewModel Database Decoupling*): Route cold-start sensor state seeding and unfinished workout verification in `ControlTrackingViewModel.kt` through `DeviceDataRepository` and `WorkoutRepository`.
* **REQ-UI-507.5** (*Repository Delegates & Invariant Preservation*): Expose clean delegates on target repositories. Preserve SQLite schema stability, thread dispatching, and battery alert thresholds. Phase B (`ATT-6426`) and Phase C (`ATT-6427`) remain strictly out of scope.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Amends `REQ-UI-010` (Equipment), `REQ-UI-257` (Reactive Links), `REQ-UI-259` (Remote Devices). Targets `MainActivityWithNavigation.kt`, `ImportBackupTabsScreen.kt`, `EquipmentViewModel.kt`, `ControlTrackingViewModel.kt`.
2. *Historical Origin & Commit Trace*: Tickets `ATT-510`, `ATT-3390`, `ATT-3998`.
3. *Root Reason for Existing Formulation*: Direct manager singletons were called prior to the introduction of unified domain repositories.
4. *Preservation of Core Invariants*: Dispatchers (`Dispatchers.IO`), battery snackbar thresholds, and unfinished workout dialog flows remain 100% identical.

### 1.4 Acceptance Criteria (Given-When-Then) & Invariants
* *Given* `MainActivityWithNavigation`, `ImportBackupTabsScreen`, `EquipmentViewModel`, and `ControlTrackingViewModel`,
* *When* inspecting AST and imports,
* *Then* zero occurrences of `*DatabaseManager.getInstance()` exist in those files.
* *Given* an unfinished workout, *When* app starts or resumes, *Then* recovery flow operates identically via `WorkoutRepository`.
* *Invariants*: Phase A strictly presentation layer; zero modifications to Phase B (`ATT-6426`) or Phase C (`ATT-6427`); 100% unit tests pass.

---

## 2. Test Specification (TST-UI-507)

### Test Case 1: AST DatabaseManager Prohibition Contract Test (`TST-UI-507.1`)
* **Scope**: Automated Contract Unit Test (`RepositoryUiCleanlinessContractTest.kt`)
* **Assertion**: Verify zero `*DatabaseManager` references in `MainActivityWithNavigation`, `ImportBackupTabsScreen`, `EquipmentViewModel`, and `ControlTrackingViewModel`.

### Test Case 2: EquipmentViewModel Repository Delegation Test (`TST-UI-507.2`)
* **Scope**: ViewModel Unit Tests (`EquipmentViewModelTest.kt`)
* **Assertion**: Verify `EquipmentViewModel` correctly interacts with repositories and backward-compatible constructors function.

### Test Case 3: ControlTrackingViewModel Delegation Test (`TST-UI-507.3`)
* **Scope**: ViewModel Unit Tests (`ControlTrackingViewModelTest.kt`)
* **Assertion**: Verify sensor seeding and unfinished workout verification execute correctly via repositories.

### Test Case 4: Tier 1 Targeted Unit Regression Suite (`TST-UI-507.4`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.equipment.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.importbackup.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Assertion**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-507.1` | Contract | `RepositoryUiCleanlinessContractTest.kt` | `REQ-UI-507.1` – `REQ-UI-507.4` | Specified |
| `TST-UI-507.2` | Unit | `EquipmentViewModelTest.kt` | `REQ-UI-507.3` | Specified |
| `TST-UI-507.3` | Unit | `ControlTrackingViewModelTest.kt` | `REQ-UI-507.4` | Specified |
| `TST-UI-507.4` | Regression | `ui.equipment.*`, `controltracking.*`, `build.*` | `REQ-PRO-001` | Specified |
