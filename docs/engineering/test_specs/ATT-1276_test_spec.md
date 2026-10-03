# Test Specification & Requirement Synchronization - ATT-1276: [Cockpit] Intuitive smoothing presets and simplified filter configuration

## 1. Feature / Bug Overview & Test Scope

* **Issue Key**: `ATT-1276` / `ATT-1397`
* **Sub-tasks**: `ATT-1395` (Analysis [Erledigt]), `ATT-1397` (Test Spec [In Bearbeitung]), `[Impl-Plan]`, `[Implementation]`, `[Test]`
* **Parent Issue**: `ATT-1276` (*[Verbesserung] [Cockpit] Intuitive smoothing presets and simplified filter configuration*)
* **Epic**: `ATT-754` (*Filtering*)
* **Target Version**: `V4.9.38` (Sprint `2026-39.3`)
* **Related Requirements**: `REQ-UI-149` (Preserved), `REQ-UI-150` (Preserved), `REQ-UI-167` (Intuitive Smoothing Presets, Simplified Filter Configuration & Smart Athletic Defaults)
* **Related Tests**: `TST-UI-102` (Preserved), `TST-UI-103` (Preserved), `TST-UI-119` (Intuitive Smoothing Presets, Smart Defaults & Filter Dialog Verification)

### Objective
Streamline and modernize sensor smoothing filter configuration in the tracking cockpit. Replace cognitive DSP friction with 1-tap quick presets (*Direct 1s*, *3s Power*, *10s Pacing*, *30s Endurance*, *Session Avg Ø*, *Session Max*, and *Custom/Expert*), introduce smart athletic defaults (defaulting cycling power `SensorType.POWER` to 3-second moving average), and modernize `ConfigureFilterDialog` into a tactile Material 3 layout with live explanatory guidance, while strictly preserving full mathematical control in custom mode, `AppModalBottomSheet` contracts (`REQ-UI-149`), and `SaveCancel` semantics (`REQ-UI-150`).

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: `REQ-UI-149` (item 3: *ConfigureFilterDialog.kt SHALL compose AppModalBottomSheet displaying smoothing filter parameters with save and cancel actions*) and `REQ-UI-150` (item 2: *ConfigureFilterDialog.kt: Primary button SHALL display @string/save*).
2. **Historical Origin & Commit Trace**: Introduced in tickets `ATT-900` / `ATT-1029` (commit `69e4f1a2` / `e971cf62`) and standardized in `ATT-1034` (commit `f849622d`).
3. **Root Reason for Existing Formulation**: In `ATT-900` and `ATT-1034`, the objective was structural migration—converting legacy XML `ConfigureFilterDialogFragment` into a Jetpack Compose `AppModalBottomSheet` and standardizing button verbs from "OK" to "Speichern" (`R.string.save`). The internal form body was ported as a minimal 1-to-1 representation of the legacy dropdowns (`FilterType`, numeric field, unit spinner) without athletic domain optimizations.
4. **Preservation of Core Invariants**: `ConfigureFilterDialog` strictly retains `AppModalBottomSheet` with `FilterAlt` icon and system window insets (`REQ-UI-149`), strictly retains `AppDialogActions.SaveCancel` with primary action "Speichern" (`REQ-UI-150`), preserves uncommitted change discarding on dismiss, preserves all underlying BANALService filter algorithms (`InstantaneousFilter`, `AverageFilter`, `TimedMovingAverageFilter`, `NumberedMovingAverageFilter`, `ExponentialSmoothingFilter`, `MaxValueFilter`), and preserves SQLite schema compatibility (`ROWS_TABLE.FILTER_TYPE`, `FILTER_CONSTANT`).

---

## 2. Harmonized Requirement Specification (`REQ-UI-167`)

### REQ-UI-167: Intuitive Smoothing Presets, Simplified Filter Configuration & Smart Athletic Defaults
The system SHALL provide an intuitive, 1-tap quick preset selection interface and smart athletic defaults for sensor telemetry smoothing across tracking cockpit views (ATT-1276):

1. **Smart Athletic Defaults**:
   - When a sensor field is created or configured with `SensorType.POWER` in `EditSensorFieldViewModel`, the system SHALL default the filter type to 3-second moving average (`FilterType.MOVING_AVERAGE_TIME`, constant `3.0`, unit `"sec"`), preventing erratic 1-second raw fluctuations during workouts.
   - In `TrackingViewsDatabaseManager`, when generating default cockpit views for bike power (`ActivityType.BIKE_POWER`), the system SHALL initialize `SensorType.POWER` rows with `FilterType.MOVING_AVERAGE_TIME` and `filterConstant = 3`.
   - For all other sensor types (`HR`, `SPEED_mps`, `CADENCE`, `ALTITUDE`, `TEMPERATURE`), the system SHALL default to `FilterType.INSTANTANEOUS` (constant `1.0`).

