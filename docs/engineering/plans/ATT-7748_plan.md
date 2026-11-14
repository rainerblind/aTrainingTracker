# Stage 3: Implementation Plan - ATT-7748: Add Auto-Import tab and automatic Dropbox and Google Drive workout sync for secondary devices

**Ticket**: [ATT-7748](https://rainerblind.atlassian.net/browse/ATT-7748)  
**Sub-task**: [ATT-7811](https://rainerblind.atlassian.net/browse/ATT-7811) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Import, Export & Backup*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-MIG-052`  
**Test Mapping**: `TST-MIG-050`  
**Branch**: `feat/ATT-7748`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Description & Background
Athletes recording workouts on secondary devices (such as handlebar bike-phones) need their sessions automatically synchronized to their everyday primary device or tablet. This ticket introduces a dedicated **Auto-Import** tab in `ImportBackupTabsScreen.kt`, an incremental cloud delta sync engine (`AutoImportManager.kt`), and background relay capabilities via `HealthConnectWorkoutSyncWorker.kt` and Strava uploader integration.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-MIG-052` (*Multi-Device Incremental Cloud Auto-Import & Local Health Connect / Strava Relay*)
* **Test Mapping**: `TST-MIG-050` (*Multi-Device Incremental Cloud Auto-Import, Health Connect Relay Worker & Auto-Import Tab Interface Verification Suite*)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Unintended Regressions**: Existing manual bulk recovery, single-file imports, and database restore procedures remain completely unchanged.
2. **Zero Duplicate Workouts**: Strict pre-download matching against `ExistingWorkoutsSnapshot` ensures files are checked prior to byte transfers, preserving existing SQLite unique constraints.
3. **Non-Interactive Cluster Matching**: Auto-import bypasses interactive modal cluster selection dialogs, matching existing routes automatically.
4. **Subtask & Gate Governance**: Code lock remains active until Gate 3 audit passes (`check-gate`).

---

## 4. Proposed Architectural Changes

### Component 1: Localization & Preference Storage
- Create domain-partitioned `app/src/main/res/values*/strings_auto_import.xml` across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) under `REQ-PRO-041.3`.
- Add preference accessors and constants to `TrainingApplication.java`:
  - `SP_AUTO_IMPORT_ENABLED = "auto_import_enabled"`
  - `SP_AUTO_IMPORT_DROPBOX = "auto_import_dropbox"`
  - `SP_AUTO_IMPORT_GOOGLE_DRIVE = "auto_import_google_drive"`
  - `SP_AUTO_IMPORT_FORMAT = "auto_import_format"`
  - `SP_SYNC_IMPORTED_WORKOUTS_TO_HEALTH_CONNECT = "sync_imported_workouts_to_health_connect"`
  - `SP_AUTO_IMPORT_LAST_SYNC_TIME = "auto_import_last_sync_time"`
  - `SP_AUTO_IMPORT_LAST_SYNC_RESULT = "auto_import_last_sync_result"`

### Component 2: Health Connect Relay Worker
- Create `com.atrainingtracker.trainingtracker.cloud.healthconnect.HealthConnectWorkoutSyncWorker.kt` extending `CoroutineWorker`.
- Expose `companion object { fun enqueue(context: Context, workoutId: Long) }` scheduling an immediate one-time work request.
- Resolves workout from `WorkoutRepository` and executes `HealthConnectManager.syncWorkout(context, workoutData, force = true)`.

### Component 3: Incremental Auto-Import Engine (`AutoImportManager.kt`)
- Encapsulate non-interactive delta sync querying Dropbox and/or Google Drive folders.
- Use `ExistingWorkoutsSnapshot.create(context)` to skip existing workouts without downloading file bytes.
- Parse new files via `LegacyImportEngine` using silent progress listener (resolving existing clusters without UI blocking).
- Enqueue `HealthConnectWorkoutSyncWorker` and schedule Strava uploads if configured.
- Refresh repositories: `WorkoutRepository.loadAllWorkouts()`, `PeriodsRepository.syncPeriodsIfDiscrepancy()`, `WorkoutClusterRepository.refreshClusters()`.

### Component 4: Auto-Import Tab UI (`ImportBackupTabsScreen.kt` & `BackupRestoreViewModel.kt`)
- Expand pager in `ImportBackupTabsScreen.kt` to 4 tabs (`pageCount = { 4 }`):
  `0: Import | 1: Auto-Import | 2: Backup | 3: Restore`.
- Render `AutoImportTabContent` composable:
  - Master enable switch.
  - Independent cloud source cards (Dropbox and Google Drive) with connection badges and connect deep-links.
  - Preferred format selector using `SingleChoiceSegmentedButtonRow` (`.fit`, `.tcx`, `.gpx`, `all`).
  - Health Connect and Strava relay switches.
  - Last sync status text and filled "Jetzt synchronisieren" (Sync Now) `Button`.

### Component 5: App & Workout List Trigger Integration
- Add lifecycle-aware / cooldown-gated check in `WorkoutSummariesTabbedScreen.kt` to evaluate `AutoImportManager.triggerSyncIfNeeded(context)` on screen appearance (cooldown: 15 minutes).

### UI Consistency (Rule 23)
* **Reference screen / component**: `ImportBackupTabsScreen.kt` / `BackupTabContent` / `Settings`
* **Reused components**: `ElevatedCard`, `Switch`, `SingleChoiceSegmentedButtonRow`, `Icon`, filled `Button` for primary sync action.
* **Theme tokens**: `MaterialTheme.colorScheme.primaryContainer`, `onPrimaryContainer`, `surfaceContainerHighest`.
* **New one-off styles**: None. Follows `docs/design_guidelines.md` §1 strictly.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: 9-Locale String Resources
* Files: `app/src/main/res/values*/strings_auto_import.xml` (all 9 locales)
* Test: `AutoImportLocalizationTest.kt` verifying 100% presence and parity.

### Step 2: Preference Accessors
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`
* Changes: Define constants and getter/setter helpers for Auto-Import settings.

### Step 3: Health Connect Relay Worker
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/cloud/healthconnect/HealthConnectWorkoutSyncWorker.kt`
* Test: `HealthConnectWorkoutSyncWorkerTest.kt` verifying work execution and export status recording.

### Step 4: Auto-Import Delta Engine
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/AutoImportManager.kt`
* Test: `AutoImportManagerTest.kt` verifying pre-download filtering and non-interactive sync.

### Step 5: Auto-Import Tab UI
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt`, `BackupRestoreViewModel.kt`
* Test: `AutoImportContractTest.kt` verifying 4-tab pager index and controls.

### Step 6: Workout Summaries Screen Trigger
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesTabbedScreen.kt`
* Changes: Add `LaunchedEffect` cooldown check to trigger auto-import on launch/resume.

### Step 7: Tier 1 Targeted Verification Suite
* Command:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.cloud.healthconnect.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Run targeted unit tests during construction, followed by full living docs verification (`python3 tools/verify_requirement_governance.py`).
* **Rollback**: Clean branch isolation on `feat/ATT-7748` guarantees zero impact on `develop` or `sprint/2026-46.6`.
