# Stage 3: Implementation Plan - ATT-7543: Real-Hardware Garmin Varia / RearVue 820 BLE V1 Decoding & GATT Lifecycle

**Ticket**: [ATT-7543](https://rainerblind.atlassian.net/browse/ATT-7543) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7543`  
**Requirement Mapping**: `REQ-CON-028` (`REQ-CON-028.3`, `REQ-CON-028.4`)  
**Test Mapping**: `TST-CON-028` (`TST-CON-028.2`, `TST-CON-028.5`, `TST-CON-028.6`, `TST-CON-028.7`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Description & Background
Physical Garmin Varia / RearVue 820 radar units fail to show `RadarEdgeOverlay` due to:
1. `BTLERadarDevice` missing `RadarStateRepository` transitions to `CONNECTING` in `startSearching()` and `CONNECTED` in `configureServices()`.
2. Real V1 characteristic `6a4e3203` notifications using sequence header `(seq << 4) | 0x02` (`0x02..0xF2`), 1-byte heartbeats, 6-byte sector diagnostics, and stateful closing speed ($\Delta d / \Delta t$) with track validity bit 7 (`vid >= 0x80`).
3. Scan filters in `BTSearchForNewDevicesEngine` omitting Garmin Member Service UUID `0xFE1F`.

---

## 2. Requirements & Verification Traceability
* **`REQ-CON-028.3` (Scan Filter Discovery)**: Add Garmin 16-bit UUID `0xFE1F` to `buildScanFilters(BIKE_RADAR)` $\to$ `TST-CON-028.7`.
* **`REQ-CON-028.4` (GATT Lifecycle & V1 Decoding)**: Implement `CONNECTING`/`CONNECTED` transitions, watchdog arming, sequence framing, 1-byte heartbeat / 6-byte sector handling, invalid target filtering, and stateful speed calculation with `STALE_MS = 2000L` $\to$ `TST-CON-028.2`.
* **`REQ-CON-028.6` (Localization Parity)**: Maintain 100% 9-language string parity $\to$ `TST-CON-028.5`.
* **`REQ-PRO-001` (Clean-Room Regression)**: BANAL unit test suite 100% pass rate $\to$ `TST-CON-028.6`.

### Requirement Archaeology & Chesterton's Fence Audit
- *Original Requirement ID & Target*: Refine `REQ-CON-028.3` and `REQ-CON-028.4` in `docs/requirements/req_tracking_cockpit.md`.
- *Historical Origin & Commit Trace*: `ATT-5083` (commit `dfbe3979`) established initial driver; `ATT-6701` unified GATT queue; `ATT-7543` aligns with real hardware capture frames.
- *Root Reason for Existing Formulation*: Simulator assumed `0x00` header, speed in byte 3, and state update only on packet arrival. Real hardware uses sequence nibbles and stateful speed derivation.
- *Preservation of Core Invariants*: 3.5s starvation watchdog, simulator preemption, zero SQLite migrations, and BLE V2/ANT+ streams remain 100% preserved.

---

## 3. System Invariants & Preserved Behavior
1. **Watchdog Integrity**: 3.5s timeout unconditionally transitions to `DISCONNECTED_ERROR` upon packet starvation.
2. **Simulation Preemption**: Physical radar packet arrival preempts `RadarTrafficSimulator` immediately (REQ-CON-029.3).
3. **Database & Schema Invariant**: Zero SQLite schema migrations; 1 Hz rolling latch aggregation preserved.
4. **V2 & ANT+ Independence**: BLE V2 (`6a4e3204`) and ANT+ Radar (`0x28`) parsing paths remain unaffected.
5. **UI Consistency**: No UI layout changes (overlay visibility restored via connectionState).

---

## 4. Step-by-Step Implementation Sequence

### Step 1: Garmin Member UUID in `BluetoothConstants.java` & Scan Filters in `BTSearchForNewDevicesEngine.java`
- Define `UUID_SERVICE_GARMIN_MEMBER = UUID.fromString("0000fe1f-0000-1000-8000-00805f9b34fb")`.
- In `BTSearchForNewDevicesEngine.buildScanFilters(BIKE_RADAR)`: include service UUID and service data filters for `UUID_SERVICE_GARMIN_MEMBER` alongside `UUID_SERVICE_RADAR`.
- Test: `BTSearchForNewDevicesEngineTest.kt` (`TST-CON-028.7`).

### Step 2: Connection Lifecycle & Watchdog Arming in `BTLERadarDevice.java`
- Override `startSearching()`: invoke `super.startSearching()` and set `RadarConnectionState.CONNECTING`.
- In `configureServices()`: set `RadarConnectionState.CONNECTED` and arm `mWatchdogRunnable` (3.5s) upon CCCD subscription.
- Test: `BTLERadarDeviceTest.kt` (`TST-CON-028.2`).

### Step 3: Real V1 Framing, Link-Alive Demuxing & Stateful Track Ageing in `BTLERadarDevice.java`
- Maintain `Map<Integer, TrackState>` (`rangeMeters`, `timestampMs`) with `STALE_TRACK_TIMEOUT_MS = 2000L`.
- Recognize 1-byte heartbeats (`len == 1 && (b & 0x0F) == 0x02`) and 6-byte sector diagnostics (`len == 6 && b == 0x06`) as link-alive frames that re-arm watchdog, prune stale tracks, and publish `CONNECTED`.
- Parse threat triplets (`len >= 4 && (len - 1) % 3 == 0 && (b & 0x0F) == 0x02`): filter out `vid < 0x80`, `vid == 0xFD`, and `dist == 0xFF`.
- Compute closing speed $\Delta d / \Delta t$ from track history, classify `FAST_APPROACHING` ($\ge 15.0\text{ m/s}$) vs `APPROACHING`, and prune expired tracks.
- Retain backwards-compatibility for legacy synthetic `0x00` payloads.
- Test: `BTLERadarDeviceTest.kt` (`TST-CON-028.2`).

### Step 4: Verification & Regression Suite
- Run targeted unit tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*"
  ```
- Run requirement governance audit:
  ```bash
  python3 tools/verify_requirement_governance.py --text-file docs/engineering/plans/ATT-7543_plan.md
  ```

---

## 5. Verification & Rollback Plan
* **Verification**: `BTLERadarDeviceTest` passing real RearVue 820 sequences; `BTSearchForNewDevicesEngineTest` passing scan filter assertions.
* **Rollback Plan**: Atomic commit on `feature/ATT-7543` reverts cleanly via `git reset --hard feature/ATT-7543~1`.
