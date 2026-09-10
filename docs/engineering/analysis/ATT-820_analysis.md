# Forensic Root Cause Analysis: Multi-Sport Cluster Candidate Resolution Defect (ATT-820)

## 1. Problem Statement & User Incident
* **Ticket**: [ATT-820](https://rainerblind.atlassian.net/browse/ATT-820) (*The 'Multi-Cluster' algorithm does not work properly for a new workout*)
* **Sub-Task**: [ATT-825](https://rainerblind.atlassian.net/browse/ATT-825) (*[Analysis] The 'Multi-Cluster' algorithm does not work properly for a new workout*)
* **FixVersion**: `V4.9.36`
* **Branch**: `bugfix/ATT-820`
* **User Context & Incident Report**:
  > *"When finnished my bakery commute this morning, it was guessed as running. Hence, the clustering algorithm did not work properly. We changed it recently that it should calculate the claster value for all sport candidates. This was not done."*
  >
  > *"I defined a new sport Type 'Einkaufen' which has a speed from ca 7 km/h to 15 km/h and overlaps the speed of running. Thus, we should get two possible sport types and consequently, two possible workout clusters. The one with the smaller cost should be the chosen one. When tracking, I did not change the sport type. Thus, I had UNKNOWN. Furthermore, I think it might be possible that the root cause is somewhere around the EditWorkoutDialog. Did you have a look at this point?"*

The user has a custom sport type **'Einkaufen'** (`bSportType = BIKE`) configured with an average speed range of **ca. 7 km/h to 15 km/h** (~1.94 m/s to 4.16 m/s), which deliberately overlaps with **'Running'** (`bSportType = RUN`, 7.2 km/h to 14.4 km/h / 2.0 to 4.0 m/s). Tracking was performed with `BSportType.UNKNOWN` (no sensors, no manual pre-selection). Upon finishing the bakery commute, the workout was erroneously classified as **Running** rather than selecting the **Bakery Commute / Einkaufen** cycling cluster which should have won based on minimal similarity cost.

---

## 2. Forensic Analysis & Root Cause Identification

Detailed investigation reveals that the defect spans **both the tracking completion pipeline and the EditWorkoutDialog (`EditWorkoutViewModel` / `EditWorkoutScreen`)**:

### A. Defect in `EditWorkoutDialog` (`EditWorkoutViewModel.kt`)
Upon tracking completion, `TrainingApplication.trackingStopped()` immediately triggers `WorkoutNavigationEvents.triggerEdit(mWorkoutID)`, navigating the user to `EditWorkoutScreen` / `EditWorkoutClusterDialog`. 

1. **Single-Sport Cluster Scoring in `fetchClusterSuggestions`**:
   In [`EditWorkoutViewModel.kt:369-379`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutViewModel.kt#L369-L379):
   ```kotlin
   private fun fetchClusterSuggestions(workout: WorkoutData) {
       ...
       val suggestions = WorkoutClusterEngine.getInstance(getApplication())
           .getClusterScores(start, end, apex, workout.totalDistance, workout.workoutName, workout.bSportType)
       _clusterSuggestions.value = suggestions
   }
   ```
   * While ATT-773 added a multi-sport candidate overload to `WorkoutClusterEngine.getClusterScores(..., candidateSportTypes: Set<BSportType>)`, **`EditWorkoutViewModel` was never updated to use it**!
   * `EditWorkoutViewModel` still calls the single-sport overload with `workout.bSportType` (which holds `BSportType.RUN` if initially guessed as running).
   * Consequently, in `calculateSimilarity`, the `Einkaufen` cluster (`bSportType = BIKE`) is considered mismatched (`cluster.bSportType !in candidateSportTypes`) and receives a heavy **`+5.0` penalty**! Its score jumps above 5.0, artificially inflating its cost above the Running cluster.
   * If `workout.bSportType == BSportType.UNKNOWN`, `candidateSportTypes` becomes empty, adding a `+2.0` penalty to all clusters instead of zero penalty for valid candidate sports.

2. **Sport Dropdown Filtering in `updateSuggestedSportTypeNames`**:
   In [`EditWorkoutViewModel.kt:265-277`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutViewModel.kt#L265-L277):
   ```kotlin
   val suggestedSports = discoveryManager.getSpeedBasedSportTypeNames(data.bSportType, data.avgSpeedMps).toMutableList()
   ```
   * Passing `data.bSportType` (`RUN`) restricts the SQL query in `SportTypeDatabaseManager` to `BASE_SPORT_TYPE = 'RUN'`, completely excluding `Einkaufen` (`BASE_SPORT_TYPE = 'BIKE'`) from the suggested sports dropdown.

---

### B. Defect in Tracking Completion Pipeline (`TrackerService.java` & `DiscoveryManager`)

1. **Arbitrary First-Match Guessing in `resolveSportType`**:
   In `TrackerService.java:803`, before live session finalization, `getSportTypeId()` writes an initial guess into `WorkoutSummaries.TABLE`:
   ```java
   long sportTypeId = getSportTypeId();
   summaryValues.put(WorkoutSummaries.SPORT_ID, sportTypeId);
   summaryValues.put(WorkoutSummaries.B_SPORT, SportTypeDatabaseManager.getInstance(this).getBSportType(sportTypeId).name());
   ```
   In `EquipmentAndSportTypeDiscoveryManager.resolveSportType`:
   ```kotlin
   val candidatesFromAverageSpeed = getSpeedBasedSportTypeIds(bSportType, averageSpeed)
   ...
   candidatesFromAverageSpeed.isNotEmpty() -> candidatesFromAverageSpeed.first()
   ```
   When both `Running` and `Einkaufen` match the session speed, `.first()` unconditionally picks `Running` (due to lower row ID or insertion order), prematurely stamping the workout as `RUN` before route cluster evaluation has evaluated similarity costs.

2. **Low-Speed Fallback Blocking in `SportTypeDatabaseManager`**:
   In `SportTypeDatabaseManager.getSportTypesIdList(bSportType, avgSpd)`:
   ```java
   Cursor cursor = db.query(SportType.TABLE, null,
       SportType.BASE_SPORT_TYPE + "=? AND " + SportType.MIN_AVG_SPEED + "<=? AND " + SportType.MAX_AVG_SPEED + ">?",
       new String[]{bSportType.name(), Double.toString(avgSpd), Double.toString(avgSpd)}, null, null, null);
   if (cursor.getCount() == 0 && bSportType == BSportType.UNKNOWN) {
       cursor = db.query(SportType.TABLE, null,
           SportType.MIN_AVG_SPEED + "<=? AND " + SportType.MAX_AVG_SPEED + ">?",
           new String[]{Double.toString(avgSpd), Double.toString(avgSpd)}, null, null, null);
   }
   ```
   * In the default database seed, `WALK` is defined with `BASE_SPORT_TYPE = 'UNKNOWN'` and speed range `[0.5, 2.0)` m/s.
   * If the commute average speed was around 7 km/h (~1.94 m/s, e.g. stopping at traffic lights or bakery), the first query for `BASE_SPORT_TYPE = 'UNKNOWN'` matches `WALK`.
   * Because `cursor.getCount() == 1` (not 0), the fallback query ignoring base sport type is never reached! `getCandidateBSportTypes` returns `{BSportType.UNKNOWN}`, completely missing both `RUN` and `BIKE`.

---

## 3. Required Remediation Architecture

### 1. In `EditWorkoutViewModel.kt`:
* In `fetchClusterSuggestions(workout: WorkoutData)`:
  * Determine candidate base sports from speed:
    `val candidateSports = discoveryManager.getCandidateBSportTypes(workout.bSportType, workout.avgSpeedMps)`
  * Call `WorkoutClusterEngine.getClusterScores` with `candidateSportTypes = candidateSports`.
  * This guarantees that in `EditWorkoutClusterDialog`, clusters matching either candidate sport (`Einkaufen` / `BIKE` or `Running` / `RUN`) receive **0 sport penalty**, and the one with the smallest geometric cost appears at the top!
* In `updateSuggestedSportTypeNames(data: WorkoutData)`:
  * When `data.bSportType == BSportType.UNKNOWN` or when ambiguous, include all sport types matching the speed profile across all base sports, ensuring `Einkaufen` is present in the dropdown.

### 2. In `TrackerService.java` & `DiscoveryManager`:
* Ensure `TrackerService.finalizeLiveSession()` evaluates candidate clusters for all candidate sports `{RUN, BIKE}`, and if a candidate cluster matches (`similarity < 1.0`), assigns that cluster's sport identity (`Einkaufen`), overriding the arbitrary speed fallback.
* In `SportTypeDatabaseManager.getSportTypesIdList`, when `bSportType == BSportType.UNKNOWN`, ensure all speed-matching sports across all base sports are queried so that overlapping custom sports (like `Einkaufen`) are never blocked by `WALK`.

---

## 4. System Invariants & Preservation Rules
1. **User Pre-Selection Sovereignty (Tier 1)**: Explicit user sport selection before tracking must strictly lock candidates to that sport.
2. **Dedicated Sensor Sovereignty (Tier 2 / REQ-SET-030)**: Active cadence/power sensors must strictly lock candidates to `identity.getBSportType()`.
3. **Lowest Similarity Cost Wins**: Among clusters matching any candidate sport, the cluster with the lowest similarity score must be suggested/selected.
