# Stage 3: Implementation Plan - ATT-3988: [High] Make sensor and device layer thread-safe

**Ticket**: [ATT-3988](https://rainerblind.atlassian.net/browse/ATT-3988)  
**Sub-task**: [ATT-4081](https://rainerblind.atlassian.net/browse/ATT-4081) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `Next Minor Release` (Unset until Joint Review per Rule 14)  
**Active Sprint**: `2026-42.9`  
**Requirement Mapping**: `REQ-TRK-032` (*Thread-Safe Sensor, Device, Filter, Service & Live Session Concurrency Architecture*)  
**Test Mapping**: `TST-TRK-024` (*Thread-Safe Sensor, Device, Filter, Service & Live Session Concurrency Verification Suite*)  
**Branch**: `feature/ATT-3988`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Description & Background

The sensor, device, filter, service, and live session layers (`DeviceManager`, `MySensor`, `ProxySensor`, `MyAccumulatorSensor`, `MySensorManager`, `MyDevice`, `MyRemoteDevice`, `MyBTLEDevice`, `MyANTDevice`, `FilterManager`, `BANALService`, `TrackerService`, and `LiveWorkoutSession`) are accessed concurrently across at least 6 distinct threads:
1. Android Main / UI Thread
2. Bluetooth LE GATT Binder Thread Pool
3. ANT+ Plugin Service PCC Handler Threads
4. `ClockDevice` 1 Hz `java.util.Timer` Thread
5. `TrackerService` 1 Hz `ScheduledExecutorService` Thread (`sampleAndWriteToDb()`)
6. `TrackerService` Single-Threaded DB Executor (`startNewWorkout()`, `rehydrateLiveSession()`, `endWorkout()`, `finalizeLiveSession()`)

Currently, unsynchronized `HashMap`/`LinkedList` collections, non-`volatile` cross-thread fields, non-atomic `containsKey` + `put` device creation, direct exposure of live mutable lists (`mNewlyFoundDevices`), `null` assignments to collection fields on `shutDown()`, and lock-held `MySensor.newValue()` listener notifications in `MySensorManager.registerSensor()` / `unregisterSensor()` expose the application to `ConcurrentModificationException`, `NullPointerException`, duplicate device registration, stale reads, and lock-ordering deadlocks.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-TRK-032` (*Thread-Safe Sensor, Device, Filter, Service & Live Session Concurrency Architecture*)
  * `REQ-TRK-032.1`: Thread-Safe `DeviceManager` Collections, Atomic Device Creation & Non-Null Teardown
  * `REQ-TRK-032.2`: Thread-Safe `MySensor`, `ProxySensor`, `MyAccumulatorSensor` & Lock-Free `MySensorManager` Listener Dispatch
  * `REQ-TRK-032.3`: Device State Visibility (`MyBTLEDevice`, `MyANTDevice`, `MyRemoteDevice`, `MyDevice`) & `FilterManager` Concurrency
  * `REQ-TRK-032.4`: Null-Safe & Volatile `BANALService` Static and Binder Accessors
  * `REQ-TRK-032.5`: Thread-Safe `TrackerService` Cross-Thread Fields & Synchronized `LiveWorkoutSession`
* **Test Mapping**: `TST-TRK-024` (`TST-TRK-024.1` – `TST-TRK-024.4`)

---

## 3. System Invariants & Preserved Behavior

1. **Deadlock Prevention Guardrail**: Coarse manager locks (`DeviceManager`, `MySensorManager`, `MySensor`) MUST NEVER be held while invoking external `SensorListener.newValue()` callbacks or cross-component device queries.
2. **Non-Null Teardown Safety**: `DeviceManager.shutDown()` MUST clear (`clear()`) its collections rather than setting references to `null`, while preserving 100% compatibility with `DeviceManagerTeardownTest` and `MyBTLEDeviceLifecycleTest`.
3. **Zero Unintended Regressions**: All 3,120+ existing unit tests continue to pass cleanly.
4. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)` (Rule 3), and `fixVersion` remains unset (Rule 14).

---

## 4. Proposed Architectural Changes

### Component 1: `DeviceManager.java` (`REQ-TRK-032.1`)
* Replace `protected Map<Long, MyRemoteDevice> mMyRemoteDevices = new HashMap<>()` with `protected final Map<Long, MyRemoteDevice> mMyRemoteDevices = new ConcurrentHashMap<>()`.
* Replace `protected Map<MyRemoteDevice, Integer> mMyRemoteDeviceTries = new HashMap<>()` with `protected final Map<MyRemoteDevice, Integer> mMyRemoteDeviceTries = new ConcurrentHashMap<>()`, and in `startSearchForPairedDevices()` call `mMyRemoteDeviceTries.clear()` instead of `mMyRemoteDeviceTries = new HashMap<>()`.
* Replace `protected Queue<MyRemoteDevice> mSearchQueue = new LinkedList<>()` with `protected final Queue<MyRemoteDevice> mSearchQueue = new ConcurrentLinkedQueue<>()`.
* Replace `protected List<Long> mNewlyFoundDevices = new LinkedList<>()` with `protected final List<Long> mNewlyFoundDevices = new CopyOnWriteArrayList<>()`, and update `getIdsOfNewlyFoundDevices()` to return `new ArrayList<>(mNewlyFoundDevices)`.
* Mark `cMyRemoteDeviceCurrentlySearchingFor`, `mAntAsyncSearchEngine`, and `mBTSearchForNewDevicesEngine` as `volatile`, and capture local variable snapshots in `mSearchingStoppedForOneReceiver.onReceive()`, `searchForNextRemoteDevice()`, `getNameOfSearchingDevice()`, `isSearchingForNewRemoteDevices()`, `getNewRemoteDeviceTypeCurrentlySearchingFor()`, and `stopSearchForNewRemoteDevices()`.
* In `createNewANTDevice(long deviceID)` and `createNewBluetoothLEDevice(long deviceID)`:
  * Fast-path check `if (mMyRemoteDevices.containsKey(deviceID)) return;`.
  * After constructing the `MyANTDevice` / `MyBTLEDevice` instance, atomically register via `MyRemoteDevice existing = mMyRemoteDevices.putIfAbsent(deviceID, device);`.
  * If `existing != null`, call `device.shutDown()` and `return;` without adding to `mSearchQueue` or starting a duplicate search.
* In `shutDown()`, guard `mMyRemoteDevices`, `mSearchQueue`, `mMyRemoteDeviceTries`, and `mNewlyFoundDevices`, shut down all active remote devices, and call `.clear()` on all four collections instead of assigning `null`.

### Component 2: `MySensor.java`, `ProxySensor.java`, `MyAccumulatorSensor.java` & `MySensorManager.java` (`REQ-TRK-032.2`)
* **`MySensor.java`**:
  * Change `mSensorListeners` to `protected final CopyOnWriteArrayList<SensorListener> mSensorListeners = new CopyOnWriteArrayList<>()`.
  * In `addSensorListener()`, use `mSensorListeners.addIfAbsent(sensorListener)`.
  * Declare `mValue`, `mActivated`, and `mLastUpdateTimestamp` as `volatile`.
  * In `newValue(T value)`, assign `mValue = value;` and `mLastUpdateTimestamp = System.currentTimeMillis();` and pass the local `value` snapshot to `sensorListener.newValue(value)`.
* **`ProxySensor.java`**:
  * Synchronize `setSourceSensor(MySensor<T> sourceSensor)` on `this` to atomically detach listeners from the previous `mSourceSensor`, update `mSourceSensor = sourceSensor`, and attach `mSensorListeners` to the new `sourceSensor` (since `MySensor.addSensorListener` uses `CopyOnWriteArrayList.addIfAbsent`, no coarse lock is acquired).
  * Override `addSensorListener` and `removeSensorListener` with `synchronized` on `ProxySensor.this` so adding/removing a listener while `setSourceSensor` runs is mutually atomic with respect to `mSourceSensor`.
  * Keep `getValue()`, `newValue()`, `getLastUpdateTimestamp()`, and `getSensorData()` lock-free using local snapshots of `final MySensor<T> source = mSourceSensor;`.
* **`MyAccumulatorSensor.java`**:
  * Declare `mInitialValue` and `mLastAbsoluteValue` as `volatile`.
  * In `accumulate()`, `increment()`, `reset()`, and `setInitialValue()`, compute the new value inside a short `synchronized (this)` critical section and invoke `super.newValue(updatedValue)` **outside** the `synchronized (this)` block so listener dispatch is never executed under a lock.
* **`MySensorManager.java`**:
  * Change `mInitialValues` from `HashMap` to `final Map<SensorType, Object> mInitialValues = new ConcurrentHashMap<>()`.
  * Refactor `registerSensor(MySensor mySensor)` and `unregisterSensor(MySensor mySensor)` so that state updates and calculation of the updated GC strings (`String sensorsString`, `String accSensorsString`) occur inside `synchronized (this)`, and `mSensorsSensor.newValue(sensorsString)` / `mAccumulatedSensorsSensor.newValue(accSensorsString)` are invoked **outside** `synchronized (this)`.

### Component 3: `MyBTLEDevice.java`, `MyANTDevice.java`, `MyRemoteDevice.java`, `MyDevice.java` & `FilterManager.java` (`REQ-TRK-032.3`)
* **`MyBTLEDevice.java`**: Declare `mState` and `mBatteryCharacteristic` as `volatile`.
* **`MyANTDevice.java`**: Declare `mDeviceState` as `volatile`.
* **`MyRemoteDevice.java`**: Declare `mSearching` and `mCalibrationFactor` as `volatile`.
* **`MyDevice.java`**: Declare `mSensorsRegistered` and `mBatteryPercentage` as `volatile`.
* **`FilterManager.java`**:
  * Change `mRequiredButNotAvailableFilters` to `final Map<SensorType, CopyOnWriteArrayList<FilterData>> mRequiredButNotAvailableFilters = new ConcurrentHashMap<>()`.
  * Change `myFilterMap` to `final Map<String, FilteredSensor> myFilterMap = new ConcurrentHashMap<>()`.
  * Change `mMapFilter2Sensor` to `final Map<FilteredSensor, MySensor> mMapFilter2Sensor = new ConcurrentHashMap<>()`.
  * Use `computeIfAbsent(filterData.sensorType, k -> new CopyOnWriteArrayList<>()).addIfAbsent(filterData)` when registering pending filters.

### Component 4: `BANALService.java` (`REQ-TRK-032.4`)
* Declare `cDeviceManager`, `cSensorManager`, and `cFilterManager` as `private static volatile`.
* Update all static methods (`getAllFilteredSensorData()`, `getBestSensor()`, `getSensorString()`, `getAccumulatedSensorString()`, `setInitialSensorValue()`, `isSearching()`, `getIdsOfActiveDevices()`, `getSensorTypeArray()`, `getActiveSensorTypeArray()`, `getAccumulatedSensorTypeSet()`, `getRemoteDeviceData()`, `getDeviceList()`) and `BANALServiceComm` binder methods to read local references (`final DeviceManager dm = cDeviceManager;`, etc.) and return safe empty collections (`Collections.emptyList()`, `Collections.emptySet()`, `new SensorType[0]`), `false`, `0`, or `null` when the manager reference is `null`.

### Component 5: `TrackerService.java` & `LiveWorkoutSession.java` (`REQ-TRK-032.5`)
* **`TrackerService.java`**:
  * Declare `mSearching`, `mTrackerHandle`, `mTrackingInterrupted`, `mLiveSession`, `mBaseFileName`, `mTimeTotal_s`, `mTimeActive_s`, and `mDistanceTotal_m` as `volatile`.
  * Capture local variable snapshots of `mLiveSession` in `sampleAndWriteToDb()`, `mAltitudeCorrectionReceiver.onReceive()`, and `finalizeLiveSession()`, and capture a local snapshot of `mTrackerHandle` in `cancelScheduledTracker()`.
* **`LiveWorkoutSession.java`**:
  * Make all state mutators and accessors on `LiveWorkoutSession` (`addSample`, `recordStreamPoint`, `applyAltitudeCorrection`, `rehydrateStreams`, `rehydrateSensorStats`, `addRawLatLng`, `getRawLatLngs`, `getSampledLatLngs`, `getSampledAltitudes`, `getSampledDistances`, `getSensorStats`, `getStartLatLng`, `setStartLatLng`, `getLastLatLng`, `setLastLatLng`, `getLastRecordedTimeS`) `synchronized`.
  * Return defensive copies from `getRawLatLngs()`, `getSampledLatLngs()`, `getSampledAltitudes()`, `getSampledDistances()`, and `getSensorStats()`.
  * Synchronize `RunningStats` methods (`update`, `rehydrate`, `getMin`, `getMax`, `getAverage`, `getCount`, `isMinChanged`, `isMaxChanged`, `isValid`) so concurrent inspection during `sampleAndWriteToDb()` and `finalizeLiveSession()` is 100% race-free.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Mandatory Gate 3 Pre-Check (Rule 4)
* Command: `python3 tools/jira_util.py check-gate ATT-4081`
* Confirm exit code `0` (`GATE_PASSED: ATT-4081 is Erledigt`) before touching any file under `app/src/`.

### Step 2: Harden `DeviceManager.java`, `MyDevice.java`, `MyRemoteDevice.java`, `MyBTLEDevice.java`, and `MyANTDevice.java`
* Files:
  * `app/src/main/java/com/atrainingtracker/banalservice/devices/DeviceManager.java`
  * `app/src/main/java/com/atrainingtracker/banalservice/devices/MyDevice.java`
  * `app/src/main/java/com/atrainingtracker/banalservice/devices/MyRemoteDevice.java`
  * `app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/MyBTLEDevice.java`
  * `app/src/main/java/com/atrainingtracker/banalservice/devices/ant_plus/MyANTDevice.java`
* Changes: Apply `ConcurrentHashMap`, `ConcurrentLinkedQueue`, `CopyOnWriteArrayList`, defensive copy in `getIdsOfNewlyFoundDevices()`, atomic `putIfAbsent` in `createNewANTDevice` / `createNewBluetoothLEDevice`, non-null `.clear()` in `shutDown()`, and `volatile` state fields.

### Step 3: Harden `MySensor.java`, `ProxySensor.java`, `MyAccumulatorSensor.java`, `MySensorManager.java`, `FilterManager.java`, and `BANALService.java`
* Files:
  * `app/src/main/java/com/atrainingtracker/banalservice/sensor/MySensor.java`
  * `app/src/main/java/com/atrainingtracker/banalservice/sensor/ProxySensor.java`
  * `app/src/main/java/com/atrainingtracker/banalservice/sensor/MyAccumulatorSensor.java`
  * `app/src/main/java/com/atrainingtracker/banalservice/sensor/MySensorManager.java`
  * `app/src/main/java/com/atrainingtracker/banalservice/filters/FilterManager.java`
  * `app/src/main/java/com/atrainingtracker/banalservice/BANALService.java`
* Changes: Apply `CopyOnWriteArrayList` and `volatile` fields in `MySensor`, synchronized listener migration in `ProxySensor.setSourceSensor`, lock-free listener dispatch in `MyAccumulatorSensor` and `MySensorManager`, `ConcurrentHashMap` in `FilterManager`, and `volatile` null-safe snapshots in `BANALService`.

### Step 4: Harden `TrackerService.java` and `LiveWorkoutSession.java`
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/LiveWorkoutSession.java`
* Changes: Add `volatile` modifiers and local snapshots in `TrackerService`; synchronize `LiveWorkoutSession` and `RunningStats` methods and return defensive copies from collection getters.

### Step 5: Write `SensorAndDeviceConcurrencyStressTest.kt` & Run Targeted Unit Tests
* Files:
  * `app/src/test/java/com/atrainingtracker/banalservice/SensorAndDeviceConcurrencyStressTest.kt`
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.SensorAndDeviceConcurrencyStressTest" --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.banalservice.sensor.*" --tests "com.atrainingtracker.trainingtracker.tracker.*"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit and concurrency stress tests in Stage 4 (~10–15s), followed by the full clean-room regression suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-3988` allows clean revert without affecting `sprint/2026-42.9` or `develop`.
