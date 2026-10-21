# Stage 1 Analysis: ATT-4459 - [Bug] [High] Fix legacy Dropbox credential key mismatch in AuthCredentialsStore causing Dropbox backup failure (PARTIAL_SUCCESS)

**Ticket**: [ATT-4459](https://rainerblind.atlassian.net/browse/ATT-4459)  
**Sub-task**: Pending Creation (`[Analysis]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.4`  
**Branch**: `feature/ATT-4459`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-10-20  

---

## 1. Problem Statement & Motivation

During on-device testing of `ATT-3472` on an authentic Android device (`com.atrainingtracker.debug`), an automated backup anomaly was observed in the Backup tab:
```
Last backup failed: PARTIAL_SUCCESS
```

Forensic investigation of application storage files (`shared_prefs/com.atrainingtracker.debug_preferences.xml` and `shared_prefs/auth_credentials_prefs.xml`) revealed the failure state:
1. **Unmigrated Dropbox Credentials in Default SharedPreferences**:
   - `com.atrainingtracker.debug_preferences.xml` contained:
     - `uploadToDropbox = true`
     - `uploadToGoogleDrive = true` & `uploadBackupToGoogleDrive = true`
     - `googleDriveLastSyncStatus = SUCCESS` (Google Drive backup upload succeeded at 20:43:19)
     - `dropboxCredential = { "access_token": "...", "refresh_token": "..." }` sat unmigrated in default `SharedPreferences`!
2. **Missing Dropbox Credentials in Isolated Auth Store**:
   - In `shared_prefs/auth_credentials_prefs.xml`:
     - `googleDriveAuthToken` and `stravaToken` were properly migrated.
     - *No Dropbox credential existed* in `auth_credentials_prefs.xml`.
3. **Consequences for Automated Backup and Data Security**:
   - **Broken Dropbox Backup (`PARTIAL_SUCCESS`)**: When `BackupWorker.doWork()` ran, `TrainingApplication.uploadToDropbox()` returned `true` because the toggle in default preferences was enabled. However, when `DropboxBackupManager.uploadBackup()` was invoked, `TrainingApplication.readDropboxCredential()` delegated to `AuthCredentialsStore.readDropboxCredential(cAppContext)`. The store searched for key `"sp_dropbox_credential"` in `auth_credentials_prefs.xml`, found `null`, and aborted the upload (`dbxSuccess = false`). Because Google Drive upload succeeded (`gdSuccess = true`) while Dropbox failed, `BackupWorker` recorded `last_backup_status = "PARTIAL_SUCCESS"`.
   - **Credential Security Leakage (`REQ-DAT-032.1`, `REQ-DAT-032.3`)**: Because `"dropboxCredential"` was never migrated, it remained in default `SharedPreferences` and was packaged into `.attbackup` zip archives during `BackupManager.createBackup(context)`, violating credential isolation rules.

---

## 2. Root Cause Analysis (Forensic Investigation)

A comprehensive audit of the code paths introduced in `ATT-4023` ([`AuthCredentialsStore.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/security/AuthCredentialsStore.kt), [`TrainingApplication.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java), and [`BackupWorker.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupWorker.kt)) identified three contributing root causes:

### 2.1 Key Name Mismatch Between `TrainingApplication.java` and `AuthCredentialsStore.kt`
In [`TrainingApplication.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java#L189), legacy third-party authentication credentials have historically been persisted under the following string keys in default `SharedPreferences`:
* `SP_DROPBOX_CREDENTIAL = "dropboxCredential"` (line 189)
* `SP_GOOGLE_DRIVE_ACCOUNT_EMAIL = "googleDriveAccountEmail"` (line 127)
* `SP_STRAVA_OAUTH_STATE = "SP_STRAVA_OAUTH_STATE"` (line 137; also in [`StravaOAuthStateManager.kt:38`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaOAuthStateManager.kt#L38))
* `SP_STRAVA_OAUTH_STATE_TIMESTAMP = "SP_STRAVA_OAUTH_STATE_TIMESTAMP"` (line 138; also in [`StravaOAuthStateManager.kt:39`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaOAuthStateManager.kt#L39))

However, in [`AuthCredentialsStore.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/security/AuthCredentialsStore.kt#L45-L65), different constant string keys were defined:
```kotlin
// In AuthCredentialsStore.kt:
const val KEY_DROPBOX_CREDENTIAL = "sp_dropbox_credential"           // Mismatched with "dropboxCredential"
const val KEY_GOOGLE_DRIVE_ACCOUNT_EMAIL = "sp_google_drive_account_email" // Mismatched with "googleDriveAccountEmail"
const val KEY_STRAVA_OAUTH_STATE = "stravaOAuthState"                 // Mismatched with "SP_STRAVA_OAUTH_STATE"
const val KEY_STRAVA_OAUTH_STATE_TIMESTAMP = "stravaOAuthStateTimestamp" // Mismatched with "SP_STRAVA_OAUTH_STATE_TIMESTAMP"

val ALL_CREDENTIAL_KEYS = setOf(
    KEY_STRAVA_TOKEN,
    KEY_STRAVA_REFRESH_TOKEN,
    KEY_STRAVA_TOKEN_EXPIRES_AT,
    KEY_STRAVA_ATHLETE_ID,
    KEY_STRAVA_OAUTH_STATE,
    KEY_STRAVA_OAUTH_STATE_TIMESTAMP,
    KEY_GOOGLE_DRIVE_ACCOUNT_EMAIL,
    KEY_GOOGLE_DRIVE_AUTH_TOKEN,
    KEY_DROPBOX_CREDENTIAL
)
```

### 2.2 Blind Spot in `migrateLegacyCredentials()` and Sanitization Routines
When `AuthCredentialsStore.migrateLegacyCredentials(context)` executes in `TrainingApplication.onCreate()`, it filters candidate keys using `ALL_CREDENTIAL_KEYS`:
```kotlin
val keysToMigrate = ALL_CREDENTIAL_KEYS.filter { 
    try { defaultPrefs.contains(it) } catch (_: Throwable) { false } 
}
```
1. Because `ALL_CREDENTIAL_KEYS` contains only `"sp_dropbox_credential"`, `defaultPrefs.contains("dropboxCredential")` is never evaluated.
2. The legacy Dropbox credential JSON is completely bypassed during migration, never copied to `auth_credentials_prefs.xml`, and never purged from default preferences.
3. Similarly, `sanitizeAfterRestore()` and `sanitizeStagedPreferencesDirectory()` only check `ALL_CREDENTIAL_KEYS`, meaning legacy keys in restored `.attbackup` archives or staging directories escape sanitization.

### 2.3 Unguarded Cloud Provider Connection State in `BackupWorker.kt`
In [`BackupWorker.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupWorker.kt#L39-L41):
```kotlin
val dropboxConnected = TrainingApplication.uploadToDropbox()
val googleDriveConnected = TrainingApplication.uploadToGoogleDrive() && TrainingApplication.uploadBackupToGoogleDrive()
```
`TrainingApplication.uploadToDropbox()` merely queries boolean flag `"uploadToDropbox"` in default preferences. It does NOT verify whether a valid Dropbox credential exists.
In contrast, [`ImportBackupTabsScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt#L126-L128) guards this state defensively:
```kotlin
val isDropboxConnected = remember(uiState) {
    TrainingApplication.uploadToDropbox() && TrainingApplication.readDropboxCredential() != null
}
```
Because `BackupWorker` lacked the `TrainingApplication.readDropboxCredential() != null` check, it attempted an upload that was doomed to fail whenever credentials were absent or unmigrated.

Furthermore, [`MainActivityWithNavigation.checkPreferences()`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt#L584-L593) performs self-healing for Strava (`if (uploadToStrava && getStravaAccessToken == null) setUploadToStrava(false)`), but lacked equivalent self-healing for Dropbox.

---

## 3. User Scope Grounding (ATT-1250)

In strict accordance with `ATT-1250` and the sprint planning specification:

* **In-Scope Goals**:
  1. **Dual-Key Migration & Canonical Mapping in `AuthCredentialsStore.kt`**:
     - Expand `AuthCredentialsStore.migrateLegacyCredentials(context)` to detect and migrate both legacy keys from `TrainingApplication.java` (`"dropboxCredential"`, `"googleDriveAccountEmail"`, `"SP_STRAVA_OAUTH_STATE"`, `"SP_STRAVA_OAUTH_STATE_TIMESTAMP"`) and store keys (`"sp_dropbox_credential"`, `"sp_google_drive_account_email"`, `"stravaOAuthState"`, `"stravaOAuthStateTimestamp"`).
     - Atomically map legacy keys to canonical keys in `auth_credentials_prefs` and purge all legacy keys from default `SharedPreferences`.
     - In `AuthCredentialsStore.readDropboxCredential(context)`, defensively check `KEY_DROPBOX_CREDENTIAL` and fall back to legacy `"dropboxCredential"` in `auth_credentials_prefs` for complete backward compatibility.
  2. **Comprehensive Sanitization in `AuthCredentialsStore.kt`**:
     - In `sanitizeAfterRestore(context)` and `sanitizeStagedPreferencesDirectory(stagingPrefsDir)`, purge/strip all keys (both canonical and legacy) from default preferences and staging XML files.
  3. **Guarded Connection State in `BackupWorker.kt`**:
     - Update line 39 in `BackupWorker.kt` to:
       ```kotlin
       val dropboxConnected = TrainingApplication.uploadToDropbox() && TrainingApplication.readDropboxCredential() != null
       ```
       matching the contract in `ImportBackupTabsScreen.kt:127`.
  4. **Active Self-Healing in `MainActivityWithNavigation.checkPreferences()`**:
     - Reset `TrainingApplication.setUploadToDropbox(false)` if `uploadToDropbox()` is true but `readDropboxCredential()` returns `null` after `migrateLegacyCredentials()` has run.
  5. **Regression & Contract Unit Tests**:
     - Extend `AuthCredentialsStoreTest.kt`, `BackupWorkerTest.kt`, `BackupManagerCredentialExclusionTest.kt`, and `MigrationEngineCredentialSanitizationTest.kt` to assert migration, exclusion, sanitization, and guarded worker execution.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - No modification to OAuth authentication protocols, PKCE flow, or Dropbox SDK v2 APIs.
  - No changes to Google Drive or Strava token exchange/refresh logic.
  - No alteration of `.attbackup` archive formats or SQLite backup snapshot mechanics (`REQ-DAT-031`).
  - No UI or visual changes to `ImportBackupTabsScreen.kt` or `DropboxSettingsDialog.kt`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**:
  Refines [`REQ-DAT-032`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L864) (*OAuth Credential Isolation, Android Auto Backup & Device Transfer Rules, and .attbackup Credential Exclusion Policy*).
  Targets `AuthCredentialsStore.kt`, `BackupWorker.kt`, and `MainActivityWithNavigation.kt`.
* **Historical Origin & Commit Trace**:
  - `git log -S "REQ-DAT-032" --oneline docs/requirements.md` points to commit `203baa27bdcf` (Ticket `ATT-4023`, Sprint `2026-42.10`, author: Rainer Blind).
  - Commit `203baa27bdcf` introduced `AuthCredentialsStore.kt` to isolate Strava, Google Drive, and Dropbox credentials into private `auth_credentials_prefs.xml` and exclude them from Auto Backup and `.attbackup` archives.
* **Root Reason for Existing Formulation**:
  - `REQ-DAT-032` explicitly specified:
    > "Upon first initialization, `AuthCredentialsStore.migrateLegacyCredentials(context: Context)` SHALL check default `SharedPreferences`. If any legacy credential keys are present, it SHALL atomically copy them to `auth_credentials_prefs` and purge them immediately from default `SharedPreferences`."
  - When implementing `ATT-4023`, constant keys (`"sp_dropbox_credential"`, `"sp_google_drive_account_email"`) were introduced without reconciling them against the existing keys defined in `TrainingApplication.java` (`"dropboxCredential"`, `"googleDriveAccountEmail"`). This discrepancy caused `migrateLegacyCredentials()` to ignore existing legacy tokens on devices upgraded from older versions.
* **Preservation of Core Invariants**:
  - `REQ-DAT-032.1`: All third-party OAuth credentials remain strictly encapsulated within `auth_credentials_prefs.xml`.
  - `REQ-DAT-032.2`: Android Auto Backup and device transfers strictly exclude `auth_credentials_prefs.xml`.
  - `REQ-DAT-032.3`: `.attbackup` archives strictly omit `auth_credentials_prefs.xml` and purged default preferences containing legacy keys.
  - `REQ-DAT-032.4`: Restoring an archive sanitizes default preferences and resets credentials to require clean re-authentication.
  - Clean-room test suite pass rate remains 100% across all modules.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Dual-Key Mapping & Normalization in `AuthCredentialsStore.kt`
We define a canonical legacy key mapping table in `AuthCredentialsStore`:
```kotlin
// Legacy keys from TrainingApplication.java
const val LEGACY_KEY_DROPBOX_CREDENTIAL = "dropboxCredential"
const val LEGACY_KEY_GOOGLE_DRIVE_ACCOUNT_EMAIL = "googleDriveAccountEmail"
const val LEGACY_KEY_STRAVA_OAUTH_STATE = "SP_STRAVA_OAUTH_STATE"
const val LEGACY_KEY_STRAVA_OAUTH_STATE_TIMESTAMP = "SP_STRAVA_OAUTH_STATE_TIMESTAMP"

val LEGACY_KEY_MIGRATION_MAP = mapOf(
    LEGACY_KEY_DROPBOX_CREDENTIAL to KEY_DROPBOX_CREDENTIAL,
    LEGACY_KEY_GOOGLE_DRIVE_ACCOUNT_EMAIL to KEY_GOOGLE_DRIVE_ACCOUNT_EMAIL,
    LEGACY_KEY_STRAVA_OAUTH_STATE to KEY_STRAVA_OAUTH_STATE,
    LEGACY_KEY_STRAVA_OAUTH_STATE_TIMESTAMP to KEY_STRAVA_OAUTH_STATE_TIMESTAMP
)

val ALL_CREDENTIAL_KEYS_TO_SANITIZE = ALL_CREDENTIAL_KEYS + LEGACY_KEY_MIGRATION_MAP.keys
```

1. **`migrateLegacyCredentials(context)`**:
   - Inspects `defaultPrefs` for any key in `ALL_CREDENTIAL_KEYS_TO_SANITIZE`.
   - For legacy keys, maps value to target canonical key in `credPrefs` (e.g. `"dropboxCredential"` $\rightarrow$ `KEY_DROPBOX_CREDENTIAL`).
   - For direct keys (`ALL_CREDENTIAL_KEYS`), copies directly to `credPrefs`.
   - Removes all detected keys from `defaultPrefs` in an atomic commit.
2. **`readDropboxCredential(context)`**:
   - Reads `KEY_DROPBOX_CREDENTIAL` (`"sp_dropbox_credential"`).
   - If null or blank, checks fallback `LEGACY_KEY_DROPBOX_CREDENTIAL` (`"dropboxCredential"`) in `credPrefs`.
3. **`sanitizeAfterRestore(context)` & `sanitizeStagedPreferencesDirectory(stagingPrefsDir)`**:
   - Iterate over `ALL_CREDENTIAL_KEYS_TO_SANITIZE`, guaranteeing that neither legacy nor new keys linger in default preferences or staging XML files.

### 5.2 Worker Gating in `BackupWorker.kt`
Update line 39 to:
```kotlin
val dropboxConnected = TrainingApplication.uploadToDropbox() && TrainingApplication.readDropboxCredential() != null
```
If the user has toggled Dropbox backup on but credentials do not exist (or have been revoked), `BackupWorker` skips Dropbox upload rather than executing a failing call and marking the entire backup `PARTIAL_SUCCESS`.

### 5.3 Active Self-Healing in `MainActivityWithNavigation.kt`
In `checkPreferences()`:
```kotlin
if (TrainingApplication.uploadToDropbox() && TrainingApplication.readDropboxCredential() == null) {
    Log.i(TAG, "Dropbox upload enabled but credentials missing; resetting uploadToDropbox to false")
    TrainingApplication.setUploadToDropbox(false)
}
```
Because `TrainingApplication.onCreate()` executes `AuthCredentialsStore.migrateLegacyCredentials(this)` before `MainActivityWithNavigation.onResume()` calls `checkPreferences()`, legitimate legacy credentials are migrated first. If no valid credentials exist after migration, the flag is cleanly reset to `false`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. **Zero Credential Leakage**: No plaintext OAuth tokens or Dropbox JSON credentials reside in default `SharedPreferences` or enter `.attbackup` zip archives.
  2. **Backward Compatibility**: Existing athletes upgrading from earlier app versions have their Dropbox and Google Drive credentials transparently migrated without re-login.
  3. **Reliable Automated Backups**: `BackupWorker` records `SUCCESS` when all configured & authenticated cloud destinations succeed, and never enters `PARTIAL_SUCCESS` due to missing credentials.
  4. **Clean-Room Test Suite Pass Rate**: 100% pass rate across all project unit tests (`testDebugUnitTest`).
* **Risk Rating**: **LOW**
  - Justification: The changes are strictly confined to SharedPreferences key mapping, defensive null checking in `BackupWorker`, and self-healing preference cleanup in `MainActivityWithNavigation`. No SQLite schema changes, no network protocol alterations, and zero breaking API changes.
