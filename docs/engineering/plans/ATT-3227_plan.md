# Stage 3 Implementation Plan: ATT-3227 - Smooth follow-me speed zoom and calibrate speed and bearing smoothing filters in advanced settings

**Ticket**: [ATT-3227](https://atrainingtracker.atlassian.net/browse/ATT-3227)  
**Sub-task**: [ATT-3297](https://atrainingtracker.atlassian.net/browse/ATT-3297) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.10`  
**Requirement Mapping**: `REQ-MAP-049` (*Follow-Me Speed Zoom Exponential Smoothing, Zoom Hysteresis Deadband, and Configurable Filter Calibration*)  
**Test Spec ID**: `TST-MAP-051`  
**Branch**: `feature/ATT-3227`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Executive Summary & Architectural Overview

In Follow-Me tracking mode, `followMeController` in `MapBehaviors.kt` dynamically adjusts camera zoom based on vehicle/athlete speed. Previously, the controller passed raw, instantaneous speed directly into `calculateFollowMeTargetZoom()`. During cycling, running, or GPS jitter, rapid speed fluctuations caused continuous camera zoom oscillations ("camera pumping"). Furthermore, bearing smoothing used a hardcoded filter constant ($\alpha = 0.15$), giving athletes no way to calibrate camera rotation or zoom responsiveness.

`ATT-3227` introduces:
1. **Low-Pass Exponential Speed Smoothing**: Damps rapid speed fluctuations using a configurable filter constant ($\alpha_{\text{speed}} \in [0.02, 0.50]$, default `0.10f`).
2. **Zoom Hysteresis Deadband**: Enforces a minimum zoom delta threshold ($\Delta \text{zoom} \ge 0.10f$) before re-animating the camera zoom level, suppressing minor micro-animations.
3. **Configurable Filter Calibration**: Exposes `mapFollowMeSpeedSmoothingAlpha` and `mapFollowMeBearingSmoothingAlpha` in `TuningPreferencesDataStore` and adds Material 3 sliders in `NavigationSection` under Advanced Settings.
4. **9-Language Localization**: Full translation parity across EN, DE, ES, FR, IT, JA, NL, PL, and PT.

### Architectural Boundaries (SWE.2)
- **Settings & Persistence Layer**: `TuningPreferencesDefaults.kt`, `TuningConfig.kt`, `TuningPreferencesDataStore.kt`.
- **Map Behavior & Rendering Layer**: `MapBehaviors.kt` (`followMeController`), `ATrainingTrackerMap.kt`.
- **Settings UI**: `NavigationSection.kt`, `AdvancedTuningDialog.kt`.
- **Localization Layer**: `res/values-*/strings.xml` across all 9 supported locales.

---

## 2. UI Consistency (Rule 23)

In accordance with Rule 23 and `docs/design_guidelines.md` (Section 5):
- **Closest Existing Reference Screen**: Section 6 ("Navigation" / `NavigationSection.kt`) within `AdvancedTuningDialog.kt`.
- **Reused Components**:
  - `TuningSliderItem` (`com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningFormControls.kt`).
  - Standard Material 3 `Slider` integrated within the standardized tuning item card/row.
- **Theme Tokens & Design Specs**:
  - Spacing: `14.dp` vertical arrangement matching existing sliders (Initial Zoom, Cruising Zoom, Tilt, Lookahead Padding).
  - Typography: `MaterialTheme.typography.bodyMedium` for setting title, `MaterialTheme.typography.bodySmall` for helper description, `bodySmall` with `FontWeight.SemiBold` for value display.
  - Colors: `MaterialTheme.colorScheme.onSurface` for title, `MaterialTheme.colorScheme.onSurfaceVariant` for description, `MaterialTheme.colorScheme.primary` for slider active track/thumb.
- **Justification for New Styles**: Zero new or one-off styles. Reuses 100% existing tuning components and design system tokens.

---

## 3. Atomic Step Sequencing

### Step 1: DataStore Keys, Defaults & Configuration
- **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDefaults.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningConfig.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStore.kt`
- **Actions**:
  1. In `TuningPreferencesDefaults.kt`:
     - Add `const val DEFAULT_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA = 0.10f`
     - Add `const val MIN_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA = 0.02f`
     - Add `const val MAX_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA = 0.50f`
     - Add `const val DEFAULT_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA = 0.15f`
     - Add `const val MIN_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA = 0.05f`
     - Add `const val MAX_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA = 0.50f`
  2. In `TuningConfig.kt`:
     - Add `val mapFollowMeSpeedSmoothingAlpha: Float = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA`
     - Add `val mapFollowMeBearingSmoothingAlpha: Float = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA`
  3. In `TuningPreferencesDataStore.kt`:
     - Add `KEY_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA = floatPreferencesKey("tuning_map_follow_me_speed_smoothing_alpha")`
     - Add `KEY_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA = floatPreferencesKey("tuning_map_follow_me_bearing_smoothing_alpha")`
     - Add both keys to `ALL_KEYS`.
     - In `tuningConfigFlow`: map keys with defaults and range clamping.
     - In `saveTuningConfig()`: persist both float preferences.
  4. In `TuningConfigTest.kt`:
     - Update test cases to verify defaults, custom values, and serialization.
- **Verification**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.TuningConfigTest"`

### Step 2: Map Controller Speed Smoothing & Hysteresis
- **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapBehaviors.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ATrainingTrackerMap.kt`
- **Actions**:
  1. In `MapBehaviors.kt`:
     - Extend `followMeController` parameter list:
       - `speedSmoothingAlpha: Float = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA`
       - `bearingSmoothingAlpha: Float = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA`
     - Add `var filteredSpeed by remember { mutableFloatStateOf(speed) }` and `var activeZoom by remember { mutableFloatStateOf(baseZoom) }`.
     - On `zoomFocus == MapZoomFocus.FOLLOW_ME` reset, re-seed `filteredSpeed = speed` and `activeZoom = baseZoom`.
     - In `LaunchedEffect`, replace hardcoded bearing alpha with `bearingSmoothingAlpha.coerceIn(0.05f, 0.50f)`.
     - Update `filteredSpeed`: `filteredSpeed += speedSmoothingAlpha.coerceIn(0.02f, 0.50f) * (speed - filteredSpeed)`.
     - Calculate `targetZoom = calculateFollowMeTargetZoom(speedMps = filteredSpeed, baseZoom = baseZoom, speedZoomEnabled = speedZoomEnabled, cruisingZoom = cruisingZoom)`.
     - Apply zoom deadband: if `Math.abs(targetZoom - activeZoom) >= 0.10f`, update `activeZoom = targetZoom`.
     - Pass `activeZoom` into camera target projection and `CameraPosition.builder().zoom(activeZoom)`.
  2. In `ATrainingTrackerMap.kt`:
     - Pass `tuningConfig.mapFollowMeSpeedSmoothingAlpha` and `tuningConfig.mapFollowMeBearingSmoothingAlpha` to `followMeController`.
- **Verification**: Unit tests for low-pass smoothing and hysteresis.

### Step 3: Advanced Settings Navigation UI Integration
- **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/NavigationSection.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt`
- **Actions**:
  1. In `NavigationSection.kt`:
     - Add parameters:
       - `mapFollowMeSpeedSmoothingAlpha: Float = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA`
       - `onMapFollowMeSpeedSmoothingAlphaChange: (Float) -> Unit = {}`
       - `mapFollowMeBearingSmoothingAlpha: Float = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA`
       - `onMapFollowMeBearingSmoothingAlphaChange: (Float) -> Unit = {}`
     - When `mapFollowMeSpeedZoomEnabled` is true, render `TuningSliderItem` for Speed Smoothing:
       - Range: `0.02f..0.50f`, step snapping `0.02f`, 23 steps.
     - Render `TuningSliderItem` for Bearing Smoothing:
       - Range: `0.05f..0.50f`, step snapping `0.05f`, 8 steps.
  2. In `AdvancedTuningDialog.kt`:
     - Add state variables: `var mapFollowMeSpeedSmoothingAlpha by remember { mutableFloatStateOf(...) }` and `var mapFollowMeBearingSmoothingAlpha by remember { mutableFloatStateOf(...) }`.
     - In `applyConfig()`, load both alphas from `config`.
     - In `AppDialogActions.SaveCancel`, pass both alphas to `TuningConfig(...)`.
     - In `NavigationSection(...)`, pass state and setter lambdas.

### Step 4: 9-Language Localization Parity
- **Target Files**:
  - `app/src/main/res/values/strings.xml` (EN)
  - `app/src/main/res/values-de/strings.xml` (DE)
  - `app/src/main/res/values-es/strings.xml` (ES)
  - `app/src/main/res/values-fr/strings.xml` (FR)
  - `app/src/main/res/values-it/strings.xml` (IT)
  - `app/src/main/res/values-ja/strings.xml` (JA)
  - `app/src/main/res/values-nl/strings.xml` (NL)
  - `app/src/main/res/values-pl/strings.xml` (PL)
  - `app/src/main/res/values-pt/strings.xml` (PT)
- **Strings to Add**:
  - `tuning_map_follow_me_speed_smoothing_title`
  - `tuning_map_follow_me_speed_smoothing_desc`
  - `tuning_map_follow_me_bearing_smoothing_title`
  - `tuning_map_follow_me_bearing_smoothing_desc`
- **Verification**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`

### Step 5: Unit Tests Construction & Targeted Execution
- **Target Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/FollowMeSmoothingTest.kt`
- **Actions**:
  - Test speed low-pass filtering logic across varying step inputs.
  - Test bearing low-pass angular filtering logic across wrap-around boundaries (0° / 360°).
  - Test zoom hysteresis deadband ($< 0.10f$ discarded, $\ge 0.10f$ applied).
- **Verification**: Targeted unit test execution asserting 100% pass.

---

## 4. Invariants & Rollback Safety

1. **Camera Animation Stability**: Camera zoom animations must never oscillate or fight between frames. The 0.10f zoom deadband guarantees convergence.
2. **Backward Compatibility**: If no preferences are set, defaults `0.10f` (speed) and `0.15f` (bearing) match canonical behavior.
3. **Range Invariants**: Speed alpha is clamped to $[0.02f, 0.50f]$; bearing alpha is clamped to $[0.05f, 0.50f]$. Values outside this range cannot cause division by zero or runaway divergence.
4. **Conditional UI**: The speed smoothing slider is only visible when speed-dependent zoom is enabled (`mapFollowMeSpeedZoomEnabled == true`).
