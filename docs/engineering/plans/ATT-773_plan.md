# Implementation Plan: Speed-Based Multi-Sport Route Clustering & Ambiguity Disambiguation (ATT-773)

* **Parent Ticket**: [ATT-773](https://rainerblind.atlassian.net/browse/ATT-773) ([Bug] After Tracking, a new cluster is always created)
* **Sub-Task**: [ATT-781](https://rainerblind.atlassian.net/browse/ATT-781) ([Impl-Plan] After Tracking, a new cluster is always created)
* **Target Lösungsversion (Fix Version/s)**: `V4.9.36`
* **Requirement**: `REQ-SET-064` (*Speed-Based Multi-Sport Route Clustering & Ambiguity Disambiguation*)
* **Test Specification**: `TST-SET-051` (*Speed-Based Multi-Sport Cluster Discovery & Identity Resolution*)
* **Branch**: `bugfix/ATT-773`

---

## 1. Executive Summary & Root Cause Analysis

### 1.1 Problem Statement
When tracking a workout with phone GPS alone (without paired hardware sensors such as bike cadence/power and without an explicit user pre-selection of sport type), the user completes a known route that already has an established Workout Cluster (e.g. *Shopping by Bike* / *Einkaufen* or *Running*).
Upon completion of live tracking, the system fails to match the existing cluster, leaving the session unclustered (or historically creating redundant duplicate clusters). This occurs even when the route geometry matches the cluster's physical path with extreme precision.

### 1.2 Root Cause Analysis
Forensic analysis of the post-tracking pipeline in `TrackerService.finalizeLiveSession()` revealed the following breakages:
1. **Speed Multi-Sport Discard in `EquipmentAndSportTypeDiscoveryManager.resolveSportType()`**:
   * For speeds around ~3.0 m/s (~11 km/h), speed-based sport lookup `getSpeedBasedSportTypeIds(bSportType, averageSpeed)` correctly identifies multiple plausible sport types: e.g. *Running* (`BSportType.RUN`) and *Shopping by Bike* (`BSportType.BIKE`).
   * However, `resolveSportType()` arbitrarily returns only the first item in the list (`candidatesFromAverageSpeed.first()`), discarding the multi-sport ambiguity.
2. **Cluster Engine Invocation Disconnect in `TrackerService.java:1175`**:
   * `TrackerService` calls `engine.suggestCluster(startPosRaw, endPosRaw, maxDispPos, mDistanceTotal_m, null, mBanalService.getBSportType())`.
   * In phone-only tracking, `mBanalService.getBSportType()` returns `cDeviceManager.getSportType()`, which evaluates to `BSportType.UNKNOWN`.
   * Even if `identity.bSportType` were passed, it would be the single arbitrary guess (e.g. `RUN`).
3. **The Disqualification Trap in `WorkoutClusterEngine.calculateSimilarity()`**:
   * When `TrainingApplication.useSportTypeForClustering()` is enabled:
     * If `workoutSportType == UNKNOWN` and `cluster.bSportType == BIKE`, a **+2.0 penalty** is added to `totalScore`.
     * If `workoutSportType == RUN` and `cluster.bSportType == BIKE`, a **+5.0 penalty** is added to `totalScore`.
   * Because `suggestCluster()` filters candidates with `it.second < 1.0`, the +2.0 or +5.0 penalty unconditionally disqualifies the legitimate matching cluster. `suggestCluster()` returns `null`, and `assignClusterToWorkout()` is never invoked.

---

## 2. Technical Implementation Architecture

We implement candidate-aware multi-sport clustering across three coordinated components:

### 2.1 Component 1: `EquipmentAndSportTypeDiscoveryManager.kt`
Expose speed-based candidate `BSportType` retrieval:
* Add method:
  ```kotlin
  fun getCandidateBSportTypes(
      bSportType: BSportType,
      averageSpeed: Double
  ): Set<BSportType> {
      val speedSportTypeIds = getSpeedBasedSportTypeIds(bSportType, averageSpeed)
      val candidateBSports = speedSportTypeIds.mapNotNull { sportTypeManager.getBSportType(it) }.toSet()
      return when {
          candidateBSports.isNotEmpty() -> candidateBSports
          bSportType != BSportType.UNKNOWN -> setOf(bSportType)
          else -> emptySet()
      }
  }
  ```
* When `bSportType == BSportType.UNKNOWN`, `getSpeedBasedSportTypeIds(BSportType.UNKNOWN, averageSpeed)` queries all sport types matching the speed, returning both `RUN` and `BIKE`.

### 2.2 Component 2: `WorkoutClusterEngine.kt`
Overload similarity calculation and cluster suggestion to accept a set of candidate `BSportType`s:
1. **Candidate-Aware Similarity Calculation**:
   ```kotlin
   fun calculateSimilarity(
       start: LatLng, end: LatLng, apex: LatLng, distance: Double, 
       cluster: WorkoutCluster, 
       workoutName: String? = null,
       candidateSportTypes: Set<BSportType> = emptySet()
   ): Double {
       val s1 = (distanceBetween(start, LatLng(cluster.startLat, cluster.startLng)) / TrainingApplication.getClusterTolEndpoints()) * 0.25
       val s2 = (distanceBetween(end, LatLng(cluster.endLat, cluster.endLng)) / TrainingApplication.getClusterTolEndpoints()) * 0.25
       val s3 = (distanceBetween(apex, LatLng(cluster.maxDispLat, cluster.maxDispLng)) / TrainingApplication.getClusterTolApex()) * 0.25
       val s4 = (Math.abs(distance - cluster.refDistance) / cluster.refDistance / TrainingApplication.getClusterTolDistance()) * 0.25
       var totalScore = s1 + s2 + s3 + s4
       
       // ATT-412 / ATT-773: Tiered Sport Type Awareness with Multi-Sport Candidate Evaluation
       if (TrainingApplication.useSportTypeForClustering()) {
           if (cluster.bSportType in candidateSportTypes) {
               // Perfect match against one of the candidate sports supported by speed/hardware
               // Zero penalty applied!
           } else if (candidateSportTypes.isEmpty()) {
               // Truly unclassified workout (no candidate sports)
               if (cluster.bSportType != BSportType.UNKNOWN) {
                   totalScore += 2.0
               }
           } else {
               // Mismatch: cluster sport is outside the candidate set
               val penalty = if (cluster.bSportType != BSportType.UNKNOWN) 5.0 else 2.0
               totalScore += penalty
           }
       }

       if (workoutName != null) {
           val normalizedWorkout = normalizeName(workoutName)
           val normalizedCluster = normalizeName(cluster.name)
           if (normalizedWorkout.isNotEmpty() && normalizedWorkout == normalizedCluster) totalScore *= 0.5 
       }
       return totalScore
   }
   ```
2. **Backward-Compatible Single-Sport Overload**:
   ```kotlin
   fun calculateSimilarity(
       start: LatLng, end: LatLng, apex: LatLng, distance: Double, 
       cluster: WorkoutCluster, 
       workoutName: String? = null,
       workoutSportType: BSportType = BSportType.UNKNOWN
   ): Double = calculateSimilarity(
       start, end, apex, distance, cluster, workoutName,
       if (workoutSportType != BSportType.UNKNOWN) setOf(workoutSportType) else emptySet()
   )
   ```
3. **Candidate-Aware Cluster Suggestion**:
   ```kotlin
   @JvmOverloads
   fun suggestCluster(
       start: LatLng, end: LatLng, apex: LatLng, distance: Double, 
       workoutName: String? = null, 
       candidateSportTypes: Set<BSportType>
   ): WorkoutCluster? {
       val endpointTol = TrainingApplication.getClusterTolEndpoints().toDouble()
       val latToleranceDegrees = endpointTol / 111000.0
       val distToleranceMeters = distance * TrainingApplication.getClusterTolDistance().toDouble() * 4.0

       val candidates = dbManager.findCandidates(start.latitude, start.longitude, distance, latToleranceDegrees, distToleranceMeters)
       if (DEBUG) Log.d(TAG, "Found ${candidates.size} candidates for shape [start=$start, dist=$distance, name=$workoutName, sports=$candidateSportTypes]")

       return candidates.map { cluster ->
           val score = calculateSimilarity(start, end, apex, distance, cluster, workoutName, candidateSportTypes)
           cluster to score
       }.filter { it.second < 1.0 }
        .minByOrNull { it.second }?.first
   }

   @JvmOverloads
   fun suggestCluster(
       start: LatLng, end: LatLng, apex: LatLng, distance: Double, 
       workoutName: String? = null, 
       workoutSportType: BSportType = BSportType.UNKNOWN
   ): WorkoutCluster? {
       val candidateSports = if (workoutSportType != BSportType.UNKNOWN) setOf(workoutSportType) else emptySet()
       return suggestCluster(start, end, apex, distance, workoutName, candidateSports)
   }
   ```
4. **Candidate-Aware Cluster Scoring Overload**:
   Overload `getClusterScores` similarly to accept `candidateSportTypes: Set<BSportType>`.

### 2.3 Component 3: `TrackerService.java`
Implement the 3-tier candidate arbitration and cluster disambiguation:
```java
        // 4. Identity Determination (SCRUM-200, ATT-773)
        EquipmentAndSportTypeDiscoveryManager discoveryManager = EquipmentAndSportTypeDiscoveryManager.getInstance(this);
        EquipmentAndSportTypeDiscoveryManager.InferredIdentity identity = discoveryManager.resolveIdentity(
            new HashSet<>(mBanalService.getDatabaseIdsOfActiveRemoteDevices()), 
            mBanalService.getBSportType(), 
            getAverageSpeed()
        );

        LatLng startPosRaw = mLiveSession.getStartLatLng();
        LatLng endPosRaw = mLiveSession.getLastLatLng();
        LatLng maxDispPos = summariesManager.getExtremaPosition(mWorkoutID, SensorType.LINE_DISTANCE_m, ExtremaType.MAX);

        if (startPosRaw != null && endPosRaw != null && maxDispPos != null) {
            WorkoutClusterEngine engine = WorkoutClusterEngine.Companion.getInstance(this);
            
            // Determine candidate sport types for clustering (ATT-773)
            Set<BSportType> candidateSports;
            BSportType userSelectedSport = mBanalService.getUserSelectedBSportType();
            if (userSelectedSport != null && userSelectedSport != BSportType.UNKNOWN) {
                // Tier 1: User Pre-Selection Sovereignty (Option A)
                candidateSports = Collections.singleton(userSelectedSport);
            } else if (identity.isHighConfidence()) {
                // Tier 2: Dedicated Hardware Sensor Sovereignty (REQ-SET-030)
                candidateSports = Collections.singleton(identity.getBSportType());
            } else {
                // Tier 3: Speed-Based Multi-Sport Candidate Set
                candidateSports = discoveryManager.getCandidateBSportTypes(mBanalService.getBSportType(), getAverageSpeed());
            }

            WorkoutCluster suggestion = engine.suggestCluster(startPosRaw, endPosRaw, maxDispPos, mDistanceTotal_m, null, candidateSports);
            if (suggestion != null) {
                if (identity.isHighConfidence()) {
                    ContentValues nameValues = new ContentValues();
                    String autoName = getString(R.string.cluster_autoname_format, suggestion.getName(), suggestion.getHitCount() + 1);
                    nameValues.put(WorkoutSummaries.WORKOUT_NAME, autoName);
                    nameValues.put(WorkoutSummaries.CLUSTER_ID, suggestion.getId());
                    summariesManager.getDatabase().update(WorkoutSummaries.TABLE, nameValues, WorkoutSummaries.C_ID + "=?", new String[]{String.valueOf(mWorkoutID)});
                    
                    summariesManager.applyInferredIdentity(mWorkoutID, identity);
                } else {
                    // Low hardware confidence -> Workout Cluster wins everything and resolves ambiguous sport type
                    engine.assignClusterToWorkout(this, mWorkoutID, suggestion.getId());
                }
            } else {
                summariesManager.applyInferredIdentity(mWorkoutID, identity);
            }
        } else {
            summariesManager.applyInferredIdentity(mWorkoutID, identity);
        }
```

---

## 3. Impact Analysis & System Invariants (SWE.1.BP.5)

### 3.1 Mandatory System Invariants ("What MUST NOT Change")
1. **User Pre-Selection Sovereignty (Option A)**:
   * If the user explicitly pre-selected a sport (e.g. *Running*) before tracking (`mBanalService.getUserSelectedBSportType() != null` and `!= BSportType.UNKNOWN`), candidate sports are restricted strictly to `{userSelectedSport}`. A cycling cluster on the same path receives +5.0 penalty and is rejected.
2. **Hardware Sensor Sovereignty (`REQ-SET-030`)**:
   * If high-confidence hardware sensors (cadence sensor, power meter) are active (`identity.isHighConfidence()`), candidate sports are strictly `{hardwareSport}`. A running cluster on the same path is rejected.
3. **Tie-Breakers (Option A)**:
   * If multiple existing clusters across different candidate sports match geometrically, `minByOrNull { it.second }` selects the candidate cluster with the lowest overall similarity score.
4. **Stable Cluster Naming (`REQ-SET-034`)**:
   * The cluster's permanent base name is preserved. The workout adopts the auto-generated numbered name (`<ClusterName> #<hitCount+1>`).
5. **Single-Sport Compatibility**:
   * All existing callers passing a single `workoutSportType` to `suggestCluster()` or `calculateSimilarity()` continue to function with 100% binary and behavioral compatibility.

### 3.2 Impact Analysis & Call Sites
* `EquipmentAndSportTypeDiscoveryManager.kt`: New helper method `getCandidateBSportTypes`. Zero alterations to existing methods.
* `WorkoutClusterEngine.kt`: Overloads added for `Set<BSportType>` across `calculateSimilarity()`, `suggestCluster()`, and `getClusterScores()`. Existing call sites in `EditWorkoutViewModel`, `LegacyImportEngine`, and `WorkoutRepository` retain their current signatures.
* `TrackerService.java`: `finalizeLiveSession()` determines candidate sports according to the 3-tier hierarchy and passes them to `suggestCluster()`.

---

## 4. Test Strategy & Verification (`TST-SET-051`)

### 4.1 Automated Unit Tests
Create unit test suite in `app/src/test/java/com/atrainingtracker/trainingtracker/database/SpeedBasedClusterMatchingTest.kt`:
1. **Multi-Sport Candidate Discovery**:
   * Verify that at average speed 3.0 m/s with `BSportType.UNKNOWN`, `getCandidateBSportTypes()` returns both `BSportType.RUN` and `BSportType.BIKE`.
2. **Zero Penalty for Candidate Matches**:
   * Calculate similarity between a candidate set `{RUN, BIKE}` and a cluster with `BIKE`. Verify sport penalty is `0.0` and `totalScore < 1.0`.
   * Calculate similarity between `{RUN, BIKE}` and a cluster with `OTHER`. Verify penalty is `5.0` and cluster is rejected.
3. **User Pre-Selection Sovereignty**:
   * When user pre-selected `BSportType.RUN`, candidate set is `{RUN}`. Verify bike cluster receives `5.0` penalty and is rejected.
4. **High-Confidence Hardware Sovereignty**:
   * When hardware confidence is true with `BSportType.BIKE`, candidate set is `{BIKE}`. Verify running cluster receives `5.0` penalty and is rejected.
5. **Tie-Breaker Assertion**:
   * When both a `RUN` cluster and a `BIKE` cluster match the route geometry, the cluster with the lower geometric divergence wins.

### 4.2 Test Execution Commands
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.SpeedBasedClusterMatchingTest"
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.WorkoutClusterEngineTest"
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.WorkoutClusterApexTest"
```

---

## 5. Traceability Matrix

| Requirement ID | Component(s) Affected | Verification Test ID | Status |
|:---|:---|:---|:---|
| **REQ-SET-064** | `EquipmentAndSportTypeDiscoveryManager.kt`, `WorkoutClusterEngine.kt`, `TrackerService.java` | **TST-SET-051** | Planned |
