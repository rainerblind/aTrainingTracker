# Forensic Root Cause Analysis: Multi-Sport Cluster Candidate Resolution Defect (ATT-820)

## 1. Problem Statement & User Incident
* **Ticket**: [ATT-820](https://rainerblind.atlassian.net/browse/ATT-820) (*[Bug] The 'Multi-Cluster' algorithm does not work properly for a new workout*)
* **Sub-Task**: [ATT-867](https://rainerblind.atlassian.net/browse/ATT-867) (*[Subtask] [Analysis] The 'Multi-Cluster' algorithm does not work properly for a new workout*)
* **FixVersion**: `V4.9.36`
* **Branch**: `bugfix/ATT-820`
* **User Context & Incident Report (Iteration 2)**:
  > *"On todays 'bakery shopping' I had the chance to test ATT-820. Unfortunately, the clustering still does not work as it should. After finishing the workout, it was unassigned."*
  > 
  > *"I clearly see that the 'Run' and 'Einkaufen' sport types are detected based on the speed. When selecting 'Run' as sport type we get a cluster score of 2,991, when selecting 'Einkaufen', we get a score of 0,491. Consequently, this workout should have been matched to the 'Bakery Shopping' cluster with 'Einkaufen' as sport type."*
  >
  > *"I left the sport type at UNKNOWN. When looking at the workout in detail, I noticed that the location of the min altitude was at a point where it should not be and thus resulting in these large numbers."*

---

## 2. Forensic Investigation & Live Database Findings

Direct forensic analysis of the live databases and configuration from the connected test device (Pixel 10, Android 17, build `V4.9.35` / commit `e96f3f4b`):

### A. Live Workout Metadata (`WorkoutSummaries.db`, Workout `_id = 2823`)
* **Time**: `2026-09-11 04:27:06` UTC
* **Distance**: `1933.94 m`
* **Active Duration**: `641 s`
* **Authoritative Average Speed**: `3.017 m/s` (~10.86 km/h)
* **Start Point**: `(48.6566439, 9.0544953)`
* **End Point**: `(48.6567827, 9.0543320)`
* **Max Displacement (Apex)**: `(48.6593954, 9.0626654)`
* **Altitude Extrema (Spurious Min Altitude)**:
  * Min: `(48.6592392, 9.0625845)` — **located at the apex/bakery** instead of the route low point at home.
  * Max: `(48.6593776, 9.0626444)`

### B. Target Cluster Metadata (`RouteClusters.db`, Cluster `_id = 59`)
* **Name**: `"Kurz zum Bäcker"`
* **Probable Sport ID**: `6` (`"Einkaufen "`, `b_sport_type = BIKE`)
* **Pre-workout Reference Distance**: `2257.43 m` (updated to `2203.52 m` after hit 6)
* **Cluster Min Altitude Centroid**: `(48.6566663, 9.0547876)` — located at the home endpoint.
* **Spatial Drift**: 
  * Endpoints / Apex drift: $< 10\text{ m}$.
  * Distance drift: $|1933.94 - 2257.43| = 323.49\text{ m}$ ($14.33\%$).
  * Min Altitude Location drift: $675\text{ m}$ (distance between apex and home).

### C. Device Tolerance Settings (`com.atrainingtracker.debug_preferences.xml`)
Extracted live from device:
* `clusterTolEndpoints = 200.0 m`
* `clusterTolDistance = 0.04856` (~$4.85\%$)
* `clusterTolAltitudePos = 196.36 m`
* `clusterTolApex = 202.44 m`
* `clusterUseAltitudePos = true`

---

## 3. Root Cause Analysis

### 1. The Premature Blind Sport Guess in `TrackerService.java:803`
When tracking with `BSportType.UNKNOWN`, at workout stop in `sampleAndWriteToDb()`:
```java
long sportTypeId = getSportTypeId();
summaryValues.put(WorkoutSummaries.SPORT_ID, sportTypeId);
summaryValues.put(WorkoutSummaries.B_SPORT, SportTypeDatabaseManager.getInstance(this).getBSportType(sportTypeId).name());
```
* `getSportTypeId()` queries speed candidates for `3.017 m/s`, returning `[ID 2: Laufen (RUN), ID 6: Einkaufen (BIKE)]`.
* Lacking route cluster awareness at this step, `resolveSportType()` blindly calls `.first()`, which picks **`ID 2: Laufen` (`RUN`)**.
* The session is prematurely stamped in the database as `Sport = RUN`.

### 2. Live Clustering Rejection in `TrackerService.finalizeLiveSession()`
In `TrackerService.finalizeLiveSession()`:
```java
WorkoutCluster suggestion = engine.suggestCluster(
    startPosRaw, endPosRaw, maxDispPos, mDistanceTotal_m, null, candidateSports, minAltPos, maxAltPos
);
```
* **No Name Bonus at Tracking Stop**:
  `workoutName` is passed as `null`. In `WorkoutClusterEngine.calculateSimilarity`, the $0.5\times$ name match bonus is **inactive**.
* **Geometric Score Exceeds the Hard 1.0 Threshold**:
  Evaluating the workout against Cluster 59 at hitCount 5 with the user's configured device tolerances:
  * $s_1 (\text{start}) = (1.84\text{ m} / 200\text{ m}) \times 0.20 = 0.0092$
  * $s_2 (\text{end}) = (1.91\text{ m} / 200\text{ m}) \times 0.20 = 0.0095$
  * $s_3 (\text{apex}) = (4.75\text{ m} / 202.44\text{ m}) \times 0.20 = 0.0047$
  * $s_4 (\text{distance}) = (14.33\% / 4.856\%) \times 0.20 = \mathbf{0.5902}$
  * $s_{\text{minAlt}} (\text{altitude pos}) = (675.2\text{ m} / 196.36\text{ m}) \times 0.10 = \mathbf{0.3912}$
  * $s_{\text{maxAlt}} = (133.7\text{ m} / 196.36\text{ m}) \times 0.10 = 0.0681$
  * **Total Raw Score**: $0.0092 + 0.0095 + 0.0047 + 0.5902 + 0.3912 + 0.0681 = \mathbf{1.0729}$!
* Because `1.0729 > 1.0`, `suggestCluster`'s filter `.filter { it.second < 1.0 }` **rejected Cluster 59**.
* When no cluster matches under 1.0, `TrackerService` falls back to the initial guess from Step 1, leaving the workout as `clusterId = -1` (unassigned) and `Sport = RUN`.

### 3. Why the User Observed `0,491` and `2,991` in `EditWorkoutScreen`
* In `EditWorkoutScreen`, once the cluster or route name "Kurz zum Bäcker" was selected, line 846 of `WorkoutClusterEngine` activated:
  `if (normalizedWorkout == normalizedCluster) totalScore *= 0.5`
* This applied the $0.5\times$ multiplier:
  * With `Einkaufen` (`BIKE` $\in$ candidateSports, 0 penalty): Score $= 0.982 \times 0.5 = \mathbf{0.491}$.
  * With `Run` (`RUN`, mismatch penalty $+5.0$): Score $= (0.982 + 5.0) \times 0.5 = 5.982 \times 0.5 = \mathbf{2.991}$.
* This explains why the user saw $\le 1.0$ in the editor dialog, but live tracking left it unassigned.

### 4. Structural Code Defect in `TrackerService.java:1190`
In `TrackerService.java:1189-1191`:
```java
} else {
    // Tier 3: Speed-Based Multi-Sport Candidate Set
    candidateSports = discoveryManager.getCandidateBSportTypes(mBanalService.getBSportType(), getAverageSpeed());
}
```
* Passing `mBanalService.getBSportType()` in Tier 3 is fundamentally incorrect whenever the service is not in an explicit `BSportType.UNKNOWN` state. If `mBanalService` returns any concrete sport, it forces `SportTypeDatabaseManager` to filter by `BASE_SPORT_TYPE = ?`, preventing speed-based expansion across other base sports.
* Tier 3 must evaluate candidates across `BSportType.UNKNOWN`, exactly as `EditWorkoutViewModel.kt:396` does.

---

## 4. Remediation Architecture

1. **`TrackerService.java:1190`**:
   * Explicitly pass `BSportType.UNKNOWN` for Tier 3 speed candidate discovery:
     `candidateSports = discoveryManager.getCandidateBSportTypes(BSportType.UNKNOWN, getAverageSpeed());`
2. **`BANALService.java` Clean State Management**:
   * Ensure user pre-selection state is cleanly isolated per session so zero-touch workouts always default to unconstrained auto-discovery.
3. **Cluster Engine Tolerance & Extrema Resilience**:
   * Review altitude extrema coordinate drift weighting when GPS altitude readings produce noisy or inverted min/max coordinate locations.
4. **Automated Unit & Regression Tests**:
   * Test multi-sport speed candidate resolution with ambiguous speeds ($3.017\text{ m/s}$).
   * Verify mathematical score calculation and threshold handling.
