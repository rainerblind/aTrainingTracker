# Stage 2: Requirement & Test Specification - ATT-4459: [Bug] [High] Fix legacy Dropbox credential key mismatch in AuthCredentialsStore causing Dropbox backup failure (PARTIAL_SUCCESS)

**Ticket**: [ATT-4459](https://rainerblind.atlassian.net/browse/ATT-4459)  
**Sub-task**: Pending Creation (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.4`  
**Requirement Mapping**: `REQ-DAT-032` (*OAuth Credential Isolation, Android Auto Backup & Device Transfer Rules, and .attbackup Credential Exclusion Policy*)  
**Test Spec ID**: `TST-DAT-027`  
**Branch**: `feature/ATT-4459`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`)  
**Date**: 2026-10-20  

---

## 1. Requirement Specification (REQ-DAT-032)

### 1.1 Problem Statement & Rationale
During on-device testing on an authentic Android device (`com.atrainingtracker.debug`), automated backup reported:
```
Last backup failed: PARTIAL_SUCCESS
```
Forensic analysis revealed:
1. `TrainingApplication.java` has historically stored credentials in default `SharedPreferences` under string keys:
   - `SP_DROPBOX_CREDENTIAL = "dropboxCredential"`
   - `SP_GOOGLE_DRIVE_ACCOUNT_EMAIL = "googleDriveAccountEmail"`
   - `SP_STRAVA_OAUTH_STATE = "SP_STRAVA_OAUTH_STATE"`
   - `SP_STRAVA_OAUTH_STATE_TIMESTAMP = "SP_STRAVA_OAUTH_STATE_TIMESTAMP"`
2. `AuthCredentialsStore.kt` defined mismatched constant key names:
   - `KEY_DROPBOX_CREDENTIAL = "sp_dropbox_credential"`
   - `KEY_GOOGLE_DRIVE_ACCOUNT_EMAIL = "sp_google_drive_account_email"`
   - `KEY_STRAVA_OAUTH_STATE = "stravaOAuthState"`
   - `KEY_STRAVA_OAUTH_STATE_TIMESTAMP = "stravaOAuthStateTimestamp"`
3. Because `ALL_CREDENTIAL_KEYS` in `AuthCredentialsStore.kt` only checked store key constants, `migrateLegacyCredentials()`, `sanitizeAfterRestore()`, and `sanitizeStagedPreferencesDirectory()` completely bypassed `"dropboxCredential"` and legacy keys.
4. Legacy Dropbox credentials remained unmigrated in default `SharedPreferences` and leaked into `.attbackup` archives.
5. In `BackupWorker.kt`, `dropboxConnected` checked only `TrainingApplication.uploadToDropbox()` without verifying `TrainingApplication.readDropboxCredential() != null`, attempting Dropbox upload without valid credentials and failing with `PARTIAL_SUCCESS`.
6. `MainActivityWithNavigation.checkPreferences()` lacked self-healing for orphaned Dropbox upload toggles.

### 1.2 Functional & Architectural Requirements
The system SHALL refine `REQ-DAT-032` with the following clauses:

* **Dual-Key Legacy Credential Migration & Aliasing (`REQ-DAT-032.1`)**:
  * `AuthCredentialsStore` SHALL define legacy key constants matching `TrainingApplication.java`:
    * `LEGACY_KEY_DROPBOX_CREDENTIAL = "dropboxCredential"`
    * `LEGACY_KEY_GOOGLE_DRIVE_ACCOUNT_EMAIL = "googleDriveAccountEmail"`
    * `LEGACY_KEY_STRAVA_OAUTH_STATE = "SP_STRAVA_OAUTH_STATE"`
    * `LEGACY_KEY_STRAVA_OAUTH_STATE_TIMESTAMP = "SP_STRAVA_OAUTH_STATE_TIMESTAMP"`
  * `AuthCredentialsStore` SHALL define `LEGACY_KEY_MIGRATION_MAP` mapping legacy keys to canonical store keys:
    * `"dropboxCredential"` $\rightarrow$ `KEY_DROPBOX_CREDENTIAL` (`"sp_dropbox_credential"`)
    * `"googleDriveAccountEmail"` $\rightarrow$ `KEY_GOOGLE_DRIVE_ACCOUNT_EMAIL` (`"sp_google_drive_account_email"`)
    * `"SP_STRAVA_OAUTH_STATE"` $\rightarrow$ `KEY_STRAVA_OAUTH_STATE` (`"stravaOAuthState"`)
    * `"SP_STRAVA_OAUTH_STATE_TIMESTAMP"` $\rightarrow$ `KEY_STRAVA_OAUTH_STATE_TIMESTAMP` (`"stravaOAuthStateTimestamp"`)
  * `migrateLegacyCredentials(context: Context)` SHALL inspect default `SharedPreferences` for both canonical store keys (`ALL_CREDENTIAL_KEYS`) and legacy keys (`LEGACY_KEY_MIGRATION_MAP.keys`).
  * When a legacy key is detected, it SHALL copy the value to the mapped canonical key in `auth_credentials_prefs` and purge the legacy key from default `SharedPreferences`.
  * `readDropboxCredential(context: Context)` SHALL first attempt to read `KEY_DROPBOX_CREDENTIAL` from `auth_credentials_prefs`. If null or blank, it SHALL defensively check `LEGACY_KEY_DROPBOX_CREDENTIAL` in `auth_credentials_prefs` before returning null.

* **Exhaustive Post-Restore & Staging Sanitization (`REQ-DAT-032.4`)**:
  * `ALL_CREDENTIAL_KEYS_TO_SANITIZE` SHALL encompass the union of `ALL_CREDENTIAL_KEYS` and all keys in `LEGACY_KEY_MIGRATION_MAP.keys`.
  * `sanitizeAfterRestore(context: Context)` SHALL strip all keys in `ALL_CREDENTIAL_KEYS_TO_SANITIZE` from default `SharedPreferences` and clear `auth_credentials_prefs`.
  * `sanitizeStagedPreferencesDirectory(stagingPrefsDir: File)` SHALL inspect XML files in staging and strip lines matching any key in `ALL_CREDENTIAL_KEYS_TO_SANITIZE`.

* **Guarded Cloud Provider Connection State in BackupWorker (`REQ-DAT-032.6`)**:
  * In `BackupWorker.kt`, `dropboxConnected` SHALL be evaluated as:
    ```kotlin
    val dropboxConnected = TrainingApplication.uploadToDropbox() && TrainingApplication.readDropboxCredential() != null
    ```
  * If `uploadToDropbox` is enabled but no valid Dropbox credentials exist, `BackupWorker` SHALL treat Dropbox as disconnected and skip uploading to Dropbox, avoiding false `PARTIAL_SUCCESS` failure statuses.

* **Self-Healing Preferences in MainActivityWithNavigation (`REQ-DAT-032.7`)**:
  * In `MainActivityWithNavigation.checkPreferences()`, the system SHALL check:
    ```kotlin
    if (TrainingApplication.uploadToDropbox() && TrainingApplication.readDropboxCredential() == null) {
        TrainingApplication.setUploadToDropbox(false)
    }
    ```
  * If Dropbox upload is enabled in default preferences but `readDropboxCredential()` returns null after migration has executed, `uploadToDropbox` SHALL be automatically reset to `false`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Legacy Key Migration)**:
  * *Given* default `SharedPreferences` populated with `dropboxCredential = "{\"access_token\":\"xyz\"}"`, `googleDriveAccountEmail = "user@test.com"`, `SP_STRAVA_OAUTH_STATE = "state123"`, and `SP_STRAVA_OAUTH_STATE_TIMESTAMP = 123456789L`,
  * *When* `AuthCredentialsStore.migrateLegacyCredentials(context)` executes,
  * *Then* the values are migrated to `auth_credentials_prefs` under canonical keys (`sp_dropbox_credential`, `sp_google_drive_account_email`, `stravaOAuthState`, `stravaOAuthStateTimestamp`), and all legacy keys are purged from default preferences.