2. **Intuitive Quick Presets in ConfigureFilterDialog**:
   - In `ConfigureFilterDialog.kt`, the system SHALL present a dedicated 1-tap preset selection group comprising 7 distinct choices:
     - *Direct / Instantaneous (1s)*: `FilterType.INSTANTANEOUS`, constant `1.0` (`@string/filter_preset_direct`).
     - *3 Seconds (Power)*: `FilterType.MOVING_AVERAGE_TIME`, constant `3.0`, unit `"sec"` (`@string/filter_preset_3s`).
     - *10 Seconds (Pacing)*: `FilterType.MOVING_AVERAGE_TIME`, constant `10.0`, unit `"sec"` (`@string/filter_preset_10s`).
     - *30 Seconds (Endurance)*: `FilterType.MOVING_AVERAGE_TIME`, constant `30.0`, unit `"sec"` (`@string/filter_preset_30s`).
     - *Session Average (Ø)*: `FilterType.AVERAGE`, constant `1.0` (`@string/filter_preset_avg`).
     - *Session Max*: `FilterType.MAX_VALUE`, constant `1.0` (`@string/filter_preset_max`).
     - *Custom / Expert*: Granular manual configuration (`@string/filter_preset_custom`).
   - The active preset SHALL be highlighted dynamically based on the current filter configuration.
   - Selecting a preset SHALL immediately update the staged filter type, constant, and unit, and reflect in the explanatory preview surface without requiring manual entry.

3. **Explanatory Guidance Surface**:
   - The dialog SHALL display a Material 3 container presenting a human-readable summary (`getSummary`) and detailed athletic explanation (`getDetails`), keeping the user informed of the active filter's behavior.

4. **Expandable Custom / Expert Controls**:
   - When *Custom / Expert* is selected, the dialog SHALL display granular inputs: `FilterType` dropdown, numeric input for time/samples/alpha, and unit selector (`sec`, `min`, `samples`), preserving full mathematical control.

5. **Preserved Modal Bottom Sheet & Action Contracts**:
   - The dialog SHALL compose `AppModalBottomSheet` with `FilterAlt` icon and `statusBarsPadding()` / `navigationBarsPadding()`.
   - The action bar SHALL compose `AppDialogActions.SaveCancel` with "Abbrechen" (`@string/Cancel`) and "Speichern" (`@string/save`), staging modifications until saved.

6. **9-Language Localization Parity**:
   - All preset names, section headers, and labels SHALL be fully localized across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 3. Detailed Test Specification (`TST-UI-119`)

### TST-UI-119: Intuitive Smoothing Presets, Smart Defaults & Filter Dialog Verification

1. **Smart Defaults Unit Verification (`EditSensorFieldViewModelTest.kt`)**:
   - *Test 1.1*: Initialize `EditSensorFieldViewModel` for a new field (`sensorFieldId = -1L`). When `onSensorTypeChanged(SensorType.POWER)` is called, assert `uiState.selectedFilterType == FilterType.MOVING_AVERAGE_TIME`, `uiState.filterConstant == 3.0`, and `uiState.movingAverageUnit == "sec"`.
   - *Test 1.2*: When `onSensorTypeChanged(SensorType.HR)` or `SensorType.SPEED_mps` is called, assert `uiState.selectedFilterType == FilterType.INSTANTANEOUS`, `uiState.filterConstant == 1.0`, and `uiState.movingAverageUnit == "sec"`.
   - *Test 1.3*: In `TrackingViewsDatabaseManager`, verify default rows generated for `ActivityType.BIKE_POWER` contain `FILTER_TYPE = MOVING_AVERAGE_TIME` and `FILTER_CONSTANT = 3` for `SensorType.POWER`.

2. **Preset Resolution & State Mapping Verification (`EditSensorFieldViewModelTest.kt`)**:
   - *Test 2.1*: Verify `resolvePreset(FilterType.INSTANTANEOUS, 1.0, "sec") == FilterPreset.DIRECT`.
   - *Test 2.2*: Verify `resolvePreset(FilterType.MOVING_AVERAGE_TIME, 3.0, "sec") == FilterPreset.SMOOTH_3S`.
   - *Test 2.3*: Verify `resolvePreset(FilterType.MOVING_AVERAGE_TIME, 10.0, "sec") == FilterPreset.SMOOTH_10S`.
   - *Test 2.4*: Verify `resolvePreset(FilterType.MOVING_AVERAGE_TIME, 30.0, "sec") == FilterPreset.SMOOTH_30S`.
   - *Test 2.5*: Verify `resolvePreset(FilterType.AVERAGE, 1.0, "sec") == FilterPreset.SESSION_AVG`.
   - *Test 2.6*: Verify `resolvePreset(FilterType.MAX_VALUE, 1.0, "sec") == FilterPreset.SESSION_MAX`.
   - *Test 2.7*: Verify `resolvePreset(FilterType.EXPONENTIAL_SMOOTHING, 0.5, "sec") == FilterPreset.CUSTOM`.
   - *Test 2.8*: Verify `resolvePreset(FilterType.MOVING_AVERAGE_NUMBER, 5.0, "samples") == FilterPreset.CUSTOM`.
   - *Test 2.9*: Verify `resolvePreset(FilterType.MOVING_AVERAGE_TIME, 45.0, "sec") == FilterPreset.CUSTOM`.

