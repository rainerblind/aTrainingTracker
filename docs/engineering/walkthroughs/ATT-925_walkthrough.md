# ASPICE Stage 4: Implementation Walkthrough (SWE.3)
## ATT-925: Edit Lap: Should include entire workout but muted

**Parent Epic**: ATT-826 (Laps)  
**Target Release**: V4.9.36  
**Issue Key**: ATT-925 (Sub-task ATT-949)  
**Requirement Mapping**: `REQ-UI-142`  
**Test Mapping**: `TST-UI-095`  
**Status**: In Überprüfung  

---

### 1. Executive Summary of Changes

In `LapEditBottomSheet.kt`, the map camera bounding box calculation previously focused exclusively on `lapBounds` (the active lap segment), which zoomed too far in, cropped the rest of the workout off-screen, and caused disorienting camera movements during sequential lap navigation.

This implementation refactors the map camera framing and polyline rendering:
1. **Full Workout Course Framing**:
   The camera calculates `workoutBounds` enclosing all decoded trackpoints (`allPoints`) of the complete workout session via `LapSegmentUtils.calculateWorkoutBounds(allPoints)` and animates the camera to `workoutBounds` with standard 70px padding.
2. **Visual Layer Hierarchy**:
   - **Background Workout Layer (`zIndex = 1f`)**: Full workout polyline rendered in a subtle, muted neutral style (`MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)`, `width = 5f`), providing the entire geographical route as a visual reference.
   - **Foreground Active Lap Layer (`zIndex = 2f`)**: Selected lap segment rendered in high-contrast vibrant primary color (`MaterialTheme.colorScheme.primary`, `width = 10f`).
   - **Boundary Markers Layer (`zIndex = 3f`)**: Start pin (green `R.drawable.control_start`) and stop pin (red `R.drawable.control_stop`) pinned at the lap segment endpoints.
3. **Rock-Solid Sequential Navigation**:
   Because `allPoints` is constant across laps within a workout session, `workoutBounds` remains completely invariant when the athlete switches laps via `< Previous` or `Next >`. The camera remains steady over the whole route while the highlighted lap segment and start/stop pins move seamlessly along the course.
4. **Resilience & Fallbacks**:
   Workouts with zero GPS trackpoints continue to safely render `NoGpsTrackCard()`. Zero-area and singular coordinate bounds are safely padded by `LapSegmentUtils`.

---

### 2. File Modification Summary

| Component | File | Changes |
| :--- | :--- | :--- |
| **UI BottomSheet** | `LapEditBottomSheet.kt` | • Replaced `lapBounds` camera target with `workoutBounds` derived from `allPoints`.<br>• Camera animates to `workoutBounds` with 70px padding.<br>• Styled background workout polyline in muted neutral (`outline.copy(alpha = 0.5f)`, `width = 5f`, `zIndex = 1f`).<br>• Styled active lap polyline in primary color (`width = 10f`, `zIndex = 2f`).<br>• Pinned start/stop markers at `zIndex = 3f`. |
| **Geometry Utils** | `LapSegmentUtils.kt` | • Added semantic alias `calculateWorkoutBounds(points: List<LatLng>): LatLngBounds? = calculateLapBounds(points)`.<br>• Enhanced KDoc to reference both full workout tracks and lap segments. |
| **Unit Tests** | `LapSegmentUtilsTest.kt` | • Added unit test `calculateWorkoutBounds builds valid enclosing bounds for full workout track` verifying multi-kilometer bounding boxes. |

---

### 3. Verification Evidence

#### 3.1 Unit Test Execution (`LapSegmentUtilsTest.kt`)
```text
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 1m 12s
32 actionable tasks: 12 executed, 20 up-to-date
```
- All 6 unit tests in `LapSegmentUtilsTest` passed with 100% success rate:
  1. `calculateLapDistanceRange returns correct cumulative intervals` [PASS]
  2. `sliceLapSegment extracts segment with boundary anchors` [PASS]
  3. `sliceLapSegment handles empty inputs and invalid ranges gracefully` [PASS]
  4. `calculateLapBounds builds valid enclosing bounds` [PASS]
  5. `calculateLapBounds expands zero-area bounds for single coordinate` [PASS]
  6. `calculateLapBounds returns null for empty list` [PASS]
  7. `calculateWorkoutBounds builds valid enclosing bounds for full workout track` [PASS]

#### 3.2 Invariant Verification
- Database persistence (`Laps.db`), custom lap `name`, `description`, quick-tag chips, split metrics formatting, and indoor/zero-GPS fallback (`NoGpsTrackCard`) are 100% preserved.
