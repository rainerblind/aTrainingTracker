# ASPICE Stage 1: Problem & System Analysis (SWE.1 / SYS.2)
## ATT-925: Edit Lap: Should include entire workout but muted

**Parent Epic**: ATT-826 (Laps)  
**Target Release**: V4.9.36  
**Issue Type**: Improvement (Verbesserung)  
**Status**: In Bearbeitung  

---

### 1. Executive Summary & Problem Statement

In the workout summary, athletes can tap any lap row in the laps table to open `LapEditBottomSheet` to inspect split metrics, assign quick-tag chips (e.g. *Warm-up*, *Interval*, *Hill Climb*), edit custom names and descriptions, navigate sequentially across laps, and inspect the lap on a map by expanding the sheet upward.

Under the initial implementation of the map view (ATT-511), the map camera bounds were calculated strictly around the active lap segment:
```kotlin
val lapBounds = remember(lapSegment) {
    LapSegmentUtils.calculateLapBounds(lapSegment)
}
...
cameraPositionState.animate(
    CameraUpdateFactory.newLatLngBounds(lapBounds, 70),
    durationMs = 500
)
```

#### Deficiencies Identified:
1. **Lack of Global Spatial Context**:
   Because the camera automatically zooms into `lapBounds`, the map view is cropped tightly to only the active lap. The athlete cannot see where this lap occurred within the total workout course (e.g., whether it was the initial hill climb, a loop around a lake, or a finishing stretch).
2. **Off-Screen Background Track**:
   Although `LapEditBottomSheet` draws the full workout polyline in the background, the extreme zoom level pushes the rest of the workout completely outside the visible viewport.
3. **Disorienting Sequential Navigation**:
   When navigating sequentially between laps using `< Previous` and `Next >`, the camera repeatedly recalculates bounding boxes and pans/zooms to different local coordinates, causing visual jumping rather than providing a stable reference perspective of the course.

---

### 2. User Motivation & Requirements Analysis

Athletes require clear spatial reference when analyzing interval splits or editing lap annotations:
- The map should present the **entire workout course** inside the camera viewport at a glance.
- The entire workout course should serve as a **muted background route** (subtle, neutral color and width) showing the total track geometry.
- The **active lap segment** must stand out prominently in the foreground with vibrant primary styling and distinct start (green) and end (red) pins.
- Navigating across laps via `< Previous` and `Next >` should maintain **stable camera framing**, allowing the athlete to watch the highlighted segment move sequentially along the course.
- If GPS coordinates are unavailable (e.g., stationary trainer workouts), the existing `NoGpsTrackCard` fallback must be preserved.

---

### 3. Architecture & Implementation Strategy

#### 3.1 Geometry & Camera Bounding Box Calculation (`LapSegmentUtils.kt` & `LapEditBottomSheet.kt`)
Instead of restricting camera bounding box calculation to `lapSegment`, the camera target must enclose the complete workout polyline `allPoints`:
```kotlin
val workoutBounds = remember(allPoints) {
    LapSegmentUtils.calculateLapBounds(allPoints)
}
```
* `LapSegmentUtils.calculateLapBounds(points: List<LatLng>)` already provides robust bounding box calculation with singular-point protection against zero-area camera animation crashes.
* We can expose or document `calculateWorkoutBounds(points: List<LatLng>): LatLngBounds? = calculateLapBounds(points)` in `LapSegmentUtils.kt` for semantic clarity.
* In `LapEditBottomSheet.kt`, `LaunchedEffect(workoutBounds, isMapLoaded)` will animate the camera to `workoutBounds` with standard padding (70px). Because `workoutBounds` depends on `allPoints` (which is constant for the session), camera framing remains steady during sequential lap switching.

#### 3.2 Visual Hierarchy & Layering (Muted Workout vs. Vibrant Lap)
Within the `GoogleMap` composable in `LapEditBottomSheet.kt`:
1. **Background Layer (Muted Workout Track)**:
   - Points: `allPoints` (entire workout).
   - Styling: Muted neutral tone with clear contrast against terrain tiles (`MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)` or `Color.Gray.copy(alpha = 0.6f)`).
   - Width: `4f` to `5f`.
   - `zIndex`: `1f`.
2. **Foreground Layer (Active Lap Segment)**:
   - Points: `lapSegment`.
   - Styling: Vibrant primary color (`MaterialTheme.colorScheme.primary`).
   - Width: `10f`.
   - `zIndex`: `2f`.
3. **Marker Layer (Lap Boundary Pins)**:
   - Start Marker: `lapSegment.firstOrNull()`, green pin (`R.drawable.control_start`, `TTColor.StartPoint`), `zIndex = 3f`.
   - Stop Marker: `lapSegment.lastOrNull()`, red pin (`R.drawable.control_stop`, `TTColor.EndPoint`), `zIndex = 3f`.

---

### 4. Impact Analysis & Invariants (SWE.1.BP.5)

| Component | Nature of Impact | Risk / Invariant Guard |
| :--- | :--- | :--- |
| `LapEditBottomSheet.kt` | UI Composable (Map bounds & polyline zIndex/styling) | **Low**. Only affects map camera bounding box and polyline layer styling. Text fields, metrics card, and saving logic are unaffected. |
| `LapSegmentUtils.kt` | Geometry Utility | **Zero**. Pure mathematical helper functions; existing methods remain backward compatible. |
| `LapSegmentUtilsTest.kt` | Unit Test Suite | **Positive**. Adds verification test case for full workout bounds computation. |
| `Laps.db` / `WorkoutData` | Data Layer | **Zero**. No database schema or repository changes required. |
| Indoor Fallback | UI Fallback | **Guaranteed**. `NoGpsTrackCard` renders when `mapPolyline` is empty or zero GPS points exist. |

---

### 5. Verification & Test Strategy

1. **Unit Test Verification (`LapSegmentUtilsTest.kt`)**:
   - Verify `calculateLapBounds(allPoints)` correctly calculates `LatLngBounds` enclosing the entire workout coordinate list.
2. **Compose Map Integration**:
   - Verify `LapEditBottomSheet` camera bounds target `workoutBounds` enclosing all points.
   - Verify entire workout is drawn with muted polyline styling and active lap is drawn with primary polyline and markers.
   - Verify sequential navigation updates the highlighted lap segment without erratic camera jumping.
3. **Clean-Room Regression**:
   - Full test suite `./gradlew testDebugUnitTest` must pass with 100% success rate.
