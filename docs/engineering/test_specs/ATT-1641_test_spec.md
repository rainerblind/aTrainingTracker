# Stage 2: Requirement & Test Specification - ATT-1641: Separate Workout Filter Equipment Section into Bikes and Shoes with Sport-Tab Awareness

**Ticket**: [ATT-1641](https://atrainingtracker.atlassian.net/browse/ATT-1641)  
**Sub-task**: [ATT-1650](https://atrainingtracker.atlassian.net/browse/ATT-1650) (`[Test-Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Requirement Mapping**: `REQ-UI-193` (*Workout Filter: Categorized Equipment Sections (Bikes & Shoes) with Sport-Tab Awareness*)  
**Test Spec ID**: `TST-UI-147`  
**Branch**: `feature/ATT-1641`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Requirement Specification (REQ-UI-193)

### 1.1 Problem Statement & Rationale
In [WorkoutFilterBottomSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterBottomSheet.kt), equipment filtering is currently rendered under a single monolithic section titled "Ausrüstung" (`filter_section_equipment`).

When filtering workouts (especially in the "All Workouts" overview tab where `activeBSportType == null`, but also when equipment overlaps), bikes and running shoes are displayed together in the same chip group (e.g. Road Bike, Running Shoes, Gravel Bike, Trail Shoes mixed together).

Athletes expect equipment to be cleanly divided by category:
1. Separate sections for **Fahrräder / Bikes** (`R.string.equipment_type_bike`) and **Schuhe / Shoes** (`R.string.equipment_type_shoe`).
2. Tab-context sensitivity:
   - In the **Bike tab** (`activeBSportType == BSportType.BIKE`), only the Bikes section must be shown (never shoes or other gear).
   - In the **Run tab** (`activeBSportType == BSportType.RUN`), only the Shoes section must be shown (never bikes or other gear).
   - In the **Other tab** (`activeBSportType == BSportType.UNKNOWN`), only unclassified / other equipment must be shown.
   - In the **All / Overview tab** (`activeBSportType == null`), bikes, shoes, and any unclassified/other equipment must be rendered as distinct, clearly titled sections rather than mixed into one generic group.

### 1.2 Functional & Architectural Requirements
1. *Sport Category Partitioning*:
   - The system SHALL partition equipment items derived from `allWorkouts` by sport type into `availableBikes` (`bSportType == BSportType.BIKE`), `availableShoes` (`bSportType == BSportType.RUN`), and `availableOtherEquipment` (fallback for unclassified or other sports where `bSportType != BSportType.BIKE && bSportType != BSportType.RUN`).
   - If a specific sub-sport filter is active (`localSportId != null`), only equipment associated with that sub-sport ID SHALL be included within the respective category.
2. *Tab-Context Sensitive Section Rendering*:
   - In the Bike tab (`activeBSportType == BSportType.BIKE`): The bottom sheet SHALL render only the **Fahrräder** / **Bikes** section (`R.string.equipment_type_bike`) if `availableBikes.isNotEmpty()`. Running shoes and other gear SHALL NOT be displayed.
   - In the Run tab (`activeBSportType == BSportType.RUN`): The bottom sheet SHALL render only the **Schuhe** / **Shoes** section (`R.string.equipment_type_shoe`) if `availableShoes.isNotEmpty()`. Bikes and other gear SHALL NOT be displayed.
   - In the Other tab (`activeBSportType == BSportType.UNKNOWN`): The bottom sheet SHALL render only the **Sonstige Ausrüstung** / **Equipment** section (`R.string.filter_section_equipment`) if `availableOtherEquipment.isNotEmpty()`.
   - In the All Workouts tab (`activeBSportType == null`): The bottom sheet SHALL render each non-empty category as a distinct, clearly titled section (**Fahrräder**, **Schuhe**, and **Sonstige Ausrüstung**) rather than mixing all gear into a single monolithic group.
3. *Single Selection State & Invariant Maintenance*:
   - `localEquipId: Long?` SHALL remain mutually exclusive across all equipment sections. Selecting any chip SHALL toggle that ID and immediately deselect any equipment from other categories.
   - A `LaunchedEffect` monitoring all available equipment (`availableBikes + availableShoes + availableOtherEquipment`) SHALL automatically clear `localEquipId` (`null`) whenever the currently selected equipment ID is no longer present in the active filtered set.
4. *100% 9-Language Localization Parity*:
   - Section headers SHALL utilize localized string resources `equipment_type_bike`, `equipment_type_shoe`, and `filter_section_equipment` across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing entries.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Bike Tab Tab-Awareness)**:
  * *Given* the athlete opens `WorkoutFilterBottomSheet` from the Bike tab (`BSportType.BIKE`)
  * *When* viewing equipment filters
  * *Then* only the "Fahrräder" section is displayed, and no running shoes are visible.
* **Criterion 2 (Run Tab Tab-Awareness)**:
  * *Given* the athlete opens `WorkoutFilterBottomSheet` from the Run tab (`BSportType.RUN`)
  * *When* viewing equipment filters
  * *Then* only the "Schuhe" section is displayed, and no bikes are visible.
* **Criterion 3 (All Tab Distinct Categorized Sections)**:
  * *Given* the athlete opens `WorkoutFilterBottomSheet` from the "All Workouts" tab (`activeBSportType == null`)
  * *When* viewing equipment filters
  * *Then* bikes and shoes are rendered in separate, clearly titled sections ("Fahrräder" and "Schuhe") rather than mixed in a single generic group.
* **Criterion 4 (Automatic State Clearing)**:
  * *Given* an active equipment selection (`localEquipId != null`)
  * *When* the athlete switches to a sport tab or selects a sport sub-type where that equipment is not available
  * *Then* `localEquipId` SHALL automatically be cleared (`null`).

### 1.4 System Invariants
1. `WorkoutFilterCriteria` data class, JSON serialization, and database schemas remain 100% unchanged.
2. Selection state is strictly mutually exclusive (at most one equipment filter active).
3. Zero regressions in existing filter capabilities (date range, distance range, duration range, locations, clusters, attributes).

---

## 2. Test Specification (TST-UI-147)

### Test Case 1: Tab-Aware Equipment Partitioning Logic (`TST-UI-147.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterEquipmentPartitioningTest.kt`
* **Preconditions**: A list of workouts containing Bike workouts with Bike equipment, Run workouts with Shoe equipment, and Swimming workouts with unclassified equipment.
* **Actions & Expected Results**:
  1. For `activeBSportType == BSportType.BIKE`: Verify that `availableBikes` contains only bike equipment, while `availableShoes` and `availableOtherEquipment` are empty.
  2. For `activeBSportType == BSportType.RUN`: Verify that `availableShoes` contains only shoe equipment, while `availableBikes` and `availableOtherEquipment` are empty.
  3. For `activeBSportType == null`: Verify that `availableBikes`, `availableShoes`, and `availableOtherEquipment` are correctly populated and disjoint.
  4. For sub-sport filtering (`localSportId != null`): Verify that only equipment belonging to that specific `sportId` is returned within its category.

### Test Case 2: State Invariant & Deselection on Context Change (`TST-UI-147.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterEquipmentPartitioningTest.kt`
* **Preconditions**: An active `localEquipId` matching a bike.
* **Actions & Expected Results**:
  1. When switching context to `activeBSportType == BSportType.RUN`, verify that `allAvailableEquipment` no longer contains the bike ID, triggering `localEquipId = null`.
  2. When selecting an item in shoes, verify that `localEquipId` updates to the shoe ID (replacing the bike selection).

### Test Case 3: 9-Language Localization & Specifier Audit (`TST-UI-147.3`)
* **Scope**: Localization Parity Test
* **Goal**: Verify string presence and matching `%s`/`%d` tokens across all 9 locales:
  * Keys: `equipment_type_bike`, `equipment_type_shoe`, `filter_section_equipment`
  * Locales: `values/` (EN), `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches.

### Test Case 4: Clean-Room Regression Suite (`TST-UI-147.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-147.1` | Unit | `WorkoutFilterBottomSheetKt` partitioning logic | `REQ-UI-193` | Specified |
| `TST-UI-147.2` | Unit | State clearing invariant (`LaunchedEffect`) | `REQ-UI-193` | Specified |
| `TST-UI-147.3` | Localization | `TranslationParityTest` / `strings.xml` audit | `REQ-UI-193`, `REQ-UI-106` | Specified |
| `TST-UI-147.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
