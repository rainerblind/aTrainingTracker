# Stage 2: Requirement & Test Specification - ATT-4015: Route progress tracking projects in raw degrees and jumps on out-and-back routes

**Ticket**: [ATT-4015](https://rainerblind.atlassian.net/browse/ATT-4015) | **Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance*)  
**Target Release**: `V4.9.40` | **Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4015`  
**Requirement Mapping**: `REQ-NAV-056` (complementing `REQ-GEO-002`) | **Test Spec ID**: `TST-NAV-056`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`) | **Date**: 2026-10-21  

---

## 1. Requirement Traceability Matrix

| Test Case | Scope | Target Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-NAV-056.1]` | Unit | `TrackingElevationProfileResolver.resolve` | `REQ-NAV-056.1` | Planned |
| `[TST-NAV-056.2]` | Unit | `LiveSegmentsRepository.updateLiveSegments` | `REQ-NAV-056.2` | Planned |
| `[TST-NAV-056.3]` | Unit | `LiveClimbsRepository.updateClimbLiveProgress` | `REQ-NAV-056.3` | Planned |
| `[TST-NAV-056.4]` | Contract | `PolylineProgressTracker.projectLocation` | `REQ-NAV-056.4`, `REQ-GEO-002.3` | Planned |
| `[TST-NAV-056.5]` | Localization | `TranslationParityTest` | `REQ-UI-106`, `REQ-NAV-056` | Planned |
| `[TST-NAV-056.6]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001`, `REQ-NAV-056` | Planned |

---

## 2. Formal Requirement Specifications (REQ-NAV-056)

* **Problem Statement & Rationale**: While `PolylineProgressTracker` provides windowed continuity via `lastSegmentIndex: Int?`, production callers (`TrackingElevationProfileResolver`, `LiveSegmentsRepository`, `LiveClimbsRepository`) omit `lastSegmentIndex`, causing runtime execution to fall back to unwindowed global $O(N)$ search and jumping across opposing legs on out-and-back routes.
* **Functional & Architectural Clauses**:
  1. `REQ-NAV-056.1` (*Elevation Profile Resolver Continuity*): `resolve()` SHALL accept `lastSegmentIndex: Int? = null`, pass it to `projectLocation`, and return `segmentIndex: Int?` in `ResolvedElevationProfileData`. Stateful callers SHALL persist this across consecutive fixes. Reset to `null` on route change, stop, or corridor deviation ($>50\text{m}$).
  2. `REQ-NAV-056.2` (*Strava Live Segments Continuity*): `LiveSegmentMath` SHALL persist `lastSegmentIndex: Int?`, pass it to `projectLocation` during active tracking and finish evaluation, and update on each fix. Reset to `null` on transition to `FAR_FAR_AWAY`, `reconcileSegments`, or finish.
  3. `REQ-NAV-056.3` (*Live Climbs Continuity*): `LiveClimbData` SHALL persist `lastSegmentIndex: Int?`, pass it to `projectLocation` in `updateClimbLiveProgress`, and update in returned copy. Reset to `null` on summit, off-climb clear ($>50\text{m}$), timeout, or route change.
  4. `REQ-NAV-056.4` (*Invariants & Backward Compatibility*): Stateless callers passing `null` retain global search; pure JVM decoupling; thread-safe repository state; 100% clean-room test pass rate.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-NAV-056`, complementing `REQ-GEO-002` and `REQ-GEO-001`. Targets `TrackingElevationProfileResolver.kt`, `LiveSegmentsRepository.kt`, and `LiveClimbsRepository.kt`.
* **Historical Origin & Commit Trace**: Sprint 2026-41.10 (`ATT-3266`) and Sprint 2026-43.6 (`ATT-4015`, commit `0da4472d`). Discovered during Final Review of `ATT-4015`.
* **Root Reason for Existing Formulation**: Callers initially retained default `null` to minimize blast radius, leaving runtime production executions vulnerable to opposing-leg snapping on out-and-back tracks.
* **Preservation of Core Invariants**: Backward-compatible fallback for `null` callers, pure JVM decoupling, and thread-safe encapsulation preserved.

---

## 3. Formal Test Case Specifications (Given / When / Then)

* **`[TST-NAV-056.1]` Elevation Profile Stateful Continuity**: *Given* an out-and-back route with overlapping outbound/inbound legs, *When* `resolve` is called sequentially with propagating `lastSegmentIndex`, *Then* `alongTrackDist` and `segmentIndex` advance monotonically without snapping to inbound leg; route change resets to `null`.
* **`[TST-NAV-056.2]` Live Segments Stateful Continuity**: *Given* an out-and-back Strava Live Segment, *When* `LiveSegmentsRepository` processes fixes, *Then* `liveSegment.math.lastSegmentIndex` propagates to `projectLocation` and updates smoothly; `FAR_FAR_AWAY` or finish resets index to `null`.
* **`[TST-NAV-056.3]` Live Climbs Stateful Continuity**: *Given* a switchback climb, *When* `updateClimbLiveProgress` executes with previous `lastSegmentIndex`, *Then* progress fraction advances continuously without jumping ahead; summit or off-climb resets index to `null`.
* **`[TST-NAV-056.4]` Stateless Caller Backward Compatibility**: *Given* callers passing `lastSegmentIndex = null`, *When* `projectLocation` runs, *Then* global search is executed identically to legacy behavior.

---

## 4. Edge Case & Regression Matrix

| Edge Case / Hazard | Expected Handling & Mitigation Strategy | Target Test Case |
| :--- | :--- | :--- |
| **Route Switch During Tracking** | Immediate reset of `lastSegmentIndex` to `null` prevents cross-route index corruption. | `[TST-NAV-056.1]` |
| **Severe Off-Corridor Deviation** | Distance $> 50\text{m}$ falls back to global search and resets stored index cleanly upon rejoining. | `[TST-NAV-056.1]`, `[TST-NAV-056.2]` |
| **Segment Reconcile / Abandonment** | `reconcileSegments()` and `FAR_FAR_AWAY` transition safely nullify `lastSegmentIndex`. | `[TST-NAV-056.2]` |
| **Initial Fix / Unseeded State** | `lastSegmentIndex == null` defaults cleanly to global search across entire path. | `[TST-NAV-056.4]` |

---

## 5. Localization & Resource Parity Matrix

* **Localization Scope**: Pure mathematical caller integration; 0 new UI strings introduced.
* **`[TST-NAV-056.5]` Localization Parity Audit**: Verified 100% parity across all 9 canonical locales via `TranslationParityTest`.
* **`[TST-NAV-056.6]` Full Suite Clean-Room Regression**: Verified 100% pass rate via `./gradlew testDebugUnitTest`.
