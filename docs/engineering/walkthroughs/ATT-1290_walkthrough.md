# Walkthrough: Live Normalized Power (NP) and Intensity Factor (IF) Calculations & Cockpit Integration (ATT-1290)

## Fulfilling REQ-TRK-020 & TST-TRK-012

A complete Dr. Andrew Coggan Normalized Power (NP) and Intensity Factor (IF) live telemetry pipeline was engineered across `BANALService`, `CyclingPowerMetricsDevice`, `SensorType`, `SensorFilterDefaults`, Compose UI, and targeted unit test suites conforming to ASPICE SWE.3, SWE.4, and SWE.5 standards.

---

### Implemented Architecture & Changes

#### 1. Sensor Definitions, Activity Registration & Formatting
- **`IntensityFactorFormatter.java`**:
  - Implemented 2-decimal `#0.00` formatting (e.g. `0.85`, `1.03`, `0.70`) using active locale decimal separators via `DecimalFormat`.
  - Defensive handling returning `"--"` for `null`, `NaN`, infinite, or negative inputs.
- **`SensorType.java`**:
  - Registered `NORMALIZED_POWER` (`fullNameId = R.string.normalized_power`, `shortNameId = R.string.normalized_power_short`, `unitId = R.string.units_power`, `SensorValueType.DOUBLE`, `IntegerFormatter`).
  - Registered `INTENSITY_FACTOR` (`fullNameId = R.string.intensity_factor`, `shortNameId = R.string.intensity_factor_short`, `unitId = R.string.units_intensity_factor`, `SensorValueType.DOUBLE`, `IntensityFactorFormatter`).
  - Mapped icons to `ic_power`: `case POWER, POWER_TO_WEIGHT, NORMALIZED_POWER, INTENSITY_FACTOR -> R.drawable.ic_power;`.
- **`ActivityType.java`**:
  - Registered both `SensorType.NORMALIZED_POWER` and `SensorType.INTENSITY_FACTOR` in `ActivityType.BIKE_POWER`.
- **`SensorFilterDefaults.kt`**:
  - Registered `FilterType.INSTANTANEOUS` (1.0s direct) for both `NORMALIZED_POWER` and `INTENSITY_FACTOR` since smoothing is already inherently embedded in the Coggan mathematical formulation.

#### 2. Coggan Algorithmic Engine & Thread Safety (`CyclingPowerMetricsDevice.java`)
- **Rolling Window & Cumulative Accumulation**:
  - Ingests active 1Hz power samples into a 30-sample rolling FIFO window (`ArrayDeque<Double> mPowerWindow30s`).
  - Evaluates active recording state via injected `RecordingStateProvider` (defaulting to `TrainingApplication.isRecordingSuspended()`). When recording is paused or auto-paused, window ingestion, sum accumulation, and counter increments are completely frozen.
  - Adheres to the Coasting Policy: 0 W power samples are genuine physiological recovery points and are ingested into the 30-sample window ($0^4 = 0$).
  - Warm-Up Window ($t < 30\text{ s}$): While the queue contains fewer than 30 samples, both NP and IF are undefined; `newValue(null)` is published and formatted as `"--"`.
  - Rolling 30s Average Power: $P_{30s} = \frac{1}{30} \sum_{i=0}^{29} P(t-i)$.
  - 4th Power Weighting: $y(t) = (P_{30s})^4$.
  - Cumulative Accumulator: $S_4 = \sum y(t)$ with valid tick counter $N$.
  - Normalized Power Extraction: $\text{NP} = \sqrt[4]{\frac{S_4}{N}}$, emitted via `mNormalizedPowerSensor.newValue(np)`.
  - Intensity Factor Extraction: Retrieved via injected `FtpProvider` (`SettingsDataStoreJavaHelper.getFtp(context)`). If $\text{FTP} > 0$ and $\text{NP} \ge 0$, computes $\text{IF} = \frac{\text{NP}}{\text{FTP}}$ and emits via `mIntensityFactorSensor.newValue(ifVal)`. If FTP is unconfigured ($\le 0$ or null), emits `null`.
