# Implementation Plan: High-Fidelity Period Tracks (SCRUM-159)

## 1. Goal
Transition the Period Map visualization from using thinned overviews (decoded from summary polylines) to originally sampled, fine-granular tracks fetched from the samples database.

## 2. Requirement Mapping
*   **REQ-MAP-015**: The system SHALL display fine-granular tracks in the Period Map.
*   **Test ID**: TST-UI-058.

## 3. Impact Analysis (SWE.1.BP.5)
*   **Android System**: Loading raw samples for many workouts in a single period (e.g. Yearly) could consume significant memory.
*   **Component Interfaces**: Update `PeriodSummary` to support rich path data.
*   **UI Performance**: High interaction lag if too many points are rendered. Plan to use the iterative simplification implemented in SCRUM-152 to cap points at ~1000 per track *during rendering* while still maintaining far higher fidelity than the current summary-level thinning.

## 4. Proposed Changes

### Data Layer (PeriodData.kt)
*   Change `workoutIdToPolylineMap` (Long -> String) to `workoutIdToPathMap` (Long -> List<LatLng>).

### ViewModel (PeriodsViewModel.kt)
*   Modify `showPeriodMap(summary: PeriodSummary)`:
    - Instead of just setting the state, it will launch a coroutine to fetch full `TrackType.BEST` points from the `workoutRepository` for every workout ID in the period.
    - Post a updated "Detail-Rich" summary once loaded.
*   Add a loading state to `PeriodsViewModel` to show progress if the period is large (e.g., a full year of GPS data).

### UI Layer (InteractivePeriodMap.kt)
*   Update to receive and render the high-resolution `LatLng` lists.
*   Apply iterative simplification to ensure map responsiveness regardless of data volume.

## 5. Verification Criteria (TST-UI-058)
*   Open a Period Map.
*   Verify that the tracks show fine-granular jitter/detail identical to the Workout Details view.
*   Verify map performance is maintained.
