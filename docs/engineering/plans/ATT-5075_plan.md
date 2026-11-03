# Stage 3: Implementation Plan - ATT-5075: Harmonize Lieblingsstrecken Map Popup

**Ticket**: [ATT-5075](https://rainerblind.atlassian.net/browse/ATT-5075)  
**Sub-task**: [ATT-5156](https://rainerblind.atlassian.net/browse/ATT-5156) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-UI-322` (*General Map Layer Visibility Controls & Dynamic Decluttering Menu*)  
**Test Mapping**: `TST-UI-282` (*General Map Layer Visibility Controls & Preference Persistence Verification*)  
**Branch**: `feature/ATT-5075`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Problem Description & Background
In `MapScreenWithTrack.kt`, tapping a cluster currently opens `ClusterOnMapSheet`, which displays purple/lila (`TTColor.Cluster`) text and icons, missing cluster statistics (sport subtitle, elevation, PR trophy/best time, relative recency). This plan modernizes `ClusterOnMapSheet` to match `WorkoutClusterComponents.kt` list item cards, enriches `MapCluster` with `WorkoutClusterStats`, and adjusts peek height to `PeekHeightWorkout + navBarHeight`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-322` (General Map Layer Visibility Controls & Dynamic Decluttering Menu)
* **Test Mapping**: `TST-UI-282` (General Map Layer Visibility Controls & Preference Persistence Verification)

---

## 3. System Invariants & Preserved Behavior
1. **Map Canvas Polyline Styling**: Polyline stroke color (`TTColor.Cluster`), zIndex (`12f`), and width (`MapVisualization.CLUSTER_WIDTH`) on the map canvas remain intact.
2. **Referential Stability (`REQ-MAP-051`)**: Memoized `latLngs` coordinate decoding in `toMapCluster()` preserved across recompositions.
3. **Non-Regression**: Zero regression on routes, segments, locations, or live track layers.
4. **Human Decision Gate**: Parent ticket advances to `Final Review (Human)` only; never auto-closed to `Erledigt`.

---

## 4. Proposed Architectural Changes

### Component 1: Data Model & ViewModel Enrichment (`MapModels.kt`, `MapFragmentWithTrackViewModel.kt`)
* In `MapModels.kt`: Add `val stats: WorkoutClusterStats? = null` to `@Immutable data class MapCluster`.
* In `toMapCluster()`: Add optional `stats: WorkoutClusterStats? = null` parameter and pass to constructor.
* In `MapFragmentWithTrackViewModel.kt`: Update `mapClustersFlow` to `combine(workoutClusterRepository.allClusters, workoutClusterRepository.clusterStats)` and map `cluster.toMapCluster(stats = statsMap[cluster.id])`.

### Component 2: Bottom Sheet UI Refactoring (`MapScreenWithTrack.kt`)
* Calibrate cluster peek height: Change line 213 from `BottomSheetDesign.PeekHeightKnownLocation` to `BottomSheetDesign.PeekHeightWorkout + navBarHeight`.
* Refactor `ClusterOnMapSheet`:
  - Row 1: 32dp sport icon (`Color.Unspecified`) + column with cluster title (`titleMedium`, `onSurface`) and sport subtitle (`bodySmall`, `onSurfaceVariant`).
  - Row 2: Distance (`MetricItem`, `onSurfaceVariant`), elevation gain (when $> 0\text{m}$, `MetricItem`, `onSurfaceVariant`), and PR best time (when present, `Icons.Filled.EmojiEvents` in `TTColor.Gold` trophy + time text).
  - Row 3: Recording count in `primary` (bold `bodyMedium`) + bullet separator + relative recency (`WorkoutClusterStats.formatRelativeRecency`).
* Remove all purple `TTColor.Cluster` text/icon color styling from sheet.

### UI Consistency (Rule 23)
* **Reference component**: `WorkoutClusterComponents.kt` (`WorkoutClusterIdentityRow` & `WorkoutClusterMetadataBlock`).
* **Reused components**: `MetricItem`, `DistanceFormatter`, `TimeFormatter`, `WorkoutClusterStats.formatRelativeRecency`.
* **Theme tokens**: `MaterialTheme.colorScheme.onSurface`, `onSurfaceVariant`, `primary`, `TTColor.Gold` for PR trophy, `Color.Unspecified` for sport icon.
* **New one-off styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Model & ViewModel Stats Enrichment
* Files: `MapModels.kt`, `MapFragmentWithTrackViewModel.kt`
* Add `stats: WorkoutClusterStats? = null` to `MapCluster` and `toMapCluster()`.
* Combine `allClusters` and `clusterStats` in `mapClustersFlow`.

### Step 2: Harmonize ClusterOnMapSheet & Peek Height
* File: `MapScreenWithTrack.kt`
* Update peek height to `BottomSheetDesign.PeekHeightWorkout + navBarHeight`.
* Modernize `ClusterOnMapSheet` to 3-row layout without `TTColor.Cluster` text/icons.

### Step 3: Targeted Unit & Contract Tests
* Files: `MapModelsTest.kt`, `MapFragmentWithTrackViewModelTest.kt`, `GeneralMapLayersContractTest.kt`
* Verify stats enrichment, referential equality, absence of purple text tinting, and peek height.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Run targeted tests for map package, followed by `./gradlew testDebugUnitTest` in Stage 5.
* **Rollback**: Clean revert via `git restore .` on `feature/ATT-5075`.
