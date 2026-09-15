# Walkthrough - ATT-1048: Modernize Strava Settings to Bottom Popup (AppModalBottomSheet)

## 1. Executive Summary
Under **ATT-1048**, the legacy full-screen `StravaUploadFragment` outlier was eliminated by converting Strava settings into a standardized `AppModalBottomSheet` (`StravaSettingsDialog.kt`) hosted in `StravaSettingsDialogFragment.kt` (`DialogFragment`). The modal bottom sheet provides seamless one-handed accessibility, retains the authentic Strava logo and branding (`R.drawable.logo_square_strava`), provides immediate connection status and OAuth2 authorization/deauthorization controls (`StravaHelper.requestAccessToken`, `StravaDeauthorizationThread`), manual synchronization triggers for equipment (`StravaEquipmentSynchronizeThread`) and routes (`RoutesRepository.syncRoutesFromStravaAsync`) with real-time timestamp display, selective upload switches (`uploadStravaGPS`, `uploadStravaAltitude`, `uploadStravaHR`, `uploadStravaPower`, `uploadStravaCadence`), and standardizes dialog actions using `AppDialogActions.SaveCancel` ("Abbrechen" / "Speichern"). The navigation drawer and settings preference screen now present this modal bottom sheet directly without replacing the active fragment.

## 2. Changes Implemented

### A. Core UI & Dialog Modernization
* **`StravaSettingsDialog.kt`**:
  * Implemented `@Composable fun StravaSettingsDialog(onDismiss: () -> Unit)` utilizing `AppModalBottomSheet`.
  * Configured header with `iconPainter = painterResource(R.drawable.logo_square_strava)` and `iconTint = Color.Unspecified` to preserve authentic Strava brand colors.
  * Preserved Strava connection status badge, connection action (`@string/connect_to_strava` launching `StravaHelper.requestAccessToken(...)`), and disconnection action (`@string/strava_disconnect` revoking tokens and running `StravaDeauthorizationThread`).
  * Implemented manual synchronization cards for equipment (`@string/updateStravaEquipment`) and routes (`@string/updateStravaRoutes`) with real-time formatted timestamps from `TrainingApplication`.
  * Implemented selective upload configuration switches for GPS (`uploadStravaGPS`), Altitude (`uploadStravaAltitude`), Heart Rate (`uploadStravaHR`), Power (`uploadStravaPower`), and Cadence (`uploadStravaCadence`), all defaulting to `true`.
  * Standardized actions via `AppDialogActions.SaveCancel`: dismissing or tapping "Abbrechen" discards unsaved modifications; tapping "Speichern" commits preferences to `SharedPreferences`.

### B. DialogFragment Bridge & Navigation Integration
* **`StravaSettingsDialogFragment.kt`**:
  * Created `DialogFragment` subclass configured with `setStyle(STYLE_NORMAL, android.R.style.Theme_Translucent_NoTitleBar)`.
  * Hosts `StravaSettingsDialog` and dismisses on sheet close.
* **`MainActivityWithNavigation.kt`**:
  * Updated navigation drawer item `R.id.drawer_strava` to instantiate and show `StravaSettingsDialogFragment.newInstance()`.
  * Updated `onPreferenceStartScreen(TrainingApplication.PREFERENCE_SCREEN_STRAVA)` to launch `StravaSettingsDialogFragment` directly instead of replacing the viewport with the legacy `StravaUploadFragment`.

### C. Automated Testing & Reflection Integrity
* **`ModalBottomSheetDialogsIntegrityTest.kt`**:
  * Added `testStravaSettingsDialog_existsAndExposesComposableAndFragment()` verifying composable signature and `DialogFragment` bridge instantiation.

## 3. Verification & Evidence
* **Clean-Room Unit Test Pass**: Executed `./gradlew testDebugUnitTest --no-daemon` (**BUILD SUCCESSFUL in 1m 45s**, 0 failures, 0 regressions across all 32 actionable tasks).
* **Device Deployment & Verification**: Successfully deployed via `./gradlew installDebug` to physical Google Pixel 10 (`66020DLCR002FL`) and verified clean startup and interaction.
* **Traceability & Requirements**: `REQ-UI-153` and `TST-UI-106` marked as `Verified` in `docs/requirements.md` and `docs/tests.md`.
