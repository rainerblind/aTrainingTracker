# Stage 1 Analysis: ATT-4015 - [Bug] [Medium] Route progress tracking projects in raw degrees and jumps on out-and-back routes

**Ticket**: [ATT-4015](https://rainerblind.atlassian.net/browse/ATT-4015)  
**Sub-task**: [ATT-4557](https://rainerblind.atlassian.net/browse/ATT-4557) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4015`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-21  

---

## 1. Problem Statement & Root Cause Analysis

* **Defect 1: Raw Degree Projection & Gate Skew**:
  * In `PolylineProgressTracker.kt`, orthogonal projection $t$ and perpendicular gate generation (`createGate`) used raw unscaled degrees lat/lng. At $50^\circ\text{ N}$ ($\cos 50^\circ \approx 0.643$), $1^\circ\text{ lng}$ is ~36% shorter, skewing gates to $114.5^\circ$ and distorting along-track distances under lateral offset.
* **Defect 2: Final Review Bounce — Unwindowed Production Callers**:
  * While `PolylineProgressTracker.projectLocation` implements windowed search $[i-2, i+10]$ with $50\text{m}$ corridor fallback when given `lastSegmentIndex: Int?`, **none of the 3 production callers pass `lastSegmentIndex`** (all default to `null`):
    1. `TrackingElevationProfileResolver.kt:38` (Cockpit elevation profile marker)
    2. `LiveSegmentsRepository.kt:342, 383` (Strava Live Segments progress & finish)
    3. `LiveClimbsRepository.kt:235` (Live Climbs ascent & grade telemetry)
  * As a consequence, runtime execution remains unwindowed global $O(N)$ search ($0 \dots \text{path.size}-2$). On out-and-back routes where outbound and inbound legs share the road corridor, GPS jitter jumps progress across opposing legs.

---

## 2. Stateful Caller Lifecycle & Reset Rules

| Production Caller | Stateful Holder | Injection Point | Reset Rule & Lifecycle Boundary |
| :--- | :--- | :--- | :--- |
| **`TrackingElevationProfileResolver`** / `SensorGridScreen` / `TrackingViewModel` | `ResolvedElevationProfileData.segmentIndex` & `remember(route.id)` in UI / ViewModel | Pass `lastSegmentIndex` into `resolve()`; persist returned `segmentIndex` | Reset to `null` when `activeNavigatedRoute` changes, navigation stops, or athlete deviates off-corridor ($>50\text{m}$). |
| **`LiveSegmentsRepository`** | `LiveSegmentMath.lastSegmentIndex: Int?` | Pass `liveSegment.math.lastSegmentIndex` into `projectLocation` (:342, :383); update on result | Reset to `null` on transition to `FAR_FAR_AWAY`, on `reconcileSegments`, or segment completion. Seeded on `APPROACHING -> ON_SEGMENT`. |
| **`LiveClimbsRepository`** | `LiveClimbData.lastSegmentIndex: Int?` | Pass `current.lastSegmentIndex` into `projectLocation` (:235); update in returned `copy()` | Reset to `null` on climb finish, off-climb clear (`>50\text{m}`), timeout, or `activeNavigatedRouteId` change in `refreshClimbs`. |

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Wire stateful `lastSegmentIndex` propagation into `LiveSegmentsRepository`, `LiveClimbsRepository`, and `TrackingElevationProfileResolver` / `SensorGridScreen` / `TrackingViewModel`.
  2. Implement strict reset rules across route, segment, and climb lifecycle boundaries.
  3. Author caller-level unit tests verifying `lastSegmentIndex` propagation and out-and-back continuity without leg jumping.
  4. Maintain living documentation synchronization (`REQ-GEO-002`, `TST-GEO-002`).
* **Out-of-Scope Non-Goals**:
  1. No alterations to Compose canvas rendering, gestures, or layout bounds in `SensorGridScreen.kt` or `ElevationProfile.kt`.
  2. No changes to Strava segment PR matching or ClimbPro sheet state machine transitions.
  3. Zero database schema migrations or SQLite alterations.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: Amends `REQ-GEO-001` via `REQ-GEO-002` (*Equirectangular Scaled Polyline Projection, Orthogonal Virtual Gates & Windowed Out-and-Back Route Progress Tracking*).
* **Historical Origin & Commit Trace**: Sprint 2026-41.10 (`ATT-3266`, commits `e9e43fba`, `d480540c`).
* **Root Reason for Existing Formulation**: `ATT-3266` unified divergent tracking math using flat Cartesian vectors ($1^\circ \text{lat} = 1^\circ \text{lng}$) and unwindowed linear search, neglecting latitude scaling and out-and-back leg overlap.
* **Preservation of Core Invariants**: Directional gate crossing via sign flip, smooth finish $\lambda$-blending, 3D altitude/grade interpolation, and pure JVM decoupling remain strictly preserved. Stateless callers passing `lastSegmentIndex = null` retain global search.

---

## 5. Architectural Strategy & System Invariants

* **Equirectangular Projection**: $\Delta\text{lng}$ scaled by $\cos\phi = \cos(\text{toRadians}((\text{lat}_1 + \text{lat}_2)/2))$, yielding physically perpendicular gates ($90.0^\circ$) and unskewed $t$.
* **Windowed Search & Propagation**: Window $[i-2, i+10]$ with $50\text{m}$ corridor threshold ensures forward continuity; callers store returned segment index and pass it sequentially.
* **Core Invariants**: Zero Android SDK dependencies in `PolylineProgressTracker`; backward compatibility for unseeded callers; thread-safe repository state; 100% test pass rate.
* **Risk Rating**: **MEDIUM** — Touches core progress tracking across routes, segments, and climbs; fully mitigated by comprehensive caller-level unit tests and pure JVM regression coverage.
