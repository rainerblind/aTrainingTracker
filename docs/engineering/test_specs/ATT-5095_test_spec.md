# Stage 2: Requirement & Test Specification - ATT-5095: Decode Garmin Varia BLE V2 Stream

**Ticket**: [ATT-5095](https://rainerblind.atlassian.net/browse/ATT-5095)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1`  
**Requirement Mapping**: `REQ-CON-030` (*Garmin Varia BLE V2 High-Resolution Radar Stream & Vehicle Classification*)  
**Test Spec ID**: `TST-CON-030`  
**Branch**: `feature/ATT-5095`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-CON-030)

### 1.1 Problem Statement & Rationale
Next-generation Garmin Varia radar units (e.g. RearVue 820) introduce a high-resolution 2D BLE telemetry stream on characteristic `6a4e3204-667b-11e3-949a-0800200c9a66` extending radar detection range up to 175 m, providing lateral offset (identifying overtaking trajectory and close passes), and classifying vehicle size (Bikes/Motorcycles vs. Cars vs. Trucks/Busses).

### 1.2 Functional & Architectural Requirements
1. *BLE V2 Handshake Choreography (`REQ-CON-030.1`)*: On discovery of characteristic `6a4e3204`, `BTLERadarDevice` SHALL verify LESC bonding, request MTU 247, execute serialized GATT session handshake, and enable CCCD indications/notifications.
2. *Automatic Legacy Fallback Invariant (`REQ-CON-030.2`)*: If characteristic `6a4e3204` is absent, or if handshake negotiation fails or times out within 5.0 seconds, `BTLERadarDevice` SHALL immediately fall back to subscribing to legacy V1 characteristic `6a4e3203`.
3. *High-Resolution V2 Packet Parsing & Domain Mapping (`REQ-CON-030.3`)*: The system SHALL parse multi-target V2 telemetry frames (header `0x01`), decoding `targetId`, range (0–175 m, 0.1 m resolution), signed approach speed, lateral offset, and vehicle size category into enriched `RadarTarget` domain models.
4. *Lateral Passing Clearance & Large Vehicle Aggregation (`REQ-CON-030.4`)*: `RadarWindowAggregator` SHALL track minimum lateral passing distance during vehicle overtaking ($y \le 3.0\text{ m}$) and latch `RADAR_PASSING_DISTANCE_m` and `RADAR_LARGE_VEHICLE_COUNT` at each 1 Hz tick.
5. *Scalar Sensor Publishing (`REQ-CON-030.5`)*: `BTLERadarDevice` SHALL register and publish `SensorType.RADAR_PASSING_DISTANCE_m` and `SensorType.RADAR_LARGE_VEHICLE_COUNT` to `MySensorManager`.
6. *Traffic Simulator V2 Extension (`REQ-CON-030.6`)*: `RadarTrafficSimulator` SHALL generate realistic V2 trajectory scenarios including vehicle size diversity and lateral pass positioning.
7. *9-Language Localization Parity (`REQ-CON-030.7`)*: All sensor names and short labels in `strings_radar.xml` SHALL maintain 100% key and format parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (V2 Discovery & 2D Stream)**: *Given* a connected Garmin Varia BLE peripheral exposing `6a4e3204`, *When* GATT discovery completes, *Then* `BTLERadarDevice` requests MTU 247, executes handshake, and publishes 2D targets (up to 175 m, lateral offset).
* **Criterion 2 (Legacy V1 Fallback)**: *Given* a legacy radar unit exposing only `6a4e3203`, *When* discovered, *Then* `BTLERadarDevice` automatically subscribes to V1 without error or timeout.
* **Criterion 3 (Close-Pass & Large Vehicle Latching)**: *Given* an overtaking large vehicle at 1.2 m lateral clearance ($y \le 3.0\text{ m}$), *When* 1 Hz aggregation samples, *Then* latches `RADAR_PASSING_DISTANCE_m` to 1.2 m and increments `RADAR_LARGE_VEHICLE_COUNT`.

### 1.4 System Invariants
* Guaranteed legacy V1 fallback; 3.5s starvation watchdog intact; non-blocking serialized GATT operations; thread safety across 4–8 Hz streams and 1 Hz latching; 100% 9-language translation parity.

---

## 2. Test Specification (TST-CON-030)

### Test Case 1: V2 Handshake Choreography & Fallback Invariant (`TST-CON-030.1`, `TST-CON-030.2`)
* **Scope**: Unit Test (`BTLERadarDeviceTest.kt`)
* **Preconditions**: Mock GATT peripheral discovering `6a4e3204` vs. discovering only `6a4e3203`.
* **Action**: Trigger characteristic discovery and negotiation callbacks.
* **Expected Result**: Verifies MTU 247 request and V2 CCCD enable for V2 device; verifies immediate fallback to V1 subscription when `6a4e3204` is absent.

### Test Case 2: V2 Packet Parsing & Multi-Target Mapping (`TST-CON-030.3`)
* **Scope**: Unit Test (`BTLERadarDeviceTest.kt`)
* **Preconditions**: Synthetic 2D V2 byte array with target ID, range 165.4 m, lateral offset -2.1 m, and truck classification.
* **Action**: Execute packet demuxing and parsing.
* **Expected Result**: Produces `RadarTarget` with exact range, speed, lateral offset, and `TRUCK_OR_BUS` classification.

### Test Case 3: Passing Distance Latching & Large Vehicle Count (`TST-CON-030.4`)
* **Scope**: Unit Test (`RadarWindowAggregatorTest.kt`)
* **Preconditions**: Target sequence approaching from 80 m, passing at 1.4 m lateral offset ($y \le 3.0\text{ m}$), and receding.
* **Action**: Feed target stream across 1 Hz window boundary.
* **Expected Result**: `RadarLatchedSnapshot` contains latched `passingDistanceMeters = 1.4` and incremented `largeVehicleCount`.

### Test Case 4: 9-Language Localization & Translation Parity (`TST-CON-030.7`)
* **Scope**: Localization Contract Test (`TranslationParityTest.kt`)
* **Goal**: Verify string presence and matching format specifiers across all 9 locales: EN, DE, ES, FR, IT, JA, NL, PL, PT.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-CON-030.8`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --tests "com.atrainingtracker.banalservice.devices.radar.*" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CON-030.1` | Unit | `BTLERadarDevice.onServicesDiscovered` | `REQ-CON-030.1` | Specified |
| `TST-CON-030.2` | Unit | `BTLERadarDevice.fallbackToV1` | `REQ-CON-030.2` | Specified |
| `TST-CON-030.3` | Unit | `BTLERadarDevice.parseV2ThreatPacket` | `REQ-CON-030.3` | Specified |
| `TST-CON-030.4` | Unit | `RadarWindowAggregator.onTargetsUpdated` | `REQ-CON-030.4` | Specified |
| `TST-CON-030.5` | Unit | `SensorType.RADAR_PASSING_DISTANCE_m` | `REQ-CON-030.5` | Specified |
| `TST-CON-030.6` | Unit | `RadarTrafficSimulator.generateV2Targets` | `REQ-CON-030.6` | Specified |
| `TST-CON-030.7` | Localization | `TranslationParityTest` | `REQ-CON-030.7` | Specified |
| `TST-CON-030.8` | Regression | Tier 1 Targeted Verification | `REQ-PRO-001` | Specified |
