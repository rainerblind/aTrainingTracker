# Stage 2: Requirement & Test Specification - ATT-6319: Point-to-Point Apex Migration & Import Unification

**Ticket**: [ATT-6319](https://atrainingtracker.atlassian.net/browse/ATT-6319)  
**Parent Epic**: [ATT-176](https://atrainingtracker.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6319`  
**Requirement Mapping**: `REQ-DAT-039` (`req_data_migration_export.md`) | **Test Spec ID**: `TST-DAT-035` (`tst_data_migration_export.md`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-DAT-039)

### 1.1 Problem Statement & Rationale
While `REQ-SET-097` (`ATT-5899`) introduced non-degenerate elliptical apex calculation (`findApexFromPoints`) for live recordings on point-to-point (A->B) routes ($d(\text{start}, \text{end}) \ge 150\text{ m}$), historical workouts in `TABLE_EXTREMA_VALUES` and clusters in `WorkoutClusters.db` still store legacy apex coordinates that collapsed onto the destination point ($P_{\text{apex}} \approx P_{\text{end}}$). Newly tracked workouts fail similarity scoring against legacy clusters ($s_3 \gg 1.0$), fragmenting clusters. Furthermore, `LegacyImportEngine.kt` and `WorkoutDataMapper.kt` still calculate apex exclusively by maximizing $d(\text{start}, pt)$, re-corrupting apex coordinates during file imports and summary mapping. The system SHALL perform a self-healing migration across existing point-to-point database records and unify apex calculation across import pipelines and data mappers.

### 1.2 Functional & Architectural Requirements
1. **Self-Healing Database Apex Migration (`REQ-DAT-039.1`)**:
   - `WorkoutClusterEngine` SHALL provide `healLegacyPointToPointApexes()` to scan point-to-point workouts ($d(\text{start}, \text{end}) \ge 150\text{ m}$) having valid track points or map polylines.
   - For workouts where the stored apex degenerates near the destination ($d(P_{\text{apex}}, P_{\text{end}}) < 50\text{ m}$ or collinearity collapse), recompute non-degenerate apex via `findApexFromPoints(start, points, end)` and persist updated `MAX_LINE_DISTANCE_LAT`, `LNG`, and `LINE_DISTANCE_m` in `TABLE_EXTREMA_VALUES`.
   - Recalculate cluster centroids (`maxDispLat`, `maxDispLng`) for all affected clusters.
2. **Repository SharedPreferences-Guarded Trigger (`REQ-DAT-039.2`)**:
   - `WorkoutClusterRepository.refreshClusters()` SHALL trigger `healLegacyPointToPointApexes()` asynchronously on `Dispatchers.IO`, guarded by `SP_KEY_LAST_APEX_REPAIR`, ensuring exactly-once execution.
3. **Legacy Import Pipeline Unification (`REQ-DAT-039.3`)**:
   - `LegacyImportEngine.kt` SHALL compute apex coordinates for imported FIT/TCX/GPX/Strava streams using `findApexFromPoints(start, points, end)`, persist them in `TABLE_EXTREMA_VALUES`, and forward `workoutPolyline = points` to `suggestCluster`.
4. **Data Mapper Symmetrical Apex Resolution (`REQ-DAT-039.4`)**:
   - In `WorkoutDataMapper.kt`, `resolveAuthoritativeApex` SHALL accept `endLatLng: LatLng?` and delegate to `findApexFromPoints(start, points, end)`.
5. **System Invariants (`REQ-DAT-039.5`)**:
   - Closed-loop routes ($d(\text{start}, \text{end}) < 150\text{ m}$) SHALL remain completely untouched; no DDL schema changes; 100% unit test pass rate.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Self-Healing Apex Migration)**:
  * *Given* historical point-to-point workouts with degenerated apex ($P_{\text{apex}} \approx P_{\text{end}}$),
  * *When* `WorkoutClusterRepository.refreshClusters()` executes,
  * *Then* `healLegacyPointToPointApexes()` recomputes non-degenerate apexes via `findApexFromPoints`, updates `TABLE_EXTREMA_VALUES` and cluster centroids, and sets the SharedPreferences completion flag.
* **Criterion 2 (Import Pipeline Unification)**:
  * *Given* an imported FIT/TCX/GPX point-to-point activity in `LegacyImportEngine`,
  * *When* imported into the database,
  * *Then* apex coordinates are computed via `findApexFromPoints`, stored in extrema, and passed to `suggestCluster`.
* **Criterion 3 (Data Mapper Parity)**:
  * *Given* `WorkoutDataMapper.resolveAuthoritativeApex` called with points, start, and end,
  * *When* evaluated for a point-to-point route,
  * *Then* it returns the non-degenerate mid-corridor apex matching `findApexFromPoints`.

---

## 2. Test Specification (TST-DAT-035)

### Test Case 1: Database Self-Healing Migration Test (`TST-DAT-035.1`)
* **Scope**: `WorkoutClusterApexMigrationTest.kt`.
* **Assertions**: Assert `healLegacyPointToPointApexes()` recomputes degenerated A->B apexes and updates cluster centroids while leaving closed-loop routes intact.

### Test Case 2: Legacy Import Apex Calculation Test (`TST-DAT-035.2`)
* **Scope**: `WorkoutClusterApexMigrationTest.kt` / `LegacyImportEngineTest.kt`.
* **Assertions**: Assert `LegacyImportEngine.kt` uses `findApexFromPoints` and passes `workoutPolyline` to `suggestCluster`.

### Test Case 3: WorkoutDataMapper Apex Resolution Test (`TST-DAT-035.3`)
* **Scope**: `WorkoutClusterApexMigrationTest.kt` / `WorkoutDataMapperTest.kt`.
* **Assertions**: Assert `WorkoutDataMapper.resolveAuthoritativeApex` delegates to `findApexFromPoints` when `endLatLng` is provided.

### Test Case 4: Tier 1 Targeted Regression Suite (`TST-DAT-035.4`)
* **Scope**: Targeted unit tests (`database.WorkoutCluster*`, `migration.*`, `ui.aftermath.*`, `build.*`).
* **Assertions**: 100% pass across touched packages and architectural rules.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-DAT-035.1]` | Unit / DB | `WorkoutClusterEngine.healLegacyPointToPointApexes`: Recompute A->B apexes | `REQ-DAT-039.1`, `REQ-DAT-039.2` | Planned |
| `[TST-DAT-035.2]` | Contract | `LegacyImportEngine.kt`: `findApexFromPoints` & `suggestCluster` polyline | `REQ-DAT-039.3` | Planned |
| `[TST-DAT-035.3]` | Contract | `WorkoutDataMapper.kt`: `resolveAuthoritativeApex` with `endLatLng` | `REQ-DAT-039.4` | Planned |
| `[TST-DAT-035.4]` | Regression | Tier 1 targeted suite (`database.WorkoutCluster*`, `migration.*`, `build.*`) | `REQ-DAT-039.5`, `REQ-PRO-001` | Planned |
