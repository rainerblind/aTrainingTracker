# Stage 3: Implementation Plan - ATT-2023: Workouts Imported Twice (Revision 2: Option 2)

**Ticket**: [ATT-2023](https://rainerblind.atlassian.net/browse/ATT-2023)  
**Sub-task**: [ATT-2142](https://rainerblind.atlassian.net/browse/ATT-2142) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-529](https://rainerblind.atlassian.net/browse/ATT-529) (*Import TCX Files*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Branch**: `feature/ATT-2023`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Architecture & SWE.2 Detailed Design

```
+-----------------------------------------------------------------------------------+
| LegacyImportEngine.kt                                                             |
|                                                                                   |
| importFromTcxInternal() / importFromGpxInternal()                                 |
|                                                                                   |
|   1. Parse XML headers: firstTime, bSportType, laps, samples                      |
|   2. importMutex.withLock {                                                       |
|        if (isWorkoutExisting(summaryDb, baseFileName, firstTime, bSportType)) {   |
|            Log.d(TAG, "Skipping $baseFileName: Workout already exists.")           |
|            return@withLock true // DUPLICATE_SKIPPED                              |
|        }                                                                          |
|        ... create sample tables & insert summary ...                              |
|      }                                                                            |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
| isWorkoutExisting(db, fileBaseName, timeStart, bSportType)                        |
|                                                                                   |
| if (timeStart.isNullOrBlank()) -> exact FILE_BASE_NAME check                      |
|                                                                                   |
| if (bSportType != null && bSportType != BSportType.UNKNOWN):                      |
|   SQL: FILE_BASE_NAME = ? OR TIME_START = ? OR                                    |
|        (Sport = ? AND TIME_START IS NOT NULL AND                                  |
|         ABS(strftime('%s', TIME_START) - strftime('%s', ?)) <= 180)               |
|   Args: [fileBaseName, timeStart, bSportType.name, timeStart]                     |
|                                                                                   |
| else:                                                                             |
|   SQL: FILE_BASE_NAME = ? OR TIME_START = ? OR                                    |
|        (TIME_START IS NOT NULL AND                                                |
|         ABS(strftime('%s', TIME_START) - strftime('%s', ?)) <= 180)               |
|   Args: [fileBaseName, timeStart, timeStart]                                      |
+-----------------------------------------------------------------------------------+
```

---

## 2. Atomic Implementation Steps

### Step 1: Update `isWorkoutExisting` in `LegacyImportEngine.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt`
* **Changes**:
  1. Add `bSportType: BSportType? = null` parameter to `isWorkoutExisting`.
  2. Adjust the threshold from `30` to `180`.
  3. When `bSportType` is valid, include `${WorkoutSummaries.B_SPORT} = ? AND ` in the timestamp window clause and bind `bSportType.name` into `selectionArgs`.

### Step 2: Forward `bSportType` in TCX and GPX Import Functions
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt`
* **Changes**:
  1. In `importFromTcxInternal` (around line 641), invoke `isWorkoutExisting(summaryDb, baseFileName, firstTime, bSportType)`.
  2. In `importFromGpxInternal` (around line 1055), invoke `isWorkoutExisting(summaryDb, baseFileName, firstTime, bSportType)`.

### Step 3: Update `LegacyImportEngineDeduplicationTest.kt`
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngineDeduplicationTest.kt`
* **Changes**:
  1. Update existing unit tests asserting `<= 30` to assert `<= 180`.
  2. Add unit test verifying that passing `bSportType` binds `${WorkoutSummaries.B_SPORT} = ?` with `bSportType.name`.
  3. Add unit test verifying that activities offset by 65s (matching physical dual-phone Dropbox files) evaluate as duplicates.

### Step 4: Verification
* Run targeted unit tests: `./gradlew testDebugUnitTest --tests "*LegacyImportEngineDeduplicationTest*"`
* Run clean-room test suite: `./gradlew testDebugUnitTest`

---

## 3. Invariants & Governance Pre-Check

1. **Zero Regressions**: 100% test pass rate across the full test suite.
2. **Backward Compatibility**: Existing callers calling `isWorkoutExisting` without `bSportType` continue to work cleanly with default `null`.
3. **No False-Positive Suppression**: Workouts of differing sports within 3 minutes are not suppressed.
4. **Parent Ticket Terminal State**: Terminal state remains `Final Review (Human)`.
