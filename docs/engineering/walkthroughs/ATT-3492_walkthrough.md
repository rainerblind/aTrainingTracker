# Stage 5 Walkthrough - ATT-3492: Style cluster recalculate button in red and add direct Advanced Settings deep-link to Favorite Routes info

**Ticket**: [ATT-3492](https://atrainingtracker.atlassian.net/browse/ATT-3492)  
**Sub-task**: [ATT-3509](https://atrainingtracker.atlassian.net/browse/ATT-3509) (`[Test]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.3`  
**Requirement Mapping**: `REQ-SET-076` (*Red Cluster Recalculate Button and Direct Advanced Settings Deep-Link from Favorite Routes*), amending `REQ-SET-075` and `REQ-UI-149`  
**Test Spec ID**: `TST-SET-076`  
**Branch**: `feature/ATT-3492`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Executive Summary & Verification Objective

During Sprint 2026-42.2 Joint Review (ATT-3452), two usability and safety refinements were identified regarding workout route clustering:
1. **Red Destructive Styling for Recalculate Action**: In Advanced Settings Section 6 (`Route & Workout Clustering` / `ClusterTuningSection.kt`), the "Alle Cluster neu berechnen" (Recalculate All Clusters) action permanently wipes all existing cluster groupings and centroids from SQLite history before rebuilding. Default primary button styling failed to visually warn users before tapping.
2. **Discoverability & Direct Deep-Linking from Favorite Routes**: In Favorite Routes (`WorkoutClustersTabsScreen.kt`), the educational info dialog (`ClusterInfoDialog.kt`) explains the 3D topological clustering heuristics. However, its Section 4 text referenced sliders "below" (which were moved to Advanced Settings in ATT-3452) and lacked a direct path for athletes to adjust parameters.

ATT-3492 resolves both issues with:
* High-visibility Material 3 error button styling (`containerColor = MaterialTheme.colorScheme.error`, `contentColor = MaterialTheme.colorScheme.onError`) and WCAG 2.1-compliant accessible progress indicators.
* Updated Section 4 description directing athletes to Advanced Settings, while maintaining 100% 9-language translation parity.
* A direct action button (`R.string.cluster_info_open_advanced_tuning`) inside Section 4 of `ClusterInfoDialog.kt` executing a strictly ordered state transition: `onDismissRequest()` to teardown sheet state, followed immediately by `onNavigateToAdvancedSettings()`.
* Architectural adherence to Single Source of Truth and pure Compose delegation: zero Activity-level casts or lifecycle couplings inside UI composables.

---

## 2. Changes Made & Files Modified

### 2.1 UI Styling & Accessibility (`ClusterTuningSection.kt`)
* [ClusterTuningSection.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/ClusterTuningSection.kt):
  - Configured `colors = ButtonDefaults.buttonColors(...)`:
    - `containerColor = MaterialTheme.colorScheme.error`
    - `contentColor = MaterialTheme.colorScheme.onError`
    - `disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)`
    - `disabledContentColor = MaterialTheme.colorScheme.onError`
  - Configured `CircularProgressIndicator(color = MaterialTheme.colorScheme.onError)` ensuring accessible contrast during recalculation.

### 2.2 Deep-Link Action & Navigation Delegation (`ClusterInfoDialog.kt`)
* [ClusterInfoDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterInfoDialog.kt):
  - Added `onNavigateToAdvancedSettings: () -> Unit` parameter.
  - Added direct action button with `Icon(Icons.Default.Tune)` and `Text(stringResource(R.string.cluster_info_open_advanced_tuning))`.
  - Enforced strict execution order: `onDismissRequest()` strictly before `onNavigateToAdvancedSettings()`.

### 2.3 Navigation Plumbing
* [WorkoutClustersTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersTabsScreen.kt): Forwarded `onNavigateToAdvancedSettings: () -> Unit`.
* [WorkoutClustersScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersScreen.kt): Forwarded `onNavigateToAdvancedSettings: () -> Unit`.
* [ATrainingTrackerApp.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt): Wired callback to `navController.navigate(NavRoutes.ADVANCED_SETTINGS)`.
* [WorkoutClustersFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersFragment.kt): Wired callback to `(activity as? MainActivityWithNavigation)?.navigateToDrawerItem(R.id.drawer_advanced_tuning)`.
* [ClusterTuningScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterTuningScreen.kt) & [ImportBackupTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt): Supplied dismissal closures.

### 2.4 Localization Parity (9 Locales)
* `values/strings.xml`, `values-de/strings.xml`, `values-es/strings.xml`, `values-fr/strings.xml`, `values-it/strings.xml`, `values-ja/strings.xml`, `values-nl/strings.xml`, `values-pl/strings.xml`, `values-pt/strings.xml`:
  - Added `cluster_info_open_advanced_tuning`.
  - Updated `cluster_info_tuning_desc` to describe Advanced Settings centralization while preserving strict and relaxed label tokens across all 9 languages.

---

## 3. Test Verification & Results

### 3.1 Unit & Contract Tests
1. **`ClusterTuningContractTest`**:
   - Verified recalculate button uses Material 3 error tokens and progress indicator uses `onError`.
2. **`ClusterInfoDialogTest`**:
   - Verified 100% 9-language translation parity for `cluster_info_open_advanced_tuning` and `cluster_info_tuning_desc`.
   - Verified strict and relaxed labels match across all 9 locales.
3. **`ClusterInfoDialogContractTest`**:
   - Verified required parameter `onNavigateToAdvancedSettings: () -> Unit`.
   - Verified action button with `Tune` icon and text resource.
   - Verified sequential ordering: `onDismissRequest()` called strictly before `onNavigateToAdvancedSettings()`.
   - Verified zero Activity casts in UI composable.
   - Verified callback propagation through `WorkoutClustersTabsScreen` and `WorkoutClustersScreen`.
4. **`AdvancedTuningModularityTest`**:
   - Verified all files in `ui.settings.tuning` remain strictly `< 400` lines.
     - `ClusterTuningSection.kt`: 173 lines.
     - `ClusterInfoDialog.kt`: 154 lines.
     - `WorkoutClustersTabsScreen.kt`: 395 lines.
     - `WorkoutClustersScreen.kt`: 344 lines.

### 3.2 Full Clean-Room Regression Suite
* **Command**: `./gradlew testDebugUnitTest`
* **Result**: BUILD SUCCESSFUL, 100% pass rate across all project modules, zero regressions.

---

## 4. ASPICE Traceability Matrix

| Requirement | Test Spec | Verification Method | Status |
| :--- | :--- | :--- | :--- |
| `REQ-SET-076` (Error Styling & Accessibility) | `TST-SET-076.1` | `ClusterTuningContractTest` | Verified |
| `REQ-SET-076` (9-Language Localization) | `TST-SET-076.2` | `ClusterInfoDialogTest` | Verified |
| `REQ-SET-076` (Deep-Link Action & Pure Delegation) | `TST-SET-076.3` | `ClusterInfoDialogContractTest` | Verified |
| `REQ-SET-076` (Modularity < 400 lines) | `TST-SET-076.4` | `AdvancedTuningModularityTest` | Verified |
| `REQ-PRO-001` (Clean-Room Zero Regressions) | `TST-SET-076.5` | `./gradlew testDebugUnitTest` | Verified |
