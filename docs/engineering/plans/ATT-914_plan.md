# Implementation Plan - ATT-914: Strava Routes Periodic Background Fetch

## 1. Context & Objectives
In `aTrainingTracker`, athletes can connect their Strava accounts to synchronize created and starred routes for map overlay, track following, and workout clustering. Currently, route synchronization only occurs when the user manually taps "Update Strava Routes" in the Strava Settings dialog or performs a pull-to-refresh on the Routes screen.
The objective of **ATT-914** is to provide automated periodic background synchronization of Strava routes via Android WorkManager, mirroring the look, feel, and interval patterns established for Strava segments (ATT-913) and Dropbox automated backups.

Key Deliverables:
1. **Background Worker (`StravaRoutesSyncWorker.kt`)**: Periodic WorkManager worker executing `RoutesRepository.getInstance(applicationContext).syncRoutesFromStrava()` under network and battery constraints.
2. **Preference & State Management (`TrainingApplication.java`)**: Persistent storage of automated routes sync enabled flag and interval in days.
3. **User Interface (`StravaSettingsDialog.kt`)**: Compose controls mirroring `automated_strava_segments_sync` with an interactive switch toggle and dropdown interval selector (`R.array.backup_interval_entries`).
4. **Declarative Preference Parity (`prefs_strava.xml`)**: Preference category for automated routes sync and intervals.
5. **Localization Parity**: 100% parity across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
6. **Verification**: Automated unit tests for worker scheduling, execution, preferences, UI states, and full clean-room regression.

---

## 2. Requirements & Verification Traceability
| Requirement ID | Component | Verification Test ID | Description |
|---|---|---|---|
| **REQ-EXP-012** | `StravaRoutesSyncWorker.kt` | **TST-EXP-009** | WorkManager periodic scheduling, network/battery constraints, background sync execution, and error retry. |
| **REQ-EXP-012** | `TrainingApplication.java` | **TST-EXP-009** | Getters, setters, and SharedPreferences persistence for automated routes sync flags. |
| **REQ-EXP-012** | `StravaSettingsDialog.kt` | **TST-EXP-009** | Automated routes sync switch, dropdown interval selector, and lifecycle schedule invocation. |
| **REQ-EXP-012** | `prefs_strava.xml` | **TST-EXP-009** | Preference XML declaration parity for automated routes sync and interval. |
| **REQ-EXP-012** | `strings.xml` (all 9 locales) | **TST-EXP-009** | 100% translation parity for automated route sync titles, summaries, and interval labels. |

---

## 3. Proposed Changes & Component Architecture

### 3.1 Background Execution Layer (`StravaRoutesSyncWorker.kt`)
* **New File**: `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/StravaRoutesSyncWorker.kt`
* **Implementation Details**:
  - Extends `androidx.work.CoroutineWorker`.
  - `doWork()`:
    1. Reads `TrainingApplication.getAutomatedStravaRoutesSync()` and `TrainingApplication.getStravaAccessToken()`.
    2. If disabled or unauthenticated, logs and returns `Result.success()` immediately.
    3. Calls `RoutesRepository.getInstance(applicationContext).syncRoutesFromStrava()`.
    4. If sync returns `true`, logs success and returns `Result.success()`.
    5. If sync returns `false` (token expired, network error, athlete ID resolution failure), returns `Result.retry()`.
    6. In case of unexpected exception, logs error and returns `Result.retry()`.
  - `companion object`:
    - `WORK_NAME = "automated_strava_routes_sync_work"`
    - `schedule(context: Context)`:
      - Verifies `TrainingApplication.isWorkManagerAvailable()`.
      - If automated routes sync is disabled or Strava disconnected, cancels unique work `WORK_NAME`.
      - If enabled and connected, builds constraints: `NetworkType.CONNECTED` and `requiresBatteryNotLow(true)`.
      - Enqueues `PeriodicWorkRequestBuilder<StravaRoutesSyncWorker>(intervalDays, TimeUnit.DAYS)` with exponential backoff using `ExistingPeriodicWorkPolicy.UPDATE`.

