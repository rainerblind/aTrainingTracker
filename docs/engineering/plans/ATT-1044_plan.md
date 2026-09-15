# ASPICE Stage 3: Technical Implementation Plan (ATT-1044)

## 1. Goal Description
Eliminate the full-screen fragment replacement outlier for sensor search settings by replacing `SearchSettingsFragment` with a standardized modal bottom sheet (`SearchSettingsDialog` / `SearchSettingsDialogFragment` backed by `AppModalBottomSheet` and `AppDialogActions.SaveCancel`) per `REQ-UI-154` and `TST-UI-107`. Update `MainActivityWithNavigation.kt` to launch `SearchSettingsDialogFragment` directly from the navigation drawer and preference screen callbacks, preserving all search behaviors, trigger events, retry counts, and getter contracts in `TrainingApplication`.

---

## 2. Proposed Changes

### Component 1: Search Settings Modal Bottom Sheet
#### [NEW] [SearchSettingsDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/search/SearchSettingsDialog.kt)
- Create `@Composable fun SearchSettingsDialog(onDismiss: () -> Unit)` utilizing `AppModalBottomSheet`.
- **Header**:
  - `title = stringResource(R.string.Search_Settings)`
  - `iconPainter = painterResource(id = R.drawable.ic_search)`
  - `onDismissRequest = onDismiss`
  - `actions = { AppDialogActions.SaveCancel(...) }`
- **Search Tries Section**:
  - Title: `@string/prefsNumberOfSearchTriesTitle` ("Anzahl der Suchversuche" / "Number of search tries").
  - Slider: `Slider` with `valueRange = 1f..5f`, `steps = 3`, bound to staged `numberOfSearchTriesInt` state (default: 3).
  - Value Badge: A text badge or numerical label indicating the current number of tries (e.g. `1` to `5`).
- **Trigger Section ("Suche starten bei" / `@string/prefStartSearchTitle`)**:
  - Section headline: `@string/prefStartSearchTitle`.
  - Switches:
    - `startSearchWhenAppStarts` (default: `true`, title: `@string/prefsStartSearchWhenAppStartsTitle`)
    - `startSearchWhenResumeFromPaused` (default: `true`, title: `@string/prefsStartSearchWhenResumeFromPausedTitle`)
    - `startSearchWhenUserChangesSport` (default: `true`, title: `@string/prefsStartSearchWhenUserChangesSportTitle`)
    - `startSearchWhenTrackingStarts` (default: `false`, title: `@string/prefsStartSearchWhenTrackingStartsTitle`)
- **Behavior Section ("Suchverhalten" / `@string/prefs_search_behavior_title`)**:
  - Section headline: `@string/prefs_search_behavior_title`.
  - Switches with title and summary:
    - `searchOnlyForSportSpecificDevices` (default: `true`, title: `@string/prefsSearchOnlyForSportSpecificDevicesTitle`, summary: `@string/prefsSearchOnlyForSportSpecificDevicesSummary`)
    - `changeSportWhenDeviceGetsLost` (default: `true`, title: `@string/prefsChangeSportWhenDeviceGetsLostTitle`, summary: `@string/prefsChangeSportWhenDeviceGetsLostSummary`)
- **Action Bar (`AppDialogActions.SaveCancel`)**:
  - `onCancel = onDismiss` (discards uncommitted staged preferences).
  - `onSave = { ... }`:
    - Commits staged values (`numberOfSearchTriesInt`, `startSearchWhenAppStarts`, `startSearchWhenResumeFromPaused`, `startSearchWhenUserChangesSport`, `startSearchWhenTrackingStarts`, `searchOnlyForSportSpecificDevices`, `changeSportWhenDeviceGetsLost`) to `SharedPreferences`.
    - Invokes `onDismiss()`.

#### [NEW] [SearchSettingsDialogFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/search/SearchSettingsDialogFragment.kt)
- Extends `DialogFragment` with `STYLE_NORMAL, android.R.style.Theme_Translucent_NoTitleBar`.
- Composes `SearchSettingsDialog` within `ATrainingTrackerTheme`.
- Companion object exposes `TAG = "SearchSettingsDialogFragment"` and `@JvmStatic fun newInstance() = SearchSettingsDialogFragment()`.

---

### Component 2: Navigation & Routing Integration
#### [MODIFY] [MainActivityWithNavigation.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt)
- In `navigateToDrawerItem` (`R.id.drawer_search_settings`):
  - Close navigation drawer (`mDrawerLayout.closeDrawer(GravityCompat.START)`).
  - Launch `SearchSettingsDialogFragment.newInstance().show(supportFragmentManager, SearchSettingsDialogFragment.TAG)`.
  - Return `false` to avoid replacing the active main screen fragment.
- In `onPreferenceStartScreen`:
  - When `preferenceScreen.key == "search_settings"`, show `SearchSettingsDialogFragment.newInstance().show(supportFragmentManager, SearchSettingsDialogFragment.TAG)` and return `true`.

---

### Component 3: Test Verification
#### [MODIFY] [ModalBottomSheetDialogsIntegrityTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/ModalBottomSheetDialogsIntegrityTest.kt)
- Add `testSearchSettingsDialog_existsAndExposesComposableAndFragment()`:
  - Asserts `SearchSettingsDialogKt` has public composable `SearchSettingsDialog`.
  - Asserts `SearchSettingsDialogFragment` extends `androidx.fragment.app.DialogFragment` and provides `newInstance()`.

---

## 3. Invariants & Guardrails
- **SharedPreferences Keys Parity**: `numberOfSearchTriesInt`, `startSearchWhenAppStarts`, `startSearchWhenResumeFromPaused`, `startSearchWhenUserChangesSport`, `startSearchWhenTrackingStarts`, `searchOnlyForSportSpecificDevices`, and `changeSportWhenDeviceGetsLost` must remain strictly identical for `DeviceManager` and `BANALService`.
- **Default Value Alignment**: Retain exact defaults (3 for tries, false for tracking starts, true for app starts, resume, sport change, sport-specific devices, and change sport on device lost).
- **TrainingApplication Getters**: All 7 getters in `TrainingApplication.java` (`getNumberOfSearchTries()`, `startSearchWhenAppStarts()`, etc.) must continue to function seamlessly without modification.
- **Visual Design System**: Material 3 typography, tokens, and paddings must align with `UnitsSettingsDialog`, `DisplaySettingsDialog`, `DropboxSettingsDialog`, and `StravaSettingsDialog`.
- **Viewport Continuity**: The underlying screen (tracking, workouts, periods, routes) must remain visible beneath the modal sheet scrim.

---

## 4. Verification Plan
### Automated Tests
- `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.core.ModalBottomSheetDialogsIntegrityTest"`
- `./gradlew testDebugUnitTest --no-daemon` (full suite clean-room regression check, 32 tasks).

### Physical Device Verification
- `./gradlew installDebug` on Google Pixel 10 (`66020DLCR002FL`).
- Verify opening Search Settings from the navigation drawer, adjusting tries slider, toggling trigger and behavior switches, testing cancel vs. save persistence, and confirming active screen continuity beneath the scrim.
