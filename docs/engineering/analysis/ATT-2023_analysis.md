# Stage 1 Analysis: ATT-2023 - [Import/TCX] Workouts Imported Twice Due to Shallow Filename-Only Deduplication and Concurrent Worker Race Conditions

**Ticket**: [ATT-2023](https://rainerblind.atlassian.net/browse/ATT-2023)  
**Sub-task**: [ATT-2076](https://rainerblind.atlassian.net/browse/ATT-2076) (`[Analysis]`)  
**Parent Epic**: [ATT-529](https://rainerblind.atlassian.net/browse/ATT-529) (*Import TCX Files*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.12`  
**Branch**: `feature/ATT-2023`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Statement & Motivation

During historical TCX and GPX file imports (via the local storage file picker or Dropbox bulk cloud recovery), workouts are frequently imported twice into the application's databases (`WorkoutSummaries.db` and dynamic tables in `WorkoutSamples.db`). These duplicate records pollute workout lists, inflate aggregated statistics across Day, Week, Month, and Year periods (`PeriodsRepository`), skew route cluster hit counters, and trigger redundant Strava upload attempts.

### Current Behavior vs Expected Behavior:
* **Current Behavior**:
  1. If a workout was recorded in-app or previously imported, importing another TCX/GPX file with a different filename (e.g. `activity_12345.tcx`, `run (1).tcx`, or `legacy_import_1715351400.tcx`) inserts a duplicate workout summary and creates a duplicate samples table, even though the internal workout timestamp (`TIME_START`), sport type, and GPS track are identical.
  2. In Dropbox bulk recovery, 3 concurrent coroutine workers running on `Dispatchers.IO` pick up candidate files simultaneously. Because `isWorkoutExisting` and subsequent database insertion are not synchronized, two workers evaluating identical or duplicate files execute the check concurrently before either has inserted the row. Both checks return `false`, resulting in duplicate insertions.
  3. Dropbox recovery scans multiple directory paths (`/TCX` and `/apps/Workouts/TCX`). Files present in both directories have distinct remote paths (`pathLower`) and are treated as separate items by `distinctBy`, queuing identical workouts twice.
* **Expected Behavior**:
  1. The deduplication check must be multi-dimensional: checking both `FILE_BASE_NAME` and `TIME_START` (with a ±30-second window to accommodate clock drift and rounding).
  2. Database checking, table creation, and summary insertion must be guarded by an atomic mutex to guarantee race-free execution across concurrent workers.
  3. Scanned cloud files must be deduplicated by filename base before queuing, preventing duplicate downloads from multiple paths.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic examination of [LegacyImportEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt) lines 115–230, 270–300, 610–665, 730–750, 1000–1045, and 1146–1151 reveals three architectural flaws:

### 2.1 Shallow Filename-Only Deduplication (`isWorkoutExisting`)
In `LegacyImportEngine.kt` lines 1146–1151:
```kotlin
private fun isWorkoutExisting(db: WorkoutSummariesDatabaseManager, fileBaseName: String): Boolean {
    db.database.query(WorkoutSummaries.TABLE, arrayOf(WorkoutSummaries.C_ID), 
        "${WorkoutSummaries.FILE_BASE_NAME} = ?", arrayOf(fileBaseName), null, null, null).use {
        return it.count > 0
    }
}
```
* The query strictly filters on `WorkoutSummaries.FILE_BASE_NAME = ?`.
* It completely ignores `WorkoutSummaries.TIME_START` (the workout's true start timestamp parsed from the `<Id>`, `<Lap StartTime="...">`, or `<time>` XML tags).
* An athlete downloading an activity from Garmin Connect or Strava receives a file named `activity_987654.tcx`. If the workout was already recorded in-app (where `FILE_BASE_NAME` is formatted as `2024_05_10_14_30_00`), `isWorkoutExisting` checks `FILE_BASE_NAME = 'activity_987654'`, finds no match, and inserts the duplicate session.
* Furthermore, in `importFromTcx` (lines 613–619) and `importFromGpx` (lines 1005–1011):
  ```kotlin
  if (baseFileName.startsWith("legacy_import", ignoreCase = true) && firstTime != null) {
      baseFileName = firstTime!!.replace(" ", "_").replace(":", "")
      if (isWorkoutExisting(summaryDb, baseFileName)) {
          return false
      }
  }
  ```
  If `baseFileName` does not start with `"legacy_import"`, this post-parsing check is skipped completely, allowing non-conforming file names to bypass deduplication entirely.

### 2.2 Concurrent Worker Race Condition on `Dispatchers.IO`
In `bulkRecoverFromDropbox` (lines 176–228):
```kotlin
coroutineScope {
    val channel = Channel<Pair<Int, Metadata>>(Channel.UNLIMITED)
    entries.forEachIndexed { index, entry -> channel.trySend(Pair(index, entry)) }
    channel.close()

    (1..3).map {
        launch(Dispatchers.IO) {
            for ((_, entry) in channel) {
                val baseFileName = entry.name.substringBeforeLast(".").removeSuffix("-TMP").removeSuffix("~")
                if (isWorkoutExisting(summaryDb, baseFileName)) {
                    skippedCount.incrementAndGet()
                    continue
                }
                ...
                importFromTcx(context, tempFile, listener, uploadToStrava)
            }
        }
    }
}
```
* When multiple workers process duplicate files (or copies of the same workout under different names), Worker 1 and Worker 2 both execute `isWorkoutExisting(summaryDb, baseFileName)` simultaneously before either has inserted the row into `WorkoutSummaries`.
* Neither worker finds an existing record, so both proceed to parse, create sample tables, and insert summary rows, creating a double import.
* There is no synchronization or mutex guarding the check-and-insert transaction.

### 2.3 Multi-Folder Scan Duplication
In lines 122–126 and line 159:
```kotlin
val possiblePaths = when (format.lowercase()) {
    "tcx" -> listOf("/TCX", "/apps/Workouts/TCX")
    ...
}
...
val entries = allEntries.distinctBy { it.pathLower ?: it.name }
```
* If a backup file exists in both `/TCX/2024_05_10_14_30_00.tcx` and `/apps/Workouts/TCX/2024_05_10_14_30_00.tcx`, their `pathLower` values differ (`/tcx/...` vs `/apps/workouts/tcx/...`).
* `distinctBy` does not filter out the second copy; both are queued into `channel` and processed concurrently.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Multi-dimensional deduplication checking `FILE_BASE_NAME` and `TIME_START` (with a ±30-second window in `WorkoutSummaries.db`).
  2. Coroutine-safe `Mutex` guard around the critical check-and-insert transaction in `importFromTcx` and `importFromGpx`.
  3. Pre-dispatch filename-base deduplication in `bulkRecoverFromDropbox` to eliminate multi-folder duplicate queueing.
  4. Accurate reporting in the final result summary (`skippedCount` correctly reflects all skipped duplicates).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Altering existing SQLite table schemas for `WorkoutSummaries` or `WorkoutSamples`.
  2. Modifying Dropbox API SDK pagination or authorization flows.
  3. Modifying route cluster similarity scoring algorithms or tolerance configurations.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-MIG-005` (*Incremental Import*) and `REQ-MIG-016` (*Paginated & Recursive Cloud Recovery*), targeting `LegacyImportEngine.kt`.
   - Target Release: `V4.9.38`.
   - Parent Epic: `ATT-529` (*Import TCX Files*).

2. **Historical Origin & Commit Trace**:
   - Commit `917f6984` (`ATT-117`): Initial incremental import deduplication using `FILE_BASE_NAME`.
   - Commit `32585f52` (`ATT-560`): Introduced multi-folder scanning for `/TCX` and `/apps/Workouts/TCX` with `distinctBy { it.pathLower ?: it.name }`.
   - Commit `a6ebdc81` (`ATT-549`): Introduced 3 concurrent workers in `bulkRecoverFromDropbox` to unblock background importing.

3. **Root Reason for Existing Formulation**:
   - The original author assumed that all TCX files followed the standard in-app naming pattern `YYYY_MM_DD_HH_MM_SS.tcx`. Under that single-folder, standardized-naming assumption, matching `FILE_BASE_NAME` was thought to be identical to matching the start time.
   - When multi-path scanning was added in `ATT-560`, `pathLower` was used to prevent scanning errors within Dropbox, but this allowed the exact same file in two different folders to be queued twice.
   - When concurrent workers were added in `ATT-549`, no synchronization was introduced around `isWorkoutExisting`, creating the race condition.

4. **Preservation of Core Invariants**:
   - `FILE_BASE_NAME` matching is strictly preserved as the primary fast-path filter.
   - Adding `TIME_START` matching and an atomic `Mutex` enhances deduplication accuracy without breaking existing single-file or bulk import flows.
   - Pre-deduplicating entries by base name ensures that duplicate files from multiple scanned paths are discarded before download, saving bandwidth and device battery.

---

## 5. Architectural Strategy & High-Level Solution

### Component 1: `LegacyImportEngine.kt`
1. **Multi-Dimensional Deduplication (`isWorkoutExisting`)**:
   - Enhance `isWorkoutExisting(db: WorkoutSummariesDatabaseManager, fileBaseName: String, timeStart: String? = null): Boolean`:
     - First, query `FILE_BASE_NAME = ?`.
     - Second, if `timeStart` is provided, query `TIME_START = ?`.
     - Third, if `timeStart` can be parsed to epoch seconds, query with tolerance: `ABS(strftime('%s', timeStart) - ?) <= 30`.
2. **Atomic Synchronization Guard (`importMutex`)**:
   - Introduce `private val importMutex = Mutex()` in `LegacyImportEngine`.
   - In `importFromTcx` and `importFromGpx`, once `firstTime` is resolved and before creating tables or inserting summaries, enter `importMutex.withLock`:
     - Re-check `isWorkoutExisting(summaryDb, baseFileName, firstTime)`.
     - If true, log skip and return `false` cleanly without creating duplicate tables or records.
     - If false, proceed with table creation and summary insertion inside the lock.
3. **Pre-Dispatch Base Name Deduplication**:
   - In `bulkRecoverFromDropbox`, deduplicate `allEntries` by base name:
     ```kotlin
     val entries = allEntries.distinctBy {
         it.name.substringBeforeLast(".").removeSuffix("-TMP").removeSuffix("~").lowercase()
     }
     ```

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing single-file and bulk TCX/GPX imports.
  2. Full telemetry, lap structures, and route clusters remain 100% intact.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**
  - Deduplication enhancements and mutex synchronization are additive guards that prevent duplicate database writes without changing data models or business logic.
