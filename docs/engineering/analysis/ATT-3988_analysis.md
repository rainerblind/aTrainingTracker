# Stage 1 Analysis: ATT-3988 — Make sensor and device layer thread-safe

## 1. Executive Summary
- **Ticket**: `ATT-3988` — `[High] Make sensor and device layer thread-safe` (Epic: `ATT-431` *Clean Code*)
- **Classification**: Architectural Concurrency & Thread-Safety Hardening (Bug / Defect Prevention)
- **Root Cause**: The sensor (`MySensor`, `ProxySensor`, `MyAccumulatorSensor`, `MySensorManager`), device (`DeviceManager`, `MyDevice`, `MyRemoteDevice`, `MyBTLEDevice`, `MyANTDevice`), filter (`FilterManager`), service bridge (`BANALService`), and tracker (`TrackerService`, `LiveWorkoutSession`) layers rely on unsynchronized `HashMap`/`LinkedList` collections, non-`volatile` cross-thread state variables, non-atomic check-then-act device creation, live mutable list exposure (`mNewlyFoundDevices`), `null` assignments to collection fields during teardown (`shutDown()`), and lock-held listener dispatch (`MySensorManager.registerSensor`/`unregisterSensor` calling `MySensor.newValue()` while holding `MySensorManager.this`). Because these components are accessed concurrently across at least 6 distinct execution contexts (Android Main/UI thread, Bluetooth LE GATT binder thread pool, ANT+ Plugin PCC handler threads, 1 Hz `ClockDevice` `java.util.Timer` thread, 1 Hz `TrackerService` `ScheduledExecutorService` thread, and `TrackerService` DB executor thread), they are susceptible to `ConcurrentModificationException`, `NullPointerException`, stale memory visibility, duplicate hardware device instances, and potential lock-ordering deadlocks.

## 2. Problem Domain & Concurrency Topology

### 2.1 Active Execution Contexts (Threads)
1. **Android Main / UI Thread**:
   - `BANALService.onCreate()` / `onDestroy()`, `BANALServiceComm` binder calls (`getIdsOfNewlyFoundDevices()`, `getSensorData()`, `getAllSensorData()`, `resetAccumulators()`, `startSearchForFailedDevices()`).
   - `BroadcastReceiver` callbacks in `DeviceManager`, `FilterManager`, `TrackerService` (`mAltitudeCorrectionReceiver`, `mPairingChangedReceiver`, `mSearchingFinishedReceiver`, `mSensorsChangedReceiver`), and `MyBTLEDevice` (`mDisconnectHandler` on `Looper.getMainLooper()`).
   - `LocationListener` (`MyLocationManager` / GPS callbacks on Main Looper).
2. **Bluetooth LE GATT Binder Threads (`BluetoothGattCallback`)**:
   - Invokes `MyBTLEDevice.mGattCallback.onConnectionStateChange()`, `onServicesDiscovered()`, `onCharacteristicRead()`, `onCharacteristicChanged()` on arbitrary Binder threads, mutating `mState`, `mBatteryCharacteristic`, registering/unregistering sensors in `MySensorManager`, and calling `MySensor.newValue()`.
   - Invokes `BTSearchForNewDevicesEngine` scan callbacks (`onLeScan` / `ScanCallback`), calling `DeviceManager.mNewDeviceFoundListener.onNewDeviceFound()` -> `createNewBluetoothLEDevice()` and mutating `mNewlyFoundDevices`.
3. **ANT+ Plugin Service PCC Threads**:
   - Invokes `IPluginAccessResultReceiver.onResultReceived()`, `IDeviceStateChangeReceiver.onDeviceStateChange()`, and data event receivers in `MyANTDevice` subclasses, mutating `mDeviceState`, notifying `DeviceManager.onSearchStopped()`, registering/unregistering sensors, and calling `MySensor.newValue()`.
   - Invokes `ANTAsyncSearchEngine` callbacks calling `DeviceManager.mNewDeviceFoundListener.onNewDeviceFound()` -> `createNewANTDevice()`.
4. **`ClockDevice` Timer Thread (`java.util.Timer`)**:
   - Fires every 1000 ms on a dedicated background `Timer` thread (`ClockDevice.java:46`), calling `mTimeActiveSensor.increment(1)`, `mTimeTotalSensor.increment(1)`, `mLapSensor.newValue(mLapNr)`.
