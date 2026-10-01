# Stage 1 Analysis: ATT-1731 - Remove Filtering with Respect to Lieblingsstrecken from WorkoutFilterBottomSheet

**Ticket**: [ATT-1731](https://atrainingtracker.atlassian.net/browse/ATT-1731)  
**Sub-task**: [ATT-1795](https://atrainingtracker.atlassian.net/browse/ATT-1795) (`[Analysis]`)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.6`  
**Branch**: `feature/ATT-1731`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Statement & Motivation

During Sprint Review 2026-40.5 (evaluation of [ATT-1642](https://atrainingtracker.atlassian.net/browse/ATT-1642)), the human user evaluated the multi-dimensional filter dialog in [WorkoutFilterBottomSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterBottomSheet.kt). The user observed that:
1. Filtering with respect to **Lieblingsstrecken** (*Favorite Tracks / Route Clusters*) takes too long to become available upon opening the sheet due to asynchronous cluster computation.
2. In real-world usage, the cluster cloud presents far too many chip options, adding visual noise and excessive sheet height.
3. Athletes rarely search for historical workouts by picking a route cluster from the generic workout filter sheet; instead, route inspection is naturally initiated from the dedicated Routes/Clusters tab or drill-down navigation.

Therefore, the user mandated removing the *Lieblingsstrecken* filter section and its selection logic from `WorkoutFilterBottomSheet.kt`, while leaving starting location (*Lieblingsorte* / *"Start at"*) filtering intact.

---

## 2. Root Cause Analysis (Forensic Investigation)

### Forensic Investigation of `WorkoutFilterBottomSheet.kt`:
- **Section 9 (Lines 701–738)**:
  ```kotlin
  // 9. Favorite Tracks / Route Clusters (Lieblingsstrecken, REQ-UI-187, REQ-UI-194)
  if (availableClusters.isNotEmpty()) {
      Column {
          Text(
              text = stringResource(R.string.my_locations),
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.primary
          )
          Spacer(modifier = Modifier.height(6.dp))
          FlowRow(...) {
              availableClusters.forEach { cluster ->
                  val isSelected = localClusterId == cluster.id
                  FilterChip(
                      selected = isSelected,
                      onClick = { ... },
                      label = { Text(cluster.name) }
                  )
              }
          }
      }
  }
  ```
- **Local State (Lines 160–161)**:
  ```kotlin
  var localClusterId by remember(criteria.clusterId) { mutableStateOf(criteria.clusterId) }
  var localClusterName by remember(criteria.clusterName) { mutableStateOf(criteria.clusterName) }
  ```
- **Clear All (Lines 293–294)**:
  ```kotlin
  localClusterId = null
  localClusterName = null
  ```
- **Apply Criteria (Lines 315–316)**:
  ```kotlin
  clusterId = localClusterId,
  clusterName = localClusterName
  ```
- **Parameter `availableClusters` (Line 143)**:
  Passed from `WorkoutTabsScreen.kt` (Line 141), which collected it from `viewModel.availableClusters`.

### Investigation of Drill-Down Interactions:
- In `KnownLocationCard` and `WorkoutHeader`, athletes can tap a route badge or cluster name to drill down directly into workouts matching that route: `WorkoutFilterCriteria(clusterId = cluster.id, clusterName = cluster.name)`.
- In `ActiveFilterChipsRow.kt`, the active chip for route cluster allows the athlete to see and remove the drill-down cluster filter.
- Preserving `criteria.clusterId` and `criteria.clusterName` in `WorkoutFilterCriteria` and `ActiveFilterChipsRow.kt` is essential for backward compatibility and drill-down navigation; only the explicit chip picker section inside `WorkoutFilterBottomSheet.kt` is to be removed.

---

## 3. User Scope Grounding (ATT-1250)

### In-Scope Goals:
1. Remove Section 9 (*Favorite Tracks / Route Clusters*) and its `availableClusters.forEach { FilterChip(...) }` UI from `WorkoutFilterBottomSheet.kt`.
2. Remove local cluster filter state (`localClusterId`, `localClusterName`) from `WorkoutFilterBottomSheet.kt`.
3. In `onApplyCriteria()` in `WorkoutFilterBottomSheet.kt`, preserve any existing `criteria.clusterId` and `criteria.clusterName` on `criteria.copy(...)` so that applying other filters (e.g. date, sport, distance) does not inadvertently clear external drill-down filters.
4. Remove `availableClusters` parameter binding from `WorkoutFilterBottomSheet.kt` and `WorkoutTabsScreen.kt`.
5. Update unit test suites to verify that `WorkoutFilterBottomSheet` does not render cluster filter chips or section 9, while `ActiveFilterChipsRow` and `WorkoutFilterCriteria` continue to support cluster filtering.

### Out-of-Scope Non-Goals (Scope Bounding):
1. **No modification to `ClusterFilterBottomSheet.kt`**: Cluster filter sheet filters clusters themselves by equipment/thresholds/start location; it does not filter by favorite tracks and is unaffected.
2. **No removal of cluster filtering from `WorkoutFilterCriteria` or `WorkoutSummariesViewModel`**: Drill-down from `KnownLocationCard` and `WorkoutHeader` via `criteria.clusterId` remains 100% operational.
3. **No removal of cluster chip from `ActiveFilterChipsRow.kt`**: When a cluster filter is active (via drill-down), the athlete must still be able to see the active filter and tap `(X)` to clear it.
4. **No impact on starting location (*"Start at"*) filtering**: Section 8 in `WorkoutFilterBottomSheet.kt` remains completely untouched.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

- **Original Requirement ID & Target**: `REQ-UI-187` (*Selection of Lieblingsorte and Lieblingsstrecken in Workout and Cluster Filter Dialogs*) and `REQ-UI-194` (*Filter Dialogs: Clean Chip Formatting and Prioritized Section Ordering*).
- **Historical Origin & Commit Trace**: Introduced in commit `91acde33` (`ATT-1402`, Sprint 2026-40.4) and commit `c57e911a` (`ATT-1642`, Sprint 2026-40.5).
- **Root Reason for Existing Formulation**: 
  When multi-dimensional filtering was added in Epic ATT-1396, route clusters were added to `WorkoutFilterBottomSheet` alongside locations so athletes had complete filtering capability in one sheet. In practice, cluster discovery takes noticeable background time, and athletes with 20+ clusters found the chip list overwhelming.
- **Preservation of Core Invariants**: 
  All other filter dimensions (date, sport, equipment, attributes, distance, duration, and start location) remain 100% operational. Drill-down filtering from route items into workouts is fully preserved.

---

## 5. Architectural Strategy & High-Level Solution

```
Before (ATT-1642):
  WorkoutFilterBottomSheet
    ├── 1. Search Text
    ├── 2. Date Interval
    ├── 3. Sport Type
    ├── 4. Equipment
    ├── 5. Attributes
    ├── 6. Distance
    ├── 7. Duration
    ├── 8. Start at (Lieblingsorte)
    └── 9. Lieblingsstrecken (Chips for every cluster)  <-- REMOVED

After (ATT-1731):
  WorkoutFilterBottomSheet
    ├── 1. Search Text
    ├── ...
    └── 8. Start at (Lieblingsorte)  <-- Clean, fast, bounded
```

1. **Remove Section 9 from `WorkoutFilterBottomSheet.kt`**:
   Delete Section 9 block.
2. **Clean State in `WorkoutFilterBottomSheet.kt`**:
   Remove `localClusterId` and `localClusterName`. In `onApplyCriteria`, retain `clusterId = criteria.clusterId, clusterName = criteria.clusterName`.
3. **Clean Invocation in `WorkoutTabsScreen.kt`**:
   Remove `availableClusters = availableClusters` argument.
4. **Contract Verification**:
   Add test asserting `WorkoutFilterBottomSheet.kt` source contains zero references to `availableClusters` or `R.string.my_locations` within its filter sections.

---

## 6. Gate 1 Readiness & Next Steps
- Scope is cleanly bounded, archaeology audit complete.
- Ready for Gate 1 review.
