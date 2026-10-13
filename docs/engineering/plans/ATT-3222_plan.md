# Stage 3: Implementation Plan - ATT-3222: Enable filtered and smoothed sensor values in cockpit prior to tracking start

**Ticket**: [ATT-3222](https://rainerblind.atlassian.net/browse/ATT-3222)  
**Sub-task**: [ATT-3292](https://rainerblind.atlassian.net/browse/ATT-3292) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.10`  
**Requirement Mapping**: `REQ-FIL-013`  
**Test Mapping**: `TST-FIL-005`  
**Branch**: `feature/ATT-3222`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Problem Description & Background

Athletes warming up, calibrating equipment (e.g. power meter zero-offset), or checking telemetry tiles before starting workout recording currently see dashes (`--`) in any field backed by a rolling smoothing filter (such as 3s / 10s moving average power, exponentially smoothed heart rate, speed, or derived metrics like VAM / vertical speed and slope %).

This defect was introduced in commit `d937073c`, which applied `if (!TrainingApplication.isPaused())` indiscriminately across all filter classes in `com.atrainingtracker.banalservice.filters`. Because `TrainingApplication.isPaused()` evaluates to `true` whenever `cTrackingMode != TrackingMode.TRACKING` (including `TrackingMode.READY`), all incoming sensor samples are dropped prior to the user pressing "Start Tracking".

The solution decouples rolling smoothing filters (`ExponentialSmoothingFilter`, `TimedMovingAverageFilter`, `NumberedMovingAverageFilter`) from the pause state, allowing them to compute and display real-time physical metrics continuously. To prevent pre-recording warmup metrics from polluting official workout statistics, cumulative aggregators (`AverageFilter`, `MaxValueFilter`) retain their pause gates, and a formal `reset()` lifecycle hook is added to `MyFilter` and registered in `FilterManager` to flush all filter state upon receiving `BANALService.RESET_ACCUMULATORS_INTENT`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-FIL-013` (*Continuous Pre-Start Rolling Telemetry Filtering, Session Reset Isolation & Live Cockpit Metric Readiness*)
* **Test Mapping**: `TST-FIL-005` (*Continuous Rolling Smoothing Telemetry, Session Reset Isolation & Live Pre-Start Metric Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Defensive Null-Safety Invariant (`REQ-FIL-011`)**: Passing `null` to `newValue()` in any filter class must not throw `NullPointerException` and must not mutate filter state or result.
2. **Cumulative Session Isolation**: Workout session summaries (`AverageFilter`, `MaxValueFilter`) must not accumulate readings when paused or in pre-start ready mode (`TrackingMode.READY`).
3. **VAM Stability & Gating Preservation (`REQ-FIL-012`, `TST-FIL-004`)**: All location accuracy gating, low-speed wander suppression, and stationary deadband logic in `VerticalSpeedAndSlopeDevice.calculateVam()` remain completely unchanged.
4. **Thread Safety**: All state-mutating filter operations (`newValue`, `reset`, `getFilteredValue`) remain strictly `synchronized` on the filter instance to guarantee safe concurrent access between sensor ingest threads and UI render threads.
5. **Human Gate Governance (Rule 2)**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`. Parent ticket `ATT-3222` transitions to `Final Review (Human)` only at the conclusion of Stage 5.

---

## 4. Proposed Architectural Changes

### Component 1: Rolling Smoothing Filter Decoupling
* **Files**:
  - `app/src/main/java/com/atrainingtracker/banalservice/filters/ExponentialSmoothingFilter.java`
  - `app/src/main/java/com/atrainingtracker/banalservice/filters/TimedMovingAverageFilter.java`
  - `app/src/main/java/com/atrainingtracker/banalservice/filters/NumberedMovingAverageFilter.java`
* **Changes**:
  - In `newValue(Number value)`, remove the `!TrainingApplication.isPaused()` check while preserving `value != null`.
  - In `NumberedMovingAverageFilter`, maintain bounded ring-buffer storage: when `mValues.size() < mSize`, append element; once full, replace oldest slot using `mValues.set(mIndex, value)`.
  - Ensure instantaneous continuous recalculation of rolling window metrics regardless of `cTrackingMode`.

### Component 2: Filter Lifecycle Reset Contract
* **Files**:
  - `app/src/main/java/com/atrainingtracker/banalservice/filters/MyFilter.java`
  - `app/src/main/java/com/atrainingtracker/banalservice/filters/ExponentialSmoothingFilter.java`
  - `app/src/main/java/com/atrainingtracker/banalservice/filters/TimedMovingAverageFilter.java`
  - `app/src/main/java/com/atrainingtracker/banalservice/filters/NumberedMovingAverageFilter.java`
  - `app/src/main/java/com/atrainingtracker/banalservice/filters/AverageFilter.java`
  - `app/src/main/java/com/atrainingtracker/banalservice/filters/MaxValueFilter.java`
* **Changes**:
  - In `MyFilter.java`, add `public void reset() {}` default implementation.
  - In `ExponentialSmoothingFilter.java`, implement `reset()`: `synchronized (this) { mFilteredValue = 0.0; }`.
  - In `TimedMovingAverageFilter.java`, implement `reset()`: `synchronized (this) { mTimestampedValues.clear(); }`.
  - In `NumberedMovingAverageFilter.java`, implement `reset()`: `synchronized (this) { mValues.clear(); mIndex = 0; }`.
  - In `AverageFilter.java`, implement `reset()`: `synchronized (this) { mAccumulatedValue = 0.0; mNumberOfSamples = 0; }`.
  - In `MaxValueFilter.java`, implement `reset()`: `synchronized (this) { mMaxValue = 0; }`.

### Component 3: FilterManager Accumulator Reset Broadcast Receiver
* **Files**:
  - `app/src/main/java/com/atrainingtracker/banalservice/filters/FilterManager.java`
* **Changes**:
  - Declare `IntentFilter mResetAccumulatorsFilter = new IntentFilter(BANALService.RESET_ACCUMULATORS_INTENT);`.
  - Implement `BroadcastReceiver mResetAccumulatorsReceiver` that invokes `resetAllFilters()`.
  - Register receiver in constructor via `ContextCompat.registerReceiver(mContext, mResetAccumulatorsReceiver, mResetAccumulatorsFilter, ContextCompat.RECEIVER_NOT_EXPORTED)`.
  - Implement `public synchronized void resetAllFilters()` iterating over `myFilterMap.values()` and calling `filter.reset()`.
  - In `shutDown()`, safely unregister `mResetAccumulatorsReceiver`.

### Component 4: Pre-Start Telemetry in VerticalSpeedAndSlopeDevice
* **Files**:
  - `app/src/main/java/com/atrainingtracker/banalservice/devices/VerticalSpeedAndSlopeDevice.java`
* **Changes**:
  - `calculateMetrics()` already queries `cAltitudeFilter` and `cSpeedFilter` at 1Hz. Because these filters will now return non-null smoothed values in `TrackingMode.READY`, `calculateMetrics()` will automatically compute and broadcast live VAM and slope percentage prior to workout start without code modifications in `calculateMetrics()`.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
No UI changes. All changes operate at the telemetry filtering and data acquisition layer (`com.atrainingtracker.banalservice.filters`). Existing cockpit tiles in `SensorGridScreen` automatically render the live values emitted by the underlying sensors.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Base Filter Lifecycle Contract (`MyFilter.java`)
* **Files**: `app/src/main/java/com/atrainingtracker/banalservice/filters/MyFilter.java`
* **Changes**: Add `public void reset() { /* default no-op for stateless filters */ }`.

### Step 2: Unblock Rolling Smoothing Filters & Add `reset()`
* **Files**:
  - `app/src/main/java/com/atrainingtracker/banalservice/filters/ExponentialSmoothingFilter.java`
  - `app/src/main/java/com/atrainingtracker/banalservice/filters/TimedMovingAverageFilter.java`
  - `app/src/main/java/com/atrainingtracker/banalservice/filters/NumberedMovingAverageFilter.java`
* **Changes**:
  - Remove `!TrainingApplication.isPaused()` guard from `newValue(Number value)`.
  - Implement `reset()` across all three rolling filter classes.
  - In `NumberedMovingAverageFilter`, enforce bounded ring buffer replacement via `mValues.set(mIndex, value)`.

### Step 3: Implement `reset()` in Cumulative Filters
* **Files**:
  - `app/src/main/java/com/atrainingtracker/banalservice/filters/AverageFilter.java`
  - `app/src/main/java/com/atrainingtracker/banalservice/filters/MaxValueFilter.java`
* **Changes**:
  - Implement `reset()` in `AverageFilter` (`mAccumulatedValue = 0.0`, `mNumberOfSamples = 0`).
  - Implement `reset()` in `MaxValueFilter` (`mMaxValue = 0`).
  - Preserve `!TrainingApplication.isPaused()` in `newValue()`.

### Step 4: Accumulator Reset Intent Handling in `FilterManager`
* **Files**:
  - `app/src/main/java/com/atrainingtracker/banalservice/filters/FilterManager.java`
* **Changes**:
  - Register `mResetAccumulatorsReceiver` for `BANALService.RESET_ACCUMULATORS_INTENT`.
  - Implement `resetAllFilters()` calling `filter.reset()` on all registered `MyFilter` instances.
  - Safely unregister `mResetAccumulatorsReceiver` in `shutDown()`.

### Step 5: Unit Tests for Continuous Telemetry, Session Isolation, and Reset Contract
* **Files**:
  - `app/src/test/java/com/atrainingtracker/banalservice/filters/FilterResetAndPreStartTest.kt`
* **Tests**:
  - Verify rolling filters calculate values when `TrainingApplication.isPaused() == true`.
  - Verify cumulative filters do not accumulate when `TrainingApplication.isPaused() == true`.
  - Verify `reset()` flushes filter internal state and output values.
  - Verify `FilterManager` invokes `reset()` upon receiving `RESET_ACCUMULATORS_INTENT`.
  - Verify existing null-safety tests in `FilterNullSafetyTest` and `SensorFilterDefaultsTest` continue to pass 100%.
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.filters.*"`

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Execute targeted unit tests: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.filters.*"`.
  - Execute translation parity tests: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`.
  - In Stage 5, execute full clean-room unit test suite: `./gradlew testDebugUnitTest`.
* **Rollback Plan**:
  - The changes are strictly isolated to `feature/ATT-3222`. Reverting the feature branch or running `git revert` restores previous pause-gating behavior with zero residual impact.
