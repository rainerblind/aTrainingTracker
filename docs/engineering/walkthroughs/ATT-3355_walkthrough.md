# Stage 5: Walkthrough - ATT-3355: Move workout route clustering parameters to Advanced Settings

**Ticket**: [ATT-3355](https://rainerblind.atlassian.net/browse/ATT-3355)  
**Sub-task**: [ATT-3437](https://rainerblind.atlassian.net/browse/ATT-3437) (`[Test]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-SET-075`  
**Test Mapping**: `TST-UI-318`  
**Branch**: `feature/ATT-3355`  
**Author**: AI Agent 1 (Verification & Quality Engineer)  
**Date**: 2026-10-13  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - Amends `REQ-SET-010` (*Tuning Parameters UI*) and `REQ-SET-065` (*Altitude Extrema-Aware 3D Route Clustering & Database Migration*), integrating with `REQ-UI-357` (*Dedicated Full-Screen Advanced Settings Screen Architecture, Zero-Dismissal Scrolling, and Lifecycle-Driven Category Reorganization*), formulated as `REQ-SET-075` (*Dedicated Workout Route Clustering Section in Advanced Settings, Single Source of Truth, and Modularity Preservation*). Targets `ui/settings/tuning/categories/ClusterTuningSection.kt`, `ui/settings/tuning/AdvancedTuningAccordion.kt`, `ui/settings/tuning/AdvancedTuningScreen.kt`, `ui/settings/tuning/AdvancedTuningDialog.kt`, `settings/TuningPreferencesDataStore.kt`, `ui/clusters/ClusterTuningScreen.kt`, and `migration/ImportBackupTabsScreen.kt`.
2. **Historical Origin & Commit Trace**:
   - `REQ-SET-010` was created in Sprint 2026-40.2 to establish algorithmic sensitivity sliders for 2D spatial clustering.
   - `REQ-SET-065` was added in Sprint 2026-40.5 (`ATT-502` / `ATT-503`) to support 3D altitude extrema coordinates (`minAltitudePos`, `maxAltitudePos`, `altitudePositionTolerance`).
   - `REQ-UI-357` was established in Sprint 2026-42.1 (`ATT-3344`) to migrate Advanced Settings to a dedicated full-screen Compose destination with a 3-phase athletic lifecycle sequence.
3. **Root Reason for Existing Formulation**:
   - Clustering tolerances originally resided inside a standalone screen (`ClusterTuningScreen.kt`) opened from an overflow menu in `WorkoutClustersScreen.kt`. Later, when TCX import needed sensitivity adjustment (`ImportBackupTabsScreen.kt`), the controls were embedded alongside pre-import workflows. Consequently, algorithmic route matching configuration was isolated outside the authoritative Advanced Settings catalog, leading to fragmentation and duplicate code patterns across screens in violation of the Single Source of Truth and DRY principles (Rule 36).
4. **Preservation of Core Invariants**:
   - Invariant 1: All six mathematical tuning parameters, their DataStore/SharedPreferences keys (`clusterTolEndpoints`, `clusterTolApex`, `clusterTolDistance`, `clusterTolAltitudePos`, `clusterUseSportType`, `clusterUseAltitudePos`), default values (200m, 400m, 0.20, 400m, true, true), value ranges (10..500m, 10..500m, 0.01..0.20, 10..500m), and storage semantics in `TrainingApplication.java` MUST remain strictly preserved.
   - Invariant 2: Contextual cluster recalculation triggers in `WorkoutClustersScreen` and pre-import tuning in `ImportBackupTabsScreen` MUST remain 100% operational through shared, reusable composables.
   - Invariant 3: Modularity constraint (< 400 lines of code per file in `ui.settings.tuning`) MUST be strictly maintained (`AdvancedTuningModularityTest`).
   - Invariant 4: 100% 9-language translation parity maintained across all supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 1. Executive Summary

This deliverable documents Stage 5 verification and walkthrough for **ATT-3355**. Previously, the algorithmic parameters governing the 3D topological route clustering engine were isolated in a standalone screen (`ClusterTuningScreen.kt`) and duplicated in import workflows (`ImportBackupTabsScreen.kt`). 

ATT-3355 consolidates the canonical configuration of workout clustering tolerances into **Advanced Settings** as a modular accordion category in full compliance with ASPICE and Rule 36:
1. **Modular Accordion Section (`ClusterTuningSection.kt`)**: Added `ROUTE_CLUSTERING` to `TuningSection`, positioned in Phase C (History, Routes & Post-Workout) between `WORKOUT_MASKS_CARDS` and `AFTERMATH_ANALYSIS`. Rendered with `Icons.Default.Hub`, title `R.string.tuning_cat_route_clustering`, dynamic live subtitle formatting via `TuningSubtitleFormatter.formatClusterTuningSubtitle(...)`, and initially collapsed state (`emptySet()`).
2. **Single Source of Truth & DRY Refactoring (Rule 36)**: Extracted `ClusterTuningContent.kt` (< 270 lines) containing the master sensitivity slider, detail sliders (Endpoints, Apex, Distance, Altitude Position), sport type toggle, and altitude extrema toggle. Both `ClusterTuningSection.kt`, `ClusterTuningScreen.kt`, and `ImportBackupTabsScreen.kt` share this underlying composable with zero code duplication.
3. **DataStore, Defaults & Atomic Factory Reset Integration**: Integrated clustering parameters into `TuningPreferencesDefaults.kt`, `TuningConfig.kt`, and `TuningPreferencesDataStore.kt`. Updates are synchronized immediately to `TrainingApplication.cSharedPreferences` so background engines (`WorkoutClusterEngine`, `LegacyImportEngine`) reflect adjustments without restarting. Triggering "Auf Standardwerte zurücksetzen" (Reset to Defaults) restores all clustering parameters to canonical defaults.
4. **Modularity & Localization Integrity**: All files in `ui.settings.tuning` remain strictly below 400 lines (`AdvancedTuningModularityTest`). Complete 9-language translation coverage is verified across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 2. Verification Results

### 2.1 Targeted Unit & Contract Testing

#### `ClusterTuningContractTest` & `ClusterTuningSectionTest`
- `ClusterTuningContractTest`: **PASSED** (Verifies `ClusterTuningScreen` delegates to `ClusterTuningContent` and contextual recalculation remains functional).
- `ClusterTuningSectionTest`: **PASSED** (Verifies section rendering, master slider, individual tolerance sliders, and toggle switches).

#### `AdvancedTuningAccordionTest` (Lifecycle Category Sequence & Formatter)
- `testTuningSection_enumSequenceMatchesLifecycleOrdering`: **PASSED** (Asserts `ROUTE_CLUSTERING` is in Phase C between `WORKOUT_MASKS_CARDS` and `AFTERMATH_ANALYSIS`).
- `testFormatClusterTuningSubtitle`: **PASSED** (Verifies metric and imperial live subtitle string generation).

#### `TuningPreferencesDataStoreClusterTest` & `TuningConfigTest` (Persistence & Factory Reset)
- `testClusteringDefaults_matchSpecification`: **PASSED** (200m, 400m, 0.20, 400m, true, true).
- `testUpdateClusteringConfig_persistsSynchronously`: **PASSED**.
- `testResetToDefaults_resetsClusteringParameters`: **PASSED**.

#### `AdvancedTuningModularityTest` (File Size Constraints)
- `testAllTuningFiles_under400Lines`: **PASSED** (All files in `ui.settings.tuning` verified < 400 lines: `AdvancedTuningScreen.kt` = 398 lines, `AdvancedTuningDialog.kt` = 387 lines, `ClusterTuningSection.kt` = 69 lines).

#### `TranslationParityTest`
- `TranslationParityTest`: **PASSED** (100% translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT for all clustering tokens).

### 2.2 Clean-Room Test Suite Execution
- **Command**: `./gradlew testDebugUnitTest`
- **Result**: **BUILD SUCCESSFUL** (100% pass rate across entire suite, zero regressions).

---

## 3. Living Documentation Status

- **`docs/requirements.md`**: `REQ-SET-075` verified.
- **`docs/tests.md`**: `TST-UI-318` updated to `Verified`.
- **Governance Check**: `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-42.1` -> Clean pass.

---

## 4. Subtask & Ticket Advancement Plan

1. Stage 5 subtask **ATT-3437** (`[Test]`) description updated with this walkthrough.
2. Subtask transitioned to `In Überprüfung` and Gate 5 audit initiated via `python3 tools/review_agent.py audit ATT-3437`.
3. Upon Gate 5 sign-off (`RECOMMEND PASS`), subtask moved to `Erledigt` via transition `freigabe`.
4. Parent ticket **ATT-3355** transitioned to `Final Review (Human)`.
5. Changes committed to `feature/ATT-3355`.
6. Branch `feature/ATT-3355` merged into `sprint/2026-42.1` with `--no-ff`, and `feature/ATT-3355` deleted.
