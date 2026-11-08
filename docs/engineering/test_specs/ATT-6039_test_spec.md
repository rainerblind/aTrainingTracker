# Stage 2: Requirement & Test Specification - ATT-6039

**Ticket**: [ATT-6039](https://rainerblind.atlassian.net/browse/ATT-6039)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Import, Export & Backup*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Requirement Mapping**: `REQ-MIG-049` (*Unified Chronological Ascending Ingestion Order for Bulk Imports & Cloud Recovery*)  
**Test Spec ID**: `TST-MIG-047`  
**Branch**: `feature/ATT-6039`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-MIG-049)

### 1.1 Problem Statement & Rationale
Bulk import pipelines (Strava bulk ZIP, SAF directory scan, multi-URI selection, and Dropbox/GDrive cloud recovery) currently process activities in reverse or arbitrary order. Because `WorkoutClusterEngine` auto-naming assigns `#1` to the first ingested workout, newest-first ingestion inverts sequential numbering (assigning `#1` to modern workouts and `#350` to older ones) and inverts SQLite `_id` chronological ordering. Ingestion order across all bulk import flows must be unified to chronological ascending (oldest first).

### 1.2 Functional & Architectural Requirements
* `REQ-MIG-049.1` (Strava Bulk Export): `StravaBulkExportImporter` SHALL sort parsed activities ascending by numeric `activityId.toLongOrNull() ?: Long.MAX_VALUE` (fallback `reversed()` if non-numeric) prior to the sequential import loop.
* `REQ-MIG-049.2` (SAF Directory Scanner): `SafDirectoryScanner.scanDocumentFile` SHALL sort discovered matching URIs ascending by document filename using `String.CASE_INSENSITIVE_ORDER` before returning.
* `REQ-MIG-049.3` (Multi-URI Import): `BackupRestoreViewModel.importWorkoutFilesInternal` SHALL pre-sort input URIs ascending by resolved filename via `ImportFileValidator.resolveDisplayName` or content resolver query using `String.CASE_INSENSITIVE_ORDER`.
* `REQ-MIG-049.4` (Cloud Recovery): `LegacyImportEngine.bulkRecoverFromDropbox` and `bulkRecoverFromGoogleDrive` SHALL sort deduplicated entries ascending by filename using `String.CASE_INSENSITIVE_ORDER` before channel dispatch.
* `REQ-MIG-049.5` (Cluster & DB Invariants): First ingested workout in a cluster SHALL receive `#1`, aligning with oldest activity date. Monotonic incrementation and natural SQLite autoincrement `_id` timeline alignment MUST be preserved.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Strava Sort Order)**: *Given* `activities.csv` with descending activity IDs `[300, 200, 100]`, *When* ingested by `StravaBulkExportImporter`, *Then* activities are dispatched in ascending order `[100, 200, 300]`.
* **Criterion 2 (SAF & Multi-URI Sort)**: *Given* unsorted file URIs (`2024-02.fit`, `2024-01.fit`), *When* scanned or imported via `SafDirectoryScanner` / `BackupRestoreViewModel`, *Then* files are processed in ascending filename order.
* **Criterion 3 (Cloud Recovery Sort)**: *Given* unordered Dropbox/GDrive metadata entries, *When* recovered via `LegacyImportEngine`, *Then* deduplicated entries are enqueued ascending by filename.

### 1.4 System Invariants
* Existing deduplication (`isWorkoutExisting`), Zip-Slip security (`REQ-MIG-040`), equipment matching, and coroutine cooperative cancellation remain 100% invariant.

---

## 2. Test Specification (TST-MIG-047)

### Test Case 1: Strava Bulk Export Ascending Order (`TST-MIG-047.1`)
* **Scope**: Unit Test (`StravaBulkExportImporterTest.kt`)
* **Preconditions**: Strava ZIP fixture with `activities.csv` listing IDs in reverse order `[3002, 3001, 3000]`.
* **Action**: Invoke `StravaBulkExportImporter.importArchive()`.
* **Expected Result**: Listener progress and import calls verify processing in ascending order `[3000, 3001, 3002]`.

### Test Case 2: SAF Directory Scanner Ascending Order (`TST-MIG-047.2`)
* **Scope**: Unit Test (`SafDirectoryScannerTest.kt`)
* **Preconditions**: Mock DocumentFile directory returning unsorted children `["track_c.fit", "track_a.fit", "track_b.fit"]`.
* **Action**: Invoke `SafDirectoryScanner.scanDocumentFile()`.
* **Expected Result**: Returned `List<Uri>` is sorted ascending by filename: `["track_a.fit", "track_b.fit", "track_c.fit"]`.

### Test Case 3: Cloud Recovery & Multi-File Entry Sorting (`TST-MIG-047.3`)
* **Scope**: Unit Test (`LegacyImportEngineDeduplicationTest.kt`)
* **Preconditions**: List of unsorted Dropbox/GDrive `Metadata` entries.
* **Action**: Execute entry deduplication and sorting pipeline.
* **Expected Result**: Deduplicated entries are ordered chronologically ascending by filename.

### Test Case 4: Localization & Tier 1 Regression (`TST-MIG-047.4`)
* **Scope**: Localization & Unit Regression
* **Action**: Run `TranslationParityTest` (9 locales) and targeted `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.*"`.
* **Expected Result**: 100% parity across 9 languages, 0 test failures.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Test Class / Procedure | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-MIG-047.1` | Unit | `StravaBulkExportImporterTest.testImportArchive_sortsActivitiesAscending` | `REQ-MIG-049.1` | Specified |
| `TST-MIG-047.2` | Unit | `SafDirectoryScannerTest.testScanDocumentFile_sortsUrisAscendingByName` | `REQ-MIG-049.2` | Specified |
| `TST-MIG-047.3` | Unit | `LegacyImportEngineDeduplicationTest.testCloudRecovery_sortsAscending` | `REQ-MIG-049.3`, `049.4` | Specified |
| `TST-MIG-047.4` | Audit/Reg | `TranslationParityTest` & `migration.*` | `REQ-MIG-049.5`, `REQ-UI-106` | Specified |
