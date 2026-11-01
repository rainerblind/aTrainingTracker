# Stage 3: Implementation Plan - ATT-4931: Decouple Workout Clusters Heatmap from Periods Styling

**Ticket**: [ATT-4931](https://rainerblind.atlassian.net/browse/ATT-4931)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-MAP-058` (`REQ-MAP-058.1`..`REQ-MAP-058.5`, refining `REQ-MAP-056` and `REQ-MAP-057`)  
**Test Mapping**: `TST-MAP-060` (`TST-MAP-060.1`..`TST-MAP-060.5`)  
**Branch**: `feature/ATT-4931`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-11-01  

---

## 1. Problem Description & Background
Sprint Review 2026-44.1 revealed that `ATT-4833` shared heatmap changes unintentionally restyled the Workout Clusters heatmap (`WorkoutClusterHeatmapScreen.kt`): base alpha stops were raised to 55%/80%/100%, opacity to 1.0, and logarithmic corridor normalization (`ATT-4929`) was implicitly forced onto all `MapContentScope.heatmap` callers. Per Human Clarification in Ceremony 1, `ATT-4931` restores the pre-`ATT-4833` visual appearance for Workout Clusters (alpha stops 20%/30%/70%, opacity 0.8, classic linear saturation) while cleanly decoupling Periods heatmap styling.

---

## 2. Traceability & Requirements Mapping
* **`REQ-MAP-058.1` / `TST-MAP-060.1`**: `ThermalColorLut.CLASSIC_BLUE` preset with base alphas 20% (51), 30% (77), 70% (180), and layer opacity 0.8.
* **`REQ-MAP-058.2` / `TST-MAP-060.2`**: Classic step intensity strategy in `PolylineHeatmapTileProvider` saturating after 4..29 passes (0.25f, 0.15f, 0.08f, 0.05f, 0.035f).
* **`REQ-MAP-058.3` / `TST-MAP-060.3`**: Opt-in corridor normalization in `MapContentScope.heatmap` (`normalizeCorridors = false` by default, leaving linear step accumulation active).
* **`REQ-MAP-058.4` / `TST-MAP-060.4`**: Explicit configuration in `WorkoutClusterHeatmapScreen.kt` using `CLASSIC_BLUE`, classic step intensity, and `normalizeCorridors = false`.
* **`REQ-MAP-058.5` / `TST-MAP-060.5`**: Clean-room regression verification across `ui.map.*`, `ui.clusters.*`, and `ui.aftermath.periodlist.*`.

---

## 3. System Invariants & Preserved Behavior
1. **Zero Regression for Periods**: Periods heatmaps (`PeriodSummaryCard`, `InteractivePeriodMap`, `PeriodMapScreen`) remain 100% on `ThermalColorLut.HOT`, dynamic stroke scaling, and logarithmic corridor normalization.
2. **Exact Pre-`ATT-4833` Clusters Look**: Workout Clusters displays classic cyan-blue-indigo colors with 20%/30%/70% alphas and 0.8 opacity.
3. **Thread Safety**: Tile generation executes exclusively on `Dispatchers.Default` with zero UI-thread blocking.
4. **Code Lock (Rule 4)**: Production source code in `app/src/main/...` remains strictly locked until Gate 3 audit passes.

---

## 4. Proposed Architectural Changes (SWE.2)
* **`ThermalColorLut.kt`**:
  * Define `CLASSIC_BLUE_STOPS` (Pair(0.0f, [0, 229, 255, 51]), Pair(0.5f, [0, 0, 255, 77]), Pair(1.0f, [49, 27, 146, 180])).
  * Expose `ThermalColorLut.CLASSIC_BLUE = ThermalColorLut(opacity = 0.8, stops = CLASSIC_BLUE_STOPS)`.
* **`PolylineHeatmapTileProvider.kt`**:
  * Add `calculateClassicStepIntensity(pathCount, intensityPerPass)` preserving the 5-tier saturation curve.
  * Restrict logarithmic normalization to explicit `maxCorridorPassCount != null`.
* **`MapContentScope.kt`**:
  * Add `normalizeCorridors: Boolean = false` to `heatmap()`.
  * In `MapContentScopeImpl.Render`, evaluate `estimateMaxCorridorPassCount` only when `normalizeCorridors == true`.
* **`WorkoutClusterHeatmapScreen.kt`**:
  * Explicitly call `heatmap(mapState.heatmapPaths, opacity = heatmapOpacity, colorLut = ThermalColorLut.CLASSIC_BLUE, normalizeCorridors = false)`.

### UI Consistency (Rule 23)
* **Reference Screen**: Pre-`ATT-4833` Workout Clusters Map (`WorkoutClusterHeatmapScreen.kt`).
* **Theme Tokens**: Standard Google Maps overlay tokens, `ThermalColorLut.CLASSIC_BLUE`, and `TTAlpha`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`ThermalColorLut.kt`)**: Add `CLASSIC_BLUE_STOPS` and `ThermalColorLut.CLASSIC_BLUE` preset.
2. **Step 2 (`PolylineHeatmapTileProvider.kt`)**: Add `calculateClassicStepIntensity` and ensure `maxCorridorPassCount = null` retains linear accumulation.
3. **Step 3 (`MapContentScope.kt`)**: Add `normalizeCorridors: Boolean = false` and guard corridor pass count estimation.
4. **Step 4 (`WorkoutClusterHeatmapScreen.kt`)**: Explicitly configure `CLASSIC_BLUE` and `normalizeCorridors = false`.
5. **Step 5 (`InteractivePeriodMap.kt`)**: Explicitly pass `normalizeCorridors = true` with `ThermalColorLut.HOT`.
6. **Step 6 (Test Suite)**: Author unit tests in `ThermalColorLutTest.kt` and `PolylineHeatmapTileProviderTest.kt` verifying decoupling and regression safety.

---

## 6. Verification & Rollback Plan
* **Targeted Verification**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"`
  * `python3 tools/verify_requirement_governance.py`
* **Rollback Plan**: Revert git commits on isolated `feature/ATT-4931` branch without impacting sprint branch.
