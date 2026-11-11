# Stage 1 Analysis: ATT-6686 - WorkoutClusterEngine: Bipolar Product Apex & last_apex_repair_v2

**Ticket**: [ATT-6686](https://rainerblind.atlassian.net/browse/ATT-6686)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2` | **SP**: 2  
**Branch**: `sprint/2026-46.2` (`subagent-ATT-176---Route-Clusters-Pro-Agent-self-c3a66c20`)  
**Author**: AI Agent 1 (Senior ASPICE Architecture & Pro Verifier)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
On the Pixel 10 after `ATT-6319`, the `Bike2Work` cluster (`clusterId = 1553`, 606 workouts) retained its `max_disp` (Apex) marker at `(48.70718, 8.99088)`, only 64.8 m from destination `end`. Point-to-point routes ($d(\text{start}, \text{end}) \ge 150\text{ m}$) must anchor apex in the mid-corridor ($D/2$, e.g. `(48.68211, 9.01750)` for `Bike2Work`) or at detour summits (`(48.67097, 8.96245)` for `gemütlich`). Stored apexes degenerated near endpoints, fracturing cluster similarity scoring ($s_3 \gg 1.0$) and preventing zero-touch auto-naming.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Flawed Elliptical Sum (`dStart + dEnd`) in `findApexFromPoints()`**:
  * For route distance $D = 7,330\text{ m}$, a lateral curve $h = 120\text{ m}$ at midpoint yields $\Delta S \approx \frac{2h^2}{D} = +3.93\text{ m}$. In contrast, longitudinal GPS overshoot $\delta = 85\text{--}120\text{ m}$ near start/end yields $\Delta S = 2\delta = +170\text{--}240\text{ m}$.
  * Because $\max(d_{\text{start}} + d_{\text{end}}) \ge D + 20\text{ m}$ is virtually always triggered by endpoint noise on real roads, the collinear 50% midpoint fallback was dead code; the elliptical sum always selected an endpoint overshoot.
* **Premature Skip Filter in `healLegacyPointToPointApexes()`**:
  * `distToEnd < 50f` assumed degenerate apexes lie $< 50\text{ m}$ from `end`. On real workouts (e.g. `#2063`), riders overshoot the terminal coordinate by $85.7\text{ m} \ge 50\text{ m}$ before stopping. `healLegacyPointToPointApexes()` skipped all 606 workouts and flagged `last_apex_repair_v1 = 1`.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Audited `WorkoutClusterEngine.kt:344-381` and `551-555`: The bipolar product $d(\text{start}, P) \times d(P, \text{end})$ naturally equals 0 at both endpoints ($P = \text{start}$ or $P = \text{end}$), eliminating endpoint overshoots mathematically without distance thresholds or secondary collinear fallback loops.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail (REQ-PRO-029)
* **In-Scope Goals**:
  1. `WorkoutClusterEngine.findApexFromPoints()`: For point-to-point routes ($d \ge 150\text{ m}$), maximize bipolar product $d(\text{start}, P) \times d(P, \text{end})$, deleting dead-code `cumDistances` fallback.
  2. `WorkoutClusterEngine.healLegacyPointToPointApexes()`: Remove `distToEnd < 50f` skip filter; evaluate `trueApex` for all A->B workouts; heal if `recordedApex == null || distanceBetween(recordedApex, trueApex) > 25f`.
  3. `WorkoutClusterRepository.kt`: Bump key to `SP_KEY_LAST_APEX_REPAIR = "last_apex_repair_v2"`, executing when stored value `< 2` and saving `2`.
  4. Author unit/contract test cases and synchronize living documentation.
* **Out-of-Scope Non-Goals**:
  * Closed-loop courses ($d(\text{start}, \text{end}) < 150\text{ m}$) remain 100% untouched using distance-from-start.
  * No SQLite schema DDL changes; no modifications to cluster score thresholds. Complexity: 2 SP.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-SET-097.1` (`req_settings_profiles.md`) & `REQ-DAT-039.1`/`039.2` (`req_data_migration_export.md`).
* **Historical Origin & Commit Trace**: `ATT-5899` introduced non-degenerate A->B apex; `ATT-6319` introduced `healLegacyPointToPointApexes()`.
* **Root Reason for Existing Formulation**: `ATT-5899` used focal sum to separate straight-line collinearity, unaware that endpoint overshoots dominate lateral deflection ($2\delta \gg 2h^2/D$). `ATT-6319` used $< 50\text{ m}$ heuristic which missed realistic $85\text{--}120\text{ m}$ road stops.
* **Preservation of Core Invariants**: The bipolar product is maximal at mid-corridor for straight routes and at the apex for curved routes. Zero change to closed-loop calculations.

---

## 5. Architectural Strategy & Implementation Surfaces
| Component | File Path | Scope of Modification |
| :--- | :--- | :--- |
| **Clustering Engine** | `database/WorkoutClusterEngine.kt` | Replace elliptical sum with bipolar product in `findApexFromPoints()`; remove `distToEnd < 50f` filter and use $> 25\text{ m}$ update threshold in `healLegacyPointToPointApexes()` |
| **Repository** | `database/WorkoutClusterRepository.kt` | Update `SP_KEY_LAST_APEX_REPAIR = "last_apex_repair_v2"`, condition `< 2`, and store `2` |
| **Test Suites** | `database/WorkoutClusterPointToPointApexTest.kt`<br/>`database/WorkoutClusterApexMigrationTest.kt` | Add test cases verifying bipolar product apex on overshoots/detours, collinear midpoints, and v2 migration |
| **Living Docs** | `docs/requirements/req_settings_profiles.md`<br/>`docs/requirements/req_data_migration_export.md` | Formalize bipolar product calculation and v2 self-healing migration |

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Closed-loop course apex calculation ($d < 150\text{ m}$) is 100% untouched.
  2. Thread safety under `clusterLock`; asynchronous non-blocking migration on `Dispatchers.IO`.
  3. Zero regressions in existing cluster matching and unit tests.
* **Risk Rating**: **LOW**. Pure mathematical refinement inside domain engine and SharedPreferences-guarded self-healing migration without database schema alterations.
