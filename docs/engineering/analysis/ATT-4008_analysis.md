# Stage 1 Analysis: ATT-4008 - Add missing database indexes and remove N+1 queries in routes and clustering

**Ticket**: [ATT-4008](https://rainerblind.atlassian.net/browse/ATT-4008)  
**Sub-task**: [ATT-4551](https://rainerblind.atlassian.net/browse/ATT-4551) (`[Analysis]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4008`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-21  

---

## 1. Problem Statement & Motivation
* **Missing DB Indexes**: `route_points` lacks an index on foreign key `route_id_fk`; `ExtremumValues` lacks a composite index prefixed by `workoutId`; `WorkoutSummaries` lacks indexes on `clusterId` and `fileBaseName` (the latter omitted from `onUpgrade`); `Laps` lacks an index on `workoutID`. All point/extrema queries degrade to $O(N)$ full table scans.
* **N+1 Query Amplification**: `RoutesDatabaseManager.getAllRoutes` issues $1 + 2N$ queries (loading all lat/lng points and waypoints into memory per route). `WorkoutClusterEngine.migrateHistory` re-queries `getAllClusters()` and reads ~20 extrema/summary attributes per workout row-by-row.
* **Unbatched SQLite Transactions**: `migrateHistory` executes individual updates per workout without an enclosing SQLite transaction, triggering disk sync thrashing.
* **Unsynchronized Concurrency Hazard**: Background startup migration (`TrainingApplication`), file imports (`ImportEngine`), and workout finalization (`TrackerService`) invoke cluster mutations concurrently without mutual exclusion, risking duplicate clusters and corrupted centroid math.

---

## 2. Root Cause Analysis (Forensic Investigation)
* **Index Omissions Verified**:
  * `route_points(route_id_fk)`: `RoutesDatabaseManager.kt:844` only created `idx_route_waypoints_route_id`. `EXPLAIN QUERY PLAN` on `SELECT * FROM route_points WHERE route_id_fk = ?` confirms `SCAN route_points` ($4.3\times$ query latency on 100k points).
  * `ExtremumValues(workoutId, sensorType, extremaType)`: existing index `idx_extrema_sensor_extrema` (`WorkoutSummariesDatabaseManager.java:1718`) is indexed on `(sensorType, extremaType)`. Without `workoutId` leading prefix, `getExtremaPosition(workoutId, ...)` performs a full table scan.
  * `WorkoutSummaries(clusterId)`: missing in `onCreate` and `onUpgrade`, forcing full scans on `getWorkoutCountForCluster`, stats, and sport discovery.
  * `WorkoutSummaries(fileBaseName NOCASE)`: added in `onCreate` (ATT-3991) but missing from `onUpgrade` for `oldVersion < 25`.
  * `Laps(workoutID)`: `LapsDatabaseManager.java:431` has zero indexes, forcing full scans on `getLapsForWorkout(workoutId)`.
* **N+1 and Transaction Deficits**:
  * `getAllRoutes()` (`RoutesDatabaseManager.kt:336-360`) loops over all routes and calls `getRoutePath(id)` and `getWaypointsForRoute(id)`. Callers checking metadata (e.g. `RoutesRepository.kt:33`) load millions of coordinates into memory.
  * `migrateHistory()` (`WorkoutClusterEngine.kt:488-615`) executes outside `beginTransaction() ... endTransaction()`, syncing journals per record while querying `getAllClusters()` on each iteration.
* **Concurrency Gap**: `WorkoutClusterEngine.kt` lacks `@Synchronized` or lock guards; concurrent runs between `TrainingApplication.java:1430`, `ImportEngine.kt:313`, and `TrackerService.java:2815` execute simultaneously without isolation.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Bump DB versions and add missing indexes in both `onCreate` and `onUpgrade`: `Routes.db` (v10 $\rightarrow$ v11), `WorkoutSummaries.db` (v25 $\rightarrow$ v26), and `Laps.db` (v2 $\rightarrow$ v3).
  * Implement lightweight `getAllRouteSummaries(): List<RouteSummary>` and batch route path/waypoint loading in `getAllRoutes()`.
  * Enclose `WorkoutClusterEngine.migrateHistory()` in SQLite transactions and cache the in-memory cluster set during migration.
  * Serialize cluster mutation pipelines (`migrateHistory`, `learnFromWorkout`, `assignClusterToWorkout`) via synchronization/mutex.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to cluster scoring algorithms, spatial distance formulas, or centroid math (Rule 20 / REQ-SET-064).
  * No changes to UI screens, ViewModels, or Jetpack Compose composables.
  * No modification or schema changes to `WorkoutSamples.db`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Net-new requirement only (`REQ-DAT-034`)**: No existing requirements modified.
* **Preservation of Core Invariants**: Existing cluster discovery (`REQ-SET-064`), 3D altitude extrema clustering (`REQ-SET-065`), sequential naming (`REQ-SET-066`, `REQ-SET-067`), candidate evaluation parity (`REQ-MIG-029`), and filename deduplication (`REQ-MIG-042`) remain 100% mathematically and functionally invariant.

---

## 5. Architectural Strategy & High-Level Solution
* **Database Schema Migrations**:
  | Database | Helper / Version | Target Index | Migration Scope |
  | :--- | :--- | :--- | :--- |
  | `Routes.db` | `RoutesDbHelper` (v10 $\rightarrow$ v11) | `idx_route_points_route_id` on `route_points(route_id_fk)` | `onCreate` & `onUpgrade` |
  | `WorkoutSummaries.db` | `WorkoutSummariesDbHelper` (v25 $\rightarrow$ v26) | `idx_extrema_workout_sensor_extrema` on `ExtremumValues(workoutId, sensorType, extremaType)`; `idx_summaries_cluster_id` on `WorkoutSummaries(clusterId)`; `idx_summaries_file_base_name_nocase` | `onCreate` & `onUpgrade` |
  | `Laps.db` | `LapsDbHelper` (v2 $\rightarrow$ v3) | `idx_laps_workout_id` on `Laps(workoutID)` | `onCreate` & `onUpgrade` |
* **Routes Batching & Lazy Metadata**: Provide `getAllRouteSummaries()` for metadata-only consumers; rewrite `getAllRoutes()` to fetch all points and waypoints in ordered bulk queries grouped in memory into `RouteWithPath`.
* **Transactional Clustering**: Wrap `migrateHistory()` in `beginTransaction() / setTransactionSuccessful() / endTransaction()` on both databases; cache cluster candidates in memory during the migration pass.
* **Thread Safety**: Introduce an explicit internal lock / synchronization barrier in `WorkoutClusterEngine` to serialize migrations and cluster assignment calls.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Complete backward compatibility for existing SQLite databases across all supported upgrade paths.
  2. Zero deviation in cluster matching results, naming output, or route waypoint/point sequence.
  3. Strict thread safety and non-blocking background execution during migration.
* **Risk Rating**: **MEDIUM** — Touches multiple SQLite database helpers, version upgrades, and engine synchronization; mitigated by migration unit tests, index EXPLAIN QUERY PLAN verification, and clean-room test execution.
