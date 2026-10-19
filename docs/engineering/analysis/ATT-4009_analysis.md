# Stage 1: Problem Domain & Root Cause Analysis - ATT-4009

**Ticket ID:** ATT-4009 / ATT-4211  
**Ticket Title:** [Bug] [Medium] Legacy database upgrade path crashes and heavy backfills run inside onUpgrade  
**Target Release:** V4.9.40  
**Active Sprint:** 2026-42.10 (Sprint ID: 378)  
**Author:** AI Pair Programmer (DeepMind Antigravity)  
**Date:** 2026-10-19  
**Status:** In Review (Gate 1 - Resilient Asynchronous Architecture & DAL Guards)  

---

## 1. Executive Summary & Problem Formulation

During a comprehensive whole-app architecture audit of `com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper` and `WorkoutClusterDatabaseManager.WorkoutClusterDbHelper`, severe legacy migration flaws, crashes, and performance violations were identified:

1. **Fatal SQLite DDL Syntax Crash for `oldVersion < 11` (Parent Criterion 1)**:
   - In `WorkoutSummariesDatabaseManager.java:1834`:
     ```java
     addColumn(db, "WorkoutNamePatterns", "sportId", "text", "???");
     ```
     which executes:
     ```sql
     ALTER TABLE WorkoutNamePatterns ADD COLUMN sportId text DEFAULT ???;
     ```
     In SQLite, `DEFAULT ???` is invalid SQL syntax (`near "?": syntax error`). An unquoted `???` is treated as an expression rather than a string literal (`'???'`). Any user upgrading from schema version $\le 10$ crashes immediately with `android.database.sqlite.SQLiteException`.

2. **Column Inconsistencies & Invalid Legacy Queries in `onUpgrade`**:
   - In `onUpgrade` for `oldVersion < 10`, `WorkoutNamePatterns` was created with `sportId int`. In `oldVersion < 11`, `addColumn` attempts to add `sportId` a second time.
   - Lines 1836–1844 query `"sport"` from `WorkoutNamePatterns`, but `WorkoutNamePatterns` created in version 10 never possessed a column named `"sport"`—it already had `"sportId"`. Querying `new String[]{WorkoutSummaries.C_ID, "sport"}` throws `SQLiteException: no such column: sport`.
   - In `oldVersion < 11`: line 1811 `// addColumn(db, WorkoutSummaries.TABLE, WorkoutSummaries.B_SPORT, "text");` was commented out, but lines 1826–1828 update `B_SPORT`. If upgrading from version 3 where `bSport` didn't exist, updating `bSport` throws `no such column: bSport`.

3. **Violation of Acceptance Criterion 2 (Synchronous Backfills Blocking Database Open)**:
   - Heavy cross-database routines run synchronously inside `onUpgrade` on whichever thread opens the database:
     - `migrateExistingWorkouts` (`oldVersion < 17`): iterates through all workouts, opens `WorkoutSamples.db`, decodes polyline, elevation, and distance streams, and encodes them back.
     - `migrateExtremaPositions` (`oldVersion < 19`): iterates through all extrema without coordinates, querying `WorkoutSamples.db` through `WorkoutSamplesDatabaseManager.getExtremaPosition`.
     - `migrateSpatialBounds` (`oldVersion < 21`): iterates through all rows in `WorkoutSummaries`, decodes polylines, and calculates bounding boxes.
     - `backfillAltitudeExtrema` (`oldVersion < 10` in `WorkoutClusterDatabaseManager`): queries `WorkoutSummaries.db` across all clusters synchronously.
   - In databases with hundreds of historical workouts, executing these cross-database DML loops inside `onUpgrade` freezes the calling thread and causes fatal Application Not Responding (ANR) dialogs on first launch after update.
   - Furthermore, swallowed exceptions in these backfills allow `SQLiteOpenHelper` to bump `user_version`, leaving data unpopulated with zero retry mechanisms in the one-shot `onUpgrade` lifecycle.

---

