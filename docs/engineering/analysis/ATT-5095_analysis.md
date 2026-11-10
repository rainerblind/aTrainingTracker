# Stage 1 Analysis: ATT-5095 - Garmin Varia BLE V2 High-Resolution Radar Stream & Vehicle Classification

**Ticket**: [ATT-5095](https://rainerblind.atlassian.net/browse/ATT-5095)  
**Sub-task**: [ATT-6595](https://rainerblind.atlassian.net/browse/ATT-6595) (`[Analysis]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-5095`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
Garmin Varia RearVue 820 introduces a high-resolution 2D radar stream on BLE characteristic `6a4e3204-667b-11e3-949a-0800200c9a66` (range up to 175 m, lateral offset, speed vectors, vehicle sizing).
1. **High-Resolution 2D Telemetry**: Cyclists need to know if an approaching car is overtaking in an adjacent lane or approaching on a collision course, and classify vehicle sizes (Bikes/Motorcycles vs. Cars vs. Trucks/Busses).
2. **Close-Pass Measurement**: Capture exact lateral passing clearance ($|x|$ at $y \to 0\text{ m}$) like an *OpenBikeSensor*.
3. **BLE V2 Pre-Handshake**: Characteristic `6a4e3204` requires LESC bonding, MTU 247 expansion, and a serialized GATT initialization handshake.
4. **Resilient Fallback**: Must automatically fall back to V1 stream `6a4e3203` on legacy units (RTL515/516/315/715) or unbonded devices.

---

## 2. Root Cause Analysis, Gap Analysis & Independent Peer Review
* **Architectural Gap**: `BTLERadarDevice.java` only subscribes to V1 `6a4e3203` (1D scalar range, 140 m), discarding lateral offset and vehicle classification.
* **Independent Peer Review & Verification (4-Augen-Prinzip — ATT-3534)**:
  - *Handshake & GATT Sequencing*: Unlocking `6a4e3204` requires an active LESC bond (`BluetoothDevice.BOND_BONDED`), MTU request (247), battery CCCD write, and session handshake via `MyBTLEDevice`'s serialized GATT queue.
  - *Automatic Fallback Invariant*: If characteristic `6a4e3204` is absent (RTL515/RTL516) or bond establishment fails, `BTLERadarDevice` immediately subscribes to V1 `6a4e3203`, ensuring 100% backward compatibility.
  - *1 Hz Window Latching*: `RadarWindowAggregator` must track minimum lateral passing distance during overtaking ($y \le \text{PASSING\_THRESHOLD\_METERS}$) and commit `RADAR_PASSING_DISTANCE_m` and `RADAR_LARGE_VEHICLE_COUNT` at each 1 Hz tick.

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit
* **In-Scope Goals (ATT-5095 - 3 SP)**:
  - Implement BLE V2 handshake choreography (LESC bond check, MTU 247, battery/session handshake, V2 CCCD subscription).
  - Implement automatic fallback to V1 `6a4e3203` when V2 is unavailable.
  - Parse V2 packets into enriched `RadarTarget` (`lateralOffsetMeters`, `vehicleSizeCategory`, `laneState`).
  - Add scalar `SensorType`s: `RADAR_PASSING_DISTANCE_m` and `RADAR_LARGE_VEHICLE_COUNT`.
  - Extend `RadarWindowAggregator` to latch minimum lateral passing distance and count large vehicles.
  - Extend `RadarTrafficSimulator` to generate V2 trajectories (lane changes, close passes, large vehicles).
  - 9-language localization parity in `strings_radar.xml` (zero inline emojis).
* **Out-of-Scope Non-Goals**:
  - `ATT-5088`: SQLite post-workout analytics and Strava close-pass sync.
  - `ATT-5096`: Visual 2D lane rendering on `RadarEdgeOverlay`.
  - `ATT-5102`: ANT+/BLE tail light control profiles.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Shard**: `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md`.
* **Refined Requirements**: Net-new `REQ-CON-030` (*Garmin Varia BLE V2 High-Resolution Radar Stream & Vehicle Classification*) and `TST-CON-030`.
* **Chesterton's Fence**: V1 `6a4e3203` parsing and the 3.5s disconnect watchdog (`mWatchdogRunnable`) must remain fully intact. V2 additions extend the data pipeline without breaking legacy V1 packet processing.

---

## 5. Architectural Strategy & System Invariants
* **Components**: `BTLERadarDevice.java`, `RadarTarget.kt`, `RadarWindowAggregator.kt`, `RadarTrafficSimulator.kt`, `SensorType.java`.
* **Core Invariants**:
  1. 100% legacy radar compatibility: Seamless fallback to V1 for RTL515, RTL516, RVR315, and RCT715.
  2. Non-blocking GATT operations: All writes/reads routed through `MyBTLEDevice` queue with timeout auto-advance.
  3. Thread safety: All state updates to `RadarStateRepository` and `RadarWindowAggregator` synchronized.
  4. 100% 9-language translation parity in `strings_radar.xml`.
* **Risk Rating**: **MEDIUM** (BLE bonding, MTU negotiation, and proprietary V2 packet structure; risk minimized by guaranteed fallback to tested V1 driver).
