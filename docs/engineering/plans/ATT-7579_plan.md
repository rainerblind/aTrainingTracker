# Stage 3: Implementation Plan - ATT-7579: Fix MyBTLEDevice snapshot pool overrun and post-disconnect stale target revival

**Ticket**: [ATT-7579](https://rainerblind.atlassian.net/browse/ATT-7579)  
**Sub-task**: [ATT-7927](https://rainerblind.atlassian.net/browse/ATT-7927) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-CON-020` (`REQ-CON-020.1`), `REQ-CON-028` (`REQ-CON-028.4`)  
**Test Mapping**: `TST-CON-012` (`TST-CON-012.1`), `TST-CON-028` (`TST-CON-028.2`, `TST-CON-028.6`)  
**Branch**: `feat/ATT-7579`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Description & Background
In `MyBTLEDevice.java` and `BTLERadarDevice.java`:
1. **Characteristic Pool Overrun Under UI/GC Stalls**: `MyBTLEDevice` recycles characteristic snapshots using a fixed 8-slot ring-buffer pool (`SNAPSHOT_POOL_SIZE = 8`) and `AtomicInteger mPoolIndex`. In 4–8 Hz radar streams, if the main-thread Looper stalls for $\sim 1\text{ s}$, the pool wraps around and `snapshot.setValue(payload)` overwrites queued characteristic values waiting to be executed, corrupting or dropping telemetry frames.
2. **Post-Disconnect Stale Target Revival**: When `BTLERadarDevice.disconnectFromGatt()` transitions `RadarStateRepository` to `DISCONNECTED` and clears tracks, already-posted measurement runnables on `mHandler` execute right after, calling `processTargets()`. This resurrects `RadarConnectionState.CONNECTED`, repopulates stale targets, and rearms `mWatchdogRunnable` for 3.5s.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-CON-020` (*Resilient Bluetooth LE & ANT+ Reconnection, Immutable Telemetry Dispatch, and Queue Integrity Architecture*)
  * `REQ-CON-020.1`: Immutable BLE Notification Payload Dispatch & Connection Lifecycle Guarding.
* **Requirement**: `REQ-CON-028` (*BLE Cycling Radar Protocol Driver, Serialized GATT Queue, Latch Aggregator & Parity*)
  * `REQ-CON-028.4`: BTLERadarDevice V1 Threat Decoding, GATT Lifecycle & Reactive Repository.
* **Test Mapping**: `TST-CON-012` & `TST-CON-028`
  * `TST-CON-012.1`: Snapshot immutability across 16 rapid allocations and runnable drop when disconnected.
  * `TST-CON-028.2`: BTLERadarDevice disconnect stale target revival prevention test.
  * `TST-CON-028.6`: Targeted unit test suite (`com.atrainingtracker.banalservice.devices.bluetooth_le.*` & `com.atrainingtracker.banalservice.devices.radar.*`).

---

## 3. System Invariants & Preserved Behavior
1. **Payload Immutability**: Each queued runnable holds an independent `BluetoothGattCharacteristic` snapshot with defensively cloned byte array.
2. **Thread Safety & Lock Invariant**: Lifecycle checks (`mIsShutDown`, `gatt != mBluetoothGatt`, `mState != CONNECTED_WITH_SERVICE`) execute under `mGattLock`.
3. **Peripheral Driver Compatibility**: Zero regression in `BTLEHeartRateDevice`, `BTLEBikePowerDevice`, `BTLEBikeDevice`, `BTLERunSpeedDevice`, and `BTLERadarDevice`.
4. **Watchdog Disconnect**: 3.5s starvation watchdog behavior preserved.
5. **Zero SQLite Schema Modifications**: No database migrations or schema adjustments.

---

## 4. Proposed Architectural Changes

### Component 1: `MyBTLEDevice.java`
* Remove `SNAPSHOT_POOL_SIZE = 8`, `mCharacteristicPool`, and `mPoolIndex`.
* Refactor `obtainSnapshotCharacteristic`:
  ```java
  protected BluetoothGattCharacteristic obtainSnapshotCharacteristic(BluetoothGattCharacteristic source, byte[] payload) {
      if (mIsShutDown) {
          return null;
      }
      BluetoothGattCharacteristic snapshot = new BluetoothGattCharacteristic(
              source.getUuid(),
              source.getProperties(),
              source.getPermissions()
      );
      snapshot.setValue(payload != null ? payload.clone() : null);
      return snapshot;
  }
  ```
* In `shutDown()`: remove `mCharacteristicPool.clear()`.
* In `characteristicUpdate`: guard measurement runnable execution:
  ```java
  if (isMeasurementCharacteristic(characteristic.getUuid())) {
      mHandler.post(new Runnable() {
          @Override
          public void run() {
              synchronized (mGattLock) {
                  if (mIsShutDown || gatt != mBluetoothGatt || mState != State.CONNECTED_WITH_SERVICE) {
                      return;
                  }
              }
              measurementCharacteristicUpdate(gatt, characteristic);
          }
      });
  }
  ```
* In `disconnectFromGatt()`: ensure `mState = State.DISCONNECTED` under `synchronized (mGattLock)`.

### Component 2: `BTLERadarDevice.java`
* In `measurementCharacteristicUpdate(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic)`:
  Add defensive guard at method entry:
  ```java
  if (mIsShutDown || gatt != mBluetoothGatt || mState != State.CONNECTED_WITH_SERVICE) {
      return;
  }
  ```

### UI Consistency (Rule 23)
* No UI components modified.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `MyBTLEDevice.java` (`REQ-CON-020.1`)
* Remove pool variables (`SNAPSHOT_POOL_SIZE`, `mCharacteristicPool`, `mPoolIndex`).
* Refactor `obtainSnapshotCharacteristic` to allocate a fresh, unshared `BluetoothGattCharacteristic` with cloned payload.
* In `characteristicUpdate`, wrap `measurementCharacteristicUpdate` invocation in the connection lifecycle guard under `mGattLock`.
* In `disconnectFromGatt()`, assign `mState = State.DISCONNECTED;` under `mGattLock`.

### Step 2: Update `BTLERadarDevice.java` (`REQ-CON-028.4`)
* In `measurementCharacteristicUpdate`, add entry check: `if (mIsShutDown || gatt != mBluetoothGatt || mState != State.CONNECTED_WITH_SERVICE) return;`.

### Step 3: Update and Add Unit Tests in `BLEPayloadDispatchTest.kt` (`TST-CON-012.1`)
* Update `testCharacteristicPoolPreallocation()` to assert distinct unshared instances (`assertNotSame(snapshots[0], snapshots[8])`) and payload retention across 16 rapid allocations (`assertArrayEquals(byteArrayOf(i), snapshots[i].value)`).
* Add test verifying that when `mBluetoothGatt` is null or `mState != CONNECTED_WITH_SERVICE`, queued measurement runnables do not call `measurementCharacteristicUpdate`.

### Step 4: Add Unit Tests in `BTLERadarDeviceTest.kt` (`TST-CON-028.2`)
* Add test `testDisconnectFromGatt_dropsLateQueuedMeasurementRunnable()` asserting that executing a queued measurement runnable after `disconnectFromGatt()` leaves `RadarStateRepository` in `DISCONNECTED` state and does not revive `CONNECTED` or repopulate targets.

### Step 5: Clean-Room Regression Verification (`TST-CON-028.6`)
* Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --tests "com.atrainingtracker.banalservice.devices.radar.*"`.
* Verify 100% test pass rate.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests in `BLEPayloadDispatchTest.kt` and `BTLERadarDeviceTest.kt`.
* **Rollback**: Isolated git branch `feat/ATT-7579`.
