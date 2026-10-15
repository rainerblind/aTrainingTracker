# Stage 5 Walkthrough - ATT-3581: Calibrate Follow-Me High-Speed Zoom with Logarithmic Scaling and Remove Camera Sliders from Advanced Settings

**Ticket**: [ATT-3581](https://rainerblind.atlassian.net/browse/ATT-3581)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Active Sprint**: `2026-42.5` (Sprint ID `372`)  
**Target Release**: `V4.9.40`  
**Requirement Mapping**: `REQ-MAP-050` (*Calibrated Piecewise Linear-to-Logarithmic Follow-Me High-Speed Zoom and Autonomous Camera Control*)  
**Test Spec ID**: `TST-MAP-052` (*Follow-Me Piecewise Logarithmic Zoom Calibration & UI Simplification Verification Suite*)  
**Branch**: `feature/ATT-3581`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Executive Summary

Ticket ATT-3581 resolves a critical map visualization issue where Follow-Me camera tracking at cycling descent speeds (40–60+ km/h) suffered from exponential viewport blowout due to Google Maps' logarithmic zoom scale:
1. **Piecewise Linear-to-Logarithmic Zoom Curve (`MapBehaviors.kt`)**:
   - Preserves the intuitive linear zoom progression between $0\text{ km/h}$ ($20.0f$) and $20\text{ km/h}$ ($18.0f$) for standstill, walking, and uphill climbing.
   - Transitions above $20\text{ km/h}$ to base-2 logarithmic scaling ($\text{cruisingZoom} - \log_2(\text{speedKmh} / 20.0)$), ensuring visible lookahead distance grows linearly with speed (constant time horizon in seconds) rather than exponentially.
   - Raises the minimum zoom floor from $12.0f$ to $16.0f$ (`DEFAULT_MAP_FOLLOW_ME_MIN_ZOOM = 16.0f`), guaranteeing street-level resolution even on extreme high-speed descents ($\ge 80\text{ km/h}$).
2. **Autonomous Camera Controller Wiring (`ATrainingTrackerMap.kt`)**:
   - Follow-Me controller directly consumes calibrated physical defaults from `TuningPreferencesDefaults`.
   - Prevents stale or legacy DataStore tweaks from overriding the physically calibrated camera curve.
3. **Streamlined Navigation Advanced Settings UI (`NavigationSection.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`)**:
   - Removed the obsolete Follow-Me camera tuning sub-section and its 7 sliders/toggles (`Initial Zoom`, `Speed-Dependent Zoom Enabled`, `Cruising Speed Zoom`, `Tilt Angle`, `Lookahead Bottom Padding`, `Speed Smoothing`, `Bearing Smoothing`).
   - Retained active athlete navigation controls: `Route Selection Radius`, `Navigation Cue Dismiss Duration`, `Turn Cue Countdown Distance`, and `Auto-Follow Unique Route`.
4. **DataStore Lifecycle Migration & Startup Purge**:
   - Added `purgeLegacyMapCameraPreferences()` invoked automatically on application startup (`TuningPreferencesDataStore.init`) and when accessing Advanced Settings (`LaunchedEffect(Unit)` in `AdvancedTuningScreen` and `AdvancedTuningDialog`).
   - Added companion helper `purgeLegacyMapCameraKeys(prefs: MutablePreferences)`.
5. **9-Language Resource Cleanup**:
   - Purged all 15 obsolete string resources from all 9 locale resource files (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

---

## 2. Changes Implemented

### 2.1 Piecewise Linear-to-Logarithmic Speed Zoom (`MapBehaviors.kt`)
Replaced the unbounded linear speed zoom with piecewise linear-to-logarithmic scaling:
```kotlin
fun calculateFollowMeTargetZoom(
    speedMps: Float,
    baseZoom: Float = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_INITIAL_ZOOM,
    cruisingZoom: Float = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_CRUISING_ZOOM,
    speedZoomEnabled: Boolean = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_SPEED_ZOOM_ENABLED,
    minZoom: Float = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_MIN_ZOOM,
    maxZoom: Float = TuningPreferencesDefaults.MAX_MAP_FOLLOW_ME_INITIAL_ZOOM
): Float {
    if (!speedZoomEnabled) {
        return baseZoom.coerceIn(minZoom, maxZoom)
    }

    val speedKmh = (speedMps * 3.6f).coerceAtLeast(0.0f)
    val rawZoom = if (speedKmh <= 20.0f) {
        val zoomSlope = (baseZoom - cruisingZoom) / 20.0f
        baseZoom - zoomSlope * speedKmh
    } else {
        val logScale = (baseZoom - cruisingZoom) / 2.0f
        val logDelta = logScale * (ln(speedKmh / 20.0f) / ln(2.0f))
        cruisingZoom - logDelta
    }

    return rawZoom.coerceIn(minZoom, maxZoom)
}
```

### 2.2 Tuning Preferences & Defaults (`TuningPreferencesDataStore.kt`)
* Added `DEFAULT_MAP_FOLLOW_ME_MIN_ZOOM = 16.0f` to `TuningPreferencesDefaults`.
* Deprecated legacy camera keys and update methods.
* Added `LEGACY_MAP_CAMERA_KEYS`, `purgeLegacyMapCameraKeys(prefs: MutablePreferences)`, and `purgeLegacyMapCameraPreferences()`.
* Added startup auto-purge in `TuningPreferencesDataStore.init` and settings screen lifecycle hooks.
* Added `mapFollowMePreferences()` mapping to calibrated constants, decoupling `tuningConfigFlow` from legacy keys.

### 2.3 Navigation Tuning UI Clean-Up (`NavigationSection.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`)
* In `NavigationSection.kt`: Removed `tuning_map_camera_section_title` header, divider, and all 7 camera slider composables. Verified zero unused imports.
* In `AdvancedTuningScreen.kt` & `AdvancedTuningDialog.kt`: Removed local camera state variables, `applyConfig` bindings, `onSaveSettings` persists, and passed parameters. Added `LaunchedEffect(Unit) { tuningDataStore.purgeLegacyMapCameraPreferences() }`.

### 2.4 Autonomous Controller Wiring (`ATrainingTrackerMap.kt`)
* Simplified `followMeController(...)` call to pass canonical defaults directly from `TuningPreferencesDefaults`.

### 2.5 9-Locale Resource Cleanup (Rule 29)
Removed the 15 obsolete string resources across all 9 `strings.xml` files:
`tuning_map_camera_section_title`, `tuning_map_base_zoom_title`, `tuning_map_base_zoom_desc`, `tuning_map_speed_zoom_enabled_title`, `tuning_map_speed_zoom_enabled_desc`, `tuning_map_cruising_zoom_title`, `tuning_map_cruising_zoom_desc`, `tuning_map_tilt_angle_title`, `tuning_map_tilt_angle_desc`, `tuning_map_lookahead_padding_title`, `tuning_map_lookahead_padding_desc`, `tuning_map_follow_me_speed_smoothing_title`, `tuning_map_follow_me_speed_smoothing_desc`, `tuning_map_follow_me_bearing_smoothing_title`, `tuning_map_follow_me_bearing_smoothing_desc`.

---

## 3. Verification & Test Evidence

### 3.1 Targeted Test Execution
1. **`FollowMeCameraZoomTest`**:
   - `testCalculateFollowMeTargetZoom_lowSpeedLinear`: PASSED (0 km/h -> 20.0f, 10 km/h -> 19.0f, 20 km/h -> 18.0f)
   - `testCalculateFollowMeTargetZoom_highSpeedLogarithmic`: PASSED (30 km/h -> 17.42f, 40 km/h -> 17.00f, 50 km/h -> 16.68f, 60 km/h -> 16.42f, 80 km/h -> 16.00f)
   - `testCalculateFollowMeTargetZoom_highSpeedClampingAt16`: PASSED (90 km/h -> 16.00f clamped)
   - `testCalculateFollowMeTargetZoom_disabledLocksToBaseZoom`: PASSED
   - `testCalculateFollowMeTargetZoom_customClamping`: PASSED
2. **`AdvancedTuningVisualContractTest`**:
   - `navigationSection_omitsMapCameraSlidersAndTitle_andRetainsNavigationControls`: PASSED
3. **`FollowMePaddingContractTest` & `FollowMeUnifiedAnchorContractTest`**:
   - Verified canonical controller default parameters: PASSED
4. **`MapCameraTuningPreferencesTest`**:
   - `defaultConstants_matchExpectedSpecification`: PASSED
   - `mapFollowMePreferences_returnsCalibratedDefaultsRegardlessOfLegacyPreferences`: PASSED
   - `purgeLegacyMapCameraKeys_removesAllSevenLegacyKeys`: PASSED
5. **`MapCameraLocalizationTest`**:
   - `testMapCameraStrings_purgedAcrossAll9Locales`: PASSED (asserts complete removal of all 15 obsolete string keys across EN, DE, ES, FR, IT, JA, NL, PL, and PT)

### 3.2 Full Clean-Room Regression Suite
* **Command**: `./gradlew testDebugUnitTest`
* **Result**: `BUILD SUCCESSFUL` with 100% pass rate across the entire test suite.

### 3.3 Requirement Governance
* **Command**: `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-42.5`
* **Result**: Passed cleanly with living documents (`REQ-MAP-050` and `TST-MAP-052`) updated to `Verified`.

---

## 4. UI & Zoom Progression Comparison

### 4.1 Zoom Curve Progression Comparison
| Speed (km/h) | Previous Linear Zoom | Previous Viewport vs. 20 km/h ($2^{\Delta z}$) | New Calibrated Zoom | New Viewport vs. 20 km/h ($2^{\Delta z}$) | Lookahead Perception |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **0 km/h** | 20.0f | $0.25\times$ | **20.0f** | $0.25\times$ | Standstill / tight detail |
| **10 km/h** | 19.0f | $0.50\times$ | **19.0f** | $0.50\times$ | Steep uphill climbing |
| **20 km/h** | 18.0f | $1.00\times$ | **18.0f** | $1.00\times$ | Cruising speed baseline |
| **30 km/h** | 17.0f | $2.00\times$ | **17.42f** | $1.50\times$ | Fast flat riding |
| **40 km/h** | 16.0f | $4.00\times$ | **17.00f** | $2.00\times$ | Moderate descent |
| **50 km/h** | 15.0f | $8.00\times$ (blowout!) | **16.68f** | $2.50\times$ | Fast descent (curves visible) |
| **60 km/h** | 14.0f | $16.00\times$ (blowout!) | **16.42f** | $3.00\times$ | High-speed descent |
| **80+ km/h** | 12.0f | $64.00\times$ (regional map) | **16.00f** (clamped) | $4.00\times$ | Street-level context preserved |

### 4.2 Advanced Settings Navigation Section
| Category | Previous UI | New Streamlined UI |
| :--- | :--- | :--- |
| **Route Selection Radius** | Slider (0.1–10.0 km) | Slider (0.1–10.0 km) - Retained |
| **Cue Dismiss Duration** | Slider (1–10 s) | Slider (1–10 s) - Retained |
| **Cue Countdown Distance** | Slider (30–300 m) | Slider (30–300 m) - Retained |
| **Auto-Follow Unique Route** | Toggle switch | Toggle switch - Retained |
| **Follow-Me Camera Section** | Sub-header + 7 sliders/toggles | **Completely Removed** (Autonomous Calibration) |
