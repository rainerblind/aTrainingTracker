# Stage 1 Analysis: ATT-5824 - WorkoutClustersScreen Merge Dialog Two-Step Transition State Independence

**Ticket**: [ATT-5824](https://rainerblind.atlassian.net/browse/ATT-5824)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.11`  
**Branch**: `feature/ATT-5824`  
**Author**: AI Agent 1 (`subagent_pro`)  
**Date**: 2026-11-07  

---

## 1. Problem Statement & Motivation
When merging favorite routes (*Lieblingsstrecken*) via the long-click context menu in `WorkoutClustersScreen`, selecting a candidate target cluster closes `WorkoutClusterSelectionDialog` without displaying the confirmation `AlertDialog`. The confirmation dialog only appears unexpectedly when opening the context menu a second time. Expected: selecting a candidate immediately opens the confirmation dialog on the first attempt.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Failure Mechanism**: In `WorkoutClustersScreen.kt` (L269–351), merge state uses `var clusterToMerge` and `var mergeTargetCluster`. Selection sheet displays when `clusterToMerge != null && mergeTargetCluster == null`. In `WorkoutClusterComponents.kt` (L758–761), candidate clicks execute `onSelect(cluster)` immediately followed by `onDismiss()`. `onSelect` sets `mergeTargetCluster = target`, but `onDismiss` resets `clusterToMerge = null`. Consequently, the confirmation condition `(clusterToMerge != null && mergeTargetCluster != null)` evaluates to false. On the second context menu tap, `clusterToMerge` is re-populated while stale `mergeTargetCluster` remains non-null, triggering the confirmation dialog prematurely with stale state.
* **Independent Peer Review & Verification**:
  Verified against live code: `WorkoutClusterSelectionDialog` callers across `ImportBackupTabsScreen.kt` and `WorkoutClusterHeatmapScreen.kt` rely on `onDismiss()` self-closure. The bug is purely a caller-side state coupling flaw in `WorkoutClustersScreen.kt`, violating Rule 77 / Section 5.19 (`Two-Step Dialog Transition State Independence`).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Decouple selection and confirmation dialog states in `WorkoutClustersScreen.kt` using `pendingMergePair: Pair<WorkoutCluster, WorkoutCluster>?`.
  2. Add contract unit test verifying two-step dialog state independence and non-interference on dismissal.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Altering `WorkoutClusterSelectionDialog` or candidate similarity scoring (`WorkoutClusterEngine`).
  - Modifying other single-step dialog consumers.
* **Complexity Rating**: **1 SP (Trivial)**. Localized Compose UI state refactor within a single screen.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-SET-089.3` (*Explicit Merge Confirmation Dialog*), adheres to Rule 77 (`docs/design_guidelines.md` §5.19).
* **Historical Origin & Commit Trace**: Commit `76a403d3` (ATT-5666, Sprint 2026-45.10).
* **Root Reason for Existing Formulation**: ATT-5666 introduced the merge flow using two separate variables, incorrectly assuming `onSelect` would transition states while `onDismiss` would execute only on explicit sheet dismissal.
* **Preservation of Core Invariants**: Reusable bottom sheet dismissal semantics preserved; transactional backend cluster merge intact; 100% 9-language string parity preserved.

---

## 5. Architectural Strategy & High-Level Solution
* In `WorkoutClustersScreen.kt`, replace `mergeTargetCluster: WorkoutCluster?` with `pendingMergePair: Pair<WorkoutCluster, WorkoutCluster>?`.
* In `WorkoutClusterSelectionDialog`:
  - `onSelect = { target -> clusterToMerge?.let { pendingMergePair = it to target } }`
  - `onDismiss = { clusterToMerge = null }`
* Bind the confirmation `AlertDialog` strictly to `pendingMergePair != null`:
  - Reset `pendingMergePair = null` on confirm, dismiss, or cancel.
  - On confirm: `viewModel.mergeClusters(source, target)`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Selection sheet cancellation (tap outside/back) never triggers confirmation dialog (`pendingMergePair == null`).
  2. Transactional SQLite merge and centroid re-weighting logic in `WorkoutClusterEngine` remain 100% intact.
  3. No regression in existing candidate scoring or cluster lists.
* **Risk Rating**: **LOW** (Isolated Compose state decoupling in a single screen; verified by targeted contract test).
