# Stage 1 Analysis: ATT-2023 - [Import/TCX] Workouts Imported Twice Due to Shallow Filename-Only Deduplication and Concurrent Worker Race Conditions (Revision 2)

**Ticket**: [ATT-2023](https://rainerblind.atlassian.net/browse/ATT-2023)  
**Sub-task**: [ATT-2139](https://rainerblind.atlassian.net/browse/ATT-2139) (`[Analysis]`)  
**Parent Epic**: [ATT-529](https://rainerblind.atlassian.net/browse/ATT-529) (*Import TCX Files*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Branch**: `feature/ATT-2023`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Statement & Sprint Review Feedback (Revision 2)

During sprint testing of Revision 1, duplicate imports were still observed on the user's test device (e.g. workout *"boneshaking in the morning"* on 2015-06-09 at 06:57 and 06:58).

### Forensic Inspection of Physical Files:
Inspection of the athlete's Dropbox storage at `/home/rainer/Dropbox/apps/Workouts/TCX` identified two distinct physical TCX files for this activity:
* `2015-06-09_065716.tcx`: Start `04:57:16Z` (06:57:16 local), duration 2,265s, distance 14.66 km, sport `Biking`, size 1.3 MB.
* `2015-06-09_065821.tcx`: Start `04:58:21Z` (06:58:21 local), duration 2,216s, distance 10.07 km, sport `Biking`, size 517 KB.

### Root Cause of Revision 1 Miss:
1. Both files record the **exact same bicycle ride** (tracked simultaneously on two phones; started 65 seconds apart, ended within 16 seconds of each other).
2. Revision 1 configured a start-time delta threshold of $\pm 30\text{ seconds}$ (`<= 30`). Because the start time difference between the two devices was 65 seconds, Revision 1 evaluated the second file as a separate workout and imported both.
3. Furthermore, Revision 1's `isWorkoutExisting` did not inspect the sport type (`B_SPORT`), preventing sport-aware deduplication.

### User Decision on Resolution Strategy:
The user selected **Option 2: Extended Start-Time Tolerance Window**:
* Widen the start-time tolerance threshold to **$\pm 3\text{ minutes}$ ($\pm 180\text{ seconds}$)** for workouts of the **same sport**.
* Pass `bSportType` from `importFromTcxInternal` and `importFromGpxInternal` into `isWorkoutExisting`.
* If a workout already exists within $\pm 3$ minutes for the same sport, the second incoming file is classified as a duplicate and skipped cleanly.

---

## 2. Technical Architecture & Scope (Option 2)

### 2.1 Extended Multi-Dimensional Query in `isWorkoutExisting`
In [LegacyImportEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt):
```kotlin
internal fun isWorkoutExisting(
    db: WorkoutSummariesDatabaseManager, 
    fileBaseName: String, 
    timeStart: String? = null,
    bSportType: BSportType? = null
): Boolean {
    if (timeStart.isNullOrBlank()) {
        db.database.query(
            WorkoutSummaries.TABLE, 
            arrayOf(WorkoutSummaries.C_ID), 
            "${WorkoutSummaries.FILE_BASE_NAME} = ?", 
            arrayOf(fileBaseName), 
            null, null, null
        ).use {
            return it.count > 0
        }
    }

    val hasSport = bSportType != null && bSportType != BSportType.UNKNOWN
    val selection = if (hasSport) {
        "${WorkoutSummaries.FILE_BASE_NAME} = ? OR " +
        "${WorkoutSummaries.TIME_START} = ? OR " +
        "(${WorkoutSummaries.B_SPORT} = ? AND ${WorkoutSummaries.TIME_START} IS NOT NULL AND ABS(strftime('%s', ${WorkoutSummaries.TIME_START}) - strftime('%s', ?)) <= 180)"
    } else {
        "${WorkoutSummaries.FILE_BASE_NAME} = ? OR " +
        "${WorkoutSummaries.TIME_START} = ? OR " +
        "(${WorkoutSummaries.TIME_START} IS NOT NULL AND ABS(strftime('%s', ${WorkoutSummaries.TIME_START}) - strftime('%s', ?)) <= 180)"
    }

    val selectionArgs = if (hasSport) {
        arrayOf(fileBaseName, timeStart, bSportType.name, timeStart)
    } else {
        arrayOf(fileBaseName, timeStart, timeStart)
    }

    db.database.query(
        WorkoutSummaries.TABLE, 
        arrayOf(WorkoutSummaries.C_ID), 
        selection, 
        selectionArgs, 
        null, null, null
    ).use {
        return it.count > 0
    }
}
```

### 2.2 Caller Integration
* In `importFromTcxInternal`: Pass parsed `bSportType` to `isWorkoutExisting(summaryDb, baseFileName, firstTime, bSportType)` inside `importMutex.withLock`.
* In `importFromGpxInternal`: Pass parsed `bSportType` to `isWorkoutExisting(summaryDb, baseFileName, firstTime, bSportType)` inside `importMutex.withLock`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Expand start-time tolerance window from $\pm 30\text{s}$ to $\pm 180\text{s}$ ($\pm 3\text{ minutes}$) for workouts of the same sport.
  * Supply `bSportType` to `isWorkoutExisting` in `LegacyImportEngine.kt`.
  * Update `LegacyImportEngineDeduplicationTest.kt` to assert the 180-second window and sport filtering.
  * Clean-room full-suite regression validation.
* **Out-of-Scope Non-Goals**:
  * No modification to trackpoint parsing or database schema (existing `WorkoutSummaries.B_SPORT` column is used).
  * No deletion or modification of source files on Dropbox or local storage.

---

## 4. Invariants & Risk Assessment

* **Core Invariants**:
  1. Exact filename matches (`FILE_BASE_NAME = ?`) continue to be deduplicated immediately.
  2. Concurrent workers remain synchronized by `importMutex`.
  3. Workouts of different sports within 3 minutes (e.g. brick session run immediately following a ride) are NOT falsely flagged as duplicates when `bSportType` differs.
* **Risk Rating**: **LOW** (Targeted SQL query tolerance adjustment within existing mutex-guarded import pipeline).
