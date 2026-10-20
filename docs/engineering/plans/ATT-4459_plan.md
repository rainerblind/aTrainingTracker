# Stage 3: Implementation Plan - ATT-4459: [Bug] [High] Fix legacy Dropbox credential key mismatch in AuthCredentialsStore causing Dropbox backup failure (PARTIAL_SUCCESS)

**Ticket**: [ATT-4459](https://rainerblind.atlassian.net/browse/ATT-4459)  
**Sub-task**: Pending Creation (`[Impl-Plan]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.4`  
**Requirement Mapping**: `REQ-DAT-032` (*OAuth Credential Isolation, Android Auto Backup & Device Transfer Rules, and .attbackup Credential Exclusion Policy*)  
**Test Mapping**: `TST-DAT-027` (*OAuth Credential Isolation, Android Auto Backup & Device Transfer Rules, and .attbackup Credential Exclusion Verification Suite*)  
**Branch**: `feature/ATT-4459`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-10-20  

---

## 1. Problem Description & Background

During on-device testing of automated backup in `ATT-3472` on an authentic Android device (`com.atrainingtracker.debug`), the Backup tab reported:
```
Last backup failed: PARTIAL_SUCCESS
```

Forensic investigation revealed:
1. Legacy SharedPreferences keys in [`TrainingApplication.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java#L189) (`"dropboxCredential"`, `"googleDriveAccountEmail"`, `"SP_STRAVA_OAUTH_STATE"`, `"SP_STRAVA_OAUTH_STATE_TIMESTAMP"`) mismatched the constant names defined in [`AuthCredentialsStore.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/security/AuthCredentialsStore.kt#L45-L65) (`"sp_dropbox_credential"`, `"sp_google_drive_account_email"`, `"stravaOAuthState"`, `"stravaOAuthStateTimestamp"`).
2. Because `ALL_CREDENTIAL_KEYS` checked only the store constants, `migrateLegacyCredentials()`, `sanitizeAfterRestore()`, and `sanitizeStagedPreferencesDirectory()` bypassed the legacy keys.
3. As a result, `"dropboxCredential"` remained in default `SharedPreferences` and leaked into `.attbackup` archives (`REQ-DAT-032.1`, `REQ-DAT-032.3`). Furthermore, `TrainingApplication.readDropboxCredential()` returned `null`, causing `DropboxBackupManager.uploadBackup()` to fail immediately while Google Drive upload succeeded, leaving `last_backup_status = "PARTIAL_SUCCESS"`.
4. In [`BackupWorker.kt:39`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupWorker.kt#L39), `dropboxConnected` checked only `TrainingApplication.uploadToDropbox()` without defensively checking `TrainingApplication.readDropboxCredential() != null` (unlike [`ImportBackupTabsScreen.kt:127`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt#L127)).
5. In [`MainActivityWithNavigation.checkPreferences()`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt#L584), the app lacked active self-healing to reset `uploadToDropbox = false` when credentials are missing.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: [`REQ-DAT-032`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L864) (*OAuth Credential Isolation, Android Auto Backup & Device Transfer Rules, and .attbackup Credential Exclusion Policy*)
  * `REQ-DAT-032.1`: Dual-key legacy credential migration & aliased retrieval.
  * `REQ-DAT-032.4`: Exhaustive post-restore & staging XML sanitization.
  * `REQ-DAT-032.6`: Guarded cloud provider connection state in `BackupWorker`.
  * `REQ-DAT-032.7`: Self-healing preferences reset in `MainActivityWithNavigation.checkPreferences()`.
* **Test Mapping**: [`TST-DAT-027`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L820) (*OAuth Credential Isolation, Android Auto Backup & Device Transfer Rules, and .attbackup Credential Exclusion Verification Suite*)
  * `TST-DAT-027.8`: Legacy key normalization, dual-key migration, and aliased credential read in `AuthCredentialsStoreTest.kt`.
  * `TST-DAT-027.9`: Guarded `BackupWorker` cloud provider null check in `BackupWorkerTest.kt`.
  * `TST-DAT-027.10`: Defensive self-healing preferences reset in `MainActivityWithNavigationInteropTest.kt` (or targeted test).
  * `TST-DAT-027.4`: Archive bundling exclusion in `BackupManagerCredentialExclusionTest.kt`.
  * `TST-DAT-027.5`: Restore engine sanitization in `MigrationEngineCredentialSanitizationTest.kt`.
  * `TST-DAT-027.7`: Clean-room full regression suite (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Credential Leakage**: No third-party OAuth tokens, state strings, or JSON credential payloads reside in default `SharedPreferences` or enter `.attbackup` archives.
2. **Backward Compatibility & Seamless Migration**: Upgrading users from legacy app versions retain active authentication without being logged out.
3. **Backup Status Integrity**: Automated backups report `SUCCESS` or `LOCAL_SUCCESS` when all configured and authenticated targets succeed; `PARTIAL_SUCCESS` is never triggered by missing credentials.
4. **Code Lock Until Gate 3 Passes**: No production code edits in `app/src/main` until Gate 3 is approved.
5. **Single Squashed Atomic Commit**: All changes committed strictly at Stage 5.

---

## 4. Proposed Architectural Changes

### Component 1: `AuthCredentialsStore.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/security/AuthCredentialsStore.kt`
* **Layer**: Security / Storage
* **Changes**:
  1. Add legacy constant definitions matching `TrainingApplication.java`:
     ```kotlin
     const val LEGACY_KEY_DROPBOX_CREDENTIAL = "dropboxCredential"
     const val LEGACY_KEY_GOOGLE_DRIVE_ACCOUNT_EMAIL = "googleDriveAccountEmail"
     const val LEGACY_KEY_STRAVA_OAUTH_STATE = "SP_STRAVA_OAUTH_STATE"
     const val LEGACY_KEY_STRAVA_OAUTH_STATE_TIMESTAMP = "SP_STRAVA_OAUTH_STATE_TIMESTAMP"
     ```
  2. Define `LEGACY_KEY_MIGRATION_MAP`:
     ```kotlin
     val LEGACY_KEY_MIGRATION_MAP = mapOf(
         LEGACY_KEY_DROPBOX_CREDENTIAL to KEY_DROPBOX_CREDENTIAL,
         LEGACY_KEY_GOOGLE_DRIVE_ACCOUNT_EMAIL to KEY_GOOGLE_DRIVE_ACCOUNT_EMAIL,
         LEGACY_KEY_STRAVA_OAUTH_STATE to KEY_STRAVA_OAUTH_STATE,
         LEGACY_KEY_STRAVA_OAUTH_STATE_TIMESTAMP to KEY_STRAVA_OAUTH_STATE_TIMESTAMP
     )
     ```
  3. Define `ALL_CREDENTIAL_KEYS_TO_SANITIZE = ALL_CREDENTIAL_KEYS + LEGACY_KEY_MIGRATION_MAP.keys`.
  4. Refactor `migrateLegacyCredentials(context: Context)`:
     - Scan `defaultPrefs` for all keys in `ALL_CREDENTIAL_KEYS_TO_SANITIZE`.
     - For each present key:
       - Determine destination key: `targetKey = LEGACY_KEY_MIGRATION_MAP[key] ?: key`.
       - Copy value from `defaultPrefs` into `credPrefs` under `targetKey`.
       - Remove key from `defaultPrefs`.
     - Apply/commit changes atomically.
  5. Refactor `readDropboxCredential(context: Context)`:
     - Read `KEY_DROPBOX_CREDENTIAL` (`"sp_dropbox_credential"`).
     - If null or blank, fall back to checking `LEGACY_KEY_DROPBOX_CREDENTIAL` (`"dropboxCredential"`) in `credPrefs`.
  6. Refactor `sanitizeAfterRestore(context: Context)`:
     - Strip all keys in `ALL_CREDENTIAL_KEYS_TO_SANITIZE` from default preferences.
     - Invoke `clearAllCredentials(context)`.
  7. Refactor `sanitizeStagedPreferencesDirectory(stagingPrefsDir: File)`:
     - Discard `auth_credentials_prefs.xml` and `.bak`.
     - Strip lines containing any key in `ALL_CREDENTIAL_KEYS_TO_SANITIZE`.
  8. In `clearStravaOAuthState(context: Context)` and `clearStravaCredentials(context: Context)`:
     - Defensively remove both canonical and legacy Strava OAuth state keys (`KEY_STRAVA_OAUTH_STATE`, `LEGACY_KEY_STRAVA_OAUTH_STATE`, `KEY_STRAVA_OAUTH_STATE_TIMESTAMP`, `LEGACY_KEY_STRAVA_OAUTH_STATE_TIMESTAMP`).

### Component 2: `BackupWorker.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupWorker.kt`
* **Layer**: Background Migration Worker
* **Changes**:
  - Update line 39 to guard `dropboxConnected`:
    ```kotlin
    val dropboxConnected = TrainingApplication.uploadToDropbox() && TrainingApplication.readDropboxCredential() != null
    ```
    This matches the existing contract in `ImportBackupTabsScreen.kt:126-128`.

### Component 3: `MainActivityWithNavigation.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt`
* **Layer**: UI / Lifecycle Host
* **Changes**:
  - In `checkPreferences()`:
    ```kotlin
    if (TrainingApplication.uploadToDropbox() && TrainingApplication.readDropboxCredential() == null) {
        Log.i(TAG, "Dropbox enabled but credentials missing; resetting uploadToDropbox to false")
        TrainingApplication.setUploadToDropbox(false)
    }
    ```
    This mirrors the Strava preference reset check (`uploadToStrava() && getStravaAccessToken() == null`).

### UI Consistency (Rule 23)
* No UI changes. This defect fix affects only preference migration, security sanitization, worker null gating, and background self-healing.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: `AuthCredentialsStore.kt` Legacy Key Migration & Sanitization
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/security/AuthCredentialsStore.kt`
* **Action**:
  - Add `LEGACY_KEY_*` constants, `LEGACY_KEY_MIGRATION_MAP`, and `ALL_CREDENTIAL_KEYS_TO_SANITIZE`.
  - Update `migrateLegacyCredentials()` to map legacy keys to canonical keys and remove legacy keys from default preferences.
  - Update `readDropboxCredential()` with fallback to `LEGACY_KEY_DROPBOX_CREDENTIAL`.
  - Update `sanitizeAfterRestore()` and `sanitizeStagedPreferencesDirectory()` to use `ALL_CREDENTIAL_KEYS_TO_SANITIZE`.
  - Update `clearStravaOAuthState()` and `clearStravaCredentials()` to clear legacy OAuth state keys.

### Step 2: `BackupWorker.kt` Cloud Connection State Guard
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupWorker.kt`
* **Action**:
  - Change line 39: `val dropboxConnected = TrainingApplication.uploadToDropbox() && TrainingApplication.readDropboxCredential() != null`.

### Step 3: `MainActivityWithNavigation.kt` Self-Healing Preferences Guard
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt`
* **Action**:
  - In `checkPreferences()`, add the `uploadToDropbox` null-credential check and reset.

### Step 4: Unit Test Updates and Additions
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/security/AuthCredentialsStoreTest.kt`:
    - Add `testLegacyKeyNormalizationAndDualKeyMigration()` verifying `"dropboxCredential"`, `"googleDriveAccountEmail"`, `"SP_STRAVA_OAUTH_STATE"`, and `"SP_STRAVA_OAUTH_STATE_TIMESTAMP"` migrate to canonical store keys and are purged from default preferences (`TST-DAT-027.8`).
    - Add `testReadDropboxCredential_fallbackToLegacyKey()` verifying credentials stored under legacy key in `auth_credentials_prefs` are read successfully (`TST-DAT-027.8`).
    - Update `testSanitizeAfterRestoreRemovesLegacyKeysAndClearsAuthStore()` asserting all legacy keys are purged.
  - `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupWorkerTest.kt`:
    - Add `testDoWork_whenDropboxEnabledWithoutCredentials_skipsDropboxAndSucceedsLocally()` verifying `LOCAL_SUCCESS` when credentials are null (`TST-DAT-027.9`).
    - Update `testDoWork_whenCloudUploadFails_preservesLocalBackupAndReturnsRetry()` to mock `TrainingApplication.readDropboxCredential()` returning a valid credential when testing upload failure.
  - `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupManagerCredentialExclusionTest.kt`:
    - Assert default preferences with `"dropboxCredential"` and `"googleDriveAccountEmail"` are purged before archive creation (`TST-DAT-027.4`).
  - `app/src/test/java/com/atrainingtracker/trainingtracker/migration/MigrationEngineCredentialSanitizationTest.kt`:
    - Assert staging XML files containing `"dropboxCredential"` have that line stripped by `sanitizeStagedPreferencesDirectory()` (`TST-DAT-027.5`).
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/MainActivityWithNavigationInteropTest.kt` (or targeted test):
    - Verify `checkPreferences()` resets `uploadToDropbox` to false when `readDropboxCredential()` is null (`TST-DAT-027.10`).

### Step 5: Fast Unit Test Loop
* **Commands**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.security.AuthCredentialsStoreTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.BackupWorker*"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.BackupManagerCredentialExclusionTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.MigrationEngineCredentialSanitizationTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Fast unit tests during Stage 4 construction ($< 15\text{ s}$).
  - Full clean-room test suite execution in Stage 5 (`./gradlew testDebugUnitTest`).
  - Complete check of `docs/requirements.md` and `docs/tests.md` governance.
* **Rollback**:
  - Branch isolation on `feature/ATT-4459` allows atomic rollback via `git checkout sprint/2026-43.4` without contaminating mainline branches.
