# Architectural & Domain Analysis - ATT-1267: [Settings] Independent Theme Selector for Workout Cockpit (Always Dark, Auto Day/Night, System)

## 1. Executive Summary & Problem Statement

* **Issue Key**: `ATT-1267`
* **Sub-tasks**: `[Analysis]`, `[Test-Spec]`, `[Impl-Plan]`, `[Implementation]`, `[Test]`
* **Parent Issue**: `ATT-1267` (*[Feature] [Settings] Independent Theme Selector for Workout Cockpit (Always Dark, Auto Day/Night, System)*)
* **Parent Epic**: `ATT-1157` (*[Epic] Optimize dark mode*)
* **Target Version**: `V4.9.38` (Sprint `2026-39.3`)
* **Associated Requirements**: `REQ-UI-106` (Localization Parity), `REQ-UI-149` (AppModalBottomSheet), `REQ-UI-150` (AppDialogActions.SaveCancel), `REQ-UI-168` (Proposed: Independent Workout Cockpit Theme Selector)
* **Associated Verification**: `TST-UI-120` (Proposed: Workout Cockpit Theme Selection & Isolation Verification)

### Problem Statement
In `aTrainingTracker`, endurance athletes mount their smartphones on bike handlebars or wear them on running armbands in diverse outdoor environments: from bright direct midday sunlight to dusk, nighttime rides, and indoor turbo trainer sessions.

Currently, the app's theme is strictly bound to the global Android system theme via `isSystemInDarkTheme()`. This creates several severe friction points for athletes:
1. **Battery Drain on OLED/AMOLED Displays**: Long outdoor activities (3–8 hours) quickly drain phone batteries when running a bright light theme. True black OLED mode can reduce display power draw by 40–60%. However, athletes whose phones are globally set to light mode are unable to use battery-saving dark mode during training without altering their entire OS setting.
2. **Glaring and Night Blindness**: In evening or dawn rides, a bright white tracking screen blinds the athlete and impairs night vision.
3. **Sunlight Readability Preferences**: Conversely, some riders mounted under intense direct noon sun prefer a high-contrast light background with black typography, even if their phone OS is set to dark mode.
4. **App-Wide vs. Cockpit Separation**: Athletes want a specialized, high-performance cockpit during training, while keeping the rest of the application (Settings, Navigation Drawer, History, Workout Summaries, Periods, Strava/Dropbox sync) clean, approachable, and adhering to the standard theme.

---

## 2. Problem Domain & Scope Analysis

### 2.1 Clear Separation: Cockpit vs. Control Tracking vs. Rest of App
As confirmed by product requirements, there is a strict and crucial semantic boundary between the **Workout Cockpit** and other screens:

```
+-----------------------------------------------------------------------------------+
|                            aTrainingTracker Application                           |
+-----------------------------------------------------------------------------------+
|  General App Shell (Follows System / Light Theme):                                |
|  - Navigation Drawer                                                              |
|  - History & Workout Summaries (WorkoutSummariesTabbedScreen)                     |
|  - Periods & Statistics (PeriodsScreen)                                           |
|  - Routes & Segments (StarredSegmentsScreen, RoutesScreen)                        |
|  - Settings Dialogs (Strava, Dropbox, Export, Units, Search, Display)             |
|  - Known Locations & Sensor Management                                            |
+-----------------------------------------------------------------------------------+
|  TrackingTabsScreen (ScreenMode.TRACKING):                                        |
|  +-------------------------------------+---------------------------------------+  |
|  | Page 0: Control Tab                 | Pages 1..N: Workout Cockpit           |  |
|  | (ControlTrackingScreen)             | (TrackingTabGridContent)              |  |
|  | - Start / Pause / Stop Buttons      | - Sensor Value Tiles & Telemetry      |  |
|  | - Sensor Pairing & Device Selection | - Dynamic Zone Gauges & Smoothing     |  |
|  | - Sport Type Selector               | - Live Map & Elevation Profile        |  |
|  | -> NOT part of Cockpit!             | - Live Segments                       |  |
|  | -> Uses Standard App Theme          | -> SUBJECT TO COCKPIT THEME SETTING!  |  |
|  +-------------------------------------+---------------------------------------+  |
+-----------------------------------------------------------------------------------+
```

