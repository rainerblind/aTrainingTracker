# Stage 3: Implementation Plan - ATT-4015: Route progress tracking projects in raw degrees and jumps on out-and-back routes

**Ticket**: [ATT-4015](https://rainerblind.atlassian.net/browse/ATT-4015) | **Sub-task**: Pending Submission (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4015`  
**Requirement Mapping**: `REQ-GEO-002` (amending `REQ-GEO-001`) | **Test Mapping**: `TST-GEO-002`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Architecture & Invariant Compliance

* **Mathematical Architecture**: Enhance `PolylineProgressTracker` (pure Kotlin JVM engine in package `com.atrainingtracker.trainingtracker.geo`) with:
  1. *Local Equirectangular Scaling*: Scale longitudinal differences by $\cos(\text{meanLat})$ for projection scalar $u$, along-track parameter $t$, gate perpendicular rotation, and cross-products, eliminating mid-latitude distortion (~36% at $50^\circ\text{ N}$).
  2. *Windowed Continuity*: Constrain segment search to $[lastSeg - 2, lastSeg + 10]$ when `lastSegmentIndex != null` and cross-track error $\le 50\text{m}$, preventing out-and-back / lollipop jumping under lateral GPS jitter.
* **Core Invariants & Preserved Behavior**:
  1. *Pure JVM Execution*: Zero Android SDK or SQLite dependencies; execution remains purely mathematical and instant.
  2. *Stateless Backward Compatibility*: Callers omitting `lastSegmentIndex` default to `null` and execute unconstrained global search identically to legacy behavior.
  3. *3D Telemetry & Convergence*: Altitude/grade linear interpolation and $\lambda$-blended finish convergence remain strictly preserved.
  4. *Zero UI/Database Side Effects*: No Compose canvas alterations, no SQLite schema migrations.
* **UI Consistency (Rule 23)**: No UI changes.

---

## 2. Atomic Implementation Steps

* **Step 1 — Local Equirectangular Coordinate Scaling (`REQ-GEO-002.1`)**:
  * Update `PolylineProgressTracker.crossProduct`: compute $\cos\phi$ at mean latitude and scale longitude deltas $(c.\text{lng} - a.\text{lng})\cos\phi$ and $(b.\text{lng} - a.\text{lng})\cos\phi$.
  * Update `createGate`: rotate segment delta in metric/equirectangular space to $(\Delta\text{lat}_\perp, \Delta\text{lng}_\perp) = (dX, -dY / \cos\phi)$, forming exact $90.0^\circ$ physical perpendicularity at non-equatorial latitudes.
  * Update `projectLocation`: calculate segment $dX = (P_{2.\text{lng}} - P_{1.\text{lng}})\cos\phi, dY = P_{2.\text{lat}} - P_{1.\text{lat}}$ and projection $u = \frac{\Delta\text{lng}\cos\phi \cdot dX + \Delta\text{lat} \cdot dY}{dX^2 + dY^2}$.
* **Step 2 — Windowed Forward Segment Search & Corridor Fallback (`REQ-GEO-002.2`)**:
  * Extend signature: `projectLocation(path, location, lastSegmentIndex: Int? = null, searchWindowBackward: Int = 2, searchWindowForward: Int = 10, maxCorridorMeters: Double = 50.0)`.
  * If `lastSegmentIndex != null`, search segments in $[(lastSeg - W_{\text{back}}).\text{coerceAtLeast}(0), (lastSeg + W_{\text{fwd}}).\text{coerceAtMost}(N - 2)]$.
  * If the minimum cross-track error within window satisfies $D_{\text{cross}} \le maxCorridorMeters$, return the windowed projection.
  * If $D_{\text{cross}} > maxCorridorMeters$ (off-route excursion or rejoin) or `lastSegmentIndex == null`, fall back to global search across $0 \dots N - 2$.
* **Step 3 — Backward Compatibility & Invariant Guarding (`REQ-GEO-002.3`)**:
  * Ensure existing callers (`LiveSegmentsRepository`, `LiveClimbsRepository`, `TrackingElevationProfileResolver`) compile and run without signature modification.
* **Step 4 — Comprehensive Unit Test Suite Expansion (`TST-GEO-002.1`–`TST-GEO-002.5`)**:
  * Add unit tests in `PolylineProgressTrackerTest.kt` verifying diagonal projection accuracy at $50^\circ\text{ N}$, gate $90^\circ$ orthogonality, out-and-back leg continuity under lateral jitter, corridor fallback upon deviation, and stateless caller compatibility.

---

## 3. File-Level Change Matrix

| File Path | Role | Nature of Modifications |
| :--- | :--- | :--- |
| `app/.../geo/PolylineProgressTracker.kt` | Production | Equirectangular scaling in `crossProduct`, `createGate`, and `projectLocation`; windowed search with corridor fallback. |
| `app/.../geo/PolylineProgressTrackerTest.kt` | Test | Unit tests for diagonal equirectangular projection, gate orthogonality, out-and-back windowed continuity, and fallback. |

---

## 4. Test Execution Strategy

* **Tier 1 — Per-Ticket Verification (Stages 4 & 5)**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.geo.*" \
    --tests "com.atrainingtracker.trainingtracker.climbs.*" \
    --tests "com.atrainingtracker.trainingtracker.segments.*"
  ```
* **Governance Verification**:
  ```bash
  python3 tools/verify_requirement_governance.py
  ```

---

## 5. Rollback & Risk Mitigation

* **Risk Level**: **MEDIUM**. Core geo projection engine shared across route elevation profiles, live climbs, and starred segments.
* **Mitigation**: Pure JVM mathematical tests run in seconds without mocks; backward-compatible default parameters prevent regressions in existing callers.
* **Rollback Plan**: Zero SQLite schema or persistent data changes; full rollback achievable via `git checkout` / `git revert` on `feature/ATT-4015`.
