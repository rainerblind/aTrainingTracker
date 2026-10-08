# Stage 3 Implementation Plan - ATT-2509

**Ticket**: [ATT-2509](https://atrainingtracker.atlassian.net/browse/ATT-2509)  
**Summary**: Color Climb Polylines on Route Map to Highlight Climb Spans  
**Active Sprint**: `2026-41.3`  
**Requirement Mapping**: `REQ-UI-298`  
**Test Mapping**: `TST-UI-258`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Architectural Design & UI Consistency Audit (Rule 23)

### 1.1 Architectural Overview (SWE.2)
```mermaid
flowchart TD
    subgraph RouteOnMapScreen
        A[RouteSummary] --> B[route?.climbs]
        C[MapDetailLayout] --> D[mapContent]
    end
    
    subgraph MapContentScope DSL
        D -->|1. Base Route| E[routes(listOf(route))]
        D -->|2. Climb Spans| F[climbs(climbs)]
        D -->|3. Pins| G[markers(allMarkers)]
    end
    
    subgraph GoogleMap GL Canvas
        E --> H[Route Base Polyline zIndex=20-21f]
        F --> I[Climb Highlight Overdraw zIndex=25f]
        G --> J[Start / End / Ascent Pins zIndex=50f]
    end
```

### 1.2 UI Consistency & Color Harmony (Rule 23)
- **Zero Ad-Hoc Hex Colors**: Polyline colors are mapped strictly through the canonical color token provider `getClimbCategoryColors(climb.category).first`.
- **100% Cross-Screen Color Synchronization**:
  - Climb polyline color on map == Start marker pin background (`createAscentMarker(context, bgColor)`)
  - Climb polyline color on map == `ClimbCategoryChip(category)` in `RouteClimbsBreakdownSection`
  - Climb polyline color on map == `LiveClimbSheet` status banner and chips
- **Stroke Width & Cap Ergonomics**:
  - `width = 10f` (matching `MapVisualization.ROUTE_WIDTH`) ensures perfect coverage without visual bloat.
  - `jointType = JointType.ROUND` ensures corner curvature without jagged artifacts.
- **Unclassified Sections**:
  - `ClimbCategory.UNCATEGORIZED` climbs and non-climbing flats/descents remain untouched, retaining `TTColor.RouteSelected` (ForestGreen).

---

## 2. Step-by-Step Atomic Implementation Steps (SWE.3)

### Step 1: Extend `MapContentScope` Interface & Implementation (`MapContentScope.kt`)
1. **Interface Declaration**:
   Add to `MapContentScope`:
   ```kotlin
   /**
    * Renders climb highlights along a route or track color-coded by climb category (REQ-UI-298 / ATT-2509).
    */
   fun climbs(climbs: List<Climb>)
   ```
2. **State & Data Model**:
   Inside `MapContentScopeImpl`:
   ```kotlin
   data class ClimbHighlightData(
       val path: List<LatLng>,
       val color: Color,
       val zIndex: Float = 25f,
       val width: Float = 10f
   )
   private val climbHighlights = mutableStateListOf<ClimbHighlightData>()
   ```
3. **Implementation Method**:
   ```kotlin
   override fun climbs(climbs: List<Climb>) {
       climbs.forEach { climb ->
           if (climb.category != ClimbCategory.UNCATEGORIZED) {
               val points = if (climb.pathPoints.isNotEmpty()) {
                   climb.pathPoints.map { it.latLng }
               } else {
                   listOf(climb.startLatLng, climb.endLatLng)
               }
               if (points.size >= 2) {
                   val (color, _, _) = getClimbCategoryColors(climb.category)
                   this.climbHighlights.add(ClimbHighlightData(points, color))
               }
           }
       }
   }
   ```
4. **Lifecycle Management**:
   Add `climbHighlights.clear()` to `clear()`.
5. **Render Pass**:
   Inside `Render(currentZoom: Float)` under section 6:
   ```kotlin
   // 6c. Climb Span Highlights (REQ-UI-298 / ATT-2509)
   climbHighlights.forEach { highlight ->
       Polyline(
           points = highlight.path,
           color = highlight.color,
           width = highlight.width,
           zIndex = highlight.zIndex,
           jointType = JointType.ROUND
       )
   }
   ```

### Step 2: Wire Climb Highlights into `RouteOnMapScreen.kt`
In `RouteOnMapScreen.kt:135-138`:
```kotlin
        mapContent = {
            if (route != null) {
                routes(listOf(route))
                climbs(climbs)
```

### Step 3: Implement Unit & Contract Tests
1. **`ClimbPolylineContractTest.kt`**:
   - `testClimbsHighlightCreation_forAllStandardCategories`: Tests `CAT_4`, `CAT_3`, `CAT_2`, `CAT_1`, `HC`.
   - `testUncategorizedClimbs_areOmittedFromHighlightLayer`: Asserts 0 highlights for `UNCATEGORIZED`.
   - `testFallbackToEndpoints_whenPathPointsEmpty`: Asserts fallback to start/end points.
   - `testClimbPolylineZIndexAndWidth`: Asserts `zIndex = 25f`, `width = 10f`.
2. **`RouteOnMapScreenClimbContractTest.kt`**:
   - Asserts AST call to `climbs(climbs)` inside `RouteOnMapScreen.kt`.

### Step 4: Verification & Regression Gate
Run targeted unit tests and full regression:
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.ClimbPolylineContractTest"
./gradlew testDebugUnitTest
```

---

## 3. Preserved Invariants

1. **Route Interaction**: Click handling (`onRouteClick`), elevation scrubbing (`activeScrubPath`), and zoom-to-fit (`routeBounds`) remain completely unaffected.
2. **Marker Elevation**: Markers remain at `zIndex = 50f` above all polylines.
3. **Database Integrity**: Zero schema changes or persistence alterations.
4. **Localization Parity**: 100% maintained (zero new strings needed).