* **Criterion 2 (Defensive Fallback in readDropboxCredential)**:
  * *Given* `auth_credentials_prefs` containing a credential stored under legacy key `"dropboxCredential"`,
  * *When* `AuthCredentialsStore.readDropboxCredential(context)` is called,
  * *Then* it returns the credential string successfully.
* **Criterion 3 (Sanitization of Legacy Keys)**:
  * *Given* staging XML files or default `SharedPreferences` containing legacy keys (`dropboxCredential`, `googleDriveAccountEmail`, etc.),
  * *When* `sanitizeAfterRestore(context)` or `sanitizeStagedPreferencesDirectory(stagingDir)` runs,
  * *Then* all occurrences of both legacy and canonical credential keys are stripped.
* **Criterion 4 (BackupWorker Guarded Execution)**:
  * *Given* `uploadToDropbox = true` and `uploadToGoogleDrive = true` with valid Google Drive credentials but missing Dropbox credentials,
  * *When* `BackupWorker.doWork()` runs,
  * *Then* `dropboxConnected` is evaluated as `false`, Google Drive upload succeeds, and the backup status records `SUCCESS` (or `LOCAL_SUCCESS` if no cloud providers are connected) rather than `PARTIAL_SUCCESS`.
* **Criterion 5 (Self-Healing Preference Reset)**:
  * *Given* `uploadToDropbox = true` and `readDropboxCredential() == null`,
  * *When* `MainActivityWithNavigation.checkPreferences()` runs on resume,
  * *Then* `TrainingApplication.uploadToDropbox()` is reset to `false`.

