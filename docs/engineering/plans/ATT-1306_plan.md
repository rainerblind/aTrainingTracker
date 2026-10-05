# Stage 3: Implementation Plan - ATT-1306: Google Drive Integration for Automated Workout Export and Backup Synchronization

**Ticket**: [ATT-1306](https://rainerblind.atlassian.net/browse/ATT-1306)  
**Sub-task**: [ATT-2475](https://rainerblind.atlassian.net/browse/ATT-2475) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.1`  
**Requirement Mapping**: `REQ-DAT-020`  
**Test Mapping**: `TST-DAT-015`  
**Branch**: `feature/ATT-1306`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-05  

---

## 1. Problem Description & Background

During the Sprint 2026-40.16 Joint Review on a physical Google Pixel 10 (debug build from `sprint/2026-40.16`), attempting to authenticate via "Mit Google Drive verbinden" resulted in:
`Google Sign-In failed (status 10)` = `DEVELOPER_ERROR`

This occurred because Google Play Services requires an Android OAuth client ID registered in the Google Cloud Console for package `com.atrainingtracker.debug` with the debug SHA-1 signing fingerprint (`DB:ED:99:07:90:E0:27:B9:99:46:DB:54:29:50:88:D0:E9:20:68:8E`), along with the Google Drive API enabled. Because this configuration is an administrative task outside client code, client-side error presentation must guide the developer/user gracefully.

Review findings identified two critical user-facing issues:
1. **Raw English Unmapped Error**: The dialog displayed the unlocalized string `"Google Sign-In failed (status 10)"` inside a German UI session without actionable explanation.
2. **Premature Active Sync Toggles**: Toggles for automated workout export, automated database backup, and Wi-Fi only restriction were displayed as active and enabled even when Google Drive was not connected.

Rework Cycle 3 resolves these findings by:
* Introducing localized error mapping for Google Sign-In status codes (specifically explaining status 10 / developer setup needed, network failures, and general errors).
* Establishing a clean disconnected UI state in `GoogleDriveSettingsDialog.kt` where sync toggles are conditionally hidden when `isConnected == false`, displaying an informative onboarding connect prompt.
* Guaranteeing 100% 9-language localization parity across all supported application locales.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-DAT-020` (*Google Drive Integration for Automated Workout Export and Database Backup*)
  - Clause 6: Settings & Navigation UI (conditional disconnected state, localized error presentation, status 10 developer diagnostic).
  - Clause 7: 9-language localization parity.
* **Test Mapping**: `TST-DAT-015` (*Google Drive Workout Export and Database Backup Integration Verification*)
  - Group 7: UI Dialog, Error Mapping & Disconnected State (`GoogleDriveAuthErrorMappingTest.kt`).
  - Group 8: 9-Language Localization Parity (`TranslationParityTest.kt`).
  - Group 9: Full Suite Clean-Room Regression (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing 1,757 clean-room unit tests continue to pass with 0 failures.
2. **Least-Privilege Privacy Invariant**: OAuth scope remains strictly `https://www.googleapis.com/auth/drive.file`.
3. **Decoupled Provider Invariant**: Dropbox, Strava, and local backup mechanisms operate completely independently without shared failure modes.
4. **Subtask Self-Sufficiency**: Subtask `ATT-2475` transitions directly to `Erledigt` upon passing Gate 3 audit via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent ticket `ATT-1306` remains reserved for the human user in `Final Review (Human)`.
6. **ASPICE TDD Process Governance**: In accordance with ASPICE SWE.2 / Gate 3 rules, production code and unit test files are constructed strictly during Stage 4 following Gate 3 sign-off. Gate 3 audits the completeness and correctness of the architectural plan.

---

## 4. Proposed Architectural Changes

### Component 1: `GoogleDriveAuthErrorResolver.kt` (Pure Mapping Utility)
* **Package**: `com.atrainingtracker.trainingtracker.cloud.googledrive`
* **Role**: Pure, testable static mapping function:
  `fun resolveErrorMessageResId(throwable: Throwable?): Int?`
* **Deterministic Status Code Mapping**:
  - `throwable == null` -> `null`
  - `ApiException`:
    - `e.statusCode == CommonStatusCodes.DEVELOPER_ERROR` (10) -> `R.string.google_drive_error_developer_config`
    - `e.statusCode == CommonStatusCodes.NETWORK_ERROR` (7) -> `R.string.google_drive_error_network`
    - `e.statusCode == GoogleSignInStatusCodes.SIGN_IN_CANCELLED` (12501) -> `null` (user dismissed picker intentionally; zero error banner)
    - Any other status code (unmapped codes, e.g. 8 `INTERNAL_ERROR`, 12500 `SIGN_IN_FAILED`, etc.) -> `R.string.google_drive_error_generic`
  - Non-`ApiException` `Throwable` (e.g. `IOException`, `SecurityException`) -> `R.string.google_drive_error_generic`

### Component 2: `GoogleDriveSettingsDialog.kt` (Reactive State & Conditional Disconnected UI)
* **Package**: `com.atrainingtracker.trainingtracker.ui.settings.googledrive`
* **Reactive State Hoisting & Synchronization**:
  - `var isConnected by remember { mutableStateOf(TrainingApplication.uploadToGoogleDrive()) }`
  - `var accountEmail by remember { mutableStateOf(TrainingApplication.getGoogleDriveAccountEmail()) }`
  - `var errorMessageResId by remember { mutableStateOf<Int?>(null) }`
  - **Connection Flow**:
    - Tapping "Verbinden": Clears `errorMessageResId = null`, sets `isLoading = true`, and launches `signInLauncher`.
    - In `signInLauncher` result callback:
      - If `GoogleSignIn.getSignedInAccountFromIntent` succeeds: launches coroutine on `Dispatchers.IO` to execute `acquireBearerToken(...)`.
      - On successful token acquisition: updates `TrainingApplication.storeGoogleDriveCredential(...)` and `TrainingApplication.setUploadToGoogleDrive(true)`, then switches to Main thread to set `isConnected = true`, `accountEmail = account.email`, `isLoading = false`, `errorMessageResId = null`.
      - On failure or exception: passes exception to `GoogleDriveAuthErrorResolver.resolveErrorMessageResId(e)` and assigns to `errorMessageResId`, with `isLoading = false`.
    - In `onDisconnectClick`:
      - Launches coroutine to execute `GoogleDriveAuthManager.disconnect(context)`, wipes credentials, sets `TrainingApplication.setUploadToGoogleDrive(false)`.
      - Updates UI state: `isConnected = false`, `accountEmail = null`, `errorMessageResId = null`, `isLoading = false`.
  - **Conditional Viewport Rendering**:
    - `GoogleDriveConnectionHeader` is always visible at the top (displaying "Verbinden" when disconnected or account email + "Trennen" when connected).
    - If `errorMessageResId != null`: displays localized error banner in `MaterialTheme.colorScheme.error` with `MaterialTheme.typography.bodySmall`.
    - `if (isConnected)`:
      - Renders `HorizontalDivider()`.
      - Renders synchronization switches: "Workouts automatisch exportieren", "Datenbank-Backup automatisch synchronisieren", "Nur über WLAN hochladen".
      - Renders `lastSyncText`.
    - `else`:
      - Renders informative onboarding prompt: `Text(stringResource(R.string.google_drive_connect_prompt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)`.
      - Synchronization switches are omitted, completely preventing contradictory states where toggles are ON while disconnected.

### Component 3: 9-Language Localization Parity
* **Resource Files**: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`
* **New Keys & Semantics**:
  1. `google_drive_error_developer_config`:
     - DE: *"Google Sign-In Konfigurationsfehler (Status 10): Bitte prüfen Sie die OAuth-Client-Registrierung (Paketname com.atrainingtracker.debug und SHA-1-Fingerabdruck) in der Google Cloud Console."*
     - EN: *"Google Sign-In configuration error (status 10): Please check the OAuth client registration (package name com.atrainingtracker.debug and SHA-1 fingerprint) in the Google Cloud Console."*
  2. `google_drive_error_network`:
     - DE: *"Keine Internetverbindung oder Netzwerkfehler beim Google Sign-In."*
     - EN: *"No internet connection or network error during Google Sign-In."*
  3. `google_drive_error_generic`:
     - DE: *"Google Sign-In fehlgeschlagen. Bitte versuchen Sie es erneut."*
     - EN: *"Google Sign-In failed. Please try again."*
  4. `google_drive_connect_prompt`:
     - DE: *"Verbinden Sie Ihr Google-Konto, um abgeschlossene Workouts und Datenbank-Backups automatisch in Google Drive zu sichern."*
     - EN: *"Connect your Google account to automatically export workouts and synchronize database backups to Google Drive."*

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: `DropboxSettingsDialog.kt` (closest existing cloud settings modal bottom sheet).
* **Reused components**: `AppBottomSheetContent`, `AppDialogActions.SaveCancel`, `HorizontalDivider`, `Switch`, `Text`, `LinearProgressIndicator`.
* **Theme tokens**:
  - Shapes: `MaterialTheme.shapes.extraLarge` (dialog default).
  - Spacing: `16.dp` card padding, `12.dp` toggle row spacing, `8.dp` bottom sheet padding.
  - Colors: `MaterialTheme.colorScheme.error` for error text, `MaterialTheme.colorScheme.onSurfaceVariant` for descriptions, standard theme surface and primary accents.
* **New one-off styles & justification**: None. Follows established design system and dialog specifications exactly.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: 9-Language String Localization
* **Target Files**: `app/src/main/res/values*/strings.xml` (all 9 locales).
* **Changes**: Add `google_drive_error_developer_config`, `google_drive_error_network`, `google_drive_error_generic`, and `google_drive_connect_prompt`.
* **Verification**: `TranslationParityTest.kt` passes with 0 missing keys.

### Step 2: Error Mapping Architecture (`GoogleDriveAuthErrorResolver.kt`)
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/cloud/googledrive/GoogleDriveAuthErrorResolver.kt`
* **Changes**: Implement pure, testable function `resolveErrorMessageResId(throwable: Throwable?): Int?` covering status 10, status 7, cancellation, unmapped status codes, and non-ApiException throwables.
* **Verification**: `GoogleDriveAuthErrorMappingTest.kt` validates status 10, status 7, cancellation, and fallback cases.

### Step 3: UI Enhancement in `GoogleDriveSettingsDialog.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/googledrive/GoogleDriveSettingsDialog.kt`
* **Changes**:
  - Integrate `GoogleDriveAuthErrorResolver.resolveErrorMessageResId`.
  - Display localized error banner via `stringResource(resId)` when error is present.
  - Wrap synchronization switches in `if (isConnected) { ... } else { Text(stringResource(R.string.google_drive_connect_prompt), ...) }`.

### Step 4: Unit & Contract Tests
* **Target Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/cloud/googledrive/GoogleDriveAuthErrorMappingTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/translations/TranslationParityTest.kt`
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.cloud.googledrive.*"`

### Step 5: Clean-Room Full Suite Regression
* **Command**: `./gradlew testDebugUnitTest`
* **Verification**: 100% pass rate, 0 failures, 0 regressions across all workspace modules.

---

## 6. Verification & Rollback Plan

* **Pre-Construction Check**: Verify `python3 tools/jira_util.py check-gate ATT-2475` exits with 0 before editing any production code files.
* **Verification**: Targeted tests during construction, followed by full test suite execution in Stage 5.
* **Rollback Plan**: All changes are isolated on `feature/ATT-1306`. If rejected, git revert cleanly restores previous state without impacting `develop`.
