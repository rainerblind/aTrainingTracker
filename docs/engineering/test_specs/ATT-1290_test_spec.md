# Stage 2 Test Specification: ATT-1290 - Live Normalized Power (NP) and Intensity Factor (IF) calculations

**Ticket**: [ATT-1290](https://rainerblind.atlassian.net/browse/ATT-1290)  
**Sub-task**: [ATT-3636](https://rainerblind.atlassian.net/browse/ATT-3636) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*UI Improvements & Visual Modernization*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Branch**: `feature/ATT-1290`  
**Requirement Mapping**: `REQ-TRK-020`  
**Test Mapping**: `TST-TRK-012`  
**Author**: AI Agent 1 (Implementer)  
**Reviewer / Auditor**: AI Agent 2 (Auditor)  
**Date**: 2026-10-15  

---

## 1. Formal Requirement Specification (`REQ-TRK-020`)

### REQ-TRK-020: Live Normalized Power (NP) and Intensity Factor (IF) Calculations & Cockpit Integration

The system SHALL calculate Dr. Andrew Coggan's Normalized Power (NP) and Intensity Factor (IF) in real time during cycling workouts, integrating these metrics into the telemetry engine, filter presets, and tracking cockpit (ATT-1290, complementing `REQ-TRK-019`, `REQ-UI-370`, `REQ-UI-373`, and `REQ-UI-374`):

1. **First-Class Sensor & Metric Definitions**:
   - `SensorType.NORMALIZED_POWER`: `fullNameId = R.string.normalized_power`, `shortNameId = R.string.normalized_power_short` ("NP"), `unitId = R.string.units_power` ("W"), `SensorValueType.DOUBLE`, `IntegerFormatter`.
   - `SensorType.INTENSITY_FACTOR`: `fullNameId = R.string.intensity_factor`, `shortNameId = R.string.intensity_factor_short` ("IF"), `unitId = R.string.units_intensity_factor` (""), `SensorValueType.DOUBLE`, `IntensityFactorFormatter` (2 decimal places `#0.00`).
   - Icon Mapping: `case POWER, POWER_TO_WEIGHT, NORMALIZED_POWER, INTENSITY_FACTOR -> R.drawable.ic_power;`.

2. **Decoupled 1Hz Computed Engine Architecture (`CyclingPowerMetricsDevice.java`)**:
   - Maintain a 30-sample rolling FIFO window of 1-second power samples $P(t) \ge 0.0\text{ W}$.
   - **Pause Gating**: Gated by `TrainingApplication.isRecordingSuspended()`. When paused or auto-paused, the state of the 30-sample queue and cumulative sums MUST NOT advance.
   - **Coasting Policy**: 0 W power samples are genuine physiological recovery points and MUST be ingested into the 30-sample rolling window.
   - **Warm-Up Window ($t < 30\text{ s}$)**: While the queue contains fewer than 30 samples, Normalized Power and Intensity Factor are undefined; `newValue(null)` SHALL be published and formatted as `"--"`.
   - **Calculation ($t \ge 30\text{ s}$)**:
     - Rolling 30s average power: $P_{30s} = \frac{1}{30}\sum_{i=0}^{29} P(t-i)$.
     - 4th power weighting: $y(t) = (P_{30s})^4$.
     - Accumulate cumulative 4th power sum $S_4 = \sum y(t)$ and valid tick counter $N$.
     - 4th root extraction: $\text{NP} = \sqrt[4]{\frac{S_4}{N}}$.
     - Publish $\text{NP}$ via `mNormalizedPowerSensor.newValue(np)`.
   - **Intensity Factor**:
     - Retrieve athlete FTP via injected `FtpProvider.getFtp(context)`.
     - If $\text{FTP} > 0$ and $\text{NP} \ge 0$, compute $\text{IF} = \frac{\text{NP}}{\text{FTP}}$ and publish via `mIntensityFactorSensor.newValue(ifVal)`.
     - If FTP is unconfigured ($\le 0$ or null), publish `null` to `mIntensityFactorSensor.newValue(null)`.

3. **Thread Safety & Lifecycle Invariants**:
   - All calculation, queue mutation, and sensor emission logic SHALL be synchronized within `CyclingPowerMetricsDevice`.
   - Scheduled executor service SHALL be explicitly terminated via `shutdownNow()` during service destruction.

4. **Sensor Filter Defaults**:
   - `NORMALIZED_POWER` and `INTENSITY_FACTOR` SHALL default to `FilterType.INSTANTANEOUS` (1.0s direct).

5. **Activity Types**:
   - `SensorType.NORMALIZED_POWER` and `SensorType.INTENSITY_FACTOR` SHALL be registered in `ActivityType.BIKE_POWER`.

6. **Cockpit Grid & Guidance UI**:
   - Cockpit tiles for NP and IF SHALL render `"--"` when undefined or unconfigured.
   - In `EditSensorFieldDialog.kt`, selecting `INTENSITY_FACTOR` when FTP is unconfigured SHALL display a guidance prompt with direct access to Zone Settings.

7. **9-Language Localization Parity**:
   - All string resources SHALL be defined across all 9 application locales: EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 2. Acceptance Criteria (Given-When-Then)

### Criterion 1 (Warmup Window / Undefined Initial State)
- **Given** an athlete starts a cycling workout with a paired power meter,
- **When** fewer than 30 seconds of active cycling power data have elapsed ($t < 30\text{ s}$),
- **Then** `NORMALIZED_POWER` and `INTENSITY_FACTOR` sensors emit `null`, and the cockpit tiles display `"-- W"` and `"--"`.

### Criterion 2 (Steady-State Baseline Verification)
- **Given** an athlete cycling at a constant 200 Watts for 60 seconds,
- **When** 30 seconds have elapsed,
- **Then** the 30-second rolling average is 200 W, and Normalized Power computes exactly to 200 W ($\pm 0.1\text{ W}$).

### Criterion 3 (Stochastic Surge Penalty Verification)
- **Given** an athlete alternating between 100 Watts and 300 Watts (arithmetic mean 200 W),
- **When** Normalized Power is evaluated across the intervals,
- **Then** the resulting Normalized Power strictly exceeds the 200 W arithmetic mean ($\text{NP} > 200\text{ W}$), reflecting the non-linear 4th power physiological strain.

### Criterion 4 (Intensity Factor Calculation)
- **Given** an athlete with configured FTP of 300 Watts producing a Normalized Power of 240 Watts,
- **When** observing the `INTENSITY_FACTOR` tile,
- **Then** it displays `"0.80"`.

### Criterion 5 (Unconfigured FTP Fallback)
- **Given** an athlete with no configured FTP baseline in `SettingsDataStore`,
- **When** observing the `INTENSITY_FACTOR` cockpit tile,
- **Then** it displays `"--"`, and selecting the field in `EditSensorFieldDialog` surfaces a guidance button prompting the athlete to configure their FTP.

### Criterion 6 (Pause Gating Invariant)
- **Given** active tracking is paused (manually or auto-paused),
- **When** time passes during the pause,
- **Then** the 30-second power buffer and cumulative 4th power sums do not advance.

---

## 3. Concrete Test Specifications (`TST-TRK-012`)

### 1. Mathematical Unit Tests (`NormalizedPowerMathTest.kt`)
- `testWarmupWindow_lessThan30Samples_returnsNull()`:
  Feeds 29 samples of 250W. Asserts NP is null.
- `testSteadyStatePower_30Samples_equalsArithmeticMean()`:
  Feeds 30 samples of 200W. Asserts NP is 200.0 W.
- `testStochasticPower_surgeIntervals_npExceedsAveragePower()`:
  Feeds 30 samples at 100W followed by 30 samples at 300W. Asserts NP > 200.0 W.
- `testCoastingInclusion_zeroWattsAreIngested()`:
  Feeds 15 samples of 300W and 15 samples of 0W. Asserts 30s rolling mean is 150W.
- `testIntensityFactor_validFtp_computesCorrectRatio()`:
  Tests NP = 240.0, FTP = 300 -> IF = 0.80.
- `testIntensityFactor_unconfiguredFtp_returnsNull()`:
  Tests FTP = 0, FTP = -1, FTP = null -> IF is null.

### 2. Formatter Unit Tests (`IntensityFactorFormatterTest.kt`)
- `testFormat_nullValue_returnsDashes()`
- `testFormat_nanOrInfinite_returnsDashes()`
- `testFormat_negative_returnsDashes()`
- `testFormat_validValues_formatsWithTwoDecimals()` (`0.85` -> `"0.85"`, `1.025` -> `"1.03"`, `0.7` -> `"0.70"`)

### 3. Engine & Device Integration Tests (`CyclingPowerMetricsDeviceTest.kt`)
- `testCalculateMetrics_npAndIf_emitsCorrectSensors()`
- `testCalculateMetrics_recordingSuspended_doesNotAdvanceNpBuffer()`
- `testCalculateMetrics_unconfiguredFtp_emitsNullIf()`

### 4. Sensor Filter Presets & Activity Types Tests
- `SensorFilterDefaultsTest.kt`: Asserts default `FilterType.INSTANTANEOUS` for `NORMALIZED_POWER` and `INTENSITY_FACTOR`.
- `ActivityTypeTest.kt`: Asserts presence of `NORMALIZED_POWER` and `INTENSITY_FACTOR` in `BIKE_POWER`.

### 5. Localization Parity Audit (`TranslationParityTest.kt`)
- Asserts string tokens `normalized_power`, `normalized_power_short`, `intensity_factor`, `intensity_factor_short` exist across all 9 locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

### 6. Clean-Room Regression Verification
- Run `./gradlew testDebugUnitTest` asserting 100% test pass rate.

---

## 4. Requirement Traceability Matrix

| Requirement Clause | Test Specification | Verification Target |
|:---|:---|:---|
| `REQ-TRK-020.1` (Sensor Definitions) | `TST-TRK-012.4`, `TST-TRK-012.5` | `SensorType.java`, `IntensityFactorFormatter.java` |
| `REQ-TRK-020.2` (30s Window & NP Algorithm) | `TST-TRK-012.1`, `TST-TRK-012.3` | `CyclingPowerMetricsDevice.java` |
| `REQ-TRK-020.2` (Intensity Factor) | `TST-TRK-012.1`, `TST-TRK-012.3` | `CyclingPowerMetricsDevice.java` |
| `REQ-TRK-020.3` (Thread Safety & Shutdown) | `TST-TRK-012.3` | `CyclingPowerMetricsDevice.java` |
| `REQ-TRK-020.4` (Filter Defaults) | `TST-TRK-012.5` | `SensorFilterDefaults.kt` |
| `REQ-TRK-020.5` (Activity Types) | `TST-TRK-012.5` | `ActivityType.java` |
| `REQ-TRK-020.6` (Cockpit Grid & Guidance UI) | `TST-TRK-012.1` | `EditSensorFieldDialog.kt` |
| `REQ-TRK-020.7` (9-Language Parity) | `TST-TRK-012.5` | 9 `strings.xml` resource files |
