# Stage 1 Analysis: ATT-5075 - Lieblingsstrecken map popup: match info and look of Lieblingsstrecken list items (and remove lila/purple color)

**Ticket**: [ATT-5075](https://rainerblind.atlassian.net/browse/ATT-5075)  
**Sub-task**: [ATT-5154](https://rainerblind.atlassian.net/browse/ATT-5154) (`[Analysis]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Branch**: `feature/ATT-5075`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Problem Statement & Motivation
In `MapScreenWithTrack.kt`, tapping a *Lieblingsstrecke* (Workout Cluster) displays a bottom sheet popup (`ClusterOnMapSheet`, introduced in ATT-4847). Currently, this popup renders a minimal ad-hoc layout with all text and icons tinted in purple/lila (`TTColor.Cluster`). Furthermore, it omits key cluster information shown on the main *Lieblingsstrecken* list items (`WorkoutClustersListScreen.kt`), such as sport subtitle, elevation gain, best time (PR), and relative recency. Harmonizing `ClusterOnMapSheet` with the list item card styling removes the purple text and creates visual consistency across the app.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
- **Root Cause**:
  - `MapScreenWithTrack.kt:540-610`: `ClusterOnMapSheet` hardcoded `TTColor.Cluster` across 4 icon/text call sites rather than using standard Material 3 color tokens (`onSurface`, `onSurfaceVariant`, `primary`).
  - `MapCluster` (`MapModels.kt:325-344`) lacked `WorkoutClusterStats` (best time, last activity date, elevation).
  - `MapFragmentWithTrackViewModel.kt:99-101` mapped `allClusters` to `MapCluster` without combining `workoutClusterRepository.clusterStats`.
- **Independent Peer Review & Verification**:
  - Audited `MapScreenWithTrack.kt:571-608`: confirmed purple tinting on sport icon, distance icon/text, and hit count text.
  - Audited `WorkoutClusterComponents.kt:76-224` (`WorkoutClusterIdentityRow` & `WorkoutClusterMetadataBlock`): confirmed standard look: `Color.Unspecified` for multi-color sport icon, `onSurface` for title, `onSurfaceVariant` for metrics, `TTColor.Gold` for PR trophy, `primary` for hit count, and relative recency text.
  - Audited `WorkoutClusterRepository.kt:71`: verified `clusterStats: StateFlow<Map<Long, WorkoutClusterStats>>` is readily available for enrichment.

---

## 3. User Scope Grounding (ATT-1250)
- **In-Scope Goals**:
  1. Enrich `MapCluster` with `stats: WorkoutClusterStats? = null` in `MapModels.kt` via `MapFragmentWithTrackViewModel.kt`.
  2. Refactor `ClusterOnMapSheet` in `MapScreenWithTrack.kt` to match the 3-row layout of the *Lieblingsstrecken* list card (sport icon + title + sport subtitle, distance + elevation + PR best time, hit count + relative recency).
  3. Remove `TTColor.Cluster` from all text/icons in `ClusterOnMapSheet` (retaining `TTColor.Cluster` for polyline canvas strokes only).
  4. Calibrate cluster peek height to `BottomSheetDesign.PeekHeightWorkout + navBarHeight` (~140dp) for complete visibility.
  5. Add unit and contract tests in `MapModelsTest.kt` and `MapFragmentWithTrackViewModelTest.kt`.
- **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Changing cluster polyline map color (`TTColor.Cluster` on map canvas remains intact).
  - Embedding a redundant mini-map inside the bottom sheet (map canvas is directly visible behind the sheet).
  - Modifying `WorkoutClustersListScreen.kt` or cluster database schema.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
- **Original Requirement ID & Target**: Modifies `REQ-UI-322.3` and `TST-UI-282.3` under `REQ-UI-322` (*General Map Layer Visibility Controls & Dynamic Decluttering Menu*), Parent Epic `ATT-176`. Targets `MapScreenWithTrack.kt`, `MapModels.kt`, and `MapFragmentWithTrackViewModel.kt`.
- **Historical Origin & Commit Trace**: Introduced in Sprint 2026-44.2 via commit `4d3f24f2` (`ATT-4847`).
- **Root Reason for Existing Formulation**: ATT-4847 tinted popup text purple to match the polyline layer color, but this produced low-contrast, non-standard text and broke parity with list items.
- **Preservation of Core Invariants**: Polyline map stroke color, zIndex (`12f`), layer visibility controls, and touch interactions remain 100% intact.

---

## 5. Architectural Strategy & High-Level Solution
- **Data Enrichment (`MapModels.kt`, `MapFragmentWithTrackViewModel.kt`)**: Add `stats: WorkoutClusterStats? = null` to `MapCluster`. Combine `workoutClusterRepository.allClusters` and `clusterStats` in `mapClustersFlow`.
- **UI Refactoring (`MapScreenWithTrack.kt`)**: Update `ClusterOnMapSheet` to render:
  1. Header: 32dp sport icon (`Color.Unspecified`) + cluster title (`onSurface`) + sport name (`onSurfaceVariant`).
  2. Metrics: Distance (`MetricItem`), Avg Elevation Gain (when $> 0$), and PR Trophy (`TTColor.Gold`) + best time (or avg duration).
  3. Summary: Hit count in `primary` (bold) + relative recency (`WorkoutClusterStats.formatRelativeRecency`).
- **Peek Height**: Use `BottomSheetDesign.PeekHeightWorkout + navBarHeight`.

---

## 6. System Invariants & Risk Assessment
- **Core Invariants**:
  1. Map canvas polyline rendering (`TTColor.Cluster`) and layer toggling preserved.
  2. Zero regression in route, segment, or known location bottom sheets.
  3. 100% test pass rate across map and cluster test suites.
- **Risk Rating**: **LOW**. Localized Compose UI styling and ViewModel StateFlow enrichment; zero impact on GPS, database schema, or sensor services.
