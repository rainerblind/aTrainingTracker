# ASPICE Stage 1: Analysis & Problem Domain Formal Audit (ATT-1044)

## 1. Problem Domain & Motivation
The application currently handles sensor search settings (`R.id.drawer_search_settings`) by replacing the active main fragment in `MainActivityWithNavigation.kt` with a full-screen `SearchSettingsFragment`. This fragment embeds legacy AndroidX XML preferences (`R.xml.prefs_search` hosted in `PreferenceFragmentCompat`) alongside a small Compose header.

This legacy architecture exhibits several issues:
1. **Design System Inconsistency**: In the navigation drawer, settings items have been modernized per `REQ-UI-149`, `REQ-UI-150`, `REQ-UI-152`, and `REQ-UI-153` into modal bottom sheets:
   - Export Settings: `ExportSettingsDialogFragment` (`AppModalBottomSheet`)
   - Units Settings: `UnitsSettingsDialogFragment` (`AppModalBottomSheet`)
   - Display Settings: `DisplaySettingsDialogFragment` (`AppModalBottomSheet`)
   - Dropbox Settings: `DropboxSettingsDialogFragment` (`AppModalBottomSheet`)
   - Strava Settings: `StravaSettingsDialogFragment` (`AppModalBottomSheet`)
   In contrast, Search Settings remains the final settings hub outlier that replaces the main screen view hierarchy, destroying active background context and causing navigation disruption.
2. **Hybrid Preference Architecture Debt**: `SearchSettingsFragment` mixes a Compose view for its title header with AndroidX XML preference views (`SeekBarPreference`, `CheckBoxPreference`), resulting in dual view hierarchies, rigid styling, and layout friction.
3. **Ergonomic & Transactional Deficiencies**: Changes in `SearchSettingsFragment` immediately mutate `SharedPreferences` without explicit user confirmation or transactional staging (`AppDialogActions.SaveCancel`).

---

## 2. Root Cause Analysis
- `SearchSettingsFragment` was built as a dedicated `PreferenceFragmentCompat` reading `R.xml.prefs_search`.
- While modern Compose bottom sheets were rolled out across all other settings screens, search settings was left as a full-screen fragment replacement in `MainActivityWithNavigation.kt`.
- `MainActivityWithNavigation.kt` routes `R.id.drawer_search_settings` and `onPreferenceStartScreen("search_settings")` to fragment replacement rather than presenting a modal `DialogFragment`.

---

## 3. Target Architecture & Scope Boundaries
### 3.1 Composable Dialog Architecture (`SearchSettingsDialog.kt`)
- Backed by `AppModalBottomSheet` with drag handle, close button, and window insets (`navigationBarsPadding()`, `imePadding()`).
- **Header**:
  - Icon: Search icon (`painterResource(R.drawable.ic_search)`).
  - Title: Localized title (`@string/Search_Settings`).
- **Search Tries Section**:
  - Title: `@string/prefsNumberOfSearchTriesTitle` ("Anzahl der Suchversuche" / "Number of search tries").
  - Value selector: Slider from 1 to 5 (steps = 3) with dynamic numeric indicator reflecting staged `numberOfSearchTriesInt` (default: 3).
- **Trigger Section ("Suche starten bei" / `@string/prefStartSearchTitle`)**:
  - `startSearchWhenAppStarts` (boolean switch, default: `true`, title: `@string/prefsStartSearchWhenAppStartsTitle`)
  - `startSearchWhenResumeFromPaused` (boolean switch, default: `true`, title: `@string/prefsStartSearchWhenResumeFromPausedTitle`)
  - `startSearchWhenUserChangesSport` (boolean switch, default: `true`, title: `@string/prefsStartSearchWhenUserChangesSportTitle`)
  - `startSearchWhenTrackingStarts` (boolean switch, default: `false`, title: `@string/prefsStartSearchWhenTrackingStartsTitle`)
- **Behavior Section ("Suchverhalten" / `@string/prefs_search_behavior_title`)**:
  - `searchOnlyForSportSpecificDevices` (boolean switch with summary, default: `true`, title: `@string/prefsSearchOnlyForSportSpecificDevicesTitle`, summary: `@string/prefsSearchOnlyForSportSpecificDevicesSummary`)
  - `changeSportWhenDeviceGetsLost` (boolean switch with summary, default: `true`, title: `@string/prefsChangeSportWhenDeviceGetsLostTitle`, summary: `@string/prefsChangeSportWhenDeviceGetsLostSummary`)
- **Action Bar**:
  - Follows `AppDialogActions.SaveCancel` ("Abbrechen" / `@string/Cancel` and "Speichern" / `@string/save`).
  - `onSave`: Commits staged values (`numberOfSearchTriesInt`, `startSearchWhenAppStarts`, `startSearchWhenResumeFromPaused`, `startSearchWhenUserChangesSport`, `startSearchWhenTrackingStarts`, `searchOnlyForSportSpecificDevices`, `changeSportWhenDeviceGetsLost`) to `SharedPreferences` and dismisses dialog.
  - `onCancel`: Discards uncommitted staged values and dismisses dialog.

### 3.2 DialogFragment Host (`SearchSettingsDialogFragment.kt`)
- Extends `DialogFragment` with `STYLE_NORMAL, android.R.style.Theme_Translucent_NoTitleBar`.
- Composes `SearchSettingsDialog` within `ATrainingTrackerTheme`.

### 3.3 Navigation Integration (`MainActivityWithNavigation.kt`)
- `R.id.drawer_search_settings`: Closes navigation drawer and displays `SearchSettingsDialogFragment.newInstance().show(supportFragmentManager, SearchSettingsDialogFragment.TAG)`.
- `onPreferenceStartScreen("search_settings")`: Shows `SearchSettingsDialogFragment`.

---

## 4. Impacted Files & Components
- **New UI Components**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/search/SearchSettingsDialog.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/search/SearchSettingsDialogFragment.kt`
- **Navigation & Routing**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt`
- **Testing & Verification**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/ModalBottomSheetDialogsIntegrityTest.kt`
- **Documentation**:
  - `docs/requirements.md` (new `REQ-UI-154`)
  - `docs/tests.md` (new `TST-UI-107`)

---

## 5. Invariant Checklist
- [x] **SharedPreferences Keys & Defaults**:
  - `numberOfSearchTriesInt` (`Int`, min 1, max 5, default 3)
  - `startSearchWhenAppStarts` (`Boolean`, default true)
  - `startSearchWhenResumeFromPaused` (`Boolean`, default true)
  - `startSearchWhenUserChangesSport` (`Boolean`, default true)
  - `startSearchWhenTrackingStarts` (`Boolean`, default false)
  - `searchOnlyForSportSpecificDevices` (`Boolean`, default true)
  - `changeSportWhenDeviceGetsLost` (`Boolean`, default true)
  Must remain 100% backward compatible for `DeviceManager` and `TrainingApplication`.
- [x] **Getter Methods**: `TrainingApplication` getters (`getNumberOfSearchTries()`, `startSearchWhenAppStarts()`, etc.) must read unchanged SharedPreferences values.
- [x] **Navigation & Viewport**: Navigation drawer item `R.id.drawer_search_settings` must open the sheet without replacing the underlying active fragment.
- [x] **Transactional Action Bar**: "Abbrechen" must discard uncommitted adjustments; "Speichern" must commit them.
