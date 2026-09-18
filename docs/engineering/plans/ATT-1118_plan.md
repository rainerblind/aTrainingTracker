# Implementation Plan: Properly Handle Retired Equipment in UI and Pickers (ATT-1118)

## 1. Overview & Architectural Motivation
Under **ATT-1118** and **REQ-UI-158**, the application ensures that equipment flagged as retired (`Retired = 1` in SQLite `Equipment.db`, introduced in ATT-1114) is cleanly isolated from active workout selection pickers and sensor identity auto-inference, while preserving full historical attribution for past workouts, providing clear visual status in the equipment manager, and offering manual lifecycle controls.

### Key Objectives & Invariants
1. **Picker Isolation with Current-Assignment Preservation (`EditWorkoutViewModel.kt`)**:
   - Workout edit pickers (`updateEquipmentNames`, `showAllBikes`, `showAllShoes`) must exclude retired equipment from available options.
   - **System Invariant**: If a workout being edited is *already assigned* to a retired equipment (e.g. historical workout or workout synced before retirement), that specific retired equipment MUST be retained in the options list so the user's current selection remains intact and is not wiped.
2. **Sensor Inference Isolation (`EquipmentAndSportTypeDiscoveryManager.kt`)**:
   - When inferring workout identity from active hardware sensors (`resolveIdentity`), retired equipment must be excluded from resolution. Retired gear must never be auto-assigned to new workouts.
3. **Sport Type Equipment Link Isolation (`SportTypeViewModel.kt`)**:
   - `availableEquipment(bSportType)` must return active equipment only, preventing obsolete gear from being linked to sport types.
4. **Equipment Manager Visual Hierarchy & Grouping (`EquipmentTabsScreen.kt`, `EquipmentViewModel.kt`)**:
   - Retired items in `EquipmentTabsScreen` must display a distinct visual badge (`Retired` / `Ausgemustert`).
   - The equipment screen must organize items into distinct sections (Active vs. Retired) or provide a clean toggle to show/hide retired items.
5. **Manual Retirement Lifecycle (`EditEquipmentDialog.kt`, `EquipmentDbHelper.java`)**:
   - `EditEquipmentDialog` must provide an interactive toggle/switch for retirement status ("Retired" / "In Ruhestand"), allowing athletes to retire or unretire gear locally.
   - `EquipmentDbHelper` must persist retirement changes to SQLite.
6. **Historical Fidelity (`WorkoutDataMapper.kt`, `WorkoutFilterBottomSheet.kt`)**:
   - Historical workout queries resolve equipment names and icons by ID without filtering by retirement status. Past workouts retain their original equipment display.
   - `WorkoutFilterBottomSheet` continues to dynamically extract available equipment from historical workouts, ensuring past workouts on retired gear remain filterable.

---

## 2. Impact Analysis (ASPICE SWE.1.BP.5)

### Slated Target Files & Methods
1. `app/src/main/java/com/atrainingtracker/trainingtracker/database/EquipmentDbHelper.java`:
   - `EquipmentData`: add `public final boolean isRetired`.
   - `getEquipmentItems(BSportType)` and `getEquipmentItems()`: read `Retired` column into `EquipmentData`.
   - Add `getEquipmentItems(boolean activeOnly)` / `getEquipmentItems(BSportType, boolean activeOnly)`.
   - Add `updateEquipment(long, String, int, List<Long>, boolean)` and `setEquipmentRetired(long, boolean)`.
   - Add `isEquipmentRetired(long id): boolean`.
2. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutViewModel.kt`:
   - `updateEquipmentNames(sportName: String)`: filter out retired equipment, preserving the workout's currently assigned equipment.
   - `showAllBikes()`, `showAllShoes()`: filter out retired equipment, preserving currently assigned equipment.
   - `getFilteredLinkedEquipment()`: filter out retired equipment, preserving currently assigned equipment.
3. `app/src/main/java/com/atrainingtracker/trainingtracker/database/EquipmentAndSportTypeDiscoveryManager.kt`:
   - `getLinkedEquipmentIds(activeDeviceIds)`: exclude retired equipment from candidates.
   - `resolveIdentity`: ensure inferred equipment is not retired.
4. `app/src/main/java/com/atrainingtracker/banalservice/ui/sporttype/SportTypeViewModel.kt`:
   - `availableEquipment(bSportType)`: return active equipment only.
5. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentViewModel.kt`:
   - `EquipmentItem`: add `val isRetired: Boolean`.
   - `loadEquipment()`: populate `isRetired`.
   - `updateEquipment(item)`: pass `item.isRetired` to database.
   - Add `toggleRetired(item: EquipmentItem)`.
6. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EditEquipmentDialog.kt`:
   - Add retirement `Switch` / `Row` with localized label.
   - Pass updated `isRetired` in emitted `EquipmentItem`.
7. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentTabsScreen.kt`:
   - Add "Retired" visual badge to `EquipmentItem`.
   - Group active and retired equipment with distinct section headers or collapsible section.
8. Localization resource files (`app/src/main/res/values*/strings.xml`):
   - Add strings across 9 languages: `equipment_retired`, `equipment_active`, `equipment_section_active`, `equipment_section_retired`, `equipment_show_retired`, `equipment_hide_retired`.

### Mapped Requirements Cross-Check
| Requirement ID | Description | Impact & Preservation Assessment |
| :--- | :--- | :--- |
| **REQ-UI-158** | Retired Equipment UI & Picker Isolation | Primary target; ensures picker filtering, visual badge, grouping, and manual toggle. |
| **REQ-EXP-009** | Strava Duplicate Reconciliation Equipment Enrichment | Preserved; `EquipmentDbHelper.addOrUpdateStravaGear` and schema v2 column `Retired` remain authoritative. |
| **REQ-EXP-008** | Smart Asymmetric Strava Naming Strategy | Preserved; workout naming and Strava duplicate reconciliation unaffected. |
| **REQ-UI-157** | Tab-Aware Sport & Sport-Aware Equipment Filtering | Preserved; `WorkoutFilterBottomSheet` continues to derive equipment from `allWorkouts`. |
| **REQ-UI-151** | Workout Editing Modal Bottom Sheet | Preserved; `EditWorkoutScreen` composable and save/cancel actions remain intact. |

### System Risk & Side Effect Evaluation
- **SQLite Concurrency & Compatibility**: Zero schema migration required; `Retired` column already exists in schema version 2.
- **Null Safety**: In `EquipmentData`, `isRetired` defaults to `false` when column is 0 or null.
- **Backward Compatibility**: Workouts with legacy equipment or no equipment continue to function normally.

---

## 3. Detailed Implementation Steps

### Step 1: Enhance `EquipmentDbHelper.java`
- Update `EquipmentData` class:
  ```java
  public static class EquipmentData {
      public final long id;
      public final String name;
      public final BSportType sportType;
      public final int frameType;
      public final String stravaName;
      public final String stravaId;
      public final boolean isRetired;

      public EquipmentData(long id, String name, BSportType sportType, int frameType, String stravaName, String stravaId, boolean isRetired) { ... }
      public EquipmentData(long id, String name, BSportType sportType, int frameType, String stravaName, String stravaId) {
          this(id, name, sportType, frameType, stravaName, stravaId, false);
      }
  }
  ```
- In `getEquipmentItems(BSportType)` and `getEquipmentItems()`:
  - Add `RETIRED` to queried columns.
  - Read `isRetired = cursor.getInt(retiredIdx) == 1`.
- Add overloaded `getEquipmentItems(BSportType sportType, boolean activeOnly)`:
  - When `activeOnly == true`, append `AND (Retired IS NULL OR Retired = 0)`.
- Add overloaded `updateEquipment(long id, String name, int frameType, @NonNull List<Long> linkedDeviceIds, boolean isRetired)`:
  - `values.put(RETIRED, isRetired ? 1 : 0);`
- Add helper `setEquipmentRetired(long id, boolean isRetired)`:
  - Direct update of `RETIRED` column.
- Add `isEquipmentRetired(long id): boolean`.

### Step 2: Update `EditWorkoutViewModel.kt`
- When compiling equipment options in `updateEquipmentNames(sportName)`:
  - Find currently assigned equipment: `val currentEquip = equipmentList.find { it.name == workoutData.value?.equipmentName || it.id == workoutData.value?.equipmentId }`.
  - Filter `sportEquipment`:
    ```kotlin
    val sportEquipment = when (currentBSportType) {
        BSportType.BIKE -> equipmentList.filter { it.sportType == BSportType.BIKE && (!it.isRetired || it.id == currentEquip?.id) }.map { it.name }
        BSportType.RUN -> equipmentList.filter { it.sportType == BSportType.RUN && (!it.isRetired || it.id == currentEquip?.id) }.map { it.name }
        else -> emptyList()
    }
    ```
  - Apply the same filter in `showAllBikes()` and `showAllShoes()`.
  - Filter `getFilteredLinkedEquipment`: exclude retired items unless equal to `currentEquip`.

