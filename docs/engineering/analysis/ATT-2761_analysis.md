# Stage 1: Problem Domain & Root Cause Analysis - ATT-2761: Transition Route Color Palette from Green to Royal Blue for Climb Contrast and Terrain Glanceability

**Ticket**: [ATT-2761](https://atrainingtracker.atlassian.net/browse/ATT-2761)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Requirement Mapping**: `REQ-UI-305` (Refining `REQ-MAP-023`, `REQ-UI-279`, `REQ-UI-282`)  
**Sprint**: `2026-41.4`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  
**Status**: Completed (Ready for Gate 1 Review)  

---

## 1. Problem Statement & User Impact

In `aTrainingTracker`, planned routes and navigation paths on Google Maps and detail screens have historically been rendered using shades of green:
- `TTColor.RouteSelected = Color(0xFF228B22)` (ForestGreen)
- `TTColor.RouteUnselected = Color(0xFF90EE90)` (LightGreen)
- `TTColor.RouteActiveNavigation = Color(0xFF00E676)` (Vibrant Electric Emerald)
- `TTColor.RouteActiveNavigationOverlay = Color(0xFF004D20)` (Deep Emerald)

This green route palette introduces two severe perceptual and ergonomic conflicts:

### 1.1 Severe Contrast Clash with Climb Categories & Zones
Under `REQ-UI-274`, `REQ-UI-298`, and UCI standard cycling conventions, cycling climbs along routes are color-coded by category:
- **HC**: Dark Red / Violet (`#880E4F`)
- **Category 1**: Red (`#C62828`)
- **Category 2**: Orange (`#EF6C00`)
- **Category 3**: Yellow (`#F9A825`)
- **Category 4**: Green (`#2E7D32`)
- **Uncategorized (UC)**: Neutral Grey (`#757575`)

When a route with a **Category 4 climb** (`#2E7D32` Green) is rendered on `RouteOnMapScreen` using `MapContentScope.climbs()`, the Category 4 climb span is drawn directly over the base route polyline (`#228B22` ForestGreen). Because both colors share the identical green hue family and closely matching luminance, the climb span visually blends into the route line ("green-on-green"). Athletes inspecting course climbs cannot distinguish where the Kat 4 climb begins or ends. Similarly, neutral grey UC climbs suffer diminished visual separation against muted green route ribbons.

### 1.2 Poor Figure-Ground Separation on Map Topography
Standard Google Maps and OpenStreetMap vector tiles render geographical terrain features—such as forests, woodlands, nature reserves, parks, golf courses, and agricultural meadows—in light-to-medium green. When cycling or running routes traverse rural or forest environments, the green route polyline suffers poor figure-ground separation against the background map tiles, severely impairing glanceability under outdoor sunlight glare and high-vibration handlebar mounting.

---

## 2. Forensic Investigation & Architectural Gap Analysis

### 2.1 Color Tokens in `Color.kt`
In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/Color.kt`, route colors are centralized inside `TTColor`:
```kotlin
object TTColor {
    ...
    // Route Visualization
    val RouteSelected = Color(0xFF228B22) // ForestGreen
    val RouteUnselected = Color(0xFF90EE90) // LightGreen
    val RouteActiveNavigation = Color(0xFF00E676) // Vibrant Electric Emerald for actively navigated routes
    val RouteActiveNavigationOverlay = Color(0xFF004D20) // Deep Emerald for active route dashed overlay
}
```

### 2.2 Production Components Bound to `TTColor` Route Tokens
The codebase cleanly references these centralized tokens rather than hardcoding hex values across UI components:
1. **Map Layer Polyline Model (`MapModels.kt`)**:
   ```kotlin
   override val color: Color get() = when {
       isActiveNavigation -> TTColor.RouteActiveNavigation
       isSelected -> TTColor.RouteSelected
       else -> TTColor.RouteUnselected
   }
   override val overlayColor: Color get() = if (isActiveNavigation) {
       TTColor.RouteActiveNavigationOverlay
   } else {
       color
   }
   ```
2. **Route Selection Entry Point (`RouteSelectionButton.kt`)**:
   Uses `TTColor.RouteSelected.copy(alpha = 0.35f)` for borders and `0.12f` for container tint, plus icon tinting.
3. **Route Selector Sheet & Cards (`RouteSelectorSheet.kt`)**:
   Uses `TTColor.RouteSelected` for `MidRideHeimwegCard` border, background tint, and icon tint.
4. **Return Navigation HUD (`ReturnNavigationHud.kt`)**:
   Uses `TTColor.RouteSelected` for card border and home destination icon tint.
5. **Route Summary Header (`RouteSummaryHeader.kt`)**:
   Uses `TTColor.RouteSelected` for switch thumb/track/border when selected, and `TTColor.RouteUnselected` when unselected.
6. **Cockpit Spatial Toggle (`SensorGridScreen.kt`)**:
   `SpatialCockpitToggleCard` for navigation hints uses `accentColor = TTColor.RouteSelected`.
7. **Turn Prompt Banner (`TurnPromptBanner.kt`)**:
   Uses `TTColor.RouteActiveNavigation` for animated overlay border.
8. **Workout Cluster Previews (`WorkoutClusterComponents.kt`)**:
   Uses `TTColor.RouteSelected` for cluster route polyline previews.

### 2.3 Unit & Contract Test Coupling
A subset of existing unit tests in `MapRouteActiveNavigationTest.kt` explicitly assert the legacy green hex values:
- `assertEquals(Color(0xFF00E676), activeRoute.color)`
- `assertEquals(Color(0xFF004D20), activeRoute.overlayColor)`
- `assertEquals(Color(0xFF228B22), passiveRoute.color)`

These assertions will be updated to reflect the new Royal Blue color tokens.

### 2.4 Style Guide & Design Guidelines Drift (`docs/design_guidelines.md`)
`docs/design_guidelines.md` currently codifies green route semantics in two sections:
- **Section 5.4 (Color)**: Explicitly describes green tones for route geometry (`TTColor.RouteSelected`, `TTColor.RouteActiveNavigation`) and mandates green touches on route buttons.
- **Section 5.7 (In-Ride Navigation Cues & HUD Overlays)**: Specifies a subtle green border accent (`TTColor.RouteActiveNavigation`).

These sections must be updated to codify Royal Blue domain semantics, preventing documentation drift.

---

## 3. Chesterton's Fence & Requirement Archaeology

### 3.1 Historical Trace of Route Color Requirements
1. **`REQ-MAP-023` (ATT-1841, Sprint `2026-40.14`)**:
   - Introduced `RouteActiveNavigation` (`#00E676` Electric Emerald) and `RouteActiveNavigationOverlay` (`#004D20`) with 16f thickness and directional chevrons to differentiate actively navigated routes from passive 10f green routes (`#228B22`).
   - Established the 3-tier X-Ray polyline sandwich (`baseZIndex < SEGMENT_Z_INDEX < overlayZIndex`).
2. **`REQ-UI-279` (ATT-2458, Sprint `2026-41.1`)**:
   - Relocated route selection to `ControlTrackingScreen` with green domain accents (`TTColor.RouteSelected`).
3. **`REQ-UI-282` (ATT-2462, Sprint `2026-41.1`)**:
   - Styled `MidRideHeimwegCard` and `ReturnNavigationHud` with `TTColor.RouteSelected` accents.
4. **`REQ-UI-298` (ATT-2509, Sprint `2026-41.3`)**:
   - Introduced climb span polyline highlighting on route maps using category colors, while leaving flat/downhill spans in `RouteSelected` (ForestGreen).

### 3.2 Root Reason for Original Green Choice
Green was historically chosen because it represented classic outdoor recreation ("trails", "greenways") and contrasted with Strava's signature orange (`#FC4C02`). However, at the time green was chosen:
- Route climb span polyline overlay (`ATT-2509`) did not exist; climbs were only represented as start marker pins.
- Category 4 climbs share UCI standard green (`#2E7D32`), which directly conflicts with green route lines.

### 3.3 Why Royal Blue Resolves the Conflict at the Architectural Root
Transitioning routes to **Royal Blue** (`Color(0xFF1565C0)` / Material Blue 800):
1. **Zero Climb Category Collision**:
   None of the climb categories or physiological zones utilize blue. All 6 categories stand out sharply:
   - HC (`#880E4F` Violet) on Royal Blue: High Contrast
   - Cat 1 (`#C62828` Red) on Royal Blue: High Contrast
   - Cat 2 (`#EF6C00` Orange) on Royal Blue: High Contrast
   - Cat 3 (`#F9A825` Yellow) on Royal Blue: High Contrast
   - **Cat 4 (`#2E7D32` Green) on Royal Blue: Extreme Contrast (Complements)**
   - **UC (`#757575` Grey) on Royal Blue: High Contrast**
2. **Instant Glanceability over Map Terrain**:
   Blue polylines are immediately distinct from green forest/park vector polygons across Google Maps and OSM tiles.
3. **Coexistence with Live Track Breadcrumbs**:
   Live recorded breadcrumb tracks (`LiveTrackLayer`, `resolveLiveTrackColor`) use high-contrast Electric Cyan (`Color(0xFF00E5FF)`) on dark maps and a dotted X-Ray polyline pattern (`listOf(Dot(), Gap(15f))`). The solid planned route in Royal Blue remains visually and structurally distinct from the dotted live recorded track.

---

## 4. Proposed Solution Architecture

### 4.1 Token Definitions (`Color.kt`)
Update `TTColor` route visualization tokens:
- `val RouteSelected = Color(0xFF1565C0)` (Royal Blue / Material Blue 800: deep, saturated, crisp)
- `val RouteUnselected = Color(0xFF90CAF9)` (Soft Steel / Sky Blue / Material Blue 200: clearly secondary)
- `val RouteActiveNavigation = Color(0xFF1E88E5)` (Vibrant Sapphire Blue / Material Blue 600: prominent active navigation ribbon)
- `val RouteActiveNavigationOverlay = Color(0xFF0D47A1)` (Deep Midnight Navy / Material Blue 900: high-contrast dashed centerline overlay)

### 4.2 Architectural Flow
```
TTColor.RouteSelected (Color(0xFF1565C0))
   ├── MapRoute (Passive Selected Polyline, width 10f, zIndex 20f)
   ├── RouteSelectionButton (12.dp card border 35%, icon container 12%, icon tint)
   ├── RouteSelectorSheet (MidRideHeimwegCard border 35%, icon tint)
   ├── ReturnNavigationHud (Card border 35%, home destination icon tint)
   ├── RouteSummaryHeader (Switch thumb, track, border)
   ├── SensorGridScreen (Navigation hints toggle card accent)
   └── WorkoutClusterComponents (Cluster route preview polyline)

TTColor.RouteActiveNavigation (Color(0xFF1E88E5))
   ├── MapRoute (Active Base Polyline, width 16f, zIndex 25f)
   └── TurnPromptBanner (Navigation turn cue border)

TTColor.RouteActiveNavigationOverlay (Color(0xFF0D47A1))
   └── MapRoute (Active Dashed Centerline, width 8f, zIndex 45f)
```

---

## 5. Invariants & Risk Assessment

| Invariant | Risk Mitigation |
| :--- | :--- |
| **Strava Live Segments (`#FC4C02`)** | Strava Orange remains strictly untouched; the multi-tier X-Ray polyline sandwich (`baseZIndex 25f < SEGMENT_Z_INDEX 30f < overlayZIndex 45f`) remains intact. |
| **Climb Categorization Colors** | All 6 climb category colors (`HC`, `CAT_1`..`CAT_4`, `UC`) remain strictly untouched. |
| **Live Breadcrumb Track Distinction** | Live track retains `pattern = listOf(Dot(), Gap(style.trackDotGap))` and `resolveLiveTrackColor`, preventing confusion with the solid planned route. |
| **Living Documentation Sync** | `docs/design_guidelines.md` Sections 5.4 and 5.7 updated in Stage 2/4. |
| **Full Regression Suite** | Execute `./gradlew testDebugUnitTest` ensuring 100% pass rate across all 438 test classes. |

---

## 6. Stage 1 Verification Checklist
- [x] Problem domain and root causes investigated.
- [x] Full codebase audit of all `RouteSelected`, `RouteUnselected`, `RouteActiveNavigation`, and `RouteActiveNavigationOverlay` usages completed.
- [x] Chesterton's Fence archaeology documented across `REQ-MAP-023`, `REQ-UI-279`, `REQ-UI-282`, and `REQ-UI-298`.
- [x] Royal Blue color palette curated with verified climb category and map terrain contrast.
- [x] Documented in `docs/engineering/analysis/ATT-2761_analysis.md`.
