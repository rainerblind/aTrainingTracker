# Stage 1 Analysis: ATT-4015 - [Bug] [Medium] Route progress tracking projects in raw degrees and jumps on out-and-back routes

**Ticket**: [ATT-4015](https://rainerblind.atlassian.net/browse/ATT-4015)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4015`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Problem Domain & Root Cause Analysis

* **Defect 1: Skewed Projection & Virtual Gates (Raw Degrees)**:
  * In `PolylineProgressTracker.kt:96-99, 121-137, 215-228`, segment projection scalar $t$, perpendicular gate endpoints (`createGate`), and cross-products are evaluated directly in unscaled degrees latitude/longitude.
  * Because $1^\circ \text{longitude} \approx 111.139 \times \cos(\text{lat})\text{ km}$ while $1^\circ \text{latitude} \approx 111.139\text{ km}$, at $50^\circ\text{ N}$ ($\cos 50^\circ \approx 0.643$) longitudinal distances are physically ~36% shorter per degree.
  * *Impact 1 (Gate Skew)*: Gate endpoints constructed via $(\Delta\text{lng}, -\Delta\text{lat})$ produce a virtual gate line at $114.5^\circ$ relative to a diagonal track instead of true $90.0^\circ$ perpendicularity.
  * *Impact 2 (Along-Track Error)*: Orthogonal projection on a diagonal segment distorts parameter $t$ under lateral offsets; a $50\text{m}$ lateral GPS weave produces a $22.8\text{m}$ along-track error on a $1.3\text{km}$ segment ($t = 0.4827$ instead of $0.5000$).
* **Defect 2: Out-and-Back Route Segment Jumping (Unwindowed Global Search)**:
  * In `PolylineProgressTracker.projectLocation()`, the algorithm iterates over all segments $0 \dots \text{path.size} - 2$ globally on every fix without evaluating prior position or forward direction.
  * On out-and-back or lollipop routes, outbound and inbound legs share the same physical road corridor. A slight lateral GPS jitter (e.g. $5\text{m}$) closer to the return leg segment causes `bestSegIdx` to immediately jump from outbound (e.g. km 1) to return (e.g. km 19), destabilizing along-track distance, elevation markers, and prompts.

---

## 2. Call Site & Blast Radius Audit

| Component | Target File | Nature of Change & Impact |
| :--- | :--- | :--- |
| **Mathematical Engine** | `PolylineProgressTracker.kt` | Scale $\Delta\text{lng}$ by $\cos(\text{meanLat})$ for projection $u, t$ and gate generation; introduce windowed segment search around `lastSegmentIndex` with corridor/off-track fallback. |
| **Callers** | `TrackingElevationProfileResolver.kt`, `LiveSegmentsRepository.kt`, `LiveClimbsRepository.kt` | Maintain backward compatibility for stateless callers (`lastSegmentIndex = null`) while enabling stateful callers to pass tracking progress. |
| **Unit Tests** | `PolylineProgressTrackerTest.kt` | Add test cases verifying diagonal equirectangular projection at $50^\circ\text{ N}$, gate orthogonality, and out-and-back leg continuity under lateral jitter. |
| **Living Documentation** | `docs/requirements.md`, `docs/tests.md` | Synchronize `REQ-GEO-001` and `TST-GEO-001` with equirectangular scaling and windowed progress invariants. |

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Implement local equirectangular scaling ($\cos(\text{lat})$ on $\Delta\text{lng}$) in `projectLocation` and `createGate` in `PolylineProgressTracker.kt`.
  2. Implement windowed nearest-segment search in `projectLocation` when `lastSegmentIndex` is supplied (with fallback to global search when unseeded or off-corridor).
  3. Expand `PolylineProgressTrackerTest.kt` with targeted tests verifying mathematical correctness on non-cardinal routes and out-and-back paths.
  4. Synchronize `REQ-GEO-001` and `TST-GEO-001` living documentation.
* **Out-of-Scope Non-Goals**:
  1. No changes to UI rendering, Compose canvases, or gesture handlers in `SensorGridScreen.kt` or `ElevationProfile.kt`.
  2. No changes to Strava segment PR/delta math or ClimbPro sheet state machines.
  3. No changes to database schemas or SQLite tables.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: Amends `REQ-GEO-001` (*Unified PolylineProgressTracker Engine Architecture*).
* **Historical Origin & Commit Trace**: Sprint 2026-41.10 (`ATT-3266`, commits `e9e43fba`, `d480540c`).
* **Root Reason for Existing Formulation**: `ATT-3266` unified divergent segment and climb tracking logic, using Euclidean 2D vector math that implicitly assumed flat equatorial coordinates ($1^\circ \text{lat} = 1^\circ \text{lng}$) and unwindowed linear search.
* **Preservation of Core Invariants**: Directional gate crossing via sign flip, smooth finish $\lambda$-blending, 3D altitude/grade interpolation, and pure JVM decoupling (no Android framework dependencies) remain strictly preserved.

---

## 5. Architectural Strategy & System Invariants

* **Equirectangular Projection**:
  * For segment $[P_1, P_2]$, compute $\cos\phi = \cos(\text{toRadians}((P_{1.\text{lat}} + P_{2.\text{lat}}) / 2.0))$.
  * Vector components: $dX = (P_{2.\text{lng}} - P_{1.\text{lng}}) \times \cos\phi, \quad dY = P_{2.\text{lat}} - P_{1.\text{lat}}$.
  * Projection parameter: $u = \frac{(L_{\text{lng}} - P_{1.\text{lng}})\cos\phi \cdot dX + (L_{\text{lat}} - P_{1.\text{lat}}) \cdot dY}{dX^2 + dY^2}, \quad t = \text{coerceIn}(u, 0.0, 1.0)$.
  * Gate endpoints: $(\Delta\text{lat}_\perp, \Delta\text{lng}_\perp) = (dX, -dY / \cos\phi)$, ensuring exact $90^\circ$ physical orthogonality.
* **Windowed Search with Corridor Fallback**:
  * Signature overload / default: `projectLocation(path, location, lastSegmentIndex: Int? = null, searchWindowBackward: Int = 2, searchWindowForward: Int = 10, maxCorridorMeters: Double = 50.0)`.
  * If `lastSegmentIndex != null`, evaluate segments in $[i_{\text{prev}} - W_{\text{back}}, i_{\text{prev}} + W_{\text{fwd}}]$. If minimum cross-track error $\le D_{\text{corridor}}$, accept candidate; otherwise fall back to global search for off-route recovery.
* **System Invariants**: Pure Kotlin JVM object without Android SDK dependencies; backward compatibility for callers passing `lastSegmentIndex = null`.
* **Risk Rating**: **MEDIUM** — Touches core geo projection logic shared by routes, climbs, and segments; fully mitigated by pure JVM unit tests without SQLite or threading side effects.
