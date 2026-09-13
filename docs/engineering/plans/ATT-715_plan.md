# Implementation Plan: Sport-Specific Equipment Selection & In-Place Expansion in Edit Workout Dialog (ATT-715)

## Overview
Within `EditWorkoutScreen` (the workout editing dialog), the Equipment dropdown selector currently displays all equipment in the database (every bicycle and pair of running shoes combined) regardless of the sport type. For example, cycling workouts show running shoes, and running workouts show bicycles. Furthermore, the generic `+ all equipment +` option provides a backdoor that dumps unfiltered equipment across sports.

This plan reactivates sport-specific equipment partitioning ([`REQ-UI-130`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L254), [`TST-UI-083`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L284)) based on the active workout's base sport type (`BSportType`) and linked equipment count ($N$).

---

## User Review Required
> [!NOTE]
> The equipment selection hierarchy strictly adheres to the user-confirmed 3-case rule based on the number of linked equipment items ($N$):
> 1. **$N > 1$ (Multiple linked items)**: Shows `[- none -, <linked equipment...>, + all bikes/shoes +]`. Tapping the expansion token expands the list in-place to all equipment of that sport type.
> 2. **$N == 1$ (Exactly one linked item)**: Shows `[- none -, <all equipment...>]`, with the uniquely linked equipment automatically preselected.
> 3. **$N == 0$ (No linked items)**: Shows `[- none -, <all equipment...>]`, with `- none -` preselected.
> 4. The generic token `+ all equipment +` and unfiltered fallback to `equipmentRepository.equipmentList` are permanently eliminated.

---

## Proposed Changes

### Component 1: Presentation & ViewModel Logic
#### [MODIFY] [EditWorkoutViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutViewModel.kt)
* **Remove Cross-Sport Backdoors**:
  * Remove `allEquipment` constant and `showAllEquipment()` method.
* **Strict Base Sport Equipment Partitioning in `updateSuggestedEquipmentNames(sportName: String)`**:
  * Query linked equipment names via `discoveryManager.getEquipmentNamesForSport(sportName)`.
  * Filter linked equipment by `currentBSportType` to eliminate legacy cross-sport links.
  * Partition available equipment by `currentBSportType`:
    * `BSportType.BIKE` $\rightarrow$ bicycles only (`equipmentList.filter { it.sportType == BSportType.BIKE }.map { it.name }`).
    * `BSportType.RUN` $\rightarrow$ running shoes only (`equipmentList.filter { it.sportType == BSportType.RUN }.map { it.name }`).
    * `BSportType.UNKNOWN` / other $\rightarrow$ empty list.
  * Implement 3-case hierarchy:
    * **Case 1 ($N > 1$)**: Populate options with `[- none -, <linked equipment...>, <allBikes or allShoes>]`.
    * **Case 2 ($N == 1$)** & **Case 3 ($N == 0$)**: Populate options with `[- none -, <sport equipment...>]`.
* **In-Place Expansion**:
  * In `showAllBikes()`: Populate `[- none -, <all bikes...>]` strictly containing bicycles.
  * In `showAllShoes()`: Populate `[- none -, <all shoes...>]` strictly containing shoes.
* **Smart Sport Switching in `updateSportName(newSportName: String)`**:
  * Compare `previousBSportType` and `newBSportType`.
  * If new sport has $N == 1$ linked equipment: automatically preselect the linked equipment.
  * If new sport has $N \ne 1$:
    * If `newBSportType == previousBSportType` and currently selected equipment is valid for `newBSportType`: retain the current equipment.
    * If `newBSportType != previousBSportType` (cross-base-sport switch): reset to `- none -` (`null` / `-1L`).
* **Initial Workout Loading in `loadWorkoutData()`**:
  * If stored `data.equipmentName` matches `data.bSportType`: preserve it.
  * If stored `data.equipmentName` is null or incompatible with `data.bSportType`:
    * If $N == 1$: preselect the linked equipment.
    * Else: preselect `- none -`.

---

### Component 2: Presentation & Jetpack Compose UI
#### [MODIFY] [EditWorkoutScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt)
* Update `DropdownSelector` for Equipment:
  * Remove `viewModel.allEquipment` from `stayOpenOn`.
  * Configure `stayOpenOn = setOf(viewModel.allShoes, viewModel.allBikes)`, ensuring tapping `+ all bikes +` or `+ all shoes +` expands the dropdown menu in-place without premature dismissal.

---

### Component 3: Automated Unit Testing
#### [NEW] [EditWorkoutEquipmentTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutEquipmentTest.kt)
* Comprehensive unit tests verifying:
  1. **Case 1 ($N > 1$)**: Sport with 2 linked bikes presents `[- none -, Bike A, Bike B, + all bikes +]` with zero shoes.
  2. **In-Place Expansion**: Tapping `+ all bikes +` updates options in-place to `[- none -, Bike A, Bike B, Bike C]` with zero shoes.
  3. **Case 2 ($N == 1$)**: Sport with 1 linked shoe presents `[- none -, Shoe 1, Shoe 2]` and automatically preselects `Shoe 1`.
  4. **Case 3 ($N == 0$)**: Sport with 0 linked bikes presents `[- none -, <all bikes...>]` and preselects `- none -`.
  5. **Cross-Sport Switching (Bike $\rightarrow$ Run)**: Changes equipment pool strictly to shoes and resets/infers equipment.
  6. **Cross-Sport Switching (Road Bike $\rightarrow$ MTB)**: Retains valid selected bike.
  7. **Unknown / Other Base Sport**: Presents only `[- none -]`.

---

## Verification Plan

### Automated Tests
1. Run new unit tests:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.editworkout.EditWorkoutEquipmentTest"
   ```
2. Run full regression test suite:
   ```bash
   ./gradlew testDebugUnitTest
   ```

### Manual Verification
1. Open any cycling workout in `EditWorkoutScreen`.
2. Open the Equipment dropdown:
   * Verify zero running shoes appear.
   * If the sport has multiple linked bikes, verify `[- none -, <linked bikes...>, + all bikes +]` is displayed.
   * Tap `+ all bikes +`: verify dropdown remains open and expands to show all bikes in the database.
3. Switch sport to a running sport:
   * Verify equipment resets (does not keep bike).
   * Verify dropdown options switch strictly to running shoes.
