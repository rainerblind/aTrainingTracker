# Stage 3: Implementation Plan - ATT-2307: Stale Sensor Settings Equipment Mapping Due to Missing Reactive Synchronization with EquipmentDbHelper

**Ticket**: [ATT-2307](https://atrainingtracker.atlassian.net/browse/ATT-2307)  
**Sub-task**: [ATT-2320](https://atrainingtracker.atlassian.net/browse/ATT-2320) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-40.15`  
**Requirement Mapping**: `REQ-UI-257` (*Reactive Bi-Directional Equipment-to-Sensor Synchronization Between EquipmentDbHelper, DeviceDataRepository, and EquipmentViewModel*)  
**Test Mapping**: `TST-UI-216` (*Reactive Bi-Directional Equipment-to-Sensor Synchronization and Cache Invalidation Test*)  
**Branch**: `feature/ATT-2307`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Description & Background

During Sprint Review of ATT-2126 (Equipment-to-Sensor Mapping Matrix), a synchronization defect was discovered:
When an athlete modifies equipment-to-sensor mappings via `EquipmentSensorMatrixScreen.kt` or equipment tabs, changes are written to SQLite via `EquipmentDbHelper.setDeviceLink(...)` and reflected in `EquipmentViewModel`. However, `DeviceDataRepository.kt` maintains an in-memory cache of devices (`_allDevices`) and is not notified of these changes. Consequently, Sensor Settings (`DeviceListScreen` and `EditDeviceDialog`) render stale `linkedEquipment` until an app restart.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-257` (*Reactive Bi-Directional Equipment-to-Sensor Synchronization Between EquipmentDbHelper, DeviceDataRepository, and EquipmentViewModel*)
* **Test Mapping**: `TST-UI-216` (`TST-UI-216.1` to `TST-UI-216.5`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: The 1,600+ passing unit tests must continue to pass cleanly (`REQ-PRO-001`).
2. **Strict Circular Update Immunity**: Flow observers (`refreshDeviceFromDb`, `loadEquipment`) are strictly read-only and never trigger database mutations. Convergence is mathematically guaranteed with zero circular cascade risk.
3. **Lifecycle & Memory Leak Safety**: ViewModel collection runs strictly within `viewModelScope` and cancels upon `onCleared()`. Repository collection runs within application-scoped `repositoryScope`.
4. **Mandatory Programmatic Pre-Check**: Before modifying production code in Stage 4, `python3 tools/jira_util.py check-gate ATT-2320` must exit with code 0 (`GATE_PASSED: ATT-2320 is Erledigt`).

---

## 4. Proposed Architectural Changes

### Component 1: `EquipmentRepository.kt` (Reactive Event Stream)
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/EquipmentRepository.kt`
* Add a companion `MutableSharedFlow<Long?>` with buffer capacity:
  ```kotlin
  private val _equipmentLinksChanged = MutableSharedFlow<Long?>(
      extraBufferCapacity = 64,
      onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  @JvmStatic
  val equipmentLinksChanged: SharedFlow<Long?> = _equipmentLinksChanged.asSharedFlow()

  @JvmStatic
  fun notifyEquipmentLinksChanged(affectedDeviceId: Long? = null) {
      _equipmentLinksChanged.tryEmit(affectedDeviceId)
  }
  ```

### Component 2: `EquipmentDbHelper.java` (Database Mutation Dispatch)
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/database/EquipmentDbHelper.java`
* In `addDeviceLink(long equipmentId, long deviceId)`: invoke `EquipmentRepository.notifyEquipmentLinksChanged(deviceId)`.
* In `removeDeviceLink(long equipmentId, long deviceId)`: invoke `EquipmentRepository.notifyEquipmentLinksChanged(deviceId)`.
* In `setEquipmentLinks(int antDeviceId, @NonNull List<String> equipmentList)`: invoke `EquipmentRepository.notifyEquipmentLinksChanged((long) antDeviceId)`.
* In `updateEquipment(long id, String name, int frameType, @NonNull List<Long> linkedDeviceIds, boolean isRetired)`: invoke `EquipmentRepository.notifyEquipmentLinksChanged(null)`.
* In `deleteEquipment(long id)`: invoke `EquipmentRepository.notifyEquipmentLinksChanged(null)`.

### Component 3: `DeviceDataRepository.kt` (Reactive Invalidation & Cache Refresh)
* File: `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicedata/DeviceDataRepository.kt`
* In `init`, launch collection on `repositoryScope`:
  ```kotlin
  repositoryScope.launch {
      EquipmentRepository.equipmentLinksChanged.collect { affectedDeviceId ->
          if (affectedDeviceId != null) {
              refreshDeviceFromDb(affectedDeviceId)
          } else {
              loadAllDevices()
          }
      }
  }
  ```

### Component 4: `EquipmentViewModel.kt` (Bi-Directional Invalidation)
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentViewModel.kt`
* In `init`, launch collection on `viewModelScope`:
  ```kotlin
  viewModelScope.launch(ioDispatcher) {
      EquipmentRepository.equipmentLinksChanged.collect {
          loadEquipment()
      }
  }
  ```

### Component 5: `EditDeviceViewModel.kt` (Fresh Snapshot Initialization)
* File: `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceViewModel.kt`
* In `loadInitialDeviceData(deviceId: Long)`:
  Remove early exit guard to always fetch fresh snapshot:
  ```kotlin
  fun loadInitialDeviceData(deviceId: Long) {
      _deviceSnapshot.value = devicesRepository.getDeviceSnapshotById(deviceId)
      _editingId.value = deviceId
  }
  ```

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Programmatic Gate 3 Pre-Check
* Verify Gate 3 sign-off via:
  ```bash
  python3 tools/jira_util.py check-gate ATT-2320
  ```

### Step 2: Implement Reactive Event Stream in `EquipmentRepository.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/EquipmentRepository.kt`
* Define `equipmentLinksChanged` and `notifyEquipmentLinksChanged`.

### Step 3: Wire Mutation Notifications in `EquipmentDbHelper.java`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/database/EquipmentDbHelper.java`
* Dispatch `notifyEquipmentLinksChanged` in `addDeviceLink`, `removeDeviceLink`, `setEquipmentLinks`, `updateEquipment`, and `deleteEquipment`.

### Step 4: Wire Reactive Collection in `DeviceDataRepository.kt`
* File: `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicedata/DeviceDataRepository.kt`
* Collect `EquipmentRepository.equipmentLinksChanged` in `init`.

### Step 5: Wire Bi-Directional Collection in `EquipmentViewModel.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentViewModel.kt`
* Collect `EquipmentRepository.equipmentLinksChanged` in `init`.

### Step 6: Refactor `EditDeviceViewModel.kt` Snapshot Loading
* File: `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceViewModel.kt`
* Unconditionally update `_deviceSnapshot.value` in `loadInitialDeviceData`.

### Step 7: Author Unit Tests
* Files:
  - `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/devicedata/DeviceDataRepositoryEquipmentSyncTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentViewModelSyncTest.kt`
* Execute targeted tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepositoryEquipmentSyncTest" --tests "com.atrainingtracker.trainingtracker.ui.equipment.EquipmentViewModelSyncTest"
  ```
