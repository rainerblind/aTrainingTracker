# Implementation Plan - ATT-602: Auto-Upload Imported Workouts (TCX) to Strava

## Problem Statement
When workouts are imported from TCX files (either via single file picker or bulk cloud recovery in `LegacyImportEngine.importFromTcx()`), the workout samples, summary statistics, and spatial clusters are created and stored in local SQLite databases, but no upload to Strava (or configured online communities) is scheduled or executed. Users must manually locate each imported workout and tap upload.

## User Review Required

> [!IMPORTANT]
> **Asynchronous Non-Blocking Execution**:
> Community uploads are scheduled asynchronously through the existing `ExportManager` and Android `WorkManager` architecture (`ExportWorker` with `NetworkType.CONNECTED` constraints). Import file parsing, local database insertion, and spatial recalculation will NEVER block waiting for network upload or Strava API responses. If network is unavailable, the local workout record and stats remain completely intact and available.

> [!NOTE]
> **Extensibility**:
> The trigger queries `ExportType.COMMUNITY.exportToFileFormats`. If future online community platforms (e.g. TrainingPeaks, Runkeeper) are activated in `ExportType.COMMUNITY`, they will automatically participate in post-import uploads without engine modifications.

---

## Requirement & Test Mapping

| Requirement ID | Description | Component(s) | Test ID | Jira Sub-task |
|:---|:---|:---|:---|:---|
| **REQ-EXT-008** | **Automated Community Upload for Imported Workouts.** Upon successful completion of TCX import and stats recalculation (`LegacyImportEngine.importFromTcx`), evaluate if community upload to Strava is active (`TrainingApplication.uploadToCommunity(FileFormat.STRAVA)`). If active, schedule an asynchronous upload task via `ExportManager`, persisting export status in `ExportStatusDatabaseManager` and queuing background execution via `WorkManager`. Initialize `UPLOAD_TO_STRAVA` to `1` in `WorkoutSummaries`. | `LegacyImportEngine.kt`, `ExportManager.java` | **TST-EXT-005** | `ATT-614` |

---

## Proposed Changes

### Component: `migration` (`com.atrainingtracker.trainingtracker.migration`)

#### [MODIFY] [LegacyImportEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt)
1. In `importFromTcx()`:
   * When inserting the new workout summary record (`summaryValues`), set `WorkoutSummaries.UPLOAD_TO_STRAVA` to `1` if `TrainingApplication.uploadToCommunity(FileFormat.STRAVA)` is true, or leave default `-1`.
   * Immediately following `recalculateStats()` completion, call `schedulePostImportCommunityUpload(context, workoutId, baseFileName)`.
2. Implement `schedulePostImportCommunityUpload(context: Context, workoutId: Long, baseFileName: String, exportManager: ExportManager = ExportManager(context))`:
   * Iterates through all active formats in `ExportType.COMMUNITY.exportToFileFormats`.
   * For each active format where `TrainingApplication.uploadToCommunity(format)` is enabled, verifies `UPLOAD_TO_STRAVA` flag is not explicitly disabled (`!= 0`).
   * Calls `exportManager.exportWorkoutTo(workoutId, format)` to generate the export file with user privacy filters and enqueue the background upload task with `WorkManager`.
   * Wraps scheduling in robust error handling to guarantee network/scheduling faults never abort or roll back a successful import.

---

### Component: `tests` (`com.atrainingtracker.trainingtracker.migration`)

#### [NEW] [TcxImportCommunityUploadTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/migration/TcxImportCommunityUploadTest.kt)
Create automated unit test suite verifying:
1. `testImportSchedulesStravaUploadWhenEnabled`: When `uploadToCommunity(FileFormat.STRAVA)` is true, `schedulePostImportCommunityUpload` invokes `exportWorkoutTo(workoutId, FileFormat.STRAVA)`.
2. `testImportSkipsUploadWhenCommunityDisabled`: When `uploadToCommunity(FileFormat.STRAVA)` is false, no export or upload tasks are scheduled.
3. `testImportRespectsExplicitOptOut`: When `uploadToStrava` in `WorkoutSummaries` is `0` (opt-out), Strava upload is bypassed.
4. `testImportRobustnessAgainstSchedulingExceptions`: Verifies that if `ExportManager` throws an exception, `schedulePostImportCommunityUpload` catches it gracefully without crashing or invalidating the imported workout.

---

## Verification Plan

### Automated Tests
* Execute the new unit test suite:
  ```bash
  ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.migration.TcxImportCommunityUploadTest
  ```
* Execute full regression test suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```

### Manual Verification
1. Connect Strava in app settings (`StravaUploadFragment`).
2. Import a `.tcx` workout file via the file picker in *Import & Backup*.
3. Verify that an export status notification appears in the notification drawer / `WorkoutSummaries` card indicating the workout is queued for Strava upload.
4. Verify the workout appears in Strava after upload completion.
