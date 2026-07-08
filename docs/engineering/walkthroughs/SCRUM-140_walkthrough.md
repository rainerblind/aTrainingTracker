# Walkthrough: Sensor Source Auditing & Candidate Identification (SCRUM-140 / SCRUM-144)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-UI-049** | Tapping a sensor icon in the `SensorStatus` header SHALL display a dialog showing the source device (name, technical identity), current value with units, and battery status. This dialog SHALL be available for all icons, including inactive ones, to identify potential/configured sources. | Verified |
| **REQ-UI-050** | The `EditDeviceDialog` and `SensorSourceDialog` SHALL use a consistent three-row identification block: Row 1 (Icon + Name/LED), Row 2 (Icon + Battery/Status), and Row 3 (Left-aligned Manufacturer). | Verified |

## 2. Verification Evidence (TST-UI-049 / TST-UI-050)
*   **Procedure**:
    1. Started a session with an ANT+ HR strap. Tapped the active HR icon.
    2. Tapped an inactive Power icon (with a paired but disconnected meter).
    3. Tapped the Time icon.
*   **Observation**:
    *   **Active Sensor**: Showed "145 bpm" and the connected HR strap details.
    *   **Inactive Sensor**: Dialog appeared and correctly identified ALL paired candidates for that sensor type (e.g. multiple paired HR straps).
    *   **Internal Sensors**: Sensors like "Time" or "Altitude" (when no pressure sensor paired) correctly identify as "Internal Smartphone Sensors".
    *   **Layout Consistency**: Both `EditDeviceDialog` and `SensorSourceDialog` use the exact same 3-row identity block.
*   **Result**: **PASS**

## 3. Technical Changes
### BANALService Layer
*   `ProxySensor.java`: Added `getSourceDeviceId()` to track the underlying hardware source.
*   `BANALService.java`: Exposed `getSourceDeviceId(SensorType)` in `BANALServiceComm`.
*   `DeviceType.java`: Refined `getDeviceTypeList()` to include all technical sources (including `CLOCK` and `VERTICAL_SPEED_AND_SLOPE`).

### Repository & ViewModel
*   `BANALServiceRepository.kt`: Added 1Hz reactive mapping of `SensorType -> SourceDeviceId`.
*   `TrackingTabsViewModel.kt`: Exposed all devices and telemetry to the tracking cockpit.

### UI Components
*   `SensorStatus.kt`: Enabled interaction for all icons.
*   `SensorSourceDialog.kt`: 
    *   Implemented full candidate identification logic: now shows ALL paired devices that could provide the selected sensor type.
    *   Marks the currently active source with a green status LED.
    *   Standardized 3-row layout: Icon+Name/LED, Icon+Battery/Status, Manufacturer (below).
    *   Localized value display with units from `MyHelper`.
*   `EditDeviceDialog.kt`: Synchronized with the 3-row identity standard.
*   `SensorType.java`: Centralized icon resource mapping.
