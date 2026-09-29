# Stage 2 Requirement & Test Specification: Dedicated Advanced Tuning Preferences Screen with Parameter Validation and Reset-to-Defaults

**Ticket**: `ATT-1304` / Subtask: `ATT-1632`  
**Date**: 2026-09-29  
**Status**: Completed  
**Author**: AI Agent 1 (Implementer)  
**Requirements**: `REQ-SET-073`  
**Test Cases**: `TST-SET-062`  

---

## 1. Specification Overview
This document specifies the formal functional requirements, safety bounds, state invariants, user experience guidelines, and verification tests for the dedicated **Advanced Tuning Preferences** configuration screen and reactive tuning subsystem.

The goal is to allow athletes, hardware developers, and field testers to inspect and calibrate key tracking heuristics and battery saving behavior without recompiling the application, backed by ironclad clamping invariants (`coerceIn`) and instant one-tap factory reset recovery.

---

## 2. Requirement Details (`REQ-SET-073`)

### 2.1 Navigation & Presentation Hierarchy
1. **Drawer Navigation**:
   - `AppNavigationDrawer.kt`: Add `R.id.drawer_advanced_tuning` under `DrawerGroup(R.string.drawer__settings)`.
   - `NavRoutes.kt`: Map `R.id.drawer_advanced_tuning` to `SettingsBottomSheetType.ADVANCED_TUNING`.
   - Title: `@string/advanced_tuning_title` ("Advanced Tuning" / "Erweiterte Abstimmung").
   - Leading Icon: `R.drawable.ic_tune` (Material 3 tune icon).
2. **Display Settings Link**:
   - In `DisplaySettingsDialog.kt`, render a dedicated navigation entry under the power savings category (`settings_category_savings`) linking directly to `ADVANCED_TUNING`.
3. **Advisory Notice Banner**:
   - Top of `AdvancedTuningDialog.kt`: Prominent Material 3 `Surface` / `Card` with warning styling (`colorScheme.errorContainer` or warning accent).
   - Localized text warning that these parameters modify low-level tracking algorithms and battery longevity, accompanied by a reminder that defaults can be restored at any time.

### 2.2 Parameter Definitions, Data Types & Clamping Bounds

| Parameter Key | Type | Default | Min (Floor) | Max (Ceiling) | Step / Unit | Invariant & Clamping Rules |
|---|---|---|---|---|---|---|
| `BATTERY_SAVER_FULL_DIM_FACTOR` | `Float` | `0.25f` (25%) | `0.05f` (5%) | `0.50f` (50%) | 1% | `fullDimFactor >= 0.05f` (safety floor against blackout); `fullDimFactor <= mediumDimFactor` |
| `BATTERY_SAVER_MEDIUM_DIM_FACTOR` | `Float` | `0.50f` (50%) | `0.20f` (20%) | `0.90f` (90%) | 1% | `mediumDimFactor >= fullDimFactor`; `mediumDimFactor <= 0.90f` |
| `BATTERY_SAVER_SLOPE_FLAT` | `Float` | `2.0f` (2.0%) | `0.0f` (0%) | `5.0f` (5%) | 0.5% | `slopeFlat <= slopeSteep` |
| `BATTERY_SAVER_SLOPE_STEEP` | `Float` | `5.0f` (5.0%) | `2.0f` (2%) | `15.0f` (15%) | 0.5% | `slopeSteep >= slopeFlat` |
| `BATTERY_SAVER_WAKEUP_DURATION_S` | `Int` | `15` s | `5` s | `60` s | 1 s | `duration in 5..60` |
| `BATTERY_SAVER_DOWNWARD_DELAY_S` | `Int` | `3` s | `1` s | `15` s | 1 s | `delay in 1..15` |
| `GPS_ACCURACY_THRESHOLD_M` | `Float` | `200.0f` m | `10.0f` m | `500.0f` m | 10 m | `accuracyThreshold in 10.0f..500.0f` |
| `ALTITUDE_FILTER_WINDOW_S` | `Int` | `21` s | `5` s | `60` s | 1 s | `window in 5..60` |
| `SLOPE_MIN_SPEED_MPS` | `Float` | `0.5f` m/s | `0.2f` m/s | `2.0f` m/s | 0.1 m/s | `minSpeed >= 0.2f` (prevents division by zero) |

