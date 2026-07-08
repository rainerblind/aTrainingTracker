# Walkthrough: Longest Workout Navigation (SCRUM-157)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-UI-052** | Tapping the "Longest Workout" highlight in a Period Summary SHALL navigate to the filtered workout list and automatically scroll to the specific workout record. | Verified |

## 2. Verification Evidence (TST-UI-053)
*   **Procedure**:
    1. Opened a Period Summary (e.g., "May 2026") that contains multiple cycling workouts.
    2. Located the "Longest Workout" highlight under the Cycling category.
    3. Tapped the "Longest Workout" section.
*   **Observation**:
    *   The app navigated to the `WorkoutSummariesListFragment` filtered for Cycling in May 2026.
    *   The list automatically scrolled to ensure the longest workout was visible on screen.
*   **Result**: **PASS**

## 3. Technical Changes
### Data Propagation
*   Added `scrollToWorkoutId` parameter to `WorkoutSummariesListFragment.newInstance`.
*   Passed this ID through `PeriodsFragment.startWorkoutSummaryList`.
*   Added `onLongestWorkoutClick` callback to `PeriodSummaryCard`, `SportStatsRow`, `PeriodList`, and `PeriodsTabsScreen`.

### UI Interactivity (PeriodSummaryCard.kt)
*   Applied `Modifier.clickable` to the "Longest Workout" Column in `SportStatsRow`.
*   Triggered the new navigation callback when the section is tapped.

### List Logic (WorkoutSummariesListFragment.kt)
*   Retrieved the `initialScrollToId` from fragment arguments.
*   Implemented a `LaunchedEffect(workouts)` that uses `scrollState.scrollToItem()` once the workout list is populated and the target ID is found.
