# Implementation Plan - Scale down Powered by Strava in WorkoutSummary (ATT-259)

## 1. Requirement Traceability
| ID | Description | Component | Test ID |
|:---|:---|:---|:---|
| **REQ-UI-026** | Official third-party logos (e.g., Strava) must be scaled to subordinate heights (10dp-24dp) to prevent visual dominance. | `StravaActivitySection.kt` | `TST-UI-036` |

## 2. Proposed Changes

### `StravaActivitySection.kt`
- Update the `PoweredByStrava` component call to include `height = 16.dp`.
- This ensures the logo is scaled down appropriately when displayed within the `WorkoutSummary`.

## 3. Impact Analysis
- **UI Consistency**: Brings the `WorkoutSummary` Strava branding in line with other parts of the app (e.g., `SegmentList`, `RouteSummaryHeader`).
- **Compliance**: Maintains compliance with Strava's branding guidelines while ensuring it doesn't dominate the workout data.

## 4. Verification Plan
### Manual Verification (TST-UI-036)
1. Open the workout list.
2. Find a workout that has been uploaded to Strava (displays Strava results).
3. Verify that the "Powered by Strava" logo at the bottom of the Strava results section is compact (16dp height) and not overly large.
