# Implementation Plan - ATT-441: Persistent Cluster Previews

Refine the Workout Cluster refresh logic to ensure that enriched metadata (Preview Paths and Linked Routes) is persisted in the relational database. This fulfills the requirement that heavy analysis "runs once and the result is stored properly," enabling near-instant, silent refreshes even after an app restart.

## User Review Required

> [!IMPORTANT]
> - **Database Upgrade (v8)**: This change involves a schema update to the `RouteClusters` table. All existing route families will be automatically enriched with their persisted previews upon the first visit after the update.
> - **Persistence Strategy**: Previews (the last 5 workout tracks) and linked authoritative routes will now be stored as encoded strings in the database.
> - **Silent Background Updates**: While the core metadata is now persistent, the system will still perform O(1) background updates to the "Last 5" previews whenever a new workout is recorded, ensuring the map previews remain fresh without showing a blocking progress card.

## Proposed Changes

### 1. Database Layer: Relational Persistence (v8)
#### [MODIFY] [WorkoutClusterDatabaseManager.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterDatabaseManager.kt)
- Bump `DB_VERSION` to **8**.
- Add `COLUMN_PREVIEW_PATHS` (TEXT) and `COLUMN_ROUTE_POLYLINE` (TEXT) to the schema.
- Update `WorkoutCluster` data class mapping to handle these new fields.
- Implement `serializePreviewPaths` (JSON/Comma-separated) and `deserializePreviewPaths`.

### 2. Repository Logic: One-Time Enrichment
#### [MODIFY] [WorkoutClusterRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterRepository.kt)
- **Refactor `refreshClusters`**:
    - Check if the loaded `rawClusters` already have `previewPaths` or `routePolyline` populated.
    - If data is missing for a cluster, perform the enrichment pass and **persist** the result back to the database.
    - If all clusters are already enriched, emit the list immediately and bypass the `migrationStatus` UI entirely.
- **Background Maintenance**:
    - Ensure that `recalculateClustersWithProgress` and `repairClusterMetadata` also persist their findings to the new DB columns.

### 3. Engine Integration: Dynamic Refresh
#### [MODIFY] [WorkoutClusterEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt)
- Update `assignClusterToWorkout` to trigger a silent, O(1) preview update for the affected cluster, ensuring the persisted "Last 5" tracks remain current.

## Verification Plan

### Automated Tests
#### [NEW] [TST-PERF-010](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md)
- **Procedure**:
    1. Open the app and navigate to "My Locations".
    2. Wait for the initial "Phase 2: Previews" migration to complete.
    3. **Force Close** the app.
    4. **Restart** the app and navigate back to "My Locations".
    5. **Verify** that the list appears INSTANTLY with all map previews visible.
    6. **Verify** that NO progress notification is shown on this second visit.

### Manual Verification
- Record a new workout on a clustered route.
- Verify that the cluster's preview in the list updates to include the new track (background refresh).
- Verify that the "Analyzing route families..." card still appears if the user manually wipes the app data (fresh install/bootstrap).
