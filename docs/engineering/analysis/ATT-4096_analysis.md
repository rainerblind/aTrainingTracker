# Stage 1 Analysis: ATT-4096 — Eliminate potential MySensorManager and ProxySensor lock-order inversion by dispatching initial newValue callbacks outside locks

## 1. Executive Summary
- **Ticket**: `ATT-4096` — `[Verbesserung] [Medium] Eliminate potential MySensorManager and ProxySensor lock-order inversion by dispatching initial newValue callbacks outside locks` (Epic: `ATT-431` *Clean Code*)
- **Classification**: Architectural Concurrency & Deadlock Prevention (Defect Prevention / Thread Safety)
- **Root Cause**: In Sprint `2026-42.9`, `ATT-3988` (commit `2a5a6103`) converted `MySensor.mSensorListeners` to a thread-safe `CopyOnWriteArrayList` and dispatched `newValue()` callbacks in `MySensor` and `MySensorManager.registerSensor()` / `unregisterSensor()` outside of monitor locks. However, a latent A/B lock-order inversion remains active across `MySensorManager`, `ProxySensor`, and `MySensor`:
  1. **Lock-Order Path A (`MySensorManager` monitor -> `ProxySensor` monitor -> foreign listener execution)**:
     When any thread registers or unregisters a sensor via `MySensorManager.registerSensor(mySensor)` or `unregisterSensor(mySensor)`, it acquires `synchronized (this)` on the `MySensorManager` monitor. Inside this critical section, it calls `setBestSensorForProxySensor(sensorType)`. This in turn invokes `((ProxySensor) proxy).setSourceSensor(bestSensor)`. `ProxySensor.setSourceSensor()` is `synchronized (this)` on the `ProxySensor` monitor. Inside `ProxySensor.setSourceSensor()`, it iterates `mSensorListeners` and invokes `sourceSensor.addSensorListener(sensorListener)`. In `MySensor.addSensorListener(listener)`, if `mActivated == true`, it immediately invokes `sensorListener.newValue(mValue)`. Consequently, foreign `SensorListener.newValue(...)` callbacks are executed while holding **both** the coarse `MySensorManager` monitor lock and the `ProxySensor` monitor lock.
  2. **Lock-Order Path B (`ProxySensor` monitor -> foreign listener execution -> `MySensorManager` monitor)**:
     When another thread (such as Android Main/UI thread via `FilterManager.createFilter()`, or a background worker configuring a proxy listener) invokes `ProxySensor.addSensorListener(sensorListener)`, it acquires `synchronized (this)` on the `ProxySensor` monitor. If `mSourceSensor != null`, it calls `source.addSensorListener(sensorListener)` under the `ProxySensor` lock. `MySensor.addSensorListener()` immediately invokes `sensorListener.newValue(mValue)` while holding the `ProxySensor` monitor lock. If this `sensorListener` implementation is re-entrant or queries the sensor manager topology (e.g., calling `MySensorManager.getSensor()`, `MySensorManager.getSensorList()`, `MySensorManager.getAccumulatedSensorTypeSet()`, or registering a derived sensor), it attempts to acquire `synchronized (this)` on `MySensorManager`.
  3. **Deadlock Hazard**:
     If Thread 1 executes Path A (`MySensorManager` -> `ProxySensor` -> `listener.newValue()`) while Thread 2 simultaneously executes Path B (`ProxySensor` -> `listener.newValue()` -> `MySensorManager`), a classic A/B - B/A circular wait occurs, resulting in a thread deadlock.

---

## 2. Problem Domain & Concurrency Topology

