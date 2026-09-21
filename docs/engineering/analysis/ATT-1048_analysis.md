# ASPICE Stage 1: Analysis & Problem Domain Formal Audit (ATT-1048)

## 1. Problem Domain & Motivation
The application currently handles Strava online community settings (`R.id.drawer_strava`) by replacing the active main fragment in `MainActivityWithNavigation.kt` with a full-screen `StravaUploadFragment`. This fragment stitches together a Compose header (`ComposeView` running `StravaConnectionHeader`) with legacy XML preferences (`R.xml.prefs_strava` hosted in `PreferenceFragmentCompat`).

This legacy architecture exhibits several issues:
1. **Design System Inconsistency**: In the navigation drawer, settings items have been modernized per `REQ-UI-149`, `REQ-UI-150`, and `REQ-UI-152` into modal bottom sheets:
   - Export Settings: `ExportSettingsDialogFragment` (`AppModalBottomSheet`)
   - Units Settings: `UnitsSettingsDialogFragment` (`AppModalBottomSheet`)
   - Display Settings: `DisplaySettingsDialogFragment` (`AppModalBottomSheet`)
   - Dropbox Settings: `DropboxSettingsDialogFragment` (`AppModalBottomSheet`)
   In contrast, Strava Settings remains the final service settings outlier that replaces the main screen view hierarchy, destroying background context and causing navigation disruption.
2. **Hybrid Preference Architecture Debt**: `StravaUploadFragment` mixes a Compose view for OAuth connection controls with AndroidX XML preference views (`Preference`, `CheckBoxPreference`), resulting in inconsistent theme styling, dual view trees, and rigid layout constraints.
3. **Ergonomic & Transactional Deficiencies**: Preference changes in `StravaUploadFragment` immediately mutate SharedPreferences without explicit user confirmation or transactional staging (`AppDialogActions.SaveCancel`).

---

## 2. Root Cause Analysis
- `StravaUploadFragment` was originally built as an Android PreferenceFragment in early versions of the app.
- When Strava OAuth and connection controls were modernized with Compose (`StravaConnectionHeader`), the Compose header was grafted onto the preference fragment rather than refactoring the entire screen into a standardized `AppModalBottomSheet`.
- `MainActivityWithNavigation.kt` routes `R.id.drawer_strava` and `onPreferenceStartScreen(TrainingApplication.PREFERENCE_SCREEN_STRAVA)` to fragment transaction replacement rather than launching a modal `DialogFragment`.

---

## 3. Target Architecture & Scope Boundaries
### 3.1 Composable Dialog Architecture (`StravaSettingsDialog.kt`)
- Backed by `AppModalBottomSheet` with drag handle, close button, and window insets (`navigationBarsPadding()`, `imePadding()`).
- **Header**:
  - Icon: Authentic Strava logo (`R.drawable.logo_square_strava` via `iconPainter`, `iconTint = Color.Unspecified`).
  - Title: Localized title (`@string/Strava`).
- **Connection Section**:
  - Connection status badge:
    - Connected: Green text (`@string/strava_connected_status`).
    - Disconnected: Error text (`@string/strava_disconnected_status`).
  - Action Button & States:
    - Connecting (`authState is StravaAuthState.Loading`): Circular progress indicator and `@string/please_wait`.
    - Disconnected: Connect button (`ConnectWithStravaButton` / button) initiating `StravaHelper.requestAccessToken(context)`.
    - Connected: ErrorContainer-styled button (`@string/strava_disconnect`), clearing token via `TrainingApplication.deleteStravaToken()`, executing `StravaDeauthorizationThread(activity).start()`, and updating connection state.
    - Connection Success (`authState is StravaAuthState.Success`): Triggers synchronization of equipment (`StravaEquipmentSynchronizeThread`), segments (`SegmentsRepository.syncSegmentsAsync`), and routes (`RoutesRepository.syncRoutesFromStravaAsync`), and resets state via `StravaAuthRepository.getInstance().resetState()`.
- **Synchronization Actions Section (visible when connected)**:
  - Equipment Sync: Displays `@string/updateStravaEquipment` and summary timestamp `TrainingApplication.getLastUpdateTimeOfStravaEquipment()`. Tapping triggers `StravaEquipmentSynchronizeThread(activity).start()`.
  - Route Sync: Displays `@string/updateStravaRoutes` and summary timestamp `TrainingApplication.getLastUpdateTimeOfStravaRoutes()`. Tapping triggers `RoutesRepository.getInstance(context).syncRoutesFromStravaAsync()`.
- **Selective Upload Section (visible when connected)**:
  - Category Title: `@string/selectiveUpload`.
  - Toggles (defaults to `true`):
    - `uploadStravaGPS` (`@string/uploadGPS`)
    - `uploadStravaAltitude` (`@string/uploadAltitude`)
    - `uploadStravaHR` (`@string/uploadHR`)
    - `uploadStravaPower` (`@string/uploadPower`)
    - `uploadStravaCadence` (`@string/uploadCadence`)
- **Action Bar**:
  - Follows `AppDialogActions.SaveCancel` ("Abbrechen" / `@string/Cancel` and "Speichern" / `@string/save`).
  - `onSave`: Commits staged selective upload preferences to `SharedPreferences` and dismisses dialog.
  - `onCancel`: Discards uncommitted preference changes and dismisses dialog.

### 3.2 DialogFragment Host (`StravaSettingsDialogFragment.kt`)
- Extends `DialogFragment` with `STYLE_NORMAL, android.R.style.Theme_Translucent_NoTitleBar`.
- Composes `StravaSettingsDialog` within `ATrainingTrackerTheme`.

### 3.3 Navigation Integration (`MainActivityWithNavigation.kt`)
- `R.id.drawer_strava`: Closes drawer and displays `StravaSettingsDialogFragment.newInstance().show(supportFragmentManager, StravaSettingsDialogFragment.TAG)`.
- `onPreferenceStartScreen(TrainingApplication.PREFERENCE_SCREEN_STRAVA)`: Shows `StravaSettingsDialogFragment`.

---

## 4. Impacted Files & Components
- **New UI Components**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/strava/StravaSettingsDialog.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/strava/StravaSettingsDialogFragment.kt`
- **Navigation & Routing**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt`
- **Testing & Verification**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/ModalBottomSheetDialogsIntegrityTest.kt`
- **Documentation**:
  - `docs/requirements.md` (new `REQ-UI-153`)
  - `docs/tests.md` (new `TST-UI-106`)

---

## 5. Invariant Checklist
- [x] **Strava Token Persistence**: `SP_STRAVA_TOKEN` and associated OAuth keys/token expiration handling must remain 100% backward compatible.
- [x] **OAuth Callback Redirect Scheme**: Callback URL prefix `strava://rainerblind.github.io` handled by `StravaOAuthCallbackActivity` must seamlessly update `StravaAuthRepository`.
- [x] **Selective Upload Keys**: SharedPreferences keys `uploadStravaGPS`, `uploadStravaAltitude`, `uploadStravaHR`, `uploadStravaPower`, `uploadStravaCadence` (boolean, default true) must remain strictly identical for `StravaUploadThread` / `StravaHelper`.
- [x] **Synchronization Services**: Synchronizing equipment, segments, and routes must continue to use `StravaEquipmentSynchronizeThread`, `SegmentsRepository.syncSegmentsAsync`, and `RoutesRepository.syncRoutesFromStravaAsync`.
- [x] **Theme & Visual Guidelines**: Authentic Strava brand logo (`R.drawable.logo_square_strava`) must not be tinted; Material 3 typography and colors must align with `DropboxSettingsDialog` and other bottom sheets.
