# Stage 3: Implementation Plan - ATT-3321: Expose turn cue countdown distance slider in advanced navigation settings

**Ticket**: [ATT-3321](https://rainerblind.atlassian.net/browse/ATT-3321)  
**Sub-task**: [ATT-3347](https://rainerblind.atlassian.net/browse/ATT-3347) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-287` (amending Clause 2), `REQ-SET-073`  
**Test Mapping**: `TST-UI-247`  
**Branch**: `feature/ATT-3321`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-12  

---

## 1. Problem Description & Background

In the workout navigation HUD, turn prompts (`TurnPromptBanner.kt`) appear when an athlete approaches an upcoming route turn cue. The turn-by-turn navigation engine (`TurnByTurnNavigationEngine.kt`) triggers `isApproaching = true` when the distance along the route to the upcoming cue is less than or equal to `countdownThreshold = tuningConfig.turnCueCountdownDistanceMeters.toDouble()`.

`TuningPreferencesDataStore` already defines `KEY_TURN_CUE_COUNTDOWN_DISTANCE`, clamps the value between `30.0f` and `300.0f` (default: `100.0f`), and exposes it via `TuningConfig.turnCueCountdownDistanceMeters`. However, this parameter is currently not exposed in the Advanced Tuning UI (`NavigationSection.kt` / `AdvancedTuningDialog.kt`), nor does `TuningPreferencesDataStore` provide a dedicated mutator method `updateTurnCueCountdownDistanceMeters`.

This plan details the construction steps to:
1. Provide a dedicated mutator `updateTurnCueCountdownDistanceMeters(distanceMeters: Float)` in `TuningPreferencesDataStore.kt`.
2. Add a `TuningSliderItem` in `NavigationSection.kt` for "Navigation Cue Distance" spanning `30 m` to `300 m` in 10 m steps (26 intermediate steps) with default `100 m`.
3. Wire the state and persistence in `AdvancedTuningDialog.kt`.
4. Ensure 100% 9-language localization parity across all supported application locales.
5. Add unit and contract tests verifying DataStore persistence, slider behavior, and dynamic navigation engine threshold triggers.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-287` (*Navigation Cue Overlay Transparency, Dismiss Duration, Countdown Distance & Expert Settings Configuration*), amending Clause 2, and `REQ-SET-073` (*DataStore Expert Tuning Repository*).
* **Test Mapping**: `TST-UI-247` (*Navigation Cue Overlay Transparency, Dismiss Duration, Countdown Distance & Expert Settings Configuration Verification*).

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-287` (*Navigation Cue Overlay Transparency, Dismiss Duration & Expert Settings Configuration*), amending Clause 2, and `REQ-SET-073` (*DataStore Expert Tuning Repository*).
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-41.2 (`ATT-2632`, commit `8e6bcfc5`), refining earlier groundwork from Sprint 2026-40.16 (`ATT-1450`, commit `54c5c2d3` / `REQ-MAP-028`); amended in Sprint 2026-42.1 (`ATT-3321`).
* **Root Reason for Existing Formulation**: In ATT-1450, `turnCueCountdownDistanceMeters` was introduced as an internal configuration parameter and defaulted to 100 meters, but was not given a UI slider in ATT-2632 when transparency and auto-dismiss duration were exposed. Athletes travelling at high cycling speeds require earlier warning windows (200-300m), while urban athletes prefer tighter thresholds (30-50m).
* **Preservation of Core Invariants**: Default value of 100 meters is strictly preserved so existing users experience zero behavioral regression unless they explicitly adjust the slider. Safety clamping bounds (`30.0f .. 300.0f`), atomic factory reset (`resetToDefaults()`), and 100% full-suite test pass rate remain strictly preserved.

---

## 3. System Invariants & Preserved Behavior

1. **Default Cue Window**: On clean installs and default settings (`turnCueCountdownDistanceMeters == 100.0f`), navigation cues trigger exactly at 100m, preserving existing behavior.
2. **Safety Clamping Bounds**: The distance is strictly clamped to `[30.0f, 300.0f]`.
3. **Dynamic Engine Responsiveness**: `TurnByTurnNavigationEngine.kt` uses `tuningConfig.turnCueCountdownDistanceMeters.toDouble()` dynamically for `countdownThreshold`.
4. **Localization Parity**: 100% string coverage across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
5. **Clean-Room Test Pass Rate**: 100% test pass rate across all unit tests in `./gradlew testDebugUnitTest`.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: DataStore & Mutator (`TuningPreferencesDataStore.kt`)
* Expose dedicated mutator method:
  ```kotlin
  suspend fun updateTurnCueCountdownDistanceMeters(distanceMeters: Float) {
      val clamped = distanceMeters.coerceIn(
          TuningPreferencesDefaults.MIN_TURN_CUE_COUNTDOWN_DISTANCE_METERS,
          TuningPreferencesDefaults.MAX_TURN_CUE_COUNTDOWN_DISTANCE_METERS
      )
      context.dataStore.edit { prefs ->
          prefs[KEY_TURN_CUE_COUNTDOWN_DISTANCE] = clamped
      }
  }
  ```

### Component 2: UI Presentation & Section Item (`NavigationSection.kt`)
* Update `NavigationSection` signature to accept:
  ```kotlin
  turnCueCountdownDistanceMeters: Float = TuningPreferencesDefaults.TURN_CUE_COUNTDOWN_DISTANCE_METERS,
  onTurnCueCountdownDistanceChange: (Float) -> Unit = {},
  ```
* Render `TuningSliderItem`:
  ```kotlin
  TuningSliderItem(
      title = stringResource(R.string.tuning_nav_cue_distance_title),
      valueText = "${turnCueCountdownDistanceMeters.roundToInt()} m",
      helperText = stringResource(R.string.tuning_nav_cue_distance_desc),
      defaultText = stringResource(R.string.tuning_default_format, "100 m"),
      value = turnCueCountdownDistanceMeters,
      onValueChange = { snapped ->
          val rounded = (snapped / 10f).roundToInt() * 10f
          onTurnCueCountdownDistanceChange(rounded.coerceIn(
              TuningPreferencesDefaults.MIN_TURN_CUE_COUNTDOWN_DISTANCE_METERS,
              TuningPreferencesDefaults.MAX_TURN_CUE_COUNTDOWN_DISTANCE_METERS
          ))
      },
      valueRange = TuningPreferencesDefaults.MIN_TURN_CUE_COUNTDOWN_DISTANCE_METERS..TuningPreferencesDefaults.MAX_TURN_CUE_COUNTDOWN_DISTANCE_METERS,
      steps = 26
  )
  ```

### Component 3: Dialog State & Persistence (`AdvancedTuningDialog.kt`)
* Add state:
  ```kotlin
  var turnCueCountdownDistanceMeters by remember { mutableFloatStateOf(TuningPreferencesDefaults.TURN_CUE_COUNTDOWN_DISTANCE_METERS) }
  ```
* In `applyConfig(config: TuningConfig)`:
  ```kotlin
  turnCueCountdownDistanceMeters = config.turnCueCountdownDistanceMeters
  ```
* In `newConfig` builder:
  ```kotlin
  turnCueCountdownDistanceMeters = turnCueCountdownDistanceMeters,
  ```
* Pass into `NavigationSection`:
  ```kotlin
  turnCueCountdownDistanceMeters = turnCueCountdownDistanceMeters,
  onTurnCueCountdownDistanceChange = { turnCueCountdownDistanceMeters = it },
  ```

### Component 4: 9-Language Localization
* Add `tuning_nav_cue_distance_title` and `tuning_nav_cue_distance_desc` across:
  - `values/strings.xml`
  - `values-de/strings.xml`
  - `values-es/strings.xml`
  - `values-fr/strings.xml`
  - `values-it/strings.xml`
  - `values-ja/strings.xml`
  - `values-nl/strings.xml`
  - `values-pl/strings.xml`
  - `values-pt/strings.xml`

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: `NavigationSection.kt` (`TuningSliderItem` for Route Selection Radius and Navigation Cue Transparency).
* **Reused components**: `TuningSliderItem` (standard Material 3 Slider with title, valueText, helperText, defaultText).
* **Theme tokens**: 14.dp column vertical spacing, `MaterialTheme.colorScheme.onSurface`, `MaterialTheme.typography.bodyMedium`.
* **New one-off styles & justification**: None. 100% compliant with `docs/design_guidelines.md` Section 5.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Add Strings Across 9 Locales
* Files: `app/src/main/res/values*/strings.xml`
* Define `tuning_nav_cue_distance_title` and `tuning_nav_cue_distance_desc`.

### Step 2: Implement DataStore Mutator
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStore.kt`
* Add `suspend fun updateTurnCueCountdownDistanceMeters(distanceMeters: Float)`.

### Step 3: Implement Navigation UI Controls
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/NavigationSection.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt`
* Add countdown distance slider, bind state, and persist upon save.

### Step 4: Unit & Contract Tests
* Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStoreTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/settings/TuningConfigTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnByTurnNavigationEngineTest.kt`
* Verify mutator, clamping, factory reset, and engine approaching trigger threshold.
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.trainingtracker.routes.TurnByTurnNavigationEngineTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Run targeted unit tests during construction, followed by full `./gradlew testDebugUnitTest` and on-device screenshot verification on Pixel 10 in Stage 5.
* **Rollback**: Branch isolation (`feature/ATT-3321`) allows full revert without affecting `sprint/2026-42.1`.
