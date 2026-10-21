# Stage 2: Requirement & Test Specification - ATT-4008: [Verbesserung] Add missing database indexes and remove N+1 queries in routes and clustering

**Ticket**: [ATT-4008](https://rainerblind.atlassian.net/browse/ATT-4008) | **Sub-task**: Pending Submission (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4008`  
**Requirement Mapping**: `REQ-DAT-034` | **Test Spec ID**: `TST-DAT-029`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`) | **Date**: 2026-10-21  

---

## 1. Requirement Traceability Matrix

| Requirement | Description | Test Case | Target Artifact | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-DAT-034.1` | Schema version upgrades & index migrations (`onCreate` & `onUpgrade`) | `TST-DAT-029.1` | `RoutesDbHelper`, `WorkoutSummariesDbHelper`, `LapsDbHelper` | Planned |
| `REQ-DAT-034.2` | Routes N+1 query elimination (`getAllRouteSummaries`, batched `getAllRoutes`) | `TST-DAT-029.2` | `RoutesDatabaseManager.kt` | Planned |
| `REQ-DAT-034.3` | Transactional batching & in-memory cluster caching during migration | `TST-DAT-029.3` | `WorkoutClusterEngine.kt` | Planned |
| `REQ-DAT-034.4` | Concurrency synchronization lock across cluster mutation pathways | `TST-DAT-029.4` | `WorkoutClusterEngine.kt` | Planned |
| `REQ-DAT-034.5` | Preservation of core invariants (clustering math, schema compatibility) | `TST-DAT-029.5` | Clean-Room Test Suite | Planned |

---

## 2. Formal Requirement Specifications

* **Database Version Upgrades & Index Migrations (`REQ-DAT-034.1`)**:
  * `RoutesDbHelper` SHALL bump `DB_VERSION` to `11`. `onCreate` & `onUpgrade` (`oldVersion < 11`) SHALL create index `idx_route_points_route_id` on `route_points(route_id_fk)`.
  * `WorkoutSummariesDbHelper` SHALL bump `DB_VERSION` to `26`. `onCreate` & `onUpgrade` (`oldVersion < 26`) SHALL create `idx_extrema_workout_sensor_extrema` on `ExtremumValues(workoutId, sensorType, extremaType)`, `idx_summaries_cluster_id` on `WorkoutSummaries(clusterId)`, and `idx_summaries_file_base_name_nocase` on `WorkoutSummaries(fileBaseName COLLATE NOCASE)`.
  * `LapsDbHelper` SHALL bump `DB_VERSION` to `3`. `onCreate` & `onUpgrade` (`oldVersion < 3`) SHALL create `idx_laps_workout_id` on `Laps(workoutID)`.
* **Routes N+1 Query Elimination (`REQ-DAT-034.2`)**:
  * `RoutesDatabaseManager` SHALL expose `getAllRouteSummaries(): List<RouteSummary>`, issuing 1 query on `TABLE_ROUTES` with zero point/waypoint queries.
  * `getAllRoutes(): List<RouteWithPath>` SHALL fetch route summaries, then bulk-fetch path points and waypoints in ordered batch queries, grouping into `RouteWithPath` in-memory.
* **Transactional Batching & Cluster Caching (`REQ-DAT-034.3`)**:
  * `WorkoutClusterEngine.migrateHistory` SHALL enclose Phase 1 (routes) and Phase 2 (workouts) in SQLite transactions (`beginTransaction() / setTransactionSuccessful() / endTransaction()`) across both databases.
  * `migrateHistory` SHALL cache clusters in memory and maintain the local cluster cache across mutations, eliminating per-workout $O(N)$ `getAllClusters()` DB scans.
* **Concurrency Synchronization (`REQ-DAT-034.4`)**:
  * `WorkoutClusterEngine` cluster mutation pathways (`migrateHistory`, `learnFromWorkout`, `learnFromRoute`, `assignClusterToWorkout`) SHALL be serialized via internal synchronization lock (`@Synchronized` / mutex).
* **System Invariants (`REQ-DAT-034.5`)**:
  * Zero alteration to cluster scoring algorithms, spatial distance formulas, or centroid math (`REQ-SET-064`, `REQ-SET-065`).
  * 100% backward compatibility across all SQLite upgrade paths (`Routes.db` v10 $\rightarrow$ v11, `WorkoutSummaries.db` v25 $\rightarrow$ v26, `Laps.db` v2 $\rightarrow$ v3).

---

## 3. Formal Test Case Specifications (Given / When / Then)

* **`TST-DAT-029.1` — Schema Migration & Index Verification**:
  * *Given* SQLite databases on fresh creation and upgrade (`Routes.db` v10 $\rightarrow$ v11, `WorkoutSummaries.db` v25 $\rightarrow$ v26, `Laps.db` v2 $\rightarrow$ v3), *When* opened, *Then* `PRAGMA index_list` confirms all 5 target indexes exist and `EXPLAIN QUERY PLAN` confirms indexed scans.
* **`TST-DAT-029.2` — Routes N+1 Query Elimination & Parity**:
  * *Given* 50 stored routes, *When* `getAllRouteSummaries()` is invoked, *Then* exactly 1 query executes against `TABLE_ROUTES`, and *When* `getAllRoutes()` is invoked, *Then* batched loading returns identical `RouteWithPath` results to legacy loading.
* **`TST-DAT-029.3` — Transactional Batching & Cluster In-Memory Caching**:
  * *Given* 500 workouts during `migrateHistory()`, *When* migration executes, *Then* writes execute inside SQLite transactions and candidate matching evaluates against cached clusters with zero per-row `getAllClusters()` scans.
* **`TST-DAT-029.4` — Concurrency Synchronization Stress**:
  * *Given* parallel threads invoking `migrateHistory`, `learnFromWorkout`, and `assignClusterToWorkout`, *When* executing concurrently, *Then* operations serialize safely with zero race hazards, deadlocks, or duplicate clusters.

---

## 4. Edge Case & Regression Matrix

| Edge Case / Invariant | Precondition | Trigger / Input | Expected Result |
| :--- | :--- | :--- | :--- |
| **Idempotent Migration** | Indexes already exist (e.g. v25 created with `fileBaseName NOCASE`) | `onUpgrade` executed | `CREATE INDEX IF NOT EXISTS` runs idempotently without SQLite exceptions. |
| **Empty Route Table** | Zero routes in `Routes.db` | `getAllRouteSummaries()` / `getAllRoutes()` called | Returns `emptyList()` immediately without executing waypoint/point queries. |
| **Concurrent Mutation** | `migrateHistory` running in background | `TrackerService` finalizes workout calling `learnFromWorkout` | Synchronization lock serializes calls; centroid math remains coherent. |
| **Transaction Rollback** | Exception thrown during Phase 2 workout loop | Database write fails mid-migration | Outer transaction aborts cleanly without partial cluster corruptions. |

---

## 5. Localization & Resource Parity Matrix

* **Resource Impact**: Zero new string resources required; touches only internal SQLite helpers, DAOs, and engine synchronization.
* **Regression Test**: Run `TranslationParityTest` to confirm 0 missing entries and 100% format specifier parity across all 9 canonical locales.
