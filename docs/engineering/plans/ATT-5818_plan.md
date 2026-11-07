# Stage 3: Implementation Plan - ATT-5818: ClusterNamingDialog Route Picker Tap Handling

**Ticket**: [ATT-5818](https://rainerblind.atlassian.net/browse/ATT-5818)  
**Sub-task**: [ATT-5917](https://rainerblind.atlassian.net/browse/ATT-5917) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Requirement Mapping**: `REQ-UI-462` (`REQ-UI-462.4`)  
**Test Mapping**: `TST-UI-462` (`TST-UI-462.5`)  
**Branch**: `feature/ATT-5818`  
**Author**: AI Agent 1 (`subagent_pro`)  
**Date**: 2026-11-07  

---

## 1. Problem Description & Background
In `ClusterNamingDialog` (`ImportBackupTabsScreen.kt`), athletes configure route assignment for recurring workouts. Currently, only tapping the trailing route icon (`my_locations`) opens the candidate selection dialog (`WorkoutClusterSelectionDialog`). Tapping directly into the text field body of "Zugeordnete Lieblingsstrecke" (`cluster_naming__selected_route_label`) does nothing because the tap is consumed before bubbling to outer modifiers. The system SHALL wire a `MutableInteractionSource` observing `PressInteraction.Release` to trigger `showSelectionDialog = true` on any tap across the field surface (Rule 77 / Design Guidelines §5.19).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-462` (*Cluster Naming & Selection Dialog*, specifically `REQ-UI-462.4`)
* **Test Mapping**: `TST-UI-462` (*Cluster Naming & Selection Dialog Verification Suite*, specifically `TST-UI-462.5`)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Interaction Regression**: Trailing icon clear (`X`) and picker (`my_locations`) buttons remain fully functional.
2. **Read-Only Preservation**: Text field remains strictly `readOnly = true` (no keyboard popup, no cursor).
3. **Queue Integrity**: Queue progression, bounds animations, and cluster confirmation logic in `ClusterNamingDialog` remain 100% intact.
4. **UI Localization**: 100% 9-language translation parity preserved with zero inline emojis (`ATT-5400`).

---

## 4. Proposed Architectural Changes

### Component 1: `ImportBackupTabsScreen.kt` (UI Layer)
* In `ClusterNamingDialog`:
  - Declare `val routePickerInteractionSource = remember { MutableInteractionSource() }`.
  - Collect `PressInteraction.Release` inside `LaunchedEffect(routePickerInteractionSource)` to set `showSelectionDialog = true`.
  - Pass `interactionSource = routePickerInteractionSource` to the route assignment `OutlinedTextField`.
  - Remove dead outer `Modifier.clickable { showSelectionDialog = true }`.

### Component 2: `ClusterNamingDialogEscapeContractTest.kt` (Test Suite)
* Add `testRoutePickerTextField_bindsInteractionSourceContract()` asserting that `ClusterNamingDialog` binds `MutableInteractionSource` with `PressInteraction.Release` and removes outer `Modifier.clickable` on the route selection field.

### UI Consistency (Rule 23)
* **Reference screen / component**: Existing `ClusterNamingDialog` (`ImportBackupTabsScreen.kt`).
* **Reused components**: `OutlinedTextField`, `MutableInteractionSource`, `PressInteraction.Release`.
* **Theme tokens**: Standard Material 3 `OutlinedTextField` tokens and `MaterialTheme.colorScheme.primary`.
* **New one-off styles & justification**: None. Retains existing styling 1:1.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Wire `MutableInteractionSource` in `ImportBackupTabsScreen.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt`
* Changes: Import `MutableInteractionSource` and `PressInteraction`. Add `routePickerInteractionSource`, `LaunchedEffect` collecting `PressInteraction.Release`, and pass to `OutlinedTextField`.

### Step 2: Implement UI Contract Test in `ClusterNamingDialogEscapeContractTest.kt`
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterNamingDialogEscapeContractTest.kt`
* Changes: Add contract test verifying `MutableInteractionSource` and `PressInteraction.Release` binding (`TST-UI-462.5`).

### Step 3: Targeted Unit Tests & Verification
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.localization.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit test suite and verify living docs via `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Branch isolation (`feature/ATT-5818`) allows clean revert via `git restore .`.
