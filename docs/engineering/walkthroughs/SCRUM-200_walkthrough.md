# Walkthrough: Intelligent Identity Propagation (SCRUM-200)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-SET-029** | Automatic selection of Equipment and Strava upload based on SportType. | Verified |

## 2. Verification Evidence (TST-SET-021)
* **Interaction**:
    * Navigated to **Edit Workout**.
    * Tapped the "My Locations" icon to see cluster suggestions.
    * Selected a cluster that is mapped to a specific Sport Type (e.g., "Cycling").
* **Observation**:
    * The **Workout Name** updated to the cluster's name.
    * The **Sport Type** updated to the cluster's probable sport.
    * The **Equipment** was automatically selected if only one piece of equipment is linked to that sport.
    * The **Strava Upload** checkbox was automatically enabled because the selected sport type has a valid Strava mapping.
* **Fancy Name Interaction**:
    * Selected a "Fancy Name" from the auto-name dialog.
    * Verified that the associated sport and equipment were propagated correctly.
* **Result**: **PASS**

## 3. Technical Changes
### Identity Arbitration & Conflict Resolution
The system implements a **Sensor-First Confidence Model** to decide between hardware and route patterns:
* **High Confidence (Hardware Wins)**: If a workout has exactly one piece of equipment linked to its active sensors (e.g. a specific bike), the system preserves that equipment and its linked sport type. The Workout Cluster provides the **Workout Name**, but the sport/gear defaults remain hardware-driven.
* **Low Confidence (Workout Wins)**: If no specific equipment is linked via sensors, the system corrects generic guesses by adopting the cluster's majority sport type, propagating its preferred gear and Strava settings.

### Data Layer Logic
* **`EquipmentAndSportTypeDiscoveryManager.kt`**:
    * Implemented `resolveIdentity()`: Analyzes active sensors to determine if a unique piece of equipment is linked (Approach 1). Returns an `InferredIdentity` with a `isHighConfidence` flag.
* **`WorkoutSummariesDatabaseManager.java`**:
    * Added `applyInferredIdentity()`: Performs atomic database updates for Sport, Gear, and Strava settings.
* **`WorkoutClusterEngine.kt`**:
    * Upgraded `assignClusterToWorkout()` to perform the arbitration logic. It adopt names from clusters but respects high-confidence hardware signals for sport/gear classification.
* **`TrackerService.java`**:
    * Integrated the determination flow into `finalizeLiveSession()`. It now performs Approach 1 (Hardware) followed by Approach 2 (Workout Cluster) with the new arbitration rules.

### Repository & UI
* **`WorkoutDataMapper.kt`**: Removed virtual overrides. UI now purely reflects authoritative data stored by the background service.
* **`WorkoutRepository.kt`**: Orchestrates high-level actions (`assignClusterToWorkout`, `applyFancyNameToWorkout`) and handles data reloading.
* **`EditWorkoutViewModel.kt`**: Simplified to delegate all determination logic to the core data layer.
