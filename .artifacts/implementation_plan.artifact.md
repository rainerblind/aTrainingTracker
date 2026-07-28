# Implementation Plan - ATT-455: Restore Sport Filtering on Period Map

Restore functional sport-type filtering within the Period Detail Map while maintaining the established UI layout. Tapping a sport summary row will correctly filter all map components: anchor tracks, dynamically loaded member tracks, technical markers, and heatmaps.

## User Review Required

> [!IMPORTANT]
> **Deep Filtering (REQ-PER-009)**: Tapping the sport summary rows (positioned below the period header) will now act as a primary filter for the entire map visualization. This ensures that only the relevant activities (tracks, markers, and heatmap density) are displayed, providing a focused analytical context without altering the screen's visual structure.

## Proposed Changes

### [Component] Architecture & Data Layer

#### [MODIFY] [PeriodData.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodData.kt)
- **Update** `PeriodSummary`: Add `anchorIdToPolylineMap: Map<Long, String>` (or use `workoutIdToPolylineMap` for anchors as well) to allow sport-aware filtering of instant anchor tracks.
- **Update** `PeriodMapState`: Change `heatmapPaths: List<List<LatLng>>` to `workoutIdToHeatmapPathMap: Map<Long, List<LatLng>>` to enable ID-based filtering of the heatmap layer.

#### [MODIFY] [PeriodsRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsRepository.kt)
- **Update** `enrich` to ensure all spatial data is mapped to workout IDs.

### [Component] Logic Layer (ViewModel)

#### [MODIFY] [PeriodsViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsViewModel.kt)
- **Update** `showPeriodMap` to populate the ID-mapped paths in `PeriodMapState`.

### [Component] UI & Map Layer

#### [MODIFY] [PeriodMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodMapScreen.kt)
- **Retain** the existing header layout (Period details above Sport rows).
- **Refine Filtering**: Ensure `InteractivePeriodMap` receives only the data that matches the user's `selectedSports`.
- **Pass** filtered tracks, markers, and heatmap paths to the map component.

#### [MODIFY] [InteractivePeriodMap.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/InteractivePeriodMap.kt)
- **Update** to render only the filtered data provided by the screen layer.
- **Ensure** anchor tracks utilize the correct sport-type colors/styling.

## Verification Plan

### Manual Verification
- **TST-PER-012 (Jira: ATT-466)**:
    1. Open a **Period Detail Map**.
    2. **Verify** sport rows are at the top.
    3. **Tap** "Cycling" -> Map shows only bike paths.
    4. **Tap** "Running" -> Map shows only run paths.
    5. **Toggle Both** -> Map shows all.
    6. **Verify** heatmap intensity changes as sports are filtered.
