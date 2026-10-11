# ATT-3102 Walkthrough: Unified Viewport Anchor Authority & Lookahead Camera Targeting

## Overview
- **Issue**: [ATT-3102](https://atrainingtracker.atlassian.net/browse/ATT-3102)
- **Requirement**: `REQ-MAP-046`
- **Test Specification**: `TST-MAP-048`
- **Sprint**: `2026-41.8`
- **Status**: Verified

## Problem Addressed
During active workout tracking with the map enabled and `MapZoomFocus.FOLLOW_ME` active, the map camera continuously alternated/jittered between the screen center (50% Y) and the bottom forward-lookahead position (~65% Y) at a 1 Hz frequency (coinciding with location update emissions).

Forensic investigation revealed a fundamental architectural violation of **Rule 33 (Unified Viewport Anchor Authority for Map Tracking)**:
1. `CameraPosition.builder().target(loc)` in the Google Maps Android SDK places `target` at the **unpadded physical screen center (50% Y)**.
2. In Sprint 2026-41.2 (`ATT-3054`), `GoogleMap(contentPadding = PaddingValues(top = topPadding))` was introduced to shift the visible center downward to the lower third.
3. Every 1 second (1 Hz GPS update), `followMeController` called `cameraPositionState.animate(newCameraPosition(target = currentLocation))`, pulling the camera to the physical center, while the native Google Maps contentPadding mechanism shifted it back to the lower third. This created a persistent 1 Hz vertical oscillation loop.

## Architectural Changes & Solution

1. **Decoupled GoogleMap Content Padding (`ATrainingTrackerMap.kt`)**:
   - In `ATrainingTrackerMap.kt`, removed the competing `topPadding` from `GoogleMap(contentPadding = ...)`. For `FOLLOW_ME` mode, `contentPadding` is strictly `PaddingValues(0.dp)` (or standard edge paddings), eliminating competing viewport shifting at the native map surface level.
   - Forwarded `lookaheadPaddingPercent = tuningConfig.mapFollowMeLookaheadPaddingPercent` and `viewportHeightDp = maxHeight.value` into `followMeController`.

2. **Analytical Forward Lookahead Calculation (`MapBehaviors.kt`)**:
   - Implemented `calculateFollowMeCameraTarget(currentLocation, bearing, zoomLevel, lookaheadPaddingPercent, viewportHeightDp)`:
     - Calculates vertical pixel displacement: $D_{\text{pixels}} = \text{viewportHeightDp} \times \frac{\text{lookaheadPaddingPercent}}{100.0}$.
     - Determines Web Mercator ground resolution in meters/pixel at latitude:
       $$\text{metersPerPixel} = \frac{156543.03392 \times \cos(\text{lat}_{\text{rad}})}{2^{\text{zoomLevel}}}$$
     - Calculates required lookahead ground offset: $D_{\text{meters}} = D_{\text{pixels}} \times \text{metersPerPixel}$.
     - Applies spherical offset via `SphericalUtil.computeOffset(currentLocation, D_{\text{meters}}, bearing)` to calculate the new focal target.
   - When lookahead is disabled (`lookaheadPaddingPercent == 0f`) or viewport height is unavailable (`viewportHeightDp <= 0f`), it safely returns `currentLocation` unchanged.

3. **Single Spatial Authority in `followMeController`**:
   - In `followMeController`, camera updates compute `targetPosition = calculateFollowMeCameraTarget(targetLocation, filteredBearing, targetZoom, lookaheadPaddingPercent, viewportHeightDp)`.
   - The camera position is animated directly to `targetPosition` with zero competing native padding adjustments, maintaining a rock-solid lower-third anchor for the athlete.

4. **Unit & Contract Testing**:
   - `FollowMeCameraTargetTest.kt` (`TST-MAP-048.1`):
     - Verified target projection along bearing.
     - Verified zero-padding and zero-height invariant returns `currentLocation`.
     - Verified inverse scaling with zoom level ($2^Z$) and latitude cosine.
     - Verified cardinal bearing rotations (0°, 90°, 180°, 270°).
   - `FollowMeUnifiedAnchorContractTest.kt` (`TST-MAP-048.2`):
     - Verified `ATrainingTrackerMap.kt` passes zero padding to `GoogleMap` in `FOLLOW_ME` mode.
     - Verified lookahead percentage and viewport height are piped to `followMeController`.
     - Verified `followMeController` delegates target calculation to `calculateFollowMeCameraTarget`.
   - `FollowMePaddingContractTest.kt`: Updated to assert unified anchor authority.

## Verification & Test Results
- **Targeted Test Suite**:
  - `FollowMeCameraTargetTest`: 100% pass (5 tests).
  - `FollowMeUnifiedAnchorContractTest`: 100% pass (3 tests).
  - `FollowMePaddingContractTest`: 100% pass (2 tests).
- **Clean-Room Full Suite Regression**:
  - `./gradlew testDebugUnitTest` executed with 100% pass rate across all 32 tasks, 0 failures, 0 regressions (execution time: 2m 30s).

## ASPICE Traceability
- **Requirement**: `REQ-MAP-046` in `docs/requirements.md` (Verified)
- **Test Specification**: `TST-MAP-048` in `docs/tests.md` (Verified)
- **Implementation Plan**: `docs/engineering/plans/ATT-3102_plan.md`
- **Analysis**: `docs/engineering/analysis/ATT-3102_analysis.md`
