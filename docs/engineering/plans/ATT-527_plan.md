# Stage 3 Implementation Plan: Elevation Graph Support Zooming (ATT-527)

* **Parent Ticket**: [ATT-527](https://rainerblind.atlassian.net/browse/ATT-527) (*[Feature] Elevation Graph: Support zooming*)
* **Sub-Task**: [ATT-1638](https://rainerblind.atlassian.net/browse/ATT-1638) (*[Impl-Plan] Architecture & Detailed Implementation Plan*)
* **Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)
* **Target Version**: `V4.9.38`
* **Target Sprint**: `2026-40.4`
* **Requirements**: [`REQ-UI-192`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L347)
* **Test Cases**: [`TST-UI-146`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L409)

---

## 1. Architectural Overview (SWE.2)

To satisfy `REQ-UI-192` with zero regression risk and high testability, the implementation is decomposed into a clean separation of concerns:
1. **Pure Mathematical Engine (`ElevationProfileZoomMath`)**:
   A stateless utility object encapsulating all viewport calculations, coordinate transformations, clamping invariants, centroid anchoring, and adaptive tick interval determinations. Having this as a pure Kotlin object enables 100% JVM unit test coverage without mocking Compose or Android Graphics.
2. **Gesture & Viewport State in `ElevationProfile.kt`**:
   Compose state variables managing the active visible window:
   * `zoomScale: Float` ($1.0f .. 10.0f$, default $1.0f$).
   * `startDist: Double` ($0.0 .. (D_{\text{total}} - W_{\text{vis}})$, default $0.0$).
   * `isPanMode: Boolean` (toggled or automatically disambiguated).
3. **Canvas Drawing Pipeline**:
   * Distance ticks, segment polylines, and scrubber markers are mapped through `ElevationProfileZoomMath.distanceToCanvasX`.
   * Visible elevation segments are clipped to the viewport canvas bounds.
   * Scrubber marker and coordinate label render if the active distance lies within the visible window.
4. **Interactive Overlay Affordances**:
   * Interactive zoom badge and reset pill (`1.0x`) visible when $Z > 1.0f$.
   * Single-finger accessible `+` and `-` zoom action buttons.

---

## 2. Detailed Technical Design & Formulations

### 2.1 Pure Mathematical Engine (`ElevationProfileZoomMath`)
```kotlin
object ElevationProfileZoomMath {
    const val MIN_ZOOM = 1.0f
    const val MAX_ZOOM = 10.0f
    const val MIN_TRACK_DIST_FOR_ZOOM = 10.0 // meters

    fun calculateVisibleDistance(totalDist: Double, zoomScale: Float): Double {
        if (totalDist <= MIN_TRACK_DIST_FOR_ZOOM) return totalDist
        val clampedScale = zoomScale.coerceIn(MIN_ZOOM, MAX_ZOOM)
        return totalDist / clampedScale
    }

    fun clampStartDistance(totalDist: Double, visibleDist: Double, rawStartDist: Double): Double {
        val maxStart = (totalDist - visibleDist).coerceAtLeast(0.0)
        return rawStartDist.coerceIn(0.0, maxStart)
    }

    fun distanceToCanvasX(dist: Double, startDist: Double, visibleDist: Double, canvasWidth: Float): Float {
        if (visibleDist <= 0.0) return 0f
        return (((dist - startDist) / visibleDist) * canvasWidth).toFloat()
    }

    fun canvasXToDistance(canvasX: Float, startDist: Double, visibleDist: Double, canvasWidth: Float, totalDist: Double): Double {
        if (canvasWidth <= 0f) return startDist
        val ratio = (canvasX / canvasWidth).toDouble().coerceIn(0.0, 1.0)
        return (startDist + ratio * visibleDist).coerceIn(0.0, totalDist)
    }

    fun applyZoomAtCentroid(
        totalDist: Double,
        currentZoom: Float,
        targetZoom: Float,
        centroidX: Float,
        canvasWidth: Float,
        currentStartDist: Double
    ): Pair<Float, Double> {
        if (totalDist <= MIN_TRACK_DIST_FOR_ZOOM) return Pair(1.0f, 0.0)
        val newZoom = targetZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val currentVisible = calculateVisibleDistance(totalDist, currentZoom)
        val centroidRatio = if (canvasWidth > 0f) (centroidX / canvasWidth).toDouble().coerceIn(0.0, 1.0) else 0.5
        val centroidDist = currentStartDist + centroidRatio * currentVisible
        val newVisible = calculateVisibleDistance(totalDist, newZoom)
        val newStartDist = clampStartDistance(totalDist, newVisible, centroidDist - centroidRatio * newVisible)
        return Pair(newZoom, newStartDist)
    }

    fun calculateAdaptiveDistanceStep(visibleDist: Double, unit: Int): Float {
        return if (unit == MyUnits.METRIC) {
            when {
                visibleDist > 50_000 -> 10_000f
                visibleDist > 20_000 -> 5_000f
                visibleDist > 5_000 -> 1_000f
                visibleDist > 1_500 -> 500f
                visibleDist > 500 -> 100f
                else -> 50f
            }
        } else {
            val visibleMiles = visibleDist / BANALService.METER_PER_MILE
            val mileStep = when {
                visibleMiles > 30 -> 5f
                visibleMiles > 10 -> 2f
                visibleMiles > 3 -> 1f
                visibleMiles > 1 -> 0.5f
                visibleMiles > 0.3 -> 0.1f
                else -> 0.05f
            }
            (mileStep * BANALService.METER_PER_MILE).toFloat()
        }
    }
}
```

### 2.2 Gesture Processing Pipeline
In `ElevationProfile.kt`, we replace the single `detectDragGestures` with a combined pointer input scope:
1. **Transform Gestures (`detectTransformGestures`)**:
   * Evaluates `zoom` and `pan.x`.
   * If `zoom != 1f`: updates `(zoomScale, startDist)` using `applyZoomAtCentroid`.
   * If `pan.x != 0f`: updates `startDist = clampStartDistance(totalDist, visibleDist, startDist - (pan.x / width) * visibleDist)`.
2. **Tap & Double-Tap Gestures (`detectTapGestures`)**:
   * `onDoubleTap`: smoothly resets `zoomScale = 1.0f` and `startDist = 0.0`.
   * `onTap`: converts tap X to distance $d$ via `canvasXToDistance` and emits `onDistanceSelected(d)`.
3. **Scrubbing Drag Gestures**:
   * When dragging with 1 finger: if $Z = 1.0f$, drags the scrubber; if $Z > 1.0f$ in Scrub mode or starting from scrubber, updates `currentDistance`. When in Pan mode, drags pan the visible window.

### 2.3 Visual Overlay & Controls
* **Header Action Row**:
  * Alongside the existing Legend info button (`IconButton(Info)`):
    * If $Z > 1.0f$: Render a compact pill badge with `String.format("%.1fx", zoomScale)` and a close/reset icon (`Icons.Default.Refresh` or `Close`). Tapping resets to $1.0x$.
    * Render `+` (`ZoomIn`) and `-` (`ZoomOut`) icon buttons (scaled to 24dp) for accessible discrete zoom steps ($1.5x, 2.0x, 3.0x, 5.0x, 10.0x$).
    * Render a mode toggle between Pan (`OpenWith`) and Scrub (`TouchApp`).

---

## 3. Atomic Implementation Steps (SWE.3)

1. **Step 1: Domain Logic & Mathematics**:
   * Create `ElevationProfileZoomMath.kt` under `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/`.
2. **Step 2: Unit Testing**:
   * Author `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileZoomMathTest.kt`.
   * Validate initial bounds, centroid anchoring, clamping, invertibility, adaptive ticks, and edge cases.
   * Run tests via `./gradlew testDebugUnitTest --tests "*ElevationProfileZoomMathTest*"`.
3. **Step 3: Component Integration in `ElevationProfile.kt`**:
   * Add zoom and pan state to `ElevationProfile`.
   * Update canvas coordinate calculations to map through `ElevationProfileZoomMath`.
   * Update grid ticks and labels to use adaptive step based on $W_{\text{vis}}$.
   * Integrate multi-touch pinch, pan, tap, and double-tap gestures.
   * Add accessible overlay controls and zoom badge.
4. **Step 4: Regression Testing & Review**:
   * Run clean-room unit tests across all modules.
   * Advance through Gate 3, 4, and 5.

---

## 4. Invariants Check
* **Preserve `ElevationProfile` function signatures**: Default arguments ensure no caller breakage.
* **Preserve `calculateElevationBounds` & `calculateElevationProfileHeight`**: Unaltered.
* **Preserve slope grade colors**: Slope is calculated strictly from physical meters and elevations.
* **Preserve `onDistanceSelected(Double?)`**: Downstream map views receive exact absolute distance in meters.
