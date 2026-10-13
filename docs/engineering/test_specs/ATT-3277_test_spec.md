# Stage 2: Requirement & Test Specification - ATT-3277: Use favorite route icon for clusters in workout summary and edit views

**Ticket**: [ATT-3277](https://atrainingtracker.atlassian.net/browse/ATT-3277)  
**Sub-task**: [ATT-3328](https://atrainingtracker.atlassian.net/browse/ATT-3328) (`[Test-Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: Upcoming (Rule 19: Lösungsversion assigned upon completion)  
**Active Sprint**: `2026-42.1`  
**Requirement Mapping**: `REQ-SET-058` (*Cluster Visibility & Direct Cluster Navigation in Workout Summary*), `REQ-SET-059` (*Explicit Workout Cluster Management & Assignment in Editor*)  
**Test Spec ID**: `TST-SET-064`  
**Branch**: `feature/ATT-3277`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-12  

---

## 1. Requirement Specification (REQ-SET-058 & REQ-SET-059 Revision)

### 1.1 Problem Statement & Rationale
When reviewing or editing a workout:
* In the workout summary header ([WorkoutHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt)), the cluster button chip displays the location pin icon (`R.drawable.my_locations`).
* Immediately beneath the cluster chip, the workout's start and destination endpoints (e.g. *"Start & Ziel: Zu Hause"*) also display using the identical location pin icon (`R.drawable.my_locations`).
* In the workout editor ([EditWorkoutScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt)), the "Selected Route" field also uses `R.drawable.my_locations` as its leading icon.

This creates visual ambiguity between a multi-kilometer route trajectory (*Lieblingsstrecke*) and a single-point geographic anchor (*Lieblingsort*). In the navigation drawer ([AppNavigationDrawer.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt)), the app already distinguishes between the two by using `R.drawable.ic_favorite_route` for favorite route clusters and `R.drawable.my_locations` for favorite locations.

Updating `REQ-SET-058` and `REQ-SET-059` to specify `R.drawable.ic_favorite_route` harmonizes the visual design and eliminates ambiguity.

### 1.2 Functional & Architectural Requirements
1. **Workout Summary Header Clustered State (`WorkoutHeader.kt:159`)**:
   * When `data.clusterId > 0 && !data.clusterName.isNullOrBlank()`, the cluster button chip SHALL display `R.drawable.ic_favorite_route` as its leading icon with size `14.dp` and tint `MaterialTheme.colorScheme.primary`.
2. **Workout Summary Header Unclustered State (`WorkoutHeader.kt:180`)**:
   * When unclustered, the cluster label row SHALL display `R.drawable.ic_favorite_route` with size `14.dp` and tint `MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium)`.
3. **Workout Summary Known Locations Preservation (`WorkoutHeader.kt:311, 320, 328`)**:
   * Start and destination location rows (`startLoc`, `endLoc`) SHALL preserve `R.drawable.my_locations` to maintain visual differentiation between point locations and route trajectories.
4. **Edit Workout Screen Leading Icon (`EditWorkoutScreen.kt:133`)**:
   * The "Selected Route" (`cluster_naming__selected_route_label`) `OutlinedTextField` SHALL render `R.drawable.ic_favorite_route` as its leading icon, tinted with `MaterialTheme.colorScheme.primary` when `currentClusterId > 0` and `MaterialTheme.colorScheme.onSurfaceVariant` otherwise.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Clustered Workout Header)**:
  * *Given* a workout associated with an active cluster (`clusterId > 0`)
  * *When* viewing the workout summary header in `WorkoutSummary` or `TrackOnMapScreen`
  * *Then* the cluster button chip renders `R.drawable.ic_favorite_route` tinted in primary color.
* **Criterion 2 (Unclustered Workout Header)**:
  * *Given* a workout not associated with any cluster (`clusterId <= 0`)
  * *When* viewing the workout summary header
  * *Then* the unclustered label row renders `R.drawable.ic_favorite_route` in neutral variant styling.
* **Criterion 3 (Start & Destination Point Locations Contrast)**:
  * *Given* a workout with both an assigned cluster and known start/end locations (e.g. "Start & Ziel: Zu Hause")
  * *When* viewing the workout header
  * *Then* the cluster chip renders `R.drawable.ic_favorite_route` while the start/destination endpoint rows render `R.drawable.my_locations`, clearly distinguishing the route path from the location points.
* **Criterion 4 (Edit Workout Screen)**:
  * *Given* the athlete opens `EditWorkoutScreen`
  * *When* inspecting the "Selected Route" input field
  * *Then* the leading icon renders `R.drawable.ic_favorite_route`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-SET-058` (*Cluster Visibility & Direct Cluster Navigation in Workout Summary*) and `REQ-SET-059` (*Explicit Workout Cluster Management & Assignment in Editor*).
* **Historical Origin & Commit Trace**: Introduced during Sprint 2026-40.x (ATT-388, ATT-503, ATT-1398).
* **Root Reason for Existing Formulation**: `my_locations` was historically used as a general location icon across the app. Subsequently, `R.drawable.ic_favorite_route` was introduced specifically for route polylines in the drawer (`AppNavigationDrawer.kt:170`), while `R.drawable.my_locations` became the dedicated icon for point locations (`Lieblingsorte` / start and destination locations). Retaining `my_locations` on the cluster button caused two identical icons to appear side-by-side with conflicting meanings.
* **Preservation of Core Invariants**: Sizing (14.dp in `WorkoutHeader`, default leading icon size in `EditWorkoutScreen`), touch targets, interactive cluster navigation (`onClusterClick`), and editor popup dialogs remain 100% unaltered.

### 1.5 System Invariants
* Zero changes to database schemas, clustering calculations, hit counts, or navigation backstacks.
* `onClusterClick` and `onClicked` event isolation in `WorkoutHeader` MUST be 100% preserved.
* Full compatibility across Light, Dark, and AMOLED themes.

---

## 2. Test Specification (TST-SET-064)

### Test Case 1: `WorkoutClusterIconVisualContractTest` (`TST-SET-064.1`)
* **Scope**: Architectural & Visual AST Contract Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutClusterIconVisualContractTest.kt`
* **Preconditions**: Project source tree is accessible.
* **Action**:
  1. Inspect `WorkoutHeader.kt`: assert `ic_favorite_route` is used for cluster button icon (clustered and unclustered states).
  2. Inspect `WorkoutHeader.kt`: assert `my_locations` is preserved for start and end location rows (`IconTextRow`).
  3. Inspect `EditWorkoutScreen.kt`: assert `ic_favorite_route` is used as leading icon in the selected route text field.
  4. Inspect `ic_favorite_route.xml`: verify vector drawable exists and contains valid path data.
* **Expected Result**: All assertions pass.

### Test Case 2: Clean-Room Regression Suite (`TST-SET-064.2`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Class / Method | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-064.1` | Visual Contract | `WorkoutClusterIconVisualContractTest` | `REQ-SET-058`, `REQ-SET-059` | Specified |
| `TST-SET-064.2` | Clean-Room Suite | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
