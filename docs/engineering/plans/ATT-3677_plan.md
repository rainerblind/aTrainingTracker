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
  * `REQ-TRK-018.2`: Dynamic unit conversion (`MyHelper.UserUnit2mps`) with anti-chatter hysteresis ($+0.5$) and parameterized physical ceiling ($6.0\text{ km/h}$ Metric / $6.0\text{ mph}$ Imperial).
  * `REQ-TRK-018.4`: Synchronous hierarchical sensor gating and elevation drift protection (preserved).
  * `REQ-TRK-018.5`: Manual pause mutex precedence (`mManualPauseLock`) (preserved).
* **Test Mapping**: `TST-TRK-010`
  * `TST-TRK-010.1`: Sport-Specific Default Activation & Decoupled Thresholds Tests (`AutoPauseManagerTest.kt`, `TuningPreferencesDataStoreAutoPauseTest.kt`).
  * `TST-TRK-010.2`: Debounce Counter & Stationary Transition per Sport (`AutoPauseManagerTest.kt`).
  * `TST-TRK-010.3`: Anti-Chatter Hysteresis Deadband, Ceiling & Unit Conversion (`AutoPauseManagerTest.kt`).
  * `TST-TRK-010.12`: Preference Migration & Compatibility Bridge Tests (`TuningPreferencesDataStoreAutoPauseTest.kt`).
  * `TST-TRK-010.13`: Full clean-room regression (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants, Boundary Math & Preserved Behavior

1. **Manual Pause Sovereignty (Race-Free Mutex Precedence)**:
   - `AutoPauseManager.evaluateTick` unconditionally checks `manualPauseLocked` as its **first** step before evaluating any sport-specific threshold or speed packet:
     ```kotlin
     if (manualPauseLocked) {
         currentState = AutoPauseState.MANUAL_PAUSED
         debounceCounter = 0
         sensorLostRecoveryTicks = 0
         return AutoPauseTransition(...)
     }
     ```
   - Automated triggers cannot override an active manual pause lock.
2. **Boundary & Physical Ceiling Math**:
   - For Metric: $v_{\text{pause}} \in [1, 5]\text{ km/h}$. Resume threshold is $v_{\text{resume}} = \min(v_{\text{pause}} + 0.5\text{ km/h}, 6.0\text{ km/h})$. At max $v_{\text{pause}} = 5\text{ km/h}$, $v_{\text{resume}} = 5.5\text{ km/h} < 6.0\text{ km/h}$.
   - For Imperial: $v_{\text{pause}} \in [1, 5]\text{ mph}$ ($1\text{ mph} \approx 1.609\text{ km/h}$, $5\text{ mph} \approx 8.047\text{ km/h}$). Because $5\text{ mph} > 6.0\text{ km/h}$, a global $6.0\text{ km/h}$ ceiling would invert hysteresis ($v_{\text{resume}} < v_{\text{pause}}$). Therefore, the ceiling is parameterized per active unit:
     - Metric ceiling: $6.0\text{ km/h}$ ($1.667\text{ m/s}$).
     - Imperial ceiling: $6.0\text{ mph}$ ($2.682\text{ m/s}$).
   - In both modes, $v_{\text{resume}} = \min(v_{\text{pause}} + 0.5, v_{\text{ceiling}})$ strictly guarantees $v_{\text{resume}} > v_{\text{pause}}$ across the entire allowed range $[1, 5]$.
3. **DataStore Preference Migration Strategy**:
   - On first launch after update, if the sport-specific integer keys are unset in DataStore:
     - If legacy global keys `KEY_AUTO_PAUSE_SPEED_THRESHOLD_KMH` or `KEY_AUTO_PAUSE_DEBOUNCE_SEC` exist, their rounded integer values (`Math.round(legacyKmh).coerceIn(1, 5)` and `legacyDebounce.coerceIn(min, max)`) are copied into the sport-specific keys.
     - If legacy keys are also absent, standard sport defaults (Bike 3/2, Run 2/1, Other 1/1; Debounce Bike 2s, Run 5s, Other 10s) are applied.
     - Legacy keys remain populated as backward-compatible bridges.
4. **Non-Blocking Telemetry Loop Invariant (TrackerService Caching)**:
   - In `TrackerService.java`, calling `runBlocking` on DataStore every 1 second inside `evaluateAutoPauseTick()` would introduce potential jitter to the high-frequency telemetry loop.
   - Therefore, `TrackerService` maintains an in-memory cached `volatile AutoPauseConfig mCachedAutoPauseConfig`.
   - `mCachedAutoPauseConfig` is initialized during service creation and updated asynchronously via `tuningPreferencesDataStore.tuningConfigFlow` (or reloaded when preferences change). `evaluateAutoPauseTick()` reads the cached reference with $O(1)$ zero blocking I/O overhead.
5. **Elevation & Distance Invariance**: Synchronous gating in `VerticalSpeedAndSlopeDevice` and `MyAccumulatorSensor` ensures 0 elevation gain and 0 distance drift during pause.
6. **Moving Average Speed Integrity**: $v_{\text{avg}} = \frac{\text{Distance}}{\text{TimeActive}}$ in `WorkoutSummaries` strictly excludes pause duration.
7. **Human Gate Sovereignty**: Subtasks transition directly to `Erledigt` upon passing Gate audits via `freigabe`; terminal sign-off of parent `ATT-3677` remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: Preference Layer (`TuningPreferencesDataStore.kt`)
* Define 6 new integer preference keys:
  - `KEY_AUTO_PAUSE_BIKE_SPEED_THRESHOLD: Preferences.Key<Int> = intPreferencesKey("tuning_auto_pause_bike_speed_threshold")`
  - `KEY_AUTO_PAUSE_BIKE_DEBOUNCE_SEC: Preferences.Key<Int> = intPreferencesKey("tuning_auto_pause_bike_debounce_sec")`
  - `KEY_AUTO_PAUSE_RUN_SPEED_THRESHOLD: Preferences.Key<Int> = intPreferencesKey("tuning_auto_pause_run_speed_threshold")`
  - `KEY_AUTO_PAUSE_RUN_DEBOUNCE_SEC: Preferences.Key<Int> = intPreferencesKey("tuning_auto_pause_run_debounce_sec")`
  - `KEY_AUTO_PAUSE_OTHER_SPEED_THRESHOLD: Preferences.Key<Int> = intPreferencesKey("tuning_auto_pause_other_speed_threshold")`
  - `KEY_AUTO_PAUSE_OTHER_DEBOUNCE_SEC: Preferences.Key<Int> = intPreferencesKey("tuning_auto_pause_other_debounce_sec")`
* Add constants to `TuningPreferencesDefaults`:
  - `MIN_AUTO_PAUSE_SPEED_THRESHOLD = 1`, `MAX_AUTO_PAUSE_SPEED_THRESHOLD = 5`
  - Bike defaults: `DEFAULT_AUTO_PAUSE_BIKE_SPEED_THRESHOLD_METRIC = 3`, `DEFAULT_AUTO_PAUSE_BIKE_SPEED_THRESHOLD_IMPERIAL = 2`, `DEFAULT_AUTO_PAUSE_BIKE_DEBOUNCE_SEC = 2`, `MAX_AUTO_PAUSE_BIKE_DEBOUNCE_SEC = 10`
  - Run defaults: `DEFAULT_AUTO_PAUSE_RUN_SPEED_THRESHOLD_METRIC = 2`, `DEFAULT_AUTO_PAUSE_RUN_SPEED_THRESHOLD_IMPERIAL = 1`, `DEFAULT_AUTO_PAUSE_RUN_DEBOUNCE_SEC = 5`, `MAX_AUTO_PAUSE_RUN_DEBOUNCE_SEC = 20`
  - Other defaults: `DEFAULT_AUTO_PAUSE_OTHER_SPEED_THRESHOLD_METRIC = 1`, `DEFAULT_AUTO_PAUSE_OTHER_SPEED_THRESHOLD_IMPERIAL = 1`, `DEFAULT_AUTO_PAUSE_OTHER_DEBOUNCE_SEC = 10`, `MAX_AUTO_PAUSE_OTHER_DEBOUNCE_SEC = 60`
* Extend `TuningConfig` data class with 6 integer properties and preserve legacy getters `autoPauseSpeedThresholdKmh` and `autoPauseDebounceSec` for backwards compatibility.
* Expose individual mutator methods in `TuningPreferencesDataStore`.

### Component 2: Domain Engine (`AutoPauseConfig` & `AutoPauseManager.kt`)
* Update `AutoPauseConfig` to carry:
  - `bikeSpeedThreshold: Int`, `bikeDebounceSec: Int`
  - `runSpeedThreshold: Int`, `runDebounceSec: Int`
  - `otherSpeedThreshold: Int`, `otherDebounceSec: Int`
  - `isMetric: Boolean = true`
  - Helper functions `getSpeedThresholdForSport(sportType: BSportType): Int` and `getDebounceSecForSport(sportType: BSportType): Int`
* Update `AutoPauseManager.evaluateTick`:
  - Mutex lock check first (`if (manualPauseLocked) ...`).
  - Query sport-specific threshold and debounce window.
  - Determine active unit (`if (config.isMetric) MyUnits.METRIC else MyUnits.IMPERIAL`).
  - Convert threshold to canonical m/s via `MyHelper.UserUnit2mps(threshold.toDouble(), unit)`.
  - Calculate resume threshold with $+0.5$ hysteresis and unit-specific physical ceiling ($6.0\text{ km/h}$ / $6.0\text{ mph}$), converted to m/s via `MyHelper.UserUnit2mps`.
  - Evaluate stationary condition against sport-specific debounce window.

### Component 3: Service & Helper Bridge (`SettingsDataStoreJavaHelper.kt` & `TrackerService.java`)
* In `SettingsDataStoreJavaHelper.kt`:
  - Add `@JvmStatic fun getAutoPauseConfig(context: Context): AutoPauseConfig` querying current `TuningConfig` and checking `MyHelper.getUnits() == MyUnits.METRIC`.
* In `TrackerService.java`:
  - Cache `mCachedAutoPauseConfig` in memory.
  - Initialize upon service creation and update on preference changes.
  - `evaluateAutoPauseTick()` uses `mCachedAutoPauseConfig` directly without synchronous blocking DataStore reads.

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
  - Add sport-specific constants, preference keys, and migration fallback mapping.
  - Update `tuningConfigFlow` mapping with defaults, migration coercion, and legacy fallbacks.
  - Add preference update methods (`setAutoPauseBikeSpeedThreshold`, `setAutoPauseBikeDebounceSec`, etc.).

### Step 2: Refactor `AutoPauseConfig` and `AutoPauseManager.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/autopause/AutoPauseManager.kt`
* Changes:
  - Update `AutoPauseConfig` data class with sport-specific fields, helper getters, and `isMetric`.
  - Update `evaluateTick` to evaluate mutex lock first, then resolve sport-specific parameters and unit-specific ceilings via `MyHelper.UserUnit2mps`.

### Step 3: Implement Java Helper Bridge and TrackerService In-Memory Caching
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/settings/SettingsDataStoreJavaHelper.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`
* Changes:
  - Add `@JvmStatic fun getAutoPauseConfig(context: Context): AutoPauseConfig`.
  - Cache `mCachedAutoPauseConfig` in `TrackerService.java`, avoiding blocking I/O on `evaluateAutoPauseTick()`.

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
  - Add test coverage for decoupled thresholds, unit conversions, debounce windows per sport, unit switching boundary checks, and legacy compatibility.

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
