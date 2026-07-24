# Implementation Plan - ATT-346 Refinement: Incremental UI Updates & Progress Visibility

Address the issue where the Periods screen remains empty during background synchronization by implementing a RAM-based streaming aggregator that provides instant, incremental updates to the UI.

## User Review Required

> [!IMPORTANT]
> - **Streaming Feedback**: Stats will now appear on the screen **as they are being calculated**, workout by workout. You won't have to wait for the entire history to be processed before seeing results.
> - **Reliable Progress**: Fixed a logic error that could hide the progress bar if the initial database check was performed before history was loaded into memory.

## Proposed Changes

### Repository Logic: RAM-First Streaming Aggregator

#### [MODIFY] [PeriodsRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsRepository.kt)
- **State Initialization**: Ensure `_migrationProgress` is set to `0.0f` immediately if the database cache is found to be incomplete, even before workouts are available.
- **Incremental Rebuild Loop**:
    1. Maintain four `LinkedHashMap<String, PeriodSummary>` objects in the repository's scope during `rebuildDatabase`.
    2. As each workout is processed (newest-first):
        - Perform the suggested comparison/merge algorithm in RAM.
        - **Periodically Emit**: Every 10-20 workouts, convert the RAM maps to the UI list format (`List<List<PeriodSummary>>`), enrich with Anchor Routes, and update the `_groupedPeriods` flow.
    3. **Background Persistence**: Continue saving to the SQLite database in the background. The UI will stay responsive by using the RAM-based stream.
- **Atomic "Finished" Signal**: Set `is_finished = true` only after the last workout is saved and the database transaction is committed.

### UI Layer Refinement

#### [MODIFY] [PeriodsTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsTabsScreen.kt)
- Ensure the progress card is handled correctly during rapid incremental updates.

## Verification Plan

### Manual Verification
1. Clear app data (to force a full rebuild).
2. Open the "Periods" screen.
3. **Verify** that a progress bar appears **immediately**.
4. **Verify** that the list of periods starts populating with the newest workouts and grows as the bar progresses.
5. **Verify** that once finished, the list is complete and the progress bar disappears.
