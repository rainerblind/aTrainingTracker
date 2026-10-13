# Stage 3: Implementation Plan - ATT-3174: Linearly interpolate sparse path points on Climb elevation profile and live progress metrics

**Ticket**: [ATT-3174](https://atrainingtracker.atlassian.net/browse/ATT-3174)  
**Sub-task**: [ATT-3213](https://atrainingtracker.atlassian.net/browse/ATT-3213) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2464](https://atrainingtracker.atlassian.net/browse/ATT-2464) (*ClimbPro Live Climb Engine*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.9`  
**Requirement Mapping**: `REQ-MAP-047`  
**Test Mapping**: `TST-MAP-049`  
**Branch**: `feature/ATT-3174`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Problem Description & Background

In `LiveClimbsRepository.kt` (`processLocation()`), in-ride telemetry progress and rider pin positioning along an active climb previously snapped to a single nearest discrete trackpoint (`path[closestIdx]`).
Between sparse trackpoints (50m–200m apart on straight roads or imported GPX tracks):
1. The rider pin on `LiveClimbSheet.kt` remained frozen at a fixed X coordinate and then abruptly jumped forward upon crossing the halfway Voronoi boundary between vertices.
2. In-ride telemetry metrics (`completedDist`, `remainingDist`, `currentAlt`, `remainingGain`, `progressFraction`, and `currentGradePercent`) remained static over long stretches, jumping discontinuously in coarse steps.
3. Athletes riding on-road between two distant vertices (e.g. 180m apart) could approach or exceed the 100m off-route threshold (`CLIMB_OFF_ROUTE_THRESHOLD_METERS`) because distance was measured to the nearest discrete vertex rather than the road segment.

`REQ-MAP-047` operationalizes `docs/design_guidelines.md` §2.4 by projecting the athlete's current location orthogonally onto the nearest line segment $[P_i, P_{i+1}]$, calculating fractional parameter $t \in [0.0, 1.0]$, and continuously interpolating distance, altitude, progress fraction, and segment gradient.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MAP-047` (*Continuous Linear Polyline Projection & Progress Metric Interpolation for Live Climbs*, amending `REQ-MAP-027` and aligning with `docs/design_guidelines.md` §2.4).
* **Test Mapping**: `TST-MAP-049` (*Live Climb Continuous Linear Projection, Telemetry Metric Interpolation & Cross-Track Off-Route Verification*).

---

## 3. System Invariants & Preserved Behavior

1. **Gate Threshold Invariants**:
   - Summit gate threshold (`CLIMB_SUMMIT_GATE_THRESHOLD_METERS = 35.0m`).
   - Start gate threshold (`CLIMB_START_GATE_THRESHOLD_METERS = 30.0m`).
   - Approach threshold (`CLIMB_APPROACH_THRESHOLD_METERS = 250.0m`).
   - Approach bearing tolerance ($\Delta \text{bearing} \le 60^\circ$).
2. **Off-Route Threshold & Invariant**:
   - Off-route threshold (`CLIMB_OFF_ROUTE_THRESHOLD_METERS = 100.0m`).
   - Distance evaluated is the perpendicular cross-track distance to the projected polyline point, preventing false off-route triggers on long road segments.
3. **Single-Point Geometry Fallback**:
   - Climbs with fewer than 2 points (`path.size < 2`) fall back gracefully to discrete vertex $P_0$ without `IndexOutOfBoundsException`.
4. **Scope Bounding (Rule 26 / ATT-1250)**:
   - Changes are strictly confined to `LiveClimbsRepository.kt` (lines 220–256) and `LiveClimbsRepositoryTest.kt`.
   - Zero database migrations, zero schema modifications, zero cross-cutting changes.
5. **UI Consistency (Rule 23)**:
   - No UI changes. `LiveClimbSheet.kt` automatically consumes the continuous `ActiveClimbState` fields (`currentProgressFraction`, `distanceToSummit`, `remainingElevationGain`, `currentGradePercent`).
6. **Parent Human Gate Invariance (Rule 2)**:
   - Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `LiveClimbsRepository.kt` (`com.atrainingtracker.trainingtracker.climbs`)
* **Layer**: Domain / Repository (Live Tracking Engine).
* **Changes in `processLocation()`**:
  1. Add single-point guard: If `path.size < 2`, measure distance to $P_0$ via `Location.distanceBetween`. If $> 100\text{m}$, return `null`. Otherwise return `current` with `completedDist = 0.0`, `distanceToSummit = climb.distanceMeters`, `currentProgressFraction = 0f`, and `currentGradePercent = climb.avgGradePercent`.
  2. Implement orthogonal segment projection loop for $i \in 0 \dots \text{path.size} - 2$:
     - For segment $[P_i, P_{i+1}]$:
       - $\Delta \text{lat} = P_{i+1}.\text{lat} - P_i.\text{lat}$
       - $\Delta \text{lng} = P_{i+1}.\text{lng} - P_i.\text{lng}$
       - $L^2 = \Delta \text{lat}^2 + \Delta \text{lng}^2$
       - $t = \text{if } (L^2 > 0.0) \left(\frac{(\text{location.lat} - P_i.\text{lat})\Delta \text{lat} + (\text{location.lng} - P_i.\text{lng})\Delta \text{lng}}{L^2}\right).\text{coerceIn}(0.0, 1.0) \text{ else } 0.0$
       - $\text{projLat} = P_i.\text{lat} + t \times \Delta \text{lat}$
       - $\text{projLng} = P_i.\text{lng} + t \times \Delta \text{lng}$
       - `Location.distanceBetween(location.latitude, location.longitude, projLat, projLng, results)`
       - Track `bestSegIdx` and `bestT` minimizing cross-track distance $d$.
  3. Off-route check:
     - If $\min(\text{crossTrackDist}) > \text{CLIMB\_OFF\_ROUTE\_THRESHOLD\_METERS}$ (100.0m), return `null`.
  4. Continuous telemetry interpolation:
     - `p1 = path[bestSegIdx]`, `p2 = path[bestSegIdx + 1]`
     - `segDist = p2.distance - p1.distance`
     - `segAlt = p2.altitude - p1.altitude`
     - $\text{completedDist} = ((p1.\text{distance} - \text{path.first().distance}) + \text{bestT} \times \text{segDist}).\text{coerceAtLeast}(0.0)$
     - $\text{remainingDist} = (\text{climb.distanceMeters} - \text{completedDist}).\text{coerceAtLeast}(0.0)$
     - $\text{currentAlt} = p1.\text{altitude} + \text{bestT} \times \text{segAlt}$
     - $\text{summitAlt} = \text{path.last().altitude}$
     - $\text{remainingGain} = (\text{summitAlt} - \text{currentAlt}).\text{coerceAtLeast}(0.0)$
     - $\text{progressFraction} = (\text{completedDist} / \text{climb.distanceMeters}).\text{coerceIn}(0.0, 1.0).\text{toFloat}()$
     - $\text{grade} = \text{if } (\text{segDist} > 0.0) (\text{segAlt} / \text{segDist}) \times 100.0 \text{ else } \text{climb.avgGradePercent}$
     - Return `current.copy(...)` with smoothly interpolated values.

### Component 2: `LiveClimbsRepositoryTest.kt` (`com.atrainingtracker.trainingtracker.climbs`)
* **Layer**: Unit Test.
* **Test Additions**:
  - `testProcessLocation_sparsePath_interpolatesProgressAndAltitudeContinuously`: Verify continuous progress at 25%, 50%, and 75% between 500m vertices.
  - `testProcessLocation_midSegmentSparseTrack_preventsFalseOffRoute`: Verify that being 10m off-route at the midpoint of a 180m segment (90.5m from nearest vertex) does NOT trigger false off-route cancellation.
  - `testProcessLocation_trueOffRoute_dismissesClimb`: Verify that 120m perpendicular offset triggers off-route cancellation.
  - `testProcessLocation_singlePointPath_safeFallback`: Verify single-point path executes safely without exception.

### UI Consistency (Rule 23)
* **Reference screen / component**: No UI changes. `LiveClimbSheet.kt` directly consumes the smoothly emitted `ActiveClimbState` flow.
* **Reused components**: N/A
* **Theme tokens**: N/A
* **New one-off styles & justification**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement Orthogonal Segment Projection in `LiveClimbsRepository.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/climbs/LiveClimbsRepository.kt`
* **Changes**:
  - Add single-point path guard returning safe fallback.
  - Replace discrete vertex search with orthogonal projection across segments $[P_i, P_{i+1}]$.
  - Compute cross-track distance to projected coordinate $(\text{projLat}, \text{projLng})$.
  - Linearly interpolate `completedDist`, `remainingDist`, `currentAlt`, `remainingGain`, `progressFraction`, and `currentGradePercent`.

### Step 2: Add Unit Tests in `LiveClimbsRepositoryTest.kt`
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/climbs/LiveClimbsRepositoryTest.kt`
* **Changes**:
  - Add test methods for continuous 25%/50%/75% interpolation, mid-segment false off-route prevention, true off-route dismissal, and single-point fallback.

### Step 3: Execute Targeted Unit Tests
* **Command**: `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.climbs.LiveClimbsRepositoryTest`
* **Assertion**: 100% test pass rate on all targeted tests.

---

## 6. Verification & Rollback Plan

* **Verification**:
  1. Targeted test class execution: `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.climbs.LiveClimbsRepositoryTest`.
  2. Full clean-room test suite: `./gradlew testDebugUnitTest`.
* **Rollback Plan**:
  - Git branch isolation (`feature/ATT-3174`). If an unexpected failure occurs, revert commits cleanly via `git reset --hard HEAD~1`.
