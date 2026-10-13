# Stage 3: Implementation Plan - ATT-3277: Use favorite route icon for clusters in workout summary and edit views

**Ticket**: [ATT-3277](https://atrainingtracker.atlassian.net/browse/ATT-3277)  
**Sub-task**: [ATT-3329](https://atrainingtracker.atlassian.net/browse/ATT-3329) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: Upcoming (Rule 19: Lösungsversion assigned upon completion)  
**Active Sprint**: `2026-42.1`  
**Requirement Mapping**: `REQ-SET-058`, `REQ-SET-059`  
**Test Mapping**: `TST-SET-064`  
**Branch**: `feature/ATT-3277`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-12  

---

## 1. Problem Description & Background

In the workout summary header ([WorkoutHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt)) and workout editor ([EditWorkoutScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt)), route clusters (*Lieblingsstrecken*) currently render using the location pin icon (`R.drawable.my_locations`).

Directly below the cluster button in [WorkoutHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt#L305-L335), the workout's start and destination endpoints (e.g. *"Start & Ziel: Zu Hause"*) also display using `R.drawable.my_locations`. This creates visual ambiguity between a multi-kilometer route trajectory and a single-point geographic anchor.

In contrast, [AppNavigationDrawer.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt#L170-L171) already differentiates between the two:
* Favorite Routes / Clusters: `R.drawable.ic_favorite_route` (route polyline with heart glyph)
* Favorite Locations / Endpoints: `R.drawable.my_locations` (location pin)

This plan specifies the atomic steps to substitute `R.drawable.ic_favorite_route` in the cluster chips and the selected route input field while preserving point location icons.

---

## 2. Traceability & Requirements Mapping

* **Requirement**:
  * `REQ-SET-058` (*Cluster Visibility & Direct Cluster Navigation in Workout Summary*)
  * `REQ-SET-059` (*Explicit Workout Cluster Management & Assignment in Editor*)
* **Test Mapping**:
  * `TST-SET-064` (*Favorite Route Icon Disambiguation for Clusters in Workout Header and Editor*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Database Alterations**: No SQLite schema or data migrations; clustering hit counts and identities remain unchanged.
2. **Event Isolation**: `onClusterClick` and `onClicked` event boundaries in `WorkoutHeader.kt` remain 100% preserved.
3. **Endpoint Location Icon Invariant**: Point locations (`startLoc`, `endLoc`) in `WorkoutHeader.kt` continue using `R.drawable.my_locations`.
4. **Subtask Direct Completion**: Sub-tasks transition directly to `Erledigt` upon passing Gate audit via transition `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Workout Header (`WorkoutHeader.kt`)
* In the clustered state pill button (line 159), replace `R.drawable.my_locations` with `R.drawable.ic_favorite_route`.
* In the unclustered state row (line 180), replace `R.drawable.my_locations` with `R.drawable.ic_favorite_route`.
* Sizing (`14.dp`), container padding, tint colors (`primary` / `onSurfaceVariant`), and text styling remain identical.
* Location point rows (lines 311, 320, 328) retain `R.drawable.my_locations`.

### Component 2: Edit Workout Screen (`EditWorkoutScreen.kt`)
* In the leading icon of the "Selected Route" `OutlinedTextField` (line 133), replace `R.drawable.my_locations` with `R.drawable.ic_favorite_route`.
* Tint logic (`primary` when clustered, `onSurfaceVariant` when unassigned) remains identical.

### UI Consistency (Rule 23)
* **Reference screen / component**: [AppNavigationDrawer.kt:170](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt#L170) (`DrawerItemConfig(R.id.drawer_my_locations, R.drawable.ic_favorite_route, R.string.my_locations)`).
* **Reused components**: Existing [WorkoutHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt) pill button and [EditWorkoutScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt) `OutlinedTextField`.
* **Theme tokens**: `MaterialTheme.colorScheme.primary`, `MaterialTheme.colorScheme.onSurfaceVariant`, `TTAlpha.Faint`, `TTAlpha.Medium`.
* **New one-off styles & justification**: None. Reusing existing vector asset `R.drawable.ic_favorite_route`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Create Architectural & Visual Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutClusterIconVisualContractTest.kt`
* **Changes**: Create contract tests asserting:
  1. `WorkoutHeader.kt` contains `ic_favorite_route` for both cluster button and unclustered row.
  2. `WorkoutHeader.kt` retains `my_locations` for `startLoc` and `endLoc` point locations.
  3. `EditWorkoutScreen.kt` contains `ic_favorite_route` as leading icon for the selected route field.
  4. Vector asset `ic_favorite_route.xml` exists and is non-empty.

### Step 2: Update WorkoutHeader.kt
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt`
* **Changes**: Replace `R.drawable.my_locations` with `R.drawable.ic_favorite_route` at line 159 and line 180.

### Step 3: Update EditWorkoutScreen.kt
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt`
* **Changes**: Replace `R.drawable.my_locations` with `R.drawable.ic_favorite_route` at line 133.

### Step 4: Execute Targeted Unit Tests
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutClusterIconVisualContractTest" --tests "com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderLocationTest"`

---

## 6. Verification & Rollback Plan

* **Verification**: Run targeted visual contract and location unit tests during Stage 4, followed by full clean-room `./gradlew testDebugUnitTest` in Stage 5.
* **Rollback**: Full branch revert via git without impacting `sprint/2026-42.1` or `develop`.
