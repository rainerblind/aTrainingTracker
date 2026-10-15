# Walkthrough: Real-Time Power-to-Weight Ratio (W/kg) Cockpit Metric & Athlete Weight Baselines (ATT-1289)

## Fulfilling REQ-TRK-019 & TST-TRK-011

A complete real-time power-to-weight ratio (W/kg) computed metric pipeline was engineered across `BANALService`, `SettingsDataStore`, Jetpack Compose UI, and unit test suites conforming to ASPICE SWE.3 and SWE.4 standards.

---

### Implemented Architecture & Changes

#### 1. Athlete Weight Preference & DataStore Layer
- **`SettingsDataStore.kt`**:
  - Added persistent float preference `KEY_ATHLETE_WEIGHT_KG` (`athlete_weight_kg`, default `0.0f`).
  - Added constants `MIN_ATHLETE_WEIGHT_KG = 30.0f`, `MAX_ATHLETE_WEIGHT_KG = 300.0f`, `LBS_PER_KG = 2.20462262185f`, `MIN_ATHLETE_WEIGHT_LBS`, `MAX_ATHLETE_WEIGHT_LBS`.
  - Added thread-safe singleton cache `getInstance(context)`.
  - Exposed reactive `athleteWeightKgFlow: Flow<Float>` and mutating function `setAthleteWeightKg(weightKg: Float)` with boundary clamping (`coerceIn(30.0f, 300.0f)` or `0.0f`).
- **`SettingsDataStoreJavaHelper.kt`**:
  - Added `@JvmStatic` accessors `getAthleteWeightKg(context)` and `setAthleteWeightKg(context, weightKg)` utilizing cached `SettingsDataStore.getInstance(context)`.

#### 2. Formatting, Filtering & Sensor Definitions
- **`PowerToWeightFormatter.java`**:
  - Implemented 1-decimal `#0.0` formatting (e.g. `4.0`, `3.8`) using active locale decimal separators.
  - Safe null/NaN/infinite/negative handling returning `"--"`.
- **`SensorType.java`**:
  - Registered `POWER_TO_WEIGHT` enum constant with `fullNameId = R.string.power_to_weight`, `shortNameId = R.string.power_to_weight_short`, `unitId = R.string.units_power_to_weight`, `SensorValueType.DOUBLE`, and `PowerToWeightFormatter`.
  - Added icon mapping `case POWER, POWER_TO_WEIGHT -> R.drawable.ic_power;`.
- **`DeviceType.java`**:
  - Registered `CYCLING_POWER_METRICS(SensorType.POWER_TO_WEIGHT, BSportType.BIKE, 1)`.
- **`SensorFilterDefaults.kt`**:
  - Registered default 3-second moving average (`FilterType.MOVING_AVERAGE_TIME`, 3.0s).
- **`ActivityType.java`**:
  - Added `SensorType.POWER_TO_WEIGHT` to `ActivityType.BIKE_POWER`.

#### 3. Computed Metrics Engine & Thread Safety
- **`CyclingPowerMetricsDevice.java`**:
  - Computes real-time W/kg at 1Hz (`mScheduler.scheduleWithFixedDelay(this::calculateMetrics, 1, 1, TimeUnit.SECONDS)`).
  - Evaluates pause gating via injected `RecordingStateProvider` (defaulting to `TrainingApplication.isRecordingSuspended()`).
  - Evaluates athlete weight via injected `AthleteWeightProvider` (defaulting to `SettingsDataStoreJavaHelper.getAthleteWeightKg(context)`).
  - Synchronized calculation and sensor updates ensuring strict thread safety.
  - Graceful teardown via `mScheduler.shutdownNow()` tied into `DeviceManager.shutDown()` and `BANALService.onDestroy()`.
- **`DeviceManager.java` & `BANALService.java`**:
  - Instantiates `CyclingPowerMetricsDevice` and exposes it in device enumeration and lifecycle shutdown.

#### 4. User Interface & Dialogs
- **`AthleteWeightDialog.kt`**:
  - Compose dialog supporting metric (kg) and imperial (lbs) units with automatic two-way conversion, epsilon-tolerant boundary validation, and safe clamping.
  - Supports updating and clearing athlete weight.
- **`ZonesSettingsActivity.kt`**:
  - Added athlete weight baseline display row on Power tab with direct edit trigger opening `AthleteWeightDialog`.
- **`EditSensorFieldDialog.kt`**:
  - Added reactive athlete weight configuration button and modal trigger when `SensorType.POWER_TO_WEIGHT` is selected.

#### 5. 9-Language Localization
- String tokens (`power_to_weight`, `power_to_weight_short`, `units_power_to_weight`, `athlete_weight_title`, `athlete_weight_label`, `athlete_weight_dialog_title`, `athlete_weight_save`, `athlete_weight_unconfigured`, `athlete_weight_invalid`, `athlete_weight_card_description`, `athlete_weight_clear`, `athlete_weight_edit`) implemented across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

---

### Verification Evidence (TST-TRK-011)

1. **Athlete Weight Persistence & Boundary Clamping (`SettingsDataStoreWeightTest.kt`)**:
   - `testDefaultAthleteWeightIsZero`: PASS.
   - `testSaveAndRetrieveAthleteWeight`: PASS.
   - `testClampingBelowMinimum`: PASS.
   - `testClampingAboveMaximum`: PASS.
   - `testClearAthleteWeight`: PASS.

2. **Power-to-Weight Formatter Verification (`PowerToWeightFormatterTest.kt`)**:
   - `testFormat_validValues`: PASS.
   - `testFormat_nullValue_returnsDashes`: PASS.
   - `testFormat_nanValue_returnsDashes`: PASS.
   - `testFormat_infiniteValue_returnsDashes`: PASS.
   - `testFormat_negativeValue_returnsDashes`: PASS.

3. **Cycling Power Metrics Engine Verification (`CyclingPowerMetricsDeviceTest.kt`)**:
   - `testCalculateMetrics_validPowerAndWeight_publishesCorrectWPerKg`: PASS.
   - `testCalculateMetrics_fractionalPowerAndWeight_publishesExactRatio`: PASS.
   - `testCalculateMetrics_zeroPower_publishesZeroWPerKg`: PASS.
   - `testCalculateMetrics_unconfiguredWeight_publishesNull`: PASS.
   - `testCalculateMetrics_recordingSuspended_skipsCalculation`: PASS.
   - `testCalculateMetrics_negativeOrInvalidPower_publishesNull`: PASS.

4. **Sensor Filter Defaults Verification (`SensorFilterDefaultsTest.kt`)**:
   - `getDefaultFilterForType_powerToWeight_returnsMovingAverage3Seconds`: PASS.
   - All 9 filter defaults unit tests: PASS.

5. **9-Language String Parity Verification (`TranslationParityTest.kt`)**:
   - 4 translation parity tests: PASS.

6. **Clean-Room Full Suite Regression Execution**:
   - Command: `./gradlew testDebugUnitTest`
   - Result: 100% test pass rate with zero regressions across the codebase.
