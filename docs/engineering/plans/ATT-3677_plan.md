# Stage 3: Implementation Plan - ATT-3677: Configure auto-pause speed thresholds and debounce windows independently per sport (Bike, Run, Other)

**Ticket**: [ATT-3677](https://rainerblind.atlassian.net/browse/ATT-3677)  
**Sub-task**: [ATT-3694](https://rainerblind.atlassian.net/browse/ATT-3694) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `None`  
**Active Sprint**: `2026-42.5` (Sprint ID `372`)  
**Requirement Mapping**: `REQ-TRK-018` (*Sport-Specific Auto-Pause Engine with Anti-Chatter Hysteresis, Mutex Precedence, Sensor Dropout Guard, Synchronous Ingestion Gating, and Sport-Decoupled Thresholds*)  
**Test Mapping**: `TST-TRK-010` (*Sport-Specific Auto-Pause Engine, Anti-Chatter Hysteresis, Unit Conversion & Mutex Verification*)  
**Branch**: `feature/ATT-3677`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Problem Description & Background

In Sprint `2026-42.4`, `ATT-45` decoupled Auto-Pause activation toggles per sport (`Bike`, `Run`, `Other`), but consolidated the Speed Threshold and Debounce Window into a single global slider set. Because low-speed dynamics differ fundamentally across sports (e.g. $4\text{ km/h}$ walking/climbing during a run vs. urban cycling vs. Nordic skiing), sharing thresholds causes nuisance stops or delayed pauses. In addition, the existing slider exposed fractional decimal values hardcoded to metric `"km/h"`, creating an inconsistent user experience for athletes using Imperial units (`mph`).

The objective of this ticket is to decouple speed thresholds and debounce windows independently for Cycling (`BIKE`), Running (`RUN`), and Other (`UNKNOWN`), using strictly integer (`Int`) values, dynamic unit-aware display ($1 \dots 5\text{ km/h}$ Metric or $1 \dots 5\text{ mph}$ Imperial), sport-specific debounce ranges ($1..10\text{ s}$ Bike, $1..20\text{ s}$ Run, $1..60\text{ s}$ Other), and mounting sliders directly below each sport's toggle when enabled in `SensorsGpsFilterSection.kt`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-TRK-018`
  * `REQ-TRK-018.1`: Independent sport-specific enablement flags (preserved).
  * `REQ-TRK-018.2`: Independent sport-specific integer speed thresholds and debounce windows:
    - Bike: Speed threshold $1..5$ (default 3 Metric / 2 Imperial), Debounce $1..10\text{ s}$ (default 2s).
    - Run: Speed threshold $1..5$ (default 2 Metric / 1 Imperial), Debounce $1..20\text{ s}$ (default 5s).
    - Other: Speed threshold $1..5$ (default 1 Metric / 1 Imperial), Debounce $1..60\text{ s}$ (default 10s).
  * `REQ-TRK-018.2`: Dynamic unit conversion (`MyHelper.UserUnit2mps`) with anti-chatter hysteresis ($+0.5$) and clamped physical ceiling ($6.0\text{ km/h}$ / $4.0\text{ mph}$).
  * `REQ-TRK-018.4`: Synchronous hierarchical sensor gating and elevation drift protection (preserved).
  * `REQ-TRK-018.5`: Manual pause mutex precedence (`mManualPauseLock`) (preserved).
* **Test Mapping**: `TST-TRK-010`
  * `TST-TRK-010.1`: Sport-Specific Default Activation & Decoupled Thresholds Tests (`AutoPauseManagerTest.kt`, `TuningPreferencesDataStoreAutoPauseTest.kt`).
  * `TST-TRK-010.2`: Debounce Counter & Stationary Transition per Sport (`AutoPauseManagerTest.kt`).
  * `TST-TRK-010.3`: Anti-Chatter Hysteresis Deadband, Ceiling & Unit Conversion (`AutoPauseManagerTest.kt`).
  * `TST-TRK-010.12`: Preference Migration & Compatibility Bridge Tests (`TuningPreferencesDataStoreAutoPauseTest.kt`).
  * `TST-TRK-010.13`: Full clean-room regression (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Manual Pause Sovereignty**: `mManualPauseLock` strictly supersedes speed telemetry. Auto-resume can never un-pause a session manually paused by the athlete.
2. **Elevation & Distance Invariance**: Synchronous gating in `VerticalSpeedAndSlopeDevice` and `MyAccumulatorSensor` ensures 0 elevation gain and 0 distance drift during pause.
3. **Moving Average Speed Integrity**: $v_{\text{avg}} = \frac{\text{Distance}}{\text{TimeActive}}$ in `WorkoutSummaries` strictly excludes pause duration.
4. **Backward Compatibility**: Legacy `TuningConfig` accessors (`autoPauseSpeedThresholdKmh`, `autoPauseDebounceSec`) and `AutoPauseConfig` fields remain available as deprecated bridges returning bike-sport values.
5. **Human Gate Sovereignty**: Subtasks transition directly to `Erledigt` upon passing Gate audits via `freigabe`; terminal sign-off of parent `ATT-3677` remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: Preference Layer (`TuningPreferencesDataStore.kt`)
* Define 6 new integer preference keys:
  - `KEY_AUTO_PAUSE_BIKE_SPEED_THRESHOLD: Preferences.Key<Int>`
  - `KEY_AUTO_PAUSE_BIKE_DEBOUNCE_SEC: Preferences.Key<Int>`
  - `KEY_AUTO_PAUSE_RUN_SPEED_THRESHOLD: Preferences.Key<Int>`
  - `KEY_AUTO_PAUSE_RUN_DEBOUNCE_SEC: Preferences.Key<Int>`
  - `KEY_AUTO_PAUSE_OTHER_SPEED_THRESHOLD: Preferences.Key<Int>`
  - `KEY_AUTO_PAUSE_OTHER_DEBOUNCE_SEC: Preferences.Key<Int>`
* Add constants to `TuningPreferencesDefaults`:
  - `MIN_AUTO_PAUSE_SPEED_THRESHOLD = 1`, `MAX_AUTO_PAUSE_SPEED_THRESHOLD = 5`
  - Bike defaults: `DEFAULT_AUTO_PAUSE_BIKE_SPEED_THRESHOLD_METRIC = 3`, `DEFAULT_AUTO_PAUSE_BIKE_SPEED_THRESHOLD_IMPERIAL = 2`, `DEFAULT_AUTO_PAUSE_BIKE_DEBOUNCE_SEC = 2`, `MAX_AUTO_PAUSE_BIKE_DEBOUNCE_SEC = 10`
  - Run defaults: `DEFAULT_AUTO_PAUSE_RUN_SPEED_THRESHOLD_METRIC = 2`, `DEFAULT_AUTO_PAUSE_RUN_SPEED_THRESHOLD_IMPERIAL = 1`, `DEFAULT_AUTO_PAUSE_RUN_DEBOUNCE_SEC = 5`, `MAX_AUTO_PAUSE_RUN_DEBOUNCE_SEC = 20`
  - Other defaults: `DEFAULT_AUTO_PAUSE_OTHER_SPEED_THRESHOLD_METRIC = 1`, `DEFAULT_AUTO_PAUSE_OTHER_SPEED_THRESHOLD_IMPERIAL = 1`, `DEFAULT_AUTO_PAUSE_OTHER_DEBOUNCE_SEC = 10`, `MAX_AUTO_PAUSE_OTHER_DEBOUNCE_SEC = 60`
* Extend `TuningConfig` data class with 6 integer properties and preserve legacy getters for backwards compatibility.
* Expose individual mutator methods in `TuningPreferencesDataStore`.

### Component 2: Domain Engine (`AutoPauseConfig` & `AutoPauseManager.kt`)
* Update `AutoPauseConfig` to carry:
  - `bikeSpeedThreshold: Int`, `bikeDebounceSec: Int`
  - `runSpeedThreshold: Int`, `runDebounceSec: Int`
  - `otherSpeedThreshold: Int`, `otherDebounceSec: Int`
  - `isMetric: Boolean = true`
  - Helper functions `getSpeedThresholdForSport(sportType: BSportType): Int` and `getDebounceSecForSport(sportType: BSportType): Int`
* Update `AutoPauseManager.evaluateTick`:
  - Query sport-specific threshold and debounce window.
  - Determine active unit (`if (config.isMetric) MyUnits.METRIC else MyUnits.IMPERIAL`).
  - Convert threshold to canonical m/s via `MyHelper.UserUnit2mps(threshold.toDouble(), unit)`.
  - Calculate resume threshold with $+0.5$ hysteresis and clamped physical ceiling ($6.0\text{ km/h}$ / $4.0\text{ mph}$), converted to m/s.
  - Evaluate stationary condition against sport-specific debounce window.

### Component 3: Service & Helper Bridge (`SettingsDataStoreJavaHelper.kt` & `TrackerService.java`)
* In `SettingsDataStoreJavaHelper.kt`:
  - Add `@JvmStatic fun getAutoPauseConfig(context: Context): AutoPauseConfig` querying current `TuningConfig` and checking `MyHelper.getUnits() == MyUnits.METRIC`.
* In `TrackerService.java`:
  - Simplify auto-pause config initialization to call `SettingsDataStoreJavaHelper.getAutoPauseConfig(this)`.

### Component 4: User Interface (`SensorsGpsFilterSection.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`)
* In `SensorsGpsFilterSection.kt`:
  - Remove the bottom shared 2 sliders.
  - Render sport-specific Speed Threshold and Debounce sliders directly under each sport toggle, visible when that sport is enabled.
  - Format slider readouts as `"$value $speedUnits"` where `speedUnits` is derived from `stringResource(MyHelper.getShortUnitsId(SensorType.SPEED_mps))`.
  - Slider ranges:
    - Bike: Threshold `1..5` (steps 3), Debounce `1..10` (steps 8).
    - Run: Threshold `1..5` (steps 3), Debounce `1..20` (steps 18).
    - Other: Threshold `1..5` (steps 3), Debounce `1..60` (steps 58).
* In `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt`:
  - Wire the 6 new sport-specific states and mutators from `TuningConfig`.

### UI Consistency (Rule 23 — Section 5 Design Guidelines)
* **Closest existing reference screen**: `SensorsGpsFilterSection.kt` and `ZonesSettingsActivity.kt`.
* **Reused components**: `TuningToggleItem`, `TuningSliderItem`, `HorizontalDivider`, `Column`.
* **Theme tokens**:
  - Typography: `MaterialTheme.typography.titleSmall`, `bodySmall`.
  - Colors: `MaterialTheme.colorScheme.onSurface`, `onSurfaceVariant`.
  - Spacing & Indentation: Child slider columns indented with `Modifier.padding(start = 16.dp)`, spaced with `Arrangement.spacedBy(8.dp)`.
* **New one-off styles & justification**: None. Uses existing Material 3 styling tokens and components.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Extend `TuningPreferencesDefaults` & `TuningPreferencesDataStore.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStore.kt`
* Changes:
  - Add sport-specific constants, preference keys, and `TuningConfig` fields.
  - Update `tuningConfigFlow` mapping with defaults and coercion.
  - Add preference update methods (`setAutoPauseBikeSpeedThreshold`, `setAutoPauseBikeDebounceSec`, etc.).

### Step 2: Refactor `AutoPauseConfig` and `AutoPauseManager.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/autopause/AutoPauseManager.kt`
* Changes:
  - Update `AutoPauseConfig` data class with sport-specific fields, helper getters, and `isMetric`.
  - Update `evaluateTick` and resume threshold calculations to use sport-specific parameters and `MyHelper.UserUnit2mps`.

### Step 3: Implement Java Helper Bridge and Update `TrackerService.java`
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/settings/SettingsDataStoreJavaHelper.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/TrackerService.java`
* Changes:
  - Add `@JvmStatic fun getAutoPauseConfig(context: Context): AutoPauseConfig`.
  - Wire in `TrackerService.java`.

### Step 4: Refactor UI in `SensorsGpsFilterSection.kt`, `AdvancedTuningScreen.kt`, and `AdvancedTuningDialog.kt`
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/SensorsGpsFilterSection.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt`
* Changes:
  - Nest sliders under each sport toggle with `Modifier.padding(start = 16.dp)`.
  - Wire state and event handlers across screen and dialog.

### Step 5: Update Unit Tests
* Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/autopause/AutoPauseManagerTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStoreAutoPauseTest.kt`
* Changes:
  - Add test coverage for decoupled thresholds, unit conversions, debounce windows per sport, and legacy compatibility.

### Step 6: Execute Targeted Tests and Clean-Room Full Suite Regression
* Commands:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.autopause.*" --tests "com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStoreAutoPauseTest"
  ./gradlew testDebugUnitTest
  ```
* Expected Outcome: 100% test pass rate with zero regressions.

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Run all unit tests for AutoPauseManager and TuningPreferencesDataStore.
  - Verify zero lint/compilation errors.
  - Verify clean-room suite pass rate.
* **Rollback Plan**:
  - Revert `feature/ATT-3677` via `git checkout sprint/2026-42.5 -- .` if unexpected regression occurs.
