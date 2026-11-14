# Stage 5: Walkthrough & Verification - ATT-7579: Fix MyBTLEDevice snapshot pool overrun and post-disconnect stale target revival

**Ticket**: [ATT-7579](https://rainerblind.atlassian.net/browse/ATT-7579)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-CON-020` (`REQ-CON-020.1`), `REQ-CON-028` (`REQ-CON-028.4`)  
**Test Mapping**: `TST-CON-012` (`TST-CON-012.1`), `TST-CON-028` (`TST-CON-028.2`, `TST-CON-028.6`)  
**Branch**: `feat/ATT-7579`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Executive Summary & Verification Overview

This walkthrough documents the successful construction and verification of **ATT-7579**:
1. **Unshared Immutable Characteristic Snapshot Allocation (`REQ-CON-020.1`)**:
   - Eliminated the fixed 8-slot ring buffer pool (`SNAPSHOT_POOL_SIZE = 8`, `mCharacteristicPool`, `mPoolIndex`) in `MyBTLEDevice.java`.
   - `obtainSnapshotCharacteristic` now allocates an independent, unshared `BluetoothGattCharacteristic` with defensively cloned byte payload (`payload != null ? payload.clone() : null`).
   - Prevents buffer overrun and telemetry frame corruption during high-frequency 4–8 Hz radar streams under GC or main-thread Looper stalls.
2. **Post-Disconnect Stale Notification Dropping & Lifecycle Guarding (`REQ-CON-020.1`, `REQ-CON-028.4`)**:
   - In `MyBTLEDevice.disconnectFromGatt()`, assigned `mState = State.DISCONNECTED;` under `synchronized (mGattLock)`.
   - In `MyBTLEDevice.characteristicUpdate()`, wrapped the asynchronous execution of `measurementCharacteristicUpdate` on `mHandler` in a connection validation check under `synchronized (mGattLock)`: if `mIsShutDown || gatt != mBluetoothGatt || mState != State.CONNECTED_WITH_SERVICE`, the packet is dropped immediately.
   - In `BTLERadarDevice.measurementCharacteristicUpdate()`, added defensive entry guard `if (mIsShutDown || gatt != mBluetoothGatt || mState != State.CONNECTED_WITH_SERVICE) return;`.
   - Strictly prevents late-arriving queued packets from resurrecting `RadarConnectionState.CONNECTED`, repopulating stale targets, or re-arming the 3.5s watchdog timer following a disconnect.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Scope & Test Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-020.1` | `TST-CON-012.1` | Unshared snapshot allocations across 16 rapid notifications and stale runnable drop on disconnect (`BLEPayloadDispatchTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-028.4` | `TST-CON-028.2` | BTLERadarDevice disconnect stale target revival prevention test (`BTLERadarDeviceTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-028.6` | `TST-CON-028.6` | Clean-room targeted unit test suite (`bluetooth_le.*` & `radar.*`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-PRO-001` | Living requirement governance audit (`verify_requirement_governance.py`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Regression Test Suite
* **Command**: `python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --tests "com.atrainingtracker.banalservice.devices.radar.*" --max-workers=2`
* **Result**: `BUILD SUCCESSFUL` (All targeted tests passing with 0 failures, 0 errors, 0 skipped).

### Living Requirement Governance Audit
* **Command**: `python3 tools/verify_requirement_governance.py`
* **Result**: `PASS` (100% parity across living doc shards, 0 unmounted islands, zero architecture/system violations).

---

## 4. Invariant & Governance Verification

1. **Payload Immutability**: Each queued runnable holds an independent `BluetoothGattCharacteristic` snapshot with defensively cloned byte array.
2. **Thread Safety & Lock Invariant**: Lifecycle checks (`mIsShutDown`, `gatt != mBluetoothGatt`, `mState != CONNECTED_WITH_SERVICE`) execute under `mGattLock`.
3. **Peripheral Driver Compatibility**: Zero regression in `BTLEHeartRateDevice`, `BTLEBikePowerDevice`, `BTLEBikeDevice`, `BTLERunSpeedDevice`, and `BTLERadarDevice`.
4. **Watchdog Disconnect**: 3.5s starvation watchdog behavior preserved.
5. **Zero SQLite Migrations**: In-memory protocol driver abstraction requiring zero database changes.
