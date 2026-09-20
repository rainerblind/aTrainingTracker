# Stage 4 Implementation Walkthrough: Sync Strava Segments and Routes Every Day (ATT-1178)

## 1. Overview
In accordance with Strava API rules and 7-day TTL caching requirements, automated synchronization for Strava Segments and Strava Routes has been made mandatory and fixed to a daily (24-hour) schedule. The user-facing automated sync toggle switches and interval selectors have been removed from the Strava Settings dialog, while manual on-demand sync cards and selective upload settings are preserved.

## 2. Changes Made
- **[TrainingApplication.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java)**:
  - `isAutomatedStravaRoutesSyncEnabled()` and `isAutomatedStravaSegmentsSyncEnabled()` are hardcoded to return `true`.
  - `getStravaRoutesSyncIntervalDays()` and `getStravaSegmentsSyncIntervalDays()` return `"1"`.
- **[StravaSegmentsSyncWorker.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/segments/StravaSegmentsSyncWorker.kt)**:
  - `schedule(context)` enqueues periodic work with interval `1, TimeUnit.DAYS` if Strava access token is present; cancels periodic work if disconnected.
  - `doWork()` always executes TTL pruning (`pruneExpiredSegments()`), then checks Strava connection before syncing.
- **[StravaRoutesSyncWorker.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/routes/StravaRoutesSyncWorker.kt)**:
  - `schedule(context)` enqueues periodic work with interval `1, TimeUnit.DAYS` if Strava access token is present; cancels periodic work if disconnected.
  - `doWork()` always executes TTL pruning (`pruneExpiredRoutes()`), then checks Strava connection before syncing.
- **[StravaSettingsDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/strava/StravaSettingsDialog.kt)**:
  - Removed automated sync switches and interval dropdown pickers.
  - Retained manual sync cards (`updateStravaEquipment`, `updateStravaRoutes`, `updateStravaSegments`), showing the last updated timestamps and allowing on-demand manual refresh.
- **[StravaSegmentsSyncWorkerTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/segments/StravaSegmentsSyncWorkerTest.kt)**:
  - Updated unit tests to assert 1-day interval, disconnection cancellation, TTL pruning call, and sync execution.
- **[StravaRoutesSyncWorkerTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/routes/StravaRoutesSyncWorkerTest.kt)**:
  - Updated unit tests to assert 1-day interval, disconnection cancellation, TTL pruning call, and sync execution.

## 3. Verification & Test Results
- **Targeted Unit Tests**:
  - `StravaSegmentsSyncWorkerTest`: 4/4 PASS
  - `StravaRoutesSyncWorkerTest`: 4/4 PASS
- **Full Suite Clean-Room Regression**:
  - `./gradlew testDebugUnitTest`: BUILD SUCCESSFUL (0 failures, 0 regressions across all modules)

## 4. Sub-Task Status
- Sub-task **[ATT-1188](https://rainerblind.atlassian.net/browse/ATT-1188)** is in **Freigabe (Human)** awaiting human review and approval (*Freigabe erteilt*) before proceeding to Stage 5 (`[Test] ATT-1189`).
