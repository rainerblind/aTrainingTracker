# Test Specification: Enforce 7-Day TTL Cache Retention and Orphan Pruning for Strava Routes and Segments (ATT-1177)

## 1. Traceability & Requirements Mapping

* **Ticket Key**: `ATT-1177`
* **Sub-Task Key**: `ATT-1181` (`[Test-Spec]`)
* **Target Version**: `V4.9.37`
* **Target Branch**: `feature/ATT-1177`
* **Mapped Requirement**: `REQ-EXT-010` (documented in `docs/requirements.md`)
* **Verification Test ID**: `TST-EXT-007` (documented in `docs/tests.md`)

---

## 2. Requirement Specification (REQ-EXT-010)

### Title
**Strava Route & Segment 7-Day TTL Cache Retention, Orphan Pruning & Route Preservation**

### Functional Specification
The system SHALL enforce strict compliance with Section 6.2 ("Cache and Retention") of the Strava API Agreement and Developer Terms across all Strava-originated routes and starred segments:

1. **7-Day Retention Limit & Persistent Timestamping**:
   - The system SHALL NOT retain cached Strava routes (`RouteSource.STRAVA` in `Routes.db`) or starred segments (in `Segments.db`) for longer than 7 days (604,800,000 ms) without fresh synchronization.
   - Both `Routes.db` (`TABLE_ROUTES`) and `Segments.db` (`TABLE_STARRED_SEGMENTS`) SHALL maintain a `synced_at` timestamp column (`INTEGER DEFAULT 0`, Schema v8).
   - Whenever a route or segment is synchronized or updated from the Strava API, its `synced_at` timestamp SHALL be updated to current epoch milliseconds (`System.currentTimeMillis()`).

2. **Automated TTL Expiration & Cascade Eviction**:
   - The system SHALL automatically evict records where `currentTimeMillis() - synced_at > 7 days`.
   - TTL eviction SHALL execute upon repository instantiation (`init`), during background sync worker execution (`StravaRoutesSyncWorker`, `StravaSegmentsSyncWorker`), and on demand.
   - For expired segments, the eviction SHALL cascade-delete associated polyline stream coordinates from `SegmentStreamsTable`.

3. **Orphan & Tombstone Pruning**:
   - During remote synchronization, the system SHALL compare local Strava records against the remote API response.
   - Any local route with `source == RouteSource.STRAVA` whose external Strava ID is absent from the active remote route set SHALL be promptly pruned from `Routes.db`.
   - Similarly, any local starred segment whose Strava ID is absent from the active remote starred segments set SHALL be pruned from `Segments.db` alongside its stream coordinates.

4. **Athlete Route Preservation (Local GPX Duplication)**:
   - To allow athletes to retain desired navigation tracks permanently offline without violating Strava caching limits, the system SHALL provide a "Save as Local Route" / "Als lokale Route speichern" feature.
   - Duplicating a Strava route SHALL create an independent route record in `Routes.db` with `source = RouteSource.LOCAL_GPX`, clear external Strava identifiers, and copy all spatial coordinate points, bounds, and elevation profiles.
   - Converted local routes SHALL be athlete-owned data and SHALL NOT be subject to Strava TTL eviction or orphan pruning.

5. **System Invariants**:
   - Native recorded workouts (`WorkoutSummaries.db`, `WorkoutSamples.db`, `Laps.db`), athlete-owned routes (`RouteSource.LOCAL_GPX` and `RouteSource.WORKOUT`), recurring route clusters in `WorkoutCluster.db`, and hardware sensor bindings in `Equipment.db` MUST NOT be touched, altered, or deleted during TTL eviction or orphan pruning.
   - Eviction operations MUST execute safely in background transactions without blocking UI threads or failing on offline devices.

### Acceptance Criteria (Given-When-Then)
* **AC-1 (TTL Expiration for Routes)**:
  * *Given* cached Strava routes in `Routes.db` with `synced_at = now - 8 days` (expired) and `synced_at = now - 1 day` (fresh),
  * *When* `pruneExpiredStravaRoutes()` is triggered,
  * *Then* the expired route SHALL be removed from `Routes.db` and the fresh route SHALL be preserved.
* **AC-2 (TTL Expiration for Segments & Cascade Streams)**:
  * *Given* cached starred segments in `Segments.db` with `synced_at = now - 8 days` (expired) and associated stream coordinates in `SegmentStreamsTable`, alongside a fresh segment with `synced_at = now - 2 days`,
  * *When* `pruneExpiredSegments()` is triggered,
  * *Then* the expired segment summary and all its stream coordinates SHALL be deleted from `Segments.db`, while the fresh segment and its streams remain intact.
* **AC-3 (Invariant Protection - Non-Strava Routes)**:
  * *Given* athlete-created routes with `source = RouteSource.LOCAL_GPX` or `source = RouteSource.WORKOUT` with `synced_at = 0` or timestamp older than 7 days,
  * *When* TTL eviction or orphan pruning runs,
  * *Then* athlete-created routes MUST NOT be pruned or altered.
* **AC-4 (Orphan Pruning during Sync)**:
  * *Given* local Strava routes [A, B, C] and a fresh remote Strava sync response containing only [A, C] (B was deleted or unstarred on Strava),
  * *When* `pruneOrphanStravaRoutes(activeExtIds)` executes,
  * *Then* route B SHALL be deleted from `Routes.db`, and A and C SHALL remain.
