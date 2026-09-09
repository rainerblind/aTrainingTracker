# Implementation Plan: Comprehensive Workout Cluster Performance & Volume Statistics (ATT-221)

* **Parent Ticket**: [ATT-221](https://rainerblind.atlassian.net/browse/ATT-221) ([Verbesserung] More WorkoutCluster stats)
* **Sub-Task**: [ATT-815](https://rainerblind.atlassian.net/browse/ATT-815) ([Impl-Plan] More WorkoutCluster stats)
* **Requirement**: `REQ-SET-068` (*Comprehensive Workout Cluster Performance & Volume Statistics*)
* **Test Specification**: `TST-SET-055` (*Workout Cluster Performance & Volume Statistics Verification*)
* **Target Version**: `V4.9.36`
* **Branch**: `feature/ATT-221`
* **Status**: Implemented & Verified
* **Author**: AI Agent 1 (Senior Android Architect)
* **Date**: 2026-09-09

---

## 1. Overview & Problem Statement

Currently, the Workout Clusters ("Lieblingsstrecken" / Favorite Routes) interface provides basic metadata: reference distance, primary sport type, linked equipment, and the total lifetime hit count (`cluster.hitCount`). While users can see how often they have run or ridden a route, they lack deeper insights into their historical performance and cumulative volume on that route, such as:
1. **Personal Record / Best Time**: The fastest completed execution on the route.
2. **Average Duration**: Mean time spent completing the route.
3. **Pace / Speed**: Sport-adaptive average velocity (min/km for running/walking, km/h or mph for cycling/other).
4. **Cumulative Volume**: Total distance covered, total elevation climbed, and total moving time across all recorded sessions.
5. **Recency**: Timestamp and formatted relative recency of the last activity on the cluster (e.g., "06.09.2026 (3 days ago)").

### Architectural Solution:
* **Single-Pass Database Aggregation**: Implement an optimized SQL aggregation method in `WorkoutSummariesDatabaseManager` that calculates count, last hit datetime, average speed, average duration, minimum duration (best time for finished sessions), total distance, total ascent, and total active time grouped by `clusterId`.
* **Domain Model**: Create an immutable data class `WorkoutClusterStats` encapsulating these aggregated values.
* **Repository & ViewModel Reactive Distribution**: Cache and expose cluster statistics via `WorkoutClusterRepository.clusterStats: StateFlow<Map<Long, WorkoutClusterStats>>` and `WorkoutClustersViewModel.clusterStats`.
* **Presentation Architecture**:
  * **Cluster Detail Screen (`WorkoutClusterSummaryHeader`)**: Render a three-tier dashboard card layout displaying:
    * *Tier 1 (Performance)*: Best Time (highlighted with gold trophy accent), Average Time, and Sport-Adaptive Velocity (Pace or Speed).
    * *Tier 2 (Cumulative Volume)*: Total Distance, Total Elevation Gain, and Total Active Time.
    * *Tier 3 (Activity & Recency)*: Tappable Recordings count button (routes to filtered session list) and Last Activity date with relative recency.
  * **Cluster List Card (`ClusterItem` / `WorkoutClusterMetadataBlock`)**: Enrich the metadata block alongside the 100x100 preview map with Best Time (trophy), Total Distance, and Last Hit date without exceeding layout bounds.

---

## 2. Proposed Architecture & System Decomposition

```
[ WorkoutSummaries.db ]
        │
        ▼ (Single-Pass SQL GROUP BY clusterId)
[ WorkoutSummariesDatabaseManager.getWorkoutClusterStatsForAllClusters() ]
        │
        ▼ (Map<Long, WorkoutClusterStats>)
[ WorkoutClusterRepository.clusterStats: StateFlow<Map<Long, WorkoutClusterStats>> ]
        │
        ▼ (StateFlow Distribution)
[ WorkoutClustersViewModel.clusterStats ]
        │
   ┌────┴──────────────────────────────┐
   ▼                                   ▼
[ ClusterItem / MetadataBlock ]    [ WorkoutClusterSummaryHeader ]
 (Compact list card metadata:       (3-tier detail dashboard:
  Best Time 🏆, Last Hit, Total)      Tier 1: PR 🏆, Avg Time, Pace/Speed
                                      Tier 2: Total Dist, Ascent, Time
                                      Tier 3: Recordings, Last Hit Recency)
```

### 2.1 Domain Data Model: `WorkoutClusterStats`
```kotlin
data class WorkoutClusterStats(
    val clusterId: Long,
    val workoutCount: Int = 0,
    val lastHitTimestampS: Long? = null,
    val lastHitDateStr: String? = null,
    val avgSpeedMps: Double = 0.0,
    val avgDurationSec: Long = 0L,
    val bestDurationSec: Long? = null,
    val totalDistanceMeters: Double = 0.0,
    val totalAscentMeters: Long = 0L,
    val totalActiveTimeSec: Long = 0L
)
```

### 2.2 Relative Time Formatter Utility
A clean utility function `formatRelativeRecency(context: Context, timestampS: Long, nowS: Long = System.currentTimeMillis() / 1000L): String`:
* Calculates calendar days difference.
* `days == 0` -> Formatted date + `(Today)` (`R.string.cluster_stats_today`).
* `days == 1` -> Formatted date + `(Yesterday)` (`R.string.cluster_stats_yesterday`).
* `days in 2..6` -> Formatted date + `(X days ago)` (`R.string.cluster_stats_days_ago`).
* `days >= 7` -> Formatted date + `(X weeks ago)` (`R.string.cluster_stats_weeks_ago`).

---

## 3. Implementation Steps (Numbered Sequence)

### Step 1: SQL Aggregation Query in `WorkoutSummariesDatabaseManager.java`
* Add `public Map<Long, WorkoutClusterStats> getWorkoutClusterStatsForAllClusters()`:
  ```sql
  SELECT 
      clusterId,
      COUNT(*) AS workoutCount,
      MAX(strftime('%s', timeStart)) AS lastHitEpochS,
      MAX(timeStart) AS lastHitDateStr,
      AVG(speedAverage_mps) AS avgSpeedMps,
      AVG(timeActive_s) AS avgDurationSec,
      MIN(CASE WHEN finished = 1 AND timeActive_s > 0 THEN timeActive_s ELSE NULL END) AS bestDurationSec,
      SUM(distanceTotal_m) AS totalDistanceMeters,
      SUM(ascending) AS totalAscentMeters,
      SUM(timeActive_s) AS totalActiveTimeSec
  FROM WorkoutSummaries
  WHERE clusterId > 0
  GROUP BY clusterId
  ```
* Also add single-cluster overload `public WorkoutClusterStats getWorkoutClusterStats(long clusterId)` for targeted refreshes.

### Step 2: Repository Integration in `WorkoutClusterRepository.kt`
* Define `private val _clusterStats = MutableStateFlow<Map<Long, WorkoutClusterStats>>(emptyMap())`.
* Expose `val clusterStats: StateFlow<Map<Long, WorkoutClusterStats>> = _clusterStats.asStateFlow()`.
* Populate `_clusterStats` during `refreshClusters()`, cluster recalculation, and after workout cluster assignments/removals.

### Step 3: ViewModel Exposure in `WorkoutClustersViewModel.kt`
* Expose `val clusterStats: StateFlow<Map<Long, WorkoutClusterStats>> = repository.clusterStats`.
* Expose helper `fun getClusterStats(clusterId: Long): WorkoutClusterStats? = clusterStats.value[clusterId]`.

### Step 4: UI Presentation in `WorkoutClusterSummaryHeader.kt` / `WorkoutClusterHeatmapScreen.kt`
* In `WorkoutClusterSummaryHeader`:
  * Observe stats for `cluster.id`.
  * Render **Tier 1: Performance Card**:
    * Best Time (highlighted with gold trophy icon `Icons.Filled.EmojiEvents` or `control_start`/medal, formatted using `TimeFormatter`).
    * Average Time (formatted using `TimeFormatter`).
    * Average Pace / Speed: Inspect `cluster.probableSportId` via `SportTypeDatabaseManager.getBSportType(sportId)`. If `BSportType.RUN`, render Pace (`min/km` via `PaceFormatter.format_with_units(1.0 / avgSpeedMps)`); otherwise Speed (`km/h` via `SpeedFormatter.format_with_units(avgSpeedMps)`).
  * Render **Tier 2: Cumulative Volume Card**:
    * Total Distance (`DistanceFormatter.format_with_units(stats.totalDistanceMeters)`).
    * Total Ascent (`AltitudeFormatter.format_with_units(stats.totalAscentMeters)`).
    * Total Moving Time (`TimeFormatter.format_with_units(stats.totalActiveTimeSec)`).
  * Render **Tier 3: Activity & Recency Card**:
    * Interactive recordings count button (navigates to member session list).
    * Last Activity date + relative recency label (e.g. `06.09.2026 (3 days ago)`).

### Step 5: Compact List Card Enrichment in `WorkoutClusterComponents.kt`
* In `WorkoutClusterMetadataBlock`:
  * If stats are available for the cluster:
    * Display Best Time (`🏆 35:12`) if `stats.bestDurationSec != null`.
    * Display Total Distance (`stats.totalDistanceMeters`) and Last Hit date alongside recordings count.
  * Preserve the 100x100 preview map alignment without vertical overflow.

### Step 6: Localization & Strings Parity
* Add string resources in `values/strings.xml` and all 8 localized `values-*/strings.xml` files (DE, ES, FR, IT, JA, NL, PL, PT):
  * `cluster_stats_best_time`: "Best Time" / "Bestzeit"
  * `cluster_stats_avg_time`: "Average Time" / "Durchschnittszeit"
  * `cluster_stats_avg_speed`: "Average Speed" / "Durchschnittsgeschwindigkeit"
  * `cluster_stats_avg_pace`: "Average Pace" / "Durchschnittstempo"
  * `cluster_stats_total_distance`: "Total Distance" / "Gesamtdistanz"
  * `cluster_stats_total_elevation`: "Total Ascent" / "Gesamter Anstieg"
  * `cluster_stats_total_time`: "Total Time" / "Gesamtzeit"
  * `cluster_stats_last_activity`: "Last Activity" / "Letzte Aktivität"
  * `cluster_stats_today`: "Today" / "Heute"
  * `cluster_stats_yesterday`: "Yesterday" / "Gestern"
  * `cluster_stats_days_ago`: "%1$d days ago" / "vor %1$d Tagen"
  * `cluster_stats_weeks_ago`: "%1$d weeks ago" / "vor %1$d Wochen"

---

## 4. System Invariants & Preserved Behavior

| Invariant Subsystem | Constraint | Preservation Strategy |
|:---|:---|:---|
| **Database Schema** | No SQLite table migrations or column modifications. | Pure read aggregation via `SELECT ... GROUP BY clusterId` on existing columns of `WorkoutSummaries.TABLE`. |
| **Spatial Similarity & Clustering** | Spatial clustering algorithm, 3D similarity scoring, start/end/apex geometric tolerances MUST NOT change. | `calculateSimilarity()` and clustering engine logic remain untouched. |
| **Cluster Identity & Numbering** | Auto-naming counters (`REQ-SET-066`, `REQ-SET-067`) and rename flows MUST remain intact. | `formatClusterWorkoutName` and cluster naming pipelines remain untouched. |
| **Zero-Workout Clusters** | Clusters with 0 recordings must handle null stats gracefully without crashing or dividing by zero. | `WorkoutClusterStats` default values provide safe 0 and null handling. |
| **Map Rendering Performance** | Pre-calculated map state in `ClusterMapState` and selection-driven background loading must not block main thread. | Database aggregation is offloaded to `Dispatchers.IO`; Compose UI observes state reactively. |

---

## 5. Testing & Verification Strategy (`TST-SET-055`)

### 5.1 Unit Tests (`WorkoutClusterStatsTest.kt`)
* **Test 1: Empty Cluster Boundary**:
  * Query cluster with 0 sessions -> returns default stats (0 count, null best time, 0 distance).
* **Test 2: Single-Workout Benchmark**:
  * 1 finished workout (duration 2400s, distance 10000m, ascent 150m, speed 4.16 m/s).
  * Assert `bestDurationSec == 2400L`, `avgDurationSec == 2400L`, `totalDistanceMeters == 10000.0`, `totalAscentMeters == 150L`.
* **Test 3: Multi-Workout Aggregation**:
  * 3 workouts: durations [2400s, 2100s, 2700s], distances [10000m, 10200m, 9800m], ascents [150m, 200m, 180m].
  * Assert `bestDurationSec == 2100L` (PR).
  * Assert `avgDurationSec == 2400L` (mean).
  * Assert `totalDistanceMeters == 30000.0`.
  * Assert `totalAscentMeters == 530L`.
* **Test 4: Unfinished Workout Exclusion**:
  * Finished workout (3000s, `finished = 1`), unfinished workout (1500s, `finished = 0`).
  * Assert `bestDurationSec == 3000L` (unfinished workout excluded from PR).
* **Test 5: Relative Recency Formatter**:
  * Difference 0 days -> contains "Today".
  * Difference 1 day -> contains "Yesterday".
  * Difference 4 days -> contains "4 days ago".
  * Difference 14 days -> contains "2 weeks ago".
* **Test 6: Sport-Adaptive Velocity**:
  * Sport type `RUN` -> formatted as pace (`min/km`).
  * Sport type `BIKE` -> formatted as speed (`km/h`).

### 5.2 Localization Parity Test
* Unit test asserting all new string keys are present across all 9 language directories with identical placeholder tokens (`%1$d`).

---

## 6. Cross-Requirement Impact Assessment (SWE.2 Interface Consistency)

Cross-requirement analysis performed against existing requirements touching modified files:

| File Slated for Modification | Mapped Requirements | Potential Impact & Verification of Non-Degradation |
|:---|:---|:---|
| `WorkoutSummariesDatabaseManager.java` | `REQ-SET-004`, `REQ-SET-007`, `REQ-DAT-008`, `REQ-DAT-009`, `REQ-DAT-010`, `REQ-MIG-024`, `REQ-MAP-020`, `REQ-PER-001`, `REQ-STB-003` | Read-only SQL aggregation added. No existing database upgrade scripts or transaction logic altered. `REQ-DAT-008` (atomic upgrades) and `REQ-DAT-010` (deletion purges) strictly preserved. |
| `WorkoutClusterRepository.kt` | `REQ-SET-016`, `REQ-SET-021`, `REQ-SET-033`, `REQ-SET-035`, `REQ-SET-038`, `REQ-SET-040`, `REQ-SET-062`, `REQ-PER-001`, `REQ-PER-003`, `REQ-PER-006` | Statistics StateFlow added alongside existing cluster list and migration flows. `REQ-SET-062` (auto-purge zero clusters) and `REQ-SET-038` (hit count healing) remain unchanged. |
| `WorkoutClustersViewModel.kt` | `REQ-SET-010`, `REQ-SET-014`, `REQ-SET-032`, `REQ-SET-060`, `REQ-SET-063`, `REQ-SET-065`, `REQ-SET-067`, `REQ-UI-136`, `REQ-PER-001`, `REQ-PER-002` | `clusterStats` StateFlow exposed to Composable tree. Background map state calculation (`ClusterMapState`) and sorting criteria remain intact. |
| `WorkoutClusterHeatmapScreen.kt` | `REQ-SET-009`, `REQ-SET-012`, `REQ-SET-016`, `REQ-SET-017`, `REQ-SET-020`, `REQ-SET-021`, `REQ-SET-022`, `REQ-SET-024`, `REQ-SET-032`, `REQ-SET-033`, `REQ-SET-041`, `REQ-SET-060`, `REQ-SET-065`, `REQ-SET-067` | Summary header enhanced with multi-tier statistics cards. Edit fingerprint mode, marker toggles, context menus, and navigation actions remain identical. |
| `WorkoutClusterComponents.kt` | `REQ-SET-035`, `REQ-SET-042`, `REQ-SET-056`, `REQ-SET-057`, `REQ-SET-059`, `REQ-SET-067` | Compact preview card metadata block displays Best Time and Total Distance. 100x100 preview map rendering, start/end/apex markers, and click handlers preserved without truncation. |
