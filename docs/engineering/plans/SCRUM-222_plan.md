# Implementation Plan: Stable Cluster Naming (SCRUM-222)

## 1. Problem Statement
When a workout is matched to a `WorkoutCluster`, it is often automatically named using the format `<cluster name> #<counter>`. When the user later edits this workout or when the system migrates history, the `WorkoutClusterEngine` adopts this auto-generated name as the new permanent name for the cluster, leading to "name pollution" (e.g., a cluster named "Park Loop #2").

## 2. Requirement Mapping
| Requirement ID | Component | Test ID | Description |
|:---|:---|:---|:---|
| **REQ-SET-034** | `WorkoutClusterEngine` | **TST-SET-025** | The system SHALL maintain the base name of a Workout Cluster when assigning it to a workout. |

## 3. Impact Analysis
* **Component: `WorkoutClusterEngine`**:
    * Must be updated to strip hit counters from workout names before using them as cluster name candidates.
    * No change to public interfaces.
* **Data Integrity**: 
    * Positive impact: prevents the cluster name from drifting over time.
    * Existing "polluted" names can be fixed by triggering a full recalculation.

## 4. Proposed Changes

### `WorkoutClusterEngine.kt`
* Refactor `normalizeName` to handle case-sensitive and case-insensitive stripping of both `#\d+` (hit count) and ` var \d+` (versioning).
* Create a dedicated `stripHitCount(name: String)` helper that ONLY strips the hit count `#\d+`.
* Update `learnFromWorkout`:
    * Apply `stripHitCount` to the `userSpecifiedName` before creating or updating a cluster name.
* Update `migrateHistory`:
    * Apply `stripHitCount` to the `workoutName` before using it as a cluster name candidate.

## 5. Verification Plan
### Manual Verification (TST-SET-025)
1.  **Preparation**: Create a cluster named "Morning Run".
2.  **Tracking**: Complete a workout that matches this cluster. 
3.  **Observation**: 
    *   Workout name should be "Morning Run #1" (or similar).
    *   Navigate to **Regular Tracks**.
    *   Verify the cluster name is still "Morning Run".
4.  **Edit**: Edit the workout in History and change name to "Sunny Morning Run #42".
5.  **Observation**: 
    *   Workout name is saved as "Sunny Morning Run #42".
    *   Navigate to **Regular Tracks**.
    *   Verify the cluster name is "Sunny Morning Run" (stripped of #42).
