# ATT-5095 Walkthrough: Garmin Varia BLE V2 High-Resolution Radar Stream

**Ticket**: [ATT-5095](https://rainerblind.atlassian.net/browse/ATT-5095)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Sprint**: `2026-46.1` | **Target Release**: `V5.0.0` | **Status**: Verified  
**Requirement Mapping**: `REQ-CON-030` | **Test Mapping**: `TST-CON-030`  
**Branch**: `feature/ATT-5095`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-10  

---

## 1. Executive Summary

Under `ATT-5095` (governed by `REQ-CON-030` / `TST-CON-030`):
- **BLE V2 Handshake Choreography & MTU 247 (`REQ-CON-030.1`)**: Implemented discovery of Garmin Varia V2 characteristic `6a4e3204-667b-11e3-949a-0800200c9a66` in `BTLERadarDevice.java`, requesting MTU expansion to 247 bytes and enabling CCCD indications/notifications.
- **Automatic Legacy Fallback Invariant (`REQ-CON-030.2`)**: Enforced automatic fallback to legacy V1 characteristic `6a4e3203` if `6a4e3204` is absent, guaranteeing 100% backward compatibility for RTL515/315/715 devices without triggering connection timeout or watchdog starvation.
- **High-Resolution V2 Frame Parsing (`REQ-CON-030.3`)**: Added `parseV2ThreatPacket` demuxing packet type `0x01` and decoding 8-byte target records: target ID, range up to 175 m (0.1 m precision), speed, signed lateral offset, vehicle size category (`UNKNOWN_OR_LIGHT`, `CAR`, `TRUCK_OR_BUS`), and lane state into `RadarTarget`.
- **Passing Clearance & Large Vehicle Aggregation (`REQ-CON-030.4`)**: Updated `RadarWindowAggregator.kt` to track minimum lateral passing clearance during close overtaking ($y \le 3.0\text{ m}$) into `passingDistanceMeters` and accumulate overtaking large vehicles (`largeVehicleCount`) with constructor/reset parity.
- **Scalar Sensor Publishing (`REQ-CON-030.5`)**: Declared and registered `SensorType.RADAR_PASSING_DISTANCE_m` (distance formatter) and `SensorType.RADAR_LARGE_VEHICLE_COUNT` (integer formatter) in `SensorType.java` and `BTLERadarDevice.java`.
- **Traffic Simulator V2 Extension (`REQ-CON-030.6`)**: Extended `RadarTrafficSimulator.kt` with lateral offset and vehicle classification trajectories (`generateV2Targets`).
- **9-Language Localization Parity (`REQ-CON-030.7`)**: Defined strings for passing distance and large vehicle count across EN, DE, ES, FR, IT, JA, NL, PL, PT in `strings_radar.xml` with 100% parity and zero emojis.

---

## 2. Traceability Matrix

| Requirement | Test Specification | Production Component | Verification Scope | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-030.1` | `TST-CON-030.1` | `BTLERadarDevice.java` | V2 Handshake Choreography & MTU 247 Request | **Verified** |
| `REQ-CON-030.2` | `TST-CON-030.2` | `BTLERadarDevice.java` | Automatic Legacy V1 Fallback Invariant | **Verified** |
| `REQ-CON-030.3` | `TST-CON-030.3` | `BTLERadarDevice.java`, `RadarState.kt` | 2D Multi-Target Record Parsing (175m, Lateral, Size, Lane) | **Verified** |
| `REQ-CON-030.4` | `TST-CON-030.4` | `RadarWindowAggregator.kt` | Lateral Passing Clearance Latch ($y \le 3.0\text{ m}$) & Large Vehicle Count | **Verified** |
| `REQ-CON-030.5` | `TST-CON-030.5` | `SensorType.java`, `BTLERadarDevice.java` | Scalar Sensor Registration & Metric Publishing | **Verified** |
| `REQ-CON-030.6` | `TST-CON-030.6` | `RadarTrafficSimulator.kt` | V2 Trajectory Simulation & Category Assignment | **Verified** |
| `REQ-CON-030.7` | `TST-CON-030.7` | `strings_radar.xml` (9 locales) | 9-Language Localization Parity Contract Test | **Verified** |

---

## 3. Verification & Test Execution

- **Explicit Executed Test Scope**:
  - `Tier 1 targeted: com.atrainingtracker.banalservice.devices.bluetooth_le.*, com.atrainingtracker.banalservice.sensor.*, com.atrainingtracker.banalservice.devices.radar.*, com.atrainingtracker.trainingtracker.localization.TranslationParityTest, com.atrainingtracker.trainingtracker.build.*`
  - Suites run: `BTLERadarDeviceTest` (V2 frame parsing, multi-target lateral offset decoding, vehicle size categories, fallback to V1 when V2 absent, watchdog timeout), `RadarWindowAggregatorTest` (passing distance latching, large vehicle counting, 1 Hz window resets), `TranslationParityTest` (100% parity across 9 languages), `AspiceSweAuditContractTest` (ASPICE SWE.1/SWE.2 contract validation).
  - Result: **100% PASS** (241 tests completed, exit code 0, 0 failures, 0 errors, 0 regressions in 1m 41s).
- **Physical Device / On-Device Checks**:
  - Checked `adb devices` (no physical devices connected; emulator boot strictly avoided per Rule 79). Protocol decoding and sensor telemetry verified through automated unit and regression suites.
