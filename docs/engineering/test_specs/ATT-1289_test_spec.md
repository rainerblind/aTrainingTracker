# Stage 2 Requirement & Test Specification: ATT-1289 - Real-time power-to-weight ratio (W/kg) cockpit metric

**Ticket**: [ATT-1289](https://rainerblind.atlassian.net/browse/ATT-1289)  
**Sub-task**: [ATT-3631](https://rainerblind.atlassian.net/browse/ATT-3631) (`[Test-Spec]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*UI Improvements & Visual Modernization*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Branch**: `feature/ATT-1289`  
**Requirement Mapping**: `REQ-TRK-019` (complementing `REQ-UI-370`, `REQ-UI-373`, `REQ-UI-374`, and `REQ-FIL-012`)  
**Test Mapping**: `TST-TRK-011`  
**Author**: AI Agent 1 (Implementer)  
**Reviewer / Auditor**: AI Agent 2 (Auditor)  
**Date**: 2026-10-15  

---

## 1. System Requirement Specification (`REQ-TRK-019`)

### REQ-TRK-019: Real-Time Power-to-Weight (W/kg) Cockpit Telemetry & Athlete Weight Baseline Architecture

The system SHALL provide a real-time power-to-weight ratio (W/kg) computed metric for cycling workouts, maintaining persistent athlete weight baselines, unit-aware conversions, seamless cockpit grid integration, and zero-disruption unconfigured prompt workflows (ATT-1289, complementing `REQ-UI-370`, `REQ-UI-373`, and `REQ-UI-374`):

1. **Persistent Athlete Weight Preference (`SettingsDataStore.kt`)**:
   - The system SHALL maintain a persistent float preference in `SettingsDataStore`:
     - `KEY_ATHLETE_WEIGHT_KG = floatPreferencesKey("athlete_weight_kg")` (default `0.0f`).
   - The system SHALL expose a reactive flow `val athleteWeightKgFlow: Flow<Float>` emitting the latest body weight.
   - The system SHALL provide thread-safe `@JvmStatic` accessors in `SettingsDataStoreJavaHelper.kt`:
     - `fun getAthleteWeightKg(context: Context): Float`
     - `fun setAthleteWeightKg(context: Context, weightKg: Float)`
   - **Validation & Clamping**: Stored athlete weight SHALL validate that $m_{\text{athlete}} \in [30.0, 300.0]\text{ kg}$ (or $0.0\text{f}$ representing unconfigured). Values outside this interval SHALL be clamped to the boundaries.
   - **Unit Parity**: When Imperial units are active (`TrainingApplication.getUnit() == MyUnits.IMPERIAL`), the UI SHALL display and accept body weight in pounds (`lbs`) with conversion factor $1\text{ kg} = 2.20462262185\text{ lbs}$. Stored DataStore representation strictly remains canonical kilograms.

2. **First-Class Sensor & Metric Definition (`SensorType.POWER_TO_WEIGHT`)**:
   - The system SHALL register `POWER_TO_WEIGHT` in `SensorType.java` with:
     - `fullNameId = R.string.power_to_weight`
     - `shortNameId = R.string.power_to_weight_short`
     - `unitId = R.string.units_power_to_weight`
     - `sensorValueType = SensorValueType.DOUBLE`
     - `formatter = new PowerToWeightFormatter()`
     - `isAvailable = true`
   - **Formatter Contract (`PowerToWeightFormatter.java`)**: When the input value is null, non-finite, or $\le 0.0$, the formatter SHALL return `"--"`. Otherwise, it SHALL format the value with exactly 1 decimal place (`#0.0`, e.g. `4.0` or `3.8`) using the active locale decimal separator.
   - **Filter Defaults**: In `SensorFilterDefaults.kt`, `SensorType.POWER_TO_WEIGHT` SHALL default to a 3-second moving average (`FilterType.MOVING_AVERAGE_TIME`, 3.0s), matching raw power smoothing, while supporting 10s, 30s, session average (`FilterType.AVERAGE`), and instantaneous (`FilterType.INSTANTANEOUS`).

3. **Decoupled 1Hz Computed Metrics Engine (`CyclingPowerMetricsDevice.java`)**:
   - The system SHALL provide a dedicated computed device `CyclingPowerMetricsDevice` extending `MyDevice` with `DeviceType.CYCLING_POWER_METRICS`.
   - The device SHALL observe 1-second filtered cycling power (`SensorType.POWER`).
   - On a periodic 1-second scheduled tick, the device SHALL:
     - Check synchronous pause gating: if `TrainingApplication.isRecordingSuspended()` is true, skip metric updates.
     - Retrieve active cycling power and athlete weight from `SettingsDataStoreJavaHelper`.
     - If $m_{\text{athlete}} > 0.0$ and $P \ge 0.0$, publish $w\_per\_kg = \frac{P}{m_{\text{athlete}}}$ via `mPowerToWeightSensor.newValue(w_per_kg)`.
     - If $m_{\text{athlete}} \le 0.0$, publish `null` to display the unconfigured placeholder `"--"`.

4. **Physiological Baselines UI in Zone Settings (`ZonesSettingsActivity.kt`)**:
   - In "My Stuff" -> "Training Zones" (`ZoneSettingsScreen.kt`), the system SHALL render an "Athlete Baselines / Physiologische Eckdaten" section alongside cycling FTP.
   - The section SHALL display current body weight formatted in the active unit (e.g. `75.0 kg` or `165.3 lbs`).
   - Tapping the row SHALL open a modal dialog allowing the athlete to update their body weight with instantaneous validation.

5. **Cockpit Integration & Quick Fallback Dialog (`SensorGridScreen.kt` & `EditSensorFieldViewModel.kt`)**:
   - When athlete weight is unconfigured ($m_{\text{athlete}} \le 0.0$), the cockpit W/kg tile SHALL render `"-- W/kg"`.
   - Tapping an unconfigured W/kg tile during active tracking, or adding `POWER_TO_WEIGHT` in `EditSensorFieldViewModel` when unconfigured, SHALL launch `AthleteWeightDialog`.
   - Upon saving valid weight, `SettingsDataStore` SHALL be updated and live W/kg telemetry SHALL immediately commence without requiring service reconnection.

6. **9-Language Localization Parity**:
   - All string resources SHALL be defined across all 9 application locales: `values/` (EN), `values-de/` (DE), `values-es/` (ES), `values-fr/` (FR), `values-it/` (IT), `values-ja/` (JA), `values-nl/` (NL), `values-pl/` (PL), `values-pt/` (PT).

---

## 2. Test Case Specification (`TST-TRK-011`)

### TST-TRK-011: Real-Time Power-to-Weight (W/kg) Calculation, Weight Persistence & Unit Parity Test Suite

#### 1. DataStore Weight Persistence & Clamping Tests (`SettingsDataStoreWeightTest.kt`)
- `testDefaultWeight_isZero`: Verify clean initialization returns `0.0f`.
- `testSaveAndReadWeight_persistsCanonicalKg`: Save `75.0f` kg, verify `athleteWeightKgFlow.first()` and `getAthleteWeightKg()` return `75.0f`.
- `testWeightClamping_clampsToValidBounds`: Values $< 30.0\text{ kg}$ clamp to $30.0\text{ kg}$; values $> 300.0\text{ kg}$ clamp to $300.0\text{ kg}$. Value $0.0\text{f}$ is retained as unconfigured.

#### 2. Metric & Imperial Unit Conversion Tests (`SettingsDataStoreWeightTest.kt`)
- `testMetricFormatting`: 75.0 kg produces "75.0 kg".
- `testImperialFormattingAndConversion`: 75.0 kg produces "165.3 lbs". Input of 165.3 lbs parses and saves as 75.0 kg $\pm 0.05\text{ kg}$.

#### 3. Power-to-Weight Calculation & Pause Gating Tests (`CyclingPowerMetricsDeviceTest.kt`)
- `testCalculatePowerToWeight_nominal`: Power 300 W, Weight 75 kg -> 4.0 W/kg.
- `testCalculatePowerToWeight_fractional`: Power 250 W, Weight 65 kg -> 3.846 W/kg (formats to 3.8 W/kg).
- `testCalculatePowerToWeight_zeroPower`: Power 0 W, Weight 75 kg -> 0.0 W/kg.
- `testCalculatePowerToWeight_unconfiguredWeight`: Power 300 W, Weight 0.0 kg -> `null` (displays `"--"`).
- `testCalculatePowerToWeight_pauseGating`: When `TrainingApplication.isPaused()` or `TrainingApplication.isAutoPaused()` is true, metric calculation is suppressed.

#### 4. Formatter Contract Tests (`PowerToWeightFormatterTest.kt`)
- `testFormat_nullValue_returnsDashes`: `null` -> `"--"`.
- `testFormat_zeroOrNegative_returnsZero`: `0.0` -> `"0.0"`.
- `testFormat_positiveValues`: `4.0` -> `"4.0"`, `3.846` -> `"3.8"`.
- `testFormat_nonFinite_returnsDashes`: `Double.NaN`, `Double.POSITIVE_INFINITY` -> `"--"`.

#### 5. Sensor Filter Defaults Tests (`SensorFilterDefaultsTest.kt`)
- `testPowerToWeightSensor_defaultsToThreeSecondMovingAverage`: Verify `SensorFilterDefaults.getDefaultFilterConfig(SensorType.POWER_TO_WEIGHT)` returns `FilterType.MOVING_AVERAGE_TIME`, 3.0s, "sec".
- `testAllSensorTypes_validity`: Verify the global loop across all `SensorType.values()` passes without missing configurations.

#### 6. Activity Type & Sensor Availability Tests (`ActivityTypeTest.kt`)
- Verify `SensorType.POWER_TO_WEIGHT` is included in `ActivityType.getSensorTypeArray` for `ActivityType.BIKE_POWER`.

#### 7. Localization Parity Tests (`TranslationParityTest.kt`)
- Verify all new string keys (`power_to_weight`, `power_to_weight_short`, `units_power_to_weight`, `athlete_weight_title`, `athlete_weight_dialog_title`, `athlete_weight_save`, `athlete_weight_unconfigured`, `athlete_weight_invalid`) are present and non-empty across all 9 locales with zero format specifier mismatches.

#### 8. Clean-Room Regression Suite
- Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate across all project tests.

---

## 3. Acceptance Criteria (Given-When-Then)

- **Criterion 1 (Persistent Weight Storage)**:
  - *Given* an athlete enters their body weight (e.g. 75.0 kg or 165 lbs),
  - *When* saving in Zone Settings or via the fallback dialog,
  - *Then* the value is stored in `SettingsDataStore` in canonical kilograms and emitted across `athleteWeightKgFlow`.

- **Criterion 2 (Real-Time Calculation & 1-Decimal Formatting)**:
  - *Given* an athlete weighing 75 kg producing 300 Watts smoothed power,
  - *When* observing the cockpit W/kg tile,
  - *Then* it displays "4.0 W/kg" in real time.

- **Criterion 3 (Unconfigured Fallback & Immediate Activation)**:
  - *Given* an athlete with unconfigured weight ($0.0\text{ kg}$),
  - *When* viewing the W/kg tile,
  - *Then* it displays "-- W/kg", and tapping the tile opens `AthleteWeightDialog`, after which live W/kg computation commences immediately upon saving.

- **Criterion 4 (Unit Parity)**:
  - *Given* Imperial units selected in application settings,
  - *When* entering and viewing weight,
  - *Then* values are displayed in lbs with accurate conversion.

---

## 4. Traceability Matrix

| Requirement ID | Requirement Summary | Test Case ID | Test Implementation |
|---|---|---|---|
| `REQ-TRK-019.1` | Persistent Athlete Weight & Clamping | `TST-TRK-011.1` | `SettingsDataStoreWeightTest.kt` |
| `REQ-TRK-019.2` | Metric & Imperial Unit Conversion | `TST-TRK-011.2` | `SettingsDataStoreWeightTest.kt` |
| `REQ-TRK-019.3` | Power-to-Weight Calculation & Pause Gating | `TST-TRK-011.3` | `CyclingPowerMetricsDeviceTest.kt` |
| `REQ-TRK-019.4` | Formatter Contract (1 Decimal Place) | `TST-TRK-011.4` | `PowerToWeightFormatterTest.kt` |
| `REQ-TRK-019.5` | Sensor Filter Defaults (3s MA) | `TST-TRK-011.5` | `SensorFilterDefaultsTest.kt` |
| `REQ-TRK-019.6` | Activity Type Sensor Inclusion | `TST-TRK-011.6` | `ActivityTypeTest.kt` |
| `REQ-TRK-019.7` | 9-Language Localization Parity | `TST-TRK-011.7` | `TranslationParityTest.kt` |
| `REQ-TRK-019.8` | Full Clean-Room Regression | `TST-TRK-011.8` | `./gradlew testDebugUnitTest` |
