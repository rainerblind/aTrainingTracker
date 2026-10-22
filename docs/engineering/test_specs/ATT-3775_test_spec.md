# Stage 2: Requirement & Test Specification - ATT-3775: Subscribe reactively to currentSpeed and currentBearing in TrackingViewModel.observeMapState so Follow-Me zoom and bearing update continuously

**Ticket**: [ATT-3775](https://rainerblind.atlassian.net/browse/ATT-3775)  
**Sub-task**: [ATT-3870](https://rainerblind.atlassian.net/browse/ATT-3870) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.7`  
**Requirement Mapping**: `REQ-MAP-052` (*Reactive Speed/Bearing Flow Subscription in TrackingViewModel & Continuous Follow-Me Smoothing Convergence*)  
**Test Spec ID**: `TST-MAP-054`  
**Branch**: `feature/ATT-3775`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Requirement Specification (REQ-MAP-052)

### 1.1 Problem Statement & Rationale
For Follow-Me camera zoom (`calculateFollowMeTargetZoom`) and heading rotation (`calculateFollowMeCameraTarget`) to respond smoothly and continuously in both `READY` and `TRACKING` modes:
1. `TrackingViewModel.observeMapState()` MUST actively subscribe to `banalServiceRepository.currentSpeed` and `banalServiceRepository.currentBearing` inside `combine(...)` (`liveTelemetryFlow`, Rule 39) rather than reading `.value` statically when `currentTrack` emits.
2. `followMeController` in `MapBehaviors.kt` MUST continuously step its exponential smoothing state (`filteredSpeed`, `filteredBearing`, `activeZoom`) until convergence even when `speed`, `bearing`, or `currentLocation` remain constant across subsequent 1-Hz ticks (`StateFlow` structural equality conflation).

### 1.2 Functional & Architectural Requirements
1. **Reactive Kinematics Subscription (`REQ-MAP-052.1` — Rule 39)**:
   - `TrackingViewModel.observeMapState()` SHALL combine `banalServiceRepository.currentTrack`, `banalServiceRepository.bSportType`, `banalServiceRepository.currentSpeed`, and `banalServiceRepository.currentBearing` inside `liveTelemetryFlow`.
   - Any emission on `currentSpeed` or `currentBearing` SHALL immediately emit an updated `TrackingMapState` (`_mapState` and `_uiState`) in both `READY` (empty `currentTrack`) and `TRACKING` (non-empty `currentTrack`) modes without requiring a `currentTrack` emission.
   - `null` emissions on `currentSpeed` or `currentBearing` SHALL map safely to `0.0f`.
2. **Continuous Follow-Me EMA Convergence (`REQ-MAP-052.2`)**:
   - `MapBehaviors.kt` SHALL define pure function `stepFollowMeSmoothing(...)` returning `FollowMeSmoothingStep(filteredSpeed, filteredBearing, activeZoom, effectiveZoom, isConverged)`:
     - Shortest-arc bearing interpolation in `[-180f, 180f]`, snapping to `targetBearing` when `|diff| < 0.5f`.
     - Exponential speed smoothing `filteredSpeed += sAlpha * (targetSpeed - filteredSpeed)`, snapping to `targetSpeed` when `|targetSpeed - filteredSpeed| < 0.05f`.
     - Hysteresis deadband (`0.10f`) during transient steps, plus exact target zoom settlement (`|targetZoom - currentActiveZoom| >= 0.01f`) once `filteredSpeed` converges to `targetSpeed`.
   - In `followMeController`, `LaunchedEffect` SHALL include `zoomFocus` in its keys and loop (`delay(1000L)` between steps) until `step.isConverged == true`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Reactive Speed & Bearing Emissions Without Track Updates)**:
  * *Given* `TrackingViewModel` in `READY` mode (`currentTrack = emptyList()`) or `TRACKING` mode (fixed `currentTrack`),
  * *When* `banalServiceRepository.currentSpeed` emits `8.5` or `banalServiceRepository.currentBearing` emits `135.0` while `currentTrack` does not emit,
  * *Then* `viewModel.mapState.value.userSpeed` immediately updates to `8.5f` and `viewModel.mapState.value.userBearing` immediately updates to `135.0f` (and `viewModel.uiState.value` reflects the same values).
* **Criterion 2 (Continuous EMA Zoom & Bearing Convergence Under Constant Speed)**:
  * *Given* a constant target speed (e.g. `2.5 m/s` = `9 km/h` or `11.11 m/s` = `40 km/h`) and bearing (`10.0f` from `350.0f`),
  * *When* `stepFollowMeSmoothing` steps until `isConverged == true`,
  * *Then* `filteredSpeed` equals `targetSpeed`, `filteredBearing` equals `10.0f`, and `effectiveZoom` equals `calculateFollowMeTargetZoom(targetSpeed)`.

### 1.4 System Invariants
- Zero regression in `REQ-MAP-050` zoom curve math or `REQ-MAP-051` polyline memory stability.
- Zero new UI strings required; existing 9-locale parity remains 100% intact.

---

## 2. Test Specification (TST-MAP-054)

### Test Case 1: `testObserveMapState_reactsImmediatelyToCurrentSpeedAndCurrentBearingWithoutCurrentTrackEmission` (`TST-MAP-054.1`)
* **Scope**: ViewModel Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModelMapStateTest.kt`
* **Action**: Emit multiple values (and `null`) on `currentSpeedFlow` and `currentBearingFlow` while `currentTrackFlow` remains empty (`READY` mode) and while `currentTrackFlow` holds a fixed coordinate (`TRACKING` mode).
* **Expected Result**: `viewModel.mapState.value.userSpeed`, `viewModel.mapState.value.userBearing`, `viewModel.uiState.value.userSpeed`, and `viewModel.uiState.value.userBearing` update immediately on every emission, and `mapSegments` / `mapRoutes` retain referential identity (`===`).

### Test Case 2: `testStepFollowMeSmoothing_convergesToTargetZoomAndBearingUnderSteadyInputs` (`TST-MAP-054.2`)
* **Scope**: Pure Camera Math & Source Contract Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModelMapStateTest.kt`
* **Action**: Step `stepFollowMeSmoothing` for low speed (`2.5 m/s` = `9 km/h`), cruising speed (`5.56 m/s` = `20 km/h`), high speed (`11.11 m/s` = `40 km/h`), and $350^\circ \to 10^\circ$ wrap-around bearing, and inspect `TrackingViewModel.kt` & `MapBehaviors.kt` source contracts.
* **Expected Result**: `isConverged` becomes `true`, `effectiveZoom` settles to `calculateFollowMeTargetZoom(targetSpeed)`, `filteredBearing` settles to `10.0f`, and `TrackingViewModel.kt` contains zero `currentSpeed.value` or `currentBearing.value` reads.

### Test Case 3: Clean-Room Regression Suite (`TST-MAP-054.3`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-MAP-054.1` | ViewModel Unit | `TrackingViewModel.observeMapState` | `REQ-MAP-052.1` | Specified |
| `TST-MAP-054.2` | Camera Math & Contract | `stepFollowMeSmoothing`, `followMeController` | `REQ-MAP-052.2` | Specified |
| `TST-MAP-054.3` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
