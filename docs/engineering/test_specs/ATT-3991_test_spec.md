# Stage 2: Requirement & Test Specification: ATT-3991

**Ticket**: [ATT-3991](https://atrainingtracker.atlassian.net/browse/ATT-3991)  
**Subtask**: `ATT-4167` (`[Test-Spec]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `Next Minor Release` (V4.9.40)  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-MIG-042` (*Samples Table Name Escaping, Case-Insensitive Deduplication, Deterministic Disambiguation & Structural DDL Construction*)  
**Test Case Mapping**: `TST-MIG-039`  
**Branch**: `feature/ATT-3991`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. System Requirements & Functional Specifications

### 1.1 Technical Constraints (MUST / MUST NOT)
1. **Strict Identifier Escaping**:
   - `WorkoutSamplesDatabaseManager.getTableName(String workoutName)` MUST escape every embedded double-quote character (`"`) as two double-quotes (`""`) per standard SQL-92 / SQLite identifier quoting rules (`"\"" + workoutName.replace("\"", "\"\"") + "\""`).
   - `getTableName()` MUST reject null, empty, blank, or literal `"null"` (case-insensitive) input with `IllegalArgumentException`.
2. **Preservation of Existing Table Naming Scheme**:
   - The system MUST NOT migrate existing workout table names from `<baseFileName>` to synthetic identifiers (such as `samples_<workoutId>`). Historical SQLite database tables, backup archives, and exporters MUST continue to resolve sample tables via `getTableName(baseFileName)`.
3. **Prohibition of Destructive Table Drops**:
   - When importing workouts from `.attbackup` archives or external files (GPX, TCX, FIT), the system MUST NOT drop or overwrite any pre-existing sample tables.
4. **Prohibition of Unvalidated External DDL Execution**:
   - `ImportEngine` MUST NOT execute raw, unverified `CREATE TABLE` or DDL strings extracted directly from `sqlite_master` in imported archives.
   - Column names MUST be structurally whitelisted against `^[a-zA-Z0-9_]+$` and column data types MUST be whitelisted against recognized SQLite storage classes (`INTEGER`, `REAL`, `TEXT`, `BLOB`, `NUMERIC`). Multi-statement queries containing semicolons (`;`) MUST be strictly forbidden.

### 1.2 Functional Behavior (SHALL)
1. **Case-Insensitive Duplicate Detection**:
   - `ImportEngine` and `LegacyImportEngine` SHALL query candidate workout base names against `WorkoutSummaries.FILE_BASE_NAME` using `COLLATE NOCASE`.
   - If an incoming workout matches an existing record's `FILE_BASE_NAME` (case-insensitive) AND matches its start timestamp `TIME_START`, the system SHALL classify it as a **true duplicate** and skip importing without modifying database state.
2. **Deterministic Collision Disambiguation**:
   - If an incoming workout collides on `FILE_BASE_NAME` (case-insensitive) or table existence in `WorkoutSamples.db` with a *different* start timestamp, the system SHALL deterministically disambiguate the workout name using the start timestamp epoch seconds (`${baseFileName}_${epochSeconds}`).
   - If a sub-second collision or previous collision candidate exists, the system SHALL increment a monotonic counter suffix (`_${epochSeconds}_${subSecondCounter}`) to guarantee uniqueness.
   - The disambiguated name SHALL be stored in `WorkoutSummaries.FILE_BASE_NAME` and used for creating the target sample table.
3. **Structural DDL Construction**:
   - In `ImportEngine.copySamplesTable`, the system SHALL inspect the source table schema via `PRAGMA table_info`, filter valid columns, and construct the `CREATE TABLE IF NOT EXISTS` statement internally using `getTableName(targetBaseName)`.
4. **Covering Index for Case-Insensitive Queries**:
   - `WorkoutSummariesDatabaseManager.onCreate()` SHALL create index `idx_summaries_file_base_name_nocase` on `tblWorkoutSummaries (fileBaseName COLLATE NOCASE)` to maintain $O(\log N)$ lookup performance.

---

## 2. Acceptance Criteria (Given-When-Then)

### Acceptance Criterion 1: Safe SQL Identifier Escaping with Embedded Double Quotes
- **Given** a workout name containing double-quotes (e.g. `My "Fast" Ride`),
- **When** `WorkoutSamplesDatabaseManager.getTableName()` is invoked,
- **Then** the returned identifier SHALL equal `"\"My \"\"Fast\"\" Ride\""`, and passing this identifier into `CREATE TABLE`, `INSERT`, `SELECT`, and `DROP TABLE` in SQLite SHALL execute cleanly without syntax errors or injection.

### Acceptance Criterion 2: Case-Insensitive Collision Protection
- **Given** an existing workout with `FILE_BASE_NAME = "morning_ride"` and sample table `"morning_ride"` containing time-series data,
- **When** an imported file or backup containing `Morning_Ride` with a distinct start timestamp is processed,
- **Then** the existing `"morning_ride"` table SHALL NOT be dropped or modified, all pre-existing samples SHALL remain intact, and the incoming workout SHALL be inserted under a disambiguated name with its own separate sample table.

### Acceptance Criterion 3: True Duplicate Skip on Case-Insensitive Match
- **Given** an existing workout `morning_ride` recorded at timestamp `T`,
- **When** an import file `Morning_Ride` with the exact same timestamp `T` is presented,
- **Then** the import engine SHALL identify the entry as a duplicate and skip it cleanly without dropping tables or inserting duplicate summary rows.

### Acceptance Criterion 4: Structural DDL Construction & Injection Immunity
- **Given** a malicious `.attbackup` archive whose `sqlite_master` contains a crafted DDL statement with trailing commands (e.g. `CREATE TABLE evil (...); DROP TABLE tblWorkoutSummaries;`),
- **When** `ImportEngine.copySamplesTable` runs,
- **Then** the raw SQL string is ignored, column names and types are parsed via `PRAGMA table_info`, and a clean `CREATE TABLE IF NOT EXISTS` statement is constructed locally with zero multi-statement injection execution.

---

## 3. Chesterton's Fence & Requirement Archaeology (`REQ-MIG-042`)

1. **Original Requirement ID & Target**:
   - Net-new requirement `REQ-MIG-042`, extending `REQ-MIG-031` (`ATT-2023`) and `REQ-TRK-026` (`ATT-3980`).
   - Targets `WorkoutSamplesDatabaseManager.java`, `ImportEngine.kt`, and `LegacyImportEngine.kt`.
2. **Historical Origin & Commit Trace**:
   - Discovered during whole-app code review of `feature/ATT-3921` (October 2026), tracked under ticket `ATT-3991`.
3. **Root Reason for Existing Formulation**:
   - `WorkoutSamplesDatabaseManager.getTableName` simply wrapped names in double quotes (`"\"" + name + "\""`) assuming input names would never contain quotes.
   - `existsInTarget` and `isWorkoutExisting` used SQLite's default binary collation for string equality, failing to detect that `"Morning_Ride"` collides with `"morning_ride"` in SQLite's case-insensitive table namespace, leading to destructive `DROP TABLE IF EXISTS` operations.
4. **Preservation of Core Invariants**:
   - Preservation of existing file-based table naming scheme without migrating to synthetic names, O(log N) indexed queries via `COLLATE NOCASE`, and 100% clean-room test pass rate MUST NOT be broken.

---

## 4. Test Specifications

### 4.1 TST-MIG-039.1: Embedded Double Quote Escaping Unit Test
- **File**: `com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManagerEscapingTest.kt`
- **Method**: `testTableName_escapesEmbeddedDoubleQuotesCorrectly()`
- **Procedure**:
  1. Call `WorkoutSamplesDatabaseManager.getTableName("My \"Fast\" Ride")`.
  2. Assert that returned string equals `"\"My \"\"Fast\"\" Ride\""`.
  3. Create table in real SQLite database using returned identifier.
  4. Insert sample row and query back; assert success without `SQLiteException`.

### 4.2 TST-MIG-039.2: Case-Insensitive Collision Protection Unit Test
- **File**: `com.atrainingtracker.trainingtracker.migration.WorkoutImportCollisionTest.kt`
- **Method**: `testImport_doesNotDropExistingCaseInsensitiveCollidingTable()`
- **Procedure**:
  1. Create existing workout `morning_ride` with 10 sample rows in `WorkoutSamples.db`.
  2. Attempt to import `Morning_Ride` with different `TIME_START`.
  3. Verify that `morning_ride` table still exists with all 10 sample rows intact.
  4. Verify that new workout is disambiguated with a timestamp suffix (e.g. `Morning_Ride_1710000000`).

### 4.3 TST-MIG-039.3: True Duplicate Skip Unit Test
- **File**: `com.atrainingtracker.trainingtracker.migration.WorkoutImportCollisionTest.kt`
- **Method**: `testImport_skipsTrueDuplicateCaseInsensitive()`
- **Procedure**:
  1. Create existing workout `morning_ride` with timestamp `T`.
  2. Import workout `Morning_Ride` with timestamp `T`.
  3. Assert return status is `DUPLICATE_SKIPPED` and zero new tables/rows are added.

### 4.4 TST-MIG-039.4: Sub-Second Collision Disambiguation Test
- **File**: `com.atrainingtracker.trainingtracker.migration.WorkoutImportCollisionTest.kt`
- **Method**: `testImport_subSecondCollision_advancesDisambiguationCounter()`
- **Procedure**:
  1. Simulate two distinct workouts sharing identical base names and identical epoch timestamps.
  2. Verify that the secondary import appends monotonic counter (e.g. `_1`) avoiding collision.

### 4.5 TST-MIG-039.5: Structural DDL Construction & Injection Immunity Test
- **File**: `com.atrainingtracker.trainingtracker.migration.BackupImportSecurityTest.kt`
- **Method**: `testCopySamplesTable_rejectsMaliciousDDLAndBuildsStructurally()`
- **Procedure**:
  1. Provide mock source table containing valid columns and a crafted table name with quotes or semicolons.
  2. Verify `ImportEngine.copySamplesTable` constructs clean `CREATE TABLE IF NOT EXISTS` based solely on `PRAGMA table_info` and whitelisted types.
  3. Verify zero injection commands are executed against target database.

---

## 5. Bidirectional Traceability Matrix

| Requirement ID | Technical Constraint | Functional Behavior | Test Case ID | Test Target | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `REQ-MIG-042.1` | SQL-92 `""` quote escaping | Safe identifier resolution | `TST-MIG-039.1` | `WorkoutSamplesDatabaseManagerEscapingTest.kt` | Specified |
| `REQ-MIG-042.2` | `COLLATE NOCASE` deduplication | True duplicate skip | `TST-MIG-039.3` | `WorkoutImportCollisionTest.kt` | Specified |
| `REQ-MIG-042.3` | Deterministic disambiguation | No dropping colliding tables | `TST-MIG-039.2`, `TST-MIG-039.4` | `WorkoutImportCollisionTest.kt` | Specified |
| `REQ-MIG-042.4` | Structural DDL via `PRAGMA table_info` | Zero raw SQL execution | `TST-MIG-039.5` | `BackupImportSecurityTest.kt` | Specified |
| `REQ-MIG-042.5` | Dirty state pre-flight check | One-time collision healing | `TST-MIG-039.2` | `WorkoutSummariesDatabaseManager.java` | Specified |
