# Walkthrough - SCRUM-130: Interactive Peak Markers on Period Map

Implemented high-density peak achievements (Max Altitude and Max Line Distance) visualization for multi-workout period maps.

## 1. Requirements Fulfilled
- **REQ-UI-033**: Interactive markers for Max Alt and Max Dist.
- **REQ-UI-035**: Marker alpha synchronized with period polylines.
- **REQ-UI-036**: Marker click triggers workout "Peek" in BottomSheet.
- **REQ-UI-037**: Markers scaled down to 24dp for better density.

## 2. Verification Results
- **TST-UI-041**: **PASS**
    - Verified that markers appear for every workout in a week/month summary.
    - Verified that markers inherit the period's alpha (e.g., 0.6f for Yearly).
    - Verified that tapping a marker correctly identifies and peeks the associated workout.

## 3. Technical Changes
- **Model**: Added `maxAltitudeLatLng` and `maxDisplacementLatLng` to `WorkoutData`.
- **Aggregation**: `PeriodsViewModel` now constructs `PeriodPeakMarker` list during period summarization.
- **Map**: `InteractivePeriodMap` renders markers using the unified `createSensorMarker` style (pins) with period-aware alpha.
- **Filtering**: Period map markers now respect the sport-type filter selection.
