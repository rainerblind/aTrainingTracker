# Walkthrough: Selective Period Markers (SCRUM-154)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-UI-033** | The `PeriodMapScreen` SHALL display optional and selectable markers for the Start, End, Maximum Altitude, and Maximum Line Distance of each workout in the period. | Verified |

## 2. Verification Evidence (TST-UI-041)
*   **Procedure**:
    1. Open the Period Map for a Month or Year.
    2. Tap the **drop (Place)** icon on the floating action button (not in the header).
    3. Verify that a dropdown menu appears with localized "Max Altitude", "Max Distance", "Start", and "End" options.
    4. Toggle the options and verify that markers appear/disappear on the map in real-time.
*   **Observation**:
    *   Markers are correctly filtered based on user selection.
    *   The preference is persisted across application restarts.
*   **Result**: **PASS**

## 3. Technical Changes
### Data Layer (PeriodData.kt)
*   Introduced `PeriodMarkerType` enum (ALTITUDE, DISTANCE).
*   Added `markerType` property to `PeriodPeakMarker`.

### Logic Layer (PeriodsViewModel.kt)
*   Updated `aggregateToPeriod` to Assign correct types to markers.
*   Implemented polyline decoding to generate **Start** and **End** markers for each workout.
*   Implemented `enabledMarkerTypes` state flow backed by DataStore.

### UI Layer (PeriodsTabsScreen.kt / PeriodMapScreen.kt)
*   Removed marker configuration from the `PeriodsTabsScreen` header.
*   Implemented a `DropdownMenu` triggered by a **22dp Place (drop) icon** on the map's floating action button.
*   Localized all menu options across supported languages.
