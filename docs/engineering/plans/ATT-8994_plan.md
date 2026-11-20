# Stage 3 Implementation Plan: ATT-8994 - Sensor Disconnect & FilterManager Stale-Value / Flat-Line Sparkline Fix

**Ticket**: [ATT-8994](https://rainerblind.atlassian.net/browse/ATT-8994) | **Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-20_01_Früh` | **Branch**: `feature/ATT-8994`  
**Requirement**: `REQ-FIL-038` (`REQ-FIL-038.1`..`.4`) | **Test Spec**: `TST-FIL-038` (`TST-FIL-038.1`..`.4`)  
**Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-20

---

## 1. Architecture & Target LoC Budget (SWE.2)

| File | Current LoC | Budget / Cap | Action | Requirement |
| :--- | :---: | :---: | :--- | :--- |
| `MySensor.java` | `138` | `<= 155` | Clear `mValue = null` & lock-free `newValue(null)` dispatch in `deactivateSensor()` | `REQ-FIL-038.1` |
| `MyAccumulatorSensor.java` | `200` | `<= 215` | Silent `deactivateSensor()` override (`mActivated = false` only) | `REQ-FIL-038.1` |
| `ProxySensor.java` | `146` | `<= 175` | `mAccumulatorBacked` tracking, silent `deactivateSensor()`, outside-lock `null` notify on `setSourceSensor(null)` | `REQ-FIL-038.1` |
| `ANTHeartRateDevice.java` | `407` | `<= 415` | `mRrIntervalSensor.deactivateSensor()` before `unregisterSensor()`/`removeSensor()` in `unregisterSensors()` & `shutDown()` | `REQ-FIL-038.2` |
| `BTLEHeartRateDevice.java` | `359` | `<= 367` | `mRrIntervalSensor.deactivateSensor()` before `unregisterSensor()`/`removeSensor()` in `unregisterSensors()` & `shutDown()` | `REQ-FIL-038.2` |
| `PolarOnlineStreamingBridge.kt` | `1,301` | `<= 1,315 / 1,500` | `sensor.deactivateSensor()` before `unregisterSensor()`/`removeSensor()` in `unregisterImuCadenceSensors()` & `unregisterSensors()` | `REQ-FIL-038.2` |
| `FilterManager.kt` | `315` | `<= 365` | `mAccumulatorFilters`, shared `syncFilterBinding()` for `rebindExistingFilters()` & `createFilter()`, non-`ProxySensor` inactive deferral, `shutDown()` cleanup | `REQ-FIL-038.3`, `.4` |
| `DeviceManager.java` | `1,707` | `1,707 / 1,708` (**`0` edits**) | Untouched | Invariant |
| `TrackerService.java` | `3,538` | `3,538 / 3,559` (**`0` edits**) | Untouched | Invariant |
| `DevicesDatabaseManager.java` | `1,669` | `1,669 / 1,671` (**`0` edits**) | Untouched | Invariant |
| `BANALServiceRepository.kt` / `SensorFieldView.kt` | `1,186` / `913` | **0 edits** | Untouched (`L940` already skips `value == null`) | `REQ-FIL-038.4` |

---

## 2. Atomic Implementation Steps

### Step 0: Lock-Free `MySensor`, Silent `MyAccumulatorSensor` & `ProxySensor` Unbind Safety (`REQ-FIL-038.1` / `TST-FIL-038.1`)
- **`MySensor.java` (`L49-51`)**:
  - Update `deactivateSensor()`:
    ```java
    public void deactivateSensor() {
        mActivated = false;
        mValue = null;
        for (SensorListener sensorListener : mSensorListeners) {
            sensorListener.newValue(null);
        }
    }
    ```
  - Assigns `mActivated = false; mValue = null;` **before** iterating `CopyOnWriteArrayList<SensorListener> mSensorListeners` with **zero locks held** (`REQ-SEN-021`).
- **`MyAccumulatorSensor.java`**:
  - Override `deactivateSensor()` to set `mActivated = false;` **silently** without clearing `mValue`/`mInitialValue` or notifying `mSensorListeners`, preserving session totals (`DISTANCE_m`, `ASCENT`, `DESCENT`, `TIME_ACTIVE`, `STRIDES`, `LAP_NR`, `CALORIES`) and `MyAccumulatorSensorResetToTest`.
- **`ProxySensor.java`**:
  - Add `private volatile boolean mAccumulatorBacked;` initialized in constructor: `mAccumulatorBacked = sourceSensor instanceof MyAccumulatorSensor;`.
  - Expose `public boolean isAccumulatorBacked() { return mAccumulatorBacked; }`.
  - Override `deactivateSensor()` to set `mActivated = false;` **silently** (preserving lock discipline and accumulator safety during `synchronized MySensorManager.shutDown()`).
  - In `setSourceSensor(MySensor<T> sourceSensor)`: inside `synchronized (mSourceLock)` set `if (sourceSensor instanceof MyAccumulatorSensor) { mAccumulatorBacked = true; }`; strictly **outside** `mSourceLock` (`REQ-SEN-021.2`), when `sourceSensor == null && previousSource != null && !(previousSource instanceof MyAccumulatorSensor)`, invoke `sensorListener.newValue(null)` for each listener in `listenersSnapshot`.
- **Unit Tests**: Create `app/src/test/java/com/atrainingtracker/banalservice/sensor/MySensorDeactivationAndProxyTest.kt` (`// REQ-FIL-038.1 / TST-FIL-038.1`) and extend `MySensorManagerProxyTest.kt`, verifying lock-free deactivation null-push, silent `MyAccumulatorSensor`/`ProxySensor.deactivateSensor()`, `ProxySensor.setSourceSensor(null)` outside-lock null-push for non-accumulators vs silence for accumulators (`STRIDES`), and `isAccumulatorBacked()` across `source -> null` transitions. Verify `MyAccumulatorSensorResetToTest` & `SensorAndDeviceConcurrencyStressTest`.

### Step 1: Dynamic Sensor Deactivation in HRM Devices (`REQ-FIL-038.2` / `TST-FIL-038.2`)
- **`ANTHeartRateDevice.java` (`L374-380`, `L393-399`)**: Call `mRrIntervalSensor.deactivateSensor();` before `mMySensorManager.unregisterSensor(mRrIntervalSensor)` and `removeSensor(SensorType.RR_INTERVAL)` in both `unregisterSensors()` and `shutDown()`.
- **`BTLEHeartRateDevice.java` (`L328-334`, `L346-352`)**: Call `mRrIntervalSensor.deactivateSensor();` before `mMySensorManager.unregisterSensor(mRrIntervalSensor)` and `removeSensor(SensorType.RR_INTERVAL)` in both `unregisterSensors()` and `shutDown()`.
- **`PolarOnlineStreamingBridge.kt` (`unregisterImuCadenceSensors()` & `unregisterSensors()`)**: Call `sensor.deactivateSensor()` before `mMySensorManager?.unregisterSensor(sensor)` and `removeSensor(...)` for `mCadenceSensor`, `mStridesSensor`, `mVerticalOscillationSensor`, `mGroundContactTimeSensor`, and `mRrIntervalSensor`. (`mStridesSensor` inherits `MyAccumulatorSensor.deactivateSensor()` -> silent!)
- **Unit Tests**: Extend `ANTHeartRateDeviceTest.kt`, `BTLEHeartRateDeviceTest.kt`, and `PolarOnlineStreamingBridgeTest.kt` (`// REQ-FIL-038.2 / TST-FIL-038.2`) asserting that dynamic `RR_INTERVAL` (and Polar IMU cadence/VO/GCT) listeners receive `null` immediately on `unregisterSensors()`/`shutDown()`, while `STRIDES` listeners do NOT receive `null`.

### Step 2: `FilterManager.kt` Disconnect Clearing, Accumulator/Cumulative Immunity & `createFilter()` Parity (`REQ-FIL-038.3`, `REQ-FIL-038.4` / `TST-FIL-038.3`, `TST-FIL-038.4`)
- Add thread-safe `protected val mAccumulatorFilters: MutableSet<MyFilter<*>> = ConcurrentHashMap.newKeySet()` (cleared in `shutDown()`).
- Add helper `isAccumulatorSensor(sensor: MySensor<*>?): Boolean` unwrapping `ProxySensor` (returning `true` if `current.isAccumulatorBacked` or unwrapped `current is MyAccumulatorSensor<*>`).
- Add helper `clearLiveFilterOnDisconnect(filter: MyFilter<*>, boundSensor: MySensor<*>?)`:
  - If `mAccumulatorFilters.contains(filter) || isAccumulatorSensor(boundSensor)`: add to `mAccumulatorFilters` and return (never clear accumulators).
  - If `filter is InstantaneousFilter<*>`: call `(filter as InstantaneousFilter<Any?>).newValue(null)`.
  - If `filter is MovingAverageFilter`: call `filter.reset()` (clears `TimedMovingAverageFilter` and `NumberedMovingAverageFilter` to `null` / `"--"`).
  - Never reset `AverageFilter`, `MaxValueFilter`, or `ExponentialSmoothingFilter` (`REQ-FIL-011` / `REQ-FIL-013`).
- In `rebindFilter(myFilter, oldSensor, newSensor)` and `createFilter(filterData)` initial bind: whenever `isAccumulatorSensor(sensor)` is true, record `mAccumulatorFilters.add(myFilter)`.
- Extract shared `syncFilterBinding(filterData: FilterData, filter: MyFilter<*>)` invoked by **both** `rebindExistingFilters()` and `createFilter()`'s existing-filter branch (`L118-129`):
  - If `currentSensor is ProxySensor<*>`: keep bound to retained `ProxySensor` (`REQ-TRK-055.1`), mark `mAccumulatorFilters` if `isAccumulatorSensor(currentSensor)`, and if `currentSensor.sourceSensor == null || !currentSensor.sourceSensor.isActivated` invoke `clearLiveFilterOnDisconnect(filter, currentSensor)`.
  - Else if `currentSensor == null || !currentSensor.isActivated`: mark `mAccumulatorFilters` if `isAccumulatorSensor(boundSensor)`, unbind via `rebindFilter(filter, boundSensor, null)` if `boundSensor != null`, and invoke `clearLiveFilterOnDisconnect(filter, boundSensor)`.
  - Else if `boundSensor != currentSensor`: rebind via `rebindFilter(filter, boundSensor, currentSensor)`.
- In `createFilter(filterData)` new-filter branch (`L132-138`): defer to `mRequiredButNotAvailableFilters` ONLY when `mySensor == null || (mySensor !is ProxySensor<*> && !mySensor.isActivated)` (NEVER defer a retained `ProxySensor` even when `sourceSensor == null`!).
- **Unit & Integration Tests**: Extend `FilterManagerRebindTest.kt` (`// REQ-FIL-038.3 / TST-FIL-038.3` & `// REQ-FIL-038.4 / TST-FIL-038.4`) covering:
  1. Device-pinned `InstantaneousFilter`, `TimedMovingAverageFilter`, `NumberedMovingAverageFilter` disconnect (`null` and `!isActivated`) -> `null` (`"--"`) & clean reconnect;
  2. Device-pinned `AverageFilter`, `MaxValueFilter`, and dynamic `STRIDES` accumulator immunity across repeated `SENSORS_CHANGED` broadcasts;
  3. `"Bester Sensor"` `ProxySensor` fallback preservation (2 HRMs -> 1 disconnects -> fallback active) AND last-sensor disconnect clearing BOTH `InstantaneousFilter` and `MovingAverageFilter` while retaining `ProxySensor` binding & seamless reconnect;
  4. `createFilter()` on inactive static sensor defers and binds cleanly upon `activateSensor()` + `SENSORS_CHANGED`, while retained `ProxySensor` with `sourceSensor == null` binds immediately without deferral;
  5. `BANALServiceRepository.updateSparklineBuffers(filterManager.getAllFilteredSensorData())` appends `0` samples after disconnect across repeated 1-Hz ticks (zero flat-line).

### Step 3: Living Docs Sync & Locked Incremental Verification (`Rule 7.5` / `Rule 8.6`)
- Promote `REQ-FIL-038` & `TST-FIL-038` from `[Planned]` to `[Implemented]` with bumped versions + Change History rows (`req_tracking_cockpit_part2.md`, `tst_tracking_cockpit.md`, `docs/requirements.md`, `docs/tests.md`) + `--sync-indexes`.
- Run locked incremental Gradle verification (`0` new Kotlin compiler warnings across `:app:compileDebugKotlin` & `:app:compileDebugUnitTestKotlin`, `:app:assembleDebug` GREEN, NO `--rerun-tasks`, NO `-PstampGitHash=true`):
  ```bash
  python3 tools/parallel_sprint_lock.py --timeout 1800 -- \
    ./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:assembleDebug :app:testDebugUnitTest --rerun \
      --tests "com.atrainingtracker.banalservice.sensor.MySensorDeactivationAndProxyTest" \
      --tests "com.atrainingtracker.banalservice.sensor.MySensorManagerProxyTest" \
      --tests "com.atrainingtracker.banalservice.sensor.MyAccumulatorSensorResetToTest" \
      --tests "com.atrainingtracker.banalservice.sensor.SensorAndDeviceConcurrencyStressTest" \
      --tests "com.atrainingtracker.banalservice.filters.FilterManagerRebindTest" \
      --tests "com.atrainingtracker.banalservice.filters.FilterNullSafetyTest" \
      --tests "com.atrainingtracker.banalservice.filters.FilterResetAndPreStartTest" \
      --tests "com.atrainingtracker.banalservice.filters.FilterManagerAccumulatorResetTest" \
      --tests "com.atrainingtracker.banalservice.devices.ant_plus.ANTHeartRateDeviceTest" \
      --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.BTLEHeartRateDeviceTest" \
      --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOnlineStreamingBridgeTest" \
      --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" \
      --tests "com.atrainingtracker.trainingtracker.build.InterfaceSignatureDriftContractTest" \
      --tests "com.atrainingtracker.trainingtracker.build.DeadCodeEliminationContractTest" \
      --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest"
  ```
- Run 4 Python safety linters + both `verify_requirement_governance.py` checks (`exit 0`).

---

## 3. Invariants & Hygiene Checklist
- **Protected Monoliths & Unchanged Files**: `0` edits to `DeviceManager.java` (`1,707/1,708`), `TrackerService.java` (`3,538/3,559`), `DevicesDatabaseManager.java` (`1,669/1,671`), `BANALServiceRepository.kt` (`1,186`), `SensorFieldView.kt` (`913`); `PolarOnlineStreamingBridge.kt <= 1,315/1,500`.
- **Concurrency & Governance**: Lock-free `MySensor.deactivateSensor()`, outside-`mSourceLock` `ProxySensor` null dispatch (`REQ-SEN-021.2`), hyphenated `// REQ-FIL-038.x / TST-FIL-038.x` tags (`REQ-PRO-099.3`), `0` `src/main` reads (`REQ-PRO-049.1`), `0` new Kotlin compiler warnings, `Localization Parity: N/A - no new UI strings`, zero touches to the 5 user-owned dirty/untracked paths.
