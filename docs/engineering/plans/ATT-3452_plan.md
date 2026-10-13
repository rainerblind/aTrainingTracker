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
**Date**: 2026-10-13 (Revised per Gate 3 Audit)  

---

## 1. Problem Description & Background

In `aTrainingTracker`, workout route clustering algorithmic parameters were historically accessed from a settings icon in the collapsing header of the Favorite Routes screen (`WorkoutClustersTabsScreen.kt` / `WorkoutClustersScreen.kt`), launching `ClusterTuningScreen.kt`.

In Sprint 2026-42.1 (ATT-3355), all algorithmic route clustering parameters were consolidated into Advanced Settings (`AdvancedTuningScreen.kt` / `ClusterTuningSection.kt`) as a dedicated accordion section using shared controls (`ClusterTuningContent.kt`). However:
1. **Redundant Entry Point**: The header settings icon in `WorkoutClustersTabsScreen.kt` still navigated to `ClusterTuningScreen.kt`, creating dual competing configuration paths for the same algorithmic parameters in violation of the Single Source of Truth principle (Rule 36).
2. **Missing Recalculation Action in Advanced Settings**: While parameters could be adjusted and persisted in Advanced Settings, athletes had no direct mechanism to recalculate all historical clusters without navigating to the favorite routes screen.
3. **Missing Material 3 Confirmation Guard**: Cluster recalculation is an inherently destructive operation (resetting learned cluster names, custom boundaries, and manual assignments), yet lacked a mandatory Material 3 confirmation guard.

