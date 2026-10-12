# Stage 1 Analysis: ATT-3277 - Use favorite route icon for clusters in workout summary and edit views

**Ticket**: [ATT-3277](https://atrainingtracker.atlassian.net/browse/ATT-3277)  
**Sub-task**: [ATT-3327](https://atrainingtracker.atlassian.net/browse/ATT-3327) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: Upcoming (Rule 19: Lösungsversion assigned upon completion)  
**Active Sprint**: `2026-42.1`  
**Branch**: `feature/ATT-3277`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-12  

---

## 1. Problem Statement & Motivation

When reviewing a recorded workout or editing workout details:
1. In the workout summary header ([WorkoutHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt#L159)), the cluster button chip currently renders the location pin icon (`R.drawable.my_locations`).
2. Directly beneath this chip (lines 311, 320, 328), the workout's start and destination geographic endpoints (e.g. *"Start & Ziel: Zu Hause"*) also render using the identical location pin icon (`R.drawable.my_locations`).
3. In the workout editor ([EditWorkoutScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt#L133)), the *"Selected Route"* (`cluster_naming__selected_route_label`) text field also displays `R.drawable.my_locations` as its leading icon.

This creates visual ambiguity: a multi-kilometer polyline route cluster (*Lieblingsstrecke*) is represented using a single-point map pin icon, colliding directly with actual point locations (*Lieblingsorte*) rendered immediately below it.

In contrast, the application navigation drawer ([AppNavigationDrawer.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt#L170-L171)) already differentiates between the two spatial concepts:
* **Lieblingsstrecken (Clusters / Favorite Routes)**: Uses `R.drawable.ic_favorite_route` (a route polyline with heart glyph).
* **Lieblingsorte (Known Locations)**: Uses `R.drawable.my_locations` (a location pin).

Replacing `R.drawable.my_locations` with `R.drawable.ic_favorite_route` for route clusters restores visual semantics, aligns with the app's established design language, and eliminates UI confusion.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic inspection of the UI components confirms:
1. **[WorkoutHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt)**:
   * Line 159 (Clustered state button): `painter = painterResource(id = R.drawable.my_locations)` inside the interactive pill button (`data.clusterId > 0`).
   * Line 180 (Unclustered state row): `painter = painterResource(id = R.drawable.my_locations)` next to `clusterLabel`.
   * Lines 311, 320, 328 (Location text rows): `iconRes = R.drawable.my_locations` for `startLoc` and `endLoc`.
2. **[EditWorkoutScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt)**:
   * Line 133: `painter = painterResource(id = R.drawable.my_locations)` in the `leadingIcon` parameter of `OutlinedTextField` labeled with `cluster_naming__selected_route_label`.
3. **Drawable Asset Availability**:
   * `app/src/main/res/drawable/ic_favorite_route.xml` is an existing, production-ready 24×24dp vector asset that supports tinting via `LocalContentColor` or Compose `tint` parameters.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Replace `R.drawable.my_locations` with `R.drawable.ic_favorite_route` in [WorkoutHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt) (clustered button chip at line 159 and unclustered row at line 180).
  * Replace `R.drawable.my_locations` with `R.drawable.ic_favorite_route` in [EditWorkoutScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt) (leading icon at line 133).
  * Preserve all tinting, alpha values, click handling (`onClusterClick`), button sizing (height 30.dp, icon size 14.dp), and text styling.
  * Ensure start and destination locations in [WorkoutHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt#L305-L335) retain `R.drawable.my_locations` so point locations remain distinct from route clusters.
  * Update living requirements `REQ-SET-058` and `REQ-SET-059` to document `ic_favorite_route`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying cluster assignment heuristics, clustering calculations, or database schemas.
  * Modifying known locations (*Lieblingsorte*) icons or management dialogs.
  * Changing layout structures or button geometries outside of icon resource substitution.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**:
  * [`REQ-SET-058`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L106): *Cluster Visibility & Direct Cluster Navigation in Workout Summary*.
  * [`REQ-SET-059`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L107): *Explicit Workout Cluster Management & Assignment in Editor*.
* **Historical Origin & Commit Trace**:
  * Introduced during Sprint 2026-40.x (ATT-388, ATT-503, ATT-1398).
* **Root Reason for Existing Formulation**:
  * When `REQ-SET-058` and `REQ-SET-059` were originally drafted, `my_locations` was used as the generic spatial icon across the app. Subsequently, `R.drawable.ic_favorite_route` was introduced specifically for route polylines in the drawer (`AppNavigationDrawer.kt:170`), while `R.drawable.my_locations` became the dedicated icon for point locations (`Lieblingsorte` / start and destination locations). Retaining `my_locations` on the cluster button caused two identical icons to appear side-by-side with conflicting meanings.
* **Preservation of Core Invariants**:
  * Sizing (14.dp in `WorkoutHeader`, default leading icon size in `EditWorkoutScreen`), touch targets, interactive cluster navigation (`onClusterClick`), and editor popup dialogs remain 100% unaltered.

---

## 5. Architectural Strategy & High-Level Solution

1. **[WorkoutHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt)**:
   * Update lines 159 and 180 to use `painterResource(id = R.drawable.ic_favorite_route)`.
   * Retain lines 311, 320, 328 using `R.drawable.my_locations`.
2. **[EditWorkoutScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt)**:
   * Update line 133 to use `painterResource(id = R.drawable.ic_favorite_route)`.
3. **Verification**:
   * Add / update Compose screenshot and UI unit test assertions asserting `ic_favorite_route` resource usage.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing workout list, workout details, and edit workout functionality.
  2. Distinct visual representation between route clusters (`ic_favorite_route`) and start/end locations (`my_locations`).
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**
  * Targeted visual asset substitution with zero database, threading, or business logic alterations.
