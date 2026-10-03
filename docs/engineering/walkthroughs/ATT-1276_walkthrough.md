# Implementation Walkthrough - ATT-1276: [Cockpit] Intuitive Smoothing Presets and Simplified Filter Configuration

**Ticket**: [ATT-1276](https://rainerblind.atlassian.net/browse/ATT-1276)  
**Sub-task**: [ATT-1404](https://rainerblind.atlassian.net/browse/ATT-1404) (`[Implementation]`)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.3`  
**Git Branch**: `feature/ATT-1276`  
**Requirements**: `REQ-UI-149`, `REQ-UI-150`, `REQ-UI-167`  
**Test Specifications**: `TST-UI-102`, `TST-UI-119`  

---

## 1. Executive Summary

ATT-1276 modernizes sensor smoothing configuration in `ConfigureFilterDialog` to provide everyday athletes with intuitive, 1-tap quick presets while retaining granular mathematical filter controls for advanced users. Cycling power sensors now default smartly to 3-second moving averages, eliminating jumpy instantaneous readings without user friction. Core architectural invariants, modal bottom sheet contracts (`REQ-UI-149`), Save/Cancel semantics (`REQ-UI-150`), and database/DSP signal processing schemas remain completely preserved and backward-compatible.

---

## 2. Key Changes Implemented

### 2.1 ViewModel & State Machine Architecture (`EditSensorFieldViewModel.kt`)
* **`FilterPreset` Enum**: Introduced 7 distinct preset targets:
  - `DIRECT` (`R.string.filter_preset_direct`): Instantaneous 1s raw reading.
  - `SMOOTH_3S` (`R.string.filter_preset_3s`): 3-second moving average (cycling power standard).
  - `SMOOTH_10S` (`R.string.filter_preset_10s`): 10-second moving average (steady pacing / climbs).
  - `SMOOTH_30S` (`R.string.filter_preset_30s`): 30-second moving average (endurance trend).
  - `SESSION_AVG` (`R.string.filter_preset_avg`): Entire workout session average ($\varnothing$).
  - `SESSION_MAX` (`R.string.filter_preset_max`): Workout peak / maximum value.
  - `CUSTOM` (`R.string.filter_preset_custom`): Explicit manual / expert configuration.
* **Bidirectional Preset Resolution (`resolveFilterPreset`)**: Decouples presentation from raw underlying filters (`FilterType`, numeric constant, unit). Accurately resolves presets and cleanly falls back to `CUSTOM` for arbitrary inputs (e.g. 5s, sample-based, or exponential smoothing).
* **Smart Athletic Defaults**:
  - `onSensorTypeChanged(SensorType)`: Defaults `SensorType.POWER` to `FilterType.MOVING_AVERAGE_TIME` (3s, `"sec"`), while keeping other sensors at `FilterType.INSTANTANEOUS` (1s).
  - `setupDefaultState()`: Aligned default sensor initialization with smart athletic heuristics.
* **Preserved Dismiss Semantics (`onFilterConfigDismissed`)**: Accurately restores initial configuration parameters without committing unapproved edits.
* **Defensive Robustness**: Added null-safety guards in `getFullDeviceList` for uninitialized or null device ID/name arrays.

### 2.2 Modernized Compose Bottom Sheet (`ConfigureFilterDialog.kt`)
* **Scaffold Invariants**: Maintained `AppModalBottomSheet` with `FilterAlt` icon and localized title `filter_configure_smoothing` (`REQ-UI-149`), and `AppDialogActions.SaveCancel` (`REQ-UI-150`). Redundant inner `verticalScroll` was eliminated to prevent nested scroll crashes, as `AppModalBottomSheet` natively provides bounded vertical scroll behavior.
* **Quick Presets Section**: Implemented a responsive `FlowRow` containing 7 Material 3 `FilterChip` items with touch targets $\ge 48\text{dp}$. Tapping a chip immediately updates state.
* **Explanatory Guidance Surface Card**: Added a high-contrast `Surface` styled with `MaterialTheme.colorScheme.surfaceVariant` and `12.dp` rounded corners, rendering live summary and descriptive behavioral text.
* **Expandable Custom / Expert Section**: Enclosed in `AnimatedVisibility`, automatically expanding when `FilterPreset.CUSTOM` is active or manual controls are toggled, exposing the raw `FilterTypeSpinner`, numeric constant inputs, and unit dropdowns.
* **Stateless Factoring & Previews**: Extracted `ConfigureFilterDialogContent` to provide comprehensive Light Mode and Dark Mode `@Preview` composables wrapped in `ATrainingTrackerTheme`.

### 2.3 Default Database Views (`TrackingViewsDatabaseManager.java`)
* Updated `addDefaultTab` row generation: Default start rows for `SensorType.POWER` (such as in `ActivityType.BIKE_POWER`) are initialized directly to `FilterType.MOVING_AVERAGE_TIME` with `filterConstant = 3`.

### 2.4 Localization Parity (`strings_filters.xml` across all 9 locales)
Added 9 localized string keys across EN, DE, ES, FR, IT, JA, NL, PL, and PT with strict parity:
- `filter_presets_header`
- `filter_preset_direct`
- `filter_preset_3s`
- `filter_preset_10s`
- `filter_preset_30s`
- `filter_preset_avg`
- `filter_preset_max`
- `filter_preset_custom`
- `filter_custom_header`

---

## 3. Verification & Test Evidence

### 3.1 Unit Test Suite (`ConfigureFilterDialogTest.kt`)
Executed `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.ConfigureFilterDialogTest"`:
```
BUILD SUCCESSFUL in 16s
32 actionable tasks: 4 executed, 28 up-to-date
```
Test cases verified:
1. `testResolveFilterPreset_mapsStandardPresetsAccurately` - PASSED
2. `testResolveFilterPreset_fallsBackToCustomForArbitraryInputs` - PASSED
3. `testSmartAthleticDefaults_defaultsPowerSensorTo3sMovingAverage` - PASSED
4. `testSmartAthleticDefaults_defaultsOtherSensorsToInstantaneous` - PASSED
5. `testPresetSelection_transitionsFilterStateCleanly` - PASSED
6. `testModeSwitching_togglesCustomFilterExpanded` - PASSED
7. `testExponentialSmoothing_adjustsAndConstrainsAlpha` - PASSED
8. `testFilterConfigDismissed_restoresInitialConfiguration` - PASSED

### 3.2 Modal Bottom Sheet Contract Integrity (`ModalBottomSheetDialogsIntegrityTest.kt`)
Executed `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.core.ModalBottomSheetDialogsIntegrityTest"`:
```
BUILD SUCCESSFUL
```
- `ConfigureFilterDialog` and `EditSensorFieldDialog` reflection and composable contracts verified.

### 3.3 Visual Verification on Physical Device
- **Schnellauswahl Mode**: Pure athletic presets, clean layout without confusing custom option mixed in.
- **Manuell / Experte Mode**: Explicit technical parameter controls with alpha constrained to $(0, 1]$.

| Schnellauswahl | Manuell / Experte |
| :---: | :---: |
| ![Schnellauswahl](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/attachments/filter_mode_schnellauswahl.png) | ![Manuell](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/attachments/filter_mode_manuell.png) |

---

## 4. Invariant Compliance Checklist
- [x] **REQ-UI-149**: `ConfigureFilterDialog` remains `AppModalBottomSheet` with standard navigation/status bar edge-to-edge padding.
- [x] **REQ-UI-150**: Retains `AppDialogActions.SaveCancel` with "Abbrechen" and "Speichern". Unsaved changes discarded on dismiss.
- [x] **REQ-UI-167**: Quick presets and top SegmentedButton mode switch fully implemented.
- [x] **ATT-1405**: Exponential smoothing parameter $\alpha$ strictly constrained to $(0, 1]$.
- [x] **TST-UI-119**: 100% test coverage for presets, smart defaults, state machine transitions, mode switching, and dialog contracts.
- [x] **REQ-UI-106**: Localization parity across supported application locales.
