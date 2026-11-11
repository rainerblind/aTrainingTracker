# Stage 2: Requirement & Test Specification - ATT-6706: Route BLE V2 (6a4e3204) notifications, register V2 radar SensorTypes, and sequence V2 MTU/LESC handshake

**Ticket**: [ATT-6706](https://rainerblind.atlassian.net/browse/ATT-6706)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar: BLE & ANT+ Acquisition, Live Edge Overlay, Audio/Haptic Alerts & Traffic Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.2`  
**Requirement Mapping**: `REQ-CON-030` (`REQ-CON-030.1` - `REQ-CON-030.7`)  
**Test Spec ID**: `TST-CON-030` (`TST-CON-030.1` - `TST-CON-030.8`)  
**Branch**: `subagent-ATT-1286---Radar-Flash-Agent-self-6fd93067`  
**Author**: AI Agent 1 (Flash Agent) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (`REQ-CON-030`)

### 1.1 Problem Statement & Rationale
Sprint `2026-46.1` audit of `ATT-5095` identified three defects blocking BLE V2 telemetry (`6a4e3204`):
1. `MyBTLEDevice.characteristicUpdate()` drops 100% of live V2 notifications because its characteristic equality filter strictly tests against `BluetoothConstants.getCharacteristicUUID(getDeviceType())` (statically mapped to V1 `6a4e3203`).
2. `RADAR_PASSING_DISTANCE_m` and `RADAR_LARGE_VEHICLE_COUNT` are omitted from `DeviceType.getSensorDeviceTypes()` (`DeviceType.java`), preventing Cockpit tile selection and sensor attribution.
3. `BTLERadarDevice.configureServices()` calls unmanaged `gatt.requestMtu(247)` racing directly against concurrent CCCD writes, omits LESC bonding verification (`BluetoothDevice.BOND_BONDED`), and lacks automatic `fallbackToV1()` on CCCD write failure.

### 1.2 Functional & Architectural Requirements
1. *BLE V2 Handshake Choreography (`REQ-CON-030.1`)*:
   • Upon peripheral connection and service discovery, if characteristic `6a4e3204` is present, `BTLERadarDevice` SHALL verify LESC bonding state (`BluetoothDevice.BOND_BONDED`), enqueue an MTU expansion request to 247 bytes (`RequestMtuOp`) in `mGattQueue`, execute the serialized GATT initialization handshake, and enable CCCD indications/notifications on `6a4e3204`.
   • `MyBTLEDevice` SHALL delegate measurement characteristic filtering to an overridable `isMeasurementCharacteristic(UUID)` method, enabling `BTLERadarDevice` to route both V1 (`6a4e3203`) and V2 (`6a4e3204`) telemetry packets to `measurementCharacteristicUpdate()`.
2. *Automatic Legacy Fallback Invariant (`REQ-CON-030.2`)*:
   • If characteristic `6a4e3204` is absent, or if peripheral is not bonded, or if MTU/handshake negotiation or CCCD descriptor write fails (e.g. non-`GATT_SUCCESS`), `BTLERadarDevice` SHALL immediately fall back to subscribing to legacy V1 characteristic `6a4e3203`.
3. *High-Resolution V2 Packet Parsing & Domain Mapping (`REQ-CON-030.3`)*:
   • The system SHALL parse multi-target V2 telemetry frames (header `0x01`), decoding `targetId`, range (0–175 m), signed approach speed, lateral offset, and vehicle size category into `RadarTarget`.
4. *Lateral Passing Clearance & Large Vehicle Aggregation (`REQ-CON-030.4`)*:
   • `RadarWindowAggregator` SHALL track minimum lateral passing distance during overtaking ($y \le 3.0\text{ m}$) and latch `RADAR_PASSING_DISTANCE_m` and `RADAR_LARGE_VEHICLE_COUNT` at each 1 Hz tick.
5. *Scalar Sensor Publishing & DeviceType Registration (`REQ-CON-030.5`)*:
   • `DeviceType.getSensorDeviceTypes()` SHALL map both `RADAR_PASSING_DISTANCE_m` and `RADAR_LARGE_VEHICLE_COUNT` to `DeviceType.BIKE_RADAR`.
6. *Traffic Simulator V2 Extension (`REQ-CON-030.6`)*:
   • `RadarTrafficSimulator` SHALL generate realistic V2 trajectory scenarios including vehicle size diversity and lane positioning.
7. *9-Language Localization Parity (`REQ-CON-030.7`)*:
   • All sensor names and short labels in `strings_radar.xml` SHALL maintain 100% key and format parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (V2 Notification Routing)**:
  * *Given* a connected Garmin Varia BLE peripheral streaming V2 frames on `6a4e3204`,
  * *When* `onCharacteristicChanged` is invoked on `mGattCallback`,
  * *Then* `MyBTLEDevice` delegates to `isMeasurementCharacteristic` and routes the payload to `BTLERadarDevice.measurementCharacteristicUpdate()`.
* **Criterion 2 (GATT Queue MTU Sequencing)**:
  * *Given* a connected Garmin Varia BLE peripheral exposing `6a4e3204`,
  * *When* service discovery completes,
  * *Then* `RequestMtuOp(247)` is enqueued in `mGattQueue` and completed before the CCCD descriptor write is dispatched.
* **Criterion 3 (LESC Bonding & CCCD Failure Fallback)**:
  * *Given* a peripheral exposing `6a4e3204` that is unbonded or encounters CCCD write failure (`status != GATT_SUCCESS`),
  * *When* handshake negotiation fails,
  * *Then* `BTLERadarDevice` automatically downgrades and subscribes to legacy V1 characteristic `6a4e3203`.
* **Criterion 4 (SensorType DeviceType Registration)**:
  * *Given* `SensorType.RADAR_PASSING_DISTANCE_m` or `SensorType.RADAR_LARGE_VEHICLE_COUNT`,
  * *When* `DeviceType.getSensorDeviceTypes(sensorType)` is evaluated,
  * *Then* the resulting list contains `DeviceType.BIKE_RADAR`.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-CON-030` (Garmin Varia BLE V2 High-Resolution Radar Stream & Vehicle Classification), targeting `MyBTLEDevice.java`, `BTLERadarDevice.java`, `DeviceType.java`, `ActivityType.java`, and `RadarTrafficSimulator.kt`.
2. *Historical Origin & Commit Trace*: Introduced in `ATT-5095` (commit `524e937d`) establishing BLE V2 packet decoding and window aggregation for 2D radar metrics.
3. *Root Reason for Existing Formulation*: The initial formulation assumed single-characteristic measurement filtering in `MyBTLEDevice.characteristicUpdate()` and did not serialize `requestMtu(247)` through `mGattQueue` nor verify LESC bonding state, leading to dropped V2 notifications and GATT command collisions.
4. *Preservation of Core Invariants*: Refining `REQ-CON-030` with `isMeasurementCharacteristic(UUID)` extraction, serialized `RequestMtuOp`, LESC bonding verification, and `DeviceType` registration guarantees 100% backward compatibility for legacy V1 radars (`6a4e3203`), seamless fallback on unbonded/failed handshakes, and full Cockpit UI tile availability without schema migrations.

---

## 2. Test Specification (`TST-CON-030`)

| Test Spec ID | Target File / Scope | Procedure & Verification Method | Expected Result |
| :--- | :--- | :--- | :--- |
| `TST-CON-030.1` | `BTLERadarDeviceTest.kt`, `MyBTLEDeviceTest.kt` | Emulate V2 discovery, verify `RequestMtuOp(247)` enqueued in `mGattQueue`, and route V2 notification via `isMeasurementCharacteristic` | `onMtuChanged` advances queue; V2 notification processed by `measurementCharacteristicUpdate` |
| `TST-CON-030.2` | `BTLERadarDeviceTest.kt` | Emulate unbonded state (`BOND_NONE`) or `onDescriptorWrite` non-zero status | `fallbackToV1` is triggered, subscribing to `6a4e3203` |
| `TST-CON-030.3` | `BTLERadarDeviceTest.kt` | Feed multi-target V2 telemetry frame | Range (0-175 m), lateral offset, approach speed, and vehicle size parsed |
| `TST-CON-030.4` | `RadarWindowAggregatorTest.kt` | Simulate close-pass overtaking ($y \le 3.0\text{ m}$) | `RADAR_PASSING_DISTANCE_m` latched; large vehicle count incremented |
| `TST-CON-030.5` | `DeviceTypeTest.kt`, `SensorTypeTest.kt` | Evaluate `DeviceType.getSensorDeviceTypes()` for passing distance and large vehicle count | Both return `DeviceType.BIKE_RADAR` |
| `TST-CON-030.6` | `RadarTrafficSimulatorTest.kt` | Trigger V2 simulated frames | Produces valid targets with lateral offset and size categories |
| `TST-CON-030.7` | `TranslationParityTest.kt` | Validate `strings_radar.xml` across all 9 locales | 100% string and format parity (EN, DE, ES, FR, IT, JA, NL, PL, PT) |
| `TST-CON-030.8` | Clean-Room Suite | Execute `testDebugUnitTest` under `/tmp/att_build_merge.lock` | 100% green pass rate |
