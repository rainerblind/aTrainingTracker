# Stage 2: Requirement & Test Specification - ATT-2630: Unify bike and shoe icon tinting in equipment sensor matrix

**Ticket**: [ATT-2630](https://atrainingtracker.atlassian.net/browse/ATT-2630)  
**Sub-task**: [ATT-2656](https://atrainingtracker.atlassian.net/browse/ATT-2656) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.2`  
**Requirement Mapping**: `REQ-UI-288` (*Equipment Sensor Matrix Bike and Shoe Icon Tint Unification*)  
**Test Spec ID**: `TST-UI-248`  
**Branch**: `feature/ATT-2630`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Requirement Specification (REQ-UI-288)

### 1.1 Problem Statement & Rationale
In `EquipmentSensorMatrixScreen.kt`, the leading category icon in `MatrixEquipmentRow` displays `ic_equipment_bike` for bikes and `ic_equipment_shoe` for shoes. However, active bikes are tinted with `MaterialTheme.colorScheme.primary` while active shoes are tinted with `MaterialTheme.colorScheme.secondary`. In Material 3, `primary` and `secondary` resolve to disparate color hues, creating visual dissonance across rows and across tabs in `DevicesTabbedScreen` (Tab 3: "Räder" vs Tab 4: "Schuhe"). Furthermore, `MatrixSectionHeader` uniformly tints both bike and shoe icons using `primary`.

To achieve visual harmony and consistent chromatic semantics, all active equipment icons (both bikes and shoes) in `MatrixEquipmentRow` MUST be tinted with `MaterialTheme.colorScheme.primary`, while retired equipment items (`item.isRetired`) MUST remain tinted with `MaterialTheme.colorScheme.outline`.

### 1.2 Functional & Architectural Requirements
The system SHALL unify active equipment icon tinting across all equipment categories in the equipment sensor matrix (ATT-2630):
1. *Icon Tint Unification (`EquipmentSensorMatrixScreen.kt`)*:
   - In `MatrixEquipmentRow`, the leading icon tint SHALL evaluate to `MaterialTheme.colorScheme.outline` when `item.isRetired == true`.
   - When `item.isRetired == false`, the leading icon tint SHALL evaluate to `MaterialTheme.colorScheme.primary` for both bikes (`isBike == true`) and shoes (`isBike == false`).
   - `MaterialTheme.colorScheme.secondary` SHALL NOT be used for active equipment icon tinting in `MatrixEquipmentRow`.
2. *Asset & Header Parity*:
   - The vector assets `ic_equipment_bike` and `ic_equipment_shoe` SHALL continue to distinguish bike and shoe categories.
   - `MatrixSectionHeader` SHALL preserve `MaterialTheme.colorScheme.primary` tinting for both bike and shoe headers.
3. *Material 3 Theme Consistency*:
   - Icon sizing (`22.dp`), row height (`56.dp`), sticky column width (`184.dp`), and subtle divider colors SHALL remain strictly preserved.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Active Bike Icon Tint)**:
  * *Given* an active (non-retired) bike item rendered in `MatrixEquipmentRow`,
  * *When* the row composable resolves its icon tint,
  * *Then* the tint SHALL evaluate to `MaterialTheme.colorScheme.primary`.
* **Criterion 2 (Active Shoe Icon Tint)**:
  * *Given* an active (non-retired) shoe item rendered in `MatrixEquipmentRow`,
  * *When* the row composable resolves its icon tint,
  * *Then* the tint SHALL evaluate to `MaterialTheme.colorScheme.primary` (matching active bikes).
* **Criterion 3 (Retired Equipment Icon Tint)**:
  * *Given* a retired equipment item (either bike or shoe, `item.isRetired == true`),
  * *When* the row composable resolves its icon tint,
  * *Then* the tint SHALL evaluate to `MaterialTheme.colorScheme.outline`.
* **Criterion 4 (Header Alignment & Tab Harmonization)**:
  * *Given* the athlete switching between Tab 3 ("Räder") and Tab 4 ("Schuhe") in `DevicesTabbedScreen`,
  * *When* observing the section header and equipment rows,
  * *Then* both views SHALL present uniform `primary` chromatic accenting for active items.

### 1.4 System Invariants
- Database schemas for `EQUIPMENT` and `LINKS` MUST NOT be altered.
- Checkbox toggle state handling and reactive bidirectional persistence (`REQ-UI-257`) MUST NOT be altered.
- All existing contract tests in `EquipmentSensorMatrixScreenTest` and `EquipmentSensorMatrixContractTest` MUST continue to pass with 100% success.
- Zero unit test regressions across the clean-room test suite (`./gradlew testDebugUnitTest`).

---

## 2. Test Specification (TST-UI-248)

### Test Case 1: `EquipmentSensorMatrixScreenTest_testEquipmentIconTintContract` (`TST-UI-248.1`)
* **Scope**: Contract / Layout Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentSensorMatrixScreenTest.kt`
* **Preconditions**: `EquipmentSensorMatrixScreen.kt` source available.
* **Action**:
  1. Inspect `MatrixEquipmentRow` composable in `EquipmentSensorMatrixScreen.kt`.
  2. Verify active equipment icon tint does not differentiate between bike and shoe (`MaterialTheme.colorScheme.primary` for both).
  3. Verify `MaterialTheme.colorScheme.secondary` is not referenced for equipment icon tinting in `MatrixEquipmentRow`.
  4. Verify retired items retain `MaterialTheme.colorScheme.outline`.
* **Expected Result**: Assertions pass cleanly, guaranteeing architectural consistency.

### Test Case 2: `EquipmentSensorMatrixContractTest_sportTabsIntegration` (`TST-UI-248.2`)
* **Scope**: Integration Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentSensorMatrixContractTest.kt`
* **Action**:
  1. Verify sport tabs integration in `DevicesTabbedScreen` remains unbroken.
  2. Verify 2-tab structure in `EquipmentTabsScreen` remains unbroken.
* **Expected Result**: All assertions in `EquipmentSensorMatrixContractTest` pass.

### Test Case 3: 9-Language Localization Audit (`TST-UI-248.3`)
* **Scope**: Localization Parity Test
* **Goal**: Verify existing string resources (`Equipment`, `equipment_type_bike`, `equipment_type_shoe`, `devices_tab_bikes`, `devices_tab_shoes`) remain 100% intact across all 9 supported locales: EN, DE, ES, FR, IT, JA, NL, PL, PT.
* **Expected Result**: 100% parity, zero missing entries.

### Test Case 4: Clean-Room Regression Suite (`TST-UI-248.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: 100% pass rate across all project unit tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-248.1` | Contract / Unit | `EquipmentSensorMatrixScreenTest` | `REQ-UI-288` | Specified |
| `TST-UI-248.2` | Integration Contract | `EquipmentSensorMatrixContractTest` | `REQ-UI-288`, `REQ-UI-284` | Specified |
| `TST-UI-248.3` | Localization | `TranslationParityTest` / `strings.xml` | `REQ-UI-288`, `REQ-UI-106` | Specified |
| `TST-UI-248.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
