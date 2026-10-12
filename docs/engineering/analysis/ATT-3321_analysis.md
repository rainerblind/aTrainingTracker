# Stage 1 Analysis: ATT-3321 - Expose turn cue countdown distance slider in advanced navigation settings

**Ticket**: [ATT-3321](https://rainerblind.atlassian.net/browse/ATT-3321)  
**Sub-task**: [ATT-3345](https://rainerblind.atlassian.net/browse/ATT-3345) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*[Epic] Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Branch**: `feature/ATT-3321`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-12  

---

## 1. Problem Statement & Motivation

During active route navigation, the turn-by-turn guidance engine (`TurnByTurnNavigationEngine.kt`) triggers the glanceable navigation cue banner (`TurnPromptBanner.kt`) when the athlete's distance along the route to an upcoming turn vertex drops below a countdown threshold:
```kotlin
val countdownThreshold = tuningConfig.turnCueCountdownDistanceMeters.toDouble()
val isApproaching = upcomingCue != null && distToCue <= countdownThreshold
```

Currently, `turnCueCountdownDistanceMeters` is defined in `TuningPreferencesDataStore.kt` with key `KEY_TURN_CUE_COUNTDOWN_DISTANCE` and default value `100.0f` (bounded between `30.0f` and `300.0f`). However, this setting is currently not exposed in the Advanced Settings UI (`NavigationSection.kt` inside `AdvancedTuningDialog.kt`). Furthermore, because `AdvancedTuningDialog.kt` does not track or preserve `turnCueCountdownDistanceMeters` in its local state during save operations, saving settings from the dialog can inadvertently overwrite this parameter with the constructor default.

Athletes operating across different sports and speeds require tailored notification windows:
* High-speed cyclists (e.g. 35+ km/h or descending mountain roads) cover 100 meters in under 10 seconds and need earlier alerts (200–300 m) to safely evaluate approaching junctions, road conditions, and brake in time.
* Runners, hikers, and urban cyclists prefer shorter countdown windows (30–50 m) to prevent premature distraction at dense city intersections where multiple streets diverge closely together.

Exposing this setting as an interactive Material 3 slider in **Advanced Settings -> Navigation** gives athletes explicit, granular control over their navigation notification timing.

---

## 2. Root Cause Analysis (Forensic Investigation & Architectural Gap)

1. **Backend Infrastructure Already Present**:
   - `TuningPreferencesDefaults.kt`:
     - `TURN_CUE_COUNTDOWN_DISTANCE_METERS = 100.0f`
     - `MIN_TURN_CUE_COUNTDOWN_DISTANCE_METERS = 30.0f`
     - `MAX_TURN_CUE_COUNTDOWN_DISTANCE_METERS = 300.0f`
   - `TuningConfig.kt`:
     - `val turnCueCountdownDistanceMeters: Float = TuningPreferencesDefaults.TURN_CUE_COUNTDOWN_DISTANCE_METERS`
   - `TuningPreferencesDataStore.kt`:
     - `KEY_TURN_CUE_COUNTDOWN_DISTANCE = floatPreferencesKey("tuning_turn_cue_countdown_distance")`
     - Already wired into `ALL_KEYS`, `tuningConfigFlow`, and `updateTuningConfig(config)`.
   - `TurnByTurnNavigationEngine.kt`:
     - Already consumes `tuningConfig.turnCueCountdownDistanceMeters.toDouble()` dynamically at line 172.

2. **Missing UI Control & State Binding**:
   - `NavigationSection.kt`:
     - Does not provide a `TuningSliderItem` for turn cue countdown distance.
   - `AdvancedTuningDialog.kt`:
     - Does not declare `var turnCueCountdownDistanceMeters` in remember state.
     - Does not bind `turnCueCountdownDistanceMeters = config.turnCueCountdownDistanceMeters` in `applyConfig`.
     - In `onSave`, instantiates `TuningConfig(...)` without passing `turnCueCountdownDistanceMeters`, resulting in fallback to the constructor default rather than respecting the persisted value.
   - `TuningPreferencesDataStore.kt`:
     - Lacks a standalone mutator `suspend fun updateTurnCueCountdownDistanceMeters(distanceMeters: Float)` for targeted preference updates.

3. **Missing Localized String Resources**:
   - No string resources exist for the countdown slider title and description (`tuning_nav_cue_distance_title`, `tuning_nav_cue_distance_desc`) across any of the 9 supported locales.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * **UI Slider Integration (`NavigationSection.kt`)**:
    - Add a `TuningSliderItem` in `NavigationSection.kt`:
      - Title: `stringResource(R.string.tuning_nav_cue_distance_title)`
      - Value format: formatted integer distance (e.g. `100 m`, `200 m`)
      - Helper text: `stringResource(R.string.tuning_nav_cue_distance_desc)`
      - Default text: formatted default (`Default: 100 m` / `Standard: 100 m`)
      - Value range: `30.0f .. 300.0f`
      - Step resolution: 10 m increments (26 discrete slider steps, snapped to multiples of 10)
  * **State Wiring in `AdvancedTuningDialog.kt`**:
    - Add `turnCueCountdownDistanceMeters` mutable state initialized from `TuningPreferencesDefaults.TURN_CUE_COUNTDOWN_DISTANCE_METERS`.
    - Update `applyConfig(config)` to populate `turnCueCountdownDistanceMeters`.
    - Pass value and setter callback `onTurnCueCountdownDistanceChange` to `NavigationSection`.
    - Include `turnCueCountdownDistanceMeters = turnCueCountdownDistanceMeters` in `newConfig = TuningConfig(...)` on save.
  * **DataStore Mutator (`TuningPreferencesDataStore.kt`)**:
    - Add `suspend fun updateTurnCueCountdownDistanceMeters(distanceMeters: Float)` with bounds clamping to `[30.0f, 300.0f]`.
  * **9-Language Localization Parity**:
    - Add `tuning_nav_cue_distance_title` and `tuning_nav_cue_distance_desc` across EN, DE, ES, FR, IT, JA, NL, PL, and PT.
  * **Verification**:
    - Add unit tests verifying slider range clamping, persistence, and navigation engine reactivity.
    - Execute clean-room full suite `./gradlew testDebugUnitTest`.
    - Update living documentation (`docs/requirements.md` / `REQ-UI-287` and `docs/tests.md` / `TST-UI-247`).

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to geometric turn cue synthesis math in `TurnCueDetector.kt`.
  * No changes to audio alert playback frequencies or tones in `NavigationAudioAlertManager.kt`.
  * No modification to `TurnPromptBanner.kt` layout hierarchy or Material 3 container styles.
  * No database schema migrations.

---

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-287` (*Navigation Cue Overlay Transparency, Dismiss Duration & Expert Settings Configuration*), amending Clause 2, and `REQ-SET-073` (*DataStore Expert Tuning Repository*).
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-41.2 (`ATT-2632`, commit `8e6bcfc5`), refining earlier groundwork from Sprint 2026-40.16 (`ATT-1450`, commit `54c5c2d3` / `REQ-MAP-028`).
* **Root Reason for Existing Formulation**: In ATT-1450, `turnCueCountdownDistanceMeters` was introduced as an internal configuration parameter and defaulted to 100 meters, but was not given a UI slider in ATT-2632 when transparency and auto-dismiss duration were exposed. Athletes travelling at high cycling speeds have repeatedly expressed the need for earlier warning windows, while urban athletes prefer tighter thresholds.
* **Preservation of Core Invariants**: The default value of 100 meters (`TURN_CUE_COUNTDOWN_DISTANCE_METERS = 100.0f`) is strictly preserved so existing users experience zero behavioral regression unless they explicitly adjust the slider. Safety clamping bounds (`30.0f .. 300.0f`), atomic factory reset (`resetToDefaults()`), and 100% full-suite test pass rate remain strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

1. **Slider Specification in `NavigationSection.kt`**:
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

2. **State & Persistence Wiring in `AdvancedTuningDialog.kt`**:
   - Manage `turnCueCountdownDistanceMeters` via Compose `mutableFloatStateOf`.
   - Propagate to `NavigationSection`.
   - Include in `TuningConfig` during `saveTuningConfig(newConfig)`.

3. **DataStore Mutator (`TuningPreferencesDataStore.kt`)**:
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

4. **Localization Resource Parity**:
   Define `tuning_nav_cue_distance_title` and `tuning_nav_cue_distance_desc` across all 9 languages.

---

## 6. Verification & Test Plan

1. **Targeted Unit Tests**:
   - `TuningConfigTest.kt` / `TuningPreferencesDataStoreTest.kt`:
     - Test default value equals 100.0f.
     - Test mutation and clamping to `[30.0f, 300.0f]`.
     - Test reset to defaults restores 100.0f.
   - `TurnByTurnNavigationEngineTest.kt`:
     - Test cue countdown trigger activates at custom distance (e.g. 200 m instead of 100 m) when configured in `tuningConfig`.
2. **Translation Parity Test**:
   - Verify `TranslationParityTest.kt` passes with 0 missing strings across all 9 locales.
3. **Clean-Room Regression**:
   - Execute full test suite `./gradlew testDebugUnitTest`.
4. **Physical Device Verification (Pixel 10)**:
   - Deploy debug build to Pixel 10.
   - Navigate to **Einstellungen -> Experten-Einstellungen -> Navigation**.
   - Verify slider renders cleanly, displays current value, and adjusts in 10 m increments.
