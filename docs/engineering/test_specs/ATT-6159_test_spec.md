# Stage 2: Requirement & Test Specification - ATT-6159: WorkoutClusterEngine.moveWorkoutToCluster Fix

**Ticket**: [ATT-6159](https://rainerblind.atlassian.net/browse/ATT-6159)  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6159`  
**Requirement Mapping**: `REQ-REV-007` | **Test Spec ID**: `TST-REV-008`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-REV-007)

### 1.1 Problem Statement & Rationale
In `WorkoutClusterEngine.kt:1166-1208` (`moveWorkoutToCluster`), two sequential flaws corrupt cluster records when an athlete moves a workout:
1. `dbManager.updateCluster(oldCluster.copy(...))` computes and updates centroids, but directly afterwards, `dbManager.updateCluster(oldCluster.copy(probableSportId = ...))` copies the stale, pre-centroid-update snapshot, overwriting and discarding the recomputed centroids. The same bug affects `newCluster`.
2. `summariesManager.getMostFrequentSportIdForCluster` runs before `assignClusterToWorkout(context, workoutId, newClusterId)` reassigns `WorkoutSummaries.CLUSTER_ID`, calculating sport frequency against the pre-move workout distribution.

### 1.2 Functional & Architectural Requirements
1. **Centroid Update Preservation (`REQ-REV-007.1`)**:
   - In `moveWorkoutToCluster`, centroid calculations SHALL compute updated coordinates using pre-move `hitCount` and persist them to `dbManager`. Subsequent sport mutations SHALL NEVER overwrite updated centroids with a stale snapshot.
2. **Database Sequence Invariant (`REQ-REV-007.2`)**:
   - `assignClusterToWorkout(context, workoutId, newClusterId)` SHALL execute before sport frequency recalculation, ensuring `WorkoutSummaries.CLUSTER_ID` is updated in SQLite and `hitCount` is reconciled.
3. **Post-Move Sport Frequency Recalculation (`REQ-REV-007.3`)**:
   - `summariesManager.getMostFrequentSportIdForCluster` SHALL be evaluated after `assignClusterToWorkout` for both source and target clusters. Updated sport properties (`probableSportId`, `bSportType`) SHALL be applied to freshly queried cluster records from `dbManager.getClusterById`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Centroid Preservation)**: Given a workout moved from `currentClusterId` to `newClusterId`, when `moveWorkoutToCluster` completes, then both source and target clusters retain their recomputed weighted centroids without being overwritten.
* **Criterion 2 (Sport Frequency Synchronization)**: Given a workout moved to a cluster that alters the majority sport, when `moveWorkoutToCluster` completes, then `probableSportId` and `bSportType` are computed against the post-move database state and persisted on the fresh cluster instance.
* **Criterion 3 (Reconciled Hit Count)**: Given a moved workout, when processed, then pre-move hit counts are used for centroid weighting and post-move hit counts reflect the reassignment.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new refinement `REQ-REV-007` refining `REQ-REV-002` and `REQ-DAT-025`.
2. *Historical Origin & Commit Trace*: Introduced in `b1029c42`; sequential `.copy()` operations were chained on the initial immutable parameter snapshot.
3. *Root Reason for Existing Formulation*: Developer grouped sport updates alongside centroid calculation without realizing immutable data class instances discard previous database writes.
4. *Preservation of Core Invariants*: Centroid formulas, `WorkoutSummaries.CLUSTER_ID` authority, and cluster locking remain intact; zero schema alterations.

---

## 2. Test Specification (TST-REV-008)

### Test Case 1: Centroid and Sport Preservation in `moveWorkoutToCluster` (`TST-REV-008.1`)
* **Scope**: Unit test in `EditWorkoutClusterTest.kt`. Verify moving a workout between clusters with sport change recalculates and preserves both centroids and `probableSportId`.

### Test Case 2: Post-Move Reassignment Execution Order (`TST-REV-008.2`)
* **Scope**: Unit test in `EditWorkoutClusterTest.kt`. Verify `assignClusterToWorkout` runs before `getMostFrequentSportIdForCluster`.

### Test Case 3: Tier 1 Targeted Test Suite (`TST-REV-008.3`)
* **Scope**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.EditWorkoutClusterTest" --tests "com.atrainingtracker.trainingtracker.database.WorkoutCluster*" --tests "com.atrainingtracker.trainingtracker.build.*"`.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-REV-008.1]` | Unit | `WorkoutClusterEngine.moveWorkoutToCluster` | `REQ-REV-007.1`, `REQ-REV-007.3` | Specified |
| `[TST-REV-008.2]` | Unit | `moveWorkoutToCluster` execution order | `REQ-REV-007.2` | Specified |
| `[TST-REV-008.3]` | Regression | Tier 1 targeted cluster suite | `REQ-PRO-001` | Specified |
