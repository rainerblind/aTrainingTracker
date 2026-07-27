# Implementation Plan - ATT-440 Final Bliss: Reactive Visual Enrichment

Address the missing bar graphs and summary maps in the Periods view by ensuring the repository reactively enriches period data as workout history arrives, and refining the layout for persistent visibility.

## User Review Required

> [!IMPORTANT]
> - **Guaranteed Visuals**: I am refactoring the repository to ensure that as soon as your workouts are loaded from the database, the Period summaries (Mini-maps and Graphs) are automatically updated. No more empty cards.
> - **Layout Stability**: I am refining the padding logic to ensure that the Bar Graph is always visible in the "un-collapsed" state and correctly follows the header during scrolling.
> - **Total Data Sync**: The "Periods" view will now be a perfect, real-time reflection of your entire workout history.

## Proposed Changes

### 1. Repository Layer: Robust Reactive Enrichment
Fulfills REQ-PER-007 | Test: TST-PERF-008

#### [MODIFY] [PeriodsRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsRepository.kt)
- **Refactor `init`**:
    - Ensure `loadFromDatabase` is called as soon as the app starts to show cached data.
    - Synchronize the `allWorkouts` observer to trigger a re-enrichment pass (`loadFromDatabase(forceIncremental = true)`) whenever new sessions are discovered.
- **Enrichment Logic**: Ensure `enrich()` is called even if `allWorkouts` is initially small, and updates as it grows.

### 2. UI Layer: Header & Graph Visibility
Fulfills REQ-UI-104 | Test: TST-UI-076

#### [MODIFY] [PeriodsTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsTabsScreen.kt)
- **Correct Padding**:
    - The content `Column` must use the *un-collapsed* header height for its initial padding.
    - As the user scrolls, the `Column` should move up, but we must ensure the `PeriodBarGraph` is not immediately swallowed by the header.
- **Z-Index Check**: Ensure the header `Surface` is correctly positioned on top of the content.

## Verification Plan

### Manual Verification (TST-UI-076 Refined)
1. Open the 'Periods' screen.
2. **Verify** that the summary cards show their mini-maps as the history loads.
3. **Verify** that the Bar Graph is visible at the top of the list.
4. **Scroll Audit**: Ensure the graph and list move together and transition smoothly into the collapsed header state.