### 1.4 System Invariants
1. **Zero Credential Leakage**: No third-party OAuth tokens, state tokens, or JSON credential payloads reside in default `SharedPreferences` or enter `.attbackup` archives.
2. **Backward Compatibility**: Existing athletes upgrading from legacy versions retain their authentication state without being logged out.
3. **Backup Status Integrity**: Automated backups report `SUCCESS` or `LOCAL_SUCCESS` when all configured and authenticated targets succeed; `PARTIAL_SUCCESS` is never triggered by missing credentials.
4. **Clean-Room Test Pass Rate**: 100% test pass rate across the full project test suite (`./gradlew testDebugUnitTest`).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-DAT-032` (introduced in `ATT-4023`), refined by `ATT-4459`. Targets `AuthCredentialsStore.kt`, `TrainingApplication.java`, `BackupWorker.kt`, `MainActivityWithNavigation.kt`, `BackupManager.kt`, `MigrationEngine.kt`, and `AndroidManifest.xml`.
* **Historical Origin & Commit Trace**: Commit `203baa27bdcf` (Ticket `ATT-4023`, Sprint `2026-42.10`, author: Rainer Blind) and refined in Sprint `2026-43.4` (`ATT-4459`).
* **Root Reason for Existing Formulation**: When `ATT-4023` introduced `AuthCredentialsStore.kt` to isolate credentials, constant key names (`"sp_dropbox_credential"`, `"sp_google_drive_account_email"`, `"stravaOAuthState"`, `"stravaOAuthStateTimestamp"`) were created without reconciling them against existing constants in `TrainingApplication.java` (`"dropboxCredential"`, `"googleDriveAccountEmail"`, `"SP_STRAVA_OAUTH_STATE"`, `"SP_STRAVA_OAUTH_STATE_TIMESTAMP"`). As a result, `migrateLegacyCredentials()` and sanitization routines missed legacy keys in default `SharedPreferences`, causing credentials to bypass migration into `auth_credentials_prefs.xml`, leak into `.attbackup` archives, and cause `BackupWorker` to fail with `PARTIAL_SUCCESS`.
* **Preservation of Core Invariants**: Full credential isolation within `auth_credentials_prefs.xml`, Auto Backup exclusion, `.attbackup` exclusion, restore sanitization, backward compatibility without re-login for upgrading users, reliable cloud backups, and 100% clean-room test suite pass rate are strictly preserved.

---

## 3. Test Specification (TST-DAT-027)

### Test Case 1: Legacy Credential Migration Test (`TST-DAT-027.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/security/AuthCredentialsStoreTest.kt`
* **Preconditions**: Default `SharedPreferences` populated with Strava tokens, athlete ID, OAuth state, Google Drive email/token, and Dropbox credential.
* **Action**: Execute `AuthCredentialsStore.migrateLegacyCredentials(context)`.
* **Expected Result**: All credentials exist in `auth_credentials_prefs` and are completely purged from default `SharedPreferences`.

### Test Case 2: Credential Read & Write Delegation Test (`TST-DAT-027.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/security/AuthCredentialsStoreTest.kt`
* **Preconditions**: Clean credentials store.
* **Action**: Call `TrainingApplication` getters/setters for Strava, Google Drive, and Dropbox credentials.
* **Expected Result**: Read and write operations delegate to `AuthCredentialsStore` without writing to default `SharedPreferences`.

### Test Case 3: Android Auto Backup XML Rules Validation Test (`TST-DAT-027.3`)
* **Scope**: Integration Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/security/BackupRulesXmlValidationTest.kt`
* **Preconditions**: XML rule descriptors present in resources.
* **Action**: Parse `data_extraction_rules.xml` and `full_backup_content.xml`.
* **Expected Result**: `auth_credentials_prefs.xml` is excluded from cloud backup, device transfer, and full backup domains.

