# Stage 3: Implementation Plan - ATT-6696: EquipmentViewModel & Repositories Domain DTO Decoupling

**Ticket**: [ATT-6696](https://rainerblind.atlassian.net/browse/ATT-6696)  
**Sub-task**: `[Impl-Plan]` (Created on submission)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-46.2`  
**Requirement Mapping**: `REQ-UI-511` | **Test Mapping**: `TST-UI-511`  
**Branch**: `feature/ATT-6696` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
In Phase A (`ATT-432`), presentation components were decoupled from SQLite singletons, but repositories retained leaky `*DatabaseManager` inner DTO return types (`SimpleSensorInfo`, `NameAndBatteryPercentage`, `SimpleSportTypeInfo`, `Stats`). Furthermore, `EquipmentViewModel.kt` retained 3 `@Deprecated` database manager parameters and 7 `resolve*()` shims. This plan introduces pure domain DTOs, consolidates equipment CRUD in `EquipmentRepository`, and modernizes `EquipmentViewModel` (`docs/coding_standards.md` §1.12, §2.5, §5.9).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-511` (*Clean Repository Domain DTOs & EquipmentViewModel Decoupling*)
  * `REQ-UI-511.1`: Domain DTOs (`CriticalBatteryDevice`, `SimpleSensorInfo`, `SimpleSportTypeInfo`, `WorkoutStatsSummary`).
  * `REQ-UI-511.2`: `EquipmentRepository` encapsulation of equipment & sport-link CRUD.
  * `REQ-UI-511.3`: `EquipmentViewModel` constructor modernization and retirement of 3 deprecated params & 7 shims.
  * `REQ-UI-511.4`: Clean architecture contract verification in `RepositoryUiCleanlinessContractTest`.
* **Test Mapping**: `TST-UI-511` (`TST-UI-511.1` contract, `TST-UI-511.2` repository tests, `TST-UI-511.3` ViewModel tests, `TST-UI-511.4` targeted regression).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Data Drift**: All sensor ordering, battery thresholds, equipment links, and stats calculations remain 100% numerically identical.
2. **Thread Safety**: All repository mutations and reads remain dispatched on `Dispatchers.IO`.
3. **UI Consistency**: No visual or layout changes (pure architectural DTO and ViewModel constructor decoupling).
4. **Code Lock & Governance**: Production code lock enforced until Gate 3 PASS (`Rule 4`).

---

## 4. Proposed Architectural Changes
* **Domain Models**:
  * `DeviceDataRepository.kt`: Declare domain `CriticalBatteryDevice` and `SimpleSensorInfo`.
  * `SportTypesRepository.kt`: Declare domain `SimpleSportTypeInfo`.
  * `WorkoutRepository.kt`: Declare domain `WorkoutStatsSummary`.
* **Repository Delegations**:
  * `EquipmentRepository.kt`: Wrap all `EquipmentDbHelper` CRUD and `SportTypeEquipmentLinkManager` link methods.
* **ViewModel & Screen Modernization**:
  * `EquipmentViewModel.kt`: Remove `dbSportHelper`, `dbDevicesHelper`, `dbSummariesManager` and all 7 `resolve*()` shims. Rely exclusively on injected domain repositories.
  * Migrate `EditEquipmentDialog`, `EquipmentSensorMatrixScreen`, `EquipmentSensorOrdering`, `SensorBatteryNotificationManager`, `StatsData`, and `EditWorkoutViewModel` to domain models.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Domain DTOs & Repository Return Types)**: Define domain models in `DeviceDataRepository`, `SportTypesRepository`, `WorkoutRepository`, and update `StatsData.kt`.
2. **Step 2 (EquipmentRepository Encapsulation)**: Add equipment CRUD and sport-link delegates to `EquipmentRepository.kt`.
3. **Step 3 (EquipmentViewModel & Presentation Decoupling)**: Modernize `EquipmentViewModel.kt` constructor; update call sites in `EditEquipmentDialog`, `EquipmentSensorMatrixScreen`, `EquipmentSensorOrdering`, and `SensorBatteryNotificationManager`.
4. **Step 4 (Test Harness Modernization)**: Refactor `EquipmentViewModelLifecycleTest`, `EquipmentViewModelMatrixTest`, `EquipmentViewModelRetiredTest`, and `EquipmentViewModelSyncTest` to inject mocked repositories.
5. **Step 5 (Contract Guard & Verification)**: Harden `RepositoryUiCleanlinessContractTest.kt` asserting zero `*DatabaseManager` DTO returns or imports in `EquipmentViewModel`. Run targeted suite:
   `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.equipment.*" --tests "com.atrainingtracker.trainingtracker.repositories.*" --tests "com.atrainingtracker.banalservice.ui.devices.devicedata.*" --tests "com.atrainingtracker.trainingtracker.ui.RepositoryUiCleanlinessContractTest" --max-workers=2`

---

## 6. Verification & Rollback Plan
* **Verification**: AST/reflection cleanliness assertions + 4 modernized ViewModel test suites.
* **Rollback**: Branch isolation allows full revert without impacting `develop` or `sprint/2026-46.2`.
