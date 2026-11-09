# Stage 5: Walkthrough & Verification - ATT-5899: WorkoutClusterEngine Route Corridor Discrimination & Apex Degeneracy

**Ticket**: [ATT-5899](https://rainerblind.atlassian.net/browse/ATT-5899)  
**Sub-task**: Pending (`[Test]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Workout Clusters*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Requirement Mapping**: `REQ-SET-097` (`REQ-SET-097.1`–`REQ-SET-097.4`)  
**Test Mapping**: `TST-SET-097` (`TST-SET-097.1`–`TST-SET-097.4`)  
**Branch**: `feature/ATT-5899`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Executive Summary & Verification Overview
For point-to-point routes ($A \to B$), the legacy max start-distance apex metric $\max_i d(\text{start}, P_i)$ naturally collapsed onto endpoint $B$, causing apex degeneracy ($apex \approx end$) and preventing the spatial engine from discriminating parallel route corridors (e.g., Waldweg vs. Bundesstraße between two towns).
This implementation delivers:
1. **Non-Degenerate Elliptical Apex Calculation**: Implemented `findApexFromPoints(start, points, end)` evaluating the distance sum $S(P) = d(\text{start}, P) + d(P, \text{end})$ for $d(\text{start}, \text{end}) \ge 150\text{ m}$. For collinear straight-line segments ($\max S(P) < D + 20\text{ m}$), the point nearest the 50% cumulative distance midpoint is anchored as apex. For loop routes ($d < 150\text{ m}$) or when `end == null`, legacy behavior is preserved 100%.
2. **Live Tracking Synchronization**: Updated `TrackerService` session completion to compute apex and displacement using `findApexFromPoints(startPos, points, endPos)`.
3. **5-Station Corridor Deviation Scoring**: Added polyline-aware corridor deviation tie-breaking in `calculateSimilarity`, `scoreClusters`, and `suggestCluster` using 5 fractional stations $\{0.15, 0.30, 0.50, 0.70, 0.85\}$ against cluster `previewPaths`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-SET-097.1` | `TST-SET-097.1` | Elliptical Apex Geometry (`WorkoutClusterPointToPointApexTest`) | **PASSED** | `Verified` |
| `REQ-SET-097.2` | `TST-SET-097.3` | TrackerService Apex Integration (`MaxLineDistanceIntegrityTest`) | **PASSED** | `Verified` |
| `REQ-SET-097.3` | `TST-SET-097.2` | 5-Station Corridor Scoring (`WorkoutClusterCorridorScoringTest`) | **PASSED** | `Verified` |
| `REQ-SET-097.4` | `TST-SET-097.1` | Loop Route Backward Compatibility (`WorkoutClusterPointToPointApexTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-SET-097.4` | Tier 1 Targeted Regression Suite (306 tests) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope
* Scope: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.clusters.*" --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.tracker.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
```text
BUILD SUCCESSFUL in 49s
32 actionable tasks: 1 executed, 31 up-to-date
```
* Results: 306 unit tests passed with 100% success rate (0 failures, 0 regressions).
* Living Docs & SWE Audit: `AspiceSweAuditContractTest` passed 100%.
* Governance Parity: `python3 tools/verify_requirement_governance.py` passed with 100% parity (Requirements: 922/916/6, Tests: 924/924/0).

---

## 4. Hardware / Physical Verification (Pixel 10)
* Pure algorithmic spatial geometry and scoring engine optimization; zero direct UI/layout changes; verified via automated Robolectric and JUnit 4 regression test suites.

---

## 5. Invariant & Governance Verification
1. **Loop Invariant**: For all closed loops ($d(\text{start}, \text{end}) < 150\text{ m}$), apex calculation remains mathematically identical to legacy $\max d(\text{start}, P)$.
2. **Collinear Midpoint Invariant**: Ruler-straight $A \to B$ corridors anchor apex at the geometric midpoint, completely preventing $apex \approx end$.
3. **Living Documentation Synchronized**: `REQ-SET-097` and `TST-SET-097` set to `Verified` across `req_settings_profiles.md`, `tst_settings_profiles.md`, `docs/requirements.md`, and `docs/tests.md`.
4. **Target Release Integrity**: Target release verified as `V5.0.0` (matching Jira active unreleased release).
