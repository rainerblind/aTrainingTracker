# Implementation Plan - ATT-913: Strava Segments Periodic Background Fetch

## 1. Context & Objectives
In `aTrainingTracker`, athletes can connect their Strava accounts to synchronize starred segments. Currently, synchronization only occurs when the user manually taps a sync action or logs in.
The objective of **ATT-913** is to provide automated periodic background synchronization of starred Strava segments via Android WorkManager, mirroring the look, feel, and interval patterns established for Dropbox automated backups.

Key Deliverables:
1. **Background Worker (`StravaSegmentsSyncWorker.kt`)**: Periodic WorkManager worker executing `SegmentsRepository.syncStarredSegments(BSportType.UNKNOWN)` under network and battery constraints.
2. **Preference & State Management (`TrainingApplication.java`)**: Persistent storage of automated sync enabled flag, interval in days, and last update timestamp.
3. **User Interface (`StravaSettingsDialog.kt`)**: Compose controls mirroring `DropboxSettingsDialog.kt` with a switch toggle and dropdown interval selector (`R.array.backup_interval_entries`).
4. **Manual Sync Trigger**: An `OutlinedCard` displaying last sync timestamp and triggering on-demand segment synchronization.
5. **Preference Parity (`prefs_strava.xml`)**: Declarative preferences for automated segments sync and intervals.
6. **Localization Parity**: 100% parity across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
7. **Verification**: Automated unit tests for worker scheduling, execution, preferences, and full clean-room regression.

---

## 2. Requirements & Verification Traceability
| Requirement ID | Component | Verification Test ID | Description |
|---|---|---|---|
| **REQ-EXP-011** | `StravaSegmentsSyncWorker.kt` | **TST-EXP-008** | WorkManager periodic scheduling, network constraints, background sync execution, and error retry. |
| **REQ-EXP-011** | `TrainingApplication.java` | **TST-EXP-008** | Getters, setters, and SharedPreferences persistence for automated sync flags and timestamps. |
| **REQ-EXP-011** | `StravaSettingsDialog.kt` | **TST-EXP-008** | Automated sync switch, dropdown interval selector, manual sync card, and save integration. |
| **REQ-EXP-011** | `prefs_strava.xml` | **TST-EXP-008** | Preference XML declaration parity for automated segment sync and interval. |
| **REQ-EXP-011** | `strings.xml` (all 9 locales) | **TST-EXP-008** | 100% translation parity for automated sync titles, summaries, and interval labels. |

---

## 3. Proposed Changes & Component Architecture

### 3.1 Background Execution Layer (`StravaSegmentsSyncWorker.kt`)
* **New File**: `app/src/main/java/com/atrainingtracker/trainingtracker/segments/StravaSegmentsSyncWorker.kt`
* **Implementation Details**:
  - Extends `androidx.work.CoroutineWorker`.
  - `doWork()`:
    1. Reads `TrainingApplication.isAutomatedStravaSegmentsSyncEnabled()` and `TrainingApplication.getStravaAccessToken()`.
    2. If disabled or unauthenticated, returns `Result.success()` immediately.
    3. Calls `SegmentsRepository.getInstance(applicationContext).syncStarredSegments(BSportType.UNKNOWN)`.
    4. Upon success, formats current timestamp with `SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())` and writes to `TrainingApplication.setLastUpdateTimeOfStravaSegments(...)`.
    5. Returns `Result.success()`.
    6. In case of `IOException` or network failure, logs warning and returns `Result.retry()`.
  - `companion object`:
    - `WORK_NAME = "automated_strava_segments_sync_work"`
    - `schedule(context: Context)`:
      - Verifies `TrainingApplication.isWorkManagerAvailable()`.
      - If automated sync disabled or Strava disconnected, cancels unique work.
      - If enabled, builds constraints: `NetworkType.CONNECTED` and `requiresBatteryNotLow(true)`.
      - Enqueues `PeriodicWorkRequestBuilder<StravaSegmentsSyncWorker>(intervalDays, TimeUnit.DAYS)` using `ExistingPeriodicWorkPolicy.UPDATE`.

