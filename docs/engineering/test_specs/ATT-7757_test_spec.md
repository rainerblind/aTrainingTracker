# Stage 2: Requirement & Test Specification - ATT-7757: Trim whitespace on RouteCluster names and self-heal existing entries

**Ticket**: [ATT-7757](https://rainerblind.atlassian.net/browse/ATT-7757)  
**Sub-task**: Pending creation via `submit-stage 2` (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.7` | **Branch**: `feature/ATT-7757`  
**Requirement Mapping**: `REQ-PER-041` | **Test Spec ID**: `TST-PER-049`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-15  

---

## 1. Requirement Specification (`REQ-PER-041`)

### 1.1 Problem Statement, Attributes & Rationale
* **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit
* **Rationale**: Android soft keyboards frequently append trailing whitespace after autocomplete words (`"Kurze Runde "`), causing double spaces in `formatClusterWorkoutName` (`"Kurze Runde  #2"`), false uniqueness misses in `isNameTaken`/`findUniqueClusterName`, and stale legacy SQLite rows.

### 1.2 Functional & Architectural Requirements
1. **Write, Lookup & Formatting Normalization (`REQ-PER-041.1`)**:
   - The system SHALL trim leading/trailing whitespace and reject blank-only cluster names across `updateClusterIdentity`, `EditWorkoutClusterIdentityDialog`, `addManualCluster`, `executeClusterSplit`, `EditWorkoutViewModel.createNewCluster`, `WorkoutClusterRepository.updateCluster`/`updateClusterSync`, `RoutesRepository.syncClusterNameChange`, `WorkoutClusterEngine` (`manuallyCreateCluster`, `syncRouteNameChange`, `splitCluster`, `createNewClusterFromWorkout`, `findUniqueClusterName`, `formatClusterWorkoutName`), and `WorkoutClusterDatabaseManager` (`insertCluster`, `updateCluster`, `isNameTaken`, `getClusterByName`).
2. **Startup/Refresh Self-Healing (`REQ-PER-041.2`)**:
   - `WorkoutClusterRepository.loadAllClustersLightweight()` and `refreshClusters()` SHALL detect untrimmed persisted `RouteClusters.name` entries, normalize them in-place using `findUniqueClusterName` collision resolution (falling back to `"Cluster <id>"` if blank), update SQLite, and propagate healed names to `WorkoutRepository`, linked `Routes`, and `clusterMutationEvents`.

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* *Given* `"  Kurze Runde  "` on create, rename, split, route-sync, or `isNameTaken`, *when* processed, *then* `"Kurze Runde"` is stored/matched and `formatClusterWorkoutName(..., 2)` outputs `"Kurze Runde #2"`.
* *Given* blank-only `"   "` on create/rename/insert/update, *when* submitted, *then* the write is rejected (`-1L` or no-op).
* *Given* existing SQLite rows `"Kurze Runde"` and `"Kurze Runde "`, *when* `loadAllClustersLightweight()` or `refreshClusters()` executes, *then* the untrimmed row heals to `"Kurze Runde var 2"` and propagates to `WorkoutData.clusterName` and linked routes.
* **Invariants**: Internal multi-word spacing MUST remain untouched; `REQ-PER-040` non-recursive sync MUST NOT regress; zero DB schema version changes.

---

## 2. Test Specification (`TST-PER-049`)

### Test Case 1: Write, Lookup & Auto-Naming Whitespace Normalization (`TST-PER-049.1`)
* **Target Files**: `WorkoutClusterDatabaseManagerTest.kt`, `WorkoutClusterEngineAutoNamingTest.kt` / `WorkoutClusterNameTrimTest.kt`, `WorkoutClustersViewModelTest.kt`, `EditWorkoutViewModelTest.kt`
* **Scope**: Unit Test
* **Action & Expected Result**: Pass `"  Kurze Runde  "` and `"   "` to `insertCluster`, `updateCluster`, `isNameTaken`, `manuallyCreateCluster`, `syncRouteNameChange`, `splitCluster`, `formatClusterWorkoutName`, `updateClusterIdentity`, `addManualCluster`, `executeClusterSplit`, and `createNewCluster`; assert trimmed values persist, `"Kurze Runde "` matches `"Kurze Runde"` in `isNameTaken`/`findUniqueClusterName`, blank-only writes are rejected, and `"Kurze Runde #2"` has single spacing.

### Test Case 2: Startup & Refresh Self-Healing & Collision Propagation (`TST-PER-049.2`)
* **Target File**: `WorkoutClusterRepositoryTest.kt`
* **Scope**: Unit Test
* **Action & Expected Result**: Seed `clusterDb.getAllClusters()` with `"  Alte Runde  "` and collision pair `("Kurze Runde", "Kurze Runde ")`; invoke `loadAllClustersLightweight()` and `refreshClusters()`; assert `clusterDb.updateCluster` receives `"Alte Runde"` and `"Kurze Runde var 2"`, and `WorkoutRepository.onClusterRenamed`, `RoutesRepository.syncClusterNameChange`, and `clusterMutationEvents` emit the healed names.

### Test Case 3: 9-Locale & Regression Check (`TST-PER-049.3`)
* **Scope**: Localization Parity & Tier 1 Unit Regression (No new user-facing string resources added — N/A for new keys)
* **Command**: `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.editworkout.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PER-049.1` | Unit | Write/lookup/format paths | `REQ-PER-041.1` | Specified |
| `TST-PER-049.2` | Unit | `loadAllClustersLightweight`, `refreshClusters` | `REQ-PER-041.2` | Specified |
| `TST-PER-049.3` | Parity/Regression | `TranslationParityTest` & targeted suites | `REQ-PER-041`, `REQ-PRO-001` | Specified |
