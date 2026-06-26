# Implementation Plan - SCRUM-122: Structural Start-Alignment for Segment Items

Improve the visual organization of the Segment Item by ensuring a consistent horizontal starting point for metric values across different rows, while maintaining flexible row widths for large data values.

## 1. Requirements Mapping
- **Requirement**: `REQ-UI-020` (Consistent Vertical Metric Representation)
- **Test ID**: `TST-UI-030` (Metric Consistency & Structural Alignment)

## 2. Impact Analysis
- **UI Components**: `SegmentDetails.kt`.
- **Structural Integrity**: We replace flexible spacers with a fixed-width lead-in container for category icons.
- **Safety**: The data values (right side) remain in a standard horizontal `Row`, ensuring they can expand freely without causing awkward gaps if values are long.
- **Side Effects**: None. This is a layout-only refinement.

## 3. Proposed Changes

### 3.1 Fixed-Width Lead-in (`SegmentDetails.kt`)
- Define a shared constant for the lead-in width (e.g., `56.dp`).
- Wrap the leading icons of Row 2 (Grades) and Row 3 (Elevations) in a `Box` or `Row` with this fixed width.
- **Row 2**: Fixed-width container for `ic_grade`.
- **Row 3**: Fixed-width container for `ic_altitude` + `ic_ascent`.
- Result: The first numerical value in both rows will align perfectly at the `56.dp` mark.

### 3.2 Baseline Alignment
- Switch the vertical alignment of these rows from `CenterVertically` to `Bottom`.
- This ensures the technical data sits on a solid visual baseline, matching the professional look of the Workout Summary.

## 4. Verification Plan
- **Build**: Ensure successful compilation.
- **Visual Audit**:
    1. Verify that the '%' value (Grade) and the 'm' value (Gain) start at the exact same horizontal position.
    2. Verify that long elevation values do not break the row or cause misalignment in the columns to the right.
- **Compose Preview**: Check the "Segment Details" preview to confirm baseline and start-point precision.
