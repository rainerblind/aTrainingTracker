# Stage 2: Requirement & Test Specification - ATT-1958: [Bug] [Settings/Aftermath] Fix Lap Display Mode Selection Persistence in Expert Settings and Change Default to Visualizer Only

**Ticket**: [ATT-1958](https://rainerblind.atlassian.net/browse/ATT-1958)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.9`  
**Requirement Mapping**: `REQ-UI-229` (*Aftermath/Settings: Configurable Lap Section Display Mode (Table vs. Split Visualizer) in Advanced Settings*)  
**Test Spec ID**: `TST-UI-188`  
**Branch**: `feature/ATT-1958`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Requirement Specification (REQ-UI-229 Refinement)

### 1.1 Problem Statement & Rationale
During Sprint Review of ATT-1870, two issues were identified regarding `lapDisplayMode` in `AdvancedTuningDialog.kt` and `MyPreferenceManager.kt`:
1. **Dialog State Desynchronization**: When an athlete alters `lapDisplayMode` via the FilterChip selector in the Advanced Settings dialog and saves, the preference persists and applies to workout summaries. However, when reopening `AdvancedTuningDialog`, the chip selector fails to display the persisted preference because `preferenceManager.workoutCardPreferencesFlow.collectAsState(initial = WorkoutCardSectionPreferences())` provides a dummy initial object on frame 0, prematurely tripping `isAftermathPrefsInitialized = true` and silently dropping the real persisted preferences arriving on frame 1.
2. **Default Presentation Mode**: Stacked dual representations (`BOTH`) out-of-the-box create visual clutter and redundant split metrics. Setting `LapDisplayMode.VISUALIZER_ONLY` as the default for fresh installations and factory resets ensures a clean, modern post-workout digest out-of-the-box while preserving athlete choice via Advanced Settings.

### 1.2 Functional & Architectural Requirements
The system SHALL refine `REQ-UI-229` to enforce state synchronization in `AdvancedTuningDialog.kt` and default `LapDisplayMode.VISUALIZER_ONLY` in `MyPreferenceManager.kt`:

1. **Default Mode & Fallback Specification (`MyPreferenceManager.kt`)**:
   - `WorkoutCardSectionPreferences` SHALL define `val lapDisplayMode: LapDisplayMode = LapDisplayMode.VISUALIZER_ONLY`.
   - In `workoutCardPreferencesFlow`, if `preferences[WORKOUT_CARD_LAP_DISPLAY_MODE]` is null or corrupted, deserialization SHALL defensively fall back to `LapDisplayMode.VISUALIZER_ONLY`.
   - Factory reset (`resetToDefaults()`) SHALL restore `lapDisplayMode` to `LapDisplayMode.VISUALIZER_ONLY`.

2. **Advanced Settings State Synchronization (`AdvancedTuningDialog.kt`)**:
   - `AdvancedTuningDialog` SHALL collect `workoutCardPreferencesFlow` and `editWorkoutFieldPreferencesFlow` with `initial = null`.
   - The dialog SHALL synchronize `workoutCardPrefs` and `editWorkoutPrefs` from the flows when the first non-null emission occurs, ensuring real persisted preferences from DataStore are accurately reflected in the UI upon opening the dialog.
   - When the athlete changes chips and taps Save, the selected `lapDisplayMode` SHALL be persisted to DataStore via `preferenceManager.setWorkoutCardPreferences(workoutCardPrefs)`.

3. **Preservation of Core Invariants**:
   - Interactive lap editing: Tapping a split in `LapSplitVisualizer` or a row in `LapRow` opens `LapEditBottomSheet` across all modes.
   - SQLite single-thread confinement remains untouched.
   - 100% 9-language localization parity across English, German, Spanish, French, Italian, Japanese, Dutch, Polish, and Portuguese.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-229`), refining and extending `REQ-UI-210`, `REQ-UI-216`, and `REQ-UI-204` under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*). Refined in ATT-1958.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.8 (`ATT-1870`, Commit `7f747b02`) and Sprint 2026-40.9 (`ATT-1958`).
3. *Root Reason for Existing Formulation*: In ATT-1870, `BOTH` was initially chosen as default to preserve visual backward compatibility, but user feedback confirmed that stacked views create unnecessary clutter and `VISUALIZER_ONLY` provides the cleanest out-of-the-box experience. Furthermore, using a dummy initial state in `collectAsState` created a race condition with `isAftermathPrefsInitialized` that dropped the real persisted values upon reopening the dialog.
4. *Preservation of Core Invariants*: Full backward compatibility with user preference selection, interactive lap editing via `LapEditBottomSheet` in all modes, zero database schema mutations, and 9-language localization parity are 100% preserved.

### Acceptance Criteria (Given-When-Then)
- **AC-1 (Default Preference Out-of-the-Box & Reset)**:
  - *Given* an athlete with a fresh app installation or after tapping *Auf Standardwerte zurücksetzen* in Advanced Settings,
  - *When* inspecting `lapDisplayMode` in `WorkoutCardSectionPreferences` or DataStore,
  - *Then* `lapDisplayMode` SHALL equal `LapDisplayMode.VISUALIZER_ONLY`.
- **AC-2 (Dialog Persistence & State Reflection)**:
  - *Given* an athlete selecting any `lapDisplayMode` (`Table`, `Visualizer`, `Both`) in `AdvancedTuningDialog` and tapping Save,
  - *When* reopening `AdvancedTuningDialog`,
  - *Then* the 3-way `FilterChip` selector SHALL accurately display the persisted mode as selected.
- **AC-3 (Workout Summary Presentation)**:
  - *Given* a workout with recorded laps,
  - *When* `lapDisplayMode` is `VISUALIZER_ONLY`,
  - *Then* only `LapSplitVisualizer` SHALL be rendered, and the classic table (`LapTableHeader` and rows) SHALL be omitted.

---

## 2. Test Specification (TST-UI-188)

### 2.1 Unit & Contract Tests
1. **Preferences & DataStore Default Verification (`LapDisplayModePreferencesTest.kt`)**:
   - Assert `WorkoutCardSectionPreferences()` initializes `lapDisplayMode` to `LapDisplayMode.VISUALIZER_ONLY`.
   - Assert deserialization fallback for unknown/empty string returns `LapDisplayMode.VISUALIZER_ONLY`.
2. **Settings Dialog ViewModel/Manager Verification (`LapDisplayModeSettingsTest.kt`)**:
   - Assert default preferences produce `LapDisplayMode.VISUALIZER_ONLY`.
   - Assert factory reset restores `LapDisplayMode.VISUALIZER_ONLY`.
3. **AdvancedTuningDialog State Synchronization Contract (`AdvancedTuningVisualContractTest.kt`)**:
   - Assert `AdvancedTuningDialog.kt` collects `workoutCardPreferencesFlow` and `editWorkoutFieldPreferencesFlow` with `initial = null`.
   - Assert `workoutCardPrefs` is gated on non-null emission, preventing the initial fallback dummy object from race-condition locking out real DataStore values.

### 2.2 Regression Verification
- Run targeted unit test suite:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.LapDisplayMode*" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*"
  ```
- Run clean-room full test suite in Stage 5:
  ```bash
  ./gradlew testDebugUnitTest
  ```

---

## 3. Traceability Matrix

| Requirement | Test Spec | Verification Method | Target Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-229` (Default Mode `VISUALIZER_ONLY`) | `TST-UI-188.1` | Unit Test (`LapDisplayModePreferencesTest.kt`, `LapDisplayModeSettingsTest.kt`) | `Approved` |
| `REQ-UI-229` (Dialog State Synchronization) | `TST-UI-188.2` | Contract Test (`AdvancedTuningVisualContractTest.kt`) | `Approved` |
| `REQ-PRO-001` (Clean-Room Full Suite) | `TST-UI-188.3` | Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`) | `Approved` |
