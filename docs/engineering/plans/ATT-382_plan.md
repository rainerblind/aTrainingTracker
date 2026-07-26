# Implementation Plan - ATT-382: Multi-Phase Progress UI Refinement

Enhance the training history migration UI to include a descriptive title and explicit phase labeling (Phase 1/2) for improved transparency and professional feedback.

## User Review Required

> [!IMPORTANT]
> - **DTO Change**: The shared `MigrationStatus` model will now include a `title` field. Both Periods and Clusters will be updated to utilize this.
> - **Visual Hierarchy**: The progress card will feature a persistent bold title above the rotating status messages.
> - **Phase Prefixes**: Status messages will be prefixed with "Phase 1:" (Data Load) and "Phase 2:" (Aggregation) to clarify the technical process.

## Proposed Changes

### 1. Data Model Refinement
Fulfills REQ-PER-005 | Test: TST-PERF-006

#### [MODIFY] [MigrationStatus.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/util/MigrationStatus.kt)
- Add `val title: String` property.

### 2. Repository Refactoring
Fulfills REQ-PER-005 | Test: TST-PERF-006

#### [MODIFY] [PeriodsRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsRepository.kt)
- Update `performHierarchicalMigration()`:
    - Set title to `workout_periods__migration_title`.
    - Prefix reading status with `Phase 1:`.
    - Prefix syncing/finalizing status with `Phase 2:`.

#### [MODIFY] [WorkoutClusterRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterRepository.kt)
- Update `refreshClusters()` to include a high-level title in its status emissions.

### 3. UI & Localization
Fulfills REQ-UI-106 | Test: TST-PERF-006

#### [MODIFY] [PeriodsTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsTabsScreen.kt) & [WorkoutClustersTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersTabsScreen.kt)
- Render `status.title` using `titleMedium` typography at the top of the card.
- Maintain existing progress indicators and status messages below the title.

#### [MODIFY] `strings.xml` (and 8 translations)
- Add `workout_periods__migration_title`: "Introducing Periods Database..."
- Add `migration_phase_1_prefix`: "Phase 1: %s"
- Add `migration_phase_2_prefix`: "Phase 2: %s"

## Verification Plan

### Manual Verification (TST-PERF-006)
1. Navigate to 'Periods' (v19 restart).
2. **Verify** that the card shows the bold title at the top.
3. **Verify** that status messages are prefixed with "Phase 1:" during reading.
4. **Verify** that status messages are prefixed with "Phase 2:" during aggregation.
5. **Verify** that all text is correctly translated in German.
