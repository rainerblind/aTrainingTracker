# Walkthrough: Imperial Units Support in RouteClusters (SCRUM-177)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-SET-023** | Support imperial units in RouteClusters (Manual Creation and Tuning). | Verified |

## 2. Verification Evidence (TST-SET-015)
* **Manual Cluster Creation**:
    * Changed application units to **Imperial** in settings.
    * Navigated to **Favorite Tracks > Add (+)**.
    * Verified that the distance field hint changed to "Workout Distance (mile)".
    * Entered "6.2" miles.
    * Saved the cluster and verified it appears as roughly "10.00 km" (or "6.21 mile") in the list depending on current unit.
* **Cluster Tuning**:
    * Navigated to **Favorite Tracks > Tuning**.
    * Toggled **Show Detailed Parameters**.
    * Verified that the Endpoint and Apex tolerances are displayed in **feet (ft)**.
    * Switched back to **Metric** and verified they changed back to **meters (m)**.
* **Result**: **PASS**

## 3. Technical Changes
### UI & Logic
* **`ManualClusterScreen.kt`**:
    * Dynamically displays "mile" or "m" in the distance field hint based on `TrainingApplication.getUnit()`.
    * Automatically converts miles to meters before calling `viewModel.addManualCluster` if in imperial mode.
* **`ClusterTuningScreen.kt`**:
    * Added logic to display Endpoint and Apex tolerance sliders in **feet** if in imperial mode, using a `lengthMultiplier` and appropriate unit labels.
* **`strings.xml`**: Updated `cluster_manual_distance_hint` to support a string parameter for the unit name.
