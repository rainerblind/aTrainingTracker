# Implementation Plan - ATT-1078: Delete All Strava Data on Token Invalidation & Reauthorization Architecture

## 1. Context & Objectives
Under Section 7.4 ("Deletion Obligation") and Section 6.2 ("Cache and Retention") of the Strava API Policy and Section 4.4 of the Strava API Agreement, third-party applications are contractually obligated to promptly and permanently delete all Strava Data and Personal Data derived from Strava Data upon authorization revocation, account disconnection, or account deletion.

Currently in `aTrainingTracker`:
* Disconnection in `StravaSettingsDialog` only deletes `SP_STRAVA_TOKEN` from `SharedPreferences` via `TrainingApplication.deleteStravaToken()`.
* `StravaDeauthorizationThread` relies on deprecated Apache `DefaultHttpClient` and attempts to fetch an access token *after* `deleteStravaToken()` has already erased it.
* Database stores containing Strava Data (`StravaUpload.db`, `Segments.db`, `Routes.db`, and `Equipment.db`) are **not** cleared on deauthorization.
* If a token is revoked remotely on Strava's website, `StravaHelper.getRefreshedAccessToken()` encounters an error but takes no action to clean up local Strava data or notify the user.

The goal of **ATT-1078** is to:
1. Implement a unified `StravaDataPurgeManager` that coordinates complete, thread-safe data purging across all 6 storage layers (SharedPreferences, StravaUpload.db, Segments.db, Routes.db, Equipment.db, and WorkManager).
2. Modernize the remote deauthorization call (`/oauth/deauthorize`) using `OkHttpClient`.
3. Protect system invariants: preserve 100% of native workout sessions (summaries, samples, laps), non-Strava routes, local equipment entities, and physical ANT+/BLE sensor links.
4. Unlink equipment (`StravaId = NULL`, `StravaName = NULL`) rather than deleting equipment rows, enabling seamless re-linking by name in `StravaEquipmentSynchronizeThread` upon reauthorization without creating duplicate gear.
5. Implement passive server-side revocation detection in `StravaHelper.getRefreshedAccessToken()` when Strava rejects a refresh token with `invalid_grant` / HTTP 400.
6. Verify through comprehensive unit tests conforming to `TST-EXT-006` and full regression testing.

---

## 2. Requirements & Verification Traceability
| Requirement ID | Component | Verification Test ID | Description |
|---|---|---|---|
| **REQ-EXT-009** | `StravaDataPurgeManager.kt` | **TST-EXT-006** | Orchestrate comprehensive purge across credentials, databases, and background workers. |
| **REQ-EXT-009** | `StravaUploadDbHelper.java` | **TST-EXT-006** | Purge all cached activity JSON, segment efforts, and Strava activity IDs. |
| **REQ-EXT-009** | `SegmentsDatabaseManager.java` | **TST-EXT-006** | Delete all Strava starred segments and associated polyline streams. |
| **REQ-EXT-009** | `RoutesDatabaseManager.kt` | **TST-EXT-006** | Delete all Strava-origin routes and cascade-delete route points. |
| **REQ-EXT-009** | `EquipmentDbHelper.java` | **TST-EXT-006** | Unlink Strava IDs and names (`NULL`) while preserving local equipment and sensor links. |
| **REQ-EXT-009** | `StravaDeauthorizationThread.java` | **TST-EXT-006** | Modernize deauthorization using `OkHttpClient` and delegate to purge pipeline. |
| **REQ-EXT-009** | `StravaHelper.kt` | **TST-EXT-006** | Automatically trigger purge upon detecting unrecoverable token revocation (`invalid_grant`). |
| **REQ-EXT-009** | `StravaSettingsDialog.kt` | **TST-EXT-006** | Wire disconnect action to purge manager and update UI connection state. |

---

## 3. Component Architecture & Proposed Changes

### 3.1 Central Purge Coordinator (`StravaDataPurgeManager.kt`) [NEW]
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaDataPurgeManager.kt`
* **Responsibilities**:
  - `purgeAllStravaData(context: Context, alsoRevokeRemote: Boolean = false, onComplete: (() -> Unit)? = null)`
  - Run asynchronously on `Dispatchers.IO`:
    1. If `alsoRevokeRemote == true`, extract current valid access token and dispatch `POST https://www.strava.com/oauth/deauthorize` with `Authorization: Bearer <token>` via `OkHttpClient`. Handle network errors gracefully (local purge proceeds regardless).
    2. Clear all Strava preferences via `TrainingApplication.deleteStravaToken()`.
    3. Purge `StravaUploadDbHelper`: invoke `clearAllStravaData()`.
    4. Purge `SegmentsDatabaseManager`: invoke `deleteAllTables()`.
    5. Purge `RoutesDatabaseManager`: invoke `deleteRoutesBySource(RouteSource.STRAVA)`.
    6. Unlink `EquipmentDbHelper`: invoke `unlinkAllStravaEquipment()`.
    7. Cancel background workers: `WorkManager.getInstance(context).cancelUniqueWork("automated_strava_segments_sync_work")` and `"automated_strava_routes_sync_work"`.
    8. Reset auth repository: `StravaAuthRepository.getInstance().resetState()`.
    9. Post completion callback on `Dispatchers.Main`.

