# Implementation Plan - ATT-342 Final Refinement: Total Path Priority & Marker Culling

Address the visual heaviness of the heatmap and the OOM crash caused by marker overload. This refinement ensures that as you zoom in, the heatmap recedes to a subtle background glow while individual workout traces become fully opaque and clean.

## User Review Required

> [!IMPORTANT]
> - **Crash Fix (Marker Culling)**: I identified that the "points along the paths" are the hundreds of marker pins for every member workout. Rendering these is what caused the app to crash. I will now **completely hide member markers** by default to ensure 100% stability and a clean look.
> - **Subtle Heatmap**: At high zoom levels, the heatmap weight will be reduced by another 90%. It will become a faint "density shadow" rather than a dominant blue band.
> - **Opaque Paths**: Individual workout lines will become **100% opaque** as soon as you zoom in (level 14+), ensuring your data is the primary focus.

## Proposed Changes

### 1. Map DSL Layer: Data-First Blending
Fulfills REQ-MAP-016 (Refinement) | Test: TST-MAP-010

#### [MODIFY] [MapContentScope.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapContentScope.kt)
- **Refine `Render` loop**:
    - Implement a "Receding Heatmap" schedule that aggressively favors individual traces:
        | Zoom | Track Alpha | Heatmap Weight | Max Intensity | Member Marker Alpha |
        |:---|:---|:---|:---|:---|
        | < 13 | 0.4f | 0.005 | 20.0 | 0.0 (Hidden) |
        | 13-14 | **0.8f** | **0.001** | **60.0** | **0.0** (Hidden) |
        | 15-16 | **1.0f** | **0.0005**| **100.0**| **0.0** (Hidden) |
        | 17+ | **1.0f** | **0.0002**| **200.0**| **0.1** (Faint Ghost) |
- **Rationale**:
    - By zoom level 15, the tracks are 100% opaque.
    - The heatmap weight is so low that it only highlights the "core" of your most popular routes as a subtle glow.
    - Member markers are kept hidden to prevent visual clutter and OOM crashes.

### 2. Map Layer Foundation: Stable Markers
#### [MODIFY] [MapLayers.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapLayers.kt)
- **Refinement**: Ensure the primary cluster signature (Start/End/Apex) always stays at 100% alpha and is rendered on top of everything else to maintain navigational reference.

## Verification Plan

### Manual Verification (TST-MAP-010 Refined)
1. Open the 'Solitude Runde' cluster heatmap.
2. **Verify** that at zoom level 11/12, the overview is thin and sharp.
3. Zoom in to level 14/15. **Verify** that the individual workout lines are now clearly the dominant element and look like clean, solid traces.
4. **Verify** that there are no "points" (pins) cluttering the path at these levels.
5. **Stability Audit**: Rapidly zoom in and out to verify that the `OutOfMemoryError` is permanently resolved.
