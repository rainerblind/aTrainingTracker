# Implementation Plan: Enforce 7-Day TTL Cache Retention and Orphan Pruning for Strava Routes and Segments (ATT-1177)

## 1. Traceability & Scope Reference
* **Ticket Key**: `ATT-1177`
* **Sub-Task Key**: `ATT-1182` (`[Impl-Plan]`)
* **Target Version**: `V4.9.37`
* **Target Branch**: `feature/ATT-1177`
* **Requirement**: `REQ-EXT-010`
* **Test Case**: `TST-EXT-007`

---

## 2. Architecture & Design Decisions

### 2.1 Database Schema v8 Migrations
1. **`Routes.db` (`RoutesDatabaseManager.kt`)**:
   * Increment `DB_VERSION` from 7 to 8.
   * Add `COLUMN_SYNCED_AT = "synced_at"` (`INTEGER DEFAULT 0`) to `RouteContract`.
   * Update `CREATE_TABLE_ROUTES` to include `$COLUMN_SYNCED_AT INTEGER DEFAULT 0`.
   * In `onUpgrade`, if `oldVersion < 8`: execute `ALTER TABLE Routes ADD COLUMN synced_at INTEGER DEFAULT 0;`.
   * Update `RouteSummary` model with `val syncedAt: Long = 0L`.
   * Update `mapCursorToRouteSummary` to parse `synced_at`.
   * In `insertRoute`, write `syncedAt` (defaulting to current system time if not specified or `0L`).
   * Add `getRouteById(routeId: Long): RouteWithPath?`.
   * Add `updateRouteSyncedAt(routeId: Long, timestamp: Long): Int`.
   * Add `pruneExpiredStravaRoutes(maxAgeMs: Long = 7 * 24 * 60 * 60 * 1000L): Int`.
   * Add `pruneOrphanStravaRoutes(activeStravaIds: Set<String>): Int`.
   * Add `duplicateRouteAsLocal(routeId: Long): Long` creating an independent copy with `RouteSource.LOCAL_GPX` and `externalId = ""`.

2. **`Segments.db` (`SegmentsDatabaseManager.java`)**:
   * Increment `DB_VERSION` from 7 to 8.
   * Add `public static final String SYNCED_AT = "synced_at";` to `Segments`.
   * Update `CREATE_TABLE_STARRED_SEGMENTS` to include `+ Segments.SYNCED_AT + " integer default 0)"`.
   * In `onUpgrade`, if `oldVersion < 8`: execute `ALTER TABLE StarredSegmentsTable ADD COLUMN synced_at integer default 0;`.
   * In `addOrUpdateSegment(StravaSegment segment)`: set `cv.put(Segments.SYNCED_AT, System.currentTimeMillis());`.
   * Add `public int pruneExpiredSegments(long maxAgeMs)`.
   * Add `public int pruneOrphanSegments(Set<Long> activeStravaIds)`.

### 2.2 Repository Synchronization Logic
1. **`RoutesRepository.kt`**:
   * In `init`: invoke `routesDb.pruneExpiredStravaRoutes()`.
   * In `syncRoutesFromStrava()`:
     - Invoke `routesDb.pruneExpiredStravaRoutes()`.
     - Extract `activeExtIds = stravaRoutes.map { it.idStr }.toSet()`.
     - For each route in `stravaRoutes`: if already present in local DB, update `synced_at` to `System.currentTimeMillis()`. If not present, fetch streams and insert with current timestamp.
     - Invoke `routesDb.pruneOrphanStravaRoutes(activeExtIds)`.
     - Call `refreshRoutes()` to update UI StateFlow.
   * Expose `suspend fun duplicateRouteAsLocal(routeId: Long): Long`.

2. **`SegmentsRepository.kt`**:
   * In `init`: invoke `segmentsDb.pruneExpiredSegments(7 * 24 * 60 * 60 * 1000L)`.
   * In `syncStarredSegments()`: invoke `segmentsDb.pruneExpiredSegments(7 * 24 * 60 * 60 * 1000L)`.
   * In `syncStarredSegmentsWorker()`:
     - Retain `newIds` during pagination.
     - Orphan cleanup: `toDelete = oldIds - newIds`, delete each via `deleteSegment` (which cascade-prunes `TABLE_STARRED_SEGMENTS` and `TABLE_SEGMENT_STREAMS`).

3. **Background Sync Workers**:
   * `StravaRoutesSyncWorker.kt`:
     - In `doWork()`, invoke `routesDb.pruneExpiredStravaRoutes()` so offline/disconnected states prune expired cached Strava routes past 7 days.
   * `StravaSegmentsSyncWorker.kt`:
     - In `doWork()`, invoke `segmentsDb.pruneExpiredSegments(7 * 24 * 60 * 60 * 1000L)`.

### 2.3 UI Integration: Athlete Route Preservation
1. **`RouteItem.kt`**:
   * In contextual dropdown menu: when `summary.source == RouteSource.STRAVA`, show action `Save as Local Route` (`R.string.save_as_local_route`).
   * Pass click event to `onDuplicateAsLocal(summary.id)`.
2. **`RouteList.kt`, `RouteTabbedScreen.kt`, `RoutesFragment.kt`**:
   * Thread `onDuplicateAsLocal` callback up to `RoutesViewModel`.
3. **`RoutesViewModel.kt`**:
   * Implement `fun duplicateRouteAsLocal(routeId: Long)` which calls `routesRepository.duplicateRouteAsLocal(routeId)`.
4. **Strings**:
   * Localize `save_as_local_route` ("Save as Local Route" / "Als lokale Route speichern") across languages.

---

## 3. Invariant Protection Matrix
* **Native Workouts**: `WorkoutSummaries.db`, `WorkoutSamples.db`, and `Laps.db` are never touched by TTL or orphan pruning.
* **Local Routes**: Routes with `source == RouteSource.LOCAL_GPX` or `source == RouteSource.WORKOUT` are never deleted by TTL or orphan routines.
* **Hardware Sensors & Equipment**: `Equipment.db` and ANT+/BLE sensor links remain 100% intact.
* **Clustering**: `WorkoutCluster.db` clusters generated from past workouts remain intact even if a referenced Strava route is pruned.

---

## 4. Test Implementation Plan
1. **`RoutesDatabaseManagerTTLTest.kt`**:
   - `testSchemaV8Migration`: Verifies `synced_at` column is present and defaults to 0.
   - `testPruneExpiredStravaRoutes`: Verifies routes older than 7 days are deleted, routes within 7 days are kept, and `LOCAL_GPX` routes are never deleted.
   - `testPruneOrphanStravaRoutes`: Verifies routes missing from remote active ID set are pruned.
   - `testDuplicateRouteAsLocal`: Verifies route duplication creates independent `LOCAL_GPX` route that survives subsequent TTL pruning.
   - `testSystemInvariantsUntouched`: Asserts workout summaries, samples, laps, and equipment are unmodified.
2. **`SegmentsDatabaseManagerTTLTest.kt`**:
   - `testSchemaV8Migration`: Verifies `synced_at` column added.
   - `testPruneExpiredSegments`: Verifies expired segments (> 7 days) and their stream points are deleted.
   - `testPruneOrphanSegments`: Verifies orphan segments missing from remote ID set are pruned.
3. **Clean-Room Verification**:
   - Run `./gradlew testDebugUnitTest` across the whole project.
