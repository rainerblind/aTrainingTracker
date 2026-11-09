# Stage 2: Requirement & Test Specification - ATT-5902

**Ticket**: [ATT-5902](https://rainerblind.atlassian.net/browse/ATT-5902)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Import, Export & Backup*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Requirement Mapping**: `REQ-MIG-050` (*In-Memory Existing Workout Deduplication Snapshot & Zero-I/O Fast-Forward for Bulk Importers*)  
**Test Spec ID**: `TST-MIG-048`  
**Branch**: `feature/ATT-5902`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-MIG-050)

### 1.1 Problem Statement & Rationale
When resuming or repeating large bulk imports (Strava export ZIPs, local SAF multi-file imports, or Dropbox/Google Drive recovery), importers currently decompress streams, copy files to `cacheDir`, and parse workout data before checking database existence via `isWorkoutExisting`. An upstream in-memory snapshot of known workout identifiers (`FILE_BASE_NAME`, Strava `ActivityId`) enables zero-I/O pre-filtering to fast-forward past duplicate activities in under 50 ms.

### 1.2 Functional & Architectural Requirements
* `REQ-MIG-050.1` (In-Memory Snapshot): Pre-load `ExistingWorkoutsSnapshot` containing lowercased `FILE_BASE_NAME` set and Strava `ActivityId` set in a single pass (< 3 ms) supporting $O(1)$ case-insensitive lookups.
* `REQ-MIG-050.2` (Strava Bulk Export): `StravaBulkExportImporter.importArchive` SHALL check `baseFileName` and `row.activityId` against the snapshot before opening ZIP streams, decompressing GZIP streams, or invoking FIT/TCX/GPX parsers, fast-forwarding skipped counts immediately.
* `REQ-MIG-050.3` (Local Multi-File Import): `BackupRestoreViewModel.importWorkoutFilesInternal` SHALL evaluate candidate `baseFileName` before opening `contentResolver` input streams or writing temporary files in `cacheDir` (except for `legacy_import` files requiring stream-derived names).
* `REQ-MIG-050.4` (Cloud Recovery): `LegacyImportEngine` Dropbox and Google Drive recovery SHALL check entry names against the in-memory snapshot, eliminating per-file SQLite queries.
* `REQ-MIG-050.5` (Defense-in-Depth & Invariants): Downstream transactional deduplication (`isWorkoutExisting`) in `persistParsedWorkout` MUST remain 100% intact as a safety net.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Strava Zero-I/O)**: *Given* a Strava ZIP with already-imported activities, *When* ingested by `StravaBulkExportImporter`, *Then* duplicates are skipped without opening ZIP streams or invoking parsers.
* **Criterion 2 (SAF Zero-Copy)**: *Given* local files existing in `WorkoutSummaries`, *When* imported via `BackupRestoreViewModel`, *Then* duplicates are skipped without copying bytes to `cacheDir`.
* **Criterion 3 (Cloud In-Memory)**: *Given* cloud recovery files, *When* recovered via `LegacyImportEngine`, *Then* known entries are skipped in memory without per-entry SQLite queries.

### 1.4 System Invariants
* Downstream transactional `isWorkoutExisting` in `persistParsedWorkout` remains intact.
* `legacy_import` files bypass filename-only pre-filtering.
* Zero duplicate rows inserted; zero unimported workouts skipped.

---

## 2. Test Specification (TST-MIG-048)

### Test Case 1: Snapshot Creation & Case-Insensitive Matching (`TST-MIG-048.1`)
* **Scope**: Unit Test (`ExistingWorkoutsSnapshotTest.kt`)
* **Action**: Verify snapshot loads `FILE_BASE_NAME` and `ActivityId` sets; assert case-insensitive matching (`"track_01"` vs `"Track_01"`).

### Test Case 2: Strava Bulk Export Zero-I/O Fast-Forward (`TST-MIG-048.2`)
* **Scope**: Unit Test (`StravaBulkExportImporterTest.kt`)
* **Action**: Ingest archive containing duplicate activities; verify `zip.getInputStream` is never called and parsers are not invoked for duplicates.

### Test Case 3: BackupRestoreViewModel Zero-Copy Fast-Forward (`TST-MIG-048.3`)
* **Scope**: Unit Test (`BackupRestoreViewModelImportTest.kt`)
* **Action**: Ingest URIs matching snapshot base names; verify `contentResolver.openInputStream` is never called and no `cacheDir` files are created.

### Test Case 4: Cloud Recovery In-Memory Deduplication (`TST-MIG-048.4`)
* **Scope**: Unit Test (`LegacyImportEngineDeduplicationTest.kt`)
* **Action**: Verify `bulkRecoverFromDropbox` / `bulkRecoverFromGoogleDrive` filter duplicates in memory without individual SQLite queries.

### Test Case 5: Targeted Package Regression (`TST-MIG-048.5`)
* **Scope**: Targeted Unit Test Suite
* **Action**: Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.*"`.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Class / Method | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-MIG-048.1` | Unit | `ExistingWorkoutsSnapshotTest.testSnapshotLookup` | `REQ-MIG-050.1` | Specified |
| `TST-MIG-048.2` | Unit | `StravaBulkExportImporterTest.testImportArchive_fastForwardDuplicates` | `REQ-MIG-050.2` | Specified |
| `TST-MIG-048.3` | Unit | `BackupRestoreViewModelImportTest.testImportWorkoutFiles_skipsExistingWithoutCopy` | `REQ-MIG-050.3` | Specified |
| `TST-MIG-048.4` | Unit | `LegacyImportEngineDeduplicationTest.testCloudRecovery_inMemoryDeduplication` | `REQ-MIG-050.4` | Specified |
| `TST-MIG-048.5` | Reg | `com.atrainingtracker.trainingtracker.migration.*` | `REQ-MIG-050.5` | Specified |
