# Stage 2: Requirement & Test Specification - ATT-2023: Workouts Imported Twice (Revision 2: Option 2)

**Ticket**: [ATT-2023](https://rainerblind.atlassian.net/browse/ATT-2023)  
**Sub-task**: [ATT-2140](https://rainerblind.atlassian.net/browse/ATT-2140) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-529](https://rainerblind.atlassian.net/browse/ATT-529) (*Import TCX Files*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Branch**: `feature/ATT-2023`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Formal System Requirement: REQ-MIG-031 (Revision 2)

### Title
**Multi-Dimensional Workout Deduplication & Concurrent Worker Race Condition Guard in TCX/GPX Import with Extended 3-Minute Same-Sport Tolerance Window (Option 2).**

### Description
The system SHALL prevent duplicate imports of workouts across single-file and cloud bulk recovery flows through multi-dimensional deduplication, atomic synchronization, pre-dispatch filtering, and an extended same-sport start-time tolerance window (ATT-2023 Revision 2):

1. **Extended Same-Sport Tolerance Window (`isWorkoutExisting` in `LegacyImportEngine.kt`)**:
   - `isWorkoutExisting` SHALL accept optional parameter `bSportType: BSportType? = null`.
   - When `bSportType` is non-null and not `BSportType.UNKNOWN`, the query SHALL evaluate:
     ```sql
     FILE_BASE_NAME = ? OR 
     TIME_START = ? OR 
     (Sport = ? AND TIME_START IS NOT NULL AND ABS(strftime('%s', TIME_START) - strftime('%s', ?)) <= 180)
     ```
   - When `bSportType` is null or `BSportType.UNKNOWN`, the query SHALL evaluate:
     ```sql
     FILE_BASE_NAME = ? OR 
     TIME_START = ? OR 
     (TIME_START IS NOT NULL AND ABS(strftime('%s', TIME_START) - strftime('%s', ?)) <= 180)
     ```
   - The tolerance threshold SHALL be $180\text{ seconds}$ ($3\text{ minutes}$), reliably preventing dual-phone recordings (e.g. `2015-06-09_065716.tcx` vs `2015-06-09_065821.tcx` with a 65-second offset) from creating duplicate workout records.

2. **Caller Integration (`importFromTcxInternal` & `importFromGpxInternal`)**:
   - In `importFromTcxInternal` and `importFromGpxInternal`, `isWorkoutExisting` SHALL be invoked with the parsed `bSportType` inside the `importMutex.withLock` block.
   - When `isWorkoutExisting` returns `true`, the candidate file SHALL be skipped without creating sample tables or inserting summary records, returning `ImportStatus.DUPLICATE_SKIPPED`.

3. **Atomic Synchronization & Pre-Dispatch Queue Deduplication**:
   - The atomic coroutine `Mutex` (`importMutex`) SHALL continue to guard table creation and database insertion against race conditions across concurrent workers.
   - Cloud bulk recovery (`bulkRecoverFromDropbox`) SHALL continue to deduplicate file entries by normalized base name prior to worker queuing.

4. **Accurate Reporting**:
   - Skipped duplicate files SHALL increment `skippedCount`, accurately reflecting deduplicated sessions in the final import summary.

### Acceptance Criteria (Given-When-Then)
- **AC-1 (Same-Sport Duplicate within 3-Minute Window)**:
  - *Given* an existing workout recorded with sport `Biking` at `06:57:16` (`2015-06-09 06:57:16`),
  - *When* importing a second file (`2015-06-09_065821.tcx`) with sport `Biking` at `06:58:21` (offset: 65s, $\le 180\text{s}$),
  - *Then* `isWorkoutExisting` SHALL return `true`, skipping insertion and creating zero duplicate records.
- **AC-2 (Distinct Sport Not Suppressed)**:
  - *Given* an existing workout recorded with sport `Running` at `10:00:00`,
  - *When* importing a file with sport `Biking` at `10:02:00` (offset: 120s),
  - *Then* `isWorkoutExisting` SHALL NOT classify it as a duplicate, and the workout SHALL be imported normally.
- **AC-3 (Exact Filename Match)**:
  - *Given* an existing workout with `FILE_BASE_NAME = 'my_workout'`,
  - *When* importing a file with `FILE_BASE_NAME = 'my_workout'`,
  - *Then* `isWorkoutExisting` SHALL return `true` immediately regardless of timestamp or sport.
- **AC-4 (Concurrent Worker Safety)**:
  - *Given* 3 concurrent coroutine workers processing candidate files on `Dispatchers.IO`,
  - *When* two workers process dual-phone recordings of the same workout concurrently,
  - *Then* `importMutex.withLock` SHALL synchronize check-and-insert, allowing the first worker to insert and causing the second worker to skip without duplicate tables or records.

---

## 2. Formal Test Specification: TST-MIG-028 (Revision 2)

### Test Cases

#### TST-MIG-028.1: Unit Test for Extended 3-Minute Same-Sport Query Construction
- **Test File**: `LegacyImportEngineDeduplicationTest.kt`
- **Method**: Verify that `isWorkoutExisting` constructs the SQL query with `<= 180` and binds `bSportType.name` when `bSportType` is provided.

#### TST-MIG-028.2: Unit Test for Dual-Phone Offset Detection (65 seconds)
- **Test File**: `LegacyImportEngineDeduplicationTest.kt`
- **Method**: Verify that activities offset by 65 seconds (matching the physical Dropbox files `2015-06-09_065716.tcx` vs `2015-06-09_065821.tcx`) evaluate as duplicates.

#### TST-MIG-028.3: Unit Test for Sport-Aware Disambiguation
- **Test File**: `LegacyImportEngineDeduplicationTest.kt`
- **Method**: Verify that workouts with different sport types within the 3-minute window do not match the sport clause.

#### TST-MIG-028.4: Clean-Room Full Suite Regression Execution
- **Method**: Run `./gradlew testDebugUnitTest` verifying 100% pass rate.
