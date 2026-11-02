# Stage 5: Walkthrough & Verification - ATT-5080: Fix Accumulator Reset Display and TrackingViewModel Null Safety

**Ticket**: [[ATT-5080]](https://rainerblind.atlassian.net/browse/ATT-5080)  
**Sub-task**: [[ATT-5143]](https://rainerblind.atlassian.net/browse/ATT-5143) (`[Test]`)  
**Parent Epic**: [[ATT-355]](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-TRK-049` (`REQ-TRK-049.4`, `REQ-TRK-049.5`)  
**Test Mapping**: `TST-TRK-049.5` (`TST-TRK-049.5a`..`TST-TRK-049.5c`)  
**Branch**: `feature/ATT-5080`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Executive Summary & Verification Overview
* **Issue Addressed**: Stopping a workout caused accumulator fields (e.g. `TIME_ACTIVE`, `DISTANCE`) to reset to `--:--:--` instead of baseline zeroes (`0:00:00`), and null string-valued sensors caused a fatal `NullPointerException` crash in `TrackingViewModel.applySensorData()`.
* **Delivered Fix**:
  1. `FilterManager.java`: In `resetAllFilters()`, re-seeded filters mapped to `MyAccumulatorSensor` with `sensor.getValue()` (`mZeroValue`), preserving baseline zeroes (`0:00:00`, `0.00 km`, `1`) in `TrackingMode.READY`.
  2. `DefaultStringFormatter.java` & `SensorData.java`: Enforced non-null `"--"` fallbacks when string values are null.
  3. `TrackingViewModel.kt`: Defensively guarded `newFormattedValue` resolution with `sensorData.stringValue ?: "--"` prior to copying state.
  4. Unit Tests: Added `FilterManagerAccumulatorResetTest.kt` (`TST-TRK-049.5a`) and `TrackingViewModelSensorNullSafetyTest.kt` (`TST-TRK-049.5b`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-049.4` | `[TST-TRK-049.5a]` | Unit Test (`FilterManagerAccumulatorResetTest`) | **PASSED** | `Verified` |
| `REQ-TRK-049.5` | `[TST-TRK-049.5b]` | Unit Test (`TrackingViewModelSensorNullSafetyTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `[TST-TRK-049.5c]` | Tier 1 Targeted (`filters.*`, `sensor.*`, `devices.*`, `ui.tracking.*`, `build.*`) | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Unit Test Suite (Rule 60 Scope)
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.filters.*" \
  --tests "com.atrainingtracker.banalservice.sensor.*" \
  --tests "com.atrainingtracker.banalservice.devices.*" \
  --tests "com.atrainingtracker.trainingtracker.ui.tracking.*" \
  --tests "com.atrainingtracker.trainingtracker.build.*"
BUILD SUCCESSFUL in 1m 18s (32 actionable tasks: 1 executed, 31 up-to-date) - 100% PASS
```

### Dedicated Accumulator & Null Safety Suites
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.filters.FilterManagerAccumulatorResetTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingViewModelSensorNullSafetyTest"
BUILD SUCCESSFUL in 12s (32 actionable tasks: 2 executed, 30 up-to-date) - 5 tests passed (100%)
```

### Requirement Governance Audit (`verify_requirement_governance.py`)
```text
python3 tools/verify_requirement_governance.py
Modified/Altered existing requirement(s) detected: REQ-TRK-049.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
```

---

## 4. Hardware / Physical Verification (Pixel 10)
* Pure filter reset and null-safety logic fix; zero layout, rendering, or schema modifications. Verified via automated Robolectric and targeted unit test suites per Rule 60.

---

## 5. Invariant & Governance Verification
1. **Accumulator Zero Baseline Invariant**: Accumulators display baseline zeroes in `READY` mode regardless of broadcast arrival ordering.
2. **Instantaneous Invalidation Invariant**: Disconnected instantaneous sensors continue resetting to null (`--`).
3. **Compose Null Safety**: `SensorFieldState.value` receives strictly non-null strings, eliminating Compose UI NPEs.
4. **Living Documentation Synchronized**: `REQ-TRK-049` and `TST-TRK-049` updated to `Verified`.
5. **Subtask Governance**: Moving Stage 5 subtask to `Erledigt` via Gate 5 advances parent ticket to `Final Review (Human)`.
