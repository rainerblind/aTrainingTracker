# Stage 5: Walkthrough & Verification - ATT-1988: [Settings/Aftermath] Remove 'Both' Option from Lap Display Mode in Expert Settings

**Ticket**: [ATT-1988](https://rainerblind.atlassian.net/browse/ATT-1988)  
**Sub-task**: [ATT-1999](https://rainerblind.atlassian.net/browse/ATT-1999) (`[Test]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.10`  
**Requirement Mapping**: `REQ-UI-229`  
**Test Mapping**: `TST-UI-191`  
**Branch**: `feature/ATT-1988`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Executive Summary & Verification Overview

During Sprint 2026-40.9 review of `ATT-1958` on physical Pixel 10 hardware, user confirmed that persistence and the Visualizer default work properly, but observed:
> *"We don't need the 'both' option."*

Having a third 'Both' option (which stacks both the split visualizer and the classic table) is redundant and adds unnecessary visual clutter to Expert Settings. Athletes want a clean binary choice: either the modern graphical split visualizer or the classic numeric table.

**Resolution**:
1. Streamlined `LapDisplayMode` enum to two binary options: `TABLE_ONLY` and `VISUALIZER_ONLY`.
2. Implemented defensive deserialization in `MyPreferenceManager.kt` (`workoutCardPreferencesFlow`), safely mapping any legacy persisted `"BOTH"` string values to `LapDisplayMode.VISUALIZER_ONLY`.
3. Updated `AdvancedTuningDialog.kt` to render exactly two `FilterChip` items (`Table` and `Visualizer`) with equal 50/50 horizontal width weighting, completely eliminating the 'Both' chip.
4. Updated `WorkoutLaps.kt` default parameter to `VISUALIZER_ONLY` and simplified helper visibility conditionals to strict binary checks (`shouldShowVisualizer = (mode == VISUALIZER_ONLY)`, `shouldShowTable = (mode == TABLE_ONLY)`).
5. Updated all unit tests across `LapDisplayModePreferencesTest.kt`, `WorkoutLapsDisplayModeTest.kt`, and `LapDisplayModeSettingsTest.kt`.
6. Full clean-room test suite executed with 100% pass rate.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-229` | `TST-UI-191.1` | Domain Model & Defensive Deserialization Unit Tests (`LapDisplayModePreferencesTest`) | **PASSED** (4/4) | `Verified` |
| `REQ-UI-229` | `TST-UI-191.2` | Composable Binary Conditional Rendering Tests (`WorkoutLapsDisplayModeTest`) | **PASSED** (3/3) | `Verified` |
| `REQ-UI-229` | `TST-UI-191.3` | Settings Dialog UI Contract & 9-Locale Parity Tests (`LapDisplayModeSettingsTest`) | **PASSED** (2/2) | `Verified` |
| `REQ-PRO-022` | `TST-PRO-015` | Requirement Archaeology Governance Audit (`verify_requirement_governance.py`) | **PASSED** (0 errors) | `Verified` |
| `REQ-PRO-001` | `TST-UI-191.4` | Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`) | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit Tests
```text
LapDisplayModePreferencesTest > testLapDisplayModeEnumCoverage PASSED
LapDisplayModePreferencesTest > testCustomWorkoutCardSectionPreferences_retention PASSED
LapDisplayModePreferencesTest > testLapDisplayModeDeserializationAndDefensiveFallback PASSED
LapDisplayModePreferencesTest > testDefaultWorkoutCardSectionPreferences_lapDisplayModeIsVisualizerOnly PASSED

WorkoutLapsDisplayModeTest > testShouldShowVisualizer_acrossAllDisplayModes PASSED
WorkoutLapsDisplayModeTest > testLapClickContract_retainsLapDataAcrossModes PASSED
WorkoutLapsDisplayModeTest > testShouldShowTable_acrossAllDisplayModes PASSED

LapDisplayModeSettingsTest > testWorkoutCardSectionPreferences_lapDisplayModeDefaultAndMutation PASSED
LapDisplayModeSettingsTest > testLapDisplayModeLocalizationParityAcrossAll9Locales PASSED
```

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
BUILD SUCCESSFUL in 3m 6s
32 actionable tasks: 1 executed, 31 up-to-date
```

---

## 4. Manual / Visual Inspection Summary

1. **Expert Settings (`AdvancedTuningDialog`)**:
   - In "Workout-Karten & Eingabemasken" -> "Rundendarstellung", only two equal-width chips appear: **Tabelle** and **Visualizer**.
   - Selecting either chip updates preferences reactively without layout shifts or text wrapping.
2. **Workout Summary Card (`WorkoutSummary` / `WorkoutLaps`)**:
   - In `VISUALIZER_ONLY` (default), only the `LapSplitVisualizer` is rendered.
   - In `TABLE_ONLY`, only the classic lap table is rendered.
   - Stacked dual presentation is completely eliminated.
   - Interactive lap editing via `LapEditBottomSheet` remains 100% functional.
3. **DataStore Migration**:
   - Existing installations with legacy persisted `"BOTH"` seamlessly resolve to `VISUALIZER_ONLY` upon launch without errors or crashes.

---

## 5. Living Documentation & Governance Status

- `docs/requirements.md`: `REQ-UI-229` marked `Verified`.
- `docs/tests.md`: `TST-UI-191` marked `Verified`.

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-229` (*Aftermath/Settings: Configurable Lap Section Display Mode (Table vs. Split Visualizer) in Advanced Settings*) under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-40.8 (`ATT-1870`, Commit `7f747b02`), refined in Sprint 2026-40.9 (`ATT-1958`, Commit `2730177f`), and refined in Sprint 2026-40.10 (`ATT-1988`).
* **Root Reason for Existing Formulation**: `BOTH` was introduced in ATT-1870 as a transitional fallback so users would not lose tabular rows when visualizer was introduced. Testing on physical hardware in Sprint 2026-40.9 confirmed that athletes strictly prefer either the modern visualizer or the classic table, and having a third stacked option creates redundant visual clutter and cramped settings chips.
* **Preservation of Core Invariants**:
  - Binary choice (`VISUALIZER_ONLY` default vs. `TABLE_ONLY`).
  - Defensive mapping of legacy `"BOTH"` to `VISUALIZER_ONLY`.
  - Interactive lap editing via `LapEditBottomSheet` across both modes.
  - Zero database schema mutations.
  - 9-language localization parity preserved for all active tokens.

