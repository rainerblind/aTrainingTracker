# Stage 1: Analysis & Problem Domain - ATT-4005: Add real SQLite migration and persistence tests with Robolectric

**Ticket**: [ATT-4005](https://rainerblind.atlassian.net/browse/ATT-4005)  
**Sub-task**: `ATT-4201` (`[Analysis]`)  
**Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Executive Summary & Problem Formulation

During independent whole-app code review on branch `feature/ATT-3921` (`0234bfc0`), ticket **ATT-4005** reported:
> "No androidTest, no Robolectric, no SQLite JVM driver. DB upgrade tests mock `SQLiteDatabase` and only verify `execSQL` strings (e.g. `WorkoutSummariesDatabaseSourceMigrationTest`). SQL syntax, full upgrade chains and data preservation are never exercised - the legacy upgrade crash and the SportType drop would have been caught."

### 1.1 Forensic Codebase & Test Infrastructure Audit
A comprehensive audit of the project build configurations and existing database unit tests reveals:
1. **Mock-Heavy Unit Testing Pattern**:
   - Existing migration tests (e.g. `WorkoutSummariesDatabaseSourceMigrationTest.kt`, `WorkoutSummariesDatabaseRaceMigrationTest.kt`, `SportTypeDatabaseUpgradeTest.kt`) instantiate helpers with `mockk<SQLiteDatabase>(relaxed = true)` and verify string arguments passed to `mockDb.execSQL(match { ... })`.
   - In `SportTypeDatabaseUpgradeTest.kt`, complex MockK answers simulate `rawQuery("PRAGMA table_info(...)")` via `MockCursorFactory`.
   - While these tests verify that specific branching paths are taken and specific SQL strings are dispatched, they **do not execute SQL against an SQLite engine**.
   - Consequently:
     - SQL syntax errors (such as unquoted string literals, invalid `DEFAULT ???` in `ALTER TABLE`, or malformed queries) pass unit tests completely unnoticed.
     - Pragma inspection and column existence checks are evaluated against hand-crafted mock cursor rows rather than real SQLite table metadata.
     - Multi-version upgrade chains (e.g. migrating schema from v4 -> v25) and data preservation across upgrades are never validated end-to-end.
2. **Current Test Dependency State**:
   - `app/build.gradle` currently declares:
     ```groovy
     testImplementation 'junit:junit:4.13.2'
     testImplementation "io.mockk:mockk:1.14.11"
     testImplementation "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0"
     testImplementation 'net.sf.kxml:kxml2:2.3.0'
     testImplementation 'org.json:json:20240303'
     ```
   - Neither `org.robolectric:robolectric` nor `androidx.test:core` nor an SQLite JDBC driver is declared in `dependencies`.
   - AGP unit testing options are configured with JVM memory and fork limits (`forkEvery = 80`, `maxHeapSize = "768m"`, `maxParallelForks = Math.max(1, processors / 2)`), and strictly forbid `returnDefaultValues = true` (`docs/coding_standards.md` §5.1).
3. **Database Population in the App**:
   The app manages 15 SQLite database files registered in `DatabaseSchemaRegistry.kt`:
   - `WorkoutSummaries.db` (v25) - Core workout statistics, polylines, extrema, spatial bounds, race, source.
   - `SportType.db` (v3) - Sport definitions, base sport types, placeholder flags.
   - `Devices.db` (v7) - ANT+ and BLE sensor configurations and device links.
   - `Routes.db` (v10) - Navigation routes, waypoints, spatial bounds, TTL sync timestamps.
   - `Segments.db` (v9) - Strava starred segments, polyline streams, spatial bounds.
   - `TrackingViews.db` (v14) - Cockpit display tabs, rows, view sizes, section dividers.
   - `KnownLocations.db` (v6) - Known locations, geofence radii, altitude calibrations, home locations.
   - `StravaUpload.db` (v6) - Upload status, minimized Strava activity JSON payloads.
   - `Equipment.db` (v2) - Bikes, shoes, Strava gear IDs, retirement flags, hardware sensor links.
   - `WorkoutCluster.db` (v11) - Recurring route clusters, 3D altitude extrema, counters.
   - `Laps.db` (v2) - Lap intervals, names, descriptions.
   - `Pebble.db` (v3) - Smartwatch navigation layouts.
   - `WorkoutSamples.db` (v1) - Dynamic 1Hz trackpoint sensor sample tables.
   - `AthleteMetrics.db` (v1) - Resting HR, max HR, FTP historical records.
   - `ExportStatus.db` (v1) - Background export retry status.

---

## 2. Root Cause & Gap Analysis

1. **Why was Robolectric omitted previously?**
   - Historical emphasis was placed on lightweight, ultra-fast JVM unit tests running via pure MockK (executing ~3,300 tests in under 3 minutes).
   - Android framework dependencies (Context, SQLiteDatabase, Cursor, ContentValues) were mocked in memory.
2. **The High-Cost Failure Mode**:
   - Pure string-matching mock tests allowed critical schema bugs to slip into production:
     - `ATT-3993`: `SportTypeDbHelper.onUpgrade` contained a legacy `drop table if exists SportTypes` statement that silently wiped all custom user sports on schema bump to v2. Unit tests mocked `execSQL` and did not catch that data was permanently dropped.
     - `ATT-4009`: In `WorkoutSummariesDatabaseManager.java:1834`, upgrading from oldVersion < 11 issues `ALTER TABLE WorkoutNamePatterns ADD COLUMN sportId text DEFAULT ???;` which is a fatal SQLite syntax error (`???` is not valid unquoted SQL), and queries non-existent columns.
3. **Evaluating Testing Strategies (Robolectric vs. androidTest vs. SQLite JDBC)**:
   - **Strategy A: `androidTest` (Instrumented Tests on Emulator/Device)**:
     - *Pros*: Runs against real Android OS SQLite library on physical devices or emulators.
     - *Cons*: Requires emulator boots in CI; slow execution; cannot run as part of `./gradlew testDebugUnitTest` fast unit loop.
   - **Strategy B: Pure JVM SQLite Driver (e.g. Xerial `sqlite-jdbc` or `androidx.sqlite:sqlite-jvm`)**:
     - *Pros*: Runs in JVM.
     - *Cons*: Does not mock `android.database.sqlite.SQLiteDatabase` or `SQLiteOpenHelper`. All existing helpers inherit directly from `android.database.sqlite.SQLiteOpenHelper`, which contains native Android JNI hooks that fail with `RuntimeException: Stub!` on JVM unless Robolectric or an Android environment is present.
   - **Strategy C: Robolectric (Recommended)**:
     - *Pros*: Runs directly in `./gradlew testDebugUnitTest` on standard JVM. Robolectric shadows `SQLiteDatabase`, `SQLiteOpenHelper`, and `Cursor` backed by a real SQLite engine (SQLite4Java / native SQLite). Executes authentic DDL/DML, evaluates real syntax, tests full upgrade transitions from old schema fixtures, and verifies actual data preservation.
     - *Cons*: Slightly higher initialization overhead on first test execution in a worker process (~1-2 seconds), but well within CI and local performance budgets.

---

## 3. Chesterton's Fence & Archaeology

1. **Why was `returnDefaultValues = false` enforced?**
   - Coding standard §5.1 (`docs/coding_standards.md`) explicitly prohibits `returnDefaultValues = true` because silent default returns hide regressions.
   - Introducing Robolectric aligns with this invariant by providing real Android framework implementations for Context, SQLite, and Looper rather than returning stubbed default values.
2. **Why was `WorkoutSummariesDatabaseSourceMigrationTest` written with MockK?**
   - It was created to verify the `onUpgrade(23, 24)` branch without pulling in external test runner frameworks.
   - The test verified the code *dispatched* the ALTER command, but could not verify that SQLite actually executed it, that existing row data remained intact, or that default `'TRACKED'` values were queryable from existing rows.
3. **Core Invariant Preservation**:
   - The existing ~3,300 MockK unit tests must remain fast, deterministic, and untouched.
   - Adding Robolectric for database migration tests must not disrupt parallel test execution (`maxParallelForks`, `forkEvery = 80`).
   - All tests must pass cleanly in `./gradlew testDebugUnitTest`.

---

## 4. Scope & Technical Architecture (SWE.1 / SWE.2)

### 4.1 In-Scope Objectives
1. **Robolectric Test Dependencies**:
   - Add `robolectric:4.14.1` (or latest compatible with AGP 9.0 / Java 17) to `testImplementation` in `app/build.gradle`.
   - Add `androidx.test:core:1.6.1` and `androidx.test.ext:junit:1.2.1`.
   - Verify Gradle test configuration executes Robolectric tests cleanly without classloader leaks or heap exhaustion.
2. **Reusable Migration Test Harness**:
   - Create a clean test base or utility (`RealSqliteTestHelper` / `DatabaseMigrationFixtureHelper`) that:
     - Creates temporary or in-memory SQLite databases.
     - Provides facilities to execute historical DDL snapshots (e.g. creating tables as they existed in v1, v4, v10, v22, v23).
     - Inserts baseline test records into the old-version database.
     - Invokes the real `SQLiteOpenHelper.onUpgrade(db, oldVersion, newVersion)` or opens the database through the helper to trigger automatic migration.
     - Asserts schema integrity (`PRAGMA table_info`) and verifies data preservation (all pre-existing records and values retained, new columns populated with defaults).
3. **Initial High-Value Real SQLite Migration Test Suites**:
   - Implement real SQLite migration test for `WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper`:
     - Test migrations across key version milestones: v22 -> v23 (`RACE`), v23 -> v24 (`SOURCE`), v24 -> v25 (`COUNT`).
     - Test data preservation: existing workouts retain all fields and receive proper defaults.
   - Implement real SQLite migration test for `SportTypeDatabaseManager.SportTypeDbHelper`:
     - Test v1 -> v3 and v2 -> v3 migrations against real SQLite.
     - Verify custom user-defined sports (`_id >= 6`) are 100% preserved.
     - Verify `WALK` base sport type migration to `RUN`.
     - Verify `is_placeholder` column addition.
   - Implement real SQLite migration test for `EquipmentDbHelper`:
     - Test v1 -> v2 migration (`RETIRED` column addition and data preservation).
   - Implement real SQLite migration test for `RoutesDatabaseManager.RoutesDbHelper`:
     - Test v5 -> v10 migration against real SQLite, asserting `route_waypoints` table creation and spatial bounds.
   - Implement real SQLite migration test for `TrackingViewsDatabaseManager.TrackingViewsDbHelper`:
     - Test v13 -> v14 migration asserting `SectionDividersTable` creation.

### 4.2 Out-of-Scope (To prevent scope creep)
- Fixing `ATT-4009` (Legacy database upgrade path crashes): `ATT-4009` has its own dedicated ticket in the sprint backlog and will be addressed separately once the Robolectric test infrastructure from `ATT-4005` is in place.
- Refactoring production database managers or changing database schemas: this ticket is strictly focused on establishing real SQLite testing capabilities and migration test suites (Developer Testing Tools Epic ATT-2466).

---

## 5. System Invariants & Preserved Behavior

1. **Zero Production Code Regression**: No production application runtime classes are modified. Only `app/build.gradle` (test dependencies) and `app/src/test/` test suites are added/updated.
2. **Deterministic Test Execution**: Tests run hermetically in memory or temporary directory fixtures without side effects on physical device storage.
3. **Full Suite Compatibility**: All 3,302 existing unit tests must continue to pass cleanly with 0 failures.
4. **Single Atomic Commit (Rule 44)**: Changes remain on `feature/ATT-4005` until squashed into `sprint/2026-42.10` upon Gate 5 approval.
