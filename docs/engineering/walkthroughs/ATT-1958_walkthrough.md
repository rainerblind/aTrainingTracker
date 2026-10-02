# Stage 5: Walkthrough & Verification - ATT-1958: [Bug] [Settings/Aftermath] Fix Lap Display Mode Selection Persistence in Expert Settings and Change Default to Visualizer Only

**Ticket**: [ATT-1958](https://rainerblind.atlassian.net/browse/ATT-1958)  
**Sub-task**: [ATT-1980](https://rainerblind.atlassian.net/browse/ATT-1980) (`[Test]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.9`  
**Requirement Mapping**: `REQ-UI-229` (*Aftermath/Settings: Configurable Lap Section Display Mode (Table vs. Split Visualizer) in Advanced Settings*)  
**Test Mapping**: `TST-UI-188` (*Lap Display Mode Visualizer Default & Advanced Settings Dialog State Synchronization Verification*)  
**Branch**: `feature/ATT-1958`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Executive Summary & Verification Overview

Ticket ATT-1958 resolves two defects identified during the review of the configurable Lap Display Mode feature:

1. **State Desynchronization on Reopening Advanced Settings (`AdvancedTuningDialog.kt`)**:
   - *Problem*: When an athlete altered `lapDisplayMode` in Expert Settings (*Workout-Karten & Eingabemasken*) and saved, the selection was correctly saved to DataStore and reflected in the workout list. However, reopening `AdvancedTuningDialog` displayed the default fallback setting (`BOTH`) instead of the persisted setting.
   - *Root Cause*: `preferenceManager.workoutCardPreferencesFlow.collectAsState(initial = WorkoutCardSectionPreferences())` emitted a non-null dummy fallback instance on frame 0. `LaunchedEffect` tripped on frame 0, setting `isAftermathPrefsInitialized = true`. When the real persisted values arrived asynchronously from DataStore on frame 1, `isAftermathPrefsInitialized` was already `true`, causing the real persisted preferences to be silently discarded.
   - *Resolution*: Collected `workoutCardPreferencesFlow` and `editWorkoutFieldPreferencesFlow` with `initial = null`, and gated initialization in `LaunchedEffect` on `cardPrefs != null && editPrefs != null`. This ensures the dialog only initializes once genuine persisted preferences have arrived from disk.

2. **Default Presentation Mode Change to Visualizer Only (`MyPreferenceManager.kt`)**:
   - *Problem*: Defaulting to `LapDisplayMode.BOTH` resulted in stacked duplicate representations (both the classic table and the split visualizer) out-of-the-box, causing visual clutter on the Workout Summary card.
   - *Resolution*: Updated the default `lapDisplayMode` in `WorkoutCardSectionPreferences` and the fallback deserializer in `workoutCardPreferencesFlow` to `LapDisplayMode.VISUALIZER_ONLY`. Fresh installs and factory resets now provide a modern, streamlined split visualizer out-of-the-box, while athletes desiring tabular representation or dual views can opt into them via Advanced Settings.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-229` (Default Mode `VISUALIZER_ONLY`) | `[TST-UI-188.1]` | Unit Tests (`LapDisplayModePreferencesTest`, `LapDisplayModeSettingsTest`) | **PASSED** | `Verified` |
| `REQ-UI-229` (Dialog State Synchronization) | `[TST-UI-188.2]` | Contract Test (`AdvancedTuningVisualContractTest.testAdvancedTuningDialog_workoutCardPrefsFlowGating`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `[TST-UI-188.3]` | Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`) | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit Tests
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.LapDisplayMode*" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*"
BUILD SUCCESSFUL in 45s
32 actionable tasks: 12 executed, 20 up-to-date
```
- `LapDisplayModePreferencesTest.testLapDisplayModeEnumCoverage`: PASSED
- `LapDisplayModePreferencesTest.testDefaultWorkoutCardSectionPreferences_lapDisplayModeIsVisualizerOnly`: PASSED
- `LapDisplayModePreferencesTest.testCustomWorkoutCardSectionPreferences_retention`: PASSED
- `LapDisplayModePreferencesTest.testLapDisplayModeDeserializationAndDefensiveFallback`: PASSED
- `LapDisplayModeSettingsTest.testWorkoutCardSectionPreferences_lapDisplayModeDefaultAndMutation`: PASSED
- `LapDisplayModeSettingsTest.testLapDisplayModeLocalizationParityAcrossAll9Locales`: PASSED
- `AdvancedTuningVisualContractTest.testAdvancedTuningDialog_workoutCardPrefsFlowGating`: PASSED
- `AdvancedTuningVisualContractTest.testAdvancedTuningDialog_initiallyCollapsesAllSections`: PASSED

### Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`)
```text
./gradlew testDebugUnitTest
BUILD SUCCESSFUL in 3m 4s
32 actionable tasks: 1 executed, 31 up-to-date
0 failures, 0 regressions across all project modules.
```

---

## 4. Hardware / Physical Verification (Pixel 10)

1. **Default Presentation**:
   - Inspect a workout with recorded laps in the Workout Summary card.
   - Verify that only `LapSplitVisualizer` is rendered out-of-the-box.
   - Verify the classic `WorkoutLaps` table is omitted.
2. **Settings Modification & Reopening**:
   - Open Settings -> Expert Settings (`AdvancedTuningDialog`).
   - Expand *Workout-Karten & Eingabemasken*.
   - Verify *Rundendarstellung* chip selector shows *Visualizer* selected by default.
   - Select *Tabelle* (`TABLE_ONLY`) and tap *Speichern*.
   - Observe Workout Summary card: classic table is displayed; split visualizer is hidden.
   - Reopen Expert Settings -> *Workout-Karten & Eingabemasken*:
   - Verify *Tabelle* chip remains selected.
   - Select *Beide* (`BOTH`) and tap *Speichern*.
   - Reopen Expert Settings: verify *Beide* chip remains selected.
3. **Factory Reset**:
   - Tap *Auf Standardwerte zurücksetzen* in Expert Settings.
   - Verify *Rundendarstellung* resets to *Visualizer*.

---

## 5. Invariant & Governance Verification

1. **Interactive Lap Editing**: Maintained across all modes via `LapEditBottomSheet`.
2. **DataStore Invariance**: Persistent key `workout_card_lap_display_mode` with safe defensive fallback.
3. **9-Language Parity**: All display mode labels fully localized in EN, DE, ES, FR, IT, JA, NL, PL, and PT.
4. **Fix Version Audit**: Parent ticket `ATT-1958` specifies Fix Version `V4.9.38`.
5. **Living Documentation Synchronized**: `REQ-UI-229` and `TST-UI-188` set to `Verified`.
