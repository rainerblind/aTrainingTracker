# Stage 2: Requirement & Test Specification - ATT-4666: Unify DropboxSettingsDialog and GoogleDriveSettingsDialog with shared layout, granular toggles, backup interval, and last backup/sync status

**Ticket**: [ATT-4666](https://rainerblind.atlassian.net/browse/ATT-4666)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.7` | **Branch**: `feature/ATT-4666`  
**Requirement Mapping**: `REQ-UI-430` (*Multi-Provider Cloud Settings UI Harmonization*) | **Test Spec ID**: `TST-UI-395`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Requirement Specification (REQ-UI-430)

### 1.1 Problem Statement & Rationale
`DropboxSettingsDialog` and `GoogleDriveSettingsDialog` present asymmetric UX and configuration granularity: Dropbox exposes only global automated backups and interval even when disconnected, lacking workout upload, Wi-Fi only, email, and sync/backup timestamps. Google Drive lacks backup interval selection and backup timestamps. Extracting shared Compose components unifies UX symmetry (Rule 62) and eliminates code duplication (Rule 36).

### 1.2 Functional & Architectural Requirements
* **REQ-UI-430.1 (Shared Layout & Header)**: Extract parameterized `CloudConnectionHeader.kt` and `CloudProviderSettingsContent.kt` in `ui.settings.cloud`. Delegate existing `DropboxConnectionHeader` and `GoogleDriveConnectionHeader` to `CloudConnectionHeader`, preserving `ComposeSafePainterContractTest.kt`.
* **REQ-UI-430.2 (Disconnected State Symmetry)**: When disconnected, display connection header + localized connect prompt (`dropbox_connect_prompt` / `google_drive_connect_prompt`), hiding all switches until authenticated.
* **REQ-UI-430.3 (Connected Granularity Symmetry)**: When connected, display account email, `Upload Workouts` switch, `Upload Backups` switch, conditional `Backup Interval` dropdown (`backup_interval_days`) when `Upload Backups` is true, `Wi-Fi Only` switch, and `Last Sync` / `Last Backup` timestamp footers.
* **REQ-UI-430.4 (Transactional Save & WorkManager)**: Save commits staged preferences and interval to `TrainingApplication` / `SharedPreferences` and reschedules `BackupWorker.schedule(context)`. Dismiss discards edits.
* **REQ-UI-430.5 (9-Language Parity)**: Localize `dropbox_connect_prompt`, `upload_workouts_to_dropbox_summary`, `upload_backup_to_dropbox_summary`, `last_backup_never` across all 9 locales.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Disconnected Prompt)**: *Given* disconnected state, *When* opening either cloud dialog, *Then* connection header and connect prompt SHALL show; all config switches SHALL be hidden.
* **Criterion 2 (Connected Symmetry)**: *Given* connected state, *When* opening either cloud dialog, *Then* account email, workout upload, backup upload, Wi-Fi only, and sync/backup timestamps SHALL show.
* **Criterion 3 (Conditional Backup Interval)**: *Given* connected state, *When* `Upload Backups` is enabled in either dialog, *Then* `Backup Interval` dropdown SHALL show and commit on save.

### 1.4 System Invariants
OAuth PKCE scheme (`db-iknmdmr31sf64r0`), Google Sign-In `drive.file` scope, credential isolation in `auth_credentials_prefs.xml`, `safePainterResource` contract in `ComposeSafePainterContractTest.kt`, and 100% clean-room test pass rate MUST NOT be broken.

---

## 2. Test Specification (TST-UI-395)

* **TST-UI-395.1 (Dropbox Dialog State & Switches Unit Test)**: Verify in `DropboxSettingsDialogTest.kt` that disconnected state hides switches and shows prompt; connected state displays email, switches, interval, and timestamps; save commits preferences and calls `BackupWorker.schedule`.
* **TST-UI-395.2 (Google Drive Backup Interval & Parity Unit Test)**: Verify in `GoogleDriveSettingsDialogTest.kt` that backup interval is visible when backup is enabled; save commits `backup_interval_days` and reschedules `BackupWorker`; last backup timestamp displays.
* **TST-UI-395.3 (Shared Cloud Component Contract Test)**: Verify `ComposeSafePainterContractTest.kt` passes without modification; verify `CloudConnectionHeader` and `CloudProviderSettingsContent` handle connected/disconnected states cleanly.
* **TST-UI-395.4 (9-Language Localization Parity)**: Run `TranslationParityTest.kt` verifying 100% string parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
* **TST-UI-395.5 (Clean-Room Full Suite Regression)**: Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-395.1` | Unit / Robolectric | `DropboxSettingsDialog` | `REQ-UI-430.1..4` | Specified |
| `TST-UI-395.2` | Unit / Robolectric | `GoogleDriveSettingsDialog` | `REQ-UI-430.3..4` | Specified |
| `TST-UI-395.3` | Architectural Contract | `CloudConnectionHeader` | `REQ-UI-430.1`, `REQ-UI-380` | Specified |
| `TST-UI-395.4` | Localization | `TranslationParityTest` | `REQ-UI-430.5`, `REQ-LOC-001` | Specified |
| `TST-UI-395.5` | Full Suite Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
