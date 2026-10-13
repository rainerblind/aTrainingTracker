# Stage 2: Requirement & Test Specification - ATT-3321: Expose turn cue countdown distance slider in advanced navigation settings

**Ticket**: [ATT-3321](https://rainerblind.atlassian.net/browse/ATT-3321)  
**Sub-task**: [ATT-3346](https://rainerblind.atlassian.net/browse/ATT-3346) (`[Test-Spec]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*[Epic] Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-287` (amending Clause 2), `REQ-MAP-028` (complementing Clause 7), `REQ-SET-073`  
**Test Mapping**: `TST-UI-247` (amending Clause 1 & 3)  
**Branch**: `feature/ATT-3321`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-12  

---

## 1. Detailed Requirements & Architectural Constraints

### 1.1 Formal Specification (REQ-UI-287 Amendment & REQ-SET-073 Complement)

The system SHALL expose the turn cue countdown distance parameter as an interactive Material 3 slider in **Advanced Settings -> Navigation**, persist adjustments in `TuningPreferencesDataStore`, and dynamically apply the distance threshold during active turn-by-turn navigation (ATT-3321, amending `REQ-UI-287` Clause 2 and complementing `REQ-MAP-028` Clause 7 and `REQ-SET-073`):

1. **Preference Definition & DataStore Mutator (`TuningPreferencesDataStore.kt`)**:
   * The system SHALL maintain `TuningPreferencesDefaults.TURN_CUE_COUNTDOWN_DISTANCE_METERS = 100.0f`, `MIN_TURN_CUE_COUNTDOWN_DISTANCE_METERS = 30.0f`, and `MAX_TURN_CUE_COUNTDOWN_DISTANCE_METERS = 300.0f`.
   * `TuningPreferencesDataStore` SHALL expose a dedicated mutator `suspend fun updateTurnCueCountdownDistanceMeters(distanceMeters: Float)` that clamps input values to `[30.0f, 300.0f]`.
   * Invoking `resetToDefaults()` in `TuningPreferencesDataStore` SHALL restore `turnCueCountdownDistanceMeters` back to `100.0f`.

2. **Advanced Settings UI Slider Integration (`NavigationSection.kt` & `AdvancedTuningDialog.kt`)**:
   * In `NavigationSection.kt`, the system SHALL render a `TuningSliderItem` for "Navigation Cue Distance" (*Distanz für Abbiegehinweise*):
     * Title: `stringResource(R.string.tuning_nav_cue_distance_title)`.
     * Value format: `"${turnCueCountdownDistanceMeters.roundToInt()} m"`.
     * Helper text: `stringResource(R.string.tuning_nav_cue_distance_desc)`.
     * Default text: `stringResource(R.string.tuning_default_format, "100 m")`.
     * Value range: `30.0f .. 300.0f`.
     * Resolution: 10 m increments (26 discrete steps, snapping to nearest 10 m via `(snapped / 10f).roundToInt() * 10f`).
   * In `AdvancedTuningDialog.kt`:
     * Declare `var turnCueCountdownDistanceMeters by remember { mutableFloatStateOf(TuningPreferencesDefaults.TURN_CUE_COUNTDOWN_DISTANCE_METERS) }`.
     * Bind `applyConfig(config)` to populate `turnCueCountdownDistanceMeters = config.turnCueCountdownDistanceMeters`.
     * Pass `turnCueCountdownDistanceMeters` and setter callback to `NavigationSection`.
     * In `onSave`, pass `turnCueCountdownDistanceMeters = turnCueCountdownDistanceMeters` into `newConfig = TuningConfig(...)` to ensure user adjustments are strictly preserved and saved to `TuningPreferencesDataStore`.

3. **Dynamic Navigation Engine Reactivity (`TurnByTurnNavigationEngine.kt`)**:
   * `TurnByTurnNavigationEngine.kt` line 172 consumes `countdownThreshold = tuningConfig.turnCueCountdownDistanceMeters.toDouble()`.
   * Setting `countdownThreshold` dynamically triggers `isApproaching = (upcomingCue != null && distToCue <= countdownThreshold)`.
   * When approaching an upcoming turn cue, `TurnPromptBanner.kt` SHALL appear when `distToCue <= countdownThreshold`.

4. **9-Language Localization Parity**:
   * String resources `tuning_nav_cue_distance_title` and `tuning_nav_cue_distance_desc` SHALL be defined across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing strings.

5. **Preservation of System Invariants**:
   * Immediate turn confirmation distance ($\le 25\text{ m}$) MUST remain constant.
   * Off-route corridor detection and recovery thresholds MUST remain unchanged.
   * Atomic factory reset (`resetToDefaults()`) MUST restore default 100 m.
   * 100% clean-room test pass rate across all test suites.

---

### 1.2 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (UI Slider Presentation & Formatting)**:
  * *Given* an athlete navigating to **Einstellungen -> Experten-Einstellungen -> Navigation**,
  * *When* viewing the navigation settings accordion,
  * *Then* a slider for "Navigation Cue Distance" / "Distanz für Abbiegehinweise" SHALL be visible with range 30 m to 300 m and default 100 m.

* **Criterion 2 (Granular Step Adjustments)**:
  * *Given* the athlete drags the slider,
  * *When* adjusting to any position,
  * *Then* the value SHALL snap cleanly to the nearest 10 m increment (e.g. 30 m, 40 m, ..., 100 m, ..., 200 m, ..., 300 m).

* **Criterion 3 (Dynamic Countdown Trigger)**:
  * *Given* the athlete sets the cue distance to 200 m and saves,
  * *When* approaching a turn cue during active route navigation at distance 195 m,
  * *Then* `TurnPromptBanner` appears and begins its countdown, whereas under the default 100 m setting it would not trigger until 100 m.

* **Criterion 4 (Preference Persistence & Factory Reset)**:
  * *Given* the athlete modifies the cue distance to 250 m and saves,
  * *When* the app restarts,
  * *Then* `turnCueCountdownDistanceMeters` remains 250.0f,
  * *When* the athlete taps "Auf Werkseinstellungen zurücksetzen",
  * *Then* `turnCueCountdownDistanceMeters` reverts to 100.0f.

---

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-287` (*Navigation Cue Overlay Transparency, Dismiss Duration & Expert Settings Configuration*), amending Clause 2, and `REQ-SET-073` (*DataStore Expert Tuning Repository*).
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-41.2 (`ATT-2632`, commit `8e6bcfc5`), refining earlier groundwork from Sprint 2026-40.16 (`ATT-1450`, commit `54c5c2d3` / `REQ-MAP-028`).
* **Root Reason for Existing Formulation**: In ATT-1450, `turnCueCountdownDistanceMeters` was introduced as an internal configuration parameter and defaulted to 100 meters, but was not given a UI slider in ATT-2632 when transparency and auto-dismiss duration were exposed. Athletes travelling at high cycling speeds have repeatedly expressed the need for earlier warning windows, while urban athletes prefer tighter thresholds.
* **Preservation of Core Invariants**: The default value of 100 meters (`TURN_CUE_COUNTDOWN_DISTANCE_METERS = 100.0f`) is strictly preserved so existing users experience zero behavioral regression unless they explicitly adjust the slider. Safety clamping bounds (`30.0f .. 300.0f`), atomic factory reset (`resetToDefaults()`), and 100% full-suite test pass rate remain strictly preserved.

---

## 2. Concrete Test Specifications & Verification Procedures

### 2.1 Preference & DataStore Unit Tests (`TuningPreferencesDataStoreTest.kt` & `TuningConfigTest.kt`)

* **TST-UI-247.1d: Default value verification**:
  * Verify `TuningPreferencesDefaults.TURN_CUE_COUNTDOWN_DISTANCE_METERS == 100.0f`.
  * Verify `TuningConfig().turnCueCountdownDistanceMeters == 100.0f`.
* **TST-UI-247.1e: Mutator clamping & persistence**:
  * Invoke `updateTurnCueCountdownDistanceMeters(200.0f)`, verify DataStore emits 200.0f.
  * Invoke `updateTurnCueCountdownDistanceMeters(10.0f)`, verify value is clamped to minimum 30.0f.
  * Invoke `updateTurnCueCountdownDistanceMeters(500.0f)`, verify value is clamped to maximum 300.0f.
* **TST-UI-247.1f: Factory reset**:
  * Modify cue distance to 250.0f.
  * Invoke `resetToDefaults()`, verify value reverts to 100.0f.

### 2.2 Navigation Engine Verification (`TurnByTurnNavigationEngineTest.kt`)

* **TST-UI-247.2d: Dynamic countdown threshold reactivity**:
  * Create `TurnByTurnNavigationEngine` with `turnCueCountdownDistanceMeters = 200.0f`.
  * Simulate athlete position at 180 m from turn cue: assert `isApproaching == true`.
  * Create `TurnByTurnNavigationEngine` with `turnCueCountdownDistanceMeters = 50.0f`.
  * Simulate athlete position at 180 m from turn cue: assert `isApproaching == false`.
  * Simulate athlete position at 45 m from turn cue: assert `isApproaching == true`.

### 2.3 9-Language Localization Audit (`TranslationParityTest.kt`)

* **TST-UI-247.3b: Localization parity**:
  * Verify string keys `tuning_nav_cue_distance_title` and `tuning_nav_cue_distance_desc` exist across EN, DE, ES, FR, IT, JA, NL, PL, and PT with 0 missing entries.

### 2.4 Clean-Room Full Suite Regression Execution

* Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate across all unit test suites.

---

## 3. Traceability Matrix

| Requirement Clause | Test Specification | Target File | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-UI-287.2` (Slider in NavigationSection) | `TST-UI-247.1e` | `NavigationSection.kt`, `AdvancedTuningDialog.kt` | UI / Compose Contract Test |
| `REQ-UI-287.1` (DataStore Mutator & Reset) | `TST-UI-247.1d`, `TST-UI-247.1e`, `TST-UI-247.1f` | `TuningPreferencesDataStore.kt` | Unit Test |
| `REQ-MAP-028.2` (Engine Countdown Threshold) | `TST-UI-247.2d` | `TurnByTurnNavigationEngineTest.kt` | Unit Test |
| `REQ-UI-287.5` (9-Language Parity) | `TST-UI-247.3b` | `res/values-*/strings.xml` | String Parity Audit |
| `REQ-PRO-001` (Clean-Room Regression) | Clean-Room Suite | `./gradlew testDebugUnitTest` | Full Suite Execution |
