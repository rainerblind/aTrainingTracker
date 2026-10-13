# Stage 1 Analysis: ATT-2864 - Render climb overlay as solid polyline above route line and segment overlay as dashed above climbs

**Ticket**: [ATT-2864](https://atrainingtracker.atlassian.net/browse/ATT-2864)  
**Sub-task**: [ATT-2954](https://atrainingtracker.atlassian.net/browse/ATT-2954) (`[Analysis]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2864`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Statement & Motivation

During on-device testing of `ATT-2763` on Google Pixel 10 (Sprint 2026-41.4 & 2026-41.5 Reviews), visual occlusion occurred when inspecting routes with both climbs and segments:
1. Climbs must be rendered as a continuous line (solid polyline, not dashed) positioned directly above the route line.
2. When a Strava segment coincides with a climb along the route, the segment polyline must be rendered as a **dashed polyline** positioned above the climb polyline.

### Sprint 2026-41.5 User Feedback
> *"Revision needed: Human user during Sprint Review: Unfortunately, this does not work. Moved back to Zu erledigen. Note that the root cause might be that we draw the routes in two layers: within one layer it is solid, in the other it is dashed. Please check in more detail."*

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic inspection of [MapContentScope.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapContentScope.kt), [MapModels.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapModels.kt), and [MapLayers.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapLayers.kt) confirms the human user's exact observation:

### Root Cause 1: Route Dual-Layer Polyline Z-Index Inversion
In `MapModels.kt` and `MapVisualization`:
```kotlin
const val ROUTE_BASE_Z_INDEX = 20.0f
const val ROUTE_OVERLAY_Z_INDEX = 40.0f
const val ROUTE_ACTIVE_BASE_Z_INDEX = 25.0f
const val ROUTE_ACTIVE_OVERLAY_Z_INDEX = 45.0f

const val CLIMB_Z_INDEX = 28.0f
const val SEGMENT_Z_INDEX = 30.0f
```
`MapRoute` is rendered using `XRayPolyline` in two layers:
1. Solid base polyline at `baseZIndex` (`ROUTE_BASE_Z_INDEX = 20.0f`).
2. Patterned overlay polyline at `overlayZIndex` (`ROUTE_OVERLAY_Z_INDEX = 40.0f`).

Because `ROUTE_OVERLAY_Z_INDEX` (40.0f) and `ROUTE_ACTIVE_OVERLAY_Z_INDEX` (45.0f) were greater than `CLIMB_Z_INDEX` (28.0f) and `SEGMENT_Z_INDEX` (30.0f):
* The route's dashed upper layer was drawn **on top of** the climb (28.0f) and **on top of** the segment (30.0f).
* This inverted the desired visual hierarchy, making the route appear above the climb and segment.

### Root Cause 2: Missing Z-Index Constraints in Previous Contract Tests
In `RouteClimbSegmentLayeringContractTest.kt`, assertions only verified `CLIMB_Z_INDEX > ROUTE_BASE_Z_INDEX` (28f > 20f). The contract test failed to assert that `CLIMB_Z_INDEX` must also be strictly greater than `ROUTE_OVERLAY_Z_INDEX` and `ROUTE_ACTIVE_OVERLAY_Z_INDEX`.

---

## 3. Targeted Solution Architecture

To establish the strict three-tier layering:
1. **Lower Route Overlay Z-Indices**:
   - `ROUTE_BASE_Z_INDEX = 20.0f`
   - `ROUTE_OVERLAY_Z_INDEX = 22.0f` (strictly < 28.0f)
   - `ROUTE_ACTIVE_BASE_Z_INDEX = 24.0f`
   - `ROUTE_ACTIVE_OVERLAY_Z_INDEX = 26.0f` (strictly < 28.0f)
2. **Middle Tier (Climbs)**:
   - `CLIMB_Z_INDEX = 28.0f`
   - Rendered as a solid, continuous polyline in UCI category colors without dash patterns.
3. **Top Tier (Segments)**:
   - `SEGMENT_Z_INDEX = 30.0f`
   - Rendered as a dashed polyline (`isDashed = true`, `hasSolidBase = false`) in Strava Orange (`#FC5200`).
   - Transparent gaps between dashes allow the underlying climb category color to show through clearly.
4. **Architectural Contract Hardening**:
   - Update `RouteClimbSegmentLayeringContractTest.kt` to explicitly assert that `CLIMB_Z_INDEX` exceeds both `ROUTE_OVERLAY_Z_INDEX` and `ROUTE_ACTIVE_OVERLAY_Z_INDEX`.
   - Update `MapRouteActiveNavigationTest.kt` to reflect updated overlay z-index values.

---

## 4. User Scope Grounding (ATT-1250)

* **In-Scope**:
  1. Update `ROUTE_OVERLAY_Z_INDEX` (22f) and `ROUTE_ACTIVE_OVERLAY_Z_INDEX` (26f) in `MapModels.kt` (`MapStyle` and `MapVisualization`).
  2. Maintain `CLIMB_Z_INDEX = 28f` and `SEGMENT_Z_INDEX = 30f`.
  3. Validate `RouteOnMapScreen` dashed segment rendering with transparent gaps above climbs.
  4. Update contract tests `RouteClimbSegmentLayeringContractTest.kt` and `MapRouteActiveNavigationTest.kt`.
  5. Synchronize living docs (`docs/requirements.md` / `docs/tests.md`).
* **Out-of-Scope**:
  1. Modifying climb detection or segment matching algorithms.
  2. Altering elevation profile rendering or GPS tracking pipelines.

---

## 5. Chesterton's Fence Audit

* **Why were route overlay z-indices set to 40f/45f initially?**
  Originally, `ROUTE_OVERLAY_Z_INDEX` was placed at 40f to ensure the route pattern was visible above legacy markers and map backgrounds before climbs and segments were added as map overlays. Now that climbs (28f) and segments (30f) are established first-class overlays on routes, the route's upper layer must be grouped with the route base (20f–26f) below climbs.
* **Are other layers impacted?**
  No. Live track overlay (50f), user location (100f), and waypoint markers (>= 50f) remain in higher tiers above segments and climbs.

---

## 6. Acceptance Criteria

1. Route polylines (both base and overlay) have z-index < 28.0f.
2. Climbs are rendered as continuous solid lines at z-index 28.0f above the route.
3. Segments are rendered as dashed lines at z-index 30.0f above climbs, with climb color visible through gaps.
4. Full clean-room test suite passes with 100% success rate.
