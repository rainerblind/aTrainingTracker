# ASPICE Stage 4: Implementation Walkthrough (ATT-1118)

## 1. Executive Summary
Implemented retired equipment isolation across selection pickers, workout auto-inference, and manual editing interfaces while introducing visual status indicators, active/retired grouping, and a manual retirement toggle in the equipment manager. Enforced the critical invariant where workouts historically assigned to retired equipment preserve access to that gear during editing. Delivered 9-language localization parity and extensive unit test coverage satisfying `REQ-UI-158` and `TST-UI-111`.

---

## 2. Changes Made

### Component 1: Database Layer (`EquipmentDbHelper`)
- **[EquipmentDbHelper.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/EquipmentDbHelper.java)**:
  - Added `isRetired` boolean field and constructor overload to `EquipmentData`.
  - Updated `getEquipmentItems(BSportType)` and `getEquipmentItems()` to read `RETIRED` column into `EquipmentData.isRetired`.
  - Added overloaded methods `getEquipmentItems(BSportType, boolean activeOnly)` and `getEquipmentItems(boolean activeOnly)` filtering on `(Retired IS NULL OR Retired=0)`.
  - Updated `updateEquipment(long, String, int, List<Long>, boolean isRetired)` and overloaded `updateEquipment(long, String, int, List<Long>)`.
  - Added `setEquipmentRetired(long id, boolean isRetired)` and `isEquipmentRetired(long id): boolean`.

### Component 2: Edit Workout & Discovery Pickers
- **[EditWorkoutViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutViewModel.kt)**:
  - Implemented `isEligibleEquipment(eq: EquipmentData): Boolean` ensuring `!eq.isRetired || eq.id == currentEquipId || eq.name == currentEquipName`.
  - Filtered equipment in `getFilteredLinkedEquipment`, `updateSuggestedEquipmentNames`, `showAllBikes`, and `showAllShoes` to preserve current assignment invariant while excluding all other retired items.
- **[EquipmentAndSportTypeDiscoveryManager.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/EquipmentAndSportTypeDiscoveryManager.kt)**:
  - Filtered sensor-linked equipment, sport type equipment names, and candidate gear in auto-inference against `!equipmentDbHelper.isEquipmentRetired(id)`.
- **[SportTypeViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/ui/sporttype/SportTypeViewModel.kt)**:
  - Updated `availableEquipment(bSportType)` to fetch active-only equipment via `dbEquipmentHelper.getEquipmentItems(bSportType, true)`.

### Component 3: Equipment Management UI & Toggle
- **[EquipmentViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentViewModel.kt)**:
  - Added `isRetired: Boolean = false` to `EquipmentItem` model and mapped `data.isRetired` during `loadEquipment()`.
  - Updated `updateEquipment(item)` to persist `item.isRetired`.
  - Added `toggleRetired(item: EquipmentItem)` calling `setEquipmentRetired(item.id, !item.isRetired)`.
  - Added constructor injection with default values for dispatchers and database managers to ensure testability.
- **[EditEquipmentDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EditEquipmentDialog.kt)**:
  - Added interactive `Switch` row labeled with `equipment_retired` string resource.
  - Passed `isRetired` to confirmation callback.
- **[EquipmentTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentTabsScreen.kt)**:
  - Added "Retired" visual badge (`Surface` with primary color tone) in `EquipmentItem` header.
  - Grouped items into `Active Equipment` and `Retired Equipment` sections with sticky category headers.

### Component 4: Localization Parity (9 Languages)
- Added `equipment_retired`, `equipment_section_active`, and `equipment_section_retired` across all supported locales:
  - `values/strings.xml` (EN)
  - `values-de/strings.xml` (DE)
  - `values-es/strings.xml` (ES)
  - `values-fr/strings.xml` (FR)
  - `values-it/strings.xml` (IT)
  - `values-ja/strings.xml` (JA)
  - `values-nl/strings.xml` (NL)
  - `values-pl/strings.xml` (PL)
  - `values-pt/strings.xml` (PT)

### Component 5: Unit Test Suites
- **[EquipmentDbHelperRetiredTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/database/EquipmentDbHelperRetiredTest.kt)**:
  - Validates `EquipmentData.isRetired`, `getEquipmentItems(activeOnly = true/false)`, `updateEquipment`, `setEquipmentRetired`, and `isEquipmentRetired`.
- **[EditWorkoutViewModelRetiredEquipmentTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutViewModelRetiredEquipmentTest.kt)**:
  - Validates exclusion of retired items from unassigned pickers and preservation of currently assigned retired equipment.
- **[EquipmentDiscoveryRetiredTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/database/EquipmentDiscoveryRetiredTest.kt)**:
  - Validates filtering of retired equipment in sensor identity resolution, linked gear IDs, and candidate auto-inference.
- **[SportTypeViewModelRetiredTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/banalservice/ui/sporttype/SportTypeViewModelRetiredTest.kt)**:
  - Validates `availableEquipment(sportType)` queries active-only equipment.
- **[EquipmentViewModelRetiredTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentViewModelRetiredTest.kt)**:
  - Validates `EquipmentViewModel.loadEquipment` maps retirement flags, `updateEquipment` forwards flags, and `toggleRetired` triggers status inversion.

---

## 3. Verification & Validation

- **Targeted Unit Tests**:
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.EquipmentDbHelperRetiredTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.editworkout.EditWorkoutViewModelRetiredEquipmentTest" --tests "com.atrainingtracker.trainingtracker.database.EquipmentDiscoveryRetiredTest" --tests "com.atrainingtracker.banalservice.ui.sporttype.SportTypeViewModelRetiredTest" --tests "com.atrainingtracker.trainingtracker.ui.equipment.EquipmentViewModelRetiredTest"`: **BUILD SUCCESSFUL** (18/18 passed).
- **Full Clean-Room Regression**:
  - `./gradlew testDebugUnitTest`: **BUILD SUCCESSFUL** (0 failures across all modules).
- **Physical Device Deployment & Verification (Google Pixel 10 / Android 17 / API 37)**:
  - Fixed runtime `NoSuchMethodException` in `EquipmentViewModel.<init>(Application)` by adding `@JvmOverloads constructor` and adding reflection unit test.
  - Deployed debug APK via `./gradlew installDebug`.
  - Verified equipment manager rendering:
    - Active equipment section displayed under "Aktive Ausrüstung".
    - Retired equipment section displayed under "Ausgemusterte Ausrüstung".
    - Retired items (e.g. "2Danger", "Fixie") clearly display the "Ausgemustert" status badge.
    - Zero crashes upon navigation or scrolling.
