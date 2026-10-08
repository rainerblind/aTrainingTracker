# Stage 3: Implementation Plan - ATT-2761: Transition Route Color Palette from Green to Royal Blue for Climb Contrast and Terrain Glanceability

**Ticket**: [ATT-2761](https://atrainingtracker.atlassian.net/browse/ATT-2761)  
**Sub-task**: [ATT-2824](https://atrainingtracker.atlassian.net/browse/ATT-2824) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.4`)  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-UI-306` (*Royal Blue Route Palette Transition, High-Contrast Climb Category Differentiation, and Topographic Glanceability*)  
**Test Mapping**: `TST-UI-266` (*Royal Blue Route Color Tokens, Multi-Tier Contrast Hierarchy, and Living Style Guide Synchronization Verification*)  
**Branch**: `improvement/ATT-2761`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  
**Status**: Ready for Gate 3 Review  

---

## 1. Problem Description & Background

In `aTrainingTracker`, route polylines and navigation accents have historically been rendered using green color tones:
- `TTColor.RouteSelected = Color(0xFF228B22)` (ForestGreen)
- `TTColor.RouteUnselected = Color(0xFF90EE90)` (LightGreen)
- `TTColor.RouteActiveNavigation = Color(0xFF00E676)` (Electric Emerald)
- `TTColor.RouteActiveNavigationOverlay = Color(0xFF004D20)` (Deep Emerald)

This green palette introduces two severe visual defects:
1. **Severe Climb Category Collision**: Cycling climbs along routes are color-coded according to the standard UCI category hierarchy (`REQ-UI-274`, `REQ-UI-298`). Category 4 climbs (`#2E7D32` Green) overlay directly on top of the green route polyline with near-zero chromatic contrast ("green on green"), rendering Cat 4 climbs virtually invisible. Uncategorized (UC) grey climbs (`#757575`) also suffer poor separation.
2. **Topographic Tile Blending**: Map vector tiles (Google Maps and OSM) render natural terrain (parks, forests, meadows, nature reserves) in light-to-medium green. Green routes blend into the background, degrading glanceability under outdoor sunlight glare and bicycle handlebar vibration.

**Architectural Solution**:
Transition the route visualization palette from green to **Royal Blue** (`Color(0xFF1565C0)` / Material Blue 800) and its coordinated shade hierarchy:
- Neither cycling climb categories (HC, Cat 1..4, UC) nor physiological exertion zones (Zone 1..5) utilize blue tones.
- Every climb category—especially Cat 4 Green (`#2E7D32`)—stands out with instant, sharp chromatic contrast against a Royal Blue route ribbon.
- Royal Blue provides superior figure-ground separation against green topographic map tiles.
- Blue aligns with industry navigation mental models (Google Maps, Apple Maps, Komoot) and the app's brand primary (`#1464F4` Electric Blue).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-306` (*Royal Blue Route Palette Transition, High-Contrast Climb Category Differentiation, and Topographic Glanceability*)
* **Test Mapping**: `TST-UI-266` (*Royal Blue Route Color Tokens, Multi-Tier Contrast Hierarchy, and Living Style Guide Synchronization Verification*):
  - `TST-UI-266.1`: Route Color Tokens Audit (`Color.kt`)
  - `TST-UI-266.2`: Multi-Tier Contrast & Climb Category Differentiation (`RouteColorPaletteContractTest.kt`)
  - `TST-UI-266.3`: Active Navigation Polyline Hierarchy (`MapRouteActiveNavigationTest.kt`)
  - `TST-UI-266.4`: Living Style Guide Synchronization (`docs/design_guidelines.md`)
  - `TST-UI-266.5`: Full Clean-Room Regression Suite (`./gradlew clean testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **UCI Climb Category Palette Invariance**: `getClimbCategoryColors` (HC `#880E4F`, Cat 1 `#C62828`, Cat 2 `#EF6C00`, Cat 3 `#F9A825`, Cat 4 `#2E7D32`, UC `#757575`) MUST NOT be altered.
2. **Strava Orange & Segments Synergy**: `TTColor.StravaOrange = Color(0xFFFC4C02)` and Strava Live Segments X-Ray layering (`SEGMENT_Z_INDEX = 30f`) MUST NOT be altered. Strava Orange retains prominent complementary contrast against Royal Blue.
3. **Multi-Layer Active Navigation Hierarchy**: Active navigation polyline dimensions and z-indices remain invariant:
   - Base polyline: `width = 16f`, `zIndex = 25f`, `color = TTColor.RouteActiveNavigation` (`Color(0xFF1E88E5)`).
   - Overlay dashed centerline: `width = 8f`, `zIndex = 45f`, `color = TTColor.RouteActiveNavigationOverlay` (`Color(0xFF0D47A1)`), `pattern = listOf(Dash(30f), Gap(20f))`.
   - Passive selected route: `width = 10f`, `zIndex = 20f`, `color = TTColor.RouteSelected` (`Color(0xFF1565C0)`).
   - Unselected background route: `width = 6f`, `zIndex = 5f`, `color = TTColor.RouteUnselected` (`Color(0xFF90CAF9)`).
4. **Live Track Breadcrumb Distinction**: Recorded GPS breadcrumb track (`LiveTrackLayer`, `resolveLiveTrackColor`) remains distinguishable via its dotted pattern (`listOf(Dot(), Gap(15f))`).
5. **Route UI Component Transparency**: Existing components (`RouteSelectionButton.kt`, `RouteSelectorSheet.kt`, `ReturnNavigationHud.kt`, `RouteSummaryHeader.kt`, `TurnPromptBanner.kt`, `WorkoutClusterComponents.kt`) reference `TTColor.RouteSelected` / `TTColor.RouteActiveNavigation` directly; by redefining the tokens in `Color.kt`, all components automatically inherit the refined Royal Blue palette without breaking call signatures or layout constraints.
6. **Living Style Guide Parity**: `docs/design_guidelines.md` Sections 5.4 and 5.7 MUST be updated in the same changeset to eliminate stale references to green route tones.
7. **Human Gate Invariance (Rule 2)**: Subtask `ATT-2824` transitions directly to `Erledigt` upon passing Gate 3 audit via `freigabe`. Parent ticket `ATT-2761` terminal state is `Final Review (Human)`.
8. **Zero Regression**: 100% clean-room test suite pass rate across all unit test classes.

---

## 4. UI Consistency (Rule 23)

In compliance with `docs/design_guidelines.md` (Section 5 *Visual Consistency Baseline*):
- **Closest Reference Component**: Map route visualization in `MapModels.kt` (`MapRoute`) and navigation cues in `TurnPromptBanner.kt` / `ReturnNavigationHud.kt`.
- **Reused Tokens**:
  - `TTColor.RouteSelected = Color(0xFF1565C0)` (Material Blue 800 / Royal Blue)
  - `TTColor.RouteUnselected = Color(0xFF90CAF9)` (Material Blue 200 / Soft Steel Blue)
  - `TTColor.RouteActiveNavigation = Color(0xFF1E88E5)` (Material Blue 600 / Vibrant Sapphire Blue)
  - `TTColor.RouteActiveNavigationOverlay = Color(0xFF0D47A1)` (Material Blue 900 / Deep Midnight Navy)
- **Theme Adaptability**: All updated tokens have been evaluated across Light, Dark, and AMOLED themes. The vibrant sapphire blue (`#1E88E5`) base with deep midnight navy (`#0D47A1`) dashed centerline provides high contrast against black `#000000` AMOLED surfaces as well as light map tiles.
- **One-Off Styles**: Zero one-off styles or arbitrary inline hex values. All route colors are centralized strictly within `TTColor` in `Color.kt`.

---

## 5. Architectural Decomposition (SWE.2)

```text
┌────────────────────────────────────────────────────────────────────────┐
│                        Color.kt (TTColor)                              │
│                                                                        │
│  RouteSelected               = Color(0xFF1565C0)  // Royal Blue        │
│  RouteUnselected             = Color(0xFF90CAF9)  // Soft Steel Blue   │
│  RouteActiveNavigation       = Color(0xFF1E88E5)  // Sapphire Blue     │
│  RouteActiveNavigationOverlay= Color(0xFF0D47A1)  // Midnight Navy     │
└───────────────────┬───────────────────────────────┬────────────────────┘
                    │                               │
                    ▼                               ▼
┌───────────────────────────────────────┐ ┌──────────────────────────────┐
│             MapModels.kt              │ │     Route UI Components      │
│                                       │ │                              │
│  MapRoute.color:                      │ │ • RouteSelectionButton.kt    │
│    isActiveNavigation -> Sapphire     │ │ • RouteSelectorSheet.kt      │
│    isSelected         -> Royal Blue   │ │ • ReturnNavigationHud.kt     │
│    else               -> Steel Blue   │ │ • TurnPromptBanner.kt        │
│  MapRoute.overlayColor:               │ │ • RouteSummaryHeader.kt      │
│    isActiveNavigation -> Midnight Navy│ │ • WorkoutClusterComponents.kt│
└───────────────────────────────────────┘ └──────────────────────────────┘
```

---

## 6. Step-by-Step Implementation Sequence

### Step 1: Update Core Route Visualization Tokens in `Color.kt`
- **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/Color.kt`
- **Action**: Redefine:
  - `RouteSelected = Color(0xFF1565C0)` (replacing `Color(0xFF228B22)`)
  - `RouteUnselected = Color(0xFF90CAF9)` (replacing `Color(0xFF90EE90)`)
  - `RouteActiveNavigation = Color(0xFF1E88E5)` (replacing `Color(0xFF00E676)`)
  - `RouteActiveNavigationOverlay = Color(0xFF0D47A1)` (replacing `Color(0xFF004D20)`)
- **Verification**: Compilation check via `./gradlew compileDebugKotlin`.

### Step 2: Synchronize Living Design Guidelines
- **Target File**: `docs/design_guidelines.md`
- **Action**:
  - Section 5.4 (*Color*): Update *Route & Navigation Domain Tints* to state Royal Blue tones (`TTColor.RouteSelected`, `TTColor.RouteActiveNavigation`), replacing obsolete green wording.
  - Section 5.7 (*In-Ride Navigation Cues & HUD Overlays*): Update banner border accent description to Royal Blue (`TTColor.RouteActiveNavigation`).
- **Verification**: Markdown audit confirming zero stale green route references.

### Step 3: Update `MapRouteActiveNavigationTest.kt`
- **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapRouteActiveNavigationTest.kt`
- **Action**:
  - Update `assertEquals(Color(0xFF00E676), activeRoute.color)` -> `assertEquals(Color(0xFF1E88E5), activeRoute.color)`
  - Update `assertEquals(Color(0xFF004D20), activeRoute.overlayColor)` -> `assertEquals(Color(0xFF0D47A1), activeRoute.overlayColor)`
  - Update `assertEquals(Color(0xFF228B22), passiveRoute.color)` -> `assertEquals(Color(0xFF1565C0), passiveRoute.color)`
- **Verification**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.MapRouteActiveNavigationTest"`.

### Step 4: Create `RouteColorPaletteContractTest.kt`
- **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/theme/RouteColorPaletteContractTest.kt`
- **Action**: Implement unit and contract tests verifying:
  1. Exact ARGB values for `RouteSelected`, `RouteUnselected`, `RouteActiveNavigation`, `RouteActiveNavigationOverlay`.
  2. Euclidean RGB distance between `RouteSelected` (`Color(0xFF1565C0)`) and all 6 climb categories (`HC`, `CAT_1`, `CAT_2`, `CAT_3`, `CAT_4`, `UNCATEGORIZED`), asserting Cat 4 green (`Color(0xFF2E7D32)`) has $\Delta C > 0.35$ (whereas the old green had $\Delta C \approx 0.08$).
  3. `docs/design_guidelines.md` synchronization (Sections 5.4 and 5.7 refer to Royal Blue, no green route mentions).
- **Verification**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.theme.RouteColorPaletteContractTest"`.

### Step 5: Full Clean-Room Regression Suite
- **Command**: `./gradlew clean testDebugUnitTest`
- **Assertion**: 100% pass rate across all test classes, 0 regressions.

---

## 7. Verification & Testing Strategy

| Scope | Verification Command / Target | Success Criteria |
| :--- | :--- | :--- |
| **Token Correctness** | `./gradlew testDebugUnitTest --tests "*.RouteColorPaletteContractTest"` | Exact ARGB equality with Royal Blue tokens |
| **Climb Category Contrast** | `RouteColorPaletteContractTest.kt` | Euclidean RGB distance $\Delta C > 0.35$ for Cat 4 green |
| **MapRoute Active Hierarchy** | `./gradlew testDebugUnitTest --tests "*.MapRouteActiveNavigationTest"` | Active and passive route color, width, and z-index parity |
| **Component Contracts** | `./gradlew testDebugUnitTest --tests "*Route*ContractTest*"` | All route UI contract tests pass |
| **Full Regression** | `./gradlew clean testDebugUnitTest` | 100% test pass rate |

---

## 8. Rollback & Contingency Plan

If any visual regression or unexpected conflict is detected during Stage 5 verification:
1. The 4 color tokens in `Color.kt` can be reverted with a single commit.
2. `MapRouteActiveNavigationTest.kt` and `RouteColorPaletteContractTest.kt` will track the revert.
3. No database schema changes, migrations, or persistent DataStore keys are involved; rollback risk is strictly zero.
