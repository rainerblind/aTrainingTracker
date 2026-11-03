# Stage 3: Implementation Plan - ATT-5081: [Bug] Google Drive: automatically recover stuck credential-failed workout uploads on app startup

**Ticket**: [[ATT-5081]](https://rainerblind.atlassian.net/browse/ATT-5081)  
**Sub-task**: [[ATT-5130]](https://rainerblind.atlassian.net/browse/ATT-5130) (`[Impl-Plan]`)  
**Parent Epic**: [[ATT-162]](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-DAT-037.4` (*Stuck Export Recovery & Re-Queueing*)  
**Test Mapping**: `TST-DAT-033.4` (`TST-DAT-033.4a`..`TST-DAT-033.4c`)  
**Branch**: `feature/ATT-5081`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Problem Description & Background
In ATT-4922, `ExportManager.recoverStuckGoogleDriveExports(context)` was implemented to rescue workouts in `FINISHED_FAILED` ("Google Drive credential is null or not linked"). However, recovery was wired only to new exports and interactive settings reconnects. On app launch, previously failed workouts remain in terminal `FINISHED_FAILED` with no WorkManager jobs scheduled. The app must automatically trigger silent token refresh and stuck export recovery asynchronously during startup in `MainActivityWithNavigation.checkPreferences()`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-DAT-037.4` (*Stuck Export Recovery & Re-Queueing on Application Startup*)
  * `REQ-DAT-037.4` (Valid Token Recovery) $\rightarrow$ `TST-DAT-033.4a` (`MainActivityGoogleDriveStartupRecoveryTest`)
  * `REQ-DAT-037.4` (Silent Token Refresh & Recovery) $\rightarrow$ `TST-DAT-033.4b` (`MainActivityGoogleDriveStartupRecoveryTest`)
  * `REQ-DAT-037.4` (Missing Account Deactivation) $\rightarrow$ `TST-DAT-033.4c` (`MainActivityGoogleDriveStartupRecoveryTest`)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Main-Thread Blocking**: All network token operations and SQLite recovery calls execute asynchronously on `lifecycleScope.launch(ioDispatcher)`.
2. **WorkManager Safety**: Only exports in `FINISHED_FAILED` matching `"Google Drive credential is null or not linked"` are recovered and re-queued. Exponential backoff and `MAX_RETRIES = 5` remain preserved.
3. **Graceful Error Isolation**: Network/auth exceptions during background startup refresh are caught non-fatally and logged.
4. **UI Consistency**: No UI changes.

---

## 4. Proposed Architectural Changes
* **Activity Layer (`MainActivityWithNavigation.kt`)**:
  * In `checkPreferences()`, when `TrainingApplication.uploadToGoogleDrive()` is true:
    * If `token.isNullOrBlank() && account == null`: disable `uploadToGoogleDrive(false)` (existing behavior).
    * Else: launch coroutine on `lifecycleScope.launch(ioDispatcher)`:
      * If `currentToken.isNullOrBlank() && account != null`: call `GoogleDriveAuthManager.refreshTokenSync(this)`.
      * If `!currentToken.isNullOrBlank()`: call `ExportManager.recoverStuckGoogleDriveExports(this)` in try-catch.
* **Testing Layer (`MainActivityGoogleDriveStartupRecoveryTest.kt`)**:
  * Create dedicated test class covering valid token recovery, silent token refresh recovery, and missing account deactivation.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1: Startup Recovery Wiring (`MainActivityWithNavigation.kt`)**:
   Update `checkPreferences()` to launch background coroutine on `ioDispatcher` when Google Drive is enabled and account/token is present, performing silent token refresh and `ExportManager.recoverStuckGoogleDriveExports(this)`.
2. **Step 2: Automated Unit Tests (`MainActivityGoogleDriveStartupRecoveryTest.kt`)**:
   Implement `TST-DAT-033.4a` (valid token), `TST-DAT-033.4b` (silent refresh), and `TST-DAT-033.4c` (no account).
3. **Step 3: Targeted Verification & Regression Audit**:
   Execute targeted unit tests:
   `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.activities.*" --tests "com.atrainingtracker.trainingtracker.exporter.*"`
   and verify requirement governance via `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests in Stage 4, followed by clean-room regression suite in Stage 5.
* **Rollback**: Isolated branch `feature/ATT-5081`; revert branch if regression detected before Stage 5 squash.
