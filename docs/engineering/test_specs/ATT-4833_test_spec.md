# Test Specification - ATT-4833

## 1. Overview & Scope
- **Ticket**: ATT-4833 ([Verbesserung] Review and polish Periods screen visual appearance, mini-maps, and summary cards)
- **Requirement**: `REQ-PER-024` (Route-Frequency Heatmap Unification, Tolerance Calibration & Region Propagation)
- **Living Test Spec**: `TST-PER-030` in `docs/tests/tst_aftermath_periods.md`
- **Target Files**: `ThermalColorLut.kt`, `PolylineHeatmapTileProvider.kt`, `PeriodMapUtils.kt`, `PeriodSummaryCard.kt`, `PeriodMapScreen.kt`, `InteractivePeriodMap.kt`, `PeriodsViewModel.kt`

## 2. Test Cases (TST-PER-030)

### TST-PER-030.1: ThermalColorLut High-Contrast Progression Test
- **Component**: `PolylineHeatmapTileProviderTest.kt` / `ThermalColorLut.kt`
- **Objective**: Verify that `ThermalColorLut` stops provide enhanced base alpha for single-pass readability (~55%) and 100% saturation on frequent corridors:
  - Base alpha at low intensity (`t = 10`): $\approx 140$ (~55%).
  - Mid intensity (`t = 128`): $\approx 204$ (~80%).
  - High intensity (`t = 255`): $255$ (100% alpha).
- **Assertions**: `lowAlpha < midAlpha`, `midAlpha < highAlpha`, `highAlpha == 255`.

### TST-PER-030.2: Adaptive Corridor Frequency Normalization Test
- **Component**: `PolylineHeatmapTileProviderTest.kt` / `PolylineHeatmapTileProvider.kt`
- **Objective**: Verify that `PolylineHeatmapTileProvider` adaptively calculates step intensity:
  - $\le 3$ workouts: 2 passes to saturation ($1.0 / 2 = 0.50f$).
  - $\le 10$ workouts (Week): 3 passes to saturation ($1.0 / 3 \approx 0.33f$).
  - $\le 35$ workouts (Month): 5 passes to saturation ($1.0 / 5 = 0.20f$).
  - $\le 100$ workouts: 8 passes to saturation ($1.0 / 8 = 0.125f$).
  - $> 100$ workouts (Year): 12 passes to saturation ($1.0 / 12 \approx 0.083f$).
- **Assertions**: Calculated step intensity matches expected adaptive thresholds.

### TST-PER-030.3: Corridor Overlap Tolerance Calibration Test
- **Component**: `PeriodCardMapStateTest.kt` / `PeriodMapUtils.kt`
- **Objective**: Verify Douglas-Peucker simplification tolerance bounds:
  - `PeriodType.DAY`: 5.0m
  - `PeriodType.WEEK`: 5.0m
  - `PeriodType.MONTH`: 6.0m
  - `PeriodType.YEAR`: 8.0m
- **Assertions**: All tolerances $\le 8.0\text{m}$.

### TST-PER-030.4: Heatmap-Primary Visualization & Track Suppression Test
- **Component**: `InteractivePeriodMapTest.kt` / `InteractivePeriodMap.kt` / `PeriodMapScreen.kt`
- **Objective**: Verify that in period overview (`peekedTracks.isEmpty()`):
  - Member polylines and anchor tracks are not rendered; start/stop markers are suppressed.
  - Heatmap is rendered exclusively.
  - When `peekedTracks.isNotEmpty()`, only the selected track is rendered with high alpha and its markers.

### TST-PER-030.5: Region State Propagation Test
- **Component**: `PeriodsViewModelTest.kt` / `PeriodsViewModel.kt`
- **Objective**: Verify `showPeriodMap(summary, initialRegionId)` initializes `selectedRegionId` with `initialRegionId`.

### TST-PER-030.6: Clean-Room Full Suite Regression Execution
- **Command**: `./gradlew testDebugUnitTest --tests "*PolylineHeatmapTileProviderTest*" --tests "*PeriodCardMapStateTest*" --tests "*InteractivePeriodMapTest*"`
- **Criteria**: 100% pass rate with zero regressions.
