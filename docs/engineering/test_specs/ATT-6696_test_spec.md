# Stage 2: Requirement & Test Specification - ATT-6696: EquipmentViewModel & Repositories Domain DTO Decoupling

**Ticket**: [ATT-6696](https://rainerblind.atlassian.net/browse/ATT-6696) | **Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-46.2` | **Branch**: `feature/ATT-6696`  
**Requirement Mapping**: `REQ-UI-511` | **Test Spec ID**: `TST-UI-511`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-UI-511)

### 1.1 Problem Statement & Rationale
In Phase A (`ATT-432`), presentation layers were decoupled from direct SQLite singletons, but repositories retained leaky `*DatabaseManager` inner DTO return types (`DevicesDatabaseManager.SimpleSensorInfo`, `DevicesDatabaseManager.NameAndBatteryPercentage`, `SportTypeDatabaseManager.SimpleSportTypeInfo`, `WorkoutSummariesDatabaseManager.Stats`). Furthermore, `EquipmentViewModel.kt` retained 3 `@Deprecated` database manager parameters and 7 `resolve*()` shims to support legacy test harnesses, while directly instantiating `EquipmentDbHelper` and `SportTypeEquipmentLinkManager` (`docs/coding_standards.md` §1.12, §2.5, §5.9). Presentation layers must depend exclusively on domain models, and `EquipmentViewModel` must depend solely on domain repositories.

### 1.2 Functional & Architectural Requirements
* **REQ-UI-511.1** (*Domain DTO Return Types*): `DeviceDataRepository`, `SportTypesRepository`, and `WorkoutRepository` SHALL replace leaky `*DatabaseManager` inner DTO return types with strongly typed domain models (`CriticalBatteryDevice`, `SimpleSensorInfo`, `SimpleSportTypeInfo`, `WorkoutStatsSummary`).
* **REQ-UI-511.2** (*EquipmentRepository Encapsulation*): `EquipmentRepository` SHALL encapsulate equipment CRUD and sport-to-equipment associations, eliminating direct UI dependencies on `EquipmentDbHelper` and `SportTypeEquipmentLinkManager`.
* **REQ-UI-511.3** (*EquipmentViewModel Constructor Modernization*): `EquipmentViewModel.kt` SHALL accept domain repositories directly (`EquipmentRepository`, `SportTypesRepository`, `DeviceDataRepository`, `WorkoutRepository`). The 3 `@Deprecated` database manager constructor parameters and all 7 `resolve*()` delegation fallbacks SHALL be retired and removed.
* **REQ-UI-511.4** (*Clean Architecture Contract*): `RepositoryUiCleanlinessContractTest` SHALL verify zero leaky DatabaseManager DTOs or manager imports in `EquipmentViewModel` and UI callers.

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* *Given* domain repositories `DeviceDataRepository`, `SportTypesRepository`, and `WorkoutRepository`,
* *When* queried for battery levels, sensors, sport types, or equipment stats,
* *Then* they return pure domain models with zero leaky `*DatabaseManager` inner DTOs.
* *Given* `EquipmentViewModel`,
* *When* constructed and observed,
* *Then* it operates exclusively via domain repositories without deprecated database manager parameters or resolve shims.
* *Invariants*: Zero functional or numerical regression across equipment screens; thread safety on `Dispatchers.IO` preserved; 100% test pass rate across equipment test harnesses.

---

## 2. Test Specification (TST-UI-511)

### Test Case 1: Domain DTO Decoupling Contract Test (`TST-UI-511.1`)
* **Scope**: Automated AST/Reflection Contract Test (`RepositoryUiCleanlinessContractTest.kt`)
* **Assertion**: Verify public APIs of `DeviceDataRepository`, `SportTypesRepository`, and `WorkoutRepository` return pure domain DTOs with zero `*DatabaseManager` types; verify `EquipmentViewModel.kt` imports zero `*DatabaseManager` or `*DbHelper` classes.

### Test Case 2: EquipmentRepository Encapsulation Test (`TST-UI-511.2`)
* **Scope**: Automated Repository Tests (`EquipmentRepositorySyncTest.kt`, `EquipmentDbLinkContractTest.kt`)
* **Assertion**: Verify equipment CRUD operations and sport-type associations execute cleanly via `EquipmentRepository`.

### Test Case 3: EquipmentViewModel Modernization & Lifecycle Regression Test (`TST-UI-511.3`)
* **Scope**: ViewModel Unit Tests (`EquipmentViewModelLifecycleTest.kt`, `EquipmentViewModelMatrixTest.kt`, `EquipmentViewModelRetiredTest.kt`)
* **Assertion**: Verify `EquipmentViewModel` initializes cleanly with repository injection, updating StateFlows for equipment, sport types, and sensor counts with 100% data fidelity.

### Test Case 4: Tier 1 Targeted Regression Suite (`TST-UI-511.4`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.equipment.*" --tests "com.atrainingtracker.trainingtracker.repositories.*" --tests "com.atrainingtracker.banalservice.ui.devices.devicedata.*" --tests "com.atrainingtracker.trainingtracker.ui.RepositoryUiCleanlinessContractTest"`
* **Assertion**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-511.1` | Contract (AST) | `RepositoryUiCleanlinessContractTest.kt` | `REQ-UI-511.1`, `REQ-UI-511.4` | Specified |
| `TST-UI-511.2` | Repository (Unit) | `EquipmentRepository.kt`, `EquipmentRepositorySyncTest.kt` | `REQ-UI-511.2` | Specified |
| `TST-UI-511.3` | Unit (VM) | `EquipmentViewModel.kt`, `EquipmentViewModelLifecycleTest.kt` | `REQ-UI-511.3` | Specified |
| `TST-UI-511.4` | Regression | `ui.equipment.*`, `repositories.*`, `devicedata.*` | `REQ-PRO-001` | Specified |
