# Implementation Plan: Professional Color Consistency (SCRUM-150)

## 1. Goal
Standardize color and alpha usage across the application to improve visual hierarchy, ensure consistent Material 3 depth, and simplify theme maintenance.

## 2. Requirement Mapping
*   **REQ-UI-054**: Implement semantic alpha constants and color tokens. (Test: TST-UI-059)
*   **REQ-UI-055**: Highlighting primary performance data with the `primary` color. (Test: TST-UI-060)

## 3. Impact Analysis (SWE.1.BP.5)
*   **Android System**: Updating status bar color logic to ensure visibility on all backgrounds.
*   **Component Interfaces**: Changing `MetricItem` parameters to support primary highlighting.
*   **Data Integrity**: Zero risk.
*   **Visual Logic**: High. The app will have a more authoritative look for key metrics, and headers will have correct depth.

## 4. Proposed Changes

### Theme & Palette (`Color.kt`, `Theme.kt`)
*   Define `TTAlpha` object: `High (1.0f)`, `Medium (0.7f)`, `Disabled (0.38f)`, `Subtle (0.12f)`.
*   Fix `surfaceContainerHighest` mapping in `Theme.kt` (it currently points to "Low").
*   Add semantic achievement colors (Gold, Silver, Bronze) to the color scheme.
*   Standardize `statusBarColor` to follow the background/container instead of a hardcoded blue.

### Component Layer (`MetricItem.kt`, `WorkoutExtrema.kt`)
*   Update `MetricItem` to use `TTAlpha` for secondary values.
*   Add `isPrimaryValue: Boolean` to `MetricItem`. If true, the value text uses `MaterialTheme.colorScheme.primary`.
*   Update `WorkoutExtrema.kt` to replace hardcoded `.copy(alpha = 0.7f)` and `0.3f` with semantic tokens.

### Screen Logic (`WorkoutDetails.kt`, `RouteSummaryHeader.kt`)
*   Enable `isPrimaryValue` for Total Distance and Active Time.
*   Update `AltitudeRow` to use semantic colors.

## 5. Verification Criteria (TST-UI-059, TST-UI-060)
*   Manual audit of code for hardcoded alpha literals.
*   Visual verification that primary workout metrics (Distance/Time) are blue (primary) while secondary labels remain gray (onSurfaceVariant).
*   Verify status bar readability in both light and dark modes.