* **AC-5 (Athlete Route Preservation via Duplication)**:
  * *Given* a cached Strava route `R_strava`,
  * *When* the athlete invokes `duplicateRouteAsLocal(R_strava.id)`,
  * *Then* a new route `R_local` SHALL be created in `Routes.db` with `source = RouteSource.LOCAL_GPX`, identical coordinates, bounds, and profile, and `externalId = null`. When `R_strava` subsequently expires past 7 days, `R_local` MUST survive TTL eviction.
* **AC-6 (Preservation of Native Workouts & Equipment Pairings)**:
  * *Given* native recorded workouts in `WorkoutSummaries.db`, `WorkoutSamples.db`, and `Laps.db`, and sensor pairings in `Equipment.db`,
  * *When* TTL pruning or orphan pruning runs,
  * *Then* all native workout tables and equipment records MUST remain 100% untouched.

---

## 3. Detailed Test Specification (TST-EXT-007)

### Test Architecture
* **Unit / Integration Test Classes**:
  1. `RoutesDatabaseManagerTTLTest.kt` (Robolectric / SQLite unit test)
  2. `SegmentsDatabaseManagerTTLTest.kt` (Robolectric / SQLite unit test)
  3. `RoutesRepositoryTTLTest.kt` (Repository and Flow layer test)
* **Execution Framework**: JUnit 4 / AndroidX Test / Robolectric.

### Step-by-Step Test Procedures

#### Phase 1: Database Migration & Timestamp Persistence (Schema v8)
1. Verify `RoutesDatabaseManager.DB_VERSION == 8` and `SegmentsDatabaseManager.DB_VERSION == 8`.
2. Perform SQLite upgrade from version 7 to 8 on both databases.
3. Assert `synced_at` column exists in `TABLE_ROUTES` and `StarredSegmentsTable` with default value `0`.

#### Phase 2: Routes TTL Expiration Testing
1. Insert three routes into `RoutesDatabaseManager`:
   - Route 1: `source = RouteSource.STRAVA`, `externalId = "strava_101"`, `synced_at = System.currentTimeMillis() - 8 * 86400000L` (8 days ago).
   - Route 2: `source = RouteSource.STRAVA`, `externalId = "strava_102"`, `synced_at = System.currentTimeMillis() - 2 * 86400000L` (2 days ago).
   - Route 3: `source = RouteSource.LOCAL_GPX`, `externalId = null`, `synced_at = 0`.
2. Invoke `pruneExpiredStravaRoutes(7 * 86400000L)`.
3. Assert:
   - Route 1 is deleted (count return = 1).
   - Route 2 is present in `RoutesDatabaseManager.getAllRoutes()`.
   - Route 3 (`LOCAL_GPX`) is present in `RoutesDatabaseManager.getAllRoutes()`.

#### Phase 3: Segments TTL & Stream Cascade Eviction Testing
1. Insert two starred segments and their coordinate streams into `SegmentsDatabaseManager`:
   - Segment 1: `id = 201L`, `synced_at = System.currentTimeMillis() - 9 * 86400000L` (9 days ago), with 5 stream points.
   - Segment 2: `id = 202L`, `synced_at = System.currentTimeMillis() - 1 * 86400000L` (1 day ago), with 5 stream points.
2. Invoke `pruneExpiredSegments(7 * 86400000L)`.
3. Assert:
   - Segment 201 is deleted from `StarredSegmentsTable`.
   - Coordinate streams for segment 201 are completely deleted from `SegmentStreamsTable`.
   - Segment 202 and its coordinate streams remain fully present.

#### Phase 4: Orphan Pruning Testing
1. In `RoutesDatabaseManager`, insert Strava routes with `externalId` "1", "2", "3".
2. Simulate sync response returning active set `{"1", "3"}`.
3. Call `pruneOrphanStravaRoutes(setOf("1", "3"))`.
4. Assert:
   - Route with `externalId = "2"` is deleted.
   - Routes "1" and "3" are retained.
5. In `SegmentsDatabaseManager`, insert segments with Strava IDs `1001L`, `1002L`.
6. Simulate sync response returning active set `{1001L}`.
7. Call `pruneOrphanSegments(setOf(1001L))`.
8. Assert:
   - Segment `1002L` and its streams are deleted.
   - Segment `1001L` is retained.

#### Phase 5: Route Preservation via Duplication
1. Insert a Strava route `R_orig` (`source = RouteSource.STRAVA`, 10 GPS points).
2. Call `duplicateRouteAsLocal(R_orig.id)`.
3. Assert:
   - Returns a new valid route ID `R_dup_id`.
   - `R_dup` has `source = RouteSource.LOCAL_GPX`.
   - `R_dup` has `externalId = null`.
   - `R_dup` has the exact same point coordinates, total distance, elevation gain, and bounding box as `R_orig`.
4. Update `R_orig.synced_at = now - 10 days`.
5. Run `pruneExpiredStravaRoutes()`.
6. Assert:
   - `R_orig` is deleted.
   - `R_dup` remains intact in the database.

#### Phase 6: System Invariant Protection
1. Pre-populate `WorkoutSummariesDatabaseManager` with 1 workout, `WorkoutSamplesDatabaseManager` with 20 samples, and `LapsDatabaseManager` with 2 laps.
2. Pre-populate `EquipmentDbHelper` with 1 bike linked to an ANT+ speed sensor.
3. Execute all TTL and orphan pruning routines across both routes and segments.
4. Assert:
   - Workout summaries count == 1.
   - Workout samples count == 20.
   - Laps count == 2.
   - Equipment and sensor link count == 1.
