# Implementation Plan - ATT-379: Dual-Phase Hierarchical Aggregator for Periods

Optimize the training history aggregation engine to provide instant visual feedback and eliminate O(N^2) processing bottlenecks during the initial migration.

## User Review Required

> [!IMPORTANT]
> - **Dual-Phase Sync**: The system will first perform a high-speed database read (Phase 1) and then proceed to a prioritized hierarchical sync (Phase 2).
> - **O(N) Efficiency**: Re-grouping of thousands of workouts is now performed only **once** per sync pass, ensuring the UI remains perfectly fluid even for power users.
> - **Immediate Visibility**: Period cards will appear on the screen as each month bucket is committed, matching the "burst" speed of the Workouts tab.

## Proposed Changes

### 1. Repository: The Dual-Phase Aggregator
Fulfills REQ-PER-004 | Test: TST-PERF-005

#### [MODIFY] [PeriodsRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsRepository.kt)
- **Refactor `performHierarchicalMigration()`**:
    - **Phase 1 (Rapid Read)**: Execute a direct database cursor scan to read all workouts into memory. Show localized progress: `"Reading workout [X] of [Total]..."`.
    - **Global Pre-grouping**: Perform the O(N) grouping (Day/Week/Month/Year) **exactly once** for the entire history.
    - **Phase 2 (Priority Sync)**: Iterate through Month buckets. After each month's transaction:
        - Call `loadFromDatabase(forceIncremental = true, precalculatedGroups = groups)`.
        - This allows the UI to "pump" in new data without expensive redundant calculations.
- **Refined `loadFromDatabase`**:
    - Add an optional `precalculatedGroups: WorkoutGroups?` parameter to completely bypass the O(N) re-grouping logic during migration refreshes.

## Verification Plan

### Manual Verification (TST-PERF-005)
1. Trigger a full Periods sync (Version 17 restart).
2. **Verify** that the status bar shows "Reading..." first, then transitions to "Syncing...".
3. **Verify** that as each month is processed, the list of training cards **immediately grows** on the screen.
4. **Verify** that UI interaction (scrolling) remains smooth throughout the high-frequency sync pass.
