# Stage 3: Implementation Plan - ATT-3355: Move workout route clustering parameters to Advanced Settings

**Ticket**: [ATT-3355](https://rainerblind.atlassian.net/browse/ATT-3355)  
**Sub-task**: [ATT-3435](https://rainerblind.atlassian.net/browse/ATT-3435) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-SET-075` (*Dedicated Workout Route Clustering Section in Advanced Settings, Single Source of Truth, and Modularity Preservation*)  
**Test Mapping**: `TST-UI-318`  
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

## 1. Problem Description & Architectural Context

Workout route clustering uses a 3D topological spatial matching engine to group similar workouts into reusable favorite routes (clusters). The sensitivity of this algorithm is governed by six mathematical tuning parameters:
1. Endpoint Tolerance (`clusterTolEndpoints`, meters, default: 200m)
2. Apex Tolerance (`clusterTolApex`, meters, default: 400m)
3. Distance Tolerance (`clusterTolDistance`, percent, default: 20%)
4. Altitude Position Tolerance (`clusterTolAltitudePos`, meters, default: 400m)
5. Use Sport Type for Clustering (`clusterUseSportType`, boolean, default: true)
6. Use Altitude Position for Clustering (`clusterUseAltitudePos`, boolean, default: true)

These algorithmic parameters represent the textbook definition of power-user expert settings ("Experteneinstellungen"), identical in nature to existing Advanced Settings parameters (GPS accuracy filter, barometric altitude smoothing window, slope minimum speed). Currently, they reside in a specialized screen (`ClusterTuningScreen.kt`) and are partially duplicated in `ImportBackupTabsScreen.kt`.

Relocating the canonical configuration of workout route clustering into **Advanced Settings** (`AdvancedTuningScreen.kt` / `AdvancedTuningDialog.kt`) as a dedicated modular accordion section:
1. Establishes a single authoritative home for algorithmic route matching preferences.
2. Eliminates code and layout duplication across the cluster screen and import screen in full adherence to the Single Source of Truth & DRY principle (Rule 36).
3. Retains contextual shortcuts in `WorkoutClustersScreen` and `ImportBackupTabsScreen` that reuse the shared composable.
4. Integrates clustering parameters into the atomic "Auf Standardwerte zurücksetzen" (Reset to Defaults) workflow.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-SET-075` (*Dedicated Workout Route Clustering Section in Advanced Settings, Single Source of Truth, and Modularity Preservation*)
* **Test Mapping**: `TST-UI-318` (*Dedicated Workout Route Clustering Section, Parameter Persistence, and Modularity Verification*)
  * `TST-UI-318.1`: Route Clustering Section & Controls Verification (`ClusterTuningSectionTest.kt`)
  * `TST-UI-318.2`: Subtitle Formatter Dynamic Metric/Imperial Formatting (`TuningSubtitleFormatterTest.kt`)
  * `TST-UI-318.3`: DataStore & SharedPreferences Synchronization & Atomic Reset (`TuningPreferencesDataStoreTest.kt`)
  * `TST-UI-318.4`: Single Source of Truth & Contextual Screen Parity (`ClusterTuningContractTest.kt`)
  * `TST-UI-318.5`: Modularity & File Size Limits (`AdvancedTuningModularityTest.kt`)
  * `TST-UI-318.6`: 9-Language Localization Parity (`TranslationParityTest.kt`)
  * `TST-UI-318.7`: Clean-Room Full Regression Suite (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Mathematical Engine Invariance**: Clustering similarity calculations in `WorkoutClusterEngine.kt` (`calculateSimilarity()`) remain completely unchanged.
2. **Storage Contract Compatibility**: Keys in `TrainingApplication` / `SharedPreferences` (`clusterTolEndpoints`, `clusterTolApex`, `clusterTolDistance`, `clusterTolAltitudePos`, `clusterUseSportType`, `clusterUseAltitudePos`) and DataStore remain synchronized so that background services (`TrackerService`, `WorkoutClusterEngine`, `LegacyImportEngine`) read updated values immediately without restart.
3. **Modularity Constraint (< 400 lines)**: Every file in `ui/settings/tuning` MUST remain strictly under 400 lines of code (`AdvancedTuningModularityTest`).
4. **Zero Tonal Elevation**: All accordion cards and containers retain `tonalElevation = 0.dp`, `RoundedCornerShape(12.dp)`, `surfaceVariant`, `outlineVariant`, animated chevron rotation, and initially collapsed state (`emptySet()`).
5. **9-Language Translation Parity**: 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT for all string tokens.
6. **Subtask Self-Sufficiency & Human Gate Invariance**: Subtasks transition directly to `Erledigt` via transition `freigabe` once Agent 2 passes the gate audit. Parent tickets terminate exclusively in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: `TuningPreferencesDataStore.kt` & `TuningPreferencesDefaults.kt`
* Add canonical defaults in `TuningPreferencesDefaults`:
  - `CLUSTER_TOL_ENDPOINTS = 200.0f`
  - `CLUSTER_TOL_APEX = 400.0f`
  - `CLUSTER_TOL_DISTANCE = 0.20f`
  - `CLUSTER_TOL_ALTITUDE_POS = 400.0f`
  - `CLUSTER_USE_SPORT_TYPE = true`
  - `CLUSTER_USE_ALTITUDE_POS = true`
* Add keys in `TuningPreferencesDataStore.Companion`:
  - `KEY_CLUSTER_TOL_ENDPOINTS`: `floatPreferencesKey("tuning_cluster_tol_endpoints")`
  - `KEY_CLUSTER_TOL_APEX`: `floatPreferencesKey("tuning_cluster_tol_apex")`
  - `KEY_CLUSTER_TOL_DISTANCE`: `floatPreferencesKey("tuning_cluster_tol_distance")`
  - `KEY_CLUSTER_TOL_ALTITUDE_POS`: `floatPreferencesKey("tuning_cluster_tol_altitude_pos")`
  - `KEY_CLUSTER_USE_SPORT_TYPE`: `booleanPreferencesKey("tuning_cluster_use_sport_type")`
  - `KEY_CLUSTER_USE_ALTITUDE_POS`: `booleanPreferencesKey("tuning_cluster_use_altitude_pos")`
* Extend `TuningConfig` data class with the 6 parameters initialized to their defaults.
* In `TuningPreferencesDataStore.updateTuningConfig()` and atomic `resetToDefaults()`, persist values both to DataStore and synchronously update `TrainingApplication.setClusterTolEndpoints(...)`, etc.

### Component 2: `AdvancedTuningAccordion.kt` & Subtitle Formatter
* Extend `enum class TuningSection` with `ROUTE_CLUSTERING`, positioned in Phase C (History, Routes & Post-Workout) between `WORKOUT_MASKS_CARDS` and `AFTERMATH_ANALYSIS`:
  ```kotlin
  enum class TuningSection {
      NAVIGATION,
      COCKPIT_TYPOGRAPHY,
      BATTERY_SAVER,
      SENSORS_GPS,
      SENSOR_SEARCH,
      WORKOUT_MASKS_CARDS,
      ROUTE_CLUSTERING,
      AFTERMATH_ANALYSIS
  }
  ```
* In `TuningSubtitleFormatter`:
  - Add `fun formatClusterTuningSubtitle(endpointTol: Float, apexTol: Float, distTol: Float, altTol: Float, useSport: Boolean, useAlt: Boolean, isImperial: Boolean, context: Context): String`
  - Formats: e.g. `End: 200m · Apex: 400m · Dist: 20% · Alt: 400m` (or imperial `End: 0.124mi · Apex: 0.248mi · Dist: 20% · Alt: 0.248mi`).

### Component 3: Single Source of Truth `ClusterTuningContent.kt` (Rule 36)
* Extract `ClusterTuningContent` and `TuningSlider` from `ClusterTuningScreen.kt` into `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterTuningContent.kt`.
* Keeps `ClusterTuningContent.kt` clean (< 250 lines) and accessible to:
  - `ClusterTuningSection.kt` (Advanced Settings accordion)
  - `ClusterTuningScreen.kt` (Workout Clusters overflow screen)
  - `ImportBackupTabsScreen.kt` (Import tuning dialog)

### Component 4: Modular Category Composable `ClusterTuningSection.kt`
* Create `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/ClusterTuningSection.kt`.
* Wraps `ClusterTuningContent` inside `TuningAccordionSection` with:
  - `title = stringResource(R.string.tuning_cat_route_clustering)`
  - `icon = Icons.Default.Hub`
  - `subtitle = TuningSubtitleFormatter.formatClusterTuningSubtitle(...)`
  - `isExpanded = expandedSections.contains(TuningSection.ROUTE_CLUSTERING)`
  - `onToggle = { onToggleSection(TuningSection.ROUTE_CLUSTERING) }`

### Component 5: Integration in `AdvancedTuningScreen.kt` & `AdvancedTuningDialog.kt`
* Add mutable states for `clusterTolEndpoints`, `clusterTolApex`, `clusterTolDistance`, `clusterTolAltitudePos`, `clusterUseSportType`, `clusterUseAltitudePos`.
* Render `ClusterTuningSection` between `WorkoutMasksAndCardsSection` and `AftermathAnalysisSection`.
* In `handleSave()`, persist all 6 parameters via `tuningDataStore`.
* In `handleResetDefaults()`, reset all 6 parameters to `TuningPreferencesDefaults`.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: `SensorsGpsFilterSection.kt` and `AftermathAnalysisSection.kt` (`TuningAccordionSection` card with `0.dp` tonal elevation, `surfaceVariant`, `outlineVariant`, animated expand/collapse).
* **Reused components**:
  - `TuningAccordionSection` (`AdvancedTuningAccordion.kt`)
  - `ClusterTuningContent` (`ui/clusters/ClusterTuningContent.kt`)
  - `AppDialogActions.SaveCancel` (`AppDialogActions.kt`)
* **Theme tokens**:
  - Shapes: `RoundedCornerShape(12.dp)` for accordion cards.
  - Spacing: `8.dp`, `12.dp`, `16.dp` per Section 5.2.
  - Colors: `MaterialTheme.colorScheme.surfaceVariant`, `outlineVariant`, `primary`, `onSurface`, zero tonal elevation (`0.dp`).
* **New one-off styles & justification**: None. Strictly adheres to established Material 3 design tokens and shared components.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `TuningPreferencesDataStore.kt` & `TuningPreferencesDefaults.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStore.kt`
* **Action**:
  - Add clustering defaults in `TuningPreferencesDefaults`.
  - Add clustering keys in `TuningPreferencesDataStore.Companion`.
  - Add fields in `TuningConfig`.
  - Update `updateTuningConfig()` and atomic reset to synchronize to both DataStore and `TrainingApplication.cSharedPreferences`.

### Step 2: Extract & Modularize `ClusterTuningContent.kt` (Rule 36)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterTuningContent.kt`
* **Action**:
  - Extract `ClusterTuningContent` and `TuningSlider` into standalone file (< 250 lines).
  - Update `ClusterTuningScreen.kt` to import `ClusterTuningContent`.

### Step 3: Extend `AdvancedTuningAccordion.kt` & `TuningSubtitleFormatter`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAccordion.kt`
* **Action**:
  - Add `ROUTE_CLUSTERING` to `enum class TuningSection`.
  - Implement `TuningSubtitleFormatter.formatClusterTuningSubtitle(...)`.

### Step 4: Create `ClusterTuningSection.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/ClusterTuningSection.kt`
* **Action**:
  - Build composable `< 200` lines rendering `TuningAccordionSection` with `Icons.Default.Hub`, title `R.string.tuning_cat_route_clustering`, dynamic subtitle, and embedded `ClusterTuningContent`.

### Step 5: Wire `ClusterTuningSection` into `AdvancedTuningScreen.kt` & `AdvancedTuningDialog.kt`
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt`
* **Action**:
  - Bind states, handleSave persistence, and handleResetDefaults.

### Step 6: 9-Language Localization Parity
* **Files**: `app/src/main/res/values*/strings.xml` (EN, DE, ES, FR, IT, JA, NL, PL, PT)
* **Action**:
  - Add `tuning_cat_route_clustering` and subtitle abbreviation tokens across all 9 languages.

### Step 7: Unit & Contract Tests Construction
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/settings/ClusterTuningSectionTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStoreTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/settings/AdvancedTuningModularityTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Action**:
  - Verify all 7 clauses of `TST-UI-318`.

---

## 6. Verification & Test Execution Strategy

1. **Targeted Unit Tests**:
   - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.*"`
   - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.TranslationParityTest"`
2. **Clean-Room Regression Suite**:
   - `./gradlew testDebugUnitTest` across the entire project (100% pass rate).
