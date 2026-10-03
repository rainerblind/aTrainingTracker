# Architectural & Problem Analysis - ATT-1276: [Cockpit] Intuitive smoothing presets and simplified filter configuration

## 1. Executive Summary & Problem Statement

* **Issue Key**: `ATT-1276` / `ATT-1395`
* **Sub-tasks**: `ATT-1395` (Analysis), `[Test-Spec]`, `[Impl-Plan]`, `[Implementation]`, `[Test]`
* **Parent Issue**: `ATT-1276` (*[Verbesserung] [Cockpit] Intuitive smoothing presets and simplified filter configuration*)
* **Epic**: `ATT-754` (*Filtering*)
* **Target Version**: `V4.9.38` (Sprint `2026-39.3`)
* **Associated Requirements**: `REQ-UI-149` (Preserved), `REQ-UI-150` (Preserved), `REQ-UI-167` (Intuitive Smoothing Presets, Simplified Filter Configuration & Smart Athletic Defaults)
* **Associated Verification**: `TST-UI-119` (Intuitive Smoothing Presets, Smart Defaults & Filter Dialog Verification)

### Problem Statement
In `aTrainingTracker`, sensor telemetry displayed on the live workout tracking grid can be smoothed using digital signal filters (implemented in `com.atrainingtracker.banalservice.filters`: `InstantaneousFilter`, `AverageFilter`, `TimedMovingAverageFilter`, `NumberedMovingAverageFilter`, `ExponentialSmoothingFilter`, `MaxValueFilter`).

Currently, configuring sensor value smoothing in `ConfigureFilterDialog.kt` exposes raw technical controls:
1. **Mathematical Jargon & Cognitive Friction**: Users must understand raw DSP terminology (selecting between `MOVING_AVERAGE_TIME`, `MOVING_AVERAGE_NUMBER`, `EXPONENTIAL_SMOOTHING`), type raw numbers into a text field, and select units from a dropdown ("sec", "min", "samples").
2. **Cumbersome Common Use Cases**: For standard athletic training scenarios—such as applying 3-second or 10-second power smoothing for cycling—the user must open the dialog, select "moving average", type "3", select "sec", and tap save. For athletes on the move or setting up tracking views, this is slow and error-prone.
3. **Missing Athletic Defaults**: When adding a new sensor field or changing the sensor type in `EditSensorFieldViewModel`, the filter is unconditionally defaulted to `FilterType.INSTANTANEOUS` (1.0). For cycling power (`SensorType.POWER`), instantaneous 1-second values oscillate wildly with pedal strokes (e.g., jumping between 150W and 280W during steady cadence), frustrating athletes who expect the industry-standard 3-second average default (as found on Garmin Edge, Wahoo ELEMNT, and Hammerhead Karoo).
4. **Visual & Ergonomic Outlier**: While `ConfigureFilterDialog` adheres to `AppModalBottomSheet` (`REQ-UI-149`) and `AppDialogActions.SaveCancel` (`REQ-UI-150`), its internal layout is an austere stack of dropdowns and text fields without tactile, modern selection chips or visual hierarchy.

---

## 2. Problem Domain & Scope Analysis

### 2.1 Athletic Training Use Cases & Presets
Athletes do not think in mathematical filter algorithms; they think in athletic pacing windows:
* **Direct / Instantaneous (1s / Raw)**: Essential for immediate responsiveness, e.g. instantaneous heart rate, cadence, or speed.
* **3 Seconds (`MOVING_AVERAGE_TIME`, 3s)**: The international cycling industry standard for wattage (`POWER`). Eliminates micro-torque dead spots per pedal stroke revolution while maintaining rapid response to sprints and cadence surges.
* **10 Seconds (`MOVING_AVERAGE_TIME`, 10s)**: The benchmark for steady-state pacing, threshold intervals, and sustained hill climbs.
* **30 Seconds (`MOVING_AVERAGE_TIME`, 30s)**: Used for endurance pacing, long-term trends, and serving as a real-time proxy for Normalized Power.
* **Session Average (Ø) (`AVERAGE`)**: Displays the cumulative mean of the entire active workout session.
* **Session Max (`MAX_VALUE`)**: Displays the peak sensor reading achieved during the session.
* **Custom / Expert (`CUSTOM`)**: Preserves full access to granular mathematical configurations: arbitrary time windows (seconds/minutes), sample counts (`MOVING_AVERAGE_NUMBER`), and exponential smoothing factor ($\alpha \in (0, 1]$).

### 2.2 Smart Athletic Defaults
* When an athlete configures a field with `SensorType.POWER`:
  - Default filter MUST automatically be set to **3-second moving average** (`FilterType.MOVING_AVERAGE_TIME`, constant `3.0`, unit `"sec"`).
* When default cockpit views are initialized in `TrackingViewsDatabaseManager` for `ActivityType.BIKE_POWER`:
  - `SensorType.POWER` rows MUST default to `FilterType.MOVING_AVERAGE_TIME` with `filterConstant = 3`.
* For other sensors (`HR`, `SPEED_mps`, `CADENCE`, `ALTITUDE`, `TEMPERATURE`), the system safely retains `FilterType.INSTANTANEOUS` (constant `1.0`).

---

## 3. Target UI/UX Architecture & Specifications

### 3.1 Modernized `ConfigureFilterDialog`
The bottom sheet layout will be structured into two ergonomic zones:
1. **Header & Context**:
   - `AppModalBottomSheet` with title `R.string.filter_configure_smoothing`, leading icon `Icons.Default.FilterAlt`, drag handle, and insets.
