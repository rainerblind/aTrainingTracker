# Stage 3: Architecture & Implementation Plan - ATT-2306: Optimize Visual Design, Column Alignment, and Spacing in Equipment Sensor Matrix Screen

**Ticket**: [ATT-2306](https://atrainingtracker.atlassian.net/browse/ATT-2306)  
**Sub-task**: [ATT-2325](https://atrainingtracker.atlassian.net/browse/ATT-2325) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-40.15`  
**Requirement Mapping**: `REQ-UI-263` (*Equipment Sensor Matrix Table Layout, Column Continuity, and Information Density Optimization*)  
**Test Mapping**: `TST-UI-222` (*Equipment Sensor Matrix Table Layout, Column Continuity, and Information Density Verification*)  
**Branch**: `feature/ATT-2306`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Description & Background

During the Sprint Review of ATT-2126 (which delivered the functional Equipment-to-Sensor Matrix tab in `EquipmentTabsScreen.kt`), visual inspection of the matrix table revealed key cosmetic and visual alignment issues:
1. **Redundant Top-Left Header**: The top-left corner cell displayed `@string/equipment_tab_sensor_matrix` ("Sensor-Matrix"), redundantly duplicating the selected tab label instead of labeling the column content ("Ausrüstung" / "Equipment").
2. **Discontinuous Vertical Grid Divider**: The 1dp vertical dividing line separating the sticky equipment column from the sensor columns broke at section headers (`MatrixSectionHeader`), creating an interrupted, unpolished visual appearance.
3. **Premature Equipment Name Clipping**: `STICKY_COLUMN_WIDTH` was 156dp and `maxLines = 1`, resulting in aggressive ellipsis truncation on standard multi-word equipment names (e.g. "Brooks Gree...", "Specialized S...").
4. **Sensor Column Demarcation**: Checkbox columns lacked subtle vertical separation guides, making it difficult on wide or scrolled screens to visually trace from the sensor name down to the corresponding checkbox.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-263` (*Equipment Sensor Matrix Table Layout, Column Continuity, and Information Density Optimization*)
* **Test Mapping**: `TST-UI-222` (`TST-UI-222.1` to `TST-UI-222.3`)
* **Living Documentation**: `docs/requirements.md` (`REQ-UI-263`), `docs/tests.md` (`TST-UI-222`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Database / DAO Regressions**: SQLite tables (`EQUIPMENT`, `LINKS`, `DEVICES`), foreign keys, and queries remain 100% untouched.
2. **Preserved Reactive State Sync**: Bi-directional reactive synchronization (`REQ-UI-257`) between `EquipmentDetailsScreen` and `EquipmentSensorMatrixScreen` via `EquipmentViewModel.equipmentList` and `devicesRepository.allDevices` must operate with zero behavioral drift.
3. **1-Tap Persistence Invariant**: Checkbox clicks must directly invoke `onToggleLink(equipmentId, sensorId, checked)` invoking `EquipmentDbHelper.setDeviceLink`.
4. **9-Language Localization Parity**: All referenced string keys (`equipment_title`, `equipment_tab_sensor_matrix`, `equipment_type_bike`, `equipment_type_shoe`, `equipment_retired`) must exist and match format specifications across EN, DE, ES, FR, IT, JA, NL, PL, PT.
5. **Mandatory Programmatic Pre-Check**: Before Stage 4 production code edits, `python3 tools/jira_util.py check-gate ATT-2325` must return exit code 0 (`GATE_PASSED: ATT-2325 is Erledigt`).

---

## 4. Proposed Architectural & UI Layout Changes (SWE.2)

### Target Component: `EquipmentSensorMatrixScreen.kt`

```
+------------------------+---+-------------------+---+-------------------+
| Ausrüstung / Equipment | | | HRM-Pro           | | | Speed Sensor 2    | (Sticky Header, 60dp)
+------------------------+---+-------------------+---+-------------------+
| Räder (2)              | | |                   | | |                   | (Section Header, 36dp)
+------------------------+---+-------------------+---+-------------------+
| Specialized Tarmac     | | |        [X]        | | |        [X]        | (Equipment Row, 56dp)
| SL8 Pro                | | |                   | | |                   | (2-line wrap)
+------------------------+---+-------------------+---+-------------------+
| Schuhe (3)             | | |                   | | |                   | (Section Header, 36dp)
+------------------------+---+-------------------+---+-------------------+
| Brooks Green           | | |        [X]        | | |        [ ]        | (Equipment Row, 56dp)
| Silence 2              | | |                   | | |                   | (2-line wrap)
+------------------------+---+-------------------+---+-------------------+
                           ^                       ^                   ^
               Continuous 1dp divider         Subtle 0.2f column guides
```

#### Detailed Modifications:
1. **Layout Constants**:
   - `STICKY_COLUMN_WIDTH`: Increase from `156.dp` to `184.dp`.
   - `SENSOR_COLUMN_WIDTH`: Retain `88.dp`.
   - `ROW_HEIGHT`: Retain `56.dp`.
   - `SECTION_HEADER_HEIGHT`: Set to `36.dp`.
2. **Top-Left Header Cell**:
   - Replace `stringResource(R.string.equipment_tab_sensor_matrix)` with `stringResource(R.string.equipment_title)`.
3. **Section Header Structural Partitioning (`MatrixSectionHeader`)**:
   - Refactor `MatrixSectionHeader` into a `Row` containing:
     - Sticky left cell: `Modifier.width(STICKY_COLUMN_WIDTH).fillMaxHeight().padding(horizontal = 10.dp)` displaying icon, title, and count.
     - Continuous vertical divider: `Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))`.
     - Trailing background surface: `Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainer)`.
4. **Subtle Sensor Column Guides**:
   - In both sticky sensor header and row cells, embed an aligned 1dp right vertical guide:
     `Box(modifier = Modifier.width(1.dp).fillMaxHeight().align(Alignment.CenterEnd).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)))`.
5. **Two-Line Equipment Name Wrapping**:
   - In `MatrixEquipmentRow`, update the equipment title `Text` composable:
     - `maxLines = 2`
     - `style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp)`
     - `overflow = TextOverflow.Ellipsis`

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Programmatic Gate 3 Pre-Check
* Verify Gate 3 sign-off via:
  ```bash
  python3 tools/jira_util.py check-gate ATT-2325
  ```

### Step 2: Implement UI Refinements in `EquipmentSensorMatrixScreen.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentSensorMatrixScreen.kt`
* Expand `STICKY_COLUMN_WIDTH = 184.dp`.
* Update top-left header cell text to `R.string.equipment_title`.
* Refactor `MatrixSectionHeader` with partitioned sticky leading cell and continuous vertical divider.
* Add subtle vertical column guides (`alpha = 0.2f`) in sensor header cells and `MatrixEquipmentRow` cells.
* Configure `maxLines = 2` with `18.sp` line height in `MatrixEquipmentRow`.

### Step 3: Author Layout & Contract Tests
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentSensorMatrixScreenTest.kt`
* Validate:
  - Metric contract: `STICKY_COLUMN_WIDTH == 184.dp`.
  - Header string contract: Top-left cell references `R.string.equipment_title`.
  - Max lines contract: Equipment name typography configures `maxLines = 2`.
  - Divider continuity contract: Divider exists in header, section header, and rows.

### Step 4: Run Targeted Unit Tests & Verification
* Run targeted tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.equipment.*"
  ```
* Audit 9-language translation parity:
  ```bash
  ./gradlew testDebugUnitTest --tests "*TranslationParityTest*"
  ```

### Step 5: Clean-Room Regression Verification (Stage 5)
* Execute full regression test suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```
* Deploy debug APK onto physical test device (Pixel 10):
  ```bash
  ./gradlew installDebug
  ```
* Author walkthrough deliverable `docs/engineering/walkthroughs/ATT-2306_walkthrough.md`.
* Synchronize living documentation `docs/requirements.md` (`REQ-UI-263`) and `docs/tests.md` (`TST-UI-222`) to `Verified`.