### 3.2 Credentials & Preference Reset (`TrainingApplication.java`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`
* **Changes**:
  - Update `deleteStravaToken()` to comprehensively remove all Strava-related preference keys:
    - `SP_STRAVA_TOKEN`
    - `SP_STRAVA_REFRESH_TOKEN`
    - `SP_STRAVA_TOKEN_EXPIRES_AT`
    - `SP_STRAVA_ATHLETE_ID`
    - `SP_UPLOAD_TO_STRAVA`
    - `SP_STRAVA_DEMO_MODE`
    - `SP_LAST_UPDATE_TIME_OF_STRAVA_EQUIPMENT`
    - `SP_LAST_UPDATE_TIME_OF_STRAVA_ROUTES`
    - `SP_LAST_UPDATE_TIME_OF_STRAVA_SEGMENTS`

### 3.3 Activity Data Purge (`StravaUploadDbHelper.java`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/db/StravaUploadDbHelper.java`
* **Changes**:
  - Add public method:
    ```java
    public void clearAllStravaData() {
        if (DEBUG) Log.d(TAG, "clearAllStravaData");
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE, null, null);
    }
    ```
  - This purges all rows from `StravaUploads`, ensuring no Strava activity JSON, upload IDs, or Strava activity IDs remain in the application.

### 3.4 Starred Segments Purge (`SegmentsDatabaseManager.java`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/segments/SegmentsDatabaseManager.java`
* **Changes**:
  - Verify and utilize `deleteAllTables()` to wipe both `TABLE_STARRED_SEGMENTS` and `TABLE_SEGMENT_STREAMS`.
  - In `SegmentsRepository.kt`, provide a method to clear in-memory state if active.

### 3.5 Strava Routes Purge (`RoutesDatabaseManager.kt`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/RoutesDatabaseManager.kt`
* **Changes**:
  - Add public method:
    ```kotlin
    fun deleteRoutesBySource(source: RouteSource): Int {
        val db = writableDatabase
        return db.delete(
            RouteContract.TABLE_ROUTES,
            "${RouteContract.COLUMN_SOURCE} = ?",
            arrayOf(source.name)
        )
    }
    ```
  - Because `TABLE_ROUTE_POINTS` defines `FOREIGN KEY (route_id) REFERENCES Routes(_id) ON DELETE CASCADE`, all route coordinates and elevation points are automatically cascade-deleted.

### 3.6 Equipment Unlinking (`EquipmentDbHelper.java`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/EquipmentDbHelper.java`
* **Changes**:
  - Add public method:
    ```java
    public void unlinkAllStravaEquipment() {
        if (DEBUG) Log.d(TAG, "unlinkAllStravaEquipment");
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.putNull(STRAVA_ID);
        values.putNull(STRAVA_NAME);
        db.update(EQUIPMENT, values, null, null);
    }
    ```
  - Preserves `_id`, `Name`, `SportType`, `FrameType`, and `Links` sensor bindings, satisfying the invariant that hardware configurations MUST NOT be destroyed.

### 3.7 Automated Revocation Detection (`StravaHelper.kt`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaHelper.kt`
* **Changes**:
  - In `getRefreshedAccessToken()`:
    When token refresh fails with HTTP 400 (Bad Request) or HTTP 401 (Unauthorized) and the response indicates an invalid or revoked refresh token (`invalid_grant`), automatically trigger:
    ```kotlin
    StravaDataPurgeManager.purgeAllStravaData(TrainingApplication.getContext(), alsoRevokeRemote = false)
    ```

### 3.8 Modernized Remote Deauthorization, Dialog Integration & Confirmation Flow
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaDeauthorizationThread.java`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/strava/StravaSettingsDialog.kt`
  - `app/src/main/res/values*/strings.xml` (9 languages)
