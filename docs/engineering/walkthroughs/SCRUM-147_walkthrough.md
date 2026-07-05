# Walkthrough: Color-Coded Sensor Source Headers (SCRUM-147)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-UI-051** | The `SensorSourceDialog` SHALL use color-coded section headers to clearly distinguish between source roles: Dark Green for "Source Device", Light Green for "Active Backups", and Grey for "Not Connected Devices". | Verified |

## 2. Verification Evidence (TST-UI-052)
*   **Procedure**: Opened `SensorSourceDialog` for a sensor with active and standby sources.
*   **Observation**:
    *   "Source Device" header is displayed in Dark Green (`RouteColorSelected`).
    *   "Active Backups" header is displayed in Light Green (`RouteColorUnselected`).
    *   "Not connected devices" header is displayed in Grey (`onSurfaceVariant`).
*   **Result**: **PASS**

## 3. Technical Changes
### SensorSourceDialog.kt
*   Imported `RouteColorSelected` and `RouteColorUnselected` from the theme.
*   Applied the respective colors to the `Text` components serving as section headers.
*   Increased header typography scale to `titleSmall` with `FontWeight.Bold` for better visual prominence.
*   Maintained `onSurfaceVariant` for the "Not connected" and "No source configured" labels to ensure appropriate technical subordination.

### strings.xml (Global)
*   Refined section header strings:
    *   `source_device`: "Source"
    *   `source_active_backups`: "Active Backups"
    *   `source_not_connected`: "Not Connected"
*   Localized these technical identifiers across all supported languages (DE, FR, ES, IT, PT, NL, PL, JA) to ensure professional consistency for international users.