### 3.2 Preference & Lifecycle Integration (`TrainingApplication.java`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`
* **Changes**:
  - Define keys:
    - `SP_AUTOMATED_STRAVA_SEGMENTS_SYNC = "automated_strava_segments_sync"`
    - `SP_STRAVA_SEGMENTS_SYNC_INTERVAL_DAYS = "strava_segments_sync_interval_days"`
    - `SP_LAST_UPDATE_TIME_OF_STRAVA_SEGMENTS = "last_update_time_of_strava_segments"`
  - Implement static methods:
    - `isAutomatedStravaSegmentsSyncEnabled()` (default: `true`)
    - `setAutomatedStravaSegmentsSyncEnabled(boolean)`
    - `getStravaSegmentsSyncIntervalDays()` (default: `"1"`)
    - `setStravaSegmentsSyncIntervalDays(String)`
    - `getLastUpdateTimeOfStravaSegments()` (default: `@string/lastUpdateOfSegmentsNever`)
    - `setLastUpdateTimeOfStravaSegments(String)`
  - In `onCreate()`:
    - If `isWorkManagerAvailable()` and `getStravaAccessToken() != null`, call `StravaSegmentsSyncWorker.schedule(this)`.

### 3.3 UI Integration (`StravaSettingsDialog.kt`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/strava/StravaSettingsDialog.kt`
* **Changes**:
  - Add state for automated sync toggle (`automatedSegmentsSync`), interval days (`segmentsSyncIntervalDays`), and last update (`segmentsLastUpdate`).
  - Listen for changes to `SP_LAST_UPDATE_TIME_OF_STRAVA_SEGMENTS` in `DisposableEffect`.
  - In the manual sync card section:
    - Add an `OutlinedCard` for `updateStravaSegments` with `segmentsLastUpdate` summary, invoking `SegmentsRepository.getInstance(context).syncSegmentsAsync(BSportType.UNKNOWN)` on click.
  - In the automated sync configuration section (mirroring `DropboxSettingsDialog.kt`):
    - Row with title (`@string/automated_strava_segments_sync`), description (`@string/automated_strava_segments_sync_summary`), and Material 3 `Switch`.
    - If enabled, render `DropdownSelector` for interval using `R.array.backup_interval_entries` and `R.array.backup_interval_values`.
  - In `AppDialogActions.SaveCancel`:
    - Persist values to SharedPreferences.
    - Trigger `StravaSegmentsSyncWorker.schedule(context)`.

### 3.4 Preference XML Parity (`prefs_strava.xml`)
* **File**: `app/src/main/res/xml/prefs_strava.xml`
* **Changes**:
  - Add `Preference` for `updateStravaSegments`.
  - Add `SwitchPreferenceCompat` for `automated_strava_segments_sync`.
  - Add `ListPreference` for `strava_segments_sync_interval_days`.

### 3.5 Localization Parity (`strings.xml` in 9 locales)
* **Files**: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`
* **New Keys**:
  - `updateStravaSegments`
  - `lastUpdateOfSegmentsNever`
  - `automated_strava_segments_sync`
  - `automated_strava_segments_sync_summary`
  - `strava_segments_sync_interval`

---

## 4. Verification Plan & Test Specification

### 4.1 Automated Unit Tests
1. **`StravaSegmentsSyncWorkerTest.kt`**:
   - `testSchedule_whenDisabled_cancelsWork`
   - `testSchedule_whenEnabled_enqueuesPeriodicWorkWithCorrectIntervalAndConstraints`
   - `testDoWork_whenDisconnectedOrDisabled_returnsSuccessWithoutSync`
   - `testDoWork_whenConnectedAndEnabled_callsSyncStarredSegmentsAndUpdatesTimestamp`
   - `testDoWork_whenSyncThrowsException_returnsRetry`
2. **`TrainingApplicationTest.kt`**:
   - Verify defaults and setter/getter parity for segments sync preferences.
3. **`TranslationParityTest.kt`**:
   - Verify 100% translation presence across all 9 locales with zero missing keys or format violations.
4. **Clean-Room Regression Suite**:
   - `./gradlew testDebugUnitTest` across all modules.

---

## 5. Invariants & Safety Rules
* Manual segment synchronization via pull-to-refresh (`SegmentListViewModel.kt`) remains completely unaffected.
* Segment database tables (`StarredSegmentsTable`), live segment tracking geometry (`REQ-LIV-001` - `REQ-LIV-004`), and PR update logic (`REQ-EXP-010`) remain untouched.
* Battery and network constraints ensure no unwanted background data usage on metered or low-battery states.
