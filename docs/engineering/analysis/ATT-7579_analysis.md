# Stage 1: Problem Domain & Root Cause Analysis - ATT-7579

**Ticket**: [ATT-7579](https://rainerblind.atlassian.net/browse/ATT-7579)  
**Sub-task**: [ATT-7925](https://rainerblind.atlassian.net/browse/ATT-7925) (`[Analysis]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Story Points**: 2 SP  
**Role**: `agent1` (Subagent Pro) / Review: `agent2` (Subagent Flash)  
**Status**: In Progress (Stage 1)  

---

## 1. Problem Statement & Forensic Root Cause Analysis

### Finding 1: Characteristic Snapshot Pool Overrun Under Main-Thread Stalls (`MyBTLEDevice.java`)
In `MyBTLEDevice.java` (lines 222–225 and 445–463), incoming BLE notifications are processed via `obtainSnapshotCharacteristic(source, payload)`. To avoid GC allocations, `MyBTLEDevice` maintains a fixed-size ring-buffer pool (`SNAPSHOT_POOL_SIZE = 8`) per characteristic UUID with a shared `AtomicInteger mPoolIndex`. When a packet arrives, `obtainSnapshotCharacteristic` selects `pool[idx]` (where `idx = ((rawIndex % 8) + 8) % 8`), sets `snapshot.setValue(payload)`, and returns the instance. Then `characteristicUpdate` posts a `Runnable` to `mHandler` passing `snapshot`.

*Root Cause*:
1. At high telemetry rates (such as 4–8 Hz cycling radar streams on `6a4e3203` or `6a4e3204`), 8 packets arrive in just 1.0–2.0 seconds.
2. If the Android main looper experiences a transient stall (e.g., Compose UI layout, database I/O, garbage collection pause), more than 8 notifications accumulate in `mHandler`'s queue.
3. The pool index wraps around modulo 8, causing `obtainSnapshotCharacteristic` to call `setValue(payload)` on the same `BluetoothGattCharacteristic` instances that are still queued in the `Handler` message queue awaiting execution.
4. When the main thread eventually drains the queue, earlier runnables read the mutated, overwritten byte arrays of later packets, resulting in packet corruption, duplicate telemetry frames, and lost intermediate targets.

### Finding 2: Post-Disconnect Stale Target Revival (`BTLERadarDevice.java` & `MyBTLEDevice.java`)
In `MyBTLEDevice.java` (lines 926–931), `characteristicUpdate` dispatches `measurementCharacteristicUpdate(gatt, characteristic)` unconditionally within an un-guarded `Runnable`:
```java
mHandler.post(new Runnable() {
    @Override
    public void run() {
        measurementCharacteristicUpdate(gatt, characteristic);
    }
});
```
When peripheral disconnect occurs, `BTLERadarDevice.disconnectFromGatt()` executes: it cancels the watchdog, clears `mTrackHistory`, transitions `RadarStateRepository` to `DISCONNECTED` with empty targets, resets scalar sensors, and calls `super.disconnectFromGatt()`, which sets `mBluetoothGatt = null`.

*Root Cause*:
1. If incoming measurement notifications were received on the Binder thread immediately prior to disconnection, their `Runnable`s remain queued in `mHandler`.
2. When those runnables execute on `mHandler` *after* `disconnectFromGatt()`, `measurementCharacteristicUpdate` calls `processTargets(targets)`.
3. In `BTLERadarDevice.processTargets(targets)`, the method unconditionally publishes `RadarState(RadarConnectionState.CONNECTED, ...)` to `RadarStateRepository` and rearms `mWatchdogRunnable` for 3,500 ms.
4. Consequently, a disconnected radar peripheral is resurrected as `CONNECTED` with stale target ghosts on the Cockpit display until the 3.5s watchdog times out.

---

## 2. Independent Peer Review & Verification

1. **Pool Size & Overrun**: Inspected `MyBTLEDevice.java` lines 222–225 (`SNAPSHOT_POOL_SIZE = 8`) and `obtainSnapshotCharacteristic`. Confirmed that `snapshot.setValue(payload)` mutates the shared pool instance in-place. Confirmed in `BLEPayloadDispatchTest.kt` lines 304–306 that `snapshots[0] === snapshots[8]`, proving in-place mutation.
2. **Runnable Lifecycle Guarding**: Inspected `MyBTLEDevice.java` lines 926–931. Confirmed no checks for `mIsShutDown`, `gatt == mBluetoothGatt`, or `mState == State.CONNECTED_WITH_SERVICE` exist inside the posted `Runnable`.
3. **Target Resurrection**: Inspected `BTLERadarDevice.java` lines 461–486 and 550–577. Confirmed that running `processTargets()` on an obsolete runnable overwrites `RadarConnectionState.DISCONNECTED` with `CONNECTED`.

---

## 3. Proposed Architecture & Scope Bounding

1. **Decoupled Immutable Characteristic Snapshots**:
   - Eliminate `SNAPSHOT_POOL_SIZE`, `mCharacteristicPool`, and `mPoolIndex`.
   - Update `obtainSnapshotCharacteristic` to allocate a fresh, unshared `BluetoothGattCharacteristic(uuid, properties, permissions)` with defensively cloned byte payload (`payload != null ? payload.clone() : null`).
   - At 1–8 Hz, allocating small ephemeral objects is negligible for Android ART GC (which collects short-lived young generation objects in microsecond pauses) while guaranteeing 100% thread safety and zero buffer overwrite.
2. **Synchronized Connection Lifecycle Guarding**:
   - In `MyBTLEDevice.characteristicUpdate`, guard the posted `Runnable` under `mGattLock`:
     ```java
     synchronized (mGattLock) {
         if (mIsShutDown || gatt != mBluetoothGatt || mState != State.CONNECTED_WITH_SERVICE) {
             return;
         }
     }
     ```
   - In `MyBTLEDevice.disconnectFromGatt()`, update `mState = State.DISCONNECTED` under `mGattLock` alongside `mBluetoothGatt = null`.
   - Defensively in `BTLERadarDevice.measurementCharacteristicUpdate`, verify `mIsShutDown || gatt != mBluetoothGatt || mState != State.CONNECTED_WITH_SERVICE` before parsing packets.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-CON-020.1` (*Immutable BLE Notification Payload Dispatch*) in `MyBTLEDevice.java`.
   - `REQ-CON-028.4` (*BTLERadarDevice GATT Lifecycle & Reactive Repository*) in `BTLERadarDevice.java`.
2. **Historical Origin & Commit Trace**:
   - `ATT-4007` (Sprint 2026-42.10, commit `0234bfc0`): Added snapshot pool to reduce allocation pressure.
   - `ATT-5083` (Sprint 2026-46.3): Added `BTLERadarDevice` with 4–8 Hz high-rate streams and reactive `RadarStateRepository`.
3. **Root Reason for Existing Formulation**:
   - The 8-slot characteristic pool was introduced when BLE sensors operated at low rates (1–2 Hz) where main-thread latency was assumed $\le 500\text{ ms}$. With high-frequency 4–8 Hz radar streams, the 8-slot buffer overflows during normal UI stalls.
   - Measurement runnables were un-guarded because disconnections were assumed to clear all handler callbacks; however, `removeCallbacksAndMessages(null)` is called in `shutDown()`, not in mid-ride `disconnectFromGatt()`.
4. **Preservation of Core Invariants**:
   - Thread safety on `mHandler` (main looper) preserved.
   - Compatibility with existing BLE devices (`BTLEHeartRateDevice`, `BTLEBikePowerDevice`, `BTLEBikeDevice`, `BTLERunSpeedDevice`) 100% preserved.
   - 3.5s watchdog fail-safe disconnect preserved.
   - Zero SQLite schema modifications.

---

## 5. Change Impact Analysis (SUP.10 / REQ-PRO-064)
* **Affected Requirements**: `REQ-CON-020` (`REQ-CON-020.1` Immutable BLE Notification Payload Dispatch) and `REQ-CON-028` (`REQ-CON-028.4` BTLERadarDevice GATT Lifecycle & Reactive Repository).
* **Affected Architecture Elements**: `MyBTLEDevice.java`, `BTLERadarDevice.java`.
* **Verification Impact & Test Surface**:
  - `BLEPayloadDispatchTest.kt`: Update snapshot pool tests to assert independent immutable snapshots across 16 rapid allocations without in-place mutation or payload overwriting.
  - `BTLERadarDeviceTest.kt`: Add unit test asserting queued measurement runnables arriving after `disconnectFromGatt()` are dropped and do not revive `RadarConnectionState.CONNECTED`.
  - Targeted unit tests: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --tests "com.atrainingtracker.banalservice.devices.radar.*"`.
* **Risk Assessment & Invariants**:
  - **Risk Rating**: `LOW` (replaces flawed 8-slot pooling with standard immutable objects and guards asynchronous Handler runnables against disconnected GATT clients; zero changes to SQLite schemas or public API contracts).
  - **Invariants**: 100% thread safety on `mHandler` (main looper), backward compatibility with all BLE peripherals (`BTLEHeartRateDevice`, `BTLEBikePowerDevice`, `BTLEBikeDevice`, `BTLERunSpeedDevice`), fail-safe 3.5s watchdog disconnect preserved, zero SQLite schema modifications.
