# Implementation Plan - SCRUM-37 Refinement: High-Density Device Items

Refine the `DeviceItem` layout to match the visual language of the Segment and Route lists, removing the divider and prioritizing the availability indicator on the left.

## 1. Requirements Mapping
- **Requirement**: `REQ-UI-042` (Mappable Device Items)
- **Test ID**: `TST-UI-044` (Device Item Visual Consistency)

## 2. Impact Analysis
- **UI Component**: `DeviceItem.kt`.
- **Logic**: No changes.
- **Side Effects**: None. This is a pure layout refinement.

## 3. Proposed Changes

### 3.1 Refined Layout (`DeviceItem.kt`)
- **Remove `HorizontalDivider`**.
- **Integrated Identity Row**:
    - Leading: Device Type Icon (**32dp**).
    - Body: 
        - Name (**`titleLarge`**).
        - Sub-row: **Availability Icon** (**14dp**) followed by Manufacturer name and Last Seen date.
- **Metrics Footer**:
    - Left: Linked Equipment list.
    - Right: Battery Status Icon and the **Prominent Live Value** (**`headlineSmall`**, ExtraBold).
- **Control**: Position the pairing **Switch** as a subordinate toggle in the top-right or bottom-right to minimize whitespace.

## 4. Verification Plan
- **Visual Audit**: 
    1. Verify no horizontal line is present.
    2. Verify the availability icon (green/red dot) is on the left side of the metadata.
    3. Confirm the overall look feels "Mappable" (consistent with Route/Segment cards).
