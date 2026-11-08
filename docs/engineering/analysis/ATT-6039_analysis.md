# Stage 1 Analysis: ATT-6039 - StravaBulkExportImporter & LegacyImportEngine: Unify bulk import processing order to chronological ascending (oldest first)

**Ticket**: [ATT-6039](https://rainerblind.atlassian.net/browse/ATT-6039)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Import, Export & Backup*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Branch**: `feature/ATT-6039`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
Bulk import flows across the app currently process historical activities in non-deterministic or reverse order:
1. **Strava Bulk Export (`StravaBulkExportImporter.kt:148`)**: Strava exports `activities.csv` in reverse-chronological order (newest first). The importer iterates top-to-bottom without re-ordering, ingesting newest workouts first.
2. **Sequential Cluster Numbering & DB ID Inversion**: In `WorkoutClusterEngine.assignClusterToWorkout` (lines 714–716, 737), auto-naming assigns `#1` to the first ingested workout. When importing newest-first, a 2026 workout receives `#1` while an older 2012 workout receives `#350`, inverting chronological numbering and SQLite `_id` order.
3. **SAF Directory, Multi-File, Dropbox & Google Drive Recovery**: Discovered files in `SafDirectoryScanner`, `BackupRestoreViewModel.importWorkoutFilesInternal`, and `LegacyImportEngine` (`bulkRecoverFromDropbox` / `bulkRecoverFromGoogleDrive`) rely on incidental filesystem/API order rather than deterministic ascending sorting.

---

## 2. Root Cause Analysis & Independent Peer Review
* **Root Cause**:
  - `StravaBulkExportImporter.kt`: `activities.forEachIndexed { ... }` runs directly on the parsed CSV list without sorting.
  - `SafDirectoryScanner.kt`: `scanDocumentFile` appends `child.uri` in arbitrary `listFiles()` traversal order.
  - `BackupRestoreViewModel.kt`: `importWorkoutFilesInternal` processes `uris` without pre-sorting by resolved filename.
  - `LegacyImportEngine.kt`: `bulkRecoverFromDropbox` (line 194) and `bulkRecoverFromGoogleDrive` (line 408) deduplicate entries but enqueue them directly into the worker channel without sorting.
* **Independent Peer Review & Verification (4-Augen-Prinzip)**:
  - Confirmed Strava activity IDs are monotonically increasing 64-bit integers (`Long`). Sorting by numeric `activityId.toLongOrNull() ?: Long.MAX_VALUE` ascending adheres to Rule 1.3 (numeric primitive sorting over display strings).
  - Confirmed ISO/standard workout filenames (`YYYY-MM-DD_HHMM...`) sort chronologically under case-insensitive string comparison.
  - Identified synergy with `ATT-5902` (`ExistingWorkoutsSnapshot`): Clean ascending ordering allows fast-forwarding to resume from the exact chronological cut-off point.

---

## 3. Scope Grounding & Non-Goals (ATT-1250)
* **In-Scope Goals**:
  - Sort Strava activities ascending by raw numeric `activityId.toLongOrNull()` (fallback `reversed()` if non-numeric).
  - Sort discovered URIs in `SafDirectoryScanner.scanDocumentFile` ascending by filename (`String.CASE_INSENSITIVE_ORDER`).
  - Pre-sort `uris` in `BackupRestoreViewModel.importWorkoutFilesInternal` ascending by resolved filename via `ImportFileValidator.resolveDisplayName` (Rule 36 DRY).
  - Sort deduplicated `entries` ascending by filename in `LegacyImportEngine.bulkRecoverFromDropbox` and `bulkRecoverFromGoogleDrive`.
  - Add unit tests verifying ascending processing order in `StravaBulkExportImporterTest`, `SafDirectoryScannerTest`, and `LegacyImportEngineDeduplicationTest`.
* **Out-of-Scope Non-Goals**: Modifying stream parsers (`FitWorkoutParser`, `TcxWorkoutParser`), altering database schemas, or changing clustering spatial tolerances.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Refined Requirements**: Extends `REQ-MIG-048` (*Strava Bulk Export*), `REQ-UI-363` (*SAF Directory Import*), and `REQ-MIG-031`/`034`/`035` (*Cloud Recovery*).
* **Historical Origin**: `ATT-4957` (Strava Bulk Export), `ATT-3491` (SAF Directory Scanner), `ATT-2341` (FIT Cloud Recovery).
* **Root Reason for Existing Formulation**: Initial implementations focused on format decoding and streaming memory efficiency; ingestion order was left implicit.
* **Preservation of Core Invariants**: Deduplication (`isWorkoutExisting`), Zip-Slip security (`REQ-MIG-040`), non-blocking cancel signals, and equipment synchronization remain 100% preserved.

---

## 5. System Invariants & Risk Assessment
* **Core Invariants**: Zero regression to standalone file imports; cluster spatial detection untouched; sequential counter `#1..#N` numbers chronologically from oldest to newest.
* **Risk Rating**: **LOW** (Deterministic in-memory sorting of pre-dispatch lists before the import loop).
