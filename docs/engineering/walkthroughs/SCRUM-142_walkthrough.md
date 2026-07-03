# Walkthrough: Reactive Map Layer Updates (SCRUM-142)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-MAP-009** | The map SHALL immediately redraw and re-style polylines (Route, Segment, Track) when their underlying selection or visibility state changes in the UI. | Verified |

## 2. Verification Evidence (TST-UI-045)
*   **Procedure**: Opened Route Detail Map and toggled the "Active" switch in the header.
*   **Observation**: The route polyline immediately updated its color (Grey <-> Indigo), width (6dp <-> 10dp), and Z-index without requiring any map interaction (zooming or panning).
*   **Result**: **PASS**

## 3. Technical Changes
### ATrainingTrackerMap.kt
*   Added `content` (DSL lambda) as a key to the `remember` block for the `MapContentScopeImpl`.
*   Ensures that whenever the parent UI re-invokes the DSL (e.g., due to a state change in the route summary), the technical scope is recreated.

### MapContentScope.kt
*   Refactored `composables`, `tracks`, `segments`, `routes`, `markers`, and `currentTracks` to use `mutableStateListOf`.
*   This ensures that the `MapBoundsController` and the `Render()` function react instantly to items being added or cleared during the DSL collection phase.

### MapModels.kt
*   Annotated `MappablePath` with `@Stable`.
*   Annotated `MapTrack`, `MapSegment`, and `MapRoute` with `@Immutable`.
*   Optimizes technical redraws by allowing the Compose compiler to skip unnecessary recompositions.
