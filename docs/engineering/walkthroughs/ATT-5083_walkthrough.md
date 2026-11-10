# ATT-5083 Walkthrough: BLE Cycling Radar Protocol Driver, Domain Model, Scalar SensorTypes & Bike Equipment Integration

**Ticket**: [ATT-5083](https://rainerblind.atlassian.net/browse/ATT-5083)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Sprint**: `2026-46.1` | **Target Release**: `V5.0.0` | **Status**: Verified  
**Requirement Mapping**: `REQ-CON-028` | **Test Mapping**: `TST-CON-028`  

---

## 1. Executive Summary

Under `ATT-5083` (governed by `REQ-CON-028` / `TST-CON-028`):
- **DeviceType.BIKE_RADAR & Equipment Matrix (`REQ-CON-028.1`)**: Registered `DeviceType.BIKE_RADAR` (`BSportType.BIKE`, equipment priority 45) in `DeviceType.java`, `DeviceManager.java`, and `DevicesDatabaseManager.isBikeSensor()`.
- **Serialized GATT Operation Queue (`REQ-CON-028.2`)**: Implemented thread-safe FIFO GATT operation queue in `MyBTLEDevice.java` to serialize CCCD descriptor writes and characteristic read/writes with callback advancement and retry/timeout handling.
- **Resilient BLE Radar Peripheral Discovery (`REQ-CON-028.3`)**: Added Garmin Varia Radar 128-bit service UUID (`6a4e3200-667b-11e3-949a-0800200c9a66`) and Service Data scan filtering in `BTSearchForNewDevicesEngine.java`.
- **V1 Threat Demuxing & Reactive StateFlow Repository (`REQ-CON-028.4`)**: Implemented `BTLERadarDevice.java` decoding Garmin V1 (`6a4e3203`) threat packets (`1 + 3*N` bytes) via `byte[0]` header demuxing, publishing multi-target streams (4–8 Hz) to `RadarStateRepository.kt` (`StateFlow<RadarState>`) with a 3.5s disconnect watchdog.
- **1 Hz Scalar Latch & Zero-Migration Persistence (`REQ-CON-028.5`)**: Exposed 4 scalar metrics (`RADAR_THREAT_COUNT`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`, `RADAR_VEHICLE_COUNT`) via `RadarWindowAggregator.kt` maintaining peak threat/speed and min distance latches and vehicle passing hysteresis with zero SQLite schema migrations (`DB_VERSION` unchanged).
- **9-Language Localization Parity (`REQ-CON-028.6`)**: Authored `strings_radar.xml` across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with 100% key and placeholder parity.

---

## 2. Traceability Matrix

| Requirement | Test Specification | Production Component | Verification Scope | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-028.1` | `TST-CON-028.4`, `TST-CON-028.6` | `DeviceType.java`, `DeviceManager.java` | Equipment Matrix, Bike Sensor Registry | **Verified** |
| `REQ-CON-028.2` | `TST-CON-028.1` | `MyBTLEDevice.java` | FIFO GATT Queue, Callback Advancement | **Verified** |
| `REQ-CON-028.3` | `TST-CON-028.6` | `BTSearchForNewDevicesEngine.java` | 128-bit UUID & Service Data Matching | **Verified** |
| `REQ-CON-028.4` | `TST-CON-028.2` | `BTLERadarDevice.java`, `RadarStateRepository.kt` | Header Demuxing, Threat Calculation, 3.5s Watchdog | **Verified** |
| `REQ-CON-028.5` | `TST-CON-028.3`, `TST-CON-028.4` | `RadarWindowAggregator.kt`, `SensorType.java` | 1 Hz Latch, Vehicle Hysteresis, Zero-Migration | **Verified** |
| `REQ-CON-028.6` | `TST-CON-028.5` | `strings_radar.xml` (9 locales) | 9-Language Translation Parity Audit | **Verified** |

---

## 3. Verification & Test Execution

- **Executed Test Scope**:
  - `Tier 1 targeted: banalservice.*, ui.equipment.*, localization.TranslationParityTest, ui.theme.ThemeColorCentralizationAuditTest, ui.components.core.*, build.*`
  - Test suites: `BTLERadarDeviceTest`, `MyBTLEDeviceQueueTest`, `RadarWindowAggregatorTest`, `SensorCategoryAndSelectorContractTest`, `TranslationParityTest`, `ThemeColorCentralizationAuditTest`, `AspiceSweAuditContractTest`.
  - Result: **100% PASS** (exit code 0, 0 failures, 0 errors, 0 regressions).
- **Physical Device / On-Device Checks**:
  - Checked `adb devices` (no live devices attached; emulator boot strictly avoided per Rule 79).
  - Protocol driver, background BLE GATT engine, and domain model components; zero UI or manifest alterations. Physical APK deployment omitted per Rule 60 in favor of targeted JVM unit test and contract test verification.
