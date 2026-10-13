# Stage 2 Requirement & Test Specification: ATT-2946 - Calibrate speed-dependent map zoom curve and implement bottom camera padding for forward lookahead

**Ticket**: [ATT-2946](https://atrainingtracker.atlassian.net/browse/ATT-2946)  
**Sub-task**: [ATT-3010](https://atrainingtracker.atlassian.net/browse/ATT-3010) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2946`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Requirement Specification (REQ-MAP-042)

### 1.1 Requirement Definition
* **Requirement ID**: `REQ-MAP-042`
* **Title**: Calibratable Speed-Dependent Map Zoom Curve, Follow-Me Forward Lookahead Camera Padding & Pitch Tuning
* **Type**: Functional & UI Specification
* **Target Release**: `V4.9.40`
* **Status**: Specified
* **Amends/Complements**: Amends and refines `REQ-MAP-004` (*Zoom depending on speed: Wider context at speed, detail when slow*, ATT-437)
* **Parent Ticket**: ATT-2946

### 1.2 Description
The system shall calibrate the speed-dependent map zoom curve in Follow-Me tracking navigation (`MapZoomFocus.FOLLOW_ME`), apply bottom camera content padding to anchor the rider in the lower third for forward road lookahead, and integrate user-configurable camera tuning parameters into `TuningConfig` and `AdvancedTuningDialog`:

1. *Speed Unit Normalization & Linear Speed-Zoom Curve (`MapBehaviors.kt`)*:
   - In `followMeController`, sensor telemetry speed supplied in $\text{m/s}$ shall be converted to $\text{km/h}$ via $\text{speedKmh} = \text{speedMps} \times 3.6\text{f}$.
   - When `speedZoomEnabled` is true, the target camera zoom $Z_{\text{target}}$ shall scale linearly between base zoom $Z_{\text{base}}$ at $0\text{ km/h}$ and cruising zoom $Z_{\text{cruise}}$ at $20\text{ km/h}$:
     $$\text{zoomSlope} = \frac{Z_{\text{base}} - Z_{\text{cruise}}}{20.0\text{f}}$$
     $$Z_{\text{target}} = (Z_{\text{base}} - \text{zoomSlope} \times \text{speedKmh}).\text{coerceIn}(12.0\text{f}, 21.0\text{f})$$
   - When `speedZoomEnabled` is false, $Z_{\text{target}} = Z_{\text{base}}.\text{coerceIn}(12.0\text{f}, 21.0\text{f})$, locking the camera firmly at the selected base zoom regardless of velocity.
2. *Forward Lookahead Bottom Content Padding (`ATrainingTrackerMap.kt`)*:
   - In `ATrainingTrackerMap.kt`, the root map canvas shall be hosted inside `BoxWithConstraints` (or query local layout height).
   - When `zoomFocus == MapZoomFocus.FOLLOW_ME`, `GoogleMap` shall apply bottom content padding:
     $$\text{paddingBottom} = \text{maxHeight} \times \frac{\text{mapFollowMeLookaheadPaddingPercent}}{100\text{f}}$$
     $$\text{contentPadding} = \text{PaddingValues}(\text{bottom} = \text{paddingBottom})$$
     visually anchoring the rider in the lower third ($\approx 25\% - 35\%$ from bottom) and dedicating $\approx 65\% - 75\%$ of the viewport to the forward route corridor.
   - When `zoomFocus != MapZoomFocus.FOLLOW_ME` (e.g. `EXPLICIT_BOUNDS`, `FIT_ALL`, `MANUAL`), bottom padding shall resolve to `0.dp` (`PaddingValues(0.dp)`).
3. *Calibratable 3D Camera Tilt Perspective*:
   - `followMeController` shall apply `tilt(mapFollowMeTiltAngle.coerceIn(0f, 70f))` to the `CameraPosition.builder()`.
4. *DataStore & Advanced Tuning Integration (`TuningPreferencesDataStore.kt`, `TuningConfig.kt`)*:
   - `TuningConfig` shall expose 5 new immutable properties:
     - `mapFollowMeInitialZoom: Float = 20.0f` (range: $15.0\text{f} .. 21.0\text{f}$, step $0.5\text{f}$)
     - `mapFollowMeSpeedZoomEnabled: Boolean = true`
     - `mapFollowMeCruisingZoom: Float = 18.0f` (range: $14.0\text{f} .. 19.5\text{f}$, step $0.5\text{f}$)
     - `mapFollowMeTiltAngle: Float = 70.0f` (range: $0.0\text{f} .. 70.0\text{f}$, step $5.0\text{f}$)
     - `mapFollowMeLookaheadPaddingPercent: Float = 30.0f` (range: $10.0\text{f} .. 50.0\text{f}$, step $5.0\text{f}$)
   - All 5 preference keys registered in `ALL_KEYS` for atomic factory reset in `resetToDefaults()`.
5. *UI Settings Integration (`NavigationSection.kt`, `AdvancedTuningDialog.kt`)*:
   - `NavigationSection.kt` shall render interactive controls for base zoom, speed-zoom toggle, cruising zoom, tilt angle, and forward lookahead padding.
   - `TuningResetDefaultsButton` in `AdvancedTuningDialog.kt` resets all 5 parameters to factory defaults.
6. *9-Language Localization Parity*:
   - String resources for all labels, descriptions, and value formatters defined across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero missing entries.
7. *Preservation of System Invariants*:
   - Low-pass filtered bearing stabilization (`REQ-MAP-003`).
   - Smooth camera animation via `cameraPositionState.animate(..., 400)`.
   - Manual user pan/zoom gestures and explicit route bounds framing (`REQ-MAP-022`).
   - 100% full clean-room unit test pass rate across the full test suite.

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: `REQ-MAP-004` (*Zoom depending on speed*), targeting `MapBehaviors.kt` and `ATrainingTrackerMap.kt`.
2. **Historical Origin & Commit Trace**: Initial follow-me camera prototype implementation in `followMeController`.
3. **Root Reason for Existing Formulation**: `followMeController` originally consumed `speed` directly in $\text{m/s}$, resulting in a negligible $0.56$ zoom level drop at $20\text{ km/h}$, with hardcoded $70^\circ$ tilt and centered rider anchor without lookahead padding.
4. **Preservation of Core Invariants**: Bearing smoothing, camera animation, manual zoom override, map snapshots, and Map DSL layer rendering remain $100\%$ operational.

---

## 3. Acceptance Criteria (Given-When-Then)

* **Scenario 1: Speed-Dependent Zoom Scaling at Speed**:
  - *Given* an athlete riding with Follow-Me camera tracking active and default tuning parameters ($Z_{\text{base}} = 20.0$, $Z_{\text{cruise}} = 18.0$ at $20\text{ km/h}$, speed zoom enabled),
  - *When* the athlete is stationary ($0\text{ km/h}$ / $0.0\text{ m/s}$),
  - *Then* the camera zoom SHALL be $20.0\text{f}$.
  - *When* the athlete accelerates to $20\text{ km/h}$ ($5.56\text{ m/s}$),
  - *Then* the camera zoom SHALL scale smoothly to $18.0\text{f}$.
  - *When* descending at $40\text{ km/h}$ ($11.11\text{ m/s}$),
  - *Then* the camera zoom SHALL scale to $16.0\text{f}$.

* **Scenario 2: Speed-Dependent Zoom Disabled**:
  - *Given* an athlete sets `mapFollowMeSpeedZoomEnabled = false` and `mapFollowMeInitialZoom = 19.0f`,
  - *When* the athlete rides at $0\text{ km/h}$, $25\text{ km/h}$, or $45\text{ km/h}$,
  - *Then* the camera zoom SHALL remain locked at $19.0\text{f}$.

* **Scenario 3: Forward Lookahead Bottom Padding**:
  - *Given* `ATrainingTrackerMap` renders in `MapZoomFocus.FOLLOW_ME` with default $30\%$ padding on a $400\text{dp}$ tall viewport,
  - *Then* bottom content padding SHALL evaluate to $120\text{dp}$, anchoring the rider coordinate in the lower third.
  - *Given* `ATrainingTrackerMap` renders in `MapZoomFocus.FIT_ALL` or `EXPLICIT_BOUNDS`,
  - *Then* bottom content padding SHALL evaluate to $0\text{dp}$.

* **Scenario 4: Configurable Camera Tilt**:
  - *Given* an athlete configures `mapFollowMeTiltAngle = 35.0f`,
  - *When* Follow-Me camera updates execute,
  - *Then* `CameraPosition.tilt` SHALL be $35.0^\circ$.

* **Scenario 5: Factory Reset in Advanced Tuning**:
  - *Given* custom camera values in `AdvancedTuningDialog`,
  - *When* the athlete taps `TuningResetDefaultsButton`,
  - *Then* all 5 camera parameters SHALL immediately restore to defaults ($20.0\text{f}$, `true`, $18.0\text{f}$, $70.0\text{f}$, $30.0\text{f}$).

---

## 4. Test Specification (TST-MAP-044)

### 4.1 Unit & Contract Tests
1. **`FollowMeCameraZoomTest`**:
   - `testCalculateTargetZoom_stationary_returnsBaseZoom()`: Verifies $Z_{\text{target}} == 20.0\text{f}$ at $0\text{ m/s}$.
   - `testCalculateTargetZoom_cruisingSpeed20Kmh_returnsCruisingZoom()`: Verifies $Z_{\text{target}} \approx 18.0\text{f}$ at $5.556\text{ m/s}$ ($20\text{ km/h}$).
   - `testCalculateTargetZoom_highSpeed40Kmh_scalesLinearly()`: Verifies $Z_{\text{target}} \approx 16.0\text{f}$ at $11.111\text{ m/s}$ ($40\text{ km/h}$).
   - `testCalculateTargetZoom_speedZoomDisabled_returnsConstantBaseZoom()`: Verifies zoom remains invariant across speeds when `speedZoomEnabled == false`.
   - `testCalculateTargetZoom_clampedToMinAndMax()`: Verifies target zoom cannot exceed $[12.0\text{f}, 21.0\text{f}]$.
   - `testFollowMeCameraTilt_respectsConfiguredAngleAndClamps()`: Verifies tilt values are clamped within $[0.0\text{f}, 70.0\text{f}]$.

2. **`FollowMePaddingContractTest`**:
   - `testATrainingTrackerMap_appliesLookaheadPaddingInFollowMeMode()`: Verifies `contentPadding` has non-zero bottom padding during `FOLLOW_ME` and `0.dp` otherwise.
   - `testFollowMeController_acceptsTuningParametersWithDefaults()`: Verifies signature backward compatibility.

3. **`MapCameraTuningPreferencesTest`**:
   - `testTuningConfig_defaultsContainMapCameraParameters()`: Verifies default values on `TuningConfig`.
   - `testTuningPreferencesDataStore_readWriteMapCameraParameters()`: Verifies persistence and flow emission.
   - `testTuningPreferencesDataStore_resetToDefaults_clearsMapCameraKeys()`: Verifies atomic reset.

4. **`MapCameraLocalizationTest`**:
   - `testMapCameraStrings_haveParityAcrossAll9Locales()`: Verifies all new string tokens exist across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

---

## 5. Traceability Matrix

| Requirement ID | Test Case ID | Test Class | Verification Scope | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-MAP-042` (Clause 1) | `TST-MAP-044.1` | `FollowMeCameraZoomTest` | Speed unit normalization, linear zoom curve, speedZoom toggle | Specified |
| `REQ-MAP-042` (Clause 2) | `TST-MAP-044.2` | `FollowMePaddingContractTest` | Forward lookahead bottom content padding in `GoogleMap` | Specified |
| `REQ-MAP-042` (Clause 3) | `TST-MAP-044.3` | `FollowMeCameraZoomTest` | Calibratable camera tilt angle ($0^\circ - 70^\circ$) | Specified |
| `REQ-MAP-042` (Clause 4-5) | `TST-MAP-044.4` | `MapCameraTuningPreferencesTest` | DataStore persistence, TuningConfig, and reset to defaults | Specified |
| `REQ-MAP-042` (Clause 6) | `TST-MAP-044.5` | `MapCameraLocalizationTest` | 9-language localization parity across all supported locales | Specified |
