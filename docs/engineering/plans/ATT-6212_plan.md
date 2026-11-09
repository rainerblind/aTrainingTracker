# Stage 3: Implementation Plan - ATT-6212: Prevent Worldwide Segment Fitting When currentLocation is Null

**Ticket**: [ATT-6212](https://rainerblind.atlassian.net/browse/ATT-6212)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Karten & Routen (Maps & Routes)*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.13` | **Branch**: `feature/ATT-6212`  
**Requirement Mapping**: `REQ-MAP-060` (`REQ-MAP-060.1`–`REQ-MAP-060.3`), `REQ-MAP-059`, `REQ-MAP-046`  
**Test Mapping**: `TST-MAP-062` (`TST-MAP-062.1`–`TST-MAP-062.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-08  

---

## 1. Problem Description & Background
When opening `MapScreenWithTrack` (*Karten -> Karte*) outside an active recording session, `currentLocation` is null. In `MapBoundsController` (`MapBehaviors.kt`), `isLocal(target)` previously returned `true` when `userPos == null`. For athletes with segments across multiple continents (e.g. Germany and Cuba), all segments worldwide were included in `LatLngBounds.Builder()`, triggering a hemisphere zoom over the Atlantic/Cuba (`att_5880_cuba.png`) and overriding the initial zoom-14 camera.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-MAP-060` (*Local Bounds Proximity Guard & Initial Location Fallback*)
  * `REQ-MAP-060.1`: `isLocal(target)` in `MapBoundsController` SHALL evaluate proximity against `userPos = currentLocation ?: initialLocation`. If `userPos == null`, `isLocal(...)` SHALL return `false`.
  * `REQ-MAP-060.2`: `MapBoundsController` SHALL accept optional `initialLocation: LatLng? = null`, forwarded from `ATrainingTrackerMap`'s resolved initial location.
  * `REQ-MAP-060.3`: When `hasPoints` is false and `currentLocation == null`, `MapBoundsController` SHALL NOT lock `hasFittedInitialBounds = true`, preserving deferred live GPS fix alignment per `REQ-MAP-059.2`.
* **Test Mapping**: `TST-MAP-062` (`TST-MAP-062.1`–`TST-MAP-062.5`)
  * `TST-MAP-062.1`: Unit test verifying `isLocal` returns `false` on null `userPos` and rejects distant coordinates (> 1000m).
  * `TST-MAP-062.2`: Contract test verifying `MapBoundsController` signature includes `initialLocation` and anchors `userPos`.
  * `TST-MAP-062.3`: Contract test verifying deferred fix preservation when `currentLocation == null` and `!hasPoints`.
  * `TST-MAP-062.4`: Contract test in `MapScreenWithTrackTest.kt` verifying `initialLocation` forwarding in `ATrainingTrackerMap`.
  * `TST-MAP-062.5`: Tier 1 targeted regression suite passing 100%.

---

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines `REQ-MAP-059` (*Initial Camera Centering*) and `REQ-MAP-046` (*Map Bounds Invariants*).
* **Historical Origin & Commit Trace**: Sprint 2026-41.6 (`0101e12e` introduced `MapBoundsController` with `if (userPos == null) return true`) and Sprint 2026-45.12 (`8f8f959a`, `ATT-5880`).
* **Root Reason for Existing Formulation**: `isLocal` naively assumed showing all segments was better than none when position was unknown; multi-continent Strava datasets broke this with transatlantic bounding boxes.
* **Preservation of Core Invariants**: `MapInitialLocationResolver` cascade (`REQ-MAP-059.1`), deferred GPS fix alignment (`REQ-MAP-059.2`), 1000m proximity radius, and Rule 33 single spatial authority remain preserved.

---

## 3. Proposed Architectural Changes (SWE.2)
* **`MapBehaviors.kt` (`MapBoundsController`)**:
  - Add parameter `initialLocation: LatLng? = null`.
  - Include `initialLocation` in `LaunchedEffect` keys.
  - Define `val userPos = currentLocation ?: initialLocation`.
  - In `isLocal(target: LatLng)`: change `if (userPos == null) return true` to `if (userPos == null) return false`.
  - In bounds fitting fallback: gate `cameraPositionState.move` and `hasFittedInitialBounds = true` on `currentLocation != null` when `!hasPoints`.
* **`ATrainingTrackerMap.kt`**: Pass `initialLocation = initialLocation` to `MapBoundsController`.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1: Code Lock Verification**: Execute `python3 tools/jira_util.py check-gate <IMPL_PLAN_KEY>` to verify Gate 3 sign-off.
2. **Step 2: Update MapBehaviors.kt**: Add `initialLocation` parameter, update `userPos` resolution, make `isLocal` return `false` on null, and gate fallback on `currentLocation != null`.
3. **Step 3: Update ATrainingTrackerMap.kt**: Forward `initialLocation` to `MapBoundsController`.
4. **Step 4: Update Unit & Contract Tests**: Add `TST-MAP-062.1`..`TST-MAP-062.4` in `MapBehaviorsTest.kt` and `MapScreenWithTrackTest.kt`.
5. **Step 5: Run Tier 1 Targeted Unit Tests**: Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.build.*"`.
6. **Step 6: Verify Living Governance**: Execute `python3 tools/verify_requirement_governance.py`.

---

## 5. System Invariants & Rollback Plan
* **Core Invariants**: REQ-MAP-059.1 fallback resolution; REQ-MAP-046 spatial authority; 1000m local threshold; 100% test pass rate.
* **Rollback Plan**: Working tree clean except for docs; revert cleanly via `git checkout .` if any step fails.
