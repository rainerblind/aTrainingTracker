# Stage 1 Analysis: ATT-1289 - Real-time power-to-weight ratio (W/kg) cockpit metric

**Ticket**: [ATT-1289](https://rainerblind.atlassian.net/browse/ATT-1289)  
**Sub-task**: [ATT-3630](https://rainerblind.atlassian.net/browse/ATT-3630) (`[Analysis]`)  
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

## 1. Problem Statement & Motivation

In competitive and recreational cycling, absolute mechanical power (Watts) is insufficient to gauge performance on gradients, as gravitational resistance scales directly with total mass. Pacing climbs (e.g., maintaining 3.5 W/kg or 4.2 W/kg) is the global standard for road, mountain, and gravel cyclists to avoid premature exhaustion.

Currently, `aTrainingTracker` captures and displays raw cycling power (`SensorType.POWER`), but does not provide a power-to-weight ratio (W/kg) metric. Cyclists are forced to perform mental calculations or rely on post-ride third-party synchronization (e.g. Strava) to analyze their climbing output.

Furthermore, athlete body weight is not tracked as a physiological baseline in `SettingsDataStore`, preventing any real-time mass-normalized computations.

### Key Objectives & Invariants:
1. **Persistent Athlete Weight Preference (`SettingsDataStore.kt`)**:
   - Store canonical body weight in kilograms as `KEY_ATHLETE_WEIGHT_KG` (`Float`, default `0.0f`).
   - Expose reactive `athleteWeightKgFlow: Flow<Float>` and thread-safe synchronous helper methods in `SettingsDataStoreJavaHelper.kt`.
   - Unit conversion parity: Display and accept inputs in pounds (`lbs`) when Imperial units are active (`TrainingApplication.getUnit() == MyUnits.IMPERIAL`), converting accurately to and from canonical kilograms ($1\text{ kg} = 2.20462262185\text{ lbs}$).
2. **First-Class Sensor & Metric Definition (`SensorType.POWER_TO_WEIGHT`)**:
   - Register `POWER_TO_WEIGHT` in `SensorType.java` with units `"W/kg"`, `SensorValueType.DOUBLE`, and dedicated `PowerToWeightFormatter` (1 decimal place `#0.0`).
   - Integrate with `BANALService` filter pipeline: default to 3-second moving average (`FilterType.MOVING_AVERAGE_TIME`, 3.0s), supporting 10s, 30s, session average (`FilterType.AVERAGE`), and instantaneous smoothing.
3. **Decoupled Real-Time Computed Metrics Architecture (`CyclingPowerMetricsDevice.java`)**:
   - Introduce a dedicated computed metrics engine `CyclingPowerMetricsDevice` extending `MyDevice` (similar to `VerticalSpeedAndSlopeDevice`), running a 1Hz calculation loop.
   - Synchronously gate execution against paused and auto-paused tracking states.
   - Consume filtered or raw cycling power and athlete weight to publish real-time $W/\text{kg} = \frac{P}{m_{\text{athlete}}}$.
4. **Physiological Baselines UI in Zone Settings**:
   - In "My Stuff" -> "Training Zones" (`ZonesSettingsActivity.kt` / `ZoneSettingsScreen.kt`), provide an "Athlete Baselines / Physiologische Eckdaten" section alongside FTP (`KEY_CYCLING_FTP`), enabling athletes to configure and review their weight.
5. **Cockpit Integration & Quick Unconfigured Fallback Dialog**:
   - When athlete weight is unconfigured (`weight <= 0.0f`):
     - Cockpit tile renders placeholder `"-- W/kg"`.
     - Tapping the unconfigured tile in live tracking or selecting `POWER_TO_WEIGHT` in `EditSensorFieldViewModel` triggers a quick weight entry dialog (`AthleteWeightDialog`).
     - Once saved, live computation immediately commences without requiring service restart.
6. **9-Language Localization Parity**:
   - Provide complete localized labels and dialog strings across EN, DE, ES, FR, IT, JA, NL, PL, and PT.

---

## 2. Forensic Investigation & Current Codebase Audit

1. **Settings & Persistence Layer**:
   - `SettingsDataStore.kt` contains `KEY_CYCLING_FTP` and `ftpFlow` for cycling baseline power, but lacks any body mass entry.
   - `SettingsDataStoreJavaHelper.kt` serves as the synchronous bridge between background Java sensor services (`BANALService`, `MyDevice`) and Kotlin Coroutines/DataStore.
2. **Sensor Pipeline & Filter Management**:
   - `SensorType.java` enumerates 37 sensor types with formats and unit IDs. `POWER` is `SensorValueType.DOUBLE` with `IntegerFormatter`.
   - `BANALService.java` routes sensor packets through `FilterManager.java` which instantiates `MyFilter` instances based on `FilterData`.
   - `SensorFilterDefaults.kt` provides default presets: `POWER` defaults to `FilterType.MOVING_AVERAGE_TIME` (3.0s). `POWER_TO_WEIGHT` should mirror this preset.
3. **Cockpit UI & Grid Tile Layout**:
   - `SensorGridScreen.kt` renders sensor cards via `SensorFieldView.kt`.
   - `SensorFieldView.kt` handles clicks: in `ScreenMode.TRACKING`, it currently only processes long-clicks to edit. Adding an `onClick` callback allows contextual interaction when a tile represents an unconfigured state.
   - `EditSensorFieldViewModel.kt` configures grid fields. Adding `SensorType.POWER_TO_WEIGHT` automatically integrates it into cycling activity views (`ActivityType.BIKE_POWER`).

---

## 3. Scope Definition & User Scope Grounding (ATT-1250)

### In-Scope:
- Addition of `KEY_ATHLETE_WEIGHT_KG` to `SettingsDataStore` and reactive flows/getters/setters.
- Metric and Imperial unit conversion for weight ($1\text{ kg} \approx 2.20462\text{ lbs}$).
- Definition of `SensorType.POWER_TO_WEIGHT` with `PowerToWeightFormatter` (1 decimal place).
- Engine `CyclingPowerMetricsDevice.java` to compute live W/kg.
- "Athlete Baselines" UI section in `ZonesSettingsActivity.kt` / `ZoneSettingsScreen.kt`.
- Unconfigured prompt handling and quick `AthleteWeightDialog` in `SensorGridScreen.kt` and `EditSensorFieldViewModel.kt`.
- Localization parity across all 9 application locales.
- Unit tests verifying storage, math conversions, sensor filtering, and UI state binding.

### Out-of-Scope (Deferred to Subsequent Tickets):
- Normalized Power (NP) and Intensity Factor (IF) calculations (explicitly reserved for `ATT-1290`).
- Training Stress Score (TSS) and Mechanical Work in kJ (explicitly reserved for `ATT-1291`).
- Visual zone gauge and balance tiles (reserved for `ATT-1293` and `ATT-1295`).
- Multi-sport athlete weight tracking (e.g. running power Stryd W/kg); initial scope bounds to cycling power.

---

## 4. Chesterton's Fence Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**:
   - Extends `REQ-TRK-001` (*Sensor Ingestion Pipeline*), complements `REQ-UI-370` (*7-Zone Coggan Cycling Power Model*), `REQ-UI-373` (*Automated Training Zone Calculation*), and `REQ-UI-374` (*Contextual Zone Settings Navigation*).
2. **Historical Origin & Commit Trace**:
   - Cycling power support originally introduced in `BANALService` (`ANTBikePowerDevice`, `BTLEBikePowerDevice`). Modernized to Compose 7-zone Coggan model in Sprint `2026-42.4` (`ATT-3184`, commit `6ae4625b`).
3. **Root Reason for Existing Formulation**:
   - Previously, power telemetry was treated solely as raw sensor input from hardware power meters without secondary physiological calculations beyond Coggan zone coloring.
4. **Preservation of Core Invariants**:
   - DataStore preference integrity preserved.
   - SQLite `WorkoutSamples` column creation preserved (`makeColumns(SensorType.values())`).
   - Clean-room test suite pass rate strictly preserved (2828+ tests passing).

---

## 5. Technical Architecture & Component Design

### Component 1: `SettingsDataStore.kt` & `SettingsDataStoreJavaHelper.kt`
- Add `KEY_ATHLETE_WEIGHT_KG = floatPreferencesKey("athlete_weight_kg")` (default `0.0f`).
- `val athleteWeightKgFlow: Flow<Float> = dataStore.data.map { it[KEY_ATHLETE_WEIGHT_KG] ?: 0.0f }`
- `suspend fun setAthleteWeightKg(weightKg: Float)` (validating $0.0 \le \text{weight} \le 300.0\text{ kg}$).
- Add Java helper methods in `SettingsDataStoreJavaHelper.kt`:
  ```kotlin
  @JvmStatic
  fun getAthleteWeightKg(context: Context): Float
  @JvmStatic
  fun setAthleteWeightKg(context: Context, weightKg: Float)
  ```

### Component 2: `SensorType.java` & Formatter
- Register `POWER_TO_WEIGHT` in `SensorType.java`:
  ```java
  POWER_TO_WEIGHT(R.string.power_to_weight, R.string.power_to_weight_short, R.string.units_power_to_weight, SensorValueType.DOUBLE, new PowerToWeightFormatter(), true),
  ```
- Implement `PowerToWeightFormatter.java` in `com.atrainingtracker.banalservice.sensor.formater`:
  - Returns `"--"` when `value == null` or `value <= 0.0`.
  - Otherwise returns `DecimalFormat("#0.0")` format string (e.g. `4.0` or `3.8`).

### Component 3: `CyclingPowerMetricsDevice.java` & `DeviceManager.java`
- Create `CyclingPowerMetricsDevice` extending `MyDevice`:
  - Instantiates `mPowerToWeightSensor = new MySensor<>(this, SensorType.POWER_TO_WEIGHT)`.
  - Sets up `FilterData(null, SensorType.POWER, FilterType.MOVING_AVERAGE_TIME, 1)`.
  - Schedules 1-second task:
    - Verifies tracking not paused / auto-paused.
    - Retrieves power from `BANALService`.
    - Retrieves weight from `SettingsDataStoreJavaHelper`.
    - If weight $> 0$ and power $\ge 0$:
      $w\_per\_kg = \frac{\text{power}}{\text{weight}}$
      `mPowerToWeightSensor.newValue(w_per_kg)`
    - Else `mPowerToWeightSensor.newValue(null)`.
- Register `DeviceType.CYCLING_POWER_METRICS` in `DeviceType.java` and initialize in `DeviceManager.java`.

### Component 4: "Athlete Baselines" UI in Zone Settings
- In `ZonesSettingsActivity.kt` / `ZoneSettingsScreen.kt`:
  - Add card below header / above zones: "Athlete Baselines" (`athlete_baselines_title`).
  - Displays Weight (`athlete_weight_label`): e.g. `75.0 kg` (or `165.3 lbs` in Imperial mode).
  - Tapping opens dialog to input weight with validation ($30.0 \dots 250.0\text{ kg}$ or $66 \dots 550\text{ lbs}$).

### Component 5: Cockpit Integration & Quick Fallback Dialog
- In `SensorGridScreen.kt`:
  - Check if any active field has `fieldState.label == stringResource(R.string.power_to_weight_short)` and athlete weight $\le 0$.
  - Tapping unconfigured tile opens `AthleteWeightDialog` directly during tracking.
  - Upon save, updates `SettingsDataStore`, instantly unlocking live W/kg telemetry.

---

## 6. Living Documentation Mapping

- **Requirement**: `REQ-TRK-019` (*Real-Time Power-to-Weight (W/kg) Cockpit Telemetry & Athlete Weight Baseline Architecture*) in `docs/requirements.md`.
- **Test Specification**: `TST-TRK-011` (*Real-Time Power-to-Weight (W/kg) Calculation, Weight Persistence & Unit Parity Test Suite*) in `docs/tests.md`.

---

## 7. Verification Strategy & Gate 1 Sign-Off Plan

1. **Targeted Unit Tests**:
   - `SettingsDataStoreWeightTest.kt`: Verify saving, loading, unit conversion between kg and lbs, and boundary clamping.
   - `CyclingPowerMetricsDeviceTest.kt`: Verify $W/\text{kg}$ computation ($300\text{ W} / 75\text{ kg} = 4.0\text{ W/kg}$), pause gating, zero/negative power handling, and unconfigured weight fallback.
   - `PowerToWeightFormatterTest.kt`: Verify decimal formatting, null handling, and localization.
   - `SensorFilterDefaultsTest.kt`: Verify `POWER_TO_WEIGHT` default filter configuration (3s moving average).
2. **Clean-Room Regression**:
   - Execute `./gradlew testDebugUnitTest` ensuring 100% pass rate.
3. **Audit**:
   - Invoke `python3 tools/review_agent.py audit ATT-3630` for independent Gate 1 audit approval.
