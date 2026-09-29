# Stage 3 Architectural & Invariant Implementation Plan: Dedicated Advanced Tuning Preferences Screen with Parameter Validation and Reset-to-Defaults

**Ticket**: `ATT-1304` / Subtask: `ATT-1633`  
**Date**: 2026-09-29  
**Status**: Completed  
**Author**: AI Agent 1 (Implementer)  
**Requirements**: `REQ-SET-073`  
**Test Cases**: `TST-SET-062`  

---

## 1. Executive Summary & Goals
This document outlines the detailed software construction architecture for implementing the **Advanced Tuning Preferences** screen and reactive parameter subsystem. It provides a modular, type-safe, and resilient architecture that decouples hardcoded tracking heuristics from service logic, exposes them via an isolated Material 3 configuration sheet, protects all inputs with defensive value clamping (`coerceIn`), and ensures instant recovery through atomic factory reset.

---

## 2. Architecture & Component Design (SWE.2)

```mermaid
graph TD
    subgraph UI Layer
        AND[AppNavigationDrawer.kt] -->|R.id.drawer_advanced_tuning| NR[NavRoutes.kt]
        DSD[DisplaySettingsDialog.kt] -->|SettingsBottomSheetType.ADVANCED_TUNING| NR
        NR --> ATD[AdvancedTuningDialog.kt]
    end

    subgraph Data & Persistence Layer
        ATD -->|Writes clamped params / reset| TPDS[TuningPreferencesDataStore.kt]
        TPDS -->|Encapsulates| DS[Context.dataStore (user_settings)]
        TPDS -->|Synchronous Bridge| JH[SettingsDataStoreJavaHelper.kt]
    end

    subgraph Domain & Active Services
        TPDS -->|Reactive Flow| BSC[BatterySaverController.kt]
        BSC -->|Configures| BSM[BatterySaverStateMachine.kt]
        TPDS -->|Reactive Flow| TTS[TrackingTabsScreen.kt]
        JH -->|getGpsAccuracyThreshold| SLD[SpeedAndLocationDevice.java]
        JH -->|getAltitudeFilterWindow & getSlopeMinSpeed| VSD[VerticalSpeedAndSlopeDevice.java]
    end
```

---

## 3. Data Layer Specification

### 3.1 `TuningPreferencesDataStore.kt`
Resides in `package com.atrainingtracker.trainingtracker.settings`.
Uses `context.dataStore` (`user_settings`).

Keys:
- `PREF_TUNING_BATTERY_SAVER_FULL_DIM` (`floatPreferencesKey("tuning_battery_saver_full_dim")`) -> default `0.25f`
- `PREF_TUNING_BATTERY_SAVER_MEDIUM_DIM` (`floatPreferencesKey("tuning_battery_saver_medium_dim")`) -> default `0.50f`
- `PREF_TUNING_BATTERY_SAVER_SLOPE_FLAT` (`floatPreferencesKey("tuning_battery_saver_slope_flat")`) -> default `2.0f`
- `PREF_TUNING_BATTERY_SAVER_SLOPE_STEEP` (`floatPreferencesKey("tuning_battery_saver_slope_steep")`) -> default `5.0f`
- `PREF_TUNING_BATTERY_SAVER_WAKEUP_SEC` (`intPreferencesKey("tuning_battery_saver_wakeup_sec")`) -> default `15`
- `PREF_TUNING_BATTERY_SAVER_DOWNWARD_DELAY_SEC` (`intPreferencesKey("tuning_battery_saver_downward_delay_sec")`) -> default `3`
- `PREF_TUNING_GPS_ACCURACY_THRESHOLD` (`floatPreferencesKey("tuning_gps_accuracy_threshold")`) -> default `200.0f`
- `PREF_TUNING_ALTITUDE_FILTER_WINDOW` (`intPreferencesKey("tuning_altitude_filter_window")`) -> default `21`
- `PREF_TUNING_SLOPE_MIN_SPEED` (`floatPreferencesKey("tuning_slope_min_speed")`) -> default `0.5f`

Data class `TuningConfig`:
```kotlin
data class TuningConfig(
    val fullDimFactor: Float = 0.25f,
    val mediumDimFactor: Float = 0.50f,
    val slopeFlatThreshold: Float = 2.0f,
    val slopeSteepThreshold: Float = 5.0f,
    val wakeupDurationSec: Int = 15,
    val downwardDelaySec: Int = 3,
    val gpsAccuracyThresholdMeters: Float = 200.0f,
    val altitudeFilterWindowSec: Int = 21,
    val slopeMinSpeedMps: Float = 0.5f
)
```

