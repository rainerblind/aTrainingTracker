# ATT-3105 Walkthrough: Normalize Climb Popup Elevation Profile X-Axis to Climb Distance

## Overview
- **Issue**: [ATT-3105](https://atrainingtracker.atlassian.net/browse/ATT-3105)
- **Requirement**: `REQ-UI-338`
- **Test Specification**: `TST-UI-298`
- **Sprint**: `2026-41.8`
- **Status**: Verified

## Problem Addressed
During Sprint 2026-41.7 Joint Review on Google Pixel 10 (documented in evidence attachment `climb_elevation_profile_progress_bug.png`), inspecting an individual climb in the Climb detail popup (`ClimbDetailSheet.kt` hosting `ClimbOnMapScreen.kt`) revealed that the elevation profile chart displayed global parent route coordinates (e.g. 0 to 28.10 km) rather than local climb distance (e.g. 0 to 722 m).

For a 722 m climb beginning at km 27.38 of a 28.10 km route:
- The X-axis displayed 0, 5, 10, 15, 20, 28.10 km.
- The climb profile was compressed into a tiny sliver at the far right edge (between 97.4% and 100% of the horizontal canvas).
- Approximately 97% of the chart area was left completely blank.
- The interactive scrubber was nearly impossible to use over the tiny 3% width.

Forensic investigation revealed:
- `ClimbDetector.kt` extracts climbs from parent routes where each `PathPoint` retains its cumulative parent route distance (`p.distance`), e.g., 27,380 m to 28,102 m.
- `ClimbOnMapScreen.kt` passed `climb.pathPoints` directly to `MapDetailLayout` as `activeScrubPath`.
- `ElevationProfile` computes chart bounds from `0.0` to `pathPoints.last().distance`, causing the compression.

## Architectural Changes & Solution

1. **Normalized Path Property on `Climb` (`Climb.kt`)**:
   - Added computed property `Climb.normalizedPathPoints`:
     ```kotlin
     val normalizedPathPoints: List<PathPoint>
         get() {
             if (pathPoints.isEmpty()) return emptyList()
             val startDist = pathPoints.first().distance
             if (startDist == 0.0) return pathPoints
             return pathPoints.map { it.copy(distance = (it.distance - startDist).coerceAtLeast(0.0)) }
         }
     ```
   - Offsets distances so the first point is at `0.0 m` and the final point is at the climb length ($d_{\text{summit}}$).
   - Preserves latitude, longitude, altitude, timestamps, and telemetry values strictly unmodified.
   - Raw `climb.pathPoints` remains immutable, preserving route context for header and database storage.

2. **Active Scrub Path Normalization in `ClimbOnMapScreen.kt`**:
   - Remembered `val normalizedClimbPath = remember(climb) { climb.normalizedPathPoints }`.
   - Supplied `activeScrubPath = normalizedClimbPath` to `MapDetailLayout`.
   - The embedded `ElevationProfile` chart now spans from base ($0.0\text{ m}$) to summit ($d_{\text{summit}}$), utilizing 100% of the horizontal chart width.
   - Preserved `climb.pathPoints.firstOrNull()?.distance` in `ClimbHeader.kt` so the "Start at 27.4 km" badge remains accurate.

3. **Interactive Scrubbing & Map Pin Synchronization**:
   - Scrubbing along the normalized elevation profile ($0.0 \le s \le d_{\text{summit}}$) resolves cleanly via `TelemetryMetricUtils.findNearestPoint` on `normalizedClimbPath`.
   - Map marker position is derived via `activeScrubPoint.latLng`, smoothly tracking along the physical climb polyline from start to summit.

4. **Unit & Contract Tests**:
   - **`ClimbNormalizationTest.kt`**:
     - `testNormalizedPathPoints_shiftsDistancesToBaseZero`: Validates distance shift [27380.0, 27700.0, 28102.0] -> [0.0, 320.0, 722.0] and lat/lng/alt integrity.
     - `testNormalizedPathPoints_emptyListReturnsEmpty`: Verifies empty list safety.
     - `testNormalizedPathPoints_alreadyZeroBaseReturnsSameList`: Verifies no-op identity when already base-0.
     - `testScrubbingLookup_findNearestPointResolvesCorrectCoordinates`: Verifies nearest point resolution at local distances (10m, 320m, 700m).
   - **`ClimbDetailSheetContractTest.kt`**:
     - `testClimbOnMapScreen_normalizedPathIntegrationAndHeaderRouteOffset`: Validates `activeScrubPath = normalizedClimbPath` and `routes_climb_start_at` retention.

## Verification & Test Results
- **Targeted Test Suite**:
  - `ClimbNormalizationTest`: 100% pass across all 4 test cases.
  - `ClimbDetailSheetContractTest`: 100% pass across all 4 test cases (`BUILD SUCCESSFUL in 32s`).
- **Clean-Room Full Suite Regression**:
  - `./gradlew testDebugUnitTest` executed with 100% pass rate across all modules (zero failures, zero regressions).

## ASPICE Traceability
- **Requirement**: `REQ-UI-338` in `docs/requirements.md` (Verified)
- **Test Specification**: `TST-UI-298` in `docs/tests.md` (Verified)
- **Implementation Plan**: `docs/engineering/plans/ATT-3105_plan.md`
- **Analysis**: `docs/engineering/analysis/ATT-3105_analysis.md`
