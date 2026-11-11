# Stage 5: Walkthrough & Verification - ATT-6686: Bipolar Product Apex & last_apex_repair_v2

**Ticket**: [ATT-6686](https://rainerblind.atlassian.net/browse/ATT-6686)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2` | **SP**: 2  
**Requirement Mapping**: `REQ-SET-097.1`, `REQ-DAT-039.1`, `REQ-DAT-039.2`  
**Test Mapping**: `TST-SET-097.1`, `TST-DAT-035.1`  
**Branch**: `sprint/2026-46.2` (`subagent-ATT-176---Route-Clusters-Pro-Agent-self-c3a66c20`)  
**Author**: AI Agent 1 (Senior ASPICE Architecture & Pro Verifier)  
**Date**: 2026-11-11  

---

## 1. Executive Summary & Verification Overview
Inspection of real Pixel 10 telemetry (`Bike2Work` cluster `#1553`, 606 workouts) showed the spatial apex (`max_disp`) positioned $64.8\text{ m}$ from `end` at `(48.70718, 8.99088)`. This defect was caused by:
1. The elliptical sum $d(\text{start}, P) + d(P, \text{end})$ in `findApexFromPoints()`, where longitudinal endpoint overshoots mathematically overpower lateral corridor curves ($2\delta \gg 2h^2/D$).
2. The `distToEnd < 50f` skip filter in `healLegacyPointToPointApexes()`, which skipped realistic rides where riders pass $> 50\text{ m}$ past terminal coordinates (e.g. $85.7\text{ m}$ on `#2063`).

We replaced the elliptical sum with the bipolar distance product $d(\text{start}, P) \times d(P, \text{end})$, removed the `< 50f` skip filter, updated the healing threshold to `distanceBetween(recordedApex, trueApex) > 25f`, and bumped the preference key to `SP_KEY_LAST_APEX_REPAIR = "last_apex_repair_v2"` (checking `< 2`, writing `2`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-SET-097.1` | `TST-SET-097.1` | Automated Unit Test (`WorkoutClusterPointToPointApexTest`) | **PASSED** | `Verified` |
| `REQ-DAT-039.1` | `TST-DAT-035.1` | Automated Unit/Migration Test (`WorkoutClusterApexMigrationTest`) | **PASSED** | `Verified` |
| `REQ-DAT-039.2` | `TST-DAT-035.1` | Repository SharedPreferences Bump Test (`WorkoutClusterApexMigrationTest`) | **PASSED** | `Verified` |
| `REQ-SET-097.4` | `TST-SET-097.4` | Closed-Loop Invariance Regression Test | **PASSED** | `Verified` |
| `REQ-UI-106` | `TST-SET-097.3` | Localization Parity Audit (`TranslationParityTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-SET-097.4` | Tier 1 Targeted Regression (`com.atrainingtracker.trainingtracker.database.WorkoutCluster*`) | **PASSED** | `Verified` |

---

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-SET-097.1` (`req_settings_profiles.md`) & `REQ-DAT-039.1`/`039.2` (`req_data_migration_export.md`).
* **Historical Origin & Commit Trace**: `ATT-5899` (non-degenerate A->B apex) and `ATT-6319` (`healLegacyPointToPointApexes()`).
* **Root Reason for Existing Formulation**: `ATT-5899` used elliptical focal sum to discriminate collinear lines, unaware that longitudinal overshoots dominate lateral deflection ($2\delta \gg 2h^2/D$). `ATT-6319` added a `< 50f` heuristic which missed realistic $85\text{--}120\text{ m}$ road stops.
* **Preservation of Core Invariants**: The bipolar product peaks at midpoint for straight paths and detour summit for curved routes. Closed-loop course calculation ($d < 150\text{ m}$) is 100% preserved.

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope (`./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.WorkoutCluster*"`)
```text
BUILD SUCCESSFUL in 4m 33s
33 actionable tasks: 9 executed, 24 up-to-date
```
* **WorkoutClusterPointToPointApexTest**:
  - `testPointToPointRouteWithLateralArc_selectsEllipticalApexAwayFromEnd`: Detour apex selected away from end ($> 1.5\text{ km}$).
  - `testCollinearStraightPointToPointRoute_selectsMidpoint`: Straight collinear line selects $D/2$ midpoint.
  - `testPointToPointRoute_withEndpointOvershoots_selectsDetourApexAndSuppressesOvershoots`: Start/end overshoots produce product $0$ and are completely excluded.
  - `testPointToPointRoute_straightCollinearWithTerminalOvershoot_selectsMidpoint`: Midpoint selected even with $100\text{ m}$ terminal overshoot.
  - `testClosedLoopCourse_preservesLegacyStartDisplacement100Percent`: Closed loops ($d < 150\text{ m}$) 100% identical to legacy logic.
* **WorkoutClusterApexMigrationTest**:
  - `testHealLegacyPointToPointApexes_recomputesDegenerateApexAndUpdatesClusterCentroid`: Heals degenerate apexes and updates cluster centroids.
  - `testHealLegacyPointToPointApexes_withOvershootGreaterThan50m_isHealedInV2`: Verifies real-world overshoots ($85.7\text{ m} \ge 50\text{ m}$) are healed.
  - `testWorkoutClusterRepository_triggersApexMigrationOnce`: Verifies `last_apex_repair_v2` executes when $< 2$ and saves `2`.
  - `testHealLegacyPointToPointApexes_leavesClosedLoopsUntouched`: Closed loops are never modified.

---

## 4. Hardware / Physical Verification (Pixel 10)
Pure algorithmic and database migration enhancement. Verified against mathematical route profiles and authentic Pixel 10 telemetry traces (`Bike2Work` #2063). Zero UI layout modifications.

### Visual Consistency (Rule 23)
No UI changes.

---

## 5. Invariant & Governance Verification
1. **Zero Production Regressions**: Targeted cluster test suites and governance checks pass cleanly.
2. **Closed-Loop Route Invariance**: Closed loops ($d < 150\text{ m}$) strictly preserve legacy behavior.
3. **Living Documentation Synchronized**: `REQ-SET-097` and `REQ-DAT-039` verified via `verify_requirement_governance.py`.
4. **Human Decision Gate**: Parent ticket transitioned to `Final Review (Human)` assigned to Rainer Blind.
