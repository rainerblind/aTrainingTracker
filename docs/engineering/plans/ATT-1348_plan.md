# Implementation Plan - ATT-1348: Fatal Crash in MyBTLEDevice$7.run Due to Null mBluetoothGatt Reference

**Ticket**: [ATT-1348](https://rainerblind.atlassian.net/browse/ATT-1348)  
**Sub-task**: [ATT-1351](https://rainerblind.atlassian.net/browse/ATT-1351)  
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

## 1. Technical Architecture & Modifications

### 1.1 Concurrency Primitives & Data Structure Modernization
In `MyBTLEDevice.java`:
1. **Thread-Safe Characteristic Queue**:
   Replace the un-synchronized `LinkedList` with `ConcurrentLinkedQueue` to guarantee atomic enqueuing and polling across Binder threads and UI Looper:
   ```java
   protected final Queue<BluetoothGattCharacteristic> mReadCharacteristicQueue = new ConcurrentLinkedQueue<>();
   ```
2. **Volatile GATT Reference**:
   Declare `mBluetoothGatt` as `volatile` to ensure atomic, visibility-guaranteed reads and writes across threads:
   ```java
   protected volatile BluetoothGatt mBluetoothGatt;
   ```
3. **Dedicated In-Memory Mutex (`mGattLock`)**:
   Introduce a private synchronization lock for atomic reference swapping:
   ```java
   private final Object mGattLock = new Object();
   ```

### 1.2 Explicit Lifecycle Callback Management
Replace the anonymous 5-minute battery re-read runnable with a dedicated, cancellable instance:
```java
private BluetoothGattCharacteristic mBatteryCharacteristic;

private final Runnable mBatteryReadRunnable = new Runnable() {
    @Override
    public void run() {
        final BluetoothGatt gatt = mBluetoothGatt;
        final BluetoothGattCharacteristic batteryChar = mBatteryCharacteristic;
        if (gatt != null && batteryChar != null) {
            mReadCharacteristicQueue.add(batteryChar);
            readNextCharacteristic();
        }
    }
};
```
In `characteristicUpdate`:
```java
if (BluetoothConstants.UUID_CHARACTERISTIC_BATTERY_LEVEL.equals(characteristic.getUuid())) {
    Integer batteryPercentage = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
    if (batteryPercentage != null) {
        if (DEBUG) Log.i(TAG, "got battery percentage: " + batteryPercentage);
        setBatteryPercentage(batteryPercentage);
    }

    // Cancel any previous battery read task and reschedule
    mBatteryCharacteristic = characteristic;
    mHandler.removeCallbacks(mBatteryReadRunnable);
    mHandler.postDelayed(mBatteryReadRunnable, READ_BATTERY_PERCENTAGE_PERIOD);
}
```

### 1.3 Redesigned `disconnectFromGatt()` & `shutDown()`
```java
private void disconnectFromGatt() {
    if (DEBUG) Log.i(TAG, "disconnectFromGatt()");

    // 1. Cancel pending delayed battery read runnable and purge characteristic queue
    mHandler.removeCallbacks(mBatteryReadRunnable);
    mReadCharacteristicQueue.clear();

    // 2. Atomically detach and nullify mBluetoothGatt under lock
    final BluetoothGatt gattToClose;
    synchronized (mGattLock) {
        gattToClose = mBluetoothGatt;
        mBluetoothGatt = null;
    }

    // 3. Close the detached GATT client asynchronously without holding mGattLock
    if (gattToClose != null) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        gattToClose.disconnect();
                        gattToClose.close();
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error closing GATT", e);
                }
            }
        });
    }
}

@Override
public void shutDown() {
    if (DEBUG) Log.i(TAG, "shutDown()");
    mHandler.removeCallbacksAndMessages(null);
    disconnectFromGatt();
    super.shutDown();
}
```

### 1.4 Defensively Guarded `readNextCharacteristic()`
Eliminate Time-of-Check to Time-of-Use (TOCTOU) race conditions via local snapshot capturing and comprehensive null checks:
```java
protected void readNextCharacteristic() {
    if (DEBUG) Log.i(TAG, "readNextCharacteristic");

    final BluetoothGattCharacteristic gattChar = mReadCharacteristicQueue.poll();
    if (gattChar != null) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                final BluetoothGatt gatt = mBluetoothGatt;
                if (gatt != null) {
                    gatt.readCharacteristic(gattChar);
                }
            }
        });
    } else {
        if (!sensorsRegistered()) {
            mState = State.CONNECTED_WITH_SERVICE;
            setLastActive();
            registerSensors();

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    final BluetoothGatt gatt = mBluetoothGatt;
                    if (gatt == null) {
                        return;
                    }
                    BluetoothGattService btGattService = gatt.getService(BluetoothConstants.getServiceUUID(getDeviceType()));
                    if (btGattService == null) {
                        Log.d(TAG, "Expected service not found!");
                        return;
                    }
                    BluetoothGattCharacteristic btGattChar = btGattService.getCharacteristic(BluetoothConstants.getCharacteristicUUID(getDeviceType()));
                    if (btGattChar == null) {
                        Log.d(TAG, "Expected characteristic not found!");
                        return;
                    }
                    gatt.setCharacteristicNotification(btGattChar, true);

                    BluetoothGattDescriptor descriptor = btGattChar.getDescriptor(BluetoothConstants.UUID_CHARACTERISTIC_CLIENT_CONFIG);
                    if (descriptor != null) {
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor);
                    }
                }
            });
        }
    }
}
```

### 1.5 Guarded `onConnectionStateChange`
In `onConnectionStateChange`:
```java
if (newState == BluetoothProfile.STATE_CONNECTED) {
    if (DEBUG) Log.i(TAG, "Connected to GATT server");

    mState = State.CONNECTED_TO_GATT;
    mHandler.post(new Runnable() {
        @Override
        public void run() {
            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            final BluetoothGatt gatt = mBluetoothGatt;
            if (gatt != null) {
                gatt.discoverServices();
                notifyStopSearching(true);
            }
        }
    });
} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
    Log.i(TAG, "Disconnected from GATT server.");
    mState = State.DISCONNECTED;
    unregisterSensors();
    disconnectFromGatt();
    startSearching();
}
```

---

## 2. Invariant & Safety Analysis

1. **Deadlock Freedom (AB-BA Invariant)**:
   * `mGattLock` is an internal, non-reentrant mutex used strictly for in-memory pointer swapping (`gattToClose = mBluetoothGatt; mBluetoothGatt = null;`).
   * `mGattLock` is **never** held when calling out to Android Bluetooth framework APIs (`connectGatt`, `disconnect`, `close`, `readCharacteristic`, `writeDescriptor`) or when posting/dispatching to `mHandler`.
   * This completely prevents inversion/deadlock cycles between Android's Bluetooth Service binder lock and the UI looper.

2. **TOCTOU Elimination**:
   * Dereferencing is performed strictly on a local stack variable (`final BluetoothGatt gatt = mBluetoothGatt; if (gatt != null) gatt.readCharacteristic(...)`).
   * If a concurrent thread sets `mBluetoothGatt = null`, the local stack reference `gatt` remains valid. Android's framework method on a closing GATT simply returns `false` instead of crashing with a JVM `NullPointerException`.

3. **Lifecycle & Queue Purity**:
   * Calling `disconnectFromGatt()` synchronously drains `mReadCharacteristicQueue` and cancels `mBatteryReadRunnable`.
   * Even if a BLE sensor disconnects and reconnects repeatedly, no stale characteristics leak into subsequent connections, and no delayed timers fire against dead connections.

4. **Backward Compatibility & Peripheral Invariants**:
   * All subclasses (`BTLEHeartRateDevice`, `BTLEBikeDevice`, `BTLEBikePowerDevice`, `BTLERunSpeedDevice`) continue to utilize the standard `measurementCharacteristicUpdate(...)` contract without requiring any subclass code modification.

---

## 3. Automated Unit Testing Implementation

Create test suite `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/MyBTLEDeviceLifecycleTest.kt`:
* Utilize JUnit 4, MockK, and Robolectric/ShadowLooper to orchestrate multi-threaded callback events.
* Implement:
  1. `testReadNextCharacteristic_whenGattIsNull_doesNotThrowNpe`: Verifies null GATT exception shielding during characteristic poll.
  2. `testReadNextCharacteristic_whenQueueEmpty_andGattIsNull_doesNotThrowNpe`: Verifies defensive null guards on empty queue service discovery branch.
  3. `testReadNextCharacteristic_toctouRaceConditionSimulation_doesNotThrowNpe`: Simulates concurrent nullification during Looper dispatch.
  4. `testDisconnectFromGatt_clearsQueue_andNullifiesGattUnderLock`: Verifies atomic queue clearing and GATT reference detachment under `mGattLock`.
  5. `testDelayedBatteryRunnable_cancelledOnDisconnection_doesNotExecute`: Verifies that `mBatteryReadRunnable` is cancelled and does not run post-disconnection.
  6. `testOnConnectionStateChange_connected_guardsDiscoverServicesWhenGattNull`: Verifies null safety on `STATE_CONNECTED` transition.
  7. `testShutDown_cleansUpAllCallbacksAndGatt`: Verifies complete cleanup upon service shutdown.

---

## 4. Verification & Regression Plan

1. **Unit Test Execution**:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.MyBTLEDeviceLifecycleTest"
   ```
2. **Full Clean-Room Regression**:
   ```bash
   ./gradlew testDebugUnitTest
   ```
   Ensures 0 failures, 0 regressions across all existing unit test suites.
