# Stage 3: Implementation Plan (ATT-2341)

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

## 1. Architectural Overview & SWE.2 Design

The Dropbox bulk recovery subsystem in `LegacyImportEngine.kt` (`bulkRecoverFromDropbox`) currently enumerates candidate directories and filters files exclusively for `.tcx` and `.gpx` formats.
This plan details the atomic implementation steps to incorporate `.fit` binary files into the recursive Dropbox discovery, deduplication, download, and ingestion pipeline.

```
                    +------------------------------------+
                    | LegacyImportEngine.kt              |
                    | bulkRecoverFromDropbox(...)        |
                    +-----------------+------------------+
                                      |
                     +----------------v----------------+
                     | Format & Path Resolution:       |
                     | format = "fit" -> /FIT, etc.    |
                     | format = "all" -> TCX, GPX, FIT |
                     +----------------+----------------+
                                      |
                     +----------------v----------------+
                     | DbxClientV2 Recursive Scan      |
                     | filter: .tcx, .gpx, .fit        |
                     +----------------+----------------+
                                      |
                     +----------------v----------------+
                     | Cross-Path Deduplication        |
                     | baseName.removeSuffix(...)      |
                     +----------------+----------------+
                                      |
                     +----------------v----------------+
                     | Pre-Download Duplicate Check    |
                     | isWorkoutExisting(summaryDb)    |
                     +----------------+----------------+
                                      |
                               +------+------+
                               |             |
                         (new workout)   (existing)
                               |             |
                               v             v
                     +---------+----+  +-----+----------+
                     | Download to  |  | skippedCount++ |
                     | tempFile     |  +----------------+
                     +---------+----+
                               |
            +------------------+------------------+
            |                  |                  |
        (ext == "tcx")     (ext == "gpx")     (ext == "fit")
            |                  |                  |
            v                  v                  v
     importFromTcxInternal importFromGpxInternal importFromFitInternal
            |                  |                  |
            +------------------+------------------+
                               |
                     +---------v---------+
                     | Increment Counter |
                     | & Cleanup Temp    |
                     +-------------------+
```

---

## 2. Invariants & Chesterton's Fence Preservation

1. **Non-Breaking TCX & GPX Pipeline**: Existing candidate paths (`/TCX`, `/apps/Workouts/TCX`, `/GPX`, `/apps/Workouts/GPX`) and handlers for `.tcx` and `.gpx` must remain 100% identical in behavior and test assertion compatibility.
2. **Worker Channel Concurrency & Semaphores**: The 3-worker channel concurrency bounded by `interactionSemaphore(3)` must be strictly maintained to prevent memory pressure or thread starvation.
3. **Atomic Mutex Synchronization**: `importMutex` within `importFromFitInternal` guards against concurrent table creation and summary insertion collisions.
4. **Pre-Download Duplicate Skipping**: Files whose base name exists in `WorkoutSummariesDatabase` must be skipped before triggering any network download, saving athlete bandwidth and battery.
5. **Temporary Artifact Cleanup**: All temporary files created under `cacheDir/legacy_recovery/job_$current` must be cleaned up in `finally` blocks.

---

## 3. Atomic Implementation Steps (SWE.3)

### Step 1: Expand Target Paths and Extension Lists in `LegacyImportEngine.kt`
- Modify `possiblePaths` in `bulkRecoverFromDropbox`:
  ```kotlin
  val possiblePaths = when (format.lowercase()) {
      "tcx" -> listOf("/TCX", "/apps/Workouts/TCX")
      "gpx" -> listOf("/GPX", "/apps/Workouts/GPX")
      "fit" -> listOf("/FIT", "/apps/Workouts/FIT", "/Workouts/FIT")
      else -> listOf("/TCX", "/apps/Workouts/TCX", "/GPX", "/apps/Workouts/GPX", "/FIT", "/apps/Workouts/FIT", "/Workouts/FIT")
  }
  ```
- Modify `targetExtensions` in `bulkRecoverFromDropbox`:
  ```kotlin
  val targetExtensions = when (format.lowercase()) {
      "tcx" -> listOf(".tcx")
      "gpx" -> listOf(".gpx")
      "fit" -> listOf(".fit")
      else -> listOf(".tcx", ".gpx", ".fit")
  }
  ```

### Step 2: Route `.fit` Extension in Worker Channel Dispatch
- In `bulkRecoverFromDropbox`, update the status resolution block:
  ```kotlin
  val ext = entry.name.substringAfterLast('.').lowercase()
  val status = when (ext) {
      "tcx" -> importFromTcxInternal(context, tempFile, listener, uploadToStrava)
      "gpx" -> importFromGpxInternal(context, tempFile, listener, uploadToStrava)
      "fit" -> importFromFitInternal(context, tempFile, listener, uploadToStrava)
      else -> when (format.lowercase()) {
          "tcx" -> importFromTcxInternal(context, tempFile, listener, uploadToStrava)
          "gpx" -> importFromGpxInternal(context, tempFile, listener, uploadToStrava)
          "fit" -> importFromFitInternal(context, tempFile, listener, uploadToStrava)
          else -> ImportStatus.FAILED
      }
  }
  ```

### Step 3: Implement Targeted Unit & Contract Tests
- Create `DropboxBulkRecoveryFitContractTest.kt` in `app/src/test/java/com/atrainingtracker/trainingtracker/migration/`:
  - `testPossiblePathsAndExtensions_resolvesCorrectlyPerFormat`: Asserts path resolution for `"fit"`, `"tcx"`, `"gpx"`, and `"all"`.
  - `testPreDispatchDropboxEntryDeduplication_filtersFitDuplicatesAcrossFolders`: Asserts base name deduplication when `.fit` files reside across candidate folders.
  - `testWorkerChannelRouting_dispatchesFitToImportFromFitInternal`: Asserts dispatch to `importFromFitInternal` and correct tallying of `importedCount`, `skippedCount`, and `failedCount`.

### Step 4: Verification & Regression Testing
- Execute targeted unit tests:
  ```bash
  ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.migration.DropboxBulkRecoveryFitContractTest
  ```
- Execute full unit test suite for clean-room verification.

---

## 4. ASPICE Traceability

| Stage | Artefact | Verification Gate |
| :--- | :--- | :--- |
| Stage 1 | `docs/engineering/analysis/ATT-2341_analysis.md` | Gate 1 (Subtask `ATT-2540` - Passed) |
| Stage 2 | `docs/engineering/test_specs/ATT-2341_test_spec.md` (`REQ-MIG-035`, `TST-MIG-032`) | Gate 2 (Subtask `ATT-2541` - Passed) |
| Stage 3 | `docs/engineering/plans/ATT-2341_plan.md` | Gate 3 (Subtask `ATT-2542`) |
| Stage 4 | `LegacyImportEngine.kt`, `DropboxBulkRecoveryFitContractTest.kt` | Gate 4 |
| Stage 5 | Clean-Room Suite Regression & Walkthrough | Gate 5 |
