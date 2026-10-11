# Stage 4 Implementation Report: ATT-3105 - Normalize Climb popup elevation profile X-axis to climb distance instead of full route distance

**Ticket**: [ATT-3105](https://atrainingtracker.atlassian.net/browse/ATT-3105)  
**Sub-task**: [ATT-3146](https://atrainingtracker.atlassian.net/browse/ATT-3146) (`[Implementation]`)  
**Parent Epic**: [ATT-2565](https://atrainingtracker.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.8`  
**Branch**: `feature/ATT-3105`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Implementation Summary

Stage 4 (Software Construction & Implementation) for [ATT-3105](https://atrainingtracker.atlassian.net/browse/ATT-3105) has been executed in strict adherence to the approved Stage 3 Implementation Plan and `REQ-UI-338`.

### 1.1 Key Modifications

1. **`Climb.kt` - Normalized Path Points Representation**:
   - Added computed property `Climb.normalizedPathPoints` which offsets all `PathPoint` distances by `pathPoints.first().distance`.
   - Distances start at `0.0 m` and end at the local climb length ($d_{\text{summit}}$).
   - Preserves latitude, longitude, altitude, timestamps, and telemetry values strictly unmodified.
   - Handled boundary conditions: empty path returns empty list; already base-0 path returns the same instance without reallocation.
   - Preserves the raw `climb.pathPoints` holding the parent route offset.

2. **`ClimbOnMapScreen.kt` - Active Scrub Path Integration**:
   - Remembered `val normalizedClimbPath = remember(climb) { climb.normalizedPathPoints }`.
   - Passed `activeScrubPath = normalizedClimbPath` to `MapDetailLayout`.
   - Ensures the embedded `ElevationProfile` chart X-axis spans from $0.0\text{ m}$ to $d_{\text{summit}}$, utilizing 100% of the horizontal chart width.
   - Preserved `climb.pathPoints.firstOrNull()?.distance` in `ClimbHeader.kt` so the "Start at 27.4 km" route indicator remains accurate.

3. **`ClimbNormalizationTest.kt` - Unit Tests**:
   - Verified that route distances `[27380.0, 27700.0, 28102.0]` normalize to `[0.0, 320.0, 722.0]`.
   - Verified lat/lng/alt preservation and raw climb entity immutability.
   - Verified empty path points and already zero-base path points handling.
   - Verified scrubbing lookup via `TelemetryMetricUtils.findNearestPoint` on normalized points.

4. **`ClimbDetailSheetContractTest.kt` - Contract Tests**:
   - Added `testClimbOnMapScreen_normalizedPathIntegrationAndHeaderRouteOffset` verifying:
     - `ClimbOnMapScreen.kt` references `climb.normalizedPathPoints`.
     - `ClimbOnMapScreen.kt` wires `activeScrubPath = normalizedClimbPath`.
     - `ClimbHeader.kt` preserves `climb.pathPoints.firstOrNull()?.distance` and `routes_climb_start_at`.

---

## 2. Test Execution

* Executed targeted tests via `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.climbs.ClimbNormalizationTest" --tests "com.atrainingtracker.trainingtracker.ui.climbs.ClimbDetailSheetContractTest"`:
  ```
  BUILD SUCCESSFUL in 32s
  32 actionable tasks: 6 executed, 26 up-to-date
  ```
  All tests passed cleanly with 0 failures and 0 errors.

---

## 3. Invariants & Governance Verification

- Entity immutability preserved: raw `climb.pathPoints` untouched in memory and database.
- Coordinate integrity preserved: latitude, longitude, and elevation unaltered.
- 100% 9-language localization parity preserved.
- Code modularity: `ClimbOnMapScreen.kt` remains ~310 lines, below the 400-line threshold.
