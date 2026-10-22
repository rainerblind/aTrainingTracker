# Stage 5 Walkthrough - ATT-3494: Improve turn cue detection algorithm and curvature classification along route polylines

**Ticket**: [ATT-3494](https://atrainingtracker.atlassian.net/browse/ATT-3494)  
**Stage 5 Subtask**: [ATT-3501](https://atrainingtracker.atlassian.net/browse/ATT-3501) (`[Test]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `Sprint 2026-42.3`  
**Active Sprint**: `Sprint 2026-42.3`  
**Requirement Mapping**: `REQ-MAP-045` (`Verified`)  
**Test Mapping**: `TST-MAP-045` (`Verified`)  
**Branch**: `feature/ATT-3494`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Executive Summary & Root Cause

In `aTrainingTracker`, turn-by-turn navigation cues visually and auditorily guide athletes along recorded or imported GPX routes (`TurnCueDetector.kt`, `TurnByTurnNavigationEngine.kt`). Prior to ATT-3494, the geometric turn detection engine suffered from four fundamental architectural deficiencies:
1. **Fixed-Window Blind Spots**: It relied on a single fixed 15-meter lookaround chord (`WINDOW_LOOKAROUND_METERS = 15.0m`). Gradual, sweeping 90-degree curves or highway ramps distributed across 30m–80m had point-to-point chord changes below the 30° detection threshold, completely missing genuine 90° turns.
2. **False Cues on Curvy / Winding Roads**: The slight-turn threshold was set too loose (30°) with no spatial trajectory integration, triggering spurious "Halb rechts" / "Halb links" turn prompts on natural country roads where athletes should stay on the path.
3. **Sub-Optimal Apex Localization**: Turn cues were anchored at arbitrary interior trackpoints rather than the curvature apex, leading to misleading countdown distances.
4. **Boundary & Noise Vulnerabilities**: Stationary duplicate trackpoints caused zero-length division or bearing NaN spikes, while GPS jitter at start boundaries produced phantom cues.

ATT-3494 resolves these issues with an end-to-end ASPICE SWE.3/SWE.4 redesign:
- **Polyline Pre-Sanitization & Multipath Spike Filter**: De-duplicates stationary coordinates (< 0.5m) and filters acute multipath reversals (> 165° over < 5m) in linear $O(N)$ time.
- **Dual-Window Multi-Scale Curvature Analysis**: Employs short (10m) and long (35m) forward/backward lookaround chords advancing via strictly monotonic pointers in $O(N)$ time. Short windows capture localized intersection corners, while long windows detect sweeping 90° curves.
- **Continuous Cluster Apex Localization with Midpoint Tie-Breaking**: Groups contiguous deflection vertices into maneuvers, selecting the vertex of maximum curvature (with exact center tie-breaking on constant-radius arcs).
- **Departure & Finish Boundary Clamping**: Pins immediate departure turns within $[0, 10\text{m}]$ to $0.0\text{m}$ for instant engine activation, and suppresses finish cues within 10m of the destination.
- **Junction Conflict Resolution & Hysteresis**: Enforces minimum 30m separation for same-direction turns (with non-downgrading severity hysteresis) and 15m for chicane S-curves.
- **Calibrated Direction Thresholds**: Straight (< 25°), Slight (25°–59°), Regular (60°–119°), Sharp (120°–154°), U-Turn (155°–180°).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MAP-045` (*Turn-by-Turn Navigation Geometric Turn Cue Detection, Multi-Scale Curvature Analysis, Apex Localization, and Threshold Calibration*) -> **Status: Verified**
* **Test Mapping**: `TST-MAP-045` (*Geometric Turn Cue Detection, Multi-Scale Curvature Analysis, Apex Localization, and Threshold Calibration Verification*) -> **Status: Verified**

---

## 3. Preserved Invariants & Invariant Verification

1. **Pure Geodesic Mathematical Autonomy**: All calculations in `TurnCueDetector.kt` use pure geodesic geometry (`RouteAutoDetector.distanceBetweenMeters`, `computeBearing`, `computeSignedAngleDelta`) without Android framework dependencies, ensuring fast execution and 100% JVM unit testability.
2. **Deterministic Linear $O(N)$ Monotonicity**: Window lookaround pointers (`prevShort`, `nextShort`, `prevLong`, `nextLong`) only increment forward, guaranteeing $O(1)$ amortized cost per vertex and $< 20\text{ms}$ execution for 5,000 trackpoints.
3. **TurnByTurnNavigationEngine Compatibility**: Start-of-route departure turns within $[0, 10\text{m}]$ are pinned to `distanceFromStart = 0.0m` and coordinate `path.first().latLng`, seamlessly activating immediate guidance (`isApproaching = true`, `isTurnNow = true`) without race conditions.
4. **Zero Full-Suite Regressions**: Full clean-room test suite `./gradlew testDebugUnitTest` executed with 100% pass rate across the entire codebase.

---

## 4. Test Verification Results

### Unit Test Suite: `TurnCueDetectorTest.kt`
All 21 unit tests in `TurnCueDetectorTest` pass cleanly:

| Test Case | Description | Result |
| :--- | :--- | :--- |
| `testTST_MAP_045_1_sharp90DegreeCorner` | 50m North, 90° Right, 50m East; verifies apex within ±5m of 50m | **PASS** |
| `testTST_MAP_045_2_sweeping90DegreeCurveOver45Meters` | 30m North, 45m curve turning 90° Right, 30m East; verifies sweeping cue | **PASS** |
| `testTST_MAP_045_3_windingRoadGentleBendsSuppressed` | 120m road with alternating 10° bends (< 20° total); verifies 0 cues | **PASS** |
| `testTST_MAP_045_4_sweepingCircularArcMidpointTieBreaking` | 30m straight, 30m circular arc (15°/seg), 30m straight; verifies apex near 45m | **PASS** |
| `testTST_MAP_045_5_hairpinSwitchbackConsolidatedToSingleUTurn` | 40m North, sharp 180° switchback, 40m South; verifies single U_TURN cue | **PASS** |
| `testTST_MAP_045_6_highFrequencyGpsJitterFiltered` | 100m straight road with ±3m lateral jitter every 5m; verifies 0 cues | **PASS** |
| `testTST_MAP_045_7_shortRouteSafelyReturnsEmptyList` | Short route (< 20m); safely returns empty list without error | **PASS** |
| `testTST_MAP_045_8_fiveThousandPointBenchmarkUnder100Ms` | 5,000 points synthetic polyline; completes in < 25ms (< 100ms budget) | **PASS** |
| `testTST_MAP_045_9_chicaneSeparationPreservesBothCues` | 15m chicane transition (Left at 30m, Right at 45m); preserves both cues | **PASS** |
| `testTST_MAP_045_10_boundaryConditionAtExactly20Meters` | 20m straight route; safely returns empty list | **PASS** |
| `testTST_MAP_045_11_straightRouteContinuousLateralJitterZeroCues` | Continuous ±2m jitter along straight route; produces 0 cues | **PASS** |
| `testTST_MAP_045_12_zeroLengthDuplicateSegmentsSanitizeCleanly` | Consecutive duplicate coordinates (< 0.5m); sanitized cleanly | **PASS** |
| `testTST_MAP_045_13_thresholdOscillationSuppressesDuplicateCues` | 26° turn with subtle 24.9°/25.1° oscillation; single SLIGHT_RIGHT cue | **PASS** |
| `testTST_MAP_045_14_highFrequencyLateralNoiseZeroCues` | High-frequency ±1.8m noise; produces 0 cues | **PASS** |
| `testTST_MAP_045_15_closedLoopRouteStartFinishBoundaryProtection` | 30m square closed loop; exactly 3 cues (30m, 60m, 90m), finish suppressed | **PASS** |
| `testTST_MAP_045_16_multipathReboundSpikeSanitized` | Acute multipath spike (> 165° reversal, 2m leg); cleanly rejected | **PASS** |
| `testTST_MAP_045_17_immediateStartOfRouteTurnClampedToZero` | 90° turn at 2m departure; clamped to 0.0m with RIGHT direction | **PASS** |
| `testTST_MAP_045_18_thresholdOscillationHysteresisBuffer` | 30° turn with +2° variation; retains initial apex via 5° hysteresis buffer | **PASS** |
| `detectGeometricCues_90DegreeRightTurn_detectsRightTurn` | Legacy regression test: 90° right turn | **PASS** |
| `detectGeometricCues_90DegreeLeftTurn_detectsLeftTurn` | Legacy regression test: 90° left turn | **PASS** |
| `classifyTurn_thresholdsMatchSpecification` | Angular threshold unit tests across all directions | **PASS** |

### Clean-Room Regression Run
* Command: `./gradlew testDebugUnitTest`
* Duration: 2m 36s
* Result: **100% Tests Passed** (0 failures, 0 errors, 0 regressions across all modules).
