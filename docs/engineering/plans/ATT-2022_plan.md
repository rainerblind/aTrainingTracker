# Stage 3: Implementation Plan - ATT-2022: [Import/Clusters] Map Camera Does Not Re-Align or Update Zoom for Sequential Cluster Naming Dialogs During Import

**Ticket**: [ATT-2022](https://rainerblind.atlassian.net/browse/ATT-2022)  
**Sub-task**: [ATT-2072](https://rainerblind.atlassian.net/browse/ATT-2072) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.12`  
**Requirement Mapping**: `REQ-MAP-022` (*Google Maps CameraUpdateFactory Initialization Resilience and Lifecycle Guarding in Import Route Preview*)  
**Test Mapping**: `TST-MAP-024`  
**Branch**: `feature/ATT-2022`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Description & Background

When athletes import bulk activities or TCX backup archives containing multiple recurring route candidates, the application presents each candidate sequentially using `ClusterNamingDialog` in `ImportBackupTabsScreen.kt`. While text metadata (date, sport, distance) updates as users advance through the queue, the embedded Google Maps camera remains frozen at the spatial position and zoom bounds of the first candidate.

Forensic analysis revealed that `ClusterNamingDialog` remains mounted across queue items, recomposing with the new `state: ClusterInteraction`. In `ATT-1579`, `isMapLoaded` was declared as `var isMapLoaded by remember(state) { mutableStateOf(false) }`. Because the native GoogleMap does not remount, `onMapLoaded` fires only once on initial layout and never fires again on recomposition. Consequently, `isMapLoaded` was reset to `false` and locked permanently, preventing `LaunchedEffect(bounds, isMapLoaded)` from animating or re-centering the camera for subsequent clusters.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MAP-022` (*Google Maps CameraUpdateFactory Initialization Resilience and Lifecycle Guarding in Import Route Preview*)
  - Clause 1: `isMapLoaded` tracked across recompositions via `var isMapLoaded by remember { mutableStateOf(false) }`.
  - Clause 2: Camera animation triggered on sequential queue transitions via `LaunchedEffect(state, bounds, isMapLoaded)`.
  - Clause 3: Bounds calculation includes polyline points and marker coordinates (`state.start`, `state.end`, `state.apex`).
  - Clause 4: Defensive `try-catch` with static center fallback preserved.
* **Test Mapping**: `TST-MAP-024`
  - `TST-MAP-024.1`: Map lifecycle synchronization & sequential queue recomposition unit tests (`ImportBackupMapResilienceTest.kt`).
  - `TST-MAP-024.2`: Defensive exception handling & static centering fallback tests (`ImportBackupMapResilienceTest.kt`).
  - `TST-MAP-024.3`: Comprehensive bounds envelope calculation (track + markers) (`ImportBackupMapResilienceTest.kt`).
  - `TST-MAP-024.4`: 9-language localization audit (`TranslationParityTest.kt`).
  - `TST-MAP-024.5`: Clean-room full suite regression (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Cold Start Crash Resilience (ATT-1579)**: Camera animations MUST remain strictly deferred until `isMapLoaded == true`.
2. **Defensive Layout Fallback**: Any exception thrown by `CameraUpdateFactory.newLatLngBounds` MUST be caught and fall back to static camera centering without crashing.
3. **Queue Sequencing & State Reset**: Dialog input fields (`name`, `selectedCluster`, `showSelectionDialog`) remain cleanly reset per candidate item (`remember(state)`).
4. **Subtask Direct Completion**: Subtasks transition directly to `Erledigt` upon passing Gate audit via transition `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent `ATT-2022` is strictly reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: `ClusterNamingDialog` in `ImportBackupTabsScreen.kt` (UI Layer)
* **Lifecycle Uncoupling**:
  - Replace:
    ```kotlin
    var isMapLoaded by remember(state) { mutableStateOf(false) }
    ```
    with:
    ```kotlin
    var isMapLoaded by remember { mutableStateOf(false) }
    ```
    ensuring the map readiness flag survives queue item transitions.
* **Comprehensive Bounds Envelope Calculation**:
  - Update `bounds` derivation to include all polyline coordinates and marker locations:
    ```kotlin
    val decodedPoints = remember(state.polyline) { PolyUtil.decode(state.polyline) }
    val bounds = remember(decodedPoints, state.start, state.end, state.apex) {
        val b = LatLngBounds.builder()
        var hasPoints = false
        decodedPoints.forEach {
            b.include(it)
            hasPoints = true
        }
        state.start?.let { b.include(it); hasPoints = true }
        state.end?.let { b.include(it); hasPoints = true }
        state.apex?.let { b.include(it); hasPoints = true }
        if (!hasPoints) null else {
            try {
                b.build()
            } catch (e: Exception) {
                null
            }
        }
    }
    ```
* **Sequential Queue Re-Framing Trigger**:
  - Key `LaunchedEffect` to `state, bounds, isMapLoaded`:
    ```kotlin
    LaunchedEffect(state, bounds, isMapLoaded) {
        if (isMapLoaded && bounds != null) {
            try {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 50))
            } catch (e: Exception) {
                Log.w("ImportBackupTabsScreen", "CameraUpdateFactory animation failed, falling back to static bounds center: ${e.message}")
                cameraPositionState.position = CameraPosition.fromLatLngZoom(bounds.center, 12f)
            }
        }
    }
    ```

### Component 2: `ImportBackupMapResilienceTest.kt` (Verification Layer)
* **Contract Test Modernization**:
  - Update `testImportBackupTabsScreenSourceCodeContract` to assert:
    - `var isMapLoaded by remember { mutableStateOf(false) }`
    - `LaunchedEffect(state, bounds, isMapLoaded)`
* **Sequential Queue Recomposition Test**:
  - Add `testSequentialQueueRecompositionCameraUpdate` verifying that when advancing between two distinct queue items:
    - Initial item 1 animates camera once `isMapLoaded` becomes `true`.
    - Advancing to item 2 maintains `isMapLoaded == true` without resetting to `false`.
    - Camera update evaluates and animates using item 2 bounds.
* **Marker Envelope Ingestion Test**:
  - Add assertions verifying that track bounds builder encompasses start, end, and apex coordinates.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Check Gate Verification
* Command: `python3 tools/jira_util.py check-gate ATT-2072` (confirm code modification gate is open).

### Step 2: Update Source Code in `ImportBackupTabsScreen.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt`
* Change: Decouple `isMapLoaded` from `state`, incorporate markers in `bounds`, and add `state` to `LaunchedEffect` trigger keys.

### Step 3: Update and Extend Unit Tests in `ImportBackupMapResilienceTest.kt`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/ImportBackupMapResilienceTest.kt`
* Changes: Update contract assertions and add `testSequentialQueueRecompositionCameraUpdate` and marker bounds coverage.

### Step 4: Execute Targeted Unit Tests
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.ImportBackupMapResilienceTest"`

### Step 5: Commit Stage 4 Construction Changes
* Git commit with descriptive message referencing `ATT-2022`.

---

## 6. Verification & Rollback Plan

* **Targeted Verification**:
  - `ImportBackupMapResilienceTest` (unit & contract suite).
  - `TranslationParityTest` (9-language localization audit).
* **Clean-Room Regression**:
  - `./gradlew testDebugUnitTest` in Stage 5.
* **Rollback Safety**:
  - Feature branch `feature/ATT-2022` is fully isolated. Any failure allows clean git revert to `sprint/2026-40.12` base.
