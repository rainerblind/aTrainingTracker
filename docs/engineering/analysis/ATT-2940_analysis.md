# Stage 1 Analysis: ATT-2940 - Increase selected route polyline thickness and remove dashed overlay at standard zoom levels

**Ticket**: [ATT-2940](https://atrainingtracker.atlassian.net/browse/ATT-2940)  
**Sub-task**: [ATT-3057](https://atrainingtracker.atlassian.net/browse/ATT-3057) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: None (Unassigned per Rule 19)  
**Active Sprint**: `2026-41.7`  
**Branch**: `feature/ATT-2940`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Statement & Motivation

During active route navigation and on-device testing with the Google Pixel 10 test device, the route line rendering was found to have two distinct route concepts that were conflated in the initial Sprint 2026-41.6 implementation:
1. **Active Routes (Visible on Map)**: Routes that the athlete has toggled on in the routes list or map layers to see nearby paths or candidate options.
2. **Selected Route (Actively Navigated Route)**: The single specific route the athlete has chosen to follow for turn-by-turn guidance and elevation navigation (`isActiveNavigation == true`).

During the Sprint 2026-41.6 Joint Review, the human user clarified the core requirement:
> *"Unfortunately, there was a misunderstanding. We have active routes and one selected route. The selected one is the one the user wants to follow. Only this one must be highlighted as requested by this ticket. The active routes should not have been touched."*

In the previous commit (`61218f1a`), `routeWidth` was increased from `10f` to `18f` and `pattern` was set to `null` across *all* routes. This inadvertently altered the appearance of all background/active routes on the map. The expected behavior is:
* Active/background routes must retain their original standard width (`10f`) and standard dashed pattern (`Dash(15f)`, `Gap(15f)`).
* Only the actively navigated route (the route the athlete is following) must be highlighted with a prominent ribbon width (`26f`) and solid polyline rendering (`pattern = null`), ensuring immediate glanceability at handlebar distance under direct sunlight.

---

## 2. Root Cause Analysis (Forensic Investigation)

In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapModels.kt`:
1. **Conflated Width Constants**:
   ```kotlin
   // In MapStyle & MapVisualization:
   val routeWidth: Float = 18f // Inadvertently increased from 10f in commit 61218f1a
   val routeActiveNavigationWidth: Float = 26f
   ```
   `routeWidth` governs all selected/active routes on the map (`isSelected == true && !isActiveNavigation`). Increasing it to `18f` made non-navigated routes excessively thick, competing with the navigated route.
2. **Indiscriminate Pattern Nullification**:
   ```kotlin
   // In MapRoute (lines 295-296):
   override val pattern: List<com.google.android.gms.maps.model.PatternItem>?
       get() = null
   ```
   Setting `pattern = null` unconditionally stripped the dashed pattern from *all* routes. Previously:
   ```kotlin
   override val pattern: List<PatternItem>?
       get() = if (isActiveNavigation) {
           listOf(Dash(ROUTE_ACTIVE_DASH_LENGTH), Gap(ROUTE_ACTIVE_GAP_LENGTH))
       } else {
           listOf(Dash(ROUTE_DASH_LENGTH), Gap(ROUTE_GAP_LENGTH))
       }
   ```
   The dashed overlay was originally problematic *only* on the actively navigated route, where it cluttered the thick route line and degraded glanceability. For secondary visible routes, the dashed pattern provides necessary visual distinction, preventing them from looking like the primary navigation course.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Revert `MapStyle.routeWidth` and `MapVisualization.ROUTE_WIDTH` from `18f` back to the original baseline `10f`.
  * Maintain `MapStyle.routeActiveNavigationWidth` and `MapVisualization.ROUTE_ACTIVE_NAVIGATION_WIDTH` at `26f`.
  * Update `MapRoute.pattern`:
    * Return `null` when `isActiveNavigation == true` (clean solid line for the route being followed).
    * Return `listOf(Dash(ROUTE_DASH_LENGTH), Gap(ROUTE_GAP_LENGTH))` when `isActiveNavigation == false` (restoring original active route behavior).
  * Update `MapRouteActiveNavigationTest.kt` to assert:
    * Navigated route: `width == 26f`, `pattern == null`.
    * Active/background route: `width == 10f`, `pattern != null` with `Dash(15f)` and `Gap(15f)`.
    * Unselected route: `width == 6f`.
  * Update `docs/requirements.md` (`REQ-MAP-040`) and `docs/tests.md` (`TST-MAP-042`).

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying Strava segment widths (`10f`) or patterns (`Dash(20f)`, `Gap(15f)`).
  * Modifying Climb Category widths (`10f`) or colors.
  * Modifying route database models or route selection logic in `RouteSelectorViewModel`.
  * Changing unselected route width (`6f`) or Z-index stacking order.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: Amends `REQ-MAP-040` (*High-Glanceability Route Polyline Scaling and Solid Line Rendering*, sprint 2026-41.6).
* **Historical Origin & Commit Trace**: Commit `61218f1a` (ATT-2940) over-generalized the scope by scaling all active route widths and nullifying patterns globally.
* **Root Reason for Existing Formulation**: The user requirement specifically asked to highlight the route the user wants to follow ("the selected one"). Over-applying the modification to all active routes degraded map clarity when multiple routes were displayed.
* **Preservation of Core Invariants**:
  * Z-index stacking order (`ROUTE_BASE_Z_INDEX (20f) < ROUTE_ACTIVE_BASE_Z_INDEX (24f) < CLIMB_Z_INDEX (28f) < SEGMENT_Z_INDEX (30f) < USER_LOCATION_Z_INDEX (100f)`) is strictly preserved.
  * Directional travel chevrons (`ActiveRouteDecorations`) remain rendered along the navigated route.
  * 100% test pass rate across unit tests must be maintained.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Constant Corrections in `MapModels.kt`
* In `MapStyle`:
  * `routeWidth: Float = 10f` (reverted from `18f`).
  * `routeActiveNavigationWidth: Float = 26f` (retained).
* In `MapVisualization`:
  * `ROUTE_WIDTH = 10f` (reverted from `18f`).
  * `ROUTE_ACTIVE_NAVIGATION_WIDTH = 26f` (retained).

### 5.2 Contextual Pattern Property in `MapRoute`
```kotlin
override val pattern: List<com.google.android.gms.maps.model.PatternItem>?
    get() = if (isActiveNavigation) {
        null
    } else {
        listOf(
            com.google.android.gms.maps.model.Dash(MapVisualization.ROUTE_DASH_LENGTH),
            com.google.android.gms.maps.model.Gap(MapVisualization.ROUTE_GAP_LENGTH)
        )
    }
```
* When `isActiveNavigation == true`: renders exclusively as a solid 26f ribbon without dashed overlay.
* When `isActiveNavigation == false`: restores the standard dashed pattern over the 10f base line, leaving active routes untouched as requested by the user.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing map features and unit tests.
  2. Z-index hierarchy and Strava segment / Live Climb overlay prominence preserved.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**
  * Justification: Minimal, surgical property adjustments directly addressing explicit user feedback, with full test coverage in JVM unit tests.
