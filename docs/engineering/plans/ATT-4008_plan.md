# Stage 3: Implementation Plan - ATT-4008: Add missing database indexes and remove N+1 queries in routes and clustering

**Ticket**: [ATT-4008](https://rainerblind.atlassian.net/browse/ATT-4008)  
**Sub-task**: [ATT-4553](https://rainerblind.atlassian.net/browse/ATT-4553) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4008`  
**Requirement Mapping**: `REQ-DAT-034` | **Test Mapping**: `TST-DAT-029`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-21  

---

## 1. Architecture & Invariant Compliance (SWE.2)
* **Database Layer Migrations**: Bump schema versions with idempotent `CREATE INDEX IF NOT EXISTS` in both `onCreate` and `onUpgrade`:
  * `Routes.db` (v10 $\rightarrow$ v11): `idx_route_points_route_id` on `route_points(route_id_fk)`.
  * `WorkoutSummaries.db` (v25 $\rightarrow$ v26): `idx_extrema_workout_sensor_extrema` on `ExtremumValues(workoutId, sensorType, extremaType)`, `idx_summaries_cluster_id` on `WorkoutSummaries(clusterId)`, `idx_summaries_file_base_name_nocase` on `WorkoutSummaries(fileBaseName COLLATE NOCASE)`.
  * `Laps.db` (v2 $\rightarrow$ v3): `idx_laps_workout_id` on `Laps(workoutID)`.
* **DAO & Query Optimization**:
  * Expose `getAllRouteSummaries(): List<RouteSummary>` in `RoutesDatabaseManager` for metadata callers (e.g. `RoutesRepository.kt:33`), avoiding loading coordinate paths.
  * Optimize `getAllRoutes(): List<RouteWithPath>` to bulk-query path points and waypoints in ordered passes grouped by route ID, replacing $1 + 2N$ queries with 3.
* **Engine Optimization & Concurrency Synchronization**:
  * In `WorkoutClusterEngine.migrateHistory`: wrap passes in SQLite transactions and cache `allClusters` in memory during Phase 2 to eliminate per-workout $O(N)$ DB queries.
  * Synchronize cluster mutations (`migrateHistory`, `suggestCluster`, `learnFromWorkout`, `assignClusterToWorkout`) using a lock object (`clusterLock`) in `WorkoutClusterEngine` to prevent race conditions.
* **UI Consistency**: No UI changes (pure database, repository, and domain engine optimization).

---

## 2. Atomic Implementation Steps
1. **Step 1: Schema Migrations & Index DDL** (`REQ-DAT-034.1`):
   * `RoutesDatabaseManager.kt`: Bump `DB_VERSION` to 11. Add index in `onCreate` and `onUpgrade(oldVersion < 11)`.
   * `WorkoutSummariesDatabaseManager.java`: Bump `DB_VERSION` to 26. Add 3 indexes in `onCreate` and `onUpgrade(oldVersion < 26)`.
   * `LapsDatabaseManager.java`: Bump `DB_VERSION` to 3. Add index in `onCreate` and `onUpgrade(oldVersion < 3)`.
2. **Step 2: Routes Batch Querying & Summary API** (`REQ-DAT-034.2`):
   * In `RoutesDatabaseManager.kt`, implement `getAllRouteSummaries()` and rewrite `getAllRoutes()` using bulk queries for points and waypoints.
   * Update `RoutesRepository.kt` callers to use `getAllRouteSummaries()` where full paths are not needed.
3. **Step 3: Cluster Migration Batching, Caching & Thread Safety** (`REQ-DAT-034.3`, `REQ-DAT-034.4`):
   * In `WorkoutClusterEngine.kt`, enclose `migrateHistory` in SQLite transactions, maintain an in-memory cluster cache during migration, and synchronize mutation entry points via `clusterLock`.
4. **Step 4: Existing Test Assertions & Migration Suite** (`REQ-DAT-034.5`):
   * Update `LapsDatabaseManagerTest.kt` (v3) and `WorkoutSummariesDatabaseSourceMigrationTest.kt` (v26).
   * Author `DatabaseMigrationIndexTest.kt`, `RoutesDatabaseManagerOptimizationTest.kt`, and `WorkoutClusterEngineOptimizationTest.kt`.

---

## 3. File-Level Change Matrix
| Target File | Component / Role | Proposed Modification |
| :--- | :--- | :--- |
| `RoutesDatabaseManager.kt` | SQLite / DAO | Bump v11, add `idx_route_points_route_id`, add `getAllRouteSummaries()`, batch `getAllRoutes()`. |
| `WorkoutSummariesDatabaseManager.java` | SQLite / DAO | Bump v26, add `idx_extrema_workout_sensor_extrema`, `idx_summaries_cluster_id`, `idx_summaries_file_base_name_nocase`. |
| `LapsDatabaseManager.java` | SQLite / DAO | Bump v3, add `idx_laps_workout_id` in `onCreate` and `onUpgrade`. |
| `WorkoutClusterEngine.kt` | Domain Engine | Transaction batching and in-memory cluster caching in `migrateHistory`; synchronization lock. |
| `RoutesRepository.kt` | Repository | Delegate metadata checks (`hasStravaRoutes`) to `getAllRouteSummaries()`. |
| `LapsDatabaseManagerTest.kt` | Unit Test | Update `DB_VERSION` assertion from 2 to 3; add v3 upgrade verification. |
| `WorkoutSummariesDatabaseSourceMigrationTest.kt` | Unit Test | Update `DB_VERSION` assertion from 25 to 26; add v26 upgrade verification. |
| `DatabaseMigrationIndexTest.kt` | Contract Test | Verify PRAGMA index lists and EXPLAIN QUERY PLAN index usage for all 5 target indexes. |
| `RoutesDatabaseManagerOptimizationTest.kt` | Unit Test | Verify `getAllRouteSummaries()` and batched `getAllRoutes()` parity. |
| `WorkoutClusterEngineOptimizationTest.kt` | Unit Test | Verify transaction batching, cached cluster lookup, and concurrent mutation serialization. |

---

## 4. Test Execution Strategy
* **Targeted Unit Tests**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.*" \
    --tests "com.atrainingtracker.trainingtracker.repositories.RoutesRepository*"
  ```
* **Clean-Room Verification**: Full `./gradlew testDebugUnitTest` suite (High-Risk SQLite migration exception) and `python3 tools/verify_requirement_governance.py`.

---

## 5. Rollback & Risk Mitigation
* **Rollback**: Schema upgrades are backward-compatible and additive. Git branch `feature/ATT-4008` is isolated.
* **Risk Mitigation**: All index creation commands use `CREATE INDEX IF NOT EXISTS` inside `onUpgrade` try/catch blocks; query plan benchmarking verifies zero regression.
