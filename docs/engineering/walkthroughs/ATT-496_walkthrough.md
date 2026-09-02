# Walkthrough: ATT-496 & ATT-495 - Idempotent Cluster Creation & Empty Cluster Purging

## 1. Overview
Resolved two related TCX import cluster defects:
1. **Duplicate "var 2" Clusters (ATT-496)**:
   - Added `getClusterByName()` in `WorkoutClusterDatabaseManager.kt`.
   - In `WorkoutClusterEngine.kt` (`learnFromWorkout`), if spatial `suggestCluster()` returns `null`, the engine falls back to checking `getClusterByName(normalizedInputName)`. If a cluster with the exact specified name already exists, it refines the existing cluster instead of creating `"Name var 2"`.
2. **Stale Previews & Zero-Workout Cluster Purging (ATT-495)**:
   - In `WorkoutClusterEngine.kt` (`assignClusterToWorkout`), when a workout is reassigned away from an `oldCluster` and `oldCluster.hitCount` drops to `0`, `oldCluster.previewPaths` is reset to `emptyList()` so no phantom route previews linger in list views.
   - In `WorkoutClusterRepository.kt` (`refreshClusters`), when `realCount == 0`, orphan clusters are automatically deleted from the database if they are NOT linked to an explicit Route (`routePolyline.isNullOrEmpty()` and no route link). Explicit Route clusters are preserved even when containing 0 workouts.

---

## 2. Changes Made

### Database Layer
- **[WorkoutClusterDatabaseManager.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterDatabaseManager.kt)**: Added `getClusterByName(name)` for exact name lookups.
- **[WorkoutClusterEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt)**:
  - Updated `learnFromWorkout()`: Exact name fallback before generating `"var 2"`.
  - Updated `assignClusterToWorkout()`: Reset `previewPaths = emptyList()` when `hitCount` reaches `0`.
- **[WorkoutClusterRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterRepository.kt)**:
  - Updated `refreshClusters()`: Automatically delete unlinked orphan clusters where `realCount == 0`, preserving explicit Route-linked clusters.

---

## 3. Verification Evidence

### Automated Unit Tests
Executed `:app:testDebugUnitTest`:
```text
BUILD SUCCESSFUL
14 passed, 0 skipped, 0 failed
```

### Requirements & Test Status
- **`REQ-SET-061` / `TST-SET-047`**: VERIFIED (Idempotent cluster creation by name).
- **`REQ-SET-062` / `TST-SET-048`**: VERIFIED (Unlinked zero-workout clusters purged; Route-linked clusters preserved).
