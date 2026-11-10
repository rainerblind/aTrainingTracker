# Stage 2: Requirement & Test Specification - ATT-5083: BLE Cycling Radar Protocol Driver, Domain Model & Telemetry

**Ticket**: [ATT-5083](https://rainerblind.atlassian.net/browse/ATT-5083) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-5083`  
**Requirement Mapping**: `REQ-CON-028` | **Test Spec ID**: `TST-CON-028`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-CON-028)

### 1.1 Problem Statement & Rationale
To support Garmin Varia cycling rearview radars (RearVue 820, RTL515, RCT715) over BLE, BANALService requires a dedicated `DeviceType.BIKE_RADAR` and `BTLERadarDevice` driver. Because the Garmin radar interleaves threat packets with heartbeat/sector packets on characteristic `6a4e3203` and requires sequential CCCD configuration, `MyBTLEDevice` requires a serialized GATT operation queue. High-frequency (4–8 Hz) multi-target streams must feed reactive UI via `RadarStateRepository` with a 3.5s disconnect watchdog, while Cockpit and `TrackerService` require 1 Hz scalar metrics (`RADAR_THREAT_COUNT`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`, `RADAR_VEHICLE_COUNT`) via a 1 Hz window aggregator with zero SQLite schema migrations.

### 1.2 Functional & Architectural Requirements
* **REQ-CON-028.1** (*DeviceType.BIKE_RADAR & Equipment Matrix*): `DeviceType.BIKE_RADAR` SHALL be added with `BSportType.BIKE`, auto-registered in `DevicesDatabaseManager.isBikeSensor()`, and integrated into `EquipmentSensorMatrixScreen`.
* **REQ-CON-028.2** (*Serialized GATT Operation Queue*): `MyBTLEDevice` SHALL serialize GATT operations (CCCD descriptor writes, characteristic reads/writes) in a thread-safe FIFO queue, executing each operation only upon receiving callback confirmation.
* **REQ-CON-028.3** (*Resilient BLE Peripheral Discovery*): `BTSearchForNewDevicesEngine` SHALL detect Garmin Varia Radar service `6a4e3200-667b-11e3-949a-0800200c9a66` across primary advertisement UUIDs, scan response records, and post-connect service discovery.
* **REQ-CON-028.4** (*BTLERadarDevice Driver & Reactive Stream*): `BTLERadarDevice` SHALL subscribe to V1 characteristic `6a4e3203-667b-11e3-949a-0800200c9a66`, filter non-threat packets by header `byte[0]`, parse multi-target frames into immutable `RadarTarget` records, and publish to `RadarStateRepository` (`StateFlow<RadarState>`) at 4–8 Hz with a 3.5s fail-safe timeout transitioning to `DISCONNECTED_ERROR`.
* **REQ-CON-028.5** (*1 Hz Latch Aggregator, Vehicle Counter & Zero Migration*): Expose scalar `SensorType`s (`RADAR_THREAT_COUNT`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`, `RADAR_VEHICLE_COUNT`). Maintain a 1 Hz window aggregator/latch for `TrackerService` sampling. Maintain cumulative overtaking vehicle count with target-ID and closing-range hysteresis. Zero SQLite schema migrations (`DB_VERSION` unchanged).
* **REQ-CON-028.6** (*9-Language Localization Parity*): Provide string resources in partitioned `app/src/main/res/values*/strings_radar.xml` with 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* *Given* a BLE radar streaming interleaved threat, heartbeat, and sector packets on `6a4e3203`, *When* parsed by `BTLERadarDevice`, *Then* non-threat packets are filtered and valid targets are published to `RadarStateRepository` at 4–8 Hz.
* *Given* an active workout with 4–8 Hz radar stream, *When* sampled at 1 Hz by `TrackerService`, *Then* peak speed and closest distance within the 1s window are latched and saved to `WorkoutSamples.db` with zero schema alterations.
* *Given* a disconnected or out-of-range radar, *When* 3.5 seconds elapse without valid packets, *Then* `RadarStateRepository` transitions to `DISCONNECTED_ERROR`.
* *Invariants*: Existing BLE sensors unaffected; zero schema migrations; 100% 9-language translation parity.

---

## 2. Test Specification (TST-CON-028)

### Test Case 1: Serialized GATT Queue Unit Test (`TST-CON-028.1`)
* **Scope**: Unit Test (`MyBTLEDeviceQueueTest.kt`)
* **Assertion**: Verify sequential FIFO execution of descriptor writes and reads without dropped or concurrent GATT operations.

### Test Case 2: BTLERadarDevice V1 Packet Decoding & Watchdog Unit Test (`TST-CON-028.2`)
* **Scope**: Unit Test (`BTLERadarDeviceTest.kt`)
* **Assertion**: Verify header `byte[0]` packet demuxing, multi-target parsing (range, approach speed, threat level), and 3.5s timeout transition to `DISCONNECTED_ERROR`.

### Test Case 3: 1 Hz Window Aggregator & Vehicle Counter Hysteresis Test (`TST-CON-028.3`)
* **Scope**: Unit Test (`RadarWindowAggregatorTest.kt`)
* **Assertion**: Verify 1s peak threat/speed and min distance retention, and verify vehicle count increments once per passing vehicle target-ID lifecycle.

### Test Case 4: DeviceType, SensorType & Database Parity Test (`TST-CON-028.4`)
* **Scope**: Unit Test (`DeviceTypeTest.kt`, `SensorTypeTest.kt`, `WorkoutSamplesDatabaseManagerTest.kt`)
* **Assertion**: Verify `DeviceType.BIKE_RADAR` binding to Bike, dynamic column generation without schema bumps, and extrema registration.

### Test Case 5: 9-Language Localization Parity Test (`TST-CON-028.5`)
* **Scope**: Contract Test (`TranslationParityTest.kt`)
* **Assertion**: Verify 100% string key and format specifier parity in `strings_radar.xml` across all 9 locales.

### Test Case 6: Tier 1 Targeted Regression Suite (`TST-CON-028.6`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Assertion**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CON-028.1` | Unit | `MyBTLEDevice.java`, `MyBTLEDeviceQueueTest.kt` | `REQ-CON-028.2` | Specified |
| `TST-CON-028.2` | Unit | `BTLERadarDevice.java`, `RadarStateRepository.kt`, `BTLERadarDeviceTest.kt` | `REQ-CON-028.3`, `REQ-CON-028.4` | Specified |
| `TST-CON-028.3` | Unit | `RadarWindowAggregator.kt`, `RadarWindowAggregatorTest.kt` | `REQ-CON-028.5` | Specified |
| `TST-CON-028.4` | Unit | `DeviceType.java`, `SensorType.java`, `WorkoutSamplesDatabaseManager.java` | `REQ-CON-028.1`, `REQ-CON-028.5` | Specified |
| `TST-CON-028.5` | Parity | `strings_radar.xml`, `TranslationParityTest.kt` | `REQ-CON-028.6` | Specified |
| `TST-CON-028.6` | Regression | `banalservice.*`, `build.*` | `REQ-PRO-001` | Specified |
