# Stage 3: Implementation Plan - ATT-6319: Point-to-Point Apex Migration & Import Unification

**Ticket**: [ATT-6319](https://atrainingtracker.atlassian.net/browse/ATT-6319) | **Epic**: [ATT-176](https://atrainingtracker.atlassian.net/browse/ATT-176) | **Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.1` | **Branch**: `feature/ATT-6319` | **Req/Test**: `REQ-DAT-039` / `TST-DAT-035`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Architecture Rationale
Pre-existing point-to-point (A->B) workouts ($d(\text{start}, \text{end}) \ge 150\text{ m}$) store degenerated apex coordinates ($P_{\text{apex}} \approx P_{\text{end}}$), causing new recordings with elliptical midpoint apexes to fail similarity scoring ($s_3 \gg 1.0$) and fragment clusters. Furthermore, `LegacyImportEngine.kt` and `WorkoutDataMapper.kt` still use legacy distance-from-start maximization. We introduce a self-healing migration in `WorkoutClusterEngine` triggered once via `WorkoutClusterRepository`, and unify apex calculation in `LegacyImportEngine` and `WorkoutDataMapper`.

---

## 2. System Invariants & Preserved Behavior
1. **Closed-Loop Invariant (`REQ-DAT-039.5`)**: Closed-loop workouts ($d(\text{start}, \text{end}) < 150\text{ m}$) preserve legacy distance-from-start apex calculation 100%.
2. **Database Integrity**: No SQLite DDL schema changes (`DB_VERSION` unchanged); updates restricted to `TABLE_EXTREMA_VALUES` and `WorkoutClusters.db`.
3. **Execution Guard**: SharedPreferences flag (`SP_KEY_LAST_APEX_REPAIR = "last_apex_repair_v1"`) ensures migration runs strictly once on `Dispatchers.IO` without startup delay.

---

## 3. Step-by-Step Implementation Sequence

### Step 1: Self-Healing Apex Migration in WorkoutClusterEngine (`REQ-DAT-039.1`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt`
* **Changes**: Add `healLegacyPointToPointApexes(context: Context): Int`. For A->B workouts ($d \ge 150\text{ m}$) with degenerated apex ($d(P_{\text{apex}}, P_{\text{end}}) < 50\text{ m}$), recompute `trueApex = findApexFromPoints(start, points, end)` and update `TABLE_EXTREMA_VALUES`. Recalculate `maxDispLat`/`maxDispLng` centroids for all affected clusters.

### Step 2: SharedPreferences-Guarded Trigger in Repository (`REQ-DAT-039.2`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterRepository.kt`
* **Changes**: In `refreshClusters()`, check `prefs.getInt(SP_KEY_LAST_APEX_REPAIR, 0) < 1`. If unmigrated, execute `healLegacyPointToPointApexes(context)` and set flag to `1`.

### Step 3: Unify Apex Calculation in LegacyImportEngine (`REQ-DAT-039.3`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt`
* **Changes**: In lines 1323–1337, replace distance-from-start loop with `findApexFromPoints(start, points, end)`; update `LINE_DISTANCE_m` extrema; pass `workoutPolyline = points` to `suggestCluster`.

### Step 4: Symmetrical Apex Resolution in WorkoutDataMapper (`REQ-DAT-039.4`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMapper.kt`
* **Changes**: Pass `endLatLng` to `resolveAuthoritativeApex`; delegate to `findApexFromPoints(startLatLng, points, endLatLng)` to guarantee runtime read/display parity.

### Step 5: Contract & Migration Test Verification Suite (`TST-DAT-035.1` – `TST-DAT-035.3`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterApexMigrationTest.kt`
* **Changes**: Add tests verifying: (1) `healLegacyPointToPointApexes()` heals A->B apexes and cluster centroids while preserving closed loops; (2) `LegacyImportEngine` source compliance; (3) `WorkoutDataMapper.resolveAuthoritativeApex` parity.

### Step 6: Targeted Unit & Governance Test Execution (`TST-DAT-035.4`)
* **Commands**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.WorkoutCluster*" --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
  * `python3 tools/verify_requirement_governance.py`