### 2.1 Concurrency Topology
The sensor subsystem in `banalservice` operates at the crossroads of multiple asynchronous threads:
1. **Bluetooth LE GATT Binder Thread Pool**: Invokes callbacks (`onCharacteristicChanged`, `onServicesDiscovered`), registering BLE sensors via `MySensorManager.registerSensor()` (acquiring `MySensorManager` monitor).
2. **ANT+ Plugin PCC Handler Threads**: Invokes device state and data events, registering/unregistering ANT+ sensors via `MySensorManager.registerSensor()` / `unregisterSensor()`.
3. **Android Main / UI Thread**: Invokes `FilterManager.createFilter()` -> `mySensor.addSensorListener(myFilter)` (acquiring `ProxySensor` monitor) when configuring cockpit views, workout screens, or map overlays.
4. **1 Hz TrackerService Scheduled Executor**: Continuously samples sensor data and may trigger dynamic filter queries or state inspections.

### 2.2 Forensic Lock Inspection

#### A. `ProxySensor.java`
Currently:
```java
@Override
public synchronized void addSensorListener(SensorListener sensorListener) {
    super.addSensorListener(sensorListener);
    final MySensor<T> source = mSourceSensor;
    if (source != null) {
        source.addSensorListener(sensorListener);   // also add this sensor listener to the source device
    }
}

@Override
public synchronized void removeSensorListener(SensorListener sensorListener) {
    super.removeSensorListener(sensorListener);
    final MySensor<T> source = mSourceSensor;
    if (source != null) {
        source.removeSensorListener(sensorListener);  // also remove this sensor listener from the source device
    }
}

public synchronized void setSourceSensor(MySensor<T> sourceSensor) {
    final MySensor<T> previousSource = mSourceSensor;
    // move all SensorListeners to the new sensor
    for (SensorListener sensorListener : mSensorListeners) {
        if (previousSource != null) {
            previousSource.removeSensorListener(sensorListener);
        }
        if (sourceSensor != null) {
            sourceSensor.addSensorListener(sensorListener);
        }
    }

    mSourceSensor = sourceSensor;
}
```
**Defects**:
1. `addSensorListener(sensorListener)` is `synchronized (this)`. Inside `super.addSensorListener(sensorListener)`, `sensorListener.newValue(mValue)` is called (while holding `ProxySensor.this`). Then `source.addSensorListener(sensorListener)` is called, which calls `sensorListener.newValue(source.mValue)` (still holding `ProxySensor.this`).
2. In fact, calling `super.addSensorListener(sensorListener)` on `ProxySensor` adds `sensorListener` to `ProxySensor.mSensorListeners`. But `ProxySensor` never directly receives `newValue()` from hardware; data flows from `mSourceSensor`. When `source.addSensorListener(sensorListener)` is also called, `sensorListener` is registered on `source.mSensorListeners`!
   When `sourceSensor.newValue()` is called, `sourceSensor` notifies all its listeners (which includes `sensorListener`).
   When `setSourceSensor(sourceSensor)` runs, it migrates each listener in `mSensorListeners` from `previousSource` to `sourceSensor`.
   During this migration, `sourceSensor.addSensorListener(sensorListener)` calls `sensorListener.newValue(mValue)` while holding `ProxySensor.this` (and in Path A, while also holding `MySensorManager.this`).
3. Notice that `mSourceSensor` is declared `protected volatile MySensor<T> mSourceSensor`.
   Because `mSensorListeners` is a `CopyOnWriteArrayList` inherited from `MySensor`, thread-safe iteration and listener storage are already guaranteed without coarse monitor locks.
4. Calling foreign listener callbacks (`sensorListener.newValue`) while holding `synchronized (this)` violates the core design principle: *never call open foreign code while holding internal locks*.

#### B. `MySensorManager.java`
Currently:
```java
protected synchronized void setBestSensorForProxySensor(SensorType sensorType) {
    if (DEBUG) Log.d(TAG, "setBestSensorForProxySensor(" + sensorType.name() + ")");

    MySensor bestSensor = getBestSensor(sensorType);
    if (bestSensor != null) {
        MySensor proxy = getSensor(sensorType);
        if (proxy instanceof ProxySensor) {
            ((ProxySensor) proxy).setSourceSensor(bestSensor);
        }
    } else {
        removeSensor(sensorType);
    }
}
```
`setBestSensorForProxySensor` is called from:
- `registerSensor(MySensor)` inside `synchronized (this)`.
- `unregisterSensor(MySensor)` inside `synchronized (this)`.
Because `setBestSensorForProxySensor` is called under `synchronized (this)` on `MySensorManager`, any calls into `proxy.setSourceSensor(bestSensor)` execute while holding `MySensorManager`.

