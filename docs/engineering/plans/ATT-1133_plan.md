# Implementation Plan: ATT-1133 / ATT-1136

## TCX Import: Still manual selection of clusters although the similarity score is below 1

### 1. Executive Summary & Problem Context
During TCX file import (single or bulk), historical workouts frequently trigger the manual cluster naming/selection dialog (`ClusterNamingDialog`) despite an existing route cluster having a similarity score below 1.0 (e.g. 0.35–0.70). Opening the cluster picker reveals the match immediately with a low score, causing user friction and redundant manual steps.

### 2. Root Cause Analysis
1. **Lossy SQLite Candidate Pruning in `suggestCluster()`**:
   In `WorkoutClusterEngine.suggestCluster()`, candidates were fetched via `dbManager.findCandidates(..., latToleranceDegrees, distToleranceMeters)`.
   `latToleranceDegrees` was calculated as `endpointTol / 111000.0` (factor 1.0x, ~200m).
   In contrast, `calculateSimilarity()` weights start endpoint deviation at only 0.20–0.25 (`s1 = (dist / tolEndpoints) * 0.25`). If a workout started 250m–400m away, its composite score was well below 1.0, but SQLite's `WHERE start_lat BETWEEN ...` query pruned it completely from candidate evaluation.
2. **Asymmetric Coordinate Handling**:
   `findCandidates()` filtered solely on `START_LAT`, completely ignoring `START_LNG`.
3. **Scoring Parameter Asymmetry**:
   `LegacyImportEngine` passed `minAltPos`, `maxAltPos`, `workoutName`, and inferred `candidateSportTypes` to `suggestCluster()`, but `ClusterInteraction` in `BackupRestoreViewModel` and `ClusterNamingDialog` in `ImportBackupTabsScreen` omitted these parameters, causing divergent scores.

---

### 3. Proposed Software Modifications

#### Component 1: `WorkoutClusterEngine.kt`
- Modify `suggestCluster()`:
  - Eliminate premature and lossy SQLite bounding box filtering.
  - Query all clusters via `dbManager.getAllClusters()`.
  - Delegate directly to `scoreClusters(allClusters, start, end, apex, distance, workoutName, candidateSportTypes, minAltPos, maxAltPos)`.
  - Return the best matching cluster (`minByOrNull { it.second }`) where `score < 1.0`.
  - Guarantees 100% mathematical parity with `scoreClusters()` and eliminates false negative pruning.

#### Component 2: `LegacyImportEngine.kt` & `BackupRestoreViewModel.kt`
- In `LegacyImportEngine.kt`:
  - Update `ProgressListener.onNewClusterCandidate()` signature to accept `workoutName: String? = null`, `candidateSportTypes: Set<BSportType> = emptySet()`, `minAltPos: LatLng? = null`, `maxAltPos: LatLng? = null`.
  - Pass these arguments from `LegacyImportEngine` when calling `listener?.onNewClusterCandidate()`.
- In `BackupRestoreViewModel.kt`:
  - Extend `ClusterInteraction` data class with `workoutName: String? = null`, `candidateSportTypes: Set<BSportType> = emptySet()`, `minAltPos: LatLng? = null`, `maxAltPos: LatLng? = null`.
  - Forward these arguments in `onNewClusterCandidate()` to the `ClusterInteraction` queue.

#### Component 3: `ImportBackupTabsScreen.kt`
- In `ClusterNamingDialog`:
  - In `showSelectionDialog`, update `clusterEngine.scoreClusters(...)` invocation to pass:
    ```kotlin
    val candidatesWithScores = remember(existingClusters, state) {
        clusterEngine.scoreClusters(
            existingClusters,
            state.start,
            state.end,
            state.apex,
            state.distance,
            workoutName = state.workoutName,
            candidateSportTypes = if (state.candidateSportTypes.isNotEmpty()) state.candidateSportTypes else if (state.bSportType != BSportType.UNKNOWN) setOf(state.bSportType) else emptySet(),
            minAltPos = state.minAltPos,
            maxAltPos = state.maxAltPos
        )
    }
    ```
  - Ensures that the candidate picker displays scores calculated with the exact same parameters as `suggestCluster()`.

#### Component 4: `WorkoutClusterDatabaseManager.kt`
- Retain and harden `findCandidates()` for backwards compatibility, ensuring generous bounding (at least 4.0x endpoint tolerance and matching longitude bounds) if called directly.

---

### 4. Invariant Protection & Traceability Check
- **REQ-MIG-025 (Leave Unclustered Sovereignty)**: Preserved. If `suggestCluster()` returns `null` and the user dismisses or chooses "Leave Unclustered", `clusterId = -1L` is saved without auto-clustering.
- **REQ-SET-064 / REQ-SET-065 (Multi-Sport & 3D Altitude Clustering)**: Preserved. Both 2D and 3D altitude similarity scoring formulas remain unchanged.
- **Live Workout Tracking**: `TrackerService` live completion calls `suggestCluster()`, which will now benefit from lossless candidate evaluation without regressions.

---

### 5. Verification & Test Plan (TST-MIG-026)
- Create automated unit test class `com.atrainingtracker.trainingtracker.database.TcxImportClusterParityTest`:
  1. `testSuggestClusterMatchesWhenStartPointDisplacedBeyondEndpointTol`:
     - Cluster start: (48.0, 11.0), end: (48.0, 11.0), apex: (48.05, 11.0), dist: 10,000m.
     - Workout start: displaced by 350m north (> 200m `endpointTol`), same end, apex, dist.
     - Verify `suggestCluster()` returns the cluster (previously pruned by `findCandidates`).
  2. `testSuggestClusterParityWithScoreClusters`:
     - Verify that for any workout shape, `suggestCluster(..)` returns the exact first cluster from `scoreClusters(allClusters, ..).filter { it.second < 1.0 }`.
  3. `testClusterInteractionParameterPreservation`:
     - Verify `ClusterInteraction` correctly stores and passes `workoutName`, `candidateSportTypes`, `minAltPos`, and `maxAltPos`.
  4. `testLegacyImportEngineAutoAssignsWithoutDialogWhenSimilarityBelow1`:
     - Mock/simulate import with a workout having similarity score 0.55 to an existing cluster.
     - Verify workout is auto-assigned to the cluster and `onNewClusterCandidate` is NEVER called.
- Run full regression: `./gradlew testDebugUnitTest`.
