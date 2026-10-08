# Stage 2: Requirement & Test Specification - ATT-2761: Transition Route Color Palette from Green to Royal Blue for Climb Contrast and Terrain Glanceability

**Ticket**: [ATT-2761](https://atrainingtracker.atlassian.net/browse/ATT-2761)  
**Sub-task**: [ATT-2823](https://atrainingtracker.atlassian.net/browse/ATT-2823) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.4`)  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-UI-306` (*Royal Blue Route Palette Transition, High-Contrast Climb Category Differentiation, and Topographic Glanceability*)  
**Test Spec ID**: `TST-UI-266` (*Royal Blue Route Color Tokens, Multi-Tier Contrast Hierarchy, and Living Style Guide Synchronization Verification*)  
**Branch**: `improvement/ATT-2761`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Requirement Specification (`REQ-UI-306`)

### 1.1 Problem Statement & Rationale
Routes in `aTrainingTracker` have historically used green color tokens:
- `TTColor.RouteSelected = Color(0xFF228B22)` (ForestGreen)
- `TTColor.RouteUnselected = Color(0xFF90EE90)` (LightGreen)
- `TTColor.RouteActiveNavigation = Color(0xFF00E676)` (Electric Emerald)
- `TTColor.RouteActiveNavigationOverlay = Color(0xFF004D20)` (Deep Emerald)

This green palette introduces two severe visual defects:
1. **Climb Category Collision**: Cycling climbs along routes are color-coded by UCI category (`REQ-UI-274`, `REQ-UI-298`). Category 4 climbs (`#2E7D32` Green) overlay directly on top of the green route polyline with near-zero chromatic contrast ("green on green"), rendering Cat 4 climbs indistinguishable. Uncategorized (UC) grey climbs (`#757575`) also suffer poor separation.
2. **Topographic Blending**: Map vector tiles (Google Maps and OSM) render natural terrain (parks, forests, meadows, reserves) in light and medium green. Green routes blend into the background, degrading glanceability under outdoor sunlight glare and bicycle handlebar vibration.

Transitioning route visualization to **Royal Blue** (`Color(0xFF1565C0)` / Material Blue 800) eliminates the climb contrast collision at the root—because neither climb categories nor physiological exertion zones use blue—while dramatically improving glanceability against green topographic map tiles and aligning with navigation mental models.

### 1.2 Functional & Architectural Requirements
The system SHALL transition all route visualization color tokens in `TTColor` from green to curated Royal Blue tokens, establish sharp visual contrast with all UCI climb category overlays and terrain backgrounds, harmonize route-related UI components, and update the living design guidelines (ATT-2761):

1. *Core Route Visualization Tokens (`Color.kt`)*:
   - `TTColor.RouteSelected` SHALL be redefined from `Color(0xFF228B22)` (ForestGreen) to `Color(0xFF1565C0)` (Material Blue 800 / Royal Blue).
   - `TTColor.RouteUnselected` SHALL be redefined from `Color(0xFF90EE90)` (LightGreen) to `Color(0xFF90CAF9)` (Material Blue 200 / Soft Steel Blue).
   - `TTColor.RouteActiveNavigation` SHALL be redefined from `Color(0xFF00E676)` (Electric Emerald) to `Color(0xFF1E88E5)` (Material Blue 600 / Vibrant Sapphire Blue).
   - `TTColor.RouteActiveNavigationOverlay` SHALL be redefined from `Color(0xFF004D20)` (Deep Emerald) to `Color(0xFF0D47A1)` (Material Blue 900 / Deep Midnight Navy).

2. *Map Route Hierarchy & Overlay Invariants (`MapModels.kt`)*:
   - Active route base polyline (`isActiveNavigation == true`, `width = 16f`, `zIndex = 25f`) SHALL render with `TTColor.RouteActiveNavigation` (`Color(0xFF1E88E5)`).
   - Active route dashed overlay (`width = 8f`, `zIndex = 45f`) SHALL render with `TTColor.RouteActiveNavigationOverlay` (`Color(0xFF0D47A1)`), producing a sharp dark navy dashed centerline within a vibrant sapphire blue ribbon.
   - Passive selected routes (`isSelected == true`, `width = 10f`, `zIndex = 20f`) SHALL render with `TTColor.RouteSelected` (`Color(0xFF1565C0)`).
   - Unselected background routes (`width = 6f`, `zIndex = 5f`) SHALL render with `TTColor.RouteUnselected` (`Color(0xFF90CAF9)`).

3. *Climb Category & Topographic Contrast*:
   - Every UCI climb category (`HC` `#880E4F`, `CAT_1` `#C62828`, `CAT_2` `#EF6C00`, `CAT_3` `#F9A825`, `CAT_4` `#2E7D32`, `UNCATEGORIZED` `#757575`) SHALL exhibit high contrast against the underlying `TTColor.RouteSelected` (`Color(0xFF1565C0)`) route ribbon.
   - Category 4 (`#2E7D32` Green) SHALL stand out with complementary chromatic contrast against Royal Blue.

4. *Route UI Component Harmonization*:
   - All interactive route components referencing `TTColor.RouteSelected` or `TTColor.RouteActiveNavigation`—including `RouteSelectionButton.kt`, `RouteSelectorSheet.kt`, `ReturnNavigationHud.kt`, `RouteSummaryHeader.kt`, `SensorGridScreen.kt` navigation hint toggle, `TurnPromptBanner.kt`, and `WorkoutClusterComponents.kt`—SHALL inherit Royal Blue accents without visual degradation across Light, Dark, and AMOLED themes.

5. *Living Style Guide Synchronization (`docs/design_guidelines.md`)*:
   - Section 5.4 (*Color*) SHALL be updated to codify Royal Blue as the authoritative domain semantic color for routes and navigation (`TTColor.RouteSelected`, `TTColor.RouteActiveNavigation`), replacing obsolete references to green route tones.
   - Section 5.7 (*In-Ride Navigation Cues & HUD Overlays*) SHALL be updated to codify the Royal Blue accent border for in-ride navigation cue banners.

6. *Preservation of System Invariants*:
   - UCI climb category colors (`getClimbCategoryColors`) MUST NOT be altered.
   - Strava Orange (`TTColor.StravaOrange = Color(0xFFFC4C02)`) and Strava Live Segments X-Ray layering (`SEGMENT_Z_INDEX = 30f`) MUST NOT be altered.
   - Live session recorded breadcrumbs (`LiveTrackLayer`, `resolveLiveTrackColor`) MUST remain distinguishable from the solid planned route via its dotted pattern (`listOf(Dot(), Gap(15f))`).
   - Clean-room test suite pass rate MUST remain 100%.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Refines and amends `REQ-MAP-023` (*Prominent High-Contrast Rendering for Actively Navigated Routes*), `REQ-UI-279` (*Branded Route Selection Entry Point*), and `REQ-UI-282` (*Take Me Home UI Styling*).
2. *Historical Origin & Commit Trace*:
   - Ticket `ATT-1841`, sprint `2026-40.14` (commit `f8194a2b`): established `RouteActiveNavigation` as `#00E676` Electric Emerald and `RouteSelected` as `#228B22` ForestGreen.
   - Ticket `ATT-2458`, sprint `2026-41.1`: codified green domain accents in `RouteSelectionButton.kt`.
   - Ticket `ATT-2462`, sprint `2026-41.1`: codified green domain accents in `ReturnNavigationHud.kt`.
   - Ticket `ATT-2509`, sprint `2026-41.3`: introduced climb polyline overlays (`MapContentScope.climbs()`), creating the unaddressed "green on green" conflict with Cat 4 climbs.
3. *Root Reason for Existing Formulation*: Green was originally selected for routes because it historically evoked natural outdoor trails and separated cleanly from Strava's orange segment polylines. However, that choice predated the introduction of climb span overlays (`ATT-2509`), where Cat 4 climbs use UCI standard green (`#2E7D32`), and ignored green map terrain tiles in rural/forested regions.
4. *Preservation of Core Invariants*:
   - The multi-layer X-Ray polyline sandwich (`baseZIndex 25f < SEGMENT_Z_INDEX 30f < overlayZIndex 45f`) established in `REQ-MAP-023` remains mathematically preserved.
   - Strava Orange (`#FC4C02`) retains prominent contrast against Royal Blue.
   - Directional chevrons at `zoom > 13f` remain functional.
   - 100% full-suite unit test pass rate is strictly maintained.

---

## 2. Test Specification (`TST-UI-266`)

### 2.1 Scope & Verification Methods
| Test Spec ID | Test Scope | Verification Method | Target Status |
| :--- | :--- | :--- | :--- |
| `TST-UI-266.1` | Route Color Tokens Audit (`Color.kt`) | Unit Test: Assert exact ARGB hex values for `RouteSelected`, `RouteUnselected`, `RouteActiveNavigation`, `RouteActiveNavigationOverlay` | Specified |
| `TST-UI-266.2` | Multi-Tier Contrast & Climb Category Differentiation | Contract Test: Verify RGB Euclidean / WCAG contrast between `RouteSelected` and all 6 climb categories (`HC`, `CAT_1`..`CAT_4`, `UC`), asserting Cat 4 green $\Delta C > 0.35$ | Specified |
| `TST-UI-266.3` | Active Navigation Polyline Hierarchy (`MapRouteActiveNavigationTest.kt`) | Unit Test: Verify `MapRoute` color resolution, width, and z-index hierarchy with new Royal Blue tokens | Specified |
| `TST-UI-266.4` | Living Style Guide Synchronization | Doc Audit: Verify `docs/design_guidelines.md` Sections 5.4 and 5.7 codify Royal Blue with zero stale green route references | Specified |
| `TST-UI-266.5` | Full Clean-Room Regression Suite | Clean-room execution: `./gradlew clean testDebugUnitTest` asserting 100% pass rate across all 438 test classes | Specified |

### 2.2 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Core Token Values)**:
  * *Given* `com.atrainingtracker.trainingtracker.ui.theme.TTColor`,
  * *When* inspecting route color tokens,
  * *Then* `RouteSelected` equals `Color(0xFF1565C0)`, `RouteUnselected` equals `Color(0xFF90CAF9)`, `RouteActiveNavigation` equals `Color(0xFF1E88E5)`, and `RouteActiveNavigationOverlay` equals `Color(0xFF0D47A1)`.

* **Criterion 2 (Cat 4 & UC Climb Contrast)**:
  * *Given* a route with recognized climbs on `RouteOnMapScreen`,
  * *When* Category 4 (`#2E7D32` Green) and UC (`#757575` Grey) climb spans render over the route polyline,
  * *Then* both climb categories stand out with sharp chromatic and luminance contrast against the Royal Blue base ribbon.

* **Criterion 3 (Active Route Visual Hierarchy)**:
  * *Given* an actively navigated route (`isActiveNavigation == true`),
  * *When* evaluated via `MapRoute`,
  * *Then* `color` resolves to `TTColor.RouteActiveNavigation` (`Color(0xFF1E88E5)`), `overlayColor` resolves to `TTColor.RouteActiveNavigationOverlay` (`Color(0xFF0D47A1)`), and the active dashed overlay renders at `zIndex = 45f` over the solid base at `zIndex = 25f`.

* **Criterion 4 (Passive & Unselected Route Distinction)**:
  * *Given* passive selected and unselected routes,
  * *When* evaluated via `MapRoute`,
  * *Then* passive selected route resolves to `TTColor.RouteSelected` (`Color(0xFF1565C0)`), and unselected route resolves to `TTColor.RouteUnselected` (`Color(0xFF90CAF9)`).

* **Criterion 5 (Living Documentation Synchronization)**:
  * *Given* `docs/design_guidelines.md`,
  * *When* inspecting Sections 5.4 and 5.7,
  * *Then* the guidelines explicitly reference Royal Blue for route geometry, selection accents, and navigation HUD borders, with zero references to green route tones.

---

## 3. Bidirectional Traceability Matrix

| Requirement ID | Test Spec ID | Production Component | Test Class / Verification | Living Docs Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-306.1` | `TST-UI-266.1` | `Color.kt` | `RouteColorPaletteContractTest.kt` | Specified |
| `REQ-UI-306.2` | `TST-UI-266.3` | `MapModels.kt` | `MapRouteActiveNavigationTest.kt` | Specified |
| `REQ-UI-306.3` | `TST-UI-266.2` | `Color.kt`, `LiveClimbSheet.kt` | `RouteColorPaletteContractTest.kt` | Specified |
| `REQ-UI-306.4` | `TST-UI-266.1` | `RouteSelectionButton.kt`, `RouteSelectorSheet.kt`, `ReturnNavigationHud.kt` | `ControlTrackingRouteSelectionContractTest.kt`, `RouteSelectorMidRideContractTest.kt`, `ReturnNavigationHudContractTest.kt` | Specified |
| `REQ-UI-306.5` | `TST-UI-266.4` | `docs/design_guidelines.md` | Doc Audit / Contract Test | Specified |
| `REQ-UI-306.6` | `TST-UI-266.5` | Entire Codebase | Full clean-room test suite (`testDebugUnitTest`) | Specified |
