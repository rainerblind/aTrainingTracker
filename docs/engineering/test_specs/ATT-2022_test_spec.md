# Stage 2: Requirement & Test Specification - ATT-2022: [Import/Clusters] Map Camera Does Not Re-Align or Update Zoom for Sequential Cluster Naming Dialogs During Import

**Ticket**: [ATT-2022](https://rainerblind.atlassian.net/browse/ATT-2022)  
**Sub-task**: [ATT-2071](https://rainerblind.atlassian.net/browse/ATT-2071) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.12`  
**Requirement Mapping**: `REQ-MAP-022` (*Google Maps CameraUpdateFactory Initialization Resilience and Lifecycle Guarding in Import Route Preview*)  
**Test Spec ID**: `TST-MAP-024`  
**Branch**: `feature/ATT-2022`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Formal Requirement Specification

### REQ-MAP-022: Google Maps CameraUpdateFactory Initialization Resilience and Lifecycle Guarding in Import Route Preview

The system SHALL guard all camera animation and update calls against premature execution before the Google Maps native renderer and layout view are fully initialized, while guaranteeing that the camera actively re-aligns, centers, and fits bounds for every sequential cluster candidate presented in the import naming queue (ATT-1579, ATT-2022):

1. **Map Lifecycle Synchronization (`ClusterNamingDialog` in `ImportBackupTabsScreen.kt`)**:
   - In `ClusterNamingDialog`, the system SHALL track map readiness across dialog recompositions via:
     ```kotlin
     var isMapLoaded by remember { mutableStateOf(false) }
     ```
     strictly retaining `isMapLoaded` across sequential cluster items in the queue once the native GoogleMap has loaded.
   - The `GoogleMap` composable SHALL register:
     ```kotlin
     onMapLoaded = { isMapLoaded = true }
     ```

2. **Sequential Queue Re-Framing & Guarded Camera Animation**:
   - The camera animation effect SHALL bind to explicit keys including the cluster `state`:
     ```kotlin
     LaunchedEffect(state, bounds, isMapLoaded)
     ```
   - The system SHALL only invoke `cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 50))` when `isMapLoaded == true && bounds != null`.
   - The camera update execution SHALL be enclosed in a defensive `try-catch` block catching `Exception` (`NullPointerException`, `IllegalStateException`).
   - **Explicit Fallback**: If `CameraUpdateFactory.newLatLngBounds` throws an exception, the system SHALL catch the exception without crashing and fall back to static camera centering:
     ```kotlin
     cameraPositionState.position = CameraPosition.fromLatLngZoom(bounds.center, 12f)
     ```

3. **Comprehensive Envelope Calculation (Track & Markers)**:
   - In `ClusterNamingDialog`, `bounds` calculation SHALL encompass all decoded polyline coordinates AND include `state.start`, `state.end`, and `state.apex` (when non-null), ensuring route marker icons are never clipped outside the visible viewport.

4. **Initial Baseline Framing**:
   - `rememberCameraPositionState` SHALL initialize with:
     ```kotlin
     position = CameraPosition.fromLatLngZoom(bounds?.center ?: LatLng(0.0, 0.0), 12f)
     ```
     guaranteeing non-crashing baseline framing prior to map load.

5. **Invariants**:
   - Cold start crash protection (`ATT-1579`) MUST NOT be compromised.
   - Track polyline geometry, marker positioning, cluster scoring engine, and queue sequencing MUST NOT be broken.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-MAP-022` (*Google Maps CameraUpdateFactory Initialization Resilience and Lifecycle Guarding in Import Route Preview*).
   - Target Release: `V4.9.38`.
   - Parent Epic: `ATT-111` (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*) / `ATT-235` (*No crashs*).

2. **Historical Origin & Commit Trace**:
   - Commit `5c5d353dc808` (`ATT-294`): Initial implementation of legacy recovery cluster naming dialog with route preview map.
   - Commit `e99cf4c34ebc` (`ATT-304`, `ATT-305`, `ATT-306`): Added `bounds?.let` null safety, but retained eager animation directly in `LaunchedEffect(bounds)` without map lifecycle synchronization.
   - Commit `2aab3ef6` (`ATT-1579`, Sprint 2026-40.4): Introduced `REQ-MAP-022` and `TST-MAP-024` to prevent fatal cold-start NPEs (`CameraUpdateFactory` uninitialized) by guarding camera animation behind `isMapLoaded`.

3. **Root Reason for Existing Formulation**:
   - In `ATT-1579`, the author declared `var isMapLoaded by remember(state) { mutableStateOf(false) }`, intending for the readiness flag to reset cleanly per queued item under the assumption that each queue item mounts a new dialog and a new map surface.
   - **Forensic Defect Discovery (ATT-2022)**: In Jetpack Compose, advancing to the next item in the import queue (`queueCount > 1`) does NOT unmount or recreate `ClusterNamingDialog`; the dialog simply recomposes with the updated `state: ClusterInteraction`. Because `GoogleMap` remains mounted, the Google Maps SDK `onMapLoaded` callback fires only ONCE on initial layout and never fires again upon composable recomposition. As a result, `remember(state)` reset `isMapLoaded` to `false` permanently for all subsequent items, preventing `LaunchedEffect(bounds, isMapLoaded)` from ever executing camera animations for items 2, 3, etc.

4. **Preservation of Core Invariants**:
   - The cold-start crash prevention from `ATT-1579` is completely preserved: camera animation remains strictly deferred until `isMapLoaded == true`.
   - By changing `remember(state)` to `remember`, `isMapLoaded` remains `true` once the map view is initially loaded.
   - By keying `LaunchedEffect(state, bounds, isMapLoaded)`, the camera animates to the new cluster bounds whenever `state` transitions to the next item.
   - Defensive `try-catch` and static center fallback are fully preserved.

---

## 3. Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Cold Start Deferred Execution - ATT-1579 Invariant)**:
  - *Given* an athlete launching the Import/Backup preview immediately upon cold start before Google Maps native layout initializes,
  - *When* `ClusterNamingDialog` is composed,
  - *Then* the application SHALL NOT throw `NullPointerException`, and camera animation SHALL be deferred until `isMapLoaded` becomes `true`.

* **Criterion 2 (First Cluster Animation)**:
  - *Given* an active `ClusterNamingDialog` for cluster item 1 in the queue,
  - *When* `isMapLoaded` transitions to `true` and `bounds` is non-null,
  - *Then* the camera SHALL smoothly animate to frame the bounds of cluster 1 with 50dp padding.

* **Criterion 3 (Sequential Cluster Camera Re-Framing - ATT-2022 Core Fix)**:
  - *Given* an import session with multiple cluster candidates in the queue (`queueCount >= 2`),
  - *When* the athlete assigns or dismisses cluster 1 and the dialog advances to cluster 2,
  - *Then* `isMapLoaded` SHALL remain `true`, and the camera SHALL immediately re-align, center, and animate to fit the new bounds of cluster 2 with 50dp padding.

* **Criterion 4 (Marker Pin Ingestion in Bounds)**:
  - *Given* a cluster candidate with polyline track points, start pin, end pin, and apex pin,
  - *When* `bounds` is calculated,
  - *Then* the bounds envelope SHALL encompass all decoded polyline points as well as `state.start`, `state.end`, and `state.apex`.

* **Criterion 5 (Defensive Exception Fallback)**:
  - *Given* a runtime layout failure where `CameraUpdateFactory.newLatLngBounds` throws an exception,
  - *When* the exception is caught,
  - *Then* the dialog SHALL NOT crash, and camera position SHALL fall back to `CameraPosition.fromLatLngZoom(bounds.center, 12f)`.

---

## 4. Test Case Specification

### TST-MAP-024.1: Map Readiness & Sequential Queue Re-Framing Tests (`ImportBackupMapResilienceTest.kt`)
* **Scope**: Unit / Compose State Verification
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/ImportBackupMapResilienceTest.kt`
* **Preconditions**: Two distinct cluster states (`cluster1` in Munich, `cluster2` in Berlin).
* **Actions & Assertions**:
  - Verify that when `isMapLoaded == false`, camera animation is deferred.
  - Verify that when `isMapLoaded == true`, camera bounds animation executes for `cluster1`.
  - Verify that upon advancing from `cluster1` to `cluster2`, `isMapLoaded` does NOT reset to `false` (retaining `remember` semantic).
  - Verify that camera update is triggered for `cluster2` using `cluster2` bounds.

### TST-MAP-024.2: Defensive Exception Handling & Fallback Unit Tests
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/ImportBackupMapResilienceTest.kt`
* **Test Objective**: Verify exception tolerance and deterministic fallback:
  - Simulate `NullPointerException` (uninitialized `CameraUpdateFactory`) -> verify exception is caught and camera falls back to `CameraPosition.fromLatLngZoom(bounds.center, 12f)` without crashing.
  - Simulate `IllegalStateException` (zero layout dimensions) -> verify exception is caught and camera falls back to static centering without crashing.

### TST-MAP-024.3: Comprehensive Bounds Envelope Calculation (Track + Markers)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/ImportBackupMapResilienceTest.kt`
* **Test Objective**: Verify polyline and marker coordinate envelope calculation:
  - Empty polyline -> `bounds` is `null` without throwing exceptions.
  - Valid multi-point polyline with start/end/apex markers -> `bounds` encompasses all track points and marker locations.

### TST-MAP-024.4: 9-Language Localization & Specifier Audit
* **Scope**: Localization Parity Test
* **Target**: Verify `ClusterNamingDialog` strings across all 9 supported languages (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches.

### TST-MAP-024.5: Clean-Room Full Suite Regression Execution
* **Command**: `./gradlew testDebugUnitTest`
* **Pass Criteria**: 100% pass rate across the full test suite with 0 failures and 0 regressions.

---

## 5. Traceability Matrix

| Test Case | Scope | Method Under Test / Target | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-MAP-024.1` | Unit | `ClusterNamingDialog` map readiness & sequential queue framing | `REQ-MAP-022` | Specified |
| `TST-MAP-024.2` | Unit | Defensive exception handling & static centering fallback | `REQ-MAP-022` | Specified |
| `TST-MAP-024.3` | Unit | Polyline & marker envelope calculation | `REQ-MAP-022` | Specified |
| `TST-MAP-024.4` | Localization | `ClusterNamingDialog` strings across 9 locales | `REQ-MAP-022`, `REQ-UI-106` | Specified |
| `TST-MAP-024.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