## 2. Forensic Investigation & Root Cause Reproduction

### 2.1 Confirmation of Findings
1. **Finding 1 (`DEFAULT ???` syntax error)**: **CONFIRMED**.
   - Verified via SQLite shell and Python SQLite engine:
     ```python
     cur.execute("ALTER TABLE t ADD COLUMN sportId text DEFAULT ???;")
     # sqlite3.OperationalError: near "?": syntax error
     ```
   - In `WorkoutSummariesDatabaseManager.java:1834`:
     ```java
     addColumn(db, "WorkoutNamePatterns", "sportId", "text", "???");
     ```
     Calls:
     ```java
     db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type + " DEFAULT " + defaultValue + ";");
     ```
     Resulting in unquoted `DEFAULT ???`. 100% reproducible crash for any database upgraded from `oldVersion < 11`.

2. **Finding 2 (Querying non-existent column `"sport"` on `WorkoutNamePatterns`)**: **CONFIRMED**.
   - Line 1797 creates `WorkoutNamePatterns` in `oldVersion < 10` with:
     `_id`, `sportId int`, `startLocationName text`, `endLocationName text`, `fancyName text`, `addCounter int`, `counter int`, `addVia int`.
   - Line 1836 queries:
     `db.query("WorkoutNamePatterns", new String[]{WorkoutSummaries.C_ID, "sport"}, ...)`
     Throws `SQLiteException: no such column: sport`.

3. **Finding 3 (Swallowing ALTER errors in `WorkoutClusterDatabaseManager`)**: **CONFIRMED**.
   - Lines 574–577 and 592–595 in `WorkoutClusterDatabaseManager.kt`:
     ```kotlin
     try { db.execSQL("ALTER TABLE ${WorkoutClusterContract.TABLE_NAME} ADD COLUMN ${WorkoutClusterContract.COLUMN_MIN_ALT_LAT} REAL") } catch (e: Exception) {}
     ```
     Empty `catch` blocks silently mask schema corruption and syntax failures.

4. **Finding 4 (Synchronous cross-database backfills blocking open)**: **CONFIRMED**.
   - `migrateExistingWorkouts`, `migrateExtremaPositions`, `migrateSpatialBounds`, and `backfillAltitudeExtrema` execute synchronously inside `onUpgrade`.
   - On a dataset of 500 workouts, polyline decoding and cross-database sampling take several seconds, directly violating Parent Criterion 2.

---

## 3. Chesterton's Fence & Archaeology

1. **Why was `WorkoutNamePatterns` created and altered this way?**
   - In 2016, version 10 created `WorkoutNamePatterns` with `sportId int`. Version 11 attempted to migrate sport strings to sport IDs across both `WorkoutSummaries` and `WorkoutNamePatterns`. The author intended to set default `"???"` as a literal string or replace `"sport"` with `"sportId"`, but omitted single quotes around `???`.
   - In `SCRUM-173` (`c80e3bc78cd02f2775145556da7cba8b50908ce0`), the auto-name feature was deleted and `WorkoutNamePatterns` was purged from active app code. The legacy upgrade path was never cleaned up.
2. **Why was `B_SPORT` commented out in line 1811?**
   - The original author assumed `bSport` was created earlier. When upgrading from v3, `bSport` was absent, leading to update failures. Safe addition via `addColumnIfNotExists` ensures columns exist without throwing duplicate errors.
3. **Why did `WorkoutClusterDatabaseManager` use `try/catch` around `ALTER TABLE`?**
   - Older SQLite versions lacked `IF NOT EXISTS` for column alterations. Empty `try/catch` was used as a crude workaround, but it masked legitimate syntax and database errors. Using `PRAGMA table_info` checks eliminates the need for empty catch blocks.

---

## 4. Proposed Remediation Architecture (SWE.1 / SWE.2)

To satisfy **Criterion 1** and **Criterion 2** while eliminating the "split-brain" risk, avoiding UI thread freezing, and providing robust data-access guards:

