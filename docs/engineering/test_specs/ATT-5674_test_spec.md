# Stage 2: Requirement & Test Specification - ATT-5674: ClusterNamingDialog Return to Create New

**Ticket**: [ATT-5674](https://rainerblind.atlassian.net/browse/ATT-5674)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5674`  
**Requirement Mapping**: `REQ-UI-462` (refining `REQ-UI-123`, `REQ-UI-149`) | **Test Spec ID**: `TST-UI-462`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-07  

---

## 1. Requirement Specification (`REQ-UI-462`)

### 1.1 Problem Statement & Rationale
In `ClusterNamingDialog` (`ImportBackupTabsScreen.kt`), selecting an existing favorite route sets `selectedCluster` non-null, hiding the new route name input and leaving the athlete deadlocked: `WorkoutClusterSelectionDialog` lists only candidate routes with no "create new" action, and the selection field lacks a clear button (`Icons.Default.Clear`). The system SHALL provide bi-directional escape back to `selectedCluster = null` both from within the selection dialog and directly on the selection field.

### 1.2 Functional & Architectural Requirements
1. **Selection Dialog Create-New Action (`REQ-UI-462.1`)**: `WorkoutClusterSelectionDialog` SHALL declare optional parameter `onCreateNew: (() -> Unit)? = null`. When non-null, a selectable card with `Icons.Default.Add` and `R.string.cluster_naming__create_new` SHALL render above suggestions. Tapping it invokes `onCreateNew()`, dismisses the sheet, and reverts `selectedCluster = null`.
2. **Direct Clear Button (`REQ-UI-462.2`)**: In `ClusterNamingDialog`, when `selectedCluster != null`, the route `OutlinedTextField` trailing icon SHALL display an `IconButton` with `Icons.Default.Clear` (content description `R.string.clear`), resetting `selectedCluster = null` in 1 tap.
3. **State Restoration & Invariants (`REQ-UI-462.3`)**: Resetting `selectedCluster = null` SHALL immediately restore the new route naming section (`cluster_naming__new_name_prompt` and `name` input) while preserving previously typed/stripped route names. Existing callers without `onCreateNew` remain visually and behaviorally identical.

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* **AC-1 (Direct Clear Action)**: *Given* `ClusterNamingDialog` with an existing route selected, *When* tapping clear (`X`), *Then* `selectedCluster` becomes `null`, the field displays `cluster_naming__create_new`, and the new route name input reappears.
* **AC-2 (Dialog Create-New Action)**: *Given* `WorkoutClusterSelectionDialog` opened from `ClusterNamingDialog`, *When* tapping `+ Neu erstellen...`, *Then* `onCreateNew()` executes, the sheet dismisses, and `selectedCluster` becomes `null`.
* **AC-3 (Caller Invariance)**: *Given* `WorkoutClustersScreen` or `WorkoutClusterHeatmapScreen`, *When* opening `WorkoutClusterSelectionDialog` with `onCreateNew == null`, *Then* only candidate routes are displayed.
* **Invariants**: 100% 9-language localization parity (`TranslationParityTest`); backwards compatibility for all existing callers of `WorkoutClusterSelectionDialog`.

---

## 2. Test Specification (`TST-UI-462`)

### Test Case 1: Escape & Clear UI Binding Contract Test (`TST-UI-462.1`)
* **Scope**: UI Binding Contract Test (`ClusterNamingDialogEscapeContractTest.kt`)
* **Action**: Assert `WorkoutClusterSelectionDialog` accepts `onCreateNew`, conditionally renders `Icons.Default.Add` + `cluster_naming__create_new`, `ClusterNamingDialog` wires `onCreateNew = { selectedCluster = null }`, and renders `Icons.Default.Clear` + `R.string.clear`.

### Test Case 2: 9-Language Localization & String Resource Audit (`TST-UI-462.2`)
* **Scope**: Localization Parity Test (`TranslationParityTest.kt`, `ClusterNamingDialogLabelsVisualContractTest.kt`)
* **Action**: Verify `cluster_naming__create_new`, `clear`, and `select_route` exist across all 9 locales with zero inline emojis (`ATT-5400`).

### Test Case 3: Composable Reflection Integrity Test (`TST-UI-462.3`)
* **Scope**: Architecture Integrity Test (`ModalBottomSheetDialogsIntegrityTest.kt`)
* **Action**: Verify `WorkoutClusterSelectionDialog` composable function remains public and callable with default parameter overloads.

### Test Case 4: Tier 1 Targeted Regression Suite (`TST-UI-462.4`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.localization.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Action**: Verify 100% test pass rate across all modified packages.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-462.1` | UI Binding Contract | `ImportBackupTabsScreen.kt`, `WorkoutClusterComponents.kt` | `REQ-UI-462.1`, `REQ-UI-462.2`, `REQ-UI-462.3` | Specified |
| `TST-UI-462.2` | Localization | `strings.xml` (all 9 locales), `TranslationParityTest` | `REQ-UI-462.1`, `REQ-UI-462.2` | Specified |
| `TST-UI-462.3` | Contract | `ModalBottomSheetDialogsIntegrityTest` | `REQ-UI-462.1`, `REQ-UI-462.3` | Specified |
| `TST-UI-462.4` | Regression | Affected Package Test Suites | `REQ-UI-462.1`..`REQ-UI-462.3` | Specified |
