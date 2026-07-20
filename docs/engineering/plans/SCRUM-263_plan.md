# Implementation Plan: Cleanup Prefs / Configs (ATT-263)

## 1. Requirement Traceability

| ID | Description | Component | Test ID |
|:---|:---|:---|:---|
| **REQ-SET-055** | Clean Architecture (Settings) - Remove legacy code and reorganize. | `ui/settings/`, `MainActivityWithNavigation.java` | `TST-INT-008` |
| **REQ-SET-050** | Update Navigation Drawer Structure references. | `main_navigation_drawer.xml` | `TST-NAV-001` |

## 2. Proposed Changes

### Deletions (Technical Debt Removal)
- **Unused Fragments**:
    - `fragments/preferences/UnitsSettingsFragment.kt` (Replaced by Dialog)
    - `fragments/preferences/DisplaySettingsFragment.kt` (Replaced by Dialog)
    - `fragments/preferences/ExportSettingsFragment.kt` (Replaced by Dialog)
    - `fragments/preferences/SearchFragment.java` (Legacy Search)
    - `fragments/preferences/RootPrefsFragment.kt` (Legacy consolidate settings)
    - `fragments/preferences/PebbleScreenFragment.java` (Deactivated Pebble support)
    - `fragments/preferences/RunkeeperUploadFragment.java` (Unsupported Online Community)
    - `fragments/preferences/TrainingpeaksUploadFragment.java` (Unsupported Online Community)
- **Unused XML Configurations**:
    - `res/xml/prefs.xml`
    - `res/xml/prefs_units.xml`
    - `res/xml/prefs_display.xml`
    - `res/xml/prefs_export.xml`

### Reorganization (Architecture Alignment)
Move all remaining settings-related UI components to a new `com.atrainingtracker.trainingtracker.ui.settings` hierarchy:
- `ui/settings/display/`: `DisplaySettingsDialog.kt`, `DisplaySettingsDialogFragment.kt`
- `ui/settings/units/`: `UnitsSettingsDialog.kt`, `UnitsSettingsDialogFragment.kt`
- `ui/settings/export/`: `ExportSettingsDialog.kt`, `ExportSettingsDialogFragment.kt`
- `ui/settings/search/`: `SearchSettingsFragment.kt`
- `ui/settings/strava/`: `StravaUploadFragment.kt`, `StravaConnectionHeader.kt`
- `ui/settings/dropbox/`: `CloudUploadFragment.kt`, `DropboxConnectionHeader.kt`
- `ui/settings/trackingtabs/`: `ActivityTypeSelectionDialog.kt`, `ActivityTypeSelectionDialogFragment.kt`, `ActivityTypeSelectionHelper.kt`

### `MainActivityWithNavigation.java` Cleanup
- Update imports for all moved fragments and dialogs.
- Remove imports for deleted fragments.
- Update `onPreferenceStartScreen` to remove cases for deleted legacy fragments.

## 3. Impact Analysis
- **Build**: Significant reduction in unused code and resources.
- **Maintenance**: Improved discoverability of settings logic.
- **Runtime**: No functional impact on existing features.

## 4. Verification Plan (TST-INT-008)
1. **Gradle Build**: Ensure the project compiles successfully after reorganization and deletions.
2. **Navigation Test**: Verify all settings items in the drawer (Units, Display, Search, Export, Strava, Dropbox, Tracking Tabs) still function correctly.
3. **Traceability**: Verify no broken references remain in the codebase.
