# Stage 2: Requirement & Test Specification - ATT-1870: Configurable Lap Section Display Mode (Table vs. Split Visualizer) in Advanced Settings

**Ticket**: [ATT-1870](https://rainerblind.atlassian.net/browse/ATT-1870)  
**Sub-task**: [ATT-1938](https://rainerblind.atlassian.net/browse/ATT-1938) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Formal Requirement Specification: `REQ-UI-229`

### 1.1 Requirement Statement
The system SHALL provide a configurable preference in Advanced Settings allowing athletes to select their preferred lap presentation mode in the Workout Summary card (`WorkoutSummary.kt` / `WorkoutLaps.kt`), preventing redundant stacked representations while maintaining tabular lap editing across all modes (ATT-1870):

1. **Lap Display Mode Domain Model (`LapDisplayMode.kt`)**:
   - The system SHALL define `enum class LapDisplayMode`: `TABLE_ONLY`, `VISUALIZER_ONLY`, `BOTH`.
2. **Preference Storage & DataStore Persistence (`MyPreferenceManager.kt`)**:
   - `WorkoutCardSectionPreferences` SHALL include `val lapDisplayMode: LapDisplayMode = LapDisplayMode.BOTH`.
   - It SHALL be persisted to DataStore key `workout_card_lap_display_mode`.
   - Deserialization SHALL fall back defensively to `LapDisplayMode.BOTH` upon null, empty, or unrecognized enum values.
   - Factory reset (`resetToDefaults()`) SHALL restore `LapDisplayMode.BOTH`.
3. **Advanced Settings UI Selector (`AdvancedTuningDialog.kt`)**:
   - In `WorkoutMasksAndCardsSection` (`TuningSection.WORKOUT_MASKS_CARDS`), when `workoutCardPrefs.showLaps` is enabled (`true`), the dialog SHALL render a selector row with label `R.string.settings_lap_display_mode_title` and 3 `FilterChip` options:
     - `Table` (`TABLE_ONLY` / `R.string.settings_lap_display_mode_table`)
     - `Visualizer` (`VISUALIZER_ONLY` / `R.string.settings_lap_display_mode_visualizer`)
     - `Both` (`BOTH` / `R.string.settings_lap_display_mode_both`)
   - When `workoutCardPrefs.showLaps` is disabled (`false`), the selector row SHALL be omitted from the UI.
4. **Conditional Rendering in WorkoutLaps (`WorkoutLaps.kt`)**:
   - `WorkoutLaps` SHALL accept `lapDisplayMode: LapDisplayMode = LapDisplayMode.BOTH`.
   - When `lapDisplayMode` is `BOTH` or `VISUALIZER_ONLY`, `LapSplitVisualizer` SHALL be rendered.
   - When `lapDisplayMode` is `BOTH` or `TABLE_ONLY`, `LapTableHeader`, `LapRow` items, and the expandable table toggle button SHALL be rendered.
   - When `lapDisplayMode` is `VISUALIZER_ONLY`, `LapTableHeader`, `LapRow` items, and the table toggle button SHALL NOT be rendered.
   - When `lapDisplayMode` is `TABLE_ONLY`, `LapSplitVisualizer` SHALL NOT be rendered.
   - Interactive lap editing: Tapping a split in `LapSplitVisualizer` or a row in `LapRow` SHALL dispatch `onLapClick` with the target `LapData`, opening `LapEditBottomSheet` across all modes.
5. **9-Language Localization Parity**:
   - All display mode labels SHALL be localized across English, German, Spanish, French, Italian, Japanese, Dutch, Polish, and Portuguese.
6. **Preservation of Core Invariants**:
   - Single-thread SQLite confinement and `lapsTable` remain untouched.
   - Default for existing and new users remains `BOTH`, ensuring zero visual regression.
   - Map polyline highlighting on `TrackOnMapScreen.kt` remains completely decoupled.

---

### 1.2 Given-When-Then Acceptance Criteria

- **AC-1 (Advanced Settings Mode Selection)**:
  - *Given* an athlete navigating to Advanced Settings (`AdvancedTuningDialog`),
  - *When* expanding the Workout-Masken & Detailkarten section while `Rundenübersicht` is enabled,
  - *Then* a 3-way selector row (`Table`, `Visualizer`, `Both`) SHALL be displayed with the active preference highlighted.
  - *When* the athlete taps `Visualizer`,
  - *Then* the preference state SHALL update immediately and persist to DataStore.

- **AC-2 (Visualizer-Only Rendering in Workout Summary)**:
  - *Given* `lapDisplayMode` set to `VISUALIZER_ONLY`,
  - *When* viewing a workout with recorded laps in `WorkoutSummary`,
  - *Then* only `LapSplitVisualizer` SHALL be rendered.
  - *And* `LapTableHeader`, table `LapRow` items, and the table expansion button SHALL NOT be rendered.

- **AC-3 (Table-Only Rendering in Workout Summary)**:
  - *Given* `lapDisplayMode` set to `TABLE_ONLY`,
  - *When* viewing a workout with recorded laps in `WorkoutSummary`,
  - *Then* only `LapTableHeader`, `LapRow` items, and the table expansion button SHALL be rendered.
  - *And* `LapSplitVisualizer` SHALL NOT be rendered.

- **AC-4 (Both Mode Rendering & Backward Compatibility)**:
  - *Given* `lapDisplayMode` set to `BOTH` (or default on fresh install),
  - *When* viewing a workout with recorded laps in `WorkoutSummary`,
  - *Then* both `LapSplitVisualizer` and the classic table (`LapTableHeader` + `LapRow`) SHALL be rendered stacked cleanly.

- **AC-5 (Lap Editing Invariant in Visualizer-Only Mode)**:
  - *Given* `lapDisplayMode` set to `VISUALIZER_ONLY`,
  - *When* the athlete taps on a split row in `LapSplitVisualizer`,
  - *Then* `LapEditBottomSheet` SHALL open for that lap, permitting custom title/description edits.

---

## 2. Test Specification: `TST-UI-183`

### 2.1 Test Cases & Execution Strategy

#### Test Case 1: DataStore & Preference Serialization (`LapDisplayModePreferencesTest.kt`)
- **Objective**: Verify that `LapDisplayMode` serializes, deserializes, and falls back safely in `MyPreferenceManager`.
- **Method**: JVM Unit Test.
- **Assertions**:
  - `LapDisplayMode` contains exact enum values: `TABLE_ONLY`, `VISUALIZER_ONLY`, `BOTH`.
  - Default `WorkoutCardSectionPreferences().lapDisplayMode` equals `LapDisplayMode.BOTH`.
  - Serializing each enum value and deserializing recovers identical enum instance.
  - Deserializing null, empty string, or invalid string `"INVALID_MODE"` returns `LapDisplayMode.BOTH`.

#### Test Case 2: Composable Conditional Rendering (`WorkoutLapsDisplayModeTest.kt`)
- **Objective**: Verify that `WorkoutLaps` conditionally includes or excludes `LapSplitVisualizer` and table rows according to `lapDisplayMode`.
- **Method**: JVM Composable / Contract Test.
- **Assertions**:
  - When `lapDisplayMode == BOTH`: both `LapSplitVisualizer` and `LapTableHeader` are present in source composition tree.
  - When `lapDisplayMode == TABLE_ONLY`: `LapTableHeader` is present and `LapSplitVisualizer` is absent.
  - When `lapDisplayMode == VISUALIZER_ONLY`: `LapSplitVisualizer` is present and `LapTableHeader` is absent.
  - When in `VISUALIZER_ONLY` mode and tapping a split, `onLapClick` is invoked with the corresponding `LapData`.

#### Test Case 3: Settings UI & 9-Language Localization Audit (`LapDisplayModeSettingsTest.kt`)
- **Objective**: Verify that the settings selector exists in `WorkoutMasksAndCardsSection` and string resources are translated across all 9 locales.
- **Method**: Contract & Resource Audit Test.
- **Assertions**:
  - `AdvancedTuningDialog.kt` contains `LapDisplayMode` filter chips inside `WorkoutMasksAndCardsSection`.
  - `FilterChip` items exist for `TABLE_ONLY`, `VISUALIZER_ONLY`, and `BOTH`.
  - The 4 string tokens (`settings_lap_display_mode_title`, `settings_lap_display_mode_table`, `settings_lap_display_mode_visualizer`, `settings_lap_display_mode_both`) are defined in:
    - `res/values/strings.xml`
    - `res/values-de/strings.xml`
    - `res/values-es/strings.xml`
    - `res/values-fr/strings.xml`
    - `res/values-it/strings.xml`
    - `res/values-ja/strings.xml`
    - `res/values-nl/strings.xml`
    - `res/values-pl/strings.xml`
    - `res/values-pt/strings.xml`

#### Test Case 4: Full-Suite Regression Verification
- **Objective**: Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions.

---

## 3. Requirement-to-Test Traceability Matrix

| Requirement Clause | Test Spec ID | Target Test File | Assertion / Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-UI-229.1` (Enum Definition) | `TST-UI-183.1` | `LapDisplayModePreferencesTest` | Verify all 3 enum values present |
| `REQ-UI-229.2` (Persistence & Fallback) | `TST-UI-183.1` | `LapDisplayModePreferencesTest` | Verify DataStore key, default `BOTH`, and invalid string fallback |
| `REQ-UI-229.3` (Settings UI Selector) | `TST-UI-183.3` | `LapDisplayModeSettingsTest` | Verify `FilterChip` selector in `WorkoutMasksAndCardsSection` |
| `REQ-UI-229.4` (Conditional Rendering) | `TST-UI-183.2` | `WorkoutLapsDisplayModeTest` | Verify split visualizer / table presence and lap click callback |
| `REQ-UI-229.5` (9-Language Localization) | `TST-UI-183.3` | `LapDisplayModeSettingsTest` | 100% translation parity across 9 resource dirs |
| `REQ-PRO-001` (Clean-Room Full Suite) | `TST-UI-183.4` | `./gradlew testDebugUnitTest` | Full regression run (zero regressions) |
