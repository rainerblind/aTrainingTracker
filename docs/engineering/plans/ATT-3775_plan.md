# Stage 3: Implementation Plan - ATT-3775: Subscribe reactively to currentSpeed and currentBearing in TrackingViewModel.observeMapState so Follow-Me zoom and bearing update continuously

**Ticket**: [ATT-3775](https://rainerblind.atlassian.net/browse/ATT-3775)  
**Sub-task**: [ATT-3871](https://rainerblind.atlassian.net/browse/ATT-3871) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.7`  
**Requirement Mapping**: `REQ-MAP-052`  
**Test Mapping**: `TST-MAP-054`  
**Branch**: `feature/ATT-3775`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Problem Description & Background

In `TrackingViewModel.observeMapState()`, `currentSpeed` and `currentBearing` must be subscribed reactively (`liveTelemetryFlow`, Rule 39) so that `TrackingMapState.userSpeed` and `TrackingMapState.userBearing` emit immediately whenever speed or bearing changes in both `READY` and `TRACKING` modes without requiring a concurrent `currentTrack` emission. Furthermore, in `MapBehaviors.kt` (`followMeController`), exponential smoothing (`filteredSpeed`, `filteredBearing`) and `activeZoom` must step continuously until convergence even when `speed`, `bearing`, or `currentLocation` remain constant across subsequent 1-Hz ticks (`StateFlow` structural equality conflation).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MAP-052` (*Reactive Speed/Bearing Flow Subscription in TrackingViewModel & Continuous Follow-Me Smoothing Convergence*)
* **Test Mapping**: `TST-MAP-054` (*Reactive Speed/Bearing Flow Subscription in TrackingViewModel & Continuous Follow-Me Smoothing Convergence Verification Suite*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: `REQ-MAP-050` zoom curve math (`calculateFollowMeTargetZoom`) and `REQ-MAP-051` polyline memory stability (`staticMapLayerFlow`) remain 100% intact.
2. **Thread Safety & Dispatcher Affinity**: Coroutine collection in `TrackingViewModel` runs on `viewModelScope` (`Dispatchers.Main`).
3. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
4. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `TrackingViewModel.observeMapState()` (`TrackingViewModel.kt`)
- Document `liveTelemetryFlow` (`REQ-MAP-052.1`, `ATT-3775`, Rule 39) combining `banalServiceRepository.currentTrack`, `banalServiceRepository.bSportType`, `banalServiceRepository.currentSpeed`, and `banalServiceRepository.currentBearing` into `LiveTelemetryTuple(currentTrack, bSportType, userSpeed = speed?.toFloat() ?: 0f, userBearing = bearing?.toFloat() ?: 0f)`.
- Ensure zero `.value` reads of `currentSpeed` or `currentBearing` exist inside `observeMapState()`.

### Component 2: Continuous Follow-Me EMA Convergence (`MapBehaviors.kt`)
- Define pure data class `FollowMeSmoothingStep(val filteredSpeed: Float, val filteredBearing: Float, val activeZoom: Float, val effectiveZoom: Float, val isConverged: Boolean)` and pure function `stepFollowMeSmoothing(...)` in `MapBehaviors.kt`:
  - Computes shortest-arc angular difference `diff` in `[-180f, 180f]`, stepping `filteredBearing` by `bAlpha * diff` and snapping directly to `(targetBearing % 360f + 360f) % 360f` when `kotlin.math.abs(diff) < 0.5f`.
  - Computes exponential speed step `filteredSpeed + sAlpha * (targetSpeed - filteredSpeed)`, snapping directly to `targetSpeed` when `kotlin.math.abs(targetSpeed - filteredSpeed) < 0.05f`.
  - Computes `targetZoom = calculateFollowMeTargetZoom(speedMps = nextFilteredSpeed, baseZoom = baseZoom, speedZoomEnabled = speedZoomEnabled, cruisingZoom = cruisingZoom, minZoom = minZoom, maxZoom = maxZoom)`.
  - Updates `activeZoom` when `zoomDelta >= 0.10f` or when `speedConverged && zoomDelta >= 0.01f`.
  - Sets `isConverged = speedConverged && bearingConverged`.
- In `followMeController`, add `zoomFocus` to `LaunchedEffect` keys and run a `do { ... if (!step.isConverged) delay(1000L) } while (!step.isConverged)` loop while `zoomFocus == MapZoomFocus.FOLLOW_ME && currentLocation != null`.

### Component 3: Unit & Contract Test Suite (`TrackingViewModelMapStateTest.kt`)
- Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModelMapStateTest.kt` testing:
  1. Immediate reactive `mapState.value.userSpeed` and `uiState.value.userSpeed` updates in `READY` (`currentTrack` empty) and `TRACKING` (`currentTrack` non-empty) modes without `currentTrack` emissions.
  2. Immediate reactive `mapState.value.userBearing` and `uiState.value.userBearing` updates without `currentTrack` emissions.
  3. Safe `null -> 0.0f` fallback for both `currentSpeed` and `currentBearing`.
  4. Referential identity (`===`) of `mapSegments` and `mapRoutes` across `currentSpeed` and `currentBearing` emissions (`REQ-MAP-051`).
  5. Multi-step `stepFollowMeSmoothing` convergence across low speed (`2.5 m/s`), cruising speed (`5.56 m/s`), high speed (`11.11 m/s`), and $350^\circ \to 10^\circ$ wrap-around bearing.
  6. Source contract verification on `TrackingViewModel.kt` and `MapBehaviors.kt`.

### UI Consistency (Rule 23)
No UI layout or visual token changes; pure reactive state propagation and camera controller convergence.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `TrackingViewModel.kt` and `MapBehaviors.kt`
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModel.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapBehaviors.kt`
* Changes: Add `FollowMeSmoothingStep` and `stepFollowMeSmoothing(...)` in `MapBehaviors.kt`, update `followMeController` to loop until convergence with `zoomFocus` in `LaunchedEffect` keys, and annotate `liveTelemetryFlow` with `REQ-MAP-052.1` / `ATT-3775` in `TrackingViewModel.kt`.

### Step 2: Create `TrackingViewModelMapStateTest.kt`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModelMapStateTest.kt`
* Changes: Implement all unit and contract tests for `TST-MAP-054`.

### Step 3: Targeted Unit Tests
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingViewModelMapStateTest" --tests "com.atrainingtracker.trainingtracker.ui.map.MapPolylineMemoryStabilityTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted tests during Stage 4 construction, followed by full clean-room regression suite in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-3775` allows clean revert without affecting `sprint/2026-42.7`.
