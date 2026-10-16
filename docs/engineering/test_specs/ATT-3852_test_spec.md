# Stage 2: Requirement & Test Specification - ATT-3852: Replace flat 40-item SensorType dropdown in EditSensorFieldDialog with categorized and searchable sensor value selector

**Ticket**: [ATT-3852](https://rainerblind.atlassian.net/browse/ATT-3852)  
**Sub-task**: [ATT-3865](https://rainerblind.atlassian.net/browse/ATT-3865) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2177](https://rainerblind.atlassian.net/browse/ATT-2177) (*UI & UX Refinements*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.7`  
**Requirement Mapping**: `REQ-UI-395` (*Categorized & Searchable SensorType Selector in EditSensorFieldDialog*)  
**Test Spec ID**: `TST-UI-358`  
**Branch**: `feature/ATT-3852`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Requirement Specification (`REQ-UI-395`)

### 1.1 Problem Statement & Rationale
`EditSensorFieldDialog.kt` previously passed all 43 `SensorType.entries` (`SensorType.java`) as a flat, alphabetically sorted `DropdownSelector` (`ExposedDropdownMenuBox`). Because `SensorType` spans 43 telemetry metrics across timing, speed/distance, cadence/strides, heart rate/calories, cycling power/pedaling dynamics, elevation/navigation, and system/environment diagnostics—and localized names rearrange alphabetical order across languages—athletes had to scroll through a 43-item ungrouped menu to find a metric. Introducing an authoritative `SensorCategory` domain enum and replacing the flat `SensorType` dropdown in `EditSensorFieldDialog.kt` with a categorized accordion and instant search selector (`SensorTypeSelectorSection`) enables immediate metric discovery while respecting `AppModalBottomSheet` scroll constraints (`docs/design_guidelines.md` Section 3 & Section 5.8).

### 1.2 Functional & Architectural Requirements
1. **Domain Categorization Enum (`REQ-UI-395.1`)**:
   * The system SHALL define `com.atrainingtracker.banalservice.sensor.SensorCategory` with 7 ordered enum values and localized title string resource IDs (`getTitleResId()`):
     1. `TIME_AND_LAPS` (`R.string.sensor_category_time_and_laps`) — 5 metrics: `TIME_ACTIVE`, `TIME_TOTAL`, `TIME_LAP`, `TIME_OF_DAY`, `LAP_NR`
     2. `SPEED_PACE_AND_DISTANCE` (`R.string.sensor_category_speed_pace_and_distance`) — 4 metrics: `SPEED_mps`, `PACE_spm`, `DISTANCE_m`, `DISTANCE_m_LAP`
     3. `CADENCE_AND_RUNNING` (`R.string.sensor_category_cadence_and_running`) — 2 metrics: `CADENCE`, `STRIDES`
     4. `HEART_RATE_AND_ENERGY` (`R.string.sensor_category_heart_rate_and_energy`) — 2 metrics: `HR`, `CALORIES`
     5. `POWER_AND_PEDALING` (`R.string.sensor_category_power_and_pedaling`) — 13 metrics: `POWER`, `NORMALIZED_POWER`, `POWER_TO_WEIGHT`, `INTENSITY_FACTOR`, `TSS`, `WORK_KJ`, `TORQUE`, `PEDAL_POWER_BALANCE`, `PEDAL_SMOOTHNESS`, `PEDAL_SMOOTHNESS_L`, `PEDAL_SMOOTHNESS_R`, `TORQUE_EFFECTIVENESS_L`, `TORQUE_EFFECTIVENESS_R`
     6. `ELEVATION_AND_NAVIGATION` (`R.string.sensor_category_elevation_and_navigation`) — 9 metrics: `ALTITUDE`, `ASCENT`, `DESCENT`, `SLOPE`, `VERTICAL_SPEED`, `BEARING`, `LINE_DISTANCE_m`, `LATITUDE`, `LONGITUDE`
     7. `SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS` (`R.string.sensor_category_system_environment_and_diagnostics`) — 8 metrics: `PHONE_BATTERY`, `BATTERY_REMAINING_TIME`, `SENSORS`, `ACCUMULATED_SENSORS`, `ACCURACY`, `TEMPERATURE`, `TEMPERATURE_MIN`, `TEMPERATURE_MAX`
   * Every `SensorType` constant in `SensorType.java` (`43` total) SHALL declare a non-null `SensorCategory` field exposed via `public SensorCategory getCategory()`.
2. **Categorized Accordion & Searchable Selector in `EditSensorFieldDialog.kt` (`REQ-UI-395.2`)**:
   * `EditSensorFieldDialog.kt` SHALL replace the flat `DropdownSelector` for `SensorType` with a dedicated `SensorTypeSelectorSection` composable.
   * `SensorTypeSelectorSection` SHALL render a compact summary card displaying the currently selected `SensorType`'s category title, full localized name (`getFullNameId()`), short name (`getShortNameId()`), and unit (`MySensorManager.getUnitIds(type)`), which toggles an inline expandable selector panel when clicked.
   * When expanded, the selector panel SHALL display:
     - A search `OutlinedTextField` (`R.string.sensor_type_search_hint`) with a leading `Icons.Default.Search` icon and a trailing clear (`Icons.Default.Clear`) button when the query is non-empty.
     - **Browse Mode (Blank Search Query)**: All 7 `SensorCategory` groups rendered as collapsible accordion sections (`RoundedCornerShape(12.dp)`), with only the category of the currently selected `SensorType` initially expanded (`expandedCategories = setOf(selectedSensorType.category)`) and all other categories collapsed (`docs/design_guidelines.md` Section 3).
     - **Search Mode (Non-Blank Search Query)**: Case-insensitive substring matching (`SensorTypeSearchFilter.filterByQuery`) against the localized full name, localized short name, localized unit, and enum name, automatically expanding all categories with matching metrics and hiding empty categories (or displaying `R.string.sensor_type_no_matches` if zero metrics match).
     - Each `SensorType` option row SHALL display the localized full name, a secondary subtitle with the localized short name, and a trailing unit badge (`RoundedCornerShape(8.dp)`) when a unit is defined, highlighting the selected `SensorType` with `MaterialTheme.colorScheme.secondaryContainer` and `Icons.Default.Check`.
3. **Bottom Sheet Scroll Safety & 9-Language Localization Parity (`REQ-UI-395.3` — Rule 23, Section 5.8)**:
   * `SensorTypeSelectorSection` SHALL render as a standard non-scrollable `Column` inside `EditSensorFieldDialog` without introducing any nested `Modifier.verticalScroll` or unbounded `LazyColumn` inside `AppModalBottomSheet(scrollable = true)`.
   * All 9 new string resources (`sensor_category_time_and_laps`, `sensor_category_speed_pace_and_distance`, `sensor_category_cadence_and_running`, `sensor_category_heart_rate_and_energy`, `sensor_category_power_and_pedaling`, `sensor_category_elevation_and_navigation`, `sensor_category_system_environment_and_diagnostics`, `sensor_type_search_hint`, `sensor_type_no_matches`) SHALL be translated across all 9 supported application locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Complete 43-Metric Domain Partition)**:
  * *Given* `SensorType.values()` and `SensorCategory.values()`,
  * *When* inspected in unit tests,
  * *Then* all 43 `SensorType` constants have a non-null `getCategory()` matching the exact 7-category partition (`5 + 4 + 2 + 2 + 13 + 9 + 8 = 43`), and every `SensorCategory` has a valid non-zero `getTitleResId()`.
* **Criterion 2 (Categorized Accordion & Search Filtering)**:
  * *Given* `SensorTypeSelectorSection` in `EditSensorFieldDialog.kt`,
  * *When* the user opens the sensor value selector with an empty search query,
  * *Then* the 7 `SensorCategory` accordion headers are shown with the active `SensorType`'s category initially expanded; and when the user enters a search query (e.g. `"power"`, `"W/kg"`, `"cad"`, `"bpm"`), matching `SensorType` items across categories are returned case-insensitively by full name, short name, unit, or enum identifier.
* **Criterion 3 (Bottom Sheet Scroll Safety & Preserved Downstream Controls)**:
  * *Given* `EditSensorFieldDialog.kt` hosted inside `AppModalBottomSheet`,
  * *When* inspected by contract tests,
  * *Then* `EditSensorFieldDialog.kt` contains zero nested `verticalScroll` or `LazyColumn` calls, and all downstream controls (Zone Display Mode, Zone Gauge toggle, Athlete Weight configuration for `POWER_TO_WEIGHT`, View Type, Accumulation Period, Source device, Text Size) remain 100% preserved.

### 1.4 System Invariants
* Zero nested `verticalScroll` or `LazyColumn` inside `AppModalBottomSheet` (`docs/design_guidelines.md` Section 5.8).
* 100% preservation of all 43 `SensorType` enum names and ordinals (`TrackingViewsDatabaseManager` SQLite compatibility).
* 9-language localization parity across all 9 locales (`REQ-UI-106`).
* 100% clean-room unit test suite pass rate (`./gradlew testDebugUnitTest`).

---

## 2. Test Specification (`TST-UI-358`)

### Test Case 1: `SensorCategoryExhaustiveMappingTest` (`TST-UI-358.1`)
* **Scope**: Unit Test (`app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/SensorCategoryAndSelectorContractTest.kt`)
* **Preconditions**: `SensorCategory` and `SensorType` loaded on JVM.
* **Action**:
  1. Assert `SensorCategory.values().size == 7` and every category has `getTitleResId() != 0`.
  2. Assert `SensorType.values().size == 43` and every `SensorType` has a non-null `getCategory()`.
  3. Assert the exact count and membership of each of the 7 `SensorCategory` partitions (`TIME_AND_LAPS` = 5, `SPEED_PACE_AND_DISTANCE` = 4, `CADENCE_AND_RUNNING` = 2, `HEART_RATE_AND_ENERGY` = 2, `POWER_AND_PEDALING` = 13, `ELEVATION_AND_NAVIGATION` = 9, `SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS` = 8).
  4. Verify `SensorTypeSearchFilter.filterByQuery` matches case-insensitively by full name, short name, unit string, and enum name, and returns all items when query is blank.
* **Expected Result**: All assertions pass 100%.

### Test Case 2: `EditSensorFieldSelectorSourceContractAndLocalizationTest` (`TST-UI-358.2`)
* **Scope**: Source Contract & 9-Language Localization Parity Test (`SensorCategoryAndSelectorContractTest.kt`)
* **Action**:
  1. Inspect `EditSensorFieldDialog.kt` and assert it defines and invokes `SensorTypeSelectorSection`, references `SensorCategory`, `R.string.sensor_type_search_hint`, and `R.string.sensor_type_no_matches`, and contains zero `verticalScroll(` or `LazyColumn(` calls (`docs/design_guidelines.md` Section 5.8).
  2. Verify all 9 new string keys exist and are non-blank across all 9 `values*/strings.xml` files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
* **Expected Result**: 100% source contract compliance and 9-locale parity.

### Test Case 3: Clean-Room Regression Suite (`TST-UI-358.3`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the entire unit test suite, including existing `SensorFieldZoneDisplayTest.kt` and `SensorFieldZoneGaugeTest.kt`.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-358.1` | Unit | `SensorCategory`, `SensorType.getCategory()`, `SensorTypeSearchFilter` | `REQ-UI-395.1`, `REQ-UI-395.2` | Specified |
| `TST-UI-358.2` | Contract & Localization | `EditSensorFieldDialog.kt`, 9 `values*/strings.xml` | `REQ-UI-395.2`, `REQ-UI-395.3`, `REQ-UI-106` | Specified |
| `TST-UI-358.3` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
