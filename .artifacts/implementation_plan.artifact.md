# Implementation Plan - ATT-342 Refinement: Inverse Heatmap Scaling & Path Priority

Refine the visual blending of workout clusters to ensure the heatmap recedes into a faint background layer as the user zooms in, while individual workout traces become fully prominent and clear. Also further reduce marker clutter to eliminate "pointiness."

## User Review Required

> [!IMPORTANT]
> - **Inverse Scaling**: The heatmap will now **fade away** at higher zoom levels, becoming a subtle "shadow" rather than a dominant band.
> - **Path Dominance**: Individual workout lines will become sharper and more opaque (Alpha up to 1.0) as you zoom in, becoming the primary visual element for detailed analysis.
> - **Clean Traces**: I am further delaying the appearance of member marker pins. They will now remain ghostly faint until you zoom in extremely close (level 17+), ensuring your paths look like clean lines instead of "pointy" tracks.

## Proposed Changes

### 1. Map DSL Layer: Inverse Blending & Marker Culling
Fulfills REQ-MAP-016 (Refinement) | Test: TST-MAP-010

#### [MODIFY] [MapContentScope.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapContentScope.kt)
- **Refine `Render` loop**:
    - Implement the "Receding Heatmap & Clean Path" schedule:
        | Zoom | Track Alpha | Heatmap Weight | Max Intensity | Marker Mult |
        |:---|:---|:---|:---|:---|
        | < 12 | 0.4f | 0.005 | 20.0 | 0.0 (Hidden) |
        | 13-14 | **0.7f** | **0.002** | **40.0** | **0.0** (Hidden) |
        | 15-16 | **0.9f** | **0.001** | **60.0** | **0.1** (Faint) |
        | 17+ | **1.0f** | **0.0005**| **100.0**| **0.5** (Subtle)|
- **Rationale**:
    - Dramatically decreasing weight and increasing `maxIntensity` as zoom increases forces the heatmap to recede, preventing visual saturation.
    - Increasing `trackAlpha` faster ensures that individual session data takes priority.
    - Keeping `markerAlphaMult` at 0.0/0.1 for longer eliminates the "pointy" artifacts on the paths.

## Verification Plan

### Manual Verification (TST-MAP-010 Refined)
1. Open the 'Solitude Runde' cluster heatmap.
2. **Verify** that at zoom level 11/12, the overview remains a sharp, thin trace with no points.
3. Zoom in to level 14/15. **Verify** that individual workout lines are prominent and clean, with NO marker pins visible.
4. Zoom in to level 18. **Verify** that individual tracks are solid (100% visible) and the heatmap is almost invisible.
