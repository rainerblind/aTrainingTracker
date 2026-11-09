# Stage 2: Requirement & Test Specification - ATT-5900: Split Workout Cluster Dialog

**Ticket**: [ATT-5900](https://atrainingtracker.atlassian.net/browse/ATT-5900)  
**Parent Epic**: [ATT-176](https://atrainingtracker.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.13` | **Branch**: `feature/ATT-5900`  
**Requirement Mapping**: `REQ-SET-096` (*Workout Cluster Split via Polyline Sub-Corridor Detection*)  
**Test Spec ID**: `TST-SET-096`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-SET-096)

### 1.1 Problem Statement & Rationale
Large historical clusters aggregate distinct route variants sharing endpoints and total distance. Blending these variants distorts performance metrics (`Bestzeit`, `Ø Zeit`, `Ø Geschwindigkeit`) and trend analysis. The system SHALL provide an interactive "Split Cluster" (`Cluster aufteilen`) action with polyline sub-corridor clustering, color-coded preview map, sensitivity slider (`250 m – 1500 m`), editable variant names, workout renaming, and atomic database split with centroid and statistics reconciliation.

### 1.2 Functional & Architectural Requirements
1. *Split Action Entry (`REQ-SET-096.1`)*: When `hitCount >= 2` in `WorkoutClusterHeatmapScreen`, header provides `Split Cluster` button (`R.string.cluster_split_action`, `Icons.Default.CallSplit`).
2. *Polyline Sub-Corridor Detection (`REQ-SET-096.2`)*: Samples polylines at fractional stations $\{0.15, 0.30, 0.50, 0.70, 0.85\}$; partitions via greedy medoid clustering using max station geodesic deviation against corridor threshold $W \in [250\text{ m}, 1500\text{ m}]$ (default $500\text{ m}$). Largest group remains Group 1 (Primary); remaining groups form secondary variants (Groups $2..N$).
3. *Interactive Preview Sheet (`REQ-SET-096.3`)*: Tapping split opens `WorkoutClusterSplitSheet` (`AppModalBottomSheet`). Renders detected sub-corridors in distinct colors on the map; M3 `Slider` modulates $W$ in $50\text{ m}$ steps. If 1 group detected, displays `R.string.cluster_split_no_variants` and disables confirm button.
4. *Variant Customization (`REQ-SET-096.4`)*: Lists Group 1 as primary route; secondary variants include checkboxes (default checked), color pills, workout count, average distance, and editable name fields. Checkbox *"Workouts passend zur neuen Lieblingsstrecke umbenennen"* (`R.string.cluster_split_rename_workouts`, default `true`) toggles member workout renaming.
5. *Atomic Database Split (`REQ-SET-096.5`)*: On confirmation, executes inside atomic SQLite transaction: inserts new clusters, reassigns workout IDs in `WorkoutSummaries` (and renames if selected), recalculates primary centroid/bounds/stats, and refreshes `WorkoutClusterRepository` state flows.
6. *9-Language Parity & Zero Emojis (`REQ-SET-096.6`)*: All new user-facing strings maintain 100% parity across all 9 locales with zero inline emojis (`ATT-5400`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Detection & Slider Modulation)**: *Given* a cluster with `hitCount >= 2`, *when* athlete adjusts corridor slider in `WorkoutClusterSplitSheet`, *then* sub-corridors and map colors update dynamically.
* **Criterion 2 (Customization & Atomic Split)**: *Given* secondary variant groups, *when* athlete edits a variant name and confirms split, *then* new `WorkoutCluster` records are created, workouts are reassigned, centroids/stats update, and no phantom tracks remain.
* **Criterion 3 (Localization Parity)**: *Given* all 9 locale files, *when* `TranslationParityTest` runs, *then* all split strings match format specifiers and have zero inline emojis.

### 1.4 System Invariants
* Unselected workouts and foreign clusters remain unaltered; SQLite transactional atomicity guarantees zero orphaned records; authoritative hit counts derive from `WorkoutSummaries`.

---

## 2. Test Specification (TST-SET-096)

### Test Case 1: Sub-Corridor Detection Algorithm (`[TST-SET-096.1]`)
* **Scope**: Unit Test | **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterCorridorDetectorTest.kt`
* **Preconditions**: Synthetic polylines with known parallel/diverging geometries.
* **Action**: Execute station sampling at fractional distances $\{0.15, 0.30, 0.50, 0.70, 0.85\}$; evaluate corridor distance metric across varying thresholds $W \in [250\text{ m}, 1500\text{ m}]$.
* **Expected Result**: Tracks diverging by $> 500\text{ m}$ split into separate groups at $W = 500\text{ m}$; identical tracks yield $0.0\text{ m}$ distance and group together.

### Test Case 2: Transactional Split & Centroid Reconciliation (`[TST-SET-096.2]`)
* **Scope**: Unit / DB Integration Test | **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterSplitTest.kt`
* **Preconditions**: Cluster with 10 workouts (6 on Corridor A, 4 on Corridor B).
* **Action**: Execute `WorkoutClusterEngine.splitCluster(...)` splitting Corridor B into a new cluster with renaming enabled.
* **Expected Result**: New cluster inserted with `hitCount = 4`, centroid matching Corridor B; original cluster updated with `hitCount = 6`, centroid matching Corridor A; workouts in `WorkoutSummaries` updated with new `clusterId` and names.

### Test Case 3: UI & Interaction Contract (`[TST-SET-096.3]`)
* **Scope**: UI Contract Test | **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterSplitDialogContractTest.kt`
* **Action**: Verify `CallSplit` visibility (`hitCount >= 2`), sheet launch, slider modulation, and confirm button enablement.

### Test Case 4: 9-Language Localization & Positional Specifier Audit (`[TST-SET-096.4]`)
* **Scope**: Localization Parity Test | **Target File**: `TranslationParityTest.kt`
* **Expected Result**: 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT with matching format specifiers and zero inline emojis.

### Test Case 5: Tier 1 Targeted Regression Suite (`[TST-SET-096.5]`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.localization.*"`

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-SET-096.1]` | Unit | `WorkoutClusterCorridorDetector.detectCorridors` | `REQ-SET-096.2` | Specified |
| `[TST-SET-096.2]` | Unit/DB | `WorkoutClusterEngine.splitCluster` | `REQ-SET-096.5` | Specified |
| `[TST-SET-096.3]` | UI Contract | `WorkoutClusterHeatmapScreen` / `WorkoutClusterSplitSheet` | `REQ-SET-096.1`, `REQ-SET-096.3`, `REQ-SET-096.4` | Specified |
| `[TST-SET-096.4]` | Localization | `TranslationParityTest` | `REQ-SET-096.6`, `REQ-UI-106` | Specified |
| `[TST-SET-096.5]` | Regression (Tier 1) | Targeted Database/Cluster/Localization Suites | `REQ-PRO-001` | Specified |
