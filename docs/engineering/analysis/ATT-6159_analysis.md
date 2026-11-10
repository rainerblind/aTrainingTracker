# Stage 1 Analysis: ATT-6159 - Stale-Copy Overwrite & Order in moveWorkoutToCluster

**Ticket**: [ATT-6159](https://rainerblind.atlassian.net/browse/ATT-6159)  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6159`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
In `WorkoutClusterEngine.kt:1166-1208` (`moveWorkoutToCluster`), two sequential flaws corrupt cluster records when an athlete moves a workout:
1. **Stale-Copy Centroid Overwrite**: `dbManager.updateCluster(oldCluster.copy(...))` calculates and updates new centroids. Directly following, `dbManager.updateCluster(oldCluster.copy(probableSportId = ...))` uses the stale, pre-centroid-update `oldCluster` snapshot, reverting and overwriting the updated centroids. The same bug affects `newCluster`.
2. **Premature Sport Frequency Query**: `summariesManager.getMostFrequentSportIdForCluster` is invoked *before* `assignClusterToWorkout(context, workoutId, newClusterId)` updates `WorkoutSummaries.CLUSTER_ID`. Thus, sport counts reflect the pre-move distribution rather than the post-move state.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  * `WorkoutClusterEngine.kt:1176-1188` & `1192-1204`: Confirmed. Second `.copy()` call passes the stale initial snapshot, overwriting `startLat`, `startLng`, `endLat`, `endLng`, `maxDispLat`, `maxDispLng`, and `refDistance`.
  * Database Sequence: Confirmed. `getMostFrequentSportIdForCluster` (L1185, L1201) executes before `assignClusterToWorkout` (L1206). `WorkoutSummariesDatabaseManager.java:250-262` confirms it filters `WHERE CLUSTER_ID = ?`, which still contains the old cluster ID until `assignClusterToWorkout` updates SQLite.
  * `hitCount` Invariant: Centroid formulas must use pre-move `hitCount` values (`oldHitCount` / `newHitCount`) captured before `assignClusterToWorkout` reconciles counts. Sport updates must query `getClusterById` afresh after `assignClusterToWorkout`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * In `moveWorkoutToCluster`:
    1. Compute and persist centroid updates using pre-move `hitCount`.
    2. Invoke `assignClusterToWorkout(context, workoutId, newClusterId)` to update `WorkoutSummaries` and reconcile `hitCount`.
    3. Re-calculate `mostFrequentSport` after `assignClusterToWorkout` and apply to freshly queried cluster instances.
  * Unit test asserting both updated centroids and updated probable sport are preserved.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Altitude extrema coordinates/bounds in clusters are owned by `ATT-6047` (blocked by this ticket).
  * No modifications to `assignClusterToWorkout` internal scoring or SQLite schemas.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new refinement `REQ-REV-007` (*Workout Cluster Reassignment Centroid Integrity & Sport Frequency Synchronization*), refining `REQ-REV-002` and `REQ-DAT-025`.
* **Historical Origin & Commit Trace**: Added in `b1029c42` where sequential `.copy()` calls were made on the initial parameter snapshot without realizing previous DB writes were overwritten.
* **Root Reason for Existing Formulation**: Developer grouped sport updates directly after centroid updates without considering immutability of data classes or DB update sequence.
* **Preservation of Core Invariants**: Centroid weighting formulas remain unchanged; `assignClusterToWorkout` retains authority over `WorkoutSummaries.CLUSTER_ID`; 100% test pass rate.

---

## 5. Architectural Strategy & High-Level Solution
* Reorder operations in `moveWorkoutToCluster`:
  1. Centroid calculation $\to$ update cluster in DB.
  2. `assignClusterToWorkout` $\to$ updates `WorkoutSummaries` and cluster `hitCount`.
  3. `getMostFrequentSportIdForCluster` $\to$ update `probableSportId` on freshly fetched cluster record.
* Add unit test in `WorkoutClusterEngineTest.kt` validating post-move centroid and sport preservation.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Cluster weighted centroids mathematically reflect added/removed workouts.
  2. `WorkoutSummaries` remains the authoritative source for sport frequency.
* **Risk Rating**: **LOW**. Contained logic and order-of-execution fix in `moveWorkoutToCluster` with zero schema impact.
