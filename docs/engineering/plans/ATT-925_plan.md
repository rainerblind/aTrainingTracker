# ASPICE Stage 3: Implementation Plan (SWE.3)
## ATT-925: Edit Lap: Should include entire workout but muted

**Parent Epic**: ATT-826 (Laps)  
**Target Release**: V4.9.36  
**Requirement Mapping**: `REQ-UI-142` (Section 5: Expandable Map & Camera Framing)  
**Verification Test**: `TST-UI-095`  
**Status**: In Bearbeitung  

---

### 1. Architectural Overview & Design Intent

Currently, when the athlete slides up `LapEditBottomSheet` to inspect an individual lap on the map, the camera frame is tightly focused on `lapBounds` (the coordinates of only the active lap). This crops the rest of the workout off-screen and causes jarring camera jumps during sequential lap switching (`< Previous` / `Next >`).

This change refactors the map camera framing and visual layering in `LapEditBottomSheet.kt`:
1. **Full Workout Camera Framing**:
   The camera bounds are calculated from the complete workout coordinate list (`allPoints`) instead of the isolated lap segment. The camera smoothly frames `workoutBounds` with standard 70px padding.
2. **Stable Sequential Navigation**:
   Because `allPoints` is invariant across laps within the same workout session, `workoutBounds` does not change when navigating between laps. The map camera remains rock-solid while the active lap highlight and start/stop markers update smoothly along the course.
3. **Muted Workout Route vs. Vibrant Lap Layering**:
   - **Background Layer (`zIndex = 1f`)**: Full workout polyline rendered in a muted neutral styling (`MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)`, `width = 5f`), clearly delineating the entire route without visually competing with the active lap.
   - **Foreground Layer (`zIndex = 2f`)**: Selected lap segment rendered in high-contrast vibrant primary styling (`MaterialTheme.colorScheme.primary`, `width = 10f`).
   - **Marker Layer (`zIndex = 3f`)**: Start pin (green, `R.drawable.control_start`) and stop pin (red, `R.drawable.control_stop`) pinned at the lap boundary coordinates.
4. **Resilience & Fallbacks**:
   Workouts without GPS (indoor sessions or 0 trackpoints) gracefully render `NoGpsTrackCard()`. Zero-area or singular coordinate bounds are safely padded by `LapSegmentUtils.calculateLapBounds` to prevent camera animation crashes.

---

### 2. Component Modifications & File Plan

#### 2.1 UI Layer: `LapEditBottomSheet.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/LapEditBottomSheet.kt`
* **Changes**:
  1. Replace `val lapBounds = remember(lapSegment) { ... }` with:
     ```kotlin
     val workoutBounds = remember(allPoints) {
         LapSegmentUtils.calculateLapBounds(allPoints)
     }
     ```
  2. Update `LaunchedEffect`:
     ```kotlin
     LaunchedEffect(workoutBounds, isMapLoaded) {
         if (isMapLoaded && workoutBounds != null) {
             try {
                 cameraPositionState.animate(
                     CameraUpdateFactory.newLatLngBounds(workoutBounds, 70),
                     durationMs = 500
                 )
             } catch (e: Exception) {
                 try {
                     cameraPositionState.move(
                         CameraUpdateFactory.newLatLngBounds(workoutBounds, 70)
                     )
                 } catch (ignored: Exception) {}
             }
         }
     }
     ```
  3. Update `GoogleMap` polyline styling and z-indices:
     - Background workout polyline: `color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)`, `width = 5f`, `zIndex = 1f`.
     - Active lap segment: `color = MaterialTheme.colorScheme.primary`, `width = 10f`, `zIndex = 2f`.
     - Start/stop markers: `zIndex = 3f`.

#### 2.2 Geometry Utility Layer: `LapSegmentUtils.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/LapSegmentUtils.kt`
* **Changes**:
  1. Ensure `calculateLapBounds(points: List<LatLng>)` documentation explicitly references support for full workout coordinate lists.
  2. Add inline alias `fun calculateWorkoutBounds(points: List<LatLng>): LatLngBounds? = calculateLapBounds(points)` for clear semantic usage.

#### 2.3 Unit Testing Layer: `LapSegmentUtilsTest.kt`
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/LapSegmentUtilsTest.kt`
* **Changes**:
  1. Add unit test `calculateLapBounds builds valid enclosing bounds for full workout track`:
     - Provide a multi-coordinate workout track covering latitude $48.0 \dots 48.2$ and longitude $11.4 \dots 11.7$.
     - Verify returned bounds encompass the complete route boundaries.

---

### 3. Impact Analysis & System Invariants

| Invariant | Protection Mechanism |
| :--- | :--- |
| **Lap Quantitative Metrics** | `timeTotalS`, `distanceTotalM`, `speedAverageMps`, and `timeStart` remain completely immutable. |
| **Editing & Persistence** | Lap custom `name`, `description`, quick-tag chips, and database persistence (`WorkoutRepository.updateLapDetails` $\rightarrow$ `Laps.db`) are completely isolated and untouched. |
| **Indoor / Zero-GPS Fallback** | `NoGpsTrackCard()` displays gracefully when `workoutData.mapPolyline` is empty or decoded coordinates are empty. |
| **Performance Overhead** | Zero disk or database IO. Bounds calculation runs once in-memory via `remember(allPoints)`. |

---

### 4. Verification Plan

1. **Unit Testing**:
   - Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.LapSegmentUtilsTest"`
2. **Full Repository Clean-Room Regression**:
   - Run `./gradlew testDebugUnitTest` across all 32 actionable tasks (0 failures, 100% pass rate).
3. **Traceability Verification**:
   - Confirm `REQ-UI-142` and `TST-UI-095` synchronization.
