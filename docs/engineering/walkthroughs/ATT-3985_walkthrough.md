# Stage 5: Walkthrough & Verification - ATT-3985: [High] Apostrophe in device name drops every sample row of the workout

**Ticket**: [ATT-3985](https://rainerblind.atlassian.net/browse/ATT-3985)  
**Sub-task**: [ATT-4068](https://rainerblind.atlassian.net/browse/ATT-4068) (`[Test]`)  
**Parent Epic**: None (*Standalone Defect from Code Review 2026-10*)  
**Target Release**: `TBD (Assigned at Sprint Review per Rule 14)`  
**Active Sprint**: `2026-42.9`  
**Requirement Mapping**: `REQ-TRK-029`  
**Test Mapping**: `TST-TRK-021`  
**Branch**: `feature/ATT-3985`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Executive Summary & Verification Overview

This walkthrough documents the end-to-end implementation and clean-room verification of **ATT-3985** (`REQ-TRK-029`, `TST-TRK-021`):
1. **SQLite Column Identifier Quoting & Unquoting (`REQ-TRK-029.1`, `REQ-TRK-029.2`)**:
   - Added `TrackerService.quoteSqlColumnIdentifier(String)` (wrapping raw column identifiers in SQL-standard double quotes `"..."` and escaping embedded `"` as `""`), `TrackerService.unquoteSqlColumnIdentifier(String)` (stripping only outer SQL identifier quotes and unescaping `""` -> `"` or `''` -> `'` without removing internal apostrophes or quotes), and `TrackerService.hasColumnInCursor(Cursor, String)` (falling back to `cursor.getColumnNames()` if `cursor.getColumnIndex(...) < 0` due to Android `AbstractWindowedCursor` dot-stripping).
   - Updated all 3 points in `TrackerService.java`:
     1. `sampleAndWriteToDb()`: device-qualified sensor column names (`<SensorType> (<DeviceName>)`) are quoted via `quoteSqlColumnIdentifier(...)`.
     2. `handleMissingColumns()` column existence check: keys from `values.keySet()` are unquoted via `unquoteSqlColumnIdentifier(key)` and checked via `hasColumnInCursor(cursor, queryKey)`.
     3. `handleMissingColumns()` `ALTER TABLE` DDL: missing columns are added via `quoteSqlColumnIdentifier(queryKey)`.
   - Preserves the `<SensorType> (<DeviceName>)` column naming convention in `WorkoutSamples.db` (`sqlite_master` and `Cursor.getColumnNames()`), maintaining 100% compatibility with `CSVFileWriter` (`Pattern.compile("(.*)( \\(.*\\)|_gps|_network|_google_fused)")`) and `.attbackup` archives.
2. **Rate-Limited Sample Insert Failure Counter & Telemetry (`REQ-TRK-029.3`)**:
   - Added `mSampleInsertFailureCount` (`getSampleInsertFailureCount()`), `SAMPLE_INSERT_FAILURE_INITIAL_BURST = 3`, `SAMPLE_INSERT_FAILURE_LOG_INTERVAL = 60`, `shouldLogSampleInsertFailure(int)`, and `logSampleInsertFailure(String, Throwable)` so sample write errors are counted on every failure and logged at `ERROR` on the initial burst (`1..3`) and every 60th failure thereafter (`60, 120, ...`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-029.1` | `TST-TRK-021.1` | `testQuoteAndUnquoteSqlColumnIdentifier_preservesApostrophesAndEscapesDoubleQuotes` (`TrackerServiceDeviceNameQuotingTest`) | **PASSED** | `Verified` |
| `REQ-TRK-029.2` | `TST-TRK-021.2` | `testSampleAndWriteToDb_withApostropheAndDoubleQuoteDeviceNames_insertsAllSampleRowsInSqlite` (`TrackerServiceDeviceNameQuotingTest`) | **PASSED** | `Verified` |
| `REQ-TRK-029.3` | `TST-TRK-021.3` | `testSampleInsertFailureCounter_rateLimitsRepeatedSqliteErrors` (`TrackerServiceDeviceNameQuotingTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-TRK-021.4` | Full Clean-Room `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 3m 33s
32 actionable tasks: 12 executed, 20 up-to-date
```

### Targeted Unit & Integration Tests
```text
./gradlew testDebugUnitTest \
  --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceDeviceNameQuotingTest" \
  --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceResumeTest" \
  --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceResilienceTest"

> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 18s
32 actionable tasks: 2 executed, 30 up-to-date
```

---

## 4. Hardware / Physical Verification (Pixel 10)

Backend database/service logic enhancement in `TrackerService.java`; verified via strict SQLite identifier grammar and schema simulation in `TrackerServiceDeviceNameQuotingTest` and the full clean-room regression suite. On-device deployment (`./gradlew installDebug`) is executed at the start of Ceremony 2 (Sprint Review) per Rule 10.

### Visual Consistency (Rule 23 — mandatory if UI is added or changed)
No UI changes.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Clean-room unit test suite executed with 100% pass rate.
2. **Living Documentation Synchronized**: Status in `docs/requirements.md` (`REQ-TRK-029`) and `docs/tests.md` (`TST-TRK-021`) updated to `Verified`.
3. **Subtask Completion**: Stage 5 subtask (`ATT-4068`) transitioned to `Erledigt` via `freigabe`.
4. **Parent Ticket Final Review**: Parent ticket (`ATT-3985`) transitioned to `Final Review (Human)` and assigned to `human` for final release sign-off.
