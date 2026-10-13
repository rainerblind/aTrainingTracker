# Stage 4: Implementation Summary - ATT-3355: Move workout route clustering parameters to Advanced Settings

**Ticket**: [ATT-3355](https://rainerblind.atlassian.net/browse/ATT-3355)  
**Sub-task**: [ATT-3436](https://rainerblind.atlassian.net/browse/ATT-3436) (`[Implementation]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-SET-075` (*Dedicated Workout Route Clustering Section in Advanced Settings, Single Source of Truth, and Modularity Preservation*)  
**Test Mapping**: `TST-UI-318`  
**Branch**: `feature/ATT-3355`  
**Author**: AI Agent 1 (Software Construction Engineer)  
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

## 1. Implementation Summary

### 1.1 `TuningPreferencesDataStore.kt` & `TuningPreferencesDefaults.kt`
- Added canonical clustering defaults in `TuningPreferencesDefaults`:
  - `CLUSTER_TOL_ENDPOINTS = 200.0f` (bounds: 10f..500f)
  - `CLUSTER_TOL_APEX = 400.0f` (bounds: 10f..500f)
  - `CLUSTER_TOL_DISTANCE = 0.20f` (bounds: 0.01f..0.20f)
  - `CLUSTER_TOL_ALTITUDE_POS = 400.0f` (bounds: 10f..500f)
  - `CLUSTER_USE_SPORT_TYPE = true`
  - `CLUSTER_USE_ALTITUDE_POS = true`
- Registered Preference keys in `TuningPreferencesDataStore.Companion`:
  - `KEY_CLUSTER_TOL_ENDPOINTS`, `KEY_CLUSTER_TOL_APEX`, `KEY_CLUSTER_TOL_DISTANCE`, `KEY_CLUSTER_TOL_ALTITUDE_POS`, `KEY_CLUSTER_USE_SPORT_TYPE`, `KEY_CLUSTER_USE_ALTITUDE_POS`.
- Extended `TuningConfig` data class with default values matching `TuningPreferencesDefaults`.
- Updated `updateTuningConfig()` and atomic `resetToDefaults()` to persist changes synchronously into `TrainingApplication.cSharedPreferences` so that background processes (`WorkoutClusterEngine`, `LegacyImportEngine`, `TrackerService`) read updated values immediately without restarting.

### 1.2 Shared Composable `ClusterTuningContent.kt` (Rule 36 - Single Source of Truth)
- Extracted and modularized `ClusterTuningContent` and `TuningSlider` into `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterTuningContent.kt` (< 270 lines).
- Refactored `ClusterTuningScreen.kt` to delegate to `ClusterTuningContent`, preserving the contextual cluster recalculation button (`recalculateClusters()`).

### 1.3 `ClusterTuningSection.kt` (Modular Accordion Section)
- Created `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/ClusterTuningSection.kt` (69 lines, < 400 lines threshold).
- Embedded `ClusterTuningContent` cleanly inside the modular category structure.

### 1.4 `AdvancedTuningAccordion.kt` & Lifecycle Reorganization
- Added `ROUTE_CLUSTERING` to `enum class TuningSection`, positioned in Phase C (History, Routes & Post-Workout) between `WORKOUT_MASKS_CARDS` and `AFTERMATH_ANALYSIS`.
- Implemented `TuningSubtitleFormatter.formatClusterTuningSubtitle(...)` generating concise live summaries:
  - Formats: e.g. `End: 0.200km · Apex: 0.400km · Dist: 20% · Alt: 0.400km` (or imperial `mi` equivalent) and adapts when altitude extrema matching is disabled.

### 1.5 Integration in `AdvancedTuningScreen.kt` & `AdvancedTuningDialog.kt`
- Added state bindings for all 6 clustering parameters.
- Rendered `TuningAccordionSection` with `Icons.Default.Hub`, title `R.string.tuning_cat_route_clustering`, dynamic subtitle, and embedded `ClusterTuningSection`.
- Included clustering parameters in `handleSave()` and `handleResetDefaults()`.

### 1.6 9-Language Localization Parity
- Added string resource tokens with 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT:
  - `tuning_cat_route_clustering`
  - `cluster_tuning_show_details`
  - `cluster_tuning_master_label`
  - `cluster_tuning_strict`
  - `cluster_tuning_relaxed`

---

## 2. Verification & Automated Tests

All tests verify `TST-UI-318` clauses:
1. `TST-UI-318.1`: Route Clustering Section & Controls Verification (`ClusterTuningSection.kt`).
2. `TST-UI-318.2`: Lifecycle Category Ordering & Subtitle Formatter (`AdvancedTuningAccordionTest.kt`).
3. `TST-UI-318.3`: DataStore Persistence & Factory Reset (`TuningPreferencesDataStoreClusterTest.kt`, `TuningConfigTest.kt`).
4. `TST-UI-318.4`: Single Source of Truth & DRY Call-Site Contracts (`ClusterTuningContractTest.kt`).
5. `TST-UI-318.5`: Modularity & File Size Limits (`AdvancedTuningModularityTest.kt`).
6. `TST-UI-318.6`: 9-Language Localization Audit (`TranslationParityTest.kt`).
7. `TST-UI-318.7`: Full Test Suite Clean-Room Pass Rate (`./gradlew testDebugUnitTest`).
