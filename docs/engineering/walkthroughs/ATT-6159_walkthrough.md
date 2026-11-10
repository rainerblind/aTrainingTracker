# Stage 5: Walkthrough & Verification - ATT-6159: Fix Stale-Copy Overwrite in moveWorkoutToCluster

**Ticket**: [ATT-6159](https://rainerblind.atlassian.net/browse/ATT-6159)  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6159`  
**Requirement Mapping**: `REQ-REV-007` | **Test Spec ID**: `TST-REV-008`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Executive Summary & Verification Scope
In `WorkoutClusterEngine.kt:1166-1208` (`moveWorkoutToCluster`), moving a workout between route clusters previously suffered from two sequential defects:
1. `dbManager.updateCluster(oldCluster.copy(...))` computed and wrote updated weighted centroids, but directly following, `dbManager.updateCluster(oldCluster.copy(probableSportId = ...))` copied the stale initial snapshot, overwriting and discarding the recomputed centroids. The same bug affected `newCluster`.
2. `summariesManager.getMostFrequentSportIdForCluster` was invoked before `assignClusterToWorkout(context, workoutId, newClusterId)` updated `WorkoutSummaries.CLUSTER_ID`, computing sport frequencies against the pre-move workout distribution.

This ticket reordered `moveWorkoutToCluster` so pre-move hit counts are captured for weighted centroid calculations and persisted first, `assignClusterToWorkout` updates `WorkoutSummaries` and reconciles cluster hit counts, and `getMostFrequentSportIdForCluster` runs post-reassignment, applying updated sport attributes strictly to freshly queried cluster records from `dbManager.getClusterById()`.

---

## 2. Automated Test Execution & Scope (Rule 60)
* **Tier 1 Targeted Test Scope**:
  * Command: `./gradlew testDebugUnitTest --max-workers=2 --tests "com.atrainingtracker.trainingtracker.database.EditWorkoutClusterTest" --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
  * Scope: All cluster editing, clustering database, aftermath reviews, and project build contract tests.
  * Result: **100% PASS** (182 tests completed, 0 failed, 1m 49s execution under `parallel_sprint_lock.py`).
* **Living Documentation Governance**:
  * Command: `python3 tools/verify_requirement_governance.py --sync-indexes && python3 tools/verify_requirement_governance.py`
  * Result: **PASS** with 100% mathematical parity across all 9 domain shards (`REQ-REV-007` and `TST-REV-008` marked `[Verified]`).

---

## 3. Acceptance Criteria Verification Table

| Acceptance Criterion | Verification Method | Status | Evidence |
| :--- | :--- | :--- | :--- |
| **AC-1 (Centroid Preservation)**: Recomputed centroids are retained after sport update without stale-copy overwrites | Unit Tests | **MET** | `EditWorkoutClusterTest.testMoveWorkoutToCluster_preservesUpdatedCentroidsAndSynchronizesSport` |
| **AC-2 (Sport Frequency Synchronization)**: `getMostFrequentSportIdForCluster` reflects post-move distribution | Unit Tests | **MET** | `EditWorkoutClusterTest.testMoveWorkoutToCluster_preservesUpdatedCentroidsAndSynchronizesSport` |
| **AC-3 (Reconciled Hit Count)**: Pre-move hit counts used for centroid formulas; post-move counts reconciled | Unit Tests | **MET** | `EditWorkoutClusterTest.testMoveWorkoutToCluster_preservesUpdatedCentroidsAndSynchronizesSport` |
| **AC-4 (Living Doc Governance)**: Master tables synced, living docs verified | Governance Tool | **MET** | `verify_requirement_governance.py` PASS |

---

## 4. Hardware / Physical Verification (Pixel 10)
* **Scope**: Non-UI backend route clustering and database coordination logic. Zero UI layout, navigation, or database schema changes. Verified via Tier 1 targeted unit test suite and clean-room build contract tests.
