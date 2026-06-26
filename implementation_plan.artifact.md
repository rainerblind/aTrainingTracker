# Implementation Plan - SCRUM-126: Elevation Section Labeling Refinement

Swap the vertical order of the label and icon in the Workout Summary elevation section to improve visual hierarchy.

## 1. Requirements Mapping
- **Requirement**: `REQ-UI-021` (Improved Elevation Section Labeling)
- **Test ID**: `TST-UI-031` (Layout Hierarchy)

## 2. Impact Analysis
- **UI Component**: `WorkoutDetails.kt` (specifically the `AltitudeRow` sub-composable).
- **Scope**: Documentation and layout logic within `WorkoutDetails`.
- **Side Effects**: None. This is a visual-only layout swap.

## 3. Proposed Changes

### 3.1 Layout Swap (`WorkoutDetails.kt`)
- In `AltitudeRow`, locate the `Column` representing the section header.
- Move the `Text` component (displaying `R.string.elevation`) to be above the `Icon` component (`ic_altitude`).
- Adjust the `Spacer` to maintain the requested 4.dp gap between the two.

## 4. Verification Plan
- **Manual Audit**: Open the Workout Summary and verify the "Elevation" text sits on top of the mountain icon.
- **Compose Preview**: Audit `PreviewWorkoutDetails` in Android Studio to confirm the new layout hierarchy.
