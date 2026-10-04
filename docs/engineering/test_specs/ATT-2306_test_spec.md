# Stage 2: Requirement & Test Specification - ATT-2306: Optimize Visual Design, Column Alignment, and Spacing in Equipment Sensor Matrix Screen

**Ticket**: [ATT-2306](https://rainerblind.atlassian.net/browse/ATT-2306)  
**Sub-task**: [ATT-2324](https://rainerblind.atlassian.net/browse/ATT-2324) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-40.15`  
**Requirement Mapping**: `REQ-UI-263` (*Equipment Sensor Matrix Table Layout, Column Continuity, and Information Density Optimization*)  
**Test Spec ID**: `TST-UI-222`  
**Branch**: `feature/ATT-2306`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Requirement Specification (REQ-UI-263)

### 1.1 Problem Statement & Rationale
During Sprint Review of ATT-2126, inspecting the "Sensor-Matrix" tab in `EquipmentTabsScreen.kt` revealed that the initial functional implementation had several visual and alignment deficiencies: the top-left cell redundantly displayed "Sensor-Matrix" instead of labeling the equipment column, vertical dividers were discontinuous through section header rows, `STICKY_COLUMN_WIDTH` was overly narrow (156dp) causing harsh single-line text truncation on multi-word gear names ("Brooks Gree...", "New Balanc..."), and sensor column headers lacked subtle column guides down to checkboxes.

### 1.2 Functional & Architectural Requirements
The system SHALL optimize table layout, column alignment, vertical divider continuity, and information density in `EquipmentSensorMatrixScreen.kt` (ATT-2306):

1. **Top-Left Column Header Normalization**:
   - The top-left corner cell of the matrix table SHALL display the localized column header `@string/equipment_title` ("Ausrüstung" / "Equipment") rather than the redundant tab title `equipment_tab_sensor_matrix`.
2. **Continuous Vertical Grid Divider**:
   - The 1dp vertical divider (`outlineVariant.copy(alpha = 0.6f)`) between the sticky equipment column and the horizontally scrollable sensor columns SHALL extend continuously without interruption across all table row types, including the sticky header row, section header rows (`MatrixSectionHeader`), and equipment item rows (`MatrixEquipmentRow`).
3. **Section Header Column Alignment**:
   - `MatrixSectionHeader` SHALL partition its layout into a sticky leading section cell of width `STICKY_COLUMN_WIDTH` (rendering the section icon, section title, and count), followed by the 1dp continuous vertical divider, followed by a matching section surface container spanning the sensor columns.
4. **Expanded Sticky Column Width & Two-Line Typography**:
   - `STICKY_COLUMN_WIDTH` SHALL be set to `184.dp` (expanded from 156.dp).
   - Equipment name `Text` in `MatrixEquipmentRow` SHALL support up to 2 lines (`maxLines = 2`, `overflow = TextOverflow.Ellipsis`, line height = 18sp), ensuring equipment titles up to 25–30 characters render without premature single-line clipping.
5. **Sensor Column Visual Alignment**:
   - Sensor column cells in the sticky top header and content rows SHALL render with subtle vertical separation guides (`outlineVariant.copy(alpha = 0.2f)`), providing clear visual tracking from sensor names down to checkboxes.
6. **Preservation of Functional & Accessibility Invariants**:
   - 1-tap Material 3 Checkbox persistence via `EquipmentViewModel.setSensorLink` and `EquipmentDbHelper.setDeviceLink`, bi-directional reactive sync (`REQ-UI-257`), sticky scrolling behavior, empty state rendering, and 9-language localization parity MUST NOT be altered.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Top-Left Column Header)**:
  * *Given* an athlete viewing the "Sensor-Matrix" tab in `EquipmentTabsScreen`,
  * *When* inspecting the top-left table cell,
  * *Then* it SHALL display "Ausrüstung" (DE) / "Equipment" (EN) matching `R.string.equipment_title`, and SHALL NOT duplicate the tab name "Sensor-Matrix".
* **Criterion 2 (Unbroken Vertical Divider Continuity)**:
  * *Given* the matrix table rendering with Bikes and Shoes sections,
  * *When* scanning vertically down the divider line between equipment names and sensor columns,
  * *Then* the 1dp vertical divider SHALL continue seamlessly through section headers ("Räder (N)", "Schuhe (N)") without visual gaps.
* **Criterion 3 (Equipment Name Legibility & 2-Line Wrapping)**:
  * *Given* an equipment item with a long name (e.g. "Brooks Green Silence 2", "Specialized S-Works Tarmac SL8"),
  * *When* rendered in the sticky left column,
  * *Then* the column width of 184dp and 2-line text wrapping SHALL display the full name or significantly reduce truncation compared to 156dp single-line clipping.
* **Criterion 4 (Sensor Column Visual Guides)**:
  * *Given* multiple paired remote sensors in the matrix,
  * *When* viewing column headers and checkboxes,
  * *Then* subtle vertical guides SHALL visually demarcate sensor columns, aligning header text and checkboxes.
* **Criterion 5 (Zero Functional Regression)**:
  * *Given* any checkbox in the matrix,
  * *When* tapped,
  * *Then* `onToggleLink` SHALL be invoked, updating SQLite via `EquipmentDbHelper.setDeviceLink` with reactive cross-screen sync.

### 1.4 System Invariants
1. Zero modifications to SQLite database schemas (`EQUIPMENT`, `LINKS`, `DEVICES`).
2. Reactive synchronization (`REQ-UI-257`) and checkbox persistence logic remain 100% intact.
3. 9-language localization parity (`REQ-LOC-001`) strictly maintained.
4. Clean-room test suite executes with 100% pass rate (`1,691/1,691`).

---

## 2. Test Specification (TST-UI-222)

### Test Case 1: Matrix Layout & Metrics Contract Test (`TST-UI-222.1`)
* **Scope**: Automated UI / Metric Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentSensorMatrixScreenTest.kt`
* **Preconditions**: Equipment items with short and long names; multiple remote sensors.
* **Action**: Verify layout constants, string resource references, and column parameters in `EquipmentSensorMatrixScreen.kt`.
* **Expected Result**:
  - `STICKY_COLUMN_WIDTH` is equal to `184.dp`.
  - Top-left header resolves `R.string.equipment_title`.
  - Equipment row titles configure `maxLines = 2`.
  - Continuous vertical divider is present across header, section headers, and equipment rows.

### Test Case 2: 9-Language Localization Audit (`TST-UI-222.2`)
* **Scope**: Localization Parity Test
* **Goal**: Verify string presence and valid translations for all referenced string keys (`equipment_title`, `equipment_tab_sensor_matrix`, `equipment_type_bike`, `equipment_type_shoe`, `equipment_retired`) across all 9 locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches.

### Test Case 3: Clean-Room Full Suite Regression (`TST-UI-222.3`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across all 1,691 unit tests with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-222.1` | UI Unit | `EquipmentSensorMatrixScreen` | `REQ-UI-263` | Specified |
| `TST-UI-222.2` | Localization | `values*/strings.xml` | `REQ-UI-263`, `REQ-LOC-001` | Specified |
| `TST-UI-222.3` | Regression | Full Test Suite (`testDebugUnitTest`) | `REQ-PRO-001` | Specified |
