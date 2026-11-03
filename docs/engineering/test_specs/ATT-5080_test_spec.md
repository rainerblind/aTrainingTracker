# Stage 2: Requirement & Test Specification - ATT-5080: Fix Accumulator Reset Display and TrackingViewModel Null Safety

**Ticket**: [[ATT-5080]](https://rainerblind.atlassian.net/browse/ATT-5080)  
**Sub-task**: [[ATT-5139]](https://rainerblind.atlassian.net/browse/ATT-5139) (`[Req & Test Spec]`)  
**Parent Epic**: [[ATT-355]](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-TRK-049` (`REQ-TRK-049.4`, `REQ-TRK-049.5`)  
**Test Spec ID**: `TST-TRK-049.5`  
**Branch**: `feature/ATT-5080`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Requirement Specification (REQ-TRK-049)

### 1.1 Problem Statement & Rationale
Upon workout stop, accumulator fields reset to `--:--:--` instead of baseline zeroes (`0:00:00`), and null string-valued sensors crash `TrackingViewModel.applySensorData()` with a fatal `NullPointerException`. Accumulators must retain baseline values in `READY` mode, and string values must guarantee non-null fallback.

### 1.2 Functional & Architectural Requirements
1. *Accumulator Baseline Re-seeding (`REQ-TRK-049.4`)*: In `FilterManager.resetAllFilters()`, filters attached to `MyAccumulatorSensor` SHALL re-seed `sensor.getValue()` (`mZeroValue`, e.g. `0` -> `0:00:00`, `0.00 km`), while non-accumulator filters continue resetting to `null` (`--`).
2. *DefaultStringFormatter Non-Null Fallback (`REQ-TRK-049.5a`)*: `DefaultStringFormatter.format(String value)` SHALL return `"--"` when `value == null`.
3. *SensorData Fallback (`REQ-TRK-049.5b`)*: `SensorData.getStringValue()` SHALL return `"--"` when `mStringValue` is null.
4. *TrackingViewModel Defensive Null Guard (`REQ-TRK-049.5c`)*: In `TrackingViewModel.applySensorData()`, `newFormattedValue` SHALL guard against Java nulls (`sensorData.stringValue ?: "--"`), guaranteeing no null passes into non-null `SensorFieldState.value`.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Amends `REQ-TRK-049.4` (*Reset Notification & Filter Cache Invalidation*) and `REQ-FIL-013` (*Session Reset Isolation*), Parent Epic `ATT-355`. Targets `FilterManager.java`, `DefaultStringFormatter.java`, `SensorData.java`, and `TrackingViewModel.kt`.
2. *Historical Origin & Commit Trace*: Traced to `080d68c3` (ATT-4926, Sprint 2026-44.2), which introduced `InstantaneousFilter.reset() { mValue = null; }` to eliminate stale workout readouts, but inadvertently wiped accumulator zero values and exposed `DefaultStringFormatter`'s null return.
3. *Root Reason for Existing Formulation*: ATT-4926 intended to clear lingering historical metrics upon workout completion, but did not distinguish between stateless live metrics (which should clear to null/dash) and cumulative metrics (which have a defined baseline `mZeroValue`).
4. *Preservation of Core Invariants*: Stale live telemetry from disconnected sensors continues to clear to `--`; accumulators reliably display baseline zeroes (`0:00:00`, `0.00 km`, `1`) in `READY` mode; zero NPE crashes on Compose state updates.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Accumulator Reset)**:
  * *Given* an active workout with registered accumulator and non-accumulator filters,
  * *When* `FilterManager.resetAllFilters()` is executed on workout stop,
  * *Then* accumulator filters hold formatted zero values (`0:00:00`), and non-accumulators hold `null` (`--`).
* **Criterion 2 (Null Safety & Crash Prevention)**:
  * *Given* a sensor data payload with null `stringValue`,
  * *When* `applySensorData()` executes in `TrackingViewModel`,
  * *Then* `SensorFieldState.value` defaults to `"--"` and no `NullPointerException` is thrown.

---

## 2. Test Specification (TST-TRK-049.5)

### Test Case 1: Accumulator Filter Reset Baseline Preservation (`TST-TRK-049.5a`)
* **Scope**: Unit Test (`FilterManagerTest.kt`)
* **Preconditions**: `FilterManager` registered with `MyAccumulatorSensor` (e.g. `TIME_ACTIVE`) and instantaneous sensor (e.g. `HEART_RATE`).
* **Action**: Invoke `FilterManager.resetAllFilters()`.
* **Expected Result**: Accumulator filter returns `0:00:00`; heart rate filter returns `null` (`--`).

### Test Case 2: String Formatter & ViewModel Null Defense (`TST-TRK-049.5b`)
* **Scope**: Unit Test (`TrackingViewModelSensorNullSafetyTest.kt`)
* **Preconditions**: Mock `FilteredSensorData` with null `value` and null `stringValue`.
* **Action**: Call `DefaultStringFormatter.format(null)` and `TrackingViewModel.applySensorData()`.
* **Expected Result**: Formatter returns `"--"`; `applySensorData()` completes without NPE with `field.value == "--"`.

### Test Case 3: Clean-Room Regression Suite (`TST-TRK-049.5c`)
* **Command**: `./gradlew testDebugUnitTest` across tracking, filters, and UI packages.
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-049.5a` | Unit | `FilterManager.resetAllFilters()` | `REQ-TRK-049.4` | Specified |
| `TST-TRK-049.5b` | Unit | `DefaultStringFormatter.format`, `TrackingViewModel.applySensorData` | `REQ-TRK-049.5` | Specified |
| `TST-TRK-049.5c` | Regression | Tier 1 Targeted Test Suite | `REQ-PRO-001` | Specified |
