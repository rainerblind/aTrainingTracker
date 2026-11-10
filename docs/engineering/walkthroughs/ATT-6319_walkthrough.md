# Stage 5: Walkthrough & Verification - ATT-6319: Point-to-Point Apex Migration & Import Unification

**Ticket**: [ATT-6319](https://atrainingtracker.atlassian.net/browse/ATT-6319)  
**Parent Epic**: [ATT-176](https://atrainingtracker.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*) | **Story Points**: 3 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6319`  
**Requirement Mapping**: `REQ-DAT-039` (`REQ-DAT-039.1`–`REQ-DAT-039.5`) | **Test Mapping**: `TST-DAT-035` (`TST-DAT-035.1`–`TST-DAT-035.4`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Executive Summary & Verification Overview
Resolved the issue where existing point-to-point (A->B) workouts ($d(\text{start}, \text{end}) \ge 150\text{ m}$) stored degenerated apex coordinates collapsed onto the destination point ($P_{\text{apex}} \approx P_{\text{end}}$), causing newly recorded workouts to fail similarity scoring ($s_3 \gg 1.0$) and fragment clusters. Furthermore, unified apex derivation across import and data mapping pipelines:
* **Self-Healing Database Apex Migration (`REQ-DAT-039.1`)**: Implemented `WorkoutClusterEngine.healLegacyPointToPointApexes(context: Context): Int`. Scans point-to-point routes with degenerate apex ($d(P_{\text{apex}}, P_{\text{end}}) < 50\text{ m}$), recomputes non-degenerate elliptical apex coordinates via `findApexFromPoints(start, points, end)`, persists updated `MAX_LINE_DISTANCE_LAT`, `LNG`, and `LINE_DISTANCE_m` in `TABLE_EXTREMA_VALUES`, and recalculates centroid `maxDispLat`/`maxDispLng` for affected clusters. Complies with Rule 20/36 via DTO cursor extractors.
* **SharedPreferences-Guarded Repository Trigger (`REQ-DAT-039.2`)**: Wired `healLegacyPointToPointApexes` into `WorkoutClusterRepository.refreshClusters()` asynchronously on `Dispatchers.IO`, guarded by `SP_KEY_LAST_APEX_REPAIR = "last_apex_repair_v1"` for exactly-once execution.
* **Unified Import Pipeline Apex Derivation (`REQ-DAT-039.3`)**: In `LegacyImportEngine.kt`, replaced legacy distance-from-start loop with `findApexFromPoints(start, points, end)`, stored corrected extrema, and forwarded `workoutPolyline = points` to `suggestCluster()`.
* **Symmetrical Data Mapper Parity (`REQ-DAT-039.4`)**: In `WorkoutDataMapper.kt`, updated `resolveAuthoritativeApex` to accept `endLatLng: LatLng? = null` and delegate to `findApexFromPoints`, guaranteeing runtime read/display parity and database healing.
* **System Invariants Preserved (`REQ-DAT-039.5`)**: Closed-loop workouts ($d(\text{start}, \text{end}) < 150\text{ m}$) preserve distance-from-start apex calculation 100%; no SQLite DDL schema changes (`DB_VERSION` unchanged).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-DAT-039.1` | `TST-DAT-035.1` | `WorkoutClusterApexMigrationTest.testHealLegacyPointToPointApexes_recomputesDegenerateApexAndUpdatesClusterCentroid` | **PASSED** | `Verified` |
| `REQ-DAT-039.2` | `TST-DAT-035.1` | `WorkoutClusterApexMigrationTest.testWorkoutClusterRepository_triggersApexMigrationOnce` | **PASSED** | `Verified` |
| `REQ-DAT-039.3` | `TST-DAT-035.2` | `WorkoutClusterApexMigrationTest.testLegacyImportEngine_usesUnifiedApexAndForwardsPolyline` | **PASSED** | `Verified` |
| `REQ-DAT-039.4` | `TST-DAT-035.3` | `WorkoutClusterApexMigrationTest.testWorkoutDataMapper_resolveAuthoritativeApex_pointToPointParity` | **PASSED** | `Verified` |
| `REQ-DAT-039.5` | `TST-DAT-035.1`, `TST-DAT-035.4` | `testHealLegacyPointToPointApexes_preservesClosedLoopRoute` & Tier 1 regression suite | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope (Rule 60)
* **Executed Scope**:
  * `--tests "com.atrainingtracker.trainingtracker.database.WorkoutCluster*"`
  * `--tests "com.atrainingtracker.trainingtracker.migration.*"`
  * `--tests "com.atrainingtracker.trainingtracker.ui.aftermath.*"`
  * `--tests "com.atrainingtracker.banalservice.database.*"`
  * `--tests "com.atrainingtracker.trainingtracker.build.*"`
* **Result**: `BUILD SUCCESSFUL in 3m 23s` (100% PASS across 913 targeted unit tests).
* **Governance Check**: `python3 tools/verify_requirement_governance.py` -> `PASS: Living documentation master tables match all 9 domain shards with 100% parity.`
* **Device Verification**: Physical device check via `adb devices`: no physical device connected; software emulator strictly avoided per Rule 79.

---

## 4. Invariants & Preserved Behavior
1. **Closed-Loop Invariant (`REQ-DAT-039.5`)**: Closed-loop workouts ($d < 150\text{ m}$) preserve legacy distance-from-start apex calculation 100%.
2. **Database Integrity**: Zero SQLite DDL schema migrations; updates strictly limited to `TABLE_EXTREMA_VALUES` and `WorkoutClusters.db`.
3. **Startup Performance**: Migration runs on `Dispatchers.IO` and skips immediately when `last_apex_repair_v1 >= 1`.
4. **Clean Code Hygiene**: Zero manual cursor extractions in `WorkoutClusterEngine.kt` (Rule 20/36).
