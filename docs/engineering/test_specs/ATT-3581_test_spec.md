# Stage 2: Requirement & Test Specification - ATT-3581: Calibrate Follow-Me High-Speed Zoom with Logarithmic Scaling and Remove Camera Sliders from Advanced Settings

**Ticket**: [ATT-3581](https://rainerblind.atlassian.net/browse/ATT-3581)  
**Sub-task**: [ATT-3716](https://rainerblind.atlassian.net/browse/ATT-3716) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.5` (Sprint ID `372`)  
**Requirement Mapping**: `REQ-MAP-050` (*Calibrated Piecewise Logarithmic Follow-Me High-Speed Zoom and Autonomous Camera Control*)  
**Test Spec ID**: `TST-MAP-052` (*Follow-Me Piecewise Logarithmic Zoom Calibration & UI Simplification Verification Suite*)  
**Branch**: `feature/ATT-3581`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Requirement Specification (REQ-MAP-050)

### 1.1 Problem Statement & Rationale
During workout tracking in Follow-Me camera mode, Google Maps zoom levels are base-2 logarithmic ($2^{-\text{zoom}}$ meters/dp). Applying a linear speed slope down to `minZoom = 12.0f` causes visible ground distance to grow exponentially on descents ($16\times$ viewport blowout at $60\text{ km/h}$), turning upcoming curves into invisible hairline threads.

Furthermore, exposing 7 manual camera tuning sliders in Advanced Settings creates unnecessary UI clutter and allows legacy DataStore tweaks to override mathematically calibrated camera physics.

The system requires calibrating the zoom curve using a piecewise linear-to-logarithmic model, clamping minimum zoom to street-level clarity (`16.0f`), wiring Cockpit directly to canonical defaults, and removing obsolete camera sliders from Advanced Settings.

### 1.2 Functional & Architectural Requirements
The system SHALL calibrate Follow-Me camera zoom using a piecewise linear-to-logarithmic curve, raise minimum zoom to street-level resolution, and streamline Advanced Settings by removing manual camera tuning controls (ATT-3581, amending `REQ-MAP-042` and `REQ-MAP-049`):

1. *Piecewise Linear-to-Logarithmic Zoom Curve (`calculateFollowMeTargetZoom` in `MapBehaviors.kt`)*:
   - For speeds $0.0 \le \text{speedKmh} \le 20.0\text{ km/h}$: interpolate linearly between `baseZoom` ($20.0f$ at $0\text{ km/h}$) and `cruisingZoom` ($18.0f$ at $20\text{ km/h}$):
     ```kotlin
     val zoomSlope = (baseZoom - cruisingZoom) / 20.0f
     val targetZoom = baseZoom - zoomSlope * speedKmh
     ```
   - For speeds $\text{speedKmh} > 20.0\text{ km/h}$: scale logarithmically with $\log_2(\text{speedKmh} / 20.0)$ to maintain a constant lookahead time horizon ($D \propto v$):
     ```kotlin
     val logScale = (baseZoom - cruisingZoom) / 2.0f
     val logDelta = logScale * (kotlin.math.ln(speedKmh / 20.0f) / kotlin.math.ln(2.0f))
     val rawZoom = cruisingZoom - logDelta
     return rawZoom.coerceIn(minZoom, maxZoom)
     ```
   - When `speedZoomEnabled == false`: return `baseZoom.coerceIn(minZoom, maxZoom)`.
   - Default `minZoom` SHALL equal `16.0f` (`DEFAULT_MAP_FOLLOW_ME_MIN_ZOOM = 16.0f` in `TuningPreferencesDefaults`).

2. *Autonomous Camera Parameter Wiring (`ATrainingTrackerMap.kt`)*:
   - `followMeController(...)` SHALL be invoked using canonical defaults from `TuningPreferencesDefaults` for all camera tuning parameters (`baseZoom = DEFAULT_MAP_FOLLOW_ME_INITIAL_ZOOM`, `speedZoomEnabled = DEFAULT_MAP_FOLLOW_ME_SPEED_ZOOM_ENABLED`, `cruisingZoom = DEFAULT_MAP_FOLLOW_ME_CRUISING_ZOOM`, `tiltAngle = DEFAULT_MAP_FOLLOW_ME_TILT_ANGLE`, `lookaheadPaddingPercent = DEFAULT_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT`, `speedSmoothingAlpha = DEFAULT_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA`, `bearingSmoothingAlpha = DEFAULT_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA`).
   - Legacy persisted DataStore tweaks SHALL NOT override calibrated camera behavior.

3. *Streamlined Navigation Advanced Settings UI (`NavigationSection.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`)*:
   - In `NavigationSection.kt`, the Follow-Me map camera sub-section divider, header (`tuning_map_camera_section_title`), and the 7 camera tuning items (`Initial Zoom`, `Speed-Dependent Zoom Enabled`, `Cruising Speed Zoom`, `Tilt Angle`, `Lookahead Bottom Padding`, `Speed Smoothing`, `Bearing Smoothing`) SHALL be removed.
   - Unused Follow-Me parameters in `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt` SHALL be removed.
   - Remaining Navigation items (`routeSelectionRadiusKm`, `navigationCueDismissDurationSec`, `turnCueCountdownDistanceMeters`, `autoFollowUniqueRouteEnabled`) SHALL remain fully active and configurable.

4. *Preservation of System Invariants*:
   - Low-speed linear progression ($\le 20\text{ km/h}$), exponential speed/bearing smoothing, hysteresis deadband ($0.10f$), forward lookahead projection, and 100% full clean-room unit test suite pass rate MUST NOT be broken.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: Amending `REQ-MAP-042` (*Speed-Dependent Map Zoom Calibration*, Sprint 2026-41.6, `ATT-2946`) and `REQ-MAP-049` (*Follow-Me Camera Speed Zoom Smoothing...*, Sprint 2026-42.0, `ATT-3227`). Formally tracked as `REQ-MAP-050`.
2. **Historical Origin & Commit Trace**: `REQ-MAP-042` introduced linear speed-dependent zoom and camera sliders in Sprint 2026-41.6 (commit `3c59a421`). `REQ-MAP-048` added forward lookahead projection in Sprint 2026-41.9 (commit `d97e201b`). `REQ-MAP-049` added exponential smoothing in Sprint 2026-42.0 (commit `c891ba32`).
3. **Root Reason for Existing Formulation**: Early Compose prototyping used linear interpolation and exposed developer sliders for quick experimentation. Because Google Maps ground resolution scales as $2^{-\text{zoom}}$, continuing linear slope above $20\text{ km/h}$ caused exponential viewport blowout.
4. **Preservation of Core Invariants**: Low-speed behavior ($0\text{–}20\text{ km/h}$) remains identical. Smoothing filters, hysteresis deadband, forward projection, and 100% full clean-room unit test pass rate are preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1: Unchanged Uphill & Low-Speed Zoom ($\le 20\text{ km/h}$)**:
  * *Given* speeds between $0\text{ km/h}$ and $20\text{ km/h}$,
  * *When* `calculateFollowMeTargetZoom` is evaluated with default parameters,
  * *Then* zoom interpolates linearly from $20.0f$ ($0\text{ km/h}$) to $19.0f$ ($10\text{ km/h}$) and $18.0f$ ($20\text{ km/h}$).
* **Criterion 2: Proportional Logarithmic High-Speed Zoom ($> 20\text{ km/h}$)**:
  * *Given* descent speeds of $30\text{ km/h}$, $40\text{ km/h}$, $50\text{ km/h}$, $60\text{ km/h}$, and $90\text{ km/h}$,
  * *When* `calculateFollowMeTargetZoom` is evaluated,
  * *Then* it returns $17.42f \pm 0.01f$ at $30\text{ km/h}$, $17.00f \pm 0.01f$ at $40\text{ km/h}$, $16.68f \pm 0.01f$ at $50\text{ km/h}$, $16.42f \pm 0.01f$ at $60\text{ km/h}$, and clamps at $16.0f$ at $90\text{ km/h}$.
* **Criterion 3: Disabled Speed Zoom Locks to Base Zoom**:
  * *Given* `speedZoomEnabled == false`,
  * *When* `calculateFollowMeTargetZoom` is evaluated at any speed,
  * *Then* it returns `baseZoom` ($20.0f$).
* **Criterion 4: Streamlined Navigation Settings Section**:
  * *Given* the athlete opening Advanced Settings -> Navigation,
  * *When* rendered,
  * *Then* the obsolete Follow-Me camera tuning sliders are omitted, while Route Selection Radius, Cue Dismiss Duration, Turn Cue Countdown Distance, and Auto-Follow Unique Route remain present.

---

## 2. Test Specification (TST-MAP-052)

### Test Case 1: Piecewise Logarithmic Speed Zoom Math (`TST-MAP-052.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/FollowMeCameraZoomTest.kt`
* **Verifications**:
  1. Low-speed linear region:
     - $0\text{ m/s}$ ($0\text{ km/h}$) $\to 20.0f$
     - $2.778\text{ m/s}$ ($10\text{ km/h}$) $\to 19.0f$
     - $5.556\text{ m/s}$ ($20\text{ km/h}$) $\to 18.0f$
  2. High-speed logarithmic region:
     - $8.333\text{ m/s}$ ($30\text{ km/h}$) $\to 17.42f \pm 0.01f$
     - $11.111\text{ m/s}$ ($40\text{ km/h}$) $\to 17.00f \pm 0.01f$
     - $13.889\text{ m/s}$ ($50\text{ km/h}$) $\to 16.68f \pm 0.01f$
     - $16.667\text{ m/s}$ ($60\text{ km/h}$) $\to 16.42f \pm 0.01f$
     - $22.222\text{ m/s}$ ($80\text{ km/h}$) $\to 16.00f \pm 0.01f$
     - $25.000\text{ m/s}$ ($90\text{ km/h}$) $\to 16.00f$ (clamped to `minZoom`)
     - $33.333\text{ m/s}$ ($120\text{ km/h}$) $\to 16.00f$ (clamped to `minZoom`)
  3. Speed zoom disabled:
     - Returns $20.0f$ across all speeds when `speedZoomEnabled == false`.
  4. Default `minZoom`:
     - Evaluates to `16.0f` (`DEFAULT_MAP_FOLLOW_ME_MIN_ZOOM`).

### Test Case 2: UI Contract & Slider Removal (`TST-MAP-052.2`)
* **Scope**: UI / AST Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningVisualContractTest.kt`
* **Verifications**:
  1. `NavigationSection.kt` does NOT reference `tuning_map_camera_section_title` or any of the 7 Follow-Me camera string titles (`tuning_map_base_zoom_title`, `tuning_map_speed_zoom_enabled_title`, `tuning_map_cruising_zoom_title`, `tuning_map_tilt_angle_title`, `tuning_map_lookahead_padding_title`, `tuning_map_follow_me_speed_smoothing_title`, `tuning_map_follow_me_bearing_smoothing_title`).
  2. `NavigationSection.kt` retains `tuning_route_selection_radius_title`, `tuning_nav_cue_dismiss_duration_title`, `tuning_turn_cue_countdown_distance_title`, and `tuning_auto_follow_unique_route_title`.

### Test Case 3: Autonomous Camera Controller Invocation (`TST-MAP-052.3`)
* **Scope**: Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/FollowMePaddingContractTest.kt`
* **Verifications**:
  1. `ATrainingTrackerMap.kt` invokes `followMeController(...)` without passing manual `tuningConfig.mapFollowMe*` parameters, using canonical defaults directly.

### Test Case 4: Full Clean-Room Regression Suite (`TST-MAP-052.4`)
* **Scope**: Full Clean-Room Suite
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% test pass rate across all 2,964+ unit tests.

---

## 3. Traceability Matrix

| Requirement Clause | Verification Procedure | Test Case ID | Target File | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-MAP-050.1` (Piecewise Linear-to-Logarithmic Zoom) | Speed Zoom Evaluation Test | `TST-MAP-052.1` | `FollowMeCameraZoomTest.kt` | Specified |
| `REQ-MAP-050.1` (Min Zoom Clamp at 16.0f) | High-Speed Descent Clamping Test | `TST-MAP-052.1` | `FollowMeCameraZoomTest.kt` | Specified |
| `REQ-MAP-050.2` (Autonomous Camera Invocation) | Follow-Me Controller Invocation Contract | `TST-MAP-052.3` | `FollowMePaddingContractTest.kt` | Specified |
| `REQ-MAP-050.3` (Camera Sliders Removal) | AST Visual Contract Test | `TST-MAP-052.2` | `AdvancedTuningVisualContractTest.kt` | Specified |
| `REQ-MAP-050.4` (Zero Regressions) | Clean-Room Full Suite Execution | `TST-MAP-052.4` | `./gradlew testDebugUnitTest` | Specified |
