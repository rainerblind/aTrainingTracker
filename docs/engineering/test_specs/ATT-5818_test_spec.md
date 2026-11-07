# Stage 2: Requirement & Test Specification - ATT-5818: ClusterNamingDialog Route Picker Tap Handling

**Ticket**: [ATT-5818](https://rainerblind.atlassian.net/browse/ATT-5818)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5818`  
**Requirement Mapping**: `REQ-UI-462` (refining `REQ-UI-462.2`, adding `REQ-UI-462.4`) | **Test Spec ID**: `TST-UI-462` (`TST-UI-462.5`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-07  

---

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-UI-462` (`REQ-UI-462.2`, `REQ-UI-462.4`) targeting route selection picker interaction in `ClusterNamingDialog` (`ImportBackupTabsScreen.kt`).
* `Historical Origin & Commit Trace`: Commit `2e35eb81` (`ATT-5674`, Sprint 2026-45.10).
* `Root Reason for Existing Formulation`: Commit `2e35eb81` added escape hatches and placed `Modifier.clickable { showSelectionDialog = true }` directly on `OutlinedTextField`, unaware that Jetpack Compose Material 3 `BasicTextField` internal pointer handlers consume tap events on `enabled = true` fields before bubbling to outer modifiers.
* `Preservation of Core Invariants`: Trailing icons (`Icons.Default.Clear` and `my_locations`) retain independent click handlers; `readOnly = true` state preserved; zero schema, string, or navigation changes; 100% backward compatibility for all dialog callers.

---

## 1. Requirement Specification (`REQ-UI-462.4`)

### 1.1 Problem Statement & Rationale
In `ClusterNamingDialog` (`ImportBackupTabsScreen.kt`), athletes configure route assignment for recurring workouts. Currently, only tapping the trailing route icon (`my_locations`) opens the candidate selection dialog (`WorkoutClusterSelectionDialog`). Tapping directly into the text field body of "Zugeordnete Lieblingsstrecke" (`cluster_naming__selected_route_label`) does nothing because the tap is consumed before bubbling to outer modifiers. Expected: tapping anywhere on the text field body reliably opens `WorkoutClusterSelectionDialog` via `MutableInteractionSource` (Rule 77 / Design Guidelines §5.19).

### 1.2 Functional & Architectural Requirements
1. **InteractionSource Tap Handling (`REQ-UI-462.4`)**: In `ClusterNamingDialog`, the read-only route assignment `OutlinedTextField` SHALL bind a `MutableInteractionSource` and observe `PressInteraction.Release` inside a `LaunchedEffect` to trigger `showSelectionDialog = true` on any tap across the field surface.
2. **Dead Modifier Cleanup**: Outer dead `Modifier.clickable` SHALL be removed from `OutlinedTextField`.
3. **Core Invariant Preservation**: `readOnly = true` state, trailing icon actions (clear route selection vs. open picker), and queue navigation SHALL remain strictly preserved.

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* **AC-1 (Field Body Tap)**: *Given* `ClusterNamingDialog` in `ImportBackupTabsScreen.kt`, *When* tapping anywhere on the text body of the read-only route assignment `OutlinedTextField`, *Then* `WorkoutClusterSelectionDialog` opens without requiring the athlete to hit the trailing `my_locations` icon.
* **AC-2 (Trailing Icon Actions)**: *Given* `ClusterNamingDialog`, *When* tapping trailing `my_locations` icon or clear `X` icon, *Then* existing picker and clear actions execute unchanged.
* **Invariants**: `readOnly = true` preserved (no keyboard/cursor); zero schema, database, or string changes; 100% localization parity.

---

## 2. Test Specification (`TST-UI-462.5`)

### Test Case 1: Route Picker Tap Handling Contract Test (`TST-UI-462.5`)
* **Scope**: UI Interaction Contract Test (`ClusterNamingDialogEscapeContractTest.kt`)
* **Action**: Assert `ClusterNamingDialog` source binds `MutableInteractionSource` observing `PressInteraction.Release` to trigger `showSelectionDialog = true`, and assert absence of outer dead `Modifier.clickable` on the route selection `OutlinedTextField`.

### Test Case 2: 9-Language Localization & String Resource Audit (`TST-UI-462.2`)
* **Scope**: Localization Parity Test (`TranslationParityTest.kt`, `ClusterNamingDialogLabelsVisualContractTest.kt`)
* **Action**: Verify `cluster_naming__selected_route_label`, `cluster_naming__create_new`, `clear`, and `select_route` exist across all 9 locales with zero inline emojis (`ATT-5400`).

### Test Case 3: Tier 1 Targeted Regression Suite (`TST-UI-462.4`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.localization.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Action**: Verify 100% test pass rate across all modified packages.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-462.5` | UI Contract | `ImportBackupTabsScreen.kt` (`ClusterNamingDialog`) | `REQ-UI-462.4` | Specified |
| `TST-UI-462.2` | Localization | `strings.xml` (9 locales), `TranslationParityTest` | `REQ-UI-462.2`, `REQ-UI-462.4` | Specified |
| `TST-UI-462.4` | Regression | Affected Package Test Suites | `REQ-UI-462.1`..`REQ-UI-462.4` | Specified |
