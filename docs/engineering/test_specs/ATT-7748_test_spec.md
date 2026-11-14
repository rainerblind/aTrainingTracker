# Stage 2: Requirement & Test Specification - ATT-7748: Add Auto-Import tab and automatic Dropbox and Google Drive workout sync for secondary devices

**Ticket**: [ATT-7748](https://rainerblind.atlassian.net/browse/ATT-7748)  
**Sub-task**: [ATT-7810](https://rainerblind.atlassian.net/browse/ATT-7810) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Import, Export & Backup*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-MIG-052` (*Multi-Device Incremental Cloud Auto-Import & Local Health Connect / Strava Relay*)  
**Test Spec ID**: `TST-MIG-050`  
**Branch**: `feat/ATT-7748`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Requirement Specification (REQ-MIG-052)

### 1.1 Problem Statement & Rationale
Athletes using secondary devices (bike computers / handlebar phones) need seamless synchronization to their main phone or tablet. Ingesting newly uploaded cloud workouts must be automatic, non-interactive, and support immediate downstream relays to Health Connect and Strava on the primary device.

### 1.2 Attributes
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
1. **Auto-Import Tab Position**: In `ImportBackupTabsScreen.kt`, insert `Auto-Import` at index 1 (`1. Import | 2. Auto-Import | 3. Backup | 4. Restore`).
2. **Cloud Source & Format Toggles**: Independent toggles for Dropbox, Google Drive, format selection (`.fit` default, `.tcx`, `.gpx`, `all`), Health Connect relay, Strava relay, and a "Sync Now" trigger.
3. **Pre-Download Deduplication**: `AutoImportManager` queries cloud folders, compares against `ExistingWorkoutsSnapshot` before download, and only fetches new files.
4. **Non-Interactive Execution**: Workouts match existing cluster routes automatically without interactive dialog interruptions.
5. **Relay Workflows**: Newly imported workouts optionally trigger `HealthConnectWorkoutSyncWorker.enqueue(context, workoutId)` and Strava upload on the receiving device.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Tab Order & UI)**:
  * *Given* the user navigates to `ImportBackupTabsScreen`,
  * *When* the tab bar renders,
  * *Then* 4 tabs appear: `Import` (0), `Auto-Import` (1), `Backup` (2), `Restore` (3).
* **Criterion 2 (Delta Cloud Sync)**:
  * *Given* an existing workout already in the database and a new `.fit` file in Dropbox,
  * *When* Auto-Import executes,
  * *Then* the existing workout is skipped before download, the new workout is imported non-interactively, and repositories refresh.
* **Criterion 3 (Health Connect Relay)**:
  * *Given* Auto-Import is enabled and `syncImportedWorkoutsToHealthConnect` is true,
  * *When* a workout is imported,
  * *Then* `HealthConnectWorkoutSyncWorker` enqueues and records `(COMMUNITY, HEALTH_CONNECT)` export status.

### 1.5 System Invariants
- Zero duplicate workouts inserted into `WorkoutSummariesDatabaseManager`.
- Zero UI modal blocking on cluster naming during auto-import.
- Thread-safe execution on background dispatchers (`Dispatchers.IO`).

---

## 2. Test Specification (TST-MIG-050)

### Test Case 1: Auto-Import Tab Layout & Toggles Contract (`TST-MIG-050.1`)
* **Scope**: Unit / Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/AutoImportContractTest.kt`
* **Preconditions**: `ImportBackupTabsScreen` tab definitions loaded.
* **Action**: Verify tab count = 4, titles map to `[tab_import, tab_auto_import, tab_backup, tab_restore]`.
* **Expected Result**: Assert exact 4-tab index ordering and control presence.

### Test Case 2: Pre-Download Deduplication & Incremental Sync (`TST-MIG-050.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/AutoImportManagerTest.kt`
* **Preconditions**: Cloud folder has 1 existing and 1 new workout.
* **Action**: Invoke `AutoImportManager.syncIncremental(context)`.
* **Expected Result**: Existing file is skipped without downloading bytes; new file is parsed non-interactively; repositories refreshed.

### Test Case 3: Health Connect Relay Worker (`TST-MIG-050.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/cloud/healthconnect/HealthConnectWorkoutSyncWorkerTest.kt`
* **Preconditions**: Valid `workoutId` in mock database with Health Connect sync enabled.
* **Action**: Run `HealthConnectWorkoutSyncWorker.doWork()`.
* **Expected Result**: Worker returns `Result.success()` and updates `(COMMUNITY, HEALTH_CONNECT)` status to `FINISHED_SUCCESS`.

### Test Case 4: 9-Language Localization Parity (`TST-MIG-050.4`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/AutoImportLocalizationTest.kt`
* **Action**: Audit `strings_auto_import.xml` across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
* **Expected Result**: 100% presence and argument-specifier parity.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-MIG-050.5`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.cloud.healthconnect.*"`
* **Goal**: 100% pass rate across migration and Health Connect suites.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-MIG-050.1` | Contract | `ImportBackupTabsScreen` tab order & controls | `REQ-MIG-052.1`, `052.2` | Specified |
| `TST-MIG-050.2` | Unit | `AutoImportManager.syncIncremental` | `REQ-MIG-052.3`, `052.5` | Specified |
| `TST-MIG-050.3` | Unit | `HealthConnectWorkoutSyncWorker.doWork` | `REQ-MIG-052.4` | Specified |
| `TST-MIG-050.4` | Localization | `AutoImportLocalizationTest` | `REQ-MIG-052.7`, `REQ-UI-106` | Specified |
| `TST-MIG-050.5` | Regression | Tier 1 Migration & Health Connect Suites | `REQ-PRO-001` | Specified |
