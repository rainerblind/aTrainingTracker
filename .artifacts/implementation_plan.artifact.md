# Implementation Plan - ATT-342 Refinement: Zoom-Adaptive Alpha & Precision Blending

Address the visual "heaviness" and "pointiness" of the cluster view by implementing a dynamic blending system that transitions from a high-level heatmap to prominent individual workout traces as the user zooms in.

## User Review Required

> [!IMPORTANT]
> - **Smooth Transition**: When you zoom in (level 13+), the "bloody" heatmap will automatically fade into the background, and the **individual workout paths** will become more solid and opaque.
> - **Zero Pointiness**: I identified that the "points along the paths" are the hundreds of marker pins for every member workout. I will now **hide these markers** at medium zoom levels and only show them when you zoom in very close (level 16+), ensuring a clean and professional look.
> - **Balanced Clarity**: By increasing the alpha of individual workouts as you zoom in, you can see exactly where each session went, while the heatmap provides a subtle "density glow" in the background.

## Proposed Changes

### 1. Map DSL Layer: Intelligent Blending Schedule
Fulfills REQ-MAP-016 (Refinement) | Test: TST-MAP-010

#### [MODIFY] [MapContentScope.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapContentScope.kt)
- **Refine `Render` loop**:
    - Implement a multi-stage schedule for blending Heatmaps, Tracks, and Member Markers:
        | Zoom | Track Alpha | Heatmap Weight | Start Intensity | Member Marker Alpha |
        |:---|:---|:---|:---|:---|
        | < 12 | 0.3f | 0.01 | 0.2f | 0.0f (Hidden) |
        | 13-14 | **0.5f** | **0.05** | **0.5f** | **0.1f** (Very Faint) |
        | 15-16 | **0.7f** | **0.10** | **0.6f** | **0.4f** |
        | 17+ | **0.9f** | **0.20** | **0.6f** | **1.0f** |
- **Logic Refinement**:
    - Apply the `trackAlpha` multiplier to all items in `trackData`.
    - Apply the `markerAlpha` multiplier to all `markers`.
    - Ensure the "Source" cluster signature (Start/End/Apex) always remains at 100% visibility for reference.

## Verification Plan

### Manual Verification (TST-MAP-010 Refined)
1. Open the 'Solitude Runde' cluster heatmap.
2. **Verify** that at zoom level 11/12, no marker pins are visible along the path, resulting in a clean line.
3. Zoom in to level 14. **Verify** that individual workout lines become more visible and the heatmap "glow" is subtle.
4. Zoom in to level 17+. **Verify** that individual traces are prominent and the member marker pins appear clearly for detailed analysis.