Methods:
- `tuningConfigFlow: Flow<TuningConfig>`
- `saveTuningConfig(config: TuningConfig)` with strict clamping:
  - `fullDim = config.fullDimFactor.coerceIn(0.05f, 0.50f)`
  - `mediumDim = config.mediumDimFactor.coerceIn(fullDim, 0.90f)`
  - `slopeFlat = config.slopeFlatThreshold.coerceIn(0.0f, 5.0f)`
  - `slopeSteep = config.slopeSteepThreshold.coerceIn(slopeFlat, 15.0f)`
  - `wakeup = config.wakeupDurationSec.coerceIn(5, 60)`
  - `downward = config.downwardDelaySec.coerceIn(1, 15)`
  - `gpsAccuracy = config.gpsAccuracyThresholdMeters.coerceIn(10.0f, 500.0f)`
  - `altitudeWindow = config.altitudeFilterWindowSec.coerceIn(5, 60)`
  - `slopeSpeed = config.slopeMinSpeedMps.coerceIn(0.2f, 2.0f)`
- `resetToDefaults()`: Atomically removes all tuning keys, restoring factory defaults in a single `edit` transaction.

### 3.2 Java Bridge (`SettingsDataStoreJavaHelper.kt`)
Add synchronous accessors for Java sensor drivers:
- `getGpsAccuracyThreshold(context: Context): Float`
- `getAltitudeFilterWindow(context: Context): Int`
- `getSlopeMinSpeed(context: Context): Float`

---

## 4. UI Layer Specification

### 4.1 Navigation Integration
1. `NavRoutes.kt`:
   - Add `SettingsBottomSheetType.ADVANCED_TUNING`.
   - Update `toBottomSheetType(itemId: Int)`: `R.id.drawer_advanced_tuning -> SettingsBottomSheetType.ADVANCED_TUNING`.
2. `AppNavigationDrawer.kt`:
   - Add `DrawerItemConfig(R.id.drawer_advanced_tuning, R.drawable.ic_tune, R.string.advanced_tuning_title)` to `drawer__settings` group.
3. `ATrainingTrackerApp.kt`:
   - Handle `SettingsBottomSheetType.ADVANCED_TUNING -> AdvancedTuningDialog(onDismiss = { drawerController.activeBottomSheet = null })`.
4. `DisplaySettingsDialog.kt`:
   - Add an action button/card row at the bottom of the power savings section navigating directly to `activeBottomSheet = SettingsBottomSheetType.ADVANCED_TUNING` (or callback `onNavigateToTuning`).

### 4.2 `AdvancedTuningDialog.kt`
Resides in `package com.atrainingtracker.trainingtracker.ui.settings.tuning`.
- Uses `AppBottomSheetContent` with `Icons.Default.Tune` and localized title `@string/advanced_tuning_title`.
- Advisory Warning Notice banner:
  - Warning card highlighting that parameters modify core heuristics and battery efficiency.
- Categorized sections:
  1. **AMOLED Battery Saver**:
     - Full Dimming Brightness: slider (5% to 50%) + formatted percentage label + helper text.
     - Medium Dimming Brightness: slider (20% to 90%) + formatted percentage label + helper text.
     - Flat Slope Threshold: slider (0.0% to 5.0%, step 0.5%) + helper text.
     - Steep Slope Threshold: slider (2.0% to 15.0%, step 0.5%) + helper text.
     - Wake-up Duration: slider (5s to 60s, step 1s) + helper text.
     - Downward Delay: slider (1s to 15s, step 1s) + helper text.
  2. **GPS & Location Filtering**:
     - Accuracy Rejection Threshold: slider (10m to 500m, step 10m) + helper text.
  3. **Elevation & Gradient Dynamics**:
     - Altitude Filter Window: slider (5s to 60s, step 1s) + helper text.
     - Slope Minimum Speed: slider (0.2 m/s to 2.0 m/s, step 0.1 m/s) + helper text.
- Action Buttons (`AppDialogActions`):
  - Prominent "Auf Werkseinstellungen zurücksetzen" (Reset to Defaults) button with confirmation / instant reset.
  - "Speichern" (Save) and "Abbrechen" (Cancel).

---

## 5. Domain & Sensor Driver Integration

