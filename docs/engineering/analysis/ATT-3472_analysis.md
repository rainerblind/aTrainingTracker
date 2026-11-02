# [Bug] [Migration] Fix automated background backup execution and WorkManager scheduling

**Ticket**: [ATT-3472](https://rainerblind.atlassian.net/browse/ATT-3472)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Status**: Backlog  

---

## Problem Statement & Value Proposition
During device restoration testing in October 2026, the restore operation unexpectedly recovered a stale backup from mid-July 2026. Forensic investigation revealed that automated background backups have failed to execute since July 21–23, 2026 due to multiple architectural defects:

1. **Timer Reset Storm via `ExistingPeriodicWorkPolicy.REPLACE`**:
   * In `BackupWorker.kt` (`schedule()`), the periodic work request is registered using `ExistingPeriodicWorkPolicy.REPLACE`.
   * `BackupWorker.schedule()` is invoked in `TrainingApplication.onCreate()` on **every application cold start**.
   * WorkManager's `REPLACE` policy cancels any existing periodic schedule and enqueues a fresh work request with an initial delay equal to the period (24 hours).
   * Because active athletes launch the app at least once daily, the 24-hour periodic timer was perpetually cancelled and rescheduled into the future, never reaching execution.
2. **Preference Fallback Value Mismatch**:
   * In `schedule()`: `prefs.getBoolean("automated_backups", true)` defaults to `true`.
   * In `doWork()`: `prefs.getBoolean("automated_backups", false)` defaults to `false`.
   * If the athlete has not explicitly toggled the switch in Dropbox settings, `schedule()` registers the job, but `doWork()` immediately aborts with `Result.success()`.
3. **Absence of Rolling Local Backups**:
   * `BackupWorker.doWork()` skips execution entirely if neither Dropbox nor Google Drive is connected (`!dropboxConnected && !googleDriveConnected`).
   * When cloud tokens expire or offline workouts occur, no local rolling backup (`.attbackup`) is retained on the device for local disaster recovery.

## User Story
* **As an**: athlete recording regular endurance workouts
* **I want**: automated backups to execute reliably in the background according to my configured interval without being cancelled by app launches
* **So that**: my latest training data is continuously protected against device loss, data corruption, or migration failures.

## Scope & Technical Architecture
1. **WorkManager Policy Migration (`BackupWorker.kt`)**:
   * Replace `ExistingPeriodicWorkPolicy.REPLACE` with `ExistingPeriodicWorkPolicy.UPDATE` (or `ExistingPeriodicWorkPolicy.KEEP` if interval is unchanged).
   * This preserves the accumulated timer and ensures the periodic job fires after the elapsed interval regardless of how frequently the user opens the application.
2. **Preference Parity & Initialization (`BackupWorker.kt` & `TrainingApplication.java`)**:
   * Align the fallback value for `"automated_backups"` in `doWork()` to `true` (matching `schedule()` and `prefs_dropbox.xml`).
   * Ensure `PreferenceManager.getDefaultSharedPreferences(this)` is fully initialized before background worker scheduling in `TrainingApplication.onCreate()`.
3. **Local Rolling Backup Fallback (`BackupWorker.kt`)**:
   * Always create and retain the local `.attbackup` bundle on the device (e.g. in app-specific external files storage or cache with rolling retention), even if cloud upload is temporarily disabled, unauthenticated, or fails due to network constraints.
   * Attempt cloud upload (Dropbox / Google Drive) as a secondary step after local bundle creation succeeds.
4. **Automated Unit & Contract Testing**:
   * Add unit tests in `BackupWorkerTest.kt` verifying `ExistingPeriodicWorkPolicy.UPDATE` usage, fallback default consistency, and local backup creation.

## Acceptance Criteria (Given-When-Then)
* **Criterion 1: Monotonic Timer Preservation on App Launch**:
  * *Given* an active periodic `BackupWorker` scheduled 20 hours ago (4 hours remaining in 24h interval)
  * *When* the athlete launches the app and `TrainingApplication.onCreate()` executes
  * *Then* the existing periodic work request is NOT cancelled or reset to 24 hours, and executes when the remaining 4 hours elapse.
* **Criterion 2: Default Value Alignment**:
  * *Given* a fresh app installation where `"automated_backups"` has not been explicitly toggled
  * *When* `BackupWorker.doWork()` executes
  * *Then* it evaluates automated backups as enabled (`true`) and does not skip execution.
* **Criterion 3: Local Backup Creation Without Cloud Provider**:
  * *Given* an athlete with no connected Dropbox or Google Drive account
  * *When* `BackupWorker` runs
  * *Then* a valid, current `.attbackup` archive is created locally, and the last backup timestamp is recorded.
* **Criterion 4: Cloud Upload When Available**:
  * *Given* an athlete with connected Dropbox or Google Drive and Wi-Fi connection
  * *When* `BackupWorker` runs
  * *Then* the freshly generated backup bundle is uploaded to the cloud provider, updating the last backup status.

## Parent Epic & Sprint Placement
* **Target Epic**: `ATT-281` (Data Sovereignty & Migration)
* **Backlog Placement**: Product Backlog (do NOT assign to active sprint)
