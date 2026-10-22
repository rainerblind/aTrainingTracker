# Stage 3: Implementation Plan: ATT-3991

**Ticket**: [ATT-3991](https://atrainingtracker.atlassian.net/browse/ATT-3991)  
**Subtask**: `ATT-4168` (`[Plan]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `Next Minor Release` (V4.9.40)  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-MIG-042` (*Samples Table Name Escaping, Case-Insensitive Deduplication, Deterministic Disambiguation & Structural DDL Construction*)  
**Test Case Mapping**: `TST-MIG-039`  
**Branch**: `feature/ATT-3991`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Executive Summary & Architectural Design (SWE.2)

### 1.1 Architecture & Component Boundaries
The remediation targets the sample table naming, deduplication, and import pipelines across four components:

1. **Identifier Escaping & Length Guard in `WorkoutSamplesDatabaseManager.java`**:
   - Update `getTableName(workoutName)` to escape embedded double quotes (`workoutName.replace("\"", "\"\"")`) per SQL-92 / SQLite rules.
   - **Length Guard Refinement**: SQLite's standard identifier limit is 64 characters. To guarantee safety:
     - Escaped identifiers are enclosed in double quotes: `"\"" + escaped + "\""`.
     - In `disambiguateBaseName`, base file names are truncated so that the final unquoted identifier (including timestamp and counter suffix) does not exceed 50 characters, ensuring that even if quotes are present, the resulting SQL identifier is strictly <= 60 characters.
     - `getTableName(workoutName)` asserts/validates that the resulting unquoted length does not exceed SQLite's 64-character limit, throwing `IllegalArgumentException` on invalid or overflow input.
   - Add `idx_summaries_file_base_name_nocase` index in `onCreate()` for $O(\log N)$ case-insensitive performance.

2. **Structural DDL Construction & Schema Fidelity in `ImportEngine.kt`**:
   - In `copySamplesTable`, completely eliminate raw SQL execution from external `sqlite_master`.
   - Inspect source schema via `PRAGMA table_info`, whitelist column names (`^[a-zA-Z0-9_]+$`) and data types (`INTEGER`, `REAL`, `TEXT`, `BLOB`, `NUMERIC`).
   - Reconstruct column definitions with full constraint fidelity:
     - Data type mapping.
     - `NOT NULL` constraints (when `notnull == 1`).
     - `DEFAULT` values (when `dflt_value != null`).
     - `PRIMARY KEY` constraints (when `pk > 0`).
   - **Schema Fidelity & Indexes/Triggers**: Sample tables in aTrainingTracker are time-series sample buckets (`WorkoutSamplesDatabaseManager`) without foreign key constraints, secondary indexes, or custom triggers (verified via `WorkoutSamplesDbHelper`). If any secondary index exists on the source samples table, query `PRAGMA index_list` and reconstruct it using validated column names; triggers are explicitly not supported on dynamic sample tables to avoid code execution vulnerabilities.
   - Build `CREATE TABLE IF NOT EXISTS ${getTableName(targetBaseName)} (...)` locally.
   - Remove destructive `DROP TABLE IF EXISTS` on colliding tables.

3. **Atomic Deterministic Collision Disambiguation & Transactionality**:
   - **Foreign Key Integrity**: In the application schema, `WorkoutSummaries` does not use SQLite foreign keys to dynamic samples tables; the link is purely symbolic via `WorkoutSummaries.FILE_BASE_NAME`. When a disambiguated `effectiveBaseFileName` is generated, it is stored in `WorkoutSummaries.FILE_BASE_NAME` and the corresponding samples table is named `getTableName(effectiveBaseFileName)`, maintaining 1:1 symbolic reference integrity.
   - **Counter Persistence & High-Frequency Batching**:
     - Maintain an in-memory `AtomicInteger importCounter` within `ImportEngine` (and across import sessions via `System.currentTimeMillis()` millisecond epoch base) ensuring that high-frequency batch imports within the same second or across re-instantiations receive strictly unique monotonic suffixes.
   - In `ImportEngine.kt`:
     - Wrap the entire check-disambiguate-create-insert cycle inside an exclusive database transaction block across `targetSummariesDb` and `targetSamplesDb`.
     - Query `WorkoutSummaries.FILE_BASE_NAME` with `COLLATE NOCASE`.
     - **True Duplicate**: If `FILE_BASE_NAME` matches (case-insensitive) AND `TIME_START` matches (or within tolerance), skip cleanly (`DUPLICATE_SKIPPED`).
     - **Collision on Distinct Activity**: If `FILE_BASE_NAME` collides case-insensitively but `TIME_START` differs, deterministically derive a unique base file name: `${truncatedBaseName}_${epochSeconds}`. If a candidate name already exists in `targetSummariesDb` (`COLLATE NOCASE`) or `targetSamplesDb` (`sqlite_master`), loop appending `_${importCounter.incrementAndGet()}` until unique. The total identifier length is clamped <= 50 characters.
     - Insert summary with disambiguated name and create samples table without dropping pre-existing data.
   - In `LegacyImportEngine.kt`:
     - Under `importMutex.withLock`: query with `COLLATE NOCASE`.
     - If base name matches existing entry but start time is distinct, deterministically disambiguate `effectiveBaseFileName` with length guard (<= 50 chars) and unique timestamp before creating table and inserting summary.

4. **Targeted Unit Test Verification**:
   - Author `WorkoutSamplesDatabaseManagerEscapingTest.kt` verifying:
     - Quote escaping with quotes in table names (`"My \"Run\" 2026"`).
     - Escaped identifier length overflow (> 64 chars) rejection/handling.
     - Table creation and CRUD operations with complex characters.
   - Author `WorkoutImportCollisionTest.kt` verifying:
     - Case-insensitive collision preservation without table drop or data loss.
     - Sub-second / batch collision counter increments.
     - True duplicate skip behavior.
     - Transaction rollback safety on simulated failure.
   - Update `LegacyImportEngineDeduplicationTest.kt` for `COLLATE NOCASE` query selections.

---

## 2. Step-by-Step Implementation Steps

### Step 1: Escape Double Quotes & Length Guard in `WorkoutSamplesDatabaseManager.getTableName()`
- **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSamplesDatabaseManager.java`
- **Location**: Line 640
- **Action**:
  - Update `getTableName`:
    ```java
    @NonNull
    public static String getTableName(String workoutName) {
        if (workoutName == null || workoutName.trim().isEmpty() || "null".equalsIgnoreCase(workoutName.trim())) {
            throw new IllegalArgumentException("Invalid workout baseFileName for sample table: " + workoutName);
        }
        String escaped = workoutName.replace("\"", "\"\"");
        if (escaped.length() > 64) {
            throw new IllegalArgumentException("Workout table name exceeds SQLite identifier limit (64 chars): " + workoutName);
        }
        return "\"" + escaped + "\"";
    }
    ```

### Step 2: Add Case-Insensitive Index in `WorkoutSummariesDatabaseManager.onCreate()`
- **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java`
- **Location**: Lines 1708–1711
- **Action**:
  - Add covering index:
    ```java
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_summaries_file_base_name_nocase ON "
            + WorkoutSummaries.TABLE + " (" + WorkoutSummaries.FILE_BASE_NAME + " COLLATE NOCASE)");
    ```

### Step 3: Implement Structural DDL & Atomic Collision Disambiguation in `ImportEngine.kt`
- **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportEngine.kt`
- **Location**: Lines 170–240, 331–360
- **Action**:
  - In `existsInTarget`: query `"${WorkoutSummaries.FILE_BASE_NAME} = ? COLLATE NOCASE"`.
  - Transactionality: Ensure `targetSummariesDb` and `targetSamplesDb` transactions cover checking, disambiguation, table creation, and insertion.
  - Implement `disambiguateBaseName(targetSummariesDb, targetSamplesDb, originalBaseName, timeStartEpoch)`:
    - Truncate `originalBaseName` to max 35 chars.
    - Append `_${timeStartEpoch}`.
    - If candidate exists in `targetSummariesDb` (`COLLATE NOCASE`) or in `targetSamplesDb` (`sqlite_master`), loop with counter `_${importCounter.incrementAndGet()}` until unique. Max unquoted length <= 50 chars.
  - In `copySamplesTable(srcDb, targetDb, srcBaseName, targetBaseName)`:
    - Inspect source table with `PRAGMA table_info` on `getTableName(srcBaseName)`.
    - Whitelist columns and types (`INTEGER`, `REAL`, `TEXT`, `BLOB`, `NUMERIC`).
    - Capture `notnull`, `dflt_value`, and `pk` attributes to preserve column constraints:
      - Construct column def: `colName colType` + (if `notnull == 1` then ` NOT NULL` else ``) + (if `dflt_value != null` then ` DEFAULT ` + `dflt_value` else ``) + (if `pk == 1` then ` PRIMARY KEY` else ``).
    - Build `CREATE TABLE IF NOT EXISTS ${getTableName(targetBaseName)} (...)`.
    - Execute built DDL and copy rows. Do NOT drop table.

### Step 4: Implement Case-Insensitive Deduplication & Disambiguation in `LegacyImportEngine.kt`
- **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt`
- **Location**: Lines 680–705, 807–840
- **Action**:
  - In `isWorkoutExisting`: update `${WorkoutSummaries.FILE_BASE_NAME} = ?` to `${WorkoutSummaries.FILE_BASE_NAME} = ? COLLATE NOCASE`.
  - In `persistParsedWorkout`:
    - Under `importMutex.withLock`:
      - If `FILE_BASE_NAME` collides case-insensitively with a different workout (different start time), deterministically disambiguate `effectiveBaseFileName` with length guard (<= 50 chars) before calling `createNewTable` and inserting summary.

### Step 5: Author Unit Tests & Update Existing Test Suites
- **Target Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutSamplesDatabaseManagerEscapingTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/migration/WorkoutImportCollisionTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngineDeduplicationTest.kt`
- **Action**:
  - Test quote escaping in table creation, insert, and select queries.
  - Test identifier length safety and overflow rejection.
  - Test case-insensitive collision disambiguation without table drop or data loss.
  - Test sub-second collision counter increments and uniqueness.
  - Test true duplicate skip behavior.
  - Test transactional rollback integrity if import is interrupted.

### Step 6: Full Clean-Room Regression Verification
- **Command**: `./gradlew testDebugUnitTest`
- **Action**: Verify all unit test suites pass with 100% success rate (0 failures, 0 regressions).

---

## 3. System Invariants & Safety Measures

1. **Existing Table Naming Integrity & Length Guard**:
   - Table naming strictly adheres to `getTableName(baseFileName)` with SQL-92 double quoting and identifier length bounded <= 60 chars.
2. **Zero Destructive Drops**:
   - `copySamplesTable` and import routines never execute `DROP TABLE IF EXISTS` on colliding tables.
3. **Transactionality & Atomicity**:
   - Existence check, disambiguation, table creation, and record copying are executed within atomic database transactions and mutex locks to eliminate race conditions.
4. **Deterministic State & Sub-Second Collision Resilience**:
   - Timestamp epoch and persistent monotonic sub-second counter guarantee deterministic, non-colliding table and summary naming.
5. **Schema Fidelity**:
   - Reconstructed DDL preserves data types, `NOT NULL`, defaults, and `PRIMARY KEY` constraints without relying on untrusted raw SQL.
6. **Decoupled Symbolic Reference Integrity**:
   - Workout summaries reference sample tables purely through `FILE_BASE_NAME`; disambiguated names are mirrored in both places atomically, avoiding orphaned sample tables.
