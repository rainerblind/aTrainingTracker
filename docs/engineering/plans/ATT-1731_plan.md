# Stage 3 Implementation Plan: ATT-1731

## 1. Ticket & Metadata
- **Parent Ticket**: [ATT-1731](https://atrainingtracker.atlassian.net/browse/ATT-1731) - `[Filter] Remove filtering with respect to Lieblingsstrecken from WorkoutFilterBottomSheet`
- **Subtask**: [ATT-1797](https://atrainingtracker.atlassian.net/browse/ATT-1797) - `Stage 3: Implementation Plan`
- **Target Version**: `V4.9.38`
- **Target Branch**: `feature/ATT-1731`
- **Author**: AI Agent 1 (Implementer)
- **Date**: 2026-10-01

---

## 2. Architectural Overview (SWE.2)

```
                            WorkoutTabsScreen
                                    │
                                    │ (omits availableClusters)
                                    ▼
                        WorkoutFilterBottomSheet
            ┌───────────────────────────────────────────────┐
            │ 1. Text Search Input                          │
            │ 2. Time & Period (Year & Date Interval)       │
            │ 3. Sport Sub-Type                             │
            │ 4. Equipment (Bikes & Shoes)                  │
            │ 5. Workout Attributes                         │
            │ 6. Distance Interval                          │
            │ 7. Duration Interval                          │
            │ 8. Start at (Favorite Locations, REQ-UI-208)  │
            │ [REMOVED: Section 9 Favorite Tracks]          │
            └───────────────────────────────────────────────┘
                                    │
                                    │ onApplyCriteria(criteria.copy(
                                    │    clusterId = criteria.clusterId,
                                    │    clusterName = criteria.clusterName,
                                    │    ...))
                                    ▼
                           WorkoutFilterCriteria
                  (preserves active route drill-down filter)
```

The change removes Section 9 (*Favorite Tracks / Route Clusters*) and the `availableClusters` parameter from `WorkoutFilterBottomSheet.kt`, while ensuring that any existing drill-down cluster filter (`criteria.clusterId`, `criteria.clusterName`) is cleanly preserved when other filter dimensions are modified and applied.

---

## 3. Atomic Implementation Steps

### Step 1: Streamline `WorkoutFilterBottomSheet.kt`
- **Target File**: [WorkoutFilterBottomSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterBottomSheet.kt)
- **Modifications**:
  1. Remove unused import: `import com.atrainingtracker.trainingtracker.database.WorkoutCluster`.
  2. Remove `availableClusters: List<WorkoutCluster> = emptyList()` parameter from `WorkoutFilterBottomSheet(...)`.
  3. Remove local state variables:
     ```kotlin
     var localClusterId by remember(criteria.clusterId) { mutableStateOf(criteria.clusterId) }
     var localClusterName by remember(criteria.clusterName) { mutableStateOf(criteria.clusterName) }
     ```
  4. In `onClearAll` lambda:
     - Remove `localClusterId = null` and `localClusterName = null`.
  5. In `onApply` lambda:
     - Replace `clusterId = localClusterId` and `clusterName = localClusterName` with:
       ```kotlin
       clusterId = criteria.clusterId,
       clusterName = criteria.clusterName
       ```
     - This guarantees that if a user opens the filter bottom sheet while a route cluster drill-down filter is active, applying new date/sport/distance filters preserves the cluster filter invariant.
  6. Remove Section 9 UI block entirely:
     ```kotlin
     // 9. Favorite Tracks / Route Clusters (Lieblingsstrecken, REQ-UI-187, REQ-UI-194)
     if (availableClusters.isNotEmpty()) {
         ...
     }
     ```
     Section 8 (*"Start at"*, `REQ-UI-208`) becomes the final section in the bottom sheet.

### Step 2: Streamline Caller in `WorkoutTabsScreen.kt`
- **Target File**: [WorkoutTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutTabsScreen.kt)
- **Modifications**:
  - In `showFilterBottomSheet` invocation block (around line 133):
    - Remove `availableClusters = availableClusters`.
    - Retain all other arguments (`criteria`, `allWorkouts`, `onApplyCriteria`, `onClearAll`, `onDismissRequest`, `activeBSportType`, `knownLocations`).

### Step 3: Implement Targeted Structural Contract Unit Tests
- **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterBottomSheetClusterRemovalTest.kt`
- **Test Invariants**:
  1. `testWorkoutFilterBottomSheetDoesNotContainClusterSection()`:
     - Verify `WorkoutFilterBottomSheet.kt` does not contain `"// 9. Favorite Tracks"`.
     - Verify `WorkoutFilterBottomSheet.kt` does not declare `localClusterId` or `localClusterName`.
     - Verify `WorkoutFilterBottomSheet.kt` does not declare `availableClusters`.
     - Verify `WorkoutFilterBottomSheet.kt` preserves `clusterId = criteria.clusterId` in `onApply`.
  2. `testWorkoutTabsScreenDoesNotPassAvailableClustersToBottomSheet()`:
     - Verify `WorkoutTabsScreen.kt` call to `WorkoutFilterBottomSheet` does not contain `availableClusters =`.

### Step 4: Verification & Regression Testing
- **Execution**:
  - Run targeted unit tests:
    ```bash
    ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutFilterBottomSheetClusterRemovalTest"
    ```
  - Run existing cluster filter criteria & active chips tests:
    ```bash
    ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutFilterCriteriaClusterTest"
    ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.ActiveFilterChipsRowClusterTest"
    ```
  - Run full test suite:
    ```bash
    ./gradlew testDebugUnitTest
    ```

---

## 4. Invariant Protection & Scope Bounding

1. **ClusterFilterBottomSheet Untouched**: `ClusterFilterBottomSheet.kt` retains its route cluster filtering and start location filters.
2. **ActiveFilterChipsRow Untouched**: `ActiveFilterChipsRow.kt` retains clean rendering and dismissal of route cluster chips when drill-down filtering is active.
3. **Drill-Down Filter Preservation Invariant**: Navigating from a route item (`KnownLocationCard`, `WorkoutHeader`) sets `criteria.clusterId`. When opening `WorkoutFilterBottomSheet` to tweak sport or date, applying filters does NOT clear the active cluster filter.
4. **Section 8 Finality**: Section 8 (*"Start at"*, `REQ-UI-208`) is now the final filter section in `WorkoutFilterBottomSheet.kt`.
5. **No Regressions**: Full clean-room test suite passes with 0 failures.
