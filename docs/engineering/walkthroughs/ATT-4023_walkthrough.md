# Stage 5: Walkthrough & Verification - ATT-4023: Exclude OAuth credentials from Auto Backup and .attbackup archives

**Ticket**: [ATT-4023](https://rainerblind.atlassian.net/browse/ATT-4023)  
**Sub-task**: [ATT-4260](https://rainerblind.atlassian.net/browse/ATT-4260) (`[Test]`)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.10`  
**Requirement Mapping**: `REQ-DAT-032` (*OAuth Credential Isolation, Android Auto Backup & Device Transfer Rules, and .attbackup Credential Exclusion*)  
**Test Mapping**: `TST-DAT-027` (*OAuth Credential Isolation, Android Auto Backup & Device Transfer Rules, and .attbackup Credential Exclusion Verification Suite*)  
**Branch**: `feature/ATT-4023`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Executive Summary & Verification Overview

During an architectural security and data sovereignty audit, critical credential leakage vectors and FileProvider vulnerabilities were identified:

1. **OAuth Credential Colocation in Default SharedPreferences (`REQ-DAT-032.1`)**:
   - OAuth access and refresh tokens for Strava, Google Drive account email and auth tokens, and Dropbox credentials were stored in default `SharedPreferences` alongside user settings (e.g. `app_user_theme`, `preferred_units`).
   - Android Auto Backup and device-to-device transfer lacked XML exclusion rules, allowing sensitive third-party tokens to be uploaded to Google Drive cloud backups and transferred to secondary devices without user authorization.

2. **Portable Backup Archive Credential Leakage (`REQ-DAT-032.3`)**:
   - `.attbackup` archives bundled the entire `shared_prefs/` directory indiscriminately. Any backup exported by the user contained live third-party API credentials, exposing persistent tokens if shared, moved to external storage, or stored insecurely.
   - Restoring a backup archive copied tokens directly into active storage without requiring fresh authentication.

3. **Insecure / Dead FileProvider Exposure (`REQ-DAT-032.4`)**:
   - `GenericFileProvider.java` was an obsolete, unreferenced subclass of `androidx.core.content.FileProvider`.
   - `provider_paths.xml` declared `<external-path name="external_files" path="." />`, which exposed the entire external storage root.
   - `file_paths.xml` contained an overly broad root cache mapping `<cache-path name="backups" path="." />`.

### Complete Resolution Architecture

- **Dedicated Isolated Storage & Transparent Migration (`AuthCredentialsStore.kt`, `REQ-DAT-032.1`)**:
  - Encapsulated all OAuth and third-party API credentials into a private preferences file: `auth_credentials_prefs.xml` (`Context.MODE_PRIVATE`).
  - Implemented `AuthCredentialsStore.migrateLegacyCredentials(context)` invoked on application startup (`TrainingApplication.onCreate()`) to automatically and transparently migrate legacy credentials out of default `SharedPreferences` and immediately purge them from default preferences.
  - Implemented strongly typed getters, setters, and deletion helpers for Strava, Google Drive, and Dropbox credentials with atomic disk flushing (`commit()`).
  - Updated `TrainingApplication.java` and `StravaOAuthStateManager.kt` to route all credential persistence through `AuthCredentialsStore`.

- **Android Auto Backup & Device Transfer Exclusion Rules (`REQ-DAT-032.2`)**:
  - Created `app/src/main/res/xml/data_extraction_rules.xml` (API 31+ Android 12+) with `<cloud-backup>` and `<device-transfer>` exclusion rules for `auth_credentials_prefs.xml`.
  - Created `app/src/main/res/xml/full_backup_content.xml` (API 23-30) with an `<exclude domain="sharedpref" path="auth_credentials_prefs.xml" />` rule.
  - Configured `AndroidManifest.xml` with `android:allowBackup="true"`, `android:dataExtractionRules="@xml/data_extraction_rules"`, and `android:fullBackupContent="@xml/full_backup_content"`.

- **Backup Archive Exclusion & Clean Re-Authentication Invariant (`REQ-DAT-032.3`)**:
  - `BackupManager.kt`: Executes `AuthCredentialsStore.migrateLegacyCredentials(context)` before bundling, ensuring legacy keys are removed from default preferences. Filters out `auth_credentials_prefs.xml` and temporary backup variants from the zip archive.
  - `MigrationEngine.kt`: Unpacks archives and executes `AuthCredentialsStore.sanitizeStagedPreferencesDirectory(restoredPrefs)`, deleting any credentials file and stripping legacy credential lines from XML files before the atomic directory swap.
  - Enforces post-restore sanitization via `AuthCredentialsStore.sanitizeAfterRestore(context)`, stripping legacy keys from default preferences and clearing `auth_credentials_prefs` to guarantee a clean re-authentication requirement upon device migration or archive restoration.

- **FileProvider Hardening (`REQ-DAT-032.4`)**:
  - Deleted dead legacy class `GenericFileProvider.java` and `provider_paths.xml`.
  - Removed `GenericFileProvider` declaration from `AndroidManifest.xml`.
  - Hardened `file_paths.xml` by eliminating the overly broad `<cache-path name="backups" path="." />` root exposure.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-DAT-032.1` (Dedicated Store & Legacy Migration) | `TST-DAT-027.1`, `TST-DAT-027.2` | `AuthCredentialsStoreTest.kt` | **PASSED** (100%) | `Verified` |
| `REQ-DAT-032.2` (Auto Backup XML Rules) | `TST-DAT-027.3` | `BackupRulesXmlValidationTest.kt` | **PASSED** (100%) | `Verified` |
| `REQ-DAT-032.3` (Archive Exclusion & Restore Sanitization) | `TST-DAT-027.4`, `TST-DAT-027.5` | `BackupManagerCredentialExclusionTest.kt`, `MigrationEngineCredentialSanitizationTest.kt` | **PASSED** (100%) | `Verified` |
| `REQ-DAT-032.4` (FileProvider Hardening) | `TST-DAT-027.6` | `FileProviderHardeningContractTest.kt` | **PASSED** (100%) | `Verified` |
| `REQ-PRO-001` (Clean-Room Full Test Suite) | `TST-DAT-027.7` | `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit Test Suites (100% Passed)
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.security.*" \
    --tests "com.atrainingtracker.trainingtracker.migration.BackupManagerCredentialExclusionTest" \
    --tests "com.atrainingtracker.trainingtracker.migration.MigrationEngineCredentialSanitizationTest" \
    --tests "com.atrainingtracker.trainingtracker.migration.DropboxCredentialSafetyTest" \
    --tests "com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaOAuthStateManagerTest"
```
- `AuthCredentialsStoreTest` (5 tests): Verified transparent legacy migration from default preferences, complete credential purging from default preferences, provider accessors/mutators for Strava/Google Drive/Dropbox, `clearAllCredentials()`, and `sanitizeAfterRestore()`.
- `BackupRulesXmlValidationTest` (3 tests): Verified XML DOM schema and attribute values in `data_extraction_rules.xml` and `full_backup_content.xml`, and verified manifest linkage.
- `BackupManagerCredentialExclusionTest` (1 test): Verified `.attbackup` archive zip entries exclude `auth_credentials_prefs.xml` and that legacy keys are purged from default preferences before bundling.
- `MigrationEngineCredentialSanitizationTest` (2 tests): Verified staging directory sanitization (file deletion and XML tag removal) and clean re-authentication enforcement after full restore.
- `FileProviderHardeningContractTest` (4 tests): Verified deletion of `GenericFileProvider.java` and `provider_paths.xml`, removal from manifest, and absence of root `.` paths in `file_paths.xml`.
- `DropboxCredentialSafetyTest` (5 tests): Verified null safety, malformed JSON handling, and token reading.
- `StravaOAuthStateManagerTest` (7 tests): Verified state parameter persistence and constant-time validation.

### Clean-Room Regression Test Suite (100% Passed)
```bash
./gradlew testDebugUnitTest
```
- Total Tests: **3,421 completed**
- Failures: **0**
- Errors: **0**
- Result: **BUILD SUCCESSFUL** with 100% pass rate across the entire test suite.

---

## 4. UI Consistency & Visual Verification (Rule 23)

- **UI Consistency**: **N/A**
  - This change modifies internal storage paths, backup XML rules, archive zip filtering, and FileProvider manifest configurations.
  - Zero UI composables, layouts, styles, themes, or user-facing strings were added or modified.

---

## 5. Invariant & Governance Verification

1. **Clean Re-Authentication Invariant**: When an athlete migrates devices or restores a backup, workout databases and settings are fully restored, while cloud integrations safely and cleanly require a one-time re-authentication.
2. **Zero Plaintext Tokens in Backups**: Android Auto Backup, device-to-device transfers, and `.attbackup` archives contain zero OAuth or API tokens.
3. **Attack Surface Reduction**: Unused `GenericFileProvider` and root directory exposures in `provider_paths.xml` and `file_paths.xml` have been excised.
4. **Living Documentation Synchronized**: Status for `REQ-DAT-032` and `TST-DAT-027` updated to `Verified` in `docs/requirements.md` and `docs/tests.md`.
5. **Single Atomic Commit per Ticket (Rule 44)**: All deliverables will be integrated into `sprint/2026-42.10` via a single atomic commit.
