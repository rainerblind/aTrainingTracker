# ASPICE Stage 3: Technical Implementation Plan (ATT-1048)

## 1. Goal Description
Eliminate the full-screen fragment replacement outlier for Strava settings by replacing `StravaUploadFragment` with a standardized modal bottom sheet (`StravaSettingsDialog` / `StravaSettingsDialogFragment` backed by `AppModalBottomSheet` and `AppDialogActions.SaveCancel`) per `REQ-UI-153` and `TST-UI-106`. Update `MainActivityWithNavigation.kt` to launch `StravaSettingsDialogFragment` directly from the navigation drawer and preference screen callbacks, preserving all authentication, synchronization, and selective upload mechanics.

---

## 2. Proposed Changes

### Component 1: Strava Settings Modal Bottom Sheet
#### [NEW] [StravaSettingsDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/strava/StravaSettingsDialog.kt)
- Create `@Composable fun StravaSettingsDialog(onDismiss: () -> Unit)` utilizing `AppModalBottomSheet`.
- **Header**:
  - `title = stringResource(R.string.Strava)`
  - `iconPainter = painterResource(id = R.drawable.logo_square_strava)`
  - `iconTint = Color.Unspecified`
  - `onDismissRequest = onDismiss`
  - `actions = { AppDialogActions.SaveCancel(...) }`
- **Connection Section**:
  - Collects `authState` from `StravaAuthRepository.getInstance().authState`.
  - When connecting (`authState is StravaAuthState.Loading`): Displays circular progress indicator and `@string/please_wait`.
  - Status badge: Displays `@string/strava_connected_status` in green (`Color(0xFF2E7D32)`) vs. `@string/strava_disconnected_status` in error color.
  - When disconnected: `ConnectWithStravaButton(onClick = { StravaHelper.requestAccessToken(context) })`.
  - When connected: Disconnect button (`@string/strava_disconnect`), invoking `TrainingApplication.deleteStravaToken()`, executing `StravaDeauthorizationThread(activity).start()`, and updating local connection state.
  - On connection success (`authState is StravaAuthState.Success`): Triggers initial synchronizations (`StravaEquipmentSynchronizeThread`, `SegmentsRepository.syncSegmentsAsync`, `RoutesRepository.syncRoutesFromStravaAsync`) and resets state via `StravaAuthRepository.getInstance().resetState()`.
- **Manual Synchronization Actions Section (visible when connected)**:
  - Equipment sync: Displays `@string/updateStravaEquipment` and summary timestamp `TrainingApplication.getLastUpdateTimeOfStravaEquipment()`. Tapping triggers `StravaEquipmentSynchronizeThread(activity).start()`.
  - Route sync: Displays `@string/updateStravaRoutes` and summary timestamp `TrainingApplication.getLastUpdateTimeOfStravaRoutes()`. Tapping triggers `RoutesRepository.getInstance(context).syncRoutesFromStravaAsync()`.
- **Selective Upload Section (visible when connected)**:
  - Header: `@string/selectiveUpload`.
  - Toggles (staged in remember state, defaulting to `true`):
    - `uploadStravaGPS` (`@string/uploadGPS`)
    - `uploadStravaAltitude` (`@string/uploadAltitude`)
    - `uploadStravaHR` (`@string/uploadHR`)
    - `uploadStravaPower` (`@string/uploadPower`)
    - `uploadStravaCadence` (`@string/uploadCadence`)
- **Action Bar (`AppDialogActions.SaveCancel`)**:
  - `onCancel = onDismiss` (discards staged toggle modifications).
  - `onSave = { ... }`:
    - Persists staged selective upload preferences to `SharedPreferences`.
    - Invokes `onDismiss()`.

#### [NEW] [StravaSettingsDialogFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/strava/StravaSettingsDialogFragment.kt)
- Extends `DialogFragment` with `STYLE_NORMAL, android.R.style.Theme_Translucent_NoTitleBar`.
- Composes `StravaSettingsDialog` within `ATrainingTrackerTheme`.
- Companion object exposes `TAG = "StravaSettingsDialogFragment"` and `@JvmStatic fun newInstance() = StravaSettingsDialogFragment()`.

---

### Component 2: Navigation & Routing Integration
#### [MODIFY] [MainActivityWithNavigation.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt)
- In `navigateToDrawerItem` (`R.id.drawer_strava`):
  - Close navigation drawer (`mDrawerLayout.closeDrawer(GravityCompat.START)`).
  - Launch `StravaSettingsDialogFragment.newInstance().show(supportFragmentManager, StravaSettingsDialogFragment.TAG)`.
  - Return `false` to avoid replacing the active main screen fragment.
- In `onPreferenceStartScreen`:
  - When `preferenceScreen.key == TrainingApplication.PREFERENCE_SCREEN_STRAVA`, show `StravaSettingsDialogFragment.newInstance().show(supportFragmentManager, StravaSettingsDialogFragment.TAG)` and return `true`.

---

### Component 3: Test Verification
#### [MODIFY] [ModalBottomSheetDialogsIntegrityTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/ModalBottomSheetDialogsIntegrityTest.kt)
- Add `testStravaSettingsDialog_existsAndExposesComposableAndFragment()`:
  - Asserts `StravaSettingsDialogKt` has public composable `StravaSettingsDialog`.
  - Asserts `StravaSettingsDialogFragment` extends `androidx.fragment.app.DialogFragment` and provides `newInstance()`.

---

## 3. Invariants & Guardrails
- **Strava Token Storage**: `TrainingApplication.SP_STRAVA_TOKEN` and associated OAuth keys/token expiration handling must not change.
- **OAuth Redirect URL**: Custom URL prefix `strava://rainerblind.github.io` handled by `StravaOAuthCallbackActivity` must remain untouched.
- **Preference Keys Parity**: `uploadStravaGPS`, `uploadStravaAltitude`, `uploadStravaHR`, `uploadStravaPower`, `uploadStravaCadence` must remain strictly identical for `StravaUploadThread` and `StravaHelper`.
- **Background Synchronization**: Background threads `StravaEquipmentSynchronizeThread`, `StravaDeauthorizationThread`, and repositories `SegmentsRepository`, `RoutesRepository` must execute identically without regression.
- **Brand Colors**: Authentic Strava brand logo (`R.drawable.logo_square_strava`) must not be tinted; Material 3 colors and typography must align with design system.

---

## 4. Verification Plan
### Automated Tests
- `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.core.ModalBottomSheetDialogsIntegrityTest"`
- `./gradlew testDebugUnitTest --no-daemon` (full suite clean-room regression check, 32 tasks).

### Physical Device Verification
- `./gradlew installDebug` on Google Pixel 10 (`66020DLCR002FL`).
- Verify opening Strava settings from navigation drawer, inspecting UI, toggling selective upload options, saving, and verifying underlying screen stays visible under scrim.
