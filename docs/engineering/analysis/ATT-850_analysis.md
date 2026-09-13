# Analysis: Consistent Workout Click-to-Map Navigation & Dedicated Header Edit Action (ATT-850)

## 1. Problem Statement & Motivation
In workout history views ([`WorkoutSummariesTabbedFragment`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesTabbedFragment.kt) and [`WorkoutSummariesListFragment`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesListFragment.kt)), each [`WorkoutSummary`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt) card displays several content sections:
- Header ([`WorkoutHeader`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt))
- Description (`WorkoutDescription`)
- Details (`WorkoutDetails`)
- Extrema (`WorkoutExtrema`)
- Strava Activity section
- Map & elevation profile (`WorkoutMediaSection`)
- Export status ([`ExportStatus`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/export/ExportStatusGroup.kt))

### Current Behavioral Inconsistency
Currently, tapping the Header, Description, Details, or Extrema sections invokes `onEditWorkout()`, immediately launching the editing form ([`EditWorkoutScreen`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt)). Only tapping the embedded map preview invokes `onMapClick()` to navigate to the detailed map screen ([`TrackOnMapScreen`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt)).

This violates standard user expectations: tapping a workout in a summary list should open the comprehensive inspection view (the workout on map screen). Editing is an explicit maintenance action that should have its own dedicated action button.

The user specified:
> *"When clicking on a workout, always navigate to the workout on map screen; when clicking on the edit workout, we always go to the edit workout dialog*  
> *Except for the Cluster button*  
> *and the Export status."*

---

## 2. Component & Architecture Analysis

### 2.1 `WorkoutSummary.kt` Interaction Model
In [`WorkoutSummary.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt):
1. **Body Sections (Header, Description, Details, Extrema)**:
   - Previously:
     ```kotlin
     val editWorkoutModifier = Modifier.clickable {
         if (workoutData.headerData.finished) { onEditWorkout() }
     }
     ```
   - Target: Change the body click action to `onMapClick()`. Tapping the header, description, metrics, extrema, or map preview routes to the full-fidelity workout map view.
2. **Exceptions Preserved**:
   - **Cluster Button**: When clustered (`clusterId > 0`), the subtle-primary pill button in `WorkoutHeader` continues routing directly to `onClusterClick(clusterId)` (opening the cluster heatmap in `WorkoutClustersFragment`).
   - **Export Status**: The `ExportStatus` section at the bottom of the card maintains its internal click target opening `ExportDetailsDialog`.
   - **Export Menu (3-dots)**: The `IconButton` with `R.drawable.ic_baseline_more_vert_24` continues to open the file export dropdown (TCX, GPX, CSV, GC, Strava, Save as Route).

### 2.2 Dedicated Edit Action in `WorkoutHeader.kt`
To allow editing a workout from `WorkoutSummary`, [`WorkoutHeader.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt) requires a dedicated Edit button:
- **Parameter**: Add `onEditWorkout: (() -> Unit)? = null` to `WorkoutHeader`.
- **Button Visuals**: When `onEditWorkout != null`, render an `IconButton` (`32.dp`, `R.drawable.ic_table_edit`, `MaterialTheme.colorScheme.primary` tint) with content description `R.string.edit_workout`.
- **Action Button Placement & Ordering**:
  In ATT-506, the layout convention established that the Edit button is on the **very right** across all headers (`ClusterSummaryHeader` and `TrackOnMapScreen`).
  In `WorkoutHeader`:
  - `actions()` (caller-provided custom actions, e.g. `SwapHoriz`)
  - `more_vert` (Export files dropdown, when `menuEnabled == true`)
  - `ic_table_edit` (Edit workout, when `onEditWorkout != null`)
  Placing `ic_table_edit` on the very right establishes 100% layout consistency across:
  1. `WorkoutClusterHeatmapScreen`: `[EditLocationAlt] [ic_table_edit]` -> edit on very right.
  2. `TrackOnMapScreen`: `[SwapHoriz] [ic_table_edit]` -> edit on very right.
  3. `WorkoutSummary`: `[Export (3-dots)] [ic_table_edit]` -> edit on very right.

