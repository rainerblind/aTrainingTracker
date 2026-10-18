# Stage 3: Implementation Plan - ATT-3985: [High] Apostrophe in device name drops every sample row of the workout

**Ticket**: [ATT-3985](https://rainerblind.atlassian.net/browse/ATT-3985)  
**Sub-task**: [ATT-4065](https://rainerblind.atlassian.net/browse/ATT-4065) (`[Impl-Plan]`)  
**Parent Epic**: None (*Standalone Defect from Code Review 2026-10*)  
**Target Release**: `TBD (Assigned at Sprint Review per Rule 14)`  
**Active Sprint**: `2026-42.9`  
**Requirement Mapping**: `REQ-TRK-029`  
**Test Mapping**: `TST-TRK-021`  
**Branch**: `feature/ATT-3985`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Description & Background

In `TrackerService.java`, 1 Hz sample columns for paired ANT+ and Bluetooth LE sensors are named `<SensorType> (<DeviceName>)`. Previously, `sampleAndWriteToDb()` wrapped these keys in unescaped single quotes (`"'" + sensorName + "'"`), and `handleMissingColumns()` stripped all apostrophes (`key.replace("'", "")`) when looking up existing columns and concatenated the unescaped key into `ALTER TABLE ... ADD COLUMN`. When a device name contains an apostrophe (`'`, e.g., `Rainer's HRM`) or double quotes (`"`, e.g., `Pro "2" Power`), SQLite fails to compile both the `INSERT` statement and the `ALTER TABLE` statement, causing `sampleAndWriteToDb()` to abort the per-second transaction and drop every sample row of the workout across all sensors.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-TRK-029` (*Safe SQLite Column Identifier Escaping for Custom Device Names & Rate-Limited Sample Insert Failure Telemetry in `TrackerService`*)
* **Test Mapping**: `TST-TRK-021` (*Safe SQLite Column Identifier Escaping for Custom Device Names (`'` and `"`) & Rate-Limited Sample Insert Failure Telemetry Verification Suite*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites (`TrackerServiceResumeTest`, `TrackerServiceResilienceTest`, `TrackerServiceAsyncInitTest`, and all exporter tests) continue to pass cleanly.
2. **Column Naming Backward Compatibility**: Sample column names stored in `sqlite_master` and returned by `Cursor.getColumnNames()` remain `<SensorType> (<DeviceName>)` (and `<SensorType>_<provider>` for location sources). Opaque device IDs are never used.
3. **Thread Safety & Dispatcher Affinity**: Database operations remain isolated to `mDbExecutor` single-thread executor, and failure counting uses thread-safe `AtomicInteger`.
4. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `TrackerService` SQLite Column Identifier Quoting & Unquoting (`TrackerService.java`)

1. **Static Helper Methods (`REQ-TRK-029.1`)**:
   * `@VisibleForTesting @NonNull public static String quoteSqlColumnIdentifier(@NonNull String rawColumnName)`:
     * Wraps `rawColumnName` in SQL standard double quotes (`"..."`) and escapes any embedded double quote `"` by doubling it (`""`):
       ```java
       public static String quoteSqlColumnIdentifier(@NonNull String rawColumnName) {
           return "\"" + rawColumnName.replace("\"", "\"\"") + "\"";
       }
       ```
   * `@VisibleForTesting @NonNull public static String unquoteSqlColumnIdentifier(@Nullable String sqlColumnIdentifier)`:
     * Recovers the exact unquoted column name stored in SQLite (`sqlite_master` / `Cursor.getColumnNames()`) without stripping internal apostrophes (`'`) or double quotes (`"`):
       ```java
       @NonNull
       public static String unquoteSqlColumnIdentifier(@Nullable String sqlColumnIdentifier) {
           if (sqlColumnIdentifier == null) {
               return "";
           }
           if (sqlColumnIdentifier.length() >= 2
                   && sqlColumnIdentifier.startsWith("\"")
                   && sqlColumnIdentifier.endsWith("\"")) {
               return sqlColumnIdentifier.substring(1, sqlColumnIdentifier.length() - 1)
                       .replace("\"\"", "\"");
           }
           if (sqlColumnIdentifier.length() >= 2
                   && sqlColumnIdentifier.startsWith("'")
                   && sqlColumnIdentifier.endsWith("'")) {
               return sqlColumnIdentifier.substring(1, sqlColumnIdentifier.length() - 1)
                       .replace("''", "'");
           }
           return sqlColumnIdentifier;
       }
       ```
   * `@VisibleForTesting static boolean hasColumnInCursor(@NonNull Cursor cursor, @NonNull String rawColumnName)`:
     * Checks `cursor.getColumnIndex(rawColumnName) >= 0`, and if `< 0` (e.g., if `rawColumnName` contains a period `.` which Android's `AbstractWindowedCursor.getColumnIndex` strips before the last dot), falls back to scanning `cursor.getColumnNames()` for an exact or case-insensitive match.

2. **Coordinated 3-Point Fix in `TrackerService.java` (`REQ-TRK-029.2`)**:
   * **Point 1 (`sampleAndWriteToDb`, lines 1432–1441)**:
     Replace:
     ```java
     sensorName += " (" + deviceName + ")";
     sensorName = "'" + sensorName + "'";
     ```
     with:
     ```java
     sensorName = quoteSqlColumnIdentifier(sensorName + " (" + deviceName + ")");
     ```
   * **Point 2 (`handleMissingColumns` column check, lines 1661–1663)**:
     Replace:
     ```java
     String queryKey = key.replace("'", "");
     if (cursor.getColumnIndex(queryKey) < 0) {
     ```
     with:
     ```java
     String queryKey = unquoteSqlColumnIdentifier(key);
     if (!hasColumnInCursor(cursor, queryKey)) {
     ```
   * **Point 3 (`handleMissingColumns` `ALTER TABLE`, line 1670)**:
     Replace:
     ```java
     samplesDb.execSQL("ALTER TABLE " + tableName + " ADD COLUMN " + key + " " + type + " null;");
     ```
     with:
     ```java
     String quotedColumn = quoteSqlColumnIdentifier(queryKey);
     samplesDb.execSQL("ALTER TABLE " + tableName + " ADD COLUMN " + quotedColumn + " " + type + " null;");
     ```

### Component 2: Rate-Limited Sample Insert Failure Counter & Telemetry (`TrackerService.java`)

1. **Failure Counter & Rate Limit Constants (`REQ-TRK-029.3`)**:
   * Add `public static final int SAMPLE_INSERT_FAILURE_INITIAL_BURST = 3;`
   * Add `public static final int SAMPLE_INSERT_FAILURE_LOG_INTERVAL = 60;`
   * Add `private final AtomicInteger mSampleInsertFailureCount = new AtomicInteger(0);`
   * Expose `@VisibleForTesting public int getSampleInsertFailureCount() { return mSampleInsertFailureCount.get(); }`
   * Add helper `private void logSampleInsertFailure(@NonNull String message, @Nullable Throwable cause)`:
     Increments `mSampleInsertFailureCount` (`int count = mSampleInsertFailureCount.incrementAndGet()`) and logs via `Log.e(TAG, message + " (sampleInsertFailureCount=" + count + ")", cause)` when `count <= SAMPLE_INSERT_FAILURE_INITIAL_BURST || count % SAMPLE_INSERT_FAILURE_LOG_INTERVAL == 0`.
   * Route failure paths in `handleMissingColumns()` (missing table with invalid `baseFileName`, non-column `SQLException`, null cursor, `ALTER TABLE` / retry exception) through `logSampleInsertFailure(...)`, and rate-limit the transaction-abort message in `sampleAndWriteToDb()` using `mSampleInsertFailureCount.get()`.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Gate 3 Programmatic Pre-Check (Rule 4)
* Command: `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>`
* Verify exit code `0` (`GATE_PASSED`) before modifying any production code in `app/src/...`.

### Step 2: Implement SQLite Identifier Quoting/Unquoting & Rate-Limited Failure Logging in `TrackerService.java`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`
* Changes:
  1. Add `SAMPLE_INSERT_FAILURE_INITIAL_BURST`, `SAMPLE_INSERT_FAILURE_LOG_INTERVAL`, `mSampleInsertFailureCount`, `getSampleInsertFailureCount()`, `shouldLogSampleInsertFailure(int count)`, and `logSampleInsertFailure(String, Throwable)`.
  2. Add `quoteSqlColumnIdentifier(String)`, `unquoteSqlColumnIdentifier(String)`, and `hasColumnInCursor(Cursor, String)`.
  3. Update `sampleAndWriteToDb()` to quote device-qualified column identifiers via `quoteSqlColumnIdentifier(sensorName + " (" + deviceName + ")")`.
  4. Update `handleMissingColumns()` to unquote `key` via `unquoteSqlColumnIdentifier(key)`, check `!hasColumnInCursor(cursor, queryKey)`, quote the `ALTER TABLE` identifier via `quoteSqlColumnIdentifier(queryKey)`, and log failures via `logSampleInsertFailure(...)`.

### Step 3: Author Unit & Robolectric SQLite Integration Tests (`TrackerServiceDeviceNameQuotingTest.kt`)
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceDeviceNameQuotingTest.kt`
* Changes:
  1. Implement `testQuoteAndUnquoteSqlColumnIdentifier_preservesApostrophesAndEscapesDoubleQuotes` (`TST-TRK-021.1`).
  2. Implement `testSampleAndWriteToDb_withApostropheAndDoubleQuoteDeviceNames_insertsAllSampleRowsInSqlite` (`TST-TRK-021.2`), verifying end-to-end 1 Hz sample insertion, dynamic `ALTER TABLE` column creation, mid-workout additional sensor column addition, and `CSVFileWriter` regex compatibility on real SQLite tables.
  3. Implement `testSampleInsertFailureCounter_rateLimitsRepeatedSqliteErrors` (`TST-TRK-021.3`), verifying `getSampleInsertFailureCount()` and rate-limited `Log.e` emission across 65 consecutive failures.

### Step 4: Execute Targeted Unit Tests (Stage 4 Fast Loop)
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceDeviceNameQuotingTest" --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceResumeTest" --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceResilienceTest"`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit and Robolectric SQLite tests in Stage 4 (`TrackerServiceDeviceNameQuotingTest`, `TrackerServiceResumeTest`, `TrackerServiceResilienceTest`), followed by the full clean-room regression suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-3985` allows clean revert without impacting `sprint/2026-42.9` or `develop`.
