# Walkthrough: Workout Cluster Sequential Auto-Naming Counter Ergonomics (ATT-785)

* **Parent Issue**: [ATT-785](https://rainerblind.atlassian.net/browse/ATT-785) ([Verbesserung] First workout of a cluster should not get the #1.)
* **Sub-Task**: [ATT-805](https://rainerblind.atlassian.net/browse/ATT-805) ([Implementation] First workout of a cluster should not get the #1.)
* **Requirement**: `REQ-SET-066` (*Workout Cluster Sequential Auto-Naming Counter Ergonomics*)
* **Test Specification**: `TST-SET-053` (*Workout Cluster Auto-Naming Counter Ergonomics Verification*)
* **Target Version**: `V4.9.36`
* **Branch**: `feature/ATT-785`

---

## 1. Summary of Changes

We implemented a centralized naming policy for route cluster workouts such that the initial activity of a cluster receives only the route's clean base name (e.g., `"Morning Run"`, `"Chiemsee-Runde"`), without any `"#1"` suffix. Subsequent activities on the same route (`count >= 2`) continue to receive sequential numbered suffixes (e.g., `"Morning Run #2"`, `"Morning Run #3"`).

### Detailed Changes:
1. **`WorkoutClusterEngine.kt`**:
   - Implemented `@JvmStatic fun formatClusterWorkoutName(context: Context, clusterName: String, hitCount: Int): String` in the companion object.
   - Updated `assignClusterToWorkout` to route through `formatClusterWorkoutName`.
   - Enhanced `normalizeName` regex to match `#` with and without spaces before digits (`Regex(" (?:# ?|var )\\d+$", ...)`), ensuring seamless equivalence with `stripHitCount`.
2. **`TrackerService.java`**:
   - In live tracking completion when matching high-confidence cluster suggestions, formatted workout name via `WorkoutClusterEngine.formatClusterWorkoutName(...)`.
3. **`EditWorkoutViewModel.kt`**:
   - In `applyClusterIdentity`, updated workout name generation to use `WorkoutClusterEngine.formatClusterWorkoutName(...)`.
4. **`WorkoutClusterAutoNamingTest.kt`**:
   - Implemented comprehensive unit test suite covering initial sessions, subsequent sessions, database updates, and regex normalization invariants.

---

## 2. Verification & Automated Test Results

### 2.1 Direct Unit Tests (`WorkoutClusterAutoNamingTest`)
```bash
./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.database.WorkoutClusterAutoNamingTest
```
* **Result**: **`BUILD SUCCESSFUL`** (5 passed, 0 failed):
  - `testFormatClusterWorkoutName_initialHitCount_omitsSuffix` :heavy_check_mark:
  - `testFormatClusterWorkoutName_subsequentHitCount_appendsSuffix` :heavy_check_mark:
  - `testAssignClusterToWorkout_initialSession_setsBaseNameWithoutSuffix` :heavy_check_mark:
  - `testAssignClusterToWorkout_subsequentSession_appendsSuffix` :heavy_check_mark:
  - `testNormalizationAndHitCountStripping_handlesBaseNameAndNumberedNameIdentically` :heavy_check_mark:

### 2.2 Regression Suite (`EditWorkoutClusterTest`)
```bash
./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.database.EditWorkoutClusterTest
```
* **Result**: **`BUILD SUCCESSFUL`** (All cluster editing, unassign, and create tests passing).

---

## 3. Invariants & Backward Compatibility Verification
- [x] **Clustering Invariants**: 3D spatial similarity calculations and geometric tolerances remain 100% untouched.
- [x] **Subsequent Numbering**: For `hitCount >= 2`, naming preserves the standard `%1$s #%2$d` localized format.
- [x] **Database Schema**: No schema migrations, SQLite alterations, or Room entity modifications.
- [x] **Normalization Stability**: Both `stripHitCount` and `normalizeName` cleanly handle base names and numbered names.
