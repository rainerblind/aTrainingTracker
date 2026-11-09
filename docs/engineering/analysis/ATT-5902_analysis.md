# Stage 1 Analysis: ATT-5902 - StravaBulkExportImporter & LegacyImportEngine: Pre-load existing workout identifiers in memory to fast-forward bulk imports without stream parsing or file copying

**Ticket**: [ATT-5902](https://rainerblind.atlassian.net/browse/ATT-5902)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Import, Export & Backup*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Branch**: `feature/ATT-5902`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
When resuming or repeating large bulk imports (Strava bulk ZIP archives, local multi-file/SAF folders, or Dropbox/Google Drive recovery), the pipeline performs expensive I/O and CPU parsing on already-imported workouts before detecting duplicates:
1. **`StravaBulkExportImporter.kt:167–207`**: For every activity in `activities.csv`, it decompresses `.gz` streams and runs full XML/FIT parsers into memory before `persistParsedWorkout` detects `isWorkoutExisting(...)` at line 247/735.
2. **`BackupRestoreViewModel.kt:712–718`**: Copies every SAF URI via `contentResolver.openInputStream` into `cacheDir` temporary files before `importFrom*Result` checks for pre-existing workouts.
3. **`LegacyImportEngine.kt:228, 441`**: Executes individual SQLite `SELECT` queries per entry across Dropbox and Google Drive recovery instead of checking an in-memory set.

---

## 2. Root Cause Analysis & Independent Peer Review
* **Root Cause**:
  - Importers lack an upstream in-memory deduplication snapshot. Deduplication is deferred to downstream SQLite checks (`isWorkoutExisting`) inside `persistParsedWorkout` after stream decompression, parsing, or temporary file copying has already occurred.
* **Independent Peer Review & Verification (4-Augen-Prinzip)**:
  - Confirmed `baseFileName` (`resolveBaseFileName(filename, row.activityId)`) and `row.activityId` are known directly from `activities.csv` without opening stream entries.
  - Confirmed SQLite `FILE_BASE_NAME = ? COLLATE NOCASE` is case-insensitive; in-memory set MUST normalize lookups using `lowercase(Locale.ROOT)`.
  - Audited `legacy_import` prefix invariant (`LegacyImportEngine.kt:722`): workouts prefixed with `legacy_import` derive their base name from parsed timestamps, so they must bypass filename-only pre-filtering.
  - Audited concurrency: Dropbox and Google Drive run across 3 concurrent coroutine workers; the shared snapshot must be read-only or thread-safe during execution.

---

## 3. Scope Grounding & Non-Goals (ATT-1250)
* **In-Scope Goals**:
  - Introduce `ExistingWorkoutsSnapshot` loading lowercased `FILE_BASE_NAME`s from `WorkoutSummaries` and `ActivityId`s from `StravaUploadDbHelper` in a single pass (< 3 ms).
  - Fast-path pre-filter in `StravaBulkExportImporter.importArchive` before `zip.getInputStream`, GZIP decompression, and FIT/TCX/GPX parsing.
  - Fast-path pre-filter in `BackupRestoreViewModel.importWorkoutFilesInternal` before opening SAF streams and copying to `context.cacheDir`.
  - In-memory pre-filter in `LegacyImportEngine.bulkRecoverFromDropbox` and `bulkRecoverFromGoogleDrive` to eliminate per-entry SQLite queries.
  - Unit tests verifying zero stream decompression, zero file copying, and instant (< 50 ms) fast-forwarding for duplicate batches.
* **Out-of-Scope Non-Goals**: Modifying binary FIT/TCX/GPX stream parser implementations, altering database schema/indices, or removing the transactional `isWorkoutExisting` guard in `persistParsedWorkout`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Refined Requirements**: Extends `REQ-MIG-031` (*Multi-Dimensional Workout Deduplication*), `REQ-MIG-048.5` (*Strava Bulk Export Deduplication*), and `REQ-MIG-049.5` (*Bulk Import Invariants*).
* **Historical Origin**: `ATT-2023` (commit `9b32948` - 3-min same-sport tolerance), `ATT-4957` (commit `33856f04` - Strava bulk export), `ATT-6039` (commit `163e83a6` - chronological ascending order).
* **Root Reason for Existing Formulation**: Transactional `isWorkoutExisting` in `persistParsedWorkout` guarantees consistency at database insertion time. Deferring the check avoided managing cache state across varied import entry points.
* **Preservation of Core Invariants**: Downstream `isWorkoutExisting` inside `importMutex.withLock` remains 100% intact as a defense-in-depth safety net. The in-memory snapshot acts strictly as an upstream fast-forward accelerator.

---

## 5. System Invariants & Risk Assessment
* **Core Invariants**: Zero regression in deduplication accuracy; no unimported workout skipped; `legacy_import` files safely parsed; downstream database integrity untouched.
* **Risk Rating**: **LOW** (Upstream read-only caching optimization of existing database identifiers without altering database schemas, transaction lifecycles, or parser logic).
