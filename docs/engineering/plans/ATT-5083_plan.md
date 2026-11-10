# Stage 3 Implementation Plan: ATT-5083 - BLE Cycling Radar Protocol Driver, Domain Model & Telemetry

**Ticket**: [ATT-5083](https://rainerblind.atlassian.net/browse/ATT-5083) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-5083`  
**Requirement Mapping**: `REQ-CON-028` (`028.1`–`028.6`) | **Test Spec**: `TST-CON-028` (`028.1`–`028.6`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Technical Architecture & SWE.2 Design
* **Domain Model & Constants**:
  - `BluetoothConstants.java`: Add Garmin Radar Service `6a4e3200-667b-11e3-949a-0800200c9a66` and V1 characteristic `6a4e3203-667b-11e3-949a-0800200c9a66`.
  - `DeviceType.java`: Add `BIKE_RADAR(SensorType.RADAR_THREAT_COUNT, BSportType.BIKE, 1)`.
  - `SensorType.java`: Add scalar telemetry metrics (`RADAR_THREAT_COUNT`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`, `RADAR_VEHICLE_COUNT`) under `SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS`.
  - `ActivityType.java`: Expose new radar metrics in `ActivityType.getSensorTypeArray()`.
* **Serialized GATT Operation Queue (`MyBTLEDevice.java`)**:
  - Encapsulate GATT operations (`WriteDescriptor`, `ReadCharacteristic`, `WriteCharacteristic`) in a thread-safe FIFO queue.
  - Advance queue strictly upon callback events (`onDescriptorWrite`, `onCharacteristicRead`, `onCharacteristicWrite`).
* **Resilient Discovery (`BTSearchForNewDevicesEngine.java`)**:
  - In `ScanCallback.onScanResult`, check `ScanRecord` service UUIDs and service data for Garmin Radar UUID alongside name filtering.
* **Driver & Reactive Repository (`BTLERadarDevice.java`, `RadarStateRepository.kt`)**:
  - `BTLERadarDevice`: Enqueues V1 CCCD notification subscription; parses threat packets by checking header `byte[0]`; demuxes target triplets into `RadarTarget`.
  - `RadarStateRepository`: Emits immutable `RadarState` (`StateFlow<RadarState>`) at 4–8 Hz; maintains 3.5s watchdog transitioning to `DISCONNECTED_ERROR`.
  - `RadarWindowAggregator.kt`: Implements 1 Hz latch holding peak threat count/speed and min distance for `TrackerService.sampleAndWriteToDb()`; tracks passing vehicle hysteresis; obeys Rule 1.8 constructor seeding & sensor-presence gating.
* **Bike Equipment Integration & Localization**:
  - `DevicesDatabaseManager.isBikeSensor()` / `EquipmentSensorOrdering.kt`: Prioritize `BIKE_RADAR` for bikes.
  - `DeviceManager.java`: Instantiate `BTLERadarDevice` for `DeviceType.BIKE_RADAR`.
  - Domain-partitioned `app/src/main/res/values*/strings_radar.xml` across all 9 locales (Rule 9, `REQ-PRO-041.3`).
* **UI Consistency**: Equipment matrix integration reuses existing table composables in `EquipmentSensorMatrixScreen.kt`. No new UI screens.

---

## 2. Atomic Implementation Steps

| Step | Component | Target Files | Action & Core Invariants | Traceability |
| :--- | :--- | :--- | :--- | :--- |
| **1** | Domain & Consts | `BluetoothConstants.java`<br>`DeviceType.java`<br>`SensorType.java`<br>`ActivityType.java` | Add radar UUIDs, `BIKE_RADAR` enum, 4 radar `SensorType`s in `SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS`, and register in `ActivityType`. Zero SQLite migration. | `REQ-CON-028.1,5`<br>`TST-CON-028.4` |
| **2** | Localization | `res/values*/strings_radar.xml` | Author partitioned `strings_radar.xml` across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) for device & sensor names. | `REQ-CON-028.6`<br>`TST-CON-028.5` |
| **3** | GATT Queue | `MyBTLEDevice.java` | Implement thread-safe serialized FIFO queue for descriptor writes and characteristic read/writes; advance on callback completion. | `REQ-CON-028.2`<br>`TST-CON-028.1` |
| **4** | Discovery Engine | `BTSearchForNewDevicesEngine.java` | Add `BIKE_RADAR` discovery, inspect `ScanRecord` service UUIDs/data for Garmin Radar UUID `6a4e3200`, and wire device creation. | `REQ-CON-028.3`<br>`TST-CON-028.2` |
| **5** | Radar Repository | `RadarState.kt`<br>`RadarStateRepository.kt` | Implement immutable `RadarState` data class, `RadarTarget`, and `RadarStateRepository` with 3.5s timeout watchdog. | `REQ-CON-028.4`<br>`TST-CON-028.2` |
| **6** | Protocol Driver | `BTLERadarDevice.java`<br>`DeviceManager.java` | Implement `BTLERadarDevice` subscribing to V1 characteristic `6a4e3203`, demuxing threat packets, and wiring into `DeviceManager`. | `REQ-CON-028.4`<br>`TST-CON-028.2` |
| **7** | Latch Aggregator | `RadarWindowAggregator.kt` | Implement 1 Hz window aggregator holding peak threat/speed and min distance; implement passing-vehicle counter hysteresis (Rule 1.8). | `REQ-CON-028.5`<br>`TST-CON-028.3` |
| **8** | Equipment Matrix | `DevicesDatabaseManager.java`<br>`EquipmentSensorOrdering.kt` | Register `BIKE_RADAR` as bike sensor in `DevicesDatabaseManager.isBikeSensor()` and set bike priority rank 45. | `REQ-CON-028.1`<br>`TST-CON-028.4` |
| **9** | Unit Tests | `MyBTLEDeviceQueueTest.kt`<br>`BTLERadarDeviceTest.kt`<br>`RadarWindowAggregatorTest.kt`<br>`SensorCategoryAndSelectorContractTest.kt` | Author unit tests for GATT queue, packet demuxing, timeout watchdog, 1 Hz latch, and update `SensorCategoryAndSelectorContractTest` (56 -> 60). | `REQ-CON-028.1-6`<br>`TST-CON-028.1-5` |
| **10** | Verification | Test Suite | Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.*" --tests "com.atrainingtracker.trainingtracker.build.*"`. | `REQ-PRO-001`<br>`TST-CON-028.6` |

---

## 3. System Invariants & Rollback Safety
* **Code Lock (Rule 4)**: No production source code modified until Gate 3 passes audit (`python3 tools/jira_util.py check-gate`).
* **Zero Schema Migration**: `WorkoutSamplesDatabaseManager` and `WorkoutSummariesDatabaseManager` dynamically adapt; `DB_VERSION` unchanged.
* **Backward Compatibility**: Existing BLE sensors (HRM, Power, Speed, Cadence) run through serialized queue with zero regression.
* **Risk Rating**: **MEDIUM** (Base `MyBTLEDevice` queue modification mitigated by isolated FIFO execution and dedicated queue tests).
