# Implementation Plan - ATT-454: Standardize Period Visualization

Enforce a deterministic analytical experience by ALWAYS displaying heatmaps within the Periods module, eliminating redundant localized toggles and their underlying preferences.

## User Review Required

> [!IMPORTANT]
> **Always-On Heatmaps (REQ-UI-120)**: The Periods module SHALL ALWAYS display heatmaps to provide a consistent analytical experience. Heatmap visibility is governed exclusively by this permanent state, ensuring professional and data-rich visualization across all analytical views. The corresponding preferences are removed to minimize system complexity.

## Proposed Changes

### [Component] Storage & Preferences

#### [MODIFY] [MyPreferenceManager.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/MyPreferenceManager.kt)
- **Delete** `IS_HEATMAP_ENABLED` key.
- **Delete** `isHeatmapEnabledFlow`.
- **Delete** `setHeatmapEnabled` function.

### [Component] Periods Module UI & Logic

#### [MODIFY] [PeriodsViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsViewModel.kt)
- **Remove** `toggleHeatmapEnabled()`.
- **Remove** `isHeatmapEnabled` state flow.

#### [MODIFY] [PeriodsTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsTabsScreen.kt)
- **Remove** `isHeatmapEnabled` and `onToggleHeatmapEnabled` from parameters.
- **Delete** the `Whatshot` `IconButton` from the header.
- **Hardcode** `isHeatmapEnabled = true` when calling `PeriodList` and `PeriodSummaryCard`.

#### [MODIFY] [PeriodMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodMapScreen.kt)
- **Remove** `isHeatmapEnabled` and `onToggleHeatmapEnabled` from parameters.
- **Delete** the `Whatshot` (MODE TOGGLE) `Surface` button from the map overlay.
- **Hardcode** `isHeatmapEnabled = true` when calling `InteractivePeriodMap`.

#### [MODIFY] [PeriodsFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsFragment.kt)
- **Update** Composable calls to reflect the removed parameters.

#### [MODIFY] [PeriodSummaryCard.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodSummaryCard.kt)
- **Remove** `isHeatmapEnabled` from `PeriodSummaryCard` and `PeriodMultiWorkoutMap` parameters.
- **Hardcode** `isHeatmapEnabled = true` in the internal map logic.

## Verification Plan

### Manual Verification
- **TST-PER-010 (Jira: ATT-460)**:
    1. Open the **Periods** screen.
    2. **Verify** that the flame icon (Heatmap toggle) is no longer visible in the header.
    3. Navigate to a specific **Period Map**.
    4. **Verify** that the toggle button in the map overlay is gone.
    5. Navigate to **Map Settings** and toggle "Show Heatmap".
    6. Return to Periods and **Verify** the map correctly reflects the global change.

### Automated Checks
- Static audit to ensure no unused `onToggleHeatmapEnabled` lambda parameters remain in the `periodlist` package.
