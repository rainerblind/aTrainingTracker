# Stage 2: Requirement & Test Specification - ATT-4009: Legacy database upgrade path crashes and heavy backfills run inside onUpgrade

**Ticket**: [ATT-4009](https://rainerblind.atlassian.net/browse/ATT-4009)  
**Sub-task**: [ATT-4212](https://rainerblind.atlassian.net/browse/ATT-4212) (`[Req & Test Spec]`)  
**Parent Epic**: None  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-MIG-046` (*Legacy SQLite Upgrade DDL Hardening, Synchronous onUpgrade Excision, and Idempotent Cross-Database Backfill Architecture*)  
**Test Spec ID**: `TST-MIG-043`  
**Branch**: `feature/ATT-4009`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Requirement Specification (`REQ-MIG-046`)

### 1.1 Problem Statement & Rationale
Legacy SQLite schema evolution in `WorkoutSummariesDatabaseManager` and `WorkoutClusterDatabaseManager` exhibits multiple critical flaws:
1. Upgrading from database version $\le 10$ emits invalid SQL `ALTER TABLE WorkoutNamePatterns ADD COLUMN sportId text DEFAULT ???;` causing fatal syntax crashes (`OperationalError: near "?": syntax error`). Additionally, queries reference non-existent columns (`"sport"` in `WorkoutNamePatterns`) and unadded columns (`B_SPORT` in `WorkoutSummaries`).
2. Heavy cross-database data backfills (`migrateExistingWorkouts`, `migrateExtremaPositions`, `migrateSpatialBounds`, and `backfillAltitudeExtrema`) run synchronously inside `onUpgrade` on whichever thread opens the database first. On databases with substantial history, this causes multi-second UI thread freezes and Application Not Responding (ANR) crashes on first launch after update.
3. Swallowed exceptions in `WorkoutClusterDatabaseManager` mask schema corruption, while swallowed errors in `WorkoutSummariesDbHelper` allow `user_version` to bump without retrying unpopulated data.

### 1.2 Functional & Architectural Requirements

1. **Legacy DDL Syntax & Column Guard Hardening (`REQ-MIG-046.1`)**:
   - In `WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper.onUpgrade`:
     - For `oldVersion < 11`:
       - Replace `addColumn(db, "WorkoutNamePatterns", "sportId", "text", "???")` with `addColumnIfNotExists(db, "WorkoutNamePatterns", "sportId", "text", "'???'")`.
       - Before querying `WorkoutNamePatterns`, verify the table exists in `sqlite_master` and that column `"sport"` exists via `PRAGMA table_info`.
       - For `WorkoutSummaries`, ensure `sportId` and `bSport` are added using `addColumnIfNotExists`.
       - Before querying `"sport"` from `WorkoutSummaries`, verify column `"sport"` exists; if absent, skip row update.
   - In `WorkoutClusterDatabaseManager.WorkoutClusterDbHelper.onUpgrade`:
     - Replace all empty `catch (e: Exception) {}` blocks around `ALTER TABLE` statements (`oldVersion < 7`, `oldVersion < 10`, `oldVersion < 11`) with deterministic `addColumnIfNotExists` calls querying `PRAGMA table_info`.
2. **Synchronous Backfill Excision from `onUpgrade` (`REQ-MIG-046.2`)**:
   - `WorkoutSummariesDbHelper.onUpgrade` and `WorkoutClusterDbHelper.onUpgrade` SHALL strictly execute Data Definition Language (DDL) operations (`CREATE TABLE`, `ALTER TABLE ... ADD COLUMN`, `CREATE INDEX`).
   - Heavy cross-database Data Manipulation Language (DML) routines (`migrateExistingWorkouts`, `migrateExtremaPositions`, `migrateSpatialBounds`, and `backfillAltitudeExtrema`) SHALL be excised from synchronous execution inside `onUpgrade`, ensuring database opening completes in $< 10\text{ ms}$ on any thread without blocking the UI or causing ANRs (Parent Acceptance Criterion 2).
3. **Idempotent Background Backfill Architecture (`REQ-MIG-046.3`)**:
   - The application SHALL provide an asynchronous, off-UI-thread backfill mechanism (`DatabaseBackfillManager` / `DatabaseBackfillWorker`).
   - Each backfill operation SHALL be strictly idempotent and self-healing:
     - `migrateSpatialBounds`: queries and populates strictly rows `WHERE boundMinLat IS NULL AND mapPolyline IS NOT NULL AND mapPolyline != ''`.
     - `migrateExtremaPositions`: queries and populates strictly rows `WHERE latitude IS NULL AND extremaType != 'AVG'`.
     - `migrateExistingWorkouts`: queries and populates strictly rows `WHERE mapPolyline = '' OR distanceStream = ''`.
     - `backfillAltitudeExtrema`: queries and populates strictly clusters `WHERE min_alt_lat IS NULL`.
   - The SQLite row state itself SHALL serve as the atomic source of truth. Any worker cancellation, interruption, or process death SHALL preserve partially migrated rows, and subsequent runs SHALL resume strictly on remaining unpopulated rows without data duplication.
4. **Data Access Layer (DAL) Fallback & Graceful Degradation (`REQ-MIG-046.4`)**:
   - During the asynchronous backfill window, queries on unmigrated rows SHALL remain fully stable and non-crashing:
     - Workouts with unpopulated spatial bounds SHALL be omitted from bounding-box queries while remaining accessible in chronological lists.
     - Elevation and distance charts for workouts with empty stream columns SHALL fall back on-demand to raw points from `WorkoutSamplesDatabaseManager`.
     - Extrema queries with null coordinates SHALL gracefully fall back to sample lookup on demand.

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Legacy Database Upgrade Success)**:
  * *Given* a SQLite database at version $\le 10$,
  * *When* the current app opens and upgrades it to version 25,
  * *Then* the upgrade completes without any SQL syntax error, column error, or crash.
* **Criterion 2 (Non-Blocking Fast Database Open)**:
  * *Given* a database containing hundreds of historical workouts,
  * *When* the database is opened for the first time after an update,
  * *Then* `onUpgrade` completes in $< 10\text{ ms}$ executing purely DDL, and zero cross-database backfills block the calling or UI thread.
* **Criterion 3 (Idempotent Background Backfill Convergence)**:
  * *Given* unpopulated legacy rows in `WorkoutSummaries`, `WorkoutSummaries_ExtremaValues`, and `WorkoutClusters`,
  * *When* the asynchronous `DatabaseBackfillManager` runs,
  * *Then* all unpopulated records are accurately computed and populated without duplicate entries.
* **Criterion 4 (Process Death & Interruption Resilience)**:
  * *Given* a backfill operation interrupted mid-flight,
  * *When* the backfill resumes,
  * *Then* it queries only remaining unpopulated (`IS NULL`) rows and converges to completion with zero data loss.
* **Criterion 5 (DAL Stability During Migration Window)**:
  * *Given* rows with unpopulated backfill columns,
  * *When* read via DAO, summaries listing, or charts,
  * *Then* queries return safely with on-demand fallback and zero exceptions.

### 1.4 System Invariants
1. `REQ-CON-017 / ATT-3990`: `onOpen()` in database helpers remains strictly passive with zero mutations and zero thread allocations.
2. `DB_VERSION = 25` remains the active schema version; no schema version bump is required.
3. Clean-room unit test suite pass rate remains 100%.

---

## 2. Test Specification (`TST-MIG-043`)

### Test Case 1: `testLegacyUpgradeV3ToV25RealSqlite` (`TST-MIG-043.1`)
* **Scope**: Robolectric Real SQLite Test (`app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesRealSqliteMigrationTest.kt`)
* **Preconditions**: Real SQLite database created at Schema v3 with baseline workout summaries.
* **Action**: Upgrade database via `onUpgrade(db, 3, 25)`.
* **Expected Result**: DDL completes instantaneously without exception. `PRAGMA table_info` confirms all v25 columns exist (`sportId`, `bSport`, `mapPolyline`, `clusterId`, `boundMinLat`, `race`, `source`, `count`). Existing rows are preserved.

### Test Case 2: `testLegacyUpgradeV10ToV25SyntaxAndColumnGuards` (`TST-MIG-043.2`)
* **Scope**: Robolectric Real SQLite Test (`app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesRealSqliteMigrationTest.kt`)
* **Preconditions**: Real SQLite database created at Schema v10 with `WorkoutNamePatterns` table (`sportId int`).
* **Action**: Upgrade database via `onUpgrade(db, 10, 25)`.
* **Expected Result**: `oldVersion < 11` executes with quoted default `'???'` without `OperationalError: near "?": syntax error`. Absence of column `"sport"` in `WorkoutNamePatterns` is guarded safely without crash.

### Test Case 3: `testClusterUpgradeV6ToV11DeterministicColumns` (`TST-MIG-043.3`)
* **Scope**: Robolectric Real SQLite Test (`app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterRealSqliteMigrationTest.kt`)
* **Preconditions**: Real SQLite database created at Cluster Schema v6.
* **Action**: Upgrade database via `onUpgrade(db, 6, 11)`.
* **Expected Result**: All spatial bounds, altitude extrema, and counter columns are added via `addColumnIfNotExists`. Zero empty catch blocks swallow errors.

### Test Case 4: `testDatabaseOpenIsInstantaneousWithoutSynchronousBackfill` (`TST-MIG-043.4`)
* **Scope**: Unit & Performance Test (`app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesRealSqliteMigrationTest.kt`)
* **Preconditions**: Real SQLite database at v16 with 200 mock workouts.
* **Action**: Trigger `onUpgrade(db, 16, 25)`.
* **Expected Result**: Upgrade completes in $< 50\text{ ms}$ on the calling thread without opening `WorkoutSamples.db` or running heavy polyline decoding.

### Test Case 5: `testIdempotentBackfillConvergenceAndInterruption` (`TST-MIG-043.5`)
* **Scope**: Integration Test (`app/src/test/java/com/atrainingtracker/trainingtracker/database/DatabaseBackfillManagerTest.kt`)
* **Preconditions**: Database populated with 10 workouts having polylines but `boundMinLat IS NULL`, and extrema with `latitude IS NULL`.
* **Action**:
  - Run backfill on first 5 rows, simulate cancellation/interruption.
  - Assert first 5 rows are populated and remaining 5 rows remain NULL.
  - Resume backfill to completion.
* **Expected Result**: 100% of rows have populated bounds and coordinates. Zero duplicate records or corrupted values.

### Test Case 6: `testDalGracefulFallbackOnUnmigratedRows` (`TST-MIG-043.6`)
* **Scope**: Unit Test (`app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManagerTest.kt`)
* **Preconditions**: Workouts with `boundMinLat = NULL` and empty stream strings.
* **Action**: Perform query operations, bounding-box searches, and summary retrieval.
* **Expected Result**: Operations complete safely without `NullPointerException` or database exceptions.

### Test Case 7: `testNineLanguageLocalizationParity` (`TST-MIG-043.7`)
* **Scope**: Localization Audit (`app/src/main/res/values*/strings.xml`)
* **Preconditions**: 9 supported language directories (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
* **Action**: Audit XML strings.
* **Expected Result**: Zero modifications to string resources (purely database migration and backend backfill fix).

### Test Case 8: `testCleanRoomFullSuiteRegressionExecution` (`TST-MIG-043.8`)
* **Scope**: Full Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
* **Preconditions**: Clean git working tree on `feature/ATT-4009`.
* **Action**: Execute `./gradlew testDebugUnitTest`.
* **Expected Result**: 100% pass rate across entire unit test suite.

---

## 3. Traceability Matrix

| Requirement ID | Requirement Clause | Test Case ID | Test Implementation |
|---|---|---|---|
| `REQ-MIG-046.1` | Legacy DDL Syntax & Column Guard Hardening | `TST-MIG-043.1`, `TST-MIG-043.2`, `TST-MIG-043.3` | `WorkoutSummariesRealSqliteMigrationTest.kt`, `WorkoutClusterRealSqliteMigrationTest.kt` |
| `REQ-MIG-046.2` | Synchronous Backfill Excision from onUpgrade | `TST-MIG-043.4` | `WorkoutSummariesRealSqliteMigrationTest.kt` |
| `REQ-MIG-046.3` | Idempotent Background Backfill Architecture | `TST-MIG-043.5` | `DatabaseBackfillManagerTest.kt` |
| `REQ-MIG-046.4` | Data Access Layer Fallback & Graceful Degradation | `TST-MIG-043.6` | `WorkoutSummariesDatabaseManagerTest.kt` |
| Invariant | Zero Localization & Full Suite Integrity | `TST-MIG-043.7`, `TST-MIG-043.8` | Full clean-room `./gradlew testDebugUnitTest` |
