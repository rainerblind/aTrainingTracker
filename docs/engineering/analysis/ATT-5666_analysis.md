# Stage 1: Problem Domain & Root Cause Analysis - ATT-5666

**Ticket**: [ATT-5666](https://rainerblind.atlassian.net/browse/ATT-5666)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176): Auto Name / Route Clusters | **Story Points**: 3 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5666`  
**Requirement Mapping**: `REQ-SET-089` (*Workout Cluster Merge*), refining `REQ-SET-033`, `REQ-UI-149`  
**Test Mapping**: `TST-SET-056` (*Cluster Merge Suite*)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Statement & Motivation
When duplicate Workout Clusters (*Lieblingsstrecken*) are created for the same physical route (e.g., during bulk import or slight GPS deviation), the list currently only offers a *Delete* action in `ClusterItem`'s context menu. Deletion unclusters workouts (`clusterId = -1L`) rather than associating them with the matching cluster. Athletes require a dedicated **Merge** action in the long-click menu of cluster `A` that presents similarity-ranked candidate clusters (`B`), confirms the operation via an explicit dialog, reassigns all workouts from `A` to `B`, recalculates `B`'s centroid and statistics, and removes `A`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification (4-Augen-Prinzip)
* **Forensic RCA**:
  - `ClusterItem` (`WorkoutClusterComponents.kt`, L400–419): Context menu only contains `DropdownMenuItem` for `R.string.delete`.
  - `WorkoutClustersViewModel.kt`: Lacks `getCandidateClustersForCluster(source: WorkoutCluster)` and `mergeClusters(source: WorkoutCluster, target: WorkoutCluster)`.
  - `WorkoutClusterEngine.kt`: Currently only supports moving single workouts (`moveWorkoutToCluster`), lacking atomic multi-workout reassignment and cluster-to-cluster weighted centroid integration.
* **Independent Code & Call-Site Audit**:
  - `WorkoutClusterEngine.scoreClusters`: Can score source `A`'s spatial anchors against all clusters where `id != A.id`, returning candidate pairs sorted ascending by similarity score (lowest score = best match).
  - `WorkoutSummariesDatabaseManager.java`: `clearClusterLink` clears cluster associations; an atomic batch reassignment query (`UPDATE ... SET clusterId = ? WHERE clusterId = ?`) is required.
  - `RoutesDatabaseManager.kt`: Any route linked to cluster `A` must also be reassigned to `B` (`COLUMN_CLUSTER_ID = targetId`).
  - `WorkoutClusterSelectionDialog`: Fully supports rendering candidate clusters with scores via `AppModalBottomSheet`.

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit (Rule 68)
* **In-Scope Goals**:
  1. Add *"Mit anderer Lieblingsstrecke zusammenführen…"* (`R.string.cluster_menu_merge`) to `ClusterItem` context menu.
  2. Implement similarity ranking of target candidate clusters via `WorkoutClusterEngine.scoreClusters(...)`.
  3. Show explicit confirmation dialog explaining directional merge and deletion of `A` with `%1$s`, `%2$d`, `%3$s`.
  4. Implement transactional `WorkoutClusterEngine.mergeClusters(context, sourceId, targetId)`: reassign workouts & routes, recalculate `B`'s weighted centroid, bounding box, hit count, preview paths, and probable sport, delete `A`, and refresh `WorkoutClusterRepository`.
  5. 9-language localization parity across all new strings.
* **Out-of-Scope Non-Goals**:
  - Modifying the underlying spatial similarity scoring algorithm (`calculateSimilarity`).
  - Automated clustering triggers in background tracking service (`TrackerService`).
* **Complexity Rating**: **3 SP (Complex)**. Cross-layer feature touching Compose UI, ViewModel, Repository, Engine mathematics, and SQLite updates. Within single-ticket capacity ($\le 3$ SP).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-SET-089` (net-new), refining `REQ-SET-033` (*Workout Cluster Deletion*) and `REQ-UI-149` (*Selection Bottom Sheets*).
* **Historical Origin & Commit Trace**: `ATT-176` / `SCRUM-214` (cluster reassignment), `ATT-318` (hit count reconciliation), `ATT-5674` (selection dialog escape).
* **Root Reason for Existing Formulation**: Manual cluster management initially prioritized single-workout moves and deletion. Cluster merging fills the architectural gap for resolving duplicate clusters without losing historical categorization.
* **Preservation of Core Invariants**: Spatial centroid math must weigh by respective hit counts; unassociated workouts must remain untouched; 100% 9-language translation parity; zero decorative emojis (`ATT-5400`).

---

## 5. Architectural Strategy & High-Level Solution
* **UI Layer**: Long-click `ClusterItem` $\rightarrow$ `onMergeRequest(cluster)` $\rightarrow$ `WorkoutClusterSelectionDialog` (ranked candidates) $\rightarrow$ Confirmation `AlertDialog` $\rightarrow$ `viewModel.mergeClusters(source, target)`.
* **Domain / Engine Layer**: `WorkoutClusterEngine.mergeClusters(sourceId, targetId)` calculates combined weighted centroid:
  $$\text{coord}_{\text{new}} = \frac{\text{coord}_B \cdot N_B + \text{coord}_A \cdot N_A}{N_B + N_A}$$
  updates bounding box, merges preview paths, updates sport type, deletes `A`, and invokes `repository.refreshClusters()`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Workouts outside cluster `A` and `B` MUST NOT be affected; SQLite transactional integrity; no duplicate routes; 9-language parity verified by `TranslationParityTest`.
* **Risk Rating**: **MEDIUM** (Multi-row SQLite update and centroid mathematics, fully protected by explicit confirmation dialog and transactional execution).
