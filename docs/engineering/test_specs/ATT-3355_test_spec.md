# Stage 2: Requirement & Test Specification - ATT-3355: Move workout route clustering parameters to Advanced Settings

**Ticket**: [ATT-3355](https://rainerblind.atlassian.net/browse/ATT-3355)  
**Sub-task**: [ATT-3434](https://rainerblind.atlassian.net/browse/ATT-3434) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-SET-075` (*Dedicated Workout Route Clustering Section in Advanced Settings, Single Source of Truth, and Modularity Preservation*)  
**Test Spec ID**: `TST-UI-318`  
**Branch**: `feature/ATT-3355`  
**Author**: AI Agent 1 (Implementer)  
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

## 1. Requirement Specification (REQ-SET-075)

### 1.1 Problem Statement & Rationale
Workout route clustering uses a 3D topological spatial matching engine to group similar workouts into reusable favorite routes (clusters). The sensitivity of this algorithm is governed by six mathematical tuning parameters:
1. Endpoint Tolerance (`clusterTolEndpoints`, meters, default: 200m)
2. Apex Tolerance (`clusterTolApex`, meters, default: 400m)
3. Distance Tolerance (`clusterTolDistance`, percent, default: 20%)
4. Altitude Position Tolerance (`clusterTolAltitudePos`, meters, default: 400m)
5. Use Sport Type for Clustering (`clusterUseSportType`, boolean, default: true)
6. Use Altitude Position for Clustering (`clusterUseAltitudePos`, boolean, default: true)

These algorithmic parameters represent power-user expert settings identical in nature to existing Advanced Settings parameters (GPS accuracy filter, barometric altitude window, slope minimum speed). Currently, they reside in a specialized screen (`ClusterTuningScreen.kt`) and are partially duplicated in `ImportBackupTabsScreen.kt`. Relocating the canonical configuration into Advanced Settings (`AdvancedTuningScreen.kt` / `AdvancedTuningDialog.kt`) as a dedicated accordion section establishes a single authoritative home for clustering configuration, eliminates UI code duplication, and enables atomic "Reset to Defaults" management.

### 1.2 Functional & Architectural Requirements
The system SHALL fulfill the following functional and architectural clauses:

1. **Modular Accordion Section Architecture (`ClusterTuningSection.kt`, `AdvancedTuningAccordion.kt`)**:
   - The system SHALL add `ROUTE_CLUSTERING` to `TuningSection` enum in `AdvancedTuningAccordion.kt`, positioned in Phase C (History, Routes & Post-Workout) between `WORKOUT_MASKS_CARDS` and `AFTERMATH_ANALYSIS`.
   - The system SHALL create `ClusterTuningSection.kt` under `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/`, hosting the clustering parameters UI.
   - The accordion card SHALL render `Icons.Default.Hub`, localized title `R.string.tuning_cat_route_clustering` (EN: "Route & Workout Clustering", DE: "Routen- & Workout-Clustering"), animated chevron rotation, and initially collapsed state (`emptySet()`).
   - `TuningSubtitleFormatter` SHALL provide `formatClusterTuningSubtitle(...)`, generating a live formatted summary string of active tolerances and toggles (e.g. `End: 200m · Apex: 400m · Dist: 20% · Alt: 400m` in metric mode, or imperial equivalent with mi/ft).

2. **Single Source of Truth & DRY Refactoring (Rule 36)**:
   - The shared UI controls and validation logic SHALL be encapsulated in `ClusterTuningContent.kt` (< 250 lines), shared identically across `ClusterTuningSection.kt`, `ClusterTuningScreen.kt`, and `ImportBackupTabsScreen.kt` with zero code or layout duplication.
   - `ClusterTuningScreen.kt` in `WorkoutClustersScreen` and `ImportBackupTabsScreen.kt` SHALL consume `ClusterTuningContent`, preserving the contextual cluster recalculation button (`recalculateClusters()`) and pre-import sensitivity adjustments.

3. **DataStore, Defaults & Atomic Factory Reset Integration**:
   - `TuningPreferencesDefaults` SHALL define defaults: `CLUSTER_TOL_ENDPOINTS = 200f`, `CLUSTER_TOL_APEX = 400f`, `CLUSTER_TOL_DISTANCE = 0.20f`, `CLUSTER_TOL_ALTITUDE_POS = 400f`, `CLUSTER_USE_SPORT_TYPE = true`, `CLUSTER_USE_ALTITUDE_POS = true`.
   - `TuningConfig` SHALL incorporate these 6 clustering parameters.
   - `TuningPreferencesDataStore` SHALL manage persistence, updating both DataStore and `TrainingApplication.cSharedPreferences` synchronously on save to ensure background services (`WorkoutClusterEngine`, `LegacyImportEngine`, `TrackerService`) read updated values immediately.
   - Invoking "Auf Standardwerte zurücksetzen" (Reset to Defaults) in Advanced Settings SHALL atomically reset clustering tolerances to their canonical factory defaults.

4. **Preservation of Invariants & Modularity**:
   - All 6 parameters, SharedPreferences keys (`clusterTolEndpoints`, `clusterTolApex`, `clusterTolDistance`, `clusterTolAltitudePos`, `clusterUseSportType`, `clusterUseAltitudePos`), default values, and range validations MUST NOT be altered.
   - Modularity invariant: Every source file in `ui.settings.tuning` MUST remain strictly < 400 lines of code (`AdvancedTuningModularityTest`).
   - 100% 9-language translation parity maintained across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1: Clustering Section Present in Advanced Settings**:
  * *Given* the athlete opens Advanced Settings ("Erweiterte Einstellungen")
  * *When* browsing the accordion categories
  * *Then* a dedicated section "Routen- & Workout-Clustering" is present with an active subtitle summarizing current tolerances and toggles.
* **Criterion 2: Parameter Adjustment & Persistence**:
  * *Given* the athlete adjusts endpoint, apex, distance, or altitude tolerances inside the clustering section
  * *When* saving or navigating away
  * *Then* the values are persisted to DataStore and SharedPreferences and immediately applied to subsequent workout clustering and import operations.
* **Criterion 3: Contextual Access Parity**:
  * *Given* an athlete viewing the Favorite Routes / Clusters screen (`WorkoutClustersScreen`) or importing workouts (`ImportBackupTabsScreen`)
  * *When* choosing to tune clustering parameters
  * *Then* the shared clustering configuration (`ClusterTuningContent.kt`) is accessible with zero divergent logic or duplicated UI code.
* **Criterion 4: Reset to Defaults**:
  * *Given* modified clustering tolerances
  * *When* the athlete taps "Auf Standardwerte zurücksetzen" in Advanced Settings
  * *Then* clustering tolerances reset to their canonical default values.

### 1.4 System Invariants
1. Mathematical Engine Invariance: Clustering formulas in `WorkoutClusterEngine.kt` (`calculateSimilarity()`) remain completely unaltered.
2. Storage Compatibility: Keys in `TrainingApplication` / `SharedPreferences` remain unchanged so that legacy Java callers continue to read identical values.
3. Modularity Constraint: Every file in `ui/settings/tuning/` strictly obeys `< 400` lines of code.
4. Zero Tonal Elevation: Accordion card styling retains `tonalElevation = 0.dp`, `RoundedCornerShape(12.dp)`, `surfaceVariant`, `outlineVariant`.

---

## 2. Test Case Specification (TST-UI-318)

### TST-UI-318.1: Route Clustering Section & Controls Verification (`ClusterTuningSectionTest.kt`)
* **Objective**: Verify that `ClusterTuningSection` renders all required controls and respects parameter bounds.
* **Test Procedure**:
  1. Render `ClusterTuningSection` with initial tolerances.
  2. Verify master slider renders and controls overall sensitivity.
  3. Verify details switch reveals individual sliders:
     - Endpoint Tolerance slider (range: 10f..500f)
     - Apex Tolerance slider (range: 10f..500f)
     - Distance Tolerance slider (range: 0.01f..0.20f)
     - Altitude Position Tolerance slider (range: 10f..500f)
  4. Verify switches:
     - "Sportart berücksichtigen" (`useSportTypeForClustering`)
     - "Höhen-Extrema berücksichtigen" (`useAltitudePosForClustering`)
  5. Verify unit formatting adapts to Metric (m, %) and Imperial (mi, %).
* **Expected Result**: All controls render, bound validations hold, and callbacks dispatch correct values.

### TST-UI-318.2: Lifecycle Category Ordering & Subtitle Formatter (`AdvancedTuningAccordionTest.kt`)
* **Objective**: Verify `ROUTE_CLUSTERING` is positioned correctly in `TuningSection` and subtitle formatter formats concisely.
* **Test Procedure**:
  1. Assert `TuningSection.values()` ordinal sequence:
     `NAVIGATION`, `COCKPIT_TYPOGRAPHY`, `BATTERY_SAVER`, `SENSORS_GPS`, `SENSOR_SEARCH`, `WORKOUT_MASKS_CARDS`, `ROUTE_CLUSTERING`, `AFTERMATH_ANALYSIS`.
  2. Invoke `TuningSubtitleFormatter.formatClusterTuningSubtitle(...)`:
     - In Metric mode: asserts format contains `"End: 200m"`, `"Apex: 400m"`, `"Dist: 20%"`, `"Alt: 400m"`.
     - In Imperial mode: asserts format contains miles representation.
* **Expected Result**: Lifecycle sequence verified, subtitle format string matches expectations.

### TST-UI-318.3: DataStore Persistence & Factory Reset (`TuningPreferencesDataStoreTest.kt`)
* **Objective**: Verify DataStore persistence and atomic reset for clustering parameters.
* **Test Procedure**:
  1. Verify `TuningConfig` initializes with canonical defaults:
     `clusterTolEndpoints == 200f`, `clusterTolApex == 400f`, `clusterTolDistance == 0.20f`, `clusterTolAltitudePos == 400f`, `clusterUseSportType == true`, `clusterUseAltitudePos == true`.
  2. Update clustering values and assert persistence.
  3. Invoke `resetToDefaults()` and verify all 6 values return to defaults.
* **Expected Result**: DataStore and SharedPreferences stay synchronized, reset restores defaults.

### TST-UI-318.4: Single Source of Truth & DRY Call-Site Contracts (`ClusterTuningContractTest.kt`)
* **Objective**: Verify that `ClusterTuningScreen.kt` and `ImportBackupTabsScreen.kt` delegate to `ClusterTuningContent.kt`.
* **Test Procedure**:
  1. Inspect `ClusterTuningContent.kt` composable signatures.
  2. Verify call-site in `ClusterTuningScreen.kt` uses `ClusterTuningContent`.
  3. Verify call-site in `ImportBackupTabsScreen.kt` uses `ClusterTuningContent`.
  4. Verify contextual recalculation button remains present in `ClusterTuningScreen.kt`.
* **Expected Result**: Zero code duplication across screens, recalculation trigger preserved.

### TST-UI-318.5: Modularity & File Size Limit Enforcement (`AdvancedTuningModularityTest.kt`)
* **Objective**: Enforce project rule that every file in `ui/settings/tuning` remains < 400 lines.
* **Test Procedure**:
  1. Scan all `.kt` files in `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/`.
  2. Assert each file line count is strictly `< 400`.
* **Expected Result**: All files (including `ClusterTuningSection.kt`, `AdvancedTuningScreen.kt`, and `AdvancedTuningDialog.kt`) pass with line count < 400.

### TST-UI-318.6: 9-Language Localization Audit (`TranslationParityTest.kt`)
* **Objective**: Ensure 100% translation coverage for all new string tokens.
* **Test Procedure**:
  1. Run `TranslationParityTest`.
  2. Verify `tuning_cat_route_clustering` exists in `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
* **Expected Result**: 100% parity across all 9 locales.

### TST-UI-318.7: Clean-Room Full Suite Regression Execution
* **Objective**: Verify zero regressions across the codebase.
* **Test Procedure**:
  - Run `./gradlew testDebugUnitTest`.
* **Expected Result**: 100% pass rate across entire suite.

---

## 3. Traceability Matrix

| Requirement Clause | Test Spec ID | Verification Method | Target File |
| :--- | :--- | :--- | :--- |
| `REQ-SET-075.1` (Accordion Architecture & Subtitle) | `TST-UI-318.1`, `TST-UI-318.2` | Automated Unit Test | `ClusterTuningSection.kt`, `AdvancedTuningAccordionTest.kt` |
| `REQ-SET-075.2` (DRY & Shared Controls) | `TST-UI-318.4` | Automated Contract Test | `ClusterTuningContent.kt`, `ClusterTuningContractTest.kt` |
| `REQ-SET-075.3` (DataStore & Factory Reset) | `TST-UI-318.3` | Automated Unit Test | `TuningPreferencesDataStoreTest.kt` |
| `REQ-SET-075.4` (Modularity < 400 lines) | `TST-UI-318.5` | Automated Architecture Test | `AdvancedTuningModularityTest.kt` |
| `REQ-SET-075.4` (Localization Parity) | `TST-UI-318.6` | Automated Localization Test | `TranslationParityTest.kt` |
| Overall System Quality | `TST-UI-318.7` | Clean-Room Full Suite | `./gradlew testDebugUnitTest` |
