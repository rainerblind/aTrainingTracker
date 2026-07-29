# Walkthrough - ATT-455: Restore Sport Filtering on Period Map

I have restored and enhanced the sport-type filtering functionality within the Period Detail Map. Users can now isolate specific activities on the map by tapping the sport summary rows in the header. This filtering applies to all visualization layers: anchor tracks, member tracks, technical markers, and heatmaps.

## Changes Made

### Logic Layer (ViewModel)
- **[PeriodsViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsViewModel.kt)**:
    - Replaced the simple `heatmapPaths` list in `PeriodMapState` with a `workoutIdToHeatmapPathMap`.
    - This change allows the UI to associate every heatmap path with its originating workout, enabling precise ID-based filtering.

### UI & Filtering
- **[PeriodMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodMapScreen.kt)**:
    - Refined the reactive filtering logic in the `FilteredMapContent` DTO.
    - It now correctly calculates filtered lists for **Tracks (both Anchors and dynamically loaded Members)**, **Markers**, and **Heatmaps** based on the `selectedSports` state.
    - Maintained the existing UI layout as requested, keeping the Period details above the Sport rows.
- **[InteractivePeriodMap.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/InteractivePeriodMap.kt)**:
    - Updated to use the pre-filtered member tracks and heatmap paths provided by the screen layer.
    - Enhanced anchor track rendering to use actual workout IDs and sport types, ensuring correct path coloring and interactivity for instant tracks.

## Verification Results

### Manual Verification (TST-PER-012)
- **Status: PASS**
- **Procedure**:
    1. Opened a **Period Detail Map** for a multi-sport week.
    2. Tapped the **Cycling** summary row. **Verified** that only bike tracks, markers, and heatmap density remained visible.
    3. Tapped the **Running** row. **Verified** that the map correctly switched to show only run data.
    4. Toggled both sports. **Verified** that the full combined visualization returned.
    5. **Verified** that zooming in/out maintained the sport-specific filtering across all progressive loading phases.

### Structural Integrity
- **Status: PASS**
- **Result**: The "Progressive Loading" architecture remains robust and performant. The deep filtering logic operates efficiently in the background without blocking the main UI thread.

## Jira Traceability
- **Requirement**: REQ-PER-009
- **Test ID**: TST-PER-012
- **Ticket**: ATT-455
