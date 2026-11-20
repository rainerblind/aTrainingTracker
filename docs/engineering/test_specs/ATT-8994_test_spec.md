# Stage 2: Requirement & Test Specification - ATT-8994

**Ticket**: [ATT-8994](https://rainerblind.atlassian.net/browse/ATT-8994) | **Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-20_01_Früh` | **Branch**: `feature/ATT-8994`  
**Requirement Mapping**: `REQ-FIL-038` | **Test Spec ID**: `TST-FIL-038` | **Date**: 2026-11-20

---

## 1. Requirement Specification (`REQ-FIL-038`)

### 1.1 Attributes & System Traceability
- **System Requirement(s)**: Derived (SW-only) — Internal sensor deactivation, `ProxySensor` unbinding, and `FilterManager` disconnect teardown lifecycle fix (zero `SYS.2`/`SYS.3` impact).
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, Integration
- **Localization Parity**: `N/A - no new UI strings` (reuses existing `"--"` formatter output across all 9 locales).

### 1.2 Functional & Architectural Requirements
1. **`REQ-FIL-038.1` (`MySensor.deactivateSensor()` Lock-Free Null Notification, `MyAccumulatorSensor` Override & `ProxySensor` Unbind Safety)**:
   - `MySensor.deactivateSensor()` SHALL set `mActivated = false`, clear `mValue = null`, and iterate `mSensorListeners` (`CopyOnWriteArrayList`, lock-free) notifying every listener with `sensorListener.newValue(null)`.
   - `MyAccumulatorSensor` SHALL override `deactivateSensor()` to set `mActivated = false` **silently** without clearing `mValue`/`mInitialValue` or notifying listeners with `null` (preserving accumulated session totals for `DISTANCE_m`, `ASCENT`, `DESCENT`, `TIME_ACTIVE`, `STRIDES`, `LAP_NR`, `CALORIES` and keeping `MyAccumulatorSensorResetToTest` intact).
   - `ProxySensor` SHALL override `deactivateSensor()` to set `mActivated = false` silently without invoking listeners (preserving lock discipline and accumulator safety during `synchronized MySensorManager.shutDown()`), AND track `mAccumulatorBacked` (`isAccumulatorBacked()`), setting it `true` whenever a `MyAccumulatorSensor` is bound as its source.
   - `ProxySensor.setSourceSensor(sourceSensor)` SHALL, strictly **outside** `mSourceLock` (`REQ-SEN-021.2`), notify `listenersSnapshot` with `sensorListener.newValue(null)` when `sourceSensor == null && previousSource != null && !(previousSource instanceof MyAccumulatorSensor)`.
2. **`REQ-FIL-038.2` (Dynamic Sensor Deactivation Before Removal in HRM Devices)**:
   - `ANTHeartRateDevice` (`unregisterSensors()` & `shutDown()`), `BTLEHeartRateDevice` (`unregisterSensors()` & `shutDown()`), and `PolarOnlineStreamingBridge` (`unregisterImuCadenceSensors()` & `unregisterSensors()`) SHALL invoke `sensor.deactivateSensor()` **before** `mMySensorManager.unregisterSensor(sensor)` and `removeSensor(...)` for all dynamic sensors (`mRrIntervalSensor`, `mCadenceSensor`, `mStridesSensor`, `mVerticalOscillationSensor`, `mGroundContactTimeSensor`). Because `MyAccumulatorSensor.deactivateSensor()` is silent, `mStridesSensor` retains its accumulated stride count automatically.
3. **`REQ-FIL-038.3` (`FilterManager` Disconnect Clearing, Accumulator & Cumulative Filter Immunity, `ProxySensor` Retention & `createFilter()` Parity)**:
   - `FilterManager` SHALL track accumulator-backed filters explicitly via a thread-safe set (`mAccumulatorFilters`) populated whenever a filter binds to a `MyAccumulatorSensor` or an accumulator-backed `ProxySensor` (`ProxySensor.isAccumulatorBacked()`) — NEVER querying `MySensorManager.mAccumulatedSensorTypeSet` — so repeated `SENSORS_CHANGED` broadcasts after dynamic accumulators (e.g. `STRIDES`) unregister (`source == null`) NEVER wipe preserved session totals.
   - `FilterManager.rebindExistingFilters()` SHALL unwrap `ProxySensor` when inspecting availability (`effectiveSource = if (currentSensor is ProxySensor<*>) currentSensor.sourceSensor else currentSensor`):
     - For **device-pinned** filters (`filterData.deviceName != null`): when `currentSensor == null || !currentSensor.isActivated`, unbind the old listener (`rebindFilter(filter, boundSensor, null)`) AND clear non-accumulator live filters (`InstantaneousFilter` via `newValue(null)`; `MovingAverageFilter` — `TimedMovingAverageFilter` / `NumberedMovingAverageFilter` — via `reset()`), NEVER resetting `AverageFilter`, `MaxValueFilter`, or `mAccumulatorFilters`. On reconnect (`currentSensor != null && currentSensor.isActivated`), rebind seamlessly (`rebindFilter(filter, null, currentSensor)`).
     - For **`"Bester Sensor"`** filters (`filterData.deviceName == null`, bound to retained `ProxySensor`): ALWAYS keep the filter bound to the retained `ProxySensor` (`REQ-TRK-055.1`). If `effectiveSource == null || !effectiveSource.isActivated`, clear non-accumulator live filters (`InstantaneousFilter` via `newValue(null)`; `MovingAverageFilter` via `reset()`) while preserving `AverageFilter`, `MaxValueFilter`, and `mAccumulatorFilters`. If a fallback sensor is active (`effectiveSource != null && effectiveSource.isActivated`), leave the live value untouched.
   - `FilterManager.createFilter(filterData)` SHALL mirror this exact logic in BOTH its existing-filter branch (`L118-129`) and its new-filter branch (`L132-138`): creating a device-pinned filter while the static sensor exists with `!mySensor.isActivated` defers cleanly via `mRequiredButNotAvailableFilters` so `activateSensor()` + `SENSORS_CHANGED` on connect binds and delivers live values.
4. **`REQ-FIL-038.4` (Sparkline Flat-Line Termination & Invariant Preservation)**:
   - Once disconnected device-pinned or last-sensor `"Bester Sensor"` live filters emit `filteredValue == null` (`"--"`), `BANALServiceRepository.updateSparklineBuffers()` (`L937-948`) SHALL stop appending samples to `LiveSparklineBuffer` (zero flat-line growth across repeated 1-Hz ticks) with `0` edits to `BANALServiceRepository.kt`, `SensorFieldView.kt`, `DeviceManager.java`, `TrackerService.java`, and `DevicesDatabaseManager.java`.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
- **Original Requirement ID & Target**: `REQ-TRK-055` (`MySensorManager.java`, `ProxySensor.java`, `FilterManager.kt`), `REQ-FIL-011` & `REQ-FIL-013` (`MyFilter.kt`, `InstantaneousFilter.kt`, `MovingAverageFilter.kt`, `AverageFilter.kt`, `MaxValueFilter.kt`), `REQ-SEN-021` (`ProxySensor.java`, `MySensorManager.java`), `REQ-TRK-049.4` (`FilterManager.resetAllFilters`), `REQ-HRV-050.3` (`PerDeviceHrvController.kt`).
- **Historical Origin & Commit Trace**: `ATT-6257` / `REQ-TRK-055`, `ATT-7530` (`c54768d2`), `ATT-4096` / `REQ-SEN-021`, `ATT-3222` / `REQ-FIL-013`, `ATT-7905` (`c8476b91`).
- **Root Reason for Existing Formulation**: `MySensor.deactivateSensor()` pre-dated device-pinned live tiles and `LiveSparklineBuffer`; `FilterManager.rebindExistingFilters()` only handled `oldSensor -> newSensor` migration on reconnect, omitting `active -> disconnected` teardown.
- **Preservation of Core Invariants**: Preserves `REQ-SEN-021` lock-free listener callbacks, `REQ-TRK-055.1` persistent `ProxySensor` retention and seamless `"Bester Sensor"` fallback, `REQ-FIL-011` null-safety (`FilterNullSafetyTest`), `REQ-FIL-013` cumulative `AverageFilter`/`MaxValueFilter` & `MyAccumulatorSensor` session preservation, `REQ-TRK-049.4` zero-baseline reset, `ExponentialSmoothingFilter` signature parity, and `0` edits to `DeviceManager.java`, `TrackerService.java`, `DevicesDatabaseManager.java`, `BANALServiceRepository.kt`, and `SensorFieldView.kt`.

---

## 3. Acceptance Criteria (Given-When-Then)
- *Given* a live non-accumulator sensor (`HR`, `RR_INTERVAL`) bound to `InstantaneousFilter` / `MovingAverageFilter` and a `MyAccumulatorSensor` (`DISTANCE_m`, `STRIDES`) bound to a filter, *When* `deactivateSensor()` runs, *Then* the non-accumulator sensor clears `mValue = null` and pushes `null` to listeners lock-free, while the `MyAccumulatorSensor` sets `mActivated = false` silently without clearing or notifying `null`.
- *Given* `ANTHeartRateDevice`, `BTLEHeartRateDevice`, or `PolarOnlineStreamingBridge` has active dynamic sensors (`mRrIntervalSensor`, IMU cadence/dynamics, `mStridesSensor`), *When* `unregisterSensors()` or `shutDown()` executes, *Then* `deactivateSensor()` runs before `unregisterSensor()`/`removeSensor()`, immediately clearing non-accumulator listeners to `null` (`"--"`) while preserving `mStridesSensor` totals.
- *Given* device-pinned `InstantaneousFilter`, `TimedMovingAverageFilter`, `NumberedMovingAverageFilter`, `AverageFilter`, `MaxValueFilter`, and accumulator filters (`STRIDES`), *When* the pinned device disconnects (`resolveSensor == null` or `!isActivated`) and repeated `SENSORS_CHANGED` broadcasts fire, *Then* live `InstantaneousFilter` & `MovingAverageFilter` reset to `null` (`"--"`), `AverageFilter`/`MaxValueFilter`/`STRIDES` retain their session totals, and upon reconnect the filter rebinds and resumes live updates (including filters initially created while `!isActivated`).
- *Given* a `"Bester Sensor"` (`deviceName == null`) `InstantaneousFilter` or `MovingAverageFilter` bound to a retained `ProxySensor`, *When* one of two HRMs disconnects, *Then* the filter remains bound to `ProxySensor` and seamlessly reads the fallback HRM; *When* the last HRM disconnects, *Then* the filter clears to `null` (`"--"`) while staying bound to `ProxySensor` and resumes automatically upon reconnect.
- *Given* a disconnected device-pinned tile registered in `BANALServiceRepository.sparklineBuffers`, *When* repeated 1-Hz `updateSparklineBuffers(filterManager.getAllFilteredSensorData())` calls execute after disconnect, *Then* `LiveSparklineBuffer` receives `0` new samples (no horizontal flat-line).

---

## 4. Test Specification (`TST-FIL-038`)
- **`TST-FIL-038.1` (`MySensorDeactivationAndProxyTest.kt`, `MySensorManagerProxyTest.kt`, `MyAccumulatorSensorResetToTest.kt`, `SensorAndDeviceConcurrencyStressTest.kt`)**: Verify `MySensor.deactivateSensor()` clears `mValue` and notifies listeners `null` lock-free; verify `MyAccumulatorSensor.deactivateSensor()` and `ProxySensor.deactivateSensor()` are silent; verify `ProxySensor.setSourceSensor(null)` notifies `null` outside `mSourceLock` strictly for non-accumulator previous sources and sets `isAccumulatorBacked()` for accumulators (`STRIDES`).
- **`TST-FIL-038.2` (`ANTHeartRateDeviceTest.kt`, `BTLEHeartRateDeviceTest.kt`, `PolarOnlineStreamingBridgeTest.kt`)**: Verify dynamic `mRrIntervalSensor` (and Polar IMU cadence/VO/GCT sensors) invoke `deactivateSensor()` and push `null` to bound listeners prior to `unregisterSensor()`/`removeSensor()` on `unregisterSensors()` & `shutDown()`, while `mStridesSensor` retains its accumulated count.
- **`TST-FIL-038.3` (`FilterManagerRebindTest.kt`, `FilterNullSafetyTest.kt`, `FilterResetAndPreStartTest.kt`, `FilterManagerAccumulatorResetTest.kt`)**: Verify device-pinned disconnect (`null` & `!isActivated`) unbinds and clears `InstantaneousFilter`/`TimedMovingAverageFilter`/`NumberedMovingAverageFilter` to `null` (`"--"`) while preserving `AverageFilter`, `MaxValueFilter`, and dynamic `STRIDES` accumulator across repeated `SENSORS_CHANGED` broadcasts; verify `"Bester Sensor"` `ProxySensor` retains binding across fallback vs last-sensor disconnect & reconnect (for both `InstantaneousFilter` and `MovingAverageFilter`); verify `createFilter()` mirrors inactive-sensor handling and rebinds on connect.
- **`TST-FIL-038.4` (`FilterManagerRebindTest.kt` Sparkline Integration + Contract Suites)**: Verify `BANALServiceRepository.updateSparklineBuffers(filterManager.getAllFilteredSensorData())` appends `0` samples after disconnect across repeated 1-Hz ticks (zero flat-line); verify `ArchitectureConformanceContractTest` (`0` edits to `DeviceManager.java`, `TrackerService.java`, `DevicesDatabaseManager.java`; `PolarOnlineStreamingBridge.kt <= 1,500`), `InterfaceSignatureDriftContractTest`, `DeadCodeEliminationContractTest`, and `SourceTextReadingAuditContractTest`.

---

## 5. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-FIL-038.1` | Unit / Concurrency | `MySensor`, `MyAccumulatorSensor`, `ProxySensor` | `REQ-FIL-038.1` | Specified |
| `TST-FIL-038.2` | Unit / Device | `ANTHeartRateDevice`, `BTLEHeartRateDevice`, `PolarOnlineStreamingBridge` | `REQ-FIL-038.2` | Specified |
| `TST-FIL-038.3` | Unit / Filter | `FilterManager`, `MyFilter` hierarchy | `REQ-FIL-038.3` | Specified |
| `TST-FIL-038.4` | Integration / Contracts | `FilterManager` + `BANALServiceRepository` sparkline, Contracts | `REQ-FIL-038.4` | Specified |
