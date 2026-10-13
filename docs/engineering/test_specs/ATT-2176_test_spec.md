# Stage 2: Requirement & Test Specification - ATT-2176: User-Customizable Section Reordering and Modular Section Architecture for Workout Summaries and Details

**Ticket**: [ATT-2176](https://rainerblind.atlassian.net/browse/ATT-2176)  
**Sub-task**: [ATT-2201](https://rainerblind.atlassian.net/browse/ATT-2201) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Requirement Mapping**: `REQ-UI-255` (*Aftermath: User-Customizable Section Reordering, Unified Order Persistence & Modular Section Architecture for Workout Summaries and Details*)  
**Test Spec ID**: `TST-UI-214`  
**Branch**: `feature/ATT-2176`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Requirement Specification (REQ-UI-255)

### 1.1 Problem Statement & Rationale
Currently, athletes can toggle the visibility of individual sections (Description, Extrema, Laps, Strava, Map, Elevation Profile, Telemetry Charts, Zone Distribution) in Advanced Settings (`AdvancedTuningDialog.kt` per `REQ-UI-229` and `REQ-UI-240`). However, two architectural limitations exist:
1. **Hardcoded Vertical Order**: The display sequence of these sections is hard-coded in both `WorkoutSummary.kt` and `TrackOnMapScreen.kt` / `MapDetailLayout.kt`. Different athletes have different post-workout priorities (e.g. data-focused athletes want Telemetry Charts and Zones first, while trail runners prefer Map and Elevation first).
2. **Duplicated Section Layout Boilerplate**: Section composition and wrapping logic are duplicated between `WorkoutSummary.kt` and `TrackOnMapScreen.kt`.

### 1.2 Functional & Architectural Requirements
The system SHALL allow athletes to customize the vertical display sequence of workout sections and ensure consistent, modular rendering across both list cards (`WorkoutSummary.kt`) and detailed workout screens (`TrackOnMapScreen.kt` / `MapDetailLayout.kt`):
1. **Section Domain Model (`WorkoutSectionType`)**:
   - The system SHALL define an enum `WorkoutSectionType` with values: `DESCRIPTION`, `EXTREMA`, `LAPS`, `STRAVA`, `MAP`, `ELEVATION`, `CHARTS`, `ZONES`.
   - The canonical default sequence SHALL be `[DESCRIPTION, EXTREMA, LAPS, STRAVA, MAP, ELEVATION, CHARTS, ZONES]`.
2. **Display Context Separation (`WorkoutDisplayContext`)**:
   - The system SHALL define `WorkoutDisplayContext` with `LIST_CARD` (passive touch, click opens details) and `FULL_DETAIL` (interactive scrubbing, zoom toolbar, lap map highlights).
3. **Unified Order Persistence (`MyPreferenceManager.kt`)**:
   - `MyPreferenceManager` SHALL persist the active section sequence in user preferences DataStore under `WORKOUT_SECTIONS_ORDER`.
   - If uninitialized or invalid, it SHALL fall back safely to the canonical default order.
   - If any enum values are omitted from stored preferences (e.g. newly introduced sections), the system SHALL append the missing values to ensure no sections are dropped.
4. **Advanced Settings Reordering UI (`AdvancedTuningDialog.kt`)**:
   - The section matrix in `AdvancedTuningDialog.kt` SHALL render rows according to the active `workoutSectionsOrder`.
   - Each row SHALL feature accessible reorder controls (`Move Up` / `Move Down` buttons) to dynamically adjust its position in the sequence, updating DataStore reactively.
   - The `Move Up` action SHALL be disabled for the first row, and `Move Down` SHALL be disabled for the last row.
5. **Dynamic Order Execution in WorkoutSummary (`WorkoutSummary.kt`)**:
   - `WorkoutSummary.kt` SHALL maintain `WorkoutHeader` and `WorkoutDetails` permanently at the top as the fixed identity anchor.
   - All subsequent enabled sections SHALL be rendered in the exact order specified by `workoutSectionsOrder`.
6. **Dynamic Relative Slotting in MapDetailLayout (`TrackOnMapScreen.kt`)**:
   - In `TrackOnMapScreen.kt`, enabled sections appearing prior to `MAP` in `workoutSectionsOrder` SHALL be slotted above the map viewport into `metadataContent`.
   - Enabled sections appearing after `MAP` in `workoutSectionsOrder` SHALL be slotted below the map into `MapDetailLayout` / `analyticsContent`.
7. **Preserved Invariants**:
   - Fixed anchor identity: `WorkoutHeader` and primary details (`Distance`, `Time`, `Speed/Pace`) remain permanently at the top.
   - Minimum map height protection: `SplitPaneMath.MIN_MAP_HEIGHT` (120dp) remains strictly guaranteed (`REQ-UI-250`).
   - Independent section visibility preferences (`REQ-UI-229`, `REQ-UI-240`) remain fully functional.
   - 100% 9-language localization parity (`REQ-LOC-001`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Reorder Action Execution)**:
  * *Given* the Advanced Settings dialog opened in the Workout Cards section,
  * *When* the athlete taps Move Up or Move Down on a section row,
  * *Then* the row moves to the new position and the updated sequence is immediately saved to DataStore.
* **Criterion 2 (Boundary Controls Gating)**:
  * *Given* the section matrix in Advanced Settings,
  * *When* inspecting the first row,
  * *Then* the Move Up button SHALL be disabled.
  * *When* inspecting the last row,
  * *Then* the Move Down button SHALL be disabled.
* **Criterion 3 (Dynamic Summary List Execution)**:
  * *Given* a custom section sequence where `CHARTS` and `ZONES` precede `MAP` and `EXTREMA`,
  * *When* viewing workout cards in the list (`WorkoutSummary.kt`),
  * *Then* the sections render in the exact custom order: `Header` -> `Details` -> `Charts` -> `Zones` -> `Map` -> `Extrema`.
* **Criterion 4 (Dynamic Relative Detail Slotting)**:
  * *Given* the same custom sequence where `CHARTS` and `ZONES` precede `MAP`,
  * *When* viewing the detailed workout screen (`TrackOnMapScreen.kt`),
  * *Then* `Charts` and `Zones` render in `metadataContent` above the map, while `Extrema` renders below the map.
* **Criterion 5 (Safe Defaults & Self-Healing)**:
  * *Given* an existing app installation with uninitialized section order,
  * *When* the app starts,
  * *Then* the default sequence is applied and all existing visibility preferences remain preserved.

---

## 2. Test Specification (TST-UI-214)

### Test Case 1: `WorkoutSectionOrderPersistenceTest` (`TST-UI-214.1`)
* **Scope**: Unit & DataStore Serialization Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/settings/WorkoutSectionOrderPersistenceTest.kt`
* **Preconditions**: Test DataStore instance.
* **Action**:
  1. Verify `WorkoutSectionType` contains all 8 canonical values: `DESCRIPTION`, `EXTREMA`, `LAPS`, `STRAVA`, `MAP`, `ELEVATION`, `CHARTS`, `ZONES`.
  2. Verify default order sequence matches `[DESCRIPTION, EXTREMA, LAPS, STRAVA, MAP, ELEVATION, CHARTS, ZONES]`.
  3. Verify string serialization / deserialization round-trip.
  4. Verify self-healing: if persisted string contains fewer items, missing enum items are appended cleanly without duplicate entries.
* **Expected Result**: 100% assertions pass.

### Test Case 2: `WorkoutSectionReorderContractTest` (`TST-UI-214.2`)
* **Scope**: Reordering Logic Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/WorkoutSectionReorderContractTest.kt`
* **Preconditions**: List of section types.
* **Action**:
  1. Test swap logic: moving item at index `i` up produces swapped elements at `i-1` and `i`.
  2. Test swap logic: moving item at index `i` down produces swapped elements at `i` and `i+1`.
  3. Verify boundary checks: index 0 cannot move up, index `size - 1` cannot move down.
* **Expected Result**: Reordering produces deterministic valid permutations of all 8 sections.

### Test Case 3: `WorkoutSummaryDynamicOrderContractTest` (`TST-UI-214.3`)
* **Scope**: Architectural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummaryDynamicOrderContractTest.kt`
* **Preconditions**: Inspect `WorkoutSummary.kt` source AST.
* **Action**: Verify `WorkoutSummary.kt` reads `workoutSectionsOrder` and renders sections dynamically rather than via rigid static sequence.
* **Expected Result**: Contract assertions pass.

### Test Case 4: `TrackOnMapScreenSectionSlottingContractTest` (`TST-UI-214.4`)
* **Scope**: Architectural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreenSectionSlottingContractTest.kt`
* **Preconditions**: Inspect `TrackOnMapScreen.kt` source AST.
* **Action**: Verify `TrackOnMapScreen.kt` dynamically partitions sections around `MAP` into `metadataContent` and `analyticsContent`.
* **Expected Result**: Contract assertions pass.

### Test Case 5: 9-Language Localization Audit (`TST-UI-214.5`)
* **Scope**: Localization Parity Test
* **Goal**: Verify any newly introduced string resources (`move_up`, `move_down`) across all 9 locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT
* **Expected Result**: 100% parity, zero missing entries.

### Test Case 6: Clean-Room Regression Suite (`TST-UI-214.6`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-214.1` | Unit | `MyPreferenceManager.workoutSectionsOrderFlow` | `REQ-UI-255` | Specified |
| `TST-UI-214.2` | Unit / Contract | `AdvancedTuningDialog` reordering logic | `REQ-UI-255` | Specified |
| `TST-UI-214.3` | Architectural Contract | `WorkoutSummary.kt` dynamic iteration | `REQ-UI-255` | Specified |
| `TST-UI-214.4` | Architectural Contract | `TrackOnMapScreen.kt` pre/post-map slotting | `REQ-UI-255` | Specified |
| `TST-UI-214.5` | Localization | `TranslationParityTest` | `REQ-UI-255`, `REQ-LOC-001` | Specified |
| `TST-UI-214.6` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
