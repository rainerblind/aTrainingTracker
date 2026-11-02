# [Bug] [Strava] Fix automated background synchronization for Strava segments, routes and equipment

**Ticket**: [ATT-3478](https://rainerblind.atlassian.net/browse/ATT-3478)  
**Parent Epic**: [ATT-597](https://rainerblind.atlassian.net/browse/ATT-597) (*Strava Support*)  
**Status**: Backlog  

---

## Problem Statement & Value Proposition
During active athlete testing, automated background updates of Strava data (starred segments, routes, and equipment) failed to execute automatically. Forensic analysis identified a critical initialization race condition alongside missing periodic worker coverage:

1. **Initialization Order Race Condition in `TrainingApplication.onCreate()`**:
   * In `TrainingApplication.java`, background workers are scheduled on lines 1198–1200:
     ```java
     initWorkManager();
     if (sWorkManagerAvailable) {
         BackupWorker.schedule(this);
         StravaSegmentsSyncWorker.schedule(this);
         StravaRoutesSyncWorker.schedule(this);
     }
     ...
     cSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this); // Line 1213!
     ```
   * Inside `StravaSegmentsSyncWorker.schedule()` and `StravaRoutesSyncWorker.schedule()`, the worker checks:
     `val stravaConnected = TrainingApplication.getStravaAccessToken() != null`
   * However, `TrainingApplication.getStravaAccessToken()` guards with `if (cSharedPreferences == null) return null;`.
   * Because `cSharedPreferences` is null during this early phase of `onCreate()`, `getStravaAccessToken()` always returns null.
   * Both workers conclude that Strava is disconnected and immediately invoke:
     `WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)`
   * As a consequence, on **every application startup**, any existing scheduled Strava sync work is actively cancelled!
2. **Missing Automated Worker for Strava Equipment**:
   * Strava Equipment (`updateStravaEquipment`) only syncs via an ad-hoc thread (`StravaEquipmentSynchronizeThread`) triggered manually in settings or when viewing the equipment screen. There is no automated `StravaEquipmentSyncWorker` running periodically.
3. **Unused Synchronization Preferences**:
   * In `prefs_strava.xml`, settings for `automated_strava_segments_sync` and `strava_segments_sync_interval_days` (and routes equivalents) exist, but the worker schedulers hardcode a 1-day interval and ignore the user's toggle and interval preferences.

## User Story
* **As an**: athlete using Strava integration
* **I want**: my starred Strava segments, saved routes, and equipment to update automatically in the background
* **So that**: my routes, segment targets, and equipment lists are consistently fresh without requiring manual "Update" button clicks in settings.

## Scope & Technical Architecture
1. **Lifecycle & Initialization Fix (`TrainingApplication.java`)**:
   * Move `PreferenceManager.getDefaultSharedPreferences(this)` and default value setup BEFORE `initWorkManager()` and worker scheduling in `TrainingApplication.onCreate()`.
   * Guarantee that `cSharedPreferences` is fully populated when `schedule()` evaluates `getStravaAccessToken()`.
2. **Automated Equipment Sync Worker (`StravaEquipmentSyncWorker.kt`)**:
   * Implement a periodic `StravaEquipmentSyncWorker` using WorkManager (following `StravaSegmentsSyncWorker` patterns).
   * Register `StravaEquipmentSyncWorker.schedule(this)` in `TrainingApplication.onCreate()`.
3. **Preference Honoring**:
   * Update `StravaSegmentsSyncWorker.schedule()` and `StravaRoutesSyncWorker.schedule()` to read and respect `automated_strava_segments_sync` / `automated_strava_routes_sync` toggles and user-configured interval days (`strava_segments_sync_interval_days`).
4. **Token Refresh Guard in Workers**:
   * Verify that before invoking API calls, workers call `StravaHelper.getRefreshedAccessToken()` to handle 6-hour token expiry cleanly in the background.
5. **Automated Unit & Integration Testing**:
   * Unit tests verifying `schedule()` correctly detects authenticated Strava state and enqueues work.
   * Tests verifying cancellation only occurs when Strava is genuinely unlinked.

## Acceptance Criteria (Given-When-Then)
* **Criterion 1: Persistent Scheduling Across App Starts**:
  * *Given* an athlete authenticated with Strava
  * *When* the athlete launches the app and `TrainingApplication.onCreate()` runs
  * *Then* `StravaSegmentsSyncWorker` and `StravaRoutesSyncWorker` remain scheduled in WorkManager and are NOT cancelled.
* **Criterion 2: Automated Equipment Synchronization**:
  * *Given* an athlete with connected Strava account
  * *When* the periodic background interval elapses
  * *Then* Strava equipment is synchronized and `lastUpdateOfEquipment` timestamp is updated automatically.
* **Criterion 3: Preference Toggling**:
  * *Given* the athlete disables `automated_strava_segments_sync` in Strava settings
  * *When* the setting is saved
  * *Then* the segment sync periodic work is cancelled cleanly.
* **Criterion 4: Token Refresh in Background**:
  * *Given* an expired Strava access token with a valid refresh token
  * *When* the background sync worker runs
  * *Then* the token is refreshed automatically via `StravaHelper.getRefreshedAccessToken()` and sync proceeds without 401 Unauthorized errors.

## Parent Epic & Sprint Placement
* **Target Epic**: `ATT-597` (Strava Support)
* **Backlog Placement**: Product Backlog (do NOT assign to active sprint)