5. **`TrackerService` 1 Hz Scheduler Thread (`Executors.newSingleThreadScheduledExecutor()`)**:
   - Executes `sampleAndWriteToDb()` every 1 second (`TrackerService.java:361`), reading `BANALService.getAllFilteredSensorData()`, `BANALService.getIdsOfActiveDevices()`, mutating `LiveWorkoutSession`, `mTimeTotal_s`, `mTimeActive_s`, `mDistanceTotal_m`, and writing to `WorkoutSamplesDbHelper`.
6. **`TrackerService` DB Executor Thread (`Executors.newSingleThreadExecutor()`)**:
   - Executes `startNewWorkout()`, `rehydrateLiveSession()`, and `endWorkout()` -> `finalizeLiveSession()`, mutating and reading `LiveWorkoutSession` and `mBaseFileName`.

### 2.2 Detailed Defect Inventory

#### A. `DeviceManager.java`
1. **Unsynchronized `mMyRemoteDevices` (`line 101`)**:
   - Declared as `protected Map<Long, MyRemoteDevice> mMyRemoteDevices = new HashMap<>()`.
   - Mutated on Main thread (`putAllDevicesToSearchQueue`), ANT+ callback threads (`createNewANTDevice`), and BLE scan callback threads (`createNewBluetoothLEDevice`).
   - Iterated concurrently on 1 Hz `TrackerService` thread (`getActiveRemoteDevices()`, `getIdsOfActiveDevices()`), Main thread (`getRemoteDeviceData()`, `getBestSensor()`, `startSearchForFailedDevices()`, `stopSearch()`, `shutDown()`), and `MySensorManager` (`getSensorList()`).
2. **Non-atomic check-then-act in `createNewANTDevice` (`lines 850-891`) and `createNewBluetoothLEDevice` (`lines 906-944`)**:
   - Checks `if (!mMyRemoteDevices.containsKey(deviceID))` and later calls `mMyRemoteDevices.put(deviceID, myANTDevice)`. Two rapid scan results for the same device can both pass `containsKey`, instantiating and starting two `MyRemoteDevice` instances for the same database ID.
3. **Unsynchronized `mMyRemoteDeviceTries` (`lines 102, 627`)**:
   - Declared as `protected Map<MyRemoteDevice, Integer> mMyRemoteDeviceTries = new HashMap<>()` and reassigned via `mMyRemoteDeviceTries = new HashMap<>()` in `startSearchForPairedDevices()`.
   - Accessed from `mSearchingStoppedForOneReceiver` and `startSearchForPairedDevices()`, risking NPE or lost updates on concurrent re-search.
4. **Unsynchronized `mSearchQueue` (`line 103`)**:
   - Declared as `protected Queue<MyRemoteDevice> mSearchQueue = new LinkedList<>()`.
   - Polled in `searchForNextRemoteDevice()` (which can be triggered from `mSearchingStoppedForOneReceiver` or `startSearchForPairedDevices()`) and cleared in `stopSearch()` / `shutDown()`.
5. **Unsynchronized `mNewlyFoundDevices` & Live List Exposure (`lines 104, 204, 234, 721-724`)**:
   - Declared as `protected List<Long> mNewlyFoundDevices = new LinkedList<>()`.
   - Appended from BLE/ANT+ scan callbacks (`mNewlyFoundDevices.add(deviceId)`) while returned as a direct live reference by `getIdsOfNewlyFoundDevices()` (`return mNewlyFoundDevices;`) and iterated by UI Fragments on the Main thread.
6. **Non-`volatile` Search State Fields (`lines 85, 105, 106`)**:
   - `cMyRemoteDeviceCurrentlySearchingFor`, `mAntAsyncSearchEngine`, and `mBTSearchForNewDevicesEngine` are read and mutated across threads without `volatile` or local snapshot guards (`cMyRemoteDeviceCurrentlySearchingFor.getDeviceId()` in `mSearchingStoppedForOneReceiver` at `line 172` and `getNameOfSearchingDevice()` at `line 728` can NPE if another thread clears `cMyRemoteDeviceCurrentlySearchingFor` between the null check and dereference).
