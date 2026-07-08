# Walkthrough: Multi-Track Visualization (SCRUM-152)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-MAP-011** | The Workout Details map SHALL support technical comparison of GPS, Fused, and Network tracks. | Verified |
| **REQ-MAP-012** | The interface SHALL provide a FAB with a selection menu and color legend. | Verified |
| **REQ-MAP-013** | User track visibility preferences SHALL be persisted across sessions. | Verified |

## 2. Verification Evidence (TST-UI-055, TST-UI-056)
*   **Procedure**:
    1. Opened the Workout Details screen for a recorded activity.
    2. Tapped the "Layers" FAB.
    3. Toggled "GPS" and "Google Fused" tracks.
    4. Closed the screen and reopened it.
*   **Observation**:
    *   The menu correctly identifies the primary path as **"Tracked"** (EN) / **"Aufgezeichnet"** (DE).
    *   Unavailable sources for the specific workout are automatically muted (disabled) in the menu.
    *   Selected technical tracks are rendered on top with 0.8 alpha for comparison.
    *   The map remained fluid during toggling due to aggressive path simplification (~1000 points).
    *   The track selection was perfectly preserved upon reopening the workout.
*   **Result**: **PASS**

## 3. Technical Implementation
### Path Optimization
*   Implemented an iterative Douglas-Peucker simplification loop in `TrackOnMapAftermathViewModel.kt`.
*   Capped high-resolution tracks at ~1000 points to ensure instantaneous UI reaction during source comparison.

### Preference Integration
*   Extended `MyPreferenceManager.kt` with `ENABLED_TRACK_TYPES` string-set persistence.
*   Synchronized the map screen selection with the global data store to maintain consistent auditing state.

### UI Architecture
*   Refactored `MapContentScope.kt` to a data-driven rendering model, eliminating interaction lag.
*   Updated `TrackOnMapScreen.kt` to dynamically muted non-existent data sources based on database availability.
