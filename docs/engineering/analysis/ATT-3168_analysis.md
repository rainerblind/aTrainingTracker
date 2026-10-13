# Stage 1 Analysis: ATT-3168 - Organize Google Drive workouts into format-specific directories (FIT, TCX, GPX) for export and import

**Ticket**: [ATT-3168](https://atrainingtracker.atlassian.net/browse/ATT-3168)  
**Sub-task**: [ATT-3267](https://atrainingtracker.atlassian.net/browse/ATT-3267) (`[Analysis]`)  
**Parent Epic**: [ATT-162](https://atrainingtracker.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.10`  
**Branch**: `feature/ATT-3168`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Problem Statement & Motivation

Currently, Google Drive workout export (`GoogleDriveUploader.kt`) hardcodes its destination directory to a single flat folder hierarchy: `aTrainingTracker/Workouts/`. Regardless of whether an athlete exports FIT, TCX, GPX, CSV, or GC JSON files, all files are stored in this single folder.

In contrast, Dropbox export (`DropboxUploader.java` via `exportInfo.shortPath` / `FileFormat.dirName`) and import categorize workouts into format-specific subdirectories:
- `/FIT/`
- `/TCX/`
- `/GPX/`
- `/CSV/`
- `/GC/`

Furthermore, when an athlete scans Google Drive for a specific file format (e.g., clicking "Google Drive scannen (FIT)" on the Import tab per `REQ-UI-293`), `LegacyImportEngine.kt` scans all candidate folders (`Workouts`, `FIT`, `TCX`, `GPX`, and root `aTrainingTracker`) rather than prioritizing the requested format folder.

### Expected Behavior
1. **Format-Specific Google Drive Export**: `GoogleDriveUploader` dynamically uploads workout files into format-specific subdirectories matching their `FileFormat.dirName` (e.g. `aTrainingTracker/FIT`, `aTrainingTracker/TCX`, `aTrainingTracker/GPX`, `aTrainingTracker/CSV`, `aTrainingTracker/GC`).
2. **Prioritized & Scoped Google Drive Import**: When initiating a format-specific bulk recovery (e.g. format `fit`, `tcx`, or `gpx`), `LegacyImportEngine` targets the corresponding format directory (`aTrainingTracker/<FORMAT>`) first, while gracefully preserving backward-compatibility fallback to `aTrainingTracker/Workouts` for historical files.
3. **Cross-Provider Structural Parity**: Google Drive and Dropbox adopt an identical directory taxonomy for workout activity archives.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1. Hardcoded Folder Hierarchy in `GoogleDriveUploader.kt`
In `GoogleDriveUploader.kt:67`:
```kotlin
val folderId = client.ensureFolderHierarchy(listOf("aTrainingTracker", "Workouts"))
```
The destination folder is hardcoded to `listOf("aTrainingTracker", "Workouts")`.
`exportInfo: ExportInfo` contains `fileFormat: FileFormat`, which exposes `fileFormat.dirName` (e.g., `"FIT"`, `"TCX"`, `"GPX"`, `"CSV"`, `"GC"`).
Instead of hardcoding `"Workouts"`, the uploader should resolve:
```kotlin
val folderNames = listOf("aTrainingTracker", exportInfo.fileFormat.dirName)
val folderId = client.ensureFolderHierarchy(folderNames)
```
This guarantees that each exported file is placed directly in its format directory, matching the path logic used by Dropbox (`"/" + exportInfo.shortPath`).

### 2.2. Folder Traversal Strategy in `LegacyImportEngine.kt`
In `LegacyImportEngine.kt:332-338`:
```kotlin
val candidateFolderHierarchies = listOf(
    listOf("aTrainingTracker", "Workouts"),
    listOf("aTrainingTracker", "FIT"),
    listOf("aTrainingTracker", "TCX"),
    listOf("aTrainingTracker", "GPX"),
    listOf("aTrainingTracker")
)
```
When `format` is passed (such as `"fit"`, `"tcx"`, or `"gpx"`), `LegacyImportEngine` queries all five folder hierarchies against the Google Drive REST API. For each folder, it calls `resolveFolderHierarchy` and `listFilesRecursively`, causing redundant network requests for unrelated folders before filtering by extension.
Instead, when a specific format is requested:
- For `"fit"`: target `listOf("aTrainingTracker", "FIT")` first, then legacy fallback `listOf("aTrainingTracker", "Workouts")` and `listOf("aTrainingTracker")`.
- For `"tcx"`: target `listOf("aTrainingTracker", "TCX")` first, then legacy fallback `listOf("aTrainingTracker", "Workouts")` and `listOf("aTrainingTracker")`.
- For `"gpx"`: target `listOf("aTrainingTracker", "GPX")` first, then legacy fallback `listOf("aTrainingTracker", "Workouts")` and `listOf("aTrainingTracker")`.
- For `"all"` (or other): target `listOf("aTrainingTracker", "FIT")`, `listOf("aTrainingTracker", "TCX")`, `listOf("aTrainingTracker", "GPX")`, then legacy fallback `listOf("aTrainingTracker", "Workouts")` and `listOf("aTrainingTracker")`.

Crucially, existing base filename deduplication:
```kotlin
val entries = allEntries.distinctBy {
    it.name.substringBeforeLast(".").removeSuffix("-TMP").removeSuffix("~").lowercase()
}
```
already prevents importing duplicates if a workout exists in both the format folder and the legacy `Workouts` folder.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Update `GoogleDriveUploader.kt` to resolve destination folder based on `exportInfo.fileFormat.dirName`.
  * Update `LegacyImportEngine.kt` to dynamically structure candidate folder hierarchies based on the requested import `format`, prioritizing `aTrainingTracker/<FORMAT>` while retaining `aTrainingTracker/Workouts` for historical backward-compatibility.
  * Update `GoogleDriveUploaderTest.kt` to verify that uploads target `listOf("aTrainingTracker", format.dirName)`.
  * Add unit tests in `LegacyImportEngine` or dedicated contract tests verifying format-scoped Google Drive folder candidate resolution.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to database backup folder (`aTrainingTracker/Backups/` in `GoogleDriveBackupManager.kt`). Backups must remain strictly isolated in `Backups/`.
  * No migration or deletion of existing remote files on athletes' Google Drives. Existing files in `Workouts/` remain untouched and discoverable via fallback import.
  * No change to Dropbox folder structures or OAuth scopes.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-DAT-020` (*Google Drive Integration for Automated Workout Export and Database Backup*) and `REQ-MIG-034` (*Google Drive Historical Workout File Discovery & Bulk Recovery*).
* **Historical Origin & Commit Trace**:
  * `REQ-DAT-020` was introduced in ticket `ATT-1306` (commit `f57007ef`, Sprint `2026-41.1`), refined in `ATT-2631` (Sprint `2026-41.3`).
  * `REQ-MIG-034` was introduced in ticket `ATT-2346` (commit `30d9afe1`, Sprint `2026-41.9`).
* **Root Reason for Existing Formulation**:
  * `REQ-DAT-020` originally specified `aTrainingTracker/Workouts/` as an initial flat container for all activity files during the initial Google Drive MVP.
  * `REQ-MIG-034` included `["aTrainingTracker", "Workouts"]` as the primary path because that was where the app previously exported files.
* **Preservation of Core Invariants**:
  * Least-privilege OAuth scope `https://www.googleapis.com/auth/drive.file` is strictly preserved.
  * Idempotent folder resolution (`ensureFolderHierarchy`) and concurrency synchronization (`hierarchyLock` and `globalFolderIdCache`) in `GoogleDriveClient.kt` are preserved.
  * Zero data loss: Historical workouts stored in `aTrainingTracker/Workouts` continue to be discovered and imported seamlessly.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1. Dynamic Folder Resolution in `GoogleDriveUploader.kt`
```kotlin
val targetHierarchy = listOf("aTrainingTracker", exportInfo.fileFormat.dirName)
val folderId = client.ensureFolderHierarchy(targetHierarchy)
```
If `exportInfo.fileFormat.dirName` is null or empty (defensive fallback), fallback to `"Workouts"`.

### 5.2. Format-Scoped Hierarchy Resolution in `LegacyImportEngine.kt`
```kotlin
val candidateFolderHierarchies = when (format.lowercase()) {
    "fit" -> listOf(
        listOf("aTrainingTracker", "FIT"),
        listOf("aTrainingTracker", "Workouts"),
        listOf("aTrainingTracker")
    )
    "tcx" -> listOf(
        listOf("aTrainingTracker", "TCX"),
        listOf("aTrainingTracker", "Workouts"),
        listOf("aTrainingTracker")
    )
    "gpx" -> listOf(
        listOf("aTrainingTracker", "GPX"),
        listOf("aTrainingTracker", "Workouts"),
        listOf("aTrainingTracker")
    )
    else -> listOf(
        listOf("aTrainingTracker", "FIT"),
        listOf("aTrainingTracker", "TCX"),
        listOf("aTrainingTracker", "GPX"),
        listOf("aTrainingTracker", "Workouts"),
        listOf("aTrainingTracker")
    )
}
```

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Least-privilege OAuth scope `drive.file` remains strictly intact.
  2. Complete backward compatibility for historical workout files stored in legacy `aTrainingTracker/Workouts/`.
  3. Base filename deduplication (`removeSuffix("-TMP").removeSuffix("~").lowercase()`) prevents duplicate imports across folders.
  4. Unlinked cloud safety: No crashes or exceptions when Google Drive is disconnected.
  5. 100% clean-room test suite pass rate (`./gradlew testDebugUnitTest`).
* **Risk Rating**: **LOW**
  * The changes are isolated to destination path resolution in `GoogleDriveUploader` and directory candidate generation in `LegacyImportEngine`. All underlying file transfer, encryption, token handling, and import parsing components remain unchanged.