7. **Null-Assignment Teardown Hazards in `shutDown()` (`lines 435-445`)**:
   - `shutDown()` sets `mMyRemoteDevices = null;` and `mSearchQueue = null;`. Any in-flight BLE/ANT+ disconnect callback, broadcast receiver, or late binder query immediately throws `NullPointerException`.

#### B. `MySensor.java`, `ProxySensor.java`, `MyAccumulatorSensor.java`, and `MySensorManager.java`
1. **Unsynchronized `MySensor.mSensorListeners` (`MySensor.java:36`)**:
   - `protected LinkedList<SensorListener> mSensorListeners = new LinkedList<>()` is iterated in `newValue()` (`lines 74-76`) from 4+ sensor threads while mutated via `addSensorListener()` / `removeSensorListener()` during `ProxySensor.setSourceSensor()` or filter registration.
2. **Non-`volatile` State in `MySensor` (`lines 34-37`) and `MyAccumulatorSensor` (`lines 33-34`)**:
   - `MySensor.mValue`, `mActivated`, and `mLastUpdateTimestamp` are written by sensor threads (`newValue`, `activateSensor`, `deactivateSensor`) and read by `TrackerService` (`getValue()`) and UI threads without memory barriers.
   - `MyAccumulatorSensor.mInitialValue` and `mLastAbsoluteValue` are mutated in `setInitialValue()`, `reset()`, `accumulate()`, and `increment()` across Main/UI (`resetAccumulators`), `MySensorManager`, `ClockDevice` Timer, and ANT+/BLE threads without synchronization or `volatile` visibility.
3. **Race Condition in `ProxySensor.setSourceSensor()` (`ProxySensor.java:102-115`)**:
   - Iterates `mSensorListeners` while swapping listeners from old `mSourceSensor` to new `sourceSensor`. Without `CopyOnWriteArrayList` (and `addIfAbsent`), concurrent `addSensorListener()` and `setSourceSensor()` can throw `ConcurrentModificationException` or register duplicate listeners.
4. **Lock-Held Listener Notification in `MySensorManager` (`MySensorManager.java:118-174` — Cross-Referenced from `ATT-3986`)**:
   - `registerSensor()` and `unregisterSensor()` are `synchronized` on `MySensorManager.this` and invoke `notifyNewSensor()` / `notifySensorRemoved()` -> `updateGCData()` -> `mSensorsSensor.newValue(...)` and `mAccumulatedSensorsSensor.newValue(...)` while still holding the `MySensorManager` monitor lock.
   - Per the `ATT-3988` deadlock-prevention guardrail, `MySensor.newValue()` must never be invoked while holding a coarse manager lock.
   - Additionally, `MySensorManager.mInitialValues` (`line 52`) is an unsynchronized `HashMap<SensorType, Object>` accessed by `setInitialValue()` and `getInitialValue()`.

#### C. `MyBTLEDevice.java`, `MyANTDevice.java`, `MyRemoteDevice.java`, `MyDevice.java`, and `FilterManager.java`
1. **`MyBTLEDevice.java` (`lines 61, 63`)**:
   - `mState` (`State.DISCONNECTED`, `SEARCHING_FINISHED`, `CONNECTED_WITH_SERVICE`, `READING_SENSORS`) and `mBatteryCharacteristic` are mutated by BLE GATT binder threads and main-looper `mDisconnectHandler`, and read by UI/status queries without `volatile`.
2. **`MyANTDevice.java` (`line 63`), `MyRemoteDevice.java` (`lines 42, 54`), `MyDevice.java` (`lines 53, 57`)**:
   - `MyANTDevice.mDeviceState`, `MyRemoteDevice.mSearching`, `MyRemoteDevice.mCalibrationFactor`, `MyDevice.mSensorsRegistered`, and `MyDevice.mBatteryPercentage` are accessed across ANT+/BLE threads and UI/Tracker threads without `volatile`.
3. **`FilterManager.java` (`lines 46-49`)**:
   - `mRequiredButNotAvailableFilters`, `myFilterMap`, and `mMapFilter2Sensor` are raw `HashMap`s mutated when filters are requested (`getFilteredSensor` -> `createFilter`) or when `mSensorsChangedReceiver` runs, and read concurrently on the 1 Hz `TrackerService` thread (`getAllFilteredSensorData()`).

