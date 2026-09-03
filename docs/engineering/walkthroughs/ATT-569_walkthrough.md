# Walkthrough: ATT-569 - Preserve Source Filename via Isolated Worker Subdirectories

## 1. Overview
Resolved the issue where a numeric counter prefix (e.g. `1_`) was prepended to the workout name/file name in the database during TCX import:
- **Root Cause**: `bulkRecoverFromDropbox` saved temporary files as `File(tempDir, "${current}_${entry.name}")`. When `importFromTcx()` derived `baseFileName` from `tcxFile.nameWithoutExtension`, the filename included `${current}_`, which was stored in `WorkoutSummaries.FILE_BASE_NAME` and `WORKOUT_NAME`.
- **Fix**: Updated `bulkRecoverFromDropbox` in `LegacyImportEngine.kt` to create a worker-specific subdirectory `val workerDir = File(tempDir, "job_$current").apply { mkdirs() }` and save the download as `File(workerDir, entry.name)`. `tcxFile.nameWithoutExtension` now matches `entry.name` without any modified prefix.

---

## 2. Changes Made

### Migration Layer
- **[LegacyImportEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt)**: Created isolated worker subdirectories (`File(tempDir, "job_$current")`) for temporary downloads so the temporary file name maintains the original source filename (`entry.name`).

---

## 3. Verification Evidence

### Automated Unit Tests
Executed `:app:testDebugUnitTest`:
```text
BUILD SUCCESSFUL
14 passed, 0 skipped, 0 failed
```

### Requirements & Test Status
- **`REQ-MIG-022` / `TST-MIG-019`**: VERIFIED (`FILE_BASE_NAME` and `WORKOUT_NAME` preserve the original source filename without any counter prefix).
