# Stage 2: Requirement & Test Specification - ATT-5666: Workout Cluster Merge

**Ticket**: [ATT-5666](https://rainerblind.atlassian.net/browse/ATT-5666)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*) | **Story Points**: 3 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5666`  
**Requirement Mapping**: `REQ-SET-089` (`REQ-SET-089.1`–`REQ-SET-089.5`) | **Test Spec ID**: `TST-SET-089`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-07  

---

## 1. Requirement Specification (`REQ-SET-089`)

### 1.1 Problem Statement & Rationale
When duplicate Workout Clusters (*Lieblingsstrecken*) are created, deleting a cluster drops historical workout associations (`clusterId = -1L`). Athletes need a directional Merge action in `ClusterItem`'s long-click context menu to rank candidates by similarity, confirm via dialog, reassign all workouts/routes from source `A` to target `B`, recompute `B`'s weighted centroid, and delete `A`.

### 1.2 Functional & Architectural Requirements
1. **Context Menu Extension (`REQ-SET-089.1`)**: In `ClusterItem` (`WorkoutClusterComponents.kt`), add *"Mit anderer Lieblingsstrecke zusammenführen…"* (`cluster_menu_merge`, `Icons.Default.CallMerge`) triggering `onMerge(cluster)`.
2. **Similarity-Ranked Target Selection (`REQ-SET-089.2`)**: Present candidate clusters (`id != A.id`) via `WorkoutClusterSelectionDialog`, scored against `A`'s spatial fingerprint (`start`, `end`, `apex`, `refDistance`, `bSportType`, `minAlt`, `maxAlt`) and sorted ascending (lowest score = highest similarity at index 0).
3. **Explicit Confirmation Dialog (`REQ-SET-089.3`)**: Display confirmation dialog with directional explanation (`cluster_merge_dialog_message`: `%1$s`, `%2$d`, `%3$s`), confirm action (`cluster_merge_action`), and cancel.
4. **Transactional Database Reassignment & Centroid Synthesis (`REQ-SET-089.4`)**: In `WorkoutClusterEngine.mergeClusters(context, sourceId, targetId)`:
   - Reassign all workouts (`CLUSTER_ID = A.id` $\rightarrow$ `B.id`) in `WorkoutSummaries.TABLE`.
   - Reassign all routes (`COLUMN_CLUSTER_ID = A.id` $\rightarrow$ `B.id`) in `RoutesDatabaseManager`.
   - Recompute `B`'s weighted centroid ($\text{coord}_{\text{new}} = \frac{\text{coord}_B \cdot N_B + \text{coord}_A \cdot N_A}{N_B + N_A}$), new `hitCount` ($N_B + N_A$), update bounding box, merge polylines, update probable sport.
   - Delete cluster `A` from `WorkoutClusterDatabaseManager` and refresh `WorkoutClusterRepository`.
5. **9-Language Parity & Zero Emojis (`REQ-SET-089.5`)**: All strings localized across all 9 locales with zero inline emojis (`ATT-5400`).

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* **AC-1 (Menu & Target Ranking)**: *Given* clusters `A` and `B`, *When* long-pressing `A` and choosing merge, *Then* selection dialog opens with `B` ranked at top.
* **AC-2 (Confirmation & Atomic Merge)**: *Given* candidate `B` selected, *When* confirming dialog, *Then* all workouts/routes of `A` are reassigned to `B`, `B`'s centroid and hit count reflect the union, and `A` is deleted.
* **Invariants**: Workouts outside `A` and `B` remain untouched; transactional SQLite integrity; zero hardcoded strings.

---

## 2. Test Specification (`TST-SET-089`)

### Test Case 1: Mathematical Centroid & Reassignment Unit Tests (`TST-SET-089.1`)
* **Scope**: Domain Engine & Database Unit Test (`WorkoutClusterMergeTest.kt`)
* **Action**: Create cluster `A` ($N_A=2$) and `B` ($N_B=4$); execute `mergeClusters(A.id, B.id)`; assert all workouts reassigned to `B`, centroid coordinates match weighted average, `hitCount == 6`, and cluster `A` deleted.

### Test Case 2: Candidate Ranking & ViewModel State Tests (`TST-SET-089.2`)
* **Scope**: ViewModel Unit Test (`WorkoutClustersViewModelTest.kt`)
* **Action**: Assert `getCandidatesForMerge(A)` excludes `A` and sorts candidates ascending by similarity score.

### Test Case 3: 9-Language Localization & Positional Specifier Audit (`TST-SET-089.3`)
* **Scope**: Localization Parity Test (`TranslationParityTest.kt`)
* **Action**: Assert `cluster_menu_merge`, `cluster_merge_dialog_title`, `cluster_merge_dialog_message` (`%1$s`, `%2$d`, `%3$s`), and `cluster_merge_action` exist across all 9 locales with zero emojis (`ATT-5400`).

### Test Case 4: Tier 1 Targeted Regression Suite (`TST-SET-089.4`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.localization.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Action**: Verify 100% test pass rate across all modified packages.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-089.1` | Domain/DB Unit | `WorkoutClusterEngine.mergeClusters` | `REQ-SET-089.4` | Specified |
| `TST-SET-089.2` | ViewModel Unit | `WorkoutClustersViewModel` | `REQ-SET-089.1`, `REQ-SET-089.2` | Specified |
| `TST-SET-089.3` | Localization | `strings.xml` (all 9 locales) | `REQ-SET-089.3`, `REQ-SET-089.5` | Specified |
| `TST-SET-089.4` | Regression | Affected Package Test Suites | `REQ-SET-089.1`..`REQ-SET-089.5` | Specified |
