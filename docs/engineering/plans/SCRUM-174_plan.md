# Implementation Plan: Route Cluster Tuning (SCRUM-174)

## 1. Problem Statement
The current route clustering parameters are hardcoded and may not work optimally for all users or environments. Users need a way to tune these parameters (Endpoint, Apex, and Distance tolerances) and trigger a full recalculation of the clustering knowledge base.

## 2. Requirement Traceability
| Requirement ID | Component | Description | Test ID |
|:---|:---|:---|:---|
| **REQ-SET-010** | UI | Tuning Parameters UI for Route Cluster Engine. | TST-SET-003 |
| **REQ-SET-011** | Logic | Full Clustering Recalculation. | TST-SET-003 |

## 3. Impact Analysis
* **Logic Layer**: `RouteClusterEngine` must be refactored to use dynamic parameters stored in `SharedPreferences`.
* **Data Layer**: `RouteClusterDatabaseManager` needs a `wipeDatabase()` method to clear learned clusters before recalculation.
* **UX**: Recalculation is a heavy task and must be performed on a background thread with visual feedback (e.g., a progress dialog).
* **Navigation**: Add a "Tuning" icon to the `FrequentPathsListScreen` top bar.

## 4. Proposed Changes
### Core Logic
* **`RouteClusterEngine.kt`**:
    * Introduce a `TuningParameters` data class.
    * Update `calculateSimilarity` and `findCandidates` to use these parameters.
    * Add `recalculateHistory()` which wipes the DB and runs `migrateHistory()`.
* **`RouteClusterDatabaseManager.kt`**:
    * Add `deleteAllClusters()`.

### UI Layer
* **`FrequentPathsViewModel.kt`**:
    * Add state for tuning parameters.
    * Add `recalculateClusters()` method.
* **`ClusterTuningScreen.kt`**: New Compose screen to edit sliders for:
    * Endpoint Tolerance (m)
    * Apex Tolerance (m)
    * Distance Tolerance (%)
* **`FrequentPathsListScreen.kt`**: Add Tuning action to the TopAppBar.

### Data Storage
* **`TrainingApplication.java`**: Define SP keys for tuning parameters and default values.

## 5. Verification Criteria (TST-SET-003)
1. Open "Regular Tracks".
2. Tap the "Tuning" icon.
3. Adjust "Endpoint Tolerance" to 300m.
4. Tap "Recalculate Clustering".
5. Observe the progress indicator.
6. Verify that the cluster list is updated once the background task finishes.