1. **The Workout Cockpit**:
   - Specifically comprises the active telemetry views, sensor grids ([`TrackingTabGridContent`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingTabGridContent.kt)), data field tiles, live route map, elevation profile, and live segment displays.
   - Also includes the preview/configuration view of these sensor fields (`ScreenMode.PREVIEW` and `ScreenMode.CONFIGURATION`) in `TrackingTabsScreen`.
   - **This is the only component controlled by the Cockpit Theme Selector.**
2. **Control Tracking (`ControlTrackingScreen`)**:
   - The device connection, protocol selection, sport type picker, and tracking start/pause/stop dashboard (Page 0 of `TrackingTabsScreen`).
   - **Explicitly NOT part of the cockpit.** Retains standard theme to ensure consistent brand identity and avoid unneeded dark inversion of pairing buttons and status indicators.
3. **Rest of the App**:
   - Completely untouched by the Cockpit Theme setting, preserving the clean white aesthetic and standard system behaviors.

---

## 3. Cockpit Theme Modes & Behavior

The selector provides **4 explicit modes**:

| Mode | Identifier | Display Behavior | Target Athletic Use Case |
|:---|:---|:---|:---|
| **Systemstandard** | `SYSTEM` (*Default*) | Matches Android OS mode (`isSystemInDarkTheme()`). | Default expected behavior for new users. |
| **Immer Dunkel (AMOLED)** | `ALWAYS_DARK` | Cockpit renders in AMOLED dark mode (`#000000` / `DarkColorScheme`), regardless of OS setting. | Maximum battery life on OLED displays, twilight/night rides, reduced eye strain. |
| **Immer Hell** | `ALWAYS_LIGHT` | Cockpit renders in clean light mode (`LightColorScheme`), regardless of OS setting. | High ambient brightness, riders who find white backgrounds easier to read in midday sun. |
| **Automatisch (Tag/Nacht)** | `AUTO` | Dynamically switches between Light and Dark based on local sunrise and sunset or ambient conditions. | Commuters and long-distance riders spanning day and night transitions without touching the phone. |

### 3.1 Resolving the Effective Cockpit Theme
The effective theme state for the cockpit is resolved via a pure function:
```kotlin
fun resolveEffectiveCockpitDarkTheme(
    mode: CockpitThemeMode,
    isSystemDark: Boolean,
    isNightTime: Boolean = false // e.g. from solar / time calculation or sensor
): Boolean {
    return when (mode) {
        CockpitThemeMode.SYSTEM -> isSystemDark
        CockpitThemeMode.ALWAYS_DARK -> true
        CockpitThemeMode.ALWAYS_LIGHT -> false
        CockpitThemeMode.AUTO -> isNightTime
    }
}
```

---

## 4. UI/UX Specifications for Display Settings Dialog

The setting will be integrated directly into [`DisplaySettingsDialog`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/display/DisplaySettingsDialog.kt).

### 4.1 Ergonomic Layout & Visual Clarity
To prevent any user confusion regarding what the setting affects:
1. **Section Header**:
   - Crisp category title: `Cockpit-Design (während des Trainings)` (`cockpit_theme_title`).
   - Subtitle / helper caption: `Gilt nur für die Daten- und Sensoranzeigen des aktiven Workouts. Das restliche Menü und 'Control Tracking' behalten das Standarddesign.` (`cockpit_theme_description`).
2. **Selection Control**:
   - Modern Material 3 `SingleChoiceSegmentedButtonRow` (or stylized choice chips/dropdown if 4 items require wrapping):
     - `System` (`cockpit_theme_system`)
     - `Dunkel` (`cockpit_theme_dark`)
     - `Hell` (`cockpit_theme_light`)
     - `Auto` (`cockpit_theme_auto`)
