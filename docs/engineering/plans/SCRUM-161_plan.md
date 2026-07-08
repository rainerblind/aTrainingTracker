# Implementation Plan: Protocol-Aware Troubleshooting Menu (SCRUM-161)

## 1. Goal
Ensure that the "Check ANT+ Installation" troubleshooting option is only displayed when the user is viewing ANT+ related sensor screens.

## 2. Requirement Mapping
* **REQ-UI-044**: Technical troubleshooting options SHALL only be displayed when relevant.
* **Test ID**: TST-UI-062

## 3. Impact Analysis (SWE.1.BP.5)
* **Visual Logic**: Minor change to `DevicesTabbedScreen.kt` to wrap the options menu in a conditional block.
* **Architecture**: No impact on data flow or background services.

## 4. Proposed Changes

### UI Layer (`DevicesTabbedScreen.kt`)
* Wrap the options menu `Box` in a conditional check: `if (protocol == Protocol.ANT_PLUS || protocol == Protocol.ALL)`.
* This ensures that when viewing Bluetooth-only sensors, the menu (and its ANT+-specific content) is not shown.

## 5. Verification Criteria (TST-UI-062)
* Open the Sensor Management screen.
* Navigate to the **Bluetooth** tab.
* **Expected**: No "Check ANT+ Installation" menu should be visible.
* Navigate to the **ANT+** tab.
* **Expected**: The "Check ANT+ Installation" menu should be visible.
