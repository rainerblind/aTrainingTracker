# Stage 3: Implementation Plan - ATT-1641: Separate Workout Filter Equipment Section into Bikes and Shoes with Sport-Tab Awareness

**Ticket**: [ATT-1641](https://atrainingtracker.atlassian.net/browse/ATT-1641)  
**Sub-task**: [ATT-1651](https://atrainingtracker.atlassian.net/browse/ATT-1651) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Requirement Mapping**: `REQ-UI-193` (*Workout Filter: Categorized Equipment Sections (Bikes & Shoes) with Sport-Tab Awareness*)  
**Test Mapping**: `TST-UI-147` (*Workout Filter Categorized Equipment Partitioning & Tab-Awareness Verification*)  
**Branch**: `feature/ATT-1641`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Problem Description & Background

In [WorkoutFilterBottomSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterBottomSheet.kt), equipment filtering is currently rendered under a single monolithic section titled "Ausrüstung" (`filter_section_equipment`).

When filtering workouts (especially in the "All Workouts" overview tab where `activeBSportType == null`, but also when equipment overlaps), bikes and running shoes are displayed together in the same chip group (e.g. Road Bike, Running Shoes, Gravel Bike, Trail Shoes mixed together).

Athletes expect equipment to be cleanly divided by category:
1. Separate sections for **Fahrräder / Bikes** (`R.string.equipment_type_bike`) and **Schuhe / Shoes** (`R.string.equipment_type_shoe`).
2. Tab-context sensitivity:
   - In the **Bike tab** (`activeBSportType == BSportType.BIKE`), only the Bikes section must be shown (never shoes or other gear).
   - In the **Run tab** (`activeBSportType == BSportType.RUN`), only the Shoes section must be shown (never bikes or other gear).
   - In the **Other tab** (`activeBSportType == BSportType.UNKNOWN`), only unclassified / other equipment must be shown.
   - In the **All / Overview tab** (`activeBSportType == null`), bikes, shoes, and any unclassified/other equipment must be rendered as distinct, clearly titled sections rather than mixed into one generic group.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-193` (*Workout Filter: Categorized Equipment Sections (Bikes & Shoes) with Sport-Tab Awareness*)
* **Test Mapping**: `TST-UI-147` (*Workout Filter Categorized Equipment Partitioning & Tab-Awareness Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Selection State Mutual Exclusivity**: `localEquipId: Long?` remains a single nullable scalar. Selecting an item in any equipment category automatically deselects any previously selected item in other categories.
2. **Context-Driven State Invariant Maintenance**: A `LaunchedEffect(allAvailableEquipment)` monitors the unified list of currently available gear (`availableBikes + availableShoes + availableOtherEquipment`). If an active selection is no longer present after a tab switch or sub-sport selection, `localEquipId` is immediately reset to `null`.
3. **Database & Schema Immutability**: No changes to SQLite databases (`Equipment.db`, `WorkoutSummariesDatabaseManager`), ContentProviders, or DAOs.
4. **Filter Criteria Contract & Serialization**: `WorkoutFilterCriteria` data class and its JSON serialization remain 100% backward compatible.
5. **Localization Parity**: Uses existing, verified string resources (`equipment_type_bike`, `equipment_type_shoe`, and `filter_section_equipment`) across all 9 application locales.
6. **Subtask Direct Completion**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
7. **Parent Human Gate Invariance**: Terminal completion of parent ticket [ATT-1641](https://atrainingtracker.atlassian.net/browse/ATT-1641) remains strictly reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `WorkoutFilterBottomSheet.kt`
* **Partitioning Logic**:
  * Replace the monolithic `availableEquipment` computation with:
    - `availableBikes`: Filter `allWorkouts` where `bSportType == BSportType.BIKE`, respecting `localSportId`. Suppressed if `activeBSportType != null && activeBSportType != BSportType.BIKE`.
    - `availableShoes`: Filter `allWorkouts` where `bSportType == BSportType.RUN`, respecting `localSportId`. Suppressed if `activeBSportType != null && activeBSportType != BSportType.RUN`.
    - `availableOtherEquipment`: Filter `allWorkouts` where `bSportType != BSportType.BIKE && bSportType != BSportType.RUN`, respecting `localSportId`. Suppressed if `activeBSportType == BSportType.BIKE || activeBSportType == BSportType.RUN`.
    - `allAvailableEquipment`: Unified list `remember(availableBikes, availableShoes, availableOtherEquipment) { availableBikes + availableShoes + availableOtherEquipment }`.
  * Update `LaunchedEffect(allAvailableEquipment)` to clear stale `localEquipId`.
* **Modular Composable Extraction**:
  * Create a local composable helper `EquipmentSection(title: String, equipment: List<Pair<Long, String>>, selectedEquipId: Long?, onSelect: (Long?) -> Unit)` to eliminate code duplication across sections.
* **UI Rendering**:
  * Render `EquipmentSection(stringResource(R.string.equipment_type_bike), availableBikes, ...)` when `availableBikes.isNotEmpty()`.
  * Render `EquipmentSection(stringResource(R.string.equipment_type_shoe), availableShoes, ...)` when `availableShoes.isNotEmpty()`.
  * Render `EquipmentSection(stringResource(R.string.filter_section_equipment), availableOtherEquipment, ...)` when `availableOtherEquipment.isNotEmpty()`.

### Component 2: `WorkoutFilterEquipmentPartitioningHelper.kt` / Unit Tests
* Extract or expose partitioning function (pure Kotlin) for direct, deterministic unit test verification in `WorkoutFilterEquipmentPartitioningTest.kt`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Programmatic Gate Check
* Verify Gate 3 sign-off before modifying any production code:
  ```bash
  python3 tools/jira_util.py check-gate ATT-1651
  ```

### Step 2: Implementation in `WorkoutFilterBottomSheet.kt`
* Extract pure partitioning logic:
  - `partitionAvailableEquipment(allWorkouts, activeBSportType, localSportId)` returning `CategorizedEquipment(bikes, shoes, other)`.
* Integrate `CategorizedEquipment` into `WorkoutFilterBottomSheet`.
* Replace single equipment section with modular `EquipmentSection` calls for Bikes, Shoes, and Other gear.

### Step 3: Author Unit Test Suite
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterEquipmentPartitioningTest.kt`.
* Verify:
  1. Bike tab isolation: only bikes returned, shoes and other are empty.
  2. Run tab isolation: only shoes returned, bikes and other are empty.
  3. Other tab isolation: only other gear returned, bikes and shoes are empty.
  4. All Workouts tab: bikes, shoes, and other gear correctly partitioned into separate non-empty lists.
  5. Sub-sport filter: selecting `localSportId` filters equipment within that sport.
  6. State invariant: switching tabs from Bike to Run automatically clears `localEquipId`.

### Step 4: Targeted Unit Test Execution
* Execute targeted unit test command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutFilterEquipmentPartitioningTest"
  ```

### Step 5: Clean-Room Full Test Suite Regression
* Execute complete regression test:
  ```bash
  ./gradlew testDebugUnitTest
  ```

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Step-by-step unit testing of `WorkoutFilterEquipmentPartitioningTest`.
  - Full project test suite execution ensuring 0 regressions across all modules.
* **Rollback Plan**:
  - Feature branch `feature/ATT-1641` isolates all changes. If any unexpected regressions occur, discard the branch via `git checkout sprint/2026-40.5 && git branch -D feature/ATT-1641`.
