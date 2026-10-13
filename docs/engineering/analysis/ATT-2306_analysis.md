# Stage 1 Analysis: ATT-2306 - Optimize Visual Design, Column Alignment, and Spacing in Equipment Sensor Matrix Screen

**Ticket**: [ATT-2306](https://rainerblind.atlassian.net/browse/ATT-2306)  
**Sub-task**: [ATT-2323](https://rainerblind.atlassian.net/browse/ATT-2323) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-40.15`  
**Branch**: `feature/ATT-2306`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Statement & Motivation

During the Sprint Review of [ATT-2126](https://rainerblind.atlassian.net/browse/ATT-2126) (Fleet-Wide Equipment-to-Sensor Mapping Matrix), on-device visual inspection revealed several visual polish, layout, and alignment defects on the "Sensor-Matrix" tab in `EquipmentTabsScreen.kt` / `EquipmentSensorMatrixScreen.kt`:

1. **Redundant Top-Left Header Cell**:
   - The top-left corner cell displays "Sensor-Matrix" (`@string/equipment_tab_sensor_matrix`), which is completely redundant since the user is already on the "Sensor-Matrix" tab. A proper table column header labeling the equipment column (e.g. "Ausrüstung" / "Equipment", `@string/equipment_title`) is needed.
2. **Discontinuous Vertical Grid Divider**:
   - The vertical divider between the sticky equipment column and the horizontally scrollable sensor columns stops abruptly at section header rows ("Räder", "Schuhe"). `MatrixSectionHeader` spans across the entire row width without rendering a continuous vertical boundary, creating a fractured grid layout.
3. **Severe Text Truncation in Equipment Names**:
   - `STICKY_COLUMN_WIDTH` is currently hardcoded to `156.dp`. With icon (22dp), spacing (8dp), and horizontal padding (20dp), only 106dp remains for the equipment name. Equipment with realistic multi-word titles (e.g. "Brooks Green Silence", "New Balance 1080", "Saucony Type A") truncates aggressively after 10–12 characters ("Brooks Gree...", "New Balanc..."), even on standard phone screens. Furthermore, `maxLines = 1` prevents 2-line wrapping.
4. **Header Cell Styling & Background Harmony**:
   - The sensor header row uses a stark, flat `surfaceContainerHighest` background that contrasts sharply with content rows without a subtle container border or card elevation, feeling unstyled compared to the rest of the Material 3 app.
5. **Horizontal Spacing & Checkbox Centering**:
   - Sensor column headers and checkbox cells (`SENSOR_COLUMN_WIDTH = 88.dp`) could benefit from subtle vertical dividers between columns and improved vertical rhythm, providing clear visual alignment between sensor names and their corresponding checkboxes.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic inspection of `EquipmentSensorMatrixScreen.kt` indicates:
1. **Top-Left Header**:
   - Lines 130–144 hardcode `Text(stringResource(R.string.equipment_tab_sensor_matrix))`. Because the tab label is already "Sensor-Matrix", this cell should properly label the contents of column 0: the athlete's equipment fleet (`@string/equipment_title` / "Ausrüstung" / "Equipment").
2. **Divider Gap in Section Headers**:
   - In lines 188–194 and 213–219, `MatrixSectionHeader` is rendered as an independent `item` inside `LazyColumn` using a full-width `Surface` with `Row(fillMaxWidth())`. It does not partition the row into a sticky header cell + vertical divider + scrollable tail, causing the 1dp vertical divider (`outlineVariant`) to terminate at the bottom of the sticky header, disappear during section headers, and restart at content rows.
3. **Sticky Column Width Constraints**:
   - Line 48 defines `private val STICKY_COLUMN_WIDTH = 156.dp`. On typical 360–412dp Android devices, dedicating 180–192dp to the sticky equipment column leaves 180–220dp for sensor columns (easily displaying 2+ full sensor columns while smoothly scrolling the rest). Additionally, in `MatrixEquipmentRow` (lines 313–321), `Text(maxLines = 1, overflow = TextOverflow.Ellipsis)` forces single-line truncation. Allowing up to 2 lines (`maxLines = 2`) with flexible line breaking expands readable title length by >150%.
4. **Visual Surface Rhythm**:
   - Header surface uses `surfaceContainerHighest` (2dp shadow) while rows use plain `surface` with a 0.5dp `HorizontalDivider`. Introducing subtle column guides and harmonized Material 3 surface tones creates a clean tabular grid.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Replace redundant "Sensor-Matrix" top-left corner title with localized equipment header (`@string/equipment_title`).
  2. Extend vertical divider continuity through section headers (`MatrixSectionHeader`), guaranteeing an unbroken vertical separator between the equipment column and sensor columns from top to bottom.
  3. Expand `STICKY_COLUMN_WIDTH` from `156.dp` to `184.dp` and configure equipment name `Text` with `maxLines = 2` to eliminate aggressive text truncation.
  4. Enhance sensor column header cells with refined typography, centered alignment, and subtle vertical column dividers for clear visual tracking down to checkboxes.
  5. Harmonize Material 3 surface colors and row divider styling for consistent dark and light mode aesthetics.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Modifying database schemas (`EQUIPMENT`, `LINKS`, `DEVICES`) or persistence queries in `EquipmentDbHelper` (already finalized in ATT-2126 / ATT-2307).
  - Altering the 3-tab structure or FAB placement in `EquipmentTabsScreen.kt`.
  - Modifying single-item edit bottom sheets (`EditEquipmentDialog`, `EditDeviceDialog`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-256` (*Equipment Management: Fleet-Wide Equipment-to-Sensor Mapping Matrix with Checkboxes for Bikes and Shoes*) under Epic `ATT-355` (*Good and consistent UI*).
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-40.15 under `ATT-2126`.
* **Root Reason for Existing Formulation**: The initial implementation established core matrix mechanics: sticky first column, sticky header row, horizontal scroll synchronization, and 1-tap checkbox persistence. Minimal placeholder styling (narrow 156dp column, tab-name header, full-width section headers) was used as a baseline to validate functionality first.
* **Preservation of Core Invariants**: Checkbox toggle persistence (`EquipmentDbHelper.setDeviceLink`), reactive multi-screen synchronization (`REQ-UI-257`), sticky scrolling behavior, and 100% test suite pass rate are strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

### Architectural Changes in `EquipmentSensorMatrixScreen.kt`
1. **Layout Dimensions**:
   - `STICKY_COLUMN_WIDTH`: Increase from `156.dp` to `184.dp` to comfortably fit long brand and model names (e.g. "Brooks Green Silence", "Specialized S-Works Tarmac").
   - `ROW_HEIGHT`: Adjust dynamically or maintain `60.dp` to accommodate 2-line wrapped equipment titles gracefully.
2. **Top-Left Header**:
   - Change label from `R.string.equipment_tab_sensor_matrix` to `R.string.equipment_title` ("Ausrüstung" / "Equipment").
   - Add subtle icon or secondary label (`@string/devices_sensors_title` or subtle diagonal separator) if appropriate, or clean typography matching table header style.
3. **Continuous Section Header Architecture**:
   - Refactor `MatrixSectionHeader` to align with the matrix column structure:
     - Left cell (`184.dp`): Section icon + Section title + count ("🚴 Räder (2)").
     - Vertical divider (`1.dp`): Continues seamlessly down the screen.
     - Right cell: Subtle accent background matching section header surface across scrollable columns.
4. **Sensor Column Dividers**:
   - Add subtle vertical divider line (`outlineVariant.copy(alpha = 0.2f)`) between sensor columns in both the sticky header row and content rows, ensuring athletes can easily scan down from a sensor name to its corresponding checkbox.
5. **Two-Line Text Wrapping**:
   - Update equipment name `Text` composable: `maxLines = 2`, `overflow = TextOverflow.Ellipsis`, `lineHeight = 18.sp`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing features, Compose previews, and unit tests (`1,691/1,691` passing).
  2. 1-tap checkbox persistence via `EquipmentViewModel.setSensorLink` and `EquipmentDbHelper.setDeviceLink` remains unchanged.
  3. Reactive sync with `DeviceDataRepository` (`REQ-UI-257`) remains intact.
  4. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**
  - Pure Compose UI layout and styling optimization confined to `EquipmentSensorMatrixScreen.kt`; zero database, repository, or business logic alterations.
