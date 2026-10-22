# Stage 2: Requirement & Test Specification - ATT-4005: Add real SQLite migration and persistence tests with Robolectric

**Ticket**: [ATT-4005](https://rainerblind.atlassian.net/browse/ATT-4005)  
**Sub-task**: `ATT-4202` (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-MIG-045` (extending `REQ-MIG-044` and `REQ-MIG-041`)  
**Test Spec ID**: `TST-MIG-042`  
**Branch**: `feature/ATT-4005`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Formal Requirement Specification (`REQ-MIG-045`)

### REQ-MIG-045: Real SQLite In-Memory & Fixture Migration Testing Framework via Robolectric

The test harness, build configuration (`app/build.gradle`), and automated unit test suite SHALL equip the project with an authentic SQLite execution environment using Robolectric and AndroidX Test, enabling `SQLiteOpenHelper` implementations to execute DDL, DML, PRAGMA statements, multi-version upgrade chains, and data preservation checks against a genuine SQLite engine during JVM test execution (`./gradlew testDebugUnitTest`), eliminating reliance on mock-only `execSQL` verification (ATT-4005, extending `REQ-MIG-044` and `REQ-MIG-041`):

1. **Robolectric & AndroidX Test Dependency Integration (`REQ-MIG-045.1`)**:
   - `app/build.gradle` SHALL include `org.robolectric:robolectric:4.14.1` (or compatible version), `androidx.test:core:1.6.1`, and `androidx.test.ext:junit:1.2.1` under `testImplementation`.
   - AGP test configuration SHALL preserve existing test runners, memory heap limits (`maxHeapSize = "768m"`, `minHeapSize = "256m"`), parallel forks (`maxParallelForks`), and strict prohibition of `returnDefaultValues = true` (`docs/coding_standards.md` §5.1).
2. **Deterministic Database Migration Test Harness (`REQ-MIG-045.2`)**:
   - The test infrastructure SHALL provide a reusable migration test fixture helper (`RealSqliteMigrationTestHelper`) that operates in an isolated temporary/in-memory database environment per test.
   - The helper SHALL provide utilities to:
     - (a) Scaffold historical database schemas at arbitrary older versions ($V_{\text{old}} < V_{\text{current}}$);
     - (b) Insert representative baseline dataset records (e.g. historical workouts, custom sport types, equipment, routes, tracking views);
     - (c) Trigger `SQLiteOpenHelper.onUpgrade(db, oldVersion, newVersion)` or open the database with the current helper;
     - (d) Inspect real SQLite metadata via `PRAGMA table_info(tableName)` and `PRAGMA user_version`;
     - (e) Query and assert data preservation, verifying that historical rows retain exact values, default values are populated for newly added columns, and no tables or columns are unintentionally dropped.
3. **Core Database Real SQLite Migration Verification Suites (`REQ-MIG-045.3`)**:
   - The test suite SHALL provide real SQLite Robolectric migration tests covering critical database helpers:
     - **`WorkoutSummariesDbHelper`**: Verify incremental migration chains from v22 -> v23 (`RACE`), v23 -> v24 (`SOURCE`), and v24 -> v25 (`COUNT`), asserting column creation, correct column types, default values (`0`, `'TRACKED'`, `NULL`), and retention of existing workout rows.
     - **`SportTypeDbHelper`**: Verify real SQLite execution of v1 -> v3 and v2 -> v3 migrations, proving that the `SportTypes` table is NOT dropped, user-defined custom sports (`_id >= 6`) are 100% preserved, `WALK` is migrated to `baseSportType = 'RUN'`, and column `is_placeholder` is added.
     - **`EquipmentDbHelper`**: Verify real SQLite execution of v1 -> v2 migration, asserting that `Retired` column is added and existing bikes/shoes and link rows remain fully intact.
     - **`RoutesDbHelper`**: Verify real SQLite execution of v5 -> v10 migration, asserting that `route_waypoints` table is created, spatial bounds columns exist, and `synced_at` timestamps are initialized.
     - **`TrackingViewsDbHelper`**: Verify real SQLite execution of v13 -> v14 migration, asserting that `SectionDividersTable` is created and existing cockpit layouts remain undisturbed.
4. **Execution Performance & Zero Production Disruption (`REQ-MIG-045.4`)**:
   - The Robolectric migration tests SHALL execute entirely within `./gradlew testDebugUnitTest` without requiring an emulator or physical device.
   - All tests SHALL complete deterministically, leaving zero persistent disk artifacts, and maintaining 100% clean-room test pass rate across the full project test suite.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Net-new requirement `REQ-MIG-045` under Epic `ATT-2466` (*Developer Testing Tools*). Targets `app/build.gradle` (test dependencies) and `app/src/test/java/.../database/`.
2. **Historical Origin & Commit Trace**: Ticket `ATT-4005`, Sprint `2026-42.10`, discovered during independent whole-app code review of `feature/ATT-3921` (`0234bfc0`). Review identified that unit tests across database managers relied exclusively on MockK mocks, leaving genuine SQLite syntax, multi-step migrations, and data preservation unexercised.
3. **Root Reason for Existing Formulation**: Early engineering prioritized fast execution on the JVM without external shadow frameworks. However, this allowed serious schema regressions (`ATT-3993` dropping user sports, `ATT-4009` malformed SQL in `WorkoutSummaries` legacy upgrade) to reach production because mock tests only matched string patterns.
4. **Preservation of Core Invariants**: The ~3,300 existing MockK unit tests MUST continue to run without interference. Fast developer loop (`./gradlew testDebugUnitTest --tests ...`) and clean-room full suite pass rate MUST remain 100% intact.

---

## 3. Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Robolectric Dependency & Execution Availability)**:
  * *Given* the test project build configuration,
  * *When* compiling and executing tests with `@RunWith(RobolectricTestRunner::class)`,
  * *Then* the test executes on the JVM with an authentic Android context and native SQLite backing without `Stub!` exceptions.
* **Criterion 2 (WorkoutSummaries Real Migration & Data Preservation)**:
  * *Given* a real SQLite database initialized with `WorkoutSummaries` Schema v22 containing sample workout rows,
  * *When* `WorkoutSummariesDbHelper.onUpgrade(db, 22, 25)` executes,
  * *Then* `race`, `source`, and `count` columns are present in SQLite metadata, and the sample workout rows are preserved with correct defaults (`race = 0`, `source = 'TRACKED'`).
* **Criterion 3 (SportType Real Migration & Non-Destructive Invariant)**:
  * *Given* a real SQLite database initialized with `SportType` Schema v1 containing default sports (1..5) and custom sports (`_id = 6`),
  * *When* `SportTypeDbHelper.onUpgrade(db, 1, 3)` executes,
  * *Then* custom sport `_id = 6` is preserved, `WALK` has `baseSportType = 'RUN'`, and `is_placeholder` column is added with value `0`.
* **Criterion 4 (Equipment Real Migration)**:
  * *Given* a real SQLite database initialized with `Equipment` Schema v1 containing bike and shoe entries,
  * *When* `EquipmentDbHelper.onUpgrade(db, 1, 2)` executes,
  * *Then* the `Retired` column is added with default `0` and all existing equipment rows are preserved.
* **Criterion 5 (Routes & TrackingViews Real Migration)**:
  * *Given* real SQLite databases initialized with `Routes` v5 and `TrackingViews` v13,
  * *When* their respective `onUpgrade` methods run to current versions,
  * *Then* `route_waypoints` and `SectionDividersTable` are created, and all pre-existing records remain readable.
* **Criterion 6 (Full Regression Suite Pass)**:
  * *Given* the entire project test suite,
  * *When* `./gradlew testDebugUnitTest` is executed,
  * *Then* all tests pass with zero failures and 100% pass rate.

---

## 4. System Invariants

1. **Production Code Zero-Touch**: No production code in `app/src/main/` is modified. Changes are strictly confined to `app/build.gradle` (test scope), test fixtures, and test specifications.
2. **Memory & Concurrency Bounds**: Robolectric tests must run within configured JVM fork boundaries without causing out-of-memory or file-handle leaks.
3. **Isolation**: Each database test must run against an independent in-memory or temporary SQLite database that is cleanly closed and deleted after test execution.
4. **No UI / Localization Impact**: Testing framework enhancements do not introduce or modify UI strings or resources.

---

## 5. Concrete Verification Test Specification (`TST-MIG-042`)

### TST-MIG-042: Real SQLite Robolectric Database Migration & Data Preservation Verification Suite

| Test ID | Target Test File | Verification Scope & Procedure |
| :--- | :--- | :--- |
| `TST-MIG-042.1` | `WorkoutSummariesRealSqliteMigrationTest.kt` | **WorkoutSummaries Real SQLite Upgrade Chain & Data Preservation Test**:<br>• Initialize real SQLite database with v22 schema and insert pre-existing workout rows.<br>• Upgrade through `WorkoutSummariesDbHelper.onUpgrade(db, 22, 25)`.<br>• Verify via `PRAGMA table_info` that `race`, `source`, and `count` columns exist.<br>• Verify pre-existing workouts retain ID, name, timestamps, and receive `race = 0`, `source = 'TRACKED'`.<br>• Verify new rows can be inserted and queried with all v25 columns. |
| `TST-MIG-042.2` | `SportTypeRealSqliteMigrationTest.kt` | **SportType Real SQLite Non-Destructive Upgrade & Custom Sport Preservation Test**:<br>• Initialize real SQLite database with v1 schema containing default sports 1..5 (`WALK` with `baseSportType = 'UNKNOWN'`) and custom user sport (`_id = 6`, `UIName = 'Rollerblading'`).<br>• Execute `SportTypeDbHelper.onUpgrade(db, 1, 3)`.<br>• Assert `SportTypes` table was NOT dropped.<br>• Assert custom sport `_id = 6` remains present with exact ID and name.<br>• Assert `WALK` (`_id = 4`) has `baseSportType = 'RUN'`.<br>• Assert `is_placeholder` column is added and defaults to `0`. |
| `TST-MIG-042.3` | `EquipmentRealSqliteMigrationTest.kt` | **Equipment Real SQLite Upgrade & Retired Column Addition Test**:<br>• Initialize real SQLite database with v1 schema containing bikes, shoes, and links.<br>• Execute `EquipmentDbHelper.onUpgrade(db, 1, 2)`.<br>• Assert `Retired` column exists via `PRAGMA table_info`.<br>• Assert existing equipment entries remain intact with `Retired = 0`. |
| `TST-MIG-042.4` | `RoutesRealSqliteMigrationTest.kt` | **Routes Real SQLite Upgrade & Waypoints Table Creation Test**:<br>• Initialize real SQLite database with v5 schema containing routes and route points.<br>• Execute `RoutesDbHelper.onUpgrade(db, 5, 10)`.<br>• Assert `route_waypoints` table and spatial bounds columns exist.<br>• Assert pre-existing routes remain intact and accessible. |
| `TST-MIG-042.5` | `TrackingViewsRealSqliteMigrationTest.kt` | **TrackingViews Real SQLite Upgrade & SectionDividers Table Creation Test**:<br>• Initialize real SQLite database with v13 schema containing cockpits and rows.<br>• Execute `TrackingViewsDbHelper.onUpgrade(db, 13, 14)`.<br>• Assert `SectionDividersTable` exists in SQLite.<br>• Assert existing cockpit tabs and rows are preserved. |
| `TST-MIG-042.6` | Full Suite Clean-Room | **Full Suite Clean-Room Regression Execution**:<br>• Execute `./gradlew testDebugUnitTest` verifying 100% pass rate across all unit and Robolectric tests. |

---

## 6. Localization Parity

* **Scope**: Localization Parity: N/A.
* **Justification**: Purely developer testing infrastructure and unit test suites. Zero user-facing strings or layouts are added or changed.

---

## 7. Traceability Matrix

| Requirement Clause | Test Case | Target Artifact | Status |
| :--- | :--- | :--- | :--- |
| `REQ-MIG-045.1` | `TST-MIG-042.1`–`TST-MIG-042.5` | `app/build.gradle` | Specified |
| `REQ-MIG-045.2` | `TST-MIG-042.1`–`TST-MIG-042.5` | `RealSqliteTestHelper.kt` | Specified |
| `REQ-MIG-045.3` | `TST-MIG-042.1` | `WorkoutSummariesRealSqliteMigrationTest.kt` | Specified |
| `REQ-MIG-045.3` | `TST-MIG-042.2` | `SportTypeRealSqliteMigrationTest.kt` | Specified |
| `REQ-MIG-045.3` | `TST-MIG-042.3` | `EquipmentRealSqliteMigrationTest.kt` | Specified |
| `REQ-MIG-045.3` | `TST-MIG-042.4` | `RoutesRealSqliteMigrationTest.kt` | Specified |
| `REQ-MIG-045.3` | `TST-MIG-042.5` | `TrackingViewsRealSqliteMigrationTest.kt` | Specified |
| `REQ-MIG-045.4` | `TST-MIG-042.6` | `./gradlew testDebugUnitTest` | Specified |
