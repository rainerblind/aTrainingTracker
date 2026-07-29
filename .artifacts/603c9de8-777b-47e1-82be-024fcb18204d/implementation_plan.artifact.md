# Implementation Plan - ATT-441-FIX: Robust Preview Path Serialization

Correct the `StringIndexOutOfBoundsException` in `PolyUtil.decode` by replacing the ambiguous piped-string serialization with a robust JSON-based format. This ensures that polylines containing the `|` character (a valid character in the polyline algorithm) do not cause data corruption and application crashes.

## User Review Required

> [!CAUTION]
> - **Database Refresh (v9)**: To fix the existing corrupted data, I will bump the database version to **9** and clear the `preview_paths` column.
> - **Impact**: The "Analyzing route families..." progress notification will appear **one last time** on your next visit to the clusters screen to re-generate the correctly formatted previews. After this, it will remain silent as intended.

## Proposed Changes

### 1. Data Layer: JSON Serialization (v9)
#### [MODIFY] [WorkoutClusterDatabaseManager.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterDatabaseManager.kt)
- **Bump `DB_VERSION`** to **9**.
- **Refactor Serialization**:
    - Use `org.json.JSONArray` to store and retrieve preview paths.
    - This eliminates delimiter collisions with the polyline character set `[63, 126]`.
- **Migration Logic**:
    - In `onUpgrade(v8 -> v9)`, execute `UPDATE RouteClusters SET preview_paths = NULL` to clear corrupted data.

### 2. UI Layer: Defensive Decoding
#### [MODIFY] [WorkoutClusterComponents.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt)
- Wrap `PolyUtil.decode` in a `try-catch` block inside `ClusterItem`.
- If a string fails to decode, return an empty list instead of crashing the entire UI thread.

## Verification Plan

### Manual Verification
1. Launch the app after the update.
2. Navigate to "My Locations".
3. **Verify** that the "Analyzing route families..." notification appears and completes successfully.
4. **Verify** that NO CRASH occurs when rendering the cluster list.
5. Kill and restart the app.
6. **Verify** that the list appears instantly and silently, with all previews intact.
