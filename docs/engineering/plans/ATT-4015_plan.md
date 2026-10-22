# Stage 3: Implementation Plan - ATT-4015: Route progress tracking projects in raw degrees and jumps on out-and-back routes

**Ticket**: [ATT-4015](https://rainerblind.atlassian.net/browse/ATT-4015) | **Sub-task**: [ATT-4559](https://rainerblind.atlassian.net/browse/ATT-4559) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4015`  
**Requirement Mapping**: `REQ-NAV-056`, `REQ-GEO-002` | **Test Mapping**: `TST-NAV-056`, `TST-GEO-002`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Architectural Strategy & SWE.2 Layering

* **Problem**: `PolylineProgressTracker.projectLocation` provides windowed continuity via `lastSegmentIndex: Int?`, but all 3 production callers omitted `lastSegmentIndex` (defaulting to `null`), causing unwindowed global $O(N)$ searches and out-and-back leg snapping at runtime.
* **Architecture Strategy (SWE.2)**: Wire stateful `lastSegmentIndex` tracking and lifecycle reset boundaries across domain, repository, and UI layers:
  1. *UI/Domain*: `TrackingElevationProfileResolver` accepts `lastSegmentIndex` and returns `segmentIndex` in `ResolvedElevationProfileData`. `SensorGridScreen` retains `lastRouteSegmentIndex` scoped to `activeNavigatedRoute?.id` via `remember(activeNavigatedRoute?.id)`.
  2. *Live Segments*: `LiveSegmentMath` persists `lastSegmentIndex: Int?`, propagated in `LiveSegmentsRepository.updateLiveSegments` on lines 342 & 383, and reset on `FAR_FAR_AWAY`, `reconcileSegments`, or completion.
  3. *Live Climbs*: `LiveClimbData` persists `lastSegmentIndex: Int?`, propagated in `LiveClimbsRepository.evaluateClimbProgress` on line 235, and reset on summit, off-climb clear ($>50\text{m}$), or route change.
* **Core Invariants**: Stateless callers passing `null` retain global search; pure JVM decoupling; thread safety under coroutine dispatchers; zero UI theme drift (Rule 23).

---

## 2. Atomic Implementation Steps

* **Step 1 — Elevation Profile Resolver & UI State (`REQ-NAV-056.1`, `TST-NAV-056.1`)**:
  * In `TrackingElevationProfileResolver.kt`: add `lastSegmentIndex: Int? = null` parameter to `resolve()`, pass to `PolylineProgressTracker.projectLocation`, and expose `segmentIndex: Int?` in `ResolvedElevationProfileData`.
  * In `SensorGridScreen.kt`: maintain `var lastRouteSegmentIndex by remember(activeNavigatedRoute?.id) { mutableStateOf<Int?>(null) }`, pass to `resolve()`, and update from `resolvedProfile.segmentIndex`.
* **Step 2 — Live Segments Repository Stateful Continuity (`REQ-NAV-056.2`, `TST-NAV-056.2`)**:
  * In `LiveSegmentsRepository.kt`: add `var lastSegmentIndex: Int? = null` to `LiveSegmentMath`.
  * Pass `liveSegment.math.lastSegmentIndex` to `projectLocation` on lines 342 (`ON_SEGMENT`) and 383 (`FINISHED`), updating on result.
  * Reset `lastSegmentIndex = null` on transition to `FAR_FAR_AWAY` or in `reconcileSegments()`.
* **Step 3 — Live Climbs Repository Stateful Continuity (`REQ-NAV-056.3`, `TST-NAV-056.3`)**:
  * In `Climb.kt`: add `val lastSegmentIndex: Int? = null` to `LiveClimbData`.
  * In `LiveClimbsRepository.kt`: pass `current.lastSegmentIndex` to `projectLocation` on line 235 and persist `projection.segmentIndex` in returned `current.copy()`.
  * Reset on summit finish, off-climb deviation ($>50\text{m}$), or `refreshClimbs()`.
* **Step 4 — Caller-Level Unit Tests & Verification (`REQ-NAV-056.4`, `TST-NAV-056.1`–`TST-NAV-056.6`)**:
  * Author unit tests verifying out-and-back forward continuity and lifecycle resets in `TrackingElevationProfileResolutionTest.kt`, `LiveSegmentsRepositoryTest.kt`, and `LiveClimbsRepositoryTest.kt`.

---

## 3. File-Level Change Matrix

| File Path | Layer | Nature of Modifications |
| :--- | :--- | :--- |
| `app/.../tracking/TrackingElevationProfileResolver.kt` | Domain | Accept `lastSegmentIndex: Int?` in `resolve()`, return `segmentIndex` in `ResolvedElevationProfileData`. |
| `app/.../tracking/SensorGridScreen.kt` | UI | Retain `lastRouteSegmentIndex` with `remember(activeNavigatedRoute?.id)` and pass to `resolve()`. |
| `app/.../segments/LiveSegmentsRepository.kt` | Repository | Add `lastSegmentIndex` to `LiveSegmentMath`, propagate in `updateLiveSegments`, reset on `FAR_FAR_AWAY`. |
| `app/.../climbs/Climb.kt` & `LiveClimbsRepository.kt` | Repository | Add `lastSegmentIndex` to `LiveClimbData`, propagate in `evaluateClimbProgress`, reset on exit. |
| `app/.../ui/tracking/tracking/TrackingElevationProfileResolutionTest.kt` | Test | Unit tests for `resolve()` stateful propagation and out-and-back leg continuity. |
| `app/.../segments/LiveSegmentsRepositoryTest.kt` | Test | Unit tests for segment `lastSegmentIndex` propagation and lifecycle resets. |
| `app/.../climbs/LiveClimbsRepositoryTest.kt` | Test | Unit tests for climb `lastSegmentIndex` propagation and off-climb resets. |

---

## 4. Test Execution Commands & Rollback Strategy

* **Targeted Verification Commands**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingElevationProfileResolutionTest" \
    --tests "com.atrainingtracker.trainingtracker.segments.LiveSegmentsRepositoryTest" \
    --tests "com.atrainingtracker.trainingtracker.climbs.LiveClimbsRepositoryTest" \
    --tests "com.atrainingtracker.trainingtracker.geo.PolylineProgressTrackerTest"
  ```
* **Governance Verification**: `python3 tools/verify_requirement_governance.py`
* **Risk Rating & Rollback**: **MEDIUM**. Zero SQLite schema migrations; full rollback via `git checkout` / `git revert`.
