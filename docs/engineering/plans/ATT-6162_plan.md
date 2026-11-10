# Stage 3: Implementation Plan - ATT-6162: BLE Crank Rollover & State Reset

**Ticket**: [ATT-6162](https://rainerblind.atlassian.net/browse/ATT-6162)  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6162`  
**Requirement Mapping**: `REQ-CON-024` (`REQ-CON-024.1`..`REQ-CON-024.4`)  
**Test Mapping**: `TST-CON-024` (`TST-CON-024.1`..`TST-CON-024.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
In `BTLEBikeDevice.java:188-197` and `BTLEBikePowerDevice.java:433-442`, 16-bit crank revolution counter rollover (`65535 -> 1`) causes `revDiff = cumulativeCrankRevolutions - mLastCrankRevolutions` to evaluate to negative numbers (`-65534`), emitting extreme negative cadence spikes (~ -3.9 million rpm) to `mCadenceSensor`. In addition, integer division `60 * revDiff * 1024 / timeDiff` truncates decimal values, cadence values lack a physiological upper bound filter (> 250 rpm), and disconnection fails to reset revolution valid flags, corrupting reconnect calculations.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-CON-024` (*BLE Crank Revolution 16-Bit Rollover, Precision & Disconnect State Reset*)
  * `REQ-CON-024.1`: 16-bit UINT16 rollover masking: `(cumulativeCrankRevolutions - mLastCrankRevolutions) & 0xFFFFL`.
  * `REQ-CON-024.2`: Floating-point division: `(60.0 * revDiff * 1024.0) / timeDiff`.
  * `REQ-CON-024.3`: Cadence plausibility filter `[0.0, 250.0]` rpm; discard out-of-range samples.
  * `REQ-CON-024.4`: `unregisterSensors()` override resetting crank and wheel valid flags and identical counters.
* **Test Mapping**: `TST-CON-024` (`TST-CON-024.1`..`TST-CON-024.5`)

---

## 3. System Invariants & Preserved Behavior
1. **Bluetooth SIG CSC/CPS Compliance**: Cadence calculation formulas strictly adhere to Bluetooth SIG specification.
2. **Wheel Telemetry Isolation**: Wheel revolution, speed, and distance calculations remain completely unaffected.
3. **Sensor Lifecycle Contracts**: Sensor unregistration and notification contracts in `MyDevice` / `MyBTLEDevice` remain preserved.
4. **Zero Regressions & Architecture Invariance**: Zero database schema changes, zero UI layout alterations, and 100% test pass rate.

---

## 4. Proposed Architectural Changes
* In `BTLEBikeDevice.java`:
  * Apply 16-bit mask to `revDiff`: `(cumulativeCrankRevolutions - mLastCrankRevolutions) & 0xFFFFL`.
  * Switch cadence calculation to floating-point division: `(60.0 * revDiff * 1024.0) / timeDiff`.
  * Gate `mCadenceSensor.newValue(cadence)` with `if (cadence >= 0.0 && cadence <= 250.0)`.
  * Override `unregisterSensors()`: invoke `super.unregisterSensors()`, reset `mLastCrankRevolutionsValid = false`, `mLastWheelRevolutionsValid = false`, `mIdenticalCrankTime = 0`, `mIdenticalWheelTime = 0`.
* In `BTLEBikePowerDevice.java`:
  * Apply identical 16-bit rollover mask, float division, plausibility filter, and `unregisterSensors()` override.
* **UI Consistency (Rule 23)**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (CSC Device Fix)**: In `BTLEBikeDevice.java`, update `revDiff` mask, float division, plausibility guard, and add `unregisterSensors()` override.
2. **Step 2 (CPS Device Fix)**: In `BTLEBikePowerDevice.java`, apply identical arithmetic, plausibility filter, and `unregisterSensors()` override.
3. **Step 3 (Targeted Unit Tests `TST-CON-024`)**:
   - In `BTLEMeasurementParsingTest.kt`, add unit tests verifying rollover (`65535 -> 1`), float precision (`76.8 rpm`), plausibility filter rejection (> 250 rpm), and `unregisterSensors()` state reset for both CSC and CPS devices.
   - Run targeted tests:
     ```bash
     ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --tests "com.atrainingtracker.trainingtracker.build.*"
     ```

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted tests in `BTLEMeasurementParsingTest.kt` followed by clean-room suite in Stage 5.
* **Rollback**: Branch isolation (`feature/ATT-6162`) enables clean rollback via `git reset --hard` before merge.
