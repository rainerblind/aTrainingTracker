# Test Specification & Requirement Synchronization - ATT-1267: [Settings] Independent Theme Selector for Workout Cockpit (Always Dark, System)

## 1. Feature Overview & Test Scope

* **Issue Key**: `ATT-1267`
* **Sub-tasks**: `ATT-1408` (Analysis [Erledigt]), `[Test-Spec]` (In Bearbeitung), `[Impl-Plan]`, `[Implementation]`, `[Test]`
* **Parent Issue**: `ATT-1267` (*[Feature] [Settings] Independent Theme Selector for Workout Cockpit (Always Dark, Auto Day/Night, System)*)
* **Parent Epic**: `ATT-1157` (*[Epic] Optimize dark mode*)
* **Target Version**: `V4.9.38` (Sprint `2026-39.3`)
* **Associated Requirements**: `REQ-UI-106` (Localization Parity), `REQ-UI-149` (AppModalBottomSheet), `REQ-UI-150` (AppDialogActions.SaveCancel), `REQ-UI-168` (Workout Cockpit Independent Theme Selector)
* **Associated Verification**: `TST-UI-120` (Workout Cockpit Theme Selection & Isolation Verification)

### Objective
Enable endurance athletes to select a high-contrast, battery-saving AMOLED Pure Black (`#000000`) theme specifically for the active workout tracking cockpit, independently of the Android operating system theme, while strictly preserving the standard application theme for general app navigation (Navigation Drawer, History, Periods, Settings Dialogs) and the pre-tracking configuration screen (`ControlTrackingScreen` - Page 0).

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: `REQ-UI-101` (*Neutral Backgrounds: The system SHALL use neutral white backgrounds (Light) or Material Surface (Dark) for all content areas...*) and `REQ-SET-052` (*Display Settings Dialog & State Parity*).
2. **Historical Origin & Commit Trace**: `REQ-UI-101` introduced in `ATT-100` / `ATT-200` to maintain clean, professional neutral backgrounds; `REQ-SET-052` introduced in `ATT-261` (commit `69e4f1a2`) and standardized in `ATT-1016` (commit `7bf4083d`) to house display-related options (`forcePortrait`, `keepScreenOn`, `noUnlocking`) within `DisplaySettingsDialog`.
3. **Root Reason for Existing Formulation**: Previously, the entire application uniformly delegated dark theme resolution to `isSystemInDarkTheme()`. No sub-component could override the theme without mutating the global OS mode or root Activity decor.
4. **Preservation of Core Invariants**: `DisplaySettingsDialog` strictly preserves `AppModalBottomSheet` (`REQ-UI-149`), strictly preserves `AppDialogActions.SaveCancel` with primary action "Speichern" (`REQ-UI-150`), existing display toggles (`forcePortrait`, `keepScreenOn`, `noUnlocking`) remain 100% functional, and the global app shell (Navigation Drawer, History, Periods, Dialogs) and `ControlTrackingScreen` (Page 0) continue to follow the ambient app theme.

---

## 2. Harmonized Requirement Specification (`REQ-UI-168`)

### REQ-UI-168: Workout Cockpit Independent Theme Selector (System vs Always Dark AMOLED)
The system SHALL provide an independent theme selector specifically for the workout tracking cockpit within Display Settings, enabling athletes to force AMOLED Pure Black mode during workouts while isolating non-cockpit screens (ATT-1267):

1. **Dedicated Setting in DisplaySettingsDialog**:
   - In `DisplaySettingsDialog.kt`, the system SHALL display a dedicated section titled `Cockpit-Design (während des Trainings)` (`@string/cockpit_theme_title`) with an explanatory subtitle `@string/cockpit_theme_description` clarifying that the setting applies exclusively to active workout data and sensor displays, while menus and "Control Tracking" retain the standard theme.
   - The selector SHALL provide a Material 3 `SingleChoiceSegmentedButtonRow` offering 2 mutually exclusive modes:
     - **Systemstandard** (`@string/cockpit_theme_system`, `CockpitThemeMode.SYSTEM`, default): Follows the Android operating system theme (`isSystemInDarkTheme()`).
     - **Immer Dunkel (AMOLED)** (`@string/cockpit_theme_always_dark`, `CockpitThemeMode.ALWAYS_DARK`): Renders the cockpit in high-contrast AMOLED Pure Black (`#000000`), regardless of whether the Android OS is set to Light or Dark.
   - The selected mode SHALL be staged in dialog state upon selection. Tapping "Abbrechen" SHALL discard changes without mutating preferences; tapping "Speichern" SHALL commit the selection to persistent storage and dismiss the dialog.

2. **Persistent Storage & Lifecycle**:
   - In `TrainingApplication`, the system SHALL persist the selected mode under SharedPreferences key `KEY_COCKPIT_THEME_MODE = "cockpit_theme_mode"` (default `"system"`), exposing `getCockpitThemeMode(): CockpitThemeMode` and `setCockpitThemeMode(mode: CockpitThemeMode)`.

