# Implementation Plan - SCRUM-139: Unified Stats Summary Block

The `StatsSummaryBlock` currently lacks the technical visual identity established in the rest of the application. This plan refactors it to use unified components and a high-density vertical layout.

## 1. Requirement Fulfillment
| Requirement ID | Description | Component | Test ID |
|:---|:---|:---|:---|
| **REQ-UI-046** | The `StatsSummaryBlock` SHALL display metrics using a vertical layout (heading above value) matching the visual identity of Workout, Segment, and Route items. | `StatsSummaryBlock.kt` | `TST-UI-046` |

## 2. Proposed Changes

### UI Components (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/stats/`)

#### [MODIFIED] `StatsSummaryBlock.kt`
*   Delete the private `StatItem` helper.
*   Refactor `StatsSummaryBlock` to use a `Row` containing four `MetricItem` instances.
*   Configure each `MetricItem` with:
    *   `layout = MetricLayout.VERTICAL`
    *   `iconSize = 24.dp`
    *   `isPrimary = true`
*   Mapping:
    1.  **Workouts**: `iconRes = R.drawable.workout_list`, label = `R.string.stats_workouts`.
    2.  **Distance**: `iconRes = R.drawable.ic_distance`, label = `R.string.stats_distance`.
    3.  **Time**: `iconRes = R.drawable.ic_time_active`, label = `R.string.stats_time`.
    4.  **Ascent**: `iconRes = R.drawable.ic_ascent`, label = `R.string.stats_ascent`.
*   Use `Modifier.weight(1f)` on each item to ensure equal distribution across the width.

## 3. Impact Analysis
*   **Android System**: No impact. Pure UI modification.
*   **Component Interfaces**: No change to `StatsData` or `onStatsClick` callback signature.
*   **Visual Consistency**: Significantly improved; ensures that equipment and sport-type stats look identical to period summaries and workout headers.

## 4. Verification Plan (TST-UI-046)
*   **Automated**: Run `app:assembleDebug` to ensure no regressions.
*   **Manual**: 
    1.  Open the Equipment details screen.
    2.  Verify that labels (e.g., "Workouts") are positioned above the numeric values.
    3.  Verify that technical icons are present and correctly colored (onSurfaceVariant).
    4.  Verify that values are bold and use standard typography.
