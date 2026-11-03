# Stage 3: Implementation Plan - ATT-5191: Unwrap ProxySensor in FilterManager.resetAllFilters()

**Ticket**: [[ATT-5191]](https://rainerblind.atlassian.net/browse/ATT-5191)  
**Parent Epic**: [[ATT-355]](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-TRK-049` (`REQ-TRK-049.4`)  
**Test Mapping**: `TST-TRK-049.5` (`TST-TRK-049.5a`, `TST-TRK-049.5b`)  
**Branch**: `feature/ATT-5191`  
**Author**: AI Agent 1 (pro / Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Description & Background
Upon stopping a workout / entering `TrackingMode.READY`, default cockpit accumulator tiles (`TIME_ACTIVE`, `TIME_LAP`, `DISTANCE`) display `--:--:--` instead of `0:00:00`. Because default tiles configure `filterData.deviceName = null` ("Best Sensor"), `FilterManager.mMapFilter2Sensor` maps to a `ProxySensor` which extends `MySensor<T>`, NOT `MyAccumulatorSensor`. Consequently, `sensor instanceof MyAccumulatorSensor` evaluates to `false` in `FilterManager.resetAllFilters()`. Furthermore, querying `sensor.getValue()` is subject to non-deterministic broadcast execution order between `FilterManager` and `MyDevice`. The fix unwraps `ProxySensor` and queries `accumulator.getZeroValue()`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-TRK-049.4` (*Unwrap ProxySensor & Re-seed Accumulator Baselines*) $\rightarrow$ `TST-TRK-049.5a` (`FilterManagerAccumulatorResetTest.kt`)
* **Requirement**: `REQ-PRO-001` (*Non-Regression Verification*) $\rightarrow$ `TST-TRK-049.5b` (Clean-room filter regression tests)

---

## 3. System Invariants & Preserved Behavior
1. **Accumulator Zero Baseline**: Filters backed by `ProxySensor -> MyAccumulatorSensor` reliably re-seed to `0:00:00` / `0.00 km` / `1` in `READY` mode, decoupled from broadcast ordering.
2. **Stateless Telemetry Invalidation**: Non-accumulator sensors (`HR`, `CADENCE`, `POWER`) continue resetting to `null` (`"--"`), preventing stale historical telemetry leaks.
3. **Listener Synchronization**: `ProxySensor` lock hierarchies (`REQ-SEN-021`) and listener dispatching remain strictly preserved.
4. **UI Consistency**: Zero layout modifications; Compose components, typography, and theme tokens are unchanged.

---

## 4. Proposed Architectural Changes
* **Sensor Layer (`ProxySensor.java`)**:
  Expose public accessors `@Nullable public MySensor<T> getSourceSensor() { return mSourceSensor; }` and alias `public MySensor<T> getBestSensor() { return mSourceSensor; }`.
* **Accumulator Layer (`MyAccumulatorSensor.java`)**:
  Expose public accessor `@NonNull public N getZeroValue() { return mZeroValue; }`.
* **Filter Layer (`FilterManager.java`)**:
  In `resetAllFilters()`, unwrap `ProxySensor` instances:
  `while (sensor instanceof ProxySensor) sensor = ((ProxySensor<?>) sensor).getSourceSensor();`
  If `sensor instanceof MyAccumulatorSensor`, retrieve `((MyAccumulatorSensor<?>) sensor).getZeroValue()` and dispatch `myFilter.newValue(resetVal)`.
* **Test Layer (`FilterManagerAccumulatorResetTest.kt`)**:
  Add unit tests verifying `ProxySensor`-wrapped accumulators re-seed to `0:00:00` / `1` even when `FilterManager` executes before `MyDevice.reset()`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1: Expose Accessors in Sensor Classes (`ProxySensor.java`, `MyAccumulatorSensor.java`)**:
   Add `getSourceSensor()` and `getBestSensor()` in `ProxySensor.java`; add `getZeroValue()` in `MyAccumulatorSensor.java`.
2. **Step 2: Unwrap ProxySensor and Re-seed in FilterManager (`FilterManager.java`)**:
   Update `FilterManager.resetAllFilters()` to unwrap `ProxySensor` and re-seed from `getZeroValue()`.
3. **Step 3: Unit Testing & Verification (`FilterManagerAccumulatorResetTest.kt`)**:
   Implement `TST-TRK-049.5a` covering direct and `ProxySensor`-wrapped accumulators under both broadcast execution orders.
4. **Step 4: Regression & Governance Check**:
   Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.filters.*"` and `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests in Stage 4, clean-room Tier 1 filter tests in Stage 5, and `verify_requirement_governance.py`.
* **Rollback**: Isolated branch `feature/ATT-5191`; changes can be cleanly reverted prior to squashing.
