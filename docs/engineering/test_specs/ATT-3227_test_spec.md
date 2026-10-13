# Stage 2: Requirement & Test Specification - ATT-3227: Smooth follow-me speed zoom and calibrate speed and bearing smoothing filters in advanced settings

**Ticket**: [ATT-3227](https://atrainingtracker.atlassian.net/browse/ATT-3227)  
**Sub-task**: [ATT-3296](https://atrainingtracker.atlassian.net/browse/ATT-3296) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.10`  
**Requirement Mapping**: `REQ-MAP-049` (*Follow-Me Camera Speed Zoom Smoothing, Zoom Hysteresis Deadband, and Configurable Speed/Bearing Filter Calibration*)  
**Test Spec ID**: `TST-MAP-051`  
**Branch**: `feature/ATT-3227`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Requirement Specification (REQ-MAP-049)

### 1.1 Problem Statement & Rationale
In Follow-Me tracking mode, `calculateFollowMeTargetZoom()` calculates camera zoom dynamically based on speed (`REQ-MAP-042`). However, raw instantaneous GPS speed was passed directly into the zoom calculation, causing the map camera to zoom in and out rapidly ("camera pumping") with every minor fluctuation in riding speed or cadence, inducing visual fatigue and disorientation. Furthermore, while bearing smoothing was performed via low-pass exponential filtering, its filter alpha was hardcoded (`alpha = 0.15f`), preventing athletes from tuning camera responsiveness to their sport or navigation preferences.

This requirement introduces low-pass exponential smoothing for speed before zoom evaluation, establishes a zoom hysteresis deadband ($\Delta \text{zoom} \ge 0.10f$) to suppress micro-animations, exposes configurable speed and bearing smoothing filter constants in Advanced Settings under Navigation, and enforces 100% localization parity across all 9 supported application locales.

### 1.2 Functional & Architectural Requirements
The system SHALL apply low-pass exponential smoothing to speed in Follow-Me tracking mode, enforce a zoom hysteresis deadband to suppress micro-animations, and provide configurable filter sliders for speed and bearing smoothing in Advanced Settings (ATT-3227, amending `REQ-MAP-042` and `REQ-MAP-048`, and aligning with Design Guidelines §3.1 & §6.1):

1. *Low-Pass Exponential Speed Smoothing (`MapBehaviors.kt`)*:
   - In `followMeController`, the system SHALL maintain smoothed speed state: `var filteredSpeed by remember { mutableFloatStateOf(speed) }`.
   - When `zoomFocus == MapZoomFocus.FOLLOW_ME` initializes, `filteredSpeed` SHALL snap to the incoming `speed`.
   - On location updates, `filteredSpeed` SHALL update via:
     $$\text{filteredSpeed} += \alpha_{\text{speed}} \times (\text{speed} - \text{filteredSpeed})$$
     clamping $\alpha_{\text{speed}}$ within $[0.02f, 0.50f]$.
   - `calculateFollowMeTargetZoom` SHALL consume `filteredSpeed` instead of raw instantaneous speed.

2. *Zoom Hysteresis Deadband (`MapBehaviors.kt`)*:
   - In `followMeController`, the system SHALL maintain `var activeZoom by remember { mutableFloatStateOf(baseZoom) }`.
   - When `speedZoomEnabled == false`, `activeZoom` SHALL lock strictly to `baseZoom`.
   - When `speedZoomEnabled == true`:
     $$\text{if } |\text{targetZoom} - \text{activeZoom}| \ge 0.10f \implies \text{activeZoom} = \text{targetZoom}$$
     otherwise retaining `activeZoom` without animation.
   - `activeZoom` SHALL be passed to `calculateFollowMeCameraTarget` and `CameraPosition.builder().zoom(activeZoom)`.

3. *Configurable Bearing Smoothing Alpha (`MapBehaviors.kt`)*:
   - `followMeController` SHALL accept parameter `bearingSmoothingAlpha: Float = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA`.
   - The bearing difference update SHALL use $\alpha_{\text{bearing}}$ clamped to $[0.05f, 0.50f]$, replacing hardcoded `0.15f`.

4. *Tuning Preferences Schema & Persistence (`TuningPreferencesDataStore.kt`)*:
   - `KEY_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA = floatPreferencesKey("tuning_map_follow_me_speed_smoothing_alpha")`:
     - Default: `0.10f`, Range: `0.02f` to `0.50f`.
   - `KEY_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA = floatPreferencesKey("tuning_map_follow_me_bearing_smoothing_alpha")`:
     - Default: `0.15f`, Range: `0.05f` to `0.50f`.
   - Include both keys in `TuningConfig`, `ALL_KEYS`, `tuningConfigFlow` with `.coerceIn()`, and `resetToDefaults()`.

5. *Advanced Settings UI Integration (`NavigationSection.kt` & `AdvancedTuningDialog.kt`)*:
   - In `NavigationSection.kt`, render `TuningSliderItem` for:
     - **Speed Smoothing**: displayed when `mapFollowMeSpeedZoomEnabled == true`. Range `0.02f..0.50f`, 24 steps (step `0.02f`), format `"%.2f"`.
     - **Bearing Smoothing**: displayed in Follow-Me camera settings. Range `0.05f..0.50f`, 9 steps (step `0.05f`), format `"%.2f"`.
   - Wire state and callbacks cleanly in `AdvancedTuningDialog.kt` and `ATrainingTrackerMap.kt`.

6. *9-Language Localization Parity*:
   - Title and description string resources (`tuning_map_speed_smoothing_title`, `tuning_map_speed_smoothing_desc`, `tuning_map_bearing_smoothing_title`, `tuning_map_bearing_smoothing_desc`) SHALL maintain 100% parity across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

7. *Preservation of System Invariants*:
   - Forward lookahead projection and tilt compensation (`REQ-MAP-048`) MUST remain intact.
   - Base zoom and cruising zoom speed curve calculation (`REQ-MAP-042`) MUST remain intact.
   - 100% clean-room test pass rate across the full test suite.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Amends `REQ-MAP-042` (*Speed-Dependent Map Zoom Calibration, Lookahead Padding & Tuning Preferences*, ATT-2946) and `REQ-MAP-048` (*Follow-Me Camera Target Forward Lookahead Projection Scaling*, ATT-3171).
2. *Historical Origin & Commit Trace*: Sprint 2026-41.6 (`ATT-2946`, commit `1c92d50e`) and Sprint 2026-41.9 (`ATT-3171`, commit `04bf2351`). Commit `300470962` originally set hardcoded `alpha = 0.15f` for bearing.
3. *Root Reason for Existing Formulation*: Instantaneous speed was directly used without low-pass filtering, causing continuous visual camera zoom pumping. Hardcoding bearing alpha was done for convenience during Compose migration.
4. *Preservation of Core Invariants*: Default bearing alpha remains `0.15f`, default speed alpha is `0.10f`, forward lookahead padding and tilt angle logic are preserved, and 100% full-suite unit test pass rate remains intact.

### 1.4 Acceptance Criteria (Given-When-Then)
- *Given* an active workout or TCX replay in `FOLLOW_ME` tracking mode with `speedZoomEnabled = true`,
- *When* instantaneous speed fluctuates with small noise ($\Delta \text{speed} < 1.0\text{ km/h}$),
- *Then* `activeZoom` remains unchanged due to the $0.10f$ hysteresis deadband, preventing jittery micro-animations.
- *Given* an athlete accelerating steadily from $10\text{ km/h}$ to $35\text{ km/h}$,
- *When* location updates arrive,
- *Then* `filteredSpeed` tracks the acceleration smoothly according to `speedSmoothingAlpha` and camera zoom scales progressively without abrupt steps.
- *Given* the athlete opening Advanced Settings -> Navigation,
- *When* adjusting the Speed Smoothing or Bearing Smoothing sliders,
- *Then* the slider values update reactively and persist to `TuningPreferencesDataStore`.
- *Given* the athlete tapping "Auf Werkseinstellungen zurücksetzen",
- *When* preferences reset,
- *Then* `mapFollowMeSpeedSmoothingAlpha` reverts to `0.10f` and `mapFollowMeBearingSmoothingAlpha` reverts to `0.15f`.

---

## 2. Test Specification (TST-MAP-051)

### 2.1 Scope & Test Architecture
The test suite validates:
1. **Mathematical Low-Pass Smoothing & Hysteresis Unit Tests (`FollowMeSmoothingTest.kt`)**:
   - Verify speed exponential smoothing updates.
   - Verify bearing exponential smoothing with custom alpha.
   - Verify zoom hysteresis deadband suppresses $\Delta \text{zoom} < 0.10f$.
   - Verify large speed transitions surpass the deadband and update `activeZoom`.
2. **DataStore Serialization & Default Reset Unit Tests (`TuningConfigTest.kt`)**:
   - Verify default values (`0.10f` for speed, `0.15f` for bearing).
   - Verify serialization/deserialization and range clamping.
   - Verify `resetToDefaults()` restores defaults.
3. **UI Contract & Slider Interaction Tests (`NavigationSectionTest.kt`)**:
   - Verify Speed Smoothing slider visibility is gated on `mapFollowMeSpeedZoomEnabled`.
   - Verify Bearing Smoothing slider renders with correct range and steps.
   - Verify callbacks fire on value change.
4. **9-Language Translation Parity Audit (`TranslationParityTest.kt`)**:
   - Verify 100% string presence across EN, DE, ES, FR, IT, JA, NL, PL, PT.
5. **Clean-Room Full Suite Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

---

## 3. ASPICE Traceability Matrix

| Requirement | Test Spec | Target Component | Status |
| :--- | :--- | :--- | :--- |
| `REQ-MAP-049.1` | `TST-MAP-051.1` | `MapBehaviors.kt` (Speed Exponential Smoothing) | Specified |
| `REQ-MAP-049.2` | `TST-MAP-051.2` | `MapBehaviors.kt` (Zoom Hysteresis Deadband) | Specified |
| `REQ-MAP-049.3` | `TST-MAP-051.3` | `MapBehaviors.kt` (Bearing Exponential Smoothing) | Specified |
| `REQ-MAP-049.4` | `TST-MAP-051.4` | `TuningPreferencesDataStore.kt` & `TuningConfig` | Specified |
| `REQ-MAP-049.5` | `TST-MAP-051.5` | `NavigationSection.kt` & `AdvancedTuningDialog.kt` | Specified |
| `REQ-MAP-049.6` | `TST-MAP-051.6` | `res/values-*/strings.xml` (9-Language Parity) | Specified |
| `REQ-PRO-001`   | `TST-MAP-051.7` | Full Clean-Room Regression Suite (`./gradlew testDebugUnitTest`) | Specified |
