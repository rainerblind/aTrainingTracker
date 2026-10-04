# Stage 2: Requirement & Test Specification - ATT-2307: Stale Sensor Settings Equipment Mapping Due to Missing Reactive Synchronization with EquipmentDbHelper

**Ticket**: [ATT-2307](https://atrainingtracker.atlassian.net/browse/ATT-2307)  
**Sub-task**: [ATT-2319](https://atrainingtracker.atlassian.net/browse/ATT-2319) (`[Test-Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-40.15`  
**Requirement Mapping**: `REQ-UI-257` (*Reactive Bi-Directional Equipment-to-Sensor Synchronization Between EquipmentDbHelper, DeviceDataRepository, and EquipmentViewModel*)  
**Test Spec ID**: `TST-UI-216`  
**Branch**: `feature/ATT-2307`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Requirement Specification (REQ-UI-257)

### 1.1 Problem Statement & Rationale
During Sprint Review of ATT-2126 (Equipment-to-Sensor Mapping Matrix), a synchronization defect was identified:
When an athlete modifies equipment-to-sensor mappings via `EquipmentSensorMatrixScreen.kt` or equipment tabs, changes are written to SQLite via `EquipmentDbHelper.setDeviceLink(...)` and reflected in `EquipmentViewModel`. However, `DeviceDataRepository.kt` maintains an in-memory cache of devices (`_allDevices`) and is not notified of these changes. Consequently, Sensor Settings (`DeviceListScreen` and `EditDeviceDialog`) render stale `linkedEquipment` until an app restart.

### 1.2 Functional & Architectural Requirements
The system SHALL establish reactive, lifecycle-safe, bi-directional synchronization between equipment mapping mutations and sensor repositories (ATT-2307):
1. **Reactive Event Stream (`EquipmentRepository.kt`)**:
   - `EquipmentRepository` SHALL expose a reactive event stream `equipmentLinksChanged: SharedFlow<Long?>` configured with buffer capacity and `BufferOverflow.DROP_OLDEST`.
   - The repository SHALL provide `@JvmStatic fun notifyEquipmentLinksChanged(affectedDeviceId: Long?)` callable from both Kotlin and Java.
2. **Database Mutation Dispatch (`EquipmentDbHelper.java`)**:
   - `EquipmentDbHelper` SHALL invoke `EquipmentRepository.notifyEquipmentLinksChanged(deviceId)` on every link mutation:
     - `addDeviceLink(equipmentId, deviceId)` $\to$ `deviceId`
     - `removeDeviceLink(equipmentId, deviceId)` $\to$ `deviceId`
     - `setEquipmentLinks(antDeviceId, ...)` $\to$ `(long) antDeviceId`
     - `updateEquipment(...)` $\to$ `null` (bulk update)
     - `deleteEquipment(...)` $\to$ `null` (bulk deletion)
3. **Reactive Invalidation & Cache Refresh (`DeviceDataRepository.kt`)**:
   - `DeviceDataRepository` SHALL collect `EquipmentRepository.equipmentLinksChanged` on `repositoryScope`.
   - When a specific `affectedDeviceId` is received, it SHALL invoke `refreshDeviceFromDb(affectedDeviceId)`.
   - When `null` is received, it SHALL invoke `loadAllDevices()`.
   - The updated device state SHALL be emitted via `allDevices` StateFlow, propagating immediately to `GetMergedDevicesUseCase` and `DeviceListViewModel`.
4. **Bi-Directional Equipment Reactivity (`EquipmentViewModel.kt`)**:
   - `EquipmentViewModel` SHALL collect `EquipmentRepository.equipmentLinksChanged` on `viewModelScope` and invoke `loadEquipment()` whenever external modifications occur (such as saves from `EditDeviceDialog`).
5. **Fresh Dialog State Initialization (`EditDeviceViewModel.kt`)**:
   - `EditDeviceViewModel.loadInitialDeviceData(deviceId)` SHALL retrieve the fresh snapshot from `DeviceDataRepository.getDeviceSnapshotById(deviceId)` unconditionally, eliminating stale early-exit retention.
6. **Circular Update Immunity & Convergence**:
   - Invalidation handlers (`refreshDeviceFromDb`, `loadEquipment`) SHALL be strictly read-only and SHALL NEVER execute database writes, guaranteeing zero recursive loops.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Matrix to Sensor Settings Reactivity)**:
  * *Given* an athlete modifies a sensor link in `EquipmentSensorMatrixScreen` or `EquipmentTabsScreen`,
  * *When* `EquipmentDbHelper.setDeviceLink` updates the SQLite database,
  * *Then* `EquipmentRepository.equipmentLinksChanged` SHALL emit the device ID, `DeviceDataRepository` SHALL refresh the device, and `DeviceListScreen` SHALL render the updated `linkedEquipment` immediately.
* **Criterion 2 (Fresh Read in Edit Device Dialog)**:
  * *Given* modified equipment links in SQLite,
  * *When* the athlete opens `EditDeviceDialog`,
  * *Then* `_deviceSnapshot` SHALL reflect the latest `linkedEquipment` stored in SQLite without requiring process restart.
* **Criterion 3 (Sensor Settings to Equipment Reactivity)**:
  * *Given* an athlete edits linked equipment in `EditDeviceDialog` and taps Save,
  * *When* `DeviceDataRepository.updateDevice` saves the links via `EquipmentDbHelper.setEquipmentLinks`,
  * *Then* `EquipmentViewModel` SHALL reactively reload, updating `_bikes` and `_shoes` in real time.
* **Criterion 4 (Lifecycle Safety & Zero Leaks)**:
  * *Given* ViewModel recreation or destruction,
  * *When* `viewModelScope` is cancelled,
  * *Then* all coroutine collectors SHALL be cleanly terminated, ensuring zero memory leaks and zero orphaned observers.

### 1.4 System Invariants
1. SQLite database schemas (`EQUIPMENT`, `LINKS`, `DEVICES`) remain unaltered.
2. Invalidation handlers are read-only: circular update cascades are prevented.
3. 100% clean-room unit test pass rate across the workspace.

---

## 2. Test Specification (TST-UI-216)

### Test Case 1: `testEquipmentLinksChanged_notifiesDeviceDataRepository_refreshesAffectedDevice` (`TST-UI-216.1`)
* **Scope**: Unit Test (`DeviceDataRepositoryEquipmentSyncTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/devicedata/DeviceDataRepositoryEquipmentSyncTest.kt`
* **Preconditions**: Mock `DeviceDataRepository` with initial cached device possessing empty `linkedEquipment`.
* **Action**: Emit device ID to `EquipmentRepository.equipmentLinksChanged`.
* **Expected Result**: `refreshDeviceFromDb` is executed, and `allDevices` emits updated `DeviceUiData` with refreshed `linkedEquipment`.

### Test Case 2: `testEquipmentDbHelper_linkMutations_emitEquipmentLinksChanged` (`TST-UI-216.2`)
* **Scope**: Unit Test (`EquipmentDbHelperSyncTest.kt` / Robolectric/Unit)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/EquipmentDbHelperSyncTest.kt`
* **Preconditions**: Mock/Spied `EquipmentRepository.notifyEquipmentLinksChanged`.
* **Action**: Execute `addDeviceLink`, `removeDeviceLink`, and `setEquipmentLinks`.
* **Expected Result**: `notifyEquipmentLinksChanged` is invoked with the expected device ID for each operation.

### Test Case 3: `testEditDeviceViewModel_loadInitialDeviceData_alwaysFetchesFreshSnapshot` (`TST-UI-216.3`)
* **Scope**: Unit Test (`EditDeviceViewModelTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceViewModelTest.kt`
* **Preconditions**: `EditDeviceViewModel` initialized with device ID. Repository snapshot subsequently changes.
* **Action**: Invoke `loadInitialDeviceData(deviceId)` again.
* **Expected Result**: `deviceSnapshot.value` updates to the fresh repository snapshot rather than keeping stale data.

### Test Case 4: `testEquipmentViewModel_reloadsEquipment_onExternalLinksChanged` (`TST-UI-216.4`)
* **Scope**: Unit Test (`EquipmentViewModelSyncTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentViewModelSyncTest.kt`
* **Preconditions**: `EquipmentViewModel` initialized.
* **Action**: Emit an event to `EquipmentRepository.equipmentLinksChanged`.
* **Expected Result**: `loadEquipment()` is invoked, refreshing `_bikes` and `_shoes`.

### Test Case 5: Clean-Room Regression Suite (`TST-UI-216.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-216.1` | Unit | `DeviceDataRepository.init` / `refreshDeviceFromDb` | `REQ-UI-257` | Specified |
| `TST-UI-216.2` | Unit | `EquipmentDbHelper.setDeviceLink` / `setEquipmentLinks` | `REQ-UI-257` | Specified |
| `TST-UI-216.3` | Unit | `EditDeviceViewModel.loadInitialDeviceData` | `REQ-UI-257` | Specified |
| `TST-UI-216.4` | Unit | `EquipmentViewModel.init` / `loadEquipment` | `REQ-UI-257` | Specified |
| `TST-UI-216.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
