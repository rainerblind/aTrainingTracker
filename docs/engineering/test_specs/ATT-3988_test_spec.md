# Stage 2: Requirement & Test Specification - ATT-3988: [High] Make sensor and device layer thread-safe

**Ticket**: [ATT-3988](https://rainerblind.atlassian.net/browse/ATT-3988)  
**Sub-task**: [ATT-4080](https://rainerblind.atlassian.net/browse/ATT-4080) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `Next Minor Release` (Unset until Joint Review per Rule 14)  
**Active Sprint**: `2026-42.9`  
**Requirement Mapping**: `REQ-TRK-032` (*Thread-Safe Sensor, Device, Filter, Service & Live Session Concurrency Architecture*)  
**Test Spec ID**: `TST-TRK-024`  
**Branch**: `feature/ATT-3988`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Requirement Specification (`REQ-TRK-032`)

### 1.1 Problem Statement & Rationale
During active workouts and sensor discovery, `DeviceManager`, `MySensor`, `ProxySensor`, `MyAccumulatorSensor`, `MySensorManager`, `MyDevice`, `MyRemoteDevice`, `MyBTLEDevice`, `MyANTDevice`, `FilterManager`, `BANALService`, `TrackerService`, and `LiveWorkoutSession` are accessed concurrently across at least six execution contexts:
1. Android Main / UI thread (service lifecycle, binder IPC queries, broadcast receivers, GPS location updates)
2. Bluetooth LE GATT binder thread pool (`BluetoothGattCallback`, BLE scan callbacks)
3. ANT+ Plugin Service PCC handler threads (`IPluginAccessResultReceiver`, `IDeviceStateChangeReceiver`, ANT+ data receivers)
4. 1 Hz `ClockDevice` `java.util.Timer` thread
5. 1 Hz `TrackerService` `ScheduledExecutorService` sampling thread (`sampleAndWriteToDb()`)
6. `TrackerService` single-threaded DB executor (`startNewWorkout()`, `rehydrateLiveSession()`, `endWorkout()`, `finalizeLiveSession()`)

Previously, these classes relied on unsynchronized `HashMap` and `LinkedList` collections, non-`volatile` cross-thread fields, non-atomic `containsKey` + `put` device creation, direct exposure of live mutable lists (`mNewlyFoundDevices`), `null` assignments to collection fields on `shutDown()`, and lock-held `MySensor.newValue()` listener notifications in `MySensorManager.registerSensor()` / `unregisterSensor()`. `REQ-TRK-032` establishes lock-free / fine-grained thread safety, atomic registration, defensive collection snapshots, and deadlock-free listener dispatch across the entire sensor, device, filter, service, and live session pipeline.

### 1.2 Functional & Architectural Requirements (`REQ-TRK-032`)

1. **Thread-Safe `DeviceManager` Collections, Atomic Device Creation & Non-Null Teardown (`REQ-TRK-032.1`)**:
   - `DeviceManager.mMyRemoteDevices` SHALL be a `final Map<Long, MyRemoteDevice>` backed by `ConcurrentHashMap<>()`.
   - `DeviceManager.mMyRemoteDeviceTries` SHALL be a `final Map<MyRemoteDevice, Integer>` backed by `ConcurrentHashMap<>()` and reset via `mMyRemoteDeviceTries.clear()` rather than reassigning `new HashMap<>()`.
   - `DeviceManager.mSearchQueue` SHALL be a `final Queue<MyRemoteDevice>` backed by `ConcurrentLinkedQueue<>()`.
   - `DeviceManager.mNewlyFoundDevices` SHALL be a `final List<Long>` backed by `CopyOnWriteArrayList<>()`, and `getIdsOfNewlyFoundDevices()` SHALL return a defensive snapshot `new ArrayList<>(mNewlyFoundDevices)` rather than a live reference.
   - `createNewANTDevice(long deviceID)` and `createNewBluetoothLEDevice(long deviceID)` SHALL atomically register newly created devices via `mMyRemoteDevices.putIfAbsent(deviceID, device)`. If a race occurs where another thread already registered a device for `deviceID`, the newly constructed duplicate instance SHALL be shut down (`shutDown()`) and SHALL NOT be added to `mSearchQueue` or started twice.
   - `cMyRemoteDeviceCurrentlySearchingFor`, `mAntAsyncSearchEngine`, and `mBTSearchForNewDevicesEngine` SHALL be declared `volatile`, and all readers/mutators (`mSearchingStoppedForOneReceiver`, `searchForNextRemoteDevice()`, `getNameOfSearchingDevice()`, `isSearchingForNewRemoteDevices()`, `getNewRemoteDeviceTypeCurrentlySearchingFor()`, `stopSearchForNewRemoteDevices()`) SHALL capture local variable snapshots before dereferencing.
   - `DeviceManager.shutDown()` SHALL clear (`clear()`) `mMyRemoteDevices`, `mSearchQueue`, `mMyRemoteDeviceTries`, and `mNewlyFoundDevices` and SHALL NEVER set those collection references to `null`, preventing `NullPointerException` on late post-shutdown callbacks or binder queries.

2. **Thread-Safe `MySensor`, `ProxySensor`, `MyAccumulatorSensor` & Lock-Free `MySensorManager` Dispatch (`REQ-TRK-032.2`)**:
   - `MySensor.mSensorListeners` SHALL be a `final CopyOnWriteArrayList<SensorListener>()` using `addIfAbsent(sensorListener)` in `addSensorListener()` to prevent `ConcurrentModificationException` and duplicate listener registration.
   - `MySensor.mValue`, `MySensor.mActivated`, and `MySensor.mLastUpdateTimestamp` SHALL be declared `volatile`.
   - `MySensor.newValue(T value)` SHALL update `mValue = value` and `mLastUpdateTimestamp = System.currentTimeMillis()` and iterate `mSensorListeners` passing the local `value` snapshot (`sensorListener.newValue(value)`) without holding any coarse lock across listener execution.
   - `ProxySensor.setSourceSensor(MySensor<T> sourceSensor)` SHALL atomically swap `mSourceSensor` under a fine-grained lock on `ProxySensor.this` (updating listener registrations on the old and new source sensors via `CopyOnWriteArrayList`-backed `removeSensorListener` / `addSensorListener`), while `ProxySensor.newValue()` and `getValue()` remain lock-free on the `volatile` `mSourceSensor` reference.
   - `MyAccumulatorSensor.mInitialValue` and `mLastAbsoluteValue` SHALL be `volatile`, and `accumulate()`, `increment()`, `reset()`, and `setInitialValue()` SHALL compute updated accumulator values atomically before invoking `super.newValue(...)` outside the state lock.
   - `MySensorManager.mInitialValues` SHALL be backed by `ConcurrentHashMap<SensorType, Object>()`.
   - `MySensorManager.registerSensor()` and `unregisterSensor()` SHALL update internal sensor maps and compute updated GC sensor strings inside `synchronized (this)`, and SHALL invoke `mSensorsSensor.newValue(...)` and `mAccumulatedSensorsSensor.newValue(...)` **outside** the `synchronized` block so `MySensorManager`'s monitor lock is never held while dispatching `SensorListener.newValue()` callbacks (completing the cross-referenced `ATT-3986` guardrail).

3. **Device State Visibility & `FilterManager` Concurrency (`REQ-TRK-032.3`)**:
   - `MyBTLEDevice.mState` and `MyBTLEDevice.mBatteryCharacteristic` SHALL be declared `volatile`.
   - `MyANTDevice.mDeviceState`, `MyRemoteDevice.mSearching`, `MyRemoteDevice.mCalibrationFactor`, `MyDevice.mSensorsRegistered`, and `MyDevice.mBatteryPercentage` SHALL be declared `volatile`.
   - `FilterManager.mRequiredButNotAvailableFilters`, `FilterManager.myFilterMap`, and `FilterManager.mMapFilter2Sensor` SHALL be backed by `ConcurrentHashMap` / `CopyOnWriteArrayList` so concurrent filter creation (`getFilteredSensor` / `createFilter`), sensor change broadcasts (`mSensorsChangedReceiver`), and 1 Hz sampling (`getAllFilteredSensorData()`, `resetAllFilters()`, `shutDown()`) never throw `ConcurrentModificationException`.

4. **Null-Safe & Volatile `BANALService` Static and Binder Accessors (`REQ-TRK-032.4`)**:
   - `BANALService.cDeviceManager`, `BANALService.cSensorManager`, and `BANALService.cFilterManager` SHALL be declared `volatile`.
   - All static and `BANALServiceComm` binder methods in `BANALService` SHALL capture local snapshots of `cDeviceManager`, `cSensorManager`, and `cFilterManager` and guard against `null`, returning safe empty collections (`Collections.emptyList()`, `Collections.emptySet()`, `new SensorType[0]`), `false`, `0`, or `null` when invoked before `onCreate()` or after `onDestroy()`.

5. **Thread-Safe `TrackerService` Cross-Thread Fields & Synchronized `LiveWorkoutSession` (`REQ-TRK-032.5`)**:
   - `TrackerService.mSearching`, `mTrackerHandle`, `mTrackingInterrupted`, `mLiveSession`, `mBaseFileName`, `mTimeTotal_s`, `mTimeActive_s`, and `mDistanceTotal_m` SHALL be declared `volatile`, and `TrackerService` methods (`sampleAndWriteToDb()`, `finalizeLiveSession()`, `mAltitudeCorrectionReceiver.onReceive()`, `cancelScheduledTracker()`) SHALL capture local snapshots of `mLiveSession` and `mTrackerHandle` before dereferencing.
   - `LiveWorkoutSession` SHALL synchronize all state mutators and accessors (`addSample`, `recordStreamPoint`, `applyAltitudeCorrection`, `rehydrateStreams`, `rehydrateSensorStats`, `addRawLatLng`, `getRawLatLngs`, `getSampledLatLngs`, `getSampledAltitudes`, `getSampledDistances`, `getSensorStats`, `getStartLatLng`, `setStartLatLng`, `getLastLatLng`, `setLastLatLng`, `getLastRecordedTimeS`) and return defensive copies from `getRawLatLngs()`, `getSampledLatLngs()`, `getSampledAltitudes()`, `getSampledDistances()`, and `getSensorStats()` (including a thread-safe `RunningStats` snapshot or synchronized `RunningStats` getters) so concurrent execution of `sampleAndWriteToDb()` (`mScheduler`), `applyAltitudeCorrection()` (Main thread), `rehydrateLiveSession()` (`mDbExecutor`), and `finalizeLiveSession()` (`mDbExecutor`) never throws `ConcurrentModificationException` or corrupts streams/extrema.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (`DeviceManager` Concurrent Registration, Iteration, Defensive Copy & Post-Shutdown Safety)**:
  * *Given* an active `DeviceManager` instance,
  * *When* multiple threads concurrently register ANT+/BLE devices (`createNewANTDevice`, `createNewBluetoothLEDevice`), append newly found device IDs, iterate `getActiveRemoteDevices()` / `getRemoteDeviceData()` / `getIdsOfNewlyFoundDevices()`, and invoke `shutDown()`,
  * *Then* zero `ConcurrentModificationException` or `NullPointerException` errors occur, `getIdsOfNewlyFoundDevices()` returns an isolated defensive copy, duplicate device creations for the same `deviceID` are atomically deduplicated via `putIfAbsent`, and post-`shutDown()` queries return empty collections without throwing `NullPointerException`.
* **Criterion 2 (`MySensor`, `ProxySensor`, `MyAccumulatorSensor` & Lock-Free `MySensorManager` Concurrency)**:
  * *Given* multiple sensor producer threads (`ClockDevice`, BLE GATT, ANT+, GPS) calling `MySensor.newValue()`, `MyAccumulatorSensor.increment()` / `accumulate()`, and `MySensorManager.registerSensor()` / `unregisterSensor()` while reader threads add/remove `SensorListener`s, swap `ProxySensor.setSourceSensor()`, and query `MySensorManager`,
  * *Then* zero `ConcurrentModificationException` or deadlocks occur, `MySensorManager` does NOT hold `synchronized (this)` while invoking `mSensorsSensor.newValue()` or `mAccumulatedSensorsSensor.newValue()`, and listeners receive consistent non-corrupted values.
* **Criterion 3 (`BANALService`, `FilterManager`, `TrackerService` & `LiveWorkoutSession` Concurrency)**:
  * *Given* concurrent 1 Hz `sampleAndWriteToDb()` ticks, Main-thread `applyAltitudeCorrection()` broadcasts, `rehydrateLiveSession()`, and `finalizeLiveSession()` calls, as well as `BANALService` static/binder queries before `onCreate()` or after `onDestroy()`,
  * *Then* `LiveWorkoutSession` streams and sensor stats remain consistent without `ConcurrentModificationException`, and `BANALService` methods return safe fallback defaults without `NullPointerException`.

### 1.4 System Invariants
* **Deadlock Prevention Invariant**: Coarse manager locks (`DeviceManager`, `MySensorManager`, `MySensor`) MUST NEVER be held while invoking external `SensorListener.newValue()` callbacks or cross-component device queries.
* **Existing Teardown & Lifecycle Contract Preservation**: `DeviceManagerTeardownTest`, `DeviceManagerLocationInitTest`, `FreshInstallLocationInitTest`, `MyBTLEDeviceLifecycleTest`, `TrackerServiceResilienceTest`, `TrackerServiceFinalizationTest`, `TrackerServiceResumeTest`, and `LiveWorkoutSessionTest` MUST continue to pass 100%.
* **Localization Invariant**: No UI strings are added or modified (`9`-locale parity remains 100% intact).

---

## 2. Test Specification (`TST-TRK-024`)

### Test Case 1: `DeviceManager` Concurrent Device Creation, Defensive Copy & Post-Shutdown Safety (`TST-TRK-024.1`)
* **Scope**: Unit & Concurrency Stress Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/SensorAndDeviceConcurrencyStressTest.kt` (and `DeviceManagerTeardownTest.kt`)
* **Preconditions**: `DeviceManager` initialized with mocked `Context` and `MySensorManager`.
* **Action**:
  1. Verify via reflection that `mMyRemoteDevices` and `mMyRemoteDeviceTries` are `ConcurrentHashMap`, `mSearchQueue` is `ConcurrentLinkedQueue`, `mNewlyFoundDevices` is `CopyOnWriteArrayList`, and `cMyRemoteDeviceCurrentlySearchingFor`, `mAntAsyncSearchEngine`, `mBTSearchForNewDevicesEngine` are `volatile`.
  2. Mutate the list returned by `getIdsOfNewlyFoundDevices()` and verify internal `mNewlyFoundDevices` is unaffected.
  3. Run multi-threaded stress workers concurrently populating `mMyRemoteDevices` (via `putIfAbsent`), mutating `mNewlyFoundDevices`, and calling `getActiveRemoteDevices()`, `getIdsOfActiveDevices()`, `getRemoteDeviceData()`, and `getIdsOfNewlyFoundDevices()`.
  4. Invoke `shutDown()` while reader threads query `DeviceManager`, and verify `mMyRemoteDevices` and `mSearchQueue` are empty (not `null`) and post-shutdown calls (`getRemoteDeviceData()`, `getActiveRemoteDevices()`, `stopSearch()`, `getIdsOfNewlyFoundDevices()`) do not throw `NullPointerException`.
* **Expected Result**: Zero exceptions (`ConcurrentModificationException`, `NullPointerException`), defensive copy verified, post-shutdown safety verified.

### Test Case 2: `MySensor`, `ProxySensor`, `MyAccumulatorSensor` & Lock-Free `MySensorManager` Stress Test (`TST-TRK-024.2`)
* **Scope**: Unit & Concurrency Stress Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/SensorAndDeviceConcurrencyStressTest.kt`
* **Preconditions**: `MySensor`, `ProxySensor`, `MyAccumulatorSensor`, and `MySensorManager` instances.
* **Action**:
  1. Verify via reflection that `MySensor.mSensorListeners` is `CopyOnWriteArrayList` and `mValue`, `mActivated`, `mLastUpdateTimestamp` are `volatile`.
  2. Spawn concurrent threads adding/removing `SensorListener`s, calling `MySensor.newValue()`, swapping `ProxySensor.setSourceSensor()`, and calling `MyAccumulatorSensor.increment()` / `accumulate()` / `reset()`.
  3. Register a `SensorListener` on `MySensorManager`'s `SensorType.SENSORS` sensor that asserts `!Thread.holdsLock(mySensorManager)` whenever `newValue()` is invoked during `registerSensor()` and `unregisterSensor()`, and spawns cross-thread queries back into `MySensorManager` to prove zero deadlock.
* **Expected Result**: `!Thread.holdsLock(mySensorManager)` is `true` inside `SensorListener.newValue()`, zero `ConcurrentModificationException`s, zero deadlocks, exact accumulator arithmetic under concurrent increments.

### Test Case 3: `FilterManager`, `BANALService`, `TrackerService` & `LiveWorkoutSession` Concurrency Test (`TST-TRK-024.3`)
* **Scope**: Unit & Concurrency Stress Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/SensorAndDeviceConcurrencyStressTest.kt`
* **Preconditions**: `FilterManager`, `BANALService`, `TrackerService`, and `LiveWorkoutSession` instances.
* **Action**:
  1. Verify `volatile` modifiers on `MyBTLEDevice` (`mState`, `mBatteryCharacteristic`), `MyANTDevice` (`mDeviceState`), `MyRemoteDevice` (`mSearching`, `mCalibrationFactor`), `MyDevice` (`mSensorsRegistered`, `mBatteryPercentage`), `BANALService` (`cDeviceManager`, `cSensorManager`, `cFilterManager`), and `TrackerService` (`mSearching`, `mTrackerHandle`, `mTrackingInterrupted`, `mLiveSession`, `mBaseFileName`, `mTimeTotal_s`, `mTimeActive_s`, `mDistanceTotal_m`).
  2. Invoke `BANALService` static methods (`getAllFilteredSensorData()`, `getIdsOfActiveDevices()`, `getRemoteDeviceData()`, `getDeviceList()`, `isSearching()`, `getSensorTypeArray()`, `getActiveSensorTypeArray()`, `getAccumulatedSensorTypeSet()`) when `cDeviceManager`, `cSensorManager`, and `cFilterManager` are `null`, verifying safe non-throwing defaults.
  3. Run multi-threaded stress test on `LiveWorkoutSession` where Thread 1 calls `addSample()`, `recordStreamPoint()`, and `addRawLatLng()`, Thread 2 calls `applyAltitudeCorrection()`, Thread 3 calls `rehydrateStreams()` / `rehydrateSensorStats()`, and Thread 4 iterates `getSampledLatLngs()`, `getSampledAltitudes()`, `getSampledDistances()`, `getRawLatLngs()`, and `getSensorStats()`.
* **Expected Result**: Zero `ConcurrentModificationException` or `NullPointerException`, defensive copies returned by `LiveWorkoutSession` getters, all `volatile` invariants verified.

### Test Case 4: Clean-Room Regression Suite (`TST-TRK-024.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the entire unit test suite (`3,120+` tests).

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-024.1` | Unit / Concurrency | `DeviceManager` (`mMyRemoteDevices`, `putIfAbsent`, `getIdsOfNewlyFoundDevices`, `shutDown`) | `REQ-TRK-032.1` | Specified |
| `TST-TRK-024.2` | Unit / Concurrency | `MySensor`, `ProxySensor`, `MyAccumulatorSensor`, `MySensorManager` (lock-free `newValue`) | `REQ-TRK-032.2` | Specified |
| `TST-TRK-024.3` | Unit / Concurrency | `MyBTLEDevice`, `MyANTDevice`, `FilterManager`, `BANALService`, `TrackerService`, `LiveWorkoutSession` | `REQ-TRK-032.3`, `REQ-TRK-032.4`, `REQ-TRK-032.5` | Specified |
| `TST-TRK-024.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-TRK-032`, `REQ-PRO-001` | Specified |
