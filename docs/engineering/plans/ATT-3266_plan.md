# Stage 3: Implementation Plan - ATT-3266: Unify polyline progress tracking engines between LiveSegments and LiveClimbs

**Ticket**: [ATT-3266](https://atrainingtracker.atlassian.net/browse/ATT-3266)  
**Sub-task**: [ATT-3281](https://atrainingtracker.atlassian.net/browse/ATT-3281) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*[Epic] Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.10`  
**Requirement Mapping**: `REQ-GEO-001` (*Unified Polyline Progress Tracking Engine with Directional Virtual Gates and Orthogonal Segment Projection*)  
**Test Mapping**: `TST-GEO-001`  
**Branch**: `feature/ATT-3266`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Problem Description & Background

`aTrainingTracker` currently maintains two independent tracking engines for guiding athletes along polylines during outdoor tracking sessions:
1. `LiveSegmentsRepository.kt`: Starred Strava segments (`REQ-LIV-001` - `REQ-LIV-004`).
2. `LiveClimbsRepository.kt`: Route climbs and free-riding ascents (`REQ-MAP-027`, `REQ-MAP-047`).

While both engines share an identical state transition lifecycle (`FAR_FAR_AWAY` $\rightarrow$ `APPROACHING` $\rightarrow$ `ON_TRACK` $\rightarrow$ `FINISHED`), their tracking algorithms diverged:
- `LiveSegmentsRepository` utilizes directional virtual start/finish line gates using 2D vector cross-product sign flips, but calculates ongoing along-track distance via raw odometer subtraction ($D_{\text{current}} - D_{\text{start}}$), making it vulnerable to cumulative GPS odometer wander, sensor drift, and shortcuts.
- `LiveClimbsRepository` uses orthogonal polyline coordinate projection ($u \in [0, 1]$) with cross-track error detection, but relies on radial proximity circles ($30\text{m}$ start, $35\text{m}$ summit), making it susceptible to premature false triggers on tight hairpin switchbacks or parallel trails.

This implementation plan unifies both engines into a pure-Kotlin mathematical engine, `PolylineProgressTracker.kt` (`com.atrainingtracker.trainingtracker.geo`), combining directional virtual gates, orthogonal segment projection, 3D altitude/grade interpolation, smooth finish $\lambda$-blending, and approach heading filtering. Both repositories are refactored to delegate directly to this unified engine.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-GEO-001` (*Unified Polyline Progress Tracking Engine with Directional Virtual Gates and Orthogonal Segment Projection*, amending `REQ-LIV-001`, `REQ-LIV-002`, `REQ-LIV-004`, `REQ-MAP-027`, and `REQ-MAP-047`)
* **Test Mapping**: `TST-GEO-001` (*Unified Polyline Progress Tracking Engine with Directional Virtual Gates and Orthogonal Segment Projection Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Pure Kotlin Mathematical Isolation**: `PolylineProgressTracker` must not depend on Android framework classes (`android.location.Location`, `Context`, Android `View`) or SQLite database managers. It accepts domain models (`LatLng`, `PathPoint`) and standard math types, making it 100% unit-testable on the JVM without mocks.
2. **Strava Live Segment Parity**: Segment PR comparison, time delta, elapsed time calculations, and multi-segment candidate tracking in `LiveSegmentsRepository` remain fully functional.
3. **ClimbPro Parity**: Route climb indexing, remaining ascent, Climb Category resolution, active route vs free-riding detection in `LiveClimbsRepository`, and `LiveClimbSheet` HUD updates remain fully functional.
4. **Smooth Finish Convergence**: Remaining distance continuously and monotonically reaches exactly $0.0\text{m}$ at the finish line without discrete jumps.
5. **Switchback & Parallel Trail Immunity**: Entry and exit gates are directional; athletes riding parallel to or descending opposite to the track do not trigger gate crossings.
6. **100% Clean-Room Unit Regression Test Suite Pass Rate**: All existing climb and segment unit tests, as well as new engine tests, must pass with zero failures.

---

## 4. Proposed Architectural Changes

### Component 1: `PolylineProgressTracker.kt` (`com.atrainingtracker.trainingtracker.geo`)
* Pure-Kotlin object/class providing mathematical primitives for polyline progress tracking.
* **Core Data Models**:
  ```kotlin
  data class PolylineGate(
      val gateStart: LatLng,
      val gateEnd: LatLng,
      val referencePoint: LatLng,
      val referenceCrossProduct: Double,
      val gateThresholdMeters: Double = 50.0,
      val bearing: Double
  )

  data class PolylineProjection(
      val projectedLatLng: LatLng,
      val segmentIndex: Int,
      val t: Double,
      val alongTrackDistance: Double,
      val crossTrackDistanceMeters: Double,
      val progressFraction: Float,
      val interpolatedAltitude: Double?,
      val instantaneousGradePercent: Double?
  )

  enum class PolylineTrackingState {
      FAR_FAR_AWAY,
      APPROACHING,
      ON_TRACK,
      FINISHED
  }
  ```
* **Mathematical Operations**:
  1. `crossProduct(a: LatLng, b: LatLng, c: LatLng): Double`:
     $$(b_{\text{lat}} - a_{\text{lat}})(c_{\text{lng}} - a_{\text{lng}}) - (b_{\text{lng}} - a_{\text{lng}})(c_{\text{lat}} - a_{\text{lat}})$$
  2. `createGate(points: List<PathPoint>, isStartGate: Boolean, gateThresholdMeters: Double = 50.0, sampleOffset: Int = 5): PolylineGate`:
     Constructs perpendicular gate $[A, B]$ and reference point $N$ with reference cross product.
  3. `checkGateCrossing(gate: PolylineGate, prevCrossProduct: Double, currCrossProduct: Double, distanceToGate: Double, isStartGate: Boolean): Boolean`:
     Evaluates sign flip and heading match within gate threshold distance.
  4. `projectLocation(path: List<PathPoint>, location: LatLng): PolylineProjection`:
     Finds optimal segment $[P_i, P_{i+1}]$ minimizing cross-track distance $D_{\text{cross}}$, computes along-track distance, interpolated altitude, and grade.
  5. `calculateBlendedRemainingDistance(totalDistance: Double, completedDistance: Double, distanceToEnd: Double, finishThresholdMeters: Double): Double`:
     Computes linear $\lambda$-blended remaining distance.
  6. `getBearingDifference(b1: Double, b2: Double): Double`:
     Calculates minimal angular delta between two bearings ($[0^\circ, 180^\circ]$).
  7. `isHeadingAligned(userBearing: Double, targetBearing: Double, toleranceDegrees: Double = 45.0): Boolean`:
     Evaluates heading alignment for approach filtering.

### Component 2: `LiveSegmentsRepository.kt` (`com.atrainingtracker.trainingtracker.segments`)
* Refactor `LiveSegmentMath` to hold `startGate: PolylineGate` and `endGate: PolylineGate` from `PolylineProgressTracker`.
* Delegate gate crossing detection to `PolylineProgressTracker.checkGateCrossing`.
* Replace raw odometer subtraction ($D_{\text{current}} - D_{\text{start}}$) with `PolylineProgressTracker.projectLocation` along-track distance.
* Delegate remaining distance computation to `PolylineProgressTracker.calculateBlendedRemainingDistance`.
* Off-track deviation resets to `FAR_FAR_AWAY` if `crossTrackDistanceMeters > SEGMENT_DISTANCE_THRESHOLD` ($50\text{m}$).

### Component 3: `LiveClimbsRepository.kt` (`com.atrainingtracker.trainingtracker.climbs`)
* For each climb candidate, compute directional start and finish virtual gates via `PolylineProgressTracker.createGate`.
* Replace radial start/summit circles with `PolylineProgressTracker.checkGateCrossing`.
* Delegate polyline projection in `evaluateClimbProgress` to `PolylineProgressTracker.projectLocation`.
* Off-track check: `crossTrackDistanceMeters > CLIMB_OFF_ROUTE_THRESHOLD_METERS` ($100\text{m}$).

---

## 5. UI Consistency (Rule 23)

* **UI Consistency Assessment**: N/A. This ticket is a pure backend/domain mathematical engine refactoring. No UI layouts, Compose screens, or string resources are added or modified.

---

## 6. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Create `PolylineProgressTracker.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/geo/PolylineProgressTracker.kt`
* Operations:
  - Implement data classes: `PolylineGate`, `PolylineProjection`, `PolylineTrackingState`.
  - Implement pure mathematical functions: `crossProduct`, `createGate`, `checkGateCrossing`, `projectLocation`, `calculateBlendedRemainingDistance`, `getBearingDifference`, `isHeadingAligned`.

### Step 2: Create Unit Tests for `PolylineProgressTracker`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/geo/PolylineProgressTrackerTest.kt`
* Test cases:
  - `testVirtualGateConstructionAndForwardCrossing`: verifies crossing detected with sign flip.
  - `testReverseCrossingRejection`: verifies reverse travel is rejected.
  - `testParallelPassWithoutSignFlipRejection`: verifies parallel riding is rejected.
  - `testOrthogonalSegmentProjectionExactMidpoint`: verifies $t = 0.5$, along-track distance, and altitude.
  - `testOrthogonalSegmentProjectionWithLateralOffset`: verifies cross-track error detection.
  - `testSmoothFinishDistanceConvergence`: verifies smooth decrease to $0.0\text{m}$.
  - `testApproachHeadingAlignment`: verifies $45^\circ$ heading tolerance.

### Step 3: Refactor `LiveSegmentsRepository.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/segments/LiveSegmentsRepository.kt`
* Changes:
  - Adapt `LiveSegmentMath` to use `PolylineGate` and `PolylineProgressTracker`.
  - Update `updateLiveSegments` to project coordinate onto polyline for accurate along-track distance.
  - Use `PolylineProgressTracker.calculateBlendedRemainingDistance`.

### Step 4: Refactor `LiveClimbsRepository.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/climbs/LiveClimbsRepository.kt`
* Changes:
  - Construct start and summit virtual gates via `PolylineProgressTracker.createGate`.
  - Replace radial checks with directional gate crossing.
  - Delegate `evaluateClimbProgress` projection to `PolylineProgressTracker.projectLocation`.

### Step 5: Update & Verify Unit Tests for Repositories
* Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/segments/LiveSegmentsRepositoryTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/climbs/LiveClimbsRepositoryTest.kt`
* Changes: Ensure test setup and simulated track updates pass seamlessly with the unified engine.

### Step 6: Execute Targeted Unit Tests
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.geo.*" --tests "com.atrainingtracker.trainingtracker.climbs.*" --tests "com.atrainingtracker.trainingtracker.segments.*"
  ```

---

## 7. Verification & Rollback Plan

* **Verification**: Targeted unit tests in Stage 4, followed by full clean-room suite (`./gradlew testDebugUnitTest`) in Stage 5 verifying 100% pass rate.
* **Rollback Plan**: All work is contained in branch `feature/ATT-3266`. If unexpected regressions arise, the branch can be cleanly reset or reverted without impacting `sprint/2026-41.10`.
