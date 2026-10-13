# Stage 2: Requirement & Test Specification (ATT-2341)

**Ticket**: [ATT-2341](https://atrainingtracker.atlassian.net/browse/ATT-2341)  
**Summary**: [Verbesserung] Scan Dropbox for FIT workout files during bulk recovery  
**Requirement**: `REQ-MIG-035`  
**Test Case**: `TST-MIG-032`  
**Parent Epic**: [ATT-1117](https://atrainingtracker.atlassian.net/browse/ATT-1117) (*[Epic] FIT File Format Support (Export & Import)*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Requirement Specification (REQ-MIG-035)

### 1.1. Formal Requirement Statement
The system SHALL extend Dropbox cloud recovery in `LegacyImportEngine.bulkRecoverFromDropbox` to discover, download, and import historical `.fit` workout activity files (ATT-2341):
1. **Target Directory Expansion (`LegacyImportEngine.kt`)**:
   - When `format` is `"all"` (or default), `possiblePaths` SHALL scan `listOf("/TCX", "/apps/Workouts/TCX", "/GPX", "/apps/Workouts/GPX", "/FIT", "/apps/Workouts/FIT", "/Workouts/FIT")`.
   - When `format` is `"fit"`, `possiblePaths` SHALL scan `listOf("/FIT", "/apps/Workouts/FIT", "/Workouts/FIT")`.
2. **Target Extension Expansion (`LegacyImportEngine.kt`)**:
   - When `format` is `"all"` (or default), `targetExtensions` SHALL filter `listOf(".tcx", ".gpx", ".fit")`.
   - When `format` is `"fit"`, `targetExtensions` SHALL filter `listOf(".fit")`.
3. **Worker Channel Routing (`LegacyImportEngine.kt`)**:
   - In the background worker channel, discovered files with extension `"fit"` SHALL be routed to `importFromFitInternal(context, tempFile, listener, uploadToStrava)`.
   - When `status` returns `ImportStatus.SUCCESS`, `importedCount` SHALL be incremented; when `ImportStatus.DUPLICATE_SKIPPED`, `skippedCount` SHALL be incremented; when `ImportStatus.FAILED`, `failedCount` SHALL be incremented.
4. **Pre-Download Duplicate Skipping & Cross-Folder Deduplication**:
   - Discovered entries SHALL be deduplicated across scanned paths by base filename (`removeSuffix("-TMP").removeSuffix("~").lowercase()`) per `REQ-MIG-031`.
   - Prior to downloading, each entry SHALL be evaluated via `isWorkoutExisting(summaryDb, baseFileName)`. Existing workouts SHALL be skipped immediately without invoking `downloadFileWithRetry`.
5. **Architectural & Invariant Preservation**:
   - Existing TCX and GPX recovery flows SHALL remain completely intact.
   - 3-worker channel concurrency bounded by `interactionSemaphore(3)` SHALL remain preserved.
   - Temporary file artifacts created under `cacheDir/legacy_recovery/` SHALL be cleaned up.

### 1.2. Requirement Archaeology & Chesterton's Fence Audit
1. *Original Target & Historical Trace*: Expands `REQ-MIG-016` (*Paginated & Recursive Cloud Recovery*), `REQ-MIG-031` (*Multi-Dimensional Workout Deduplication*), and `REQ-DAT-019` (*Garmin FIT File Import Support*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2341`, sprint `2026-41.1`, target release `V4.9.39`, Epic `ATT-1117` (*[Epic] FIT File Format Support*).
3. *Root Reason for Existing Formulation*: Previous implementation was strictly constrained to TCX and GPX formats from legacy migrations.
4. *Preservation of Core Invariants*: Full backward compatibility for TCX and GPX bulk imports is preserved; all existing unit tests asserting Dropbox recovery will continue to pass unmodified.

### 1.3. Acceptance Criteria (Given-When-Then)
- **AC-1 (Target Paths & Extension Resolution)**:
  - *Given* a bulk recovery request with `format = "fit"`,
  - *When* `bulkRecoverFromDropbox` initializes candidate paths and extensions,
  - *Then* `possiblePaths` contains `/FIT`, `/apps/Workouts/FIT`, and `/Workouts/FIT`, and `targetExtensions` contains `".fit"`.
- **AC-2 (Multi-Format Discovery)**:
  - *Given* a bulk recovery request with `format = "all"`,
  - *When* `bulkRecoverFromDropbox` scans Dropbox folders,
  - *Then* `.fit`, `.tcx`, and `.gpx` files are discovered and queued.
- **AC-3 (Pre-Download Duplicate Skipping)**:
  - *Given* a `.fit` file in Dropbox whose base filename matches an existing workout in `WorkoutSummariesDatabase`,
  - *When* the recovery worker inspects the queue entry,
  - *Then* `downloadFileWithRetry` is NOT invoked, and `skippedCount` is incremented.
- **AC-4 (FIT Worker Channel Dispatch)**:
  - *Given* a new `.fit` workout file downloaded from Dropbox,
  - *When* the worker processes the file,
  - *Then* it dispatches to `importFromFitInternal`, parsing telemetry and saving the workout to `WorkoutSummaries`.

---

## 2. Test Specification (TST-MIG-032)

### 2.1. Test Cases
1. **Contract Tests (`DropboxBulkRecoveryFitContractTest.kt`)**:
   - `testPossiblePathsAndExtensions_resolvesCorrectlyPerFormat`: Validates exact path lists and extension lists for `"fit"`, `"tcx"`, `"gpx"`, and `"all"`.
   - `testPreDispatchDropboxEntryDeduplication_filtersFitDuplicatesAcrossFolders`: Validates base name deduplication when `.fit` files reside in multiple candidate folders (e.g. `/FIT` vs `/apps/Workouts/FIT`).
   - `testPreDownloadDuplicateSkipping_skipsExistingFitWorkouts`: Validates that `isWorkoutExisting` skips download and increments `skippedCount`.
2. **Clean-Room Full Suite Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate across all modules.

---

## 3. ASPICE Traceability Matrix

| Requirement | Test Specification | Implementation Target | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-MIG-035` | `TST-MIG-032` | `LegacyImportEngine.kt` | Unit & Contract Tests (`DropboxBulkRecoveryFitContractTest`), Clean-Room Suite |
