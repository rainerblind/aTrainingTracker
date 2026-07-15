# SCRUM-207: Route-to-Cluster Synchronization

## 1. Requirement Summary
*   **Goal**: Every explicit Route in the system (imported from GPX, Strava, or created from a workout) should have a corresponding Route Cluster.
*   **Rationale**: Manually maintained routes are authoritative spatial definitions and should contribute to the learning knowledge base and automatic workout classification.

## 2. Implementation Overview

### Core Logic
*   **`RouteClusterEngine.kt`**:
    *   Implemented `learnFromRoute(RouteWithPath)`: Calculates the spatial fingerprint (Start, End, Apex) for an explicit route and seeds the cluster database.
    *   Updated `migrateHistory()`: Now processes all existing Routes first as authoritative seeds before processing Workout history.
    *   Implemented `calculateLineDistance()`: Provides geometric distance from a point to a line segment for apex calculation.

### Data Layer
*   **`RoutesRepository.kt`**:
    *   Refactored `insertRoute()` to automatically call the cluster engine when a new route is added (GPX import, manual creation).
    *   Updated `updateRouteSummary()` to trigger `syncRouteNameChange()` in the cluster engine, ensuring that manual route renames propagate to the corresponding spatial grouping.
    *   Integrated cluster seeding into the Strava synchronization flow.

## 3. Verification Details
*   **Test Case**: `TST-SET-022`
*   **Steps**:
    1.  Imported a GPX route.
    2.  Verified that a new Route Cluster with the same name and spatial fingerprint was created.
    3.  Triggered a "Recalculate History" and verified that the imported route served as a primary seed for subsequent workout matching.
*   **Result**: **PASS**
