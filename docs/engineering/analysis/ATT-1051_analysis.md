# ASPICE Stage 1: Analysis & Problem Domain Formal Audit (ATT-1051)

## 1. Problem Domain & Motivation
The application currently handles Dropbox cloud settings (`R.id.drawer_dropbox`) by replacing the active main fragment in `MainActivityWithNavigation.kt` with a full-screen `CloudUploadFragment`. This fragment combines a Compose header (`DropboxConnectionHeader`) with legacy XML preferences (`R.xml.prefs_dropbox` hosted in `PreferenceFragmentCompat`).

This legacy architecture exhibits several issues:
1. **Design System Inconsistency**: In the navigation drawer, settings items have been modernized per `REQ-UI-149` and `REQ-UI-150` into modal bottom sheets:
   - Export Settings: `ExportSettingsDialogFragment` (`AppModalBottomSheet`)
   - Units Settings: `UnitsSettingsDialogFragment` (`AppModalBottomSheet`)
   - Display Settings: `DisplaySettingsDialogFragment` (`AppModalBottomSheet`)
   In contrast, Dropbox Settings remains an outlier that replaces the main screen view hierarchy, destroying background context and causing navigation disruption.
2. **Hybrid Preference Architecture Debt**: `CloudUploadFragment` stitches together a Compose view for OAuth connection controls with AndroidX XML preference views (`SwitchPreferenceCompat`, `ListPreference`), resulting in inconsistent theme styling, dual view trees, and rigid layout constraints.
3. **Ergonomic & Transactional Deficiencies**: Preference changes in `CloudUploadFragment` immediately mutate SharedPreferences without explicit user confirmation or transactional staging (`AppDialogActions.SaveCancel`).

---

## 2. Root Cause Analysis
- `CloudUploadFragment` was originally built as an Android PreferenceFragment in early versions of the app.
- During the introduction of Dropbox OAuth PKCE (`SCRUM-244`), a Compose header was grafted onto the preference fragment rather than refactoring the entire screen into an `AppModalBottomSheet`.
- `MainActivityWithNavigation.kt` still routes `R.id.drawer_dropbox` to fragment transaction replacement rather than launching a modal `DialogFragment`.

---

## 3. Target Architecture & Scope Boundaries
### 3.1 Composable Dialog Architecture (`DropboxSettingsDialog.kt`)
- Backed by `AppModalBottomSheet` with drag handle, close button, and window insets (`navigationBarsPadding()`, `imePadding()`).
- **Header**:
  - Icon: Authentic Dropbox logo (`R.drawable.dropbox_logo_blue` via `iconPainter`, `iconTint = Color.Unspecified`).
  - Title: Localized title (`@string/Dropbox`).
- **Connection Section**:
  - Connection status badge:
    - Connected: Green text (`@string/dropbox_connected_status`) with check indicator.
    - Disconnected: Error text (`@string/dropbox_disconnected_status`).
  - Action Button:
    - Disconnected: Dropbox-blue button with logo (`@string/dropbox_connect`), initiating `Auth.startOAuth2PKCE(activity, BuildConfig.DROPBOX_APP_KEY, DbxRequestConfig(BuildConfig.DROPBOX_APP_KEY))`.
    - Connected: ErrorContainer-styled button (`@string/dropbox_disconnect`), revoking credentials via `TrainingApplication.deleteDropboxCredential()` and `TrainingApplication.setUploadToDropbox(false)`.
- **Automated Backups Section**:
  - Toggle: `@string/automated_backups` with summary `@string/automated_backups_summary` (`key = "automated_backups"`, default `true`).
  - Interval Selector: `@string/backup_interval` (Daily, Every 3 days, Weekly, Monthly from `@array/backup_interval_entries` / `@array/backup_interval_values`, default `"1"`), conditionally enabled when automated backups are enabled.
- **Action Bar**:
  - Follows `AppDialogActions.SaveCancel` ("Abbrechen" / `@string/Cancel` and "Speichern" / `@string/save`).
  - `onSave`: Commits staged `automated_backups` and `backup_interval_days` to `SharedPreferences`, triggers `BackupWorker.schedule(context)`, and dismisses dialog.
  - `onCancel`: Discards uncommitted preference changes and dismisses dialog.

### 3.2 DialogFragment Host (`DropboxSettingsDialogFragment.kt`)
- Extends `DialogFragment` with `STYLE_NORMAL, android.R.style.Theme_Translucent_NoTitleBar`.
- Composes `DropboxSettingsDialog` within `ATrainingTrackerTheme`.
- In `onResume()`: Inspects `Auth.getDbxCredential()`. If a new credential is provided following the OAuth browser redirect, securely stores it via `TrainingApplication.storeDropboxCredential()` and sets `TrainingApplication.setUploadToDropbox(true)`.

### 3.3 Navigation Integration (`MainActivityWithNavigation.kt`)
- `R.id.drawer_dropbox`: Closes drawer and displays `DropboxSettingsDialogFragment.newInstance().show(supportFragmentManager, DropboxSettingsDialogFragment.TAG)`.
- `onPreferenceStartScreen` ("cloudUpload"): Shows `DropboxSettingsDialogFragment`.

---

## 4. Impacted Files & Components
- **New UI Components**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/dropbox/DropboxSettingsDialog.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/dropbox/DropboxSettingsDialogFragment.kt`
- **Navigation & Routing**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt`
- **Testing & Verification**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/ModalBottomSheetDialogsIntegrityTest.kt`
- **Documentation**:
  - `docs/requirements.md` (new `REQ-UI-152`)
  - `docs/tests.md` (new `TST-UI-105`)

---

## 5. Invariant Checklist
- [x] **Dropbox Credential Persistence**: `SP_DROPBOX_CREDENTIAL` and `SP_UPLOAD_TO_DROPBOX` keys and format must remain 100% backward compatible.
- [x] **OAuth PKCE Compatibility**: Custom scheme redirect `db-iknmdmr31sf64r0` handling in `AuthActivity` must seamlessly return credentials to `DropboxSettingsDialogFragment` / `DropboxSettingsDialog`.
- [x] **Backup Settings Keys**: SharedPreferences keys `"automated_backups"` (boolean) and `"backup_interval_days"` (string) must remain strictly identical for `BackupWorker.kt` and `BackupRestoreViewModel.kt`.
- [x] **Worker Rescheduling**: Modifying backup interval or toggling automated backups on save must trigger `BackupWorker.schedule(context)` to update periodic WorkManager requests.
- [x] **Theme & Visual Guidelines**: Authentic Dropbox brand logo must not be tinted; Material 3 colors and typography must align with `ExportSettingsDialog` and `DisplaySettingsDialog`.