### 5.1 `BatterySaverController.kt` & `BatterySaverStateMachine.kt`
- Update `BatterySaverStateMachine` to support `BatterySaverTuningConfig` (dimming factors, slope thresholds, hysteresis ms).
- Dynamic update method: `updateTuningConfig(config: BatterySaverTuningConfig)`.
- `BatterySaverController`: updates `wakeupDurationMs` and state machine config when observed from `TuningPreferencesDataStore`.

### 5.2 `SpeedAndLocationDevice.java`
- In `onNewLocation(Location location)`:
  - Replace static `ACCURACY_THRESHOLD` with dynamic check:
    `double accuracyThreshold = SettingsDataStoreJavaHelper.getGpsAccuracyThreshold(mContext);`
    `if (location.getAccuracy() <= accuracyThreshold) { ... }`

### 5.3 `VerticalSpeedAndSlopeDevice.java` & `TrackingTabsScreen.kt`
- In `VerticalSpeedAndSlopeDevice.java`:
  - Dynamically read `minSpeed`: `double minSpeed = SettingsDataStoreJavaHelper.getSlopeMinSpeed(mContext);`
- In `TrackingTabsScreen.kt`:
  - Observe `TuningPreferencesDataStore.tuningConfigFlow` and use `config.slopeMinSpeedMps` instead of hardcoded `0.5`.

---

## 6. Detailed Step-by-Step Implementation Sequence

1. **Step 1: Resource Assets & Localization**:
   - Add `ic_tune.xml` drawable asset (Material 3 tune icon).
   - Add resource ID `drawer_advanced_tuning` in `ids.xml` (if needed) or rely on `R.id`.
   - Add localized string keys in all 9 languages (`values*/strings.xml`):
     - `advanced_tuning_title`, `advanced_tuning_warning_title`, `advanced_tuning_warning_desc`,
     - Category headers: `tuning_cat_battery_saver`, `tuning_cat_gps`, `tuning_cat_elevation`,
     - Sliders: `tuning_full_dim_factor`, `tuning_medium_dim_factor`, `tuning_slope_flat`, `tuning_slope_steep`, `tuning_wakeup_duration`, `tuning_downward_delay`, `tuning_gps_accuracy`, `tuning_altitude_window`, `tuning_slope_min_speed`,
     - Helper descriptions and default tags: `tuning_default_format`, `reset_to_defaults`, `reset_to_defaults_success`.
2. **Step 2: DataStore & Java Bridge**:
   - Implement `TuningPreferencesDataStore.kt`.
   - Implement bridge methods in `SettingsDataStoreJavaHelper.kt`.
3. **Step 3: Domain & Sensor Driver Updates**:
   - Update `BatterySaverModel.kt`, `BatterySaverStateMachine.kt`, and `BatterySaverController.kt`.
   - Update `SpeedAndLocationDevice.java` to use dynamic accuracy threshold.
   - Update `VerticalSpeedAndSlopeDevice.java` and `TrackingTabsScreen.kt` to use dynamic min speed.
4. **Step 4: UI & Navigation**:
   - Add `SettingsBottomSheetType.ADVANCED_TUNING` in `NavRoutes.kt`.
   - Add drawer item in `AppNavigationDrawer.kt`.
   - Create `AdvancedTuningDialog.kt`.
   - Connect bottom sheet in `ATrainingTrackerApp.kt`.
   - Add entry button in `DisplaySettingsDialog.kt`.
5. **Step 5: Testing & Verification**:
   - Author `TuningPreferencesDataStoreTest.kt`.
   - Author `BatterySaverDynamicTuningTest.kt`.
   - Author `AdvancedTuningNavigationTest.kt`.
   - Run full unit tests via `./gradlew testDebugUnitTest`.
   - Update living docs to `Verified`.

---

## 7. Invariants & Safety Measures
- **Brightness Safety Floor**: Under no circumstances can full dimming drop below 5% (`0.05f`), guaranteeing screen readability.
- **Inverted Threshold Prevention**: Medium dimming factor is enforced $\ge$ full dimming factor. Steep slope cutoff is enforced $\ge$ flat slope cutoff.
- **Division-by-Zero Guard**: Minimum speed for slope is enforced $\ge 0.2\text{ m/s}$.
- **GPS Fix Availability Guard**: GPS accuracy threshold cannot be set lower than 10 meters, preventing the dropping of valid satellite fixes.
- **Atomic Recovery**: Tapping "Reset to Defaults" clears overrides in a single atomic transaction.
- **100% 9-Language Parity**: Absolute string resource parity across EN, DE, FR, ES, IT, JA, NL, PL, and PT.
