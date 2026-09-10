# Forensic Root Cause Analysis: Multi-Sport Cluster Candidate Resolution Defect (ATT-820)

## 1. Problem Statement & User Incident
* **Ticket**: [ATT-820](https://rainerblind.atlassian.net/browse/ATT-820) (*The 'Multi-Cluster' algorithm does not work properly for a new workout*)
* **Sub-Task**: [ATT-825](https://rainerblind.atlassian.net/browse/ATT-825) (*[Analysis] The 'Multi-Cluster' algorithm does not work properly for a new workout*)
* **FixVersion**: `V4.9.36`
* **Branch**: `bugfix/ATT-820`
* **User Incident Report**:
  > *"When finnished my bakery commute this morning, it was guessed as running. Hence, the clustering algorithm did not work properly. We changed it recently that it should calculate the claster value for all sport candidates. This was not done."*

During a routine cycling commute to the bakery tracked without paired dedicated cadence/power sensors and without manual sport pre-selection, the workout was automatically classified as **Running** instead of matching the existing **Bakery Commute** cycling cluster.

---

## 2. Forensic Code Path & Call Site Analysis

Tracing the workout finalization sequence in `TrackerService.java:1162-1215`:

```
TrackerService.finalizeLiveSession()
  │
  ├── 1. discoveryManager.resolveIdentity(activeDeviceIds, mBanalService.getBSportType(), getAverageSpeed())
  │        └── Since activeDeviceIds is empty, identity.isHighConfidence() == false
  │        └── resolveSportType() defaults to speed lookup -> returns SportTypeId for RUN (~3.0 m/s)
  │
  ├── 2. Candidate Sport Determination (Tier 3):
  │        └── candidateSports = discoveryManager.getCandidateBSportTypes(mBanalService.getBSportType(), getAverageSpeed())
  │        └── Calls sportTypeManager.getSportTypesIdList(bSportType, avgSpd)
  │
  ├── 3. Cluster Lookup:
  │        └── engine.suggestCluster(start, end, apex, dist, ..., candidateSports)
  │        └── dbManager.findCandidates(...) finds spatial match: "Bakery Commute" (bSportType = BIKE)
  │        └── calculateSimilarity(..., candidateSports):
  │              Because candidateSports == {RUN} and DOES NOT contain BIKE:
  │              A mismatch penalty of +5.0 is applied!
  │              Similarity score jumps to ~5.2 (> 1.0 threshold).
  │        └── "Bakery Commute" cluster is REJECTED by filter { it.second < 1.0 }!
  │        └── suggestion == null!
  │
  └── 4. Fallback Execution:
           └── summariesManager.applyInferredIdentity(mWorkoutID, identity);
           └── Workout permanently stamped as RUN!
```

---

## 3. Dissected Root Cause Flaws

### Flaw 1: Disjoint Speed Intervals in `SportType` SQLite Database Table
In `SportTypeDatabaseManager.java:720-785`, default database records define strictly non-overlapping, contiguous speed intervals:
* `OTHER`: `[0.0, 0.5)` m/s
* `WALK`: `[0.5, 2.0)` m/s
* `RUN`: `[2.0, 4.0)` m/s (~7.2 – 14.4 km/h)
* `MTB`: `[4.0, 5.5)` m/s (~14.4 – 19.8 km/h)
* `BIKE`: `[5.5, 12.0)` m/s (~19.8 – 43.2 km/h)

The SQL query executed by `SportTypeDatabaseManager.getSportTypesIdList(bSportType, avgSpd)` is:
```sql
SELECT * FROM SportType WHERE
  MIN_AVG_SPEED <= ? AND MAX_AVG_SPEED > ?
```
Because the intervals are mutually exclusive, **for any given average speed, SQLite matches AT MOST ONE single row!**
For an urban cycling commute at ~3.0 m/s (10.8 km/h), SQLite matches exclusively `RUN`. `BIKE` requires `MIN_AVG_SPEED >= 5.5` m/s. Therefore, `BIKE` is **NEVER** returned by SQLite for any speed below 19.8 km/h!

### Flaw 2: Mock Masking in ATT-773 Unit Tests
In `SpeedBasedClusterMatchingTest.kt:205-207`, the unit test introduced in ATT-773 mocked `SportTypeDatabaseManager`:
```kotlin
every { sportTypeManager.getSportTypesIdList(BSportType.UNKNOWN, 3.0) } returns listOf(10L, 20L)
every { sportTypeManager.getBSportType(10L) } returns BSportType.RUN
every { sportTypeManager.getBSportType(20L) } returns BSportType.BIKE
```
The test artificially forced the database manager to return IDs for both `RUN` and `BIKE`. In the real Android runtime, `getSportTypesIdList` queries the SQLite table where returning both `RUN` and `BIKE` was physically impossible.

### Flaw 3: Base Sport Type Filtering Blinds Fallback
In `SportTypeDatabaseManager.getSportTypesIdList`:
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
* If `mBanalService.getBSportType()` is NOT `UNKNOWN` (e.g. `RUN` from default state or previous activity), the query is locked to `BASE_SPORT_TYPE = 'RUN'`, completely blinding the query to any other sport type.
* Even if `bSportType == UNKNOWN`, for low speeds `[0.5, 2.0)` m/s (e.g. 1.8 m/s due to traffic lights or bakery stops), `WALK` has `BASE_SPORT_TYPE = 'UNKNOWN'` in the database. Thus `cursor.getCount() == 1`, and the fallback query is never invoked.

### Flaw 4: Penalization of Spatially Valid Clusters in Tier 3
In `WorkoutClusterEngine.calculateSimilarity`, if a cluster's `bSportType` is not present in `candidateSportTypes`, a heavy `+5.0` penalty is added to the similarity score. Because `candidateSports` contained only `{RUN}`, the valid spatial candidate "Bakery Commute" (`bSportType == BIKE`) received a +5.0 penalty, pushing its score above the `1.0` acceptance threshold.

---

## 4. System Invariants & Preservation Rules

1. **User Pre-Selection Sovereignty (Tier 1)**:
   * If the user explicitly pre-selects a sport before tracking (`userSelectedSport != null && userSelectedSport != BSportType.UNKNOWN`), `candidateSports` MUST strictly remain `Collections.singleton(userSelectedSport)`.
2. **Dedicated Hardware Sensor Sovereignty (Tier 2 / REQ-SET-030)**:
   * If dedicated hardware sensors (cadence sensor, power meter) are connected (`identity.isHighConfidence()`), `candidateSports` MUST strictly remain `Collections.singleton(identity.getBSportType())`. Route clusters MUST NEVER override hardware-determined sports.
3. **Geometric Cluster Validity & Thresholds**:
   * Spatial tolerance formulas (`tolEndpoints`, `tolApex`, `tolDistance`, `tolAlt`) and the `< 1.0` similarity threshold MUST remain unchanged.
4. **Preserve Single-Sport Heuristic for BANALService**:
   * Legacy callers relying on a single deterministic sport guess (e.g., `BANALService.getSportTypeId(avgSpd)`) must not be disrupted.

---

## 5. Architectural Solution for Stages 2 & 3

### Solution Architecture: Overlapping Multi-Sport Candidate Determination
For Tier 3 (speed-based candidate discovery without sensors and without user pre-selection):

1. **Multi-Sport Speed Plausibility Bands**:
   * In `EquipmentAndSportTypeDiscoveryManager.getCandidateBSportTypes(bSportType: BSportType, averageSpeed: Double)`:
     * When `bSportType == BSportType.UNKNOWN` (or when resolving candidates for unconstrained clustering):
       * `BIKE` is plausible for `averageSpeed >= 1.5` m/s (from slow 5.4 km/h urban commuting up to fast road cycling).
       * `RUN` is plausible for `1.5 <= averageSpeed <= 6.0` m/s (from 5.4 km/h slow jogging to 21.6 km/h sprinting).
       * `WALK` / `UNKNOWN` is plausible for `averageSpeed <= 2.5` m/s.
     * Intermediate urban speeds (e.g., ~1.5 m/s to 6.0 m/s) MUST return a multi-sport candidate set containing at least `setOf(BSportType.RUN, BSportType.BIKE)`.

2. **Cluster-Aware Sport Plausibility Validation**:
   * When `WorkoutClusterEngine.suggestCluster` evaluates spatial candidate clusters retrieved from `findCandidates`:
     * If a cluster geometrically matches the route and its `bSportType` is plausible for the workout's speed profile, the cluster MUST be evaluated with zero sport penalty (`0.0`).
     * This allows the route cluster itself to resolve the ambiguity between running and urban cycling when hardware sensors are absent.

---

## 6. Impact Analysis & Risk Assessment
* **Android System & Performance**: Zero impact on battery, WakeLocks, or background execution.
* **Component Interfaces**: No breaking changes to public APIs. `getCandidateBSportTypes` signature remains stable.
* **Data Integrity & Backward Compatibility**: No SQLite database schema migrations required. Existing workout files and clusters are preserved.
