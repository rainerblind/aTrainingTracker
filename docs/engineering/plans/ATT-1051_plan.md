# ASPICE Stage 3: Technical Implementation Plan (ATT-1051)

## 1. Goal Description
Eliminate the full-screen fragment replacement outlier for Dropbox settings by replacing `CloudUploadFragment` with a standardized modal bottom sheet (`DropboxSettingsDialog` / `DropboxSettingsDialogFragment` backed by `AppModalBottomSheet` and `AppDialogActions.SaveCancel`) per `REQ-UI-152` and `TST-UI-105`. Update `MainActivityWithNavigation.kt` to launch `DropboxSettingsDialogFragment` directly from the navigation drawer and preference screen callbacks, preserving all authentication and automated backup mechanics.

---

## 2. Proposed Changes

### Component 1: Dropbox Settings Modal Bottom Sheet
#### [NEW] [DropboxSettingsDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/dropbox/DropboxSettingsDialog.kt)
- Create `@Composable fun DropboxSettingsDialog(onDismiss: () -> Unit)` utilizing `AppModalBottomSheet`.
- **Header**:
  - `title = stringResource(R.string.Dropbox)`
  - `iconPainter = painterResource(id = R.drawable.dropbox_logo_blue)`
  - `iconTint = Color.Unspecified`
  - `onDismissRequest = onDismiss`
  - `actions = { AppDialogActions.SaveCancel(...) }`
- **Connection Section**:
  - Displays connection state badge (Connected in green `@string/dropbox_connected_status` vs. Disconnected in error `@string/dropbox_disconnected_status`).
  - When disconnected: Dropbox-blue button with logo (`@string/dropbox_connect`), invoking `Auth.startOAuth2PKCE(activity, BuildConfig.DROPBOX_APP_KEY, DbxRequestConfig(BuildConfig.DROPBOX_APP_KEY))`.
  - When connected: Disconnect button (`@string/dropbox_disconnect`), invoking `TrainingApplication.deleteDropboxCredential()` and `TrainingApplication.setUploadToDropbox(false)`.
- **Automated Backups Section**:
  - Switch toggle for `@string/automated_backups` with summary `@string/automated_backups_summary` (staged locally).
  - Interval selector for `@string/backup_interval` displaying localized interval entries (Daily, Every 3 days, Weekly, Monthly) mapped to values ("1", "3", "7", "30"), enabled only when automated backups is enabled.
- **Action Bar (`AppDialogActions.SaveCancel`)**:
  - `onCancel = onDismiss` (discards staged preference modifications).
  - `onSave = { ... }`:
    - Persists staged `automated_backups` (boolean) to `SharedPreferences`.
    - Persists staged `backup_interval_days` (string) to `SharedPreferences`.
    - Invokes `BackupWorker.schedule(context)` to reschedule periodic WorkManager tasks with the updated interval.
    - Invokes `onDismiss()`.

#### [NEW] [DropboxSettingsDialogFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/dropbox/DropboxSettingsDialogFragment.kt)
- Extends `DialogFragment` with `STYLE_NORMAL, android.R.style.Theme_Translucent_NoTitleBar`.
- Composes `DropboxSettingsDialog` within `ATrainingTrackerTheme`.
- In `onResume()`: Checks `val dbxCredential = Auth.getDbxCredential()`. If non-null, commits the credential via `TrainingApplication.storeDropboxCredential(dbxCredential)` and `TrainingApplication.setUploadToDropbox(true)`.
- Companion object exposes `TAG = "DropboxSettingsDialogFragment"` and `@JvmStatic fun newInstance() = DropboxSettingsDialogFragment()`.

---

### Component 2: Navigation & Routing Integration
#### [MODIFY] [MainActivityWithNavigation.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt)
- In `navigateToDrawerItem` (`R.id.drawer_dropbox`):
  - Close navigation drawer (`mDrawerLayout.closeDrawer(GravityCompat.START)`).
  - Launch `DropboxSettingsDialogFragment.newInstance().show(supportFragmentManager, DropboxSettingsDialogFragment.TAG)`.
  - Return `false` to avoid replacing the active main screen fragment.
- In `onPreferenceStartScreen`:
  - When `key == "cloudUpload"`, show `DropboxSettingsDialogFragment.newInstance().show(supportFragmentManager, DropboxSettingsDialogFragment.TAG)` and return `true`.

---

### Component 3: Test Verification
#### [MODIFY] [ModalBottomSheetDialogsIntegrityTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/ModalBottomSheetDialogsIntegrityTest.kt)
- Add `testDropboxSettingsDialog_existsAndExposesComposableAndFragment()`:
  - Asserts `DropboxSettingsDialogKt` has public composable `DropboxSettingsDialog`.
  - Asserts `DropboxSettingsDialogFragment` extends `androidx.fragment.app.DialogFragment` and provides `newInstance()`.

---

## 3. Invariants & Guardrails
- **Credential Storage Format**: `TrainingApplication.SP_DROPBOX_CREDENTIAL` and `SP_UPLOAD_TO_DROPBOX` keys and format must not change.
- **OAuth PKCE Scheme**: Custom scheme `db-iknmdmr31sf64r0` handling in `AuthActivity` must remain untouched.
- **Preference Keys Parity**: `automated_backups` and `backup_interval_days` must remain strictly identical for `BackupWorker.kt` and `BackupRestoreViewModel.kt`.
- **WorkManager Rescheduling**: `BackupWorker.schedule(context)` must be triggered on save to reschedule work requests.
- **Brand Colors**: Authentic Dropbox brand logo must not be tinted; Material 3 colors and typography must align with design system.

---

## 4. Verification Plan
### Automated Tests
- `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.core.ModalBottomSheetDialogsIntegrityTest"`
- `./gradlew testDebugUnitTest` (full suite clean-room regression check, 32 tasks).

### Physical Device Verification
- `./gradlew installDebug` on Google Pixel 10 (`66020DLCR002FL`).
- Verify opening Dropbox settings from navigation drawer, inspecting UI, toggling backups, changing intervals, saving, and verifying underlying screen stays visible under scrim.
