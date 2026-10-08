# Stage 2: Requirement & Test Specification (ATT-2511)

## 1. Traceability & Scope
- **Ticket**: [ATT-2511](https://atrainingtracker.atlassian.net/browse/ATT-2511) — *[Verbesserung] Display Dedicated Climb Detail View with Focused Map and Zoomed Elevation Profile on Climb Item Tap*
- **Subtask**: `ATT-2742` (`[Req & Test Spec]`)
- **Requirement**: `REQ-UI-300` (*Dedicated Route Climb Detail View with Focused Map and Zoomed Isolated Elevation Profile*)
- **Test Specification**: `TST-UI-260` (*Dedicated Route Climb Detail View with Focused Map and Zoomed Elevation Profile Verification*)

---

## 2. Formal Requirement Specification (`REQ-UI-300`)
1. **Interactive Breakdown Item Tap (`RouteOnMapScreen.kt`)**:
   - `RouteClimbsBreakdownSection` SHALL support an optional callback `onClimbClick: ((Climb) -> Unit)? = null`.
   - Each `ElevatedCard` in `RouteClimbsBreakdownSection` SHALL specify `onClick = { onClimbClick?.invoke(climb) }`.
   - When tapped, `RouteOnMapScreen` updates its local state `selectedClimbForDetail = climb`.
2. **Dedicated Climb Detail Bottom Sheet (`ClimbDetailSheet.kt`)**:
   - When `selectedClimbForDetail != null`, the system SHALL display `ClimbDetailSheet(climb: Climb, onDismiss: () -> Unit)`.
   - Built on Material 3 `AppModalBottomSheet` with drag handle, title, `ClimbCategoryChip`, route counter, and close button.
   - **Metrics HUD Row**: Displays formatted metrics via `LocalMetricFormatter`:
     - Distance (`formatters.distance.format_with_units(climb.distanceMeters)`)
     - Elevation Gain (`+${formatters.altitude.format_with_units(climb.elevationGainMeters)}`)
     - Average Grade (`stringResource(R.string.routes_climb_avg_grade, climb.avgGradePercent)`)
     - Maximum Grade (`stringResource(R.string.routes_climb_max_grade, climb.maxGradePercent)`)
   - **Focused Map Viewport**:
     - Embedded `ATrainingTrackerMap` with `zoomFocus = MapZoomFocus.EXPLICIT_BOUNDS`.
     - Spatial bounds strictly determined by `climb.pathPoints` coordinates.
     - Renders climb path in category color (`width = 10f`, `zIndex = 25f`), start marker (`R.drawable.control_start` / `ic_ascent`), and summit marker (`R.drawable.control_stop`).
   - **Isolated Zoomed Elevation Profile**:
     - `ClimbDetailElevationProfile` canvas rendering elevation profile curve strictly across $[0, \text{climb.distanceMeters}]$.
     - Curve colored by terrain grade zones (`Zone1`..`Zone5`).
     - Subtle shaded gradient fill below curve.
     - Minimum altitude and peak summit altitude indicators.
3. **State Isolation & Dismissal**:
   - Tapping close, dragging down, or tapping outside dismisses the sheet and resets `selectedClimbForDetail = null`.
   - Parent route selection, map center, route overview scrubbing, and scroll position in `RouteOnMapScreen` MUST remain unchanged.
4. **100% 9-Language Localization Parity**:
   - String `routes_climb_max_grade` added to all 9 language resources (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 3. Test Specification (`TST-UI-260`)

### Test Case 1: `RouteClimbsBreakdownSection` Callback & Click Contract
- **Class**: `RouteClimbsBreakdownContractTest.kt`
- **Given**: A list of route `Climb` instances.
- **When**: Inspecting `RouteClimbsBreakdownSection` method signature via reflection.
- **Then**: Parameter `onClimbClick` of type `Function1<Climb, Unit>?` SHALL be present.

### Test Case 2: `ClimbDetailSheet` Structure & Content Contract
- **Class**: `ClimbDetailSheetContractTest.kt`
- **Given**: A valid `Climb` instance with `pathPoints`.
- **When**: Inspecting `ClimbDetailSheet` composable and its child components.
- **Then**:
  - `ClimbDetailSheet` and `ClimbDetailElevationProfile` SHALL be public composables in `com.atrainingtracker.trainingtracker.ui.climbs`.
  - LatLngBounds computation helper SHALL correctly derive bounds enclosing all climb path coordinates.

### Test Case 3: 9-Language Localization Parity
- **Class**: `ClimbDetailLocalizationTest.kt`
- **Given**: All 9 supported language resource files (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
- **When**: Checking for key `routes_climb_max_grade` and other climb keys.
- **Then**: Every language file SHALL define the string with valid format specifiers.

### Test Case 4: Full Clean-Room Regression Suite
- **Command**: `./gradlew testDebugUnitTest`
- **Expected Outcome**: Zero regressions, 100% pass rate.
