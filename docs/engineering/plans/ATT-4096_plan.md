# Stage 3: Implementation Plan - ATT-4096: [Medium] Eliminate potential MySensorManager and ProxySensor lock-order inversion by dispatching initial newValue callbacks outside locks

**Ticket**: [ATT-4096](https://rainerblind.atlassian.net/browse/ATT-4096)  
**Sub-task**: [ATT-4138](https://rainerblind.atlassian.net/browse/ATT-4138) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `Next Minor Release` (V4.9.40)  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-SEN-021` (*Lock-Free Initial newValue Dispatch & Decoupled ProxySensor Monitor Lifecycle*)  
**Test Mapping**: `TST-TRK-028` (*Lock-Free ProxySensor Listener Dispatch & Deadlock Prevention Concurrency Suite*)  
**Branch**: `feature/ATT-4096`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Description & Background

In Sprint `2026-42.9`, `ATT-3988` (commit `2a5a6103`) converted `MySensor.mSensorListeners` to a thread-safe `CopyOnWriteArrayList` and dispatched `newValue()` callbacks in `MySensor` and `MySensorManager.registerSensor()` / `unregisterSensor()` outside of monitor locks. However, a latent A/B lock-order inversion remained active across `MySensorManager`, `ProxySensor`, and `MySensor`:
1. **Lock-Order Path A (`MySensorManager` monitor -> `ProxySensor` monitor -> foreign listener execution)**:
   `MySensorManager.registerSensor()` / `unregisterSensor()` holds `synchronized (this)` on `MySensorManager` -> calls `setBestSensorForProxySensor(sensorType)` -> calls `((ProxySensor) proxy).setSourceSensor(bestSensor)`. `ProxySensor.setSourceSensor()` is `synchronized (this)` on `ProxySensor`. Inside `ProxySensor.setSourceSensor()`, it iterates `mSensorListeners` and invokes `sourceSensor.addSensorListener(sensorListener)`. In `MySensor.addSensorListener(listener)`, if `mActivated == true`, it immediately invokes `sensorListener.newValue(mValue)`. Consequently, foreign `SensorListener.newValue(...)` callbacks are executed while holding **both** the coarse `MySensorManager` monitor lock and the `ProxySensor` monitor lock.
2. **Lock-Order Path B (`ProxySensor` monitor -> foreign listener execution -> `MySensorManager` monitor)**:
   When another thread (such as Android Main/UI thread via `FilterManager.createFilter()`, or a background worker configuring a proxy listener) invokes `ProxySensor.addSensorListener(sensorListener)`, it acquires `synchronized (this)` on the `ProxySensor` monitor. If `mSourceSensor != null`, it calls `source.addSensorListener(sensorListener)` under the `ProxySensor` lock. `MySensor.addSensorListener()` immediately invokes `sensorListener.newValue(mValue)` while holding the `ProxySensor` monitor lock. If this `sensorListener` implementation is re-entrant or queries the sensor manager topology (e.g., calling `MySensorManager.getSensor()`, `MySensorManager.getSensorList()`, `MySensorManager.getAccumulatedSensorTypeSet()`, or registering a derived sensor), it attempts to acquire `synchronized (this)` on `MySensorManager`.
3. **Deadlock Hazard**:
   If Thread 1 executes Path A (`MySensorManager` -> `ProxySensor` -> `listener.newValue()`) while Thread 2 simultaneously executes Path B (`ProxySensor` -> `listener.newValue()` -> `MySensorManager`), a classic A/B - B/A circular wait occurs, resulting in a thread deadlock.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-SEN-021` (*Lock-Free Initial newValue Dispatch & Decoupled ProxySensor Monitor Lifecycle*)
  * `REQ-SEN-021.1`: Elimination of `synchronized` Monitor on `ProxySensor` Public API (`addSensorListener`, `removeSensorListener`)
  * `REQ-SEN-021.2`: Dedicated State-Transition Synchronization Lock (`mSourceLock`) in `ProxySensor` with decoupled initial `newValue` dispatch
  * `REQ-SEN-021.3`: Decoupled `setBestSensorForProxySensor` Execution in `MySensorManager` (`registerSensor` / `unregisterSensor`)
* **Test Mapping**: `TST-TRK-028` (`TST-TRK-028.1` – `TST-TRK-028.4`)

---

## 3. System Invariants & Preserved Behavior

1. **Deadlock Prevention Guardrail**: Monitor locks on `MySensorManager` and `ProxySensor` MUST NEVER be held while calling `SensorListener.newValue(...)`.
2. **Listener Update Delivery Parity**: All listeners registered on `ProxySensor` must receive updates emitted by the currently bound source sensor, and must receive an initial value upon registration or source sensor migration.
3. **Priority Hierarchy Invariance**: Priority lists (`SPEED_PRIORITY`, `CADENCE_PRIORITY`, `ALTITUDE_PRIORITY`, `LOCATION_PRIORITY`) and selection logic in `MySensorManager` remain unchanged.
4. **UI Consistency (Rule 23)**: No UI changes are introduced by this backend architectural concurrency ticket.
5. **Clean-Room Regression Invariant**: 100% of existing tests must pass without failure.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: `ProxySensor.java` (`REQ-SEN-021.1`, `REQ-SEN-021.2`)
* Remove `synchronized` modifier from `addSensorListener(SensorListener sensorListener)` and `removeSensorListener(SensorListener sensorListener)`.
  * `super.addSensorListener(sensorListener)` safely adds to `mSensorListeners` (`CopyOnWriteArrayList.addIfAbsent`) and dispatches `sensorListener.newValue(mValue)` without locking.
  * Capturing `final MySensor<T> source = mSourceSensor;` and calling `source.addSensorListener(sensorListener)` / `source.removeSensorListener(sensorListener)` executes completely lock-free.
* Introduce a private object lock for source sensor mutation: `private final Object mSourceLock = new Object();`.
* Refactor `setSourceSensor(MySensor<T> sourceSensor)`:
  * Check if `mSourceSensor == sourceSensor`. If identical, return early.
  * In `synchronized (mSourceLock)`:
    * Read `final MySensor<T> previousSource = mSourceSensor;`.
    * Update `mSourceSensor = sourceSensor;`.
    * Snapshot `mSensorListeners`.
    * If `previousSource != null`, remove all snapshotted listeners: `previousSource.removeSensorListener(listener)`.
    * If `sourceSensor != null`, register all snapshotted listeners on `sourceSensor`: `sourceSensor.addSensorListener(listener)`. Note that `sourceSensor.addSensorListener(listener)` will invoke `listener.newValue(sourceSensor.getValue())`.
    * To ensure `listener.newValue()` is NEVER invoked while holding `mSourceLock` or any intermediate lock:
      * Either add listeners to `sourceSensor` without immediate callback (e.g., if supported or by migrating listeners), OR ensure `mSourceLock` is released BEFORE listener notification.
      * Specifically:
        ```java
        List<SensorListener> listenersSnapshot = null;
        MySensor<T> previousSource = null;
        synchronized (mSourceLock) {
            previousSource = mSourceSensor;
            if (previousSource == sourceSensor) {
                return;
            }
            mSourceSensor = sourceSensor;
            listenersSnapshot = new ArrayList<>(mSensorListeners);
        }

        // Migrate listeners outside of mSourceLock
        if (listenersSnapshot != null) {
            for (SensorListener listener : listenersSnapshot) {
                if (previousSource != null) {
                    previousSource.removeSensorListener(listener);
                }
                if (sourceSensor != null) {
                    sourceSensor.addSensorListener(listener);
                }
            }
        }
        ```
      * By executing `sourceSensor.addSensorListener(listener)` and `previousSource.removeSensorListener(listener)` outside of `mSourceLock`, absolutely zero locks are held when `listener.newValue()` executes!

### Component 2: `MySensorManager.java` (`REQ-SEN-021.3`)
* In `registerSensor(MySensor mySensor)`:
  * Currently:
    ```java
    synchronized (this) {
        ...
        setBestSensorForProxySensor(sensorType);
        ...
    }
    ```
  * Refactor:
    * Inside `synchronized (this)`: perform map updates (`mAllSensorsMapMap`), check if a new `ProxySensor` is needed or best sensor changed.
    * Rather than calling `setBestSensorForProxySensor(sensorType)` inside `synchronized (this)`, determine the affected `sensorType` (or collect deferred proxy actions).
    * Invoke `setBestSensorForProxySensor(sensorType)` **outside** the `synchronized (this)` block (similar to how `mSensorsSensor.newValue(gcData)` is already dispatched outside `synchronized (this)`).
* In `unregisterSensor(MySensor mySensor)`:
  * Inside `synchronized (this)`: remove `mySensor` from `mAllSensorsMapMap`, compute `newBestSensor`, compute `gcData`.
  * Invoke `setBestSensorForProxySensor(sensorType)` **outside** the `synchronized (this)` block.
* In `setBestSensorForProxySensor(SensorType sensorType)`:
  * Can query `bestSensor = getBestSensor(sensorType);` and `proxy = getSensor(sensorType);` (both are already synchronized queries on `MySensorManager`), but the call to `((ProxySensor) proxy).setSourceSensor(bestSensor);` executes when `Thread.holdsLock(this)` is `false` (when called from `registerSensor` / `unregisterSensor`).

### Component 3: `SensorAndDeviceConcurrencyStressTest.kt` (`TST-TRK-028`)
* Add test `TST-TRK-028_1 concurrent MySensorManager registration and ProxySensor listener queries are deadlock-free`:
  * 8 worker threads:
    * 4 threads calling `sensorManager.registerSensor()` and `sensorManager.unregisterSensor()` on various devices.
    * 4 threads calling `proxySensor.addSensorListener()` and `proxySensor.removeSensorListener()`.
    * Listener's `newValue()` callback executes re-entrant calls: `sensorManager.sensors`, `sensorManager.accumulatedSensorTypeSet`, `sensorManager.allSensors`.
    * Verify that 1,000 iterations per thread complete cleanly within a 15-second timeout with zero deadlocks.
* Add test `TST-TRK-028_2 initial newValue dispatch on ProxySensor occurs without holding monitor locks`:
  * Inside `SensorListener.newValue()`, assert `!Thread.holdsLock(proxySensor)` and `!Thread.holdsLock(sensorManager)`.
* Add test `TST-TRK-028_3 source sensor swap in ProxySensor cleanly migrates listeners and propagates values`:
  * Test listener receives new source values and does not receive disconnected source values.

---

## 5. Atomic Implementation Steps

| Step | Scope | Target File | Action Description | Verification Command |
| :--- | :--- | :--- | :--- | :--- |
| **Step 1** | Implementation | `banalservice/sensor/ProxySensor.java` | Remove `synchronized` on `addSensorListener`/`removeSensorListener`. Use `mSourceLock` and migrate listeners outside locks in `setSourceSensor`. | `./gradlew testDebugUnitTest --tests "*SensorAndDeviceConcurrencyStressTest*"` |
| **Step 2** | Implementation | `banalservice/sensor/MySensorManager.java` | Refactor `registerSensor()` and `unregisterSensor()` to dispatch `setBestSensorForProxySensor(sensorType)` outside `synchronized (this)`. | `./gradlew testDebugUnitTest --tests "*SensorAndDeviceConcurrencyStressTest*"` |
| **Step 3** | Test Suite | `banalservice/SensorAndDeviceConcurrencyStressTest.kt` | Implement `TST-TRK-028.1`, `TST-TRK-028.2`, `TST-TRK-028.3` concurrency stress and lock-free assertion tests. | `./gradlew testDebugUnitTest --tests "*SensorAndDeviceConcurrencyStressTest*"` |
| **Step 4** | Regression Suite | Entire Project | Run full clean-room unit test suite. | `./gradlew testDebugUnitTest` |

---

## 6. Rollback & Remediation Strategy

- The changes are localized to `ProxySensor.java`, `MySensorManager.java`, and the stress test file.
- If unexpected regressions occur, git allows reverting the specific file diffs immediately.
- Invariants are verified by `./gradlew testDebugUnitTest`.
