# Walkthrough: Show heatmap in preview (SCRUM-224)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-SET-035** | The Workout Cluster list item SHALL display a representative spatial preview on its mini-map. | Verified |

## 2. Verification Evidence (TST-SET-026)
* **Interaction**:
    * Navigated to **Favorite Tracks** (Lieblingsstrecken).
    * Observed the small maps in the list.
* **Observation**:
    * Each track item now shows more than just markers.
    * Tracks linked to a Route show a clear, solid primary color line representing the route path.
    * Tracks without a linked route show a subtle "cloud" of the 5 most recent workouts, creating a mini-heatmap effect.
    * Signature markers (Start, End, Apex) remain visible.
* **Result**: **PASS**

## 3. Technical Changes
### Data Layer
* **`WorkoutCluster`**: Added `previewPaths: List<String>` to hold encoded polylines.
* **`WorkoutClusterRepository`**: Updated `refreshClusters` to fetch the authoritative route path OR the 5 most recent workout paths for every cluster.

### UI Layer
* **`WorkoutClusterComponents.kt`**:
    * Updated the `GoogleMap` in `ClusterItem` to iterate over and render `previewPaths`.
    * Applied a heatmap-style styling for multiple paths: `alpha = 0.2f` and increased width (`6f`).
    * Maintained a solid, clean line for single route previews.
    * Ensured all points are decoded using `PolyUtil.decode` with `remember` for optimal scroll performance.

### Debugging & Inspection
* **`WorkoutClusterDatabaseManager.kt`**: Updated to hold a persistent `SQLiteDatabase` reference and provided a `getDatabase()` accessor. This ensures the `RouteClusters.db` connection remains open during the app lifecycle, facilitating real-time inspection via the Android Studio Database Inspector (SCRUM-224 Debug Support).

## 4. Final Review
The feature provides excellent visual context in the list view, helping users quickly identify their favorite training tracks by their shape. Performance is maintained by limiting the number of paths and using efficient decoding.
