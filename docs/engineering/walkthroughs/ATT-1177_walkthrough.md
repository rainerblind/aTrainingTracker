# Walkthrough: ATT-1177 Enforce 7-day TTL cache retention and orphan pruning for Strava routes and segments

## Overview
- **Parent Ticket**: ATT-1177 (`[Verbesserung] Enforce 7-day TTL cache retention and orphan pruning for Strava routes and segments`)
- **Implementation Sub-task**: ATT-1183 (`[Subtask] [Implementation] Enforce 7-day TTL cache retention and orphan pruning for Strava routes and segments`)
- **Testing Sub-task**: ATT-1184 (`[Subtask] [Test] Enforce 7-day TTL cache retention and orphan pruning for Strava routes and segments`)
- **Target Version**: V4.9.37
- **Fulfills**: REQ-EXT-010, TST-EXT-007

---

## Key Changes Made

### 1. Database Schema Version 8 Upgrades & Migrations
- **`app/src/main/java/com/atrainingtracker/trainingtracker/database/RoutesDatabaseManager.kt`**:
  - Upgraded schema version from 7 to 8 (`DB_VERSION = 8`).
  - Added `synced_at INTEGER DEFAULT 0` column to `TABLE_ROUTES`.
  - Added SQLite `ALTER TABLE routes ADD COLUMN synced_at INTEGER DEFAULT 0;` migration in `onUpgrade(db, oldVersion, newVersion)` for `oldVersion < 8`.
  - Updated `RouteSummary` model and cursor mapping (`mapCursorToRouteSummary`) to include `syncedAt`.
  - Updated `insertRoute` to accept and persist `syncedAt` (defaulting to `System.currentTimeMillis()`).
  - Implemented `getRouteById(routeId: Long): RouteWithPath?`.
  - Implemented `updateRouteSyncedAt(routeId: Long, timestamp: Long): Int` to refresh retention timestamps on sync without re-inserting path coordinates.
  - Implemented `pruneExpiredStravaRoutes(maxAgeMs: Long): Int` which queries and deletes routes with `source = 'STRAVA'` where `synced_at > 0 AND synced_at < cutoff` (default 7 days).
  - Implemented `pruneOrphanStravaRoutes(activeStravaIds: Set<String>): Int` which removes cached Strava routes no longer present in the athlete's remote Strava route list.
  - Implemented `duplicateRouteAsLocal(routeId: Long): Long` which clones a Strava route as `RouteSource.LOCAL_GPX` with `synced_at = 0`, permanently isolating it from Strava TTL cache eviction.

- **`app/src/main/java/com/atrainingtracker/trainingtracker/segments/SegmentsDatabaseManager.java`**:
  - Upgraded schema version from 7 to 8 (`DATABASE_VERSION = 8`).
  - Added `SYNCED_AT = "synced_at"` (`INTEGER DEFAULT 0`) to `TABLE_STARRED_SEGMENTS`.
  - Added SQLite `ALTER TABLE starred_segments ADD COLUMN synced_at INTEGER DEFAULT 0;` migration in `onUpgrade(db, oldVersion, newVersion)` for `oldVersion < 8`.
  - Updated `addOrUpdateSegment` to persist `System.currentTimeMillis()` into `synced_at`.
  - Updated `getAllSegmentSummaries` to read `synced_at`.
  - Implemented `pruneExpiredSegments(long maxAgeMs): int` which deletes starred segments where `synced_at > 0 AND synced_at < cutoff` and cascade-deletes their stream coordinates from `TABLE_SEGMENT_STREAMS`.
  - Implemented `pruneOrphanSegments(Set<Long> activeStravaIds): int` which deletes starred segments missing from the remote active set along with their coordinate streams.

### 2. Repository Layer TTL & Orphan Enforcement
- **`app/src/main/java/com/atrainingtracker/trainingtracker/repositories/RoutesRepository.kt`**:
  - Automatically runs `pruneExpiredRoutes()` on repository initialization (`init { pruneExpiredRoutes() }`).
  - On `syncRoutesFromStrava()`:
    - Queries existing routes and updates `synced_at` for existing routes.
    - Inserts newly discovered routes with `syncedAt = System.currentTimeMillis()`.
    - Automatically executes `pruneOrphanStravaRoutes(activeExternalIds)` to eliminate deleted remote routes.
    - Automatically executes `pruneExpiredStravaRoutes()` to evict stale cached routes.
  - Exposes `suspend fun duplicateRouteAsLocal(routeId: Long): Long` and `fun pruneExpiredRoutes()`.

