# Stage 3 Implementation Plan: ATT-3053 - Harmonize Climb popup visual design and architecture with Segment and Route popups using shared base layout

**Ticket**: [ATT-3053](https://atrainingtracker.atlassian.net/browse/ATT-3053)  
**Sub-task**: [ATT-3074](https://atrainingtracker.atlassian.net/browse/ATT-3074) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Active Sprint**: `Sprint 2026-41.7`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Architectural Decomposition (SWE.2)

### 1.1 Clean Architecture Boundaries & Responsibilities
- **UI Presentation Layer (`com.atrainingtracker.trainingtracker.ui`)**:
  - `ui/components/core/EntityDetailSheetScaffold.kt`: Shared base modal bottom sheet composable encapsulating `ModalBottomSheet`, `rememberModalBottomSheetState(skipPartiallyExpanded = true)`, `BottomSheetDesign.SheetShape`, `BottomSheetDesign.SheetTonalElevation`, `dragHandle = null`, `modifier.fillMaxHeight()`, and the standardized floating circular dismiss close button (`IconButton` on `CircleShape` Surface, elevation 2.dp, semi-transparent background, `Icons.Default.Close`).
  - `ui/climbs/ClimbOnMapScreen.kt`: Canonical climb inspection screen hosting `MapDetailLayout`, featuring structured `ClimbHeader`, `ClimbDetails`, 0.5dp `HorizontalDivider`, collapsible embedded map with `MapZoomFocus.EXPLICIT_BOUNDS`, category polyline, start/summit markers, and interactive scrubbable `ElevationProfile`.
  - `ui/climbs/ClimbDetailSheet.kt`: Refactored to delegate cleanly to `EntityDetailSheetScaffold` hosting `ClimbOnMapScreen`. Retains `calculateClimbBounds` and `ClimbDetailElevationProfile` for backward compatibility.
  - `ui/segments/SegmentDetailSheet.kt`: Refactored to eliminate redundant bottom sheet scaffolding by delegating to `EntityDetailSheetScaffold`.
  - `ui/routes/RouteDetailSheet.kt`: Refactored to eliminate redundant bottom sheet scaffolding by delegating to `EntityDetailSheetScaffold`.
- **Contract & Verification Layer (`app/src/test/java`)**:
  - `ui/components/core/EntityDetailSheetScaffoldContractTest.kt`: Contract test verifying `EntityDetailSheetScaffold` composable signature, styling tokens, close button, and its consumption across `SegmentDetailSheet`, `RouteDetailSheet`, and `ClimbDetailSheet`.
  - `ui/climbs/ClimbDetailSheetContractTest.kt`: Updated contract test asserting `ClimbDetailSheet` delegates to `EntityDetailSheetScaffold` and `ClimbOnMapScreen`, `ClimbHeader` and `ClimbDetails` structure, `MapDetailLayout` integration, and `calculateClimbBounds` math.

---

## 2. UI Consistency Audit (Rule 23)

- **Closest Existing Reference Screen**:
  - `SegmentDetailSheet.kt` and `SegmentOnMapScreen.kt` (Sprint 2026-41.6, `ATT-2860`).
- **Reused Components (`ui/components/`)**:
  - `EntityDetailSheetScaffold`: Shared base bottom sheet container.
  - `MapDetailLayout`: Core collapsible map and elevation profile layout.
  - `ClimbCategoryChip`: UCI category badge chip.
  - `MetricItem`: Standardized metric typography and icon display item.
  - `ATrainingTrackerMap`: Interactive Google Map canvas.
  - `ElevationProfile`: Scrubbable elevation profile canvas with map marker synchronization.
- **Design & Theme Tokens**:
  - `BottomSheetDesign.SheetShape` (20.dp top corner curvature radius, 0.dp bottom).
  - `BottomSheetDesign.SheetTonalElevation` (0.dp to eliminate unwanted grey tinting).
  - `MaterialTheme.colorScheme.surface` for clean sheet background.
  - `MaterialTheme.colorScheme.outlineVariant` for 0.5.dp divider lines.
  - `TTColor.StartPoint` and `TTColor.EndPoint` for start and summit pins.
  - `8.dp` padding and `zIndex(10f)` for top-right dismiss close button.
- **Justification for New Styles**:
  - No one-off styles are introduced. All layouts, paddings, typography, and iconography strictly adhere to `SegmentOnMapScreen` and `BottomSheetDesign`.

---

## 3. Atomic Step Sequencing

### Step 1: Create `EntityDetailSheetScaffold.kt`
- File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/EntityDetailSheetScaffold.kt`
- Define public composable:
  ```kotlin
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun EntityDetailSheetScaffold(
      onDismiss: () -> Unit,
      modifier: Modifier = Modifier,
      content: @Composable BoxScope.() -> Unit
  )
  ```
- Configure `ModalBottomSheet` with `skipPartiallyExpanded = true`, `BottomSheetDesign.SheetShape`, `BottomSheetDesign.SheetTonalElevation`, `containerColor = MaterialTheme.colorScheme.surface`, `dragHandle = null`, `modifier = modifier.fillMaxHeight()`.
- Add floating overlay close button aligned `Alignment.TopEnd` with `8.dp` padding and `zIndex(10f)`.

### Step 2: Implement `ClimbOnMapScreen.kt`
- File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/ClimbOnMapScreen.kt`
- Implement `ClimbOnMapScreen(climb, routeIndex, totalRouteClimbs, bSportType, modifier, useStatusBarsPadding, showMap, onHeaderHeightMeasured)`.
- Implement `ClimbHeader`:
  - Top row: Sport icon (`BSportType.BIKE`), Climb name (titleLarge, bold), `ClimbCategoryChip`, and route counter badge.
  - Second row: Start offset along route (`routes_climb_start_at`).
- Implement `ClimbDetails`:
  - Row 1: Distance (`climb.distanceMeters` formatted with `ic_distance`).
  - Row 2: Average grade (`routes_climb_avg_grade`) and Maximum grade (`routes_climb_max_grade` / `climb_max_grade_label`).
  - Row 3: Ascent Gain (`climb_remaining_elevation` / `ic_ascent`) and min/max elevation (`ic_altitude` / `ic_altitude_min` / `ic_altitude_max`).
- Integrate `MapDetailLayout`:
  - `zoomFocus = MapZoomFocus.EXPLICIT_BOUNDS`
  - `initialBounds = calculateClimbBounds(climb)`
  - `activeScrubPath = climb.pathPoints`
  - `showElevationProfile = true`
  - `mapContent = { climbs(listOf(climb)); ... start & summit markers ... }`

### Step 3: Refactor `ClimbDetailSheet.kt`
- File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/ClimbDetailSheet.kt`
- Replace `AppModalBottomSheet` and the 3 custom cards with `EntityDetailSheetScaffold` hosting `ClimbOnMapScreen`.
- Preserve `calculateClimbBounds` and `ClimbDetailElevationProfile` for backward compatibility.

### Step 4: Refactor `SegmentDetailSheet.kt` and `RouteDetailSheet.kt`
- Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/SegmentDetailSheet.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteDetailSheet.kt`
- Replace repetitive `ModalBottomSheet` and dismiss `IconButton` blocks with `EntityDetailSheetScaffold`.

### Step 5: Author & Update Contract Tests
- Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/EntityDetailSheetScaffoldContractTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/climbs/ClimbDetailSheetContractTest.kt`
- Execute targeted unit tests:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.core.EntityDetailSheetScaffoldContractTest" --tests "com.atrainingtracker.trainingtracker.ui.climbs.ClimbDetailSheetContractTest" --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteClimbsBreakdownContractTest"`

---

## 4. Invariant Protection & Rollback Safety

1. **Screen State Persistence**: Dismissing `ClimbDetailSheet`, `SegmentDetailSheet`, or `RouteDetailSheet` must not reset route selection, map zoom, or scroll state in `RouteOnMapScreen`.
2. **Backward Compatibility**: `calculateClimbBounds(climb: Climb)` and `ClimbDetailElevationProfile` must remain public and intact.
3. **9-Language Localization**: Zero missing strings or altered placeholders.
4. **Clean-Room Test Pass Rate**: Full regression suite (`./gradlew testDebugUnitTest`) must pass with 100% success rate.