### 2.3 `WorkoutSummaryCompact.kt` Alignment
In compact list view ([`WorkoutSummaryCompact.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummaryCompact.kt)):
- Tapping the compact card already invokes `onMapClick(workoutData)`.
- To provide complete functional parity for compact view users, include "Edit" in the long-press context dropdown menu alongside "Delete", or allow direct edit invocation without having to enter the map first.

### 2.4 Navigation State & Lifecycle
- `WorkoutSummariesTabbedFragment` and `WorkoutSummariesListFragment` already maintain `selectedWorkoutForDetails` (routing to `TrackOnMapScreen`) and `selectedWorkoutIdForEdit` (routing to `EditWorkoutScreen`).
- In `WorkoutSummary`, changing body click targets to `onMapClick()` passes the event to `selectedWorkoutForDetails = workoutData.id`, loading aftermath data smoothly.
- Clicking `onEditWorkout` routes to `selectedWorkoutIdForEdit = workoutData.id`, opening `EditWorkoutScreen`.

---

## 3. Requirements & Test Specifications
- **New Requirement `REQ-SET-071`**: *Consistent Workout Click-to-Map Navigation & Dedicated Header Edit Action*
  - Clicking any informational section of a workout card (header, description, details, extrema, map preview) SHALL navigate to the detailed map screen.
  - Clicking the dedicated edit button in the header SHALL open the workout editor.
  - Cluster navigation and export status click targets SHALL be preserved.
- **New Test Specification `TST-SET-060`**: *Workout Card Click-to-Map Navigation & Header Edit Action Verification*
  - Verify header, description, details, and extrema taps open `TrackOnMapScreen`.
  - Verify edit button tap opens `EditWorkoutScreen`.
  - Verify cluster button and export status clicks function independently.
  - Verify action button ordering in `WorkoutHeader` maintains `ic_table_edit` on the very right.

---

## 5. Iteration 2 Feedback Analysis (ATT-860)

Following initial on-device verification, the user identified three interaction defects:
1. *Button Order*: Switch the edit workout button and three-dots context menu in `WorkoutHeader`.
2. *Touch Target Obscuration*: The edit workout button on the right edge was difficult or impossible to tap due to the scrollbar.
3. *Map Screen Edit Navigation Precedence*: Clicking the edit button in `TrackOnMapScreen` did nothing immediately; `EditWorkoutScreen` only appeared after pressing Back.

### 5.1 Root Cause 1: Button Order in `WorkoutHeader.kt`
- In `WorkoutHeader.kt` action row:
  Currently: `actions()` -> `menuEnabled` (3-dots) -> `onEditWorkout` (edit icon).
- Resolution: Swap `onEditWorkout` and `menuEnabled` so the order becomes:
  `actions()` -> `onEditWorkout` (`ic_table_edit`) -> `menuEnabled` (`more_vert` / 3-dots).
  This places the edit icon immediately to the left of the trailing 3-dots export button.

### 5.2 Root Cause 2: Touch Interception by `FastScrollbar.kt`
- In `WorkoutList.kt`, `FastScrollbar` is aligned to `Alignment.CenterEnd` with `width(32.dp)`.
- In `FastScrollbar.kt`, `pointerInput(state) { detectDragGestures { ... } }` is applied to the full-height outer container Box rather than strictly to the draggable thumb Box.
- When `ic_table_edit` was positioned at the very right of `WorkoutHeader` (within 12dp–44dp of the screen edge), touches landed in the 32dp gesture detection overlay of `FastScrollbar`, intercepting the touch events.
- Immediate Resolution (ATT-850):
  Swapping `ic_table_edit` to the left of the 3-dots button moves it inward by 32dp (to 44dp–76dp from the screen edge), placing it completely outside the scrollbar's touch zone and making it reliably clickable.
- Systemic Resolution (New Bug Ticket):
  Logged ticket **ATT-861** (`[Bug] FastScrollbar full-height touch overlay intercepts clicks on right-aligned list item actions`) with Fix Version `V4.9.36` to constrain gesture detection strictly to the draggable thumb.

### 5.3 Root Cause 3: Screen Precedence in `WorkoutSummariesTabbedFragment.kt` & `WorkoutSummariesListFragment.kt`
- In both fragments:
  ```kotlin
  if (selectedWorkoutForDetails != null) {
      TrackOnMapScreen(...)
  } else if (selectedWorkoutIdForEdit != null) {
      EditWorkoutScreen(...)
  }
  ```
- When `TrackOnMapScreen` is active, `selectedWorkoutForDetails` is non-null. Tapping the edit button sets `selectedWorkoutIdForEdit = id`, but the `if` branch condition `selectedWorkoutForDetails != null` still evaluates first, so `TrackOnMapScreen` stays on screen.
- When Back was pressed, `selectedWorkoutForDetails` was set to null, causing the next recomposition to hit the `else if (selectedWorkoutIdForEdit != null)` branch, belatedly showing `EditWorkoutScreen`.
- Resolution:
  Invert the condition order in both fragments:
  ```kotlin
  if (selectedWorkoutIdForEdit != null) {
      EditWorkoutScreen(...)
  } else if (selectedWorkoutForDetails != null) {
      TrackOnMapScreen(...)
  } else {
      // List
  }
  ```
  This immediately opens `EditWorkoutScreen` on top of `TrackOnMapScreen`, returns to `TrackOnMapScreen` when edit is dismissed, and returns to the workout list when Back is pressed from `TrackOnMapScreen`.
