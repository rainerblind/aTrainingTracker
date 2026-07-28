# Task List - ATT-454: Standardize Period Visualization

- `[x]` **Storage & Preference Cleanup**
    - `[x]` Remove `IS_HEATMAP_ENABLED` from `MyPreferenceManager.kt`
    - `[x]` Remove `isHeatmapEnabledFlow` and `setHeatmapEnabled`
- `[x]` **Logic Layer Refinement**
    - `[x]` Remove `toggleHeatmapEnabled()` from `PeriodsViewModel.kt`
    - `[x]` Remove `isHeatmapEnabled` StateFlow from `PeriodsViewModel.kt`
- `[x]` **UI Component Cleanup**
    - `[x]` Update `PeriodSummaryCard.kt` (Remove toggle support, hardcode enabled)
    - `[x]` Remove heatmap toggle from `PeriodsTabsScreen.kt`
    - `[x]` Remove heatmap toggle from `PeriodMapScreen.kt`
    - `[x]` Remove `isHeatmapEnabled` parameter from `InteractivePeriodMap.kt` and `PeriodMapUtils.kt`
- `[x]` **Integration Update**
    - `[x]` Update `PeriodsFragment.kt` to reflect UI signature changes
- `[x]` **Verification & Documentation**
    - `[x]` Perform static audit for unused parameters
    - `[x]` Create walkthrough artifact
    - `[x]` Update Jira ticket status
