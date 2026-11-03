# Stage 3: Implementation Plan - ATT-4833: Review and polish Periods screen visual appearance, mini-maps, and summary cards

**Ticket**: [ATT-4833](https://rainerblind.atlassian.net/browse/ATT-4833)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Active Sprint**: `2026-44.1`  
**Requirement Mapping**: `REQ-PER-024`  
**Test Mapping**: `TST-PER-030`  
**Author**: AI Agent 1 (Software Architect)  
**Date**: 2026-11-01  

---

## 1. Architectural Decomposition (SWE.2)

- **Heatmap Layer (`ThermalColorLut.kt`, `PolylineHeatmapTileProvider.kt`)**:
  - `ThermalColorLut`: Boost stops base alpha to 140 (~55%) for Light Cyan (0.00), 204 (~80%) for Pure Blue (0.50), and 255 (100%) for Deep Indigo (1.00). Default opacity to 1.0.
  - `PolylineHeatmapTileProvider`: Compute adaptive `stepIntensity`:
    - Target passes to saturation: $\le 3 \to 2$, $\le 10 \to 3$, $\le 35 \to 5$, $\le 100 \to 8$, $> 100 \to 12$.
    - Single pass renders clearly at base Light Cyan, while frequently traveled corridors reach Deep Indigo.

- **Map Processing & Sizing Layer (`PeriodMapUtils.kt`)**:
  - Tighten Douglas-Peucker simplification tolerance: `DAY -> 5.0m`, `WEEK -> 5.0m`, `MONTH -> 6.0m`, `YEAR -> 8.0m`.
  - In `PeriodCardMapState`, expose `activeRegionId: String? = primaryPathRegion?.region?.id`.

- **Card Mini-Map & Propagation Layer (`PeriodSummaryCard.kt`, `PeriodsViewModel.kt`, `PeriodList.kt`, `PeriodsTabsScreen.kt`, `PeriodsScreen.kt`)**:
  - In `PeriodMultiWorkoutMap`: When `visuals.heatmapProvider != null`, render heatmap tile layer and suppress underlying vector polylines.
  - In `onMapClick`: Pass `(summary, activeRegionId)` to `PeriodsViewModel.showPeriodMap(summary, initialRegionId)`.
  - In `PeriodsViewModel`: Initialize `_mapState.value.selectedRegionId` with `initialRegionId ?: primaryRegion?.id`.

- **Detailed Map View Layer (`PeriodMapScreen.kt`, `InteractivePeriodMap.kt`)**:
  - In `PeriodMapScreen`: When `peekedWorkoutDataWithTrack == null`, pass `emptyList()` for `memberTracks` and `memberMarkers`, rendering pure heatmap. When peeked, pass `peekedTracks` and its markers.
  - In `InteractivePeriodMap`: When no workout is peeked, suppress `extremaMarkers`, `anchorTracks`, and `memberTracks`, rendering pure frequency heatmap.

---

## 2. Atomic Implementation Steps

| Step | Action | Target File(s) | Verification / Test |
| :--- | :--- | :--- | :--- |
| **Step 1** | Update `ThermalColorLut` stops and `PolylineHeatmapTileProvider` adaptive intensity | `ThermalColorLut.kt`, `PolylineHeatmapTileProvider.kt` | `PolylineHeatmapTileProviderTest` |
| **Step 2** | Calibrate `getPeriodSimplificationToleranceMeters` ($\le 8\text{m}$) and add `activeRegionId` | `PeriodMapUtils.kt` | `PeriodCardMapStateTest` |
| **Step 3** | Suppress polylines under heatmap in preview card & propagate `activeRegionId` | `PeriodSummaryCard.kt`, `PeriodsViewModel.kt`, `PeriodList.kt`, `PeriodsTabsScreen.kt`, `PeriodsScreen.kt` | Compilation check |
| **Step 4** | Unify `PeriodMapScreen` / `InteractivePeriodMap` around pure overview heatmap & peeked track highlight | `PeriodMapScreen.kt`, `InteractivePeriodMap.kt` | `InteractivePeriodMapTest` |
| **Step 5** | Update tests for `TST-PER-030.1`..`TST-PER-030.5` | `PolylineHeatmapTileProviderTest.kt`, `PeriodCardMapStateTest.kt`, `InteractivePeriodMapTest.kt` | Targeted tests |

---

## 3. UI Consistency & Invariants

- **UI Consistency**: Unifies the visual language between card previews and detailed map around the high-contrast sequential blue heatmap.
- **Invariants**: Zero changes to `PeriodSummaries.db`, memory safety (no OOM from vector polyline floods), and 100% clean-room test pass rate.
