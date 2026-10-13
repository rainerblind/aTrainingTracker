# Stage 5: Walkthrough - ATT-3326: Automatically unselect route when user is no longer on an automatically selected route

**Ticket**: [ATT-3326](https://rainerblind.atlassian.net/browse/ATT-3326)  
**Sub-task**: [ATT-3376](https://rainerblind.atlassian.net/browse/ATT-3376) (`[Test]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-MAP-031` (Clause 4)  
**Test Mapping**: `TST-MAP-033` (Clause 7)  
**Branch**: `feature/ATT-3326`  
**Author**: AI Agent 1 (Verification & Quality Engineer)  
**Date**: 2026-10-13  

---

## 1. Executive Summary

This deliverable fulfills Stage 5 verification for **ATT-3326**. When an athlete encounters a trail or road fork where multiple stored routes diverge, the system can automatically bind the route the athlete chooses (`autoFollowUniqueRouteEnabled == true`). Prior to ATT-3326, if the athlete later departed from the corridor or took an unplanned detour, the system treated the route as permanently locked, persistently triggering "Off Route" audio chimes and red warning cards.

ATT-3326 introduces selection provenance tracking in `RoutesRepository` (`isRouteAutoSelected`), distinguishing between automatically bound routes and intentionally chosen manual routes. When an automatically bound route experiences an off-route deviation (`newState.isOffRoute == true`), `TurnByTurnNavigationRepository` automatically unselects the route (`setActiveNavigatedRoute(null)`), suppressing annoying off-route warnings and re-enabling candidate fork monitoring in `ForkNavigationRepository`. Manually selected routes remain strictly preserved.

---

## 2. Verification Results

### 2.1 Targeted Unit Testing
- **`RoutesRepositoryActiveNavigationTest`**:
  - `testIsRouteAutoSelected_defaultsToFalse`: **PASSED**
  - `testSetActiveNavigatedRoute_withAutoSelectedTrue_updatesProvenance`: **PASSED**
- **`ForkNavigationRepositoryTest`**:
  - `onLocationChanged_pastForkOnBranchA_autoBindsChosenRoute`: **PASSED** (verifies `isAutoSelected = true`)
  - `selectRouteManually_whenAutoFollowDisabled_stillBindsRouteImmediately`: **PASSED** (verifies `isAutoSelected = false`)
- **`TurnAlertTriggersTest`**:
  - `testOffRoute_whenRouteIsAutoSelected_automaticallyUnselectsRoute`: **PASSED** (verifies `setActiveNavigatedRoute(null)` called upon off-route deviation)
  - `testOffRoute_whenRouteIsManuallySelected_retainsRoute`: **PASSED** (verifies route retained when manually selected)
- **`TranslationParityTest`**: **PASSED** (100% localization parity across all 9 supported locales)

### 2.2 Clean-Room Test Suite Execution
- **Command**: `./gradlew testDebugUnitTest`
- **Result**: **BUILD SUCCESSFUL** (2505+ tests executed, 100% pass rate, 0 failures)
- **Execution Time**: 2m 27s

---

## 3. Living Documentation Status

- **`docs/requirements.md`**: `REQ-MAP-031` updated to `Verified`.
- **`docs/tests.md`**: `TST-MAP-033` updated to `Verified`.
- **Chesterton's Fence Audit**: Verified preserving manual route permanence, pure geodesic mathematics, and full backward compatibility.

---

## 4. Subtask & Ticket Advancement Plan

1. Subtask **ATT-3376** description updated with this walkthrough.
2. Subtask moved to `In Überprüfung` and Gate 5 audit initiated via `tools/review_agent.py audit ATT-3376`.
3. Upon Gate 5 sign-off (`RECOMMEND PASS`), subtask moved to `Erledigt`.
4. Parent ticket **ATT-3326** stepped through lifecycle transitions to `Final Review (Human)`.
5. Changes committed to `feature/ATT-3326`.
6. Branch `feature/ATT-3326` merged into `sprint/2026-42.1` with `--no-ff`, and `feature/ATT-3326` deleted.
