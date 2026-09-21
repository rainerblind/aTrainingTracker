# Walkthrough - ATT-1044: Modernize Sensor Search Settings to Bottom Popup (AppModalBottomSheet)

## 1. Executive Summary
Under **ATT-1044**, the legacy full-screen `SearchSettingsFragment` outlier was eliminated by converting sensor search settings into a standardized `AppModalBottomSheet` (`SearchSettingsDialog.kt`) hosted in `SearchSettingsDialogFragment.kt` (`DialogFragment`). The modal bottom sheet provides seamless one-handed accessibility, sensor search retry configuration via an interactive slider (1 to 5 attempts, default 3) with a dynamic numeric badge, four automated search trigger toggles (`startSearchWhenAppStarts`, `startSearchWhenResumeFromPaused`, `startSearchWhenUserChangesSport`, `startSearchWhenTrackingStarts`), and two search behavior switches with descriptive secondary summaries (`searchOnlyForSportSpecificDevices`, `changeSportWhenDeviceGetsLost`). Staging is fully transactional using `AppDialogActions.SaveCancel` ("Abbrechen" / "Speichern"), preserving existing `SharedPreferences` keys and default values consumed across `DeviceManager` and `BANALService`. The navigation drawer and preference screens now display the modal bottom sheet directly without fragment replacement.

## 2. Changes Implemented

### A. Core UI & Dialog Modernization
* **`SearchSettingsDialog.kt`**:
  * Implemented `@Composable fun SearchSettingsDialog(onDismiss: () -> Unit)` utilizing `AppModalBottomSheet`.
  * Configured header with `iconPainter = painterResource(R.drawable.ic_search)` and localized title `@string/search_settings`.
  * Implemented interactive slider (range 1f..5f, steps = 3) for `numberOfSearchTriesInt` with an accompanying badge displaying the selected retry count.
  * Implemented automated search trigger section with toggle switches for:
    * `startSearchWhenAppStarts` (default `true`)
    * `startSearchWhenResumeFromPaused` (default `true`)
    * `startSearchWhenUserChangesSport` (default `true`)
    * `startSearchWhenTrackingStarts` (default `false`)
  * Implemented search behavior section with toggle switches and descriptive secondary text for:
    * `searchOnlyForSportSpecificDevices` (default `true`)
    * `changeSportWhenDeviceGetsLost` (default `true`)
  * Standardized actions via `AppDialogActions.SaveCancel`: dismissing or tapping "Abbrechen" discards modifications; tapping "Speichern" commits changes to `SharedPreferences`.

### B. DialogFragment Bridge & Navigation Integration
* **`SearchSettingsDialogFragment.kt`**:
  * Created `DialogFragment` subclass configured with `setStyle(STYLE_NORMAL, android.R.style.Theme_Translucent_NoTitleBar)`.
  * Hosts `SearchSettingsDialog` and dismisses on sheet close.
* **`MainActivityWithNavigation.kt`**:
  * Updated navigation drawer item `R.id.drawer_search_settings` to instantiate and show `SearchSettingsDialogFragment.newInstance()`.
  * Updated `onPreferenceStartScreen("search_settings")` to launch `SearchSettingsDialogFragment` directly instead of replacing the viewport with the legacy `SearchSettingsFragment`.

### C. Automated Testing & Reflection Integrity
* **`ModalBottomSheetDialogsIntegrityTest.kt`**:
  * Added `testSearchSettingsDialog_existsAndExposesComposableAndFragment()` verifying composable signature and `DialogFragment` bridge instantiation.

## 3. Verification & Evidence
* **Clean-Room Unit Test Pass**: Executed `./gradlew testDebugUnitTest --no-daemon` (**BUILD SUCCESSFUL in 1m 40s**, 0 failures, 0 regressions across all 32 actionable tasks).
* **Device Deployment & Verification**: Successfully deployed via `./gradlew installDebug` to physical Google Pixel 10 (`66020DLCR002FL`) and verified clean startup and interaction.
* **Traceability & Requirements**: `REQ-UI-154` and `TST-UI-107` marked as `Verified` in `docs/requirements.md` and `docs/tests.md`.
