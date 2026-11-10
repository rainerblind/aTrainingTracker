# Stage 1 Analysis: ATT-6162 - 16-Bit Crank Rollover Spike & State Reset in BLE CSC/CPS

**Ticket**: [ATT-6162](https://rainerblind.atlassian.net/browse/ATT-6162)  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6162`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
In `BTLEBikeDevice.java:188-197` and `BTLEBikePowerDevice.java:433-442`, the cadence calculation from Bluetooth Cycling Speed & Cadence (CSC) and Cycling Power (CPS) characteristics computes `revDiff = cumulativeCrankRevolutions - mLastCrankRevolutions`. When the 16-bit UINT16 counter rolls over from 65535 to 0 or 1, `revDiff` becomes negative (`-65534`), emitting extreme negative cadence spikes (~ -3.9 million rpm) to `mCadenceSensor`. In addition, integer division `60 * revDiff * 1024 / timeDiff` truncates decimal precision, cadence values lack a physiological plausibility ceiling (e.g. > 250 rpm), and disconnection fails to reset `mLastCrankRevolutionsValid` / `mLastWheelRevolutionsValid`, causing false deltas on reconnect.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  * `BTLEBikeDevice.java:191` & `BTLEBikePowerDevice.java:436`: Confirmed. 16-bit subtraction without `& 0xFFFFL` produces negative deltas on rollover (`65535 -> 1` yields `-65534`).
  * Integer Division: Confirmed. All operands are long/int primitives; integer division drops fractional RPM before converting to double.
  * Plausibility Guard: Confirmed. No upper bound guard exists; corrupt packets can emit arbitrary high values. Samples outside `[0.0, 250.0]` rpm must be discarded.
  * Disconnect State Reset: Confirmed. Neither class overrides `MyDevice.unregisterSensors()`. On disconnect (`MyBTLEDevice:367`), `mLastCrankRevolutionsValid` and `mLastWheelRevolutionsValid` stay `true`, poisoning subsequent reconnects with stale baselines.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * In `BTLEBikeDevice.java` & `BTLEBikePowerDevice.java`:
    1. Apply 16-bit mask: `long revDiff = (cumulativeCrankRevolutions - mLastCrankRevolutions) & 0xFFFFL;`
    2. Enforce floating-point division: `double cadence = (60.0 * revDiff * 1024.0) / timeDiff;`
    3. Enforce plausibility filter: `if (cadence >= 0.0 && cadence <= 250.0) { mCadenceSensor.newValue(cadence); }`
    4. Override `unregisterSensors()`: reset `mLastCrankRevolutionsValid = false`, `mLastWheelRevolutionsValid = false`, `mIdenticalCrankTime = 0`, `mIdenticalWheelTime = 0`.
  * Unit tests in `BTLEMeasurementParsingTest.kt` verifying rollover math, float precision, plausibility filtering, and disconnect reset.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Merging into shared `CscRevolutionTracker` is deferred to `ATT-6042` (blocked by this ticket).
  * No changes to ANT+ cadence/speed processing or database schemas.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new refinement `REQ-CON-024` (*BLE Crank Revolution 16-Bit Rollover, Precision & State Reset*), refining `REQ-CON-002` and `REQ-CON-014`.
* **Historical Origin & Commit Trace**: Introduced in initial BLE CSC (`c89e2113`) and CPS (`49b5df12`). Subtraction assumed monotonic counters without accounting for 16-bit UINT16 rollover.
* **Root Reason for Existing Formulation**: `crankEventTime > mLastCrankEventTime` prevented negative time, but omitted counter wrap-around handling.
* **Preservation of Core Invariants**: Wheel revolution processing remains intact; GATT connection lifecycles remain unchanged; 100% test pass rate.

---

## 5. Architectural Strategy & High-Level Solution
* Fix arithmetic and bounds filtering in `BTLEBikeDevice.java` and `BTLEBikePowerDevice.java`.
* Override `unregisterSensors()` in both classes to reset revolution tracking flags on BLE disconnect.
* Expand `BTLEMeasurementParsingTest.kt` with targeted tests for both CSC and CPS devices.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Bluetooth SIG CSC and CPS specification compliance preserved.
  2. Wheel speed and distance calculations remain completely unaffected.
  3. Structured lifecycle and sensor notification contracts maintained.
* **Risk Rating**: **LOW**. Isolated arithmetic corrections and lifecycle flag resets in BLE device parsers with zero database or UI impact.