```
+-------------------------------------------------------------------------+
|                              onUpgrade()                                |
|  - Strictly Schema-Only (DDL)                                           |
|  - Fast (< 10 ms), zero UI thread freezing                              |
|  - addColumnIfNotExists() using PRAGMA table_info                       |
|  - Fix DEFAULT '???' string literal syntax                              |
|  - Safe guards before legacy DDL operations                             |
+-------------------------------------------------------------------------+
                                    |
                                    v (Dispatches background backfill)
+-------------------------------------------------------------------------+
|                  DatabaseBackfillWorker (WorkManager)                   |
|  - Asynchronous background execution (Off UI thread)                    |
|  - Self-Guiding Atomic Persistence (DB data state is source of truth):   |
|      * migrateSpatialBounds: WHERE boundMinLat IS NULL                  |
|      * migrateExtremaPositions: WHERE latitude IS NULL                  |
|      * migrateExistingWorkouts: WHERE mapPolyline = ''                  |
|      * backfillAltitudeExtrema: WHERE min_alt_lat IS NULL               |
|  - Process-Death Resilient (monotonically converges on retry)           |
+-------------------------------------------------------------------------+
                                    ^
                                    |
+-------------------------------------------------------------------------+
|                Data Access Layer (DAL) Fallback Guards                  |
|  - Unmigrated Rows Gracefully Handled:                                  |
|      * Spatial queries: fallback to general list if bounds NULL         |
|      * Chart queries: on-demand stream load from WorkoutSamples.db       |
|      * Extrema queries: on-demand position lookup if lat is NULL        |
|  - Zero NullPointerExceptions, zero UI crashes while backfill runs      |
+-------------------------------------------------------------------------+
```

### 4.1 Schema-Only `onUpgrade` (Criterion 1)
1. **Fix DDL Syntax Errors**:
   - For `WorkoutNamePatterns`, use quoted default `'???'` with `addColumnIfNotExists`:
     ```java
     addColumnIfNotExists(db, "WorkoutNamePatterns", "sportId", "text", "'???'");
     ```
   - Guard column `"sport"` query: check if table `WorkoutNamePatterns` exists and contains column `"sport"` before querying.
2. **Safe Column Additions for `WorkoutSummaries`**:
   - Ensure `sportId` and `bSport` are added using `addColumnIfNotExists(db, WorkoutSummaries.TABLE, ...)`.
   - Check if column `"sport"` exists in `WorkoutSummaries` before querying it in v11.
3. **Harden `WorkoutClusterDatabaseManager`**:
   - Replace empty `try/catch` blocks with explicit `addColumnIfNotExists` using `PRAGMA table_info`.

### 4.2 Decouple Heavy Backfills to Background WorkManager (Criterion 2)
1. **Excision from `onUpgrade`**:
   - Remove synchronous calls to `migrateExistingWorkouts`, `migrateExtremaPositions`, `migrateSpatialBounds`, and `backfillAltitudeExtrema` from `onUpgrade`.
   - `onUpgrade` solely executes fast schema DDL (`ALTER TABLE`, `CREATE TABLE`, `CREATE INDEX`), completing in $< 10\text{ ms}$.
2. **Atomic In-Database State (Zero Desynchronization Risk)**:
   - Rather than relying on external flags or files that can desynchronize across backups/restores, **the SQLite row data state itself is the single source of truth**:
     - `pendingSpatialBoundsCount = SELECT COUNT(*) FROM WorkoutSummaries WHERE boundMinLat IS NULL AND mapPolyline IS NOT NULL AND mapPolyline != ''`
     - `pendingExtremaCount = SELECT COUNT(*) FROM WorkoutSummaries_ExtremaValues WHERE latitude IS NULL AND extremaType != 'AVG'`
     - `pendingClusterExtremaCount = SELECT COUNT(*) FROM WorkoutClusters WHERE min_alt_lat IS NULL`
   - If the count is $> 0$, the `DatabaseBackfillWorker` processes those specific rows in chunks.
