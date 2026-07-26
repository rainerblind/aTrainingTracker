# Implementation Plan - ATT-361: Initial Cluster Loading Progress

Implement a detailed progress notification for the Workout Clusters (My Locations) screen. The system will provide granular feedback about hit-count self-healing and preview path preparation, aligning with the project's 'Migration Status' design pattern.

## User Review Required

> [!IMPORTANT]
> - **Visual Consistency**: The progress card will use the same 'secondaryContainer' styling as the Periods sync to maintain a unified world-class analytical feel.
> - **Granular Messaging**: Users will see exactly what the system is doing, from "Self-healing hit counts" to "Preparing family X of Y".

## Proposed Changes

### 1. Repository Logic: Phase-Aware Signaling
Fulfills REQ-PER-003 | Test: TST-PERF-003

#### [MODIFY] [WorkoutClusterRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterRepository.kt)
- **Status Flow**: Expose `val migrationStatus: StateFlow<MigrationStatus?>`.
- **Refactor `refreshClusters()`**:
    1.  Immediately emit `MigrationStatus("Loading route families...", 0.0f)`.
    2.  Update to `"Verifying history integrity..."` during the hit-count self-healing phase.
    3.  During the preview path generation loop, update the status with `"Preparing family previews ([X]/[Total])..."` and the percentage.

### 2. UI Layer: Dynamic Message Rendering
Fulfills REQ-PER-003 | Test: TST-PERF-003

#### [MODIFY] [WorkoutClustersViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt)
- Propagate the repository's `migrationStatus` flow to the UI.

#### [MODIFY] [WorkoutClustersTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersTabsScreen.kt)
- **Standardized Progress Card**: Integrate the `MigrationStatus` card (Surface with Circular/Linear Progress) above the tabbed lists.
- Use the same localized strings patterns as Periods.

### 3. Localization
Fulfills REQ-UI-106

#### [MODIFY] `strings.xml` (and translations)
- Add keys:
    - `cluster_migration_loading`: "Loading route families..."
    - `cluster_migration_healing`: "Verifying history integrity..."
    - `cluster_migration_previews`: "Preparing family previews (%1$d of %2$d)..."

## Verification Plan

### Manual Verification (TST-PERF-003)
1. Navigate to 'My Locations'.
2. **Verify** that the progress card appears immediately with a clear description of the current task.
3. **Verify** that the progress bar increments accurately during the preview preparation phase.
4. **Verify** that the UI remains responsive and the card disappears automatically upon completion.
