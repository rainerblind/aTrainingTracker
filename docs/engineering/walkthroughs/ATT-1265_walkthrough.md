# Walkthrough - ATT-1439: Implementation of Dynamic Zone-Based Color Accenting

**Issue**: [ATT-1439](https://rainerblind.atlassian.net/browse/ATT-1439)  
**Parent Issue**: [ATT-1265](https://rainerblind.atlassian.net/browse/ATT-1265) (*Dynamic zone-based color accenting for Heart Rate and Power metrics*)  
**Active Sprint**: `2026-39.3` | **Target Release**: `V4.9.38`  
**Requirement**: `REQ-UI-172` | **Test Spec**: `TST-UI-124`  
**Branch**: `feature/ATT-1265`  

---

## 1. Executive Summary

Under **ATT-1439** (Implementation sub-task of **ATT-1265**), dynamic zone-based visual color accenting was introduced to the tracking cockpit for Heart Rate and Power metrics, accompanied by a comprehensive configuration and preview section in the Training Zones settings screen.

The implementation strictly preserves the established 5-zone color palette (`#7FFF00`, `#008000`, `#FFA500`, `#FF0000`, `#9400D3`) and delivers 4 customizable, non-exclusive visual accenting options:
1. **Light / Tinted Background** (`showBackground`, default: `true`):
   - Renders a subtle `0.12f` alpha tint of the active zone color.
   - Utilizes `fieldState.zoneColor.copy(alpha = 0.12f).compositeOver(MaterialTheme.colorScheme.surface)` to eliminate parent container bleed-through and guarantee rich, opaque contrast.
2. **Left Indicator Bar** (`showLeftBar`, default: `true`):
   - Renders a prominent 6dp vertical color accent bar along the left edge of the sensor tile.
3. **Right Indicator Bar** (`showRightBar`, default: `false`):
   - Optional 6dp vertical accent bar along the right edge of the sensor tile.
4. **Text Color Tinting** (`showTextColor`, default: `false`):
   - Tints the primary numerical metric value text with the full active zone color instead of default `onSurface`.
   - When disabled, value text faithfully renders using `MaterialTheme.colorScheme.onSurface`.

---

## 2. Key Architecture & Components

### A. Domain Model & Persistence
* **[ZoneDisplayOptions.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/settings/ZoneDisplayOptions.kt)**:
  - Immutable data class defining `showBackground`, `showLeftBar`, `showRightBar`, and `showTextColor`.
* **[SettingsDataStore.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/settings/SettingsDataStore.kt)**:
  - Added DataStore keys: `ZONE_DISPLAY_SHOW_BACKGROUND`, `ZONE_DISPLAY_SHOW_LEFT_BAR`, `ZONE_DISPLAY_SHOW_RIGHT_BAR`, `ZONE_DISPLAY_SHOW_TEXT_COLOR`.
  - Exposes `zoneDisplayOptionsFlow: Flow<ZoneDisplayOptions>`.
  - Provides suspending helper `saveZoneDisplayOptions(options: ZoneDisplayOptions)`.
* **[SettingsDataStoreJavaHelper.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/settings/SettingsDataStoreJavaHelper.kt)**:
  - Exposes static Java-interoperable methods running on `Dispatchers.IO`.

### B. Tracking Cockpit & Hot Reload
* **[SensorFieldState.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldState.kt)**:
  - Added `zoneDisplayOptions: ZoneDisplayOptions = ZoneDisplayOptions()` field to `SensorFieldState`.
* **[SensorFieldView.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldView.kt)**:
  - Computes container color using `.compositeOver(MaterialTheme.colorScheme.surface)` when `hasZoneColor && showBackground`.
  - Places 6dp vertical indicator bars (`Box(Modifier.width(6.dp).fillMaxHeight().background(fieldState.zoneColor))`) aligned to `Alignment.CenterStart` and `Alignment.CenterEnd`.
  - Value text applies `fieldState.zoneColor` when `hasZoneColor && showTextColor`, falling back to `MaterialTheme.colorScheme.onSurface`.
* **[TrackingViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModel.kt)**:
  - Collects `zoneDisplayOptionsFlow` in `viewModelScope` into a cached memory reference, guaranteeing zero disk I/O during 1Hz sensor ticks while enabling hot-reloading.

### C. Settings UI & Live Cockpit Preview
* **[ZonesSettingsActivity.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/ZonesSettingsActivity.kt)**:
  - **Zonendarstellung Section**: Features 4 individual toggle switches for each visual dimension with descriptions.
  - **Interactive Zone Chips**: Z1 through Z5 filter chips styled with the exact zone color dot indicator, allowing the athlete to inspect how each zone appears.
  - **Dynamic Interval Values**: The preview tile evaluates the athlete's configured interval thresholds (`z1Max` for Zone 1, and the lower threshold `previousZoneMax + 1` for Zones 2–5; e.g. `135 bpm` for Z1, `156 bpm` for Z3).
  - **Cockpit Theme Isolation**: Evaluates `TrainingApplication.getCockpitThemeMode()`. When set to Always Dark, wraps the preview tile in `ATrainingTrackerTheme(darkTheme = isCockpitDark, amoled = isCockpitDark, setWindowColors = false)` and a dark `Surface`, faithfully previewing the cockpit tile in AMOLED mode without affecting the light mode activity window or system status bars.
* **[Theme.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/Theme.kt)**:
  - Added `setWindowColors: Boolean = true` parameter to `ATrainingTrackerTheme` so nested theme scopes do not override the host Activity's window and status bar colors.

### D. Localization (100% Parity across 9 Languages)
Added all 6 required string keys across all 9 supported locales:
* `settings_zone_display_title` ("Zonendarstellung")
* `settings_zone_display_show_background` ("Heller Hintergrund")
* `settings_zone_display_show_left_bar` ("Balken links")
* `settings_zone_display_show_right_bar` ("Balken rechts")
* `settings_zone_display_show_text_color` ("Textfarbe")
* `settings_zone_display_preview_title` ("Vorschau")

---

## 3. Verification & Test Results

### Automated Unit Tests
Executed via `./gradlew testDebugUnitTest`:
* **`ZoneDisplayOptionsTest`**: Verified default construction, custom instantiation, immutability, copy, and equality.
* **`SensorFieldZoneDisplayTest`**: Verified state preservation, default propagation, and transparent color handling.
* **`ZoneDisplayLocalizationTest`**: Verified all 6 keys exist and are non-empty across all 9 locales (`en`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).
* **Test Outcome**: 100% tests passed cleanly.

### Device Verification (Google Pixel 10)
* Tested live on connected Pixel 10 device (`66020DLCR002FL`).
* Global system mode kept in **Light Mode** (`night mode: no`).
* Cockpit Theme set to **Always Dark**.
* Verified interactive toggles update the live preview tile instantaneously.
* Verified that selecting Z1 displays `135 bpm` (configured Z1 max) and selecting Z3 displays `156 bpm` (configured Z3 min).
