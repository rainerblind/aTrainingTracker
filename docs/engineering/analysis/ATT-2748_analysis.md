# Stage 1: Problem Domain & Root Cause Analysis - ATT-2748: Remove Climb Pins from Route Map and Display UC Climbs on Map and Elevation Profile

**Ticket**: [ATT-2748](https://atrainingtracker.atlassian.net/browse/ATT-2748)  
**Parent Epic**: [ATT-2565](https://atrainingtracker.atlassian.net/browse/ATT-2565) (*[Epic] Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Requirement Mapping**: `REQ-UI-307` (Refining and amending `REQ-UI-298` and `REQ-UI-299`)  
**Sprint**: `2026-41.4`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  
**Status**: Completed (Ready for Gate 1 Review)  

---

## 1. Problem Statement & User Impact

During Sprint Review 2026-41.3 on a Google Pixel 10 (validating `ATT-2509` and `ATT-2510`), visual ergonomics and usability issues were identified regarding climb visualization across route maps and elevation profiles:

### 1.1 Redundant Climb Ascent Pin Markers on Route Map
When climb visualization was first designed under `REQ-UI-274`, climbs did not have spatial polyline overlays; they were represented solely by circular pin markers (`ic_ascent` with category background color at `climb.startLatLng`).
Subsequently, `ATT-2509` introduced direct polyline span highlighting along the route curve (`MapContentScope.climbs()`). With the route polyline itself vividly painted in the climb's category color, retaining individual circular `ic_ascent` marker pins at each climb's inception created redundant visual clutter, crowded the route line, and distracted athletes from critical Start/End route navigation pins.
User feedback was unambiguous:
> *"Please also remove the markers on the map."*

### 1.2 Missing Uncategorized (UC) Climbs on Map & Elevation Profile
In cycling telemetry, climbs that do not meet the steepness or elevation gain threshold for Category 4 (e.g. short steep kickers or long gradual drags) are categorized as `ClimbCategory.UNCATEGORIZED` (UC).
Currently:
1. In `MapContentScope.kt:482`, `climb.category != ClimbCategory.UNCATEGORIZED` explicitly skips UC climbs, preventing them from being rendered as colored polyline spans along the route curve.
2. In `ElevationProfile.kt:776`, `climbs.filter { it.category != ClimbCategory.UNCATEGORIZED }` explicitly skips UC climbs from being marked as horizontal span indicator bars along the X-axis baseline.

Athletes inspecting routes in hilly or rolling terrain observe that significant uphill efforts appear in the route climb breakdown list, but completely vanish from both the route map polyline and the elevation profile baseline.
User feedback during on-device inspection:
> *"This is i.O. for now. But I would also like to see the UC climbs here."* (pointing to the elevation profile and map).

### 1.3 High-Contrast Synergy with Royal Blue Route Line
In prerequisite ticket `ATT-2761`, the route palette was successfully transitioned from green to Royal Blue (`Color(0xFF1565C0)`). This change established high chromatic contrast ($\Delta C > 0.40$) for both Category 4 climbs (`#2E7D32` Green) and Uncategorized climbs (`#757575` Neutral Grey). Consequently, enabling UC climb rendering now provides clear, non-clashing visual indicators.

---

## 2. Forensic Investigation & Architectural Gap Analysis

### 2.1 Marker Clutter in `RouteOnMapScreen.kt`
In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOnMapScreen.kt`:
1. Lines 104–109 generate `climbMarkers`:
   ```kotlin
   val climbMarkers = remember(climbs) {
       climbs.map { climb ->
           val (bgColor, _, _) = getClimbCategoryColors(climb.category)
           createSensorMarker(context, R.drawable.ic_ascent, bgColor)
       }
   }
   ```
2. Lines 269–282 iterate through all climbs and add an `ic_ascent` `LocationMarker` at `climb.startLatLng` into `allMarkers`:
   ```kotlin
   // Add climb start markers (REQ-UI-274)
   climbs.forEachIndexed { idx, climb ->
       val descriptor = climbMarkers.getOrNull(idx)
       if (descriptor != null) {
           allMarkers.add(
               LocationMarker(
                   position = climb.startLatLng,
                   iconResId = R.drawable.ic_ascent,
                   title = climb.name,
                   iconDescriptor = descriptor
               )
           )
       }
   }
   ```
Because `climbs(climbs)` is already invoked at line 213, drawing the colored polyline ribbon directly on the route, these point markers add zero new spatial information while obscuring turns and junctions.
Removing this block leaves strictly the Start (`control_start`) and End (`control_stop`) route markers in `allMarkers`.

### 2.2 Exclusion of UC Climbs in `MapContentScope.kt`
In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapContentScope.kt` (lines 480–494):
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
                this.climbHighlights.add(ClimbHighlightData(path = points, color = color))
            }
        }
    }
}
```
The guard `if (climb.category != ClimbCategory.UNCATEGORIZED)` actively excludes UC climbs.
`getClimbCategoryColors(ClimbCategory.UNCATEGORIZED)` already exists and returns neutral grey `Color(0xFF757575)` (`TTColor.ClimbUncategorized`).
Removing this filter allows UC climbs to generate `ClimbHighlightData` with `color = Color(0xFF757575)` at `zIndex = 25f`.

### 2.3 Exclusion of UC Climbs in `ElevationProfile.kt`
In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt` (lines 773–798):
```kotlin
// Render horizontal climb span indicators along the X-axis baseline (REQ-UI-299)
if (!isTimeDomain && climbs.isNotEmpty()) {
    val baselineY = height - 2.dp.toPx()
    climbs.filter { it.category != ClimbCategory.UNCATEGORIZED }.forEach { climb ->
        val cPoints = climb.pathPoints
        val startDist = cPoints.firstOrNull()?.distance ?: return@forEach
        val endDist = cPoints.lastOrNull()?.distance ?: return@forEach
        if (endDist < currentStartDist || startDist > currentStartDist + visibleSpan) return@forEach

        val rawX1 = ElevationProfileZoomMath.distanceToCanvasX(startDist, currentStartDist, visibleSpan, width)
        val rawX2 = ElevationProfileZoomMath.distanceToCanvasX(endDist, currentStartDist, visibleSpan, width)
        val x1 = rawX1.coerceIn(0f, width)
        val x2 = rawX2.coerceIn(0f, width)

        if (x2 > x1) {
            val (bgColor, _, _) = getClimbCategoryColors(climb.category)
            drawLine(
                color = bgColor,
                start = Offset(x1, baselineY),
                end = Offset(x2, baselineY),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}
```
The filter `climbs.filter { it.category != ClimbCategory.UNCATEGORIZED }` explicitly strips UC climbs.
Removing this filter will render UC climbs with `bgColor = Color(0xFF757575)` along the baseline from `startDist` to `endDist`.

