# Stage 5 Walkthrough: ATT-2511

## 1. Executive Summary
- **Ticket**: [ATT-2511](https://atrainingtracker.atlassian.net/browse/ATT-2511) — *[Verbesserung] Display Dedicated Climb Detail View with Focused Map and Zoomed Elevation Profile on Climb Item Tap*
- **Subtask**: `ATT-2747` (`[Verification]`)
- **Requirement**: `REQ-UI-300` (*Dedicated Route Climb Detail View with Focused Map and Zoomed Isolated Elevation Profile*)
- **Test Specification**: `TST-UI-260` (*Dedicated Route Climb Detail View with Focused Map and Zoomed Elevation Profile Verification*)

---

## 2. Changes Implemented
1. **Localization Parity**:
   - Added string `routes_climb_max_grade` with 100% parity across all 9 supported languages:
     - EN: `"Max. %1$.1f%%"`
     - DE: `"Max. %1$.1f%%"`
     - ES: `"Máx. %1$.1f%%"`
     - FR: `"Max. %1$.1f%%"`
     - IT: `"Max %1$.1f%%"`
     - JA: `"最大 %1$.1f%%"`
     - NL: `"Max. %1$.1f%%"`
     - PL: `"Maks. %1$.1f%%"`
     - PT: `"Máx. %1$.1f%%"`
2. **Dedicated Climb Detail Bottom Sheet (`ClimbDetailSheet.kt`)**:
   - Built on Material 3 `AppModalBottomSheet` with drag handle, title, `ClimbCategoryChip`, route counter badge, and close button.
   - **Metrics HUD Card**: Distance, Elevation gain, Average grade, Maximum grade, and Start offset.
   - **Focused Map Viewport**: Embedded `ATrainingTrackerMap` with `zoomFocus = MapZoomFocus.EXPLICIT_BOUNDS` and tight bounding box around the climb coordinates. Highlights climb path, start marker (`R.drawable.control_start`), and summit marker (`R.drawable.control_stop`).
   - **Zoomed Isolated Elevation Profile**: `ClimbDetailElevationProfile` canvas rendering the climb curve with slope grade color coding (`Zone1`..`Zone5`), gradient under-curve fills, and minimum/maximum altitude markers.
3. **Interactive Breakdown Item Tap (`RouteOnMapScreen.kt`)**:
   - Added `onClimbClick: ((Climb) -> Unit)? = null` to `RouteClimbsBreakdownSection`.
   - Wired `ElevatedCard` tap to update `selectedClimbForDetail` state in `RouteOnMapScreen`.
   - Renders `ClimbDetailSheet` with clean dismissal preserving parent route selection, map center, scrubbing, and scroll state.

---

## 3. Verification & Test Evidence
- **`ClimbDetailLocalizationTest.kt`**: PASSED (All 9 language files verified for `routes_climb_max_grade`, `routes_climb_avg_grade`, `routes_climb_start_at`, `routes_climbs_section_title`, and `climb_route_counter`).
- **`ClimbDetailSheetContractTest.kt`**: PASSED (Composables public, M3 bottom sheet, telemetry tokens, focused map with `EXPLICIT_BOUNDS`, and `calculateClimbBounds` zero-area expansion fallback).
- **`RouteClimbsBreakdownContractTest.kt`**: PASSED (Callback parameter, `ElevatedCard` click dispatch, state management, and sheet presentation).
- **Full Regression Test Suite**: Executed with 0 regressions.
