# Stage 1 Analysis: ATT-5081 - [Bug] Google Drive: automatically recover stuck credential-failed workout uploads on app startup without requiring manual reconnect

**Ticket**: [ATT-5081](https://rainerblind.atlassian.net/browse/ATT-5081)  
**Sub-task**: [[ATT-5123]](https://rainerblind.atlassian.net/browse/ATT-5123) (`[Analysis]`)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Branch**: `feature/ATT-5081`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Problem Statement & Motivation
- In ATT-4922, `ExportManager.recoverStuckGoogleDriveExports(context)` was added to rescue workouts stuck in `FINISHED_FAILED: Google Drive credential is null or not linked`.
- However, recovery was wired only to `GoogleDriveUploader.doExport()` (when a *new* workout uploads) and `GoogleDriveSettingsDialog.kt` (interactive settings changes).
- When the app is launched, workouts already in terminal `FINISHED_FAILED` have no active WorkManager jobs scheduled. As a result, stuck workouts never auto-recover unless the user manually opens Google Drive settings or completes a new workout.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
- **Root Cause**:
  - `MainActivityWithNavigation.checkPreferences()` inspects Google Drive credentials on startup (`onResume()`), but only disables `uploadToGoogleDrive` if both token and GoogleSignIn account are null.
  - When `uploadToGoogleDrive` is true and an account exists (or a token is present), `checkPreferences()` neither triggers a background silent token refresh via `GoogleDriveAuthManager.refreshTokenSync(context)` nor calls `ExportManager.recoverStuckGoogleDriveExports(context)`.
- **Independent Peer Review & Verification**:
  - Audited `MainActivityWithNavigation.kt:610-621`: confirmed it checks `token.isNullOrBlank() && account == null` but triggers zero recovery or background refresh.
  - Audited `ExportManager.java:257-282`: `recoverStuckGoogleDriveExports` queries `recoverStuckCredentialExports`, creates WorkRequests, enqueues them via `WorkManager`, and broadcasts status updates cleanly.
  - Calling `recoverStuckGoogleDriveExports` on startup within `lifecycleScope.launch(ioDispatcher)` completely resolves the bug without blocking the main thread.

---

## 3. User Scope Grounding (ATT-1250)
- **In-Scope Goals**:
  1. In `MainActivityWithNavigation.checkPreferences()`, when `uploadToGoogleDrive()` is true and account is present: trigger background silent refresh via `GoogleDriveAuthManager.refreshTokenSync(this)` if token is null/blank.
  2. Whenever a valid token is present or silently acquired on startup, invoke `ExportManager.recoverStuckGoogleDriveExports(this)` on `ioDispatcher`.
  3. Ensure all background startup tasks are dispatched asynchronously on `Dispatchers.IO` with non-fatal try-catch error handling.
  4. Unit test coverage verifying startup token refresh and stuck export recovery invocation in `MainActivityIntentLifecycleTest` / `MainActivityGoogleDriveStartupRecoveryTest`.
- **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Modifying Dropbox or Strava startup export recovery pipelines.
  - Altering `WorkManager` exponential backoff or retry caps (`MAX_RETRIES = 5`).
  - Refactoring UI settings dialogs or Google Sign-In intent workflows.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
- **Original Requirement ID & Target**: Amends `REQ-DAT-037.4` (*Stuck Export Recovery & Re-Queueing*), under `REQ-DAT-037` (*Resilient Google Drive Background Upload Retry*), Parent Epic `ATT-162`. Targets `MainActivityWithNavigation.kt`.
- **Historical Origin & Commit Trace**: `9a59e825` (ATT-4922, Sprint 2026-44.2) introduced `recoverStuckGoogleDriveExports`.
- **Root Reason for Existing Formulation**: ATT-4922 assumed recovery during new exports or interactive dialog reconnect was sufficient, overlooking that previously failed workouts in terminal `FINISHED_FAILED` have no active jobs and stay dormant on fresh app launch.
- **Preservation of Core Invariants**: Asynchronous execution on `ioDispatcher`, non-blocking main thread on startup, credential isolation in `auth_credentials_prefs.xml`, and clean error suppression.

---

## 5. Architectural Strategy & High-Level Solution
- **Startup Wiring (`MainActivityWithNavigation.kt`)**:
  - In `checkPreferences()`, when `uploadToGoogleDrive()` is true:
    - If `token.isNullOrBlank() && account == null`, reset `uploadToGoogleDrive(false)`.
    - Else (valid token or account available), launch a coroutine on `lifecycleScope.launch(ioDispatcher)`:
      - If `token.isNullOrBlank() && account != null`, call `GoogleDriveAuthManager.refreshTokenSync(this)`.
      - If token is valid (or refresh succeeded), call `ExportManager.recoverStuckGoogleDriveExports(this)`.

---

## 6. System Invariants & Risk Assessment
- **Core Invariants**:
  1. Main thread responsiveness: zero synchronous network/DB blocking during `onResume()`.
  2. WorkManager task deduplication: `recoverStuckCredentialExports` only targets `FINISHED_FAILED` matching the specific credential error message.
  3. 100% clean-room test pass rate and backward compatibility.
- **Risk Rating**: **LOW**. Scoped to an asynchronous recovery trigger on app startup; reuses thoroughly tested `recoverStuckGoogleDriveExports` and `refreshTokenSync` routines.
