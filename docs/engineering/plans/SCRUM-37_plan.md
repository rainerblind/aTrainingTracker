# Implementation Plan - SCRUM-37: Modernize Device Management UI

Migrate the entire device management stack (Tabs, List, and Edit Dialogs) from legacy XML/RecyclerView to Jetpack Compose, using the unified design system.

## 1. Requirements Mapping
- **Requirement**: `REQ-UI-041` (Composable Device Management)
- **Requirement**: `REQ-UI-042` (Mappable Device Items)
- **Requirement**: `REQ-UI-043` (Composable Edit Dialogs)
- **Test ID**: `TST-UI-043` (Device Management Modernization Audit)

## 2. Impact Analysis
- **UI Architecture**: Complete removal of `ListDeviceFragment`, `DevicesTabbedContainerFragment`, and the `EditDeviceFragment` hierarchy in favor of Composables.
- **Components**: Replaces `FragmentDeviceListBinding`, `ItemDeviceListBinding`, and all associated XML layouts.
- **Design System**: Leverages `MappableListItem` to ensure device list cards match the rest of the application.
- **Risk**: High (Core sensor management path). 
- **Side Effects**: None expected on the underlying sensor logic (`BANALService` / `DeviceDataRepository`), as we are only replacing the View layer.

## 3. Proposed Changes

### 3.1 Composable Device Items (`DeviceItem.kt`)
- Create a new Composable `DeviceItem` using `MappableListItem`.
- **Layout**:
    - **Header**: Device Name (Headline), Manufacturer (Subordinate).
    - **Leading**: Device Type Icon (Large, consistent with current 60dp).
    - **Content**: 
        - Primary Value (e.g. HR, Speed) with units.
        - Linked Equipment list.
        - Availability Status (Available/Seen Date).
    - **Trailing/Actions**: 
        - Battery Indicator.
        - Outlined "Pair/Unpair" Button.

### 3.2 Composable List & Header (`DeviceListScreen.kt`)
- Implement a `LazyColumn` for the devices.
- Include a "Searching..." header component that displays when active, using the application's standard search indicators.

### 3.3 Composable Tabbed Container (`DevicesTabbedScreen.kt`)
- Implement a `HorizontalPager` with a `ScrollableTabRow`.
- Mirror the current logic: "All", "ANT+", "Bluetooth", and specialized categories.

### 3.4 Composable Edit Dialog (`EditDeviceDialog.kt`)
- Replace the legacy fragment-based dialogs with a unified Composable Dialog.
- Support specialized fields:
    - Name editing.
    - Calibration factor adjustment.
    - Wheel circumference selection.
    - Linked equipment management.

## 4. Verification Plan
- **Build**: Ensure successful compilation of the new Compose modules.
- **Functional Audit**:
    1. Open Pairing screen and verify all tabs load.
    2. Toggle pairing on multiple devices.
    3. Open Edit Dialog for different device types (Bike, HRM, etc.).
    4. Verify changes persist to the database.
- **Visual Audit**:
    1. Compare against `WorkoutSummary` and `RouteItem` to ensure card consistency.
    2. Verify high-density information display.
