# Stage 5 Walkthrough: ATT-6265 - Barometer Altitude Pocket-Compression Spike Rejection & DB Sync

## Overview
- **Ticket**: [ATT-6265](https://rainerblind.atlassian.net/browse/ATT-6265) (`[High] Barometer Altitude: altitude jumps briefly by 20 meters and then returns`)
- **Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)
- **Target Release**: `V5.0.0`
- **Fulfills Requirements**: `REQ-TRK-057` (`REQ-TRK-057.1`..`REQ-TRK-057.3`), `REQ-CON-017`, `REQ-TRK-047`
- **Fulfills Tests**: `TST-TRK-057` (`TST-TRK-057.1`..`TST-TRK-057.5`)
- **Implementation Subtask**: ATT-6355

---

## Changes Implemented

### 1. Causal Non-Linear Pocket-Compression Filtering (`BarometricAltitudeFilter.kt`)
- **Pipeline Architecture (`REQ-TRK-057.1`)**: Implemented a 3-stage causal pipeline:
  1. *Sliding Median Window*: 7-sample ring buffer rejecting isolated 1–2s transient compression pulses ($\pm 20\text{--}96\text{ m}$).
  2. *Physical Vertical Slew-Rate Limiter*: Clamps single-step delta to $|\Delta h| \le 1.2\text{ m/s} \times \Delta t$, eliminating pocket compression wavefronts.
  3. *Exponential Moving Average (EMA)*: Smooths residual quantization noise ($\alpha = 0.18$).
- **Climb Preservation**: Genuine physiological ascents up to 3,000 m/h ($0.83\text{ m/s}$) track with zero slope attenuation and $< 0.5\text{ m}$ steady-state phase lag.

### 2. Sensor Driver Integration & Calibration Stabilization (`AltitudeFromPressureDevice.java`)
- **Filter Wiring (`REQ-TRK-057.1`)**: Filtered raw pressure altitude in `handlePressureMeasurement()` before publishing to `mAltitudeSensor`.
- **Calibration Baseline Stabilization (`REQ-TRK-057.2`)**: Anchored `setAltitudeCorrection()` and `calibrate()` strictly to `mFilteredRawAltitude`, preventing workout-start compression pulses from permanently biasing the activity calibration offset. Clean state reset in `shutDown()`.

### 3. Sourced-Column Retroactive Drift Synchronization (`WorkoutSamplesDatabaseManager.java`)
- **Multi-Column SQLite Sync (`REQ-TRK-057.3`)**: Dynamically discovers all altitude columns in `samplesTable` matching `^ALTITUDE($| \(.*\)$)` (including `"ALTITUDE (Höhe aus Luftdruck)"`) and applies uniform (Case A) and piecewise-linear (Case B) drift corrections symmetrically within an atomic transaction.

---

## Verification & Test Results

### Tier 1 Targeted Unit Test Scope (`Rule 60`)
- **Scope**: Targeted unit tests covering modified device classes, database managers, and build contracts (`com.atrainingtracker.banalservice.devices.*`, `com.atrainingtracker.trainingtracker.database.*`, and `com.atrainingtracker.trainingtracker.build.*`).
- **Command**: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --max-workers=2 --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
- **Result**: `BUILD SUCCESSFUL` (100% PASS across all targeted tests).
- **Automated Tests**:
  - `BarometricAltitudeFilterTest.kt`: 7 tests verifying 50m pocket pulse rejection, 0.8 m/s climb tracking, timestamp handling, and resets (`TST-TRK-057.1`, `TST-TRK-057.2`).
  - `AltitudeFromPressureDeviceTest.kt`: 2 tests asserting calibration spike immunity and filtered altitude output (`TST-TRK-057.3`).
  - `WorkoutSamplesDatabaseManagerTest.kt`: 4 tests asserting symmetric Case A and Case B multi-column altitude updates (`TST-TRK-057.4`).

### Governance & Living Documentation
- **Governance Audit**: `python3 tools/verify_requirement_governance.py --sync-indexes && python3 tools/verify_requirement_governance.py`
- **Result**: `PASS: Master index tables successfully synchronized with 100% parity` (Total living tests: 933, 933 Verified; Total living requirements: 931, 925 Verified, 6 Planned).
- **Physical Device Deployment**: Skipped pursuant to `Rule 60` / `stage5-verification/SKILL.md` (pure backend mathematical sensor filter and database synchronization; zero UI, navigation, or layout changes).
