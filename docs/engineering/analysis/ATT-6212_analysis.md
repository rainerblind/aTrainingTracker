# Stage 1 Analysis: ATT-6212 - Prevent LOCAL_SEGMENTS / LOCAL_ROUTES from fitting world-wide segments when currentLocation is null

**Ticket**: [ATT-6212](https://rainerblind.atlassian.net/browse/ATT-6212)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Karten & Routen (Maps & Routes)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Branch**: `feature/ATT-6212`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
During Sprint Review 2026-45.12 for ATT-5880, opening the standalone global map (*Karten -> Karte*, `MapScreenWithTrack`) continued to zoom out to the entire Western Hemisphere centered over Cuba and the Atlantic (`att_5880_cuba.png`). Although `ATrainingTrackerMap` successfully initializes `cameraPositionState` via `MapInitialLocationResolver` to the athlete's local position (zoom 14.0f), once `isMapLoaded == true` and segments are retrieved from the database, `MapBoundsController` overrides the camera position with a multi-continent bounding box because outside active recording `currentLocation` is null.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
1. **Permissive Sentinel in `isLocal(target)` (`MapBehaviors.kt:104`)**:
   - In `MapBoundsController`, `val userPos = currentLocation` and `fun isLocal(target: LatLng): Boolean { if (userPos == null) return true ... }`.
   - Outside an active recording, `currentLocationFlow` emits `null`. When `userPos == null`, `isLocal(...)` unconditionally returns `true` for every segment/route stored in the database worldwide.
   - For athletes with stored segments across multiple countries/continents (e.g., Munich and Cuba/USA), `LatLngBounds.Builder()` includes all points globally and `CameraUpdateFactory.newLatLngBounds()` overwrites the initial zoom-14 camera with a hemisphere-wide zoom.
2. **Missing `initialLocation` Fallback Anchor**:
   - `ATrainingTrackerMap.kt` resolves `initialLocation` via `MapInitialLocationResolver`, but does not pass it to `MapBoundsController`, leaving `userPos` strictly dependent on live GPS fixes.
3. **Independent Peer Review & Verification**:
   - Verified `att_5880_cuba.png` via `read_file`: Confirmed zoom level ~2 spanning Greenland to Argentina centered on Cuba.
   - Audited `MapBehaviors.kt:98-197`: Confirmed `isLocal` returns `true` when `userPos == null`, and verified `LOCAL_ROUTES` exhibits the identical defect.
   - Hypothesis confirmed: Gating `isLocal` strictly on `if (userPos == null) return false` and backing `userPos` with `currentLocation ?: initialLocation` eliminates the hemisphere zoom while preserving legitimate local auto-fitting (< 1000m).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - In `MapBehaviors.kt` (`MapBoundsController`): Add optional `initialLocation: LatLng? = null` parameter; anchor `val userPos = currentLocation ?: initialLocation`; update `isLocal(target)` to return `false` if `userPos == null`.
  - In `ATrainingTrackerMap.kt`: Pass resolved `initialLocation` to `MapBoundsController`.
  - Ensure `MapBoundsController` does not lock `hasFittedInitialBounds = true` when `currentLocation == null` and no local points exist, preserving deferred live GPS fix alignment (REQ-MAP-059.2).
  - Add unit/contract tests in `MapBehaviorsTest.kt` verifying `isLocal` null-handling and distance filtering.
* **Out-of-Scope Non-Goals**:
  - Modifying `MapZoomFocus.FIT_PRIMARY` or `TRACK_AND_MARKERS` bounds logic.
  - Changing `MapInitialLocationResolver` priority cascade.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines `REQ-MAP-059.2` (*Initial Camera Centering & Deferred Fix Alignment*) and `REQ-MAP-046` (*Map Bounds & Follow-Me Invariants*).
* **Historical Origin & Commit Trace**: Commit `0101e12e` (June 24, 2026) introduced `MapBoundsController` with `if (userPos == null) return true` as a naive default; commit `8f8f959a` (`ATT-5880`) resolved initial camera location but left `isLocal` unpatched.
* **Root Reason for Existing Formulation**: `isLocal` assumed showing all segments was better than none when user position was unknown, but real-world multi-region Strava data broke this assumption.
* **Preservation of Core Invariants**: `MapInitialLocationResolver` cascade (REQ-MAP-059.1), deferred live GPS fix alignment (REQ-MAP-059.2), 1000m local threshold, navigation bar insets (REQ-MAP-059.3), and Rule 33 single spatial authority remain strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution
* `MapBehaviors.kt`:
  - Update `MapBoundsController` signature: `initialLocation: LatLng? = null`.
  - Define `val userPos = currentLocation ?: initialLocation`.
  - In `isLocal(target: LatLng)`: Change `if (userPos == null) return true` to `if (userPos == null) return false`.
  - In fallback branch: Only set `hasFittedInitialBounds = true` when `hasPoints` is true or `currentLocation != null`.
* `ATrainingTrackerMap.kt`: Forward `initialLocation = initialLocation` to `MapBoundsController`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: REQ-MAP-059.1 fallback resolution; REQ-MAP-046 spatial authority; 1000m local radius threshold; 100% unit test pass rate.
* **Risk Rating**: **LOW** (1 SP, localized bounds logic guard in UI layer with zero DB, network, or schema impact).
