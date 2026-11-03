# Stage 2: Requirement & Test Specification - ATT-5081: [Bug] Google Drive: automatically recover stuck credential-failed workout uploads on app startup

**Ticket**: [[ATT-5081]](https://rainerblind.atlassian.net/browse/ATT-5081)  
**Sub-task**: [[ATT-5129]](https://rainerblind.atlassian.net/browse/ATT-5129) (`[Req & Test Spec]`)  
**Parent Epic**: [[ATT-162]](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-DAT-037` (`REQ-DAT-037.4` *Stuck Export Recovery & Re-Queueing*)  
**Test Spec ID**: `TST-DAT-033` (`TST-DAT-033.4`)  
**Branch**: `feature/ATT-5081`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Requirement Specification (REQ-DAT-037.4)

### 1.1 Problem Statement & Rationale
In ATT-4922, `ExportManager.recoverStuckGoogleDriveExports(context)` was added to rescue workouts stuck in `FINISHED_FAILED: Google Drive credential is null or not linked`. However, recovery was wired only to new exports and interactive settings reconnects. Workouts that previously failed remain in terminal `FINISHED_FAILED` on app launch with no active WorkManager jobs scheduled, remaining dormant until the user manually opens Google Drive settings. The system must automatically trigger silent token refresh and stuck export recovery asynchronously during startup in `MainActivityWithNavigation.checkPreferences()`.

### 1.2 Functional & Architectural Requirements
1. **Startup Silent Token Refresh & Recovery (`REQ-DAT-037.4`)**:
   - In `MainActivityWithNavigation.checkPreferences()`, when `TrainingApplication.uploadToGoogleDrive()` is enabled:
     - If cached token is null/blank and `GoogleSignIn.getLastSignedInAccount(this)` is non-null, silently refresh the token via `GoogleDriveAuthManager.refreshTokenSync(this)` on `ioDispatcher`.
     - Whenever a valid token is present (or obtained via silent refresh), invoke `ExportManager.recoverStuckGoogleDriveExports(this)` on `ioDispatcher`.
2. **Main Thread Responsiveness & Error Isolation (`REQ-DAT-037.5`)**:
   - All network token operations and SQLite recovery calls on startup SHALL execute asynchronously on `ioDispatcher` inside `lifecycleScope.launch` with non-fatal try-catch guards.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-DAT-037.4` under `REQ-DAT-037` (Parent Epic `ATT-162`). Targets `MainActivityWithNavigation.kt` and `ExportManager.java`.
* **Historical Origin & Commit Trace**: `9a59e825` (ATT-4922, Sprint 2026-44.2) added `recoverStuckGoogleDriveExports(context)`.
* **Root Reason for Existing Formulation**: ATT-4922 hooked recovery into new workout uploads and settings dialogs, overlooking that terminal `FINISHED_FAILED` workouts have no active WorkManager jobs and stay dormant across app restarts.
* **Preservation of Core Invariants**: Asynchronous execution on `ioDispatcher`, non-blocking main thread on startup, credential isolation in `auth_credentials_prefs.xml`, and clean error suppression.

### 1.4 Acceptance Criteria (Given-When-Then) & System Invariants
* **Criterion 1 (Startup Recovery with Token)**: *Given* `uploadToGoogleDrive()` enabled, valid token present, and workouts in `FINISHED_FAILED` ("Google Drive credential is null or not linked"), *When* `MainActivityWithNavigation` resumes, *Then* `recoverStuckGoogleDriveExports()` runs on `ioDispatcher`, resetting workouts to `WAITING` and enqueueing WorkManager jobs.
* **Criterion 2 (Startup Recovery with Silent Refresh)**: *Given* `uploadToGoogleDrive()` enabled, cached token null/blank, but active `GoogleSignIn` account present, *When* `MainActivityWithNavigation` resumes, *Then* `refreshTokenSync()` acquires token and triggers stuck export recovery.
* **Invariants**: Zero main-thread blocking, WorkManager `MAX_RETRIES = 5` cap preserved, non-transient error isolation.

---

## 2. Test Specification (TST-DAT-033.4)

### Test Case 1: `testStartup_validToken_triggersStuckGoogleDriveExportRecovery` (`[TST-DAT-033.4a]`)
* **Scope**: Unit Test (`MainActivityGoogleDriveStartupRecoveryTest.kt`)
* **Action**: With `uploadToGoogleDrive = true` and valid cached token, call `checkPreferences()`; assert `recoverStuckGoogleDriveExports` is invoked on background dispatcher.

### Test Case 2: `testStartup_nullTokenWithAccount_refreshesTokenAndRecovers` (`[TST-DAT-033.4b]`)
* **Scope**: Unit Test (`MainActivityGoogleDriveStartupRecoveryTest.kt`)
* **Action**: With `uploadToGoogleDrive = true`, cached token null, and non-null GoogleSignIn account, call `checkPreferences()`; assert `refreshTokenSync` and `recoverStuckGoogleDriveExports` are called in sequence.

### Test Case 3: `testStartup_nullTokenAndNoAccount_disablesUploadWithoutRecovery` (`[TST-DAT-033.4c]`)
* **Scope**: Unit Test (`MainActivityGoogleDriveStartupRecoveryTest.kt`)
* **Action**: With `uploadToGoogleDrive = true`, null token, and null account, call `checkPreferences()`; assert `setUploadToGoogleDrive(false)` and zero recovery calls.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-DAT-033.4a]` | Unit | `MainActivityWithNavigation.checkPreferences` | `REQ-DAT-037.4` | Specified |
| `[TST-DAT-033.4b]` | Unit | `MainActivityWithNavigation.checkPreferences` | `REQ-DAT-037.4` | Specified |
| `[TST-DAT-033.4c]` | Unit | `MainActivityWithNavigation.checkPreferences` | `REQ-DAT-037.4` | Specified |
| `[TST-DAT-033.7]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
