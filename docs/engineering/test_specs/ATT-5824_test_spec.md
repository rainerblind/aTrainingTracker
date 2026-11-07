# Stage 2: Requirement & Test Specification - ATT-5824: WorkoutClustersScreen Merge Dialog Two-Step Transition State Independence

**Ticket**: [ATT-5824](https://rainerblind.atlassian.net/browse/ATT-5824)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.11`  
**Requirement Mapping**: `REQ-SET-089` (*Workout Cluster Merge via Similarity-Ranked Target Selection & Confirmation*)  
**Test Spec ID**: `TST-SET-089`  
**Branch**: `feature/ATT-5824`  
**Author**: AI Agent 1 (`subagent_flash`)  
**Date**: 2026-11-07  

---

## 1. Requirement Specification (REQ-SET-089)

### 1.1 Problem Statement & Rationale
In `WorkoutClustersScreen`, selecting a candidate target cluster from `WorkoutClusterSelectionDialog` closes the selection bottom sheet without opening the confirmation `AlertDialog` because `onDismiss()` resets `clusterToMerge` to `null` while `mergeTargetCluster` was set. The confirmation dialog only appears on a second context menu click. The system SHALL decouple selection sheet dismissal from the confirmation dialog state so the confirmation dialog opens immediately on the first attempt (Design Guidelines §5.19 / Rule 77).

### 1.2 Functional & Architectural Requirements
1. *Two-Step Dialog State Decoupling (`REQ-SET-089.3`)*: In `WorkoutClustersScreen.kt`, candidate selection SHALL capture both source and target into an independent state `pendingMergePair: Pair<WorkoutCluster, WorkoutCluster>?`.
2. *First-Tap Confirmation Transition*: Tapping a candidate cluster row in `WorkoutClusterSelectionDialog` SHALL dismiss the selection sheet and immediately display the confirmation `AlertDialog` on the first attempt.
3. *Cancellation Independence*: Dismissing `WorkoutClusterSelectionDialog` without selecting a candidate SHALL clear `clusterToMerge` without opening the confirmation dialog (`pendingMergePair == null`).
4. *Confirmation Lifecycle*: Confirming or dismissing the confirmation dialog SHALL reset `pendingMergePair = null`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (First-Tap Transition)**: *Given* clusters `A` and `B` and merge picker open for `A`, *when* `B` is tapped, *then* picker closes and confirmation dialog opens immediately for `(A, B)`.
* **Criterion 2 (Selection Dismissal)**: *Given* merge picker open for `A`, *when* dismissed without selecting, *then* picker closes and no confirmation dialog is displayed.
* **Criterion 3 (Merge Confirmation & State Reset)**: *Given* confirmation dialog for `(A, B)`, *when* confirmed, *then* `viewModel.mergeClusters(A, B)` runs and `pendingMergePair` is cleared.

### 1.4 System Invariants
* Non-merge bottom sheet consumers are not affected.
* Transactional database merge and centroid reweighting in `WorkoutClusterEngine` remain 100% intact.
* Zero hardcoded strings; 9-language translation parity preserved.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-SET-089` (`REQ-SET-089.3`), targeting `WorkoutClustersScreen.kt` and `WorkoutClusterComponents.kt`.
* **Historical Origin & Commit Trace**: Sprint 2026-45.10 (`ATT-5666`, commit `76a403d3`).
* **Root Reason for Existing Formulation**: ATT-5666 introduced the merge flow with separate mutable state variables, assuming `onSelect` would transition states while `onDismiss` executed only on sheet dismissal. Since `WorkoutClusterSelectionDialog` calls `onSelect(cluster)` followed immediately by `onDismiss()`, `clusterToMerge` was cleared upon selection, suppressing the confirmation dialog until a second context menu tap.
* **Preservation of Core Invariants**: Decoupling selection sheet dismissal from confirmation dialog state via `pendingMergePair` ensures the confirmation dialog appears reliably on the first attempt; sheet dismissal without selection (`onDismiss` without `onSelect`) cleanly closes without triggering confirmation; backend transactional merge, centroid calculations, and 9-language localization remain 100% intact.

---

## 2. Test Specification (TST-SET-089)

### Test Case 1: Two-Step Dialog Transition Contract (`[TST-SET-089.3]`)
* **Scope**: Unit / Contract Test (`WorkoutClusterMergeDialogContractTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterMergeDialogContractTest.kt`
* **Preconditions**: Simulated selection sheet callback triggers with source cluster `A` and target `B`.
* **Action**: Trigger `onSelect(B)` followed immediately by `onDismiss()`.
* **Expected Result**: `pendingMergePair` captures `(A, B)`, selection dialog is closed, and confirmation dialog condition evaluates to true.

### Test Case 2: 9-Language Localization & Specifier Audit (`[TST-SET-089.4]`)
* **Scope**: Localization Parity Test (`TranslationParityTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Expected Result**: All 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) maintain 100% parity with zero emojis.

### Test Case 3: Tier 1 Targeted Regression Suite (`[TST-SET-089.5]`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Expected Result**: 100% pass rate across clusters, database, and build tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-SET-089.1]` | Unit | `WorkoutClusterEngine.mergeClusters` | `REQ-SET-089.4` | Verified |
| `[TST-SET-089.2]` | Unit | `WorkoutClustersViewModel.getCandidateClustersForCluster` | `REQ-SET-089.2` | Verified |
| `[TST-SET-089.3]` | Contract | `WorkoutClustersScreen` merge state transition | `REQ-SET-089.3` | Specified |
| `[TST-SET-089.4]` | Localization | `TranslationParityTest` | `REQ-SET-089.5` | Specified |
| `[TST-SET-089.5]` | Regression | Clusters & Database test suite | `REQ-PRO-001` | Specified |