### Step 3: Update `EquipmentAndSportTypeDiscoveryManager.kt`
- In `getLinkedEquipmentIds(activeDeviceIds: Set<Long>)`:
  ```kotlin
  return activeDeviceIds.flatMap { deviceId ->
      equipmentDbHelper.getLinkedEquipmentIdsFromDeviceId(deviceId)
  }.filter { id -> !equipmentDbHelper.isEquipmentRetired(id) }.toSet()
  ```
- In `resolveIdentity`:
  - When candidates are resolved from `gearCandidates`, filter out retired equipment.

### Step 4: Update `SportTypeViewModel.kt`
- In `availableEquipment(bSportType)`:
  - Query active equipment: `dbEquipmentHelper.getEquipmentItems(bSportType).filter { !it.isRetired }`.

### Step 5: Update `EquipmentViewModel.kt`
- In `EquipmentItem`:
  - Add `val isRetired: Boolean = false`.
- In `loadEquipment()`:
  - Populate `isRetired = data.isRetired`.
- In `updateEquipment(item: EquipmentItem)`:
  - Call `dbEquipmentHelper.updateEquipment(item.id, item.name, item.frameType, item.linkedDeviceIds, item.isRetired)`.
- Add `toggleRetired(item: EquipmentItem)`:
  - Call `dbEquipmentHelper.setEquipmentRetired(item.id, !item.isRetired)` and reload.

### Step 6: Update `EditEquipmentDialog.kt`
- Add `var isRetired by remember { mutableStateOf(item.isRetired) }`.
- Insert an interactive switch/row:
  ```kotlin
  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
  ) {
      Text(stringResource(R.string.equipment_retired), style = MaterialTheme.typography.bodyLarge)
      Switch(
          checked = isRetired,
          onCheckedChange = { isRetired = it }
      )
  }
  ```
- Pass `isRetired` to `onConfirm(item.copy(..., isRetired = isRetired))`.

### Step 7: Update `EquipmentTabsScreen.kt`
- In `EquipmentItem`:
  - If `item.isRetired`, display a localized badge:
    ```kotlin
    if (item.isRetired) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.equipment_retired),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
    ```
- In `EquipmentList`:
  - Partition items: `val (activeItems, retiredItems) = items.partition { !it.isRetired }`.
  - Render active items first.
  - If `retiredItems.isNotEmpty()`, render a distinct section header (`Text(stringResource(R.string.equipment_section_retired), ...)`), followed by the retired items.

### Step 8: Multi-Language Localization
- Add string resources in all 9 languages (`values/`, `values-de/`, `values-fr/`, `values-es/`, `values-it/`, `values-nl/`, `values-pl/`, `values-pt/`, `values-ru/`):
  - `equipment_retired`: "Retired" / "Ausgemustert" / "Retiré" / "Retirado" / "Ritirato" / "Buiten gebruik" / "Wycofany" / "Desativado" / "Списано"
  - `equipment_section_active`: "Active Equipment" / "Aktive Ausrüstung" / ...
  - `equipment_section_retired`: "Retired Equipment" / "Ausgemusterte Ausrüstung" / ...

---

## 4. Verification & Testing Plan (TST-UI-111)

### Automated Tests
1. `EquipmentDbHelperRetiredTest.kt`:
   - Test `EquipmentData.isRetired` initialization.
   - Test `updateEquipment` with `isRetired = true` and `isRetired = false`.
   - Test `setEquipmentRetired` and `isEquipmentRetired`.
   - Test `getEquipmentItems(sportType, activeOnly = true)` filtering.
2. `EditWorkoutViewModelRetiredEquipmentTest.kt`:
   - Test workout with no equipment: retired items are filtered out.
   - Test workout with active equipment: retired items are filtered out.
   - Test workout with assigned retired equipment: that specific retired equipment is retained in options.
   - Test `showAllBikes()` and `showAllShoes()` filtering.
3. `EquipmentAndSportTypeDiscoveryManagerRetiredTest.kt`:
   - Test `getLinkedEquipmentIds`: verify retired equipment is excluded.
   - Test `resolveIdentity`: verify retired equipment is not inferred.
4. `SportTypeViewModelRetiredTest.kt`:
   - Test `availableEquipment`: verify retired equipment is excluded.
5. Full repository unit test suite:
   - `./gradlew testDebugUnitTest`: 100% pass (0 failures).
