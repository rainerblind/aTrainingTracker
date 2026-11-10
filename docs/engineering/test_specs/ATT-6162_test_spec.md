# Stage 2: Requirement & Test Specification - ATT-6162: BLE Crank Rollover & State Reset

**Ticket**: [ATT-6162](https://rainerblind.atlassian.net/browse/ATT-6162)  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6162`  
**Requirement Mapping**: `REQ-CON-024` | **Test Spec ID**: `TST-CON-024`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-CON-024)

### 1.1 Problem Statement & Rationale
In `BTLEBikeDevice.java` and `BTLEBikePowerDevice.java`, computing `cumulativeCrankRevolutions - mLastCrankRevolutions` during a 16-bit rollover (`65535 -> 1`) yields negative deltas (`-65534`) resulting in ~ -3.9 million rpm cadence spikes. Furthermore, integer division `60 * revDiff * 1024 / timeDiff` truncates decimal values, cadence values lack an upper bound guard (> 250 rpm), and disconnection fails to reset revolution valid flags, corrupting reconnect calculations.

### 1.2 Functional & Architectural Requirements
1. **16-Bit UINT16 Rollover Normalization (`REQ-CON-024.1`)**:
   - In `BTLEBikeDevice` and `BTLEBikePowerDevice`, `revDiff` SHALL be computed with 16-bit mask: `long revDiff = (cumulativeCrankRevolutions - mLastCrankRevolutions) & 0xFFFFL;`.
2. **Floating-Point Precision Division (`REQ-CON-024.2`)**:
   - Cadence SHALL be computed using float division: `double cadence = (60.0 * revDiff * 1024.0) / timeDiff;`.
3. **Physiological Plausibility Guard (`REQ-CON-024.3`)**:
   - Cadence samples SHALL be verified against `[0.0, 250.0]` rpm. Samples outside this range SHALL be discarded and not forwarded to `mCadenceSensor`.
4. **Sensor Disconnect State Reset (`REQ-CON-024.4`)**:
   - Both classes SHALL override `unregisterSensors()` and reset `mLastCrankRevolutionsValid = false`, `mLastWheelRevolutionsValid = false`, `mIdenticalCrankTime = 0`, and `mIdenticalWheelTime = 0`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (16-Bit Rollover & Float Precision)**: Given `mLastCrankRevolutions = 65535` and new `cumulativeCrankRevolutions = 1` with `timeDiff = 1024` ticks, when processed, then `revDiff` evaluates to 2 and cadence evaluates to `120.0 rpm` with zero negative spikes.
* **Criterion 2 (Plausibility Ceiling)**: Given a packet calculating cadence `> 250.0 rpm` or `< 0.0 rpm`, when evaluated, then the sample is discarded and not emitted to `mCadenceSensor`.
* **Criterion 3 (Disconnect Reset)**: Given a BLE device disconnect (`unregisterSensors()`), when invoked, then crank and wheel valid flags reset to `false` and identical counters reset to `0`.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new refinement `REQ-CON-024` refining `REQ-CON-002` and `REQ-CON-014`.
2. *Historical Origin & Commit Trace*: Introduced in `c89e2113` (CSC) and `49b5df12` (CPS); subtraction assumed monotonic counter progression.
3. *Root Reason for Existing Formulation*: `crankEventTime > mLastCrankEventTime` prevented reverse time progression but omitted 16-bit counter rollover.
4. *Preservation of Core Invariants*: Wheel speed and distance processing remain unaffected; zero UI or database modifications.

---

## 2. Test Specification (TST-CON-024)

### Test Case 1: `BTLEBikeDevice` 16-Bit Rollover & Precision (`TST-CON-024.1`)
* **Scope**: Unit test in `BTLEMeasurementParsingTest.kt`. Verify rollover from 65535 to 1 emits `120.0 rpm` without truncation or negative values.

### Test Case 2: `BTLEBikePowerDevice` 16-Bit Rollover & Precision (`TST-CON-024.2`)
* **Scope**: Unit test in `BTLEMeasurementParsingTest.kt`. Verify CPS measurement with crank rollover emits `120.0 rpm`.

### Test Case 3: Cadence Plausibility Guard (`TST-CON-024.3`)
* **Scope**: Unit test in `BTLEMeasurementParsingTest.kt`. Verify samples with calculated cadence > 250.0 rpm are discarded.

### Test Case 4: Disconnect State Reset (`TST-CON-024.4`)
* **Scope**: Unit test in `BTLEMeasurementParsingTest.kt`. Verify `unregisterSensors()` clears crank/wheel valid flags and counter states.

### Test Case 5: Tier 1 Targeted Test Suite (`TST-CON-024.5`)
* **Scope**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --tests "com.atrainingtracker.trainingtracker.build.*"`.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-CON-024.1]` | Unit | `BTLEBikeDevice.measurementCharacteristicUpdate` | `REQ-CON-024.1`, `REQ-CON-024.2` | Specified |
| `[TST-CON-024.2]` | Unit | `BTLEBikePowerDevice.measurementCharacteristicUpdate` | `REQ-CON-024.1`, `REQ-CON-024.2` | Specified |
| `[TST-CON-024.3]` | Unit | Cadence plausibility filter (`<= 250.0`) | `REQ-CON-024.3` | Specified |
| `[TST-CON-024.4]` | Unit | `BTLEBikeDevice`/`PowerDevice.unregisterSensors` | `REQ-CON-024.4` | Specified |
| `[TST-CON-024.5]` | Regression | Tier 1 targeted BLE suite | `REQ-PRO-001` | Specified |
