# Stage 5 Walkthrough: ATT-2940

## High-Glanceability Route Polyline Scaling and Solid Line Verification

### 1. Overview & Forensic Clarification
During Stage 4 construction and human review, the architectural boundary between **passive background routes** (`MapRoute.isActiveNavigation == false`) and the **single actively navigated route** (`MapRoute.isActiveNavigation == true`) was sharpened:
- The user actively following a route requires maximum glanceability and visual prominence:
  - Polyline width: `26f` (`MapVisualization.ROUTE_ACTIVE_NAVIGATION_WIDTH`).
  - Color: `TTColor.RouteActiveNavigation`.
  - Pattern: `null` (solid line, eliminating dashed overlay clutter).
- Passive active routes displayed on the map that the athlete is not actively following remain untouched:
  - Width: `10f` (`MapVisualization.ROUTE_WIDTH`).
  - Pattern: `listOf(Dash(15f), Gap(15f))`.

### 2. Implementation Changes
- **`app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapModels.kt`**:
  - `MapStyle.routeWidth` remains `10f`.
  - `MapVisualization.ROUTE_WIDTH` remains `10f`.
  - `MapVisualization.ROUTE_ACTIVE_NAVIGATION_WIDTH` set to `26f`.
  - `MapRoute.pattern`:
    ```kotlin
    val pattern: List<PatternItem>?
        get() = if (isActiveNavigation) {
            null
        } else {
            listOf(Dash(15f), Gap(15f))
        }
    ```
- **`app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapRouteActiveNavigationTest.kt`**:
  - Unit tests updated to assert `10f` width and dashed pattern for passive active routes, and `26f` solid line for active navigation route.
  - Z-Index invariant verified ($20f < 24f < 28f < 30f < 100f$).

### 3. Verification & Clean-Room Regression Results
- **Targeted Unit Tests**:
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.MapRouteActiveNavigationTest"` -> **PASSED** (100% pass rate).
- **Full Clean-Room Regression**:
  - `./gradlew testDebugUnitTest` -> **PASSED** (2,298 tests, 0 failures, 100% pass rate).
- **Living Documentation**:
  - `REQ-MAP-040` in `docs/requirements.md` set to `Verified`.
  - `TST-MAP-042` in `docs/tests.md` set to `Verified`.
