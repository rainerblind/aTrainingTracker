# ATT-5102 Walkthrough: BLE Radar Tail Light Mode Control & Workout Auto-Switch

**Ticket**: [ATT-5102](https://rainerblind.atlassian.net/browse/ATT-5102)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Sprint**: `2026-46.1` | **Target Release**: `V5.0.0` | **Status**: Verified  
**Requirement Mapping**: `REQ-CON-031` | **Test Mapping**: `TST-CON-031`  
**Branch**: `feature/ATT-5102`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-10  

---

## 1. Executive Summary

Under `ATT-5102` (governed by `REQ-CON-031` / `TST-CON-031`):
- **BLE Light Control Discovery & Serialized Queue Routing (`REQ-CON-031.1`)**: Declared `CHARACTERISTIC_RADAR_LIGHT_CONTROL` (`6a4e3202-667b-11e3-949a-0800200c9a66`) in `BluetoothConstants.java`. Added capability discovery and non-blocking GATT write command dispatch via `enqueueWriteCharacteristic` in `BTLERadarDevice.java` without delaying radar measurement notifications or starvation watchdog processing.
- **Tail Light Mode Mapping & StVZO Capability Pruning (`REQ-CON-031.2`)**: Defined `RadarLightMode.kt` enum (`OFF`, `DAY_FLASH`, `NIGHT_FLASH`, `SOLID`, `PELOTON`) with command byte encoding and StVZO legality flags. Implemented centralized singleton `RadarLightController.kt` automatically pruning flashing modes to `SOLID` when StVZO-certified hardware is connected.
- **Cockpit Quick-Switch Sheet (`REQ-CON-031.3`)**: Implemented `RadarLightQuickSwitchSheet.kt` using `AppModalBottomSheet` with legal StVZO disclaimers and live mode selection. Wired top status badge tap in `RadarEdgeOverlay.kt` and `TrackingTabsScreen.kt` while preserving pass-through gestures.
- **Workout Lifecycle Auto-Switch Automation (`REQ-CON-031.4`)**: Hooked `TrackerService.java` lifecycle (`onWorkoutStarted` / `onWorkoutStopped`) to apply configured default light mode on workout start and extinguish LEDs on workout finish, protected with mock-safe `filesDir` guards against plain unit test crashes.
- **Settings Configuration & DataStore Persistence (`REQ-CON-031.5`)**: Extended `TuningPreferencesDataStore.kt` with `KEY_RADAR_LIGHT_DEFAULT_MODE` and `KEY_RADAR_AUTO_LIGHT_CONTROL` preferences, and added the Tail Light Control card to `RadarSettingsSection.kt`.
- **Traffic Simulator Light State Emulation (`REQ-CON-031.6`)**: Updated `RadarTrafficSimulator.kt` to emulate tail light mode commands and reflect state in `RadarStateRepository`.
- **9-Language Localization Parity (`REQ-CON-031.7`)**: Defined light mode and auto-switch strings across EN, DE, ES, FR, IT, JA, NL, PL, PT in `strings_radar.xml` with 100% parity and zero emojis.

---

## 2. Traceability Matrix

| Requirement | Test Specification | Production Component | Verification Scope | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-031.1` | `TST-CON-031.1` | `BTLERadarDevice.java`, `BluetoothConstants.java` | Light Characteristic Discovery & Non-Blocking Queue Routing | **Verified** |
| `REQ-CON-031.2` | `TST-CON-031.2` | `RadarLightMode.kt`, `RadarLightController.kt` | Mode Byte Encoding & StVZO Dynamic Flashing Pruning | **Verified** |
| `REQ-CON-031.3` | `TST-CON-031.3` | `RadarLightQuickSwitchSheet.kt`, `RadarEdgeOverlay.kt` | Modal Bottom Sheet UI, Badge Tap, Pass-Through Gestures | **Verified** |
| `REQ-CON-031.4` | `TST-CON-031.4` | `RadarLightController.kt`, `TrackerService.java` | Workout Lifecycle Start/Stop Hooks & Mock-Safe Guards | **Verified** |
| `REQ-CON-031.5` | `TST-CON-031.5` | `TuningPreferencesDataStore.kt`, `RadarSettingsSection.kt` | Preference Persistence & UI Settings Card | **Verified** |
| `REQ-CON-031.6` | `TST-CON-031.6` | `RadarTrafficSimulator.kt` | Simulator Light Command Handling & State Reflection | **Verified** |
| `REQ-CON-031.7` | `TST-CON-031.7` | `strings_radar.xml` (9 locales) | 9-Language Localization Parity Contract Test | **Verified** |

---

## 3. Verification & Test Execution

- **Explicit Executed Test Scope**:
  - `Tier 1 targeted: com.atrainingtracker.banalservice.devices.bluetooth_le.*, com.atrainingtracker.banalservice.devices.radar.*, com.atrainingtracker.trainingtracker.ui.tracking.*, com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest, com.atrainingtracker.trainingtracker.ui.components.core.*, com.atrainingtracker.trainingtracker.localization.TranslationParityTest, com.atrainingtracker.trainingtracker.build.*`
  - Suites run: `RadarLightControllerTest` (command byte mapping, StVZO auto-pruning to SOLID, serialized device dispatch, traffic simulator integration, mock-safe null-guards), `BTLERadarDeviceTest` (discovery of characteristic 6a4e3202, non-blocking queue write dispatch, graceful handling when unsupported), `SensorCategoryAndSelectorContractTest` (62 SensorType constants), `TuningConfigTest` (light default mode and auto-switch preferences), `ThemeColorCentralizationAuditTest`, `TranslationParityTest` (100% key and format parity across 9 locales), `AspiceSweAuditContractTest` (ASPICE SWE.1/SWE.2 contract validation).
  - Result: **100% PASS** (715 tests completed, exit code 0, 0 failures, 0 errors, 0 regressions in 2m 40s).
- **Physical Device / On-Device Checks**:
  - Checked `adb devices` (no physical devices connected; emulator boot strictly avoided per Rule 79). Protocol queue serialization, StVZO fallback, and UI sheet lifecycle verified through automated unit, UI component, and architecture contract suites.
