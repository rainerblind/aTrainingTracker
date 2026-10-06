# Stage 2: Requirement & Test Specification - ATT-2465: Move bike and shoe sensor matrices into the Sensors view as two additional tabs

**Ticket**: [ATT-2465](https://rainerblind.atlassian.net/browse/ATT-2465)  
**Sub-task**: [ATT-2607](https://rainerblind.atlassian.net/browse/ATT-2607) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-UI-284` (*Sensors View Sport-Specific Equipment Matrix Tabs & Equipment Tabs Consolidation*)  
**Test Spec ID**: `TST-UI-244`  
**Branch**: `feature/ATT-2465`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Requirement Specification (REQ-UI-284)

### 1.1 Problem Statement & Rationale
Currently, equipment-to-sensor mapping matrices are hosted as a 3rd tab in `EquipmentTabsScreen.kt` where Bikes and Shoes tables are vertically stacked in one screen. In an athlete's mental model, associating physical sensors with equipment belongs in the Sensors view ("Alle Sensoren" / `DevicesTabbedScreen.kt`). 

The system shall relocate the equipment-to-sensor mapping matrices into `DevicesTabbedScreen.kt` as two dedicated tabs at the very right: "Räder" (Bikes) and "Schuhe" (Shoes), resulting in a clean 5-tab sensor hub: `Verbunden` | `Gekoppelt` | `Bekannt` | `Räder` | `Schuhe`. Each tab shall display an isolated sport-specific matrix table without redundant section banners. `EquipmentTabsScreen.kt` shall be streamlined to 2 clean tabs (`Bikes` and `Shoes`), eliminating UI duplication and cognitive confusion.

### 1.2 Functional & Architectural Requirements

1. **Sensors View 5-Tab Navigation Architecture (`DevicesTabbedScreen.kt`)**:
   - `DevicesTabbedScreen.kt` SHALL provide a 5-tab structure:
     - Tab 0: Connected / Available (`@string/devices_tab_available`)
     - Tab 1: Paired (`@string/devices_tab_paired`)
     - Tab 2: All Known (`@string/devices_tab_known`)
     - Tab 3: Bikes Matrix (`@string/devices_tab_bikes`, "Räder" / "Bikes")
     - Tab 4: Shoes Matrix (`@string/devices_tab_shoes`, "Schuhe" / "Shoes")
   - The tab row SHALL use `PrimaryScrollableTabRow`, ensuring smooth horizontal scrollability without label wrapping or truncation across all screen sizes and locales.
   - `DevicesTabbedScreen` SHALL accept or resolve `EquipmentViewModel: EquipmentViewModel = viewModel()`, observing reactive state flows `bikes`, `shoes`, `bikeSensors`, and `shoeSensors`.

2. **Sport-Specific Equipment-Sensor Matrix Tabs (`DevicesTabbedScreen.kt` & `EquipmentSensorMatrixScreen.kt`)**:
   - **Tab 3: Bikes Equipment Matrix**:
     - Sticky left column (`STICKY_COLUMN_WIDTH = 184.dp`) displaying bike icon, name, and retirement status.
     - Horizontally scrollable columns displaying bike-compatible and shared sensors (`BSportType.BIKE`), prioritized by domain precedence (`REQ-UI-283`).
     - Independent horizontal scroll state (`bikesHorizontalScrollState`).
     - Material 3 Checkbox persistence: 1-tap toggling calls `EquipmentViewModel.setSensorLink(equipmentId, sensorId, !isLinked)`.
     - In-tab section header is omitted (no redundant "Räder" header inside the "Räder" tab).
   - **Tab 4: Shoes Equipment Matrix**:
     - Sticky left column (`STICKY_COLUMN_WIDTH = 184.dp`) displaying shoe icon, name, and retirement status.
     - Horizontally scrollable columns displaying run-compatible and shared sensors (`BSportType.RUN`), prioritized by domain precedence (`REQ-UI-283`).
     - Independent horizontal scroll state (`shoesHorizontalScrollState`).
     - Material 3 Checkbox persistence: 1-tap toggling calls `EquipmentViewModel.setSensorLink(equipmentId, sensorId, !isLinked)`.
     - In-tab section header is omitted (no redundant "Schuhe" header inside the "Schuhe" tab).
   - Both matrix tabs SHALL participate in the collapsing top app bar nested scroll connection (`connection`).
   - If equipment or sensors are absent, informative sport-specific empty state placeholders SHALL be displayed.

3. **Equipment View Streamlining (`EquipmentTabsScreen.kt`)**:
   - `EquipmentTabsScreen.kt` SHALL revert to a clean 2-tab structure:
     - Tab 0: Bikes (`@string/equipment_type_bike`)
     - Tab 1: Shoes (`@string/equipment_type_shoe`)
   - The redundant 3rd tab ("Sensor-Matrix") SHALL be removed.
   - The floating action button (FAB) for adding new equipment SHALL be rendered for both tabs without conditional suppression.

4. **100% 9-Language Localization Parity (`REQ-LOC-001`)**:
   - String resources `devices_tab_bikes` and `devices_tab_shoes` SHALL be defined across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

### 1.3 Acceptance Criteria (Given-When-Then)

* **AC-1 (5-Tab Sensors View Structure)**:
  * *Given* an athlete opening the Sensors view (`DevicesTabbedScreen`),
  * *When* inspecting the tab bar,
  * *Then* 5 tabs SHALL be displayed in order: "Verbunden", "Gekoppelt", "Bekannt", "Räder", "Schuhe".
* **AC-2 (Dedicated Bikes Matrix Tab)**:
  * *Given* the athlete navigates to Tab 3 ("Räder") in `DevicesTabbedScreen`,
  * *When* the tab content renders,
  * *Then* strictly the Bikes matrix table SHALL be displayed with sticky bike name column and horizontally scrollable bike/shared sensors.
* **AC-3 (Dedicated Shoes Matrix Tab)**:
  * *Given* the athlete navigates to Tab 4 ("Schuhe") in `DevicesTabbedScreen`,
  * *When* the tab content renders,
  * *Then* strictly the Shoes matrix table SHALL be displayed with sticky shoe name column and horizontally scrollable run/shared sensors.
* **AC-4 (Independent Horizontal Scroll Isolation)**:
  * *Given* the athlete scrolls sensor columns horizontally in Tab 3 ("Räder"),
  * *When* switching to Tab 4 ("Schuhe"),
  * *Then* the horizontal scroll state of the Shoes table SHALL remain completely independent and un-scrolled.
* **AC-5 (1-Tap Checkbox Persistence & Cross-Screen Sync)**:
  * *Given* an unlinked checkbox in either matrix tab,
  * *When* tapped,
  * *Then* the checkbox immediately toggles to checked, updating SQLite table `LINKS`, and reactively synchronizing with `EditEquipmentDialog`.
* **AC-6 (Streamlined 2-Tab Equipment View)**:
  * *Given* the athlete opens the Equipment view (`EquipmentTabsScreen`),
  * *When* inspecting the screen,
  * *Then* only 2 tabs SHALL be present ("Räder" and "Schuhe"), and the FAB for adding equipment SHALL be active on both tabs.
* **AC-7 (9-Language Parity)**:
  * *Given* all 9 supported application locales,
  * *When* running `TranslationParityTest`,
  * *Then* zero missing entries and zero format specifier mismatches SHALL occur.

### 1.4 System Invariants & Chesterton's Fence Audit
* **Requirement Archaeology**: Refines and amends `REQ-UI-256` (*Equipment Management: Fleet-Wide Equipment-to-Sensor Mapping Matrix with Checkboxes for Bikes and Shoes*).
* **Historical Origin**: Commit `fc10ad81` (ATT-2126), refined in ATT-2382 and ATT-2464.
* **Root Reason for Existing Formulation**: The matrix was initially placed in `EquipmentTabsScreen` as an equipment configuration view. The human decision in ATT-2465 establishes that sensor mapping belongs in the Sensors hub as dedicated tabs.
* **Preservation of Core Invariants**: 1-tap checkbox persistence, bidirectional synchronization (`REQ-UI-257`), sticky columns (`REQ-UI-263`), sport-specific sensor precedence (`REQ-UI-283`), and 100% test pass rate remain strictly preserved.

---

## 2. Test Specification (TST-UI-244)

### Test Case 1: `DevicesTabbedScreenContractTest.kt` (`TST-UI-244.1`)
* **Scope**: Architectural & Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedScreenContractTest.kt`
* **Preconditions**: Headless static code structure analysis.
* **Action**: Verify `DevicesTabbedScreen.kt`:
  1. Contains 5 tabs including `devices_tab_bikes` and `devices_tab_shoes`.
  2. Uses `PrimaryScrollableTabRow`.
  3. Binds pages 3 and 4 to the Bikes and Shoes equipment matrices respectively.
  4. Accepts `EquipmentViewModel`.
* **Expected Result**: Assertions pass, verifying the 5-tab contract.

### Test Case 2: `EquipmentTabsScreenContractTest.kt` (`TST-UI-244.2`)
* **Scope**: Architectural & Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentSensorMatrixContractTest.kt`
* **Preconditions**: `EquipmentTabsScreen.kt` refactored.
* **Action**: Verify `EquipmentTabsScreen.kt`:
  1. Defines exactly 2 tabs: `equipment_type_bike` and `equipment_type_shoe`.
  2. Does NOT reference `equipment_tab_sensor_matrix`.
  3. Does NOT invoke `EquipmentSensorMatrixScreen`.
  4. Displays FAB on both tabs.
* **Expected Result**: Assertions pass, verifying the streamlined 2-tab contract.

### Test Case 3: Single-Sport Matrix Composable Verification (`TST-UI-244.3`)
* **Scope**: Unit & Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentSportSensorMatrixTest.kt`
* **Preconditions**: Matrix composable rendered with mock equipment and sensors.
* **Action**: Verify that sport matrix table renders sticky equipment column (`184.dp`), sensor column width (`88.dp`), independent scroll states, and handles empty equipment or empty sensors gracefully.
* **Expected Result**: Clean layout without section header duplication.

### Test Case 4: 9-Language Localization Audit (`TST-UI-244.4`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Action**: Verify `devices_tab_bikes` and `devices_tab_shoes` across all 9 locales:
  * EN: "Bikes", "Shoes"
  * DE: "Räder", "Schuhe"
  * ES: "Bicicletas", "Zapatillas"
  * FR: "Vélos", "Chaussures"
  * IT: "Biciclette", "Scarpe"
  * JA: "バイク", "シューズ"
  * NL: "Fietsen", "Schoenen"
  * PL: "Rowery", "Buty"
  * PT: "Bicicletas", "Sapatilhas"
* **Expected Result**: 100% parity, zero missing entries.

### Test Case 5: Clean-Room Regression Suite (`TST-UI-244.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with 0 regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-244.1` | Contract | `DevicesTabbedScreenContractTest` | `REQ-UI-284.1`, `REQ-UI-284.2` | Specified |
| `TST-UI-244.2` | Contract | `EquipmentSensorMatrixContractTest` | `REQ-UI-284.3` | Specified |
| `TST-UI-244.3` | Unit/Contract | `EquipmentSportSensorMatrixTest` | `REQ-UI-284.2` | Specified |
| `TST-UI-244.4` | Localization | `TranslationParityTest` | `REQ-UI-284.4`, `REQ-LOC-001` | Specified |
| `TST-UI-244.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
