# Stage 2: Requirement & Test Specification - ATT-4096: [Medium] Eliminate potential MySensorManager and ProxySensor lock-order inversion by dispatching initial newValue callbacks outside locks

**Ticket**: [ATT-4096](https://rainerblind.atlassian.net/browse/ATT-4096)  
**Sub-task**: [ATT-4137](https://rainerblind.atlassian.net/browse/ATT-4137) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `Next Minor Release` (V4.9.40)  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-SEN-021` (*Lock-Free Initial newValue Dispatch & Decoupled ProxySensor Monitor Lifecycle*)  
**Test Spec ID**: `TST-TRK-028`  
**Branch**: `feature/ATT-4096`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Requirement Specification (`REQ-SEN-021`)

### 1.1 Problem Statement & Rationale
In Sprint `2026-42.9`, `ATT-3988` (commit `2a5a6103`) converted `MySensor.mSensorListeners` to a thread-safe `CopyOnWriteArrayList` and dispatched `newValue()` callbacks in `MySensor` and `MySensorManager.registerSensor()` / `unregisterSensor()` outside of monitor locks. However, a latent A/B lock-order inversion remained active across `MySensorManager`, `ProxySensor`, and `MySensor`:
1. **Lock-Order Path A (`MySensorManager` monitor -> `ProxySensor` monitor -> foreign listener execution)**:
   When any thread registers or unregisters a sensor via `MySensorManager.registerSensor(mySensor)` or `unregisterSensor(mySensor)`, it acquires `synchronized (this)` on the `MySensorManager` monitor. Inside this critical section, it calls `setBestSensorForProxySensor(sensorType)`. This in turn invokes `((ProxySensor) proxy).setSourceSensor(bestSensor)`. `ProxySensor.setSourceSensor()` is `synchronized (this)` on the `ProxySensor` monitor. Inside `ProxySensor.setSourceSensor()`, it iterates `mSensorListeners` and invokes `sourceSensor.addSensorListener(sensorListener)`. In `MySensor.addSensorListener(listener)`, if `mActivated == true`, it immediately invokes `sensorListener.newValue(mValue)`. Consequently, foreign `SensorListener.newValue(...)` callbacks are executed while holding **both** the coarse `MySensorManager` monitor lock and the `ProxySensor` monitor lock.
2. **Lock-Order Path B (`ProxySensor` monitor -> foreign listener execution -> `MySensorManager` monitor)**:
   When another thread (such as Android Main/UI thread via `FilterManager.createFilter()`, or a background worker configuring a proxy listener) invokes `ProxySensor.addSensorListener(sensorListener)`, it acquires `synchronized (this)` on the `ProxySensor` monitor. If `mSourceSensor != null`, it calls `source.addSensorListener(sensorListener)` under the `ProxySensor` lock. `MySensor.addSensorListener()` immediately invokes `sensorListener.newValue(mValue)` while holding the `ProxySensor` monitor lock. If this `sensorListener` implementation is re-entrant or queries the sensor manager topology (e.g., calling `MySensorManager.getSensor()`, `MySensorManager.getSensorList()`, `MySensorManager.getAccumulatedSensorTypeSet()`, or registering a derived sensor), it attempts to acquire `synchronized (this)` on `MySensorManager`.
3. **Deadlock Hazard**:
   If Thread 1 executes Path A (`MySensorManager` -> `ProxySensor` -> `listener.newValue()`) while Thread 2 simultaneously executes Path B (`ProxySensor` -> `listener.newValue()` -> `MySensorManager`), a classic A/B - B/A circular wait occurs, resulting in a thread deadlock.

### 1.2 Functional & Architectural Requirements (`REQ-SEN-021`)

1. **Elimination of `synchronized` Monitor on `ProxySensor` Public API (`REQ-SEN-021.1`)**:
   - `ProxySensor.addSensorListener(SensorListener sensorListener)` and `ProxySensor.removeSensorListener(SensorListener sensorListener)` SHALL NOT be declared `synchronized` on `ProxySensor.this`.
   - `addSensorListener()` SHALL delegate to `super.addSensorListener(sensorListener)` and `source.addSensorListener(sensorListener)` without acquiring any monitor or coarse lock.
   - `removeSensorListener()` SHALL delegate to `super.removeSensorListener(sensorListener)` and `source.removeSensorListener(sensorListener)` without acquiring any monitor or coarse lock.

2. **Dedicated State-Transition Synchronization Lock (`REQ-SEN-021.2`)**:
   - `ProxySensor` SHALL encapsulate source sensor mutations behind a private lock object `private final Object mSourceLock = new Object();` rather than synchronizing on the public `ProxySensor` instance.
   - In `ProxySensor.setSourceSensor(MySensor<T> sourceSensor)`: state transitions and listener migration SHALL acquire `synchronized (mSourceLock)` to prevent concurrent source re-assignments from racing, remove registered listeners from `previousSource`, add registered listeners to `sourceSensor`, update `mSourceSensor = sourceSensor`, snapshot the current source value and listeners, and release `mSourceLock` BEFORE dispatching any initial `newValue()` callbacks.

3. **Decoupled `setBestSensorForProxySensor` Execution in `MySensorManager` (`REQ-SEN-021.3`)**:
   - In `MySensorManager.registerSensor(MySensor mySensor)` and `MySensorManager.unregisterSensor(MySensor mySensor)`, `setBestSensorForProxySensor(sensorType)` (or deferred `proxySensor.setSourceSensor(...)` invocations) SHALL be executed strictly **outside** `synchronized (this)`, guaranteeing that `ProxySensor.setSourceSensor` is NEVER called while holding the `MySensorManager` monitor lock.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: Net-new requirement `REQ-SEN-021` (*Lock-Free Initial newValue Dispatch & Decoupled ProxySensor Monitor Lifecycle*), extending `REQ-TRK-032.2` (`ATT-3988`). Targets `ProxySensor.java`, `MySensorManager.java`, and `SensorAndDeviceConcurrencyStressTest.kt`.
2. **Historical Origin & Commit Trace**: Ticket `ATT-4096`, Sprint `2026-42.10`, following Sprint `2026-42.9` code review of `ATT-3988` (commit `2a5a6103`).
3. **Root Reason for Existing Formulation**: `ATT-3988` eliminated lock holds for `mSensorsSensor.newValue()` in `registerSensor`/`unregisterSensor`, but left `setBestSensorForProxySensor` inside `synchronized (this)` and left `ProxySensor.addSensorListener()` / `setSourceSensor()` synchronized on `this`. Under concurrent sensor registration on background threads and UI/filter listener queries on main threads, an A/B circular wait was possible.
4. **Preservation of Core Invariants**: Full listener migration on best sensor change, immediate initial value emission upon listener subscription, priority hierarchy in `MySensorManager`, and 100% clean-room test pass rate MUST NOT be broken.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Concurrency Deadlock Elimination)**:
  * *Given* multi-threaded execution where Thread A calls `MySensorManager.registerSensor()` / `unregisterSensor()` and Thread B calls `ProxySensor.addSensorListener()` with a listener whose `newValue()` callback queries `MySensorManager` (`getSensors()`, `getAccumulatedSensorTypeSet()`, or `registerSensor()`),
  * *When* executed concurrently across 8 worker threads for 1,000 iterations,
  * *Then* zero deadlocks, thread starvations, or timeouts occur, and all threads complete cleanly in under 5 seconds.
* **Criterion 2 (Source Sensor Switch & Initial Value Propagation)**:
  * *Given* a `ProxySensor` with registered listeners when a new best sensor is selected in `MySensorManager`,
  * *When* `setBestSensorForProxySensor()` updates the source,
  * *Then* `ProxySensor.setSourceSensor()` executes without holding `MySensorManager` monitor, listeners receive the initial value from the new source, and subsequent source updates propagate to all listeners.
* **Criterion 3 (Lock-Free Callback Invariant)**:
  * *Given* `ProxySensor.addSensorListener(listener)` or `removeSensorListener(listener)`,
  * *When* inspected via thread lock reflection,
  * *Then* `!Thread.holdsLock(proxySensor)` holds true throughout listener execution.

### 1.5 System Invariants
* **Zero Coarse Lock Hold During Callbacks**: Neither `MySensorManager.this` nor `ProxySensor.this` monitor locks may be held while calling `SensorListener.newValue()`.
* **Clean-Room Regression Suite**: 100% pass rate across the full test suite (`./gradlew testDebugUnitTest`).
* **Localization Invariant**: No user-facing text strings are altered.

---

## 2. Test Specification (`TST-TRK-028`)

### Test Case 1: Concurrent ProxySensor Listener Registration & MySensorManager Mutex Deadlock Elimination (`TST-TRK-028.1`)
* **Scope**: Multi-threaded concurrency stress test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/SensorAndDeviceConcurrencyStressTest.kt`
* **Preconditions**: `MySensorManager`, `FakeRemoteDevice`, and `ProxySensor` instances.
* **Action**:
  1. Spawn 8 worker threads via `Executors.newFixedThreadPool(8)` synchronized with `CountDownLatch`.
  2. Threads 0..3: Continuously register and unregister sensors on `MySensorManager` across different device types.
  3. Threads 4..7: Continuously register and unregister dynamic `SensorListener`s on `ProxySensor`.
  4. The dynamic listener's `newValue(value)` implementation executes re-entrant queries into `MySensorManager` (`getSensors()`, `getAccumulatedSensorTypeSet()`, `getAllSensors()`, `registerSensor()`).
  5. Run 1,000 iterations per thread.
* **Expected Result**: All 8 threads complete within 15 seconds with zero deadlocks, zero `TimeoutException`, and zero `ConcurrentModificationException`.

### Test Case 2: Lock-Free Listener Callback Assertion (`TST-TRK-028.2`)
* **Scope**: Unit test / Concurrency contract
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/SensorAndDeviceConcurrencyStressTest.kt`
* **Preconditions**: `MySensorManager` and `ProxySensor`.
* **Action**:
  1. Register a test `SensorListener` on `ProxySensor`.
  2. Inside `newValue()`, evaluate `Thread.holdsLock(proxySensor)` and `Thread.holdsLock(sensorManager)`.
  3. Trigger:
     a) `proxySensor.addSensorListener(...)`
     b) `proxySensor.setSourceSensor(...)`
     c) `sensorManager.registerSensor(...)`
     d) `sensorManager.unregisterSensor(...)`