3. **Existing Display Toggles Maintained**:
   - `Hochformat erzwingen` (`forcePortrait`)
   - `Display immer an` (`keepScreenOn`)
   - `Keine Bildschirmsperre` (`noUnlocking`)
4. **Dialog Invariants Preserved**:
   - Follows `AppBottomSheetContent` (`REQ-UI-149`).
   - Follows `AppDialogActions.SaveCancel` (`REQ-UI-150`).

---

## 5. Architectural Implementation Strategy

### 5.1 Persistence Layer
- `TrainingApplication` / `SharedPreferences` (or `SettingsDataStore`):
  - Add `KEY_COCKPIT_THEME_MODE = "cockpit_theme_mode"` with default value `"system"`.
  - Provide getters/setters:
    - `getCockpitThemeMode(): CockpitThemeMode`
    - `setCockpitThemeMode(mode: CockpitThemeMode)`
    - Flow / LiveData support for reactive Compose recomposition.

### 5.2 Compose Scoping & Theme Injection
- In `TrackingTabsScreen.kt`:
  - Observe `cockpitThemeMode` as state.
  - Determine `isCockpitDark = resolveEffectiveCockpitDarkTheme(cockpitThemeMode, isSystemInDarkTheme())`.
  - For Page 0 (`ControlTrackingScreen`): Render with default ambient `MaterialTheme`.
  - For Pages 1..N (`TrackingTabGridContent`): Wrap within `ATrainingTrackerTheme(darkTheme = isCockpitDark) { ... }`.
  - This ensures that only the data fields and telemetry tiles receive the cockpit color scheme, isolating `ControlTrackingScreen` completely!

---

## 6. Call Site Audit & Affected Components

| Component / File | Purpose of Modification |
|:---|:---|
| [DisplaySettingsDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/display/DisplaySettingsDialog.kt) | Add Cockpit Theme selector with title, explanatory text, and 4-way mode selection. |
| [TrainingApplication.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java) | Add persistent SharedPreferences accessors for `CockpitThemeMode`. |
| [CockpitThemeMode.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/CockpitThemeMode.kt) | New enum defining `SYSTEM`, `ALWAYS_DARK`, `ALWAYS_LIGHT`, `AUTO` and resolution logic. |
| [TrackingTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt) | Apply localized `ATrainingTrackerTheme(darkTheme = isCockpitDark)` to pages > 0 while leaving Page 0 (`ControlTrackingScreen`) on default theme. |
| `strings.xml` & `strings_display.xml` (all 9 locales) | Add localized labels and descriptions across EN, DE, ES, FR, IT, JA, NL, PL, PT (`REQ-UI-106`). |
| [DisplaySettingsDialogTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/display/DisplaySettingsDialogTest.kt) | Unit tests verifying mode selection, persistence, and state transitions. |
| [CockpitThemeModeTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/theme/CockpitThemeModeTest.kt) | Pure logic tests verifying resolution of effective dark theme across modes, system state, and time of day. |

---

## 7. ASPICE Verification & Quality Gate Criteria

* **Gate 1 (Analysis Review)**:
  - Clear user motivation and problem definition.
  - Precise architectural boundary between Cockpit and Control Tracking.
  - Traceability to parent Epic `ATT-1157` and requirements.
* **Gate 2 (Test Specification)**:
  - Formulate atomic requirements in `docs/requirements.md` (`REQ-UI-168`).
  - Specify test cases in `docs/tests.md` (`TST-UI-120`).
* **Gate 3 (Implementation Plan)**:
  - Concrete step-by-step plan in `docs/engineering/plans/ATT-1267_plan.md`.
* **Gate 4 (Software Construction)**:
  - Code changes with gate verification (`python3 tools/jira_util.py check-gate`).
  - Strict 9-language translation parity (`TranslationParityTest`).
* **Gate 5 (Release & Verification)**:
  - Clean-room test suite execution (`./gradlew testDebugUnitTest`).
