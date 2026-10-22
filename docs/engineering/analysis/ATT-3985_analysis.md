# Stage 1 Analysis: ATT-3985 - [High] Apostrophe in device name drops every sample row of the workout

**Ticket**: [ATT-3985](https://rainerblind.atlassian.net/browse/ATT-3985)  
**Sub-task**: [ATT-4063](https://rainerblind.atlassian.net/browse/ATT-4063) (`[Analysis]`)  
**Parent Epic**: None (*Standalone Defect from Code Review 2026-10*)  
**Target Release**: `TBD (Assigned at Sprint Review per Rule 14)`  
**Active Sprint**: `2026-42.9`  
**Branch**: `feature/ATT-3985`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Statement & Motivation

During an active workout, `TrackerService.sampleAndWriteToDb()` records 1 Hz telemetry from all active sensors into the per-workout table in `WorkoutSamples.db`. While primary ("best") sensor readings are stored under bare `SensorType.name()` columns (e.g., `HR`, `POWER`, `SPEED_mps`) and location provider readings use `<SensorType>_<provider>` (e.g., `LATITUDE_gps`), per-device sensor readings from paired ANT+ or Bluetooth LE sensors are stored under columns named `<SensorType> (<DeviceName>)` (e.g., `HR (Polar H10)`).

Because users can freely rename paired sensors in **My Sensors** (`EditDeviceFragment` / `DevicesDatabaseManager`), a device name can legitimately contain an apostrophe (`'`, e.g., `Rainer's HRM`) or double quotes (`"`, e.g., `Pro "2" Power`).

Currently, `TrackerService` wraps per-device column keys in single quotes (`"'" + sensorName + "'"`) without escaping embedded quotes, and strips all apostrophes via `key.replace("'", "")` when checking existing columns in `handleMissingColumns()`. Consequently, whenever a connected sensor's custom name contains an apostrophe (`'`) or unescaped quote:
1. Every 1 Hz `samplesDb.insertOrThrow(tableName, null, samplingValues)` call fails with a SQLite syntax error (`near "s": syntax error`).
2. `handleMissingColumns()` fails to add the column or rejects the syntax error as an unrecoverable SQLite error, returning `false`.
3. `sampleAndWriteToDb()` aborts the entire per-second database transaction (`if (!sampleInserted) return;`), dropping **all** sensors' sample data for every second of the workout as well as skipping the per-second `WorkoutSummaries` and `Extrema` database updates.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Verification of the Defect in `TrackerService.java`

Inspection of [`TrackerService.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java) confirms a 3-point defect in SQLite column identifier handling:

1. **Point 1 — Single-Quoted Key Construction in `sampleAndWriteToDb()` ([`TrackerService.java:1432-1449`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java#L1432-L1449))**:
   ```java
   String sensorName = sensorType.name();
   String deviceName = sensorData.getDeviceName();
   if (deviceName != null) {
       if (deviceName.equals("gps") || deviceName.equals("network") || deviceName.equals("google_fused")) {
           sensorName += "_" + deviceName;
       } else {
           sensorName += " (" + deviceName + ")";
           sensorName = "'" + sensorName + "'";
       }
   }
   ```
   Android's `SQLiteDatabase.insertWithOnConflict()` (called by `insertOrThrow()`) builds the SQL statement `INSERT INTO <table>(<col1>,<col2>,...) VALUES (?,?...)` by concatenating keys from `ContentValues.keySet()` directly without quoting them. When `deviceName` is `Rainer's HRM`, `sensorName` becomes `'HR (Rainer's HRM)'`. The unescaped apostrophe after `Rainer` terminates the single-quoted token prematurely, causing SQLite to throw:
   ```text
   android.database.sqlite.SQLiteException: near "s": syntax error (code 1 SQLITE_ERROR): , while compiling: INSERT INTO "2026-10-18_120000"(..., 'HR (Rainer's HRM)', ...) VALUES (...)
   ```

2. **Point 2 — Destructive Apostrophe Stripping in `handleMissingColumns()` ([`TrackerService.java:1661-1663`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java#L1661-L1663))**:
   ```java
   for (String key : values.keySet()) {
       String queryKey = key.replace("'", "");
       if (cursor.getColumnIndex(queryKey) < 0) {
   ```
   - First, because `insertOrThrow()` threw `near "s": syntax error` instead of `table ... has no column named ...`, line 1649 (`!errMsg.contains("no such column") && !errMsg.contains("has no column named")`) immediately rejects the exception and returns `false` without attempting schema recovery.
   - Second, even when `handleMissingColumns()` inspects the cursor (e.g., when triggered by a missing column), `key.replace("'", "")` strips **all** apostrophes from the string, turning `"HR (Rainer's HRM)"` into `"HR (Rainers HRM)"`. Because the actual SQLite column name retains its apostrophe (`HR (Rainer's HRM)`), `cursor.getColumnIndex("HR (Rainers HRM)")` returns `-1` even after the column exists, triggering duplicate `ALTER TABLE ... ADD COLUMN` attempts whenever another sensor joins mid-workout.

3. **Point 3 — Unescaped Identifier in `ALTER TABLE ... ADD COLUMN` ([`TrackerService.java:1670`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java#L1670))**:
   ```java
   samplesDb.execSQL("ALTER TABLE " + tableName + " ADD COLUMN " + key + " " + type + " null;");
   ```
   Concatenating `key` (`'HR (Rainer's HRM)'`) directly into `ALTER TABLE` produces invalid DDL syntax (`near "s": syntax error`). Likewise, if `deviceName` contains double quotes (e.g., `Pro "2" Power`), unescaped double quotes inside a double-quoted identifier break both `INSERT` and `ALTER TABLE`.

4. **Point 4 — Unthrottled Error Logging on Repeated 1 Hz Sample Insert Failures ([`TrackerService.java:1538-1541`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java#L1538-L1541), [`1649-1652`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java#L1649-L1652))**:
   When a persistent SQLite failure occurs at 1 Hz (e.g., `SQLITE_FULL` or schema corruption), `sampleAndWriteToDb()` and `handleMissingColumns()` log full stack traces on every single 1 Hz tick (3,600 times per hour) without maintaining a failure counter or rate-limiting repeated log emissions.

### 2.2 Backward-Compatibility Audit Across Readers & Exporters

Auditing all readers of `WorkoutSamples.db` confirms why the `<SensorType> (<DeviceName>)` column naming convention must be preserved:
* [`CSVFileWriter.java:82-107`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/CSVFileWriter.java#L82-L107) inspects `cursor.getColumnNames()` and matches `Pattern.compile("(.*)( \\(.*\\)|_gps|_network|_google_fused)")` to classify source-qualified columns and resolve their `SensorType` via `matcher.group(1)`.
* [`WorkoutSamplesDatabaseManager.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSamplesDatabaseManager.java), [`WorkoutRepository.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt), [`TCXFileWriter.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/TCXFileWriter.java), [`GPXFileWriter.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/GPXFileWriter.java), and [`FitFileWriter.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/FitFileWriter.java) query primary sensor columns (`SensorType.name()`) and location columns (`<SensorType>_gps`).
* In SQLite, both `ALTER TABLE t ADD COLUMN 'HR (Polar H10)' int null` (legacy single-quoted identifier without internal quotes) and `ALTER TABLE t ADD COLUMN "HR (Polar H10)" int null` (standard SQL double-quoted identifier) create a column whose unquoted schema name in `sqlite_master` and `cursor.getColumnNames()` is `HR (Polar H10)`.
* Using standard SQL double-quoted identifiers (`"<SensorType> (<DeviceName>)"` with embedded `"` escaped as `""`) is 100% backward-compatible with all existing tables in `WorkoutSamples.db` and `.attbackup` archives while making any combination of apostrophes (`'`) and double quotes (`"`) in `deviceName` completely safe.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Quote device-qualified sample column identifiers in `TrackerService` using SQL standard double quotes (`"..."`) with embedded double quotes escaped by doubling (`""`), preserving the `<SensorType> (<DeviceName>)` column naming convention.
  * Update all three coordinated locations in `TrackerService.java`:
    1. `samplingValues` and `sensorName2Type` key construction in `sampleAndWriteToDb()`.
    2. Unquoting `key` to raw column name `queryKey` for `cursor.getColumnIndex(queryKey)` in `handleMissingColumns()` without stripping internal apostrophes or quotes.
    3. Escaped double-quoted column identifier in `ALTER TABLE <tableName> ADD COLUMN <quotedIdentifier> <type> null;` in `handleMissingColumns()`.
  * Maintain an atomic sample insert failure counter (`mSampleInsertFailureCount`) and rate-limit repeated sample insert error logs at `WARN`/`ERROR` with the failure counter included.
  * Provide comprehensive unit and Robolectric SQLite integration tests verifying 1 Hz sample recording, dynamic `ALTER TABLE` column creation, and readback for device names containing apostrophes (`Rainer's HRM`), double quotes (`Pro "2" Power`), and combined quotes (`Rainer's "Pro" HRM`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do **NOT** migrate sample column names to opaque device database IDs (would break `CSVFileWriter`, `.attbackup` archives, and historical `WorkoutSamples.db` compatibility).
  * Do **NOT** modify exporter formats or UI screens.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-TRK-029` (extending `REQ-TRK-026.4` from `ATT-3980`). Targets `TrackerService.java` and `TrackerServiceResumeTest.kt` / `TrackerServiceDeviceNameQuotingTest.kt`.
* **Historical Origin & Commit Trace**:
  * Legacy commits `a7049ef9` (*"Refactor: the extrema values as well as the string encodings for the location are no longer calculated at the end of the workout"*) and `fb9fac41` (*"perf(tracker): decouple database I/O from 1Hz sampling loop to fix ANRs"*) constructed device-qualified sample keys via `sensorName = "'" + sensorName + "'"` and stripped quotes via `key.replace("'", "")`.
  * Commit `f4650d74` (`ATT-3980` / `ATT-4048`, Sprint `2026-42.9`) hardened `handleMissingColumns()` (`REQ-TRK-026.4`) to return `false` and abort the transaction when `insertOrThrow()` fails with a non-missing-column `SQLException`.
* **Root Reason for Existing Formulation**:
  * Android's `SQLiteDatabase.insertOrThrow()` concatenates `ContentValues` keys directly into `INSERT INTO <table>(<key1>, ...) VALUES (?, ...)` without quoting identifiers. Because `<SensorType> (<DeviceName>)` contains spaces and parentheses, the original author wrapped the key in single quotes (`'...'`) assuming device names would only contain alphanumeric characters and spaces.
* **Preservation of Core Invariants**:
  * Switching from unescaped single quotes (`'...'`) and `key.replace("'", "")` to SQL-standard escaped double quotes (`"..."` with `""` escaping) and proper outer-delimiter unquoting preserves the exact unquoted SQLite column name `<SensorType> (<DeviceName>)` in `sqlite_master` and `Cursor.getColumnNames()`, maintaining 100% compatibility with existing workout tables, `CSVFileWriter`, and `REQ-TRK-026` transaction semantics.

---

## 5. Architectural Strategy & High-Level Solution

1. **Canonical SQLite Column Identifier Quoting & Unquoting Helpers in `TrackerService`**:
   * `static String quoteSqlColumnIdentifier(@NonNull String rawColumnName)`:
     Returns `"\"" + rawColumnName.replace("\"", "\"\"") + "\""`.
   * `static String unquoteSqlColumnIdentifier(@NonNull String sqlColumnIdentifier)`:
     * If wrapped in outer double quotes (`length >= 2 && startsWith("\"") && endsWith("\"")`), strips the outer `"` characters and unescapes `""` to `"`.
     * If wrapped in outer single quotes (`length >= 2 && startsWith("'") && endsWith("'")`), strips the outer `'` characters and unescapes `''` to `'` (preserving backward compatibility with any legacy caller).
     * Otherwise returns `sqlColumnIdentifier` unchanged (e.g., bare `SensorType.name()` or `<SensorType>_gps`).
2. **Coordinated 3-Point Update in `TrackerService`**:
   * **Point 1 (`sampleAndWriteToDb`)**: For non-location device names, build the raw column name `sensorName + " (" + deviceName + ")"` and wrap it via `quoteSqlColumnIdentifier(...)`.
   * **Point 2 (`handleMissingColumns` column lookup)**: Compute `String queryKey = unquoteSqlColumnIdentifier(key);` and check `cursor.getColumnIndex(queryKey) < 0`.
   * **Point 3 (`handleMissingColumns` `ALTER TABLE`)**: Compute `String quotedColumn = quoteSqlColumnIdentifier(queryKey);` and execute `samplesDb.execSQL("ALTER TABLE " + tableName + " ADD COLUMN " + quotedColumn + " " + type + " null;");`.
3. **Rate-Limited Sample Insert Failure Telemetry**:
   * Add `private final AtomicInteger mSampleInsertFailureCount = new AtomicInteger(0);` (exposed via `@VisibleForTesting public int getSampleInsertFailureCount()`).
   * When sample insertion fails (either in `handleMissingColumns` or `sampleAndWriteToDb`), increment `mSampleInsertFailureCount` and log at `Log.e`/`Log.w` on the initial burst (`failureCount <= 3`) and every `SAMPLE_INSERT_FAILURE_LOG_INTERVAL` (`60`) failures thereafter (`failureCount % 60 == 0`), including `failureCount` and suppressed failure counts in the log message.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing features and unit tests.
  2. 100% backward compatibility with existing `WorkoutSamples.db` tables, `.attbackup` archives, and `CSVFileWriter` regex column matching (`<SensorType> (<DeviceName>)`).
  3. Thread safety on `mDbExecutor` single-thread database dispatcher and atomic failure counter.
  4. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**
  * The change is localized to `TrackerService.java` column identifier quoting/unquoting and failure logging, backed by deterministic SQLite identifier escaping rules.
