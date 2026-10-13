# Stage 1: Problem Domain & Root Cause Analysis - ATT-3452

**Ticket**: [ATT-3452](https://atrainingtracker.atlassian.net/browse/ATT-3452)  
**Summary**: Remove cluster tuning from favorite routes and add recalculate action with confirmation to Advanced Settings  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `Sprint 2026-42.2`  
**Active Sprint**: `Sprint 2026-42.2`  
**Author**: AI Agent 1 (Analyst)  
**Date**: 2026-10-13 (Revised 2)  

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

### 3.2 State Management, Concurrency & Lifecycle Decoupling
1. **Application/Repository-Level Lifecycle Scope**:
   * Recalculation is an asynchronous, computation-heavy process executed on `Dispatchers.Default` (algorithm) and `Dispatchers.IO` (SQLite queries).
   * It MUST NOT be tethered to a transient UI Composable scope that cancels upon back navigation. Instead, it is triggered via the application-context singleton `WorkoutClusterRepository.getInstance(context.applicationContext)`.
   * When triggered, the repository executes `recalculateClustersWithProgress()` within its persistent coroutine scope, guaranteeing that if the user exits `AdvancedTuningScreen` while recalculation is underway, the task runs to completion without interruption or data corruption.
2. **Encapsulated Read-Only State Synchronization**:
   * `WorkoutClusterRepository` strictly encapsulates `private val _migrationStatus = MutableStateFlow<MigrationStatus?>(null)` and exposes `val migrationStatus: StateFlow<MigrationStatus?> = _migrationStatus.asStateFlow()` as a public read-only contract.
   * `ClusterTuningSection` collects `migrationStatus` with lifecycle awareness. If recalculation is active (`migrationStatus != null`), the UI reactively reflects the progress state.
   * If the user leaves `AdvancedTuningScreen` and returns, or navigates to `WorkoutClustersScreen`, the active `migrationStatus` is immediately picked up without losing progress visibility.
3. **Data Integrity & Concurrency Isolation**:
   * During recalculation, read queries against `allClusters` continue to observe the last consistent in-memory state until `refreshClusters()` finishes and atomically updates `_allClusters.value`.
   * Write operations (e.g. background workout saving or manual clustering) are safely serialized by SQLite database transaction locks in `WorkoutClusterDatabaseManager`.
   * While `migrationStatus != null`, the Recalculate button and all tolerance sliders in `ClusterTuningSection` are disabled (`enabled = false`), eliminating any possibility of double-tap triggers, concurrent recalculation runs, or modifying tolerances while the algorithm is executing.
4. **Process-Death Recovery & Self-Healing Bootstrap**:
   * If the application process is terminated by the OS (e.g. low memory killer) midway through recalculation, the database state might be left empty or partial.
   * `WorkoutClusterRepository.refreshClusters()` already implements an established, verified self-healing bootstrapper (ATT-392 / lines 88–92 of `WorkoutClusterRepository.kt`): upon subsequent repository access, if `clusterDb.getAllClusters().isEmpty() && _migrationStatus.value == null`, it automatically detects the incomplete state and triggers a self-healing background recalculation from history.

### 3.3 Confirmation Dialog Specification (Material 3 Blocking Guard)
* **Dialog Trigger**: Tapping the "Recalculate All Clusters" button opens a modal `AlertDialog`.
* **Blocking Guard**: The confirmation dialog is a mandatory blocking decision gate. It does NOT provide a "Don't ask again" option, because cluster recalculation is inherently destructive (clearing all user-defined cluster names, custom boundaries, and manual workout associations).
* **Visual Styling & Contract Compliance (Rule 23)**:
  * `containerColor = MaterialTheme.colorScheme.surface`
  * `tonalElevation = 0.dp`
  * Title: `R.string.cluster_tuning_recalculate_button` (*Recalculate All Clusters* / *Alle Cluster neu berechnen*)
  * Message: `R.string.cluster_tuning_warning` (*Warning: Recalculating will wipe all learned cluster names and grouping, then rebuild them from history using these new parameters.*)
  * Confirm Button: Text button with `R.string.cluster_tuning_recalculate_button`, styled in high-contrast `MaterialTheme.colorScheme.error`.
  * Dismiss Button: Text button with `R.string.cancel`.
* **Execution Sequence upon Confirmation**:
  1. Dismiss confirmation dialog.
  2. Immediately persist active tolerances (`endpointTolerance`, `apexTolerance`, `distanceTolerance`, `altitudePositionTolerance`, `useSportTypeForClustering`, `useAltitudePosForClustering`) to `TrainingApplication` and `TuningPreferencesDataStore`.
  3. Launch `WorkoutClusterRepository.getInstance(context).recalculateClustersWithProgress()`.

### 3.4 Modularity & File Size Invariant Protection
* `ClusterTuningSection.kt` currently contains 69 lines. Incorporating the button, confirmation dialog, and state observation will increase it to ~130-150 lines, strictly complying with the `< 400` lines limit enforced by `AdvancedTuningModularityTest.kt`.
* `AdvancedTuningScreen.kt` (389 lines) remains untouched or gains zero net lines, preserving its critical modularity constraint.

---

## 4. Acceptance Criteria & Test Strategy

* **AC-1 (Header Cleanup)**: `WorkoutClustersTabsScreen.kt` contains zero cluster tuning settings buttons or `onTuneClick` invocations.
* **AC-2 (Recalculate Action in Advanced Settings)**: `ClusterTuningSection.kt` contains a dedicated recalculate action button (`R.string.cluster_tuning_recalculate_button`).
* **AC-3 (Confirmation Guard Dialog)**: Clicking the recalculate button opens an `AlertDialog` warning the user of cluster wiping before any recalculation starts.
* **AC-4 (Concurrency & Disabling)**: While recalculating (`migrationStatus != null`), the button and controls are disabled, displaying progress indicators and preventing duplicate triggers.
* **AC-5 (Lifecycle Decoupling & Recovery)**: Recalculation runs asynchronously without blocking navigation; self-healing bootstrap protects against process death; 100% 9-language parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
* **AC-6 (Modularity & Clean-Room Suite)**: All files in `ui/settings/tuning` remain strictly `< 400` lines; full test suite passes 100% with zero regressions.

---

## 5. Risk Assessment & Mitigation

* **Risk 1: Accidental Data Wiping during Settings Browsing**:
  * *Mitigation*: Solved via mandatory modal confirmation `AlertDialog` requiring deliberate user confirmation.
* **Risk 2: Process Interruption on Screen Navigation**:
  * *Mitigation*: Recalculation runs in `WorkoutClusterRepository` background coroutines, reporting progress via global `migrationStatus` StateFlow. If killed by OS, `refreshClusters()` self-heals on next launch.
* **Risk 3: Modularity Violation in `ui/settings/tuning`**:
  * *Mitigation*: Logic encapsulated within `ClusterTuningSection.kt` (69 lines -> ~140 lines), leaving `AdvancedTuningScreen.kt` (389 lines) unaffected.
