# Stage 3: Implementation Plan - ATT-2176: User-Customizable Section Reordering and Modular Section Architecture for Workout Summaries and Details

**Ticket**: [ATT-2176](https://rainerblind.atlassian.net/browse/ATT-2176)  
**Sub-task**: [ATT-2202](https://rainerblind.atlassian.net/browse/ATT-2202) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Requirement Mapping**: `REQ-UI-255` (*Aftermath: User-Customizable Section Reordering, Unified Order Persistence & Modular Section Architecture for Workout Summaries and Details*)  
**Test Mapping**: `TST-UI-214` (*Aftermath: User-Customizable Section Reordering, Unified Order Persistence & Modular Section Architecture Verification*)  
**Branch**: `feature/ATT-2176`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Description & Background

In the workout summary cards (`WorkoutSummary.kt`) and the full-screen workout details view (`TrackOnMapScreen.kt` / `MapDetailLayout.kt`), individual section visibility can currently be toggled in Advanced Settings, but the vertical display order is rigidly hardcoded. Athletes have divergent analytical priorities (e.g. data-driven cyclists prioritize Telemetry Charts and Zones, while trail runners prioritize Map and Elevation). Furthermore, section layout and wrapping logic are duplicated between summary cards and detail views.

ATT-2176 establishes a clean, unified architecture with:
1. `WorkoutSectionType` domain enum and canonical default order.
2. DataStore persistence in `MyPreferenceManager.kt` with missing value self-healing.
3. Interactive section reordering in `AdvancedTuningDialog.kt` via accessible Move Up / Move Down buttons.
4. Dynamic section rendering in `WorkoutSummary.kt` and dynamic relative slotting around `MAP` in `TrackOnMapScreen.kt`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-255` (*Aftermath: User-Customizable Section Reordering, Unified Order Persistence & Modular Section Architecture for Workout Summaries and Details*)
* **Test Mapping**: `TST-UI-214` (*Aftermath: User-Customizable Section Reordering, Unified Order Persistence & Modular Section Architecture Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Permanent Top Identity Anchor**:
   `WorkoutHeader` (Title, Sport Type, Date, Duration, Menu) and primary metrics (`WorkoutDetails`: Distance, Duration, Pace/Speed) remain permanently anchored at the top of the workout card and details view, never displaced by reordering.
2. **Guaranteed Minimum Map Height (`REQ-UI-250`)**:
   `SplitPaneMath.MIN_MAP_HEIGHT` (120dp) remains strictly guaranteed regardless of which sections are slotted above the map.
3. **Independent Visibility Preferences (`REQ-UI-229`, `REQ-UI-240`)**:
   Existing per-section visibility toggles for cards and details remain 100% operational.
4. **Interaction Decoupling**:
   List cards remain passive (touch navigates to full details without chart gesture trapping), while full details retain interactive multi-chart scrubbing, zoom toolbar, and lap map highlights.
5. **9-Language Localization Parity (`REQ-LOC-001`)**:
   Zero missing string resources across EN, DE, ES, FR, IT, JA, NL, PL, PT.
6. **Subtask Direct Completion & Parent Human Gate**:
   Subtasks transition directly to `Erledigt` via transition `freigabe` upon passing Gate audit. Parent ticket `ATT-2176` transitions to `Final Review (Human)` after Stage 5.

---

## 4. Proposed Architectural Changes

### Component 1: Domain Model (`WorkoutSectionType.kt`)
* Package: `com.atrainingtracker.trainingtracker.ui.aftermath`
* Define `enum class WorkoutSectionType`:
  - `DESCRIPTION`, `EXTREMA`, `LAPS`, `STRAVA`, `MAP`, `ELEVATION`, `CHARTS`, `ZONES`
* Define `DEFAULT_WORKOUT_SECTIONS_ORDER: List<WorkoutSectionType>`.

### Component 2: DataStore Persistence (`MyPreferenceManager.kt`)
* Preference key: `val WORKOUT_SECTIONS_ORDER = stringPreferencesKey("workout_sections_order")`
* Expose `workoutSectionsOrderFlow: Flow<List<WorkoutSectionType>>`:
  - Deserialize comma-separated enum names.
  - Fall back safely to `DEFAULT_WORKOUT_SECTIONS_ORDER` if missing or corrupted.
  - Auto-heal by appending any missing canonical enum values.
* Expose `suspend fun setWorkoutSectionsOrder(order: List<WorkoutSectionType>)`.

### Component 3: Reordering UI in Advanced Settings (`AdvancedTuningDialog.kt`)
* Pass `workoutSectionsOrder` and `onWorkoutSectionsOrderChange` into `AdvancedTuningDialog`.
* Order the matrix feature rows according to `workoutSectionsOrder`.
* Add Move Up (`▲`) and Move Down (`▼`) IconButtons to each row:
  - Move Up disabled on index 0.
  - Move Down disabled on index `size - 1`.
  - Tapping swaps adjacent elements and triggers `onWorkoutSectionsOrderChange`.

### Component 4: Dynamic Rendering in `WorkoutSummary.kt`
* Collect `workoutSectionsOrder` from `MyPreferenceManager`.
* After the fixed header and details, loop through `workoutSectionsOrder` and invoke the appropriate composable for each enabled section.

### Component 5: Relative Slotting in `TrackOnMapScreen.kt`
* Collect `workoutSectionsOrder` from `MyPreferenceManager`.
* Partition sections relative to `MAP`:
  - Sections preceding `MAP`: rendered inside `metadataContent` (above map).
  - Sections following `MAP`: rendered inside `analyticsContent` (below map).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Create `WorkoutSectionType.kt` Domain Model
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutSectionType.kt`
* Define `WorkoutSectionType` enum and `DEFAULT_WORKOUT_SECTIONS_ORDER`.

### Step 2: Implement Persistence in `MyPreferenceManager.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/MyPreferenceManager.kt`
* Add `WORKOUT_SECTIONS_ORDER`, `workoutSectionsOrderFlow`, and `setWorkoutSectionsOrder`.
* Implement self-healing deserialization logic.

### Step 3: Add Localization Strings
* Files: `app/src/main/res/values*/strings.xml` (all 9 locales)
* Add `@string/action_move_up` ("Move up") and `@string/action_move_down` ("Move down").

### Step 4: Implement Reordering UI in `AdvancedTuningDialog.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt`
* Wire `workoutSectionsOrder` and reordering controls.

### Step 5: Implement Dynamic Section Order in `WorkoutSummary.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt`
* Loop through active section order for dynamic rendering.

### Step 6: Implement Dynamic Slotting in `TrackOnMapScreen.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt`
* Partition sections around `MAP` into `metadataContent` and `analyticsContent`.

### Step 7: Author Unit & Contract Tests
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/settings/WorkoutSectionOrderPersistenceTest.kt`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/WorkoutSectionReorderContractTest.kt`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummaryDynamicOrderContractTest.kt`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreenSectionSlottingContractTest.kt`
* Targeted test command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.WorkoutSectionOrderPersistenceTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.WorkoutSectionReorderContractTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted tests during construction, followed by full clean-room suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Work isolated on `feature/ATT-2176` branched from `sprint/2026-40.14`. Rollback is accomplished via `git checkout sprint/2026-40.14`.
