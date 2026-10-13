# Stage 3 Implementation Plan: ATT-2511

## 1. Ticket & Architectural Context
- **Ticket**: [ATT-2511](https://atrainingtracker.atlassian.net/browse/ATT-2511) — *[Verbesserung] Display Dedicated Climb Detail View with Focused Map and Zoomed Elevation Profile on Climb Item Tap*
- **Subtask**: `ATT-2743` (`[Impl-Plan]`)
- **Requirement**: `REQ-UI-300`
- **Test Specification**: `TST-UI-260`

---

## 2. SWE.2 Architecture & Component Design
The climb detail view provides a dedicated deep dive into a specific climb along a route without corrupting or resetting the parent `RouteOnMapScreen` state.

### Component Decomposition
1. **`ClimbDetailSheet.kt` (`com.atrainingtracker.trainingtracker.ui.climbs`)**:
   - `ClimbDetailSheet(climb: Climb, routeIndex: Int? = null, totalRouteClimbs: Int? = null, bSportType: BSportType = BSportType.CYCLING, onDismiss: () -> Unit)`
   - Built on top of `AppModalBottomSheet`:
     - Drag handle (`MinimumDragHandle()`).
     - Header: Climb title (`climb.name.ifBlank { "Climb" }`), `ClimbCategoryChip(climb.category)`, route counter badge (`stringResource(R.string.climb_route_counter, index, total)`), and close button.
     - Metrics HUD card:
       - Row of key metrics: Distance, Elevation gain (+ΔH), Average grade (%), Maximum grade (%), and Start at route offset.
     - Focused Map Card:
       - Embedded `ATrainingTrackerMap` with `zoomFocus = MapZoomFocus.EXPLICIT_BOUNDS`.
       - LatLngBounds calculated to tightly enclose `climb.pathPoints` with fallback to `startLatLng`/`endLatLng`.
       - Render climb polyline in category color (`width = 10f`, `zIndex = 25f`), green start marker (`R.drawable.control_start` / `ic_ascent`), and red summit marker (`R.drawable.control_stop`).
     - Isolated Zoomed Elevation Profile:
       - `ClimbDetailElevationProfile(climb: Climb, modifier: Modifier)`
       - Canvas rendering the climb elevation curve from start (0 m) to summit (`climb.distanceMeters`).
       - Gradient-colored line segments matching slope zones (`Zone1`..`Zone5`).
       - Shaded gradient fill below the curve.
       - Start altitude and summit altitude indicators.
2. **`RouteOnMapScreen.kt` Updates**:
   - Update `RouteClimbsBreakdownSection` signature to accept `onClimbClick: ((Climb) -> Unit)? = null`.
   - Update `ElevatedCard` in `RouteClimbsBreakdownSection` to invoke `onClick = { onClimbClick?.invoke(climb) }`.
   - In `RouteOnMapScreen`, add state `var selectedClimbForDetail by remember { mutableStateOf<Climb?>(null) }`.
   - Pass `onClimbClick = { climb -> selectedClimbForDetail = climb }` to `RouteClimbsBreakdownSection`.
   - Render `ClimbDetailSheet(climb = selectedClimbForDetail!!, ...)` when `selectedClimbForDetail != null`.

---

## 3. Atomic Implementation Steps

### Step 1: 9-Language Localization Parity
Add `routes_climb_max_grade` to all 9 `strings.xml` files:
- `values/strings.xml`: `<string name="routes_climb_max_grade">Max. %1$.1f%%</string>`
- `values-de/strings.xml`: `<string name="routes_climb_max_grade">Max. %1$.1f%%</string>`
- `values-es/strings.xml`: `<string name="routes_climb_max_grade">Máx. %1$.1f%%</string>`
- `values-fr/strings.xml`: `<string name="routes_climb_max_grade">Max. %1$.1f%%</string>`
- `values-it/strings.xml`: `<string name="routes_climb_max_grade">Max %1$.1f%%</string>`
- `values-ja/strings.xml`: `<string name="routes_climb_max_grade">最大 %1$.1f%%</string>`
- `values-nl/strings.xml`: `<string name="routes_climb_max_grade">Max. %1$.1f%%</string>`
- `values-pl/strings.xml`: `<string name="routes_climb_max_grade">Maks. %1$.1f%%</string>`
- `values-pt/strings.xml`: `<string name="routes_climb_max_grade">Máx. %1$.1f%%</string>`

### Step 2: Create `ClimbDetailSheet.kt`
Create `app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/ClimbDetailSheet.kt`:
- Helper `calculateClimbBounds(climb: Climb): LatLngBounds?`
- Composable `ClimbDetailElevationProfile(climb: Climb, modifier: Modifier)`
- Composable `ClimbDetailSheet(climb: Climb, routeIndex: Int?, totalRouteClimbs: Int?, bSportType: BSportType, onDismiss: () -> Unit)`

### Step 3: Integrate with `RouteOnMapScreen.kt`
- Add `onClimbClick: ((Climb) -> Unit)? = null` to `RouteClimbsBreakdownSection`.
- Add `onClick = { onClimbClick?.invoke(climb) }` to `ElevatedCard`.
- In `RouteOnMapScreen`, add state `selectedClimbForDetail`.
- Wire `selectedClimbForDetail` to `ClimbDetailSheet`.

### Step 4: Write Unit & Contract Tests
- `ClimbDetailLocalizationTest.kt`: Verifies all 9 languages contain `routes_climb_max_grade` and other climb strings.
- `RouteClimbsBreakdownContractTest.kt`: Verifies callback support and click dispatch.
- `ClimbDetailSheetContractTest.kt`: Verifies `ClimbDetailSheet`, `ClimbDetailElevationProfile`, and `calculateClimbBounds` contracts.

### Step 5: Clean-Room Full-Suite Verification
- Run `./gradlew testDebugUnitTest` ensuring 100% test pass rate with zero regressions.

---

## 4. UI Consistency & Theming (Rule 23)
- **Design Tokens**: Strict adherence to `MaterialTheme.colorScheme` (`surface`, `surfaceVariant`, `onSurface`, `onSurfaceVariant`, `outlineVariant`) and `TTColor` slope grades (`Zone1`..`Zone5`).
- **Dark/Light Mode**: Full automatic contrast parity via standard theme tokens.
- **Typography**: M3 typography tokens (`titleMedium`, `bodySmall`, `labelSmall`).
- **Rounded Corners & Spacing**: Standard 12.dp corner shapes for cards, 8.dp / 12.dp padding intervals.
- **Touch Targets**: Minimum 48.dp interactive touch targets on dismiss button and climb cards.

---

## 5. Invariants & Risk Mitigation
- **Invariants**:
  - `MapDetailLayout` route rendering, route scrubbing, and split-pane resizing MUST NOT be altered.
  - Parent route state MUST remain pristine upon opening and closing `ClimbDetailSheet`.
- **Degenerate Handling**:
  - Climbs with $< 2$ points fallback to start/end coordinate bounds and basic ramp canvas.