3. **Cockpit Scope Boundary & Theme Isolation**:
   - In `TrackingTabsScreen.kt`, the system SHALL observe `cockpitThemeMode` and determine the effective cockpit theme:
     $$\text{isCockpitDark} = \begin{cases} \text{true} & \text{if } \text{mode} == \text{ALWAYS\_DARK} \\ \text{isSystemInDarkTheme()} & \text{if } \text{mode} == \text{SYSTEM} \end{cases}$$
   - **Workout Cockpit (Pages 1..N)**: The telemetry pages (`TrackingTabGridContent`), sensor tiles, dynamic gauges, live map, elevation profile, live segments, and tab preview/config headers SHALL be enclosed in `ATrainingTrackerTheme(darkTheme = isCockpitDark)`.
   - **Control Tracking (Page 0)**: `ControlTrackingScreen` (device pairing, protocol selection, sport picker, start/pause/stop) SHALL remain enclosed in the default ambient app theme and SHALL NOT be inverted to dark mode when `isSystemInDarkTheme() == false`.
   - **Global App Shell**: All other activities, fragments, and Compose destinations (Navigation Drawer, History, Periods, Settings Dialogs) SHALL remain completely unaffected by the cockpit theme setting.

4. **100% Localization Parity Across 9 Locales**:
   - All user-facing strings (`cockpit_theme_title`, `cockpit_theme_description`, `cockpit_theme_system`, `cockpit_theme_always_dark`) SHALL be translated across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero untranslated fallbacks and strict positional format compliance (`REQ-UI-106`, `REQ-UI-122`).

---

## 3. Detailed Test Specification (`TST-UI-120`)

### TST-UI-120: Workout Cockpit Theme Selection & Isolation Verification

1. **Pure Logic & Theme Resolution Verification (`CockpitThemeModeTest.kt`)**:
   - *Test 1.1*: When `mode == CockpitThemeMode.SYSTEM` and `isSystemDark == false`, assert `resolveEffectiveCockpitDarkTheme(mode, isSystemDark) == false`.
   - *Test 1.2*: When `mode == CockpitThemeMode.SYSTEM` and `isSystemDark == true`, assert `resolveEffectiveCockpitDarkTheme(mode, isSystemDark) == true`.
   - *Test 1.3*: When `mode == CockpitThemeMode.ALWAYS_DARK` and `isSystemDark == false`, assert `resolveEffectiveCockpitDarkTheme(mode, isSystemDark) == true`.
   - *Test 1.4*: When `mode == CockpitThemeMode.ALWAYS_DARK` and `isSystemDark == true`, assert `resolveEffectiveCockpitDarkTheme(mode, isSystemDark) == true`.
   - *Test 1.5*: Verify `CockpitThemeMode.fromId("always_dark") == CockpitThemeMode.ALWAYS_DARK`, `CockpitThemeMode.fromId("system") == CockpitThemeMode.SYSTEM`, and unknown/null string falls back to `CockpitThemeMode.SYSTEM`.

2. **Persistence & Defaults Verification (`TrainingApplicationTest.kt` or `DisplaySettingsTest.kt`)**:
   - *Test 2.1*: Ensure default value of `getCockpitThemeMode()` is `CockpitThemeMode.SYSTEM` when SharedPreferences key is unset.
   - *Test 2.2*: Call `setCockpitThemeMode(CockpitThemeMode.ALWAYS_DARK)`. Verify `getCockpitThemeMode() == CockpitThemeMode.ALWAYS_DARK` and underlying SharedPreferences stores `"always_dark"`.
   - *Test 2.3*: Call `setCockpitThemeMode(CockpitThemeMode.SYSTEM)`. Verify `getCockpitThemeMode() == CockpitThemeMode.SYSTEM` and underlying SharedPreferences stores `"system"`.

3. **DisplaySettingsDialog UI Staging & Action Semantics (`DisplaySettingsDialogTest.kt`)**:
   - *Test 3.1*: When dialog is displayed, verify the segmented button row reflects the persisted `CockpitThemeMode`.
   - *Test 3.2*: When the user selects `ALWAYS_DARK` and taps "Abbrechen", verify SharedPreferences is NOT modified and dialog dismisses cleanly.
   - *Test 3.3*: When the user selects `ALWAYS_DARK` and taps "Speichern", verify `setCockpitThemeMode(CockpitThemeMode.ALWAYS_DARK)` is executed, `onSettingsChanged` callback is invoked, and dialog dismisses cleanly.
   - *Test 3.4*: Verify existing toggles (`forcePortrait`, `keepScreenOn`, `noUnlocking`) continue to be staged and persisted in parallel with `cockpitThemeMode` without cross-field interference.

4. **Cockpit Scope Isolation Verification**:
   - *Test 4.1*: Verify `TrackingTabsScreen` binds Page 0 (`ControlTrackingScreen`) using the default ambient theme and Pages 1..N (`TrackingTabGridContent`) using `isCockpitDark`.
   - *Test 4.2*: Verify that toggling `CockpitThemeMode.ALWAYS_DARK` does not mutate `isSystemInDarkTheme()` or alter the theme of `AppNavigationDrawer`, `WorkoutSummariesTabbedScreen`, or `PeriodsScreen`.

5. **9-Language Localization & Positional Specifier Verification (`TranslationParityTest.kt`)**:
   - *Test 5.1*: Verify string resources exist in all 9 locales: `cockpit_theme_title`, `cockpit_theme_description`, `cockpit_theme_system`, `cockpit_theme_always_dark`.
   - *Test 5.2*: Execute `TranslationParityTest` and assert 0 missing strings.

6. **Clean-Room Regression Suite**:
   - Execute `./gradlew testDebugUnitTest` and confirm 100% pass rate across all unit tests with 0 failures and 0 regressions.
