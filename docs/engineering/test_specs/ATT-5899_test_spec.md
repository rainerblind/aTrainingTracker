# Stage 2: Requirement & Test Specification - ATT-5899: Point-to-Point Corridor Discrimination & Elliptical Apex

**Ticket**: [ATT-5899](https://rainerblind.atlassian.net/browse/ATT-5899)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Requirement Mapping**: `REQ-SET-097` (*Point-to-Point Corridor Discrimination & Non-Degenerate Elliptical Apex*)  
**Test Spec ID**: `TST-SET-097`  
**Branch**: `feature/ATT-5899`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-SET-097)

### 1.1 Problem Statement & Rationale
On point-to-point (A -> B) routes (e.g. Sindelfingen -> Schönaich), maximizing distance from `start` alone causes `apex ≈ end`. This degenerates the 20% apex similarity term ($s_3$) into a redundant duplicate of the destination term ($s_2$), blinding `WorkoutClusterEngine` from distinguishing parallel corridors (e.g., eastern forest vs. central city) between the same endpoints.

### 1.2 Functional & Architectural Requirements
1. **Elliptical Apex Calculation (`REQ-SET-097.1`)**: For routes where $d(\text{start}, \text{end}) \ge 150\text{ m}$, `WorkoutClusterEngine.findApexFromPoints(start, points, end)` SHALL maximize the elliptical focal distance sum $S(P) = d(\text{start}, P) + d(P, \text{end})$. If $\max S(P) < d(\text{start}, \text{end}) + 20\text{ m}$, it SHALL select the 50% cumulative distance midpoint.
2. **Session Persistence Synchronization (`REQ-SET-097.2`)**: `TrackerService` SHALL supply `endPos` to `findApexFromPoints()` upon session finalization, storing non-degenerate apex coordinates in `WorkoutSummaries`.
3. **Corridor-Aware Candidate Scoring (`REQ-SET-097.3`)**: In `scoreClusters()`, when candidate clusters share matching endpoints ($s_1, s_2 \le 0.5$) and preview polylines, the system SHALL evaluate 5-station corridor deviation via `WorkoutClusterCorridorDetector` to prefer matching corridor variants.
4. **Loop Backward Compatibility (`REQ-SET-097.4`)**: For closed loops ($d(\text{start}, \text{end}) < 150\text{ m}$), displacement from `start` alone SHALL be evaluated, preserving legacy behavior 1:1.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1**: *Given* an A -> B route with lateral deflection, *When* `findApexFromPoints` runs, *Then* the apex captures the mid-corridor deflection and does NOT collapse onto `end`.
* **AC-2**: *Given* parallel candidate clusters sharing endpoints, *When* a new workout is scored, *Then* `scoreClusters()` ranks the matching corridor cluster first.
* **AC-3**: *Given* a closed-loop course, *When* `findApexFromPoints` runs, *Then* apex coordinates are identical to legacy calculations.

---

## 2. Test Specification (TST-SET-097)

### TST-SET-097.1: Elliptical Apex Unit Tests (`WorkoutClusterPointToPointApexTest.kt`)
* **Scope**: Unit test in `app/src/test/.../database/`.
* **Verifications**: Lateral arc produces mid-corridor apex ($> 1\text{ km}$ from `end`); straight line selects 50% midpoint; closed loops match legacy coordinates 100%.

### TST-SET-097.2: Corridor Scoring & Discrimination Tests (`WorkoutClusterCorridorScoringTest.kt`)
* **Scope**: Unit test in `app/src/test/.../database/`.
* **Verifications**: Forest corridor workout matches forest cluster over parallel city cluster; city workout matches city cluster.

### TST-SET-097.3: TrackerService Finalization Audit
* **Scope**: Contract / unit test in `app/src/test/.../tracker/`.
* **Verifications**: `endPos` passed to `findApexFromPoints()` and written to `WorkoutSummaries`.

### TST-SET-097.4: Tier 1 Targeted Regression Suite
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.clusters.*" --tests "com.atrainingtracker.trainingtracker.database.WorkoutCluster*" --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Expected Result**: 100% pass rate, zero regressions across cluster domain.

---

## 3. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-SET-063` (*Workout Cluster Apex Arithmetic Mean & Live Tracking Sync*), introducing `REQ-SET-097`.
* **Historical Origin & Commit Trace**: Commit `76506c0d` (`ATT-498`, sprint `2026-40.16`).
* **Root Reason for Existing Formulation**: Ensured authentic on-track apex coordinates, assuming closed loops where max displacement from start occurs midway.
* **Preservation of Core Invariants**: For loops ($d(\text{start}, \text{end}) < 150\text{ m}$), $d(\text{start}, P) + d(P, \text{end}) \equiv 2 \cdot d(\text{start}, P)$, preserving legacy behavior 1:1.

---

## 4. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-097.1` | Unit | `WorkoutClusterEngine.findApexFromPoints` | `REQ-SET-097.1`, `REQ-SET-097.4` | Specified |
| `TST-SET-097.2` | Unit | `WorkoutClusterEngine.scoreClusters` | `REQ-SET-097.3` | Specified |
| `TST-SET-097.3` | Integration | `TrackerService.onWorkoutFinished` | `REQ-SET-097.2` | Specified |
| `TST-SET-097.4` | Tier 1 | Cluster test suite | `REQ-PRO-001` | Specified |
