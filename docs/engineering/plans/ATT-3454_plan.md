# Stage 3: Implementation Plan - ATT-3454: Move missing ANT+ adapter warning toggle to sensor search and behavior tuning section

**Ticket**: [ATT-3454](https://atrainingtracker.atlassian.net/browse/ATT-3454)  
**Sub-task**: [ATT-3482](https://atrainingtracker.atlassian.net/browse/ATT-3482) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.2`  
**Active Sprint**: `Sprint 2026-42.2`  
**Requirement Mapping**: `REQ-UI-353` (*Persistent Missing ANT+ Adapter Dialog Suppression and Advanced Settings Warning Reset*), amended  
**Test Mapping**: `TST-UI-313` (`AntMissingAdapterDialogSuppressionTest.kt`, `AdvancedTuningModularityTest.kt`)  
**Branch**: `feature/ATT-3454`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Problem Description & Background

In the Advanced Tuning settings interface (`AdvancedTuningDialog.kt` and `AdvancedTuningScreen.kt`), the toggle *"Warnung bei fehlendem ANT+ Adapter"* (`tuning_show_ant_missing_adapter_warnings_title`, introduced in Sprint 2026-41.10 under ATT-3220) is currently located inside the *"Sensoren, GPS & Filter"* section (`SensorsGpsFilterSection.kt`).

This location is semantically misplaced:
1. *"Sensoren, GPS & Filter"* is dedicated to numerical telemetry filtering parameters: GPS horizontal accuracy threshold (5–250 m), barometric altitude moving average window (5–60 s), and minimum speed threshold for slope calculation (0.1–2.0 m/s).
2. The missing ANT+ adapter warning dialog is triggered by `BANALService` during sensor search sweeps (`startSearch()`) when an athlete searches for ANT+ peripherals on hardware lacking an ANT+ adapter.
3. Placing this toggle inside *"Sensorsuche & Verhalten"* (`SensorSearchTuningSection.kt`) establishes semantic cohesion, grouping it directly with other discovery and search behavior settings (retry attempts, automatic search triggers, sport-specific search).

ATT-3454 resolves this architectural inconsistency by relocating the toggle item from `SensorsGpsFilterSection.kt` to `SensorSearchTuningSection.kt`, rewiring the caller hosts (`AdvancedTuningDialog.kt` and `AdvancedTuningScreen.kt`), and updating contract tests while preserving DataStore persistence and strict file modularity (< 400 lines).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-353` (*Persistent Missing ANT+ Adapter Dialog Suppression and Advanced Settings Warning Reset*, amended for ATT-3454)
* **Test Mapping**: `TST-UI-313` (*Persistent Missing ANT+ Adapter Dialog Suppression, DataStore Persistence, and Advanced Settings Warning Reset Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Persistent DataStore Schema & State Binding**:
   - The preference key `KEY_SUPPRESS_ANT_MISSING_ADAPTER_DIALOG` in `TuningPreferencesDataStore.kt` MUST NOT be renamed or modified.
   - The inverted helper `val showAntMissingAdapterWarnings: Boolean get() = !suppressAntMissingAdapterDialog` in `TuningConfig` MUST be preserved.
   - Factory defaults (`suppress = false`, `showAntMissingAdapterWarnings = true`) and `resetToDefaults()` MUST remain 100% intact.
2. **Separation of Storage Engines**:
   - `SensorSearchPreferences` persists legacy search settings to `SharedPreferences`. The missing ANT+ adapter warning toggle MUST remain directly bound to `TuningPreferencesDataStore` via explicit state and callback parameters (`showAntMissingAdapterWarnings: Boolean`, `onShowAntMissingAdapterWarningsChange: (Boolean) -> Unit`), avoiding cross-storage coupling or synchronization lag.
3. **Broadcast Reception & Dismissal Lifecycle**:
   - `MainActivityWithNavigation.kt` broadcast filtering guard and `ATrainingTrackerApp.kt` dismissal handler (`setSuppressAntMissingAdapterDialog(true)`) MUST NOT be altered.
4. **Strict File Modularity Ceiling (< 400 lines)**:
   - `AdvancedTuningModularityTest.kt` enforces that every source file in `com.atrainingtracker.trainingtracker.ui.settings.tuning` must be strictly under 400 lines.
   - `SensorsGpsFilterSection.kt` will decrease from 93 lines to ~80 lines.
   - `SensorSearchTuningSection.kt` will increase from 205 lines to ~225 lines (well below the 400-line ceiling).
   - `AdvancedTuningDialog.kt` (~397 lines) and `AdvancedTuningScreen.kt` (~388 lines) must not exceed 400 lines.
5. **Localization Parity (9 Languages)**:
   - String resources `tuning_show_ant_missing_adapter_warnings_title` and `tuning_show_ant_missing_adapter_warnings_desc` already exist across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) and MUST be preserved.
6. **100% Clean-Room Unit Test Pass Rate**:
   - All 2573+ unit tests must continue to pass cleanly (`./gradlew testDebugUnitTest`).
7. **Human Decision Gate Guard**:
   - The parent ticket ATT-3454 terminal transition to `Erledigt` is reserved strictly for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### 4.1 Component: `SensorsGpsFilterSection.kt`
* Remove `showAntMissingAdapterWarnings: Boolean = true` and `onShowAntMissingAdapterWarningsChange: (Boolean) -> Unit = {}` from the function signature.
* Remove lines 86–91 rendering `TuningToggleItem(title = stringResource(R.string.tuning_show_ant_missing_adapter_warnings_title), ...)`.
* Update KDoc to reflect that it exclusively encapsulates GPS accuracy, altitude filter window, and slope minimum speed thresholds.

### 4.2 Component: `SensorSearchTuningSection.kt`
* Add `showAntMissingAdapterWarnings: Boolean = true` and `onShowAntMissingAdapterWarningsChange: (Boolean) -> Unit = {}` to:
  1. The overload accepting `prefs: SensorSearchPreferences`:
     ```kotlin
     @Composable
     fun SensorSearchTuningSection(
         prefs: SensorSearchPreferences,
         onPrefsChange: (SensorSearchPreferences) -> Unit,
         showAntMissingAdapterWarnings: Boolean = true,
         onShowAntMissingAdapterWarningsChange: (Boolean) -> Unit = {}
     )
     ```
  2. The primary parameter-based composable:
     ```kotlin
     @Composable
     fun SensorSearchTuningSection(
         numberOfSearchTries: Int,
         onNumberOfSearchTriesChange: (Int) -> Unit,
         startSearchWhenAppStarts: Boolean,
         onStartSearchWhenAppStartsChange: (Boolean) -> Unit,
         startSearchWhenResumeFromPaused: Boolean,
         onStartSearchWhenResumeFromPausedChange: (Boolean) -> Unit,
         startSearchWhenUserChangesSport: Boolean,
         onStartSearchWhenUserChangesSportChange: (Boolean) -> Unit,
         startSearchWhenTrackingStarts: Boolean,
         onStartSearchWhenTrackingStartsChange: (Boolean) -> Unit,
         searchOnlyForSportSpecificDevices: Boolean,
         onSearchOnlyForSportSpecificDevicesChange: (Boolean) -> Unit,
         changeSportWhenDeviceGetsLost: Boolean,
         onChangeSportWhenDeviceGetsLostChange: (Boolean) -> Unit,
         showAntMissingAdapterWarnings: Boolean = true,
         onShowAntMissingAdapterWarningsChange: (Boolean) -> Unit = {}
     )
     ```
* Under the *"Suchverhalten"* section header (`Text(text = stringResource(R.string.prefs_search_behavior_title), ...)`), append:
  ```kotlin
  TuningToggleItem(
      title = stringResource(R.string.tuning_show_ant_missing_adapter_warnings_title),
      isChecked = showAntMissingAdapterWarnings,
      onCheckedChange = onShowAntMissingAdapterWarningsChange,
      summary = stringResource(R.string.tuning_show_ant_missing_adapter_warnings_desc)
  )
  ```

### 4.3 Component: `AdvancedTuningScreen.kt` & `AdvancedTuningDialog.kt`
* In `SensorsGpsFilterSection(...)` call site:
  - Remove `showAntMissingAdapterWarnings = showAntMissingAdapterWarnings` and `onShowAntMissingAdapterWarningsChange = { showAntMissingAdapterWarnings = it }`.
* In `SensorSearchTuningSection(...)` call site:
  - Add `showAntMissingAdapterWarnings = showAntMissingAdapterWarnings, onShowAntMissingAdapterWarningsChange = { showAntMissingAdapterWarnings = it }`.

---

### UI Consistency (Rule 23 — Mandatory Section)

* **Reference Screen / Component**: `SensorSearchTuningSection.kt` and `AdvancedTuningScreen.kt` / `AdvancedTuningDialog.kt`.
* **Reused Components**: Pre-existing `TuningToggleItem` (`ui/settings/tuning/TuningFormControls.kt`).
* **Theme Tokens**: Standard Material 3 tokens defined in `TuningToggleItem` (`MaterialTheme.typography.bodyLarge`, `MaterialTheme.typography.bodyMedium`, `MaterialTheme.colorScheme.onSurface`, `MaterialTheme.colorScheme.onSurfaceVariant`).
* **New One-Off Styles & Justification**: None. The toggle reuses existing tokens and identical string resources (`tuning_show_ant_missing_adapter_warnings_title`, `tuning_show_ant_missing_adapter_warnings_desc`).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Flight Gate 3 Invariant Check (Rule 4)
* Command: `python3 tools/jira_util.py check-gate ATT-3482`
* Verify exit code 0 before making any code modifications.

### Step 2: Update Structural Contract Tests in `AntMissingAdapterDialogSuppressionTest.kt`
* Modify `AntMissingAdapterDialogSuppressionTest.kt`:
  - Rename `testSensorsGpsFilterSection_structuralToggleWiring` to `testSensorSearchTuningSection_structuralToggleWiring` and assert that `SensorSearchTuningSection.kt` contains `showAntMissingAdapterWarnings: Boolean`, `R.string.tuning_show_ant_missing_adapter_warnings_title`, and `R.string.tuning_show_ant_missing_adapter_warnings_desc`.
  - Add assertion verifying that `SensorsGpsFilterSection.kt` does NOT contain `showAntMissingAdapterWarnings` or `tuning_show_ant_missing_adapter_warnings_title`.
  - Add assertions verifying that both `AdvancedTuningDialog.kt` and `AdvancedTuningScreen.kt` pass `showAntMissingAdapterWarnings` to `SensorSearchTuningSection`.

### Step 3: Modify `SensorsGpsFilterSection.kt`
* Remove parameter `showAntMissingAdapterWarnings` and callback.
* Remove `TuningToggleItem` invocation.

### Step 4: Modify `SensorSearchTuningSection.kt`
* Add `showAntMissingAdapterWarnings` and callback to both composable overloads.
* Render `TuningToggleItem` under the *"Suchverhalten"* section.

### Step 5: Update `AdvancedTuningDialog.kt` and `AdvancedTuningScreen.kt`
* Move parameters from `SensorsGpsFilterSection` to `SensorSearchTuningSection`.
* Verify file line counts remain strictly under 400 lines.

### Step 6: Execute Targeted Unit Tests
* Commands:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.ant.AntMissingAdapterDialogSuppressionTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningModularityTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningVisualContractTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningScreenTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.TuningConfigTest"
  ```

### Step 7: Execute Full Clean-Room Regression Suite
* Command:
  ```bash
  ./gradlew testDebugUnitTest
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted tests during construction (Step 6), modularity enforcement (< 400 lines), clean-room full suite regression (Step 7), and APK installation on device.
* **Rollback**: Branch isolation on `feature/ATT-3454` guarantees immediate clean rollback via git checkout without contaminating `sprint/2026-42.2` or `develop`.
