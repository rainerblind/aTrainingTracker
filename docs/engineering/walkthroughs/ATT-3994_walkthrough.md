# Stage 5 Verification & Walkthrough: ATT-3994 / REQ-DAT-028 / TST-DAT-023

## Overview & Scope
- **Ticket ID**: `ATT-3994`
- **Summary**: `[Bug] [High] FIT export timestamps are shifted by the device UTC offset`
- **Requirement / Test ID**: `REQ-DAT-028` / `TST-DAT-023`
- **Fix Version / Target Release**: `V4.9.40`
- **Epic**: `ATT-1117` (`FIT File Format Support (Export & Import)`)
- **Key Modules**:
  - `com.atrainingtracker.trainingtracker.exporter.writer.FitFileWriter`
  - `com.atrainingtracker.trainingtracker.exporter.writer.FitFileWriterTest`
  - `docs/requirements.md` (`REQ-DAT-028`)
  - `docs/tests.md` (`TST-DAT-023`)

---

## Implemented Architectural Improvements

### 1. Strict UTC Timezone Anchoring with Thread-Safe `DateTimeFormatter` (`REQ-DAT-028.1`)
- Replaced mutable, non-thread-safe `SimpleDateFormat` in `FitFileWriter.java` with immutable, thread-safe `java.time.format.DateTimeFormatter` pattern `"yyyy-MM-dd HH:mm:ss"` anchored to `ZoneOffset.UTC`.
- Standardized ISO timestamp decoding into `LocalDateTime` and conversion to UTC epoch seconds via `Instant.toEpochMilli()`.
- Guaranteed zero epoch timestamp shifting in `FileIdMesg`, `RecordMesg`, `LapMesg`, `SessionMesg`, and `ActivityMesg` across all host device timezones (CEST, EST, JST, etc.).

### 2. Context Lifecycle Safety (`REQ-DAT-028.4`)
- Updated `FitFileWriter` constructor to bind to `context.getApplicationContext()` with a safe fallback to eliminate Activity memory leaks during background export execution.

### 3. Fail-Fast Header Parsing & Ground-Truth Validation (`REQ-DAT-028.3`)
- Replaced dangerous `new Date()` fallback with strict pre-flight parameter validation:
  - Missing, blank, or unparseable `startTime` immediately returns `ExportResult(false, false, ...)`.
  - Negative `totalTime` values are rejected immediately with a descriptive error message.
  - Safe parsing of `totalDistance` ensures non-negative metric values.

### 4. Stream Error Propagation & Elimination of Truncated Success (`REQ-DAT-028.3`)
- Eliminated silent error swallowing during sample cursor traversal in SQLite `WorkoutSamples.db`.
- Query or serialization exceptions abort encoding immediately and return `ExportResult(false, false, errorMessage)`, notifying `ExportWorker` to set `ExportStatus.FINISHED_ERROR` and halting downstream cloud uploads (Dropbox, Google Drive, Strava).

### 5. Native Athletics Pause Duration Support (`REQ-DAT-028.2`, `TST-DAT-023.8`)
- Tracked first and last sample timestamps across the session to determine total wall-clock elapsed duration (`elapsedSampleSec`).
- Properly differentiated active moving timer time (`totalTimerSec = totalTimeSec`) from total wall-clock time (`totalElapsedTimeSec = Math.max(totalTimerSec, elapsedSampleSec)`).
- Fully supports athletics workouts with substantial pauses (e.g. 10 minutes) without triggering false-positive rejection heuristics.

### 6. Transient Synthesized Lap & FIT Protocol V2.0 Compliance (`REQ-DAT-028.2`)
- For unsegmented workouts lacking explicit entries in `Laps.db`:
  - Synthesizes exactly 1 transient `LapMesg` (`Intensity.ACTIVE`) covering the full session from `startDate` to `sessionEndDate`.
  - Enforces 100% database immutability: zero mutations or inserts to `Laps.db` (`tblLaps`).
  - Aligns `totalTimerTime`, `totalElapsedTime`, and `numLaps == 1` across `LapMesg`, `SessionMesg`, and `ActivityMesg`, strictly satisfying Garmin FIT SDK 2.0 invariants and third-party platform parsers (Garmin Connect, Strava).

### 7. Robust Filesystem Cleanup & Throwable Resource Safety (`REQ-DAT-028.3`, `TST-DAT-023.6`)
- Wrapped `FileEncoder.close()` in `finally` protected by `try-catch(Throwable)` to ensure file handles are released even on `InterruptedException` or thread termination.
- Upon export failure, executes `outputFile.delete()` to ensure no partial or corrupted `.fit` files remain in the export directory.
- Deletion failures are surfaced directly in the returned `ExportResult`.

---

## Verification & Test Results

### 1. Dedicated Targeted Unit Tests (`FitFileWriterTest.kt`)
The expanded test suite covers all clauses of `TST-DAT-023`:
1. `testFitFileWriter_generatesValidBinaryFitFile_andDecodesCleanly` — **PASS**
2. `testFitFileWriter_indoorWorkoutOmitsCoordinates` — **PASS**
3. `testFitFileWriter_utcTimestampPreservationUnderDifferentTimezones` (`TST-DAT-023.1`) — **PASS** (Tested under `GMT+2`, `GMT+9`, and `GMT-5`)
4. `testFitFileWriter_synthesizesTransientLapWhenNoLapsInDatabase` (`TST-DAT-023.2`) — **PASS**
5. `testFitFileWriter_failsFastOnMissingOrCorruptStartTime` (`TST-DAT-023.3`) — **PASS**
6. `testFitFileWriter_failsFastOnSampleCursorException` (`TST-DAT-023.4`) — **PASS**
7. `testFitFileWriter_reExportHistoricalWorkoutPreservesGroundTruth` (`TST-DAT-023.5`) — **PASS**
8. `testFitFileWriter_deletesPartialFileOnExportFailure` (`TST-DAT-023.6`) — **PASS**
9. `testFitFileWriter_abortsOnNegativeTotalTimeInconsistency` (`TST-DAT-023.7`) — **PASS**
10. `testFitFileWriter_allowsWorkoutWithLongPauses` (`TST-DAT-023.8`) — **PASS**

### 2. Clean-Room Regression Test Execution (`./gradlew testDebugUnitTest`)
- **Target Task**: `:app:testDebugUnitTest`
- **Failures**: 0
- **Errors**: 0
- **Result**: `BUILD SUCCESSFUL` (100% pass rate across entire codebase)

---

## Living Documentation
- `docs/requirements.md`: `REQ-DAT-028` updated to status `Verified`.
- `docs/tests.md`: `TST-DAT-023` updated to status `Verified`.
- Strict ASPICE traceability and GitFlow branch safety preserved.
