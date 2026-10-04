# Stage 1 Analysis: ATT-1306 - Google Drive Integration for Automated Workout Export and Backup Synchronization (Rework Cycle 2: Real Google Sign-In & OAuth2 Authorization)

**Ticket**: [ATT-1306](https://rainerblind.atlassian.net/browse/ATT-1306)  
**Sub-task**: [ATT-2406](https://rainerblind.atlassian.net/browse/ATT-2406) (`[Analysis]`)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V4.9.40` (assigned upon completion per Rule 19)  
**Active Sprint**: `2026-40.16`  
**Requirement Mapping**: `REQ-DAT-020` (*Automated Activity Export and Database Backup via Google Drive Cloud Service*)  
**Test Spec ID**: `TST-DAT-015`  
**Branch**: `feature/ATT-1306`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Statement & Motivation

During Sprint 2026-40.15 Joint Review testing on physical hardware (Google Pixel 10), the initial Google Drive integration implementation was rejected due to a critical defect in the authentication and connection flow:

1. **Mock Manual Input Dialog instead of Real Native Google Sign-In**:
   - Tapping "Verbinden" (`R.string.google_drive_connect`) opened an `AlertDialog` with manual text input fields for "Google Account Email" and "Auth Token", rather than launching the standard Android Google Sign-In account selector.
2. **Zero Input Validation & Static Dummy Fallback**:
   - Entering arbitrary placeholder text (e.g. `"foobar"`) with an empty token was silently accepted without validation, immediately claiming *"Du bist mit Google Drive verbunden: foobar"*.
   - In `GoogleDriveSettingsDialog.kt`, an empty token automatically fell back to a hardcoded string (`"gdrive_oauth_token"`), resulting in non-functional 401 Unauthorized errors during actual upload/download requests.

### Objective for Rework Cycle 2
Eliminate the manual text input mock dialog and implement the official Google Play Services Sign-In and OAuth2 authorization flow. Athletes must be able to select their device's Google account via the native system picker, authorize the least-privilege `drive.file` scope, and receive a verified OAuth2 Bearer token for seamless automated workout uploads and database backups.

---

## 2. Forensic Investigation & Root Cause Analysis

### 2.1 Why Did Cycle 1 Introduce a Mock Input Dialog?
In Cycle 1, engineering focused extensively on the export pipeline, background `WorkManager` constraints, and REST client abstractions (`GoogleDriveClient.kt`, `GoogleDriveUploader.kt`, `GoogleDriveBackupManager.kt`). To simulate authenticated state without configuring Google Play Services Auth dependencies, a temporary developer shortcut was implemented in `GoogleDriveSettingsDialog.kt`. However, this mock dialog remained in place and broke end-to-end user testing on physical hardware.

### 2.2 Google Play Services Auth Architecture & Token Retrieval
To authenticate and authorize Google Drive REST API calls on Android:
1. **Dependency Requirement**:
   - `com.google.android.gms:play-services-auth:21.3.0` provides the modern, rock-solid Google Sign-In client and `GoogleAuthUtil` token retrieval APIs.
2. **Scopes**:
   - `Scope("https://www.googleapis.com/auth/drive.file")`: Least-privilege scope that grants read/write access solely to files and folders created by aTrainingTracker (`aTrainingTracker/Workouts/` and `aTrainingTracker/Backups/`).
3. **Interactive Sign-In Flow**:
   - Configured via `GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)` requesting email and `Scope("https://www.googleapis.com/auth/drive.file")`.
   - Launched in Jetpack Compose via `rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult())`.
   - Upon successful account selection, `GoogleSignIn.getSignedInAccountFromIntent(result.data)` returns the authenticated `GoogleSignInAccount`.
4. **OAuth2 Bearer Token Resolution**:
   - Using the selected `Account` object, the OAuth2 access token must be fetched via `GoogleAuthUtil.getToken(context, account, "oauth2:https://www.googleapis.com/auth/drive.file")`.
   - Because `GoogleAuthUtil.getToken` performs network I/O, it must execute on a background coroutine dispatcher (`Dispatchers.IO`).
5. **Disconnect & Token Invalidation**:
   - On Disconnect: Invokes `GoogleSignIn.getClient(context, gso).signOut()` and `revokeAccess()`, clears credentials in `TrainingApplication.deleteGoogleDriveCredential()`, and invalidates cached tokens via `GoogleAuthUtil.clearToken(context, token)`.

---

## 3. User Scope Grounding (ATT-1250)

### In-Scope Goals
1. **Dependency Integration**:
   - Add `implementation 'com.google.android.gms:play-services-auth:21.3.0'` to `app/build.gradle`.
2. **Native Authentication Flow (`GoogleDriveAuthManager.kt` / `GoogleDriveSettingsDialog.kt`)**:
   - Completely remove the manual text input `AlertDialog` (`inputEmail`, `inputToken`).
   - Launch native Google Sign-In intent requesting `drive.file` scope.
   - Asynchronously acquire verified OAuth2 Bearer token via `GoogleAuthUtil.getToken` on `Dispatchers.IO`.
   - Provide visual progress indicator (e.g. `CircularProgressIndicator`) while acquiring credentials.
   - Display informative error feedback (e.g. Snackbar / Toast) if user cancels or authentication fails.
3. **Credential Storage & Lifecycle**:
   - Persist genuine Google account email and OAuth token in `TrainingApplication.storeGoogleDriveCredential(email, token)`.
   - Support proper Disconnect: invoke `signOut()` and `revokeAccess()`, wipe credentials from preferences, and clear tokens.
4. **Token Refresh & Expiration Resilience**:
   - In `GoogleDriveClient.kt`, when encountering a `401 Unauthorized` response, attempt token invalidation via `GoogleAuthUtil.clearToken` and re-acquire a fresh token before failing the upload.
5. **9-Language Localization Parity**:
   - Maintain 100% parity across all 9 supported locales for auth failure messages and progress states.

### Out-of-Scope Non-Goals (Scope Bounding)
- Broad Drive scopes (e.g. full `drive` or `drive.readonly` access) — strictly prohibited to preserve athlete privacy.
- Changing existing folder structures (`aTrainingTracker/Workouts/` and `aTrainingTracker/Backups/`).
- Google Fit or Health Connect data synchronizations.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)

```markdown
### Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: `REQ-DAT-020` (*Automated Activity Export and Database Backup via Google Drive Cloud Service*), targeting `GoogleDriveSettingsDialog.kt`, `GoogleDriveClient.kt`, `TrainingApplication.java`, and `app/build.gradle`.
2. **Historical Origin & Commit Trace**: Introduced in `ATT-1306` (Sprint 2026-40.14) to provide native Google cloud backup and export alongside Dropbox.
3. **Root Reason for Existing Formulation**: The original requirement specified Google Drive cloud integration with `drive.file` scope, but lacked explicit verification constraints against developer mock fallbacks in UI dialogs.
4. **Preservation of Core Invariants**: 
   - Least-privilege privacy principle: Only `drive.file` scope is requested; no access to athlete's personal files.
   - Offline resilience and Wi-Fi only constraints: Background WorkManager queues uploads until valid network is available.
   - Coexistence invariant: Athletes can use Dropbox and Google Drive simultaneously without mutual interference.
```

---

## 5. Architectural Strategy & Component Flow

### 5.1 Component Interaction Diagram

```
+-------------------------------------------------------------+
|                GoogleDriveSettingsDialog                    |
|  - Renders GoogleDriveConnectionHeader                     |
|  - Triggers GoogleSignIn client via ActivityResultLauncher |
|  - Displays connection progress and error states            |
+------------------------------+------------------------------+
                               |
                               | (1) StartActivityForResult(signInIntent)
                               v
+-------------------------------------------------------------+
|             Google Play Services (Sign-In UI)               |
|  - Athlete selects Google Account                           |
|  - Athlete consents to drive.file scope                     |
|  - Returns GoogleSignInAccount                              |
+------------------------------+------------------------------+
                               |
                               | (2) GoogleSignInAccount.account
                               v
+-------------------------------------------------------------+
|                   GoogleDriveAuthManager                    |
|  - Executes on Dispatchers.IO                               |
|  - Calls GoogleAuthUtil.getToken(context, account, scope)   |
|  - Resolves verified OAuth2 Bearer token                    |
|  - Refreshes expired tokens on 401                          |
+------------------------------+------------------------------+
                               |
                               | (3) storeGoogleDriveCredential(email, token)
                               v
+-------------------------------------------------------------+
|                     TrainingApplication                     |
|  - Persists validated email and token in Encrypted/Prefs    |
|  - Sets uploadToGoogleDrive = true                          |
+------------------------------+------------------------------+
                               |
                               | (4) Bearer token provider
                               v
+-------------------------------------------------------------+
|                      GoogleDriveClient                      |
|  - HTTPS OkHttp requests with Authorization: Bearer <token> |
|  - Interacts with Google Drive API v3                       |
|  - Uploads / Downloads to aTrainingTracker/Workouts|Backups |
+-------------------------------------------------------------+
```

---

## 6. Verification Criteria for Gate 1 Sign-Off

| Check | Description | Status |
| :--- | :--- | :--- |
| **Forensic RCA** | Identified root cause of physical device rejection (mock input dialog, dummy token fallback) | **PASS** |
| **Architectural Solution** | Designed native `play-services-auth` integration with `GoogleAuthUtil` token retrieval | **PASS** |
| **Scope Bounding** | In-scope and out-of-scope boundaries strictly defined (`drive.file` scope only) | **PASS** |
| **Chesterton's Fence Audit** | All 4 mandatory fields documented for `REQ-DAT-020` | **PASS** |
