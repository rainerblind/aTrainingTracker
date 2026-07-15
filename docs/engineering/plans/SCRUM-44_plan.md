# Implementation Plan: Agnostic Route Learning Engine (SCRUM-44)

## 1. Problem Statement
The current activity naming system requires manual setup of "Known Locations." Modern athletes expect the application to automatically recognize repeated routes and suggest meaningful names and sports based on historical behavior.

## 2. Requirement Traceability
| Requirement ID | Component | Description | Test ID |
|:---|:---|:---|:---|
| **REQ-SET-006** | Data | Agnostic Route Clustering based on spatial fingerprints. | TST-SET-001 |
| **REQ-SET-007** | Logic | Inferred Sport and Name suggestions from history. | TST-SET-001 |
| **REQ-SET-008** | Logic | Continuous Learning Feedback Loop with centroid refinement. | TST-SET-001 |

## 3. Impact Analysis
* **Data Integrity**: Introduces a new database `RouteClusters.db`. Does not modify existing summary/sample schemas (backward compatible).
* **Startup Performance**: Historical migration runs on a background thread; no impact on main thread or UI responsiveness.
* **Component Interfaces**: `WorkoutDataMapper` now has a dependency on `RouteClusterEngine`.

## 4. Proposed Changes
### Data Layer
* **`RouteClusterDatabaseManager.kt`**: Manage `RouteClusters.db` and SQL-based spatial candidate filtering.
* **`RouteClusterEngine.kt`**: Implement similarity scoring and history migration logic.

### UI & Repositories
* **`WorkoutDataMapper.kt`**: Inject engine suggestions during cursor-to-UI-model mapping.
* **`WorkoutRepository.kt`**: Update `saveWorkout` to feed user edits (Name/Sport) back into the engine.

### System
* **`TrainingApplication.java`**: Orchestrate one-time historical migration.
* **`WorkoutSummariesDatabaseManager.java`**: Add ASC sorting for chronological replay.

## 5. Verification Criteria (TST-SET-001)
1. Record a workout.
2. Manually set Name="Park Loop" and Sport="Running".
3. Record a similar workout.
4. Verify the system automatically applies "Park Loop #2" and "Running".
5. Change name to "Lake Run" and verify future sessions use the new identity.
