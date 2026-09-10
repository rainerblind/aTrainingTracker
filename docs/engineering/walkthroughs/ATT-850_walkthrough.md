# Walkthrough: Consistent Workout Click-to-Map Navigation & Dedicated Header Edit Action (ATT-850)

* **Parent Ticket**: [ATT-850](https://rainerblind.atlassian.net/browse/ATT-850) (*[Verbesserung] When clicking on a workout, always navigate to the workout on map screen; when clicking on the edit workout, we always go to the edit workout dialog*)
* **Sub-Task**: [ATT-858](https://rainerblind.atlassian.net/browse/ATT-858) (*[Implementation] When clicking on a workout, always navigate to the workout on map screen; when clicking on the edit workout, we always go to the edit workout dialog*)
* **Target Version**: `V4.9.36`
* **Requirement**: [`REQ-SET-071`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L113) (*Consistent Workout Click-to-Map Navigation & Dedicated Header Edit Action*)
* **Test Specification**: [`TST-SET-060`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L229) (*Workout Card Click-to-Map Navigation & Dedicated Header Edit Action Verification*)
* **Branch**: `feature/ATT-850`

---

## 1. Overview & Architecture

Previously in workout list views, tapping anywhere on a workout summary card (Header surface, Description, Details metrics, Extrema) launched `EditWorkoutScreen`, while only tapping the mini-map preview launched `TrackOnMapScreen`. This violated user expectations during activity inspection and caused unintended transitions into edit mode.

Under ATT-850:
1. **Inspection-First Default**: Tapping anywhere on a workout card (Header title/surface, Description, Details, Extrema, MediaSection) routes directly to the detailed map screen ([`TrackOnMapScreen`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt) via `onMapClick()`).
2. **Dedicated Edit Action**: [`WorkoutHeader`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt) encapsulates an edit action button (`R.drawable.ic_table_edit`, `32.dp`, primary tint) positioned on the **very right** of the action row. Clicking it opens [`EditWorkoutScreen`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt).
3. **Preserved Exceptions**:
   - The **Cluster button** in `WorkoutHeader` navigates directly to the linked cluster heatmap screen in `WorkoutClustersFragment` via `onClusterClick(clusterId)`.
   - The **Export status badge** (`ExportStatus`) continues to open its internal `ExportDetailsDialog`.
   - The **Export menu (3-dots)** continues to open the file export format menu.
4. **Compact Card Parity**: [`WorkoutSummaryCompact`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummaryCompact.kt) routes card tap to map view and provides an Edit option in its long-press context menu alongside Delete.

---

## 2. Summary of Changes

### 2.1 Action Row & Edit Action Encapsulation ([`WorkoutHeader.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt))
* Added parameter `onEditWorkout: (() -> Unit)? = null`.
* In top-end action row, placed `actions()` on the left, `IconButton` with `R.drawable.ic_table_edit` in the middle whenever `onEditWorkout != null`, and `more_vert` export button on the **very right** (when `menuEnabled == true`).
* Resulting layout: `actions` $\rightarrow$ `ic_table_edit` $\rightarrow$ `more_vert`.
* Shifts the edit button 32dp away from the screen edge, ensuring it is cleanly accessible and completely avoids the fast scrollbar touch overlay.

### 2.2 Click-to-Map Navigation & Header Edit Wiring ([`WorkoutSummary.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt))
* Re-wired `WorkoutHeader`:
  * `onClicked = onMapClick` (tapping header navigates to map view).
  * `onEditWorkout = onEditWorkout` (forwarded to dedicated edit button).
* Replaced `editWorkoutModifier` with `mapClickModifier = Modifier.clickable { if (workoutData.headerData.finished) onMapClick() }`.
* Applied `mapClickModifier` across `WorkoutDescription`, `WorkoutDetails`, and `WorkoutExtrema`.
* Preserved `WorkoutMediaSection`, `ExportStatus`, and `onClusterClick`.

### 2.3 Cleaner Header Delegation in Map Popup ([`TrackOnMapScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt))
* Forwarded `onEditWorkout = onEditWorkout?.let { edit -> { edit(workoutData.id) } }` directly to `WorkoutHeader`.
* Removed manual inline edit button from `actions` block, delegating layout placement to `WorkoutHeader`.

### 2.4 Compact Summary Parity ([`WorkoutSummaryCompact.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummaryCompact.kt) & [`WorkoutList.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutList.kt))
* Renamed parameter to `onMapClick: () -> Unit` and added `onEditWorkout: (() -> Unit)? = null`.
* Wired card tap to `onMapClick`.
* Added "Edit Workout" menu item with `ic_table_edit` to the long-press context menu alongside "Delete".
* Forwarded callbacks from `WorkoutList`.

### 2.5 Automated Unit Tests ([`WorkoutHeaderDataTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeaderDataTest.kt))
* Added unit test `workoutBodyClick_routesToMapNavigation_andRemainsIsolatedFromEdit` validating:
  * Body click triggers `onMapClick` without triggering edit or cluster navigation.
  * Edit button click triggers `onEditWorkout` without triggering map or cluster navigation.
  * Cluster button click triggers `onClusterClick` without triggering map or edit navigation.
* Added unit test `mapScreenNavigationPrecedence_presentsEditImmediately_andRestoresMapOnDismiss` validating:
  * Tapping edit within `TrackOnMapScreen` immediately resolves active screen to `EDIT`.
  * Dismissing editor directly restores `MAP` view.
  * Pressing back from `MAP` view cleanly restores `LIST` view.

### 2.6 Map View Edit Navigation Precedence ([`WorkoutSummariesTabbedFragment.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesTabbedFragment.kt) & [`WorkoutSummariesListFragment.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesListFragment.kt))
* Inverted condition evaluation order: `if (selectedWorkoutIdForEdit != null)` evaluated before `else if (selectedWorkoutForDetails != null)`.
* Tapping edit in `TrackOnMapScreen` immediately opens `EditWorkoutScreen` without requiring back navigation.
* Dismissing `EditWorkoutScreen` returns directly to `TrackOnMapScreen`.
* Pressing Back from `TrackOnMapScreen` returns cleanly to the workout list at the preserved scroll position.

---

## 3. Verification & Test Evidence

### 3.1 Clean-Room Test Suite
Executed full clean-room unit test suite:
```bash
./gradlew testDebugUnitTest
```
**Result**: `BUILD SUCCESSFUL in 1m 7s` (32 actionable tasks, 0 failures, 0 regressions).

### 3.2 Targeted Action & Navigation Test
Executed `WorkoutHeaderDataTest`:
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderDataTest"
```
**Result**: `BUILD SUCCESSFUL` (all 4 tests passed).

### 3.3 On-Device Live Verification (Pixel 10 / Android 17)

Interactive end-to-end verification was conducted on an attached Google Pixel 10 (`66020DLCR002FL`):
1. **Header Action Ordering**: Edit icon (`ic_table_edit`) is positioned to the left of the 3-dots context menu (`more_vert`).
2. **Reliable Clickability**: Tapping the edit button triggers immediately without interception by the scrollbar (`[859, 1972][943, 2098]` on Pixel 10).
3. **Map Navigation Precedence**: Tapping edit from `TrackOnMapScreen` immediately opens `EditWorkoutScreen` and returns cleanly to map view upon dismissal.

---

## 4. Stage Status & Gate Readiness

* **Sub-Task**: [ATT-864](https://rainerblind.atlassian.net/browse/ATT-864) ([Implementation] Iteration 2) in **`Freigabe (Human)`**.
* **Fix Version**: `V4.9.36` (inherited from [ATT-850](https://rainerblind.atlassian.net/browse/ATT-850)).
* **Follow-up Bug**: [ATT-861](https://rainerblind.atlassian.net/browse/ATT-861) logged for root-cause fix of FastScrollbar touch interception.

