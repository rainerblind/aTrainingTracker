# Walkthrough: High-Granularity Period Tracks (SCRUM-159)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-MAP-015** | The `PeriodMapScreen` SHALL display originally sampled (fine-granular) location tracks instead of thinned overviews. | Verified |

## 2. Verification Evidence (TST-UI-058)
*   **Procedure**:
    1. Opened a Period Map (e.g., Weekly).
    2. Observed the tracks displayed.
*   **Observation**:
    *   Initially, the map loads the thinned overviews (fast track) for immediate feedback.
    *   A background coroutine immediately begins fetching originalmente sampled points for all workouts in the period.
    *   Once loaded, the map automatically updates to show fine-granular detail (including GPS jitter and precise cornering) matching the Workout Details view.
    *   The map performance remains fluid due to adaptive path simplification (~1000 points per track).
*   **Result**: **PASS**

## 3. Technical Changes
### Data Layer (PeriodData.kt)
*   Enhanced `PeriodSummary` with `workoutIdToPathMap` to store data-rich `LatLng` paths.

### Logic Layer (PeriodsViewModel.kt)
*   Implemented lazy background loading in `showPeriodMap`. When a period is selected, the system fetches full `TrackType.BEST` points from the database.
*   Integrated high-performance Douglas-Peucker simplification to cap granular tracks at ~1000 points, ensuring analytical depth without UI interaction lag.

### UI Layer (InteractivePeriodMap.kt)
*   Refactored to prioritize rich paths from `workoutIdToPathMap` while falling back to decoded polylines for instant initial rendering.
