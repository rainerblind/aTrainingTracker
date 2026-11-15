# Stage 3: Implementation Plan - ATT-7757: Trim whitespace on RouteCluster names and self-heal existing entries

**Ticket**: [ATT-7757](https://rainerblind.atlassian.net/browse/ATT-7757)  
**Sub-task**: Pending creation via `submit-stage 3` (`[Impl-Plan]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.7` | **Branch**: `feature/ATT-7757`  
**Requirement Mapping**: `REQ-PER-041` | **Test Mapping**: `TST-PER-049`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-15  

---

## 1. Problem Description & Background
Normalize leading/trailing whitespace and reject blank-only `WorkoutCluster` names across all write, lookup, and formatting paths, and self-heal untrimmed SQLite `RouteClusters.name` entries in `WorkoutClusterRepository` startup/refresh passes with collision resolution and immediate propagation.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PER-041` (`REQ-PER-041.1`, `REQ-PER-041.2`)
* **Test Mapping**: `TST-PER-049` (`TST-PER-049.1`..`TST-PER-049.3`)

---

## 3. System Invariants & Preserved Behavior
1. Internal spaces inside multi-word names (`"Kurze Runde"`) MUST remain untouched.
2. `REQ-PER-040` (`ATT-7758`) `WorkoutData.workoutName` immutability and non-recursive `Route <-> Cluster` sync MUST remain intact.
3. No SQLite `DB_VERSION` bump; `ANT-Android-SDKs` stays at gitlink `160000`; `req_settings_profiles.md` and `tst_settings_profiles.md` untouched.

---

## 4. Proposed Architectural Changes
* **DB Layer (`WorkoutClusterDatabaseManager.kt`)**: Trim names and reject blank-only input in `insertCluster`, `updateCluster`, and `isNameTaken` (`getClusterByName` already trims).
* **Domain Engine (`WorkoutClusterEngine.kt`)**: Extract `Companion.resolveUniqueClusterName(baseName, existingNames)` to eliminate duplicate suffix logic; normalize `.trim()` and blank guards in `formatClusterWorkoutName`, `findUniqueClusterName`, `syncRouteNameChange`, `splitCluster`, `manuallyCreateCluster`, `createNewClusterFromWorkout`, and `learnFromWorkout`.
* **Repository Self-Healing (`WorkoutClusterRepository.kt`, `RoutesRepository.kt`)**: Trim/guard `updateCluster`, `updateClusterSync`, and `syncClusterNameChange`; add `healUntrimmedClusterNames` called by both `loadAllClustersLightweight()` and `refreshClusters()` (before early return) to persist healed unique names and notify `RoutesRepository`, `WorkoutRepository.onClusterRenamed`, and `clusterMutationEvents`.
* **ViewModels & Dialog (`WorkoutClustersViewModel.kt`, `EditWorkoutViewModel.kt`, `WorkoutClusterHeatmapScreen.kt`)**: Trim inputs and reject blank-only names in `updateClusterIdentity`, `addManualCluster`, `executeClusterSplit`, `createNewCluster`, and `EditWorkoutClusterIdentityDialog`.
* **UI Consistency**: Only trims `name.trim()` in `EditWorkoutClusterIdentityDialog.onSave`; no layout, widget, or styling changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (DB Layer)**: Update `WorkoutClusterDatabaseManager.kt` (`insertCluster`, `updateCluster`, `isNameTaken`).
2. **Step 2 (Engine)**: Add `resolveUniqueClusterName` helper and normalize all write/format/lookup methods in `WorkoutClusterEngine.kt`.
3. **Step 3 (Repositories)**: Add `healUntrimmedClusterNames` in `WorkoutClusterRepository.kt` (called in `loadAllClustersLightweight` & `refreshClusters`) + `.trim()` guards in `updateCluster`/`updateClusterSync` and `RoutesRepository.syncClusterNameChange`.
4. **Step 4 (ViewModels & Dialog)**: Update `WorkoutClustersViewModel.kt`, `EditWorkoutViewModel.kt`, and `WorkoutClusterHeatmapScreen.kt`.
5. **Step 5 (Unit Tests & Verification)**: Add `TST-PER-049.1`..`TST-PER-049.3` unit tests; run targeted `:app:testDebugUnitTest`, `:app:lintDebug`, `detekt` check on modified lines, and `verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests (`database.*`, `ui.clusters.*`, `ui.aftermath.editworkout.*`, `TranslationParityTest`), `:app:lintDebug`, modified-line `detekt` = 0 findings, and `verify_requirement_governance.py` in Stage 4, followed by full Stage 5 verification.
* **Rollback**: Isolated to `feature/ATT-7757`; zero schema changes allow clean revert.
