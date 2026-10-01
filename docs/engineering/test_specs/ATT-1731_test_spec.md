# Stage 2 Requirement & Test Specification: ATT-1731

## 1. Ticket & Metadata
- **Parent Ticket**: [ATT-1731](https://atrainingtracker.atlassian.net/browse/ATT-1731) - `[Filter] Remove filtering with respect to Lieblingsstrecken from WorkoutFilterBottomSheet`
- **Subtask**: [ATT-1796](https://atrainingtracker.atlassian.net/browse/ATT-1796) - `Stage 2: Requirement & Test Specification`
- **Target Branch**: `feature/ATT-1731`
- **Target Version**: `V4.9.38`
- **Author**: AI Agent 1 (Implementer)
- **Date**: 2026-10-01

---

## 2. Requirement Specification (`REQ-UI-209`)

### REQ-UI-209: Workout Filter: Elimination of Favorite Tracks (Lieblingsstrecken) Selection Section
The system SHALL eliminate the Favorite Tracks / Route Clusters (*Lieblingsstrecken*) filter section and its chip picker from `WorkoutFilterBottomSheet.kt`, removing high latency and visual bloat while preserving external route drill-down filtering and start location (*"Start at"*) filtering (ATT-1731):

1. **Section Removal (`WorkoutFilterBottomSheet.kt`)**:
   - Section 9 (*Favorite Tracks / Route Clusters*) SHALL be completely removed from `WorkoutFilterBottomSheet.kt`.
   - Local state `localClusterId` and `localClusterName` SHALL be removed.
   - The composable parameter `availableClusters` SHALL be removed from `WorkoutFilterBottomSheet.kt`.
   - Section 8 (*"Start at"* / *"Startet bei"*, `REQ-UI-208`) SHALL become the final section of `WorkoutFilterBottomSheet.kt`.

2. **Caller Streamlining (`WorkoutTabsScreen.kt`)**:
   - `WorkoutTabsScreen.kt` SHALL omit passing `availableClusters` to `WorkoutFilterBottomSheet`.

3. **Drill-Down Filter Invariant Preservation**:
   - In `onApplyCriteria()` in `WorkoutFilterBottomSheet.kt`, active `criteria.clusterId` and `criteria.clusterName` SHALL be preserved on `criteria.copy(...)`.
   - `WorkoutFilterCriteria.matches()` and `ActiveFilterChipsRow.kt` cluster chip presentation SHALL remain 100% operational for drill-down navigation from `KnownLocationCard` and `WorkoutHeader`.
   - All other filter dimensions (query, date interval, sport sub-type, equipment, attributes, distance interval, duration interval, start location) SHALL remain 100% operational.

### Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
1. **Original Requirement ID & Target**: `REQ-UI-187` (*Selection of Lieblingsorte and Lieblingsstrecken in Workout and Cluster Filter Dialogs*) and `REQ-UI-194` (*Filter Dialogs: Clean Chip Formatting and Prioritized Section Ordering*).
2. **Historical Origin & Commit Trace**: Commits `91acde33` (`ATT-1402`, Sprint 2026-40.4) and `c57e911a` (`ATT-1642`, Sprint 2026-40.5).
3. **Root Reason for Existing Formulation**: Multi-dimensional filtering originally placed all filterable criteria in `WorkoutFilterBottomSheet`. Real-world usage revealed that cluster computation takes noticeable time, and displaying dozens of cluster chips created unnecessary visual noise.
4. **Preservation of Core Invariants**: Drill-down from route items into filtered workouts remains fully functional. Start location filtering, active filter chips row, and all other criteria remain 100% intact.

### Acceptance Criteria (Given-When-Then)
- **AC-1 (WorkoutFilterBottomSheet Clean Layout)**:
  - *Given* an athlete opening `WorkoutFilterBottomSheet`,
  - *When* scrolling to the bottom of the bottom sheet,
  - *Then* Section 8 (*"Start at"* / *"Startet bei"*) SHALL be the final filter section, and NO Favorite Tracks (*Lieblingsstrecken*) section or cluster chips SHALL be displayed.
- **AC-2 (Drill-Down Cluster Filter Preservation)**:
  - *Given* an athlete navigated to `WorkoutTabsScreen` via drill-down on a route cluster (`criteria.clusterId != null`),
  - *When* opening `WorkoutFilterBottomSheet` and applying a date or sport filter,
  - *Then* the active cluster filter SHALL NOT be cleared.
- **AC-3 (ActiveFilterChipsRow Unbroken)**:
  - *Given* an active cluster filter applied to workouts,
  - *When* viewing `ActiveFilterChipsRow`,
  - *Then* the cluster chip SHALL render cleanly without emojis and tapping `(X)` SHALL clear the cluster filter.

---

## 3. Test Specification (`TST-UI-163`)

### TST-UI-163: WorkoutFilterBottomSheet Favorite Tracks Section Removal Verification

1. **Structural Contract Unit Tests (`WorkoutFilterBottomSheetClusterRemovalTest.kt`)**:
   - `testWorkoutFilterBottomSheetDoesNotContainClusterSection()`:
     - Assert source code of `WorkoutFilterBottomSheet.kt` does NOT contain `// 9. Favorite Tracks`.
     - Assert source code of `WorkoutFilterBottomSheet.kt` does NOT contain `localClusterId` or `localClusterName`.
     - Assert source code of `WorkoutFilterBottomSheet.kt` does NOT declare parameter `availableClusters`.
   - `testWorkoutTabsScreenDoesNotPassAvailableClustersToBottomSheet()`:
     - Assert source code of `WorkoutTabsScreen.kt` does NOT pass `availableClusters` inside `WorkoutFilterBottomSheet(...)`.

2. **Drill-Down & Domain Integrity Preservation Tests**:
   - Run existing `WorkoutFilterCriteriaClusterTest`: Assert cluster filtering and JSON serialization logic remains 100% operational.
   - Run existing `ActiveFilterChipsRowClusterTest`: Assert active cluster chips row rendering and removal remains 100% operational.

3. **Clean-Room Full Suite Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` across all modules verifying 100% test pass rate with 0 regressions.

---

## 4. Traceability Matrix

| Requirement | Test Specification | Verification Method | Target File(s) | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-209` | `TST-UI-163` | Structural Contract & Preservation Tests (`WorkoutFilterBottomSheetClusterRemovalTest.kt`, `WorkoutFilterCriteriaClusterTest.kt`, `ActiveFilterChipsRowClusterTest.kt`) | `WorkoutFilterBottomSheet.kt`, `WorkoutTabsScreen.kt` | Specified |