ATT-3452 eliminates the redundant header trigger, excises `ClusterTuningScreen.kt` from `WorkoutClustersScreen.kt`, and adds a dedicated "Recalculate All Clusters" button to `ClusterTuningSection.kt` in Advanced Settings guarded by a modal Material 3 confirmation `AlertDialog` and an application-scoped domain-layer concurrency lock.

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
  - `[TST-UI-321.3]`: Lifecycle decoupling, domain-layer concurrency lock & double-tap prevention in `ClusterTuningContractTest.kt`.
  - `[TST-UI-321.4]`: Interrupted recalculation & self-healing bootstrap recovery verification.
  - `[TST-UI-321.5]`: Modularity & file size limit enforcement (< 400 lines) in `AdvancedTuningModularityTest.kt`.
  - `[TST-UI-321.6]`: 9-language localization audit in `TranslationParityTest.kt`.
  - `[TST-UI-321.7]`: Clean-room full suite regression (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Clustering Algorithm Integrity**: Mathematical calculations, 3D similarity formulas, and spatial tolerances in `WorkoutClusterEngine.kt` remain 100% unaltered.
2. **Database Invariants**: SQLite schemas, tables (`clusters`, `cluster_points`), and queries in `WorkoutClusterDatabaseManager.kt` remain unchanged.
3. **DataStore Keys & Defaults (No Orphaned Configuration)**: All 6 clustering parameters (`clusterTolEndpoints`, `clusterTolApex`, `clusterTolDistance`, `clusterTolAltitudePos`, `clusterUseSportType`, `clusterUseAltitudePos`), DataStore keys, and factory reset defaults remain 100% intact. Because ATT-3355 already established `TuningPreferencesDefaults` and `TuningPreferencesDataStore` as the single source of truth used by both views, no legacy configuration keys exist, eliminating any risk of orphaned configuration data.
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
  - Material 3 `Button` for recalculate action.
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
* **Confirmation Warning Text (Exact Content & Impact)**:
  - Title: `R.string.cluster_tuning_recalculate_button` (*Recalculate All Clusters* / *Alle Cluster neu berechnen*)
  - Text: `R.string.cluster_tuning_warning`:
    - English: `"Warning: Recalculating will wipe all learned cluster names and grouping, then rebuild them from history using these new parameters."`
    - German: `"Warnung: Beim Neuberechnen werden alle gelernten Cluster-Namen und Gruppierungen gelöscht und aus dem Verlauf mit den neuen Parametern neu erstellt."`
    - Explicit impact stated: Informs the athlete that all learned cluster names and custom group associations will be purged and rebuilt from raw track history.
* **Justification for New Styles**: Zero new one-off styles or tokens. All components, colors, and string resources already exist and comply with the design system.

---

## 5. Architectural Decomposition & Concurrency Strategy (SWE.2)

```
┌───────────────────────────────────────────────────────────────┐
│                      Advanced Settings                        │
│                 (AdvancedTuningScreen.kt)                     │
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  Accordion Phase C: Route & Workout Clustering          │  │
│  │  (ClusterTuningSection.kt)                              │  │
│  │                                                         │  │
│  │  • Collects: migrationStatus StateFlow                  │  │
│  │  • ClusterTuningContent(enabled = migrationStatus==null)│  │
│  │                                                         │  │
│  │  • [Recalculate All Clusters] Button (enabled = !active)│  │
│  │      │                                                  │  │
│  │      ▼ opens modal guard                                │  │
│  │  ┌───────────────────────────────────────────────────┐  │  │
│  │  │ AlertDialog (Blocking Confirmation Guard)         │  │  │
│  │  │ - Title: Recalculate All Clusters                 │  │  │
│  │  │ - Text: Warning: Wipes learned cluster names...   │  │  │
│  │  │ - [Cancel]  [Confirm (Error Red)]                 │  │  │
│  │  └─────────────────────┬─────────────────────────────┘  │  │
│  └────────────────────────┼────────────────────────────────┘  │
└───────────────────────────┼───────────────────────────────────┘
                            │ On Confirm: calls repository.recalculateClustersAsync()
                            ▼
      ┌───────────────────────────────────────────────────────────┐
      │       WorkoutClusterRepository (Application Singleton)    │
      │                                                           │
      │  • CoroutineScope: CoroutineScope(SupervisorJob() +       │
      │                     Dispatchers.Default)                  │
      │  • Domain Lock: AtomicBoolean(false)                      │
      │  • Method: recalculateClustersAsync()                     │
      │    - compareAndSet(false, true) -> rejects concurrent runs│
      │    - try { recalculateClustersWithProgress() }            │
      │    - finally { isRecalculating.set(false); status = null }│
      │  • Recovery: refreshClusters() self-heals empty state     │
      └───────────────────────────────────────────────────────────┘
```

### 5.1 Lifecycle Decoupling & CoroutineScope (Auditor Recommendation 1)
* Recalculation is an asynchronous, intensive process involving spatial arithmetic and SQLite batch operations.
* **Tethering Risk**: Launching recalculation within a Composable scope (`rememberCoroutineScope()`) or ViewModel scope (`viewModelScope`) cancels the operation if the user navigates away from `AdvancedTuningScreen`, risking aborted transactions and corrupt database state.
* **Decoupled Architecture**:
  - `WorkoutClusterRepository` manages a dedicated, persistent coroutine scope:
    `private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)`.
  - Recalculation is triggered via `WorkoutClusterRepository.getInstance(context).recalculateClustersAsync()`.
  - When the athlete navigates away, navigates to other tabs, or minimizes the application, the coroutine executes uninterrupted to completion.

### 5.2 Domain-Layer State Lock & Concurrency Control (Auditor Recommendation 2)
* To guarantee complete protection against race conditions and concurrent recalculation requests:
  - **Domain-Layer Lock**: `WorkoutClusterRepository` declares `private val isRecalculatingLock = java.util.concurrent.atomic.AtomicBoolean(false)`.
  - In `recalculateClustersAsync()`:
    ```kotlin
    if (!isRecalculatingLock.compareAndSet(false, true)) {
        Log.w(TAG, "Recalculation already active. Rejecting duplicate trigger.")
        return
    }
    ```
  - **UI-Layer Lock**: In `ClusterTuningSection.kt`, while `migrationStatus != null`:
    - The Recalculate button is disabled (`enabled = false`).
    - The button displays a `CircularProgressIndicator(size = 16.dp, strokeWidth = 2.dp)` and `R.string.cluster_tuning_recalculating`.
    - All tolerance sliders and switches in `ClusterTuningContent` are disabled (`enabled = false`), preventing parameter mutations while calculation is active.

### 5.3 Rollback Strategy, Failure Recovery & Process Death (Auditor Recommendation 3)
* **Try-Finally Lock & Status Guarantee**:
  - `recalculateClustersAsync()` wraps execution in `try { ... } catch (t: Throwable) { ... } finally { isRecalculatingLock.set(false); _migrationStatus.value = null }`.
  - This ensures that even if an unhandled SQLite exception or runtime error occurs, the repository lock is released and the UI immediately clears the loading state without requiring an application restart.
* **Process-Death Recovery (Self-Healing Bootstrapper)**:
  - If the application process is terminated by the OS (low memory killer) midway through recalculation:
    - On the subsequent launch, `WorkoutClusterRepository.refreshClusters()` executes line 88:
      `if (currentClusters.isEmpty() && _migrationStatus.value == null) recalculateClustersWithProgress()`
    - The bootstrapper automatically detects the incomplete/empty state and safely runs a clean background recalculation from history, ensuring zero permanent data loss or orphaned state.

### 5.4 Configuration Data Persistence & Migration (Auditor Recommendation 4)
* **No Orphaned Configuration**:
  - In Sprint 2026-42.1 (ATT-3355), `TuningPreferencesDefaults` established the canonical keys in `TuningPreferencesDataStore` and `TrainingApplication.cSharedPreferences`:
    `clusterTolEndpoints`, `clusterTolApex`, `clusterTolDistance`, `clusterTolAltitudePos`, `clusterUseSportType`, `clusterUseAltitudePos`.
  - Both the old `ClusterTuningScreen.kt` and `ClusterTuningSection.kt` share these exact keys.
  - When the athlete confirms recalculation in `ClusterTuningSection.kt`, current slider values are persisted synchronously to `TrainingApplication` / `TuningPreferencesDataStore` before launching the repository task. Zero migration scripts or legacy preference purging are required.

---

## 6. Atomic Implementation Steps

### Step 1: Add Domain Lock and Async Recalculation to `WorkoutClusterRepository.kt`
* Edit `WorkoutClusterRepository.kt`:
  - Declare `private val isRecalculatingLock = AtomicBoolean(false)`.
  - Declare `private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)`.
  - Implement `fun recalculateClustersAsync()`:
    ```kotlin
    fun recalculateClustersAsync() {
        if (!isRecalculatingLock.compareAndSet(false, true)) return
        repositoryScope.launch {
            try {
                recalculateClustersWithProgress()
            } catch (t: Throwable) {
                android.util.Log.e("WorkoutClusterRepo", "Recalculation failed", t)
            } finally {
                isRecalculatingLock.set(false)
                _migrationStatus.value = null
            }
        }
    }
    ```

### Step 2: Excising Cluster Tuning Entry Point from Favorite Routes
* Edit `WorkoutClustersTabsScreen.kt`:
  - Remove `onTuneClick: () -> Unit` parameter from `WorkoutClustersTabsScreen`.
  - Remove `IconButton(onClick = onTuneClick)` from lines 265–272 in the collapsing app bar title row.
* Edit `WorkoutClustersScreen.kt`:
  - Remove `var isTuning by rememberSaveable { mutableStateOf(false) }`.
  - Remove `recalculationFinished` observation for `isTuning`.
  - Remove `when { isTuning -> ... }` branch.
  - Remove `onTuneClick = { isTuning = true }` from `WorkoutClustersTabsScreen` invocation.

### Step 3: Enabling / Disabling Support in `ClusterTuningContent.kt`
* Edit `ClusterTuningContent.kt`:
  - Add `enabled: Boolean = true` to `fun ClusterTuningContent(...)`.
  - Propagate `enabled` to master `Slider`, details `Switch`, sport type `Switch`, altitude `Switch`, and `TuningSlider` composables.

### Step 4: Implementing Recalculate Action & Confirmation Guard in `ClusterTuningSection.kt`
* Edit `ClusterTuningSection.kt`:
  - Collect `val migrationStatus by WorkoutClusterRepository.getInstance(LocalContext.current.applicationContext).migrationStatus.collectAsStateWithLifecycle()`.
  - Derive `val isRecalculating = migrationStatus != null`.
  - Add state `var showConfirmDialog by rememberSaveable { mutableStateOf(false) }`.
  - Pass `enabled = !isRecalculating` to `ClusterTuningContent`.
  - Render Recalculate button below `ClusterTuningContent`:
    - `Button(onClick = { showConfirmDialog = true }, enabled = !isRecalculating)`
    - Content: if `isRecalculating`, show `CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)` + `Spacer(8.dp)` + `Text(stringResource(R.string.cluster_tuning_recalculating))`.
    - Else: `Text(stringResource(R.string.cluster_tuning_recalculate_button))`.
  - Render `AlertDialog` when `showConfirmDialog == true`:
    - `containerColor = MaterialTheme.colorScheme.surface`
    - `tonalElevation = 0.dp`
    - Title: `Text(stringResource(R.string.cluster_tuning_recalculate_button))`
    - Text: `Text(stringResource(R.string.cluster_tuning_warning))`
    - Confirm: `TextButton(onClick = { showConfirmDialog = false; /* save & trigger async */ }) { Text(stringResource(R.string.cluster_tuning_recalculate_button), color = MaterialTheme.colorScheme.error) }`
    - Dismiss: `TextButton(onClick = { showConfirmDialog = false }) { Text(stringResource(R.string.cancel)) }`
  - On Confirm:
    - Save parameters to `TrainingApplication` / `TuningPreferencesDataStore`.
    - Invoke `WorkoutClusterRepository.getInstance(context.applicationContext).recalculateClustersAsync()`.

### Step 5: Updating Contract & Unit Tests in `ClusterTuningContractTest.kt`
* Edit `ClusterTuningContractTest.kt`:
  - `testWorkoutClusters_hasNoTuningScreenOrAction`:
    - Assert `WorkoutClustersTabsScreen.kt` does NOT contain `onTuneClick` or `ic_settings_24`.
    - Assert `WorkoutClustersScreen.kt` does NOT contain `ClusterTuningScreen` or `isTuning`.
  - `testClusterTuningSection_hasRecalculateActionAndConfirmationDialog`:
    - Assert `ClusterTuningSection.kt` contains `cluster_tuning_recalculate_button` and `AlertDialog` with `cluster_tuning_warning` and `colorScheme.surface` with `0.dp` tonal elevation.
  - `testClusterTuningSection_domainLockAndProgressState`:
    - Assert `ClusterTuningSection.kt` observes `migrationStatus`, guards `enabled = !isRecalculating`, and triggers `recalculateClustersAsync()`.
  - `testWorkoutClusterRepository_hasDomainLockAndAsyncScope`:
    - Assert `WorkoutClusterRepository.kt` defines `isRecalculatingLock`, `repositoryScope`, and `recalculateClustersAsync()`.
  - `testClusterTuningContent_isEncapsulatedAndReusable`:
    - Assert `ClusterTuningContent.kt` declares `enabled: Boolean = true`.

### Step 6: Verification & Modularity Audit
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
     - `testWorkoutClusters_hasNoTuningScreenOrAction`
     - `testClusterTuningSection_hasRecalculateActionAndConfirmationDialog`
     - `testClusterTuningSection_domainLockAndProgressState`
     - `testWorkoutClusterRepository_hasDomainLockAndAsyncScope`
     - `testClusterTuningContent_isEncapsulatedAndReusable`
   - `TuningPreferencesDataStoreClusterTest`:
     - Assert DataStore clustering parameters retain defaults and clamping bounds.
   - `AdvancedTuningModularityTest`:
     - Assert all files in `ui.settings.tuning` remain strictly `< 400` lines.
2. **Clean-Room Regression Command**:
   `./gradlew testDebugUnitTest`
   - Expected result: 100% test pass rate with zero regressions.
