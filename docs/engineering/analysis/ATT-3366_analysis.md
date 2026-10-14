# Stage 1 Analysis: ATT-3366 - Eliminate manual cursor column extractions in repositories using fromCursor mappers

**Ticket**: [ATT-3366](https://rainerblind.atlassian.net/browse/ATT-3366)  
**Sub-task**: [ATT-3568](https://rainerblind.atlassian.net/browse/ATT-3568) (`[Analysis]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.3`  
**Branch**: `feature/ATT-3366`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Problem Statement & Motivation

Under **Rule 20 (Database & DTO Mapping Symmetry)** and **Rule 36 (Single Source of Truth & DRY — Shared Code Over Duplication)**, all database cursor reading must route through symmetric, authoritative mapper methods on the database manager or entity companion (e.g. `fromCursor(cursor: Cursor)` and `fromCursorBatch(cursor: Cursor): List<T>`).

However, in several repositories, raw manual cursor extractions were implemented inline:
1. **`WorkoutClusterRepository.kt:408–418`**: Inside `loadWorkoutsWithBatchMetadata()`, manual column index lookups (`cursor.getColumnIndexOrThrow(...)` for `C_ID`, `FILE_BASE_NAME`, and `CLUSTER_ID`) are executed repeatedly per row within a `do { ... } while (cursor.moveToNext())` loop rather than delegating to an authoritative DTO/batch-key helper or caching column indices.
2. **`RoutesDatabaseManager.kt:295–324`**: In `getWaypointsForRoute()`, manual column index resolution (9 columns: `COLUMN_WAYPOINT_ID`, `COLUMN_WAYPOINT_ROUTE_ID_FK`, `COLUMN_WAYPOINT_NAME`, `COLUMN_WAYPOINT_DESCRIPTION`, `COLUMN_WAYPOINT_LAT`, `COLUMN_WAYPOINT_LNG`, `COLUMN_WAYPOINT_ELEVATION`, `COLUMN_WAYPOINT_DIST_FROM_START`, `COLUMN_WAYPOINT_TYPE`) and instantiation of `RouteWaypoint` are implemented inline inside the query method.
3. **`RoutesDatabaseManager.kt:585–601`**: In `getRoutePath()`, manual column index resolution (4 columns: `COLUMN_LAT`, `COLUMN_LNG`, `COLUMN_DIST_FROM_START`, `COLUMN_ALTITUDE`) and instantiation of `PathPoint` are implemented inline inside the query method.
4. **`WorkoutClusterDatabaseManager.kt:269–308`**: Although `mapCursorToCluster(cursor: Cursor): WorkoutCluster` exists, it is an unexposed `private` helper and lacks standard companion `fromCursor(cursor: Cursor)` / `fromCursorBatch(cursor: Cursor)` mapper APIs.

### Deficiencies & Violations:
- **Rule 20 Violation (Database & DTO Mapping Symmetry)**: Entity reconstruction from cursors is fragmented across arbitrary inline query loops rather than centralized in canonical mapper contracts.
- **Rule 36 Violation (Single Source of Truth & DRY)**: Schema adjustments (e.g., column additions or alterations) require locating and updating disparate cursor reading blocks across repository and manager methods.
- **Performance Inefficiency**: Repeatedly resolving column names via `cursor.getColumnIndexOrThrow()` inside high-frequency row loops introduces unnecessary string hashing and JNI overhead during database iteration.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Commit & History Archaeology
1. **`WorkoutClusterRepository.kt`**:
   - `loadWorkoutsWithBatchMetadata` was introduced in Sprint 2026-40.12 under `ATT-359` / `ATT-388` to optimize workout loading.
   - Step 1 gathers IDs, filenames, and cluster IDs for bulk lookups. Because this was treated as a temporary pre-fetch accumulator rather than a formal entity, the author wrote inline `cursor.getColumnIndexOrThrow` calls directly inside the `do-while` loop.
2. **`RoutesDatabaseManager.kt`**:
   - `getWaypointsForRoute` and `getRoutePath` were created during the initial route storage implementation (`ATT-1391` / `ATT-1413`).
   - While `RouteSummary` received a dedicated `mapCursorToRouteSummary(cursor: Cursor)` helper, `RouteWaypoint` and `PathPoint` were mapped inline within their respective query methods.
3. **`WorkoutClusterDatabaseManager.kt`**:
   - `mapCursorToCluster` was written as a `private` instance method when `WorkoutClusterDatabaseManager` was first created.
   - It was never exposed on `WorkoutCluster.Companion` or as a reusable static/companion `fromCursor` function, preventing other layers from reusing it or testing it independently.

### 2.2 Forensic Inspection of Touchpoints

#### Touchpoint 1: `WorkoutClusterRepository.kt` (lines 404–418)
```kotlin
// 1. Gather IDs, FileNames, and Cluster IDs for the batch
val idList = mutableListOf<Long>()
val fileNameList = mutableListOf<String>()
val clusterIdList = mutableSetOf<Long>()
do {
    idList.add(cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID)))
    cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME))?.let {
        fileNameList.add(it)
    }
    val clusterId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID))
    if (clusterId != -1L) {
        clusterIdList.add(clusterId)
    }
} while (cursor.moveToNext())
```
*Issue*: Repeated `getColumnIndexOrThrow` calls on every row iteration. No DTO or mapper encapsulation.

#### Touchpoint 2: `RoutesDatabaseManager.kt` (lines 295–324)
```kotlin
val idIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_WAYPOINT_ID)
val routeIdIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_WAYPOINT_ROUTE_ID_FK)
val nameIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_WAYPOINT_NAME)
val descIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_WAYPOINT_DESCRIPTION)
val latIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_WAYPOINT_LAT)
val lngIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_WAYPOINT_LNG)
val elevIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_WAYPOINT_ELEVATION)
val distIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_WAYPOINT_DIST_FROM_START)
val typeIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_WAYPOINT_TYPE)

while (cursor.moveToNext()) {
    val typeStr = cursor.getString(typeIdx)
    val type = try {
        WaypointType.valueOf(typeStr)
    } catch (e: Exception) {
        WaypointType.GENERIC
    }
    waypoints.add(
        RouteWaypoint(
            id = cursor.getLong(idIdx),
            routeId = cursor.getLong(routeIdIdx),
            latLng = LatLng(cursor.getDouble(latIdx), cursor.getDouble(lngIdx)),
            altitude = if (cursor.isNull(elevIdx)) 0.0 else cursor.getDouble(elevIdx),
            name = cursor.getString(nameIdx) ?: "",
            description = cursor.getString(descIdx) ?: "",
            type = type,
            distanceFromStart = cursor.getDouble(distIdx)
        )
    )
}
```
*Issue*: Waypoint mapping logic embedded directly in `getWaypointsForRoute`, not reusable and not symmetric with `mapCursorToRouteSummary`.

#### Touchpoint 3: `RoutesDatabaseManager.kt` (lines 585–601)
```kotlin
val latIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_LAT)
val lngIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_LNG)
val distIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_DIST_FROM_START)
val altIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_ALTITUDE)

while (cursor.moveToNext()) {
    pathPoints.add(
        PathPoint(
            latLng = com.google.android.gms.maps.model.LatLng(
                cursor.getDouble(latIdx),
                cursor.getDouble(lngIdx)
            ),
            distance = cursor.getDouble(distIdx),
            altitude = cursor.getDouble(altIdx)
        )
    )
}
```
*Issue*: PathPoint extraction is duplicated inline in `getRoutePath`.

---

## 3. Target Architectural Design

### 3.1 Mapper Contracts & Cursor Lifecycle Ownership

To guarantee thread safety, zero memory leaks, and seamless interop across existing repository code, we establish a strict **Cursor Lifecycle & Positioning Contract**:

1. **Row-Level Mapper Contract (`fromCursor` / `mapCursorTo...`)**:
   - **Pre-Condition**: The cursor **MUST** already be positioned at the desired target row before calling `fromCursor(cursor)`.
   - **Post-Condition**: The mapper extracts the entity values from the *current* row only. It **MUST NOT** advance the cursor (`moveToNext()`), reset the cursor (`moveToFirst()`), or close the cursor (`close()`).
   - **Ownership**: Cursor lifecycle (opening and closing via `use { ... }`) remains strictly the responsibility of the calling repository/manager query method.
2. **Batch Mapper Contract (`fromCursorBatch`)**:
   - **Pre-Condition**: The cursor is passed open to `fromCursorBatch(cursor)`.
   - **Execution**: The batch mapper moves the cursor from `moveToFirst()` through `while (cursor.moveToNext())`, collecting each mapped row into an immutable `List<T>`.
   - **Ownership**: The batch helper does **NOT** close the cursor; closing remains guarded by the caller's `.use { ... }` block to avoid premature closing when additional operations are performed.

---

### 3.2 High-Performance Index Caching Architecture

To resolve the JNI overhead of `getColumnIndexOrThrow` in high-frequency loops without signature bloat:

1. **Index Cache Objects**:
   - Encapsulate column indices in immutable lightweight holders:
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
2. **Two-Tier Overload Pattern**:
   - **Public / Standard API**: `fromCursor(cursor: Cursor): T` initializes the index cache and extracts the row. Ideal for single-row lookups.
   - **Internal High-Performance Overload**: `fromCursor(cursor: Cursor, indices: Indices): T` extracts using the pre-computed index cache.
   - **Batch API**: `fromCursorBatch(cursor: Cursor): List<T>` instantiates `Indices(cursor)` **once** before iteration, then calls `fromCursor(cursor, indices)` in the loop. This reduces $O(N \times K)$ JNI lookups to $O(K)$ lookups where $K$ is the number of columns.

---

### 3.3 Zero-Allocation Batch Key Extraction for `WorkoutClusterRepository`

Inside `loadWorkoutsWithBatchMetadata()` in `WorkoutClusterRepository.kt`:
1. **The Allocation Hazard**: Creating intermediate `WorkoutBatchKey` wrapper objects per row in large workout databases (hundreds of rows) induces garbage collection pressure.
2. **Concrete Solution (`WorkoutBatchKeys`)**:
   - Provide an encapsulated DTO containing the final aggregated collections:
     ```kotlin
     internal data class WorkoutBatchKeys(
         val idList: List<Long>,
         val fileNameList: List<String>,
         val clusterIdList: Set<Long>
     )
     ```
   - Provide `fromCursorBatchKeys(cursor: Cursor): WorkoutBatchKeys` which:
     - Resolves column indices (`C_ID`, `FILE_BASE_NAME`, `CLUSTER_ID`) **exactly once** before looping.
     - Iterates the cursor and appends directly into primitive/string collections:
       - `idList: ArrayList<Long>`
       - `fileNameList: ArrayList<String>`
       - `clusterIdList: HashSet<Long>`
     - Returns `WorkoutBatchKeys(idList, fileNameList, clusterIdList)`.
   - Also provide `WorkoutBatchKey.fromCursor(cursor: Cursor)` for single-row testability and symmetry per Rule 20.
3. This eliminates all duplicate `getColumnIndexOrThrow` calls per row, guarantees zero intermediate DTO allocation overhead in the batch loop, and encapsulates the logic outside the repository loop body.

---

### 3.4 Consolidation of Touchpoints

#### 1. `RoutesDatabaseManager.kt`:
- **Waypoint Mapping**:
  - `fun mapCursorToRouteWaypoint(cursor: Cursor): RouteWaypoint`
  - `internal fun mapCursorToRouteWaypoint(cursor: Cursor, indices: WaypointIndices): RouteWaypoint`
  - `fun mapCursorToRouteWaypoints(cursor: Cursor): List<RouteWaypoint>`
  - `getWaypointsForRoute()` simplified to query and delegate directly to `mapCursorToRouteWaypoints(cursor)`.
- **PathPoint Mapping**:
  - `fun mapCursorToPathPoint(cursor: Cursor): PathPoint`
  - `internal fun mapCursorToPathPoint(cursor: Cursor, indices: PathPointIndices): PathPoint`
  - `fun mapCursorToPathPoints(cursor: Cursor): List<PathPoint>`
  - `getRoutePath()` simplified to query and delegate directly to `mapCursorToPathPoints(cursor)`.
- **RouteSummary Mapping**:
  - Promote `mapCursorToRouteSummary(cursor: Cursor)` from `private` to `internal`.
  - Add `mapCursorToRouteSummaries(cursor: Cursor): List<RouteSummary>`.

#### 2. `WorkoutClusterDatabaseManager.kt`:
- Symmetrically expose `fromCursor(cursor: Cursor): WorkoutCluster` and `fromCursorBatch(cursor: Cursor): List<WorkoutCluster>` (and `WorkoutCluster.Companion.fromCursor(cursor: Cursor)`).
- Update internal queries (`getAllClusters()`, `findCandidateClusters()`, `getClusterById()`, `getClusterByName()`) to use `fromCursorBatch(cursor)` or `fromCursor(cursor)`.

---

## 4. Requirement Archaeology & Chesterton's Fence

1. **Original Requirement ID & Target**:
   - Net-new requirement: `REQ-DAT-025` (*Authoritative Database Cursor Row and Batch Mappers for Route and Cluster Repositories*).
   - Complements:
     - `REQ-DAT-021` (Workout Origin Source Batch Mapping Symmetry)
     - `REQ-SET-062` / `REQ-SET-068` (Workout Cluster Persistence and Statistics)
     - `REQ-MAP-026` (Route Waypoints and Path Points)
2. **Historical Origin & Commit Trace**:
   - `RoutesDatabaseManager.kt` introduced in Sprint 2026-41.2.
   - `WorkoutClusterRepository.kt` batch querying introduced in Sprint 2026-40.12.
3. **Root Reason for Existing Formulation**:
   - Methods were implemented pragmatically to satisfy specific query needs without factoring out shared row mappers. Over time, inline parsing became technical debt violating Rule 20 and Rule 36.
4. **Preservation of Core Invariants**:
   - **Waypoint Fallbacks**: `try { WaypointType.valueOf(typeStr) } catch (_: Exception) { WaypointType.GENERIC }` MUST be preserved verbatim.
   - **Elevation Nullability**: `if (cursor.isNull(elevIdx)) 0.0 else cursor.getDouble(elevIdx)` MUST be preserved.
   - **Cursor Lifecycle**: Cursor opening and closing (`use { ... }`) remains strictly with caller query methods.
   - **Positioning Invariant**: Mappers expect cursor at the target row and do not advance it; batch helpers iterate from `moveToFirst()` through `while (moveToNext())` cleanly.
   - **Schema Stability**: Zero changes to `Routes.db` or `WorkoutCluster.db` schemas.

---

## 5. Scope Bounding (In-Scope vs Out-of-Scope)

### In-Scope:
- Consolidating cursor-to-entity mapping for `RouteWaypoint`, `PathPoint`, and `RouteSummary` in `RoutesDatabaseManager.kt` with index caching and batch overloads.
- Consolidating `WorkoutCluster` cursor mapping in `WorkoutClusterDatabaseManager.kt` with `fromCursor` and `fromCursorBatch`.
- Consolidating batch metadata key extraction and single-pass index caching in `WorkoutClusterRepository.kt`.
- Comprehensive unit and contract tests protecting cursor mapping symmetry, index handling, and fallback behavior.

### Out-of-Scope:
- Modifying SQLite schema or table contracts in `RoutesDatabaseManager` or `WorkoutClusterDatabaseManager`.
- Modifying `WorkoutDataMapper.kt` (already adheres to Rule 20 via `fromCursor` and `fromCursor(cursor, batch)`).
- Modifying UI components or view models consuming these entities.

---

## 6. Verification & Test Strategy
1. **Targeted Unit Tests**:
   - `RoutesDatabaseManagerWaypointTest.kt`: Verify waypoint extraction fidelity and default fallbacks.
   - `RoutesDatabaseManagerDeduplicationTest.kt` / `RoutesDatabaseManagerTTLTest.kt`: Verify route summary and path mapping.
   - `WorkoutClusterRepositoryTest.kt` / `WorkoutClusterStatsTest.kt`: Verify cluster entity mapping and batch metadata loading.
   - Net-new mapper contract tests validating `fromCursor` and `fromCursorBatch` parity across single and multi-row cursors, index caching correctness, and fallback safety.
2. **Full Regression Suite**:
   - Clean-room `./gradlew testDebugUnitTest` ensuring zero regressions across all 2,680+ test cases.