### 2.3 Single-Tap Factory Reset
- A single tap on "Reset to Defaults" (`@string/reset_to_defaults` / "Auf Werkseinstellungen zurücksetzen"):
  1. Atomatically clears customized keys or resets them to factory defaults in a single atomic `dataStore.edit { ... }` block.
  2. Immediately updates the UI slider positions.
  3. Displays user feedback (e.g. Toast or Snackbar: "Einstellungen auf Werkseinstellungen zurückgesetzt").

### 2.4 9-Language Localization Scope
All newly introduced UI strings (titles, category headers, warnings, slider labels, helper texts, and button actions) MUST achieve 100% translation coverage across:
- `values/strings.xml` (English - default)
- `values-de/strings.xml` (German)
- `values-fr/strings.xml` (French)
- `values-es/strings.xml` (Spanish)
- `values-it/strings.xml` (Italian)
- `values-ja/strings.xml` (Japanese)
- `values-nl/strings.xml` (Dutch)
- `values-pl/strings.xml` (Polish)
- `values-pt/strings.xml` (Portuguese)

---

## 3. Test Cases Specification (`TST-SET-062`)

### TST-SET-062-1: DataStore Defaults & Reactive Flows
- **Given**: A freshly initialized `TuningPreferencesDataStore`.
- **When**: Collecting initial emissions from each parameter `Flow`.
- **Then**:
  - `batterySaverFullDimFactorFlow` emits `0.25f`.
  - `batterySaverMediumDimFactorFlow` emits `0.50f`.
  - `batterySaverSlopeFlatFlow` emits `2.0f`.
  - `batterySaverSlopeSteepFlow` emits `5.0f`.
  - `batterySaverWakeupDurationSecFlow` emits `15`.
  - `batterySaverDownwardDelaySecFlow` emits `3`.
  - `gpsAccuracyThresholdFlow` emits `200.0f`.
  - `altitudeFilterWindowSecFlow` emits `21`.
  - `slopeMinSpeedMpsFlow` emits `0.5f`.

### TST-SET-062-2: Strict Value Clamping & Invariant Protection
- **Given**: `TuningPreferencesDataStore`.
- **When**: Writing extreme out-of-bounds values:
  - Full Dimming factor = `-0.50f` or `0.80f`.
  - Medium Dimming factor = `0.10f` or `1.50f`.
  - Flat Slope = `-5.0f` or `10.0f`.
  - Steep Slope = `1.0f` or `30.0f`.
  - GPS Accuracy = `2.0f` or `1000.0f`.
  - Min Speed for Slope = `0.0f` or `10.0f`.
- **Then**:
  - Full Dimming factor is clamped to `0.05f` and `0.50f`.
  - Medium Dimming factor is clamped to `0.20f` and `0.90f` and cannot be less than full dimming.
  - Slope thresholds are clamped within bounds and flat $\le$ steep.
  - GPS Accuracy is clamped to `10.0f` and `500.0f`.
  - Min speed is clamped to `0.2f` (preventing division by zero) and `2.0f`.

### TST-SET-062-3: Atomic Reset to Factory Defaults
- **Given**: `TuningPreferencesDataStore` with multiple non-default values saved.
- **When**: `resetToDefaults()` is executed.
- **Then**: All parameter flows immediately emit their exact factory default values.

### TST-SET-062-4: BatterySaver Dynamic Reconfiguration
- **Given**: `BatterySaverStateMachine` configured with custom thresholds.
- **When**: Telemetry is evaluated under various slopes and zones:
  - Telemetry with slope 3% evaluates to `MEDIUM_DIM` under default thresholds (2%..5%).
  - Flat threshold is tuned to 4%: slope 3% evaluates to `FULL_DIM`.
- **Then**: State machine responds dynamically to tuned thresholds without class re-creation.

### TST-SET-062-5: Navigation & Dialog Interaction Contract
- **Given**: `NavRoutes.toSettingsBottomSheetType(R.id.drawer_advanced_tuning)`.
- **When**: Evaluated.
- **Then**: Returns `SettingsBottomSheetType.ADVANCED_TUNING`.
- **Given**: `createDrawerGroups` in `AppNavigationDrawer.kt`.
- **When**: Drawer items are enumerated.
- **Then**: `R.id.drawer_advanced_tuning` is present under `drawer__settings`.
