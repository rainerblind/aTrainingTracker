# Stage 3: Implementation Plan - ATT-4005: Add real SQLite migration and persistence tests with Robolectric

**Ticket**: [ATT-4005](https://rainerblind.atlassian.net/browse/ATT-4005)  
**Sub-task**: [ATT-4203](https://rainerblind.atlassian.net/browse/ATT-4203) (`[Plan]`)  
**Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-MIG-045` (extending `REQ-MIG-044` and `REQ-MIG-041`)  
**Test Mapping**: `TST-MIG-042`  
**Branch**: `feature/ATT-4005`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Description & Background

In aTrainingTracker, historical database migration and persistence unit tests (e.g. `WorkoutSummariesDatabaseSourceMigrationTest`, `SportTypeDatabaseUpgradeTest`) have relied exclusively on MockK to mock `SQLiteDatabase` and verify calls to `db.execSQL(...)` via string argument matching. Because no authentic SQLite engine parsed or executed the statements, severe schema and data regressions slipped through into production:
1. `ATT-4009`: Malformed SQL syntax in `WorkoutSummariesDbHelper.onUpgrade(db, 3, 23)` (`DEFAULT ???`) crashed legacy user upgrades.
2. `ATT-3993`: Destructive `DROP TABLE` in `SportTypeDbHelper.onUpgrade(db, 1, 2)` erased user-defined custom sport types.

This plan addresses `ATT-4005` by integrating Robolectric and AndroidX Test into the project's test suite, creating a reusable real SQLite migration test harness (`RealSqliteTestHelper`), and establishing comprehensive real SQLite migration verification test suites for `WorkoutSummariesDbHelper`, `SportTypeDbHelper`, `EquipmentDbHelper`, `RoutesDbHelper`, and `TrackingViewsDbHelper`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MIG-045` (*Real SQLite In-Memory & Fixture Migration Testing Framework via Robolectric*)
  * `REQ-MIG-045.1`: Robolectric & AndroidX Test Dependency Integration in `app/build.gradle`.
  * `REQ-MIG-045.2`: Deterministic Database Migration Test Harness (`RealSqliteTestHelper`).
  * `REQ-MIG-045.3`: Core Database Real SQLite Migration Verification Suites (`WorkoutSummaries`, `SportType`, `Equipment`, `Routes`, `TrackingViews`).
  * `REQ-MIG-045.4`: Execution Performance & Zero Production Disruption.
* **Test Mapping**: `TST-MIG-042` (*Real SQLite Robolectric Database Migration & Data Preservation Verification Suite*)
  * `TST-MIG-042.1`: `WorkoutSummariesRealSqliteMigrationTest.kt`
  * `TST-MIG-042.2`: `SportTypeRealSqliteMigrationTest.kt`
  * `TST-MIG-042.3`: `EquipmentRealSqliteMigrationTest.kt`
  * `TST-MIG-042.4`: `RoutesRealSqliteMigrationTest.kt`
  * `TST-MIG-042.5`: `TrackingViewsRealSqliteMigrationTest.kt`
  * `TST-MIG-042.6`: Full clean-room regression suite execution.

---

## 3. System Invariants & Preserved Behavior

1. **Zero Production Disruption**: Zero modifications to production code in `app/src/main/`. All changes are strictly confined to `app/build.gradle` (test scope), test utilities, and test suites.
2. **MockK Test Coexistence**: The existing ~3,300 MockK unit tests must continue to run unimpeded. Robolectric tests run alongside MockK tests in the same test runner task.
3. **Deterministic Isolation**: Every test operates on an isolated in-memory or temporary SQLite database that is cleanly closed and deleted after each test method.
4. **Memory Heap & Process Limits**: Test execution must strictly adhere to configured JVM fork parameters (`maxHeapSize = "768m"`, `forkEvery = 80`).
5. **No Default Return Values**: AGP test configuration maintains strict prohibition of `returnDefaultValues = true`.
6. **Parent Human Gate Invariance**: Terminal completion of parent ticket `ATT-4005` remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Build & Test Dependencies (`app/build.gradle`)
Add test dependencies under `dependencies { ... }`:
```groovy
// --- Robolectric & AndroidX Test for Real SQLite Testing ---
testImplementation 'org.robolectric:robolectric:4.14.1'
testImplementation 'androidx.test:core:1.6.1'
testImplementation 'androidx.test.ext:junit:1.2.1'
```
Configure `testOptions`:
Ensure unit tests support Android resources and Robolectric execution without modifying memory heap limits.

### Component 2: Reusable Real SQLite Test Harness (`RealSqliteTestHelper.kt`)
Located in `app/src/test/java/com/atrainingtracker/testutil/RealSqliteTestHelper.kt`:
- **Database Lifecycle**:
  - `createInMemoryDatabase()` / `createDatabase(name)`: Instantiates real SQLite database using Android context from `ApplicationProvider.getApplicationContext<Context>()`.
  - `closeQuietly(db)`: Closes database safely and deletes temporary database file.
- **Schema & Metadata Inspection**:
  - `getTableColumns(db, tableName)`: Queries `PRAGMA table_info(tableName)` and returns structured list of column definitions: `name`, `type`, `notnull`, `dfltValue`, `pk`.
  - `hasColumn(db, tableName, columnName)`: Checks for existence of column.
  - `getColumnType(db, tableName, columnName)`: Returns declared SQLite type of column.
  - `hasTable(db, tableName)`: Queries `sqlite_master` for table existence.
- **Fixture Scaffolding**:
  - Helper methods to execute DDL scripts to set up historical schema versions ($V_{\text{old}}$) and insert baseline rows before triggering `onUpgrade`.

### Component 3: Real SQLite Migration Verification Test Suites
1. **`WorkoutSummariesRealSqliteMigrationTest.kt`** (`TST-MIG-042.1`):
   - Scaffold v22 schema (`tblWorkoutSummaries`, `extrema_values`, `accumulated_sensors`).
   - Insert representative workout row and extrema value row.
   - Run `WorkoutSummariesDbHelper.onUpgrade(db, 22, 25)` (or incremental steps: 22->23, 23->24, 24->25).
   - Assert `race` column exists with default `0`.
   - Assert `source` column exists with default `'TRACKED'`.
   - Assert `count` column exists on `extrema_values`.
   - Assert pre-existing workout and extrema rows retain all original field values.
   - Assert new row can be inserted and queried with all v25 columns.
2. **`SportTypeRealSqliteMigrationTest.kt`** (`TST-MIG-042.2`):
   - Scaffold v1 schema containing default sports 1..5 (`WALK` with `baseSportType = 'UNKNOWN'`) and custom user sport (`_id = 6`, `UIName = 'Rollerblading'`).
   - Run `SportTypeDbHelper.onUpgrade(db, 1, 3)` and `v2 -> v3`.
   - Assert `SportTypes` table is NOT dropped.
   - Assert custom sport `_id = 6` is preserved.
   - Assert `WALK` (`_id = 4`) has `baseSportType = 'RUN'`.
   - Assert `is_placeholder` column exists and defaults to `0`.
3. **`EquipmentRealSqliteMigrationTest.kt`** (`TST-MIG-042.3`):
   - Scaffold v1 schema with bike and shoe entries.
   - Run `EquipmentDbHelper.onUpgrade(db, 1, 2)`.
   - Assert `Retired` column exists with default `0`.
   - Assert pre-existing equipment records are preserved.
4. **`RoutesRealSqliteMigrationTest.kt`** (`TST-MIG-042.4`):
   - Scaffold v5 schema with route and route points.
   - Run `RoutesDbHelper.onUpgrade(db, 5, 10)`.
   - Assert `route_waypoints` table exists.
   - Assert pre-existing routes are preserved.
5. **`TrackingViewsRealSqliteMigrationTest.kt`** (`TST-MIG-042.5`):
   - Scaffold v13 schema with cockpit views.
   - Run `TrackingViewsDbHelper.onUpgrade(db, 13, 14)`.
   - Assert `SectionDividersTable` exists.
   - Assert pre-existing cockpits and rows are preserved.

### UI Consistency (Rule 23)
* **Scope**: No UI changes. Purely developer test harness and migration unit test suites.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Test Dependency Configuration
* Files: `app/build.gradle`
* Action: Add `org.robolectric:robolectric:4.14.1`, `androidx.test:core:1.6.1`, and `androidx.test.ext:junit:1.2.1` under `dependencies`. Validate Gradle project evaluation and dependency resolution.

### Step 2: Implement Real SQLite Test Harness (`RealSqliteTestHelper.kt`)
* Files: `app/src/test/java/com/atrainingtracker/testutil/RealSqliteTestHelper.kt`
* Action: Implement database creation, cleanup, `PRAGMA table_info` metadata extraction, table verification, and row querying helper utilities.

### Step 3: Implement `WorkoutSummariesRealSqliteMigrationTest.kt`
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesRealSqliteMigrationTest.kt`
* Action: Implement tests verifying v22 -> v25 incremental upgrades, column additions (`race`, `source`, `count`), default values, and data retention.

### Step 4: Implement `SportTypeRealSqliteMigrationTest.kt`
* Files: `app/src/test/java/com/atrainingtracker/banalservice/database/SportTypeRealSqliteMigrationTest.kt`
* Action: Implement tests verifying v1 -> v3 non-destructive upgrade, custom sport type retention (`_id >= 6`), `WALK` base sport migration, and `is_placeholder` column addition.

### Step 5: Implement `EquipmentRealSqliteMigrationTest.kt`, `RoutesRealSqliteMigrationTest.kt`, and `TrackingViewsRealSqliteMigrationTest.kt`
* Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/database/EquipmentRealSqliteMigrationTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/database/RoutesRealSqliteMigrationTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/database/TrackingViewsRealSqliteMigrationTest.kt`
* Action: Implement real SQLite migration tests for Equipment (v1 -> v2), Routes (v5 -> v10), and TrackingViews (v13 -> v14).

### Step 6: Targeted Verification & Regression Runs
* Action: Run targeted test command:
  ```bash
  ./gradlew testDebugUnitTest --tests "*RealSqliteMigrationTest*"
  ```
  Verify all real SQLite migration test cases pass cleanly with authentic SQLite backing.

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Fast feedback loop: `./gradlew testDebugUnitTest --tests "*RealSqliteMigrationTest*"`
  - Stage 5 clean-room regression: `./gradlew testDebugUnitTest` verifying 100% pass rate across all ~3,300+ tests without memory exhaustion or timeouts.
* **Rollback**:
  - Changes are isolated to `feature/ATT-4005`. Reverting branch leaves existing test suite and build configuration completely untouched.
