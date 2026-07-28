# Implementation Plan - ATT-440 Final Refinement: Synchronized Marker Alpha & Blending

Address the issue where technical markers are almost invisible in the Period Detail view by standardizing base alpha values and refining the zoom-adaptive multiplier schedule.

## User Review Required

> [!IMPORTANT]
> - **Visible Markers**: I identified a mathematical error where the marker transparency was being reduced twice, making them almost invisible. I am fixing the blending engine to ensure markers are clearly visible when you zoom in.
> - **Cohesive Look**: As requested, I am standardizing all "Member" markers (Start, Stop, Apex) to use the exact same alpha value across both Workout Clusters and Periods, ensuring a consistent professional aesthetic.
> - **Clean Overview**: Markers will still be hidden when you are zoomed out to maintain a clean overview, but they will now fade in much more prominently as you approach the ground.

## Proposed Changes

### 1. Map DSL Layer: Robust Blending Schedule
Fulfills REQ-MAP-016 (Refinement) | Test: TST-MAP-010

#### [MODIFY] [MapContentScope.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapContentScope.kt)
- **Refine `markerAlphaMult` Schedule**:
    - Current: level 17+ = 0.1 (Too faint).
    - **New Schedule**:
        | Zoom | Multiplier | Resulting Alpha (at base 0.5) |
        |:---|:---|:---|
        | < 13 | 0.0 | 0.0 (Hidden) |
        | 14-15 | **0.2** | **0.1** (Faint Context) |
        | 16 | **0.6** | **0.3** (Visible) |
        | 17+ | **1.0** | **0.5** (Standard Detail) |

### 2. UI Logic Layer: Standardized Base Alpha
#### [MODIFY] [PeriodsViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsViewModel.kt)
#### [MODIFY] [WorkoutClustersViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt)
#### [MODIFY] [InteractivePeriodMap.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/InteractivePeriodMap.kt)
- Standardize all non-primary markers to `alpha = 0.5f`.
- **Rationale**: This ensures that every technical "Member" marker in the entire application participates in the same blending logic and has the same visual weight.

## Verification Plan

### Manual Verification (TST-MAP-010 Refined)
1. Open the 'Solitude Runde' cluster heatmap or any Period Map.
2. **Verify** that at zoom level 11/12, no marker pins are visible.
3. Zoom in to level 17+. **Verify** that all technical markers (Start, End, Apex) are clearly visible and share the same transparency level.
4. **Compare** Workout Clusters and Period Maps to ensure visual parity.
