# Implementation Plan: ANT+ Logo Transparency (SCRUM-56)

## 1. Goal
Ensure that the official ANT+ Alliance logos (which have white background corners) appear transparent in the UI, particularly for Dark Mode compatibility, without modifying the binary assets.

## 2. Requirement Mapping
* **REQ-UI-057**: The system SHALL programmatically clip ANT+ Alliance logos to remove white background corners.
* **Test ID**: TST-UI-063

## 3. Impact Analysis (SWE.1.BP.5)
* **Visual Logic**: Modifies `DeviceItem.kt` and `DevicesTabbedScreen.kt`.
* **Performance**: Negligible. Clipping is a standard hardware-accelerated operation in Compose.

## 4. Proposed Changes

### UI Layer (`DeviceItem.kt`)
* Modify the `Icon` for the device type.
* If `protocol == Protocol.ANT_PLUS`, apply `Modifier.clip(RoundedCornerShape(8.dp))` to mask the square white corners of the legacy PNG assets.
* This ensures that the rounded ANT+ logo is preserved while the white background at the corners of the square image is removed.

### UI Layer (`DevicesTabbedScreen.kt`)
* Apply similar clipping logic to the header icon when an ANT+ specific icon is displayed.

## 5. Verification Criteria (TST-UI-063)
* Open the app in **Dark Mode**.
* Navigate to the **ANT+ sensors** list.
* **Expected**: The ANT+ icons (HR, Speed, etc.) should appear with transparent corners, blending perfectly with the dark surface, rather than showing white corner artifacts.
