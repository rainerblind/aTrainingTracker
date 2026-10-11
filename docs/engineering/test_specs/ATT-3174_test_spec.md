# Stage 2: Requirement & Test Specification - ATT-3174: Linearly interpolate sparse path points on Climb elevation profile and live progress metrics

**Ticket**: [ATT-3174](https://atrainingtracker.atlassian.net/browse/ATT-3174)  
**Sub-task**: [ATT-3212](https://atrainingtracker.atlassian.net/browse/ATT-3212) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2464](https://atrainingtracker.atlassian.net/browse/ATT-2464) (*ClimbPro Live Climb Engine*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.9`  
**Requirement Mapping**: `REQ-MAP-047` (*Continuous Linear Polyline Projection & Progress Metric Interpolation for Live Climbs*, amending `REQ-MAP-027` and aligning with `docs/design_guidelines.md` §2.4)  
**Test Spec ID**: `TST-MAP-049`  
**Branch**: `feature/ATT-3174`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Requirement Specification (REQ-MAP-047)

### 1.1 Problem Statement & Rationale
In `LiveClimbsRepository.kt` (lines 220–256), `processLocation()` previously determined rider progress along an active climb solely by snapping to the single nearest vertex index (`path[closestIdx]`). Between sparse vertices (50m–200m apart on straight roads or imported GPX tracks):
1. The rider pin on `LiveClimbSheet.kt` remained frozen at a fixed X coordinate and then abruptly jumped forward upon crossing the halfway Voronoi boundary between vertices.
2. In-ride telemetry metrics (`completedDist`, `remainingDist`, `currentAlt`, `remainingGain`, `progressFraction`, and `currentGradePercent`) remained static over long stretches, jumping discontinuously in coarse steps.
3. Athletes riding on-road between two distant vertices (e.g. 180m apart) could approach or exceed the 100m off-route threshold (`CLIMB_OFF_ROUTE_THRESHOLD_METERS`) because distance was measured to the nearest vertex rather than the road segment.

`REQ-MAP-047` amends `REQ-MAP-027` and operationalizes `docs/design_guidelines.md` §2.4 by mandating continuous orthogonal linear projection onto polyline segments $[P_i, P_{i+1}]$ with linear interpolation of distance, altitude, progress fraction, and gradient.

### 1.2 Functional & Architectural Requirements

1. **Continuous Orthogonal Segment Projection (`LiveClimbsRepository.kt`)**:
   - For each consecutive polyline segment $[P_i, P_{i+1}]$ in `path` ($i = 0 \dots \text{path.size} - 2$):
     - Compute the segment vector $\vec{v} = (\Delta \text{lat}, \Delta \text{lng}) = (P_{i+1}.\text{lat} - P_i.\text{lat}, P_{i+1}.\text{lng} - P_i.\text{lng})$.
     - Compute segment squared Euclidean length $L^2 = \Delta \text{lat}^2 + \Delta \text{lng}^2$.
     - If $L^2 > 0.0$, compute the scalar projection parameter $t = \frac{(\text{location.lat} - P_i.\text{lat})\Delta \text{lat} + (\text{location.lng} - P_i.\text{lng})\Delta \text{lng}}{L^2}$ clamped to $[0.0, 1.0]$; otherwise $t = 0.0$.
     - Derive projected coordinate $P_{\text{proj}} = (\text{projLat}, \text{projLng}) = (P_i.\text{lat} + t \times \Delta \text{lat}, P_i.\text{lng} + t \times \Delta \text{lng})$.
     - Measure geodesic cross-track distance between athlete $(\text{location.lat}, \text{location.lng})$ and $P_{\text{proj}}$ using `Location.distanceBetween`.
     - Identify the optimal segment index $i$ and scalar $t$ that minimize cross-track distance across all segments.

2. **Cross-Track Off-Route Protection**:
   - The off-route check SHALL evaluate the minimum cross-track distance to the projected polyline point against `CLIMB_OFF_ROUTE_THRESHOLD_METERS` (100.0m).
   - If $\min(\text{crossTrackDist}) > \text{CLIMB\_OFF\_ROUTE\_THRESHOLD\_METERS}$, `processLocation()` SHALL return `null` (off climb).
   - When riding along a straight road with sparse vertices (e.g. 180m apart), an athlete traveling on-segment SHALL NOT be falsely marked off-route.

3. **Continuous Telemetry & Progress Interpolation**:
   - Using the optimal segment $[P_i, P_{i+1}]$ and scalar $t$, the system SHALL compute:
     - $\text{completedDist} = ((P_i.\text{distance} - \text{path.first().distance}) + t \times (P_{i+1}.\text{distance} - P_i.\text{distance})).\text{coerceAtLeast}(0.0)$
     - $\text{remainingDist} = (\text{climb.distanceMeters} - \text{completedDist}).\text{coerceAtLeast}(0.0)$
     - $\text{currentAlt} = P_i.\text{altitude} + t \times (P_{i+1}.\text{altitude} - P_i.\text{altitude})$
     - $\text{remainingGain} = (\text{summitAlt} - \text{currentAlt}).\text{coerceAtLeast}(0.0)$
     - $\text{progressFraction} = (\text{completedDist} / \text{climb.distanceMeters}).\text{coerceIn}(0.0, 1.0).\text{toFloat}()$
     - $\text{currentGradePercent} = \text{if } (P_{i+1}.\text{distance} - P_i.\text{distance} > 0.0) \frac{P_{i+1}.\text{altitude} - P_i.\text{altitude}}{P_{i+1}.\text{distance} - P_i.\text{distance}} \times 100.0 \text{ else } \text{climb.avgGradePercent}$

4. **Single-Point Geometry Fallback**:
   - If `path.size < 2`, the system SHALL fall back to discrete vertex evaluation at $P_0$, using geodesic distance to $P_0$ for off-route check, $P_0.\text{altitude}$, and $\text{completedDist} = 0.0$.

5. **Chesterton's Fence & System Invariants**:
   - Summit gate threshold (`CLIMB_SUMMIT_GATE_THRESHOLD_METERS = 35.0m`), start gate threshold (`CLIMB_START_GATE_THRESHOLD_METERS = 30.0m`), approach detection (`CLIMB_APPROACH_THRESHOLD_METERS = 250.0m`), approach bearing tolerance ($\le 60^\circ$), and 100% full clean-room unit test pass rate MUST be strictly preserved.

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Continuous Progress & Altitude Interpolation on Sparse Vertices)**:
  * *Given* an active live climb with sparse trackpoints 500m apart (vertex 0 at 0m / 100m alt, vertex 1 at 500m / 150m alt),
  * *When* the athlete is located at the 250m midpoint (50% progress),
  * *Then* `completedDist` evaluates to 250.0m ($\pm 0.1\text{m}$), `progressFraction` evaluates to 0.5f, and `currentAlt` evaluates to 125.0m.

* **Criterion 2 (Mid-Segment False Off-Route Prevention)**:
  * *Given* an athlete traveling along a road segment between two vertices spaced 180m apart,
  * *When* the athlete is at the 90m midpoint 10m perpendicular to the segment (cross-track distance 10m, vertex distance 90.5m),
  * *Then* `processLocation()` evaluates cross-track distance as 10m and retains the climb as active (does not trigger false off-route cancellation).

* **Criterion 3 (True Off-Route Rejection)**:
  * *Given* an athlete who deviates $> 100\text{m}$ perpendicular to all polyline segments of the climb,
  * *When* `processLocation()` is called,
  * *Then* `processLocation()` returns `null`, cleanly dismissing the off-route climb.

* **Criterion 4 (Single-Point Fallback Safety)**:
  * *Given* a climb with a single path point (`path.size == 1`),
  * *When* `processLocation()` is evaluated,
  * *Then* the engine falls back safely to $P_0$ without throwing an `IndexOutOfBoundsException`.

---

## 2. Test Specification (TST-MAP-049)

### Test Case 1: `testProcessLocation_sparsePath_interpolatesProgressAndAltitudeContinuously` (`TST-MAP-049.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/climbs/LiveClimbsRepositoryTest.kt`
* **Preconditions**: An active climb with sparse trackpoints spaced 500m apart ($P_0$ at `(48.0, 11.0, alt=100.0, dist=0.0)`, $P_1$ at `(48.0045, 11.0, alt=150.0, dist=500.0)`).
* **Action**:
  1. Call `processLocation(locAt25Percent)` (lat = 48.001125, lng = 11.0).
  2. Call `processLocation(locAt50Percent)` (lat = 48.002250, lng = 11.0).
  3. Call `processLocation(locAt75Percent)` (lat = 48.003375, lng = 11.0).
* **Expected Result**:
  - At 25%: `completedDist` $\approx 125.0\text{m}$, `progressFraction` $\approx 0.25\text{f}$, `currentAlt` $\approx 112.5\text{m}$, `remainingGain` $\approx 37.5\text{m}$.
  - At 50%: `completedDist` $\approx 250.0\text{m}$, `progressFraction` $\approx 0.50\text{f}$, `currentAlt` $\approx 125.0\text{m}$, `remainingGain` $\approx 25.0\text{m}$.
  - At 75%: `completedDist` $\approx 375.0\text{m}$, `progressFraction` $\approx 0.75\text{f}$, `currentAlt` $\approx 137.5\text{m}$, `remainingGain` $\approx 12.5\text{m}$.
  - In all cases, `currentGradePercent` $\approx 10.0\%$.

### Test Case 2: `testProcessLocation_midSegmentSparseTrack_preventsFalseOffRoute` (`TST-MAP-049.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/climbs/LiveClimbsRepositoryTest.kt`
* **Preconditions**: Active climb with two vertices spaced 180m apart.
* **Action**:
  - Supply a GPS location positioned at the 90m midpoint but offset 10m perpendicularly from the road (cross-track distance = 10m, distance to either vertex $\approx 90.5\text{m}$).
* **Expected Result**:
  - `processLocation()` returns a non-null `ActiveClimbState` with `progressFraction` $\approx 0.5\text{f}$ and cross-track distance within threshold ($\le 100\text{m}$).

### Test Case 3: `testProcessLocation_trueOffRoute_dismissesClimb` (`TST-MAP-049.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/climbs/LiveClimbsRepositoryTest.kt`
* **Preconditions**: Active climb on polyline.
* **Action**:
  - Supply a GPS location positioned 120m perpendicularly away from the nearest segment.
* **Expected Result**:
  - `processLocation()` returns `null` (cleanly dismissed off-route).

### Test Case 4: `testProcessLocation_singlePointPath_safeFallback` (`TST-MAP-049.4`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/climbs/LiveClimbsRepositoryTest.kt`
* **Preconditions**: Climb with `pathPoints` containing exactly 1 point.
* **Action**:
  - Supply a GPS location within 20m of the single point.
* **Expected Result**:
  - Does not throw exception; returns valid state with `completedDist = 0.0`.

### Test Case 5: 9-Language Localization Parity Audit (`TST-MAP-049.5`)
* **Scope**: Localization Parity Test (`TranslationParityTest`)
* **Goal**: Zero new UI strings are required for this engine improvement; verify existing strings retain 100% parity across all 9 locales:
  - EN, DE, ES, FR, IT, JA, NL, PL, PT.
* **Expected Result**: 100% pass rate.

### Test Case 6: Full Clean-Room Regression Suite (`TST-MAP-049.6`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% unit test pass rate across the full suite with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-MAP-049.1` | Unit | `LiveClimbsRepository.processLocation` | `REQ-MAP-047` | Specified |
| `TST-MAP-049.2` | Unit | `LiveClimbsRepository.processLocation` | `REQ-MAP-047` | Specified |
| `TST-MAP-049.3` | Unit | `LiveClimbsRepository.processLocation` | `REQ-MAP-047` | Specified |
| `TST-MAP-049.4` | Unit | `LiveClimbsRepository.processLocation` | `REQ-MAP-047` | Specified |
| `TST-MAP-049.5` | Localization | `TranslationParityTest` | `REQ-UI-106` | Specified |
| `TST-MAP-049.6` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
