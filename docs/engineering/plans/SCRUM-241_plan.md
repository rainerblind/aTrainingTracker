# Implementation Plan - Fix Production Crash in AverageFilter (ATT-241)

## 1. Root Cause Analysis (RCA)
- **Problem**: A `NullPointerException` occurs in `AverageFilter.newValue()` when it receives a `null` value from a sensor.
- **Trigger**: When a listener is added to `MySensor` (e.g., in `FilterManager.createFilter`), `MySensor.addSensorListener` immediately calls `newValue(mValue)`. If the sensor hasn't received a value yet, `mValue` is `null`.
- **Stacktrace**:
  ```
  Caused by java.lang.NullPointerException: Attempt to invoke virtual method 'double java.lang.Number.doubleValue()' on a null object reference
  at com.atrainingtracker.banalservice.filters.AverageFilter.newValue(AverageFilter.java:45)
  at com.atrainingtracker.banalservice.sensor.MySensor.addSensorListener(MySensor.java:57)
  ```
- **Scope**: Similar issues exist in `ExponentialSmoothingFilter` and `NumberedMovingAverageFilter`.

## 2. Requirement Traceability
| ID | Description | Component | Test ID |
|:---|:---|:---|:---|
| **REQ-FIL-011** | All sensor filters SHALL gracefully handle null values. | `AverageFilter.java`, `ExponentialSmoothingFilter.java`, `NumberedMovingAverageFilter.java` | `TST-FIL-001` |

## 3. Proposed Changes

### `AverageFilter.java`
- Add null check in `newValue(Number value)`. Return immediately if `value` is `null`.

### `ExponentialSmoothingFilter.java`
- Add null check in `newValue(Number value)`. Return immediately if `value` is `null`.

### `NumberedMovingAverageFilter.java`
- Add null check in `newValue(Number value)`. Return immediately if `value` is `null`.
- (Safety) Add null check in `getFilteredValue()` when summing up values, in case `null` values were already present in the list.

### `MaxValueFilter.java`
- (Audit) Already contains a null check: `if (value != null && value.doubleValue() > mMaxValue.doubleValue())`. This is safe.

## 4. Impact Analysis
- **Filtering**: Null values (representing "no data") will be ignored by filters. This is correct behavior as it prevents dragging averages towards zero or causing errors.
- **Initialization**: Fixes the crash during filter creation when sensors are not yet active.
- **Stability**: Prevents a common source of production crashes in the core sensor processing logic.

## 5. Verification Plan
- **SWE.4 Unit Verification**:
    - Run existing tests for filters.
    - Add/Run `AverageFilterTest` and `ExponentialSmoothingFilterTest` specifically passing `null` to `newValue()`.
- **SWE.6 Manual Verification**:
    - TST-FIL-001: Start tracking and ensure no crashes occur during sensor initialization or when sensors are lost/disconnected.