* **Expected Result**: `!Thread.holdsLock(proxySensor)` and `!Thread.holdsLock(sensorManager)` evaluate to `true` on every callback invocation.

### Test Case 3: Source Sensor Switch & Initial Value Propagation (`TST-TRK-028.3`)
* **Scope**: Unit test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/SensorAndDeviceConcurrencyStressTest.kt`
* **Preconditions**: `ProxySensor` bound to `sourceA`.
* **Action**:
  1. Register a listener on `ProxySensor`. Verify it receives `sourceA`'s value.
  2. Invoke `proxySensor.setSourceSensor(sourceB)`.
  3. Verify listener receives `sourceB`'s value immediately.
  4. Emit new value on `sourceB`. Verify listener receives update.
  5. Emit new value on `sourceA`. Verify listener does NOT receive update from disconnected `sourceA`.
* **Expected Result**: Source migration and value delivery function flawlessly without lock contention.

### Test Case 4: Clean-Room Full Suite Regression Execution (`TST-TRK-028.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with 0 failures and 0 regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-028.1` | Concurrency | `MySensorManager`, `ProxySensor` (A/B deadlock elimination) | `REQ-SEN-021.1`, `REQ-SEN-021.2`, `REQ-SEN-021.3` | Specified |
| `TST-TRK-028.2` | Unit / Contract | `ProxySensor`, `MySensorManager` (lock-free assertions) | `REQ-SEN-021.1`, `REQ-SEN-021.2`, `REQ-SEN-021.3` | Specified |
| `TST-TRK-028.3` | Unit / Functional | `ProxySensor.setSourceSensor` (initial value propagation) | `REQ-SEN-021.2` | Specified |
| `TST-TRK-028.4` | Regression | Full test suite | `REQ-SEN-021`, `REQ-PRO-001` | Specified |
