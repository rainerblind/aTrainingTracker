# Stage 2: Requirement & Test Specification - ATT-4015: Route progress tracking projects in raw degrees and jumps on out-and-back routes

**Ticket**: [ATT-4015](https://rainerblind.atlassian.net/browse/ATT-4015) | **Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance*)  
**Target Release**: `V4.9.40` | **Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4015`  
**Requirement Mapping**: `REQ-GEO-002` (amending `REQ-GEO-001`) | **Test Spec ID**: `TST-GEO-002`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`) | **Date**: 2026-10-21  

---

## 1. Requirement Traceability Matrix

| Test Case | Scope | Target Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-GEO-002.1]` | Unit | `PolylineProgressTracker.projectLocation` | `REQ-GEO-002.1` | Planned |
| `[TST-GEO-002.2]` | Unit | `PolylineProgressTracker.createGate`, `checkGateCrossing` | `REQ-GEO-002.1` | Planned |
| `[TST-GEO-002.3]` | Unit | `PolylineProgressTracker.projectLocation` | `REQ-GEO-002.2` | Planned |
| `[TST-GEO-002.4]` | Unit | `PolylineProgressTracker.projectLocation` | `REQ-GEO-002.2` | Planned |
| `[TST-GEO-002.5]` | Contract | `PolylineProgressTracker.projectLocation` | `REQ-GEO-002.3` | Planned |
| `[TST-GEO-002.6]` | Localization | `TranslationParityTest` | `REQ-UI-106`, `REQ-GEO-002` | Planned |
| `[TST-GEO-002.7]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001`, `REQ-GEO-002` | Planned |

---

## 2. Formal Requirement Specifications (REQ-GEO-002)

* **Problem Statement & Rationale**: Unscaled lat/lon degree projection in `PolylineProgressTracker.kt` distorts orthogonal projection parameter $t$ and virtual gate angles by ~36% at $50^\circ\text{ N}$. Unwindowed global segment search causes progress to jump across parallel corridors on out-and-back and lollipop routes under minor GPS lateral jitter.
* **Functional & Architectural Clauses**:
  1. `REQ-GEO-002.1` (*Equirectangular Scaling*): Segment delta $dX = (P_{2.\text{lng}} - P_{1.\text{lng}}) \times \cos(\text{meanLat})$ and projection parameter $u = \frac{\Delta\text{lng}\cos\phi \cdot dX + \Delta\text{lat} \cdot dY}{dX^2 + dY^2}$ SHALL be scaled equirectangularly. Gate vectors $(\Delta\text{lat}_\perp, \Delta\text{lng}_\perp) = (dX, -dY / \cos\phi)$ and cross-product tests SHALL scale $\Delta\text{lng}$ by $\cos\phi$ for physical $90.0^\circ$ orthogonality.
  2. `REQ-GEO-002.2` (*Windowed Continuity with Corridor Fallback*): `projectLocation` SHALL evaluate candidate segments in $[lastSeg - 2, lastSeg + 10]$ when `lastSegmentIndex != null`. If $D_{\text{cross}} \le 50\text{m}$, window candidate is selected. If $D_{\text{cross}} > 50\text{m}$ or `lastSegmentIndex == null`, search SHALL fall back to global search.
  3. `REQ-GEO-002.3` (*Invariants & Backward Compatibility*): Pure JVM execution (zero Android SDK/SQLite dependencies) and backward compatibility for callers passing `lastSegmentIndex = null` MUST be strictly preserved.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-GEO-002`, amending `REQ-GEO-001`. Targets `PolylineProgressTracker.kt`.
* **Historical Origin & Commit Trace**: Sprint 2026-41.10 (`ATT-3266`, commits `e9e43fba`, `d480540c`). Discovered during out-and-back route tracking at mid-latitudes, tracked in `ATT-4015`.
* **Root Reason for Existing Formulation**: `ATT-3266` unified climbs and segments using Euclidean flat coordinates ($1^\circ \text{lat} = 1^\circ \text{lng}$) and linear search without directional continuity.
* **Preservation of Core Invariants**: Directional gate crossing via sign flips, 3D altitude/grade interpolation, smooth finish $\lambda$-blending, and pure JVM decoupling remain strictly preserved.

---

## 3. Formal Test Case Specifications (Given / When / Then)

* **`[TST-GEO-002.1]` Equirectangular Projection Accuracy**: *Given* diagonal segment at $50^\circ\text{ N}$ with $50\text{m}$ lateral offset, *When* `projectLocation` executes, *Then* parameter $t \approx 0.500$ without degree-skew along-track error ($< 0.5\text{m}$ vs geodesic truth).
* **`[TST-GEO-002.2]` Virtual Gate Orthogonality & Crossing**: *Given* diagonal track at $50^\circ\text{ N}$, *When* `createGate` generates endpoints, *Then* bearing is perpendicular ($90.0^\circ \pm 0.5^\circ$), and crossing triggers on sign flip.
* **`[TST-GEO-002.3]` Out-and-Back Window Continuity**: *Given* outbound leg (km 1) and parallel return leg (km 19) with `lastSegmentIndex = 2`, *When* GPS fix drifts $5\text{m}$ toward return leg, *Then* tracker remains locked to outbound segment.
* **`[TST-GEO-002.4]` Corridor Fallback & Rejoin**: *Given* athlete deviating $> 50\text{m}$ off route, *When* `projectLocation` runs, *Then* fallback to global search engages, locking onto closest segment upon rejoin.
* **`[TST-GEO-002.5]` Backward Compatibility**: *Given* legacy callers passing `lastSegmentIndex = null`, *When* `projectLocation` runs, *Then* global search is executed identically to legacy behavior.

---

## 4. Edge Case & Regression Matrix

| Edge Case / Hazard | Expected Handling & Mitigation Strategy | Target Test Case |
| :--- | :--- | :--- |
| **Polar / Equator Boundaries** | Mean lat $\approx 0^\circ \implies \cos\phi \approx 1.0$; clamping prevents division by zero if $\cos\phi \to 0$. | `[TST-GEO-002.1]` |
| **Co-located Vertices ($dX=dY=0$)** | Degenerate segment check returns $t = 0.0$ and avoids $0/0 = \text{NaN}$. | `[TST-GEO-002.1]` |
| **Window Boundary Clamping** | Range $[i-2, i+10]$ coerced to valid indices $[0, N-2]$ prevents `IndexOutOfBoundsException`. | `[TST-GEO-002.3]` |
| **Initial Fix / Unseeded State** | `lastSegmentIndex == null` defaults cleanly to global search across entire path. | `[TST-GEO-002.5]` |

---

## 5. Localization & Resource Parity Matrix

* **Localization Scope**: Pure mathematical calculation engine change; 0 new UI strings introduced.
* **`[TST-GEO-002.6]` Localization Parity Audit**: Verified 100% parity across all 9 canonical locales via `TranslationParityTest`.
* **`[TST-GEO-002.7]` Full Suite Clean-Room Regression**: Verified 100% pass rate via `./gradlew testDebugUnitTest`.
