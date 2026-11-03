# Stage 2: Requirement & Test Specification - ATT-5191: Unwrap ProxySensor in FilterManager.resetAllFilters()

**Ticket**: [[ATT-5191]](https://rainerblind.atlassian.net/browse/ATT-5191)  
**Parent Epic**: [[ATT-355]](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-TRK-049` (`REQ-TRK-049.4`)  
**Test Spec ID**: `TST-TRK-049.5`  
**Branch**: `feature/ATT-5191`  
**Author**: AI Agent 1 (flash / Implementer)  
**Date**: 2026-11-03  

---

## 1. Requirement Specification (REQ-TRK-049)

### 1.1 Problem Statement & Rationale
When stopping a workout or entering `TrackingMode.READY`, accumulator cockpit tiles (`TIME_ACTIVE`, `TIME_LAP`, `TIME_TOTAL`, `DISTANCE`) configured under "Best Sensor" (`filterData.deviceName = null`) display `--:--:--` instead of `0:00:00`. `FilterManager.resetAllFilters()` checks `sensor instanceof MyAccumulatorSensor`, which evaluates to `false` because `MySensorManager.getSensor()` wraps the source sensor in `ProxySensor<T>`. Furthermore, relying on `sensor.getValue()` during reset is vulnerable to non-deterministic broadcast execution order between `FilterManager` and `MyDevice`.

### 1.2 Functional & Architectural Requirements
1. *ProxySensor Source Unwrapping*: `ProxySensor.java` SHALL provide `@Nullable public MySensor<T> getSourceSensor()` (and alias `getBestSensor()`) returning `mSourceSensor`.
2. *Accumulator Baseline Getter*: `MyAccumulatorSensor.java` SHALL provide `@NonNull public N getZeroValue()` returning `mZeroValue`.
3. *Robust Baseline Re-seeding (`REQ-TRK-049.4`)*: In `FilterManager.resetAllFilters()`, the system SHALL unwrap `ProxySensor` instances to resolve the underlying sensor. If the resolved sensor is a `MyAccumulatorSensor`, `myFilter.newValue()` SHALL be re-seeded directly with `accumulator.getZeroValue()`, guaranteeing baseline zeroes (`0:00:00`, `1` for `LAP_NR`, `0.00 km`) regardless of receiver delivery ordering. Non-accumulator filters continue resetting to `null` (`"--"`).

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Amends `REQ-TRK-049.4` (*Reset Notification & Filter Cache Invalidation*) and `REQ-FIL-013` (*Session Reset Isolation*), Parent Epic `ATT-355`. Targets `ProxySensor.java`, `MyAccumulatorSensor.java`, and `FilterManager.java`.
2. *Historical Origin & Commit Trace*: Traced to `080d68c3` (ATT-4926, Sprint 2026-44.2) and `0087756f` (ATT-5080, Sprint 2026-45.1). ATT-4926 introduced `InstantaneousFilter.reset() { mValue = null; }`, and ATT-5080 added accumulator re-seeding in `resetAllFilters()`, but tested directly against unwrapped mocks and assumed `sensor.getValue()` was already reset.
3. *Root Reason for Existing Formulation*: ATT-5080 guarded re-seeding with `instanceof MyAccumulatorSensor` to avoid re-populating stateless sensors, but missed decorator wrapping by `ProxySensor` and broadcast ordering races.
4. *Preservation of Core Invariants*: Stale live telemetry from disconnected sensors continues resetting to `--`; accumulators reliably display baseline zeroes (`0:00:00`, `0.00 km`, `1`) in `READY` mode without race conditions.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (ProxySensor Wrapped Accumulator Re-seeding)**:
  * *Given* a filter mapped to a `ProxySensor` wrapping a `MyAccumulatorSensor` (e.g. `TIME_ACTIVE`),
  * *When* `FilterManager.resetAllFilters()` is executed on workout stop,
  * *Then* the filter is re-seeded with `accumulator.getZeroValue()`, displaying `0:00:00` instead of `--:--:--`.
* **Criterion 2 (Broadcast Order Independence)**:
  * *Given* `FilterManager.resetAllFilters()` executing before `MyDevice.reset()`,
  * *When* accumulator filters are reset,
  * *Then* baseline zero values (`0:00:00`, `1` for `LAP_NR`) are seeded rather than stale pre-reset workout totals.

---

## 2. Test Specification (TST-TRK-049.5)

### Test Case 1: ProxySensor-Wrapped Accumulator Reset (`TST-TRK-049.5a`)
* **Scope**: Unit Test (`FilterManagerAccumulatorResetTest.kt`)
* **Preconditions**: `FilterManager` registered with `ProxySensor` wrapping `MyIntegerAccumulatorSensor` (`TIME_ACTIVE`, zero=0) and `ProxySensor` wrapping `LAP_NR` (zero=1).
* **Action**: Invoke `FilterManager.resetAllFilters()` before calling `sensor.reset()`.
* **Expected Result**: Time filter returns `0:00:00`; lap filter returns `1`; non-accumulator filter returns `null` (`"--"`).

### Test Case 2: Clean-Room Regression Suite (`TST-TRK-049.5b`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.filters.*"`
* **Expected Result**: 100% pass rate across all filter tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-049.5a` | Unit | `FilterManager.resetAllFilters()` | `REQ-TRK-049.4` | Planned |
| `TST-TRK-049.5b` | Regression | Tier 1 Filter Unit Tests | `REQ-PRO-001` | Planned |
