# Stage 1 Analysis: ATT-3222 - Enable Filtered and Smoothed Sensor Values in Cockpit Prior to Tracking Start

**Ticket**: [ATT-3222](https://atrainingtracker.atlassian.net/browse/ATT-3222)  
**Sub-task**: [ATT-3290](https://atrainingtracker.atlassian.net/browse/ATT-3290) (`[Analysis]`)  
**Parent Epic**: [ATT-166](https://atrainingtracker.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.10`  
**Branch**: `feature/ATT-3222`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Problem Statement & Motivation

Currently in `aTrainingTracker`, sensor tiles configured with non-instantaneous smoothing filters (such as exponentially smoothed speed or power, 3s/5s/10s/30s moving averages, and derived metrics like VAM and slope %) only display values after the athlete taps "Start Tracking". Prior to starting (while in `TrackingMode.READY`), these fields render dashes (`--`), even when the respective sensors (Heart Rate straps, Power Meters, Cadence sensors, GPS) are actively connected and streaming valid data.

### Current Behavior vs Expected Behavior
- **Current Behavior**:
  - An athlete turns on their bike computer / phone, connects their power meter and heart rate monitor, and waits at the starting line or warms up on a trainer.
  - Cockpit tiles with raw / instantaneous metrics show numbers (e.g. raw 1-second power, heart rate).
  - Cockpit tiles configured with standard athletic smoothing (e.g. 3-second power, 5-second pace, smoothed HR, slope %, vertical speed) display `--`.
  - The athlete cannot verify smoothed sensor stability, monitor warm-up power pacing, or view slope/VAM without starting an unwanted workout recording session.
- **Expected Behavior**:
  - Rolling smoothing filters (`ExponentialSmoothingFilter`, `TimedMovingAverageFilter`, `NumberedMovingAverageFilter`) continuously process incoming sensor data regardless of recording status, rendering live smoothed numbers in the cockpit prior to workout start.
  - Derived virtual sensors (`VerticalSpeedAndSlopeDevice`) receive live smoothed speed and altitude samples in `TrackingMode.READY` and compute live vertical speed and slope.
  - When the athlete taps "Start Tracking", official recorded workout session statistics (such as `AverageFilter` and `MaxValueFilter`) start fresh without pre-start stationary pollution via an accumulator reset event.
  - During workout pause (`TrackingMode.PAUSED`), rolling smoothing filters continue showing current physical sensor values, while cumulative session aggregators remain paused.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 The Pause Gate in Rolling Filters
In commit `d937073c` (Jan 7, 2026), the condition `!TrainingApplication.isPaused()` was added to `newValue(Number value)` across all filter implementations:
- `ExponentialSmoothingFilter.java` (line 59):
  ```java
  if (value != null && !TrainingApplication.isPaused()) {
      mFilteredValue = mAlpha * value.doubleValue() + (1 - mAlpha) * mFilteredValue;
  }
  ```
- `TimedMovingAverageFilter.java` (line 54):
  ```java
  if (value != null && !TrainingApplication.isPaused()) {
      long currentTimeMillis = System.currentTimeMillis();
      mTimestampedValues.add(new TimestampedValue(currentTimeMillis, value));
      trimValues();
  }
  ```
- `NumberedMovingAverageFilter.java` (line 54):
  ```java
  if (value != null && !TrainingApplication.isPaused()) {
      mValues.add(mIndex, value);
      mIndex = (mIndex + 1) % mSize;
  }
  ```

### 2.2 Semantics of `TrainingApplication.isPaused()`
In `TrainingApplication.java` (line 1122):
```java
public static boolean isPaused() {
    return cTrackingMode != TrackingMode.TRACKING;
}
```
Before tracking starts, the tracking state is `TrackingMode.READY`. Because `TrackingMode.READY != TrackingMode.TRACKING`, `TrainingApplication.isPaused()` returns `true`!

Consequently:
1. `newValue()` in all three rolling filters immediately dropped all incoming sensor samples whenever the app was in `TrackingMode.READY`.
2. As a result, `mTimestampedValues.isEmpty()` or `mValues.isEmpty()`, and `getFilteredValue()` returned `null`.
3. In `InstantaneousFilter.java`, `newValue(T value)` does not check `TrainingApplication.isPaused()`; it assigns `mValue = value;` unconditionally. This is why raw tiles worked while filtered tiles showed `--`.
4. In `VerticalSpeedAndSlopeDevice.java` (line 216), `calculateMetrics()` calls:
   ```java
   FilteredSensorData<Double> altitudeFilteredSensorData = BANALService.getFilteredSensorData(cAltitudeFilter);
   ```
   Because `cAltitudeFilter` is a `TimedMovingAverageFilter`, it returned `null` in `TrackingMode.READY`, causing `calculateMetrics()` to abort at line 223 without computing vertical speed or slope.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **Decouple Rolling Smoothing Filters from Pause State**:
     - Remove `!TrainingApplication.isPaused()` guard from `ExponentialSmoothingFilter.java`, `TimedMovingAverageFilter.java`, and `NumberedMovingAverageFilter.java`.
     - Preserve null-safety check (`value != null`, `REQ-FIL-011`).
  2. **Introduce Filter Reset Contract**:
     - Add `public void reset()` lifecycle method in base class `MyFilter.java` (default no-op).
     - Implement `reset()` across filter subclasses:
       - `ExponentialSmoothingFilter`: reset `mFilteredValue = 0.0`.
       - `TimedMovingAverageFilter`: clear `mTimestampedValues`.
       - `NumberedMovingAverageFilter`: clear `mValues`, reset `mIndex = 0`.
       - `AverageFilter`: reset `mAccumulatedValue = 0.0`, `mNumberOfSamples = 0`.
       - `MaxValueFilter`: reset `mMaxValue = 0`.
  3. **Handle Accumulator Reset Intent in `FilterManager`**:
     - Register `BroadcastReceiver` for `BANALService.RESET_ACCUMULATORS_INTENT` in `FilterManager.java`.
     - Dispatch `reset()` to all active filters upon receiving the intent.
     - Clean up broadcast receiver in `FilterManager.shutDown()`.
  4. **Preserve Session Boundary Isolation**:
     - `AverageFilter` and `MaxValueFilter` accumulate only during active recording (`!TrainingApplication.isPaused()`), preserving pure workout summary integrity.
  5. **Verify Derived Metric Pre-Start Readiness**:
     - Validate that `VerticalSpeedAndSlopeDevice` calculates and broadcasts live vertical speed and slope in `TrackingMode.READY`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Modifying SQLite database schemas (`ROWS_TABLE`, `TRACKING_VIEWS_TABLE`).
  - Redesigning `ConfigureFilterDialog` presets or UI layout.
  - Altering `TrainingApplication.isPaused()` definition or global `TrackingMode` state machine.
  - Modifying tracker service session storage or workout recording engines.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Net-New Requirement**:
  - `REQ-FIL-013`: *Continuous Pre-Start Rolling Telemetry Filtering, Session Reset Isolation & Live Cockpit Metric Readiness*.
  - No existing requirements in `docs/requirements.md` are repealed or altered.
* **Chesterton's Fence Examination**:
  - *Historical Origin*: Commit `d937073c` added `!TrainingApplication.isPaused()` to filters to prevent filters from accepting values when paused. However, conflating rolling smoothing filters (which represent a moving window of immediate physical reality, e.g. 3s power) with cumulative session aggregates (which represent workout totals) caused rolling filters to freeze and show dashes before tracking starts.
  - *Resolution*: Decouple rolling smoothing filters from the pause state while preserving `!TrainingApplication.isPaused()` on cumulative aggregators (`AverageFilter`, `MaxValueFilter`), and adding a formal `reset()` lifecycle triggered on `BANALService.RESET_ACCUMULATORS_INTENT`.
  - *Invariant Preservation*:
    - `REQ-FIL-011` (Null-Safe Sensor Filtering) strictly preserved.
    - `REQ-FIL-012` (Stabilized VAM / Vertical Speed Gating) strictly preserved.
    - Recorded workout statistics isolation strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Architecture Diagram
```
                     Incoming Sensor Event (e.g. Power, HR, GPS)
                                      │
                                      ▼
                      MyFilter.newValue(value)
                                      │
         ┌────────────────────────────┴────────────────────────────┐
         │                                                         │
  Rolling Filters                                         Cumulative Filters
  (ExponentialSmoothing,                                  (AverageFilter,
   TimedMovingAverage,                                     MaxValueFilter)
   NumberedMovingAverage)                                          │
         │                                                         ▼
         ▼                                             if (!isPaused()) {
  Unconditionally process                                  Accumulate session total
  incoming non-null samples                            }
         │
         ▼
  Live Cockpit Value Available
  in READY & TRACKING & PAUSED
         │
         ▼
  Session Start (BANALService.RESET_ACCUMULATORS_INTENT)
         │
         ▼
  FilterManager.resetAllFilters() ───► Calls reset() on all filters
                                       (Clears buffers & accumulators)
```

### 5.2 Thread Model & Concurrency
- `FilterManager` and all `MyFilter` subclasses synchronize on `this` (`synchronized void newValue`, `synchronized Number getFilteredValue`, `synchronized void reset()`).
- Thread safety is fully maintained across BLE/ANT+ background threads and main UI rendering threads.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. **Null-Safety (`REQ-FIL-011`)**: Passing `null` to `newValue()` on any filter must never throw `NullPointerException` or corrupt internal state.
  2. **Recording Isolation**: Workout cumulative summaries (`AverageFilter`, `MaxValueFilter`) must not include sensor readings captured while tracking is paused or before tracking begins.
  3. **Accumulator Reset Cleanliness**: Triggering `RESET_ACCUMULATORS_INTENT` must cleanly reset all filters to their initial zero/empty state.
  4. **Zero Regressions**: 100% pass across clean-room regression test suite (`./gradlew testDebugUnitTest`).
* **Risk Rating**: **LOW**
  - The changes are strictly isolated to `com.atrainingtracker.banalservice.filters` and lifecycle broadcast registration.
  - No database schema migrations or external API contracts are touched.
