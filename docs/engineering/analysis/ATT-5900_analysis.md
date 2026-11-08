# Stage 1 Analysis: ATT-5900 - WorkoutClusters: Interactive 'Split Cluster' Dialog with Polyline Sub-Corridor Detection

**Ticket**: [ATT-5900](https://atrainingtracker.atlassian.net/browse/ATT-5900)  
**Parent Epic**: [ATT-176](https://atrainingtracker.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.13` | **Branch**: `feature/ATT-5900`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
* **Route Variant Mixing**: Large historical clusters (e.g. `Bike2Home (kurz)` with 350 recordings between Sindelfingen and Schönaich) aggregate 2–4 distinct route corridors (e.g. eastern forest route vs. central city route through Böblingen/Tannenberg) due to matching start, end, and reference distance fingerprints.
* **Metric & Analytics Distortion**: Blending distinct route variants into a single cluster corrupts performance metrics (`Bestzeit`, `Ø Zeit`, `Ø Geschwindigkeit`) and introduces multi-modal noise into the `Leistungsverlauf` trend chart.
* **Manual Reassignment Impracticality**: While individual workouts can be reassigned via `SwapHoriz`, manually evaluating and moving hundreds of sessions is unfeasible. Because decoded polyline paths (`heatmapPaths`) are already loaded in `WorkoutClustersViewModel`, automated geometric sub-corridor separation can be performed directly.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**: The clustering engine (`WorkoutClusterEngine.kt`) evaluates a 4-point macro spatial fingerprint (Start, End, Max Displacement Apex, and Scalar Distance). Workouts sharing endpoints and length pass the similarity threshold even when intermediate trajectories diverge by hundreds of meters.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Inspected `bike2home_kurz_screen.png` via `read_file`: verified distinct parallel corridors through and around Böblingen/Tannenberg grouped under `Bike2Home (kurz)`.
  * Audited `WorkoutClustersViewModel.kt` (lines 341–344): confirmed `heatmapPaths: List<List<LatLng>>` is already pre-decoded in memory during `selectCluster()`, allowing instantaneous sub-clustering without additional disk I/O.
  * Verified fractional distance station sampling (e.g. 15%, 30%, 50%, 70%, 85%) with pairwise max-deviation corridor metric runs in $< 50\text{ ms}$ for 350 polylines on `Dispatchers.Default`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**:
  * Top action bar `CallSplit` (`Icons.Default.CallSplit`) action in `WorkoutClusterHeatmapScreen` for clusters with `hitCount >= 2`.
  * Interactive `WorkoutClusterSplitSheet` (`AppModalBottomSheet`) featuring:
    * Color-coded map preview rendering detected sub-corridors in distinct colors.
    * Corridor sensitivity slider (`250 m – 1500 m`, default `500 m`) updating groups and map colors in real time.
    * Variant list with checkboxes, recording count, average distance, and editable cluster names (`var 2`, `var 3`).
    * Checkbox option to update member workout names to match the new cluster name.
  * Atomic transactional database split in `WorkoutClusterEngine`: inserting new clusters, reassigning workouts in `WorkoutSummaries`, and recomputing centroids, bounds, and stats for all affected clusters.
  * 100% 9-language localization parity with zero inline emojis (`ATT-5400`).
* **Out-of-Scope**: Altering global macro-clustering thresholds in `ClusterTuningScreen`, modifying GPX exports, or altering live tracking auto-naming logic.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Net-new requirement `REQ-SET-096` (*Workout Cluster Split via Polyline Sub-Corridor Detection & Interactive Preview*), complementary to `REQ-SET-089` (*Workout Cluster Merge*).
* **Historical Origin & Commit Trace**: Sprint `2026-45.10` `ATT-5666` / `ATT-5824` (`REQ-SET-089` Cluster Merge), `ATT-359` / `ATT-388` (Heatmap Polylines in `WorkoutClustersViewModel`).
* **Root Reason for Existing Formulation**: Prior cluster management prioritized macro grouping and merging duplicate clusters; dividing aggregated variants required single-session manual reassignment.
* **Preservation of Core Invariants**: Transactional SQLite integrity; workouts not selected for split remain assigned to the primary cluster; cluster hit counts strictly reconciled.

---

## 5. Architectural Strategy, Solution & Side-Effect Audit
* **Sub-Corridor Detection**: Sample polylines at fractional stations ($s_k \in \{0.15, 0.30, 0.50, 0.70, 0.85\}$); group via greedy medoid clustering using max station deviation against corridor threshold $W$. Group 1 remains in the original cluster; groups $2..k$ are selectable split variants.
* **UI Presentation**: Add `CallSplit` button to `WorkoutClusterSummaryHeader`. Tapping opens `WorkoutClusterSplitSheet`. The underlying map renders variant tracks with distinct palette colors (`TTColor` / high-contrast palette).
* **Database & Engine Split**: In `WorkoutClusterEngine.splitCluster(...)`:
  * Atomically insert new `WorkoutCluster` records with weighted centroids and bounds derived from each variant group's member workouts.
  * Reassign variant workout IDs via `WorkoutSummariesDatabaseManager.reassignWorkoutsToCluster(...)` (and update names if requested).
  * Recalculate remaining centroid, bounds, and stats for the original cluster, and call `repository.refreshClusters()`.
* **Side-Effect Audit (Rule 68)**:
  * Audited downstream consumers (`WorkoutClustersViewModel`, `MapFragmentWithTrackViewModel`): `refreshClusters()` re-hydrates `_allClusters` and `_clusterStats` symmetrically.
  * Heatmap state and member markers for the active cluster in `WorkoutClustersViewModel` are refreshed upon split completion so no phantom tracks remain.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: SQLite transactional atomicity; single-source-of-truth hit count reconciliation; 9-language localization parity.
* **Risk Rating**: **MEDIUM** — Involves multi-record SQLite updates across tables and centroid recalculation, mitigated by transactional encapsulation, existing engine patterns, and targeted unit tests.
