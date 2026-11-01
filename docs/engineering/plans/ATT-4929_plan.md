# Stage 3: Implementation Plan - ATT-4929: Heatmap Maximum Corridor Frequency Normalization

**Ticket**: [ATT-4929](https://rainerblind.atlassian.net/browse/ATT-4929)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-MAP-056` (`REQ-MAP-056.6`, `REQ-PER-024.4`)  
**Test Mapping**: `TST-MAP-058` (`TST-MAP-058.10`..`TST-MAP-058.12`)  
**Branch**: `feature/ATT-4929`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-11-01  

---

## 1. Problem Description & Background
In multi-workout Periods views (e.g. 150 workouts), static pass intensity heuristics (`calculateStepIntensity(pathCount)`) cause premature saturation after 12 passes. Frequently traveled home routes (150x) and moderate corridors (13x) render identically in deep indigo, hiding the athlete's primary corridors. Independent per-tile normalization creates abrupt contrast jumps across tile borders. To solve this, `ATT-4929` introduces global period-wide maximum corridor frequency estimation ($M$) paired with a non-linear logarithmic transfer curve, unified across summary cards and interactive maps.

---

## 2. Traceability & Requirements Mapping
* **`REQ-MAP-056.6` / `TST-MAP-058.10`**: Monotonic logarithmic intensity curve ($I(1) = 85$, $I(13) \approx 172$, $I(150) = 255$); saturation strictly at maximum.
* **`REQ-MAP-056.6` / `TST-MAP-058.11`**: Coarse spatial grid estimator (`estimateMaxCorridorPassCount`) detects true maximum overlap in $< 10\text{ ms}$ on `Dispatchers.Default`.
* **`REQ-PER-024.4` / `TST-MAP-058.12`**: `PeriodMapVisuals` propagates identical `maxCorridorPassCount` to both `PeriodSummaryCard` and `InteractivePeriodMap`.

---

## 3. System Invariants & Preserved Behavior
1. **Thread Safety & Zero Main-Thread Jank**: Coarse grid estimation runs strictly on `Dispatchers.Default`; tile rendering remains thread-safe.
2. **Visual Monotonicity & Base Legibility**: Single passes always render in visible base cyan ($I=85$); intermediate passes remain strictly monotonic.
3. **Tile Boundary Seamlessness**: A uniform global $M$ ensures identical corridor color across tile edges and zoom levels.
4. **Code Lock (Rule 4)**: Production source code remains locked until Gate 3 audit passes.

---

## 4. Proposed Architectural Changes (SWE.2)
* **Coarse Spatial Estimator (`PolylineHeatmapTileProvider.kt`)**:
  * Add `estimateMaxCorridorPassCount(allPaths: List<List<LatLng>>, coarseGridSize: Int = 128): Int` using a 2D integer accumulation grid across normalized bounding box coordinates.
* **Logarithmic Transfer Function (`PolylineHeatmapTileProvider.kt`)**:
  * Add `maxCorridorPassCount: Int? = null` parameter to constructor.
  * When $M > 1$, map count $c \ge 1.0f$ via $I(c) = \text{round}\left(85 + 170 \cdot \frac{\ln(c)}{\ln(M)}\right)$, with linear interpolation for anti-aliased edge pixels ($c < 1.0f$).
* **Visuals Pipeline Unification (`PeriodMapUtils.kt`, `MapContentScope.kt`)**:
  * Update `getPeriodMapVisuals` and `computePeriodCardMapState` to compute/pass `maxCorridorPassCount`.
  * In `MapContentScope.kt`, pass computed `maxCorridorPassCount` inside `produceState` (`Dispatchers.Default`).

### UI Consistency (Rule 23)
* **Reference Component**: Existing Google Maps Periods preview (`PeriodSummaryCard.kt`, `PeriodMapScreen.kt`).
* **Visual Theme Tokens**: Sequential blue palette (Cyan `#00E5FF` -> Pure Blue `#0000FF` -> Deep Indigo `#311B92`) preserved.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`PolylineHeatmapTileProvider.kt`)**: Implement `estimateMaxCorridorPassCount` and logarithmic transfer mapping in `getTile()`.
2. **Step 2 (`PeriodMapUtils.kt`)**: Integrate `maxCorridorPassCount` calculation into `computePeriodCardMapState` and `getPeriodMapVisuals`.
3. **Step 3 (`MapContentScope.kt`)**: Wire `maxCorridorPassCount` in asynchronous heatmap provider generation on `Dispatchers.Default`.
4. **Step 4 (`PolylineHeatmapTileProviderTest.kt`)**: Implement `TST-MAP-058.10` (1x, 13x, 150x monotonic curve) and `TST-MAP-058.11` (coarse grid estimator).
5. **Step 5 (`PeriodCardMapStateTest.kt` & `PeriodMapVisualsTest.kt`)**: Implement `TST-MAP-058.12` asserting identical frequency calibration in card and map visuals.

---

## 6. Verification & Rollback Plan
* **Targeted Verification**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.PolylineHeatmapTileProviderTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodMapVisualsTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodCardMapStateTest"`
  * `python3 tools/verify_requirement_governance.py`
* **Rollback Plan**: Full git branch isolation on `feature/ATT-4929`.
