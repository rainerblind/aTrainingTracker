# Stage 3 Implementation Plan: ATT-1289 - [Feature] Real-time power-to-weight ratio (W/kg) cockpit metric

**Ticket**: [ATT-1289](https://rainerblind.atlassian.net/browse/ATT-1289)  
**Sub-task**: [ATT-3632](https://rainerblind.atlassian.net/browse/ATT-3632) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-173](https://rainerblind.atlassian.net/browse/ATT-173) (*Cockpit display: Improve customisation*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Branch**: `feature/ATT-1289`  
**Requirement Mapping**: `REQ-TRK-019` (Real-Time Power-to-Weight Ratio Metric & Athlete Weight Persistence)  
**Test Mapping**: `TST-TRK-011` (Real-Time Power-to-Weight Test Suite)  
**Author**: AI Agent 1 (Implementer)  
**Reviewer / Auditor**: AI Agent 2 (Auditor)  
**Date**: 2026-10-15  

---

## 1. Architectural Design & SWE.2 Component Architecture

```
                                  ┌────────────────────────┐
                                  │   SettingsDataStore    │
                                  │ (athlete_weight_kg: F) │
                                  └───────────┬────────────┘
                                              │ reads weight (kg)
                                              ▼
┌─────────────────────────┐       ┌────────────────────────┐       ┌────────────────────────┐
│     BANALService        │       │CyclingPowerMetricsDev. │◄──────┤  TrainingApplication   │
│ (BestSensor: BIKE_POWER)├──────►│     (1Hz Timer)        │       │(isRecordingSuspended())│
└─────────────────────────┘       └───────────┬────────────┘       └────────────────────────┘
                                              │
                                              │ computes W/kg
                                              │ (power / weight_kg)
                                              ▼
                                  ┌────────────────────────┐
                                  │   SensorType           │
                                  │   .POWER_TO_WEIGHT     │
                                  └───────────┬────────────┘
                                              │
                         ┌────────────────────┴────────────────────┐
                         ▼                                         ▼
            ┌─────────────────────────┐               ┌─────────────────────────┐
            │   PowerToWeight         │               │  SensorGridScreen (UI)  │
            │     Formatter           │               │   (Tile + Fallback      │
            │   (1 decimal: 4.0 W/kg) │               │    AthleteWeightDialog) │
            └─────────────────────────┘               └─────────────────────────┘
```

### Component Breakdown:

1. **`SettingsDataStore.kt` & `SettingsDataStoreJavaHelper.kt` (Athlete Weight Persistence)**:
   - DataStore preference key: `KEY_ATHLETE_WEIGHT_KG = floatPreferencesKey("athlete_weight_kg")` (default `0.0f`).
   - Flow: `val athleteWeightKgFlow: Flow<Float> = dataStore.data.map { it[KEY_ATHLETE_WEIGHT_KG] ?: 0.0f }`.
   - Mutator: `suspend fun setAthleteWeightKg(weightKg: Float)` validating and clamping weight $30.0 \dots 300.0\text{ kg}$ (or `0.0f` to clear).
   - Java Bridge methods in `SettingsDataStoreJavaHelper.kt`:
     - `@JvmStatic fun getAthleteWeightKg(context: Context): Float` (with defensive fallback returning `0.0f` for unit tests and uninitialized contexts).
     - `@JvmStatic fun setAthleteWeightKg(context: Context, weightKg: Float)`
   - Unit conversion helpers:
     - Metric: displays directly in `kg`.
     - Imperial (`TrainingApplication.getUnit() == MyUnits.IMPERIAL`): displays/edits in `lbs` ($1\text{ kg} = 2.20462262185\text{ lbs}$), always stored canonically in `kg`.

2. **`SensorType.java` & `PowerToWeightFormatter.java` (Sensor Definition)**:
   - Register new enum entry in `SensorType.java`:
     `POWER_TO_WEIGHT(R.string.power_to_weight, R.string.power_to_weight_short, R.string.units_power_to_weight, SensorValueType.DOUBLE, new PowerToWeightFormatter(), true)`
   - Implement `com.atrainingtracker.banalservice.sensorformatters.PowerToWeightFormatter.java`:
     - Implements `SensorFormatterInterface`.
     - Formats `Number` to 1 decimal place using localized decimal separator (`#0.0`, e.g. `4.0` or `3.8`).
     - Returns `"--"` when input is null, `NaN`, infinite, or non-positive ($\le 0$).
   - Sensor filter default in `SensorFilterDefaults.kt`:
     - `SensorType.POWER_TO_WEIGHT -> DefaultFilterConfig(FilterType.MOVING_AVERAGE_TIME, 3.0, "sec")` (matching raw power smoothing).
   - Sensor availability in `ActivityType.java`:
     - Include `SensorType.POWER_TO_WEIGHT` in `ActivityType.BIKE_POWER.getSensorTypeArray()`.

3. **`CyclingPowerMetricsDevice.java` & `DeviceType.java` (Computed Metrics Engine)**:
   - Register `DeviceType.CYCLING_POWER_METRICS(SensorType.POWER_TO_WEIGHT, BSportType.BIKE, 1)`.
   - Create `com.atrainingtracker.banalservice.devices.CyclingPowerMetricsDevice.java` extending `MyDevice`:
     - Instantiated by `DeviceManager.java` (`createCyclingPowerMetricsDevice()`).
     - Runs 1Hz periodic timer loop:
       1. Verifies session state: if `TrainingApplication.isRecordingSuspended()` (paused or auto-paused), skips computation and keeps previous state.
       2. Reads active raw power: `BANALService.getBestSensorData(SensorType.BIKE_POWER)`.
       3. Reads athlete weight: `SettingsDataStoreJavaHelper.getAthleteWeightKg(context)`.
       4. If weight $> 0.0\text{ kg}$ and power $\ge 0.0\text{ W}$: calculates ratio $R = \text{power} / \text{weight}$ and publishes `mPowerToWeightSensor.newValue(R)`.
       5. If weight $\le 0.0\text{ kg}$ or power is null: publishes `mPowerToWeightSensor.newValue(null)`.

4. **Athlete Baselines UI & Fallback Configuration Dialog**:
   - `ZonesSettingsActivity.kt` / `ZoneSettingsScreen.kt`:
     - Add "Athlete Baselines" card (`AthleteBaselinesSection`) displaying current configured weight in active unit (`kg` or `lbs`).
     - Tapping opens `AthleteWeightDialog`.
   - `AthleteWeightDialog.kt` (`com.atrainingtracker.trainingtracker.ui.dialogs`):
     - Jetpack Compose dialog allowing input of athlete weight in current unit (`kg` or `lbs`).
     - Validates input range ($30 \dots 300\text{ kg}$ or $66 \dots 660\text{ lbs}$).
     - Persists canonical `kg` to `SettingsDataStore`.
   - Cockpit Integration (`SensorGridScreen.kt` & `EditSensorFieldViewModel.kt`):
     - When `SensorType.POWER_TO_WEIGHT` is displayed on a cockpit tile:
       - If weight is configured ($> 0$): renders real-time ratio (e.g., `4.0 W/kg`).
       - If weight is unconfigured ($\le 0$): renders `"-- W/kg"`. Long-pressing or tapping the unconfigured tile opens `AthleteWeightDialog` for immediate setup.

5. **Localization (9 Languages)**:
   - Translation keys added to `values/strings.xml`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`:
     - `power_to_weight`, `power_to_weight_short`, `units_power_to_weight`
     - `athlete_weight_title`, `athlete_weight_label`, `athlete_weight_dialog_title`, `athlete_weight_save`, `athlete_weight_unconfigured`, `athlete_weight_invalid`

---

## 2. Rule 23 UI Consistency Analysis

- **Theme Compliance**:
  - The Athlete Baselines card in `ZoneSettingsScreen.kt` follows the existing `Card` / `ElevatedCard` design using `MaterialTheme.colorScheme.surfaceVariant` and `MaterialTheme.shapes.medium`.
  - Dark Mode: Surface container `#1E1E24`, onSurface `#E2E2E6`.
  - AMOLED Pure Black Mode: Pure black background `#000000`, crisp high-contrast border and typography.
- **Components & Dialog Reusability**:
  - `AthleteWeightDialog.kt` adheres to project design tokens: `AlertDialog`, `OutlinedTextField` with keyboard type `KeyboardType.Decimal`, supporting localized decimal comma/dot.
  - Action buttons use `TextButton` with `MaterialTheme.colorScheme.primary`.
- **Zero Cockpit Layout Displacement**:
  - `POWER_TO_WEIGHT` uses the standard `SensorTile` layout identical to other sensor metrics (such as `BIKE_POWER` and `HEART_RATE_BPM`).
  - Unit string `W/kg` fits compactly in the metric unit sub-label without wrapping.

---

## 3. Step-by-Step Atomic Implementation Schedule

### Phase 1: DataStore Persistence & Synchronous Java Helper
- Add `KEY_ATHLETE_WEIGHT_KG` to `SettingsDataStore.kt` with flow and mutator.
- Add `getAthleteWeightKg()` and `setAthleteWeightKg()` to `SettingsDataStoreJavaHelper.kt`.
- Author `SettingsDataStoreWeightTest.kt` verifying storage, flows, and unit conversion.

### Phase 2: Sensor Type & Formatter Definition
- Create `PowerToWeightFormatter.java` in `com.atrainingtracker.banalservice.sensorformatters`.
- Register `POWER_TO_WEIGHT` in `SensorType.java`.
- Register `CYCLING_POWER_METRICS` in `DeviceType.java`.
- Add `SensorType.POWER_TO_WEIGHT` to `SensorFilterDefaults.kt` (3-second default MA).
- Add `SensorType.POWER_TO_WEIGHT` to `ActivityType.BIKE_POWER`.
- Author `PowerToWeightFormatterTest.kt` and update `SensorFilterDefaultsTest.kt`.

### Phase 3: Computed Metrics Device (`CyclingPowerMetricsDevice.java`)
- Create `CyclingPowerMetricsDevice.java` extending `MyDevice`.
- Implement 1Hz sampling loop with pause/auto-pause gating (`TrainingApplication.isRecordingSuspended()`).
- Register factory method in `DeviceManager.java` and lifecycle connection in `BANALService.java`.
- Author `CyclingPowerMetricsDeviceTest.kt`.

### Phase 4: Athlete Baselines Settings & Dialog UI
- Implement `AthleteWeightDialog.kt` in `com.atrainingtracker.trainingtracker.ui.dialogs`.
- Integrate Athlete Baselines card into `ZoneSettingsScreen.kt` / `ZonesSettingsActivity.kt`.
- Connect unconfigured tile click handler in `SensorGridScreen.kt` to open `AthleteWeightDialog`.

### Phase 5: 9-Language Localization Parity
- Add string tokens across all 9 `strings.xml` files with exact format specifier parity.
- Run `TranslationParityTest.kt` to verify completeness.

### Phase 6: Clean-Room Full Suite Regression
- Run targeted unit tests: `SettingsDataStoreWeightTest`, `PowerToWeightFormatterTest`, `CyclingPowerMetricsDeviceTest`, `SensorFilterDefaultsTest`.
- Run full clean-room suite: `./gradlew testDebugUnitTest` ensuring 100% pass rate.

---

## 4. Preserved Invariants & Boundary Guards

1. **Physical Soundness**: Weight values are strictly clamped to $[30.0, 300.0]\text{ kg}$ (or $[66.0, 660.0]\text{ lbs}$). Zero or negative weights produce null/`"--"` metrics to prevent division by zero.
2. **Pause Gating**: No W/kg updates occur while tracking is paused or auto-paused (`TrainingApplication.isRecordingSuspended() == true`).
3. **Canonical Storage**: Weight is always persisted in kilograms (`kg`); Imperial conversion is purely presentational.
4. **Backward Compatibility**: If no weight is entered (`0.0f`), raw power tracking and all existing cockpit metrics function without interruption.
5. **100% Test Pass Rate**: Full unit test suite must pass with 0 regressions.
