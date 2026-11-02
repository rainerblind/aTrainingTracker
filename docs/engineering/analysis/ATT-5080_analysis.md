# Stage 1 Analysis: ATT-5080 - [Bug] [Highest] Stopping a workout sets accumulator fields to --:--:-- instead of 0:00:00 and crashes TrackingViewModel with NPE on null stringValue

**Ticket**: [ATT-5080](https://rainerblind.atlassian.net/browse/ATT-5080)  
**Sub-task**: [[ATT-5134]](https://rainerblind.atlassian.net/browse/ATT-5134) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Branch**: `feature/ATT-5080`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Problem Statement & Motivation
- Following ATT-4926, stopping a workout sets accumulator fields (e.g. `TIME_ACTIVE`, `DISTANCE`) to `--:--:--` instead of `0:00:00` in `TrackingMode.READY`. On fresh app launch, they correctly display `0:00:00`.
- Furthermore, string-valued sensors (`TIME_OF_DAY`, `SENSORS`) produce a `null` `stringValue`, triggering a fatal `NullPointerException` crash in `TrackingViewModel.applySensorData()` when copying `SensorFieldState(value = newFormattedValue)`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
- **Dual Root Cause**:
  1. *Broadcast Ordering & Filter Overwrite*: `RESET_ACCUMULATORS_INTENT` is received first by `MyDevice.resetAccumulatorsReceiver` (which resets `MyAccumulatorSensor` and pushes `mZeroValue = 0`) and then by `FilterManager.mResetAccumulatorsReceiver` (which calls `InstantaneousFilter.reset()`, setting `mValue = null`). Because `mActiveTimeSensor_s` has `mRespectPause = true`, it does not emit while not tracking, leaving `mValue = null` (`--:--:--`).
  2. *Java-to-Kotlin Nullability Crash*: When `InstantaneousFilter.mValue` is `null`, `DefaultStringFormatter.format(null)` returns Java `null`. In `TrackingViewModel.applySensorData()` (line 716), passing `null` into non-null Kotlin `SensorFieldState.value` triggers `Intrinsics.checkNotNullParameter` NPE.
- **Independent Peer Review & Verification**:
  - Audited `FilterManager.java:186-193` & `InstantaneousFilter.java:51-53`: confirmed `resetAllFilters()` unconditionally nulls `InstantaneousFilter.mValue`. `FilterManager` maintains `mMapFilter2Sensor`, which can be used to re-seed accumulator filters from `sensor.getValue()`.
  - Audited `DefaultStringFormatter.java:25-27`: confirmed `return value;` returns `null` when `value == null`, unlike all other `MyFormatter` classes that return `"--"`.
  - Audited `TrackingViewModel.kt:675`: `sensorData.stringValue` lacks `?: "--"` fallback.

---

## 3. User Scope Grounding (ATT-1250)
- **In-Scope Goals**:
  1. In `FilterManager.resetAllFilters()`, re-seed filters attached to `MyAccumulatorSensor` with `sensor.getValue()` (`mZeroValue`, e.g. `0` -> `0:00:00`).
  2. In `DefaultStringFormatter.java`, return non-null `"--"` fallback when `value == null`.
  3. In `SensorData.java`, ensure `getStringValue()` returns `"--"` fallback when null.
  4. In `TrackingViewModel.kt`, defensively default `sensorData.stringValue ?: "--"`.
  5. Automated unit tests covering accumulator reset value preservation and null-safety.
- **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Altering rolling filter calculations (`TimedMovingAverageFilter`, `ExponentialSmoothingFilter`).
  - Redesigning `SensorFieldState` UI architecture or Compose layouts.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
- **Original Requirement ID & Target**: Amends `REQ-TRK-049.4` (*Reset Notification & Filter Cache Invalidation*) and `REQ-FIL-013` (*Session Reset Isolation*), Parent Epic `ATT-355`. Targets `FilterManager.java`, `DefaultStringFormatter.java`, `SensorData.java`, and `TrackingViewModel.kt`.
- **Historical Origin & Commit Trace**: `080d68c3` (ATT-4926, Sprint 2026-44.2) added `InstantaneousFilter.reset() { mValue = null; }` to clear stale historical data, but inadvertently wiped accumulator zero values and exposed `DefaultStringFormatter`'s null return.
- **Root Reason for Existing Formulation**: ATT-4926 aimed to eliminate stale workout readouts upon stopping, but failed to distinguish stateless live metrics from cumulative aggregators that possess an active baseline `mZeroValue`.
- **Preservation of Core Invariants**: Stale instantaneous telemetry from disconnected sensors is still cleared (`mValue = null`); only accumulator sensors re-seed their baseline `mZeroValue`.

---

## 5. Architectural Strategy & High-Level Solution
- **Filter Layer (`FilterManager.java`)**:
  - In `resetAllFilters()`, after `myFilter.reset()`, check `mMapFilter2Sensor.get(myFilter)`. If `sensor instanceof MyAccumulatorSensor`, query `sensor.getValue()` and re-seed via `myFilter.newValue(resetVal)`.
- **Formatter & Null Safety (`DefaultStringFormatter.java`, `SensorData.java`, `TrackingViewModel.kt`)**:
  - `DefaultStringFormatter.format`: `return value != null ? value : "--";`.
  - `SensorData.getStringValue`: `return mStringValue != null ? mStringValue : "--";`.
  - `TrackingViewModel.applySensorData`: `val newFormattedValue = ... ?: sensorData.stringValue ?: "--"`.

---

## 6. System Invariants & Risk Assessment
- **Core Invariants**:
  1. Non-accumulator sensors continue to reset to `null` (`"--"`), preventing stale telemetry leaks.
  2. Accumulators consistently display baseline zeroes (`0:00:00`, `0.00 km`, `1`) in `READY` mode.
  3. Zero NPE crashes on Compose UI thread.
- **Risk Rating**: **LOW**. Localized null-checks and filter re-seeding; zero impact on active GPS, BLE sensors, or database schema.
