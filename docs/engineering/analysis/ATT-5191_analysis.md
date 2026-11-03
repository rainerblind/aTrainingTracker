# Stage 1 Analysis: ATT-5191 - [Bug] [Highest] FilterManager.resetAllFilters(): unwrap ProxySensor so accumulator fields (TIME_ACTIVE, TIME_LAP, DISTANCE) re-seed to 0:00:00 instead of --:--:--

**Ticket**: [ATT-5191](https://rainerblind.atlassian.net/browse/ATT-5191)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Branch**: `feature/ATT-5191`  
**Author**: AI Agent 1 (pro / Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Statement & Motivation
- Observed in Sprint 2026-45.1 Review of ATT-5080: stopping a workout / entering `TrackingMode.READY` sets accumulator tiles (`TIME_ACTIVE`, `TIME_LAP`, `TIME_TOTAL`, `DISTANCE`) to `--:--:--` instead of `0:00:00` on default cockpit layouts before a new tick arrives.
- Fresh app launch initializes with `0:00:00`, but session reset wipes filters to null without re-seeding accumulator baselines.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
- **Dual Root Cause**:
  1. *ProxySensor Type Erasure in instanceof Check*: Default dashboard tiles configure `filterData.deviceName = null` ("Best Sensor"). `MySensorManager.getSensor()` returns `ProxySensor<T>` wrapping the underlying source sensor. In `FilterManager.resetAllFilters()`, `sensor instanceof MyAccumulatorSensor` evaluates to `false` because `ProxySensor` extends `MySensor<T>`, NOT `MyAccumulatorSensor`. Consequently, accumulator filters are not re-seeded and retain `mValue = null` (`--:--:--`).
  2. *Broadcast Order Dependency*: `RESET_ACCUMULATORS_INTENT` delivery order across `FilterManager` and `MyDevice` is non-deterministic. If `FilterManager` executes before `MyDevice`, calling `sensor.getValue()` reads stale pre-reset workout accumulator totals rather than baseline zero.
- **Independent Peer Review & Verification**:
  - Audited `FilterManager.java:195-201`: verified `mMapFilter2Sensor.get(myFilter)` returns `ProxySensor` without unwrapping.
  - Audited `ProxySensor.java:27-40`: confirmed `mSourceSensor` is protected without a public getter.
  - Audited `MyAccumulatorSensor.java:29, 86-92`: confirmed `mZeroValue` lacks a public getter and `getValue()` returns accumulated elapsed values prior to device reset.

---

## 3. Chesterton's Fence Archaeology & Side-Effect Impact Audit (Rule 68)
- **Original Requirement ID & Target**: Amends `REQ-TRK-049.4` (*Reset Notification & Filter Cache Invalidation*) and `REQ-FIL-013` (*Session Reset Isolation*). Targets `ProxySensor.java`, `MyAccumulatorSensor.java`, and `FilterManager.java`.
- **Historical Origin & Commit Trace**: ATT-5080 (`0087756f`) introduced accumulator re-seeding in `resetAllFilters()`, but tested directly against `MyDoubleAccumulatorSensor` mocks without accounting for `"Best Sensor"` `ProxySensor` wrapping.
- **Root Reason for Existing Formulation**: ATT-5080 aimed to prevent stale telemetry leaks by restricting re-seeding to `MyAccumulatorSensor`, but overlooked wrapper composition.
- **Preservation of Core Invariants**: Instantaneous/stateless sensors (`HR`, `CADENCE`, `POWER`) continue to reset to `null` (`"--"`).
- **Side-Effect & Cross-Caller Impact Analysis (Rule 68)**: Exposing read-only getters (`getSourceSensor()` / `getBestSensor()` and `getZeroValue()`) and unwrapping `ProxySensor` in `resetAllFilters()` has zero side-effects on active tracking, concurrency locks, or BLE/ANT+ event dispatching.

---

## 4. User Scope Grounding (ATT-1250)
- **In-Scope Goals**:
  1. In `ProxySensor.java`, expose `getSourceSensor()` (and `getBestSensor()`).
  2. In `MyAccumulatorSensor.java`, expose `getZeroValue()`.
  3. In `FilterManager.resetAllFilters()`, unwrap `ProxySensor` to reach the source sensor and re-seed with `getZeroValue()`.
  4. Add unit test in `FilterManagerAccumulatorResetTest.kt` verifying `ProxySensor`-wrapped accumulators re-seed to `0:00:00`.
- **Out-of-Scope Non-Goals**:
  - Modifying `ProxySensor` listener synchronization or lock hierarchies (`REQ-SEN-021`).
  - Redesigning `InstantaneousFilter` or UI layer formatting.

---

## 5. Architectural Strategy & High-Level Solution
- **`ProxySensor.java`**: Add public accessor `@Nullable public MySensor<T> getSourceSensor() { return mSourceSensor; }` (and alias `getBestSensor()`).
- **`MyAccumulatorSensor.java`**: Add public accessor `@NonNull public N getZeroValue() { return mZeroValue; }`.
- **`FilterManager.java`**: In `resetAllFilters()`, resolve underlying sensor via `while (sensor instanceof ProxySensor) sensor = ((ProxySensor<?>) sensor).getSourceSensor();`. If `sensor instanceof MyAccumulatorSensor`, retrieve `((MyAccumulatorSensor<?>) sensor).getZeroValue()` and dispatch `myFilter.newValue(resetVal)`.

---

## 6. System Invariants & Risk Assessment
- **Core Invariants**: Stateless metrics reset to `null` (`"--"`). Accumulator metrics consistently re-seed to baseline zero (`0:00:00`, `0.00 km`, `1`) in `READY` mode regardless of broadcast receiver execution order.
- **Risk Rating**: **LOW**. Localized accessor additions and null-safe unwrap logic. Zero impact on database or sensors.
