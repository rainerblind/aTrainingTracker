# Stage 5 Walkthrough - ATT-3366: Eliminate manual cursor column extractions in repositories using fromCursor mappers

**Ticket**: [ATT-3366](https://rainerblind.atlassian.net/browse/ATT-3366)  
**Stage 5 Subtask**: [ATT-3572](https://rainerblind.atlassian.net/browse/ATT-3572) (`[Test]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.3`  
**Requirement Mapping**: `REQ-DAT-025` (`Verified`)  
**Test Mapping**: `TST-DAT-020` (`Verified`)  
**Branch**: `feature/ATT-3366`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Executive Summary & Implementation Details

Prior to ATT-3366, database repositories across the routing and cluster persistence layers relied on ad-hoc, inline `cursor.getColumnIndexOrThrow` calls inside query while-loops:
- In `RoutesDatabaseManager.kt`: `getWaypointsForRoute` and `getRoutePath` repeatedly resolved column string indices inside row iteration loops, incurring $O(N \times K)$ JNI lookup overhead on multi-hundred-point routes.
- In `WorkoutClusterDatabaseManager.kt`: Duplicate private mapper implementations (`mapCursorToCluster`, `deserializePreviewPaths`) existed across instance and companion scopes, while queries manually looped through rows rather than delegating to centralized mapper routines.
- In `WorkoutClusterRepository.kt`: `loadWorkoutsWithBatchMetadata` performed three individual column lookups per row inside a large batch extraction loop.

ATT-3366 standardizes cursor-to-entity mapping in accordance with **Rule 20 (Database & DTO Mapping Symmetry)** and **Rule 36 (Single Source of Truth & DRY — Shared Code Over Duplication)**, introducing single-pass column index caching, authoritative row and batch mappers, zero-allocation batch key extraction, and strict cursor lifecycle preservation with zero schema changes.

### Key Architectural Changes & Refactorings

1. **Index Caching & Two-Tier Mapper Architecture (`RoutesDatabaseManager.kt`)**:
   - Introduced lightweight immutable index cache holders:
     - `WaypointIndices(cursor: Cursor)` caching 9 waypoint columns (`_ID`, `ROUTE_ID_FK`, `NAME`, `DESCRIPTION`, `LAT`, `LNG`, `ELEVATION`, `DIST_FROM_START`, `TYPE`).
     - `PathPointIndices(cursor: Cursor)` caching 4 coordinate and elevation columns (`LAT`, `LNG`, `DIST_FROM_START`, `ALTITUDE`).
     - `RouteSummaryIndices(cursor: Cursor)` caching 14 route metadata columns.
   - Provided single-row mappers:
     - `fun mapCursorToRouteWaypoint(cursor: Cursor): RouteWaypoint`
     - `internal fun mapCursorToRouteWaypoint(cursor: Cursor, indices: WaypointIndices): RouteWaypoint`
     - `fun mapCursorToPathPoint(cursor: Cursor): PathPoint`
     - `internal fun mapCursorToPathPoint(cursor: Cursor, indices: PathPointIndices): PathPoint`
     - `fun mapCursorToRouteSummary(cursor: Cursor): RouteSummary`
     - `internal fun mapCursorToRouteSummary(cursor: Cursor, indices: RouteSummaryIndices): RouteSummary`
   - Provided batch mappers resolving indices once before iteration:
     - `fun mapCursorToRouteWaypoints(cursor: Cursor): List<RouteWaypoint>`
     - `fun mapCursorToPathPoints(cursor: Cursor): List<PathPoint>`
     - `fun mapCursorToRouteSummaries(cursor: Cursor): List<RouteSummary>`
   - Refactored `getWaypointsForRoute()`, `getRoutePath()`, and `getAllRoutes()` to delegate directly through the batch mappers.

2. **Authoritative Cluster Mapping & Deduplication (`WorkoutClusterDatabaseManager.kt`)**:
   - Added `WorkoutCluster.Companion.fromCursor(cursor: Cursor): WorkoutCluster`.
   - Exposed `@JvmStatic fun deserializePreviewPaths(serialized: String): List<String>` in companion object.
   - Exposed `@JvmStatic fun mapCursorToCluster(cursor: Cursor): WorkoutCluster` in companion object.
   - Added `fromCursor(cursor: Cursor): WorkoutCluster` and `fromCursorBatch(cursor: Cursor): List<WorkoutCluster>` on `WorkoutClusterDatabaseManager`.
   - Refactored `getAllClusters()`, `findCandidates()`, `getClusterById()`, and `getClusterByName()` to delegate directly to `fromCursorBatch` / `fromCursor`.
   - Removed legacy duplicate private `mapCursorToCluster` and `deserializePreviewPaths` from instance scope.

3. **Zero-Allocation Batch Key Extraction (`WorkoutClusterRepository.kt`)**:
   - Defined `WorkoutBatchKey(val id: Long, val fileName: String?, val clusterId: Long)` with `WorkoutBatchKey.Companion.fromCursor(cursor: Cursor)`.
   - Defined `WorkoutBatchKeys(val idList: List<Long>, val fileNameList: List<String>, val clusterIdList: Set<Long>)`.
   - Implemented `@JvmStatic fun fromCursorBatchKeys(cursor: Cursor): WorkoutBatchKeys` resolving column indices (`C_ID`, `FILE_BASE_NAME`, `CLUSTER_ID`) once before iteration and streaming directly into primitive/string collections.
   - Refactored `loadWorkoutsWithBatchMetadata()` to extract batch keys via `fromCursorBatchKeys(cursor)` instead of 3x inline column resolutions per row.

4. **Creation of Unit & Contract Test Suites**:
   - `RoutesDatabaseManagerMapperTest.kt`: Validated single-row waypoint mapping with null elevation and unparseable type strings (defaulting to `WaypointType.GENERIC` and `altitude = 0.0`), path point mapping, route summary mapping, batch iteration parity, and cursor lifecycle invariance (no advancing or closing by row mappers).
   - `WorkoutClusterMapperTest.kt`: Validated cluster extraction with spatial and altitude bounds, `WorkoutBatchKey.fromCursor` single-row symmetry, and `fromCursorBatchKeys` index-cached batch extraction with `-1L` cluster ID exclusion.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-DAT-025` (*Authoritative Database Cursor Row and Batch Mappers for Route and Cluster Repositories*) $\to$ **Status: Verified**
* **Test Mapping**: `TST-DAT-020` (*Authoritative Database Cursor Row and Batch Mappers Verification*) $\to$ **Status: Verified**

---

## 3. Preserved Invariants & Invariant Verification

1. **Rule 20 (Database & DTO Mapping Symmetry)**: Standardized `fromCursor` and `fromCursorBatch` across repositories and DTO companion objects.
2. **Rule 36 (Single Source of Truth & DRY)**: Centralized column lookups into dedicated index holders and canonical mappers, eliminating duplicated inline parsing logic across query methods.
3. **Cursor Positioning & Lifecycle Ownership**: Single-row `fromCursor` mappers require the caller to position the cursor, and never mutate cursor position (`moveToNext`) or close the cursor (`close()`). Batch mappers leave cursor closing to the caller's `.use { ... }` block.
4. **Fallback & Null-Safety Invariants**: Unparseable waypoint types fall back safely to `WaypointType.GENERIC`; null elevations default to `0.0`; optional cluster spatial bounds and altitude coordinates preserve nullability semantics.
5. **Schema Invariance**: Zero SQLite table or schema alterations in `Routes.db` or `WorkoutCluster.db`.
6. **Clean-Room Regression Free**: Full unit test suite passes 100% with zero failures across 2,693+ tests.

---

## 4. Test Verification Results

### Unit & Contract Test Suites

| Test Suite | Description | Result |
| :--- | :--- | :--- |
| `RoutesDatabaseManagerMapperTest` | Verifies waypoint, pathpoint, route summary extraction, enum fallback, null elevation, and cursor lifecycle | **PASS** (6/6 tests) |
| `WorkoutClusterMapperTest` | Verifies cluster entity extraction, `WorkoutBatchKey.fromCursor` symmetry, and `fromCursorBatchKeys` index caching | **PASS** (3/3 tests) |
| `RoutesDatabaseManagerWaypointTest` | Verifies waypoint CRUD operations and route association | **PASS** (7/7 tests) |
| `RoutesDatabaseManagerDeduplicationTest` | Verifies point deduplication algorithms | **PASS** (4/4 tests) |
| `RoutesDatabaseManagerTTLTest` | Verifies route cache expiration and TTL management | **PASS** (3/3 tests) |
| `WorkoutClusterRepositoryTest` | Verifies cluster repository operations, batch loading, and stats | **PASS** (15/15 tests) |
| `WorkoutClusterStatsTest` | Verifies cluster summary metrics and computations | **PASS** (5/5 tests) |

### Clean-Room Full Suite Regression Run
* Command: `./gradlew testDebugUnitTest`
* Result: **100% Tests Passed** (2,693 tests completed, 0 failures, 0 errors, 0 regressions).
