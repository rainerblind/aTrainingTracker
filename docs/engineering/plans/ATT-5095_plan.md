# Stage 3 Implementation Plan: ATT-5095 - Decode Garmin Varia BLE V2 Radar Stream & Vehicle Classification

**Ticket**: [ATT-5095](https://rainerblind.atlassian.net/browse/ATT-5095) | **Sub-task**: [ATT-6600](https://rainerblind.atlassian.net/browse/ATT-6600) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-5095`  
**Requirement Mapping**: `REQ-CON-030` (`030.1`–`030.7`) | **Test Spec**: `TST-CON-030` (`030.1`–`030.8`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Technical Architecture & SWE.2 Design
* **GATT Protocol & V2 Handshake (`BTLERadarDevice.java`, `BluetoothConstants.java`)**:
  - Define `CHARACTERISTIC_RADAR_V2` (`6a4e3204-667b-11e3-949a-0800200c9a66`).
  - Handshake choreography (`REQ-CON-030.1`): verify LESC bonding, request MTU 247, execute serialized session handshake, and subscribe to `6a4e3204`.
  - Fallback invariant (`REQ-CON-030.2`): automatic, zero-delay fallback to legacy V1 `6a4e3203` if `6a4e3204` is absent or handshake negotiation times out (5.0s).
* **Enriched 2D Domain Model (`RadarState.kt`)**:
  - Enums: `VehicleSizeCategory` (`UNKNOWN_OR_LIGHT`, `CAR`, `TRUCK_OR_BUS`), `LaneState` (`UNKNOWN`, `IN_RIDER_LANE`, `CHANGING_LANE`, `PASSING_LANE`).
  - Extend `RadarTarget` with `@JvmOverloads constructor(...)`: add `lateralOffsetMeters: Float?`, `longitudinalSpeedMps: Float?`, `lateralSpeedMps: Float?`, `vehicleWidthMeters: Float?`, `vehicleLengthMeters: Float?`, `vehicleSizeCategory`, and `laneState` (preserving 100% Java call-site compatibility).
* **1 Hz Passing Clearance & Large Vehicle Latch (`RadarWindowAggregator.kt`, `SensorType.java`)**:
  - Latch minimum lateral clearance $|x|$ during vehicle passing window ($y \le 3.0\text{ m}$) into `RADAR_PASSING_DISTANCE_m` (`REQ-CON-030.4,5`).
  - Track cumulative `LARGE` vehicle passes into `RADAR_LARGE_VEHICLE_COUNT`.
* **Traffic Simulator V2 Extension (`RadarTrafficSimulator.kt`)**:
  - Enhance scenarios with realistic 2D trajectory vectors, lateral lane offsets, and diverse vehicle sizes (`REQ-CON-030.6`).
* **UI & Localization**:
  - No new UI components required (raw telemetry acquisition and scalar sensor pipeline).
  - 100% 9-language translation parity in `strings_radar.xml` (`REQ-CON-030.7`).

---

## 2. Atomic Implementation Steps

| Step | Component | Target Files | Action & Core Invariants | Traceability |
| :--- | :--- | :--- | :--- | :--- |
| **1** | Domain Models | `RadarState.kt`<br>`BluetoothConstants.java` | Add `VehicleSizeCategory`, `LaneState`; enrich `RadarTarget` with `@JvmOverloads`; declare `CHARACTERISTIC_RADAR_V2`. | `REQ-CON-030.3`<br>`TST-CON-030.3` |
| **2** | Scalar SensorTypes | `SensorType.java` | Add `RADAR_PASSING_DISTANCE_m` and `RADAR_LARGE_VEHICLE_COUNT` enums and formatters. | `REQ-CON-030.5`<br>`TST-CON-030.5` |
| **3** | Window Aggregator | `RadarWindowAggregator.kt` | Implement lateral passing clearance latch ($y \le 3.0\text{ m}$) and cumulative large vehicle counter. | `REQ-CON-030.4`<br>`TST-CON-030.4` |
| **4** | BLE V2 Driver | `BTLERadarDevice.java` | Implement MTU 247 request, V2 handshake, automatic V1 fallback, V2 packet demuxing, and sensor wiring. | `REQ-CON-030.1-3`<br>`TST-CON-030.1-3` |
| **5** | Traffic Simulator | `RadarTrafficSimulator.kt` | Extend simulator scenarios to emit V2 target frames with lateral offsets and vehicle classifications. | `REQ-CON-030.6`<br>`TST-CON-030.6` |
| **6** | Unit & Contract Tests | `BTLERadarDeviceTest.kt`<br>`RadarWindowAggregatorTest.kt`<br>`SensorTypeTest.kt` | Author unit tests verifying handshake, fallback, 2D packet parsing, passing clearance latching, and sensor types. | `REQ-CON-030.1-5`<br>`TST-CON-030.1-5` |
| **7** | Parity & Regression | `TranslationParityTest.kt`<br>Targeted Suite | Verify 9-language parity in `strings_radar.xml` and execute targeted test suite (`banalservice.devices.*`, `build.*`). | `REQ-CON-030.7`<br>`TST-CON-030.7,8` |

---

## 3. System Invariants & Rollback Safety
* **Code Lock (Rule 4)**: No production edits until Gate 3 audit passes (`tools/jira_util.py check-gate`).
* **Legacy V1 Safety**: Unbonded devices and legacy radar hardware (RTL515/516/315/715) fall back to V1 without connection loss.
* **Non-Blocking Serialized GATT**: All GATT commands routed through `MyBTLEDevice` queue with timeout auto-advance.
* **Database & Schema**: Zero SQLite schema migrations; purely in-memory pipeline and scalar `SensorType` latching.
* **Risk Rating**: **MEDIUM** (Proprietary Garmin BLE packet parsing and MTU/bonding negotiation; fully mitigable via V1 fallback).
