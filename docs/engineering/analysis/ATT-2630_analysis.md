# Stage 1: Problem Domain & Root Cause Analysis - ATT-2630: Unify bike and shoe icon tinting in equipment sensor matrix

**Ticket**: [ATT-2630](https://atrainingtracker.atlassian.net/browse/ATT-2630)  
**Sub-task**: [ATT-2655](https://atrainingtracker.atlassian.net/browse/ATT-2655) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.2`  
**Branch**: `feature/ATT-2630`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Problem Domain & Forensic Investigation

### 1.1 Context & Background
In the equipment sensor matrix tables (`EquipmentSensorMatrixScreen.kt`), each equipment item is rendered inside `MatrixEquipmentRow` within the sticky left column (`STICKY_COLUMN_WIDTH = 184.dp`). A leading icon distinguishes the equipment category:
* Bikes display `R.drawable.ic_equipment_bike`
* Shoes display `R.drawable.ic_equipment_shoe`

### 1.2 Forensic Root Cause Analysis (RCA)
In `EquipmentSensorMatrixScreen.kt` lines 405–414:
```kotlin
Icon(
    painter = painterResource(
        id = if (isBike) R.drawable.ic_equipment_bike else R.drawable.ic_equipment_shoe
    ),
    contentDescription = null,
    tint = if (item.isRetired) MaterialTheme.colorScheme.outline
           else if (isBike) MaterialTheme.colorScheme.primary
           else MaterialTheme.colorScheme.secondary,
    modifier = Modifier.size(22.dp)
)
```

1. **Disparate Material 3 Color Roles**:
   In Google Material 3 design, `primary` and `secondary` color tokens fulfill distinctly different roles and resolve to different color hues, saturations, and tonal values. In the application's theme, `primary` represents the dominant brand color (vibrant deep blue / cyan), whereas `secondary` represents a subdued, less prominent accent.
2. **Visual Inconsistency Across Rows & Tabs**:
   - In `MatrixEquipmentRow`, active bike icons are tinted with `MaterialTheme.colorScheme.primary`, while active shoe icons are tinted with `MaterialTheme.colorScheme.secondary`.
   - In contrast, `MatrixSectionHeader` (line 348) tints **both** bike and shoe icons uniformly with `MaterialTheme.colorScheme.primary`.
   - In `DevicesTabbedScreen.kt`, where the matrix tables are hosted as dedicated sport-specific tabs (Tab 3: "Räder" and Tab 4: "Schuhe" per `REQ-UI-284`), navigating from Bikes to Shoes abruptly changes the leading icon tint from `primary` to `secondary`.
3. **User Feedback (Sprint 2026-41.1 Review - ATT-2464)**:
   The human reviewer noted that having bike icons in `primary` and shoe icons in `secondary` looks unintentional, noisy, and mismatched. Both icons should use a unified color role for active equipment while cleanly retaining `MaterialTheme.colorScheme.outline` for retired equipment.

---

## 2. Chesterton's Fence & Requirement Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**:
   Refines Clause 4 of `REQ-UI-263` (*Equipment Sensor Matrix Table Layout, Column Continuity, and Information Density Optimization*) and integrates with `REQ-UI-284` (*Sensors View Sport-Specific Equipment Matrix Tabs & Equipment Tabs Consolidation*), targeting `EquipmentSensorMatrixScreen.kt`.
2. **Historical Origin & Commit Trace**:
   - Initial combined matrix screen created in `ATT-2126` (commit `fc10ad81`).
   - Refined in `ATT-2306` (Sprint `2026-40.14`, commit `099cb282`, table layout and column continuity).
   - Relocated into isolated sport tabs in `ATT-2465` (Sprint `2026-40.16`).
3. **Root Reason for Existing Formulation**:
   When the matrix was initially built as a single stacked view containing both bikes and shoes, the author assigned `primary` to bikes and `secondary` to shoes in an attempt to provide additional visual distinction between the two stacked sections. However, the distinct vector assets (`ic_equipment_bike` vs `ic_equipment_shoe`), explicit section headers, and tab isolation in `DevicesTabbedScreen` make the color discrepancy redundant and aesthetically disharmonious.
4. **Preservation of Core Invariants**:
   - Vector drawables remain distinct (`ic_equipment_bike` for bikes, `ic_equipment_shoe` for shoes).
   - Retired equipment items (`item.isRetired == true`) MUST preserve `MaterialTheme.colorScheme.outline` tinting and subdued background styling.
   - 1-tap Material 3 Checkbox persistence and reactive bidirectional synchronization (`REQ-UI-257`) remain 100% intact.
   - Clean-room test suite pass rate remains 100%.

---

## 3. Scope Bounding (`ATT-1250`)

### 3.1 In-Scope Objectives
* Unify active equipment icon tinting in `MatrixEquipmentRow` of `EquipmentSensorMatrixScreen.kt` so that both active bikes and active shoes use `MaterialTheme.colorScheme.primary`.
* Maintain `MaterialTheme.colorScheme.outline` for retired equipment items (`item.isRetired`).
* Author contract tests in `EquipmentSensorMatrixScreenTest.kt` asserting that active equipment rows tint both bike and shoe icons using `MaterialTheme.colorScheme.primary`.
* Synchronize living requirements and test specifications (`REQ-UI-288`, `TST-UI-248`).

### 3.2 Out-of-Scope (Strict Boundaries)
* Changes to `EquipmentTabsScreen.kt`, `DevicesTabbedScreen.kt`, or `EquipmentViewModel.kt`.
* Changes to SQLite database tables (`EQUIPMENT`, `LINKS`) or DAO mapping logic.
* Altering icon sizes (`22.dp`), column widths (`STICKY_COLUMN_WIDTH = 184.dp`), or divider styling.
* Altering retired equipment badge or error text styling.

---

## 4. Proposed Solution & Architecture

In `EquipmentSensorMatrixScreen.kt`, update `MatrixEquipmentRow`:
```kotlin
Icon(
    painter = painterResource(
        id = if (isBike) R.drawable.ic_equipment_bike else R.drawable.ic_equipment_shoe
    ),
    contentDescription = null,
    tint = if (item.isRetired) MaterialTheme.colorScheme.outline
           else MaterialTheme.colorScheme.primary,
    modifier = Modifier.size(22.dp)
)
```

This ensures:
1. Complete visual harmony between `MatrixSectionHeader` and `MatrixEquipmentRow`.
2. Consistent chromatic appearance across Tab 3 ("Räder") and Tab 4 ("Schuhe") in `DevicesTabbedScreen`.
3. Clear contrast against retired equipment (which remains `outline`).
