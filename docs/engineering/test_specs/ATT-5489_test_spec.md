# Stage 2: Requirement & Test Specification - ATT-5489: BLE Random Static MAC & Search Timeout

**Ticket**: [ATT-5489](https://rainerblind.atlassian.net/browse/ATT-5489)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: None  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.7` | **Branch**: `feature/ATT-5489`  
**Requirement Mapping**: `REQ-CON-023` | **Test Spec ID**: `TST-CON-023`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-05  

---

## 1. Requirement Specification (`REQ-CON-023`)

### 1.1 Problem Statement & Rationale
Paired BLE fitness sensors with Random Static MAC addresses (e.g. Magene `C6:CC:E3:F5:F0:37`, `E8:4D:74:07:F4:DD`) fail to connect during paired search when Android's `BluetoothRemoteDevices` cache is empty because `getRemoteDevice(mAddress)` defaults to `ADDRESS_TYPE_PUBLIC` (`RxAdd = 0`), which peripherals ignore per Bluetooth Core Spec Vol 6 Part B §1.3.2.1. Additionally, a hardcoded 10-second `SEARCH_PERIOD` causes up to 30s+ head-of-line blocking across offline paired devices.

### 1.2 Functional & Architectural Requirements
1. **Random Static MAC Classification & Address Type (`REQ-CON-023.1`)**: Classify addresses with `(firstOctet & 0xC0) == 0xC0` as Random Static. On API 33+, resolve via `BluetoothAdapter.getRemoteLeDevice(mAddress, ADDRESS_TYPE_RANDOM)` (or `ADDRESS_TYPE_PUBLIC` otherwise), falling back to `getRemoteDevice(mAddress)` on API < 33.
2. **Filtered Active Scan Assistance & Safe Teardown (`REQ-CON-023.2`)**: During `startSearching()`, launch a filtered `BluetoothLeScanner` scan for `mAddress` (if `BLUETOOTH_SCAN` is granted) to warm the OS remote device cache and trigger rapid connection. Defensively `stopScan()` on connect, timeout, disconnect, or shutdown.
3. **Configurable BLE Search Duration Persistence (`REQ-CON-023.3`)**: Introduce `SP_BTLE_SEARCH_PERIOD_SEC = "btleSearchPeriodSec"` ($3\text{--}15$s, default 5s) in `TrainingApplication` and `SensorSearchPreferences`. Pass `TrainingApplication.getBtleSearchPeriodMs()` to `mSearchTimeoutRunnable`.
4. **Expert Settings & Search Dialog Slider UI (`REQ-CON-023.4`)**: Add a discrete Material 3 slider (range 3..15, 11 snap steps, `"%d s"` badge) directly below search tries in `SensorSearchTuningSection.kt` and `SearchSettingsDialog.kt`.
5. **9-Language Localization Parity (`REQ-CON-023.5`)**: Localize `prefs_btle_search_period_title` and `prefs_btle_search_period_summary` across all 9 locales.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Random Static Connect)**: *Given* an uncached BLE sensor with Random Static MAC, *When* `startSearching()` runs, *Then* it resolves `ADDRESS_TYPE_RANDOM` or scan-assisted connect without timing out on `addr_type=public`.
* **AC-2 (Configurable Timeout)**: *Given* `btleSearchPeriodSec` configured to 4s, *When* searching for an offline sensor, *Then* timeout triggers after 4s instead of 10s.
* **AC-3 (Resource Cleanup)**: *Given* an active search, *When* connection succeeds or times out or shuts down, *Then* the active BLE scan stops cleanly.

### 1.4 System Invariants & Chesterton's Fence Audit
* **System Invariants**: Direct connect transport `TRANSPORT_LE` preserved; sequential search queue preserved; zero leak of `ScanCallback`; 100% test pass rate.
* **Archaeology**: Amends `REQ-CON-019` (`ATT-2773`) and `REQ-UI-154` (`ATT-2780`). `getRemoteDevice()` was default before API 33; 10s timeout was inherited from ANT+. Preserving direct connect while adding address type resolution and configurable timeout resolves both root causes safely.

---

## 2. Test Specification (`TST-CON-023`)

### Test Case 1: Random Static MAC Classification (`TST-CON-023.1`)
* **Scope**: Unit test in `MyBTLEDeviceTest.java`.
* **Procedure**: Assert `isRandomStaticAddress(String)` returns `true` for `C6:CC:E3:F5:F0:37`, `E8:4D:74:07:F4:DD`, `FE:00:11:22:33:44`, `C0:00:00:00:00:00` and `false` for public addresses (`00:1A:7D:...`, `44:55:66:...`) and null/malformed.

### Test Case 2: Scan Assistance Lifecycle & Guaranteed Teardown (`TST-CON-023.2`)
* **Scope**: Unit test in `MyBTLEDeviceTest.java`.
* **Procedure**: Verify `startScan` is called with address filter on `startSearching()`, and `stopScan` is invoked on `STATE_CONNECTED`, search timeout, `disconnectFromGatt()`, and `shutDown()`.

### Test Case 3: Preference Persistence & Plumbing (`TST-CON-023.3`)
* **Scope**: Unit test in `SensorSearchPreferencesTest.kt`.
* **Procedure**: Assert save/load/reset of `btleSearchPeriodSec` (default 5s, range 3..15s), and verify `TrainingApplication.getBtleSearchPeriodMs()` returns `btleSearchPeriodSec * 1000L`.

### Test Case 4: UI Sliders & Dialog Binding (`TST-CON-023.4`)
* **Scope**: Compose contract tests in `SensorSearchTuningSectionTest.kt` and `SearchSettingsDialogTest.kt`.
* **Procedure**: Assert discrete slider renders with range 3..15 and updates state cleanly.

### Test Case 5: 9-Language Localization Parity (`TST-CON-023.5`)
* **Scope**: Localization parity test in `TranslationParityTest.kt`.
* **Procedure**: Assert `prefs_btle_search_period_title` and `prefs_btle_search_period_summary` exist with matching format specifiers across all 9 locales.

### Test Case 6: Targeted Package Suite Regression (`TST-CON-023.6`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.*" --tests "com.atrainingtracker.trainingtracker.localization.*"`
* **Expected Result**: 100% test pass rate across all targeted tests.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-CON-023.1` | Random Static MAC & Address Type Resolution | `TST-CON-023.1` | Unit Test (`MyBTLEDeviceTest.java`) |
| `REQ-CON-023.2` | Active Scan Assistance & Scanner Teardown | `TST-CON-023.2` | Unit Test (`MyBTLEDeviceTest.java`) |
| `REQ-CON-023.3` | Configurable BLE Search Duration Plumbing | `TST-CON-023.3` | Unit Test (`SensorSearchPreferencesTest.kt`) |
| `REQ-CON-023.4` | Expert Settings & Search Dialog Sliders | `TST-CON-023.4` | Compose Contract Test |
| `REQ-CON-023.5` | 9-Language Localization Parity | `TST-CON-023.5` | `TranslationParityTest` |
| `REQ-CON-023` | System Invariants & Zero Regression | `TST-CON-023.6` | Targeted Regression Suite |
