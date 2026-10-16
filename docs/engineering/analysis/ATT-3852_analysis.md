# Stage 1 Analysis: ATT-3852 - Replace flat 40-item SensorType dropdown in EditSensorFieldDialog with categorized and searchable sensor value selector

**Ticket**: [ATT-3852](https://rainerblind.atlassian.net/browse/ATT-3852)  
**Sub-task**: [ATT-3864](https://rainerblind.atlassian.net/browse/ATT-3864) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.7`  
**Branch**: `feature/ATT-3852`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Problem Statement & Motivation

In [`EditSensorFieldDialog.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldDialog.kt#L81-L90), selecting a `SensorType` for a tracking cockpit tile currently opens a flat `ExposedDropdownMenuBox` (`Spinner`) listing all available `SensorType` metrics (up to 36–43 entries) in ungrouped array order (`ALTITUDE`, `ASCENT`, `DESCENT`, ..., `BATTERY_REMAINING_TIME`). Scrolling through ~40 ungrouped items in a narrow dropdown menu makes configuring tracking views tedious and error-prone.

During Ceremony 1 (Sprint-Start Screening), the human user and Scrum Master aligned on:
1. **Domain Categorization in `com.atrainingtracker.banalservice.sensor` (`SensorCategory` + `SensorType.getCategory()`)**: Introducing a `SensorCategory` enum in `com.atrainingtracker.banalservice.sensor` (`TIME_AND_LAPS`, `SPEED_PACE_AND_DISTANCE`, `CADENCE_AND_RUNNING`, `HEART_RATE_AND_ENERGY`, `POWER_AND_PEDALING`, `ELEVATION_AND_NAVIGATION`, `SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS`) with `@StringRes int titleResId` and `@DrawableRes int iconResId`, bound directly on every `SensorType` constant so the Java compiler enforces exhaustive categorization across all 43 `SensorType` values.
2. **Categorized & Searchable Selector in `EditSensorFieldDialog.kt`**: Replacing the flat `SensorType` `Spinner` in `EditSensorFieldDialog.kt` with a categorized, searchable selector featuring a quick search/filter input (`OutlinedTextField` matching localized full name and short name) and collapsible/expandable category sections (`SensorCategory`) displaying each metric's icon (`SensorType.getIconResId()`), full name, short name, and unit preview, with the active metric's category expanded by default.

---

## 2. Root Cause Analysis (Forensic Investigation)

1. **`SensorType.java` Lacks Domain Category Metadata**:
   - [`SensorType.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/sensor/SensorType.java#L42-L206) defines 43 enum constants owning `fullNameId`, `shortNameId`, `unitId`, `sensorValueType`, `myFormatter`, `filteringPossible`, `CORE_METRICS`, and `getIconResId()`, but has no `SensorCategory` grouping.
2. **Flat `Spinner` in `EditSensorFieldDialog.kt`**:
   - [`EditSensorFieldDialog.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldDialog.kt#L81-L90) renders:
     ```kotlin
     Spinner(
         label = stringResource(R.string.sensor),
         items = uiState.availableSensorTypesForCurrentActivityType.map { it.getFullName(context) },
         selectedItem = uiState.selectedSensorType?.getFullName(context) ?: "",
         onItemSelected = { index ->
             viewModel.onSensorTypeChanged(uiState.availableSensorTypesForCurrentActivityType[index])
         }
     )
     ```
   - This presents a flat, unsearchable list without category grouping, icons, short names, or unit hints.
3. **Scrollable Host Invariant (`AppModalBottomSheet` — Rule 23 / Section 5.8)**:
   - `EditSensorFieldDialog` is hosted inside `AppModalBottomSheet` (which defaults to `scrollable = true`, wrapping its content in `Modifier.verticalScroll(rememberScrollState())`). Per `docs/design_guidelines.md` Section 5.8 (`No Nested verticalScroll Inside AppModalBottomSheet`), any categorized selector inline or modal inside `EditSensorFieldDialog` must never nest an unbounded `LazyColumn` or second `verticalScroll` inside `AppModalBottomSheet`'s scrollable column! Rendering the categorized accordion sections directly as a standard Compose `Column` (or inside a dedicated selector dialog/sheet with bounded height) prevents infinite height constraint crashes while allowing smooth vertical scrolling of the bottom sheet.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Create `SensorCategory.java` in `com.atrainingtracker.banalservice.sensor` with 7 canonical categories (`TIME_AND_LAPS`, `SPEED_PACE_AND_DISTANCE`, `CADENCE_AND_RUNNING`, `HEART_RATE_AND_ENERGY`, `POWER_AND_PEDALING`, `ELEVATION_AND_NAVIGATION`, `SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS`), localized title resource IDs, and drawable icon resource IDs.
  * Assign every one of the 43 `SensorType` enum constants in `SensorType.java` to its canonical `SensorCategory` and expose `getCategory()`.
  * Replace the flat 40-item `SensorType` `Spinner` in `EditSensorFieldDialog.kt` with a categorized, searchable sensor value selector (`SensorTypeSelectorField` / expandable selector with search bar, category accordions, metric icon, full name, short name, and unit preview).
  * Add localized string resources for the 7 category titles and search placeholder across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do not alter the `Source Device` or `View Size` spinners or the `ConfigureFilterDialog` flow in `EditSensorFieldDialog.kt`.
  * Do not alter SQLite database schemas or `SensorType.ordinal()` / `name()` persistence order.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-UI-395`, complementing `REQ-UI-149` (*Modernized Modal Bottom Sheet Dialogs & Selection Flows*, `ATT-1031`) and `REQ-UI-167` (`ATT-1276`).
* **Historical Origin & Commit Trace**: `ATT-1031` migrated `EditSensorFieldDialog` from a legacy Fragment dialog to Jetpack Compose `AppModalBottomSheet`, preserving the legacy flat `Spinner` for `SensorType` as a 1:1 port.
* **Root Reason for Existing Formulation**: At the time of `ATT-1031`, the focus was on modal bottom sheet standardization rather than redesigning the 40-item sensor type list.
* **Preservation of Core Invariants**: `AppModalBottomSheet` hosting (`TST-UI-102` / `ModalBottomSheetDialogsIntegrityTest`), `viewModel.onSensorTypeChanged(...)` smart filter defaults, `AthleteWeightDialog` for `POWER_TO_WEIGHT`, FTP baseline display for `INTENSITY_FACTOR`/`TSS`, and `SensorType` enum declaration order (`ordinal()`) remain 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution

1. **Domain Layer (`com.atrainingtracker.banalservice.sensor`)**:
   - `SensorCategory.java`: Enum with 7 constants (`TIME_AND_LAPS`, `SPEED_PACE_AND_DISTANCE`, `CADENCE_AND_RUNNING`, `HEART_RATE_AND_ENERGY`, `POWER_AND_PEDALING`, `ELEVATION_AND_NAVIGATION`, `SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS`), each holding `@StringRes int titleResId` and `@DrawableRes int iconResId` (`getTitleResId()`, `getTitle(Context)`, `getIconResId()`).
   - `SensorType.java`: Add `private final SensorCategory category;` constructor field and `public SensorCategory getCategory()` getter, mapping all 43 `SensorType` constants to their exact category per the ticket specification while keeping the exact enum constant declaration order intact.
2. **UI Layer (`EditSensorFieldDialog.kt`)**:
   - Replace the `SensorType` `Spinner` with `CategorizedSensorTypeSelector`:
     - Displays the currently selected `SensorType` (icon, full name, short name/unit badge, and category subtitle) in an interactive selector card/field that toggles the categorized picker panel (or opens inline with search + collapsible category sections).
     - Includes a search `OutlinedTextField` (`R.string.sensor_search_hint`) with clear button that filters `availableSensorTypesForCurrentActivityType` case-insensitively by `getFullName(context)` and `getShortName(context)`.
     - Groups available sensor types by `SensorCategory.values()` (preserving activity-type relevance and showing non-empty categories), with collapsible accordion headers (`docs/design_guidelines.md` Section 3 — initially expanding the category of `selectedSensorType`, or expanding all matching categories when a search query is active).
     - Renders each `SensorType` option with its icon (`safePainterResource(sensorType.iconResId)`), full name, short name, and unit string (`MyHelper.getUnitsId(sensorType)`), highlighting the currently selected `SensorType`.
     - Zero nested `verticalScroll` or unbounded `LazyColumn` inside `AppModalBottomSheet` (`docs/design_guidelines.md` Section 5.8).

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. All 43 `SensorType` enum constants retain their exact declaration order (`ordinal()`) and `Parcelable` behavior.
  2. `ModalBottomSheetDialogsIntegrityTest` (`TST-UI-102`) continues to pass.
  3. Zero nested `verticalScroll` inside `AppModalBottomSheet` (`docs/design_guidelines.md` Section 5.8).
  4. 100% 9-language localization parity (`TranslationParityTest`).
  5. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** — Additive domain categorization (`SensorCategory`) and self-contained UI selector enhancement in `EditSensorFieldDialog.kt`.
