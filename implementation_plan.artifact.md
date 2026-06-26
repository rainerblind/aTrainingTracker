# Implementation Plan - SCRUM-125 Refinement: Centered Branding in Segment List

Center the "Powered by Strava" logo in the Segment list header to improve visual balance while maintaining subordinate scaling.

## 1. Requirements Mapping
- **Requirement**: `REQ-UI-026` (Consistent Branding Scaling)
- **Test ID**: `TST-UI-036` (Branding Audit)

## 2. Impact Analysis
- **UI Components**: `PoweredByStrava.kt`, `SegmentList.kt`.
- **Visuals**: Center-aligns the logo in headers where `fillMaxWidth` is applied.
- **Side Effects**: None. The change in default alignment within the `PoweredByStrava` container is safe for existing usages.

## 3. Proposed Changes

### 3.1 Alignment Refinement (`PoweredByStrava.kt`)
- Change the internal `Box` alignment from `CenterStart` to `Center`.
- This ensures that when the component is given `fillMaxWidth`, the logo is perfectly centered.

### 3.2 Spacing Adjustment (`SegmentList.kt`)
- Ensure the logo has consistent padding to feel integrated into the list flow.

## 4. Verification Plan
- **Build**: Ensure successful compilation.
- **Visual Audit**: Open Segment List and verify the top Strava logo is horizontally centered.
- **Regression Audit**: Verify that inline logos (e.g., in `SegmentDetails`) remain correctly positioned.
