# Stage 3: Implementation Plan - ATT-1987: [Aftermath/Graphs] Calibrate Pan Gesture Sensitivity and Travel Distance Across Telemetry Graphs

**Ticket**: [ATT-1987](https://rainerblind.atlassian.net/browse/ATT-1987)  
**Sub-task**: [ATT-2036](https://rainerblind.atlassian.net/browse/ATT-2036) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.11`  
**Requirement Mapping**: `REQ-UI-232` (*Aftermath/Graphs: Synchronized Horizontal Window Panning Across Stacked Telemetry Metric Graphs in Pan Mode*), `REQ-UI-234` (*UI & Interaction Design System: Cross-Domain Chart Viewport Separation*)  
**Test Mapping**: `TST-UI-190`  
**Branch**: `feature/ATT-1987`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Description & Background

During Sprint 2026-40.10 review on physical Google Pixel 10 hardware, user testing confirmed that horizontal pan navigation is fluid and responsive across Telemetry Graphs (Speed/Pace, Heart Rate, Power). However, horizontal swiping on the Elevation Profile failed to move the viewport window.

Forensic root cause analysis identified two coupled issues:
1. **Cross-Domain Scalar Coupling in `MapDetailLayout.kt`**:
   When `REQ-UI-233` decoupled X-axis domains (Distance for Elevation, Time for Telemetry), `MapDetailLayout.kt` retained a single raw scalar `var profileStartDist: Double`. Panning telemetry graphs produced values in seconds (e.g. 1,200s). In `ElevationProfile`, this scalar was ingested as meters, or conversely, elevation profile meter offsets (e.g. 15,000m) exceeded total workout time (e.g. 3,600s), causing clamping collisions in `ElevationProfileZoomMath.applyPan` and locking the viewport at `maxStart` or `0.0`.
2. **Gesture Lifecycle and Touch Tracking in `ElevationProfile.kt`**:
   `ElevationProfile.kt` captured callbacks via `rememberUpdatedState(::updateZoom)`, creating reflection function objects on each recomposition. Furthermore, `prevCentroid` was not reset when transitioning to `isDragging = true`, causing an initial jump from accumulated touch slop, and `if (pointer.isConsumed) break` terminated the gesture loop prematurely when nested inside `Modifier.verticalScroll`.

This plan specifies the SWE.2 architecture and step-by-step construction to normalize the viewport offset into a dimensionless start progress fraction (`viewportStartFraction`), implement bidirectional domain-to-fraction mappings, and stabilize the pointer input lifecycle in `ElevationProfile.kt`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-232` (*Aftermath/Graphs: Synchronized Horizontal Window Panning Across Stacked Telemetry Metric Graphs in Pan Mode*)
  - Clause 1: Backward-compatible composable signature and state ingestion.
  - Clause 2: Cancellation-free pointer observation with dynamic states captured via `rememberUpdatedState`.
  - Clause 3: Stationary tap isolation and drag completion release mechanics in Pan Mode.
  - Clause 4: ElevationProfile callback lifecycle without reflection allocations, touch slop jump elimination, and gesture loop continuity.
  - Clause 5: Normalized viewport start progress fraction in `MapDetailLayout.kt`, bidirectional domain mapping, zero-span protection, and lockstep multi-chart synchrony.
  - Clause 6: Preservation of core invariants.
* **Requirement**: `REQ-UI-234` (*UI & Interaction Design System: Mutually Exclusive Binary Mode Selection & Component Heuristics*)
  - Clause 3: Cross-Domain Chart Viewport Separation (prohibiting shared raw metric scalars across distance and time coordinates).
* **Test Mapping**: `TST-UI-190` (*Pan Mode Gesture Sensitivity & Cross-Domain Continuous Panning Verification*)
  - `[TST-UI-190.1]`: PointerInput keys decoupling contract in `TelemetryMetricGraph.kt`.
  - `[TST-UI-190.2]`: PointerInput keys decoupling contract in `ElevationProfile.kt`.
  - `[TST-UI-190.3]`: Bidirectional fraction-to-domain math & zero-span unit test (`MapDetailLayoutCrossDomainPanTest.kt`).
  - `[TST-UI-190.4]`: ElevationProfile drag updating Telemetry offset in lockstep (`MapDetailLayoutCrossDomainPanTest.kt`).
  - `[TST-UI-190.5]`: TelemetryMetricGraph drag updating Elevation offset in lockstep (`MapDetailLayoutCrossDomainPanTest.kt`).
  - `[TST-UI-190.6]`: ElevationProfile gesture continuity and callback contract test (`ElevationProfileGestureContractTest.kt`).
  - `[TST-UI-190.7]`: Clean-room full suite regression (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Vertical Scroll Freedom (`REQ-UI-226`)**: Dominant vertical gestures ($|\Delta y| > |\Delta x|$) must pass through unconsumed to parent `Modifier.verticalScroll` without pointer locking.
2. **Horizontal Plot Padding Parity**: Left (`50.dp`) and right (`25.dp`) plot bounds must remain identical between `ElevationProfile.kt` and `TelemetryMetricGraph.kt` to ensure perfect vertical line alignment.
3. **Continuous Route Scrubbing Parity (`isPanMode == false`)**: Scrubbing on either chart must continue to resolve the nearest `PathPoint` and dispatch route distance in meters, keeping the map marker and cursor lines in lockstep.
4. **Zero-Span Safety**: Trackless workouts, zero-length paths, or zero duration must return `0.0` without producing `NaN` or division-by-zero crashes.
5. **Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`; subtasks complete autonomously upon passing Gate audit via `freigabe`.

---

## 4. Proposed Architectural Changes

### Component 1: `MapDetailLayout.kt` (UI Orchestration & Coordinate Normalization)
* Replace raw scalar `var profileStartDist by remember(activeScrubPath) { mutableDoubleStateOf(0.0) }` with normalized dimensionless fraction:
  ```kotlin
  var viewportStartFraction by remember(activeScrubPath) { mutableDoubleStateOf(0.0) }
  var profileZoomScale by remember(activeScrubPath) { mutableFloatStateOf(1.0f) }
  ```
* Introduce pure mathematical mapping functions (or companion helper `MapDetailViewportMath`):
  ```kotlin
  object MapDetailViewportMath {
      fun fractionToDomain(fraction: Double, totalSpan: Double, zoomScale: Float): Double {
          if (totalSpan <= 0.0) return 0.0
          val maxStart = (totalSpan - totalSpan / zoomScale).coerceAtLeast(0.0)
          return (fraction * totalSpan).coerceIn(0.0, maxStart)
      }

      fun domainToFraction(domainVal: Double, totalSpan: Double, zoomScale: Float): Double {
          if (totalSpan <= 0.0) return 0.0
          val maxFraction = (1.0 - 1.0 / zoomScale).coerceAtLeast(0.0)
          return (domainVal / totalSpan).coerceIn(0.0, maxFraction)
      }
  }
  ```
* Compute domain spans:
  - Elevation Profile Span: `val elevationTotalSpan = if (isElevationTimeDomain) (activeScrubPath?.lastOrNull()?.timeSec ?: 0).toDouble() else (activeScrubPath?.lastOrNull()?.distance ?: 0.0)`
  - Telemetry Graphs Span: `val telemetryTotalSpan = if (activeTelemetryDomain == ProfileXAxisDomain.TIME) (activeScrubPath?.lastOrNull()?.timeSec ?: 0).toDouble() else (activeScrubPath?.lastOrNull()?.distance ?: 0.0)`
  - Global Toolbar Span: `val totalSpan`
* Translate domain values on dispatch and ingestion:
  - `ElevationProfile`:
    - `startDist = MapDetailViewportMath.fractionToDomain(viewportStartFraction, elevationTotalSpan, profileZoomScale)`
    - `onZoomChanged = { z, s -> profileZoomScale = z; viewportStartFraction = MapDetailViewportMath.domainToFraction(s, elevationTotalSpan, z) }`
  - `TelemetryMetricGraph` (Speed/Pace, HR, Power):
    - `startDist = MapDetailViewportMath.fractionToDomain(viewportStartFraction, telemetryTotalSpan, profileZoomScale)`
    - `onZoomChanged = { z, s -> profileZoomScale = z; viewportStartFraction = MapDetailViewportMath.domainToFraction(s, telemetryTotalSpan, z) }`
  - `GlobalTelemetryZoomToolbar`:
    - `startDist = MapDetailViewportMath.fractionToDomain(viewportStartFraction, totalSpan, profileZoomScale)`
    - `onZoomChanged = { z, s -> profileZoomScale = z; viewportStartFraction = MapDetailViewportMath.domainToFraction(s, totalSpan, z) }`

### Component 2: `ElevationProfile.kt` (Pointer Input & Callback Lifecycle)
* Decouple callback allocation:
  - Replace `val currentUpdateZoomState by rememberUpdatedState(::updateZoom)` with:
    ```kotlin
    val currentOnZoomChangedState by rememberUpdatedState(onZoomChanged)
    ```
    and define inline updater invoking `currentOnZoomChangedState?.invoke(newZoom, newStart) ?: run { internalZoomScale = newZoom; internalStartDist = newStart }`.
* Touch slop reset:
  - In `awaitEachGesture`, when transitioning to horizontal drag:
    ```kotlin
    } else if (ChartGestureDisambiguator.isDominantHorizontal(diffX, diffY, touchSlop)) {
        isDragging = true
        prevCentroid = pointer.position
    }
    ```
* Gesture loop continuity:
  - In `isVerticalScrolling`, remove `if (pointer.isConsumed) break`, allowing pointer releases to naturally conclude the gesture via `pressed.isEmpty()`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Encapsulate Viewport Math in `MapDetailViewportMath.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailViewportMath.kt`
* **Implementation**:
  - Implement `fractionToDomain(fraction: Double, totalSpan: Double, zoomScale: Float): Double`.
  - Implement `domainToFraction(domainVal: Double, totalSpan: Double, zoomScale: Float): Double`.
  - Include zero-span checks and defensive clamping.

### Step 2: Refactor Viewport State and Domain Wiring in `MapDetailLayout.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`
* **Implementation**:
  - Replace `profileStartDist` with `viewportStartFraction`.
  - Wire `elevationTotalSpan` and `telemetryTotalSpan`.
  - Supply mapped `startDist` and bidirectional `onZoomChanged` callbacks to `ElevationProfile`, `TelemetryMetricGraph` instances, and `GlobalTelemetryZoomToolbar`.

### Step 3: Stabilize Gesture Lifecycle & Callbacks in `ElevationProfile.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`
* **Implementation**:
  - Capture `onZoomChanged` directly via `rememberUpdatedState`.
  - Reset `prevCentroid = pointer.position` upon `isDragging = true`.
  - Remove premature `break` in `isVerticalScrolling`.

### Step 4: Implement Mathematical & Cross-Domain Lockstep Tests
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutCrossDomainPanTest.kt`
* **Coverage**:
  - Test fraction-to-domain calculation and clamping across Distance ($25,000\text{m}$) and Time ($3,600\text{s}$) domains (`[TST-UI-190.3]`).
  - Test ElevationProfile drag updating fraction and translating to Telemetry start offset (`[TST-UI-190.4]`).
  - Test Telemetry drag updating fraction and translating to Elevation start offset (`[TST-UI-190.5]`).
  - Test zero and negative span robustness (`[TST-UI-190.3]`).

### Step 5: Implement ElevationProfile Gesture & Callback Contract Tests
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileGestureContractTest.kt`
* **Coverage**:
  - Verify `ElevationProfile.kt` does not pass mutable `currentStartDist` or `currentZoomScale` to `pointerInput` keys (`[TST-UI-190.2]`).
  - Verify `rememberUpdatedState(onZoomChanged)` usage without function allocation (`[TST-UI-190.6]`).
  - Verify `prevCentroid` reset upon drag engagement (`[TST-UI-190.6]`).

### Step 6: Targeted Test Verification
* Execute targeted unit tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutCrossDomainPanTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfileGestureContractTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphGestureTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfileGestureTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**:
  1. Targeted unit and contract tests during Stage 4.
  2. Full clean-room test suite run in Stage 5: `./gradlew testDebugUnitTest`.
  3. Requirement governance check: `python3 tools/verify_requirement_governance.py`.
* **Rollback Plan**:
  - Work is strictly isolated on branch `feature/ATT-1987`.
  - If regressions occur, changes can be rolled back via `git checkout sprint/2026-40.11` without affecting other sprint tickets.
