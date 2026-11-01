# Test Specification - ATT-4862

## 1. Overview & Scope
- **Ticket**: ATT-4862 ([Bug] Lieblingsstrecke wird erstellt, obwohl 'Offen' ausgewählt wurde)
- **Requirement**: `REQ-SET-081` (Strict Preservation of Unclustered Status on Workout Edits & Explicit Cluster Management Gating)
- **Living Test Spec**: `TST-SET-081` in `docs/tests/tst_settings_profiles.md`
- **Target Files**: `WorkoutRepository.kt`, `EditWorkoutViewModel.kt`, `WorkoutClusterEngine.kt`, `EditWorkoutClusteringTest.kt`

## 2. Test Cases (TST-SET-081)

### TST-SET-081.1: Unclustered Workout Name Edit Test
- **Component**: `EditWorkoutClusteringTest.kt` / `WorkoutRepository.kt`
- **Objective**: Verify that editing the workout name of an unclustered workout (`clusterId == -1L`, "Offen") and invoking `saveWorkout()` preserves the unclustered state without creating a new cluster:
  - `learnFromWorkout()` is NOT called when `clusterId <= 0`.
  - `assignClusterToWorkout()` is NOT called.
  - Zero new clusters are inserted into SQLite.
  - `_allWorkouts` and database retain `clusterId == -1L`.

### TST-SET-081.2: Unclustered Selection ("Offen") Persistence Test
- **Component**: `EditWorkoutClusteringTest.kt` / `EditWorkoutViewModel.kt` / `WorkoutRepository.kt`
- **Objective**: Verify that when a clustered workout is unassigned via `unassignCluster()` (selecting "Offen") and subsequently edited with a custom workout name:
  - `unassignCluster()` sets `clusterId = -1L`.
  - Subsequent `saveWorkout()` persists the custom name while strictly retaining `clusterId = -1L`.
  - Legacy SCRUM-44 auto-creation does not reassign or create any cluster.

### TST-SET-081.3: Clustered Workout Centroid & Sport Update Test
- **Component**: `EditWorkoutClusteringTest.kt` / `WorkoutRepository.kt`
- **Objective**: Verify that when an already clustered workout (`clusterId == 10L`) is saved with an edited name or sport:
  - `learnFromWorkout(..., clusterIdOverride = 10L)` is invoked to update the cluster's running centroids and sport.
  - `assignClusterToWorkout()` is NOT called to override cluster assignment.
  - Workout preserves `clusterId == 10L`.

### TST-SET-081.4: Invariant & Explicit Cluster Creation Verification
- **Component**: `EditWorkoutClusterTest.kt`
- **Objective**: Verify that explicit cluster creation and assignment in `EditWorkoutClusterDialog` (`createNewCluster`, `applyClusterIdentity`, `unassignCluster`) continue to work 100% as intended.

### TST-SET-081.5: Clean-Room Regression Execution
- **Command**: `./gradlew testDebugUnitTest --tests "*EditWorkout*" --tests "*ThemeColorCentralizationAuditTest*"`
- **Criteria**: 100% pass rate with zero regressions.