- **Thread Safety**:
  - Monitor synchronization guarantees thread safety across 1Hz ticks, pause state changes, and sensor reading reads.

#### 3. Cockpit User Guidance & Dialogs
- **`EditSensorFieldDialog.kt`**:
  - Added contextual FTP guidance when `SensorType.INTENSITY_FACTOR` is selected in the cockpit layout editor.
  - Renders current configured FTP or prompts the user with direct navigation to Zone Settings if unconfigured.

#### 4. 9-Language Localization
- Added string tokens `normalized_power`, `normalized_power_short`, `intensity_factor`, `intensity_factor_short` across all 9 application locales:
  - English (`values/`)
  - German (`values-de/`)
  - Spanish (`values-es/`)
  - French (`values-fr/`)
  - Italian (`values-it/`)
  - Japanese (`values-ja/`)
  - Dutch (`values-nl/`)
  - Polish (`values-pl/`)
  - Portuguese (`values-pt/`)

---

### Verification Evidence (TST-TRK-012)

1. **Normalized Power Mathematical Verification (`NormalizedPowerMathTest.kt`)**:
   - `testWarmupWindow_returnsNull`: PASS.
   - `testSteadyStatePower_equalsMean`: PASS (30s at 200W yields exact 200.0W).
   - `testStochasticPower_exceedsArithmeticMean`: PASS (intervals of 100W and 300W with arithmetic mean 200W yield NP > 200W).
   - `testCoastingPower_properlyIncorporated`: PASS (0W coasting is accepted and properly lowers rolling mean).
   - `testIntensityFactor_validFtp`: PASS (NP 240W and FTP 300W yields IF 0.80).
   - `testIntensityFactor_nullOrZeroFtp`: PASS (returns null).
   - `testIntensityFactor_midRideFtpUpdate`: PASS (IF reacts dynamically without corrupting NP history).
   - `testLongRideStability_ieee754Precision`: PASS (5-hour simulated ride accumulates without precision degradation).

2. **Intensity Factor Formatter Verification (`IntensityFactorFormatterTest.kt`)**:
   - `testFormat_validValues`: PASS (0.854 -> 0.85, 1.028 -> 1.03, 0.7 -> 0.70).
   - `testFormat_nullValue_returnsDashes`: PASS (`"--"`).
   - `testFormat_nanValue_returnsDashes`: PASS (`"--"`).
   - `testFormat_infiniteValue_returnsDashes`: PASS (`"--"`).
   - `testFormat_negativeValue_returnsDashes`: PASS (`"--"`).

3. **Cycling Power Metrics Engine Verification (`CyclingPowerMetricsDeviceTest.kt`)**:
   - `testCalculateMetrics_validPowerAndWeight_publishesCorrectWPerKg`: PASS.
   - `testCalculateMetrics_normalizedPowerAndIntensityFactor_warmupReturnsNull`: PASS.
   - `testCalculateMetrics_normalizedPowerAndIntensityFactor_steadyStateCalculated`: PASS.
   - `testCalculateMetrics_intensityFactor_unconfiguredFtp_publishesNull`: PASS.
   - `testCalculateMetrics_recordingSuspended_skipsCalculation`: PASS.

4. **Sensor Filter Defaults Verification (`SensorFilterDefaultsTest.kt`)**:
   - `getDefaultFilterForType_normalizedPower_returnsInstantaneous`: PASS.
   - `getDefaultFilterForType_intensityFactor_returnsInstantaneous`: PASS.

5. **9-Language String Parity Verification (`TranslationParityTest.kt`)**:
   - All translation parity tests: PASS.

6. **Clean-Room Full Suite Regression Execution**:
   - Command: `./gradlew testDebugUnitTest`
   - Result: 100% test pass rate with zero regressions across the entire test suite.