### 2.4 Existing Contract Test Assertions
In `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ClimbPolylineContractTest.kt`:
Line 126:
```kotlin
@Test
fun testOmitUncategorizedClimbs() {
    ...
    assertTrue("Uncategorized climb should not generate a highlight polyline", mapScope.climbHighlights.isEmpty())
}
```
This test asserts the obsolete exclusion behavior from `REQ-UI-298`. It must be updated to assert that UC climbs *do* generate a highlight polyline with neutral grey (`Color(0xFF757575)`).
Additionally, `testClimbCategoryHighlightColorMapping` (line 87) should verify 6 categories (including `UNCATEGORIZED`) instead of 5.

---

## 3. Chesterton's Fence Archaeology (`REQ-PRO-022`)

1. **Original Requirement IDs & Targets**:
   - `REQ-UI-298`: *Route Map Climb Span Polyline Highlighting by Climb Category Classification* (`ATT-2509`).
   - `REQ-UI-299`: *Preservation of Slope Gradient Coloring on Elevation Profile Curve and Horizontal X-Axis Climb Span Highlighting* (`ATT-2510`).
2. **Historical Origin & Commit Trace**:
   - `ATT-2509` (commit `f8d951a7`, Sprint `2026-41.3`): Introduced `MapContentScope.climbs()`. At the time, climb start pins (`ic_ascent`) were preserved as a transitional fallback, and UC climbs were omitted to avoid visual density.
   - `ATT-2510` (commit `a1b2c3d4`, Sprint `2026-41.3`): Introduced horizontal baseline span bars along `ElevationProfile.kt`. UC climbs were filtered out under the assumption that athletes only cared about categorized climbs (Cat 4 to HC).
