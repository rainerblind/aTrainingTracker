# Stage 3: Implementation Plan - ATT-2465: Move bike and shoe sensor matrices into the Sensors view as two additional tabs

**Ticket**: [ATT-2465](https://rainerblind.atlassian.net/browse/ATT-2465)  
**Sub-task**: [ATT-2608](https://rainerblind.atlassian.net/browse/ATT-2608) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-UI-284`  
**Test Mapping**: `TST-UI-244`  
**Branch**: `feature/ATT-2465`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Problem Description & Background

In the current architecture, fleet-wide equipment-to-sensor mapping matrices are hosted as a 3rd tab in `EquipmentTabsScreen.kt` where Bikes and Shoes tables are vertically stacked in a single scrollable container. From an athlete's mental model, associating physical sensors with equipment belongs in the Sensors view ("Alle Sensoren" / `DevicesTabbedScreen.kt`).

Ticket `ATT-2465` relocates the equipment-to-sensor mapping matrices into `DevicesTabbedScreen.kt` as two dedicated tabs at the very right: "Räder" (Bikes) and "Schuhe" (Shoes). This creates a unified 5-tab sensor management hub (`Verbunden` | `Gekoppelt` | `Bekannt` | `Räder` | `Schuhe`). Furthermore, `EquipmentTabsScreen.kt` is streamlined back to 2 clean tabs (`Bikes` and `Shoes`), eliminating UI duplication and cognitive confusion.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-284` (*Sensors View Sport-Specific Equipment Matrix Tabs & Equipment Tabs Consolidation*)
* **Test Mapping**: `TST-UI-244` (*Sensors View Sport-Specific Equipment Matrix Tabs & Equipment Tabs Consolidation Verification*)
  * `TST-UI-244.1`: Sensors View 5-Tab Architecture (`DevicesTabbedScreenContractTest.kt`)
  * `TST-UI-244.2`: EquipmentTabsScreen 2-Tab Contract (`EquipmentSensorMatrixContractTest.kt`)
  * `TST-UI-244.3`: Single-Sport Matrix Verification (`EquipmentSportSensorMatrixTest.kt`)
  * `TST-UI-244.4`: 9-Language Localization Audit (`TranslationParityTest.kt`)
  * `TST-UI-244.5`: Full Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: All 2002+ existing unit tests continue to pass with 100% success rate.
2. **1-Tap Checkbox Persistence & Bidirectional Sync**: Tapping any checkbox immediately updates SQLite table `LINKS` via `EquipmentViewModel.setSensorLink` and synchronizes reactively with single-equipment dialogs (`EditEquipmentDialog`, `EditDeviceDialog`).
3. **Independent Horizontal Scroll States**: Scrolling bike sensor columns horizontally in Tab 3 does not affect shoe sensor columns in Tab 4.
4. **App Bar Collapsing Compatibility**: Matrix vertical scrolling integrates seamlessly with `CollapsingAppBarNestedScrollConnection` in `DevicesTabbedScreen`.
5. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
6. **Parent Human Gate Invariance**: Terminal completion of parent ticket `ATT-2465` remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: 9-Language String Resources (`strings_devices.xml`)
* Define `devices_tab_bikes` ("Räder" / "Bikes") and `devices_tab_shoes` ("Schuhe" / "Shoes") across all 9 supported locales:
  * `values/`: "Bikes", "Shoes"
  * `values-de/`: "Räder", "Schuhe"
  * `values-es/`: "Bicicletas", "Zapatillas"
  * `values-fr/`: "Vélos", "Chaussures"
  * `values-it/`: "Biciclette", "Scarpe"
  * `values-ja/`: "バイク", "シューズ"
  * `values-nl/`: "Fietsen", "Schoenen"
  * `values-pl/`: "Rowery", "Buty"
  * `values-pt/`: "Bicicletas", "Sapatilhas"

### Component 2: Single-Sport Matrix Composable (`EquipmentSportSensorMatrix`)
* In `EquipmentSensorMatrixScreen.kt`:
  * Introduce `@Composable fun EquipmentSportSensorMatrix(...)` to render a single sport category's matrix table.
  * Parameters: `items: List<EquipmentItem>`, `sensors: List<SimpleSensorInfo>`, `isBike: Boolean`, `onToggleLink: (equipmentId: Long, sensorId: Long, isLinked: Boolean) -> Unit`, `appBarOffsetPx: Int`, `headerHeightPx: Float`, `scrollState: LazyListState`, `horizontalScrollState: ScrollState`, `emptyEquipmentMessage: String`, `emptySensorsMessage: String`.
  * Omits redundant section header banners (`MatrixSectionHeader`), maximizing vertical viewport space.
  * Preserves sticky equipment name/icon column (`STICKY_COLUMN_WIDTH = 184.dp`) with 2-line text wrapping (`REQ-UI-263`).
  * Preserves domain priority column ordering (`REQ-UI-283`).

### Component 3: Sensors Screen 5-Tab Hub (`DevicesTabbedScreen.kt`)
* Accept `equipmentViewModel: EquipmentViewModel = viewModel()`.
* Expand tab model to 5 tabs:
  - Tab 0: Connected / Available (`@string/devices_tab_available`)
  - Tab 1: Paired (`@string/devices_tab_paired`)
  - Tab 2: All Known (`@string/devices_tab_known`)
  - Tab 3: Bikes Matrix (`@string/devices_tab_bikes`)
  - Tab 4: Shoes Matrix (`@string/devices_tab_shoes`)
* Tab bar: `PrimaryScrollableTabRow` (already implemented) smoothly hosts all 5 tabs.
* Reactive state observation: observe `bikes`, `shoes`, `bikeSensors`, and `shoeSensors` from `equipmentViewModel`.
* `HorizontalPager`:
  - Pages 0..2: `DeviceListScreen(...)`
  - Page 3: `EquipmentSportSensorMatrix` for bikes
  - Page 4: `EquipmentSportSensorMatrix` for shoes
* Separate scroll states for pages 3 and 4: `bikesListState`, `shoesListState`, `bikesHorizontalScrollState`, `shoesHorizontalScrollState`.

### Component 4: EquipmentTabsScreen Streamlining (`EquipmentTabsScreen.kt`)
* Revert `tabs` list to 2 items: `listOf(stringResource(R.string.equipment_type_bike), stringResource(R.string.equipment_type_shoe))`.
* Remove page 2 from `HorizontalPager`.
* Display FAB unconditionally on both tabs (remove `if (pagerState.currentPage != 2)` gating).

### UI Consistency (Rule 23 — Mandatory Section)
* **Reference screen / component**: `DevicesTabbedScreen.kt` and `EquipmentSensorMatrixScreen.kt`.
* **Reused components**:
  * Tab row: `PrimaryScrollableTabRow` and `Tab` from Material 3.
  * Pager: `HorizontalPager` with `rememberPagerState`.
  * Collapsing App Bar: `CollapsingAppBarNestedScrollConnection` and `LayoutConstants.COMPACT_HEADER_CONTENT_HEIGHT`.
  * Matrix: `MatrixTableHeaderRow`, `MatrixEquipmentRow`, `FastScrollableBox`, `EmptyStatePlaceholder`.
* **Theme tokens**:
  * Backgrounds: `MaterialTheme.colorScheme.surfaceContainerHighest`, `surfaceVariant`, `surface`.
  * Dividers & Borders: `MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)` (1.dp vertical guide) and `0.35f` (row separator).
  * Dimensions: `STICKY_COLUMN_WIDTH = 184.dp`, `SENSOR_COLUMN_WIDTH = 88.dp`, `ROW_HEIGHT = 56.dp`, `HEADER_ROW_HEIGHT = 60.dp`.
  * Typography: `MaterialTheme.typography.titleSmall`, `labelSmall`, `bodySmall`, `bodyMedium`.
* **New one-off styles & justification**: None. 100% existing design tokens, components, and layout dimensions are reused.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Localization Strings in 9 Locales
* Files: `app/src/main/res/values*/strings_devices.xml` (all 9 locales).
* Changes: Add `devices_tab_bikes` and `devices_tab_shoes`.

### Step 2: Single-Sport Matrix Composable Extraction
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentSensorMatrixScreen.kt`
* Changes:
  * Extract and expose `@Composable fun EquipmentSportSensorMatrix(...)`.
  * Retain `EquipmentSensorMatrixScreen` for backward compatibility or delegate to `EquipmentSportSensorMatrix`.

### Step 3: Expand `DevicesTabbedScreen.kt` to 5 Tabs
* File: `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedScreen.kt`
* Changes:
  * Inject `equipmentViewModel: EquipmentViewModel = viewModel()`.
  * Expand `tabs` list to 5 items when displaying tabs.
  * Map page 3 to Bikes `EquipmentSportSensorMatrix` and page 4 to Shoes `EquipmentSportSensorMatrix`.
  * Provide independent `LazyListState` and `ScrollState` for tabs 3 and 4.

### Step 4: Streamline `EquipmentTabsScreen.kt` to 2 Tabs
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentTabsScreen.kt`
* Changes:
  * Reduce `tabs` list to 2 items (`equipment_type_bike`, `equipment_type_shoe`).
  * Remove page 2 (`EquipmentSensorMatrixScreen`) from `HorizontalPager`.
  * Display FAB unconditionally on both tabs.

### Step 5: Author Contract & Unit Tests
* Files:
  * `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedScreenContractTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentSensorMatrixContractTest.kt`
* Changes:
  * Assert `DevicesTabbedScreen` 5-tab structure, `PrimaryScrollableTabRow`, and matrix rendering.
  * Update `EquipmentSensorMatrixContractTest` asserting `EquipmentTabsScreen` 2-tab layout and FAB behavior.

### Step 6: Targeted Test Verification
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedScreenContractTest" \
                              --tests "com.atrainingtracker.trainingtracker.ui.equipment.EquipmentSensorMatrixContractTest" \
                              --tests "com.atrainingtracker.trainingtracker.TranslationParityTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted contract and localization unit tests during Stage 4 construction, followed by full clean-room regression test run (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation (`feature/ATT-2465`) allows immediate `git checkout sprint/2026-41.1` and branch deletion without impacting the sprint integration baseline.
