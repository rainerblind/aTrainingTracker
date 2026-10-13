# Stage 3: Implementation Plan - ATT-3452: Remove cluster tuning from favorite routes and add recalculate action with confirmation to Advanced Settings

**Ticket**: [ATT-3452](https://atrainingtracker.atlassian.net/browse/ATT-3452)  
**Sub-task**: [ATT-3469](https://atrainingtracker.atlassian.net/browse/ATT-3469) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.2`  
**Active Sprint**: `Sprint 2026-42.2`  
**Requirement Mapping**: `REQ-SET-075` (*Dedicated Workout Route Clustering Section in Advanced Settings, Single Source of Truth, and Modularity Preservation*), amending `REQ-SET-010` and `REQ-SET-065`, superseding `REQ-SET-014`  
**Test Spec Mapping**: `TST-UI-321`  
**Branch**: `feature/ATT-3452`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Problem Description & Background

In `aTrainingTracker`, workout route clustering algorithmic parameters were historically accessed from a settings icon in the collapsing header of the Favorite Routes screen (`WorkoutClustersTabsScreen.kt` / `WorkoutClustersScreen.kt`), launching `ClusterTuningScreen.kt`.

In Sprint 2026-42.1 (ATT-3355), all algorithmic route clustering parameters were consolidated into Advanced Settings (`AdvancedTuningScreen.kt` / `ClusterTuningSection.kt`) as a dedicated accordion section using shared controls (`ClusterTuningContent.kt`). However:
1. **Redundant Entry Point**: The header settings icon in `WorkoutClustersTabsScreen.kt` still navigated to `ClusterTuningScreen.kt`, creating dual competing configuration paths for the same algorithmic parameters in violation of the Single Source of Truth principle (Rule 36).
2. **Missing Recalculation Action in Advanced Settings**: While parameters could be adjusted and persisted in Advanced Settings, athletes had no direct mechanism to recalculate all historical clusters without navigating to the favorite routes screen.
3. **Missing Material 3 Confirmation Guard**: Cluster recalculation is an inherently destructive operation (resetting learned cluster names, custom boundaries, and manual assignments), yet lacked a mandatory Material 3 confirmation guard.

ATT-3452 eliminates the redundant header trigger, excises `ClusterTuningScreen.kt` from `WorkoutClustersScreen.kt`, and adds a dedicated "Recalculate All Clusters" button to `ClusterTuningSection.kt` in Advanced Settings guarded by a modal Material 3 confirmation `AlertDialog`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-SET-075` (amended by ATT-3452)
  - Clause 1: Modular Accordion Section Architecture (`ClusterTuningSection.kt`, `AdvancedTuningAccordion.kt`).
  - Clause 2: Single Source of Truth, DRY Refactoring & Header Tuning Elimination (Rule 36, ATT-3452).
  - Clause 3: DataStore, Defaults & Atomic Factory Reset Integration.
  - Clause 4: Dedicated Recalculate Action, Material 3 Confirmation Guard & Concurrency Isolation (ATT-3452).
  - Clause 5: Preservation of Invariants & Modularity (< 400 lines).
* **Test Mapping**: `TST-UI-321`
  - `[TST-UI-321.1]`: Favorite Routes header & screen cleanup contract tests in `ClusterTuningContractTest.kt`.
  - `[TST-UI-321.2]`: Advanced Settings recalculate action & Material 3 confirmation guard in `ClusterTuningContractTest.kt`.
  - `[TST-UI-321.3]`: Lifecycle decoupling, concurrency isolation & double-tap prevention in `ClusterTuningContractTest.kt`.
  - `[TST-UI-321.4]`: Modularity & file size limit enforcement (< 400 lines) in `AdvancedTuningModularityTest.kt`.
  - `[TST-UI-321.5]`: 9-language localization audit in `TranslationParityTest.kt`.
  - `[TST-UI-321.6]`: Clean-room full suite regression (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Clustering Algorithm Integrity**: Mathematical calculations, 3D similarity formulas, and spatial tolerances in `WorkoutClusterEngine.kt` remain 100% unaltered.
2. **Database Invariants**: SQLite schemas, tables (`clusters`, `cluster_points`), and queries in `WorkoutClusterDatabaseManager.kt` remain unchanged.
3. **DataStore Keys & Defaults**: All 6 clustering parameters (`clusterTolEndpoints`, `clusterTolApex`, `clusterTolDistance`, `clusterTolAltitudePos`, `clusterUseSportType`, `clusterUseAltitudePos`), DataStore keys, and factory reset defaults remain 100% intact.
4. **Modularity Invariant**: Every file in `ui/settings/tuning/` strictly obeys `< 400` lines (`AdvancedTuningModularityTest.kt`).
5. **Localization Parity**: 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT. Zero missing string resources.
6. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`. Parent tickets stop at `Final Review (Human)`.

---

## 4. UI Consistency (Rule 23)

* **Closest Reference Screens**:
  - `AdvancedTuningScreen.kt` (Advanced Settings catalog hosting accordion categories).
  - `ClusterTuningContent.kt` (reusable clustering controls).
  - Material 3 `AlertDialog` patterns in `aTrainingTracker` (e.g., `DeleteConfirmationDialog` and `ClearDataConfirmationDialog`).
* **Reused Components**:
  - `ClusterTuningContent.kt` for sliders and toggles.
  - Material 3 `OutlinedButton` / `Button` for recalculate action.
  - Material 3 `AlertDialog` for confirmation guard.
  - Material 3 `TextButton` for dialog action buttons.
  - `CircularProgressIndicator` for in-progress loading feedback.
* **Theme Tokens Applied**:
  - `containerColor = MaterialTheme.colorScheme.surface` (neutral, non-jarring dialog container).
  - `tonalElevation = 0.dp` (flat surface adhering to application design guidelines).
  - Confirm action button text: `MaterialTheme.colorScheme.error` (signaling destructive wipe of learned cluster grouping).
  - Dismiss button: default text button with `R.string.cancel`.
  - Spacing scale: `12.dp` and `14.dp` vertical arrangement; `16.dp` button padding.
  - Shapes: `MaterialTheme.shapes.extraLarge` (Material 3 standard dialog shape).
* **Justification for New Styles**: Zero new one-off styles or tokens. All components, colors, and string resources already exist and comply with the design system.

---

## 5. Proposed Architectural Changes

```
┌───────────────────────────────────────────────────────────────┐
│                      Advanced Settings                        │
│                 (AdvancedTuningScreen.kt)                     │
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  Accordion Phase C: Route & Workout Clustering          │  │
│  │  (ClusterTuningSection.kt)                              │  │
│  │                                                         │  │
│  │  • ClusterTuningContent(enabled = !isRecalculating)     │  │
│  │    - Master Sensitivity Slider                          │  │
│  │    - Detailed Tolerance Sliders (Endpoint, Apex, etc.)  │  │
│  │    - Altitude & Sport Type Switches                     │  │
│  │                                                         │  │
│  │  • [Recalculate All Clusters] Button (enabled = !active)│  │
│  │      │                                                  │  │
│  │      ▼ triggers                                         │  │
│  │  ┌───────────────────────────────────────────────────┐  │  │
│  │  │ AlertDialog (Confirmation Guard)                  │  │  │
│  │  │ - Title: Recalculate All Clusters                 │  │  │
│  │  │ - Text: Warning: Destructive wipe...              │  │  │
│  │  │ - [Cancel]  [Confirm (Error Red)]                 │  │  │
│  │  └─────────────────────┬─────────────────────────────┘  │  │
│  └────────────────────────┼────────────────────────────────┘  │
└───────────────────────────┼───────────────────────────────────┘
                            │ On Confirm
                            ▼
      ┌───────────────────────────────────────────────┐
      │  WorkoutClusterRepository (Singleton Scope)   │
      │  • recalculateClustersWithProgress()          │
      │  • exposes migrationStatus: StateFlow         │
      │  • self-healing bootstrap on process death    │
      └───────────────────────────────────────────────┘
```

### Component 1: `WorkoutClustersTabsScreen.kt`
* Remove `onTuneClick: () -> Unit` parameter from `WorkoutClustersTabsScreen`.
* Excised `IconButton(onClick = onTuneClick)` from the collapsing app bar title row (lines 265–272).
* Preserve info button (`IconButton(onClick = { showInfoDialog = true })`) and filter sheet triggers.

### Component 2: `WorkoutClustersScreen.kt`
* Remove `var isTuning by rememberSaveable { mutableStateOf(false) }`.
* Remove `LaunchedEffect(Unit) { viewModel.recalculationFinished.collectLatest { isTuning = false } }`.
* Excised `when { isTuning -> ClusterTuningScreen(...) ... }` branch.
* Remove `onTuneClick = { isTuning = true }` invocation argument to `WorkoutClustersTabsScreen`.

### Component 3: `ClusterTuningContent.kt`
* Add optional parameter `enabled: Boolean = true` to `ClusterTuningContent`.
* Propagate `enabled` to master `Slider`, details `Switch`, sport type `Switch`, altitude `Switch`, and individual `TuningSlider` composables.
* Ensures sliders cannot be modified while recalculation is executing in the background.

### Component 4: `ClusterTuningSection.kt`
* Collect `val migrationStatus by WorkoutClusterRepository.getInstance(LocalContext.current.applicationContext).migrationStatus.collectAsStateWithLifecycle()`.
* Derive `val isRecalculating = migrationStatus != null`.
* Maintain `var showConfirmDialog by rememberSaveable { mutableStateOf(false) }`.
* Pass `enabled = !isRecalculating` to `ClusterTuningContent`.
* Render Recalculate action button below `ClusterTuningContent`:
  - `Button(onClick = { showConfirmDialog = true }, enabled = !isRecalculating)`
  - Content: if `isRecalculating`, show `CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)` + `Spacer(8.dp)` + `Text(stringResource(R.string.cluster_tuning_recalculating))`.
  - Else: `Text(stringResource(R.string.cluster_tuning_recalculate_button))`.
* Render `AlertDialog` when `showConfirmDialog == true`:
  - `containerColor = MaterialTheme.colorScheme.surface`
  - `tonalElevation = 0.dp`
  - Title: `Text(stringResource(R.string.cluster_tuning_recalculate_button))`
  - Text: `Text(stringResource(R.string.cluster_tuning_warning))`
  - Confirm: `TextButton(onClick = { showConfirmDialog = false; /* save & trigger */ }) { Text(stringResource(R.string.cluster_tuning_recalculate_button), color = MaterialTheme.colorScheme.error) }`
  - Dismiss: `TextButton(onClick = { showConfirmDialog = false }) { Text(stringResource(R.string.cancel)) }`
* Recalculation Trigger on Confirm:
  - Synchronously persist current parameter values to `TrainingApplication` / `TuningPreferencesDataStore`.
  - Launch repository recalculation in coroutine scope: `WorkoutClusterRepository.getInstance(context.applicationContext).recalculateClustersWithProgress()`.

### Component 5: `ClusterTuningContractTest.kt`
* Update contract tests:
  - Replace `clusterTuningScreen_delegatesToClusterTuningContent` with `workoutClusters_hasNoTuningScreenOrAction`.
  - Assert `WorkoutClustersTabsScreen.kt` does NOT contain `onTuneClick` or `ic_settings_24`.
  - Assert `WorkoutClustersScreen.kt` does NOT contain `ClusterTuningScreen` or `isTuning`.
  - Assert `ClusterTuningSection.kt` delegates to `ClusterTuningContent`.
  - Assert `ClusterTuningSection.kt` contains `cluster_tuning_recalculate_button` and `cluster_tuning_warning` with `AlertDialog`.
  - Assert `ClusterTuningSection.kt` observes `migrationStatus` and guards controls.

---

## 6. Atomic Implementation Steps

### Step 1: Excising Cluster Tuning Entry Point from Favorite Routes
* Edit `WorkoutClustersTabsScreen.kt`:
  - Remove `onTuneClick: () -> Unit` parameter from `WorkoutClustersTabsScreen`.
  - Remove `IconButton(onClick = onTuneClick)` from lines 265–272.
* Edit `WorkoutClustersScreen.kt`:
  - Remove `var isTuning by rememberSaveable { mutableStateOf(false) }`.
  - Remove `recalculationFinished` observation for `isTuning`.
  - Remove `when { isTuning -> ... }` branch.
  - Remove `onTuneClick = { isTuning = true }` from `WorkoutClustersTabsScreen` invocation.

### Step 2: Enabling / Disabling Support in `ClusterTuningContent.kt`
* Edit `ClusterTuningContent.kt`:
  - Add `enabled: Boolean = true` to `fun ClusterTuningContent(...)`.
  - Propagate `enabled` to:
    - Master sensitivity `Slider` (`enabled = enabled`).
    - Details toggle `Switch` (`enabled = enabled`).
    - Sub-sliders in `TuningSlider` (`enabled = enabled`).
    - Feature switches (`enabled = enabled`).

### Step 3: Implementing Recalculate Action & Confirmation Guard in `ClusterTuningSection.kt`
* Edit `ClusterTuningSection.kt`:
  - Import `LocalContext`, `collectAsStateWithLifecycle`, `rememberCoroutineScope`, `launch`, `Button`, `TextButton`, `AlertDialog`, `CircularProgressIndicator`, `WorkoutClusterRepository`, `TrainingApplication`, `TuningPreferencesDataStore`.
  - Collect `migrationStatus`.
  - Add state `showConfirmDialog`.
  - Render Recalculate button with progress indicator.
  - Render confirmation `AlertDialog` with Material 3 styling (`surface` container, `0.dp` elevation, error color confirm).
  - Wire confirmation sequence: save parameters, invoke `recalculateClustersWithProgress()`.

### Step 4: Updating Contract Tests in `ClusterTuningContractTest.kt`
* Edit `ClusterTuningContractTest.kt`:
  - Assert `WorkoutClustersTabsScreen.kt` does not contain `onTuneClick` or `ic_settings_24`.
  - Assert `WorkoutClustersScreen.kt` does not contain `ClusterTuningScreen` or `isTuning`.
  - Assert `ClusterTuningSection.kt` contains recalculate button, `AlertDialog`, warning text, and `migrationStatus` check.
  - Assert `ClusterTuningContent.kt` declares `enabled: Boolean = true`.

### Step 5: Verification & Modularity Audit
* Run targeted tests:
  `./gradlew testDebugUnitTest --tests "*Cluster*"`
* Run modularity audit:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningModularityTest"`
* Run full regression test suite:
  `./gradlew testDebugUnitTest`

---

## 7. Verification & Test Plan

1. **Targeted Unit & Contract Tests**:
   - `ClusterTuningContractTest`:
     - `testWorkoutClustersTabsScreen_hasNoTuneAction`
     - `testClusterTuningSection_hasRecalculateActionAndConfirmationDialog`
     - `testClusterTuningSection_concurrencyAndProgressState`
     - `testClusterTuningContent_isEncapsulatedAndReusable`
   - `TuningPreferencesDataStoreClusterTest`:
     - Assert DataStore clustering parameters retain defaults and clamping bounds.
   - `AdvancedTuningModularityTest`:
     - Assert all files in `ui.settings.tuning` remain strictly `< 400` lines.
2. **Clean-Room Regression Command**:
   `./gradlew testDebugUnitTest`
   - Expected result: 100% test pass rate with zero regressions.