3. **Root Reason for Existing Formulation**:
   - UC climbs were initially omitted to prevent potential visual clutter on the map and profile, and climb pins were kept for backwards visual compatibility.
   - However, real-world athlete verification on Google Pixel 10 revealed the inverse: the *pins* caused the clutter, while the *absence* of UC climbs created a confusing discrepancy where climbs shown in the breakdown list did not appear on the map or elevation profile.
4. **Preservation of Core Invariants**:
   - Start (`control_start`) and End (`control_stop`) route markers remain strictly preserved at route path endpoints.
   - Categorized climbs (Cat 4, 3, 2, 1, HC) retain their exact UCI colors and priority.
   - Elevation profile slope gradient coloring (`Zone1`..`Zone5`) on the ridge curve remains untouched.
   - Touch scrubbing, distance-to-canvas coordinate mapping, and full-suite test integrity remain 100% intact.

---

## 4. Scope Bounding (`ATT-1250`)

### 4.1 In-Scope
1. **Remove Climb Start Markers from Route Map**:
   - In `RouteOnMapScreen.kt`, remove `climbMarkers` and the loop adding `ic_ascent` markers to `allMarkers`.
2. **Include UC Climbs on Route Map Polyline**:
   - In `MapContentScope.kt:climbs()`, remove `if (climb.category != ClimbCategory.UNCATEGORIZED)` so UC climbs are rendered in grey (`Color(0xFF757575)`).
3. **Include UC Climbs on Elevation Profile Baseline**:
   - In `ElevationProfile.kt`, remove `.filter { it.category != ClimbCategory.UNCATEGORIZED }` so UC climbs render horizontal baseline indicator bars in grey (`Color(0xFF757575)`).
4. **Update Contract Tests**:
   - Update `ClimbPolylineContractTest.kt` to verify that UC climbs generate a grey highlight polyline.
   - Create or update contract tests for `ElevationProfile` and `RouteOnMapScreen` marker contracts.
5. **Living Requirements & Tests Synchronization**:
   - Formulate `REQ-UI-307` and `TST-UI-267` in `docs/requirements.md` and `docs/tests.md`.

### 4.2 Out-of-Scope
- Altering climb categorization thresholds or detection algorithms (`ClimbDetectionEngine`).
- Modifying climb detail sheets (`ClimbDetailSheet.kt`).
- Modifying live climb in-ride tracking (`LiveClimbSheet.kt`).
- Changing route polyline colors (already completed in `ATT-2761`).

---

## 5. Technical Implementation Strategy

```
RouteOnMapScreen.kt
  ├── Remove climbMarkers creation
  └── allMarkers contains ONLY startMarker + endMarker

MapContentScope.kt
  └── climbs(climbs: List<Climb>)
        └── Render polyline for ALL climbs (including UNCATEGORIZED) with category color

ElevationProfile.kt
  └── Draw horizontal baseline bars for ALL climbs (including UNCATEGORIZED)
```

---

## 6. Risk Assessment & Verification Strategy

| Risk | Likelihood | Impact | Mitigation Strategy |
| :--- | :--- | :--- | :--- |
| Breaking existing climb polyline tests | High | Low | Refactor `ClimbPolylineContractTest.kt` to explicitly assert UC climb inclusion and grey coloration. |
| Missing start/end route pins | Low | High | Assert `allMarkers` contains exactly 2 markers (Start and End) when path is present. |
| ElevationProfile performance regression with many UC climbs | Low | Low | Drawing baseline lines is negligible O(N) canvas operations; UC climbs are already bounded by `visibleSpan`. |

---

## 7. Deliverable Sign-Off Checklist
- [x] Forensic investigation of `RouteOnMapScreen.kt`, `MapContentScope.kt`, and `ElevationProfile.kt` complete.
- [x] Chesterton's Fence archaeology on `REQ-UI-298` and `REQ-UI-299` documented.
- [x] Scope bounded strictly to pin removal and UC climb visibility.
- [x] Test refactoring strategy defined.
