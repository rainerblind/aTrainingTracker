# Stage 3: Implementation Plan - ATT-4009: Legacy database upgrade path crashes and heavy backfills run inside onUpgrade

**Ticket**: [ATT-4009](https://rainerblind.atlassian.net/browse/ATT-4009)  
**Sub-task**: [ATT-4213](https://rainerblind.atlassian.net/browse/ATT-4213) (`[Plan]`)  
**Parent Epic**: None  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-MIG-046` (*Legacy SQLite Upgrade DDL Hardening, Synchronous onUpgrade Excision, and Idempotent Cross-Database Backfill Architecture*)  
**Test Mapping**: `TST-MIG-043`  
**Branch**: `feature/ATT-4009`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Description & Background

During a comprehensive whole-app architecture audit of `WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper` and `WorkoutClusterDatabaseManager.WorkoutClusterDbHelper`, severe legacy migration flaws, crashes, and performance violations were identified:

1. **Fatal SQLite DDL Syntax Crash for `oldVersion < 11` (Parent Criterion 1)**:
   - In `WorkoutSummariesDatabaseManager.java:1834`, `addColumn(db, "WorkoutNamePatterns", "sportId", "text", "???");` executes `ALTER TABLE WorkoutNamePatterns ADD COLUMN sportId text DEFAULT ???;`. SQLite throws `OperationalError: near "?": syntax error` because unquoted `???` is treated as a token/expression rather than string literal `'???'`. Any user upgrading from version $\le 10$ crashes immediately.
2. **Column Reference Bugs in `onUpgrade`**:
   - `WorkoutNamePatterns` was created in v10 with `sportId int`. In v11, `onUpgrade` queries `"sport"` which never existed, throwing `SQLiteException: no such column: sport`.
   - In v11, `addColumn(db, WorkoutSummaries.TABLE, WorkoutSummaries.B_SPORT, "text");` was commented out, but subsequent code updates `B_SPORT`. Upgrading from v3 without `bSport` throws `no such column: bSport`.
3. **Synchronous Cross-Database Backfills Blocking Database Open (Parent Criterion 2)**:
   - Heavy routines (`migrateExistingWorkouts` for v17, `migrateExtremaPositions` for v19, `migrateSpatialBounds` for v21, and `backfillAltitudeExtrema` for v10 in `WorkoutClusterDatabaseManager`) execute synchronously inside `onUpgrade` on whichever thread opens the database first.
   - For users with substantial workout histories, decoding polylines and querying across databases freezes the UI thread for multiple seconds, causing Application Not Responding (ANR) dialogs on first launch after update.
   - Swallowed exceptions in these backfills allow `SQLiteOpenHelper` to bump `user_version`, leaving data permanently unpopulated with zero retry mechanisms in the one-shot `onUpgrade` lifecycle.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MIG-046` (*Legacy SQLite Upgrade DDL Hardening, Synchronous onUpgrade Excision, and Idempotent Cross-Database Backfill Architecture*)
  * `REQ-MIG-046.1`: Legacy DDL Syntax & Column Guard Hardening (`DEFAULT '???'`, column presence guards in `WorkoutSummariesDbHelper` and `WorkoutClusterDbHelper`).
  * `REQ-MIG-046.2`: Synchronous Backfill Excision from `onUpgrade` (pure DDL execution in $< 10\text{ ms}$, zero UI thread freezing).
  * `REQ-MIG-046.3`: Idempotent Background Backfill Architecture (`DatabaseBackfillManager` operating on `WHERE ... IS NULL`, atomic SQLite row state, process-death resilience).
  * `REQ-MIG-046.4`: Data Access Layer (DAL) Fallback & Graceful Degradation (non-crashing queries and on-demand fallback during migration window).
* **Test Mapping**: `TST-MIG-043` (*Legacy SQLite Migration Hardening, Fast onUpgrade DDL, Background Backfill Convergence, and DAL Guard Verification Suite*)
  * `TST-MIG-043.1`: `testLegacyUpgradeV3ToV25RealSqlite` (v3 $\to$ v25 upgrade with row preservation).
  * `TST-MIG-043.2`: `testLegacyUpgradeV10ToV25SyntaxAndColumnGuards` (v10 $\to$ v25 upgrade verifying `DEFAULT '???'` fix).
  * `TST-MIG-043.3`: `testClusterUpgradeV6ToV11DeterministicColumns` (cluster v6 $\to$ v11 column additions via `PRAGMA table_info`).
  * `TST-MIG-043.4`: `testDatabaseOpenIsInstantaneousWithoutSynchronousBackfill` (fast DDL-only open $< 50\text{ ms}$).
  * `TST-MIG-043.5`: `testIdempotentBackfillConvergenceAndInterruption` (interruption and resumption without duplicates).
  * `TST-MIG-043.6`: `testDalGracefulFallbackOnUnmigratedRows` (safe DAL fallback for unmigrated rows).
  * `TST-MIG-043.7`: Localization audit (confirming zero string modifications).
  * `TST-MIG-043.8`: Full clean-room regression suite (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **`REQ-CON-017 / ATT-3990` Zero Mutation Invariant**: `onOpen()` in `WorkoutSummariesDbHelper` remains strictly passive with zero mutations and zero thread allocations.
2. **Schema Version Stability**: `DB_VERSION = 25` in `WorkoutSummariesDatabaseManager` and `DB_VERSION = 11` in `WorkoutClusterDatabaseManager` remain unchanged.
3. **Single Source of Truth**: The database row data state itself serves as the atomic source of truth (`WHERE boundMinLat IS NULL`, `WHERE latitude IS NULL`). No external metadata tables or drift-prone flags.
4. **Clean-Room Test Pass Rate**: 100% pass rate across entire project test suite (~3,300 tests).
5. **Single Atomic Commit per Ticket (Rule 44)**: Changes remain uncommitted until Stage 5 clean-room verification is signed off.

---

## 4. Proposed Architectural Changes

```
+-------------------------------------------------------------------------+
|                  WorkoutSummariesDbHelper.onUpgrade()                   |
|  - Strictly DDL-Only: ALTER TABLE, CREATE TABLE, CREATE INDEX           |
|  - Executes in < 10 ms on calling thread                                |
|  - addColumnIfNotExists() with PRAGMA table_info                        |
|  - Quoted default: DEFAULT '???'                                        |
|  - Column presence checks before legacy v11 queries                     |
+-------------------------------------------------------------------------+

+-------------------------------------------------------------------------+
|                   WorkoutClusterDbHelper.onUpgrade()                    |
|  - Replace empty try/catch with deterministic addColumnIfNotExists()    |
|  - Exclude synchronous backfillAltitudeExtrema from onUpgrade           |
+-------------------------------------------------------------------------+

+-------------------------------------------------------------------------+
|                     DatabaseBackfillManager                             |
|  - Off-UI-Thread (Dispatchers.IO) idempotent execution                  |
|  - Operates strictly on unpopulated rows:                              |
|      * Spatial Bounds: WHERE boundMinLat IS NULL                        |
|      * Extrema Positions: WHERE latitude IS NULL                        |
|      * Existing Workouts Streams: WHERE mapPolyline = ''                |
|      * Cluster Extrema: WHERE min_alt_lat IS NULL                       |
|  - Atomic convergence: interruption-safe with zero duplicates           |
+-------------------------------------------------------------------------+
```

### Component 1: `WorkoutSummariesDatabaseManager.java` DDL Hardening & Backfill Excision
1. **Fix `oldVersion < 11` Syntax and Guards**:
   - Replace line 1834:
     ```java
     addColumnIfNotExists(db, "WorkoutNamePatterns", "sportId", "text", "'???'");
     ```
   - Before querying `"sport"` from `WorkoutNamePatterns`, check `PRAGMA table_info("WorkoutNamePatterns")` for column `"sport"`. If absent, skip query.
   - For `WorkoutSummaries`:
     ```java
     addColumnIfNotExists(db, WorkoutSummaries.TABLE, WorkoutSummaries.SPORT_ID, "int", "0");
     addColumnIfNotExists(db, WorkoutSummaries.TABLE, WorkoutSummaries.B_SPORT, "text", null);
     ```
     Check if column `"sport"` exists in `WorkoutSummaries` before querying it.
2. **Excision of Heavy Backfills from `onUpgrade`**:
   - Remove calls to `migrateExistingWorkouts(db, oldVersion < 15);`, `migrateExtremaPositions(db);`, and `migrateSpatialBounds(db);` from `onUpgrade`.
   - Keep the migration logic accessible as static / package methods in `WorkoutSummariesDatabaseManager` so that `DatabaseBackfillManager` can invoke them asynchronously off the UI thread.
   - Update `migrateSpatialBounds` and `migrateExtremaPositions` to be strictly idempotent by querying only `WHERE boundMinLat IS NULL` and `WHERE latitude IS NULL`.

### Component 2: `WorkoutClusterDatabaseManager.kt` Hardening
1. **Deterministic Column Addition (`addColumnIfNotExists`)**:
   - Add helper function `addColumnIfNotExists(db: SQLiteDatabase, tableName: String, columnName: String, columnType: String, defaultValue: String? = null)`.
   - Replace empty `try { db.execSQL(...) } catch (e: Exception) {}` blocks in `oldVersion < 7`, `oldVersion < 10`, and `oldVersion < 11` with `addColumnIfNotExists`.
2. **Excision of `backfillAltitudeExtrema`**:
   - Remove synchronous call to `backfillAltitudeExtrema(context, db)` from `onUpgrade`.
   - Expose `backfillAltitudeExtrema(context, db)` for invocation by `DatabaseBackfillManager`.

### Component 3: `DatabaseBackfillManager.kt` Asynchronous Idempotent Engine
1. **Idempotent Background Dispatch**:
   - Create `app/src/main/java/com/atrainingtracker/trainingtracker/database/DatabaseBackfillManager.kt`.
   - `fun hasPendingBackfills(context: Context): Boolean`: checks if any unpopulated rows exist in `WorkoutSummaries`, `WorkoutSummaries_ExtremaValues`, or `WorkoutClusters`.
   - `suspend fun executePendingBackfills(context: Context) = withContext(Dispatchers.IO)`:
     - Runs spatial bounds backfill for workouts `WHERE boundMinLat IS NULL`.
     - Runs extrema positions backfill `WHERE latitude IS NULL`.
     - Runs cluster altitude extrema backfill `WHERE min_alt_lat IS NULL`.
   - Resilient: if process is killed or interrupted, next execution queries remaining `IS NULL` rows.

### Component 4: Data Access Layer (DAL) Fallback Verification
- In `WorkoutSummariesDatabaseManager.java`, verify that `queryWorkoutsInBounds` and summary readers handle `boundMinLat = NULL` without throwing exceptions.
- In `WorkoutRepository.kt` / chart loaders, verify that empty streams fall back on-demand to `WorkoutSamplesDatabaseManager` without NPE.

---

## 5. UI Consistency (Rule 23)
* **Scope**: UI Consistency: N/A.
* **Justification**: Purely SQLite database migration and background data backfill architecture. Zero user interface components, Compose screens, or layouts are modified.

---

## 6. Atomic Implementation Steps

### Step 1: DDL Syntax & Guard Hardening in `WorkoutSummariesDatabaseManager.java`
- Fix `'???'` default literal in `WorkoutNamePatterns`.
- Add column existence check for `"sport"` before querying `WorkoutNamePatterns`.
- Ensure `bSport` and `sportId` are added via `addColumnIfNotExists`.
- Add column existence check for `"sport"` before querying `WorkoutSummaries`.

### Step 2: Synchronous Backfill Excision from `WorkoutSummariesDbHelper.onUpgrade`
- Remove synchronous invocations of `migrateExistingWorkouts`, `migrateExtremaPositions`, and `migrateSpatialBounds` from `onUpgrade`.
- Refactor backfill methods to be accessible from `DatabaseBackfillManager` and ensure strict `WHERE ... IS NULL` selection.

### Step 3: Harden `WorkoutClusterDatabaseManager.kt`
- Add `addColumnIfNotExists` helper using `PRAGMA table_info`.
- Replace empty `catch` blocks with `addColumnIfNotExists`.
- Remove synchronous `backfillAltitudeExtrema` from `onUpgrade`.

### Step 4: Implement `DatabaseBackfillManager.kt`
- Create `DatabaseBackfillManager.kt` providing `hasPendingBackfills` and `executePendingBackfills`.
- Ensure thread-safe, interruptible, chunked execution on `Dispatchers.IO`.

### Step 5: Author Unit and Robolectric Test Suites
- Update `app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesRealSqliteMigrationTest.kt` with tests for v3 $\to$ v25, v10 $\to$ v25 (verifying `'???'` fix), and fast open timing (`TST-MIG-043.1`, `TST-MIG-043.2`, `TST-MIG-043.4`).
- Create `app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterRealSqliteMigrationTest.kt` with tests for cluster v6 $\to$ v11 (`TST-MIG-043.3`).
- Create `app/src/test/java/com/atrainingtracker/trainingtracker/database/DatabaseBackfillManagerTest.kt` with tests for backfill convergence and interruption resilience (`TST-MIG-043.5`).
- Add tests in `WorkoutSummariesDatabaseManagerTest.kt` for DAL fallback on unmigrated rows (`TST-MIG-043.6`).

### Step 6: Targeted Test Verification
- Run `./gradlew testDebugUnitTest --tests "*WorkoutSummariesRealSqliteMigrationTest*" --tests "*WorkoutClusterRealSqliteMigrationTest*" --tests "*DatabaseBackfillManagerTest*"`

---

## 7. Review and Gate Audit Preparation
- Update subtask `ATT-4213` description with this document.
- Transition `ATT-4213` to `In Überprüfung`.
- Run Gate 3 review audit via `python3 tools/review_agent.py audit ATT-4213`.
- Upon passing Gate 3, execute `python3 tools/jira_util.py move ATT-4213 freigabe` to advance parent `ATT-4009` to `Implementation`.
