# Stage 1 Analysis: Migrate Existing A->B Workout/Cluster Apex Coordinates and Unify Import Apex (ATT-6319)

## 1. Problem Domain & Forensic Root Cause
* **Ticket Key**: `ATT-6319` | **Target Release**: `V5.0.0` | **Epic**: `ATT-176` (Route Clusters) | **SP**: 2
* **Root Cause**: While `ATT-5899` introduced `findApexFromPoints(start, points, end)` for live recordings, two legacy paths remain broken:
  1. **Stored DB Records**: Existing point-to-point workouts ($d(\text{start}, \text{end}) \ge 150\text{ m}$) in `WorkoutSummaries.db` (`TABLE_EXTREMA_VALUES`) and clusters in `WorkoutClusters.db` still store apex coordinates near the end point ($P_{\text{apex}} \approx P_{\text{end}}$). Newly tracked workouts using the elliptical midpoint apex fail similarity scoring against legacy clusters ($s_3 \gg 1.0$), fragmenting clusters.
  2. **Legacy Import Engine**: In `LegacyImportEngine.kt` (lines 1323–1337), imported FIT/TCX/GPX/Strava activities compute apex by maximizing $d(\text{start}, pt)$ alone, degenerating to $P_{\text{apex}} \approx P_{\text{end}}$, and omit `workoutPolyline = points` in `suggestCluster`.
  3. **Data Mapper Regression**: `WorkoutDataMapper.resolveAuthoritativeApex` also optimizes $d(\text{start}, pt)$ without `endLatLng`, re-corrupting apex coordinates during reads.

## 2. Independent Peer Review & Verification (4-Augen-Prinzip)
* **Live Codebase Audit**:
  * `WorkoutClusterEngine.kt:303`: `findApexFromPoints(start, points, end)` correctly evaluates elliptical focal distance sum and collinear midpoint fallback.
  * `LegacyImportEngine.kt:1323`: Maximizes $d(\text{start}, pt)$, causing apex=end degeneracy for all imported A->B routes.
  * `WorkoutDataMapper.kt:972`: `resolveAuthoritativeApex` lacks `endLatLng` parameter and repeats legacy $d(\text{start}, pt)$ loop.
  * `WorkoutClusterRepository.kt:181`: SharedPreferences-guarded repair pattern (`SP_KEY_LAST_BOUNDS_REPAIR`) provides an established mechanism for self-healing migrations.

## 3. Chesterton's Fence & Cross-Caller Side-Effect Audit
* **Historical Requirement**: `REQ-SET-063` / `REQ-SET-097` (`ATT-5899`) established spatial fingerprinting and elliptical apex definition for A->B routes. Closed-loop routes ($d(\text{start}, \text{end}) < 150\text{ m}$) MUST preserve legacy distance-from-start behavior 100%.
* **Cross-Caller Impact (`Rule 68`)**:
  * `WorkoutDataMapper.kt`: `resolveAuthoritativeApex` callers updated to pass `endLatLng`.
  * `WorkoutClusterRepository.kt`: One-time self-healing pass during `refreshClusters()` heals existing DB records without blocking UI or corrupting closed-loop clusters.
  * `WorkoutClusterEngine.kt`: Cluster centroid recalculation for affected clusters updates `maxDispLat` and `maxDispLng` from updated member workouts.

## 4. Scope Grounding & Complexity Split Guardrail (REQ-PRO-029)
* **In-Scope**:
  1. One-time self-healing migration in `WorkoutClusterEngine` / `WorkoutClusterRepository` recalculating degenerated A->B apexes in `TABLE_EXTREMA_VALUES` and centroids in `WorkoutClusters.db`.
  2. Unifying `LegacyImportEngine.kt` to use `findApexFromPoints(start, points, end)` and forward `workoutPolyline = points` to `suggestCluster`.
  3. Updating `WorkoutDataMapper.resolveAuthoritativeApex` to use `findApexFromPoints`.
  4. Contract and migration test suite `WorkoutClusterApexMigrationTest.kt`.
* **Out-of-Scope**: Altering closed-loop apex logic, clustering distance thresholds, or schema DDL. Complexity is bounded at 2 SP.

## 5. Implementation Surfaces & Requirement Mapping
* **Requirement**: `REQ-DAT-039` (`req_data_migration_export.md`) | **Test Spec**: `TST-DAT-035` (`tst_data_migration_export.md`).

| Component | File Path | Scope of Modification |
| :--- | :--- | :--- |
| **Clustering Engine** | `database/WorkoutClusterEngine.kt` | Add `healLegacyPointToPointApexes()` to recompute A->B apexes and update cluster centroids |
| **Repository** | `database/WorkoutClusterRepository.kt` | Trigger self-healing migration pass during `refreshClusters()` under SharedPreferences flag |
| **Import Engine** | `migration/LegacyImportEngine.kt` | Use `findApexFromPoints`, set `LINE_DISTANCE_m` extrema, pass `workoutPolyline` to `suggestCluster` |
| **Data Mapper** | `ui/aftermath/WorkoutDataMapper.kt` | Update `resolveAuthoritativeApex` with `endLatLng` and `findApexFromPoints` |
| **Living Docs** | `docs/requirements/req_data_migration_export.md`<br/>`docs/tests/tst_data_migration_export.md` | Formalize `REQ-DAT-039` and `TST-DAT-035` |
