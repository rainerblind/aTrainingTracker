# Implementation Plan: ATT-496 & ATT-495 - Idempotent Cluster Creation & Empty Cluster Purging

## 1. Goal Description
Resolve two related TCX import cluster defects:
1. **Prevent Duplicate "var 2" Clusters (`ATT-496`)**: In `WorkoutClusterEngine.kt` -> `learnFromWorkout()`, when a `userSpecifiedName` is supplied and spatial matching (`suggestCluster()`) returns `null`, check if an existing cluster with the exact normalized name already exists (`getClusterByName()`). If found, refine that existing cluster rather than creating `"Name var 2"`.
2. **Clear Stale Previews & Purge Empty Unlinked Clusters (`ATT-495`)**:
   - In `WorkoutClusterEngine.kt` -> `assignClusterToWorkout()`, when a workout is reassigned away from an `oldCluster` and `oldCluster.hitCount` becomes 0, clear `oldCluster.previewPaths` so empty clusters never display phantom route previews in list views.
   - In `WorkoutClusterRepository.kt` -> `refreshClusters()`, automatically delete/purge empty route clusters (`realCount == 0`) provided they are NOT linked to an explicit Route (`routePolyline == null` and no route link).

---

## 2. Proposed Changes

### Component 1: `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterDatabaseManager.kt`
#### [MODIFY] [WorkoutClusterDatabaseManager.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterDatabaseManager.kt)
- Add `fun getClusterByName(name: String): WorkoutCluster?` to perform fast exact-name lookups on `WorkoutClusterContract.TABLE_NAME`.

### Component 2: `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt`
#### [MODIFY] [WorkoutClusterEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt)
- Update `learnFromWorkout(...)`:
  - When `clusterIdOverride == -1L`, attempt spatial `suggestCluster()`. If `null`, attempt exact name match `dbManager.getClusterByName(stripHitCount(userSpecifiedName))`. If match found, refine existing cluster instead of creating `"Name var 2"`.
- Update `assignClusterToWorkout(...)`:
  - When updating `oldCluster` whose hitCount drops to `0`, set `previewPaths = emptyList()` so no stale polylines remain.

### Component 3: `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterRepository.kt`
#### [MODIFY] [WorkoutClusterRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterRepository.kt)
- Update `refreshClusters(...)`:
  - When self-healing detects `realCount == 0`, if the cluster is NOT linked to an explicit Route (`cluster.routePolyline.isNullOrEmpty()` and no route link in `RoutesDatabaseManager`), delete the unlinked orphan cluster from the database.

---

## 3. Verification Plan

### Automated Tests
- Execute `:app:testDebugUnitTest` to verify cluster database operations and engine calculations.

### Manual Verification Steps (`TST-SET-047` & `TST-SET-048`)
1. **Idempotent Cluster Creation (`TST-SET-047`)**:
   - Import multiple TCX files that specify the same route name (e.g. `"Route A"`).
   - Verify that all workouts are assigned to the single `"Route A"` cluster and no duplicate `"Route A var 2"` is created.
2. **Empty Cluster Purging (`TST-SET-048`)**:
   - Trigger TCX import or cluster refresh.
   - Verify unlinked clusters with 0 workouts are deleted and no longer appear in the UI.
   - Verify clusters linked to an explicit Route are preserved even when they have 0 workouts.
