# Stage 3: Implementation Plan - ATT-5824: WorkoutClustersScreen Merge Dialog Two-Step Transition State Independence

**Ticket**: [ATT-5824](https://rainerblind.atlassian.net/browse/ATT-5824)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.11`  
**Requirement Mapping**: `REQ-SET-089` (`REQ-SET-089.3`)  
**Test Mapping**: `TST-SET-089` (`TST-SET-089.3`)  
**Branch**: `feature/ATT-5824`  
**Author**: AI Agent 1 (`subagent_pro`)  
**Date**: 2026-11-07  

---

## 1. Problem Description & Background
In `WorkoutClustersScreen`, selecting a candidate target cluster from `WorkoutClusterSelectionDialog` closes the selection bottom sheet without opening the confirmation `AlertDialog` because `onDismiss()` resets `clusterToMerge` to `null` while `mergeTargetCluster` was set. The confirmation dialog only appears on a second context menu click. The system SHALL decouple selection sheet dismissal from the confirmation dialog state so the confirmation dialog opens immediately on the first attempt (Design Guidelines §5.19 / Rule 77).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-SET-089` (*Workout Cluster Merge via Similarity-Ranked Target Selection & Confirmation*, specifically `REQ-SET-089.3`)
* **Test Mapping**: `TST-SET-089` (*Workout Cluster Merge & Similarity-Ranked Target Selection Verification Suite*, specifically `TST-SET-089.3`)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Dialog Interference**: Non-merge consumers of `WorkoutClusterSelectionDialog` remain completely unaffected.
2. **Transactional Integrity**: `WorkoutClusterEngine.mergeClusters` transactional SQLite logic and centroid math remain 100% intact.
3. **Cancellation Safety**: Dismissing the selection bottom sheet without selecting a candidate never opens the confirmation dialog (`pendingMergePair == null`).
4. **UI Localization**: 100% 9-language translation parity preserved with zero inline emojis (`ATT-5400`).

---

## 4. Proposed Architectural Changes

### Component 1: `WorkoutClustersScreen.kt` (UI Layer)
* Replace `var mergeTargetCluster by remember { mutableStateOf<WorkoutCluster?>(null) }` with `var pendingMergePair by remember { mutableStateOf<Pair<WorkoutCluster, WorkoutCluster>?>(null) }`.
* In `WorkoutClusterSelectionDialog`:
  - `onSelect = { target -> pendingMergePair = source to target }`
  - `onDismiss = { clusterToMerge = null }`
* In `AlertDialog`: gate on `if (pendingMergePair != null)` with `val (source, target) = pendingMergePair!!`, clearing `pendingMergePair = null` on confirm, dismiss, and cancel.

### Component 2: `WorkoutClusterMergeDialogContractTest.kt` (Test Suite)
* Create contract test asserting state decoupling in `WorkoutClustersScreen.kt` and simulating two-step transition flow.

### UI Consistency (Rule 23)
* **Reference screen / component**: Existing `WorkoutClustersScreen` merge confirmation dialog (`ATT-5666`).
* **Reused components**: `WorkoutClusterSelectionDialog`, Material 3 `AlertDialog`, `TextButton`.
* **Theme tokens**: `MaterialTheme.colorScheme.surface`, `tonalElevation = 0.dp`.
* **New one-off styles & justification**: None. Retains existing styling 1:1.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Decouple Merge State in `WorkoutClustersScreen.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersScreen.kt`
* Changes: Introduce `pendingMergePair`, capture `source to target` in `onSelect`, gate `AlertDialog` on `pendingMergePair != null`.

### Step 2: Implement Contract Test
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterMergeDialogContractTest.kt`
* Changes: Test `WorkoutClustersScreen.kt` state contract and simulated callback sequence (`TST-SET-089.3`).

### Step 3: Targeted Unit Tests & Verification
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit tests in Stage 4, then verify living doc parity via `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Branch isolation (`feature/ATT-5824`) allows clean revert via `git restore .`.
