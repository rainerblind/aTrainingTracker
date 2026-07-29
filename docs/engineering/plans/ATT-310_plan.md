# Implementation Plan - ATT-310: Fix OOM in WorkoutPeriods Heatmap

Address the `java.lang.OutOfMemoryError` caused by excessive point densification in period-based heatmap visualizations, while maintaining high quality in interactive views.

## User Review Required

> [!IMPORTANT]
> This change introduces a dual-resolution strategy.
> - **Summary Cards (List View)**: Use coarse adaptive densification (10m - 200m) to ensure smooth scrolling and prevent OOM.
> - **Interactive Map (Detail View)**: Use a balanced high-resolution (10m) combined with memory-efficient imperative point collection.

## Proposed Changes

### UI & Map Components

#### [MODIFY] [MapUtils.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapUtils.kt)
- Update `createHeatmapProvider` to accept a `densifyInterval` parameter.
- Refactor the point collection logic: Replace the `flatMap`/`map` functional chain with a memory-efficient imperative loop using a single `ArrayList`. This minimizes GC pressure and prevents intermediate object explosion.

#### [MODIFY] [PeriodMapUtils.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodMapUtils.kt)
- Update `getPeriodMapVisuals` to accept an `isInteractive: Boolean` flag.
- Implement adaptive `densifyInterval` logic:
    - **Interactive**: 10 meters (high quality).
    - **Non-Interactive (List)**:
        - `WEEK`: 10 meters
        - `MONTH`: 50 meters
        - `YEAR`: 200 meters

#### [MODIFY] [PeriodSummaryCard.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodSummaryCard.kt)
- Pass `isInteractive = false` to `getPeriodMapVisuals` when rendering list items.

#### [MODIFY] [InteractivePeriodMap.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/InteractivePeriodMap.kt)
- Pass `isInteractive = true` to `getPeriodMapVisuals` for the full-screen interactive view.

## Verification Plan

### Automated Tests
- None.

### Manual Verification
- **TST-PERF-001** (Jira [ATT-310](https://atrainingtracker.atlassian.net/browse/ATT-310)):
    1. Verify that Yearly summaries for years with many workouts (e.g., imported history) load without crashing.
    2. Verify that the heatmap in the detail map remains high-quality when zooming in.
