# Stage 4: Implementation - ATT-3366: Eliminate manual cursor column extractions in repositories using fromCursor mappers

**Ticket**: [ATT-3366](https://rainerblind.atlassian.net/browse/ATT-3366)  
**Sub-task**: [ATT-3571](https://rainerblind.atlassian.net/browse/ATT-3571) (`[Implementation]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.3`  
**Requirement Mapping**: `REQ-DAT-025`  
**Test Spec Mapping**: `TST-DAT-020`  
**Branch**: `feature/ATT-3366`  
**Author**: AI Agent 1 (Software Construction Engineer)  
**Date**: 2026-10-14  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-DAT-025` (*Authoritative Cursor-to-Entity Mappers and Column Index Caching (Rule 20 & Rule 36)*).
2. **Historical Origin & Commit Trace**:
   - In Sprint 2026-41.9 (`ATT-354`), SQLite spatial column extractions (`min_lat`, `max_lat`, etc.) were introduced with manual inline string lookups in `WorkoutClusterDatabaseManager.kt`.
   - In Sprint 2026-42.1 (`ATT-441`, `ATT-502`), 3D altitude extrema columns and JSON preview paths were added with duplicate private mapper methods.
   - In Sprint 2026-42.2 (`ATT-3344`), `RoutesDatabaseManager.kt` added deduplication routines while retaining repeated `cursor.getColumnIndexOrThrow` calls inside large while-loops.
3. **Root Reason for Existing Formulation**:
   - Incremental feature additions appended ad-hoc column queries to existing functions to minimize changes to shared mapper logic.
4. **Preservation of Core Invariants**:
   - **Schema Invariance**: Zero SQLite schema or table alterations.
   - **Lifecycle Invariance**: Single-row `fromCursor` never advances or closes the cursor; callers retain `.use { ... }` ownership.
   - **Index Caching**: Index resolution occurs once per batch query, dropping row-iteration overhead from $O(N \times K)$ to $O(K)$.
   - **Fallback Integrity**: `WaypointType` fallbacks (unparseable strings defaulting to `GENERIC`) and `altitude` defaulting to `0.0` when elevation is null strictly preserved.

---

## 1. Summary of Changes

In accordance with the approved Stage 3 Implementation Plan for **ATT-3366**, this implementation delivers:

1. **Index Caching & Mappers in `RoutesDatabaseManager.kt`**:
   - Added `internal class WaypointIndices(cursor: Cursor)` caching all 9 waypoint column indices.
   - Added `internal class PathPointIndices(cursor: Cursor)` caching 4 track point column indices.
   - Added `internal class RouteSummaryIndices(cursor: Cursor)` caching 14 route summary column indices.
   - Added authoritative single-row and batch mappers:
     - `mapCursorToRouteWaypoint(cursor: Cursor): RouteWaypoint`
     - `internal fun mapCursorToRouteWaypoint(cursor: Cursor, indices: WaypointIndices): RouteWaypoint`
     - `mapCursorToRouteWaypoints(cursor: Cursor): List<RouteWaypoint>`
     - `mapCursorToPathPoint(cursor: Cursor): PathPoint`
     - `internal fun mapCursorToPathPoint(cursor: Cursor, indices: PathPointIndices): PathPoint`
     - `mapCursorToPathPoints(cursor: Cursor): List<PathPoint>`
     - `mapCursorToRouteSummary(cursor: Cursor): RouteSummary`
     - `internal fun mapCursorToRouteSummary(cursor: Cursor, indices: RouteSummaryIndices): RouteSummary`
     - `mapCursorToRouteSummaries(cursor: Cursor): List<RouteSummary>`
   - Refactored `getWaypointsForRoute`, `getRoutePath`, and `getAllRoutes` to delegate to the new batch mappers.

2. **Authoritative Mappers in `WorkoutClusterDatabaseManager.kt`**:
   - Added `WorkoutCluster.Companion.fromCursor(cursor: Cursor): WorkoutCluster`.
   - Added `@JvmStatic fun deserializePreviewPaths(serialized: String): List<String>` in `WorkoutClusterDatabaseManager.Companion`.
   - Added `@JvmStatic fun mapCursorToCluster(cursor: Cursor): WorkoutCluster` in `WorkoutClusterDatabaseManager.Companion`.
   - Added `fromCursor(cursor: Cursor): WorkoutCluster` and `fromCursorBatch(cursor: Cursor): List<WorkoutCluster>` on `WorkoutClusterDatabaseManager`.
   - Refactored `getAllClusters()`, `findCandidates()`, `getClusterById()`, and `getClusterByName()` to delegate directly to `fromCursorBatch` / `fromCursor`.
   - Removed legacy redundant private `mapCursorToCluster` and `deserializePreviewPaths` methods from the instance scope.

3. **Zero-Allocation Batch Key Extraction in `WorkoutClusterRepository.kt`**:
   - Created `WorkoutBatchKey` data class with `WorkoutBatchKey.Companion.fromCursor(cursor: Cursor)`.
   - Created `WorkoutBatchKeys` data class containing `(idList, fileNameList, clusterIdList)`.
   - Added `@JvmStatic fun fromCursorBatchKeys(cursor: Cursor): WorkoutBatchKeys` in `WorkoutClusterRepository.Companion` with single-pass index caching.
   - Refactored `loadWorkoutsWithBatchMetadata()` to extract batch keys via `fromCursorBatchKeys(cursor)` instead of 3x inline column resolutions per row.

4. **Creation of Unit & Contract Test Suites**:
   - Authored `RoutesDatabaseManagerMapperTest.kt` verifying waypoint, pathpoint, and route summary extraction, enum fallback handling, null handling, and cursor lifecycle invariance.
   - Authored `WorkoutClusterMapperTest.kt` verifying `WorkoutCluster` extraction, `fromCursorBatchKeys` index-cached extraction, and `WorkoutBatchKey.fromCursor` single-row mapping symmetry.

---

## 2. File Changes Breakdown

| Target File | Status | Description |
| :--- | :--- | :--- |
| `database/RoutesDatabaseManager.kt` | Modified | Added `WaypointIndices`, `PathPointIndices`, `RouteSummaryIndices`, single-row and batch mappers; refactored queries. |
| `database/WorkoutClusterDatabaseManager.kt` | Modified | Added `fromCursor`, `fromCursorBatch`, companion `mapCursorToCluster`, cleaned up redundant private methods, refactored queries. |
| `database/WorkoutClusterRepository.kt` | Modified | Added `WorkoutBatchKey`, `WorkoutBatchKeys`, `fromCursorBatchKeys`, refactored `loadWorkoutsWithBatchMetadata`. |
| `test/.../RoutesDatabaseManagerMapperTest.kt` | New | Comprehensive contract and mapper tests for RouteWaypoints, PathPoints, and RouteSummaries. |
| `test/.../WorkoutClusterMapperTest.kt` | New | Contract tests for `WorkoutCluster` and `WorkoutBatchKeys` cursor mappers. |

---

## 3. Targeted Test Results

Targeted tests executed via `./gradlew testDebugUnitTest --tests "*RoutesDatabaseManager*" --tests "*WorkoutCluster*"`:
- `RoutesDatabaseManagerMapperTest`: 6/6 passed (100%)
- `WorkoutClusterMapperTest`: 3/3 passed (100%)
- `RoutesDatabaseManagerWaypointTest`: 7/7 passed (100%)
- `RoutesDatabaseManagerDeduplicationTest`: 4/4 passed (100%)
- `RoutesDatabaseManagerTTLTest`: 3/3 passed (100%)
- `WorkoutClusterRepositoryTest`: 15/15 passed (100%)
- `WorkoutClusterStatsTest`: 5/5 passed (100%)
- Overall targeted suite: **43/43 tests passed** (0 failures).
