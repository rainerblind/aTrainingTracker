# Implementation Plan - ATT-368: Neutralize Progress Notification Backgrounds

Refactor the 'Migration Status' progress notifications in the Periods and Workout Clusters screens to use a neutral surface background, complying with REQ-UI-101.

## User Review Required

> [!IMPORTANT]
> - **Design Transition**: The progress cards will move from a colored (*secondaryContainer*) background to a neutral *surface* background.
> - **Visual Distinction**: To ensure the card remains visible against the neutral background, a subtle *outlineVariant* border and 2dp tonal elevation will be added.

## Proposed Changes

### UI Component Refactoring
Fulfills REQ-UI-101 | Test: TST-UI-072

#### [MODIFY] [PeriodsTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsTabsScreen.kt)
- Change `Surface` color from `secondaryContainer` to `surface`.
- Add `border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)`.
- Set `tonalElevation = 2.dp`.

#### [MODIFY] [WorkoutClustersTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersTabsScreen.kt)
- Apply identical styling to the cluster migration progress card.

## Verification Plan

### Manual Verification (TST-UI-072)
1. Navigate to 'Periods' or 'My Locations' to trigger a sync/load.
2. **Verify** that the progress card background is neutral (white in light mode, dark surface in dark mode).
3. **Verify** that the card is clearly distinguishable via its outline.
