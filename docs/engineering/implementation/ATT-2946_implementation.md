# Stage 4 Implementation Report: ATT-2946 - Calibrate speed-dependent map zoom curve and implement bottom camera padding for forward lookahead

**Ticket**: [ATT-2946](https://atrainingtracker.atlassian.net/browse/ATT-2946)  
**Sub-task**: [ATT-3012](https://atrainingtracker.atlassian.net/browse/ATT-3012) (`[Implementation]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2946`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Executive Summary

This deliverable concludes Stage 4 (Software Construction & Implementation) for [ATT-2946](https://atrainingtracker.atlassian.net/browse/ATT-2946) in accordance with requirement `REQ-MAP-042` and test specification `TST-MAP-044`.

We calibrated the follow-me tracking camera zoom curve by normalizing velocity from $\text{m/s}$ to $\text{km/h}$ and applying a linear scaling model between base zoom (at $0\text{ km/h}$) and cruising zoom (at $20\text{ km/h}$). Furthermore, we introduced dynamic bottom viewport content padding to position the athlete in the lower third of the display during follow-me navigation, dedicating up to $70\%$ of the screen to the road ahead. Finally, full user configuration has been wired into `TuningPreferencesDataStore`, `TuningConfig`, `NavigationSection`, and `AdvancedTuningDialog`, complemented by complete 9-language localization parity and unit/contract test suites.

---

## 2. Implemented Changes

### 2.1 Domain & Mathematical Modeling (`MapBehaviors.kt`)
* Implemented `calculateFollowMeTargetZoom(speedMps, baseZoom, speedZoomEnabled, cruisingZoom, minZoom, maxZoom)`:
  - Converts $\text{m/s}$ to $\text{km/h}$ ($\text{speedMps} \times 3.6\text{f}$).
  - Computes linear zoom slope: $\text{zoomSlope} = \frac{Z_{\text{base}} - Z_{\text{cruise}}}{20.0\text{f}}$.
  - Clamps output strictly within $[12.0\text{f}, 21.0\text{f}]$.
  - Bypasses velocity scaling when `speedZoomEnabled == false`, locking camera firmly to `baseZoom`.
* Updated `followMeController` signature and camera position builder:
  - Takes `baseZoom`, `speedZoomEnabled`, `cruisingZoom`, and `tiltAngle` with fallback to `TuningPreferencesDefaults`.
  - Applies `tilt(tiltAngle.coerceIn(0f, 70f))` to `CameraPosition.builder()`.
  - Preserves 400ms camera animation and low-pass filtered bearing stabilization.

### 2.2 Viewport Content Padding & Projection (`ATrainingTrackerMap.kt`)
* Wrapped root map canvas in `BoxWithConstraints`.
* Evaluated bottom content padding dynamically:
  $$\text{bottomPadding} = \begin{cases} \text{maxHeight} \times \frac{\text{lookaheadPaddingPercent}}{100\text{f}} & \text{if } \text{zoomFocus} == \text{FOLLOW\_ME} \\ 0.\text{dp} & \text{otherwise} \end{cases}$$
* Passed `contentPadding = PaddingValues(bottom = bottomPadding)` into `GoogleMap`.
* Forwarded tuning configuration parameters to `followMeController`.

### 2.3 Preferences & DataStore (`TuningPreferencesDataStore.kt`)
* Added default constants, ranges, and steps to `TuningPreferencesDefaults`:
  - `DEFAULT_MAP_FOLLOW_ME_INITIAL_ZOOM = 20.0f` (range: $15.0 - 21.0$, step: $0.5$)
  - `DEFAULT_MAP_FOLLOW_ME_SPEED_ZOOM_ENABLED = true`
  - `DEFAULT_MAP_FOLLOW_ME_CRUISING_ZOOM = 18.0f` (range: $14.0 - 19.5$, step: $0.5$)
  - `DEFAULT_MAP_FOLLOW_ME_TILT_ANGLE = 70.0f` (range: $0.0 - 70.0$, step: $5.0$)
  - `DEFAULT_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT = 30.0f` (range: $10.0 - 50.0$, step: $5.0$)
* Extended `TuningConfig` with the 5 new properties.
* Defined preference keys and registered all 5 in `ALL_KEYS` for atomic factory reset.
* Added boundary coercion upon DataStore emission and implemented `saveTuningConfig` and individual mutator methods.

### 2.4 Settings UI Integration (`NavigationSection.kt`, `AdvancedTuningDialog.kt`)
* Extended `NavigationSection` with a dedicated section header and 5 interactive controls:
  - Base Zoom slider ($15.0 - 21.0$).
  - Speed-Dependent Zoom toggle.
  - Cruising Speed Zoom slider ($14.0 - 19.5$, visible when speed zoom is active).
  - Perspective Tilt Angle slider ($0^\circ - 70^\circ$).
  - Forward Lookahead Bottom Padding slider ($10\% - 50\%$).
* Updated `AdvancedTuningDialog` state management, persistence callback, and `TuningResetDefaultsButton` reset dispatch.

### 2.5 9-Language Localization Parity
* Injected 11 string resources across all 9 supported locales:
  - `values/strings.xml` (English)
  - `values-de/strings.xml` (German)
  - `values-es/strings.xml` (Spanish)
  - `values-fr/strings.xml` (French)
  - `values-it/strings.xml` (Italian)
  - `values-ja/strings.xml` (Japanese)
  - `values-nl/strings.xml` (Dutch)
  - `values-pl/strings.xml` (Polish)
  - `values-pt/strings.xml` (Portuguese)
* XML ampersand entities properly escaped as `&amp;`.

---

## 3. Verification & Targeted Test Suite

Four targeted test classes were implemented and verified with a 100% pass rate:
1. `FollowMeCameraZoomTest`: Verifies `calculateFollowMeTargetZoom` at $0\text{ km/h}$, $20\text{ km/h}$, $40\text{ km/h}$, toggle bypass, and tilt angle clamping.
2. `FollowMePaddingContractTest`: Verifies `BoxWithConstraints` padding application during `FOLLOW_ME` and `0.dp` fallback.
3. `MapCameraTuningPreferencesTest`: Verifies `TuningConfig` defaults, DataStore keys in `ALL_KEYS`, and range clamping.
4. `MapCameraLocalizationTest`: Verifies non-empty string parity for all 11 keys across all 9 locales.

Execution Command:
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.FollowMeCameraZoomTest" --tests "com.atrainingtracker.trainingtracker.ui.map.FollowMePaddingContractTest" --tests "com.atrainingtracker.trainingtracker.settings.MapCameraTuningPreferencesTest" --tests "com.atrainingtracker.trainingtracker.settings.MapCameraLocalizationTest"
```
Result: **BUILD SUCCESSFUL in 1m 9s** (All tests green).