### 2.3 Chesterton's Fence Archaeology (`REQ-PRO-022`)
- **Original Requirement ID & Target**: `REQ-TRK-032` (specifically `REQ-TRK-032.2` *Thread-Safe `MySensor`, `ProxySensor`, `MyAccumulatorSensor` & Lock-Free `MySensorManager` Listener Dispatch*), targeting `ProxySensor.java`, `MySensorManager.java`, `MySensor.java`, and `SensorAndDeviceConcurrencyStressTest.kt`.
- **Historical Origin & Commit Trace**: Commit `2a5a6103` (`ATT-3988`), Sprint `2026-42.9`.
- **Root Reason for Existing Formulation**: `ATT-3988` made `registerSensor()` and `unregisterSensor()` calculate GC strings inside `synchronized (this)` and dispatch `mSensorsSensor.newValue(...)` and `mAccumulatedSensorsSensor.newValue(...)` outside the lock. However, `setBestSensorForProxySensor()` remained inside the `synchronized (this)` block of `registerSensor()` and `unregisterSensor()`, and `ProxySensor.setSourceSensor()` and `addSensorListener()` remained `synchronized`.
- **Preservation of Core Invariants**:
  - Thread-safe listener migration: All registered `SensorListener`s on a `ProxySensor` must receive updates from the active best source sensor.
  - Initial value dispatch: When a listener is registered, or when the source sensor changes, the listener must receive the initial/current value.
  - Priority hierarchy: `MySensorManager`'s priority hierarchy (`SPEED_PRIORITY`, `CADENCE_PRIORITY`, `ALTITUDE_PRIORITY`, `LOCATION_PRIORITY`) must remain authoritative.
  - Zero deadlocks or race conditions under concurrent access.

---

## 3. Remediation Strategy & Architecture

### 3.1 Eliminating Monitor Hold in `ProxySensor`
1. **Remove `synchronized` on `ProxySensor` methods (`addSensorListener`, `removeSensorListener`, `setSourceSensor`)**:
   - `mSensorListeners` in `MySensor` is already a thread-safe `CopyOnWriteArrayList`.
   - `mSourceSensor` is already `volatile`.
2. **Dedicated Internal Lock for State Transitions (`mSourceLock`)**:
   - To prevent concurrent `setSourceSensor` calls from racing on listener migration, use a private lock object `private final Object mSourceLock = new Object()`.
   - Under `synchronized (mSourceLock)`:
     - Read old source sensor and new source sensor.
     - If old source == new source, no-op.
     - Update `mSourceSensor = sourceSensor`.
     - Snapshot the listeners from `mSensorListeners`.
     - Remove listeners from `previousSource`.
     - For `sourceSensor`: add listeners without initial callbacks under the lock, or collect `(listener, initialValue)` pairs.
3. **Disengaging Initial `newValue` Callbacks from Locks**:
   - In `MySensor`: Add an overload or method to add a listener with or without immediate callback, or ensure that initial callbacks are invoked after releasing any intermediate locks.
   - Specifically in `ProxySensor.setSourceSensor(newSource)`:
     - Under `mSourceLock`:
       - Update `mSourceSensor = newSource`.
       - If `previousSource != null`, remove listeners from `previousSource`.
       - If `newSource != null`, register listeners on `newSource` (or proxy listener).
       - Snapshot the current value of `newSource`: `T initialValue = newSource.getValue()`.
       - Snapshot the listener list.
     - Outside `mSourceLock` (and outside any caller lock):
       - If `initialValue != null`, dispatch `listener.newValue(initialValue)` to listeners that need the initial value.
