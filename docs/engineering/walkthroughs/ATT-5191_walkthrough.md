# Stage 5: Walkthrough & Verification - ATT-5191: Unwrap ProxySensor in FilterManager.resetAllFilters()

**Ticket**: [[ATT-5191]](https://rainerblind.atlassian.net/browse/ATT-5191)  
**Parent Epic**: [[ATT-355]](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-TRK-049` (`REQ-TRK-049.4`)  
**Test Mapping**: `TST-TRK-049.5` (`TST-TRK-049.5a`, `TST-TRK-049.5b`)  
**Branch**: `feature/ATT-5191`  
**Author**: AI Agent 1 (flash / Implementer)  
**Date**: 2026-11-03  

---

## 1. Executive Summary & Verification Overview
* **Issue Addressed**: Default cockpit accumulator tiles (`TIME_ACTIVE`, `TIME_LAP`, `DISTANCE`) configured under "Best Sensor" (`filterData.deviceName = null`) reset to `--:--:--` instead of `0:00:00` upon workout stop because `FilterManager.mMapFilter2Sensor` maps to a `ProxySensor` which does not inherit from `MyAccumulatorSensor`. Additionally, querying `sensor.getValue()` was subject to non-deterministic broadcast execution order between `FilterManager` and `MyDevice`.
* **Delivered Fix**:
  1. `ProxySensor.java`: Added `@Nullable public MySensor<T> getSourceSensor()` (and alias `getBestSensor()`) returning `mSourceSensor`.
  2. `MyAccumulatorSensor.java`: Added `@NonNull public N getZeroValue()` returning `mZeroValue`.
  3. `FilterManager.java`: In `resetAllFilters()`, unwrapped `ProxySensor` instances to resolve the underlying sensor and re-seeded accumulator filters directly with `accumulator.getZeroValue()`, decoupling baseline zero display (`0:00:00`, `1` for `LAP_NR`, `0.00 km`) from broadcast receiver ordering.
  4. Unit Tests: Extended `FilterManagerAccumulatorResetTest.kt` covering `ProxySensor`-wrapped accumulators under both broadcast execution orders.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-049.4` | `[TST-TRK-049.5a]` | Unit Test (`FilterManagerAccumulatorResetTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `[TST-TRK-049.5b]` | Tier 1 Targeted (`filters.*`, `banalservice.*`, `ui.tracking.*`, `build.*`) | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Unit Test Suite Scope (Explicitly Executed)
```text
./gradlew testDebugUnitTest \
  --tests "com.atrainingtracker.banalservice.filters.*" \
  --tests "com.atrainingtracker.banalservice.*" \
  --tests "com.atrainingtracker.trainingtracker.ui.tracking.*" \
  --tests "com.atrainingtracker.trainingtracker.build.*"
BUILD SUCCESSFUL in 1m 24s (32 actionable tasks: 1 executed, 31 up-to-date) - 100% PASS
```

### Dedicated Accumulator Reset Unit Tests
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.filters.FilterManagerAccumulatorResetTest"
BUILD SUCCESSFUL in 13s (32 actionable tasks: 6 executed, 26 up-to-date) - 5 tests passed (100%)
```

### Requirement Governance Audit (`verify_requirement_governance.py`)
```text
python3 tools/verify_requirement_governance.py
Modified/Altered existing requirement(s) detected: REQ-TRK-049.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
```

---

## 4. Hardware / Physical Verification
* Pure service/filter logic and accessor additions; zero layout, UI theme token, or database schema modifications. Verified via automated Robolectric and targeted JVM unit test suites per Rule 60.

---

## 5. Invariant & Governance Verification
1. **Accumulator Zero Baseline Invariant**: Accumulator filters display baseline zeroes (`0:00:00`, `1` for `LAP_NR`) in `TrackingMode.READY` regardless of broadcast receiver execution order.
2. **Stateless Telemetry Invalidation**: Stateless metrics (`HR`, `CADENCE`, `POWER`) cleanly reset to `null` (`"--"`).
3. **Living Documentation Synchronized**: `REQ-TRK-049` and `TST-TRK-049` updated to `Verified`.
