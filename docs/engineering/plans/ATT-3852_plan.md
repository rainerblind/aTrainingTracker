# Stage 3: Implementation Plan - ATT-3852: Replace flat 40-item SensorType dropdown in EditSensorFieldDialog with categorized and searchable sensor value selector

**Ticket**: [ATT-3852](https://rainerblind.atlassian.net/browse/ATT-3852)  
**Sub-task**: [ATT-3866](https://rainerblind.atlassian.net/browse/ATT-3866) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.7`  
**Requirement Mapping**: `REQ-UI-395`  
**Test Mapping**: `TST-UI-358`  
**Branch**: `feature/ATT-3852`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Problem Description & Background

In `EditSensorFieldDialog.kt`, selecting a `SensorType` for a tracking cockpit tile previously opened a flat `ExposedDropdownMenuBox` (`Spinner`) listing all available `SensorType` metrics in an ungrouped, unsearchable list. With 43 `SensorType` values in `SensorType.java`, athletes had to scroll through dozens of ungrouped options to configure a sensor field.

This plan implements `REQ-UI-395` by:
1. Defining `SensorCategory.java` in `com.atrainingtracker.banalservice.sensor` with 7 ordered categories (`TIME_AND_LAPS`, `SPEED_PACE_AND_DISTANCE`, `CADENCE_AND_RUNNING`, `HEART_RATE_AND_ENERGY`, `POWER_AND_PEDALING`, `ELEVATION_AND_NAVIGATION`, `SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS`), each carrying `@StringRes int titleResId` and `@DrawableRes int iconResId`.
2. Binding every one of the 43 `SensorType` constants in `SensorType.java` to its canonical `SensorCategory` via `getCategory()`.
3. Replacing the flat `SensorType` `Spinner` in `EditSensorFieldDialog.kt` with a categorized and searchable `SensorTypeSelectorSection` (and pure testable filter helper `SensorTypeSearchFilter`), without introducing nested `verticalScroll` or `LazyColumn` inside `AppModalBottomSheet` (`docs/design_guidelines.md` Section 5.8).
4. Adding localized string resources for the 7 category titles, search hint, and empty-search state across all 9 supported locales.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-395` (*Categorized & Searchable SensorType Selector in EditSensorFieldDialog*)
* **Test Mapping**: `TST-UI-358` (*Categorized & Searchable SensorType Selector, 7-Category Exhaustive Partition & 9-Language Parity Verification Suite*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing `ModalBottomSheetDialogsIntegrityTest` (`TST-UI-102`), `SensorFieldZoneDisplayTest`, and `SensorFieldZoneGaugeTest` continue to pass cleanly.
2. **Preserve `SensorType` Enum Declaration Order (`ordinal()`)**: All 43 `SensorType` constants remain in their exact declaration order in `SensorType.java` so `Parcelable` and any ordinal/name lookups remain 100% backward-compatible.
3. **No Nested `verticalScroll` Inside `AppModalBottomSheet` (`docs/design_guidelines.md` Section 5.8)**: `SensorTypeSelectorSection` uses standard Compose `Column` / `Row` / `Surface` layouts so the outer `AppModalBottomSheet` handles vertical scrolling cleanly without infinite-height constraint crashes.
4. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Domain Layer (`com.atrainingtracker.banalservice.sensor.SensorCategory` & `SensorType`)
* **New File**: `app/src/main/java/com/atrainingtracker/banalservice/sensor/SensorCategory.java`
  * Enum with 7 constants:
    1. `TIME_AND_LAPS(R.string.sensor_category_time_and_laps, R.drawable.ic_time_active)`
    2. `SPEED_PACE_AND_DISTANCE(R.string.sensor_category_speed_pace_and_distance, R.drawable.ic_speed)`
    3. `CADENCE_AND_RUNNING(R.string.sensor_category_cadence_and_running, R.drawable.ic_cadence)`
    4. `HEART_RATE_AND_ENERGY(R.string.sensor_category_heart_rate_and_energy, R.drawable.ic_heart_rate)`
    5. `POWER_AND_PEDALING(R.string.sensor_category_power_and_pedaling, R.drawable.ic_power)`
    6. `ELEVATION_AND_NAVIGATION(R.string.sensor_category_elevation_and_navigation, R.drawable.ic_altitude)`
    7. `SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS(R.string.sensor_category_system_environment_and_diagnostics, R.drawable.ic_battery_full)`
  * Methods: `getTitleResId()`, `getTitle(Context)`, `getIconResId()`.
* **Modified File**: `app/src/main/java/com/atrainingtracker/banalservice/sensor/SensorType.java`
  * Add `private final SensorCategory category;` and `public SensorCategory getCategory()`.
  * Assign each of the 43 `SensorType` constants to its canonical `SensorCategory` (`5 + 4 + 2 + 2 + 13 + 9 + 8 = 43`).

### Component 2: UI Layer (`EditSensorFieldDialog.kt` & `SensorTypeSearchFilter`)
* **Modified File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldDialog.kt`
  * Add pure helper object `SensorTypeSearchFilter` with `filterByQuery(items: List<SensorType>, query: String, fullNameResolver: (SensorType) -> String, shortNameResolver: (SensorType) -> String, unitResolver: (SensorType) -> String): List<SensorType>` matching case-insensitively across localized full name, localized short name, localized unit, and enum name.
  * Replace the flat `Spinner` for `R.string.sensor` with `SensorTypeSelectorSection`:
    - Displays a clickable `OutlinedCard` / `Surface` (`RoundedCornerShape(12.dp)`) showing the label `stringResource(R.string.sensor)`, the selected `SensorType`'s category title, full name, short name, and unit badge (`MyHelper.getUnitsId(sensorType)`), and an expand/collapse chevron.
    - When expanded, renders:
      1. `OutlinedTextField` search input (`R.string.sensor_type_search_hint`) with leading `Icons.Default.Search` and trailing clear (`Icons.Default.Clear`) icon button.
      2. Collapsible `SensorCategory` accordion groups (`RoundedCornerShape(12.dp)`):
         - When search query is blank: shows non-empty categories from `availableSensorTypesForCurrentActivityType` (falling back to `SensorType.entries` if empty), with only `selectedSensorType?.category` initially expanded (`docs/design_guidelines.md` Section 3).
         - When search query is non-blank: filters available sensors via `SensorTypeSearchFilter.filterByQuery` and automatically expands all matching categories (or renders `R.string.sensor_type_no_matches` if empty).
      3. Each metric row renders the full name (`bodyMedium`, `FontWeight.SemiBold` when selected), short name subtitle (`labelSmall`, `onSurfaceVariant`), unit badge (`RoundedCornerShape(8.dp)` when `unitId != R.string.units_none`), and `Icons.Default.Check` when selected; clicking a metric invokes `onSensorTypeSelected(sensorType)` and collapses the selector panel.
    - Preserves `Spinner` for `Source Device` and `View Size`, plus `ConfigureFilterDialog`, `AthleteWeightDialog` (`POWER_TO_WEIGHT`), and FTP baseline display (`INTENSITY_FACTOR` / `TSS`).

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: `AdvancedTuningDialog.kt` / `EditSensorFieldDialog.kt` (collapsible accordion sections inside modal bottom sheet)
* **Reused components**: `AppModalBottomSheet`, `AppDialogActions.SaveCancel`, `OutlinedTextField`
* **Theme tokens**: shapes `RoundedCornerShape(12.dp)` for category cards and `RoundedCornerShape(8.dp)` for unit badges (`docs/design_guidelines.md` Section 5.3), spacing `4.dp`, `8.dp`, `12.dp`, `16.dp`, `24.dp` (Section 5.2), colors `MaterialTheme.colorScheme.surfaceContainerLow`, `secondaryContainer`, `onSecondaryContainer`, `surfaceVariant`, `onSurfaceVariant`, `primary`, `outlineVariant` (Section 5.4)
* **New one-off styles & justification**: None. Follows existing accordion and `AppModalBottomSheet` patterns.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Add 9-Language String Resources (`REQ-UI-395.3`)
* Files: `app/src/main/res/values*/strings.xml` (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`)
* Changes: Add `sensor_category_time_and_laps`, `sensor_category_speed_pace_and_distance`, `sensor_category_cadence_and_running`, `sensor_category_heart_rate_and_energy`, `sensor_category_power_and_pedaling`, `sensor_category_elevation_and_navigation`, `sensor_category_system_environment_and_diagnostics`, `sensor_type_search_hint`, and `sensor_type_no_matches`.

### Step 2: Create `SensorCategory.java` & Update `SensorType.java` (`REQ-UI-395.1`)
* Files:
  * `app/src/main/java/com/atrainingtracker/banalservice/sensor/SensorCategory.java`
  * `app/src/main/java/com/atrainingtracker/banalservice/sensor/SensorType.java`
* Changes: Define `SensorCategory` enum and map all 43 `SensorType` constants to their canonical `SensorCategory`.

### Step 3: Implement `SensorTypeSelectorSection` & `SensorTypeSearchFilter` in `EditSensorFieldDialog.kt` (`REQ-UI-395.2`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldDialog.kt`
* Changes: Replace the flat `SensorType` `Spinner` with `SensorTypeSelectorSection` and `SensorTypeSearchFilter`, and add Light/Dark `@Preview` composables.

### Step 4: Write & Execute Targeted Unit and Contract Tests (`TST-UI-358`)
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/SensorCategoryAndSelectorContractTest.kt`
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.SensorCategoryAndSelectorContractTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.ModalBottomSheetDialogsIntegrityTest"`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit and contract tests in Stage 4, followed by full clean-room regression suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: `git revert` on `feature/ATT-3852` cleanly restores the previous `SensorType.java` and `EditSensorFieldDialog.kt` without affecting `sprint/2026-42.7`.
