# Stage 2: Requirement & Test Specification - ATT-3492: Style cluster recalculate button in red and add direct Advanced Settings deep-link to Favorite Routes info

**Ticket**: [ATT-3492](https://atrainingtracker.atlassian.net/browse/ATT-3492)  
**Sub-task**: [ATT-3506](https://atrainingtracker.atlassian.net/browse/ATT-3506) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.3`  
**Requirement Mapping**: `REQ-SET-076` (*Red Cluster Recalculate Button and Direct Advanced Settings Deep-Link from Favorite Routes*), amending `REQ-SET-075` and `REQ-UI-149`  
**Test Spec ID**: `TST-SET-076`  
**Branch**: `feature/ATT-3492`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Requirement Specification (REQ-SET-076)

### 1.1 Problem Statement & Rationale
During Sprint 2026-42.2 Joint Review (ATT-3452), the Product Owner and athletes identified two usability and safety refinements regarding workout route clustering:

1. **Destructive Recalculate Action Styling**:
   In Advanced Settings (`Erweiterte Einstellungen` -> Section 6: `Route & Workout Clustering` / `ClusterTuningSection.kt`), the "Alle Cluster neu berechnen" (Recalculate All Clusters) button currently uses default primary button styling (`ButtonDefaults.buttonColors()` containerColor = primary). Because recalculation wipes all existing route cluster groupings, custom labels, and user-refined centroids from SQLite history before rebuilding, primary blue styling fails to communicate the destructive and irreversible nature of the action before the user taps it. Applying Material 3 error styling (`containerColor = MaterialTheme.colorScheme.error`, `contentColor = MaterialTheme.colorScheme.onError`) immediately signals caution to the athlete, consistent with the Material 3 confirmation guard.

2. **Discoverability & Direct Deep-Linking from Favorite Routes**:
   In Sprint 2026-42.2 (ATT-3452), the cluster tuning settings icon was deliberately removed from the Favorite Routes header (`WorkoutClustersTabsScreen.kt`) to enforce the Single Source of Truth principle (Rule 36). However, the educational info dialog in Favorite Routes (`ClusterInfoDialog.kt`), which explains the 3D topological fingerprint and clustering heuristics to athletes, still described tuning controls as being located "below" ("Weiter unten kannst du die einzelnen Toleranzen individuell anpassen"), without directing users to Advanced Settings or providing a quick way to get there. Athletes learning about sensitivity adjustments had no direct path to navigate to the new location.

ATT-3492 resolves both issues by:
* Styling the "Alle Cluster neu berechnen" action button in `ClusterTuningSection.kt` in Material 3 red (`containerColor = MaterialTheme.colorScheme.error`, `contentColor = MaterialTheme.colorScheme.onError`), ensuring WCAG 2.1 contrast compliance during progress execution.
* Updating Section 4 copy in `ClusterInfoDialog.kt` across all 9 supported locales to explain that cluster tolerances and recalculation are configured in Advanced Settings.
* Adding a direct deep-link action button (`R.string.cluster_info_open_advanced_tuning`) inside Section 4 of `ClusterInfoDialog.kt` that delegates navigation cleanly to the parent container via an explicit callback (`onNavigateToAdvancedSettings: () -> Unit`).
* Maintaining 100% 9-language translation parity (EN, DE, ES, FR, IT, JA, NL, PL, PT) and preserving modularity bounds (< 400 lines).

---

### 1.2 Chesterton's Fence Audit & Requirement Archaeology

* **Original Requirement ID & Target**: Amends `REQ-SET-075` (*Dedicated Workout Route Clustering Section in Advanced Settings, Single Source of Truth, and Modularity Preservation*) and `REQ-UI-149` (*Educational Cluster Information Dialog*).
* **Historical Origin & Commit Trace**:
  - `REQ-UI-149`: Sprint 2026-40.5 (ATT-501, initial introduction of `ClusterInfoDialog.kt`).
  - `REQ-SET-075`: Sprint 2026-42.1 (ATT-3355) and Sprint 2026-42.2 (ATT-3452, moving recalculation to Advanced Settings and removing header tuning button).
* **Root Reason for Existing Formulation**:
  - `ClusterTuningSection.kt` used standard `Button()` without custom colors because the confirmation dialog already used error text color, but the PO requested the initial button itself to be red to warn the user prior to interaction.
  - `ClusterInfoDialog.kt` previously assumed the athlete was already in a screen with sliders right below the info dialog. Once sliders moved to Advanced Settings in ATT-3452, that copy became stale and navigation was severed.
* **Preservation of Core Invariants**:
  - Recalculation confirmation dialog (`AlertDialog`) with surface container and 0.dp tonal elevation remains mandatory.
  - Recalculation execution remains asynchronous in `WorkoutClusterRepository` with `migrationStatus` progress locking.
  - Pure Compose architectural abstraction: All composables delegate navigation up to their enclosing destination via pure lambdas; zero activity-level casts or lifecycle couplings are permitted in composable functions.
  - Modularity constraint (< 400 lines) across all modified files is strictly preserved.
  - 100% 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

### 1.3 Functional & Architectural Requirements

1. **Red Recalculate Button Styling & Accessibility (`ClusterTuningSection.kt`)**:
   - The "Recalculate All Clusters" button (`R.string.cluster_tuning_recalculate_button`) SHALL use Material 3 error button styling:
     ```kotlin
     colors = ButtonDefaults.buttonColors(
         containerColor = MaterialTheme.colorScheme.error,
         contentColor = MaterialTheme.colorScheme.onError,
         disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
         disabledContentColor = MaterialTheme.colorScheme.onError
     )
     ```
   - While recalculation is active (`isRecalculating == true`):
     - The button SHALL remain disabled (`enabled = false`).
     - The `CircularProgressIndicator` SHALL use `color = MaterialTheme.colorScheme.onError`, satisfying WCAG 2.1 contrast against the error container.

2. **Educational Info Dialog Section 4 Update (`ClusterInfoDialog.kt`)**:
   - Section 4 ("Sensitivity & Parameter Tuning" / `cluster_info_tuning_title`) description (`cluster_info_tuning_desc`) SHALL state that cluster matching tolerances (endpoints, apex, distance, altitude) and full cluster recalculation are configured in Advanced Settings (*Erweiterte Einstellungen*).
   - Section 4 SHALL render a direct action button with text resource `R.string.cluster_info_open_advanced_tuning` and leading tune icon (`Icons.Default.Tune`).
   - Clicking this button SHALL execute a strictly ordered state transition:
     1. First, invoke `onDismissRequest()` to immediately dismiss the bottom sheet and teardown dialog state.
     2. Second, invoke `onNavigateToAdvancedSettings()` to transition to the destination, preventing UI state race conditions or backstack flicker.

3. **Pure Navigation Delegation Architecture (Single Source of Truth)**:
   - `ClusterInfoDialog` SHALL require an explicit `onNavigateToAdvancedSettings: () -> Unit` parameter. Composable components SHALL NOT perform Activity-level casts or query global activity singletons.
   - `WorkoutClustersTabsScreen.kt` and `WorkoutClustersScreen.kt` SHALL accept `onNavigateToAdvancedSettings: () -> Unit` and propagate it to `ClusterInfoDialog`.
   - The enclosing parent hosting containers are solely responsible for resolving navigation to `NavRoutes.ADVANCED_SETTINGS`.

4. **100% 9-Language Localization Parity**:
   - String resource `cluster_info_open_advanced_tuning` SHALL be added to all 9 supported locales:
     - EN (`values`): `Open Advanced Settings`
     - DE (`values-de`): `Zu den erweiterten Einstellungen`
     - ES (`values-es`): `Abrir ajustes avanzados`
     - FR (`values-fr`): `Ouvrir les paramètres avancés`
     - IT (`values-it`): `Apri impostazioni avanzate`
     - JA (`values-ja`): `高度な設定を開く`
     - NL (`values-nl`): `Geavanceerde instellingen openen`
     - PL (`values-pl`): `Otwórz ustawienia zaawansowane`
     - PT (`values-pt`): `Abrir configurações avançadas`
   - String resource `cluster_info_tuning_desc` SHALL be updated across all 9 locales to reflect Advanced Settings centralization.

---

### 1.4 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Red Button Styling & Accessible Progress)**:
  * *Given* an athlete navigating to `Advanced Settings` -> `Route & Workout Clustering`,
  * *When* inspecting the "Recalculate All Clusters" button,
  * *Then* the button container color SHALL be `MaterialTheme.colorScheme.error` and content color SHALL be `MaterialTheme.colorScheme.onError`.
  * *When* recalculation is running (`isRecalculating == true`),
  * *Then* the button SHALL be disabled with `disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)`, and the spinning progress indicator color SHALL be `MaterialTheme.colorScheme.onError`.

* **Criterion 2 (Direct Deep-Link Navigation Order & State Transition)**:
  * *Given* an athlete viewing Favorite Routes (`WorkoutClustersTabsScreen`),
  * *When* the athlete taps the top app bar info icon (`Icons.Default.Info`),
  * *Then* `ClusterInfoDialog` SHALL open displaying the updated Section 4 description and a button labeled "Zu den erweiterten Einstellungen" / "Open Advanced Settings".
  * *When* the athlete taps the button,
  * *Then* `onDismissRequest()` SHALL execute before `onNavigateToAdvancedSettings()`, ensuring clean dismissal followed by immediate navigation to Advanced Settings without flicker or state leaks.

* **Criterion 3 (Modularity & Architectural Invariants)**:
  * *Given* modified source files (`ClusterTuningSection.kt`, `ClusterInfoDialog.kt`, `WorkoutClustersTabsScreen.kt`, `WorkoutClustersScreen.kt`),
  * *When* audited by `AdvancedTuningModularityTest`,
  * *Then* every file SHALL remain strictly `< 400` lines of code with zero Activity casts inside composables.

---

## 2. Test Specification (TST-SET-076)

### Test Case 1: `ClusterTuningContractTest` - Red Recalculate Button Styling (`TST-SET-076.1`)
* **Scope**: Contract / Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/ClusterTuningContractTest.kt`
* **Preconditions**: `ClusterTuningSection.kt` exists.
* **Action**: Audit Composable AST and source code of `ClusterTuningSection.kt`.
* **Expected Result**:
  * Asserts `Button(` configures `colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError, disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f), disabledContentColor = MaterialTheme.colorScheme.onError)`.
  * Asserts `CircularProgressIndicator` uses `color = MaterialTheme.colorScheme.onError`.

### Test Case 2: `ClusterInfoDialogTest` - 9-Language Localization Audit (`TST-SET-076.2`)
* **Scope**: Unit & Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterInfoDialogTest.kt`
* **Preconditions**: 9 `strings.xml` files exist in `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
* **Action**: Include `cluster_info_open_advanced_tuning` in `requiredStringKeys` and execute `testAllRequiredClusterInfoKeysExistAcrossAll9Locales`.
* **Expected Result**: 100% translation coverage, zero missing tokens, non-blank strings across all 9 locales.

### Test Case 3: `ClusterInfoDialogContractTest` - Deep-Link Button & Strict Order-of-Operations Contract (`TST-SET-076.3`)
* **Scope**: Structural AST / Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterInfoDialogContractTest.kt`
* **Preconditions**: `ClusterInfoDialog.kt` exists.
* **Action**: Audit source code and parameters of `ClusterInfoDialog.kt`.
* **Expected Result**:
  * Asserts `ClusterInfoDialog` requires `onNavigateToAdvancedSettings: () -> Unit` parameter.
  * Asserts Section 4 renders a button referencing `R.string.cluster_info_open_advanced_tuning` with `Icons.Default.Tune`.
  * Asserts click action executes `onDismissRequest()` strictly *before* `onNavigateToAdvancedSettings()`.
  * Asserts zero direct Activity casts (`(context as? MainActivityWithNavigation)` or `(activity as? ...)`) exist inside `ClusterInfoDialog.kt`.

### Test Case 4: Full Clean-Room Regression Suite (`TST-SET-076.4`)
* **Scope**: Clean-room end-to-end regression
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% pass rate across all project modules, zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-076.1` | Unit/Contract | `ClusterTuningSection.kt` | `REQ-SET-076` | Specified |
| `TST-SET-076.2` | Localization | `ClusterInfoDialogTest` | `REQ-SET-076`, `REQ-UI-106` | Specified |
| `TST-SET-076.3` | Contract | `ClusterInfoDialogContractTest` | `REQ-SET-076` | Specified |
| `TST-SET-076.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