4. **Refactoring `ProxySensor.addSensorListener` and `removeSensorListener`**:
   - In `addSensorListener(listener)`:
     - Call `super.addSensorListener(listener)` (which adds to `mSensorListeners` and dispatches `newValue(mValue)` if activated). Note: `ProxySensor` itself does not hold a separate value, or `getValue()` delegates to `mSourceSensor.getValue()`.
     - Read `final MySensor<T> source = mSourceSensor;`.
     - If `source != null`, call `source.addSensorListener(listener)`.
     - None of this requires `synchronized (this)` on `ProxySensor`!
   - In `removeSensorListener(listener)`:
     - Call `super.removeSensorListener(listener)`.
     - Read `final MySensor<T> source = mSourceSensor;`.
     - If `source != null`, call `source.removeSensorListener(listener)`.
     - Completely lock-free.

### 3.2 Decoupling `setBestSensorForProxySensor` in `MySensorManager`
1. In `MySensorManager`:
   - Inside `synchronized (this)` in `registerSensor()` and `unregisterSensor()`:
     - Compute changes and determine if any `ProxySensor` source needs updating.
     - Collect a list of deferred proxy update actions: e.g. `List<Pair<ProxySensor, MySensor>> proxyUpdates = new ArrayList<>()`.
   - Outside `synchronized (this)` in `registerSensor()` and `unregisterSensor()`:
     - Execute the deferred proxy updates: `proxy.setSourceSensor(bestSensor)`.
     - This guarantees that `setSourceSensor` is NEVER called while holding the `MySensorManager` monitor lock!

---

## 4. Scope Bounding & Impact Analysis

### 4.1 In-Scope Components
- `banalservice/sensor/ProxySensor.java`:
  - Eliminate `synchronized` on public methods (`addSensorListener`, `removeSensorListener`, `setSourceSensor`).
  - Introduce private lock `mSourceLock` for atomic source pointer swapping and listener migration.
  - Dispatch initial `newValue` notifications outside monitor/lock blocks.
- `banalservice/sensor/MySensorManager.java`:
  - Move `setBestSensorForProxySensor()` invocations (or proxy source sensor updates) outside of `synchronized (this)` in `registerSensor()` and `unregisterSensor()`.
- `SensorAndDeviceConcurrencyStressTest.kt`:
  - Add explicit concurrency stress test `TST-TRK-026` / `TST-TRK-024.4` specifically simulating the A/B deadlock scenario:
    - Thread A: continuously calls `MySensorManager.registerSensor()` and `unregisterSensor()`.
    - Thread B: continuously registers and unregisters a `SensorListener` on `ProxySensor` whose `newValue(T)` callback directly queries `MySensorManager` (`getSensors()`, `getAccumulatedSensorTypeSet()`, `registerSensor()`).
    - Run across 8 concurrent threads for $> 1000$ iterations, verifying zero deadlocks, zero exceptions, and immediate completion.

### 4.2 Out-of-Scope Elements
- Modifying `DeviceManager` or `FilterManager` (already thread-safe per `ATT-3988`).
- Modifying `SensorType` or priority lists.
- Modifying UI layers or database schemas.

---

## 5. Verification & Acceptance Criteria
1. **Deadlock Freedom**: Concurrent execution of `MySensorManager.registerSensor()` / `unregisterSensor()` and `ProxySensor.addSensorListener()` with re-entrant `MySensorManager` queries completes in $< 5$ seconds across 8 worker threads without thread starvation or circular monitor deadlock.
2. **Listener Notification Parity**: All listeners registered on a `ProxySensor` receive updates when the source sensor emits a value, and receive an initial value upon registration or source sensor migration.
3. **Full Clean-Room Regression**: 100% of existing tests pass (`./gradlew testDebugUnitTest`).
