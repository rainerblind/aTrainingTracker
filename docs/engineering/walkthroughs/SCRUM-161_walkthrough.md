# Walkthrough: Protocol-Aware Troubleshooting Menu (SCRUM-161)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-UI-044** | Technical troubleshooting options SHALL only be displayed when the relevant communication protocol is active in the current view. | Verified |

## 2. Verification Evidence (TST-UI-062)
* **Procedure**:
    1. Opened the Sensor Management screen.
    2. Selected the "Bluetooth" filter.
    3. Checked the header for the options menu (three-dots icon).
    4. Selected the "ANT+" filter.
    5. Checked the header for the options menu.
* **Observation**:
    * In the Bluetooth-only view, the options menu icon was completely hidden.
    * In the ANT+ and "All Sensors" views, the options menu icon was visible and contained the "Check ANT+ Installation" option.
* **Result**: **PASS**

## 3. Technical Changes
### UI Layer (`DevicesTabbedScreen.kt`)
* Implemented a conditional check for the `protocol` state.
* The options menu `Box` is now conditionally rendered only if `protocol == Protocol.ANT_PLUS` or `protocol == Protocol.ALL`.
* This prevents user confusion by hiding ANT+-specific tools when auditing Bluetooth sensors.
