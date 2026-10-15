# Stage 2 Test Specification: ATT-1291 - Real-time Training Stress Score (TSS) and Mechanical Work (kJ) metrics

**Ticket**: [ATT-1291](https://rainerblind.atlassian.net/browse/ATT-1291)  
**Sub-task**: [ATT-3641](https://rainerblind.atlassian.net/browse/ATT-3641) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*UI Improvements & Visual Modernization*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Branch**: `feature/ATT-1291`  
**Requirement Mapping**: `REQ-TRK-021`  
**Test Mapping**: `TST-TRK-013`  
**Author**: AI Agent 1 (Implementer)  
**Reviewer / Auditor**: AI Agent 2 (Auditor)  
**Date**: 2026-10-15  

---

## 1. Formal Requirement Specification (`REQ-TRK-021`)

### REQ-TRK-021: Real-Time Mechanical Work (kJ) and Training Stress Score (TSS) Telemetry

The system SHALL integrate Mechanical Work (kJ) and Dr. Andrew Coggan's Training Stress Score (TSS) in real time during cycling workouts, integrating these cumulative load metrics into the telemetry engine, filter presets, and tracking cockpit (ATT-1291, complementing `REQ-TRK-019`, `REQ-TRK-020`, `REQ-UI-370`, `REQ-UI-373`, and `REQ-UI-374`):

1. **First-Class Sensor & Metric Definitions**:
   - `SensorType.WORK_KJ`: `fullNameId = R.string.work_kj`, `shortNameId = R.string.work_kj_short` ("kJ"), `unitId = R.string.units_work_kj` ("kJ"), `SensorValueType.DOUBLE`, `IntegerFormatter`, `isInstantaneous = false`.
   - `SensorType.TSS`: `fullNameId = R.string.tss`, `shortNameId = R.string.tss_short` ("TSS"), `unitId = R.string.units_none`, `SensorValueType.DOUBLE`, `IntegerFormatter`, `isInstantaneous = false`.
   - Icon Mapping: `case POWER, POWER_TO_WEIGHT, NORMALIZED_POWER, INTENSITY_FACTOR, WORK_KJ, TSS -> R.drawable.ic_power;`.

2. **Decoupled 1Hz Computed Engine Architecture (`CyclingPowerMetricsDevice.java`)**:
   - **Mechanical Work Integration**:
     - For every active 1-second tick when recording is not suspended and power $P(t) \ge 0.0\text{ W}$, the system SHALL integrate mechanical energy:
       $$W_{\text{joules}} \leftarrow W_{\text{joules}} + (P(t) \times 1.0\text{ s})$$
     - Publish $\text{Work (kJ)} = \frac{W_{\text{joules}}}{1000.0}$ via `mWorkKjSensor.newValue(workKj)`.
     - Coasting ($0\text{ W}$) adds $0\text{ J}$ to total work.
   - **Active Duration Tracking**:
     - Active power duration $t$ (seconds) advances by 1 second on each valid non-suspended tick.
   - **Warm-Up Window ($t < 30\text{ s}$)**:
     - While $t < 30\text{ s}$, Normalized Power and Intensity Factor are undefined (`null`); TSS publishes `null` (formatted as `"--"`).
   - **TSS Calculation ($t \ge 30\text{ s}$)**:
     - If athlete Functional Threshold Power is configured ($\text{FTP} > 0$) and $\text{NP} \ge 0.0$ and $\text{IF} \ge 0.0$, compute live TSS:
       $$\text{TSS} = \frac{t \times \text{NP} \times \text{IF}}{\text{FTP} \times 3600.0} \times 100.0$$
     - Publish via `mTssSensor.newValue(tss)`.
     - If FTP is unconfigured ($\le 0$ or null), TSS publishes `null` (formatted as `"--"`).
   - **Active Pause Gating**:
     - When paused or auto-paused (`isRecordingSuspended() == true`), $W_{\text{joules}}$, $t$, and TSS MUST NOT advance.
   - **Session Reset**:
     - `resetSession()` SHALL reset $W_{\text{joules}} = 0.0$, $t = 0\text{ s}$, and publish `null` to both sensors.

3. **Thread Safety & Lifecycle Invariants**:
   - All calculation, queue mutation, and sensor emission logic SHALL be synchronized within `CyclingPowerMetricsDevice` monitors.
   - Scheduled executor service SHALL be explicitly terminated via `shutdownNow()` during service destruction.

4. **Sensor Filter Defaults**:
   - `WORK_KJ` and `TSS` SHALL default to `FilterType.INSTANTANEOUS` (1.0s direct).

5. **Activity Types**:
   - `SensorType.WORK_KJ` and `SensorType.TSS` SHALL be registered in `ActivityType.BIKE_POWER`.

6. **Cockpit Grid & Guidance UI**:
   - Cockpit tiles for WORK_KJ and TSS SHALL render `"--"` when undefined or unconfigured.
   - In `EditSensorFieldDialog.kt`, selecting `TSS` when FTP is unconfigured SHALL display the guidance prompt with direct access to Zone Settings.

7. **9-Language Localization Parity**:
   - All string resources (`work_kj`, `work_kj_short`, `units_work_kj`, `tss`, `tss_short`) SHALL be defined across all 9 application locales: EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 2. Acceptance Criteria (Given-When-Then)

### Criterion 1 (Mechanical Work 1Hz Integration)
- **Given** an athlete starts a workout and rides at a steady 200 Watts for 10 seconds,
- **When** 10 seconds have elapsed,
- **Then** `WORK_KJ` sensor emits 2.0 kJ (2000 Joules).

### Criterion 2 (Coasting Invariant for Work)
- **Given** an athlete coasting at 0 Watts for 30 seconds after having accumulated 50.0 kJ,
- **When** 30 seconds elapse,
- **Then** `WORK_KJ` remains exactly 50.0 kJ.

### Criterion 3 (TSS Warm-Up Window)
- **Given** an athlete in the first 29 seconds of a ride ($t < 30\text{ s}$),
- **When** observing TSS,
- **Then** TSS emits `null` and displays `"--"`, while Work (kJ) accumulates normally from second 1.

### Criterion 4 (TSS Canonical 1-Hour Test)
- **Given** an athlete with FTP = 250 Watts riding at exactly 250 Watts for 3600 seconds (1 hour),
- **When** evaluating TSS at 3600 seconds,
- **Then** TSS evaluates to exactly 100.0 ($\text{round} = 100$).

### Criterion 5 (TSS Half-Hour Test)
- **Given** an athlete with FTP = 250 Watts riding at 250 Watts for 1800 seconds (30 minutes),
- **When** evaluating TSS at 1800 seconds,
- **Then** TSS evaluates to exactly 50.0 ($\text{round} = 50$).

### Criterion 6 (Unconfigured FTP Handling)
- **Given** an athlete with unconfigured FTP ($\le 0$ or null),
- **When** evaluating TSS,
- **Then** TSS emits `null` and displays `"--"`, and selecting the tile in `EditSensorFieldDialog.kt` shows the FTP configuration prompt.

### Criterion 7 (Pause Gating Invariant)
- **Given** an active workout with 100 kJ and 25 TSS,
- **When** tracking is paused (`isRecordingSuspended() == true`) for 5 minutes,
- **Then** neither Work (kJ), active duration, nor TSS advance.

---

## 3. Test Cases & Verification Plan (`TST-TRK-013`)

1. **Mathematical Unit Tests (`MechanicalWorkAndTssMathTest.kt`)**:
   - `testWorkIntegration_constantPower`: 10s at 200W -> 2000 J = 2.0 kJ.
   - `testWorkIntegration_coastingZeroWatts`: 0W adds 0 J.
   - `testWorkIntegration_invalidOrNegativePower`: Ignored safely.
   - `testTss_warmupReturnsNull`: $t < 30\text{ s}$ -> null.
   - `testTss_oneHourAtFtp`: 3600s at 250W with FTP 250 -> 100.0.
   - `testTss_halfHourAtFtp`: 1800s at 250W with FTP 250 -> 50.0.
   - `testTss_unconfiguredFtp`: null / <= 0 FTP -> null.
   - `testTss_midRideFtpUpdate`: Dynamic FTP change recalculates TSS immediately without resetting history.
   - `testLongRidePrecision_ieee754`: 24h ride at 400W maintains numerical exactness.

2. **Computed Telemetry Engine Tests (`CyclingPowerMetricsDeviceTest.kt`)**:
   - Verify `WORK_KJ` and `TSS` sensor registration and values.
   - Verify pause gating with `RecordingStateProvider`.
   - Verify `resetSession()` clears work and active duration.

3. **Filter Defaults Tests (`SensorFilterDefaultsTest.kt`)**:
   - `WORK_KJ` and `TSS` default to `FilterType.INSTANTANEOUS`.

4. **Activity Types Tests (`ActivityTypeTest.kt`)**:
   - Verify `ActivityType.BIKE_POWER` contains `SensorType.WORK_KJ` and `SensorType.TSS`.

5. **9-Language String Parity Tests (`TranslationParityTest.kt`)**:
   - Verify all 5 string tokens across EN, DE, ES, FR, IT, JA, NL, PL, PT.

6. **Clean-Room Regression Execution**:
   - Run `./gradlew testDebugUnitTest`.
