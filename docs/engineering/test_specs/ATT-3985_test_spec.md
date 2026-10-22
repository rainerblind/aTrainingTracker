# Stage 2: Requirement & Test Specification - ATT-3985: [High] Apostrophe in device name drops every sample row of the workout

**Ticket**: [ATT-3985](https://rainerblind.atlassian.net/browse/ATT-3985)  
**Sub-task**: [ATT-4064](https://rainerblind.atlassian.net/browse/ATT-4064) (`[Req & Test Spec]`)  
**Parent Epic**: None (*Standalone Defect from Code Review 2026-10*)  
**Target Release**: `TBD (Assigned at Sprint Review per Rule 14)`  
**Active Sprint**: `2026-42.9`  
**Requirement Mapping**: `REQ-TRK-029` (*Safe SQLite Column Identifier Escaping for Custom Device Names & Rate-Limited Sample Insert Failure Telemetry in `TrackerService`*)  
**Test Spec ID**: `TST-TRK-021`  
**Branch**: `feature/ATT-3985`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Requirement Specification (`REQ-TRK-029`)

### 1.1 Problem Statement & Rationale
When recording a workout in `TrackerService.sampleAndWriteToDb()`, per-device sensor readings from paired ANT+ and Bluetooth LE sensors are stored in `WorkoutSamples.db` under columns named `<SensorType> (<DeviceName>)`. Previously, `TrackerService` wrapped those column keys in unescaped single quotes (`'...'`), stripped all apostrophes (`key.replace("'", "")`) when inspecting cursor columns in `handleMissingColumns()`, and concatenated the unescaped key into `ALTER TABLE ... ADD COLUMN`. Consequently, any paired device whose user-defined name contained an apostrophe (`'`, e.g., `Rainer's HRM`) or double quote (`"`, e.g., `Pro "2" Power`) produced invalid SQL syntax in both `insertOrThrow()` and `ALTER TABLE`, dropping every 1 Hz sample row across all sensors for the entire workout.

### 1.2 Functional & Architectural Requirements (`REQ-TRK-029`)
`TrackerService` (`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`) SHALL safely quote and unquote SQLite sample column identifiers and rate-limit sample insertion error telemetry while preserving 100% backward compatibility with the `<SensorType> (<DeviceName>)` column naming convention:

1. **Canonical SQLite Column Identifier Quoting & Unquoting Helpers (`REQ-TRK-029.1`)**:
   * `TrackerService.quoteSqlColumnIdentifier(@NonNull String rawColumnName)` SHALL wrap `rawColumnName` in standard SQL double quotes and escape any embedded double quote `"` by doubling it (`""`): `"\"" + rawColumnName.replace("\"", "\"\"") + "\""`.
   * `TrackerService.unquoteSqlColumnIdentifier(@NonNull String sqlColumnIdentifier)` SHALL recover the exact raw unquoted column name stored in SQLite (`sqlite_master` / `Cursor.getColumnNames()`):
     * When `sqlColumnIdentifier` starts and ends with `"` (`length >= 2`), it SHALL strip only the outer `"` delimiter characters and replace `""` with `"`, preserving all internal apostrophes (`'`) and quotes (`"`).
     * When `sqlColumnIdentifier` starts and ends with `'` (`length >= 2`, legacy caller compatibility), it SHALL strip only the outer `'` delimiter characters and replace `''` with `'`.
     * Otherwise, it SHALL return `sqlColumnIdentifier` unchanged (e.g., bare `SensorType.name()` or `<SensorType>_gps`).

2. **Coordinated 3-Point Column Identifier Handling in `TrackerService` (`REQ-TRK-029.2`)**:
   * **Point 1 (`sampleAndWriteToDb` Key Construction)**: For non-location device names (`deviceName != null` and not `"gps"`, `"network"`, or `"google_fused"`), `TrackerService.sampleAndWriteToDb()` SHALL construct the raw column name `sensorType.name() + " (" + deviceName + ")"` and quote it via `quoteSqlColumnIdentifier(...)` before inserting into `samplingValues` and `sensorName2Type`.
   * **Point 2 (`handleMissingColumns` Column Existence Check)**: In `TrackerService.handleMissingColumns()`, each `key` in `values.keySet()` SHALL be unquoted via `String queryKey = unquoteSqlColumnIdentifier(key)` before querying `cursor.getColumnIndex(queryKey)` (with a fallback scan of `cursor.getColumnNames()` if `getColumnIndex` returns `< 0`), ensuring internal apostrophes and quotes are never stripped when checking whether the column already exists.
   * **Point 3 (`handleMissingColumns` `ALTER TABLE` DDL)**: When adding a missing column in `handleMissingColumns()`, `TrackerService` SHALL quote the column identifier via `quoteSqlColumnIdentifier(queryKey)` in `samplesDb.execSQL("ALTER TABLE " + tableName + " ADD COLUMN " + quoteSqlColumnIdentifier(queryKey) + " " + type + " null;");`.

3. **Rate-Limited Sample Insert Failure Counter & Diagnostic Telemetry (`REQ-TRK-029.3`)**:
   * `TrackerService` SHALL maintain an atomic counter `mSampleInsertFailureCount` (`AtomicInteger`, exposed via `@VisibleForTesting public int getSampleInsertFailureCount()`).
   * Whenever a 1 Hz sample row fails to insert (e.g., unrecoverable `SQLiteException`, missing table with invalid `baseFileName`, or `ALTER TABLE`/post-recovery insert failure), `TrackerService` SHALL increment `mSampleInsertFailureCount` and emit rate-limited `Log.e(TAG, ...)` diagnostics on the initial burst (`count <= 3`) and every `SAMPLE_INSERT_FAILURE_LOG_INTERVAL` (`60`) failures thereafter (`count % 60 == 0`), including `failureCount` in the log message.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Apostrophe in Device Name)**:
  * *Given* a connected sensor whose device name contains an apostrophe (e.g., `Rainer's HRM`),
  * *When* 1 Hz workout samples are recorded via `TrackerService.sampleAndWriteToDb()`,
  * *Then* the missing column `HR (Rainer's HRM)` is dynamically added via `ALTER TABLE`, all 1 Hz sample rows (including primary sensors and `HR (Rainer's HRM)`) are persisted into the workout sample table without SQL syntax errors, and subsequent ticks insert cleanly without re-running `ALTER TABLE`.
* **Criterion 2 (Double Quotes and Combined Quotes in Device Name)**:
  * *Given* connected sensors whose device names contain double quotes (e.g., `Pro "2" Power`) or both apostrophes and double quotes (e.g., `Rainer's "Pro" HRM`),
  * *When* 1 Hz workout samples are recorded via `TrackerService.sampleAndWriteToDb()`,
  * *Then* the columns `POWER (Pro "2" Power)` and `HR (Rainer's "Pro" HRM)` are dynamically added and populated across all ticks, and `CSVFileWriter` / `Cursor.getColumnNames()` resolves their exact `<SensorType> (<DeviceName>)` column names.
* **Criterion 3 (Rate-Limited Failure Logging with Counter)**:
  * *Given* a persistent unrecoverable SQLite sample insert failure (e.g., `SQLITE_FULL`),
  * *When* repeated 1 Hz sample writes fail,
  * *Then* `getSampleInsertFailureCount()` increments on every failed sample and `Log.e` is emitted with `failureCount` on the initial burst (`count <= 3`) and every 60th failure (`count % 60 == 0`) without flooding logcat on intermediate ticks.

### 1.4 System Invariants
1. **Column Naming Backward Compatibility**: Sample column names stored in `sqlite_master` and returned by `Cursor.getColumnNames()` MUST remain `<SensorType> (<DeviceName>)` (and `<SensorType>_<provider>` for location sources). Opaque device IDs MUST NOT be used.
2. **Exporter Compatibility**: `CSVFileWriter` regex matching (`(.*)( \(.*\)|_gps|_network|_google_fused)`) and `Cursor.getColumnIndex(columnName)` MUST work without modification on columns created with `quoteSqlColumnIdentifier`.
3. **Zero UI/String Impact**: No UI strings are added or modified; existing 9-language localization parity remains 100% intact.

---

## 2. Test Specification (`TST-TRK-021`)

### Test Case 1: `testQuoteAndUnquoteSqlColumnIdentifier_preservesApostrophesAndEscapesDoubleQuotes` (`TST-TRK-021.1`)
* **Scope**: Unit Test (`TrackerServiceDeviceNameQuotingTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceDeviceNameQuotingTest.kt`
* **Preconditions**: Pure static helper methods `TrackerService.quoteSqlColumnIdentifier` and `TrackerService.unquoteSqlColumnIdentifier`.
* **Action**: Evaluate round-trip quoting and unquoting for standard device names (`HR (Polar H10)`), apostrophes (`HR (Rainer's HRM)`), double quotes (`POWER (Pro "2" Power)`), combined quotes (`CADENCE (Rainer's "Pro" Sensor)`), legacy single-quoted identifiers (`'HR (Polar H10)'`), and bare sensor columns (`HR`, `SPEED_mps_gps`).
* **Expected Result**:
  * `quoteSqlColumnIdentifier("HR (Rainer's HRM)")` == `"\"HR (Rainer's HRM)\""`.
  * `quoteSqlColumnIdentifier("POWER (Pro \"2\" Power)")` == `"\"POWER (Pro \"\"2\"\" Power)\""`.
  * `unquoteSqlColumnIdentifier(quoteSqlColumnIdentifier(raw))` == `raw` for all inputs, preserving internal `'` and `"` characters.

### Test Case 2: `testSampleAndWriteToDb_withApostropheAndDoubleQuoteDeviceNames_insertsAllSampleRowsInSqlite` (`TST-TRK-021.2`)
* **Scope**: Robolectric Real SQLite Integration Test (`TrackerServiceDeviceNameQuotingTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceDeviceNameQuotingTest.kt`
* **Preconditions**: Real in-memory/temporary Robolectric `WorkoutSamplesDatabaseManager` table created via `createNewTable(baseFileName, SensorType.values().toList())`, connected sensors returning `SensorData` for primary `HR`, `POWER`, `TIME_TOTAL`, `TIME_ACTIVE` plus device-specific sensors with names `"Rainer's HRM"`, `"Pro \"2\" Power"`, and `"Rainer's \"Pro\" Cadence"`.
* **Action**: Execute multiple 1 Hz `sampleAndWriteToDb()` ticks, then join an additional mid-workout sensor (`"Stages's \"Gen 3\" L/R"`) on tick 3 so `handleMissingColumns()` runs again while `"Rainer's HRM"` and `"Pro \"2\" Power"` already exist in the table.
* **Expected Result**:
  * Zero sample rows are dropped (`rowCount == 3`, `service.sampleInsertFailureCount == 0`).
  * `cursor.getColumnNames()` contains exact unquoted names `"HR (Rainer's HRM)"`, `"POWER (Pro \"2\" Power)"`, `"CADENCE (Rainer's \"Pro\" Cadence)"`, and `"TORQUE (Stages's \"Gen 3\" L/R)"`.
  * All primary sensor values and per-device column values match across all 3 rows, and adding the 4th sensor on tick 3 does not re-execute `ALTER TABLE` for the already-existing apostrophe/quote columns.

### Test Case 3: `testSampleInsertFailureCounter_rateLimitsRepeatedSqliteErrors` (`TST-TRK-021.3`)
* **Scope**: Unit Test (`TrackerServiceDeviceNameQuotingTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceDeviceNameQuotingTest.kt`
* **Preconditions**: Mock `SQLiteDatabase` throwing unrecoverable `SQLiteException("database or disk is full (code 13 SQLITE_FULL)")`.
* **Action**: Invoke `handleMissingColumns()` across 65 consecutive failed ticks.
* **Expected Result**:
  * `service.sampleInsertFailureCount` equals `65`.
  * `Log.e` is invoked on ticks `1`, `2`, `3`, and `60` (4 times total across 65 failures) with the failure counter formatted in the log message, suppressing intermediate logcat spam on ticks `4..59` and `61..65`.

### Test Case 4: Clean-Room Regression Suite (`TST-TRK-021.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the entire unit test suite with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-021.1` | Unit | `TrackerService.quoteSqlColumnIdentifier`, `TrackerService.unquoteSqlColumnIdentifier` | `REQ-TRK-029.1` | Specified |
| `TST-TRK-021.2` | SQLite Integration | `TrackerService.sampleAndWriteToDb`, `TrackerService.handleMissingColumns` | `REQ-TRK-029.1`, `REQ-TRK-029.2` | Specified |
| `TST-TRK-021.3` | Unit | `TrackerService.handleMissingColumns`, `TrackerService.getSampleInsertFailureCount` | `REQ-TRK-029.3` | Specified |
| `TST-TRK-021.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
