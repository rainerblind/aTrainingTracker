# Stage 2: Requirement & Test Specification - ATT-3366: Eliminate manual cursor column extractions in repositories using fromCursor mappers

**Ticket**: [ATT-3366](https://rainerblind.atlassian.net/browse/ATT-3366)  
**Sub-task**: [ATT-3569](https://rainerblind.atlassian.net/browse/ATT-3569) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.3`  
**Requirement Mapping**: `REQ-DAT-025` (*Authoritative Database Cursor Row and Batch Mappers for Route and Cluster Repositories*)  
**Test Spec ID**: `TST-DAT-020`  
**Branch**: `feature/ATT-3366`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Requirement Specification (REQ-DAT-025)

### 1.1 Problem Statement & Rationale
Under **Rule 20 (Database & DTO Mapping Symmetry)** and **Rule 36 (Single Source of Truth & DRY — Shared Code Over Duplication)**, all database cursor reading must route through symmetric, authoritative mapper methods on the database manager or entity companion (e.g. `fromCursor(cursor: Cursor)` and `fromCursorBatch(cursor: Cursor): List<T>`).

However, in several repositories, raw manual cursor extractions were implemented inline:
1. In `WorkoutClusterRepository.kt:408–418`: Manual column index lookups (`cursor.getColumnIndexOrThrow(...)` for `C_ID`, `FILE_BASE_NAME`, and `CLUSTER_ID`) are executed repeatedly per row within a `do { ... } while (cursor.moveToNext())` loop rather than delegating to an authoritative DTO/batch-key helper or caching column indices.
2. In `RoutesDatabaseManager.kt:295–324`: Manual column index resolution (9 columns) and instantiation of `RouteWaypoint` are implemented inline in `getWaypointsForRoute()`.
3. In `RoutesDatabaseManager.kt:585–601`: Manual column index resolution (4 columns) and instantiation of `PathPoint` are implemented inline in `getRoutePath()`.
4. In `WorkoutClusterDatabaseManager.kt:269–308`: `mapCursorToCluster(cursor: Cursor): WorkoutCluster` is private and unexposed, lacking standardized `fromCursor` and `fromCursorBatch` companion/manager APIs.

### 1.2 Functional & Architectural Scope

1. **Cursor Lifecycle & Positioning Invariants**:
   - **Row-Level Mapper (`fromCursor` / `mapCursorTo...`)**:
     - *Pre-Condition*: The cursor MUST already be positioned at the desired target row before calling `fromCursor(cursor)`.
     - *Post-Condition*: The mapper extracts entity values strictly from the current row. It MUST NOT advance the cursor (`moveToNext()`), reset the cursor (`moveToFirst()`), or close the cursor (`close()`).
     - *Ownership*: Cursor lifecycle management (opening, transaction boundaries, and closing via `use { ... }`) remains strictly the caller's responsibility.
   - **Batch Mapper (`fromCursorBatch` / `mapCursorTo...s`)**:
     - Iterates over the cursor from `moveToFirst()` through `while (cursor.moveToNext())`, collecting mapped items into an immutable `List<T>`.
     - The batch helper MUST NOT close the cursor, allowing callers to perform subsequent operations within their `.use { ... }` block.

2. **Routes Database Manager Mappers (`RoutesDatabaseManager.kt`)**:
   - **RouteWaypoint Mapping**:
     - `fun mapCursorToRouteWaypoint(cursor: Cursor): RouteWaypoint`
     - `internal fun mapCursorToRouteWaypoint(cursor: Cursor, indices: WaypointIndices): RouteWaypoint`
     - `fun mapCursorToRouteWaypoints(cursor: Cursor): List<RouteWaypoint>`
     - `getWaypointsForRoute(routeId: Long): List<RouteWaypoint>` SHALL delegate to `mapCursorToRouteWaypoints(cursor)`.
   - **PathPoint Mapping**:
     - `fun mapCursorToPathPoint(cursor: Cursor): PathPoint`
     - `internal fun mapCursorToPathPoint(cursor: Cursor, indices: PathPointIndices): PathPoint`
     - `fun mapCursorToPathPoints(cursor: Cursor): List<PathPoint>`
     - `getRoutePath(routeId: Long): List<PathPoint>` SHALL delegate to `mapCursorToPathPoints(cursor)`.
   - **RouteSummary Mapping**:
     - Promote `mapCursorToRouteSummary(cursor: Cursor): RouteSummary` visibility from `private` to `internal`.
     - `fun mapCursorToRouteSummaries(cursor: Cursor): List<RouteSummary>`.

3. **High-Performance Index Caching**:
   - Encapsulate column indices in immutable lightweight holders (`WaypointIndices`, `PathPointIndices`).
   - Batch mappers instantiate indices **once** before iteration, eliminating $O(N \times K)$ JNI `getColumnIndexOrThrow` invocations inside row loops.

4. **Workout Cluster Database Manager & Repository (`WorkoutClusterDatabaseManager.kt`, `WorkoutClusterRepository.kt`)**:
   - `WorkoutClusterDatabaseManager` SHALL expose:
     - `fun fromCursor(cursor: Cursor): WorkoutCluster` (and `WorkoutCluster.Companion.fromCursor(cursor)`)
     - `fun fromCursorBatch(cursor: Cursor): List<WorkoutCluster>`
     - Update internal queries (`getAllClusters()`, `findCandidateClusters()`, `getClusterById()`, `getClusterByName()`) to route through `fromCursorBatch` / `fromCursor`.
   - `WorkoutClusterRepository.kt`:
     - Provide `fromCursorBatchKeys(cursor: Cursor): WorkoutBatchKeys`, resolving column indices (`C_ID`, `FILE_BASE_NAME`, `CLUSTER_ID`) once and populating primitive/string collections directly (`idList: List<Long>`, `fileNameList: List<String>`, `clusterIdList: Set<Long>`) without intermediate wrapper object allocation per row.
     - Provide `WorkoutBatchKey.fromCursor(cursor: Cursor)` for single-row mapping symmetry per Rule 20.

5. **Preservation of Domain Invariants**:
   - **Waypoint Fallback**: `try { WaypointType.valueOf(typeStr) } catch (_: Exception) { WaypointType.GENERIC }` MUST be strictly preserved.
   - **Elevation Nullability**: `if (cursor.isNull(elevIdx)) 0.0 else cursor.getDouble(elevIdx)` MUST be strictly preserved.
   - **Schema Stability**: Zero changes to SQLite table schemas in `Routes.db` or `WorkoutCluster.db`.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Net-new requirement (`REQ-DAT-025`) complementing `REQ-DAT-021` (*Workout Origin Source Batch Mapping Symmetry*), `REQ-SET-062` (*Workout Cluster Spatial Persistence*), and `REQ-MAP-026` (*Support Waypoints, POIs and TCX Course Points*).
2. **Historical Origin & Commit Trace**: `RoutesDatabaseManager.kt` introduced in Sprint 2026-41.2; `WorkoutClusterRepository.kt` batch querying introduced in Sprint 2026-40.12 (`ATT-359` / `ATT-388`).
3. **Root Reason for Existing Formulation**: Methods were authored iteratively, leaving inline cursor column extractions duplicated across query loops rather than centralized in canonical mapper contracts.
4. **Preservation of Core Invariants**: Waypoint fallback parsing, altitude nullability defaults, cursor positioning ownership, and database schemas remain strictly preserved.

---

## 3. Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Zero Duplicate Column Lookups & Canonical Mapper Delegation)**:
  * *Given* the routes and cluster repositories querying SQLite tables,
  * *When* extracting `RouteWaypoint`, `PathPoint`, `RouteSummary`, or `WorkoutCluster` entities,
  * *Then* cursor rows are parsed via dedicated `mapCursorTo...` / `fromCursor` methods, with zero duplicated inline column index lookups across query routines.
* **Criterion 2 (Cursor Lifecycle & Positioning Invariants)**:
  * *Given* an open SQLite cursor positioned at an entity row,
  * *When* passed to a row-level mapper (`mapCursorToRouteWaypoint`, `mapCursorToPathPoint`, `fromCursor`),
  * *Then* the mapper extracts the row without advancing the cursor (`moveToNext`), without resetting the cursor (`moveToFirst`), and without closing the cursor (`close`).
* **Criterion 3 (Fallbacks & Nullability Integrity)**:
  * *Given* a cursor row with an unknown, corrupted, or null `type` string,
  * *When* mapped to `RouteWaypoint`,
  * *Then* `type` evaluates safely to `WaypointType.GENERIC`.
  * *Given* a cursor row with null elevation,
  * *When* mapped to `RouteWaypoint`,
  * *Then* `altitude` evaluates to `0.0`.
* **Criterion 4 (High-Performance Index Caching & Parity)**:
  * *Given* a multi-row cursor query in `RoutesDatabaseManager` or `WorkoutClusterRepository`,
  * *When* batch-mapping entities or extracting batch keys,
  * *Then* column indices are resolved exactly once before looping, yielding identical results to row-by-row extraction while eliminating redundant JNI lookups and intermediate wrapper allocations.

---

## 4. Test Specification (TST-DAT-020)

### 4.1 Unit & Contract Test Cases

1. **`RoutesDatabaseManagerMapperTest.kt`**:
   - `testMapCursorToRouteWaypoint_allFieldsExtractedCorrectly`: Verify all 9 fields (`id`, `routeId`, `latLng`, `altitude`, `name`, `description`, `type`, `distanceFromStart`) are accurately mapped.
   - `testMapCursorToRouteWaypoint_invalidType_fallsBackToGeneric`: Verify unrecognized or corrupted string in `type` column falls back safely to `WaypointType.GENERIC`.
   - `testMapCursorToRouteWaypoint_nullElevation_defaultsToZero`: Verify null elevation column defaults to `altitude = 0.0`.
   - `testMapCursorToPathPoint_allFieldsExtracted`: Verify `latLng`, `distance`, and `altitude` are correctly extracted into `PathPoint`.
   - `testMapCursorToRouteSummary_allFieldsExtracted`: Verify `RouteSummary` extraction parity.
   - `testBatchMappers_singlePassIndexCaching_matchesRowByRow`: Verify `mapCursorToRouteWaypoints` and `mapCursorToPathPoints` produce identical lists to row-by-row mapping.
   - `testRowMapper_doesNotAdvanceOrCloseCursor`: Verify cursor row index remains unchanged and cursor remains open after calling row mappers.

2. **`WorkoutClusterMapperTest.kt`**:
   - `testWorkoutCluster_fromCursor_and_fromCursorBatch`: Verify `fromCursor` and `fromCursorBatch` accurately extract all `WorkoutCluster` fields.
   - `testWorkoutClusterRepository_fromCursorBatchKeys`: Verify `fromCursorBatchKeys` extracts all `idList`, `fileNameList`, and `clusterIdList` correctly with pre-cached column indices.
   - `testWorkoutBatchKey_fromCursor`: Verify single-row DTO mapping symmetry.

3. **Existing Repository Regression Tests**:
   - Verify `RoutesDatabaseManagerWaypointTest.kt` passes.
   - Verify `RoutesDatabaseManagerDeduplicationTest.kt` passes.
   - Verify `RoutesDatabaseManagerTTLTest.kt` passes.
   - Verify `WorkoutClusterRepositoryTest.kt` passes.
   - Verify `WorkoutClusterStatsTest.kt` passes.

---

## 5. Traceability Matrix

| Requirement Clause | Test Case | Target Implementation File |
| :--- | :--- | :--- |
| `REQ-DAT-025` Clause 1 (Cursor Lifecycle & Positioning) | `testRowMapper_doesNotAdvanceOrCloseCursor` | `RoutesDatabaseManager.kt`, `WorkoutClusterDatabaseManager.kt` |
| `REQ-DAT-025` Clause 2 (Routes Database Manager Mappers) | `testMapCursorToRouteWaypoint_*`, `testMapCursorToPathPoint_*` | `RoutesDatabaseManager.kt` |
| `REQ-DAT-025` Clause 3 (High-Performance Index Caching) | `testBatchMappers_singlePassIndexCaching_matchesRowByRow` | `RoutesDatabaseManager.kt` |
| `REQ-DAT-025` Clause 4 (Workout Cluster Manager & Repo) | `testWorkoutCluster_fromCursor_*`, `testWorkoutClusterRepository_fromCursorBatchKeys` | `WorkoutClusterDatabaseManager.kt`, `WorkoutClusterRepository.kt` |
| `REQ-DAT-025` Clause 5 (Domain Invariants & Fallbacks) | `testMapCursorToRouteWaypoint_invalidType_fallsBackToGeneric`, `testMapCursorToRouteWaypoint_nullElevation_defaultsToZero` | `RoutesDatabaseManager.kt` |
