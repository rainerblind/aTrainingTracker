# Implementation Plan: ATT-569 - Preserve Source Filename via Isolated Worker Subdirectories

## 1. Goal Description
Resolve the issue where a numeric counter prefix was prepended to the workout/file name in the database (e.g. `1_2026-08-01` instead of `2026-08-01`):
1. **Thread-Isolated Worker Subdirectories (`LegacyImportEngine.kt`)**: Instead of prefixing the filename as `${current}_${entry.name}`, create a dedicated worker subdirectory `val workerDir = File(tempDir, "job_$current"); workerDir.mkdirs()` and save the temporary file as `File(workerDir, entry.name)`.
2. **Preserve Source Filename**: `tcxFile.nameWithoutExtension` inside `importFromTcx()` will directly match `entry.name` without any modified prefix.

---

## 2. Proposed Changes

### Component 1: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt`
#### [MODIFY] [LegacyImportEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt)
- Update `bulkRecoverFromDropbox(...)`:
  - For each download task, create worker subdirectory `val workerDir = File(tempDir, "job_$current"); workerDir.mkdirs()`.
  - Save temporary file as `File(workerDir, entry.name)`.
  - Clean up `workerDir` in `finally` block.

---

## 3. Verification Plan

### Automated Tests
- Execute `:app:testDebugUnitTest` to verify import engine and database name generation logic.

### Manual Verification Steps (`TST-MIG-019`)
1. **Filename Integrity Verification**:
   - Import TCX file `2026-08-01_10-00-00.tcx`.
   - Verify `FILE_BASE_NAME` and `WORKOUT_NAME` in the database match `2026-08-01_10-00-00` exactly without any prepended counter numbers.
