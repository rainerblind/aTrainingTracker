# Stage 1: Problem Domain & Root Cause Analysis (ATT-2341)

**Ticket**: [ATT-2341](https://atrainingtracker.atlassian.net/browse/ATT-2341)  
**Summary**: [Verbesserung] Scan Dropbox for FIT workout files during bulk recovery  
**Component**: Import / Cloud Sync (`LegacyImportEngine.kt`)  
**Parent Epic**: [ATT-1117](https://atrainingtracker.atlassian.net/browse/ATT-1117) (*[Epic] FIT File Format Support (Export & Import)*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Problem Description & Operational Context

When athletes use the cloud recovery scanner ("Scan Dropbox" / `scan_tcx`) on the Import & Backup tab, `LegacyImportEngine.bulkRecoverFromDropbox` only checks for legacy XML workout files (`.tcx`, `.gpx`).
Athletes migrating from modern bike computers (Garmin Edge, Wahoo ELEMNT, Hammerhead Karoo) or smartwatch ecosystems store their activity archives in Dropbox as binary `.fit` files (e.g. in `/FIT`, `/apps/Workouts/FIT`, or `/Workouts/FIT`).
Currently:
1. `possiblePaths` in `bulkRecoverFromDropbox` only contains `/TCX`, `/apps/Workouts/TCX`, `/GPX`, and `/apps/Workouts/GPX`.
2. `targetExtensions` only contains `listOf(".tcx")`, `listOf(".gpx")`, or `listOf(".tcx", ".gpx")`. Any `.fit` files present in scanned directories are ignored during folder discovery.
3. Even if a `.fit` file was discovered, the background worker channel handler in `bulkRecoverFromDropbox` lacks a handler for `.fit`, falling through to `ImportStatus.FAILED`.

---

## 2. Root Cause Analysis

Historically, `bulkRecoverFromDropbox` was constructed for legacy TCX migration (`ATT-529`), later expanded to support GPX. While native FIT file import support was introduced in `ATT-1828` (`REQ-DAT-019`) via `LegacyImportEngine.importFromFitInternal`, it was integrated only into the local storage file picker (`pickFitFilesLauncher`). The Dropbox bulk recovery scanner was never updated with FIT paths, extensions, or worker routing.

---

## 3. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Target & Historical Trace**:
   - `REQ-MIG-016`: Introduced recursive Dropbox folder discovery and cursor-based pagination.
   - `REQ-MIG-031` (`ATT-2023`): Introduced multi-dimensional start-time deduplication with 3-minute same-sport window (`isWorkoutExisting`) and cross-folder base name deduplication prior to worker queuing.
   - `REQ-DAT-019` (`ATT-1828`): Introduced Garmin FIT SDK parsing via `importFromFitInternal`.
   - `REQ-MIG-034` (`ATT-2346`): Introduced Google Drive multi-format bulk recovery (.fit, .tcx, .gpx).
2. **Chesterton's Fence Audit**:
   - *Why did `possiblePaths` only look for TCX and GPX?* Because at the time the method was written, aTrainingTracker only supported TCX and GPX. The method name `bulkRecoverFromDropbox` was originally named `bulkRecoverLegacyData`, referring to the old XML formats.
   - *Can we safely add `/FIT`, `/apps/Workouts/FIT`, `/Workouts/FIT` and `".fit"`?* Yes. Dropbox folder listings check whether each folder exists before traversing, catching `PathLookupException` gracefully.
   - *How do we prevent cross-format duplicate imports?* `isWorkoutExisting` checks `FILE_BASE_NAME` and start timestamp $\pm 180$ seconds. If an activity exists in the local database (whether imported from TCX, GPX, or recorded live), the FIT file is skipped before downloading.
3. **Core Invariants to Preserve**:
   - Existing TCX and GPX recovery flows must not regress.
   - 3-worker channel concurrency bounded by `interactionSemaphore(3)` must remain intact.
   - Atomic coroutine `importMutex` in `importFromFitInternal` guards Room / SQLite insertions.
   - Reactive post-recovery reconciliation (`WorkoutRepository.loadAllWorkouts()`, `PeriodsRepository.syncPeriodsIfDiscrepancy()`, `WorkoutClusterRepository.refreshClusters()`) must execute seamlessly.

---

## 4. Proposed Technical Solution & Scope

### 4.1. Directory & Extension Expansion (`LegacyImportEngine.kt`)
Update `possiblePaths` in `bulkRecoverFromDropbox`:
```kotlin
val possiblePaths = when (format.lowercase()) {
    "tcx" -> listOf("/TCX", "/apps/Workouts/TCX")
    "gpx" -> listOf("/GPX", "/apps/Workouts/GPX")
    "fit" -> listOf("/FIT", "/apps/Workouts/FIT", "/Workouts/FIT")
    else -> listOf(
        "/TCX", "/apps/Workouts/TCX",
        "/GPX", "/apps/Workouts/GPX",
        "/FIT", "/apps/Workouts/FIT", "/Workouts/FIT"
    )
}
```
Update `targetExtensions`:
```kotlin
val targetExtensions = when (format.lowercase()) {
    "tcx" -> listOf(".tcx")
    "gpx" -> listOf(".gpx")
    "fit" -> listOf(".fit")
    else -> listOf(".tcx", ".gpx", ".fit")
}
```

### 4.2. Worker Channel Dispatch (`LegacyImportEngine.kt`)
Handle `"fit"` extensions in the worker channel:
```kotlin
val ext = entry.name.substringAfterLast('.').lowercase()
val status = when (ext) {
    "fit" -> importFromFitInternal(context, tempFile, listener, uploadToStrava)
    "tcx" -> importFromTcxInternal(context, tempFile, listener, uploadToStrava)
    "gpx" -> importFromGpxInternal(context, tempFile, listener, uploadToStrava)
    else -> when (format.lowercase()) {
        "fit" -> importFromFitInternal(context, tempFile, listener, uploadToStrava)
        "tcx" -> importFromTcxInternal(context, tempFile, listener, uploadToStrava)
        "gpx" -> importFromGpxInternal(context, tempFile, listener, uploadToStrava)
        else -> ImportStatus.FAILED
    }
}
```

### 4.3. Testing Strategy
1. **Contract Tests (`DropboxBulkRecoveryFitContractTest.kt`)**:
   - Verify path and extension resolution for `format = "fit"`, `format = "all"`, `format = "tcx"`, `format = "gpx"`.
   - Verify that `.fit` files with existing session timestamps are skipped prior to download.
   - Verify cross-folder deduplication across `/FIT`, `/apps/Workouts/FIT`, and `/Workouts/FIT`.
2. **Full Regression**:
   - Run full unit test suite `./gradlew testDebugUnitTest`.

---

## 5. ASPICE Gates & Subtask Roadmap

- **Stage 1**: Subtask `ATT-2540` - Analysis & Gate 1 audit.
- **Stage 2**: Subtask `ATT-2541` - Formulate `REQ-MIG-035` and `TST-MIG-032` in living docs.
- **Stage 3**: Subtask `ATT-2542` - Implementation plan.
- **Stage 4**: Subtask `ATT-2543` - Code implementation & targeted contract tests.
- **Stage 5**: Subtask `ATT-2544` - Full regression, walkthrough, merge, and final review assignment.
