# Stage 2: Requirement & Test Specification - ATT-6686: Bipolar Product Apex & last_apex_repair_v2

**Ticket**: [ATT-6686](https://rainerblind.atlassian.net/browse/ATT-6686)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2` | **SP**: 2  
**Requirement Mapping**: `REQ-SET-097` (*Route Corridor Discrimination & Bipolar Apex*), `REQ-DAT-039` (*Apex Migration & Import Parity*)  
**Test Spec ID**: `TST-SET-097`, `TST-DAT-035`  
**Branch**: `sprint/2026-46.2` (`subagent-ATT-176---Route-Clusters-Flash-Agent-self-da166a59`)  
**Author**: AI Agent 2 (Fast Implementer & Test Author)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification (`REQ-SET-097`, `REQ-DAT-039`)

### 1.1 Problem Statement & Rationale
On point-to-point routes ($d(\text{start}, \text{end}) \ge 150\text{ m}$), the elliptical sum $d(\text{start}, P) + d(P, \text{end})$ mathematically favors longitudinal GPS overshoots near endpoints ($2\delta \gg 2h^2/D$) over lateral curves, causing apex degeneracy near destination coordinates (e.g. Pixel 10 `Bike2Work` #2063). Furthermore, `distToEnd < 50f` skip filtering prevented healing realistic stops. The system SHALL evaluate the bipolar product $d(\text{start}, P) \times d(P, \text{end})$ and execute self-healing migration v2.

### 1.2 Functional & Architectural Requirements
1. **Bipolar Product Apex (`REQ-SET-097.1`)**: For point-to-point routes ($d \ge 150\text{ m}$), `WorkoutClusterEngine.findApexFromPoints(start, points, end)` SHALL maximize $d(\text{start}, P) \times d(P, \text{end})$. Straight routes peak at midpoint ($D/2$); lateral curves peak at detour apex; endpoint overshoots yield $0$ and are excluded. Collinear fallback pass is deleted.
2. **Self-Healing Migration v2 (`REQ-DAT-039.1`)**: `WorkoutClusterEngine.healLegacyPointToPointApexes()` SHALL scan all point-to-point workouts without `distToEnd < 50f` skip filter. Workouts with `recordedApex == null || distanceBetween(recordedApex, trueApex) > 25f` SHALL be updated in `TABLE_EXTREMA_VALUES` and cluster centroids recalculated.
3. **Repository Preference Bump (`REQ-DAT-039.2`)**: `WorkoutClusterRepository` SHALL use `SP_KEY_LAST_APEX_REPAIR = "last_apex_repair_v2"`, executing when stored value `< 2` and saving `2`.
4. **Closed-Loop Backward Compatibility (`REQ-SET-097.4`, `REQ-DAT-039.5`)**: Closed loops ($d < 150\text{ m}$) SHALL remain 100% untouched using distance-from-start.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Bipolar Apex Selection)**:
  * *Given* an A->B route ($d \ge 150\text{ m}$) with endpoint overshoots and a lateral curve,
  * *When* `findApexFromPoints(start, points, end)` is invoked,
  * *Then* it selects the mid-corridor detour apex and excludes endpoint overshoots.
* **Criterion 2 (Straight Collinear Midpoint)**:
  * *Given* a straight collinear A->B route,
  * *When* `findApexFromPoints(start, points, end)` is invoked,
  * *Then* it selects the track midpoint ($D/2$) and does not degenerate onto `end`.
* **Criterion 3 (Self-Healing v2 Migration)**:
  * *Given* historical point-to-point workouts with overshoot apexes ($> 50\text{ m}$ from `end`),
  * *When* `refreshClusters()` runs with stored repair flag `< 2`,
  * *Then* `healLegacyPointToPointApexes()` heals extrema and centroids, setting `last_apex_repair_v2 = 2`.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-SET-097.1` (`req_settings_profiles.md`) & `REQ-DAT-039.1`/`039.2` (`req_data_migration_export.md`).
* **Historical Origin & Commit Trace**: `ATT-5899` (non-degenerate A->B apex) and `ATT-6319` (`healLegacyPointToPointApexes()`).
* **Root Reason for Existing Formulation**: `ATT-5899` used elliptical focal sum to discriminate collinear lines, unaware that longitudinal overshoots dominate lateral deflection ($2\delta \gg 2h^2/D$). `ATT-6319` added a `< 50f` heuristic which missed realistic $85\text{--}120\text{ m}$ road stops.
* **Preservation of Core Invariants**: The bipolar product peaks at midpoint for straight paths and detour summit for curved routes. Closed-loop course calculation ($d < 150\text{ m}$) is 100% preserved.

---

## 3. Test Specification (`TST-SET-097`, `TST-DAT-035`)

### Test Case 1: `testPointToPointRouteWithLateralArc_selectsBipolarApexAwayFromEnd` (`TST-SET-097.1`)
* **Scope**: Unit Test | **File**: `database/WorkoutClusterPointToPointApexTest.kt`
* **Preconditions**: Track with Sindelfingen -> Schönaich endpoints ($7.3\text{ km}$), lateral detour, and terminal overshoots.
* **Action**: Invoke `findApexFromPoints(start, points, end)`.
* **Expected Result**: Detour apex selected; distance to `end` $> 1.5\text{ km}$; endpoint overshoots ignored.

### Test Case 2: `testCollinearStraightPointToPointRoute_selectsMidpoint` (`TST-SET-097.2`)
* **Scope**: Unit Test | **File**: `database/WorkoutClusterPointToPointApexTest.kt`
* **Preconditions**: Collinear straight points from West to East ($d \ge 150\text{ m}$).
* **Action**: Invoke `findApexFromPoints(start, points, end)`.
* **Expected Result**: Exact midpoint selected; apex does not collapse onto `end`.

### Test Case 3: `testHealLegacyPointToPointApexes_healsOvershootAndBumpsV2` (`TST-DAT-035.1`)
* **Scope**: Unit / Migration Test | **File**: `database/WorkoutClusterApexMigrationTest.kt`
* **Preconditions**: Database with A->B workout having recorded apex $85.7\text{ m}$ from `end` (previously skipped by $< 50\text{ m}$).
* **Action**: Invoke `healLegacyPointToPointApexes(context)` and verify repository repair with `last_apex_repair_v2`.
* **Expected Result**: Workout extrema updated; cluster centroid recalculated; SharedPreferences key `last_apex_repair_v2` = 2.

### Test Case 4: Localization & Specifier Audit (`TST-SET-097.3`)
* **Scope**: Localization Parity Test | **Goal**: 100% parity across 9 locales (`REQ-UI-106`). Zero missing entries or specifier mismatches.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-SET-097.4`, `TST-DAT-035.4`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.WorkoutCluster*"`
* **Expected Result**: 100% pass rate across cluster engine and migration suites.

---

## 4. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-097.1` | Unit | `WorkoutClusterEngine.findApexFromPoints` | `REQ-SET-097.1` | Specified |
| `TST-SET-097.2` | Unit | `WorkoutClusterEngine.findApexFromPoints` | `REQ-SET-097.1` | Specified |
| `TST-DAT-035.1` | Migration | `WorkoutClusterEngine.healLegacyPointToPointApexes` | `REQ-DAT-039.1`, `REQ-DAT-039.2` | Specified |
| `TST-SET-097.3` | Localization | `TranslationParityTest` | `REQ-UI-106` | Specified |
| `TST-SET-097.4` | Regression | `WorkoutCluster*Test` | `REQ-SET-097.4`, `REQ-DAT-039.5` | Specified |
