# Implementation Walkthrough: Speed-Based Multi-Sport Route Clustering & Ambiguity Disambiguation (ATT-773)

## 1. Overview & Objectives
* **Parent Ticket**: [ATT-773](https://rainerblind.atlassian.net/browse/ATT-773) (`[Bug] After Tracking, a new cluster is always created`)
* **Sub-Task**: [ATT-782](https://rainerblind.atlassian.net/browse/ATT-782) (`[Implementation] After Tracking, a new cluster is always created`)
* **Associated Requirements & Tests**:
  * Requirement: `REQ-SET-064` ([docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L106))
  * Test Specification: `TST-SET-051` ([docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L219))
  * FixVersion: `V4.9.36`
  * Branch: `bugfix/ATT-773`

This fix resolves the bug where tracking a recurring route with a smartphone (without paired dedicated cadence/power sensors and without an explicit user sport pre-selection) failed to match existing route clusters. Intermediate urban speeds (~3.0 m/s / ~11 km/h) allow multiple plausible sports (e.g. *Running* vs *Shopping by Bike* / *Einkaufen*). The system now evaluates cluster similarity against all candidate sport types supported by the session speed, applying zero penalty when a cluster matches any candidate sport, while preserving strict hardware sensor and user pre-selection sovereignty.

---

## 2. Changes Implemented

### A. Discovery Layer
* **[EquipmentAndSportTypeDiscoveryManager.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/EquipmentAndSportTypeDiscoveryManager.kt)**:
  * Implemented `getCandidateBSportTypes(bSportType: BSportType, averageSpeed: Double): Set<BSportType>`.
  * Queries `getSpeedBasedSportTypeIds(bSportType, averageSpeed)` and maps all matching sport IDs to their corresponding base sport types via `sportTypeManager.getBSportType(id)`.
  * For speeds supporting both Running and Cycling (e.g. 3.0 m/s), returns `setOf(BSportType.RUN, BSportType.BIKE)`.
  * Added `@VisibleForTesting internal constructor` to support isolated dependency injection in unit tests.

### B. Cluster Evaluation & Scoring Layer
* **[WorkoutClusterEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt)**:
  * **Candidate-Aware Similarity (`calculateSimilarity`)**:
    * Overloaded `calculateSimilarity(..., candidateSportTypes: Set<BSportType>)`.
    * If `cluster.bSportType in candidateSportTypes`: Applies `0.0` sport penalty!
    * If `candidateSportTypes.isEmpty()`: Applies standard penalty (+2.0 if cluster is not UNKNOWN).
    * If `cluster.bSportType !in candidateSportTypes`: Applies standard mismatch penalty (+5.0 for known sport, +2.0 for UNKNOWN).
    * Added backward-compatible single sport overload `calculateSimilarity(..., workoutSportType: BSportType)`.
  * **Candidate-Aware Cluster Suggestion (`suggestCluster`)**:
    * Overloaded `suggestCluster(..., candidateSportTypes: Set<BSportType>)`.
    * Scores candidates against `candidateSportTypes`, filters by `similarity < 1.0`, and selects `minByOrNull { it.second }?.first`.
    * Retained backward-compatible single sport overload `suggestCluster(..., workoutSportType: BSportType)`.
  * **Scoring Overloads**:
    * Overloaded `getClusterScores` and `scoreClusters` with `candidateSportTypes: Set<BSportType>` alongside single-sport wrappers.

### C. Live Tracking Session Finalization & Arbitration
* **[BANALService.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/BANALService.java)**:
  * Exposed `@Nullable public BSportType getUserSelectedBSportType()` on the Binder interface `BANALServiceComm`.
* **[TrackerService.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java)**:
  * In `finalizeLiveSession()`, implemented the 3-tier arbitration hierarchy before cluster lookup:
    1. **Tier 1 (User Pre-Selection Sovereignty)**: If `mBanalService.getUserSelectedBSportType() != null && != BSportType.UNKNOWN`, `candidateSports = Collections.singleton(userSelectedSport)`.
    2. **Tier 2 (Hardware Sensor Sovereignty / REQ-SET-030)**: If `identity.isHighConfidence()`, `candidateSports = Collections.singleton(identity.getBSportType())`.
    3. **Tier 3 (Speed-Based Multi-Sport Candidates)**: If low confidence and no pre-selection, queries `discoveryManager.getCandidateBSportTypes(mBanalService.getBSportType(), getAverageSpeed())`.
  * Passes `candidateSports` to `engine.suggestCluster(...)`.
  * If a cluster matches and hardware confidence is low, `engine.assignClusterToWorkout(...)` atomically links the cluster and adopts the cluster's sport type and equipment.

### D. Automated Unit Tests
* **[SpeedBasedClusterMatchingTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/database/SpeedBasedClusterMatchingTest.kt)**:
  * Created unit test suite covering:
    1. `testCalculateSimilarity_ZeroPenaltyWhenClusterMatchesCandidateSports`: Zero penalty and match when speed candidates are `{RUN, BIKE}` and cluster is `BIKE`.
    2. `testCalculateSimilarity_PenaltyAppliedWhenClusterNotInCandidateSports`: Standard +5.0 penalty when cluster sport is outside candidates.
    3. `testCalculateSimilarity_UserPreSelectionPrecedence`: +5.0 penalty applied to bike cluster when user pre-selected running.
    4. `testCalculateSimilarity_HardwareSensorSovereignty`: Cadence sensor restricts candidate sport strictly to `{BIKE}`, rejecting running cluster.
    5. `testCalculateSimilarity_BackwardCompatibilitySingleSportOverload`: Backward compatibility for existing single sport calls.
    6. `testCandidateBSportTypes_DiscoveryManager`: Correct speed-based candidate set extraction from database manager.

---

## 3. Verification & Evidence

### Automated Unit Tests
* Executed `SpeedBasedClusterMatchingTest`:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.SpeedBasedClusterMatchingTest"
  ```
  **Result**: `BUILD SUCCESSFUL in 20s` (All 6 test cases passed).
* Executed existing cluster regression tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.WorkoutClusterApexTest" --tests "com.atrainingtracker.trainingtracker.database.EditWorkoutClusterTest"
  ```
  **Result**: `BUILD SUCCESSFUL in 10s` (All tests passed, zero regressions).

---

## 4. Adversarial Self-Review ("Red Team" Pass)
1. **Could speed-based multi-sport guessing accidentally override a user who intended to run?**
   * *No*: If the user pre-selected "Running" prior to tracking, Tier 1 (`mBanalService.getUserSelectedBSportType()`) restricts candidates strictly to `{RUN}`, rejecting any cycling clusters.
2. **Could route clusters override dedicated bike sensors?**
   * *No*: If cadence/power sensors are connected, `identity.isHighConfidence()` enforces Tier 2 and strictly preserves hardware identity (`REQ-SET-030`).
3. **What happens if multiple clusters across candidate sports match geometrically?**
   * *Handled*: `minByOrNull { it.second }` chooses the candidate cluster with the lowest overall similarity score (closest geometric fit).
4. **Is binary and source compatibility preserved for other callers of `suggestCluster`?**
   * *Yes*: Single sport overloads with `@JvmOverloads` are preserved identically.
