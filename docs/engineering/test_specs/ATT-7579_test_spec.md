# Stage 2 Test Specification: ATT-7579

**Ticket:** ATT-7579 (`[Bug] Fix MyBTLEDevice snapshot pool overrun and post-disconnect stale target revival`)  
**Sub-task:** [ATT-7926](https://rainerblind.atlassian.net/browse/ATT-7926) (`[Req & Test Spec]`)  
**Parent Epic:** ATT-1286 | **Sprint:** 2026-46.6 | **Target Release:** V5.0.0 | **Story Points:** 2 SP  
**Role:** `agent1` (Subagent Flash) / Review: `agent2` (Subagent Pro) | **Status:** In Progress (Stage 2)  

---

## 1. Traceability Matrix

| Requirement ID | Test Specification ID | Verification Method | Target Component | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-020.1` | `TST-CON-012.1` | Unit | `MyBTLEDevice.java` | Proposed |
| `REQ-CON-028.4` | `TST-CON-028.2` | Unit | `BTLERadarDevice.java` | Proposed |

---

## 2. Requirement Specification

### REQ-CON-020: Resilient Bluetooth LE & ANT+ Reconnection, Immutable Telemetry Dispatch, and Queue Integrity Architecture [Verified]
- **Tests**: `TST-CON-012`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MyBTLEDevice.java`, `DeviceManager.java`, `MyANTDevice.java`
- **Rationale**: The system (`MyBTLEDevice`, `DeviceManager`, `MyANTDevice`) SHALL guarantee immutable BLE notification payload dispatch to eliminate main-thread data corruption from Binder buffer reuse, provide deterministic search timeout lifecycle management with exponential backoff on disconnect to prevent GATT 133/257 storms, enforce search queue device matching in `DeviceManager` to prevent alien event preemption, and ensure clean handle release and non-locking recovery on ANT+ `DEAD` state (ATT-4007, ATT-7579, extending `REQ-CON-019`):
- **Refined Clause**:
  1. *Immutable BLE Notification Payload Dispatch & Connection Lifecycle Guarding (`REQ-CON-020.1`)*:
     - In `MyBTLEDevice.java`, the `BluetoothGattCallback` SHALL implement the Android 13+ (API 33) signature `onCharacteristicChanged(BluetoothGatt, BluetoothGattCharacteristic, byte[])` alongside the legacy fallback.
     - When a notification arrives, the raw payload bytes SHALL immediately be defensively cloned on the Binder callback thread (`(value != null ? value : characteristic.getValue()).clone()`).
     - A decoupled, unshared snapshot characteristic populated with the cloned byte buffer SHALL be dispatched to `mHandler`, guaranteeing that subsequent incoming BLE packets cannot mutate payload data before or during main-thread processing, eliminating ring-buffer pool overrun (`SNAPSHOT_POOL_SIZE = 8`) during main-thread stalls (ATT-7579).
     - Queued notification runnables on `mHandler` SHALL verify connection validity under `mGattLock`: if `mIsShutDown`, `gatt != mBluetoothGatt`, or `mState != State.CONNECTED_WITH_SERVICE`, the packet SHALL be dropped immediately without dispatching to `measurementCharacteristicUpdate`, preventing stale execution against disconnected GATT sessions (ATT-7579).

### REQ-CON-028: BLE Cycling Radar Protocol Driver, Serialized GATT Queue, Latch Aggregator & Parity [Verified]
- **Tests**: `TST-CON-028`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `DeviceType.java`, `SensorType.java`, `ActivityType.java`, `BluetoothConstants.java`, `MyBTLEDevice.java`, `BTSearchForNewDevicesEngine.java`, `BTLERadarDevice.java`, `RadarStateRepository.kt`, `RadarWindowAggregator.kt`, `EquipmentSensorOrdering.kt`, `strings_radar.xml`
- **Rationale**: Cyclists operating in traffic require real-time rearward situational awareness. To integrate Garmin Varia cycling radar units into BANALService, high-frequency (4–8 Hz) multi-target streams feed reactive UI via `RadarStateRepository` with a 3.5s fail-safe disconnect watchdog, while Cockpit and `TrackerService` require 1 Hz scalar metrics via a 1,000 ms rolling window aggregator with zero SQLite schema migrations and full 9-language localization parity:
- **Refined Clause**:
  4. *BTLERadarDevice V1 Threat Decoding, GATT Lifecycle & Reactive Repository (`REQ-CON-028.4`)*:
     - `BTLERadarDevice` SHALL transition `RadarStateRepository` to `RadarConnectionState.CONNECTING` when `startSearching()` is invoked.
     - `BTLERadarDevice` SHALL transition `RadarStateRepository` to `RadarConnectionState.CONNECTED` (with empty targets and armed 3.5s watchdog) once radar service and characteristic CCCD subscription are configured in `configureServices(BluetoothGatt)`.
     - Target updates SHALL be published to `RadarStateRepository` (`StateFlow<RadarState>`) at 4–8 Hz.
     - A 3.5s watchdog timer SHALL transition `RadarState` to `DISCONNECTED_ERROR` if no valid packets arrive or GATT disconnects.
     - Upon GATT disconnection (`disconnectFromGatt()`), `RadarStateRepository` SHALL transition to `RadarConnectionState.DISCONNECTED` with empty targets; late-arriving queued measurement runnables on `mHandler` SHALL be dropped under `mGattLock` when `gatt != mBluetoothGatt` or `mState != State.CONNECTED_WITH_SERVICE`, strictly preventing post-disconnect stale target revival and false reconnection (ATT-7579).

---

## 3. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-CON-020.1` (Immutable BLE Notification Payload Dispatch) in `MyBTLEDevice.java`.
   - `REQ-CON-028.4` (GATT Lifecycle & Reactive Repository) in `BTLERadarDevice.java`.
2. **Historical Origin & Commit Trace**:
   - `ATT-4007` (commit `0234bfc0`): Added `SNAPSHOT_POOL_SIZE = 8` to mitigate garbage collection overhead.
   - `ATT-5083`: Introduced `BTLERadarDevice` with 4–8 Hz high-rate streams and reactive `RadarStateRepository`.
3. **Root Reason for Existing Formulation**:
   - In `ATT-4007`, sensor streams were assumed to be 1 Hz (e.g. HRM or Cadence), where an 8-slot ring buffer appeared large enough to bridge brief 1s GC pauses. With 4–8 Hz radar streams, the 8-slot buffer wraps around within 1s under normal UI load, overwriting queued characteristic payloads.
   - Asynchronous Handler runnables lacked connection checks because disconnection was assumed to cancel all pending work; however, `removeCallbacksAndMessages(null)` is called on device teardown (`shutDown()`), not during mid-session disconnects.
4. **Preservation of Core Invariants**:
   - Zero payload mutation during main-thread processing.
   - Compatibility across all existing BLE peripherals (`BTLEHeartRateDevice`, `BTLEBikePowerDevice`, `BTLEBikeDevice`, `BTLERunSpeedDevice`).
   - 3.5s fail-safe starvation watchdog preserved.
   - Zero SQLite schema migrations.

---

## 4. Acceptance Criteria (Given-When-Then)

### AC-CON-020.1: Immutable Characteristic Snapshot Allocation
- *Given* 16 rapid BLE notifications arriving on the Binder thread while the main thread looper is blocked,
- *When* the main thread looper drains and processes the queued runnables,
- *Then* all 16 payloads are preserved in exact sequence with their original byte arrays, and no earlier snapshot is overwritten by a later packet.

### AC-CON-020.2: Stale Notification Runnable Dropping on Disconnect
- *Given* measurement notification runnables queued on `mHandler`,
- *When* `disconnectFromGatt()` executes before the runnables run,
- *Then* the runnables detect `gatt != mBluetoothGatt` or `mState != State.CONNECTED_WITH_SERVICE` and terminate immediately without dispatching to `measurementCharacteristicUpdate`.

### AC-CON-028.4: Post-Disconnect Stale Target Revival Prevention
- *Given* an active BLE radar session streaming targets,
- *When* `BTLERadarDevice.disconnectFromGatt()` is invoked and transitions `RadarStateRepository` to `DISCONNECTED`,
- *Then* subsequent execution of any stale measurement runnables on `mHandler` does not resurrect `RadarConnectionState.CONNECTED`, does not repopulate active targets, and does not re-arm `mWatchdogRunnable`.

---

## 5. Detailed Test Specification

### TST-CON-012.1: BLE Snapshot Immutability & Overrun Verification
- **File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/BLEPayloadDispatchTest.kt`
- **Test Cases**:
  1. `testCharacteristicSnapshot_distinctAllocationsPreservePayloads()`:
     - Invoke `obtainSnapshotCharacteristic` 16 times in succession with distinct payloads (`byteArrayOf(0..15)`).
     - Verify each returned snapshot is non-null.
     - Verify all 16 snapshots retain their distinct payloads (`assertArrayEquals(byteArrayOf(i), snapshots[i].value)`).
     - Verify snapshots do not share identity across modulo-8 boundaries (`assertNotSame(snapshots[0], snapshots[8])`).
  2. `testMeasurementRunnable_droppedWhenGattDisconnected()`:
     - Mock connected state with `mBluetoothGatt = gatt` and `mState = State.CONNECTED_WITH_SERVICE`.
     - Queue a measurement notification runnable.
     - Simulate disconnect (`mBluetoothGatt = null`, `mState = State.DISCONNECTED`).
     - Drain the queued runnable and verify `measurementCharacteristicUpdate` is NOT called.

### TST-CON-028.2: BTLERadarDevice Disconnect Stale Target Revival Prevention
- **File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/BTLERadarDeviceTest.kt`
- **Test Cases**:
  1. `testDisconnectFromGatt_dropsLateQueuedMeasurementRunnable()`:
     - Initialize connected radar device streaming targets (`processTargets`).
     - Queue a measurement packet on the handler.
     - Call `radarDevice.disconnectFromGatt()`.
     - Verify `RadarStateRepository.getState().connectionState == RadarConnectionState.DISCONNECTED`.
     - Execute the queued measurement runnable on the handler.
     - Verify `RadarStateRepository.getState().connectionState` remains `DISCONNECTED` and targets remain empty.

---

## 6. Living Documentation Parity Checklist
- [x] Living doc `docs/requirements/req_tracking_cockpit.md` updated with `REQ-CON-020.1` and `REQ-CON-028.4` refinements and SWE.1 BP2 attributes.
- [x] Living doc `docs/tests/tst_tracking_cockpit.md` updated with `TST-CON-012.1` and `TST-CON-028.2` procedures.
- [x] `verify_requirement_governance.py --sync-indexes` passed with 100% parity.
- [x] `verify_requirement_governance.py` passed with 0 errors and valid Chesterton's Fence audit.
