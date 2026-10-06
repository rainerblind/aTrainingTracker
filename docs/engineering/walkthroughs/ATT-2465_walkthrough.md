# Stage 5: Walkthrough & Verification - ATT-2465: Move bike and shoe sensor matrices into the Sensors view as two additional tabs

**Ticket**: [ATT-2465](https://rainerblind.atlassian.net/browse/ATT-2465)  
**Sub-task**: [ATT-2610](https://rainerblind.atlassian.net/browse/ATT-2610) (`[Test]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-UI-284`  
**Test Mapping**: `TST-UI-244`  
**Branch**: `feature/ATT-2465`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Executive Summary & Verification Overview

Ticket `ATT-2465` moves the fleet-wide equipment-to-sensor mapping matrices out of the Equipment area (`EquipmentTabsScreen.kt`) and into the centralized Sensors view (`DevicesTabbedScreen.kt`) as two dedicated tabs at the very right:
`Verbunden` | `Gekoppelt` | `Bekannt` | `Räder` | `Schuhe`

This structural enhancement aligns with the athlete's natural mental model—where associating sensors with physical bikes and shoes is treated as a core sensor management activity. It also resolves visual crowding caused by stacking Bikes and Shoes tables on a single screen. `EquipmentTabsScreen.kt` has been streamlined to 2 clean tabs (Bikes and Shoes), with the Floating Action Button (FAB) for adding equipment active across both tabs.

---

## 2. Key Architectural & Implementation Enhancements

### 1. Sensors View 5-Tab Architecture (`DevicesTabbedScreen.kt`)
- Expanded `tabTitles` to 5 entries:
  - Tab 0: Connected / Available (`@string/devices_tab_available`)
  - Tab 1: Paired (`@string/devices_tab_paired`)
  - Tab 2: All Known (`@string/devices_tab_known`)
  - Tab 3: Bikes Matrix (`@string/devices_tab_bikes`, "Räder" / "Bikes")
  - Tab 4: Shoes Matrix (`@string/devices_tab_shoes`, "Schuhe" / "Shoes")
- Integrated `PrimaryScrollableTabRow` to guarantee smooth horizontal tab scrolling without text truncation, awkward line wrapping, or clipping across all display sizes and localized string lengths.
- Injected `equipmentViewModel: EquipmentViewModel = viewModel()` into `DevicesTabbedScreen.kt`, bound from `ATrainingTrackerApp.kt` under `NavRoutes.SENSORS`.
- Created separate horizontal scroll states:
  - `val bikesHorizontalScrollState = rememberScrollState()`
  - `val shoesHorizontalScrollState = rememberScrollState()`
  Ensuring scrolling columns in the Bikes table never disturbs the horizontal scroll position in the Shoes table.

### 2. Single-Sport Matrix Composable (`EquipmentSportSensorMatrix`)
- Extracted `@Composable fun EquipmentSportSensorMatrix(...)` in `EquipmentSensorMatrixScreen.kt`.
- Features:
  - Sticky left column (`STICKY_COLUMN_WIDTH = 184.dp`) rendering equipment icon, name, and retirement status.
  - Horizontally scrollable sensor columns (`SENSOR_COLUMN_WIDTH = 88.dp`) displaying sport-compatible sensors prioritized by domain precedence (`REQ-UI-283`).
  - 1-tap Material 3 Checkbox persistence: toggles call `EquipmentViewModel.setSensorLink(equipmentId, sensorId, !isLinked)`.
  - Clean layout without redundant in-tab section banners (since the tab header itself clearly identifies "Räder" and "Schuhe").
  - Seamless participation in the collapsible top app bar nested scroll connection (`connection`).
  - Graceful empty states for missing equipment or missing sensors.

### 3. Equipment View Streamlining (`EquipmentTabsScreen.kt`)
- Streamlined `EquipmentTabsScreen.kt` to exactly 2 tabs:
  - Tab 0: Bikes (`@string/equipment_type_bike`)
  - Tab 1: Shoes (`@string/equipment_type_shoe`)
- Removed the 3rd tab ("Sensor-Matrix") and its associated horizontal pager page.
- Excised conditional FAB gating `if (pagerState.currentPage != 2)` so the FAB for adding equipment renders cleanly and consistently across both tabs.

### 4. 100% 9-Language Localization Parity (`REQ-LOC-001`)
- Added string resources `devices_tab_bikes` and `devices_tab_shoes` across all 9 supported locales:
  - `values/`: "Bikes", "Shoes"
  - `values-de/`: "Räder", "Schuhe"
  - `values-es/`: "Bicicletas", "Zapatillas"
  - `values-fr/`: "Vélos", "Chaussures"
  - `values-it/`: "Biciclette", "Scarpe"
  - `values-ja/`: "バイク", "シューズ"
  - `values-nl/`: "Fietsen", "Schoenen"
  - `values-pl/`: "Rowery", "Buty"
  - `values-pt/`: "Bicicletas", "Sapatilhas"
- Validated with `TranslationParityTest`: 100% parity, 0 missing strings.

---

## 3. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-284.1` | `TST-UI-244.1` | Sensors 5-Tab Architecture & Scrollable Tab Row (`DevicesTabbedScreenContractTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-284.2` | `TST-UI-244.1`, `TST-UI-244.3` | Sport-Specific Equipment Matrices in Tabs 3 & 4 with independent scroll (`DevicesTabbedScreenContractTest.kt`, `EquipmentSensorMatrixContractTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-284.3` | `TST-UI-244.2` | Streamlined 2-Tab EquipmentTabsScreen & Unconditional FAB (`EquipmentSensorMatrixContractTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-284.4` | `TST-UI-244.4` | 100% 9-Language Localization Parity (`TranslationParityTest.kt`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-244.5` | Full Clean-Room Regression Suite (`./gradlew testDebugUnitTest`) | **PASSED** | `Verified` |

---

## 4. Automated Test Evidence

### Targeted Unit & Contract Tests
```text
> Task :app:compileDebugUnitTestKotlin
> Task :app:testDebugUnitTest

com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedScreenContractTest > testDevicesTabbedScreen_fiveTabsContract PASSED
com.atrainingtracker.trainingtracker.ui.equipment.EquipmentSensorMatrixContractTest > testEquipmentTabsScreen_twoTabsAndUnconditionalFabContract PASSED
com.atrainingtracker.trainingtracker.ui.equipment.EquipmentSensorMatrixContractTest > testEquipmentSensorMatrixScreen_structuralContract PASSED
com.atrainingtracker.trainingtracker.ui.equipment.EquipmentSensorMatrixContractTest > testEquipmentSportSensorMatrix_structuralContract PASSED
com.atrainingtracker.trainingtracker.ui.equipment.EquipmentSensorMatrixContractTest > testLocalizationParity_allNineLocalesMustContainNewStringKeys PASSED
com.atrainingtracker.trainingtracker.ui.equipment.EquipmentSensorMatrixContractTest > testEquipmentSensorOrderingContract_screenAndDialogMustApplyPrioritizedOrdering PASSED
com.atrainingtracker.trainingtracker.localization.TranslationParityTest > testAllTranslationsCompleteAndConsistent PASSED

BUILD SUCCESSFUL in 13s
```

---

## 5. UI Consistency & Architectural Alignment (Rule 23)

* **Baseline Reference**: The standard 3-tab layout of `DevicesTabbedScreen.kt` (`Verbunden` / `Gekoppelt` / `Bekannt`):
  * **Tab Row Consistency**: `PrimaryScrollableTabRow` adopts identical container color `MaterialTheme.colorScheme.surfaceContainerHighest`, divider styling, active tab indicators, and typography matching all tabbed screens in the app.
  * **Elevation & Collapsing Behavior**: Both matrix tabs hook into the shared `connection` nested scroll connection, synchronizing with the collapsing header and status bar insets.
  * **Layout Harmony**: Sticky column width (`184.dp`), sensor column width (`88.dp`), and row heights (`56.dp`) preserve established tactile touch targets and readability standards.
  * **Equipment Area Simplification**: With the matrix tab moved to Sensors, `EquipmentTabsScreen.kt` is clean, uncluttered, and displays the standard FAB across both Bikes and Shoes tabs for rapid addition of new items.
