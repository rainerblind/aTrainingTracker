# Stage 1: Analysis - ATT-4862: [Bug] Lieblingsstrecke wird erstellt, obwohl 'Offen' ausgewählt wurde

**Ticket**: [ATT-4862](https://rainerblind.atlassian.net/browse/ATT-4862)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Active Sprint**: `2026-44.1`  
**Author**: AI Agent 1 (Forensic Analyst)  
**Date**: 2026-11-01  

---

## 1. Problem Statement & Forensic Investigation

In `EditWorkoutScreen`, users can manage route clustering for any workout via `EditWorkoutClusterDialog`. Choosing the "Offen" option unassigns the workout from all clusters, explicitly setting `clusterId = -1L` and `clusterName = null` via `EditWorkoutViewModel.unassignCluster()` and `WorkoutRepository.unassignClusterFromWorkout()`.

However, when the user finishes editing and saves the workout (e.g., after typing a custom workout name), saving the session triggers `WorkoutRepository.saveWorkout()`. Lines 1911–1937 still execute legacy auto-clustering learning logic (dating back to SCRUM-44):
```kotlin
if (userEditedWorkout.startLatLng != null && userEditedWorkout.endLatLng != null && userEditedWorkout.maxDisplacementLatLng != null) {
    val finalName = userEditedWorkout.workoutName.replace(Regex(" #\\d+$"), "").trim()
    if (finalName.isNotEmpty() && finalName != userEditedWorkout.fileBaseName) {
        val learnedId = engine.learnFromWorkout(..., userEditedWorkout.clusterId)
        if (userEditedWorkout.clusterId != learnedId) {
            engine.assignClusterToWorkout(application, workoutId, learnedId)
            ...
        }
    }
}
```
When `userEditedWorkout.clusterId` is `-1L` ("Offen"), `engine.learnFromWorkout()` treats `clusterIdOverride = -1L` as an unassigned track. If no existing cluster matches, it creates and inserts a brand new `WorkoutCluster` with `finalName` into `WorkoutClusters.db`. It then returns the new cluster's ID (`learnedId > 0`). Because `userEditedWorkout.clusterId (-1L) != learnedId`, `WorkoutRepository.saveWorkout()` immediately invokes `engine.assignClusterToWorkout(application, workoutId, learnedId)`, overriding the user's explicit "Offen" selection and creating an unwanted Lieblingsstrecke.

---

## 2. Chesterton's Fence Audit

- **Historical Trace**: `SCRUM-44` (Initial heuristic learning loop where any workout name change automatically created a route cluster), `SCRUM-228` / `SCRUM-254` (Automatic cluster link persistence and DB reloading on save), `ATT-318` / `ATT-666` (Introduction of explicit `EditWorkoutClusterDialog` with "Offen", new route creation, and existing route selection), `REQ-SET-072` / `ATT-1402` (Explicit cluster creation synchronization with `forceIdentity = true`).
- **Original Intent**: Early in development, editing a workout title was the *only* way to name a route. The system heuristically converted custom workout names into reusable route clusters.
- **Why It Must Change**: With the introduction of `EditWorkoutClusterDialog` (ATT-318, REQ-SET-059, REQ-SET-072), cluster management became an explicit, user-directed action. Creating and assigning clusters on implicit text edits breaks the principle of least astonishment, violates the explicit "Offen" choice, and creates junk route clusters.

---

## 3. Scope Bounding & Target Deliverables

1. **`WorkoutRepository.kt` (`saveWorkout`)**:
   - Guard the cluster learning loop so it strictly executes ONLY when `userEditedWorkout.clusterId > 0`.
   - When `userEditedWorkout.clusterId <= 0` (e.g. `-1L` / "Offen"), bypass cluster learning and cluster creation entirely.
   - Remove the automatic `assignClusterToWorkout()` override in `saveWorkout()`: cluster assignments must occur solely through explicit user actions in `EditWorkoutClusterDialog`.
   - When `userEditedWorkout.clusterId > 0`, call `engine.learnFromWorkout()` to update the cluster's running centroid, distance, and sport, but never reassign the cluster ID.
2. **`EditWorkoutClusteringTest.kt` / `WorkoutRepositoryTest`**:
   - Add targeted unit tests verifying that editing the workout name of an unclustered workout (`clusterId = -1L`) preserves `clusterId = -1L`, does NOT create a new `WorkoutCluster`, and does NOT call `assignClusterToWorkout()`.
   - Verify that an already clustered workout (`clusterId > 0`) preserves its cluster ID and updates its centroid/sport.

---

## 4. Invariants

- **Explicit Cluster Management**: Creating a new route cluster or assigning an existing one remains 100% functional via `EditWorkoutClusterDialog` (`createNewCluster` and `applyClusterIdentity`).
- **Zero Schema Changes**: `WorkoutClusters.db` and `WorkoutSummaries.db` schemas remain unchanged.
- **Data Integrity**: Workout name, sport, equipment, and description edits continue to persist to SQLite and Strava export seamlessly.
