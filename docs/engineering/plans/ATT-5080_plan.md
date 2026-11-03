# Stage 3: Implementation Plan - ATT-5080: Fix Accumulator Reset Display and TrackingViewModel Null Safety

**Ticket**: [[ATT-5080]](https://rainerblind.atlassian.net/browse/ATT-5080)  
**Sub-task**: [[ATT-5140]](https://rainerblind.atlassian.net/browse/ATT-5140) (`[Impl-Plan]`)  
**Parent Epic**: [[ATT-355]](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-TRK-049` (`REQ-TRK-049.4`, `REQ-TRK-049.5`)  
**Test Mapping**: `TST-TRK-049.5` (`TST-TRK-049.5a`..`TST-TRK-049.5c`)  
**Branch**: `feature/ATT-5080`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Problem Description & Background
Upon stopping a workout, accumulator metrics (`active_time`, `distance`, `lap`) wipe to `--:--:--` instead of showing baseline zeroes (`0:00:00`, `0.00 km`, `1`), and null string-valued sensors trigger a fatal `NullPointerException` in `TrackingViewModel.applySensorData()`. The fix re-seeds accumulator baseline values in `FilterManager.resetAllFilters()` and enforces non-null string fallbacks (`"--"`) across `DefaultStringFormatter`, `SensorData`, and `TrackingViewModel`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-TRK-049.4` (*Accumulator Reset Baseline Re-seeding*) $\rightarrow$ `TST-TRK-049.5a` (`FilterManagerResetTest`)
* **Requirement**: `REQ-TRK-049.5` (*String Null Safety & Formatter Defense*) $\rightarrow$ `TST-TRK-049.5b` (`TrackingViewModelSensorNullSafetyTest`)
* **Requirement**: `REQ-PRO-001` (*Non-Regression Verification*) $\rightarrow$ `TST-TRK-049.5c` (Full clean-room test suite)

---

## 3. System Invariants & Preserved Behavior
1. **Baseline Accumulator Display**: Cumulative sensors display valid zero baselines in `READY` mode without depending on asynchronous broadcast arrival order.
2. **Instantaneous Telemetry Flush**: Disconnected instantaneous sensors continue resetting to `null` (`"--"`), maintaining ATT-4926 stale-cache clearing invariants.
3. **Compose Null Safety**: `SensorFieldState.value` receives strictly non-null strings, eliminating runtime NPE crashes during Compose state updates.
4. **UI Consistency**: No UI layout changes; existing Compose components, theme tokens, and typography are preserved unchanged.

---

## 4. Proposed Architectural Changes
* **Filter Layer (`FilterManager.java`)**:
  In `resetAllFilters()`, iterate through registered filters. After `myFilter.reset()`, retrieve mapped `MySensor`. If `sensor instanceof MyAccumulatorSensor`, query `sensor.getValue()` (which yields `mZeroValue`) and re-seed via `myFilter.newValue(zeroVal)`.
* **Sensor & Formatter Layer (`DefaultStringFormatter.java`, `SensorData.java`)**:
  In `DefaultStringFormatter.format(String value)`, return `value != null ? value : "--";`. In `SensorData.getStringValue()`, guard `mStringValue != null ? mStringValue : "--";`.
* **ViewModel Layer (`TrackingViewModel.kt`)**:
  In `applySensorData()`, ensure `newFormattedValue` falls back to `"--"` if string evaluation is null before invoking `fieldToUpdate.copy(...)`.
* **Testing Layer (`FilterManagerResetTest.kt`, `TrackingViewModelSensorNullSafetyTest.kt`)**:
  Automated tests for accumulator re-seeding and string null-safety defenses.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1: FilterManager Baseline Re-seeding (`FilterManager.java`)**:
   Update `resetAllFilters()` to re-seed filters mapped to `MyAccumulatorSensor` with `sensor.getValue()`.
2. **Step 2: String Formatter & SensorData Null Guard (`DefaultStringFormatter.java`, `SensorData.java`)**:
   Return `"--"` on null input in `DefaultStringFormatter.format()` and null `mStringValue` in `SensorData.getStringValue()`.
3. **Step 3: ViewModel Defensive Copy Guard (`TrackingViewModel.kt`)**:
   Add `?: "--"` fallback to `newFormattedValue` resolution in `TrackingViewModel.applySensorData()`.
4. **Step 4: Unit Testing & Verification (`FilterManagerResetTest.kt`, `TrackingViewModelSensorNullSafetyTest.kt`)**:
   Implement `TST-TRK-049.5a` and `TST-TRK-049.5b`. Run targeted unit tests:
   `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.filters.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests in Stage 4, full clean-room suite in Stage 5, and `verify_requirement_governance.py`.
* **Rollback**: Isolated branch `feature/ATT-5080`; changes can be reverted cleanly prior to squashing.
