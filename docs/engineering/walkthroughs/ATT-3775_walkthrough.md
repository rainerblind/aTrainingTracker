# Stage 5: Walkthrough & Verification - ATT-3775: Subscribe reactively to currentSpeed and currentBearing in TrackingViewModel.observeMapState so Follow-Me zoom and bearing update continuously

**Ticket**: [ATT-3775](https://rainerblind.atlassian.net/browse/ATT-3775)  
**Sub-task**: [ATT-3873](https://rainerblind.atlassian.net/browse/ATT-3873) (`[Test]`)  
**Parent Epic**: [ATT-1358](https://rainerblind.atlassian.net/browse/ATT-1358) (*In-Ride Map & Navigation Experience*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.7`  
**Requirement Mapping**: `REQ-MAP-052`  
**Test Mapping**: `TST-MAP-054`  
**Branch**: `feature/ATT-3775`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Executive Summary & Verification Overview

This ticket resolves two defects in the live map **Follow-Me** camera pipeline (`ATT-3775` / `REQ-MAP-052`):

1. **Rule 39 Violation in [`TrackingViewModel.observeMapState()`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModel.kt#L249-L359)**:
   - Previously, `observeMapState()` read `banalServiceRepository.currentSpeed.value` and `banalServiceRepository.currentBearing.value` statically inside a `combine` driven only by `currentTrack`, `bSportType`, `staticMapLayerFlow`, and `viewInfo`.
   - Before tracking starts (`READY` mode, where `currentTrack` never emits) or whenever speed/bearing changes without a `currentTrack` emission (e.g., wheel speed sensor or speed decay), `_mapState.userSpeed` and `_mapState.userBearing` were completely frozen.
   - Refactored `observeMapState()` to introduce `liveTelemetryFlow` (`LiveTelemetryTuple`) actively subscribing `banalServiceRepository.currentTrack`, `banalServiceRepository.bSportType`, `banalServiceRepository.currentSpeed`, and `banalServiceRepository.currentBearing` while keeping `staticMapLayerFlow` decoupled (`REQ-MAP-051`).

2. **Single-Step EMA Stall Under `StateFlow` Conflation in [`followMeController`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapBehaviors.kt#L249-L410)**:
   - Previously, `followMeController` executed only a single exponential smoothing step per `LaunchedEffect(currentLocation, bearing, speed, ...)` invocation. Because `StateFlow` conflates equal values, steady speeds or stationary headings caused `filteredSpeed` (`alpha = 0.10f`) and `filteredBearing` (`alpha = 0.25f`) to stall after a single step — and for low speeds (`< 9 km/h`), the single-step zoom change was smaller than the `0.10f` hysteresis deadband, leaving `activeZoom` permanently stuck at `baseZoom`.
   - Extracted pure function `stepFollowMeSmoothing(...)` returning `FollowMeSmoothingStep(filteredSpeed, filteredBearing, activeZoom, effectiveZoom, isConverged)` and added a continuous `do { ... delay(1000L) } while (!step.isConverged)` convergence loop inside `followMeController` (with `zoomFocus` included in `LaunchedEffect` keys).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-MAP-052.1` | `TST-MAP-054.1` | Automated Unit Test (`TrackingViewModelMapStateTest`: reactive `currentSpeed` & `currentBearing` emissions in `READY` & `TRACKING` modes + `REQ-MAP-051` static layer referential identity `===`) | **PASSED** | `Verified` |
| `REQ-MAP-052.2` | `TST-MAP-054.2` | Automated Unit Test (`TrackingViewModelMapStateTest`: `stepFollowMeSmoothing` convergence across low/high steady speeds & $350^\circ \to 10^\circ$ bearing wrap-around + Rule 39 source contract) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-MAP-054.3` | Full Clean-Room `./gradlew testDebugUnitTest` (3,061 unit tests) | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
> Task :app:testDebugUnitTest
3061 tests completed, 0 failed
BUILD SUCCESSFUL
```

### Targeted Unit & Integration Tests
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingViewModelMapStateTest" --tests "com.atrainingtracker.trainingtracker.ui.map.MapPolylineMemoryStabilityTest"
BUILD SUCCESSFUL in 8s
```

---

## 4. Hardware / Physical Verification (Pixel 10)

Pure ViewModel reactive flow and camera smoothing algorithm enhancement; zero visual layout/component Chrome modifications; verified via automated unit & contract test suite (`TrackingViewModelMapStateTest`, `MapPolylineMemoryStabilityTest`) and full clean-room regression suite. On-device live tracking verification will also be performed during Ceremony 2 (`./gradlew installDebug`).

### Visual Consistency (Rule 23 — mandatory if UI is added or changed)
No UI layout or styling changes (map camera zoom/bearing reactive state pipeline only).

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Clean-room unit test suite (`3,061` tests) executed with 100% pass rate.
2. **Living Documentation Synchronized**: Status of `REQ-MAP-052` in `docs/requirements.md` and `TST-MAP-054` in `docs/tests.md` updated to `Verified`.
3. **Subtask Completion**: Stage 5 subtask `ATT-3873` transitioned to `Erledigt` via `freigabe`.
4. **Parent Ticket Final Review**: Parent ticket `ATT-3775` merged into `sprint/2026-42.7` (`--no-ff`) and transitioned to `Final Review (Human)` for final release sign-off.
