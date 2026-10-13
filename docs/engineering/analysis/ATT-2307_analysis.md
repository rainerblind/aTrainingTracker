# Stage 1 Analysis: ATT-2307 - Stale Sensor Settings Equipment Mapping Due to Missing Reactive Synchronization with EquipmentDbHelper

**Ticket**: [ATT-2307](https://atrainingtracker.atlassian.net/browse/ATT-2307)  
**Sub-task**: [ATT-2318](https://atrainingtracker.atlassian.net/browse/ATT-2318) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-40.15`  
**Branch**: `feature/ATT-2307`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Statement & Motivation

During Sprint Review of ATT-2126 (Equipment-to-Sensor Mapping Matrix), a synchronization defect was discovered when modifying equipment-to-sensor mappings:
1. An athlete links or unlinks an equipment mapping for a sensor in the "Sensor Matrix" tab (`EquipmentSensorMatrixScreen.kt`) or equipment list.
2. The change is saved to SQLite via `EquipmentDbHelper.setDeviceLink(equipmentId, sensorId, isLinked)` and immediately updates `EquipmentViewModel` StateFlows (`_bikes` and `_shoes`).
3. The athlete navigates to **Sensor Settings** (`DeviceListScreen` or `EditDeviceDialog`).
4. **Defect:** In Sensor Settings, the equipment mapping remains stale (the unselected mapping is still shown as linked, or newly linked equipment is absent). The change only becomes visible after an application process restart or manual database reload.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic architectural inspection revealed:
1. **Unnotified In-Memory Cache in `DeviceDataRepository.kt`**:
   `DeviceDataRepository` serves as the in-memory single source of truth for sensor metadata, caching loaded devices in `_allDevices: MutableStateFlow<List<DeviceUiData>>`. Lookups like `getDeviceById(id)` and `getDeviceSnapshotById(id)` query `_allDevices.value` directly.
2. **One-Way Database Mutations**:
   When equipment mappings are altered via `EquipmentViewModel.setSensorLink`, `EquipmentViewModel.updateEquipment`, `addEquipment`, or `deleteEquipment`, updates write directly to the SQLite `LINKS` table in `EquipmentDbHelper`.
3. **Missing Invalidation Bridge**:
   `EquipmentDbHelper` does not dispatch any invalidation notifications when the `LINKS` table is modified. Neither `DeviceDataRepository` nor any of its mappers are alerted to reload or refresh affected device records.
4. **Stale Snapshot Guard in `EditDeviceViewModel.kt`**:
   In `EditDeviceViewModel.kt`, `loadInitialDeviceData(deviceId)` contained an early-exit guard:
   ```kotlin
   if (_editingId.value == deviceId && _deviceSnapshot.value != null) return
   ```
   If a ViewModel instance is retained across sheet dismissals or navigation cycles, this early exit prevents re-evaluating `_deviceSnapshot` against `devicesRepository`, compounding the staleness.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Implement a reactive Kotlin `SharedFlow` event stream (`equipmentLinksChanged: SharedFlow<Long?>`) in `EquipmentRepository` (or dedicated event bus) triggered whenever sensor links are added, deleted, or bulk-updated in `EquipmentDbHelper`.
  * Connect `DeviceDataRepository` to observe `equipmentLinksChanged` within its repository scope, automatically refreshing affected device records via `refreshDeviceFromDb(deviceId)` or reloading all devices via `loadAllDevices()`.
  * Connect `EquipmentViewModel` to observe `equipmentLinksChanged` within `viewModelScope` to reload equipment reactively when sensor links are updated from Sensor Settings (`EditDeviceDialog`), achieving bi-directional synchronization.
  * Eliminate the stale early-exit guard in `EditDeviceViewModel.loadInitialDeviceData` so dialogs always initialize with fresh data from `DeviceDataRepository`.
  * Author automated unit tests validating real-time bi-directional synchronization, cycle immunity, and cache invalidation.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying SQLite schemas for `EQUIPMENT` or `LINKS` tables.
  * Altering Bluetooth LE GATT discovery or telemetry pipelines (`BTSearchForNewDevicesEngine`, `MyBTLEDevice`).
  * Redesigning matrix layout or visual column alignment (addressed separately in `ATT-2306`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Net-New Requirement**: `REQ-UI-257` (*Reactive Bi-Directional Equipment-to-Sensor Synchronization Between EquipmentDbHelper, DeviceDataRepository, and EquipmentViewModel*), extending and refining `REQ-UI-256` (*Fleet-Wide Equipment-to-Sensor Mapping Matrix with Checkboxes for Bikes and Shoes*).
* No existing requirements in `docs/requirements.md` are deleted or weakened.

---

## 5. Architectural Strategy & High-Level Solution

### Component 1: Reactive Event Stream in `EquipmentRepository.kt`
- Expose a thread-safe `SharedFlow`:
  ```kotlin
  private val _equipmentLinksChanged = MutableSharedFlow<Long?>(
      extraBufferCapacity = 64,
      onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  val equipmentLinksChanged: SharedFlow<Long?> = _equipmentLinksChanged.asSharedFlow()

  @JvmStatic
  fun notifyEquipmentLinksChanged(affectedDeviceId: Long? = null) {
      _equipmentLinksChanged.tryEmit(affectedDeviceId)
  }
  ```
- In `EquipmentDbHelper.java`, invoke `EquipmentRepository.notifyEquipmentLinksChanged(deviceId)` on link mutations (`addDeviceLink`, `removeDeviceLink`, `updateEquipment`, `deleteEquipment`, `setEquipmentLinks`).

### Component 2: `DeviceDataRepository.kt` Integration
- In `init`, launch a collector on `repositoryScope`:
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

### Component 3: `EquipmentViewModel.kt` Integration
- In `init`, collect `equipmentLinksChanged` on `viewModelScope`:
  ```kotlin
  viewModelScope.launch(ioDispatcher) {
      EquipmentRepository.equipmentLinksChanged.collect {
          loadEquipment()
      }
  }
  ```

### Component 4: Circular Update Immunity (Proof of Convergence)
- **Strict Read/Write Decoupling**:
  - Write operations (`setDeviceLink`, `updateEquipment`, `updateDevice`) write to SQLite and invoke `notifyEquipmentLinksChanged`.
  - Flow collection handlers (`refreshDeviceFromDb`, `loadEquipment`) are strictly **read-only**: they read updated rows from SQLite and update in-memory `StateFlow` instances (`_allDevices`, `_bikes`, `_shoes`).
  - Because collection handlers NEVER dispatch database writes, circular update cascades are mathematically impossible:
    $$\text{User Action} \longrightarrow \text{DB Write} \longrightarrow \text{SharedFlow Emission} \longrightarrow \text{DB Read \& StateFlow Update} \longrightarrow \mathbf{Terminal\ State}$$

### Component 5: Lifecycle & Leak-Free Architecture
- `viewModelScope` cancellation guarantees that when `EquipmentViewModel` or `EditDeviceViewModel` is cleared, all coroutines and collectors are terminated immediately.
- `DeviceDataRepository` is a singleton tied to `Application` lifecycle; its `repositoryScope` has no reference to UI contexts (Activity/Fragment), guaranteeing zero memory leaks.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regressions across the 1,600+ test suite (`REQ-PRO-001`).
  2. Single-source-of-truth consistency across both UI domains (Equipment Matrix & Sensor Settings).
  3. Thread safety: database writes execute on `Dispatchers.IO`; Flow emissions and collections are non-blocking.
  4. Parent ticket Human Decision Gate remains strictly guarded at `Final Review (Human)`.
* **Risk Rating**: **LOW**. Uses standard Kotlin Coroutines reactive primitives (`SharedFlow`), requires zero schema changes, and has proven convergence with zero circular loop risk.
