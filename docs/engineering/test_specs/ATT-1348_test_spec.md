# Test Specification - ATT-1348: Fatal Crash in MyBTLEDevice$7.run Due to Null mBluetoothGatt Reference

**Ticket**: [ATT-1348](https://rainerblind.atlassian.net/browse/ATT-1348)  
**Sub-task**: [ATT-1350](https://rainerblind.atlassian.net/browse/ATT-1350)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.2`  
**Components**:
* `com.atrainingtracker.banalservice.devices.bluetooth_le.MyBTLEDevice.java`
* `com.atrainingtracker.banalservice.devices.bluetooth_le.MyBTLEDeviceLifecycleTest.kt`
**Requirement**: `REQ-CON-012`  
**Test Spec**: `TST-CON-003`  
**Branch**: `bugfix/ATT-1348`  

---

## 1. Overview & Verification Strategy

This test specification defines the automated unit test suite and regression criteria for verifying the complete elimination of the fatal `NullPointerException` in `MyBTLEDevice$7.run` (`ATT-1348`).

The verification suite specifically validates:
1. **GATT Null-Safety & Exception Shielding**: Verifying that invoking `readNextCharacteristic()` when `mBluetoothGatt` is null (or when `connectGatt` failed) never throws `NullPointerException` or crashes the host looper.
2. **Empty Queue & Descriptor Setup Safety**: Verifying that when `mReadCharacteristicQueue` is empty and sensors are not yet registered, all service and descriptor dereferences (`getService`, `getCharacteristic`, `setCharacteristicNotification`, `getDescriptor`, `writeDescriptor`) are defensively guarded against null references.
3. **Time-of-Check to Time-of-Use (TOCTOU) Race Immunity**: Simulating asynchronous concurrent disconnection where `mBluetoothGatt` is set to null while a posted read runnable is queued on the main Looper, verifying that local variable snapshot capture prevents NPEs.
4. **Disconnection Cleanup & Queue Purging**: Verifying that `disconnectFromGatt()` and `onConnectionStateChange(STATE_DISCONNECTED)` atomically purge `mReadCharacteristicQueue`, cancel pending delayed runnables, and nullify `mBluetoothGatt` under `mGattLock`.
5. **Periodic Battery Re-Read Cancellation**: Verifying that the 5-minute delayed battery re-read runnable (`mBatteryReadRunnable`) is cancelled upon disconnection and shutdown, preventing zombie read requests on dead connections.
6. **Deadlock Freedom Verification**: Verifying that `mGattLock` is never held across Android framework Bluetooth IPC calls or looper dispatches.
7. **Clean-Room Regression**: Guaranteeing 0 failures and 0 regressions across all existing unit tests (`./gradlew testDebugUnitTest`).

---

## 2. Test Cases & Verification Matrix

### Test Case 1: `testReadNextCharacteristic_whenGattIsNull_doesNotThrowNpe` (Unit Test)
* **Goal**: Verify that when `mReadCharacteristicQueue` contains characteristics but `mBluetoothGatt` is null, `readNextCharacteristic()` processes the queue and drains the looper without throwing `NullPointerException`.
* **Preconditions**:
  - Test instance of `BTLEHeartRateDevice` initialized with mock Context and SensorManager.
  - `mBluetoothGatt` is null.
  - A mock `BluetoothGattCharacteristic` is added to `mReadCharacteristicQueue`.
* **Action**:
  - Call `readNextCharacteristic()`.
  - Drain the main Looper / execute all posted tasks on `mHandler`.
* **Expected Result**:
  - Zero exceptions thrown.
  - `mReadCharacteristicQueue` is safely polled.
  - Looper execution completes cleanly without attempting to invoke `readCharacteristic` on a null reference.

### Test Case 2: `testReadNextCharacteristic_whenQueueEmpty_andGattIsNull_doesNotThrowNpe` (Unit Test)
* **Goal**: Verify that when `mReadCharacteristicQueue` is empty and `sensorsRegistered()` returns false, the service/descriptor registration branch does not crash when `mBluetoothGatt` is null.
* **Preconditions**:
  - `mReadCharacteristicQueue` is empty.
  - Sensors are not registered (`mState != State.CONNECTED_WITH_SERVICE`).
  - `mBluetoothGatt` is null.
* **Action**:
  - Call `readNextCharacteristic()`.
  - Drain the main Looper.
* **Expected Result**:
  - Zero exceptions thrown.
  - The posted runnable checks `mBluetoothGatt == null` and returns cleanly without dereferencing service or descriptor.

### Test Case 3: `testReadNextCharacteristic_toctouRaceConditionSimulation_doesNotThrowNpe` (Unit Test)
* **Goal**: Verify that if `mBluetoothGatt` is set to null concurrently between the posting of the runnable and its execution on the main Looper, the operation does not throw `NullPointerException`.
* **Preconditions**:
  - Mock `BluetoothGatt` assigned to `mBluetoothGatt`.
  - A mock `BluetoothGattCharacteristic` added to `mReadCharacteristicQueue`.
* **Action**:
  - Call `readNextCharacteristic()`. A runnable is posted to `mHandler`.
  - Concurrently call `mBluetoothGatt = null` (simulating sudden disconnection on a Binder thread before the main Looper processes the callback).
  - Execute the posted runnable on the Looper.
* **Expected Result**:
  - Zero exceptions thrown.
  - The local snapshot check inside `run()` detects null (or uses the local non-null snapshot) without crashing.

### Test Case 4: `testDisconnectFromGatt_clearsQueue_andNullifiesGattUnderLock` (Unit Test)
* **Goal**: Verify that invoking `disconnectFromGatt()` purges all queued characteristics, nullifies `mBluetoothGatt`, and cancels delayed runnables.
* **Preconditions**:
  - Mock `BluetoothGatt` assigned to `mBluetoothGatt`.
  - Multiple mock characteristics enqueued in `mReadCharacteristicQueue`.
* **Action**:
  - Call `disconnectFromGatt()`.
  - Drain main Looper.
* **Expected Result**:
  - `mReadCharacteristicQueue.isEmpty()` is `true`.
  - `mBluetoothGatt` is `null`.
  - `mockGatt.disconnect()` and `mockGatt.close()` are called on the background/handler execution.

### Test Case 5: `testDelayedBatteryRunnable_cancelledOnDisconnection_doesNotExecute` (Unit Test)
* **Goal**: Verify that the 5-minute delayed battery re-read runnable is cancelled when `disconnectFromGatt()` or `STATE_DISCONNECTED` occurs.
* **Preconditions**:
  - Mock `BluetoothGatt` assigned.
  - Battery characteristic update triggers scheduling of `mBatteryReadRunnable` with 5-minute delay.
* **Action**:
  - Invoke `onConnectionStateChange(mockGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)`.
  - Verify `disconnectFromGatt()` is invoked.
  - Fast-forward / drain the handler message queue by 5 minutes.
* **Expected Result**:
  - The delayed battery runnable does NOT execute.
  - `mReadCharacteristicQueue` remains empty.
  - No read requests are submitted to GATT.

### Test Case 6: `testOnConnectionStateChange_connected_guardsDiscoverServicesWhenGattNull` (Unit Test)
* **Goal**: Verify that when `STATE_CONNECTED` is dispatched on a Binder thread but `mBluetoothGatt` was not assigned (e.g. `connectGatt` failed or returned null), `discoverServices()` is safely guarded.
* **Preconditions**:
  - `mBluetoothGatt` is null.
* **Action**:
  - Trigger `mGattCallback.onConnectionStateChange(mockGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)`.
  - Drain main Looper.
* **Expected Result**:
  - Zero exceptions thrown.
  - `discoverServices()` is not invoked on a null reference.

### Test Case 7: `testShutDown_cleansUpAllCallbacksAndGatt` (Unit Test)
* **Goal**: Verify that invoking `shutDown()` completely cleans up queues, runnables, and nullifies GATT references.
* **Preconditions**:
  - Active device with queued characteristics and scheduled delayed tasks.
* **Action**:
  - Call `device.shutDown()`.
* **Expected Result**:
  - `mReadCharacteristicQueue.isEmpty()` is `true`.
  - `mBluetoothGatt` is `null`.
  - All handler callbacks are removed.

### Test Case 8: Clean-Room Full Suite Regression (Automated)
* **Command**: `./gradlew testDebugUnitTest`
* **Success Criteria**: BUILD SUCCESSFUL, 0 failures across all project unit tests.

---

## 3. Requirement Traceability Matrix

| Test Case | Target Requirement | Description | Expected Status |
|:---|:---|:---|:---|
| `TC-1` | `REQ-CON-012` | `readNextCharacteristic()` with null `mBluetoothGatt` does not throw NPE | Pass |
| `TC-2` | `REQ-CON-012` | Empty queue branch with null `mBluetoothGatt` does not throw NPE | Pass |
| `TC-3` | `REQ-CON-012` | TOCTOU race simulation (concurrent nullification) does not throw NPE | Pass |
| `TC-4` | `REQ-CON-012` | `disconnectFromGatt()` purges queue and nullifies GATT under `mGattLock` | Pass |
| `TC-5` | `REQ-CON-012` | Delayed battery re-read runnable is cancelled upon disconnection | Pass |
| `TC-6` | `REQ-CON-012` | `onConnectionStateChange(STATE_CONNECTED)` guards `discoverServices()` | Pass |
| `TC-7` | `REQ-CON-012` | `shutDown()` cleans up all queues, runnables, and GATT references | Pass |
| `TC-8` | `REQ-CON-012` | Clean-room full test suite regression pass | Pass |
