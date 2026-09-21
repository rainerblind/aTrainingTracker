# Implementation Plan - ATT-1018: Cleanup Unnecessary Code, Layout XMLs, and Strings

## 1. Overview & Architectural Goals
The objective of **ATT-1018** is to eliminate technical debt, dead code, obsolete layout XML files, and unreferenced string resources resulting from modernizing the application's screens and settings to Jetpack Compose per `REQ-UI-155` and `TST-UI-108`.

---

## 2. Proposed Changes

### Component 1: Dead Fragment Retirement & Caller Migration
#### [MODIFY] [StarredSegmentsFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/segmentlist/StarredSegmentsFragment.kt)
- Replace `startStravaUploadFragment()` with `startStravaSettingsDialog()` launching `StravaSettingsDialogFragment.newInstance().show(parentFragmentManager, StravaSettingsDialogFragment.TAG)`.
- Replace `onConnectToStrava = { startStravaUploadFragment() }` with `onConnectToStrava = { startStravaSettingsDialog() }`.
- Replace `import com.atrainingtracker.trainingtracker.ui.settings.strava.StravaUploadFragment` with `import com.atrainingtracker.trainingtracker.ui.settings.strava.StravaSettingsDialogFragment`.

#### [MODIFY] [MainActivityWithNavigation.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt)
- Remove dead unused imports:
  - `import com.atrainingtracker.trainingtracker.ui.settings.dropbox.CloudUploadFragment`
  - `import com.atrainingtracker.trainingtracker.ui.settings.search.SearchSettingsFragment`
  - `import com.atrainingtracker.trainingtracker.ui.settings.strava.StravaUploadFragment`

#### [DELETE] [CloudUploadFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/dropbox/CloudUploadFragment.kt)
- Delete dead fragment class superseded by `DropboxSettingsDialog.kt` and `DropboxSettingsDialogFragment.kt`.

#### [DELETE] [SearchSettingsFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/search/SearchSettingsFragment.kt)
- Delete dead fragment class superseded by `SearchSettingsDialog.kt` and `SearchSettingsDialogFragment.kt`.

#### [DELETE] [StravaUploadFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/strava/StravaUploadFragment.kt)
- Delete dead fragment class superseded by `StravaSettingsDialog.kt` and `StravaSettingsDialogFragment.kt`.

---

### Component 2: Obsolete Layout XML Files Purge
#### [DELETE] 59 Obsolete Layout Files in `app/src/main/res/layout/`
- Delete:
  - `activity_track_on_map_aftermath.xml`
  - `altitude_correction_dialog.xml`
  - `altitude_correction_row.xml`
  - `checkbox_dialog.xml`
  - `config_filter.xml`
  - `config_tracking_view.xml`
  - `config_tracking_view_entry_configurable.xml`
  - `config_tracking_view_entry_static.xml`
  - `config_tracking_view_row.xml`
  - `config_tracking_view_table.xml`
  - `config_tracking_view_table_row.xml`
  - `control_sport_type_layout.xml`
  - `control_tracking_layout.xml`
  - `deprecated_tracking_fragment.xml`
  - `device_choice_list.xml`
  - `device_choice_row.xml`
  - `device_list.xml`
  - `dialog_change_sport.xml`
  - `dialog_custom_title.xml`
  - `dialog_edit_description.xml`
  - `dialog_edit_name.xml`
  - `dialog_edit_sport_type.xml`
  - `dialog_part_calibration.xml`
  - `dialog_part_power_features.xml`
  - `export_progress.xml`
  - `export_status__group.xml`
  - `fragment_tabbed_container.xml`
  - `fragment_workout_list.xml`
  - `fragment_workout_summaries_tabs.xml`
  - `fragment_workoutnamecounter.xml`
  - `generic_device.xml`
  - `generic_sensor_list.xml`
  - `generic_sensor_row.xml`
  - `headerview.xml`
  - `lap_summary_dialog.xml`
  - `legal_info.xml`
  - `map_aftermath.xml`
  - `number_picker_dialog_layout.xml`
  - `pairing_sensor_row.xml`
  - `segment_details.xml`
  - `sensor_field.xml`
  - `simple_login_dialog.xml`
  - `sport_type_list_layout.xml`
  - `sport_type_row.xml`
  - `tabbed_remote_devices_container_fragment.xml`
  - `tabbed_tracking_fragment.xml`
  - `temperature_layout.xml`
  - `track_on_map.xml`
  - `tracking_mode.xml`
  - `update_progress_two_lines.xml`
  - `workout_details.xml`
  - `workout_name_counter_row.xml`
  - `workout_name_schemes_list_layout.xml`
  - `workout_summaries_row.xml`
  - `workout_summary__description.xml`
  - `workout_summary__details.xml`
  - `workout_summary__export_status.xml`
  - `workout_summary__extrema.xml`
  - `workout_summary__header.xml`
- Retain the 9 active layout files:
  - `check_ant_installation_dialog.xml`
  - `export_notification__expanded.xml`
  - `export_notification__group.xml`
  - `main_activity_with_navigation.xml`
  - `main_activity_without_navigation.xml`
  - `pebble_config_list_5.xml`
  - `preference_category.xml`
  - `preference_slim.xml`
  - `tabbed_config_views.xml`

---

### Component 3: Dead String Resource Cleanup
#### [MODIFY] `app/src/main/res/values/strings.xml` and Translation Files
- Purge obsolete string identifiers belonging to deleted dialogs (e.g. old TrainingPeaks login, deprecated Pebble watchapp prompt, old layout labels) that have zero references across the application.
- Preserve all active string resources, preference defaults, and format templates.

---

## 3. Verification Plan

### Automated Tests
* Clean-room unit test suite: `./gradlew testDebugUnitTest --no-daemon` (ensure 0 failures, 0 regressions across all 32 actionable tasks).
* Reflection integrity tests: `ModalBottomSheetDialogsIntegrityTest.kt` verifying all Compose dialogs and DialogFragment bridges.

### Manual / Hardware Verification
* Build and deploy debug APK via `./gradlew installDebug` to physical Google Pixel 10 (`66020DLCR002FL`).
* Launch `MainActivityWithNavigation`, navigate through Settings bottom popups, workout list, and segments without runtime exceptions or missing resource crashes.
