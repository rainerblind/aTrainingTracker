# Walkthrough: Markers in Previews (SCRUM-233)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-SET-042** | The system SHALL display Start, End, and Apex markers in mini-map previews. | Verified |

## 2. Verification Evidence (TST-SET-032)
* **Interaction**:
    * Opened the **Workout History** list.
    * Opened the **Routes** list.
* **Observation**:
    * Workout mini-maps now show the unified technical markers (Green Start, Red Stop, Blue Apex).
    * Route mini-maps show Start and End markers only.
    * Workouts show the apex as stored in the database.
    * Markers are correctly scaled and centered.
* **Result**: **PASS**

## 3. Technical Changes
### Shared Preview Map
* **`PathPreviewMap.kt`**: 
    * Updated to accept optional `start`, `end`, and `apex` LatLng parameters.
    * Integrated unified technical markers into the preview layer.
    * Updated `LaunchedEffect` to include markers in the automatic bounds fitting, ensuring they are not cut off at the edges.

### UI Integration
* **`WorkoutSummary.kt`**: Updated `WorkoutMediaSection` to pass the stored spatial signature (Start, End, Max Displacement) to the preview map.
* **`RouteItem.kt`**: Updated the route preview to pass start/end points.

## 4. Final Review
The enhancement brings professional-grade spatial context to the list views. Athletes can now identify routes not just by their shape, but by their orientation and furthest reaching points directly from the history and route lists.
