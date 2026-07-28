# Implementation Plan - ATT-440: Instant Map Zoom Optimization

Address the delay in zooming to period map details by optimizing the camera initialization logic and removing bottlenecks in the data-fitting pipeline.

## User Review Required

> [!IMPORTANT]
> - **Instant Focus**: I identified that the map was waiting for every single background tile to load before zooming in. I am refactoring this to zoom **immediately** as soon as the map is created.
> - **Stable Rendering**: I am fixing a bug where background data updates were accidentally resetting the map's zoom state. The map will now stay locked on your training area even as more data "streams in" in the background.

## Proposed Changes

### 1. UI Layer: Reactive Camera Optimization
Fulfills REQ-MAP-004 (Refinement)

#### [MODIFY] [ATrainingTrackerMap.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ATrainingTrackerMap.kt)
- **Accelerated Zoom**: Add `MapEffect` inside the `GoogleMap` block to fit `initialBounds` as soon as the map object is created, bypassing the slow `onMapLoaded` callback.
- **State Preservation**: Ensure `MapBoundsController` correctly distinguishes between the initial fit and subsequent dynamic updates.

#### [MODIFY] [InteractivePeriodMap.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/InteractivePeriodMap.kt)
- **Key Stability**: Update `remember(summary)` to `remember(summary.periodType, summary.startTimestampS)`. This prevents the map state from resetting when non-spatial properties (like the background path list) are updated during progressive loading.

### 2. UI Layer: Bounds Controller Refinement
#### [MODIFY] [MapBehaviors.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapBehaviors.kt)
- **Aggressive Fitting**: Allow `MapBoundsController` to attempt fitting even if `isMapLoaded` is false, provided that valid tracks or markers are available.

## Verification Plan

### Manual Verification
1. Open the 'Periods' screen.
2. Tap the map icon for any period.
3. **Verify** that the map zooms into the training area **instantly** (within milliseconds), even before all map tiles are fully rendered.
4. **Verify** that the zoom does not jump or reset as more workouts are loaded into the heatmap.