3. **Process-Death & Interruption Resilience**:
   - Because each row update transitions that row's state from `NULL` to populated, any unexpected process termination or worker interruption leaves the database in a valid, partially-migrated state.
   - Upon restart, the query naturally picks up only the remaining `NULL` rows. Duplication is mathematically impossible.

### 4.3 Data Access Layer (DAL) Fallback Guards ("In-Between" State Handling)
To eliminate the "split-brain" danger if user navigates the app while backfill is running:
1. **Elevation/Distance Profile Charts**:
   - Existing DAL already checks `if (distanceStream.isEmpty())`. If empty, it falls back to reading raw points from `WorkoutSamplesDatabaseManager` on-demand. This invariant is explicitly preserved and covered by unit tests.
2. **Extrema Map Pins**:
   - If `extrema.latitude == null`, the map view controller falls back to querying `WorkoutSamplesDatabaseManager.getExtremaPosition()` on demand or gracefully omits the coordinate pin until populated.
3. **Spatial Cluster / Bounding Box Queries**:
   - Workouts with `boundMinLat IS NULL` are safely excluded from spatial bounding-box window queries until populated, while remaining 100% visible and accessible in the standard chronological workout history lists (`ORDER BY timeStamp DESC`).
4. **No UI Freezing**:
   - App launches immediately without progress dialogs or blocked UI threads, meeting user expectations and passing Parent Acceptance Criterion 2.

---

## 5. Scope Bounding

### 5.1 In-Scope
1. **Legacy DDL Fixes in `WorkoutSummariesDbHelper.onUpgrade`**:
   - Fix `'???'` default literal in `WorkoutNamePatterns`.
   - Add column existence guards for `sportId`, `bSport`, and `sport`.
2. **Harden `WorkoutClusterDatabaseManager.kt`**:
   - Replace empty `try/catch` blocks with `addColumnIfNotExists` using `PRAGMA table_info`.
3. **Decouple Heavy Backfills to Background Worker**:
   - Move `migrateExistingWorkouts`, `migrateExtremaPositions`, `migrateSpatialBounds`, and `backfillAltitudeExtrema` to `DatabaseBackfillWorker`.
   - Implement idempotent row selection (`WHERE ... IS NULL`) directly tied to SQLite data state.
4. **Data Access Layer Guards**:
   - Verify and document on-demand fallback paths for unpopulated rows during the migration window.
5. **Real SQLite Robolectric Verification Suite**:
   - Test legacy upgrade from schema v3 to v25 (instant DDL completion).
   - Test legacy upgrade from schema v10 to v25 (validating `oldVersion < 11` fix).
   - Test cluster upgrade from v6 to v11.
   - Test `DatabaseBackfillWorker` under process death / interruption simulation (verifying resumption without data duplication).

### 5.2 Out-of-Scope
- Rewriting database layers to Room.
- Schema version bump (`DB_VERSION = 25` remains unchanged).

---

## 6. Verification Plan

1. **Targeted Real SQLite Robolectric Tests (`WorkoutSummariesRealSqliteMigrationTest.kt` & `WorkoutClusterRealSqliteMigrationTest.kt`)**:
   - Verify v3 $\to$ v25 migration: assert zero crashes, correct schema, and instant DDL execution.
   - Verify v10 $\to$ v25 migration: assert `DEFAULT '???'` fix succeeds without syntax error.
   - Verify cluster migration: assert all columns exist without swallowed exceptions.
   - Verify background backfill: assert unpopulated spatial bounds, extrema positions, and cluster extrema are accurately populated asynchronously.
   - Interruption test: simulate killing backfill worker halfway through a 100-workout batch, restart worker, assert 100% convergence with zero duplicates.
2. **Full Clean-Room Test Suite**:
   - Execute `./gradlew testDebugUnitTest` ensuring 100% pass rate.
3. **ASPICE Governance**:
   - Update living documentation in `docs/requirements.md` and `docs/tests.md`.
