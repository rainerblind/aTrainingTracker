# Walkthrough - ATT-1018: Cleanup Obsolete Fragments, Unused Layout XMLs, and Dead Strings

## 1. Executive Summary
Under **ATT-1018** (Epic `ATT-431`: Clean Code), technical debt and dead legacy UI artifacts remaining after the comprehensive Jetpack Compose modernization were systematically purged from the codebase:
1. **Legacy Fragments Retired**: Removed `CloudUploadFragment.kt` (superseded by `DropboxSettingsDialog`), `SearchSettingsFragment.kt` (superseded by `SearchSettingsDialog`), and `StravaUploadFragment.kt` (superseded by `StravaSettingsDialog`).
2. **Caller Modernization**: Modernized `StarredSegmentsFragment.kt` to trigger `StravaSettingsDialogFragment.newInstance().show(...)` instead of legacy fragment transactions; removed dead fragment imports and dead `tvStart` view lookups in `MainActivityWithNavigation.kt`; removed dead `apps_toolbar` view lookup in `ConfigViewsActivityClassic.java`.
3. **Obsolete Layout XML Purge**: Deleted 59 unused legacy View-based layout XML files in `app/src/main/res/layout/` superseded by Compose screens and dialogs, reducing project footprint by over 5,200 lines of XML while strictly preserving all 9 actively required layout files.
4. **Dead String Resource Cleanup**: Purged 75 unreferenced string identifiers from `values/strings.xml` and 68 translated entries from each localized strings file (`values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
5. **Verification**: 100% clean-room test pass (`./gradlew testDebugUnitTest`) across all 32 actionable tasks with zero failures and zero regressions, clean build and installation on physical Google Pixel 10 (`66020DLCR002FL`), and verified live application execution.

---

## 2. Changes Implemented

### A. Dead Fragment Deletion & Caller Modernization
* **Deleted Fragment Classes**:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/dropbox/CloudUploadFragment.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/search/SearchSettingsFragment.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/strava/StravaUploadFragment.kt`
* **`StarredSegmentsFragment.kt`**:
  * Replaced `startStravaUploadFragment()` with `startStravaSettingsDialog()`, instantiating and showing `StravaSettingsDialogFragment.newInstance()`.
* **`MainActivityWithNavigation.kt`**:
  * Removed obsolete imports for `CloudUploadFragment`, `SearchSettingsFragment`, and `StravaUploadFragment`.
  * Removed obsolete `findViewById<TextView>(R.id.tvStart)` calls from `chooseStart()` and `chooseResume()`.
* **`ConfigViewsActivityClassic.java`**:
  * Removed dead `apps_toolbar` view lookup and `setSupportActionBar(toolbar)` call since `apps_toolbar` is commented out in `main_activity_without_navigation.xml`.

### B. Layout XML Purge (59 Files Deleted)
* **Purged Layouts**:
  * `activity_track_on_map_aftermath.xml`, `altitude_correction_dialog.xml`, `altitude_correction_row.xml`, `checkbox_dialog.xml`, `config_filter.xml`, `config_tracking_view.xml`, `config_tracking_view_entry_configurable.xml`, `config_tracking_view_entry_static.xml`, `config_tracking_view_row.xml`, `config_tracking_view_table.xml`, `config_tracking_view_table_row.xml`, `control_sport_type_layout.xml`, `control_tracking_layout.xml`, `deprecated_tracking_fragment.xml`, `device_choice_list.xml`, `device_choice_row.xml`, `device_list.xml`, `dialog_change_sport.xml`, `dialog_custom_title.xml`, `dialog_edit_description.xml`, `dialog_edit_name.xml`, `dialog_edit_sport_type.xml`, `dialog_part_calibration.xml`, `dialog_part_power_features.xml`, `export_progress.xml`, `export_status__group.xml`, `fragment_tabbed_container.xml`, `fragment_workout_list.xml`, `fragment_workout_summaries_tabs.xml`, `fragment_workoutnamecounter.xml`, `generic_device.xml`, `generic_sensor_list.xml`, `generic_sensor_row.xml`, `headerview.xml`, `lap_summary_dialog.xml`, `legal_info.xml`, `map_aftermath.xml`, `number_picker_dialog_layout.xml`, `pairing_sensor_row.xml`, `segment_details.xml`, `sensor_field.xml`, `simple_login_dialog.xml`, `sport_type_list_layout.xml`, `sport_type_row.xml`, `tabbed_remote_devices_container_fragment.xml`, `tabbed_tracking_fragment.xml`, `temperature_layout.xml`, `track_on_map.xml`, `tracking_mode.xml`, `update_progress_two_lines.xml`, `workout_details.xml`, `workout_name_counter_row.xml`, `workout_name_schemes_list_layout.xml`, `workout_summaries_row.xml`, `workout_summary__description.xml`, `workout_summary__details.xml`, `workout_summary__export_status.xml`, `workout_summary__extrema.xml`, `workout_summary__header.xml`.
* **Retained Layouts (9 Verified Active Files)**:
  * `check_ant_installation_dialog.xml`, `export_notification__expanded.xml`, `export_notification__group.xml`, `main_activity_with_navigation.xml`, `main_activity_without_navigation.xml`, `pebble_config_list_5.xml`, `preference_category.xml`, `preference_slim.xml`, `tabbed_config_views.xml`.

### C. Dead String Resource Purge
* Removed 75 unreferenced strings across default and 8 translation files:
  * Layout labels & fields: `AltitudeHint`, `Fused`, `GPS`, `LocationAltitudeText`, `LocationNameHint`, `LocationNameText`, `Network`, `OnlineCommunities`, `add_sport_type`, `calculating_extrema_values`, `calculating_max_away_point`, `conffig_tracking_night_day__night`, `configSensor`, `configSize`, `configSmoothing`, `configSource`, `config_tracking__Display`, `config_tracking__Lap`, `config_tracking__Map`, `config_tracking__day_or_night`, `config_tracking__full_screen`, `config_tracking_night_day__day`, `config_tracking_night_day__system_setting`, `default_device_name`, `delete_sensor`, `do_not_show_again`, `exampleSensorString`, `export_status__header_title`, `item_is_fixed_and_cannot_be_deleted`, `max_avg_speed`, `min_avg_speed`, `no_sensor_available`, `pairing_ANT`, `pairing_bluetooth`, `searching_for`, `sport_type_name`, `units_distance_mm`, `units_speed_short_unknown`.
  * Legacy dialogs & actions: `TrainingPeaks_login_title`, `TrainingPeaks_login_message`, `install_pebble_watchapp_title`, `install_pebble_watchapp_text`, `install_pebble_watchapp_now`, `no_user_name`, `no_password`, `title_edit_description`, `DescriptionHint`, `correct_altitude_title`, `correct_altitude_message`, `buttonSaveWorkoutText`, `buttonDeleteWorkoutText`, `workout_summary__description_header`, `LegalInfoLabel`, `show_google_maps_licence`, `title_request_dropbox_token`, `message_request_dropbox_token`.
  * Legacy activity labels: `StravaGetAccessTokenActivityLabel`, `PrefsActivityLabel`, `UpdateWorkoutActivityLabel`, `TrackOnMapActivityLabel`, `ExportDetailsActivityLabel`, `TrackingFragmentConfigListActivityLabel`, `TrackingFragmentConfigActivityLabel`, `PebbleConfigListActivityLabel`, `AltitudeCorrectionListActivityLabel`, `DeviceChoiceActivityLabel`, `PairingActivityLabel`, `EditDeviceActivityLabel`.
  * Deleted search settings: `prefsStartSearchWhenNewLapTitle`, `startSearchWhenAppStarts_short`, `startSearchWhenTrackingStarts_short`, `startSearchWhenResumeFromPaused_short`, `startSearchWhenNewLap_short`, `startSearchWhenUserChangesSport_short`, `startSearchOnlyManually`.

---

## 3. Verification & Evidence
* **Unit Test Suite**: `./gradlew testDebugUnitTest --no-daemon` (**BUILD SUCCESSFUL in 1m 19s**, 0 failures, 0 regressions across all 32 actionable tasks).
* **Compilation Integrity**: `./gradlew compileDebugKotlin compileDebugJavaWithJavac` (**BUILD SUCCESSFUL in 1m 25s**).
* **Physical Device Deployment**: Installed on Google Pixel 10 (`66020DLCR002FL`) via `./gradlew installDebug` (**BUILD SUCCESSFUL in 49s**).
* **Live Execution**: App launched cleanly (`am start -n com.atrainingtracker.debug/...MainActivityWithNavigation`), running with PID 21800, verified with live screencap rendering summary charts without inflation or resource missing crashes.
* **Traceability & Living Docs**: `REQ-UI-155` and `TST-UI-108` marked `Verified` in `docs/requirements.md` and `docs/tests.md`.
