# Implementation Plan - SCRUM-130: Interactive Peak Markers on Period Map

Highlight the geographic high-points (Max Altitude and Max Line Distance) for every workout in a period summary with interactive, subordinate markers.

## 1. Requirements Mapping
- **Requirement**: `REQ-UI-033` (Period Peak Markers)
- **Requirement**: `REQ-UI-035` (Peak Marker Alpha Sync)
- **Requirement**: `REQ-UI-036` (Peak Marker Interactivity)
- **Requirement**: `REQ-UI-037` (Peak Marker Scaling)
- **Test ID**: `TST-UI-041` (Period Peak Markers Audit)

## 2. Impact Analysis
- **UI Components**: `InteractivePeriodMap.kt`, `PeriodMapScreen.kt`.
- **Logic**: `PeriodsViewModel.kt`, `WorkoutData.kt`, `WorkoutDataMapper.kt`.
- **Side Effects**: None. This is a visual enhancement to the Period Map.

## 3. Proposed Changes

### 3.1 Data Model Expansion (`WorkoutData.kt`)
- Add `maxAltitudeLatLng: LatLng?` and `maxDisplacementLatLng: LatLng?` to `WorkoutData` to carry the peak coordinates without needing a DB scan during list rendering.

### 3.2 Mapper Update (`WorkoutDataMapper.kt`)
- Update `fromCursor` to fetch the `maxAltitudeLatLng` (ALTITUDE MAX) and `maxDisplacementLatLng` (LINE_DISTANCE_m MAX) from the `workoutSummariesDatabaseManager`.

### 3.3 Period Summary Enrichment (`PeriodData.kt`)
- Define `PeriodPeakMarker` data class: `workoutId: Long`, `pos: LatLng`, `iconResId: Int`, `title: String`.
- Add `extremaMarkers: List<PeriodPeakMarker>` to `PeriodSummary`.

### 3.4 ViewModel Aggregation (`PeriodsViewModel.kt`)
- In `aggregateToPeriod`, iterate through `items` (workouts).
- If a workout has peak coordinates, add a `PeriodPeakMarker` to the list.
- Use `R.drawable.ic_altitude_max` for altitude and `R.drawable.ic_distance` for displacement.

### 3.5 Interactive Map Rendering (`InteractivePeriodMap.kt`)
- Add `extremaMarkers` parameter to `InteractivePeriodMap`.
- Render markers using `com.google.maps.android.compose.Marker`.
- Apply `alpha = visuals.polylineAlpha`.
- Set icon size to `24dp` (using a custom bitmap descriptor or standard marker refinement).
- Implement `onClick` to call `onWorkoutClick(marker.workoutId)`.

## 4. Verification Plan
- **Build**: Ensure successful compilation.
- **Visual Audit**: 
    1. Enter Period Map for a Week/Month.
    2. Verify markers are present and semi-transparent.
- **Functional Audit**:
    1. Click a marker.
    2. Verify the "Peek" BottomSheet opens for the correct workout.
