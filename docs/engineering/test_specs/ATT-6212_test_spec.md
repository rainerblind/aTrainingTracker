# Stage 2: Requirement & Test Specification - ATT-6212: Prevent LOCAL_SEGMENTS / LOCAL_ROUTES from fitting world-wide segments when currentLocation is null

**Ticket**: [ATT-6212](https://rainerblind.atlassian.net/browse/ATT-6212)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Karten & Routen (Maps & Routes)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Requirement Mapping**: `REQ-MAP-060` (*Local Bounds Proximity Guard & Initial Location Fallback*)  
**Test Spec ID**: `TST-MAP-062`  
**Branch**: `feature/ATT-6212`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-MAP-060)

### 1.1 Problem Statement & Rationale
When opening `MapScreenWithTrack` outside an active recording session, `currentLocation` is null. `MapBoundsController`'s internal helper `isLocal(target)` previously returned `true` whenever `userPos == null`, causing all segments/routes stored worldwide to be included in `LatLngBounds.Builder()`, overwriting the initial zoom-14 camera with a hemisphere-wide zoom (e.g. over Cuba/Atlantic).

### 1.2 Functional & Architectural Requirements
1. **Local Proximity Evaluation Guard (`REQ-MAP-060.1`)**:
   - In `MapBoundsController`, `isLocal(target: LatLng)` SHALL evaluate proximity against `userPos = currentLocation ?: initialLocation`.
   - If `userPos == null`, `isLocal(...)` SHALL return `false`, preventing multi-continent or unanchored segment bounds calculation.
2. **Initial Location Fallback Parameter (`REQ-MAP-060.2`)**:
   - `MapBoundsController` SHALL accept optional `initialLocation: LatLng? = null`.
   - `ATrainingTrackerMap` SHALL forward `initialLocation` (resolved via `MapInitialLocationResolver`) to `MapBoundsController`.
3. **Deferred Fix Preservation on Empty Proximity (`REQ-MAP-060.3`)**:
   - When no points match the 1000m proximity threshold and `currentLocation == null`, `MapBoundsController` SHALL NOT permanently lock `hasFittedInitialBounds = true`, ensuring deferred live GPS fixes can still auto-align the camera per `REQ-MAP-059.2`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Null User Position)**: Given `currentLocation == null` and `initialLocation == null`, when `MapBoundsController` evaluates segments, then `isLocal` returns `false`, `hasPoints` is false, and no multi-continent bounds are fitted.
* **AC-2 (Initial Location Proximity)**: Given `currentLocation == null` and valid `initialLocation`, when segments exist within 1000m, then only those proximal segments are included in the bounding box.
* **AC-3 (Camera Stability)**: Given `currentLocation == null` and no proximal segments (< 1000m), when the map loads, then camera remains anchored at `initialLocation` (zoom 14.0f) without Atlantic/hemisphere zoom out.

### 1.4 System Invariants
* `MapInitialLocationResolver` cascade (`REQ-MAP-059.1`) preserved.
* Deferred live GPS fix camera alignment (`REQ-MAP-059.2`) preserved.
* 1000m proximity radius threshold preserved.
* Rule 33 single spatial authority preserved.

---

## 2. Test Specification (TST-MAP-062)

### Test Case 1: Null Position and Proximity Bounds Unit Tests (`[TST-MAP-062.1]`)
* **Scope**: Unit Test (`MapBehaviorsTest.kt`)
* **Preconditions**: Mock segments situated locally (< 1000m) and globally (> 1000m or distant continents).
* **Action**: Evaluate `isLocal` logic with `userPos == null` vs. valid `userPos`.
* **Expected Result**: `isLocal` returns `false` on null `userPos`; returns `true` only for targets `< 1000m`.

### Test Case 2: Initial Location Fallback & Camera Update Verification (`[TST-MAP-062.2]`)
* **Scope**: Unit / Contract Test (`MapBehaviorsTest.kt`)
* **Action**: Verify `MapBoundsController` signature includes `initialLocation: LatLng? = null` and anchors `userPos = currentLocation ?: initialLocation`.
* **Expected Result**: `initialLocation` parameter present; fallback correctly used for local proximity checks.

### Test Case 3: Wiring Contract in ATrainingTrackerMap (`[TST-MAP-062.3]`)
* **Scope**: Contract Test (`ATrainingTrackerMapTest.kt` / `MapScreenWithTrackTest.kt`)
* **Action**: Verify `ATrainingTrackerMap` passes `initialLocation` to `MapBoundsController`.
* **Expected Result**: Call site passes `initialLocation = initialLocation`.

### Test Case 4: Tier 1 Targeted Regression Suite (`[TST-MAP-062.4]`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Expected Result**: 100% PASS across map and build suites.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-MAP-062.1]` | Unit | `MapBoundsController.isLocal` | `REQ-MAP-060.1` | Specified |
| `[TST-MAP-062.2]` | Unit | `MapBoundsController` | `REQ-MAP-060.2` | Specified |
| `[TST-MAP-062.3]` | Contract | `ATrainingTrackerMap` | `REQ-MAP-060.2`, `REQ-MAP-060.3` | Specified |
| `[TST-MAP-062.4]` | Regression | `./gradlew testDebugUnitTest` | `REQ-MAP-060`, `REQ-PRO-001` | Specified |