### Test Case 4: Backup Archive Bundling Exclusion Test (`TST-DAT-027.4`)
* **Scope**: Integration Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupManagerCredentialExclusionTest.kt`
* **Preconditions**: Mock storage with `auth_credentials_prefs.xml` populated with tokens.
* **Action**: Run `BackupManager.createBackup(context)`.
* **Expected Result**: `shared_prefs/auth_credentials_prefs.xml` is absent from `.attbackup` archive, and zero tokens exist in the zip.

### Test Case 5: Restore Engine Sanitization & Re-Auth Test (`TST-DAT-027.5`)
* **Scope**: Integration Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/MigrationEngineCredentialSanitizationTest.kt`
* **Preconditions**: Backup archive containing legacy tokens in default preferences and a mock `auth_credentials_prefs.xml`.
* **Action**: Restore archive via `MigrationEngine`.
* **Expected Result**: `auth_credentials_prefs.xml` discarded, legacy token keys stripped from default preferences, and cloud status requires re-authentication.

### Test Case 6: FileProvider Hardening Contract Test (`TST-DAT-027.6`)
* **Scope**: Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/security/FileProviderHardeningContractTest.kt`
* **Preconditions**: Manifest and XML resources loaded.
* **Action**: Inspect `GenericFileProvider` and `file_paths.xml`.
* **Expected Result**: `GenericFileProvider` and `provider_paths.xml` do not exist, and `file_paths.xml` contains zero `path="."` root exposure.

### Test Case 7: Clean-Room Regression Suite (`TST-DAT-027.7`)
* **Scope**: Clean-Room Full Regression
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% pass rate across all project unit tests.

### Test Case 8: Legacy Key Normalization & Dual-Key Migration Test (`TST-DAT-027.8`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/security/AuthCredentialsStoreTest.kt`
* **Preconditions**: Default `SharedPreferences` populated with legacy keys `"dropboxCredential"`, `"googleDriveAccountEmail"`, `"SP_STRAVA_OAUTH_STATE"`, `"SP_STRAVA_OAUTH_STATE_TIMESTAMP"`.
* **Action**: Execute `AuthCredentialsStore.migrateLegacyCredentials(context)`.
* **Expected Result**:
  * Legacy keys are mapped and stored under canonical keys in `auth_credentials_prefs` (`KEY_DROPBOX_CREDENTIAL`, `KEY_GOOGLE_DRIVE_ACCOUNT_EMAIL`, `KEY_STRAVA_OAUTH_STATE`, `KEY_STRAVA_OAUTH_STATE_TIMESTAMP`).
  * All legacy keys are completely removed from default `SharedPreferences`.
  * `AuthCredentialsStore.readDropboxCredential(context)` returns the credential string.
  * Fallback to legacy key in `auth_credentials_prefs` works when only legacy key exists.

### Test Case 9: Guarded BackupWorker Cloud Provider Check Test (`TST-DAT-027.9`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupWorkerTest.kt`
* **Preconditions**: `uploadToDropbox = true`, but `TrainingApplication.readDropboxCredential()` returns `null`.
* **Action**: Execute `BackupWorker.doWork()`.
* **Expected Result**: Dropbox upload is skipped (`dropboxConnected = false`), and backup completes successfully as `LOCAL_SUCCESS` (or `SUCCESS` if Google Drive upload succeeds) without recording `PARTIAL_SUCCESS`.

### Test Case 10: Defensive Self-Healing Preferences Test (`TST-DAT-027.10`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigationTest.kt` (or targeted test)
* **Preconditions**: `uploadToDropbox = true` with `readDropboxCredential() == null`.
* **Action**: Invoke `checkPreferences()`.
* **Expected Result**: `uploadToDropbox` is automatically reset to `false`.

---

## 4. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-DAT-027.1` | Unit | `AuthCredentialsStore.migrateLegacyCredentials` | `REQ-DAT-032.1` | Specified |
| `TST-DAT-027.2` | Unit | `TrainingApplication` accessors | `REQ-DAT-032.1` | Specified |
| `TST-DAT-027.3` | Integration | XML rule validation | `REQ-DAT-032.2` | Specified |
| `TST-DAT-027.4` | Integration | `BackupManager.createBackup` | `REQ-DAT-032.3` | Specified |
| `TST-DAT-027.5` | Integration | `MigrationEngine.restore` | `REQ-DAT-032.4` | Specified |
| `TST-DAT-027.6` | Contract | Manifest / XML check | `REQ-DAT-032.5` | Specified |
| `TST-DAT-027.7` | Regression | Full Test Suite | `REQ-PRO-001` | Specified |
| `TST-DAT-027.8` | Unit | `AuthCredentialsStore.migrateLegacyCredentials` & `readDropboxCredential` | `REQ-DAT-032.1`, `REQ-DAT-032.4` | Planned (ATT-4459) |
| `TST-DAT-027.9` | Unit | `BackupWorker.doWork` | `REQ-DAT-032.6` | Planned (ATT-4459) |
| `TST-DAT-027.10` | Unit | `MainActivityWithNavigation.checkPreferences` | `REQ-DAT-032.7` | Planned (ATT-4459) |
