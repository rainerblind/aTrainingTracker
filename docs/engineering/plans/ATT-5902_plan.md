# Stage 3: Implementation Plan - ATT-5902: Pre-load existing workout identifiers in memory to fast-forward bulk imports without stream parsing or file copying

**Ticket**: [ATT-5902](https://rainerblind.atlassian.net/browse/ATT-5902)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Import, Export & Backup*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Requirement Mapping**: `REQ-MIG-050` (`REQ-MIG-050.1`..`REQ-MIG-050.5`)  
**Test Mapping**: `TST-MIG-048` (`TST-MIG-048.1`..`TST-MIG-048.5`)  
**Branch**: `feature/ATT-5902`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Description & Background
Resumed or repeated bulk imports in `StravaBulkExportImporter` (lines 167–207) decompress `.gz` streams and parse full XML/FIT files into memory, `BackupRestoreViewModel` (lines 712–718) copies SAF URIs into `cacheDir`, and `LegacyImportEngine` (lines 228, 441) executes per-file SQLite queries before downstream `persistParsedWorkout` detects duplicates. An upstream in-memory snapshot (`ExistingWorkoutsSnapshot`) loaded once in a single query (< 3 ms) provides zero-I/O pre-filtering, fast-forwarding duplicate batches in under 50 ms.

---

## 2. Traceability & Requirements Mapping
* **`REQ-MIG-050.1`** (In-Memory Snapshot) $\rightarrow$ `TST-MIG-048.1` (`ExistingWorkoutsSnapshotTest`)
* **`REQ-MIG-050.2`** (Strava Zero-I/O Fast-Forward) $\rightarrow$ `TST-MIG-048.2` (`StravaBulkExportImporterTest`)
* **`REQ-MIG-050.3`** (SAF Zero-Copy Fast-Forward) $\rightarrow$ `TST-MIG-048.3` (`BackupRestoreViewModelImportTest`)
* **`REQ-MIG-050.4`** (Cloud In-Memory Deduplication) $\rightarrow$ `TST-MIG-048.4` (`LegacyImportEngineDeduplicationTest`)
* **`REQ-MIG-050.5`** (Defense-in-Depth & Non-Regression) $\rightarrow$ `TST-MIG-048.5` (Targeted migration regression)

---

## 3. System Invariants & Preserved Behavior
1. **Defense-in-Depth**: Downstream transactional `isWorkoutExisting(summaryDb, ...)` in `persistParsedWorkout` remains 100% active.
2. **`legacy_import` Prefix Invariant**: Files prefixed with `legacy_import` derive base names from parsed timestamps and bypass filename-only pre-checks.
3. **Thread Safety**: Concurrent workers (Dropbox/Drive) access a thread-safe snapshot.
4. **UI Consistency**: No UI changes. Existing progress strings and counters are reused.

---

## 4. Proposed Architectural Changes
* **Database Managers (`WorkoutSummariesDatabaseManager.java`, `StravaUploadDbHelper.java`)**:
  - Add `getAllFileBaseNames(): Set<String>` to `WorkoutSummariesDatabaseManager` (`SELECT fileBaseName FROM WorkoutSummaries`).
  - Add `getAllActivityIds(): Set<String>` to `StravaUploadDbHelper` (`SELECT ActivityId FROM StravaUploads WHERE ActivityId IS NOT NULL`).
* **Snapshot Layer (`ExistingWorkoutsSnapshot.kt`)**:
  - Class encapsulating lowercased `existingBaseNames` and `existingStravaIds`, providing $O(1)$ `isDuplicate`, `containsBaseName`, and thread-safe `register`.
* **Importer Fast-Forward Wiring**:
  - `StravaBulkExportImporter.kt`: Pre-filter `row.activityId` and `resolveBaseFileName` before opening ZIP stream entries.
  - `BackupRestoreViewModel.kt`: Pre-filter candidate `baseFileName` from display name before opening SAF streams and copying to `cacheDir`.
  - `LegacyImportEngine.kt`: Replace per-entry SQLite queries in `bulkRecoverFromDropbox` and `bulkRecoverFromGoogleDrive` with snapshot lookups.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1: DB Manager Bulk Identifier Getters (`WorkoutSummariesDatabaseManager.java`, `StravaUploadDbHelper.java`)**:
   Implement `getAllFileBaseNames()` and `getAllActivityIds()` single-column batch queries.
2. **Step 2: Snapshot Model & Tests (`ExistingWorkoutsSnapshot.kt`, `ExistingWorkoutsSnapshotTest.kt`)**:
   Create `ExistingWorkoutsSnapshot` with case-insensitive hashing and write unit tests for `TST-MIG-048.1`.
3. **Step 3: Strava Bulk Export Zero-I/O Fast-Forward (`StravaBulkExportImporter.kt`)**:
   Pre-check snapshot before `zip.getInputStream` / decompression; add `TST-MIG-048.2` unit test.
4. **Step 4: SAF Zero-Copy Fast-Forward (`BackupRestoreViewModel.kt`)**:
   Pre-check snapshot before `openInputStream` / `cacheDir` copy; add `TST-MIG-048.3` unit test.
5. **Step 5: Cloud Recovery In-Memory Deduplication (`LegacyImportEngine.kt`)**:
   Replace worker SQLite `SELECT` calls with snapshot lookups; add `TST-MIG-048.4` unit test.
6. **Step 6: Targeted Verification & Governance**:
   Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.exporter.db.*"` and `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted tests in Stage 4; clean-room full suite in Stage 5.
* **Rollback**: Branch isolation (`feature/ATT-5902`); revert branch if any regression occurs before squash-merge.
