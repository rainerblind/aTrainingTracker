# Stage 1 Analysis: ATT-3775 - Subscribe reactively to currentSpeed and currentBearing in TrackingViewModel.observeMapState so Follow-Me zoom and bearing update continuously

**Ticket**: [ATT-3775](https://rainerblind.atlassian.net/browse/ATT-3775)  
**Sub-task**: [ATT-3869](https://rainerblind.atlassian.net/browse/ATT-3869) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.7`  
**Branch**: `feature/ATT-3775`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Problem Statement & Motivation

During the Sprint 2026-42.5 Joint Review of `ATT-3581` (*Calibrate Follow-Me High-Speed Zoom Curve and Remove Camera Tuning Controls from Advanced Settings*), testing Follow-Me speed-dependent zoom revealed that the map camera did not react continuously to speed or bearing changes.

The athlete expects:
1. `TrackingViewModel.observeMapState()` to subscribe reactively to `banalServiceRepository.currentSpeed` and `banalServiceRepository.currentBearing` (per Rule 39, `docs/coding_standards.md` Section 1.5) so that `TrackingMapState.userSpeed` and `TrackingMapState.userBearing` emit immediately whenever speed or bearing changes in both `READY` and `TRACKING` modes — even when `currentTrack` does not emit.
2. `followMeController(...)` in `MapBehaviors.kt` to continuously step its exponential moving average (`filteredSpeed`, `filteredBearing`) and `activeZoom` to convergence whenever `speed` or `bearing` changes, even when subsequent 1-Hz telemetry ticks hold a constant speed/bearing (`MutableStateFlow` structural equality conflation) or when testing with a stationary speed sensor/simulator where `currentLocation` does not change.
3. Dedicated automated unit tests (`TrackingViewModelMapStateTest`) verifying reactive `currentSpeed` and `currentBearing` propagation in `TrackingViewModel.observeMapState()` and continuous EMA zoom/bearing convergence in `MapBehaviors.kt`.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic inspection of `TrackingViewModel.kt`, `BANALServiceRepository.kt`, and `MapBehaviors.kt` identified two coupled root causes:

1. **Reactive Flow Subscription in `TrackingViewModel.observeMapState()` (Rule 39)**:
   - Originally in Sprint 2026-42.5 (`ATT-3581`), `TrackingViewModel.observeMapState()` combined only `getTrackingViewInfoFlow`, `mapDataFlow`, `currentTrack`, and `bSportType`, reading `banalServiceRepository.currentSpeed.value` and `banalServiceRepository.currentBearing.value` statically inside the `combine` block. Because `currentTrack` only emits when `TrackingMode == TRACKING` AND a new distinct `LatLng` arrives (`BANALServiceRepository.kt` lines 340–345), changes to `currentSpeed` or `currentBearing` in `READY` mode or between GPS position updates never triggered a new `TrackingMapState`.
   - In Sprint 2026-42.6 (`ATT-3690`, commit `61c0fc33`), `liveTelemetryFlow` in `TrackingViewModel.observeMapState()` was refactored to combine `currentTrack`, `bSportType`, `currentSpeed`, and `currentBearing`, **but** no unit tests were added to verify reactive emissions on `currentSpeed` and `currentBearing` when `currentTrack` is silent, and a second failure mechanism remained in `MapBehaviors.kt` (`followMeController`).

2. **Single-Step EMA Stall under `StateFlow` Conflation in `MapBehaviors.kt` (`followMeController`)**:
   - In `MapBehaviors.kt` (lines 278–340), `followMeController` uses:
     ```kotlin
     LaunchedEffect(
         currentLocation, bearing, speed, baseZoom, speedZoomEnabled,
         cruisingZoom, tiltAngle, lookaheadPaddingPercent, viewportHeightDp,
         speedSmoothingAlpha, bearingSmoothingAlpha
     )
     ```
   - `BANALServiceRepository._currentSpeed` and `_currentBearing` are `MutableStateFlow<Double?>`, which conflate equal values (`newValue == oldValue`).
   - When the athlete (or BLE speed/power simulator) transitions from `0.0 m/s` to a steady speed (e.g. `5.56 m/s` = `20 km/h`, where `targetZoom = 18.0f`, or `2.5 m/s` = `9 km/h`, where `targetZoom = 19.1f`), `speed` changes once from `0f` to `targetSpeed`.
   - `LaunchedEffect` executes **only a single EMA step** with `sAlpha = DEFAULT_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA = 0.10f`:
     - At `9 km/h` (`2.5 m/s`), Step 1 sets `filteredSpeed = 0.10 * 2.5 = 0.25 m/s` (`0.9 km/h`), yielding `targetZoom = 19.91f`. Because `|19.91f - 20.0f| = 0.09f < 0.10f` (the zoom hysteresis deadband), `effectiveZoom` remains frozen at `20.0f`, and `LaunchedEffect` exits and **never runs again** while `speed` stays at `2.5 m/s`!
     - At `20 km/h` (`5.56 m/s`), Step 1 sets `filteredSpeed = 0.556 m/s` (`2.0 km/h`), moving zoom only to `19.80f` instead of `18.0f`, and then stalls until another key changes.
   - Furthermore, `zoomFocus` was omitted from the second `LaunchedEffect` key list in `followMeController`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Ensure `TrackingViewModel.observeMapState()` reactively subscribes to `banalServiceRepository.currentSpeed` and `banalServiceRepository.currentBearing` (Rule 39) so `mapState.value.userSpeed` and `mapState.value.userBearing` (and `uiState.value`) update immediately in both `READY` and `TRACKING` modes without requiring `currentTrack` emissions.
  * Ensure `followMeController` in `MapBehaviors.kt` includes `zoomFocus` in its `LaunchedEffect` keys and continuously steps `filteredSpeed`, `filteredBearing`, and `activeZoom` (`stepFollowMeSmoothing`) until convergence (`|speed - filteredSpeed| < 0.05f` and `|shortestBearingDelta| < 0.5f`), so steady speeds and bearings smoothly reach their full calibrated zoom and heading.
  * Add comprehensive unit tests in `TrackingViewModelMapStateTest.kt` verifying reactive `currentSpeed` and `currentBearing` updates in `TrackingViewModel` (when `currentTrack` does not emit) and multi-step EMA convergence in `MapBehaviors.kt`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying the piecewise linear-to-logarithmic zoom formula (`calculateFollowMeTargetZoom`, `REQ-MAP-050`).
  * Modifying `SettingsDataStore` or `TuningPreferencesDataStore` keys.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Amends `REQ-MAP-049` (*Follow-Me Camera Speed Zoom Smoothing...*, `ATT-3227`) and `REQ-MAP-050` (*Calibrated Piecewise Linear-to-Logarithmic Follow-Me High-Speed Zoom and Autonomous Camera Control*, `ATT-3581`), and complements `REQ-MAP-051` (`ATT-3690`). Formally tracked as net-new requirement `REQ-MAP-052`.
* **Historical Origin & Commit Trace**:
  * Sprint `2026-42.0` (`ATT-3227`, commit `c891ba32`): Introduced EMA smoothing (`speedSmoothingAlpha = 0.10f`, `bearingSmoothingAlpha = 0.15f`) and `0.10f` zoom hysteresis deadband in `followMeController`.
  * Sprint `2026-42.5` (`ATT-3581`, commit `b6c5a4f7`): Calibrated piecewise linear-to-logarithmic zoom curve.
  * Sprint `2026-42.6` (`ATT-3690`, commit `61c0fc33`): Added `liveTelemetryFlow` combining `currentTrack`, `bSportType`, `currentSpeed`, and `currentBearing` in `TrackingViewModel.observeMapState()`.
* **Root Reason for Existing Formulation**:
  * `followMeController` originally assumed `LaunchedEffect` would be re-invoked every second via changing `currentLocation` and noisy GPS speeds. However, when speed or location is steady (`StateFlow` conflation) or when `sAlpha = 0.10f` produces a single-step delta smaller than the `0.10f` zoom deadband, a single-shot `LaunchedEffect` stalls before reaching target zoom/bearing.
* **Preservation of Core Invariants**:
  * All calibrated zoom curve math (`calculateFollowMeTargetZoom`), forward lookahead projection (`calculateFollowMeCameraTarget`, Rule 33), polyline referential stability (`REQ-MAP-051`), and `0.10f` hysteresis against micro-jitter remain 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution

1. **Reactive Kinematics Flow in `TrackingViewModel.observeMapState()` (`TrackingViewModel.kt`)**:
   - Verify and document `liveTelemetryFlow` (`REQ-MAP-052.1`, Rule 39) combining `banalServiceRepository.currentTrack`, `banalServiceRepository.bSportType`, `banalServiceRepository.currentSpeed`, and `banalServiceRepository.currentBearing` so any emission on `currentSpeed` or `currentBearing` immediately updates `_mapState` and `_uiState` in both `READY` (empty `currentTrack`) and `TRACKING` modes.
2. **Pure Testable `stepFollowMeSmoothing` & Continuous Convergence Loop in `MapBehaviors.kt`**:
   - Extract a pure, deterministic helper `stepFollowMeSmoothing(...)` returning `FollowMeSmoothingStep(filteredSpeed, filteredBearing, activeZoom, effectiveZoom, isConverged)` in `MapBehaviors.kt`:
     - Shortest-arc bearing step with `bearingSmoothingAlpha`, snapping to exact `targetBearing` when `|diff| < 0.5f`.
     - Exponential speed step with `speedSmoothingAlpha`, snapping to exact `targetSpeed` when `|targetSpeed - filteredSpeed| < 0.05f`.
     - Hysteresis deadband (`0.10f`) during transient steps, plus exact target zoom settlement (`|targetZoom - activeZoom| >= 0.01f`) when `filteredSpeed` has converged to `targetSpeed` so zoom never stalls inside the `0.10f` deadband at low speeds.
   - In `followMeController`, add `zoomFocus` to `LaunchedEffect` keys and run a `while` loop (`delay(1000L)` between ticks) until `step.isConverged` is `true`.
3. **Unit & Contract Verification (`TrackingViewModelMapStateTest.kt`)**:
   - Test `TrackingViewModel` reactive updates when `currentSpeed` and `currentBearing` emit while `currentTrack` is empty (`READY`) or unchanged (`TRACKING`), plus `stepFollowMeSmoothing` convergence across steady and changing speeds/bearings.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing features and unit tests (`REQ-MAP-050`, `REQ-MAP-051`).
  2. Referential stability of `staticLayers.mapSegments` and `staticLayers.mapRoutes` across 1-Hz `currentSpeed` / `currentBearing` emissions (`REQ-MAP-051`).
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**
  * Scoped strictly to `TrackingViewModel.kt`, `MapBehaviors.kt`, and unit tests.