2. **Quick Presets Grid (1-Tap Selection)**:
   - A modern 2-column or wrapped flow grid of selectable `FilterChip` / `InputChip` composables:
     - `Direkt (1s)` (`filter_preset_direct`)
     - `3 Sek (Power)` (`filter_preset_3s`)
     - `10 Sek (Pacing)` (`filter_preset_10s`)
     - `30 Sek (Ausdauer)` (`filter_preset_30s`)
     - `Durchschnitt Ø` (`filter_preset_avg`)
     - `Maximalwert` (`filter_preset_max`)
     - `Manuell / Experte` (`filter_preset_custom`)
   - Tapping any preset instantly updates the active filter configuration, highlights the chip with `primaryContainer` / `onPrimaryContainer` styling, and updates the explanatory details card.
3. **Explanatory Details Surface**:
   - A Material 3 `Surface` / `Card` with subtle outline displaying the formatted filter summary (`filterType.getSummary(...)`) and detailed athletic guidance (`filterType.getDetails(...)`).
4. **Expandable Custom / Expert Controls**:
   - When "Manuell / Experte" is selected, the advanced controls smoothly expand (animated or conditional layout):
     - `FilterTypeSpinner`: Dropdown selector for raw `FilterType`.
     - Dynamic numeric inputs: `OutlinedTextField` for constant (integer seconds/samples, or decimal $\alpha$).
     - `UnitSpinner`: Dropdown selector for units (`sec`, `min`, `samples`).
5. **Action Bar**:
   - Standard `AppDialogActions.SaveCancel` with "Abbrechen" (`R.string.Cancel`) and "Speichern" (`R.string.save`), conforming strictly to `REQ-UI-150`.

### 3.2 ViewModel State Machine & Preset Mapping
In `EditSensorFieldViewModel`:
- Introduce an enum `FilterPreset` representing the preset states:
  ```kotlin
  enum class FilterPreset {
      DIRECT,
      SMOOTH_3S,
      SMOOTH_10S,
      SMOOTH_30S,
      SESSION_AVG,
      SESSION_MAX,
      CUSTOM
  }
  ```
- Add helper method `resolvePreset(filterType: FilterType, constant: Double, unit: String): FilterPreset` to dynamically identify which preset matches the loaded or edited configuration.
- Add `onPresetSelected(preset: FilterPreset)` to instantly apply preset parameters to `uiState`.

---

## 4. Call Site Audit & Affected Components

| Component / File | Purpose of Modification |
|:---|:---|
| [ConfigureFilterDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/ConfigureFilterDialog.kt) | Modernize layout with Quick Preset chips, active preset highlight, explanatory card, and expandable expert settings. Add Compose `@Preview` (light/dark). |
| [EditSensorFieldViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldViewModel.kt) | Introduce `FilterPreset` enum and mapping; implement `onPresetSelected`; implement smart athletic default (3s smoothing for `SensorType.POWER` on new field or sensor type change). |
| [TrackingViewsDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManager.java) | Initialize `SensorType.POWER` with `FilterType.MOVING_AVERAGE_TIME` (constant 3) in default bike power cockpit views. |
| [strings_filters.xml (all 9 locales)](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values/strings_filters.xml) | Add localized strings for preset chip labels and custom section headers across EN, DE, ES, FR, IT, JA, NL, PL, PT. |
| [ConfigureFilterDialogTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/ConfigureFilterDialogTest.kt) | New unit test verifying preset resolution, smart power defaults, and state transitions. |
| [ModalBottomSheetDialogsIntegrityTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/ModalBottomSheetDialogsIntegrityTest.kt) | Verify reflection contracts for `ConfigureFilterDialog` remain satisfied. |

---

## 5. Requirement Traceability & Preserved System Invariants

### 5.1 Living Documentation Mapping
* **New Requirement**: `REQ-UI-167`: Intuitive Smoothing Presets, Simplified Filter Configuration & Smart Athletic Defaults.
* **New Test Specification**: `TST-UI-119`: Intuitive Smoothing Presets, Smart Defaults & Filter Dialog Verification.

### 5.2 Preservation of Core Invariants
1. **Modal Bottom Sheet Contract (`REQ-UI-149`)**: `ConfigureFilterDialog` MUST continue to compose `AppModalBottomSheet` with `FilterAlt` icon and edge-to-edge system insets (`navigationBarsPadding()`).
2. **Semantic Action Verbs & Save Integrity (`REQ-UI-150`)**: The primary button MUST remain "Speichern" (`R.string.save`), and uncommitted changes MUST be safely discarded if the user dismisses the dialog.
3. **Filter Engine Mathematical Integrity**: Underlying `BANALService` filter implementations (`InstantaneousFilter`, `AverageFilter`, `TimedMovingAverageFilter`, `NumberedMovingAverageFilter`, `ExponentialSmoothingFilter`, `MaxValueFilter`) and SQLite schema (`ROWS_TABLE.FILTER_TYPE`, `FILTER_CONSTANT`) MUST remain 100% backward-compatible.
4. **9-Language Localization Parity (`REQ-UI-106`)**: All preset titles and headers MUST be externalized across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 6. Risk Assessment & Independent Gate 1 Review Recommendation

* **Risk Rating**: **`LOW`**
  - Scope is strictly presentation and ViewModel default heuristics.
  - Zero database schema migrations or low-level signal processing modifications.
  - Full backward compatibility with existing saved sensor field configurations in SQLite.
* **Recommendation**: **`RECOMMEND PASS`**
