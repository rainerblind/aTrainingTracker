# Implementation Plan - ATT-342: Zoom-Adaptive Heatmap Thinning

Address the issue where heatmap lines in workout clusters become too wide and dominant at low to medium zoom levels by implementing a granular, zoom-aware styling engine.

## User Review Required

> [!IMPORTANT]
> - **Dynamic Sharpening**: When you zoom out, the app will now automatically "sharpen" the heatmap lines by increasing the intensity threshold and reducing individual point weights.
> - **Zero Bloat**: This ensures that even on long routes (like your 14km Solitude Runde), the heatmap stays thin and integrated with the map features instead of becoming solid blue bands.
> - **Responsive Transition**: The styling will adjust at every integer zoom level change, ensuring the visualization always matches the current map scale.

## Proposed Changes

### 1. Map Utility Layer: Parametric Styling
Fulfills REQ-MAP-016 (Refinement) | Test: TST-MAP-010

#### [MODIFY] [MapUtils.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapUtils.kt)
- **Refactor `createHeatmapProvider`**:
    - Add parameter `startIntensity: Float` (default 0.55f).
    - Use this parameter for the first entry in the `startPoints` float array.
    - **Rationale**: This allows the caller to "clip" more of the Gaussian blur fringe, effectively thinning the line.

### 2. Map DSL Layer: Granular Intensity Engine
Fulfills REQ-MAP-016 (Refinement) | Test: TST-MAP-010

#### [MODIFY] [MapContentScope.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapContentScope.kt)
- **Update `Render` loop**:
    - Implement a more granular weight and intensity schedule based on `steppedZoom`:
        | Zoom | Weight | Start Intensity | Opacity Offset |
        |:---|:---|:---|:---|
        | < 10 | 0.1 | 0.75 | -0.2 |
        | 10-12 | 0.2 | 0.70 | -0.1 |
        | 13-14 | 0.7 | 0.60 | 0.0 |
        | 15+ | 1.5 | 0.55 | 0.0 |
    - **Rationale**: Lower weight at low zoom prevents "intensity buildup" from overlapping points. Higher start intensity at low zoom clips the blurry edges to keep the lines sharp.

## Verification Plan

### Manual Verification (TST-MAP-010)
1. Open the 'Solitude Runde' cluster heatmap.
2. **Verify** that at zoom level 11/12 (similar to your screenshot), the lines are significantly thinner and the underlying map (roads, forest) is clearly visible.
3. Zoom in to level 16+. **Verify** that the lines become thicker and more "vibrant" to provide high-detail intensity information.
4. Zoom out to level 8. **Verify** that the lines remain thin and do not cover the whole city area.
