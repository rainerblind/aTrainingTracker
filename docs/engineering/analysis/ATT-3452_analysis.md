# Stage 1: Problem Domain & Root Cause Analysis - ATT-3452

**Ticket**: [ATT-3452](https://atrainingtracker.atlassian.net/browse/ATT-3452)  
**Summary**: Remove cluster tuning from favorite routes and add recalculate action with confirmation to Advanced Settings  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `Sprint 2026-42.2`  
**Active Sprint**: `Sprint 2026-42.2`  
**Author**: AI Agent 1 (Analyst)  
**Date**: 2026-10-13 (Revised)  

---

## 1. Problem Domain & Background

In Sprint 2026-42.1 ([ATT-3355](https://atrainingtracker.atlassian.net/browse/ATT-3355)), the algorithmic configuration for 3D route clustering was relocated to Advanced Settings (`ClusterTuningSection.kt` under Section 7: *Routen- & Workout-Clustering*), and the shared UI controls were extracted into a reusable composable (`ClusterTuningContent.kt`) in accordance with Single Source of Truth and DRY principles (`REQ-SET-075`, Rule 36).

However, during that initial migration:
1. The standalone tuning screen entry point (`IconButton(onClick = onTuneClick)`) was retained in the header row of the favorite routes screen (`WorkoutClustersTabsScreen.kt`), which navigated to `ClusterTuningScreen.kt`.
2. The destructive "Recalculate All Clusters" button (`recalculateClusters()`) remained solely inside `ClusterTuningScreen.kt`.
3. In `ClusterTuningScreen.kt`, tapping the recalculate button immediately initiated full history reprocessing without any explicit confirmation dialog, presenting a high risk of accidental data wiping (loss of custom cluster names and manual groupings).

This fragmented the user experience: athletes configuring cluster sensitivity in Advanced Settings could not trigger recalculation directly, while athletes browsing their favorite routes had an unneeded settings icon that duplicated tuning controls and exposed an unguarded, destructive recalculate action.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

### 2.1 Historical Context & Evolution
1. **Sprint 2026-39 / Sprint 2026-40 (ATT-350 / ATT-492 / ATT-501 / ATT-728)**:
   * 3D topological route clustering was introduced to group similar workout tracks into "Lieblingsstrecken" (`REQ-SET-065`).
   * A dedicated `ClusterTuningScreen.kt` was created, accessible from `WorkoutClustersTabsScreen.kt` via a gear icon.
   * A recalculate button was placed at the bottom of `ClusterTuningScreen.kt` to allow users to rebuild clusters if tolerances were altered.
2. **Sprint 2026-42.1 (ATT-3355 / REQ-SET-075)**:
   * Advanced Settings was modernized into a dedicated full-screen destination (`AdvancedTuningScreen.kt`).
   * Clustering tolerances were recognized as expert power-user settings and consolidated into Advanced Settings (`ClusterTuningSection.kt`).
   * Shared parameter sliders were factored into `ClusterTuningContent.kt`.
   * `ClusterTuningScreen.kt` was kept as an interim entry point because recalculation had not yet been integrated into `ClusterTuningSection.kt`.

### 2.2 Chesterton's Fence Analysis
* **Why was the recalculation button on `ClusterTuningScreen`?**  
  Because when `ClusterTuningScreen` was the only place where clustering tolerances could be changed, recalculation naturally resided adjacent to those tolerances.
* **Why did it lack a confirmation dialog?**  
  It was originally an internal developer/power-user tool where a text warning label below the button was deemed sufficient. In production, users can easily tap this button accidentally, causing a long-running background job and wiping all custom cluster renames.
* **Can the tuning entry point in `WorkoutClustersTabsScreen` be safely removed?**  
  Yes. Now that cluster tuning is permanently homed in Advanced Settings, having a settings icon in the routes header violates navigation consistency and duplicates settings entry points.
* **What invariants must be preserved?**  
  1. `WorkoutClusterRepository.recalculateClustersWithProgress()` execution semantics and progress reporting.
  2. Immediate persistence of active tolerances to `TrainingApplication` / `TuningPreferencesDataStore` before recalculation.
  3. Modularity: All files in `ui/settings/tuning` MUST remain strictly `< 400` lines (`AdvancedTuningModularityTest.kt`).
  4. 100% clean-room test pass rate across the full test suite.
  5. 100% 9-language translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 3. Scope & Target Architecture

### 3.1 Header Cleanup in `WorkoutClustersTabsScreen.kt` & `WorkoutClustersScreen.kt`
* Excising `IconButton(onClick = onTuneClick)` from `WorkoutClustersTabsScreen.kt`.
* Removing `onTuneClick` callback from `WorkoutClustersTabsScreen.kt`.
* Removing `isTuning` state handling and navigation to `ClusterTuningScreen` from `WorkoutClustersScreen.kt`.
* Retaining Info (`Icons.Outlined.Info`), Sort (`Icons.AutoMirrored.Filled.Sort`), and Filter buttons in `WorkoutClustersTabsScreen.kt`.
* Deprecating `ClusterTuningScreen.kt`.

### 3.2 State Management & Service Layer Architecture
* **Direct Repository Integration**: Instead of routing through `WorkoutClustersViewModel` (which is scoped to the `WorkoutClustersScreen` lifecycle), recalculation in Advanced Settings interacts directly with the singleton `WorkoutClusterRepository.getInstance(context)`.
* **Parameter Persistence Pre-Condition**: Prior to starting cluster recalculation, active slider tolerances (`endpointTolerance`, `apexTolerance`, `distanceTolerance`, `altitudePositionTolerance`, `useSportTypeForClustering`, `useAltitudePosForClustering`) MUST be immediately committed to `TrainingApplication` and `TuningPreferencesDataStore`. This guarantees that `WorkoutClusterEngine.recalculateHistory()` reads the exact newly configured parameters.
* **Global Progress State Synchronization**: `WorkoutClusterRepository` maintains `migrationStatus: StateFlow<MigrationStatus?>`. `ClusterTuningSection` collects `migrationStatus`:
  * When `migrationStatus != null`, a recalculation is actively executing.
  * If the user leaves `AdvancedTuningScreen` and returns while recalculation is in progress, the UI automatically observes the active `migrationStatus` and restores progress state.
  * The recalculation job is executed on `Dispatchers.Default` / `Dispatchers.IO` via the repository, ensuring that navigating away from `AdvancedTuningScreen` does not prematurely kill or corrupt the atomic recalculation process.

### 3.3 Confirmation Dialog Specification (Material 3 Guard)
* **Dialog Trigger**: Tapping the "Recalculate All Clusters" button opens a modal `AlertDialog`.
* **Blocking Guard**: The confirmation dialog is a mandatory blocking decision gate. It does NOT provide a "Don't ask again" option, because cluster recalculation is inherently destructive (clearing all user-defined cluster names, custom boundaries, and manual workout associations).
* **Visual Styling & Contract Compliance (Rule 23)**:
  * `containerColor = MaterialTheme.colorScheme.surface`
  * `tonalElevation = 0.dp`
  * Title: `R.string.cluster_tuning_recalculate_button` (*Recalculate All Clusters* / *Alle Cluster neu berechnen*)
  * Message: `R.string.cluster_tuning_warning` (*Warning: Recalculating will wipe all learned cluster names and grouping, then rebuild them from history using these new parameters.*)
  * Confirm Button: Text button with `R.string.cluster_tuning_recalculate_button`, styled in high-contrast `MaterialTheme.colorScheme.error`.
  * Dismiss Button: Text button with `R.string.cancel`.

### 3.4 Concurrency Control & Double-Tap Prevention
* When recalculation starts:
  1. Local `isRecalculating` is set to `true`, and the repository's `migrationStatus` transitions to non-null.
  2. The Recalculate button is disabled (`enabled = false`), preventing duplicate clicks or race conditions.
  3. A `CircularProgressIndicator` (size 18.dp, stroke 2.dp) is displayed inside the button with text `R.string.cluster_tuning_recalculating`.
  4. Sliders in `ClusterTuningContent` are disabled while recalculating (`enabled = !isRecalculating`) to prevent mutating parameters while the clustering algorithm is running.
  5. Upon completion: `isRecalculating` returns to `false`, `migrationStatus` resets to `null`, and a `Toast` or localized message confirms completion.

### 3.5 Modularity & File Size Invariant
* `ClusterTuningSection.kt` currently contains 69 lines. Incorporating the button, confirmation dialog, and state observation will increase it to ~130-150 lines, strictly complying with the `< 400` lines limit enforced by `AdvancedTuningModularityTest.kt`.
* `AdvancedTuningScreen.kt` (389 lines) remains untouched or gains zero net lines, preserving its critical modularity constraint.

---

## 4. Acceptance Criteria & Test Strategy

* **AC-1 (Header Cleanup)**: `WorkoutClustersTabsScreen.kt` contains zero cluster tuning settings buttons or `onTuneClick` invocations.
* **AC-2 (Recalculate Action in Advanced Settings)**: `ClusterTuningSection.kt` contains a dedicated recalculate action button (`R.string.cluster_tuning_recalculate_button`).
* **AC-3 (Confirmation Guard Dialog)**: Clicking the recalculate button opens an `AlertDialog` warning the user of cluster wiping before any recalculation starts.
* **AC-4 (Concurrency & Disabling)**: While recalculating, the button and controls are disabled, displaying progress indicators and preventing duplicate triggers.
* **AC-5 (State Persistence & Parity)**: Parameters are persisted before recalculation; 100% 9-language parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
* **AC-6 (Modularity & Clean-Room Suite)**: All files in `ui/settings/tuning` remain strictly `< 400` lines; full test suite passes 100% with zero regressions.

---

## 5. Risk Assessment & Mitigation

* **Risk 1: Accidental Data Wiping during Settings Browsing**:
  * *Mitigation*: Solved via mandatory modal confirmation `AlertDialog` requiring deliberate user confirmation.
* **Risk 2: Process Interruption on Screen Navigation**:
  * *Mitigation*: Recalculation runs in `WorkoutClusterRepository` background coroutines, reporting progress via global `migrationStatus` StateFlow.
* **Risk 3: Modularity Violation in `ui/settings/tuning`**:
  * *Mitigation*: Logic encapsulated within `ClusterTuningSection.kt` (69 lines -> ~140 lines), leaving `AdvancedTuningScreen.kt` (389 lines) unaffected.
