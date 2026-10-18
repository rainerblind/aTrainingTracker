# Stage 1: Problem Domain & Root Cause Analysis: ATT-3991

**Ticket**: [ATT-3991](https://atrainingtracker.atlassian.net/browse/ATT-3991)  
**Subtask**: `ATT-4166` (`[Analysis]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `Next Minor Release` (V4.9.40)  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-MIG-042` (*Samples Table Name Escaping, Case-Insensitive Deduplication, Deterministic Disambiguation & Structural DDL Construction*)  
**Test Case Mapping**: `TST-MIG-039`  
**Branch**: `feature/ATT-3991`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Executive Summary & Problem Formulation

During the whole-app code review (October 2026), three critical vulnerabilities and data loss risks were identified in the samples table naming and workout import pipelines:

1. **Unescaped SQL Table Identifiers (SQL Injection / Syntax Errors)**:
   - In `WorkoutSamplesDatabaseManager.java:640`, `getTableName(workoutName)` returns `"\"" + workoutName + "\""`.
   - If a workout name or imported file name contains an embedded double-quote (`"`), e.g. `My "Fast" Ride`, the resulting string `"\"My \"Fast\" Ride\""` breaks out of the quoted SQL identifier.
   - When passed into SQL statements (e.g. `DROP TABLE IF EXISTS "My "Fast" Ride"`, `CREATE TABLE "My "Fast" Ride"(...)`, or `SELECT * FROM "My "Fast" Ride"`), SQLite throws a syntax error, failing the operation or creating an injection vulnerability if untrusted file names are processed.

2. **Case-Insensitive Table Name Collisions & Silent Data Loss**:
   - In SQLite, table identifiers are **case-insensitive** (`"Morning_Ride"` and `"morning_ride"` address the exact same table).
   - However, in `ImportEngine.kt` and `LegacyImportEngine.kt`, duplicate detection queries `WorkoutSummaries.TABLE` using the default SQLite binary collation (`${WorkoutSummaries.FILE_BASE_NAME} = ?`).
   - If a user has an existing workout `morning_ride` and imports a workout `Morning_Ride.fit`:
     - Binary collation considers `morning_ride != Morning_Ride`, returning `exists = false`.
     - The import pipeline proceeds to import `Morning_Ride`.
     - In `WorkoutSamplesDatabaseManager.createNewTable` or `ImportEngine.copySamplesTable`, `DROP TABLE IF EXISTS "Morning_Ride"` is executed.
     - Because SQLite table names are case-insensitive, SQLite drops the existing `"morning_ride"` table!
     - The existing workout's samples are permanently deleted.

3. **Unsanitized DDL Execution from External Backup Archives**:
   - In `ImportEngine.kt:336-344`, `copySamplesTable` reads the `sql` column verbatim from `sqlite_master` in the imported `WorkoutSamples.db` file and executes it directly via `targetDb.execSQL(createSql)`.
   - Executing arbitrary SQL from an external archive exposes the application to malicious payloads and multi-statement injection.

---

## 2. Forensic Reproduction & Call-Site Audit

### 2.1 Code Walkthrough

1. **`WorkoutSamplesDatabaseManager.java:640-645`**:
   ```java
   @NonNull
   public static String getTableName(String workoutName) {
       if (workoutName == null || workoutName.trim().isEmpty() || "null".equalsIgnoreCase(workoutName.trim())) {
           throw new IllegalArgumentException("Invalid workout baseFileName for sample table: " + workoutName);
       }
       return "\"" + workoutName + "\"";
   }
   ```
   *Flaw*: Fails to escape embedded double quotes according to standard SQL-92 / SQLite rules (replacing each `"` with `""`).

2. **`WorkoutSamplesDatabaseManager.java:418-427`**:
   ```java
   public void createNewTable(String workoutName, @NonNull List<SensorType> sensorTypes) {
       SQLiteDatabase db = getDatabase();
       String table = getTableName(workoutName);
       db.execSQL("drop table if exists " + table);
       ...
   }
   ```
   *Flaw*: Indiscriminately drops existing tables before creation. If two different workouts collide on case, the pre-existing workout's table is destroyed.

3. **`ImportEngine.kt:236-240, 331-345`**:
   ```kotlin
   private fun existsInTarget(db: SQLiteDatabase, fileBaseName: String): Boolean {
       db.query(WorkoutSummaries.TABLE, arrayOf(WorkoutSummaries.C_ID),
           "${WorkoutSummaries.FILE_BASE_NAME} = ?", arrayOf(fileBaseName), null, null, null).use {
           return it.count > 0
       }
   }
   ...
   private fun copySamplesTable(srcDb: SQLiteDatabase, targetDb: SQLiteDatabase, fileBaseName: String) {
       val tableName = WorkoutSamplesDatabaseManager.getTableName(fileBaseName)
       var createSql: String? = null
       srcDb.rawQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name=$tableName", null).use {
           if (it.moveToFirst()) createSql = it.getString(0)
       }
       if (createSql != null) {
           targetDb.execSQL("DROP TABLE IF EXISTS $tableName")
           targetDb.execSQL(createSql)
           ...
       }
   }
   ```
   *Flaw*: Binary collation in `existsInTarget` misses case variants; `copySamplesTable` executes unvalidated raw DDL from external sources while executing destructive `DROP TABLE`.

4. **`LegacyImportEngine.kt:807-823`**:
   ```kotlin
   internal fun isWorkoutExisting(db: WorkoutSummariesDatabaseManager, fileBaseName: String, ...): Boolean {
       ...
       "${WorkoutSummaries.FILE_BASE_NAME} = ?"
       ...
   }
   ```
   *Flaw*: Binary equality check allows case variants of different workouts to collide and overwrite sample tables.

### 2.2 Comprehensive Call-Site Matrix for `getTableName()`

Every module in the application that queries `WorkoutSamples.db` resolves the table identifier through `WorkoutSamplesDatabaseManager.getTableName(baseFileName)`:

| Module / Class | File Location | Invocation Context | Escaping Impact |
| :--- | :--- | :--- | :--- |
| `WorkoutSamplesDatabaseManager.java` | L238, L350, L422, L435, L448, L598 | `existsTable`, `createNewTable`, `deleteWorkout`, `pruneUnselectedRows` | Quotes in table name escaped cleanly (`""`), preventing SQL syntax errors on table drop/create. |
| `WorkoutSummariesDatabaseManager.java` | L1251, L1845 | `deleteWorkout(baseFileName)` | Safe deletion without injection or identifier parse failure. |
| `CSVFileWriter.java` | L64 | `db.query(getTableName(...), ...)` | Querying samples for CSV export handles quotes in name seamlessly. |
| `FitFileWriter.java` | L68 | `db.query(getTableName(...), ...)` | FIT export remains 100% stable with quoted names. |
| `GCFileWriter.java` | L81 | `db.query(getTableName(...), ...)` | GoldenCheetah JSON export remains stable with quoted names. |
| `GPXFileWriter.java` | L78 | `db.query(getTableName(...), ...)` | GPX export remains stable with quoted names. |
| `TCXFileWriter.java` | L86 | `db.query(getTableName(...), ...)` | TCX export remains stable with quoted names. |
| `TrackOnMapHelper.java` | L340 | `db.query(getTableName(...), ...)` | Map route rendering queries table safely. |
| `WorkoutRepository.kt` | L520, L565, L610, L675, L720, L1280 | Sample queries & time-series extraction | Repository streams query table safely. |
| `TrackerService.java` | L410, L820, L1150 | Live recording sample insertion | Safe table creation and sample writes during live sessions. |
| `ImportEngine.kt` | L332 | Archive sample table copying | Safe table creation and copying during `.attbackup` restore. |
| `LegacyImportEngine.kt` | L693 | File import sample insertion | Safe table creation and sample writes during GPX/TCX/FIT imports. |

---

## 3. Chesterton's Fence Archaeology & Backward Compatibility

1. **Why was the file-based table naming scheme introduced?**
   - Originally established in early architecture (`ATT-357`, `ATT-602`), each workout session stores its time-series sensor samples in a dedicated SQLite table inside `WorkoutSamples.db`.
   - The table name is directly derived from `WorkoutSummaries.FILE_BASE_NAME` via `WorkoutSamplesDatabaseManager.getTableName(fileBaseName)`.
2. **Why must we NOT migrate to synthetic table names (e.g. `samples_<workoutId>`)?**
   - Thousands of existing user databases in the field have hundreds of tables named after `FILE_BASE_NAME` (e.g. `"2024-05-10_143000"`).
   - `.attbackup` archives, GPX/TCX/FIT exporters, map visualizers, and cache repositories directly query `getTableName(baseFileName)`.
   - Renaming or synthesizing table names would break backward compatibility with existing backups and require massive, risky schema migrations across multi-gigabyte databases.
3. **Preservation of Intent**:
   - Keep the existing `getTableName(baseFileName)` contract intact.
   - Fortify it with strict SQL escaping (`"` $\to$ `""`).
   - Prevent collisions using case-insensitive deduplication and deterministic collision disambiguation.

---

## 4. Remediation Design & Architectural Guardrails (Gate 1 Hardening)

### 4.1 Safe Identifier Escaping
- In `WorkoutSamplesDatabaseManager.getTableName(String workoutName)`:
  - Standard SQL-92 and SQLite identifier escaping requires doubling every embedded double-quote:
    ```java
    return "\"" + workoutName.replace("\"", "\"\"") + "\"";
    ```
  - Validates that non-null, non-blank input is safely enclosed.
  - Guarantees that any workout name containing double quotes can be safely passed to `db.query()`, `db.execSQL()`, etc., without breaking syntax or allowing injection.

### 4.2 Structural DDL Construction & Strict Type Whitelisting (Zero Raw SQL Execution)
- In `ImportEngine.kt:copySamplesTable`:
  - Completely eliminate raw DDL execution (`execSQL(createSql)` from untrusted archives).
  - Use **Structural Schema Inspection**:
    1. Query the source database schema columns using `PRAGMA table_info`:
       ```kotlin
       val columns = mutableListOf<String>()
       val allowedTypes = setOf("INTEGER", "REAL", "TEXT", "BLOB", "NUMERIC")
       srcDb.rawQuery("PRAGMA table_info(" + WorkoutSamplesDatabaseManager.getTableName(srcFileBaseName) + ")", null).use { cursor ->
           while (cursor.moveToNext()) {
               val colName = cursor.getString(cursor.getColumnIndexOrThrow("name"))
               val rawType = cursor.getString(cursor.getColumnIndexOrThrow("type"))?.trim()?.uppercase() ?: "TEXT"
               // Whitelist column identifiers: only alphanumeric and underscores
               if (colName.matches(Regex("^[a-zA-Z0-9_]+$"))) {
                   val normalizedType = if (allowedTypes.any { rawType.startsWith(it) }) rawType else "TEXT"
                   columns.add("\"$colName\" $normalizedType")
               }
           }
       }
       ```
    2. Build the `CREATE TABLE` statement locally using the target table identifier:
       `val safeCreateSql = "CREATE TABLE IF NOT EXISTS $targetTableName (${columns.joinToString(", ")})"`
    3. Execute the internally constructed, sanitized DDL statement.
  - This completely eliminates regex parsing vulnerabilities, multi-statement injection, and unverified external SQL execution.

### 4.3 Atomic Deterministic Disambiguation with Sub-Second Anti-Collision Guard
When importing workouts (in both `ImportEngine.kt` and `LegacyImportEngine.kt`):
1. **Case-Insensitive Querying & Performance Indexing**:
   - Query `FILE_BASE_NAME` using `COLLATE NOCASE`:
     `${WorkoutSummaries.FILE_BASE_NAME} = ? COLLATE NOCASE`
   - To guarantee that performance does not degrade to an $O(N)$ full table scan during large batch imports on databases with thousands of workouts, add a covering index:
     `CREATE INDEX IF NOT EXISTS idx_summaries_file_base_name_nocase ON tblWorkoutSummaries (fileBaseName COLLATE NOCASE);` in `WorkoutSummariesDatabaseManager.java:onCreate()`.
2. **True Duplicate Detection**:
   - If a workout with the same `FILE_BASE_NAME` (case-insensitive) AND the same start timestamp (`TIME_START` within tolerance or exact match) already exists:
     - Treat as a **true duplicate**.
     - Skip the import cleanly (`ImportStatus.DUPLICATE_SKIPPED` or skip loop iteration).
     - Existing data and sample tables remain 100% untouched.
3. **Deterministic Disambiguation with Sub-Second Uniqueness**:
   - If a workout collides on `FILE_BASE_NAME` (case-insensitive) OR table name exists in `WorkoutSamples.db`, BUT represents a distinct activity (different `TIME_START`):
     - Base candidate is derived from the activity start epoch: `${fileBaseName}_${epochSec}`.
     - **Sub-Second Anti-Collision Guard**: If two imports happen within the exact same second or if `${fileBaseName}_${epochSec}` already exists in the target database (`isBaseNameColliding(...) || samplesDb.existsTable(...)`):
       - Append a monotonic counter or random 4-character hex suffix:
         ```kotlin
         var candidate = "${fileBaseName}_${epochSec}"
         var subSecondCounter = 1
         while (isBaseNameColliding(db, candidate) || samplesDb.existsTable(candidate)) {
             candidate = "${fileBaseName}_${epochSec}_${subSecondCounter++}"
         }
         ```
     - Update `WorkoutSummaries.FILE_BASE_NAME` to the disambiguated name atomically during summary insertion.
     - Create the new samples table as `WorkoutSamplesDatabaseManager.getTableName(candidate)`.
     - Zero tables are dropped. Both the pre-existing workout and the new workout preserve their full sample datasets.

### 4.4 Dirty State Pre-Flight Migration Check
- To handle existing databases in the field that might already contain corrupted or colliding `FILE_BASE_NAME` entries from historical versions:
  - Add a one-time pre-flight verification routine `WorkoutSummariesDatabaseManager.resolveHistoricalCollisionsIfNecessary(Context)`:
    - Scans `WorkoutSummaries.TABLE` for duplicate `FILE_BASE_NAME COLLATE NOCASE`.
    - If any historical collisions exist in metadata where two workout summary rows share a base name differing only in case, the secondary workout is deterministically renamed to `${fileBaseName}_${workoutId}` in `WorkoutSummaries` and its sample table renamed via `ALTER TABLE ... RENAME TO ...` if present.
    - Guarantees clean state before any import operation begins.

---

## 5. Formal Requirement Specification (`REQ-MIG-042`)

```markdown
| **REQ-MIG-042** | **Samples Table Name Escaping, Case-Insensitive Deduplication, Deterministic Disambiguation & Structural DDL Construction.** | The system SHALL guarantee SQL injection immunity, case-insensitive collision protection, and safe structural table construction during workout imports (ATT-3991):<br>
1. *Safe Identifier Escaping (`WorkoutSamplesDatabaseManager.java`)*:<br>
• `WorkoutSamplesDatabaseManager.getTableName(String workoutName)` SHALL enclose the workout identifier in double quotes, escaping every embedded double-quote character (`"`) as two double-quotes (`""`) per SQL-92 and SQLite identifier quoting standards.<br>
• Null, empty, blank, or literal "null" inputs MUST be rejected with `IllegalArgumentException`.<br>
2. *Case-Insensitive Duplicate Detection (`ImportEngine.kt`, `LegacyImportEngine.kt`)*:<br>
• Workout duplicate checks querying `WorkoutSummaries.FILE_BASE_NAME` SHALL evaluate candidate names using `COLLATE NOCASE`.<br>
• If a candidate workout has the same `FILE_BASE_NAME` (case-insensitive) AND matches the start timestamp `TIME_START`, the system SHALL classify it as a duplicate and skip importing without modifying existing database rows or sample tables.<br>
3. *Deterministic Collision Disambiguation & Sub-Second Guard (`ImportEngine.kt`, `LegacyImportEngine.kt`)*:<br>
• If an incoming workout collides on `FILE_BASE_NAME` (case-insensitive) or table existence in `WorkoutSamples.db` with a different start timestamp, the system SHALL NOT drop the existing table.<br>
• The system SHALL deterministically disambiguate the base file name using the workout's start epoch seconds (`${baseFileName}_${epochSeconds}`), guarded against sub-second collisions with a monotonic counter, updating both `WorkoutSummaries.FILE_BASE_NAME` and the newly created samples table.<br>
4. *Structural DDL Construction & Injection Elimination (`ImportEngine.kt`)*:<br>
• `ImportEngine.copySamplesTable` SHALL NOT execute raw SQL statements from external backup archives.<br>
• The table schema SHALL be structurally inspected via `PRAGMA table_info`, column identifiers validated against `^[a-zA-Z0-9_]+$`, data types whitelisted against `INTEGER`, `REAL`, `TEXT`, `BLOB`, `NUMERIC`, and the `CREATE TABLE IF NOT EXISTS` statement constructed locally using `WorkoutSamplesDatabaseManager.getTableName(targetBaseName)`.<br>
• The method SHALL NOT execute unconditional `DROP TABLE IF EXISTS` on colliding tables.<br>
5. *Pre-Flight Migration & Dirty State Healing (`WorkoutSummariesDatabaseManager.java`)*:<br>
• On application upgrade, the system SHALL execute a one-time pre-flight check scanning for historical case-insensitive collisions in `WorkoutSummaries`, healing colliding entries deterministically before new imports run.
```

---

## 6. Formal Test Case Specification (`TST-MIG-039`)

```markdown
| **TST-MIG-039** | `ATT-3991` | **Samples Table Identifier Escaping, Case-Insensitive Collision Disambiguation & Structural DDL Verification** | `REQ-MIG-042` | 
1. *Embedded Double Quote Escaping Unit Test*:<br>
• Call `WorkoutSamplesDatabaseManager.getTableName("My \"Fast\" Ride")`.<br>
• Assert result equals `"\"My \"\"Fast\"\" Ride\""`.<br>
• Verify table creation, sample insertion, and query succeed in SQLite without syntax error.<br>
2. *Case-Insensitive Collision Disambiguation Unit Test*:<br>
• Insert existing workout `morning_ride` with samples into database.<br>
• Import workout `Morning_Ride` with distinct timestamp.<br>
• Assert that existing table `"morning_ride"` is NOT dropped and retains all original samples.<br>
• Assert that new workout is inserted with disambiguated name and its samples table created successfully.<br>
3. *Sub-Second Collision Disambiguation Test*:<br>
• Simulate two imports sharing identical file base names and identical timestamps.<br>
• Assert that the second import deterministically advances its suffix (`_1`) and avoids collision.<br>
4. *True Duplicate Case-Insensitive Skip Test*:<br>
• Provide workout with same name (differing only in case) and same timestamp.<br>
• Verify import skips without creating duplicate tables or modifying database.<br>
5. *Structural DDL Construction & Injection Immunity Test*:<br>
• Provide archive with crafted/malicious DDL in `sqlite_master` (e.g. multi-statement with `DROP TABLE`, invalid types, or comments).<br>
• Verify `ImportEngine` ignores raw DDL, inspects schema via `PRAGMA table_info`, whitelists types, and constructs clean table without executing injected commands.<br>
6. *Clean-Room Regression Suite*:<br>
• Run `./gradlew testDebugUnitTest` verifying 100% pass rate.
```

---

## 7. Scope Bounding & Non-Goals

- **In Scope**:
  - `WorkoutSamplesDatabaseManager.java`: quote escaping in `getTableName()`, covering index `idx_summaries_file_base_name_nocase`, and dirty state pre-flight check.
  - `ImportEngine.kt`: case-insensitive duplicate check, deterministic collision disambiguation with sub-second guard, structural DDL construction from `PRAGMA table_info` with type whitelisting.
  - `LegacyImportEngine.kt`: case-insensitive duplicate check in `isWorkoutExisting`, deterministic collision disambiguation in `persistParsedWorkout`.
  - Unit tests verifying quote escaping, collision preservation, sub-second collision handling, and structural DDL safety.
- **Out of Scope**:
  - Renaming existing non-colliding historical tables in user databases.
  - Modifying live GPS or sensor sampling in `TrackerService`.
