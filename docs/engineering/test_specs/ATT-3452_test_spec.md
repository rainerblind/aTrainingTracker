# Stage 2: Requirement & Test Specification - ATT-3452: Remove cluster tuning from favorite routes and add recalculate action with confirmation to Advanced Settings

**Ticket**: [ATT-3452](https://atrainingtracker.atlassian.net/browse/ATT-3452)  
**Sub-task**: [ATT-3468](https://atrainingtracker.atlassian.net/browse/ATT-3468) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.2`  
**Active Sprint**: `Sprint 2026-42.2`  
**Requirement Mapping**: `REQ-SET-075` (*Dedicated Workout Route Clustering Section in Advanced Settings, Single Source of Truth, and Modularity Preservation*), amending `REQ-SET-010`, superseding `REQ-SET-014`  
**Test Spec ID**: `TST-UI-321`  
**Branch**: `feature/ATT-3452`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Requirement Specification (Amending REQ-SET-075)

### 1.1 Problem Statement & Rationale
Workout route clustering configuration was historically initiated from a gear/settings icon in the collapsing header of the Favorite Routes screen (`WorkoutClustersTabsScreen.kt` / `WorkoutClustersScreen.kt`), opening a dedicated full-screen tuning view (`ClusterTuningScreen.kt`).

In Sprint 2026-42.1 (ATT-3355 / REQ-SET-075), all algorithmic route clustering parameters were consolidated into Advanced Settings (`AdvancedTuningScreen.kt` / `ClusterTuningSection.kt`) as a dedicated accordion section using shared controls (`ClusterTuningContent.kt`). However, two issues remained:
1. **Redundant Entry Point in Favorite Routes**: The header settings icon in `WorkoutClustersTabsScreen.kt` still navigated to `ClusterTuningScreen.kt`, creating dual competing configuration paths for the same algorithmic parameters and violating the Single Source of Truth principle (Rule 36).
2. **Missing Full Recalculation Action in Advanced Settings**: While parameters could be tuned and saved in Advanced Settings, athletes had no direct mechanism to recalculate all historical clusters without navigating away. Furthermore, cluster recalculation is an inherently destructive operation (resetting learned cluster names, custom boundaries, and manual assignments), yet lacked a mandatory Material 3 confirmation guard.

ATT-3452 resolves both issues by:
* Removing the cluster tuning settings button and `onTuneClick` callback from `WorkoutClustersTabsScreen.kt` and excising the redundant `isTuning` state handling and `ClusterTuningScreen` from `WorkoutClustersScreen.kt`.
* Adding a dedicated "Recalculate All Clusters" button (`R.string.cluster_tuning_recalculate_button`) to `ClusterTuningSection.kt` in Advanced Settings.
* Guarding the recalculate action with a mandatory modal Material 3 confirmation `AlertDialog` displaying `R.string.cluster_tuning_warning` with high-contrast error confirmation action and cancel dismissal.
* Decoupling the asynchronous recalculation task from UI lifecycle scopes via singleton `WorkoutClusterRepository.getInstance(context)`.
* Synchronizing recalculation progress via `WorkoutClusterRepository.migrationStatus: StateFlow<MigrationStatus?>`, disabling the button and sliders during execution, and displaying progress indicators.
* Preserving process-death recovery via the existing self-healing bootstrapper in `WorkoutClusterRepository.refreshClusters()`.
* Maintaining strict modularity limits (< 400 lines) across all files in `ui.settings.tuning`.

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-SET-075` (*Dedicated Workout Route Clustering Section in Advanced Settings, Single Source of Truth, and Modularity Preservation*), amending `REQ-SET-010` (*Tuning Parameters UI*), `REQ-SET-011` (*Clustering Recalculation*), and superseding `REQ-SET-014` (*Auto-Dismiss Tuning UI*).
* **Historical Origin & Commit Trace**:
  - `REQ-SET-010` & `REQ-SET-014`: Sprint 2026-40.2 (initial route clustering UI and auto-dismiss after recalculation).
  - `REQ-SET-075`: Sprint 2026-42.1 (ATT-3355, commit `d9e03f19` / `fe331ca8`), which created `ClusterTuningSection.kt` and `ClusterTuningContent.kt`.
* **Root Reason for Existing Formulation**: When ATT-3355 introduced `ClusterTuningSection.kt` into Advanced Settings, the legacy `ClusterTuningScreen.kt` and its header trigger in `WorkoutClustersTabsScreen.kt` were left in place as a temporary fallback to ensure athletes using the favorite routes screen did not lose the recalculation button before it could be integrated into Advanced Settings.
* **Preservation of Core Invariants**:
  - All 6 clustering parameters (`clusterTolEndpoints`, `clusterTolApex`, `clusterTolDistance`, `clusterTolAltitudePos`, `clusterUseSportType`, `clusterUseAltitudePos`), DataStore keys, and factory reset defaults remain 100% intact.
  - Core mathematical clustering logic in `WorkoutClusterEngine.kt` and database operations in `WorkoutClusterDatabaseManager.kt` remain unchanged.
  - Modularity constraint: Every file in `ui/settings/tuning/` strictly obeys `< 400` lines (`AdvancedTuningModularityTest.kt`).
  - 100% 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

### 1.2 Functional & Architectural Requirements
1. **Favorite Routes Header & Screen Cleanup (`WorkoutClustersTabsScreen.kt`, `WorkoutClustersScreen.kt`)**:
   - `WorkoutClustersTabsScreen` SHALL NOT display a tuning/settings `IconButton` in its collapsing top app bar.
   - The `onTuneClick: () -> Unit` parameter SHALL be removed from `WorkoutClustersTabsScreen`.
   - `WorkoutClustersScreen` SHALL NOT declare or observe `isTuning: Boolean` state, and SHALL NOT compose `ClusterTuningScreen`.
2. **Advanced Settings Recalculate Action (`ClusterTuningSection.kt`)**:
   - `ClusterTuningSection` SHALL include an explicit action button with label `R.string.cluster_tuning_recalculate_button`.
   - Tapping this button SHALL NOT immediately trigger recalculation; it SHALL open a modal confirmation `AlertDialog`.
3. **Material 3 Confirmation Guard Dialog (`ClusterTuningSection.kt`)**:
   - The confirmation dialog SHALL render with:
     - `containerColor = MaterialTheme.colorScheme.surface`
     - `tonalElevation = 0.dp`
     - Title: `R.string.cluster_tuning_recalculate_button`
     - Message: `R.string.cluster_tuning_warning` (*Warning: Recalculating will wipe all learned cluster names and grouping, then rebuild them from history using these new parameters.*)
     - Confirm button: Text button with `R.string.cluster_tuning_recalculate_button`, styled in high-contrast `MaterialTheme.colorScheme.error`.
     - Dismiss button: Text button with `R.string.cancel`.
   - The dialog MUST be a blocking modal guard without a "Don't ask again" option.
4. **State Management, Concurrency & Lifecycle Decoupling**:
   - Upon confirmation, active slider parameters SHALL be persisted immediately to `TrainingApplication` / `TuningPreferencesDataStore`.
   - Recalculation SHALL execute via `WorkoutClusterRepository.getInstance(context).recalculateClustersWithProgress()`, running in the repository coroutine scope decoupled from transient Composable lifecycles.
   - `ClusterTuningSection` SHALL collect `WorkoutClusterRepository.migrationStatus: StateFlow<MigrationStatus?>`.
   - While `migrationStatus != null`:
     - The Recalculate button SHALL be disabled (`enabled = false`) and display a `CircularProgressIndicator` alongside `R.string.cluster_tuning_recalculating`.
     - All tolerance sliders and switches in `ClusterTuningContent` SHALL be disabled (`enabled = false`) to prevent parameter mutation during calculation.
5. **Process-Death Recovery**:
   - Process interruption during calculation is safely handled by the existing self-healing bootstrapper in `WorkoutClusterRepository.refreshClusters()` (lines 88–92), which detects an empty cluster table and triggers a background recalculation from workout history upon subsequent app launch.
6. **Modularity Invariant Protection**:
   - `ClusterTuningSection.kt` (currently 69 lines) SHALL remain strictly `< 200` lines (far below the `< 400` limit enforced by `AdvancedTuningModularityTest.kt`).
   - `AdvancedTuningScreen.kt` (389 lines) SHALL NOT receive any additional lines.

---

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1: Header Cleanup in Favorite Routes**:
  * *Given* an athlete viewing the Favorite Routes screen (`WorkoutClustersTabsScreen`),
  * *When* inspecting the collapsing top app bar,
  * *Then* zero cluster tuning/settings buttons are visible and the bar contains only standard navigation and search controls.
* **Criterion 2: Dedicated Recalculate Action in Advanced Settings**:
  * *Given* an athlete navigating to Advanced Settings -> Route & Workout Clustering (`ClusterTuningSection`),
  * *When* expanding the accordion section,
  * *Then* a dedicated "Recalculate All Clusters" button is displayed below the tuning controls.
* **Criterion 3: Mandatory Confirmation Guard Dialog**:
  * *Given* the athlete tapping "Recalculate All Clusters",
  * *When* the action is triggered,
  * *Then* a modal confirmation `AlertDialog` appears with title, warning message (`cluster_tuning_warning`), an error-colored confirmation button, and a cancel button, with no background calculation starting prior to confirmation.
* **Criterion 4: Dialog Cancellation**:
  * *Given* the confirmation dialog visible on screen,
  * *When* the athlete taps "Cancel" or dismisses the dialog,
  * *Then* the dialog closes and zero clusters are wiped or recalculated.
* **Criterion 5: Execution, Concurrency Guard & Progress Feedback**:
  * *Given* the confirmation dialog visible on screen,
  * *When* the athlete confirms recalculation,
  * *Then* active tolerances are persisted, recalculation begins in the repository background scope, the button is disabled with a progress indicator, and tolerance sliders are disabled.
* **Criterion 6: Lifecycle Decoupling & Recovery**:
  * *Given* an active recalculation in progress,
  * *When* the athlete navigates back or leaves Advanced Settings,
  * *Then* recalculation continues uninterrupted to completion, and returning to the screen restores progress observation.
* **Criterion 7: 100% 9-Language Localization Parity**:
  * *Given* any supported locale (EN, DE, ES, FR, IT, JA, NL, PL, PT),
  * *When* viewing the button and confirmation dialog,
  * *Then* all text (`cluster_tuning_recalculate_button`, `cluster_tuning_warning`, `cluster_tuning_recalculating`, `cancel`) renders cleanly without missing translations.
* **Criterion 8: Modularity & Clean-Room Suite**:
  * *Given* all files in `ui/settings/tuning`,
  * *When* checking file lengths,
  * *Then* all files remain strictly `< 400` lines, and the full test suite passes 100%.

---

## 2. Test Specification (TST-UI-321)

### Test Case 1: `testWorkoutClustersTabsScreen_hasNoTuneAction` (`[TST-UI-321.1]`)
* **Scope**: Contract / Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/ClusterTuningContractTest.kt`
* **Preconditions**: `WorkoutClustersTabsScreen.kt` and `WorkoutClustersScreen.kt` source files loaded.
* **Action**:
  1. Inspect `WorkoutClustersTabsScreen.kt` for `onTuneClick` parameter and `R.drawable.ic_settings_24` / `Icons.Default.Settings` in app bar.
  2. Inspect `WorkoutClustersScreen.kt` for `isTuning` state variable and `ClusterTuningScreen` invocation.
* **Expected Result**:
  - `WorkoutClustersTabsScreen.kt` does NOT declare `onTuneClick` or render a tuning settings icon.
  - `WorkoutClustersScreen.kt` does NOT declare `isTuning` or invoke `ClusterTuningScreen`.

### Test Case 2: `testClusterTuningSection_hasRecalculateActionAndConfirmationDialog` (`[TST-UI-321.2]`)
* **Scope**: Contract / Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/ClusterTuningContractTest.kt`
* **Preconditions**: `ClusterTuningSection.kt` source file loaded.
* **Action**:
  1. Verify `ClusterTuningSection.kt` contains the recalculate button referencing `R.string.cluster_tuning_recalculate_button`.
  2. Verify `ClusterTuningSection.kt` declares a confirmation `AlertDialog` referencing `R.string.cluster_tuning_warning`.
  3. Verify `AlertDialog` uses `containerColor = MaterialTheme.colorScheme.surface` and `tonalElevation = 0.dp`.
  4. Verify confirmation button uses `MaterialTheme.colorScheme.error` styling.
* **Expected Result**:
  - All UI elements and Material 3 design tokens are present and strictly match the specification.

### Test Case 3: `testClusterTuningSection_concurrencyAndProgressState` (`[TST-UI-321.3]`)
* **Scope**: Contract / Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/ClusterTuningContractTest.kt`
* **Preconditions**: `ClusterTuningSection.kt` source file loaded.
* **Action**:
  1. Verify `ClusterTuningSection.kt` collects `migrationStatus` from `WorkoutClusterRepository`.
  2. Verify the Recalculate button is disabled (`enabled = migrationStatus == null` or equivalent).
  3. Verify `CircularProgressIndicator` and `R.string.cluster_tuning_recalculating` are displayed when recalculation is active.
  4. Verify sliders in `ClusterTuningContent` receive `enabled = migrationStatus == null` (or `!isRecalculating`).
* **Expected Result**:
  - Full concurrency isolation and double-tap prevention verified.

### Test Case 4: `testAdvancedTuningModularity_allFilesUnder400Lines` (`[TST-UI-321.4]`)
* **Scope**: Architectural / Governance Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningModularityTest.kt`
* **Preconditions**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/` directory.
* **Action**: Scan all `.kt` files in `ui/settings/tuning/` and subpackages.
* **Expected Result**: Every file strictly contains `< 400` lines.

### Test Case 5: `testLocalizationParity_clusterTuningStrings` (`[TST-UI-321.5]`)
* **Scope**: Localization Audit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/CockpitTypographyLocalizationTest.kt` or `TranslationParityTest.kt`
* **Preconditions**: XML string resources across all 9 locales: `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`.
* **Action**: Verify presence and non-emptiness of:
  - `cluster_tuning_recalculate_button`
  - `cluster_tuning_warning`
  - `cluster_tuning_recalculating`
  - `cancel`
* **Expected Result**: 100% presence and parity across all 9 languages.

### Test Case 6: `testCleanRoomFullSuiteRegression` (`[TST-UI-321.6]`)
* **Scope**: Full Clean-Room Regression Test
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% unit tests pass with zero regressions.

---

## 3. Traceability Matrix

| Requirement ID | Requirement Summary | Test Case ID | Test Implementation |
|---|---|---|---|
| `REQ-SET-075.1` | Remove tuning entry point from Favorite Routes header & screen | `TST-UI-321.1` | `ClusterTuningContractTest.testWorkoutClustersTabsScreen_hasNoTuneAction` |
| `REQ-SET-075.2` | Recalculate action button in Advanced Settings | `TST-UI-321.2` | `ClusterTuningContractTest.testClusterTuningSection_hasRecalculateActionAndConfirmationDialog` |
| `REQ-SET-075.3` | Material 3 blocking confirmation guard `AlertDialog` | `TST-UI-321.2` | `ClusterTuningContractTest.testClusterTuningSection_hasRecalculateActionAndConfirmationDialog` |
| `REQ-SET-075.4` | Lifecycle decoupling, concurrency isolation & progress indicator | `TST-UI-321.3` | `ClusterTuningContractTest.testClusterTuningSection_concurrencyAndProgressState` |
| `REQ-SET-075.5` | Modularity preservation (< 400 lines) | `TST-UI-321.4` | `AdvancedTuningModularityTest.testAllTuningFilesUnder400Lines` |
| `REQ-SET-075.6` | 9-Language localization parity | `TST-UI-321.5` | `TranslationParityTest` / Contract localization audit |
| `REQ-SET-075.7` | Zero regressions across complete unit test suite | `TST-UI-321.6` | `./gradlew testDebugUnitTest` |