- **`app/src/main/java/com/atrainingtracker/trainingtracker/segments/SegmentsRepository.kt`**:
  - Updated `SegmentSummary` data class with `@JvmOverloads` constructor and `syncedAt: Long = 0L`.
  - Automatically runs `pruneExpiredSegments()` on initialization and on completion of `syncStarredSegments`.
  - Executes `pruneOrphanSegments(activeStravaIds)` on sync to delete unstarred segments and coordinate streams.
  - Exposes `fun pruneExpiredSegments()`.

### 3. Background Sync Workers Section 6.2 Compliance
- **`app/src/main/java/com/atrainingtracker/trainingtracker/routes/StravaRoutesSyncWorker.kt`**:
  - In `doWork()`, unconditionally invokes `RoutesRepository.getInstance(applicationContext).pruneExpiredRoutes()` at the beginning of the execution cycle, ensuring 7-day TTL eviction occurs even if network sync is disabled or offline.
- **`app/src/main/java/com/atrainingtracker/trainingtracker/segments/StravaSegmentsSyncWorker.kt`**:
  - In `doWork()`, unconditionally invokes `SegmentsRepository.getInstance(applicationContext).pruneExpiredSegments()` at the beginning of the execution cycle, ensuring segment TTL eviction occurs even if network sync is disabled or offline.

### 4. Athlete Route Preservation ("Save as Local Route")
- **`app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteItem.kt`**:
  - Added overflow dropdown menu item "Save as Local Route" (`save_as_local_route`) for routes with `source == RouteSource.STRAVA`.
- **`app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteList.kt` & `RouteTabbedScreen.kt`**:
  - Propagated `onDuplicateAsLocal: (Long) -> Unit` callback up the Compose tree.
- **`app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesViewModel.kt`**:
  - Added `duplicateRouteAsLocal(routeId: Long, onComplete: ((Boolean) -> Unit)?)` coroutine handler.
- **`app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesFragment.kt`**:
  - Wired `onDuplicateAsLocal` callback to ViewModel and displayed confirmation snackbar (`route_saved_as_local`).
- **Strings**:
  - Added localized strings `save_as_local_route` and `route_saved_as_local` across all 9 supported locales: English (`values/`), German (`values-de/`), Spanish (`values-es/`), French (`values-fr/`), Italian (`values-it/`), Japanese (`values-ja/`), Dutch (`values-nl/`), Polish (`values-pl/`), and Portuguese (`values-pt/`).

### 5. Automated Tests
- **`app/src/test/java/com/atrainingtracker/trainingtracker/database/RoutesDatabaseManagerTTLTest.kt`**:
  - `testRoutesDbUpgradeToVersion8AddsSyncedAtColumn()`: Verifies migration ALTER TABLE execution.
  - `testPruneExpiredStravaRoutesRemovesOlderThanCutoff()`: Verifies 7-day TTL eviction for Strava routes.
  - `testPruneExpiredStravaRoutesPreservesLocalAndWorkoutRoutes()`: Verifies athlete-recorded workouts and local GPX routes are never touched.
  - `testPruneOrphanStravaRoutesRemovesMissingIds()`: Verifies orphan deletion when missing from active set.
  - `testDuplicateRouteAsLocalCreatesIndependentLocalGpxRoute()`: Verifies route duplication creates independent `LOCAL_GPX` route immune to TTL eviction.
- **`app/src/test/java/com/atrainingtracker/trainingtracker/segments/SegmentsDatabaseManagerTTLTest.kt`**:
  - `testSegmentsDbUpgradeToVersion8AddsSyncedAtColumn()`: Verifies migration ALTER TABLE execution.
  - `testPruneExpiredSegmentsRemovesOlderThanCutoffAndCascadesStreams()`: Verifies segment deletion and cascade stream removal.
  - `testPruneOrphanSegmentsRemovesMissingIdsAndStreams()`: Verifies orphan pruning for unstarred segments.
  - `testAddOrUpdateSegmentPersistsSyncedAtTimestamp()`: Verifies sync timestamp persistence.

---

## Verification Results
- `RoutesDatabaseManagerTTLTest`: 5 passed, 0 failed.
- `SegmentsDatabaseManagerTTLTest`: 4 passed, 0 failed.
- `StravaRoutesSyncWorkerTest`: 10 passed, 0 failed.
- `StravaSegmentsSyncWorkerTest`: 10 passed, 0 failed.
- `TranslationParityTest`: 5 passed, 0 failed (100% key and specifier coverage across all 9 locales).
- Total targeted suite: 45 tests passed, 0 failed.
- Database invariants fully preserved (recorded workouts, local GPX, equipment bindings untouched).
