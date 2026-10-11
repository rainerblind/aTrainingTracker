# Stage 2: Requirement & Test Specification - ATT-3222: Enable Filtered and Smoothed Sensor Values in Cockpit Prior to Tracking Start

**Ticket**: [ATT-3222](https://atrainingtracker.atlassian.net/browse/ATT-3222)  
**Sub-task**: [ATT-3291](https://atrainingtracker.atlassian.net/browse/ATT-3291) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-166](https://atrainingtracker.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.10`  
**Requirement Mapping**: `REQ-FIL-013` (*Continuous Pre-Start Rolling Telemetry Filtering, Session Reset Isolation & Live Cockpit Metric Readiness*)  
**Test Spec ID**: `TST-FIL-005`  
**Branch**: `feature/ATT-3222`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Requirement Specification (REQ-FIL-013)

### 1.1 Problem Statement & Rationale
Currently, sensor fields configured with filters (such as exponentially smoothed speed/power, 3s/5s/10s moving averages, and derived metrics like VAM and slope %) only display data after the athlete presses "Start Tracking". Prior to starting (while in `TrackingMode.READY`), these fields display dashes (`--`), even when the respective sensors (Heart Rate straps, Power Meters, Cadence, GPS) are actively connected and streaming data.

This occurs because all non-instantaneous filters check `!TrainingApplication.isPaused()` in their `newValue()` methods. Because `isPaused()` is defined as `cTrackingMode != TrackingMode.TRACKING`, `isPaused()` returns `true` before tracking begins, causing all smoothing and moving-average filters to discard incoming samples.

Athletes warming up, calibrating equipment, or checking their cockpit before hitting "Start" need their smoothed numbers (e.g. 3s power, smoothed heart rate, slope, and vertical speed) to be immediately visible.

### 1.2 Functional & Architectural Requirements

1. **Continuous Rolling Smoothing Telemetry**:
   - The system SHALL process incoming non-null sensor readings continuously in rolling smoothing filters (`ExponentialSmoothingFilter`, `TimedMovingAverageFilter`, `NumberedMovingAverageFilter`), independent of recording status (`TrackingMode.READY`, `TrackingMode.TRACKING`, or `TrackingMode.PAUSED`).
   - The system SHALL remove the `!TrainingApplication.isPaused()` guard from `newValue(Number value)` in `ExponentialSmoothingFilter.java`, `TimedMovingAverageFilter.java`, and `NumberedMovingAverageFilter.java`.

2. **Null-Safety Invariant (`REQ-FIL-011`)**:
   - Rolling and cumulative filters SHALL preserve null-safety: passing `null` to `newValue()` MUST NOT throw `NullPointerException` and MUST NOT alter filter state.

3. **Cumulative Workout Statistics Isolation**:
   - Session aggregators (`AverageFilter`, `MaxValueFilter`) SHALL accumulate telemetry ONLY during active workout recording (`!TrainingApplication.isPaused()`).
   - Readings arriving prior to tracking start (`TrackingMode.READY`) or during workout pause (`TrackingMode.PAUSED`) MUST NOT contaminate cumulative session averages or maximums.

4. **Filter Lifecycle Reset Contract**:
   - Base class `MyFilter<T>` SHALL declare a `public void reset()` lifecycle method (default no-op).
   - Filter subclasses SHALL implement `reset()`:
     - `ExponentialSmoothingFilter`: reset `mFilteredValue = 0.0`.
     - `TimedMovingAverageFilter`: clear internal timestamped buffer (`mTimestampedValues.clear()`).
     - `NumberedMovingAverageFilter`: clear internal samples list (`mValues.clear()`) and reset circular index (`mIndex = 0`).
     - `AverageFilter`: reset accumulator `mAccumulatedValue = 0.0` and sample count `mNumberOfSamples = 0`.
     - `MaxValueFilter`: reset max value `mMaxValue = 0`.

5. **Accumulator Reset Intent Dispatch**:
   - `FilterManager` SHALL register a `BroadcastReceiver` listening for `BANALService.RESET_ACCUMULATORS_INTENT`.
   - Upon receiving `RESET_ACCUMULATORS_INTENT` (broadcast when starting or resetting a workout session), `FilterManager` SHALL invoke `reset()` on all active filters in `myFilterMap`.
   - In `FilterManager.shutDown()`, the `BroadcastReceiver` SHALL be safely unregistered.

6. **Virtual Device Pre-Start Readiness**:
   - `VerticalSpeedAndSlopeDevice` SHALL receive live smoothed altitude and speed samples via `cAltitudeFilter` and `cSpeedFilter` in `TrackingMode.READY`, computing live vertical speed (VAM) and slope percentage before workout start.

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Live Rolling Power / HR in READY)**:
  * *Given* a sensor (e.g. Power Meter or Heart Rate monitor) connected in `TrackingMode.READY` (before pressing Start),
  * *When* samples are emitted to `TimedMovingAverageFilter` (3s power), `NumberedMovingAverageFilter`, or `ExponentialSmoothingFilter`,
  * *Then* `getFilteredValue()` SHALL return the live calculated smoothed value rather than `null`.

* **Criterion 2 (Live Vertical Speed and Slope in READY)**:
  * *Given* GPS and altitude sensors connected and streaming data in `TrackingMode.READY`,
  * *When* `VerticalSpeedAndSlopeDevice.calculateMetrics()` executes,
  * *Then* `cAltitudeFilter` and `cSpeedFilter` return valid filtered numbers and `mVerticalSpeedSensor` updates with live VAM.

* **Criterion 3 (Session Boundary Isolation for Aggregators)**:
  * *Given* sensors streaming data in `TrackingMode.READY` or `TrackingMode.PAUSED`,
  * *When* `AverageFilter.newValue()` or `MaxValueFilter.newValue()` is invoked,
  * *Then* the samples SHALL NOT be accumulated, and `getFilteredValue()` SHALL remain `null` (or 0) until active tracking commences.

* **Criterion 4 (Filter Reset on Session Start)**:
  * *Given* active filters containing pre-start rolling samples,
  * *When* `BANALService.RESET_ACCUMULATORS_INTENT` is broadcast upon tapping "Start Tracking",
  * *Then* all active filters in `FilterManager` SHALL execute `reset()`, clearing buffers and session accumulators.

* **Criterion 5 (Live Rolling Views During Pause)**:
  * *Given* an active workout that is paused (`TrackingMode.PAUSED`),
  * *When* the athlete pedals or moves,
  * *Then* rolling smoothing filters continue to update and display current physical values, while cumulative workout averages remain paused.

### 1.4 System Invariants
1. **Thread Safety**: All state-mutating operations (`newValue`, `getFilteredValue`, `reset`, `resetAllFilters`) MUST be synchronized on `this`.
2. **Null-Safety (`REQ-FIL-011`)**: Zero crashes on null input.
3. **VAM Stability Gating (`REQ-FIL-012`)**: Stationary gating, accuracy gating, and low-speed drift damping in `VerticalSpeedAndSlopeDevice` MUST remain intact.
4. **Zero Regressions**: 100% pass across clean-room test suite.

---

## 2. Test Specification (TST-FIL-005)

### Test Case 1: Rolling Filters Continuous Processing in READY & PAUSED (`TST-FIL-005.1`)
* **Scope**: Automated Unit Test (`RollingFiltersContinuousProcessingTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/filters/RollingFiltersContinuousProcessingTest.kt`
* **Preconditions**: Mock `TrainingApplication.isPaused()` to return `true` (simulating `TrackingMode.READY` or `TrackingMode.PAUSED`).
* **Action**:
  1. Pass samples `[100, 200, 300]` to `TimedMovingAverageFilter`.
  2. Pass samples `[100, 200]` to `NumberedMovingAverageFilter(size = 2)`.
  3. Pass samples `[100, 200]` to `ExponentialSmoothingFilter(alpha = 0.5)`.
* **Expected Result**:
  - `TimedMovingAverageFilter.filteredValue` is not null and equals `200.0` ($\pm 0.01$).
  - `NumberedMovingAverageFilter.filteredValue` is not null and equals `150.0` ($\pm 0.01$).
  - `ExponentialSmoothingFilter.filteredValue` is not null and equals `125.0` ($\pm 0.01$).

### Test Case 2: Cumulative Aggregators Pause Isolation (`TST-FIL-005.2`)
* **Scope**: Automated Unit Test (`RollingFiltersContinuousProcessingTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/filters/RollingFiltersContinuousProcessingTest.kt`
* **Preconditions**: Mock `TrainingApplication.isPaused()` to return `true`.
* **Action**:
  1. Pass sample `250` to `AverageFilter`.
  2. Pass sample `250` to `MaxValueFilter`.
* **Expected Result**:
  - `AverageFilter.filteredValue` returns `null` (not accumulated).
  - `MaxValueFilter.filteredValue` remains `0` (not updated).
  - When `TrainingApplication.isPaused()` returns `false`, `AverageFilter` and `MaxValueFilter` accumulate samples normally.

### Test Case 3: Filter Reset Lifecycle Contract (`TST-FIL-005.3`)
* **Scope**: Automated Unit Test (`RollingFiltersContinuousProcessingTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/filters/RollingFiltersContinuousProcessingTest.kt`
* **Preconditions**: Filters populated with non-zero samples.
* **Action**: Invoke `reset()` on each filter subclass.
* **Expected Result**:
  - `TimedMovingAverageFilter.reset()` clears buffer; `filteredValue` becomes `null`.
  - `NumberedMovingAverageFilter.reset()` clears buffer; `filteredValue` becomes `null`.
  - `ExponentialSmoothingFilter.reset()` sets `filteredValue` to `0.0`.
  - `AverageFilter.reset()` clears accumulator and count; `filteredValue` becomes `null`.
  - `MaxValueFilter.reset()` resets max value to `0`.

### Test Case 4: FilterManager Accumulator Reset Dispatch (`TST-FIL-005.4`)
* **Scope**: Automated Integration Test (`FilterManagerResetTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/filters/FilterManagerResetTest.kt`
* **Preconditions**: `FilterManager` initialized with active filters.
* **Action**: Invoke `resetAllFilters()`.
* **Expected Result**:
  - All registered filters have their `reset()` method executed.
  - Buffers and accumulators are reset cleanly.

### Test Case 5: Null-Safety Regression Invariant (`TST-FIL-005.5`)
* **Scope**: Automated Unit Test (`FilterNullSafetyTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/filters/FilterNullSafetyTest.kt`
* **Preconditions**: Filters initialized.
* **Action**: Pass `null` values into all filters.
* **Expected Result**:
  - Zero crashes, zero NPEs, filter state preserved.

### Test Case 6: Clean-Room Full Suite Regression (`TST-FIL-005.6`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: 100% pass across all test suites with zero failures and zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-FIL-005.1` | Unit | `ExponentialSmoothingFilter.newValue`, `TimedMovingAverageFilter.newValue`, `NumberedMovingAverageFilter.newValue` | `REQ-FIL-013.1` | Specified |
| `TST-FIL-005.2` | Unit | `AverageFilter.newValue`, `MaxValueFilter.newValue` | `REQ-FIL-013.3` | Specified |
| `TST-FIL-005.3` | Unit | `MyFilter.reset()` implementations | `REQ-FIL-013.4` | Specified |
| `TST-FIL-005.4` | Unit / Integration | `FilterManager.resetAllFilters` | `REQ-FIL-013.5` | Specified |
| `TST-FIL-005.5` | Unit | `FilterNullSafetyTest` | `REQ-FIL-011`, `REQ-FIL-013.2` | Specified |
| `TST-FIL-005.6` | Regression | Full clean-room test suite (`./gradlew testDebugUnitTest`) | `REQ-PRO-001` | Specified |
