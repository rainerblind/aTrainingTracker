# Walkthrough: ATT-1133 / ATT-1137

## TCX Import: Still manual selection of clusters although the similarity score is below 1

### 1. Overview of Changes
This change eliminates the root causes leading to unexpected manual cluster selection prompts during TCX file import when matching route clusters with similarity score $< 1.0$ exist:
1. **Lossless Candidate Evaluation**:
   - In [`WorkoutClusterEngine.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt), replaced lossy SQLite candidate pruning in `suggestCluster()` with comprehensive in-memory candidate evaluation via `scoreClusters(dbManager.getAllClusters(), ...)`.
   - Workouts with start point deviation $> 1.0\times \text{endpointTol}$ (e.g. 250m–400m) whose composite score is $< 1.0$ are now reliably discovered and auto-matched without false negative pruning.
2. **Defensive Bounding Box Hardening**:
   - In [`WorkoutClusterDatabaseManager.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterDatabaseManager.kt), hardened `findCandidates()` with symmetric longitude bounding.
3. **Scoring Parameter Parity**:
   - Updated `ProgressListener.onNewClusterCandidate()` in [`LegacyImportEngine.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt) to forward `workoutName`, `candidateSportTypes`, `minAltPos`, and `maxAltPos`.
   - Extended `ClusterInteraction` in [`BackupRestoreViewModel.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt) to store these parameters.
   - Updated `ClusterNamingDialog` in [`ImportBackupTabsScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt) to forward all parameters to `clusterEngine.scoreClusters()`, ensuring 100% numerical parity between import auto-matching and dialog picker scoring.

---

### 2. Files Modified
- [`app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt)
- [`app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterDatabaseManager.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterDatabaseManager.kt)
- [`app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt)
- [`app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt)
- [`app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt)
- [`app/src/test/java/com/atrainingtracker/trainingtracker/database/TcxImportClusterParityTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/database/TcxImportClusterParityTest.kt)

---

### 3. Verification & Evidence
- Added comprehensive unit tests in [`TcxImportClusterParityTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/database/TcxImportClusterParityTest.kt) verifying:
  - `testSuggestClusterMatchesWhenStartPointDisplacedBeyondEndpointTol`: Verifies candidate discovery when start point is displaced by 350m (> 200m `endpointTol`) with composite similarity < 1.0.
  - `testSuggestClusterParityWithScoreClusters`: Verifies exact matching parity between `suggestCluster` and `scoreClusters`.
  - `testClusterInteractionParameterPreservation`: Verifies that `ClusterInteraction` holds all scoring parameters.
  - `testSuggestClusterReturnsNullWhenScoreExceedsThreshold`: Verifies clean rejection when distance/shape diverges (score >= 1.0).
- Regression testing executed with zero failures:
  - `TcxImportClusterParityTest`
  - `TcxImportPeriodAndClusterIntegrationTest`
  - `WorkoutClusterImportIntegrityTest`
  - `AltitudeAwareClusterMatchingTest`
