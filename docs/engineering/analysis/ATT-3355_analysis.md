# Stage 1 Analysis: ATT-3355 - Move workout route clustering parameters to Advanced Settings

**Ticket**: [ATT-3355](https://rainerblind.atlassian.net/browse/ATT-3355)  
**Sub-task**: [ATT-3433](https://rainerblind.atlassian.net/browse/ATT-3433) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Branch**: `feature/ATT-3355`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - Amends `REQ-SET-010` (*Tuning Parameters UI*) and `REQ-SET-065` (*Altitude Extrema-Aware 3D Route Clustering & Database Migration*), integrating with `REQ-UI-357` (*Dedicated Full-Screen Advanced Settings Screen Architecture, Zero-Dismissal Scrolling, and Lifecycle-Driven Category Reorganization*), formulated as `REQ-SET-075` (*Canonical Workout Route Clustering Algorithmic Parameters Consolidation in Advanced Settings, Single Source of Truth, and Modularity Preservation*). Targets `ui/settings/tuning/categories/ClusterTuningSection.kt`, `ui/settings/tuning/AdvancedTuningAccordion.kt`, `ui/settings/tuning/AdvancedTuningScreen.kt`, `ui/settings/tuning/AdvancedTuningDialog.kt`, `settings/TuningPreferencesDataStore.kt`, `ui/clusters/ClusterTuningScreen.kt`, and `migration/ImportBackupTabsScreen.kt`.
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

## 1. Problem Statement & Motivation

Workout route clustering uses a 3D topological spatial matching engine to group similar recorded workout sessions into reusable favorite routes (clusters). The sensitivity and behavior of this algorithm is governed by six core mathematical parameters:
1. **Endpoint Tolerance** (`clusterTolEndpoints`, meters): Maximum geographic displacement allowed between session endpoints and cluster endpoints (default: 200m).
2. **Apex Tolerance** (`clusterTolApex`, meters): Maximum displacement allowed between session points of maximum distance from start and cluster apex (default: 400m).
3. **Distance Tolerance** (`clusterTolDistance`, fraction/percent): Relative length variation tolerance between session total distance and cluster distance (default: 20%).
4. **Altitude Position Tolerance** (`clusterTolAltitudePos`, meters): Geographic displacement tolerance between session altitude extrema (min/max elevation coordinates) and cluster altitude anchors (default: 400m).
5. **Use Sport Type for Clustering** (`clusterUseSportType`, boolean): Isolates different activities (e.g. Run vs. Bike) on the same geographic route into separate clusters (default: true).
6. **Use Altitude Position for Clustering** (`clusterUseAltitudePos`, boolean): Distinguishes mountain ridge from valley routes having similar 2D footprints based on elevation extrema (default: true).

### Current Deficiencies
1. **Fragmented Architectural Placement**:
   - These parameters represent algorithmic engine tuning, identical in nature to existing Advanced Settings parameters such as GPS accuracy filters, barometric altitude smoothing windows, slope calculation minimum speeds, and Follow-Me camera smoothing alphas.
   - Currently, they are hidden in a sub-screen (`ClusterTuningScreen.kt`) accessible solely via an overflow menu in `WorkoutClustersScreen.kt`, and partially replicated in `ImportBackupTabsScreen.kt` for pre-import tuning.
2. **DRY & Single Source of Truth Violation (Rule 36)**:
   - Multiple entry points configure the same algorithmic parameters using slightly divergent UI wrappers.
   - Settings are persisted via ad-hoc calls to `TrainingApplication.cSharedPreferences` without being represented in `TuningPreferencesDataStore` / `TuningConfig`, preventing global "Auf Standardwerte zurücksetzen" (Reset to Defaults) from managing them atomically.

Relocating the canonical configuration of workout route clustering into **Advanced Settings** (`AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt`) as a dedicated modular accordion section resolves both deficiencies, satisfies Rule 36, and provides a unified power-user configuration experience.

---

## 2. Technical Architecture & Component Gap Analysis

### 2.1 Component Overview & Touchpoints

```
[Advanced Settings (AdvancedTuningScreen / AdvancedTuningDialog)]
   │
   ├── Phase A: Cockpit & Navigation (Navigation, Typography, Battery)
   ├── Phase B: Hardware & Telemetry (Sensors GPS, Sensor Search)
   └── Phase C: History, Routes & Post-Workout
         ├── Section 6: Workout-Masken & Detailkarten (WorkoutMasksAndCardsSection)
         ├── Section 7: Routen- & Workout-Clustering (ClusterTuningSection) <── NEW!
         └── Section 8: Aftermath & Analyse (AftermathAnalysisSection)
                                │
                                ▼
                   [ClusterTuningContent] (Shared Composable)
                     ├── Master Sensitivity Slider (Strict ↔ Relaxed)
                     ├── Details Parameter Switch
                     ├── Sport Type & Altitude Extrema Toggles
                     └── 4 Tolerance Sliders (Endpoint, Apex, Distance, Altitude Pos)
                                ▲                   ▲
                                │                   │
                [ClusterTuningScreen]     [ImportBackupTabsScreen]
             (WorkoutClusters Screen)     (Pre-Import Dialog)
```

### 2.2 Modularity & File Size Limits (< 400 Lines)
- `AdvancedTuningScreen.kt`: Currently 372 lines.
- `AdvancedTuningDialog.kt`: Currently 388 lines.
- `AdvancedTuningAccordion.kt`: Currently 342 lines.
- `ClusterTuningScreen.kt`: Currently 373 lines (contains full `ClusterTuningContent` and slider helpers).

To strictly respect the project's < 400 lines modularity invariant (`AdvancedTuningModularityTest`):
1. **New Modular Composable**: Create `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/ClusterTuningSection.kt` (< 150 lines), hosting `ClusterTuningSection` and delegating to shared controls.
2. **Shared Controls Extraction**: Extract `ClusterTuningContent` and its child slider/switch components from `ClusterTuningScreen.kt` into `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterTuningContent.kt` (< 250 lines), reducing `ClusterTuningScreen.kt` from 373 lines to ~130 lines.
3. **Streamlined Section Embedding**: In `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt`, embed the new `ROUTE_CLUSTERING` section cleanly using concise lambdas, ensuring both files remain strictly < 400 lines.

### 2.3 Category Sequence & Athletic Lifecycle
In accordance with `REQ-UI-357`, categories are structured according to the athlete's cognitive lifecycle:
1. `NAVIGATION`: Navigation & Kartenführung (Active in-ride)
2. `COCKPIT_TYPOGRAPHY`: Cockpit-Typografie & Sensorfelder (Active in-ride)
3. `BATTERY_SAVER`: AMOLED & Akkuschoner (Active in-ride)
4. `SENSORS_GPS`: Sensoren & GPS-Filterung (Hardware & telemetry)
5. `SENSOR_SEARCH`: Sensorsuche & Verbindungen (Hardware & telemetry)
6. `WORKOUT_MASKS_CARDS`: Workout-Masken & Detailkarten (History & visualization)
7. `ROUTE_CLUSTERING`: Routen- & Workout-Clustering (Spatial route matching & grouping)
8. `AFTERMATH_ANALYSIS`: Aftermath & Analyse (Post-workout review & metrics)

### 2.4 Subtitle Formatter (`TuningSubtitleFormatter`)
Add pure, deterministic subtitle formatter `formatClusterTuningSubtitle`:
```kotlin
fun formatClusterTuningSubtitle(
    endpointTol: Float,
    apexTol: Float,
    distanceTol: Float,
    altitudeTol: Float,
    useSportType: Boolean,
    useAltitude: Boolean,
    context: Context
): String
```
Produces concise glanceable text for collapsed/expanded card headers (e.g. `"End: 200m · Apex: 400m · Dist: 20% · Alt: 400m"` in metric mode, or imperial equivalent with mi/ft).

### 2.5 DataStore & Factory Reset Integration
1. Extend `TuningPreferencesDefaults`:
   - `CLUSTER_TOL_ENDPOINTS = 200f`
   - `CLUSTER_TOL_APEX = 400f`
   - `CLUSTER_TOL_DISTANCE = 0.20f`
   - `CLUSTER_TOL_ALTITUDE_POS = 400f`
   - `CLUSTER_USE_SPORT_TYPE = true`
   - `CLUSTER_USE_ALTITUDE_POS = true`
2. Extend `TuningConfig` with the 6 clustering parameters.
3. In `TuningPreferencesDataStore`:
   - Add DataStore preference keys matching `TrainingApplication.SP_CLUSTER_*`.
   - On `saveTuningConfig()` / `resetToDefaults()`, synchronously update `TrainingApplication.cSharedPreferences` so that background services (`TrackerService`, `WorkoutClusterEngine`, `LegacyImportEngine`) immediately reflect updated tolerances.

---

## 3. Scope Bounding & Invariants (ATT-1250)

### In Scope
1. Creating `ClusterTuningSection.kt` under `ui.settings.tuning.categories`.
2. Extracting shared `ClusterTuningContent.kt` to eliminate code duplication across `ClusterTuningSection`, `ClusterTuningScreen`, and `ImportBackupTabsScreen`.
3. Adding `ROUTE_CLUSTERING` to `TuningSection` enum and implementing `formatClusterTuningSubtitle` in `TuningSubtitleFormatter`.
4. Integrating clustering parameters into `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt` (including factory reset).
5. Extending `TuningPreferencesDefaults`, `TuningConfig`, and `TuningPreferencesDataStore`.
6. Adding localized string `tuning_cat_route_clustering` across all 9 languages (EN, DE, ES, FR, IT, JA, NL, PL, PT).
7. Comprehensive unit, contract, modularity (< 400 lines), and localization parity tests.

### Out of Scope (Adjacent Components Preserved Unaltered)
1. Modifying the mathematical clustering formulas in `WorkoutClusterEngine.kt` or `calculateSimilarity()`.
2. Altering SQLite schema in `RouteClusters.db` or `WorkoutSummariesDatabaseManager`.
3. Removing the contextual recalculation button in `WorkoutClustersScreen` (it remains accessible in `ClusterTuningScreen`).

---

## 4. Risk Assessment & Audit Recommendation

- **Complexity**: Low-Medium (Refactoring and UI consolidation with established patterns).
- **Risk Level**: **LOW** (Preserves existing DataStore/SharedPreferences keys, values, and backward-compatible helper methods).
- **Audit Recommendation**: **RECOMMEND PASS**.
