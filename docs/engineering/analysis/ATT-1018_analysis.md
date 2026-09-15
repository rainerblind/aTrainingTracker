# ASPICE Stage 1: Analysis & Problem Domain Formal Audit (ATT-1018)

## 1. Problem Domain & Motivation
Across recent modernization tickets (`ATT-900`, `ATT-939`, `ATT-1034`, `ATT-1043`, `ATT-1044`, `ATT-1048`, `ATT-1051`), multiple legacy screens, full-screen fragments, and XML-based view hierarchies were replaced with modern Jetpack Compose modal bottom sheets (`AppModalBottomSheet`) and composable screen views.

However, significant dead code, obsolete layout XML files, dead fragment classes, and unused string resources remained in the codebase:
1. **Dead Legacy Fragment Outliers**:
   - `CloudUploadFragment.kt`: Replaced by `DropboxSettingsDialog.kt` and `DropboxSettingsDialogFragment.kt` under `ATT-1051`.
   - `SearchSettingsFragment.kt`: Replaced by `SearchSettingsDialog.kt` and `SearchSettingsDialogFragment.kt` under `ATT-1044`.
   - `StravaUploadFragment.kt`: Replaced by `StravaSettingsDialog.kt` and `StravaSettingsDialogFragment.kt` under `ATT-1048`.
   - `MainActivityWithNavigation.kt`: Still contains unused imports for `CloudUploadFragment`, `SearchSettingsFragment`, and `StravaUploadFragment`.
   - `StarredSegmentsFragment.kt`: Still instantiates and starts legacy `StravaUploadFragment` instead of invoking `StravaSettingsDialogFragment`.
2. **Obsolete Layout XML Files**:
   - 59 out of 68 layout XML files in `app/src/main/res/layout/` are legacy artifacts from deprecated Android View implementations (such as old workout summary cards, old dialogs, deprecated tracking layouts, and old sensor selection lists) that have been fully superseded by Jetpack Compose components.
3. **Dead String Resources**:
   - Hundreds of string resources in `res/values/strings.xml` and regional localized files (`values-de/strings.xml`, etc.) belong exclusively to deleted layouts and removed dialogs and have 0 references across the entire codebase.

Retaining this dead code increases build times, clutters code navigation, creates maintenance confusion, and inflates APK binary size.

---

## 2. Root Cause Analysis
- Modernization features were delivered incrementally by replacing navigation destinations with Compose sheets without immediately purging obsolete fragments and XML files in the same commits.
- A dedicated cleanup ticket (`ATT-1018`) was created in the active sprint backlog specifically to perform an ASPICE-compliant, regression-free sweep of unused resources, layouts, strings, and dead fragment outliers.

---

## 3. Target Scope & Boundaries

### 3.1 Dead Fragment Outliers & Navigation Cleanup
- **Retire Dead Fragment Classes**:
  - Delete `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/dropbox/CloudUploadFragment.kt`.
  - Delete `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/search/SearchSettingsFragment.kt`.
  - Delete `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/strava/StravaUploadFragment.kt`.
- **Modernize Callers**:
  - Update `StarredSegmentsFragment.kt`: Replace `startStravaUploadFragment()` with `startStravaSettingsDialog()` launching `StravaSettingsDialogFragment.newInstance()`.
  - Update `MainActivityWithNavigation.kt`: Remove dead imports of `CloudUploadFragment`, `SearchSettingsFragment`, and `StravaUploadFragment`.

### 3.2 Obsolete Layout XML Files Purge
- Retain only the 9 genuinely active layout files:
  1. `check_ant_installation_dialog.xml` (used by `InstallANTShitDialog.java`)
  2. `export_notification__expanded.xml` (used by `ExportNotificationManager.kt` RemoteViews)
  3. `export_notification__group.xml` (used by `ExportNotificationManager.kt` RemoteViews)
  4. `main_activity_with_navigation.xml` (used by `MainActivityWithNavigation.kt`)
  5. `main_activity_without_navigation.xml` (used by `ConfigViewsActivityClassic.java`)
  6. `pebble_config_list_5.xml` (used by `ConfigPebbleViewFragment.java`)
  7. `preference_category.xml` (used by `prefs_search.xml`)
  8. `preference_slim.xml` (used by `prefs_search.xml`, `prefs_strava.xml`)
  9. `tabbed_config_views.xml` (used by `ConfigViewsFragment.java`)
- Delete all 59 unused layout files from `app/src/main/res/layout/`.

### 3.3 Dead String Resources Cleanup
- Systematically purge unreferenced strings associated with retired layouts and obsolete dialogs from `app/src/main/res/values/strings.xml` and translation files (`values-de`, etc.).
- Ensure all actively used strings, configuration defaults, and format patterns remain completely intact.

---

## 4. Invariants & Guardrails
- All unit tests (`./gradlew testDebugUnitTest`) MUST continue to pass with 0 failures and 0 regressions.
- Reflection tests (`ModalBottomSheetDialogsIntegrityTest.kt`) and existing Compose screens MUST NOT be broken.
- No active layouts or resources required for runtime execution (e.g. notifications, pebble views, preferences) may be removed.
- Physical device deployment (`./gradlew installDebug`) MUST launch and execute with zero layout inflation crashes.