#### D. `BANALService.java`
1. **Non-`volatile` Static References (`lines 148-150`) & Unguarded Dereferences**:
   - `private static DeviceManager cDeviceManager;`, `private static MySensorManager cSensorManager;`, `private static FilterManager cFilterManager;` are not `volatile`.
   - Static methods (`getAllFilteredSensorData()`, `getBestSensor()`, `getSensorString()`, `getAccumulatedSensorString()`, `setInitialSensorValue()`, `isSearching()`, `getIdsOfActiveDevices()`, `getSensorTypeArray()`, `getActiveSensorTypeArray()`, `getAccumulatedSensorTypeSet()`, `getRemoteDeviceData()`, `getDeviceList()`) and `BANALServiceComm` binder methods directly dereference `cDeviceManager`, `cSensorManager`, or `cFilterManager` without capturing a local reference or checking for `null` (or partial initialization). During service startup or after `onDestroy()` (`cDeviceManager = null`), concurrent calls from `TrackerService` or UI fragments can throw `NullPointerException`.

#### E. `TrackerService.java` and `LiveWorkoutSession.java`
1. **Non-`volatile` Cross-Thread Fields in `TrackerService.java`**:
   - `mSearching` (`line 174`), `mTrackerHandle` (`line 187`), `mTrackingInterrupted` (`line 251`), `mLiveSession` (`line 254`), `mBaseFileName` (`line 287`), `mTimeTotal_s` (`line 352`), `mTimeActive_s` (`line 353`), and `mDistanceTotal_m` (`line 354`) are written and read across Main thread, `mScheduler` (1 Hz), and `mDbExecutor` without `volatile`.
2. **Unsynchronized `LiveWorkoutSession.java`**:
   - `LiveWorkoutSession` holds `ArrayList`s (`sampledLatLngs`, `sampledAltitudes`, `sampledDistances`, `rawLatLngs`), a `HashMap<SensorType, LiveSensorStats>` (`sensorStats`), and `LatLng` fields (`startLatLng`, `lastLatLng`).
   - Mutated by `mScheduler` (`addSample`, `recordStreamPoint`, `addRawLatLng`, `setStartLatLng`, `setLastLatLng`), `mDbExecutor` (`rehydrateStreams`, `rehydrateSensorStats`), and Main thread (`mAltitudeCorrectionReceiver.onReceive` calling `applyAltitudeCorrection()`), while read during `finalizeLiveSession()` (`getSampledLatLngs()`, `getSampledAltitudes()`, `getSampledDistances()`, `getSensorStats()`).
   - Concurrent execution of `applyAltitudeCorrection()` (Main thread) or `finalizeLiveSession()` (`mDbExecutor`) while `sampleAndWriteToDb()` (`mScheduler`) appends to `sampledAltitudes` / `sensorStats` causes data races or `ConcurrentModificationException`.

## 3. Chesterton's Fence & Historical Archaeology
- **`ATT-3986` (`MySensorManager` & `DeviceManager` location init)**: Previously synchronized `MySensorManager`'s sensor maps (`mSensors`, `mProxySensors`, `mSensorMap`, `mAccumulatedSensors`), and explicitly noted in a comment on `ATT-3988` that `MySensorManager.registerSensor()` / `unregisterSensor()` still invoke `MySensor.newValue()` while holding the `MySensorManager` monitor lock, deferring lock-free listener dispatch to `ATT-3988`.
- **`ATT-3922` (`MyBTLEDeviceLifecycleTest`) & `ATT-3921` (`DeviceManagerTeardownTest`)**: Established lifecycle teardown behaviors for `DeviceManager.shutDown()` and `MyBTLEDevice.disconnect()` / `shutDown()`. Existing tests verify that `shutDown()` stops searches, unregisters receivers, and cleans up devices. Replacing `mMyRemoteDevices = null` and `mSearchQueue = null` with `.clear()` preserves full teardown cleanup while eliminating post-shutdown `NullPointerException`s.
- **Deadlock Prevention Guardrail**: We must **avoid coarse-grained `synchronized` blocks on `DeviceManager` or `MySensor` that stay locked while invoking `SensorListener.newValue()` or cross-component device queries**, because sensor callbacks frequently query `DeviceManager` or `MySensorManager` from background GATT/ANT+ threads while the main thread queries devices/sensors.