### 3.2 Preference & Lifecycle Integration (`TrainingApplication.java`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`
* **Changes**:
  - Define keys:
    - `SP_AUTOMATED_STRAVA_ROUTES_SYNC = "automated_strava_routes_sync"`
    - `SP_STRAVA_ROUTES_SYNC_INTERVAL_DAYS = "strava_routes_sync_interval_days"`
  - Implement static methods:
    - `getAutomatedStravaRoutesSync()` (default: `true`)
    - `setAutomatedStravaRoutesSync(boolean)`
    - `getStravaRoutesSyncIntervalDays()` (default: `"1"`)
    - `setStravaRoutesSyncIntervalDays(String)`
  - In `onCreate()`:
    - Invoke `StravaRoutesSyncWorker.Companion.schedule(this)` alongside `StravaSegmentsSyncWorker.Companion.schedule(this)`.

### 3.3 UI Integration (`StravaSettingsDialog.kt`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/strava/StravaSettingsDialog.kt`
* **Changes**:
  - Add state variables: `automatedRoutesSync` (Boolean) and `routesSyncIntervalDays` (String).
  - In the settings content:
    - Render an Automated Route Sync section directly beneath the Automated Segment Sync section.
    - Title: `@string/automated_strava_routes_sync`
    - Summary: `@string/automated_strava_routes_sync_summary`
    - Material 3 `Switch` scaled at `0.8f`.
    - When enabled, display `DropdownSelector` labeled with `@string/strava_routes_sync_interval` backed by `R.array.backup_interval_entries` and `R.array.backup_interval_values`.
  - In Save action:
    - Persist values to SharedPreferences via `TrainingApplication` setters.
    - Trigger `StravaRoutesSyncWorker.Companion.schedule(context)`.
  - In Strava disconnect and login success handlers:
    - Trigger `StravaRoutesSyncWorker.Companion.schedule(context)` to ensure background work is properly enqueued or cancelled.

### 3.4 Preference XML Parity (`prefs_strava.xml`)
* **File**: `app/src/main/res/xml/prefs_strava.xml`
* **Changes**:
  - Add `PreferenceCategory` for `automated_strava_routes_sync`.
  - Add `SwitchPreferenceCompat` for `automated_strava_routes_sync`.
  - Add `ListPreference` for `strava_routes_sync_interval_days`.

### 3.5 Localization Parity (`strings.xml` in 9 locales)
* **Files**: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`
* **New Keys**:
  - `automated_strava_routes_sync`
  - `automated_strava_routes_sync_summary`
  - `strava_routes_sync_interval`

---

## 4. Verification Plan & Test Specification

### 4.1 Automated Unit Tests
1. **`StravaRoutesSyncWorkerTest.kt`**:
   - `testSchedule_whenDisabled_cancelsWork`
   - `testSchedule_whenEnabled_enqueuesPeriodicWorkWithCorrectIntervalAndConstraints`
   - `testDoWork_whenDisconnectedOrDisabled_returnsSuccessWithoutSync`
   - `testDoWork_whenConnectedAndEnabled_callsSyncRoutesFromStravaAndReturnsSuccess`
   - `testDoWork_whenSyncFails_returnsRetry`
   - `testDoWork_whenExceptionThrown_returnsRetry`
   - `testExistingPeriodicWorkPolicy_usesUpdatePolicy`
   - `testConstraints_requiresConnectedNetworkAndBatteryNotLow`
2. **`TrainingApplication` Preference Accessor Tests**:
   - Verify defaults and setter/getter parity for routes sync preferences.
3. **`TranslationParityTest.kt`**:
   - Verify 100% translation presence across all 9 locales for new string resources.
4. **Clean-Room Regression Suite**:
   - `./gradlew testDebugUnitTest` across all modules.

---

## 5. Invariants & Safety Rules
* Manual route synchronization via `RoutesViewModel.syncStravaRoutes()` and `StravaSettingsDialog` manual update card remains completely unaltered.
* Route learning into `WorkoutClusterEngine` (`WorkoutClusterEngine.learnFromRoute`) is preserved identically.
* Battery and network constraints ensure zero background data drain when offline or low on battery.
