# Stage 1 Analysis: ATT-5083 - BLE Cycling Radar Protocol Driver, Domain Model, Scalar SensorTypes & Bike Integration

**Ticket**: [ATT-5083](https://rainerblind.atlassian.net/browse/ATT-5083)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar: BLE & ANT+ Acquisition, Live Edge Overlay, Audio/Haptic Alerts & Traffic Analytics*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-5083`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
Under Epic ATT-1286, ATT-5083 establishes the foundational Bluetooth Low Energy (BLE) Cycling Radar subsystem:
1. **Missing Radar Protocol & Device Support**: BANALService lacks cycling radar drivers. Connecting Garmin Varia (RearVue 820, RTL515, RCT715) requires GATT service `6a4e3200-667b-11e3-949a-0800200c9a66` and V1 characteristic `6a4e3203-667b-11e3-949a-0800200c9a66`.
2. **Serialized GATT Operation Queue in `MyBTLEDevice`**: Current GATT interaction only queues characteristic reads; descriptor writes (CCCD) execute ad-hoc, causing silent drops during multi-characteristic subscriptions or control-point writes.
3. **Resilient BLE Discovery**: Cycling radars advertise service UUIDs across primary advertisement PDUs, scan responses, or service data. `BTSearchForNewDevicesEngine` must match `ScanRecord` UUIDs resiliently across diverse Android chipsets.
4. **Reactive Multi-Target Stream vs. 1 Hz Persistence Latch**: High-rate (4–8 Hz) target data must feed a reactive `RadarStateRepository` (`StateFlow<RadarState>`) with a 3.5s disconnect watchdog, while Cockpit and `TrackerService` require 1 Hz latched scalar metrics (`RADAR_THREAT_COUNT`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`, `RADAR_VEHICLE_COUNT`) without SQLite schema migrations.
5. **Equipment Matrix & Localization**: Bike-only sensor binding in `EquipmentSensorMatrixScreen` and 9-language localization via domain-partitioned `strings_radar.xml` (Rule 9, `REQ-PRO-041.3`).

---

## 2. Root Cause & Gap Analysis & Independent Peer Review
* **Architectural Gap**:
  - `DeviceType.java` and `SensorType.java`: No definitions for `BIKE_RADAR` or radar scalar telemetry metrics.
  - `MyBTLEDevice.java`: Lacks a serialized GATT operation queue for descriptor writes and sequential multi-characteristic configurations.
  - `BTSearchForNewDevicesEngine.java`: Relies strictly on platform `ScanFilter` which can miss 128-bit UUIDs placed in scan responses.
  - V1 Packet Demuxing: Characteristic `6a4e3203` interleaves threat packets (`1 + 3*N` bytes) with heartbeats and sector packets; `byte[0]` header decoding is required to parse immutable `RadarTarget` records safely.
* **Independent Peer Review & Verification (4-Augen-Prinzip — ATT-3534)**:
  - *Database Migration Audit*: Inspected `WorkoutSamplesDatabaseManager.makeColumns()` and `WorkoutSummariesDatabaseManager.TABLE_EXTREMA_VALUES`. Both dynamically derive column definitions and extrema rows from `SensorType.values()`. Adding radar scalar types requires **zero SQLite schema migrations** (`DB_VERSION` unchanged).
  - *Equipment Matrix Audit*: Inspected `DevicesDatabaseManager.isBikeSensor()`. Setting `DeviceType.BIKE_RADAR` with `BSportType.BIKE` automatically integrates radar into Bike equipment tables in `EquipmentSensorMatrixScreen.kt`.
  - *Localization Isolation*: Rule 9 domain partitioning (`res/values*/strings_radar.xml`) prevents merge conflicts across concurrent feature branches.

---

## 3. Scope Grounding & Non-Goals (ATT-1250)
* **In-Scope Goals (ATT-5083 - 3 SP)**:
  - Add `DeviceType.BIKE_RADAR` and scalar `SensorType`s (`RADAR_THREAT_COUNT`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`, `RADAR_VEHICLE_COUNT`).
  - Upgrade `MyBTLEDevice` with a serialized GATT operation queue for descriptor writes and sequential commands.
  - Implement resilient scan-record UUID matching in `BTSearchForNewDevicesEngine`.
  - Create `BTLERadarDevice` driver parsing V1 (`6a4e3203`) packets into `RadarTarget`s and driving reactive `RadarStateRepository`.
  - Implement 1 Hz window aggregator/latch for scalar sensors and passing-vehicle counter hysteresis.
  - Provide complete 9-language localization in `strings_radar.xml`.
* **Out-of-Scope Non-Goals**:
  - `ATT-5087`: Built-in Radar Traffic Simulator (separate ticket).
  - `ATT-5085`: Global Cockpit vertical edge overlay UI (separate ticket).
  - `ATT-5086` / `ATT-5119`: Audio chimes and haptic threat alerts.
  - `ATT-5084`: ANT+ Radar (`0x28`) profile driver.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Shards**: Living doc shard `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md`.
* **Refined Requirements**: Introduces net-new `REQ-CON-028` (*BLE Cycling Radar Protocol Driver, Serialized GATT Queue & Scalar Telemetry*) and `TST-CON-028`, extending `REQ-CON-002` (BLE Sensor Support) and `REQ-CON-020` (Resilient BLE Queue Integrity).
* **Preservation of Core Invariants**: Existing BLE sensors (HRM, Power, Speed, Cadence) remain unaffected; GATT read queue behavior preserved; zero database migrations.

---

## 5. System Invariants & Risk Assessment
* **Core Invariants**: Sequential GATT execution without dropped callbacks; fail-safe `DISCONNECTED_ERROR` state upon 3.5s timeout; zero SQLite schema alterations; 100% 9-language parity.
* **Risk Rating**: **MEDIUM** (Introduces new BLE device type and serialized GATT queue modifications in base `MyBTLEDevice`; mitigated by non-breaking backward compatibility for existing BLE devices and thorough unit tests).
