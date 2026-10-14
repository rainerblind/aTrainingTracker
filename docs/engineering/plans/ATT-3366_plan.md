# Stage 3: Implementation Plan - ATT-3366: Eliminate manual cursor column extractions in repositories using fromCursor mappers

**Ticket**: [ATT-3366](https://rainerblind.atlassian.net/browse/ATT-3366)  
**Sub-task**: [ATT-3570](https://rainerblind.atlassian.net/browse/ATT-3570) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.3`  
**Requirement Mapping**: `REQ-DAT-025` (*Authoritative Database Cursor Row and Batch Mappers for Route and Cluster Repositories*)  
**Test Spec Mapping**: `TST-DAT-020`  
**Branch**: `feature/ATT-3366`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Architectural Design & SWE.2 Specification

### 1.1 Architectural Motivation & Single Source of Truth (Rule 20 / Rule 36)

Under **Rule 20 (Database & DTO Mapping Symmetry)** and **Rule 36 (Single Source of Truth & DRY — Shared Code Over Duplication)**, all database cursor reading must route through symmetric, authoritative mapper methods on the database manager or entity companion (e.g. `fromCursor(cursor: Cursor)` and `fromCursorBatch(cursor: Cursor): List<T>`).

Currently, raw manual cursor extractions are performed inline in:
1. `WorkoutClusterRepository.kt:408–418`: Repeated `cursor.getColumnIndexOrThrow(...)` inside a `do-while` loop for `C_ID`, `FILE_BASE_NAME`, and `CLUSTER_ID`.
2. `RoutesDatabaseManager.kt:295–324`: Manual column index lookups (9 columns) and inline instantiation of `RouteWaypoint` inside `getWaypointsForRoute()`.
3. `RoutesDatabaseManager.kt:585–601`: Manual column index lookups (4 columns) and inline instantiation of `PathPoint` inside `getRoutePath()`.
4. `WorkoutClusterDatabaseManager.kt:269–308`: `mapCursorToCluster(cursor)` is a private helper, lacking standardized `fromCursor` and `fromCursorBatch` companion/manager APIs.

### 1.2 Target Architectural Structure

```
                             ┌─────────────────────────────────────────────────────────────┐
                             │                    RoutesDatabaseManager                    │
                             ├─────────────────────────────────────────────────────────────┤
                             │ - WaypointIndices(cursor: Cursor)                           │
                             │ - PathPointIndices(cursor: Cursor)                          │
                             │ - mapCursorToRouteWaypoint(cursor, indices?): RouteWaypoint │
                             │ - mapCursorToRouteWaypoints(cursor): List<RouteWaypoint>    │
                             │ - mapCursorToPathPoint(cursor, indices?): PathPoint         │
                             │ - mapCursorToPathPoints(cursor): List<PathPoint>            │
                             │ - mapCursorToRouteSummary(cursor): RouteSummary             │
                             │ - mapCursorToRouteSummaries(cursor): List<RouteSummary>     │
                             └───────────────┬─────────────────────────────┬───────────────┘
                                             │                             │
                                             ▼                             ▼
                              getWaypointsForRoute(id)              getRoutePath(id)
                                             │                             │
                                             ▼                             ▼
                              List<RouteWaypoint>                  List<PathPoint>

────────────────────────────────────────────────────────────────────────────────────────────────────────

 ┌──────────────────────────────────────────────┐       ┌──────────────────────────────────────────────┐
 │        WorkoutClusterDatabaseManager         │       │          WorkoutClusterRepository            │
 ├──────────────────────────────────────────────┤       ├──────────────────────────────────────────────┤
 │ - fromCursor(cursor): WorkoutCluster         │       │ - fromCursorBatchKeys(cursor):               │
 │ - fromCursorBatch(cursor):                   │       │     WorkoutBatchKeys (id, file, clusterId)   │
 │     List<WorkoutCluster>                     │       │ - WorkoutBatchKey.fromCursor(cursor)         │
 └──────────────────────┬───────────────────────┘       └──────────────────────┬───────────────────────┘
                        │ delegates internal queries                           │
                        ▼                                                      ▼
           getAllClusters(), getClusterById()              loadWorkoutsWithBatchMetadata()
```

---

## 2. Cursor Lifecycle & Index Caching Contracts

### 2.1 Cursor Lifecycle Ownership & Positioning Contract
1. **Row-Level Mappers (`mapCursorToRouteWaypoint`, `mapCursorToPathPoint`, `fromCursor`)**:
   - **Pre-Condition**: The cursor **MUST** already be positioned at the target row before invocation (`cursor.moveToFirst()` or during `while (cursor.moveToNext())`).
   - **Post-Condition**: The mapper extracts values strictly from the current row. It **MUST NOT** advance the cursor (`moveToNext`), reset the cursor (`moveToFirst`), or close the cursor (`close`).
   - **Ownership**: Cursor lifecycle (opening via `db.query(...)` and closing via `.use { ... }`) remains strictly the caller's responsibility.
2. **Batch Mappers (`mapCursorToRouteWaypoints`, `mapCursorToPathPoints`, `fromCursorBatch`)**:
   - **Pre-Condition**: An open cursor is passed to the batch method.
   - **Execution**: The batch mapper moves the cursor from `moveToFirst()` through `while (cursor.moveToNext())`, collecting mapped entities into an immutable `List<T>`.
   - **Ownership**: The batch helper does **NOT** close the cursor. Calling methods retain lifecycle control within their `.use { ... }` blocks.

### 2.2 High-Performance Index Caching Architecture
- Column indices are encapsulated in lightweight immutable index holder classes:
  ```kotlin
  internal class WaypointIndices(cursor: Cursor) {
      val idIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_WAYPOINT_ID)
      val routeIdIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_WAYPOINT_ROUTE_ID_FK)
      val nameIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_WAYPOINT_NAME)
      val descIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_WAYPOINT_DESCRIPTION)
      val latIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_WAYPOINT_LAT)
      val lngIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_WAYPOINT_LNG)
      val elevIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_WAYPOINT_ELEVATION)
      val distIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_WAYPOINT_DIST_FROM_START)
      val typeIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_WAYPOINT_TYPE)
  }

  internal class PathPointIndices(cursor: Cursor) {
      val latIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_LAT)
      val lngIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_LNG)
      val distIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_DIST_FROM_START)
      val altIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_ALTITUDE)
  }
  ```
- **Two-Tier Overload Pattern**:
  - `fun mapCursorToRouteWaypoint(cursor: Cursor): RouteWaypoint` resolves `WaypointIndices(cursor)` and delegates to indexed mapper (convenience for single-row lookups).
  - `internal fun mapCursorToRouteWaypoint(cursor: Cursor, indices: WaypointIndices): RouteWaypoint` reads directly from pre-resolved integer indices.
  - `fun mapCursorToRouteWaypoints(cursor: Cursor): List<RouteWaypoint>` instantiates `WaypointIndices(cursor)` **once** before the loop, calling `mapCursorToRouteWaypoint(cursor, indices)` on each row.
  - Reduces JNI string lookups from $O(N \times K)$ to $O(K)$.

### 2.3 Zero-Allocation Batch Key Extraction (`WorkoutClusterRepository.kt`)
- In `loadWorkoutsWithBatchMetadata()`, allocating intermediate DTO objects per row across hundreds of workouts creates avoidable garbage collection pressure.
- We introduce `WorkoutBatchKeys`:
  ```kotlin
  internal data class WorkoutBatchKeys(
      val idList: List<Long>,
      val fileNameList: List<String>,
      val clusterIdList: Set<Long>
  )
  ```
- And companion/static method `fromCursorBatchKeys(cursor: Cursor): WorkoutBatchKeys`:
  - Resolves `idIdx`, `fileIdx`, and `clusterIdx` **once** before iteration.
  - Appends directly into primitive/string collections (`ArrayList<Long>`, `ArrayList<String>`, `HashSet<Long>`).
  - Returns `WorkoutBatchKeys(idList, fileNameList, clusterIdList)`.
- We also provide `WorkoutBatchKey(val id: Long, val fileName: String?, val clusterId: Long)` with `fromCursor(cursor: Cursor)` for single-row testing and mapping symmetry per Rule 20.

---

## 3. UI Consistency (Rule 23)

- **Not Applicable**: This ticket is strictly a database manager, repository, and DTO persistence refactoring under Rule 20 and Rule 36. Zero UI or Jetpack Compose files are added or modified.

---

## 4. Invariants & Safety Measures

1. **Fallback Integrity**:
   - `WaypointType` fallbacks (defaulting unparseable strings to `GENERIC` via `try/catch`) MUST be preserved verbatim.
   - `altitude` defaulting to `0.0` when elevation column is null MUST be preserved verbatim.
2. **Schema Invariance**:
   - Zero changes to SQLite database tables or schema versions in `Routes.db` or `WorkoutCluster.db`.
3. **Cursor Positioning & Lifecycle Invariance**:
   - Single-row mappers do not advance or close the cursor.
   - Batch mappers leave cursor closing to the caller's `.use { ... }` block.
4. **Regression Safety**:
   - All existing tests in `RoutesDatabaseManagerWaypointTest.kt`, `RoutesDatabaseManagerDeduplicationTest.kt`, `RoutesDatabaseManagerTTLTest.kt`, `WorkoutClusterRepositoryTest.kt`, and `WorkoutClusterStatsTest.kt` MUST pass with 100% fidelity.

---

## 5. Atomic Implementation Step Sequencing

### Step 1: Author Unit & Contract Tests
- Create `RoutesDatabaseManagerMapperTest.kt` verifying:
  - `mapCursorToRouteWaypoint` with valid, null elevation, and corrupted `type` strings.
  - `mapCursorToPathPoint` extracting coordinates, distance, and altitude.
  - `mapCursorToRouteSummary` extracting all route summary properties.
  - Batch mappers (`mapCursorToRouteWaypoints`, `mapCursorToPathPoints`, `mapCursorToRouteSummaries`) matching row-by-row mapping.
  - Cursor position and open status invariance after row mapper execution.
- Create `WorkoutClusterMapperTest.kt` verifying:
  - `WorkoutClusterDatabaseManager.fromCursor` and `fromCursorBatch`.
  - `WorkoutClusterRepository.fromCursorBatchKeys` extracting IDs, filenames, and cluster IDs with pre-cached indices.
  - `WorkoutBatchKey.fromCursor` single-row mapping symmetry.
- *Verification Command*: `./gradlew testDebugUnitTest --tests "*RoutesDatabaseManagerMapperTest*" --tests "*WorkoutClusterMapperTest*"` (assert failure on unbuilt methods).

### Step 2: Implement Mappers in `RoutesDatabaseManager.kt`
- Define `internal class WaypointIndices(cursor: Cursor)` and `internal class PathPointIndices(cursor: Cursor)`.
- Implement `mapCursorToRouteWaypoint(cursor: Cursor)` and `mapCursorToRouteWaypoint(cursor: Cursor, indices: WaypointIndices)`.
- Implement `mapCursorToRouteWaypoints(cursor: Cursor): List<RouteWaypoint>`.
- Implement `mapCursorToPathPoint(cursor: Cursor)` and `mapCursorToPathPoint(cursor: Cursor, indices: PathPointIndices)`.
- Implement `mapCursorToPathPoints(cursor: Cursor): List<PathPoint>`.
- Promote `mapCursorToRouteSummary(cursor: Cursor)` to `internal` and add `mapCursorToRouteSummaries(cursor: Cursor): List<RouteSummary>`.
- Refactor `getWaypointsForRoute(routeId: Long)` to delegate to `mapCursorToRouteWaypoints(cursor)`.
- Refactor `getRoutePath(routeId: Long)` to delegate to `mapCursorToPathPoints(cursor)`.
- Refactor `getRoutes()`, `getRoute()`, and `getRouteByClusterId()` to delegate to `mapCursorToRouteSummaries(cursor)`.
- *Verification Command*: `./gradlew testDebugUnitTest --tests "*RoutesDatabaseManager*"`

### Step 3: Implement Mappers in `WorkoutClusterDatabaseManager.kt`
- Expose `fun fromCursor(cursor: Cursor): WorkoutCluster` (and `WorkoutCluster.Companion.fromCursor(cursor: Cursor)`).
- Implement `fun fromCursorBatch(cursor: Cursor): List<WorkoutCluster>`.
- Route `getAllClusters()`, `findCandidateClusters()`, `getClusterById()`, and `getClusterByName()` through `fromCursorBatch` / `fromCursor`.
- *Verification Command*: `./gradlew testDebugUnitTest --tests "*WorkoutCluster*"`

### Step 4: Implement Batch Keys Extraction in `WorkoutClusterRepository.kt`
- Define `internal data class WorkoutBatchKeys(val idList: List<Long>, val fileNameList: List<String>, val clusterIdList: Set<Long>)`.
- Define `internal data class WorkoutBatchKey(val id: Long, val fileName: String?, val clusterId: Long)` with companion `fromCursor(cursor: Cursor)`.
- Implement `internal fun fromCursorBatchKeys(cursor: Cursor): WorkoutBatchKeys`.
- In `loadWorkoutsWithBatchMetadata()`, replace lines 404–418 with `val batchKeys = fromCursorBatchKeys(cursor)`.
- *Verification Command*: `./gradlew testDebugUnitTest --tests "*WorkoutClusterRepositoryTest*"`

### Step 5: Full Clean-Room Regression Verification
- Run full unit test suite: `./gradlew testDebugUnitTest`.
- Verify 100% pass rate with zero regressions.