3. **Preset Selection State Transition (`EditSensorFieldViewModelTest.kt`)**:
   - *Test 3.1*: Call `onPresetSelected(FilterPreset.SMOOTH_10S)`: assert `uiState.selectedFilterType == FilterType.MOVING_AVERAGE_TIME`, `uiState.filterConstant == 10.0`, `uiState.movingAverageUnit == "sec"`, and `uiState.filterSummary` reflects 10s moving average.
   - *Test 3.2*: Call `onPresetSelected(FilterPreset.SESSION_MAX)`: assert `uiState.selectedFilterType == FilterType.MAX_VALUE`, `uiState.filterConstant == 1.0`, and `uiState.filterSummary` reflects max value.

4. **Composable Contract & Reflection Verification (`ModalBottomSheetDialogsIntegrityTest.kt`)**:
   - *Test 4.1*: Verify `ConfigureFilterDialog` composable function exists and is public in `ConfigureFilterDialogKt`.
   - *Test 4.2*: Verify parameter signature accepts `(viewModel, onDismissRequest, onSave)`.

5. **9-Language Localization & Positional Specifier Verification**:
   - *Test 5.1*: Verify string resources exist in all 9 locales: `filter_preset_direct`, `filter_preset_3s`, `filter_preset_10s`, `filter_preset_30s`, `filter_preset_avg`, `filter_preset_max`, `filter_preset_custom`, `filter_presets_header`, `filter_custom_header`.
   - *Test 5.2*: Verify zero unescaped bare format specifiers (`%s`, `%d`); all format strings use positional specifiers (`%1$s`, `%2$d`).

6. **Clean-Room Regression Suite**:
   - Run `./gradlew testDebugUnitTest` and confirm 100% pass rate with 0 failures and 0 regressions.

---

## 4. Acceptance Criteria (Given-When-Then)

* **AC-1 (Smart Power Default)**:
  - *Given* an athlete editing a cockpit sensor field,
  - *When* selecting `SensorType.POWER` from the sensor dropdown,
  - *Then* the filter SHALL automatically initialize to 3-second moving average (`FilterType.MOVING_AVERAGE_TIME`, 3.0s).
* **AC-2 (1-Tap Preset Selection)**:
  - *Given* the athlete opens `ConfigureFilterDialog`,
  - *When* tapping the "10 Sek (Pacing)" chip,
  - *Then* the active filter configuration SHALL immediately update to 10s moving average, the chip SHALL be visually highlighted, and the explanatory card SHALL update without opening technical dropdowns.
* **AC-3 (Explanatory Guidance Card)**:
  - *Given* any preset or custom filter selected in `ConfigureFilterDialog`,
  - *When* rendered,
  - *Then* the dialog SHALL display a Material 3 container showing the human-readable summary and detailed athletic description.
* **AC-4 (Custom Mode Expansion)**:
  - *Given* `ConfigureFilterDialog`,
  - *When* the athlete taps "Manuell / Experte" (or has a custom filter active),
  - *Then* the dialog SHALL display the `FilterType` dropdown, numeric input, and unit spinner, allowing arbitrary customization.
* **AC-5 (Save and Discard Semantics)**:
  - *Given* the athlete selects a preset or modifies custom filter parameters,
  - *When* tapping "Abbrechen" or dismissing the sheet,
  - *Then* changes SHALL be discarded and the previous filter configuration SHALL remain in effect.
  - *When* tapping "Speichern",
  - *Then* the staged filter SHALL be committed to the ViewModel and saved upon dialog confirmation.
* **AC-6 (9-Language Parity)**:
  - *Given* any of the 9 supported languages (EN, DE, ES, FR, IT, JA, NL, PL, PT),
  - *When* opening `ConfigureFilterDialog`,
  - *Then* all preset chips and section headers SHALL render in the active language with zero untranslated fallback strings.

---

## 5. Traceability Matrix

| Requirement | Test Specification | Verification Target | Status |
|:---|:---|:---|:---|
| `REQ-UI-149` | `TST-UI-102` | `ConfigureFilterDialog.kt` (AppModalBottomSheet contract) | Preserved |
| `REQ-UI-150` | `TST-UI-103` | `ConfigureFilterDialog.kt` (AppDialogActions.SaveCancel semantics) | Preserved |
| `REQ-UI-167` | `TST-UI-119` | `ConfigureFilterDialog.kt`, `EditSensorFieldViewModel.kt`, `TrackingViewsDatabaseManager.java`, `strings_filters.xml` | Specified |
