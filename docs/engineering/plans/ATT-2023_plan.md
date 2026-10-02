# Stage 3: Implementation Plan - ATT-2023: [Import/TCX] Workouts Imported Twice Due to Shallow Filename-Only Deduplication and Concurrent Worker Race Conditions

**Ticket**: [ATT-2023](https://rainerblind.atlassian.net/browse/ATT-2023)  
**Sub-task**: [ATT-2078](https://rainerblind.atlassian.net/browse/ATT-2078) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-529](https://rainerblind.atlassian.net/browse/ATT-529) (*Import TCX Files*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.12`  
**Requirement Mapping**: `REQ-MIG-031` (*Multi-Dimensional Workout Deduplication & Concurrent Worker Race Condition Guard in TCX/GPX Import*)  
**Test Mapping**: `TST-MIG-028`  
**Branch**: `feature/ATT-2023`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Description & Background

During bulk or single-file imports of historical activities (via local storage or Dropbox cloud bulk recovery), workouts are frequently imported twice into `WorkoutSummaries.db` and dynamic tables in `WorkoutSamples.db`. This duplicates database records, inflates aggregated metrics across Day, Week, Month, and Year periods, creates false route cluster hit increments, and triggers duplicate Strava synchronization events.

Forensic analysis revealed three distinct root causes in `LegacyImportEngine.kt`:
1. **Shallow Filename-Only Deduplication**: `isWorkoutExisting` only checks `WorkoutSummaries.FILE_BASE_NAME = ?`. Activities imported with arbitrary or altered file names (e.g. `activity_12345.tcx`, `run (1).tcx`, or `legacy_import_*.tcx`) bypass deduplication because their file names do not match previously stored names, even when their underlying start timestamp (`TIME_START`), track coordinates, and sport types are identical.
2. **Concurrent Worker Race Condition**: In `bulkRecoverFromDropbox`, 3 concurrent worker coroutines run on `Dispatchers.IO`. When duplicate files are encountered simultaneously, both workers execute `isWorkoutExisting` concurrently before either has inserted a row into `WorkoutSummaries.TABLE`. Both checks return `false`, resulting in duplicate tables and database records.
3. **Multi-Folder Scan Duplication**: Dropbox bulk recovery scans both `/TCX` and `/apps/Workouts/TCX`. Scanned items are deduplicated using `distinctBy { it.pathLower ?: it.name }`. Because the folder paths differ, identical files in both directories are queued twice.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MIG-031` (*Multi-Dimensional Workout Deduplication & Concurrent Worker Race Condition Guard in TCX/GPX Import*)
  - Clause 1: Multi-dimensional query evaluating `WorkoutSummaries.FILE_BASE_NAME = ?` AND `WorkoutSummaries.TIME_START = ?`, plus a $\pm 30$-second epoch window (`ABS(strftime('%s', timeStart) - strftime('%s', ?)) <= 30`).
  - Clause 2: Atomic coroutine `Mutex` (`importMutex`) guarding the check-and-insert transaction in `importFromTcx` and `importFromGpx`.
  - Clause 3: Pre-dispatch deduplication of cloud metadata by normalized base name in `bulkRecoverFromDropbox`.
  - Clause 4: Accurate reporting of skipped duplicates in `RecoveryResult.skipped`.
  - Clause 5: Preservation of all lap data, telemetry points, route clustering, and Strava upload parameters.
* **Test Mapping**: `TST-MIG-028`
  - `TST-MIG-028.1`: Multi-dimensional deduplication unit tests (`LegacyImportEngineDeduplicationTest.kt`).
  - `TST-MIG-028.2`: Multi-folder cloud scan base name deduplication tests (`LegacyImportEngineDeduplicationTest.kt`).
  - `TST-MIG-028.3`: Concurrent worker race condition simulation with `importMutex` (`LegacyImportEngineDeduplicationTest.kt`).
  - `TST-MIG-028.4`: 9-language localization audit (`TranslationParityTest.kt`).
  - `TST-MIG-028.5`: Clean-room full suite regression (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Database Schema Invariant**: No changes to existing SQLite schemas in `WorkoutSummaries` or `WorkoutSamples`.
2. **Parsing & Analytical Fidelity**: Telemetry streams, lap splits, extrema calculations, route clustering, and Strava uploader integration remain 100% unaltered.
3. **Fast-Path Performance**: `FILE_BASE_NAME` check remains the primary pre-download filter to avoid downloading already imported workouts.
4. **Subtask Direct Completion**: Subtasks transition directly to `Erledigt` upon passing Gate audit via transition `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent `ATT-2023` is strictly reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: `LegacyImportEngine.kt` (Import Engine & Synchronization Layer)
* **Multi-Dimensional Deduplication Method**:
  Update `isWorkoutExisting`:
  ```kotlin
  internal fun isWorkoutExisting(
      db: WorkoutSummariesDatabaseManager, 
      fileBaseName: String, 
      timeStart: String? = null
  ): Boolean {
      if (timeStart.isNullOrBlank()) {
          db.database.query(
              WorkoutSummaries.TABLE, 
              arrayOf(WorkoutSummaries.C_ID), 
              "${WorkoutSummaries.FILE_BASE_NAME} = ?", 
              arrayOf(fileBaseName), 
              null, null, null
          ).use { return it.count > 0 }
      }

      val selection = "${WorkoutSummaries.FILE_BASE_NAME} = ? OR " +
              "${WorkoutSummaries.TIME_START} = ? OR " +
              "(${WorkoutSummaries.TIME_START} IS NOT NULL AND ABS(strftime('%s', ${WorkoutSummaries.TIME_START}) - strftime('%s', ?)) <= 30)"
      val selectionArgs = arrayOf(fileBaseName, timeStart, timeStart)

      db.database.query(
          WorkoutSummaries.TABLE, 
          arrayOf(WorkoutSummaries.C_ID), 
          selection, 
          selectionArgs, 
          null, null, null
      ).use { return it.count > 0 }
  }
  ```

* **Atomic Synchronization Mutex**:
  Declare in `LegacyImportEngine`:
  ```kotlin
  internal val importMutex = Mutex()
  ```

* **Thread-Safe Check-and-Insert in `importFromTcx` and `importFromGpx`**:
  In `importFromTcx` and `importFromGpx`, once `firstTime` is resolved from header or first lap:
  1. If `baseFileName.startsWith("legacy_import", ignoreCase = true) && firstTime != null`, reformat `baseFileName = firstTime!!.replace(" ", "_").replace(":", "")`.
  2. Enter `importMutex.withLock`:
     - Re-check `if (isWorkoutExisting(summaryDb, baseFileName, firstTime)) return false`.
     - Create sample table via `samplesDbManager.createNewTable(...)` and bulk insert buffered sample rows.
     - Insert summary row into `WorkoutSummaries.TABLE`.
  3. Exit `importMutex.withLock`.
  4. Perform asynchronous post-processing (`recalculateStats`, route clustering, Strava upload scheduling) outside `importMutex`.

* **Pre-Dispatch Base Name Deduplication in `bulkRecoverFromDropbox`**:
  Replace path-based deduplication:
  ```kotlin
  val entries = allEntries.distinctBy {
      it.name.substringBeforeLast(".").removeSuffix("-TMP").removeSuffix("~").lowercase()
  }
  ```

### Component 2: `LegacyImportEngineDeduplicationTest.kt` (Verification Layer)
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngineDeduplicationTest.kt`:
  - `testDeduplicationByExactFileName`: Asserts existing workout with same `FILE_BASE_NAME` returns `true`.
  - `testDeduplicationByExactStartTime`: Asserts workout with different `FILE_BASE_NAME` but identical `TIME_START` returns `true`.
  - `testDeduplicationByToleranceWindow`: Asserts workout within $\pm 15$s returns `true`, and workout with $\Delta t > 30$s returns `false`.
  - `testPreDispatchCloudEntryDeduplication`: Asserts duplicate entries across `/TCX` and `/apps/Workouts/TCX` with identical base name are deduplicated to 1.
  - `testConcurrentWorkerMutexProtection`: Simulates concurrent coroutines attempting to check and insert identical workout records, asserting that exactly one succeeds and duplicates are cleanly skipped.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Check Gate Verification
* Command: `python3 tools/jira_util.py check-gate ATT-2078` (confirm code modification gate is open).

### Step 2: Update Source Code in `LegacyImportEngine.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt`
* Enhance `isWorkoutExisting` with timestamp and tolerance window queries.
* Add `importMutex` and guard check-and-insert transactions in `importFromTcx` and `importFromGpx`.
* Update `bulkRecoverFromDropbox` deduplication logic.

### Step 3: Author Unit & Concurrency Test Suite in `LegacyImportEngineDeduplicationTest.kt`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngineDeduplicationTest.kt`
* Implement unit tests for multi-dimensional deduplication, timestamp tolerance, cloud entry filtering, and mutex concurrency safety.

### Step 4: Execute Targeted Unit Tests
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.LegacyImportEngineDeduplicationTest"`

### Step 5: Commit Stage 4 Construction Changes
* Git commit with descriptive message referencing `ATT-2023` and `REQ-MIG-031`.

---

## 6. Verification & Rollback Plan

* **Targeted Verification**:
  - `LegacyImportEngineDeduplicationTest` (multi-dimensional deduplication and concurrency suite).
  - `TranslationParityTest` (9-language localization audit).
* **Clean-Room Regression**:
  - `./gradlew testDebugUnitTest` in Stage 5.
* **Rollback Safety**:
  - Feature branch `feature/ATT-2023` is completely isolated from `sprint/2026-40.12`. In case of unexpected issues, any commit can be reverted cleanly without impacting sprint baseline stability.
