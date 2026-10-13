# Stage 2 Requirement & Test Specification: ATT-3053 - Harmonize Climb popup visual design and architecture with Segment and Route popups using shared base layout

**Ticket**: [ATT-3053](https://atrainingtracker.atlassian.net/browse/ATT-3053)  
**Sub-task**: [ATT-3073](https://atrainingtracker.atlassian.net/browse/ATT-3073) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Active Sprint**: `Sprint 2026-41.7`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Formal Requirement Specification (`REQ-UI-333`)

### 1.1 Requirement Statement
The system SHALL eliminate duplicated bottom sheet scaffolding across entity popups and harmonize `ClimbDetailSheet.kt` with `SegmentDetailSheet.kt` and `RouteDetailSheet.kt` by introducing `EntityDetailSheetScaffold` and backing `ClimbDetailSheet` with `MapDetailLayout` (ATT-3053, amending `REQ-UI-315` and `REQ-UI-300`):

1. **Shared Entity Detail Sheet Scaffold (`EntityDetailSheetScaffold.kt`)**:
   - Provide a reusable composable `EntityDetailSheetScaffold` in `com.atrainingtracker.trainingtracker.ui.components.core` (or `ui.map`).
   - The scaffold SHALL encapsulate Material 3 `ModalBottomSheet` configured with `rememberModalBottomSheetState(skipPartiallyExpanded = true)`, `shape = BottomSheetDesign.SheetShape`, `tonalElevation = BottomSheetDesign.SheetTonalElevation`, `containerColor = MaterialTheme.colorScheme.surface`, `dragHandle = null`, and `modifier = modifier.fillMaxHeight()`.
   - The scaffold SHALL render a persistent floating circular dismiss close button (`IconButton` wrapping a 2.dp elevated `Surface` with `CircleShape`, `Icons.Default.Close`, semi-transparent surface background) anchored at `Alignment.TopEnd` with `8.dp` padding and `zIndex(10f)`.
   - The scaffold SHALL expose slot `content: @Composable BoxScope.() -> Unit`.
   - `SegmentDetailSheet.kt`, `RouteDetailSheet.kt`, and `ClimbDetailSheet.kt` SHALL all delegate to `EntityDetailSheetScaffold`, eliminating redundant bottom sheet scaffolding boilerplate.
2. **Climb Screen & Detail Architecture (`ClimbOnMapScreen.kt` & `ClimbDetailSheet.kt`)**:
   - `ClimbDetailSheet` SHALL host `ClimbOnMapScreen` inside `EntityDetailSheetScaffold`.
   - `ClimbOnMapScreen` SHALL be powered by `MapDetailLayout` with:
     - **Structured Header (`ClimbHeader`)**: Displays sport icon (`BSportType.BIKE.iconResId`), climb name (`titleLarge`, bold), `ClimbCategoryChip` indicating UCI classification, route counter badge (e.g. `climb_route_counter`, `routeIndex` of `totalRouteClimbs`), and climb start offset along route (`routes_climb_start_at`).
     - **Structured Details (`ClimbDetails`)**: 3-row metric presentation conforming to `SegmentDetails` design tokens:
       - Row 1: Climb distance (`climb.distanceMeters` formatted with `ic_distance`).
       - Row 2: Average grade (`routes_climb_avg_grade` / `ic_grade`) and Maximum grade (`routes_climb_max_grade` / `climb_max_grade_label`).
       - Row 3: Elevation gain (`climb_remaining_elevation` / `ic_ascent`) and min/max elevation (`ic_altitude` / `ic_altitude_min` / `ic_altitude_max`).
     - **Divider**: 0.5dp `HorizontalDivider` with `outlineVariant` color between header and details.
     - **MapDetailLayout Viewport**: Collapsible embedded map with `MapZoomFocus.EXPLICIT_BOUNDS`, `initialBounds = calculateClimbBounds(climb)`, category-colored climb polyline, start/summit markers, and the interactive `ElevationProfile` chart with scrubbing synchronization.
     - `useStatusBarsPadding = false`.
3. **State Isolation & Seamless Dismissal**:
   - Dismissing the climb sheet via top-right close button, downward drag gesture, scrim tap, or system back press SHALL dismiss the sheet and return to `RouteOnMapScreen` without clearing route selection, route bounds, or scroll position.
4. **Preservation of System Invariants**:
   - `calculateClimbBounds` bounding box logic with single-point fallback, category chip rendering, 9-language localization parity, and 100% full clean-room unit test pass rate MUST be strictly preserved.

### 1.2 Chesterton's Fence Requirement Archaeology
- **Original Requirement ID & Target**: Amends `REQ-UI-315` (*Harmonized Visual & UX Architecture for Climb, Segment, and Route Detail Bottom Sheets via MapDetailLayout*, Sprint 2026-41.6, ATT-2860) and `REQ-UI-300` (*Dedicated Route Climb Detail View with Focused Map and Zoomed Isolated Elevation Profile*, Sprint 2026-41.3, ATT-2511).
- **Historical Origin & Commit Trace**: Introduced in Sprint 2026-41.3 (`ATT-2511`) and refined in Sprint 2026-41.6 (`ATT-2860`).
- **Root Reason for Existing Formulation**: In ATT-2860, `SegmentDetailSheet` and `RouteDetailSheet` were unified via `MapDetailLayout`, but `ClimbDetailSheet` was left with the obsolete 3-card stack and duplicated scaffolding. The user explicitly requested: *"The popup for the climbs must be (alomst) similr to the popup of the segments. From my point of view, the climb popup should share some of the code form the segment (and route) popup. I.e., I think there should be a common base class..."*.
- **Preservation of Core Invariants**: Bounding box calculations, UCI category chips, dismiss behavior, and 100% test pass rate remain preserved.

### 1.3 Acceptance Criteria (Given-When-Then)
- **Criterion 1 (Climb Popup Visual & Functional Harmonization)**:
  - *Given* an athlete tapping a recognized climb in `RouteOnMapScreen`,
  - *When* `ClimbDetailSheet` opens,
  - *Then* it is presented using `EntityDetailSheetScaffold` and `MapDetailLayout`, displaying harmonized `ClimbHeader`, `ClimbDetails`, interactive map, and scrubbable elevation profile matching the visual rhythm of `SegmentDetailSheet`.
- **Criterion 2 (Common Base Architecture)**:
  - *Given* `SegmentDetailSheet`, `RouteDetailSheet`, and `ClimbDetailSheet`,
  - *When* inspecting their source code and structure,
  - *Then* all three leverage the common `EntityDetailSheetScaffold`.
- **Criterion 3 (Interactive Elevation Profile Scrubbing)**:
  - *Given* an athlete scrubbing the elevation profile of `ClimbDetailSheet`,
  - *When* moving the scrubber,
  - *Then* the elevation profile updates smoothly and synchronizes with the map marker.
- **Criterion 4 (Seamless Non-Destructive Dismissal)**:
  - *Given* any entity detail sheet (Climb, Segment, Route),
  - *When* tapping the top-right close button or swiping down,
  - *Then* the sheet dismisses cleanly and restores parent screen state.

---

## 2. Test Specification (`TST-UI-293`)

### 2.1 Test Cases & Verification Procedures

#### Case 1: Shared EntityDetailSheetScaffold Architectural Contract Tests (`EntityDetailSheetScaffoldContractTest.kt`)
- Verify `EntityDetailSheetScaffold` composable exists, is public, and accepts `onDismiss: () -> Unit`, `modifier: Modifier`, and `content: @Composable BoxScope.() -> Unit`.
- Verify `ModalBottomSheet` configuration: `skipPartiallyExpanded = true`, `BottomSheetDesign.SheetShape`, `BottomSheetDesign.SheetTonalElevation`, and `dragHandle = null`.
- Verify top-right dismiss close button: `IconButton` with `CircleShape` Surface, elevation `2.dp`, and `Icons.Default.Close`.
- Verify `SegmentDetailSheet.kt`, `RouteDetailSheet.kt`, and `ClimbDetailSheet.kt` all delegate to `EntityDetailSheetScaffold`.

#### Case 2: ClimbDetailSheet & ClimbOnMapScreen Visual & Layout Contract Tests (`ClimbDetailSheetContractTest.kt`)
- Verify `ClimbDetailSheet` delegates to `EntityDetailSheetScaffold` and `ClimbOnMapScreen`.
- Verify `ClimbOnMapScreen` uses `MapDetailLayout` with `MapZoomFocus.EXPLICIT_BOUNDS` and `calculateClimbBounds(climb)`.
- Verify `ClimbHeader` renders sport icon, climb name, category chip, and route counter badge.
- Verify `ClimbDetails` renders distance, average grade, maximum grade, elevation gain, and min/max elevation with standard icons (`ic_distance`, `ic_grade`, `ic_ascent`, `ic_altitude`).
- Verify `calculateClimbBounds` correctly computes bounds for multi-point and single-point climbs.

#### Case 3: 9-Language Localization Audit
- Verify existing string resources (`climb_title`, `climb_route_counter`, `routes_climb_start_at`, `climb_remaining_dist`, `climb_remaining_elevation`, `routes_climb_avg_grade`, `routes_climb_max_grade`, `climb_max_grade_label`) exist across EN, DE, ES, FR, IT, JA, NL, PL, PT.

#### Case 4: Clean-Room Full Suite Regression Execution
- Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

---

## 3. ASPICE Traceability Matrix

| Requirement | Test Specification | Target Implementation | Test File |
|---|---|---|---|
| `REQ-UI-333` Clause 1 | `TST-UI-293` Case 1 | `EntityDetailSheetScaffold.kt`, `SegmentDetailSheet.kt`, `RouteDetailSheet.kt`, `ClimbDetailSheet.kt` | `EntityDetailSheetScaffoldContractTest.kt` |
| `REQ-UI-333` Clause 2 | `TST-UI-293` Case 2 | `ClimbDetailSheet.kt`, `ClimbOnMapScreen.kt` | `ClimbDetailSheetContractTest.kt` |
| `REQ-UI-333` Clause 3-4 | `TST-UI-293` Case 3, 4 | Localization resource files & full codebase | `./gradlew testDebugUnitTest` |
