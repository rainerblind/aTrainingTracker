# Stage 3: Implementation Plan - ATT-5674: ClusterNamingDialog Return to Create New

**Ticket**: [ATT-5674](https://rainerblind.atlassian.net/browse/ATT-5674)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5674`  
**Requirement Mapping**: `REQ-UI-462` (`REQ-UI-462.1`–`REQ-UI-462.3`) | **Test Mapping**: `TST-UI-462` (`TST-UI-462.1`–`TST-UI-462.4`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Description & Background
In `ClusterNamingDialog` (`ImportBackupTabsScreen.kt`), selecting an existing favorite route locks the dialog into route-assignment mode: `selectedCluster` becomes non-null, hiding the new route name field (`name`). Reopening `WorkoutClusterSelectionDialog` only displays candidate clusters with no option to create a new route, and the route `OutlinedTextField` lacks a clear/reset action. The athlete is trapped with no way to return to `selectedCluster = null` without skipping cluster resolution entirely.

---

## 2. Invariants & Preserved Behavior
1. **Existing Caller Invariance (`REQ-UI-462.3`)**: `WorkoutClusterSelectionDialog` callers that do not supply `onCreateNew` (`WorkoutClustersScreen.kt`, `WorkoutClusterHeatmapScreen.kt`) maintain identical layout and behavior; the create-new card is strictly hidden when `onCreateNew == null`.
2. **State & Input Retention (`REQ-UI-462.3`)**: Tapping clear or create-new resets `selectedCluster = null`, restoring the new favorite route input section while strictly preserving the previously typed or pre-filled `name` string.
3. **9-Language Parity & Emoji Prohibition (`ATT-5400`)**: Reuses existing verified strings (`R.string.cluster_naming__create_new`, `R.string.clear`, `R.string.select_route`); zero decorative emojis.
4. **Architectural Code Lock (Rule 4)**: No production code changes until Gate 3 audit passes.

---

## 3. UI Consistency & Architectural Strategy
* **Reference Pattern**: `EditWorkoutClusterDialog` (L813–840 in `WorkoutClusterComponents.kt`) for the `+ Neue Strecke erstellen...` card using `Card`, `MaterialTheme.colorScheme.surfaceVariant`, `Icons.Default.Add`, and `MaterialTheme.colorScheme.primary`.
* **Theme Tokens**: Standard M3 tokens (`surfaceVariant`, `primary`, `onSurfaceVariant`); spacing scale 8.dp/12.dp.
* **Component Reusability**: Single optional lambda `onCreateNew: (() -> Unit)? = null` preserves binary compatibility and eliminates duplicate dialog composables.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Selection Dialog Create-New Extension (`REQ-UI-462.1`, `TST-UI-462.1`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt`
* **Changes**:
  - Add optional parameter `onCreateNew: (() -> Unit)? = null` to `WorkoutClusterSelectionDialog`.
  - In `WorkoutClusterSelectionDialog` body (above `candidates` list), when `onCreateNew != null`, render a selectable `Card` with `Icons.Default.Add` and `stringResource(R.string.cluster_naming__create_new)` styled identically to `EditWorkoutClusterDialog`.
  - On click, execute `onCreateNew()` and `onDismiss()`.

### Step 2: Route Field Clear Button & Dialog Wiring (`REQ-UI-462.2`, `REQ-UI-462.3`, `TST-UI-462.1`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt`
* **Changes**:
  - In `ClusterNamingDialog`, pass `onCreateNew = { selectedCluster = null; showSelectionDialog = false }` to `WorkoutClusterSelectionDialog`.
  - In `OutlinedTextField` for *„Gewählte Strecke“*, wrap `trailingIcon` in a `Row` displaying an `IconButton` with `Icons.Default.Clear` (content description `stringResource(R.string.clear)`) when `selectedCluster != null` alongside the route selection icon button.

### Step 3: Contract & Architecture Unit Tests (`TST-UI-462.1`–`TST-UI-462.3`)
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterNamingDialogEscapeContractTest.kt`: Author contract test asserting `WorkoutClusterSelectionDialog` exposes `onCreateNew`, `ClusterNamingDialog` provides clear icon binding and `onCreateNew` wiring, and strings exist.
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/ModalBottomSheetDialogsIntegrityTest.kt`: Verify reflection assertions remain green.

---

## 5. Verification Commands (`TST-UI-462.4`)
* `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.ui.components.core.ModalBottomSheetDialogsIntegrityTest"`
* `python3 tools/verify_requirement_governance.py`