* **Changes**:
  - In `StravaSettingsDialog.kt`, introduce `showDisconnectConfirmation` state variable.
  - When the user taps "Disconnect" (`onDisconnectClick`), do not immediately purge data.
  - Render an interactive `AlertDialog`:
    - Title: `@string/strava_disconnect_dialog_title` ("Disconnect from Strava?" / "Strava-Verbindung trennen?")
    - Message: `@string/strava_disconnect_dialog_message`
      - Details deletion of Strava routes, starred segments and times, unlinking of equipment, and removal of Strava upload status for past workouts.
      - Reassures the athlete that native workout recordings (summaries, samples, laps) remain safe and intact.
    - Confirm button: `@string/strava_disconnect_dialog_confirm` ("Disconnect" / "Trennen")
    - Dismiss button: `@string/Cancel` ("Cancel" / "Abbrechen")
  - Upon user confirmation, execute:
    ```kotlin
    StravaDataPurgeManager.purgeAllStravaData(context, alsoRevokeRemote = true) {
        isConnected = false
    }
    ```
  - Update `StravaDeauthorizationThread.java` to delegate to `StravaDataPurgeManager`.
  - Add localized string resources across all 9 languages (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

### 3.9 Concurrency Mutex, Route Deduplication & Cache Refresh
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/RoutesRepository.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/database/RoutesDatabaseManager.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/segments/SegmentsRepository.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaDataPurgeManager.kt`
* **Changes**:
  - In `RoutesRepository.kt`: Guard `syncRoutesFromStrava()` with a `Mutex` (`syncMutex.withLock`) to prevent concurrent executions (such as simultaneous `syncRoutesFromStravaAsync()` and `StravaRoutesSyncWorker` triggers).
  - In `RoutesDatabaseManager.kt`: Inside `insertRoute()`, enforce idempotency by querying for existing routes with identical `externalId` and `source`. When found, update the existing summary, replace route points, and prune any legacy duplicate rows.
  - In `StravaDataPurgeManager.kt`: Upon data purge, invoke `RoutesRepository.refreshRoutes()` and `SegmentsRepository.clearSegmentsCache()` to immediately clear/reload in-memory caches.

---

## 4. Invariant Protection & Impact Analysis
* [x] **Native Workout Recordings**: Workouts in `WorkoutSummaries.db`, GPS trackpoints in `WorkoutSamples.db`, and lap splits in `Laps.db` are athlete recordings. They are never touched during Strava data purge.
* [x] **Hardware Sensor Pairings**: Equipment items in `Equipment.db` remain in the database with their existing IDs; ANT+/BLE pairings in `Links` table remain intact. Only `StravaId` and `StravaName` are cleared.
* [x] **Local Routes**: Only routes where `source == RouteSource.STRAVA` are removed. Locally created GPX routes (`LOCAL_GPX`) and workout-derived routes (`WORKOUT`) remain completely untouched.
* [x] **Sport Type Links**: `SportTypeEquipmentLinkManager` links to equipment by internal `equipmentId`. Since equipment rows are unlinked rather than deleted, sport type links remain valid.
* [x] **Reauthorization Parity**: When reauthorizing, `StravaEquipmentSynchronizeThread` matches existing equipment by name where `StravaId IS NULL`, re-attaching Strava IDs without creating duplicate gear rows.
* [x] **Route Deduplication & Cache Invalidation**: Prevent duplicate Strava routes on reconnect via Mutex concurrency lock, database upsert on `externalId`, and in-memory cache refresh.
* [x] **Thread Safety & UI Fluidity**: All database deletions and remote HTTP requests run strictly on background threads (`Dispatchers.IO`), preventing ANRs or frame drops.

---

## 5. Verification Plan
* **Automated Unit Tests**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaDataPurgeManagerTest.kt`:
    - Test 1: Full purge removes credentials, clears `StravaUploadDbHelper`, deletes starred segments, deletes Strava routes, unlinks equipment, and refreshes/clears repository caches.
    - Test 2: Invariant check - native workouts, samples, laps, and hardware sensor links in `EquipmentDbHelper.LINKS` remain intact.
    - Test 3: Remote deauthorization HTTP call dispatched before token wipe.
    - Test 4: Reauthorization re-links existing unlinked equipment by name without duplicates.
    - Test 5: Automated revocation detection triggers purge when token refresh fails with `invalid_grant`.
  - `app/src/test/java/com/atrainingtracker/trainingtracker/database/RoutesDatabaseManagerDeduplicationTest.kt`:
    - Test 1: Inserts new route and points when not present.
    - Test 2: Updates summary and replaces points on duplicate externalId without creating duplicate rows.
    - Test 3: Purges multiple legacy duplicate rows if present in database.
* **Clean-Room Regression**:
  - Execute `./gradlew testDebugUnitTest` to guarantee 0 regressions across all existing suites.

