# Stage 3: Implementation Plan - ATT-5489: BLE Random Static MAC & Search Timeout

**Ticket**: [ATT-5489](https://rainerblind.atlassian.net/browse/ATT-5489)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: None  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.7` | **Branch**: `feature/ATT-5489`  
**Requirement Mapping**: `REQ-CON-023` | **Test Mapping**: `TST-CON-023`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-05  

---

## 1. Problem Description & Background
Paired BLE sensors with Random Static MAC addresses fail to connect on uncached cold-start due to `getRemoteDevice(mAddress)` defaulting to `addr_type=public`. Furthermore, a hardcoded 10-second `SEARCH_PERIOD` blocks sequential paired device search when peripherals are offline.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-CON-023` (*Paired BLE Random Static Address Resolution, Scan-Assisted Connection & Configurable Search Timeout*)
* **Test Mapping**: `TST-CON-023` (*Paired BLE Random Static Address Resolution, Scan Assistance & Configurable Search Timeout Test Suite*)

---

## 3. System Invariants & Preserved Behavior
1. **Direct LE Transport**: `device.connectGatt(..., TRANSPORT_LE)` preserved for all BLE connections.
2. **Sequential Search Queue**: `DeviceManager` sequential iteration and try decrement preserved.
3. **Guaranteed Scanner Teardown**: Active scan callback unconditionally stopped on connect, timeout, disconnect, or shutdown.
4. **9-Language Parity & Code Lock**: 100% translation parity preserved; production code locked until Gate 3 PASS.

---

## 4. Proposed Architectural Changes
* **Core BLE Driver (`MyBTLEDevice.java`)**: Add `isRandomStaticAddress(String)`. On API 33+, use `getRemoteLeDevice(address, ADDRESS_TYPE_RANDOM)` when bit 47..46 = 11b. Implement filtered `BluetoothLeScanner` assist with defensive `stopScan()` on connect, timeout, and teardown. Replace hardcoded timeout with `TrainingApplication.getBtleSearchPeriodMs()`.
* **Application Plumbing (`TrainingApplication.java`)**: Add `SP_BTLE_SEARCH_PERIOD_SEC` (default 5s, 3..15s), `getBtleSearchPeriodSec()`, and `getBtleSearchPeriodMs()`.
* **Preferences & UI (`SensorSearchTuningSection.kt`, `SearchSettingsDialog.kt`, `prefs_search.xml`)**: Add `btleSearchPeriodSec` to `SensorSearchPreferences` and render discrete Material 3 sliders (3..15s, 11 steps, `"%d s"` badge).
* **UI Consistency**: Reuses `TuningSliderItem` and `SearchSettingsDialog` slider style with `MaterialTheme.colorScheme.primaryContainer` value badges and theme typography. No custom one-off styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Application Plumbing & Preference Modeling
* Files: `TrainingApplication.java`, `SensorSearchTuningSection.kt`, `prefs_search.xml`
* Add `SP_BTLE_SEARCH_PERIOD_SEC = "btleSearchPeriodSec"`, default 5s, range 3..15s. Add `getBtleSearchPeriodMs()`. Extend `SensorSearchPreferences` load/save/reset.

### Step 2: Settings UI Integration
* Files: `SensorSearchTuningSection.kt`, `SearchSettingsDialog.kt`
* Add BLE Search Duration slider directly below "Number of Search Tries" in both screens.

### Step 3: MyBTLEDevice Address Type & Scan Assistance
* Files: `MyBTLEDevice.java`
* Add `isRandomStaticAddress(String)`. Wire API 33+ `getRemoteLeDevice(...)`. Add filtered scan assist with `stopScan()` in `onConnectionStateChange`, `mSearchTimeoutRunnable`, `disconnectFromGatt()`, and `shutDown()`. Wire timeout to `TrainingApplication.getBtleSearchPeriodMs()`.

### Step 4: Architecture Documentation Update (SWE.2)
* Files: `docs/architecture.md`
* Update Section 4.2 / Section 1.1 with BLE Random Static address resolution & scan assist invariants.

### Step 5: Unit Test Suite & Verification
* Files: `MyBTLEDeviceLifecycleTest.kt`, `SensorSearchTuningContractTest.kt`
* Tests: Verify `isRandomStaticAddress`, scan lifecycle cleanup, preference persistence/reset, and run targeted regression suite.

---

## 6. Verification & Rollback Plan
* **Verification Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.*" --tests "com.atrainingtracker.trainingtracker.localization.*"`
* **Rollback**: Branch `feature/ATT-5489` can be reverted cleanly via git if regression occurs.
