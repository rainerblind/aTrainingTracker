# Stage 1 Analysis: ATT-1641 - Separate Workout Filter Equipment Section into Bikes and Shoes with Sport-Tab Awareness

**Ticket**: [ATT-1641](https://atrainingtracker.atlassian.net/browse/ATT-1641)  
**Sub-task**: [ATT-1649](https://atrainingtracker.atlassian.net/browse/ATT-1649) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1641`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Problem Statement & Motivation

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

## 2. Root Cause Analysis (Forensic Investigation)

### Current Architecture in `WorkoutFilterBottomSheet.kt`
Currently, lines 147–162 compute a single `availableEquipment` list:
```kotlin
val availableEquipment = remember(allWorkouts, activeBSportType, localSportId) {
    allWorkouts
        .filter { it.equipmentId > 0 && !it.equipmentName.isNullOrBlank() }
        .filter { workout ->
            if (localSportId != null) {
                workout.sportId == localSportId
            } else if (activeBSportType != null) {
                workout.bSportType == activeBSportType
            } else {
                true
            }
        }
        .map { it.equipmentId to it.equipmentName!! }
        .distinctBy { it.first }
        .sortedBy { it.second }
}
```
And lines 418–439 render this list as a single monolithic block under `R.string.filter_section_equipment`.

### Architectural Flaws & Root Causes:
1. **Lack of Category Partitioning**:
   Even though `WorkoutData` contains `bSportType` (`BSportType.BIKE`, `BSportType.RUN`, `BSportType.UNKNOWN`), all equipment is flattened into a single list of `(equipmentId, equipmentName)`. The underlying sport category information is discarded during the map step.
2. **Monolithic UI Rendering**:
   Only one section header (`filter_section_equipment`) exists. In the overview tab (`activeBSportType == null`), all equipment items are displayed in a single `FlowRow`, mixing cycling and running gear.
3. **Absence of Dedicated String Keys**:
   The app already contains localized strings `equipment_type_bike` ("Räder" / "Bikes") and `equipment_type_shoe` ("Schuhe" / "Shoes") in all 9 languages (`res/values-*/strings.xml`), but `WorkoutFilterBottomSheet` never leverages them.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Partition available equipment derived from `allWorkouts` into:
    - `availableBikes` (`bSportType == BSportType.BIKE`)
    - `availableShoes` (`bSportType == BSportType.RUN`)
    - `availableOtherEquipment` (fallback for unclassified/other sports where `bSportType != BSportType.BIKE && bSportType != BSportType.RUN`)
  * Context-aware section visibility in `WorkoutFilterBottomSheet.kt`:
    * Bike tab: Only Bikes section rendered.
    * Run tab: Only Shoes section rendered.
    * Other tab: Only Other Equipment section rendered.
    * All Workouts tab: Bikes, Shoes, and Other Equipment rendered as distinct, clearly titled sections.
  * Selection state consistency:
    * `localEquipId` remains a single nullable ID (`Long?`).
    * Tapping an active chip deselects it (`null`).
    * Selecting any gear deselects previous gear across categories (mutual exclusivity).
    * `LaunchedEffect` monitors `allAvailableEquipment` (`availableBikes + availableShoes + availableOtherEquipment`) and clears `localEquipId` if the selected gear is no longer visible in the current tab/sport context.
  * Unit test coverage verifying tab awareness, category separation, and selection clearing.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to the database schema (`EquipmentDbHelper` or `WorkoutSummariesDatabaseManager`).
  * No multi-select equipment filtering (the filter criteria remains single equipment selection).
  * No alterations to `ClusterFilterBottomSheet.kt` or `RouteFilterBottomSheet.kt` unless explicitly sharing equipment components.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-157` (*Tab-Aware Sport Selection & Sport-Aware Equipment*)
* **Historical Origin & Commit Trace**: Introduced in sprint `2026-38` under `ATT-882` / `ATT-1118`.
* **Root Reason for Existing Formulation**:
  `REQ-UI-157` ensured that when an athlete was on the Bike tab, equipment from Run workouts was filtered out, and vice versa. However, it treated all equipment in the "All Workouts" tab as an undifferentiated group under "Ausrüstung", creating visual clutter and confusion when athletes owned both bikes and running shoes.
* **Preservation of Core Invariants**:
  This change preserves all invariants of `REQ-UI-157`:
  - Switching sport sub-types still filters equipment.
  - Incompatible `localEquipId` is still automatically cleared.
  - Filter criteria serialization (`WorkoutFilterCriteria`) remains 100% backward compatible.
  - The enhancement strictly refines the presentation and partitioning of equipment into distinct sport-typed sections (`REQ-UI-168`).

---

## 5. Architectural Strategy & High-Level Solution

### 1. Partitioning Logic in `WorkoutFilterBottomSheet.kt`
We partition equipment by sport category while respecting any active `localSportId` (sub-sport filter) and `activeBSportType` (tab filter):

```kotlin
// 1. Bikes
val availableBikes = remember(allWorkouts, activeBSportType, localSportId) {
    if (activeBSportType != null && activeBSportType != BSportType.BIKE) {
        emptyList()
    } else {
        allWorkouts
            .filter { it.equipmentId > 0 && !it.equipmentName.isNullOrBlank() && it.bSportType == BSportType.BIKE }
            .filter { localSportId == null || it.sportId == localSportId }
            .map { it.equipmentId to it.equipmentName!! }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }
}

// 2. Shoes
val availableShoes = remember(allWorkouts, activeBSportType, localSportId) {
    if (activeBSportType != null && activeBSportType != BSportType.RUN) {
        emptyList()
    } else {
        allWorkouts
            .filter { it.equipmentId > 0 && !it.equipmentName.isNullOrBlank() && it.bSportType == BSportType.RUN }
            .filter { localSportId == null || it.sportId == localSportId }
            .map { it.equipmentId to it.equipmentName!! }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }
}

// 3. Other / Unclassified Equipment (Safety Fallback)
val availableOtherEquipment = remember(allWorkouts, activeBSportType, localSportId) {
    if (activeBSportType == BSportType.BIKE || activeBSportType == BSportType.RUN) {
        emptyList()
    } else {
        allWorkouts
            .filter { it.equipmentId > 0 && !it.equipmentName.isNullOrBlank() && it.bSportType != BSportType.BIKE && it.bSportType != BSportType.RUN }
            .filter { localSportId == null || it.sportId == localSportId }
            .map { it.equipmentId to it.equipmentName!! }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }
}

// 4. Combined Available Equipment for State Invariant Check
val allAvailableEquipment = remember(availableBikes, availableShoes, availableOtherEquipment) {
    availableBikes + availableShoes + availableOtherEquipment
}
```

### 2. State-Clearing & Selection Invariant
The `LaunchedEffect` monitors `allAvailableEquipment`. If an equipment item was previously selected (`localEquipId != null`), but is no longer present in `allAvailableEquipment` (for instance, when switching from the Bike tab to the Run tab, or selecting a specific sub-sport), `localEquipId` is immediately reset to `null`:
```kotlin
LaunchedEffect(allAvailableEquipment) {
    if (localEquipId != null && allAvailableEquipment.none { it.first == localEquipId }) {
        localEquipId = null
    }
}
```
Because `localEquipId` is a single variable, selecting a chip in `availableBikes` replaces `localEquipId`, automatically deselecting any item in `availableShoes`, and vice versa.

### 3. Rendering Strategy
Instead of a single monolithic section, we render each non-empty category with its dedicated, localized header:
* **Fahrräder / Bikes**: Rendered with title `stringResource(R.string.equipment_type_bike)` if `availableBikes.isNotEmpty()`.
* **Schuhe / Shoes**: Rendered with title `stringResource(R.string.equipment_type_shoe)` if `availableShoes.isNotEmpty()`.
* **Sonstige Ausrüstung / Other Equipment**: Rendered with title `stringResource(R.string.filter_section_equipment)` if `availableOtherEquipment.isNotEmpty()`.

This guarantees:
- In the Bike tab: Only Bikes are shown.
- In the Run tab: Only Shoes are shown.
- In the Other tab: Only Other gear is shown.
- In the All tab: Bikes, Shoes, and Other gear are rendered as separate, clearly demarcated sections with zero mixing or jumbling.
- Zero equipment items are lost or hidden, even if unclassified.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. `localEquipId` selection is mutually exclusive across all equipment sections.
  2. Incompatible equipment is automatically deselected when changing sport tabs or sport filters.
  3. No equipment item is dropped; unclassified items render under the fallback section.
  4. No changes to `WorkoutFilterCriteria` data class or JSON serialization schema.
  5. 100% localization parity across all 9 supported languages (utilizing existing `equipment_type_bike`, `equipment_type_shoe`, and `filter_section_equipment`).
* **Risk Rating**: **LOW**
  - Pure UI layout and presentation partitioning within `WorkoutFilterBottomSheet.kt`.
  - Zero database schema changes or data migrations.
