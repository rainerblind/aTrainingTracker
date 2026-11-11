# Stage 3: Implementation Plan - ATT-6686: Bipolar Product Apex & last_apex_repair_v2

**Ticket**: [ATT-6686](https://rainerblind.atlassian.net/browse/ATT-6686)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2` | **SP**: 2  
**Requirement Mapping**: `REQ-SET-097.1`, `REQ-DAT-039.1`, `REQ-DAT-039.2`  
**Test Mapping**: `TST-SET-097.1`, `TST-DAT-035.1`  
**Branch**: `sprint/2026-46.2` (`subagent-ATT-176---Route-Clusters-Pro-Agent-self-c3a66c20`)  
**Author**: AI Agent 1 (Senior ASPICE Architecture & Pro Verifier)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background
On point-to-point routes ($d(\text{start}, \text{end}) \ge 150\text{ m}$), the elliptical sum $d(\text{start}, P) + d(P, \text{end})$ favors longitudinal GPS overshoots near endpoints ($2\delta \gg 2h^2/D$) over lateral detour summits, causing apex degeneracy near destination coordinates (e.g. Pixel 10 `Bike2Work` #2063). Furthermore, `distToEnd < 50f` skip filtering prevented healing realistic stops. The system evaluates the bipolar product $d(\text{start}, P) \times d(P, \text{end})$ and executes self-healing migration v2.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-SET-097.1` (*Bipolar Product Point-to-Point Apex*), `REQ-DAT-039.1`/`039.2` (*Self-Healing Migration v2 & Preference Bump*)
* **Test Mapping**: `TST-SET-097.1` (*Bipolar Apex Unit Tests*), `TST-DAT-035.1` (*Migration v2 Contract Tests*)

---

## 3. System Invariants & Preserved Behavior
1. **Closed-Loop Route Invariance**: Closed loops ($d(\text{start}, \text{end}) < 150\text{ m}$) remain 100% untouched using distance-from-start.
2. **Thread Safety & Dispatcher Affinity**: Mutex synchronization under `clusterLock`; background migration on `Dispatchers.IO`.
3. **No Schema Alterations**: SQLite DDL remains unchanged; zero impact on non-target database tables.
4. **Human Decision Gate**: Terminal transition to `Final Review (Human)` assigned to `human` without advancing to `Erledigt`.

---

## 4. Proposed Architectural Changes
* **Domain Engine (`WorkoutClusterEngine.kt`)**:
  - In `findApexFromPoints`: Replace elliptical sum with single-pass maximization of $d(\text{start}, P) \times d(P, \text{end})$ for point-to-point routes. Delete dead-code `cumDistances` collinear fallback.
  - In `healLegacyPointToPointApexes`: Remove `distToEnd < 50f` skip filter; evaluate `trueApex` for all point-to-point workouts and update if `recordedApex == null || distanceBetween(recordedApex, trueApex) > 25f`.
* **Repository Layer (`WorkoutClusterRepository.kt`)**:
  - Bump preference constant: `SP_KEY_LAST_APEX_REPAIR = "last_apex_repair_v2"`.
  - Guard condition: `if (lastApexRepair < 2 && currentClusters.isNotEmpty())`, executing repair and setting `prefs.edit().putInt(SP_KEY_LAST_APEX_REPAIR, 2).apply()`.
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `findApexFromPoints()` with Bipolar Product (`REQ-SET-097.1`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt`
* **Changes**: Replace elliptical sum and dead-code fallback with `maxBipolarProduct` loop maximizing $d(\text{start}, P) \times d(P, \text{end})$.

### Step 2: Remove `distToEnd < 50f` Skip Filter in `healLegacyPointToPointApexes()` (`REQ-DAT-039.1`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt`
* **Changes**: Remove `distToEnd < 50f` condition; compute `trueApex` for all candidates; update if `distanceBetween(recordedApex, trueApex) > 25f`.

### Step 3: Bump SharedPreferences Key to `last_apex_repair_v2` (`REQ-DAT-039.2`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterRepository.kt`
* **Changes**: Update `SP_KEY_LAST_APEX_REPAIR = "last_apex_repair_v2"`, condition `< 2`, and value `2`.

### Step 4: Unit & Migration Test Suite Augmentation (`TST-SET-097.1`, `TST-DAT-035.1`)
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterPointToPointApexTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterApexMigrationTest.kt`
* **Changes**: Assert overshoot suppression, collinear midpoint selection, and migration v2 healing.

### Step 5: Verification Execution
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.WorkoutCluster*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit and migration tests under POSIX lock `/tmp/att_build_merge.lock`, verifying 100% pass rate.
* **Rollback**: Branch isolation allows instantaneous `git checkout` revert without residual side-effects.