## 4. Scope Bounding & Proposed Remediation
1. **`DeviceManager.java`**:
   - Change `mMyRemoteDevices` to `final Map<Long, MyRemoteDevice> mMyRemoteDevices = new ConcurrentHashMap<>()`.
   - Change `mMyRemoteDeviceTries` to `final Map<MyRemoteDevice, Integer> mMyRemoteDeviceTries = new ConcurrentHashMap<>()` and replace `mMyRemoteDeviceTries = new HashMap<>()` with `mMyRemoteDeviceTries.clear()`.
   - Change `mSearchQueue` to `final Queue<MyRemoteDevice> mSearchQueue = new ConcurrentLinkedQueue<>()`.
   - Change `mNewlyFoundDevices` to `final CopyOnWriteArrayList<Long> mNewlyFoundDevices = new CopyOnWriteArrayList<>()` and return `new ArrayList<>(mNewlyFoundDevices)` from `getIdsOfNewlyFoundDevices()`.
   - Use atomic `mMyRemoteDevices.putIfAbsent(deviceID, device)` in `createNewANTDevice` and `createNewBluetoothLEDevice`.
   - Mark `cMyRemoteDeviceCurrentlySearchingFor`, `mAntAsyncSearchEngine`, and `mBTSearchForNewDevicesEngine` as `volatile` and use local variable snapshots in all readers/mutators.
   - In `shutDown()`, call `.clear()` on `mMyRemoteDevices`, `mSearchQueue`, `mMyRemoteDeviceTries`, and `mNewlyFoundDevices` instead of assigning `null`.
2. **`MySensor.java`, `ProxySensor.java`, `MyAccumulatorSensor.java`, `MySensorManager.java`**:
   - Change `MySensor.mSensorListeners` to `final CopyOnWriteArrayList<SensorListener> mSensorListeners = new CopyOnWriteArrayList<>()` (using `addIfAbsent` in `addSensorListener`).
   - Mark `MySensor.mValue`, `mActivated`, and `mLastUpdateTimestamp` as `volatile`.
   - In `MySensor.newValue(T value)`, store `mValue = value` and `mLastUpdateTimestamp = System.currentTimeMillis()`, and pass `value` directly to `sensorListener.newValue(value)` over the lock-free `CopyOnWriteArrayList` iterator.
   - Make `MyAccumulatorSensor` state (`mInitialValue`, `mLastAbsoluteValue`) `volatile` and synchronize state calculations before calling `super.newValue()` outside the lock.
   - In `MySensorManager`, make `mInitialValues` a `ConcurrentHashMap<>()`, and refactor `registerSensor()` / `unregisterSensor()` so `mSensorsSensor.newValue(...)` and `mAccumulatedSensorsSensor.newValue(...)` are invoked **outside** `synchronized (this)`.
3. **`MyBTLEDevice.java`, `MyANTDevice.java`, `MyRemoteDevice.java`, `MyDevice.java`, `FilterManager.java`**:
   - Mark `MyBTLEDevice.mState`, `MyBTLEDevice.mBatteryCharacteristic`, `MyANTDevice.mDeviceState`, `MyRemoteDevice.mSearching`, `MyRemoteDevice.mCalibrationFactor`, `MyDevice.mSensorsRegistered`, and `MyDevice.mBatteryPercentage` as `volatile`.
   - Convert `FilterManager`'s maps (`mRequiredButNotAvailableFilters`, `myFilterMap`, `mMapFilter2Sensor`) to `ConcurrentHashMap` / `CopyOnWriteArrayList`.
4. **`BANALService.java`**:
   - Mark `cDeviceManager`, `cSensorManager`, and `cFilterManager` as `volatile` and add null-safe local snapshots in all static and binder methods.
5. **`TrackerService.java` & `LiveWorkoutSession.java`**:
   - Mark shared cross-thread fields in `TrackerService` (`mSearching`, `mTrackerHandle`, `mTrackingInterrupted`, `mLiveSession`, `mBaseFileName`, `mTimeTotal_s`, `mTimeActive_s`, `mDistanceTotal_m`) as `volatile`, and capture local snapshots of `mLiveSession` and `mTrackerHandle` before use.
   - Make all mutators and accessors on `LiveWorkoutSession` `synchronized` and return defensive copies from collection getters (`getSampledLatLngs()`, `getSampledAltitudes()`, `getSampledDistances()`, `getRawLatLngs()`, `getSensorStats()`).
