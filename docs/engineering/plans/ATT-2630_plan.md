# Stage 3: Implementation Plan - ATT-2630: Unify bike and shoe icon tinting in equipment sensor matrix

**Ticket**: [ATT-2630](https://atrainingtracker.atlassian.net/browse/ATT-2630)  
**Sub-task**: [ATT-2657](https://atrainingtracker.atlassian.net/browse/ATT-2657) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.2`  
**Requirement Mapping**: `REQ-UI-288` (*Equipment Sensor Matrix Bike and Shoe Icon Tint Unification*)  
**Test Mapping**: `TST-UI-248` (*Equipment Sensor Matrix Bike and Shoe Icon Tint Unification Verification*)  
**Branch**: `feature/ATT-2630`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Problem Description & Root Cause Analysis

### 1.1 Problem Statement
In the equipment sensor matrix (`EquipmentSensorMatrixScreen.kt`), equipment rows are rendered within `MatrixEquipmentRow`. Active bike items tint their category icon (`ic_equipment_bike`) using `MaterialTheme.colorScheme.primary`, while active shoe items tint their category icon (`ic_equipment_shoe`) using `MaterialTheme.colorScheme.secondary`. 

In Material 3, `primary` and `secondary` resolve to disparate color hues, creating visual dissonance across rows and across tabs when navigating between Tab 3 ("Räder") and Tab 4 ("Schuhe") in `DevicesTabbedScreen`. Furthermore, `MatrixSectionHeader` uniformly tints both bike and shoe icons with `MaterialTheme.colorScheme.primary`.

### 1.2 Root Cause Summary
1. **Disparate Color Semantics in Material 3**: In `EquipmentSensorMatrixScreen.kt` lines 410–412:
   ```kotlin
   tint = if (item.isRetired) MaterialTheme.colorScheme.outline
          else if (isBike) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.secondary,
   ```
   Assigning `secondary` to active shoes was an ad-hoc heuristic from an earlier combined-table implementation (`ATT-2126`).
2. **Inconsistency with Section Headers & Tabs**: `MatrixSectionHeader` applies `MaterialTheme.colorScheme.primary` to both bike and shoe header icons, and tabbed sport isolation (`REQ-UI-284`) renders the color shift between tabs visually jarring and mismatched.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-288` (*Equipment Sensor Matrix Bike and Shoe Icon Tint Unification*)
* **Test Mapping**: `TST-UI-248` (*Equipment Sensor Matrix Bike and Shoe Icon Tint Unification Verification*)
  * `TST-UI-248.1`: Unit & Contract tint verification in `EquipmentSensorMatrixScreenTest.kt` asserting that active bike and shoe icons both evaluate to `MaterialTheme.colorScheme.primary`, retired items evaluate to `MaterialTheme.colorScheme.outline`, and `secondary` is not used.
  * `TST-UI-248.2`: Tab integration verification in `EquipmentSensorMatrixContractTest.kt`.
  * `TST-UI-248.3`: 9-language localization audit (`TranslationParityTest.kt`).
  * `TST-UI-248.4`: Full clean-room regression test suite (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Category Vector Distinctness**: Bikes continue to use `R.drawable.ic_equipment_bike`, shoes continue to use `R.drawable.ic_equipment_shoe`.
2. **Retired State Integrity**: Equipment items marked `item.isRetired == true` MUST continue to tint with `MaterialTheme.colorScheme.outline` and use subdued row backgrounds.
3. **Database & ViewModel Invariance**: Zero changes to SQLite databases (`EQUIPMENT`, `LINKS`), DAOs, or `EquipmentViewModel.kt`.
4. **Layout Dimension Invariants**: Sticky column width (`184.dp`), sensor column width (`88.dp`), row height (`56.dp`), and icon size (`22.dp`) remain unaltered.
5. **Gate 3 Pre-Check Invariant (Rule 4)**: Before modifying production code in `app/src/...`, verify `python3 tools/jira_util.py check-gate ATT-2657` exits with code 0.
6. **Parent Human Gate Invariance**: `ATT-2630` completion is strictly reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component: `EquipmentSensorMatrixScreen.kt`

In `MatrixEquipmentRow`:
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

### UI Consistency (Rule 23)
* **Closest existing reference screen**: `MatrixSectionHeader` in `EquipmentSensorMatrixScreen.kt` (lines 345–351), which already uses `MaterialTheme.colorScheme.primary` for both bike and shoe category icons.
* **Reused components**: Standard Material 3 `Icon` and `EquipmentItem` model.
* **Theme tokens**: `MaterialTheme.colorScheme.primary` for active items; `MaterialTheme.colorScheme.outline` for retired items.
* **New one-off styles & justification**: None. This change strictly removes an ad-hoc one-off usage of `secondary` and harmonizes with the rest of the application.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update Icon Tint in `EquipmentSensorMatrixScreen.kt`
* Modify `MatrixEquipmentRow` to tint active icons with `MaterialTheme.colorScheme.primary` regardless of whether `isBike` is true or false.
* Preserve `MaterialTheme.colorScheme.outline` when `item.isRetired` is true.

### Step 2: Author Unit / Contract Test in `EquipmentSensorMatrixScreenTest.kt`
* Add test `testEquipmentIconTintContract_unifiesActiveBikeAndShoeToPrimary()`:
  * Verify `MatrixEquipmentRow` in `EquipmentSensorMatrixScreen.kt` assigns `MaterialTheme.colorScheme.primary` for active equipment.
  * Verify `MaterialTheme.colorScheme.secondary` is not referenced for equipment icon tinting.
  * Verify `MaterialTheme.colorScheme.outline` is assigned for retired equipment.

### Step 3: Run Targeted Unit Tests
* Execute:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.equipment.EquipmentSensorMatrixScreenTest" --tests "com.atrainingtracker.trainingtracker.ui.equipment.EquipmentSensorMatrixContractTest"`
* Verify all tests pass with 100% success.

---

## 6. Verification & Rollback Plan

* **Verification**: Execute targeted unit tests during Stage 4 construction, followed by full clean-room `./gradlew testDebugUnitTest` in Stage 5.
* **Rollback**: Work is isolated on `feature/ATT-2630`. Any regression can be reverted cleanly by discarding commits on `feature/ATT-2630` before merging into `sprint/2026-41.2`.
