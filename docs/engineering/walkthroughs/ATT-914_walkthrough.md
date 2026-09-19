# Walkthrough: ATT-914 Strava Routes - Periodically fetch from Strava

## Overview
- **Ticket**: ATT-914 (`[Feature] Strava Routes: Periodically fetch from Strava`)
- **Sub-task**: ATT-1171 (`[Subtask] [Implementation] Strava Routes: Periodically fetch from Strava`)
- **Target Version**: V4.9.37
- **Fulfills**: REQ-EXP-012, TST-EXP-009

---

## Changes Made

### 1. WorkManager Worker
* **`app/src/main/java/com/atrainingtracker/trainingtracker/routes/StravaRoutesSyncWorker.kt`**
  * Created `StravaRoutesSyncWorker` as a `CoroutineWorker` extending AndroidX WorkManager.
  * Implemented `schedule(context)` companion method that reads SharedPreferences (`isAutomatedStravaRoutesSync` and interval days). If disabled, cancels the unique periodic work `StravaRoutesSyncWorker`. If enabled, enqueues `PeriodicWorkRequestBuilder` with interval (clamped to minimum 1 day / 24 hours), exponential backoff, and network/battery constraints.
  * In `doWork()`, checks `StravaAuthManager.getInstance(context).isAuthenticated()`. If authenticated, calls `RoutesRepository.getInstance(applicationContext).syncRoutesFromStrava()`. Returns `Result.success()` or `Result.retry()` on transient network failures.

### 2. Application Preferences & Lifecycle Integration
* **`app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`**
  * Declared SharedPreferences constants `SP_AUTOMATED_STRAVA_ROUTES_SYNC` (`"automated_strava_routes_sync"`) and `SP_STRAVA_ROUTES_SYNC_INTERVAL_DAYS` (`"strava_routes_sync_interval_days"`).
  * Added getters and setters `isAutomatedStravaRoutesSync()`, `setAutomatedStravaRoutesSync(boolean)`, `getStravaRoutesSyncIntervalDays()`, and `setStravaRoutesSyncIntervalDays(int)`.
  * Invoked `StravaRoutesSyncWorker.Companion.schedule(this)` in `TrainingApplication.onCreate()`.

### 3. XML Preferences & Dialog UI
* **`app/src/main/res/xml/prefs_strava.xml`**
  * Declared `PreferenceCategory` for automated Strava route sync with `SwitchPreferenceCompat` and `ListPreference`.
* **`app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/strava/StravaSettingsDialog.kt`**
  * Added `automatedRoutesSync` and `routesSyncIntervalDays` states.
  * Rendered Automated Route Sync section with toggle switch and dropdown selector.
  * Dispatched `StravaRoutesSyncWorker.schedule(context)` when saving settings, connecting, or disconnecting.

### 4. Localization Parity
* Added string keys across all 9 supported locales:
  * `automated_strava_routes_sync`
  * `automated_strava_routes_sync_summary`
  * `strava_routes_sync_interval`
  * Locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

### 5. Unit Tests
* **`app/src/test/java/com/atrainingtracker/trainingtracker/routes/StravaRoutesSyncWorkerTest.kt`**
  * Comprehensive suite of 10 tests covering worker execution, repository sync invocation, retry behavior, cancellation when disabled, and WorkManager constraints.

---

## Test Results
- `StravaRoutesSyncWorkerTest`: 10 passed, 0 failed.
- `StravaSegmentsSyncWorkerTest`: 10 passed, 0 failed.
- `TranslationParityTest`: passed.
- `ModalBottomSheetDialogsIntegrityTest`: passed.
- `CoreDependencyAlignmentTest`: passed.
- Build & install: Debug APK successfully compiled and installed on Pixel 10.
