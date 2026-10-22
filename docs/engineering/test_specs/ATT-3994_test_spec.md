# Stage 2: Requirement & Test Specification: ATT-3994

**Ticket**: [ATT-3994](https://atrainingtracker.atlassian.net/browse/ATT-3994)  
**Subtask**: `ATT-4182` (`[Specification]`)  
**Parent Epic**: [ATT-1117](https://atrainingtracker.atlassian.net/browse/ATT-1117) (*FIT File Format Support (Export & Import)*)  
**Target Release**: `Next Minor Release` (V4.9.40)  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-DAT-028` (*UTC-Anchored Binary FIT Export, Transient Lap Synthesis, Fail-Fast Stream Error Propagation, and Application Context Lifetime*)  
**Test Case Mapping**: `TST-DAT-023` (`TST-DAT-023.1` – `TST-DAT-023.6`)  
**Branch**: `feature/ATT-3994`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. System Requirements & Functional Specifications

### 1.1 Technical Constraints (MUST / MUST NOT)
1. **Strict UTC Timestamp Invariant**:
   - `FitFileWriter` MUST NOT interpret or parse database timestamps using the host device's local default timezone (`TimeZone.getDefault()`).
   - All timestamp parsers and formatters in `FitFileWriter` MUST be explicitly anchored to `TimeZone.getTimeZone("UTC")`.
   - Every binary FIT timestamp (`FileIdMesg.timeCreated`, `RecordMesg.timestamp`, `LapMesg.startTime`, `LapMesg.timestamp`, `SessionMesg.startTime`, `SessionMesg.timestamp`, `ActivityMesg.timestamp`) MUST equal the exact UTC instant recorded in SQLite.
2. **FIT Specification Lap Compliance & Transient Invariant**:
   - Every exported activity FIT file MUST contain at least one valid `LapMesg` for each `SessionMesg`.
   - For workouts lacking explicit laps in `Laps.db`, `FitFileWriter` MUST synthesize a single `LapMesg` representing the full workout session.
   - `FitFileWriter` MUST NOT write, insert, or mutate any data in `Laps.db` (`tblLaps`). The synthesized lap is strictly a transient in-memory serialization artifact.
3. **Fail-Fast Error Handling & Elimination of Truncated Success**:
   - `FitFileWriter` MUST NOT catch and swallow exceptions occurring during sample queries or record writing.
   - `FitFileWriter` MUST NOT return `new ExportResult(true, ...)` when records are omitted or truncated due to query or encoding errors.
   - If `startTime` is null, blank, or malformed, `FitFileWriter` MUST NOT fall back to `new Date()`. It MUST fail the export cleanly with `new ExportResult(false, false, ...)`.
4. **Context Lifecycle Safety**:
   - `FitFileWriter` MUST NOT retain strong references to short-lived `Activity` contexts. It MUST initialize with `context.getApplicationContext()`.

---

## 2. Formal Requirement Specification (`REQ-DAT-028`)

### Requirement Text
`FitFileWriter` (`app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/FitFileWriter.java`) SHALL guarantee that all exported Garmin FIT activity files (`.fit`) encode timestamps in true UTC matching SQLite `CURRENT_TIMESTAMP` ground truth regardless of host device timezone, synthesize a single transient in-memory `LapMesg` for unsegmented workouts to satisfy FIT Protocol V2.0 compliance without mutating `Laps.db`, fail fast with `ExportResult(false)` upon timestamp parse errors or sample read exceptions without publishing truncated files, and bind to `getApplicationContext()` to prevent Activity lifecycle leaks (ATT-3994, extending `REQ-DAT-018`):

1. **Strict UTC Timezone Anchoring (`REQ-DAT-028.1`)**:
   - `FitFileWriter.mDateFormat` SHALL explicitly configure `setTimeZone(TimeZone.getTimeZone("UTC"))` and `Locale.US` for pattern `"yyyy-MM-dd HH:mm:ss"`.
   - All timestamp conversions (`startDate`, `sampleDate`, `lapStart`, `endDate`) SHALL decode SQLite timestamps in UTC. Host device local timezone offsets (e.g. CEST UTC+2, EST UTC-5) SHALL NOT shift epoch timestamps in `FileIdMesg`, `RecordMesg`, `LapMesg`, `SessionMesg`, or `ActivityMesg`.
2. **Transient Synthesized Lap for Lap-Less Workouts (`REQ-DAT-028.2`)**:
   - When `LapsDatabaseManager.getInstance(mContext).getLaps(workoutID)` returns null or an empty list, `FitFileWriter` SHALL synthesize and encode exactly one `LapMesg` covering the full workout duration: `startTime = startDate`, `timestamp = endDate`, `totalElapsedTime = totalTimeSec`, `totalTimerTime = totalTimeSec`, `totalDistance = distTotal`, `avgSpeed = (distTotal / totalTimeSec)`, `intensity = Intensity.ACTIVE`, `sport = sport`, `subSport = subSport`.
   - `sessionMesg.setNumLaps(1)` SHALL match the written lap count.
   - Transient Invariant: The synthesized lap SHALL be emitted solely into the binary `FileEncoder` stream and SHALL NOT mutate, insert, or write back to `Laps.db` (`tblLaps`).
3. **Fail-Fast Error Handling & Elimination of Truncated Success (`REQ-DAT-028.3`)**:
   - If `startTime` is null, empty, or unparseable, `FitFileWriter` SHALL NOT fall back to `new Date()`. It SHALL immediately return `new ExportResult(false, false, "FIT export failed: missing or unparseable startTime")`.
   - If an exception occurs during sample querying or record writing in the `samplesDb.query` cursor loop, `FitFileWriter` SHALL NOT swallow the exception or proceed to write summary messages. It SHALL abort encoding, log the error, and return `new ExportResult(false, false, "FIT export failed during sample encoding: " + e.getMessage())`.
   - If `totalTime` or `totalDistance` cannot be parsed, `FitFileWriter` SHALL log an error and handle gracefully, ensuring non-negative values.
   - Failing fast SHALL notify `ExportWorker` (`informOthersFailed`), set `ExportStatus.FINISHED_ERROR`, notify the athlete, and prevent broken/truncated file upload to cloud services (Dropbox, Google Drive, Strava).
4. **Safe Application Context Lifetime (`REQ-DAT-028.4`)**:
   - `FitFileWriter` constructor SHALL pass `context.getApplicationContext()` to `super(context.getApplicationContext())`, ensuring long-running export tasks cannot leak Activity instances.

### Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: Net-new requirement `REQ-DAT-028`, extending `REQ-DAT-018` (`ATT-1827`) under Epic `ATT-1117` (*FIT File Format Support*). Targets `FitFileWriter.java` and `FitFileWriterTest.kt`.
2. **Historical Origin & Commit Trace**: Ticket `ATT-3994`, Sprint `2026-42.10`, discovered during independent whole-app code review of `feature/ATT-3921`.
3. **Root Reason for Existing Formulation**: `FitFileWriter` inherited unconfigured `SimpleDateFormat` pattern from legacy text exporters (`TCXFileWriter`), which inadvertently shifted binary FIT timestamps by the device UTC offset. Defensive try-catches swallowed exceptions, outputting truncated files marked as success.
4. **Preservation of Core Invariants**: Garmin FIT Protocol V2.0 compliance, semicircle GPS scaling ($2^{31}/180.0$), sensor metric encoding, `Laps.db` persistence immutability, and 100% clean-room test pass rate strictly preserved.

---

## 3. Test Specification & Verification Matrix (`TST-DAT-023`)

| Test ID | Target Unit | Verification Objective | Assertion / Expected Outcome |
| :--- | :--- | :--- | :--- |
| **`TST-DAT-023.1`** | `FitFileWriter.doExport` | **UTC Timezone Invariant & Offset Independence with DateTimeFormatter** | Under simulated `GMT+2`, `GMT+9`, and `GMT-5` default timezones, `Decode` of exported `.fit` yields `FileIdMesg`, `RecordMesg`, and `SessionMesg` timestamps matching ground-truth UTC seconds without shift using thread-safe `DateTimeFormatter`. |
| **`TST-DAT-023.2`** | `FitFileWriter.doExport` | **Transient Lap Synthesis for Lap-Less Workouts & Timer Consistency** | For workout with 0 laps in `Laps.db`, exported `.fit` contains exactly 1 `LapMesg` with full duration and distance; `sessionMesg.numLaps == 1`; `total_timer_time` and `total_elapsed_time` align per FIT SDK 2.0; zero rows added to `Laps.db`. |
| **`TST-DAT-023.3`** | `FitFileWriter.doExport` | **Fail-Fast on Corrupted / Missing StartTime** | When `startTime` is null, empty, or unparseable, `doExport` returns `success == false` without falling back to `new Date()`. |
| **`TST-DAT-023.4`** | `FitFileWriter.doExport` | **Fail-Fast on Sample Read / Write Exception** | When SQLite cursor throws an exception mid-stream, `doExport` returns `success == false`, no truncated file is marked successful, and error is logged. |
| **`TST-DAT-023.5`** | `FitFileWriter.doExport` | **Historical Workout Re-Export Ground Truth** | Re-exporting a previously recorded workout produces accurate UTC timestamps matching ground truth SQLite records with zero database side-effects. |
| **`TST-DAT-023.6`** | `FitFileWriter.doExport` | **Partial File Cleanup on Export Failure** | When an export fails due to invalid parameters or mid-stream exceptions, any partially written `.fit` file is completely deleted (`outputFile.exists() == false`), leaving no corrupted residue on disk. If cleanup fails, it is surfaced in `ExportResult`. |
| **`TST-DAT-023.7`** | `FitFileWriter.doExport` | **Negative Integration Test on Inconsistent Metrics** | When `totalTime` is negative or inconsistent, `doExport` fails fast with an explicit error without synthesizing an invalid lap. |
| **`TST-DAT-023.8`** | `FitFileWriter.doExport` | **Workout with Significant Pauses Verification** | When a workout contains substantial pause duration (e.g. 10 minutes) where active `totalTimerTime` < wall-clock `totalElapsedTime`, export succeeds and encodes timer vs elapsed metrics correctly without rejection. |
| **`TST-DAT-023.9`** | Clean-Room Suite | **Regression Suite Execution** | `./gradlew testDebugUnitTest` runs with 100% pass rate across all modules. |

---

## 4. Acceptance Criteria (Given-When-Then)

### Criterion 1: Device in Non-UTC Timezone (CEST / UTC+2)
- **Given** a host device configured in Central European Summer Time (`TimeZone.getTimeZone("GMT+2")`).
- **And** a recorded workout in `WorkoutSummaries` with UTC start time `"2026-07-01 10:00:00"`.
- **When** `FitFileWriter.doExport` generates the `.fit` file.
- **Then** the decoded `FileIdMesg.timeCreated` and `RecordMesg.timestamp` equal `2026-07-01 10:00:00 UTC` (1751364000000 ms), completely unshifted by the 2-hour device offset.

### Criterion 2: Workout Lacking Intermediate Laps & Timer Consistency
- **Given** a workout in `WorkoutSummaries` with duration 120s and distance 1500m, but 0 entries in `Laps.db`.
- **When** `FitFileWriter.doExport` generates the `.fit` file.
- **Then** the decoded `.fit` file contains exactly one `LapMesg` covering the 120s duration and 1500m distance.
- **And** `sessionMesg.numLaps` equals 1.
- **And** `total_timer_time` and `total_elapsed_time` match 1:1 between `LapMesg` and `SessionMesg`.
- **And** `Laps.db` remains 100% untouched with zero newly inserted rows.

### Criterion 3: Missing or Corrupt StartTime
- **Given** an `ExportInfo` referencing a workout whose `timeStart` is null or invalid string `"invalid-date"`.
- **When** `FitFileWriter.doExport` executes.
- **Then** it returns `ExportResult.success() == false`.
- **And** it does NOT set `startDate = new Date()`.

### Criterion 4: Sample Cursor Read Failure
- **Given** a sample table whose cursor throws an `SQLiteException` during `moveToNext()`.
- **When** `FitFileWriter.doExport` executes.
- **Then** it catches the error and returns `ExportResult.success() == false`.
- **And** `ExportWorker` transitions status to `ExportStatus.FINISHED_ERROR` and halts downstream cloud uploads.

### Criterion 5: Partial File Cleanup on Export Failure
- **Given** an export task that encounters an error (validation error or stream exception).
- **When** `FitFileWriter.doExport` fails.
- **Then** the partially created `.fit` file in the export target directory is deleted immediately.
- **And** `outputFile.exists()` evaluates to `false`.

### Criterion 6: Negative Duration Inconsistency Rejection
- **Given** a workout whose `totalTime` is negative (e.g. `"-100.0"`).
- **When** `FitFileWriter.doExport` executes.
- **Then** it aborts with `ExportResult.success() == false` and explicit error message, preventing invalid lap synthesis.

### Criterion 7: Workout with Significant Pauses Verification
- **Given** a workout containing 10 minutes of pause duration where active timer time (300s) is strictly less than elapsed wall-clock time (900s).
- **When** `FitFileWriter.doExport` executes.
- **Then** export succeeds and encodes `totalTimerTime = 300s` and `totalElapsedTime = 900s` in both `LapMesg` and `SessionMesg`.

---

## 5. Invariants & Guardrails

1. **FIT Protocol Compatibility**: Semicircle GPS conversion ($2^{31}/180.0$), sensor metric encoding, and CRC checksums must remain 100% compliant with Garmin FIT Protocol Version 2.0.
2. **Database Immutability**: Exporters are strictly read-only operations. Zero writes or mutations to `WorkoutSummaries.db`, `WorkoutSamples.db`, or `Laps.db`.
3. **No Regressions**: Existing TCX, GPX, CSV, and GC JSON exporters retain identical functionality.
4. **Clean-Room Test Pass Rate**: 100% test pass rate across all modules under `./gradlew testDebugUnitTest`.

---

## 6. Traceability Matrix

| Requirement | Test Specification | Target File | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-DAT-028.1` | `TST-DAT-023.1` | `FitFileWriter.java` | Unit Test (`FitFileWriterTest.kt`) |
| `REQ-DAT-028.2` | `TST-DAT-023.2`, `TST-DAT-023.8` | `FitFileWriter.java` | Unit Test (`FitFileWriterTest.kt`) |
| `REQ-DAT-028.3` | `TST-DAT-023.3`, `TST-DAT-023.4`, `TST-DAT-023.6`, `TST-DAT-023.7` | `FitFileWriter.java` | Unit Test (`FitFileWriterTest.kt`) |
| `REQ-DAT-028.4` | `TST-DAT-023.1` | `FitFileWriter.java` | Unit Test (`FitFileWriterTest.kt`) |
| `REQ-DAT-028` | `TST-DAT-023.5` | `FitFileWriter.java` | Unit Test (`FitFileWriterTest.kt`) |
| `REQ-PRO-001` | `TST-DAT-023.9` | Full Project | Clean-Room Gradle Test |
