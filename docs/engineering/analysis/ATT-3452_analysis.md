# Stage 1: Problem Domain & Root Cause Analysis - ATT-3452

**Ticket**: [ATT-3452](https://atrainingtracker.atlassian.net/browse/ATT-3452)  
**Summary**: Remove cluster tuning from favorite routes and add recalculate action with confirmation to Advanced Settings  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `Sprint 2026-42.2`  
**Active Sprint**: `Sprint 2026-42.2`  
**Author**: AI Agent 1 (Analyst)  
**Date**: 2026-10-13  

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

### 3.1 Architectural Scope
1. **Header Cleanup in `WorkoutClustersTabsScreen.kt` & `WorkoutClustersScreen.kt`**:
   * Excising `IconButton(onClick = onTuneClick)` from `WorkoutClustersTabsScreen.kt`.
   * Removing `onTuneClick` callback from `WorkoutClustersTabsScreen.kt`.
   * Removing `isTuning` state handling and navigation to `ClusterTuningScreen` from `WorkoutClustersScreen.kt`.
   * Retaining Info (`Icons.Outlined.Info`), Sort (`Icons.AutoMirrored.Filled.Sort`), and Filter buttons in `WorkoutClustersTabsScreen.kt`.
   * Deprecating `ClusterTuningScreen.kt`.

2. **Recalculate Button & Guarded Confirmation in `ClusterTuningSection.kt`**:
   * Adding a prominent "Recalculate All Clusters" button (`cluster_tuning_recalculate_button`) at the bottom of `ClusterTuningSection.kt` with Material 3 error container styling.
   * Tapping the button opens an `AlertDialog`:
     * `containerColor = MaterialTheme.colorScheme.surface`, `tonalElevation = 0.dp` (Rule 23 / BottomSheetVisualContractTest compliance).
     * Title: `R.string.cluster_tuning_recalculate_button`.
     * Message: `R.string.cluster_tuning_warning` (*Warning: Recalculating will wipe all learned cluster names and grouping, then rebuild them from history using these new parameters*).
     * Confirm Button: "Recalculate" (error color).
     * Dismiss Button: "Cancel".
   * Upon confirmation:
     * Saves current tolerances to `TrainingApplication` / `TuningPreferencesDataStore`.
     * Asynchronously triggers `WorkoutClusterRepository.getInstance(context).recalculateClustersWithProgress()`.
     * Displays a progress indicator on the button while active (`cluster_tuning_recalculating`).

3. **Modularity & File Limit Invariant Protection**:
   * `ClusterTuningSection.kt` is currently 69 lines. Adding the button, dialog state, and handler will increase it to ~120-140 lines, easily respecting the `< 400` line threshold of `AdvancedTuningModularityTest.kt`.
   * `AdvancedTuningScreen.kt` (389 lines) remains completely untouched or gains at most 1 line, ensuring it stays strictly `< 400` lines.

---

## 4. Acceptance Criteria & Test Strategy

* **AC-1 (Header Cleanup)**: `WorkoutClustersTabsScreen.kt` contains zero cluster tuning settings buttons or `onTuneClick` invocations.
* **AC-2 (Recalculate Action in Advanced Settings)**: `ClusterTuningSection.kt` contains a dedicated recalculate action button (`R.string.cluster_tuning_recalculate_button`).
* **AC-3 (Confirmation Guard Dialog)**: Clicking the recalculate button opens an `AlertDialog` warning the user of cluster wiping before any recalculation starts.
* **AC-4 (Modularity)**: All files in `ui/settings/tuning` remain strictly `< 400` lines of code.
* **AC-5 (Localization & Parity)**: All dialog strings exist with 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
* **AC-6 (Clean-Room Suite)**: Full test suite passes 100% with zero regressions.

---

## 5. Risk Assessment

* **Risk 1: Accidental Data Wiping during Settings Browsing**:
  * *Mitigation*: Mitigated completely by the modal confirmation `AlertDialog` requiring deliberate user confirmation.
* **Risk 2: Modularity Violation in `ui/settings/tuning`**:
  * *Mitigation*: Encapsulate all recalculation and dialog logic within `ClusterTuningSection.kt` (which is only 69 lines), avoiding line inflation in `AdvancedTuningScreen.kt` (389 lines).
