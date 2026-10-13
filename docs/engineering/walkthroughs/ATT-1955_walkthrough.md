# Stage 5: Verification Walkthrough - ATT-1955: In-Ride Fork-in-the-Road Route Selection & Decision Alerts

**Ticket**: [ATT-1955](https://rainerblind.atlassian.net/browse/ATT-1955)  
**Sub-task**: [ATT-2435](https://rainerblind.atlassian.net/browse/ATT-2435) (`[Test] In-Ride Fork-in-the-Road Route Selection & Decision Alerts`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.16`  
**Requirement Mapping**: `REQ-MAP-031`  
**Test Mapping**: `TST-MAP-033`  
**Branch**: `feature/ATT-1955`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Executive Summary

This walkthrough document verifies the complete software construction, mathematical modeling, geometric polyline projection, corridor departure matching, divergence vertex detection, directional bearing classification, Cockpit HUD decision card rendering, autonomous route binding, and 9-language localization for [ATT-1955](https://rainerblind.atlassian.net/browse/ATT-1955), fulfilling requirement `REQ-MAP-031` and test specification `TST-MAP-033`.

aTrainingTracker now delivers an intelligent in-ride fork-in-the-road route selection and alert system for athletes departing along common corridors:
- **Dynamic Outbound Corridor Matching (`ForkRouteMatcher.kt`)**: Continuously tracks candidate stored routes in `RoutesRepository.allRoutes` matching the athlete's outbound trajectory within spatial corridor tolerance ($\le 50\text{ m}$) sharing a minimum departure prefix ($\ge 300\text{ m}$).
- **Divergence Vertex Detection & Alert Window (`RouteDivergenceDetector.kt`)**: Detects upcoming divergence coordinates where candidate paths separate by $> 40\text{ m}$ cross-distance, calculates remaining distance $D_{\text{fork}}$ to the junction along the corridor, computes relative departure bearings (Left, Straight, Right), and activates the alert state within $0 < D_{\text{fork}} \le 300\text{ m}$.
- **Ambient Cockpit HUD Decision Card (`ForkDecisionCard.kt`, `SensorGridScreen.kt`)**: Displays a high-contrast Material 3 card presenting relative direction arrows, countdown distance to the fork, candidate route names, total distances, elevation gains, and 1-tap route selection or dismissal.
- **Autonomous Route Binding & Deviation Handling (`ForkNavigationRepository.kt`)**: When the rider turns onto one of the branches and travels $\ge 50\text{ m}$ past the divergence vertex with cross-track error $< 25\text{ m}$ (while alternative branches have cross-track error $> 50\text{ m}$), the system automatically locks the chosen route into `RoutesRepository.activeNavigatedRouteId`, instantly activating turn prompts (`REQ-MAP-028`) and live climb tracking (`REQ-MAP-027`). If the athlete deviates $> 50\text{ m}$ from all branches or taps close, the alert dismisses cleanly.
- **100% 9-Language Localization Parity**: All 7 alert labels, direction cues, distance templates, dismiss actions, and selection hints are localized across EN, DE, ES, FR, IT, JA, NL, PL, PT with zero missing keys.

---

## 2. Verification Matrix

| Acceptance Criterion | Verification Method | Status | Evidence |
| :--- | :--- | :--- | :--- |
| **AC-1: Outbound Corridor Candidate Matching** | [ForkRouteMatcherTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/routes/ForkRouteMatcherTest.kt) | **PASSED** | Verifies tracking candidate routes within $\le 50\text{ m}$ buffer for $\ge 300\text{ m}$, filtering reverse-oriented and early-diverged routes, and polyline projection math. |
| **AC-2: Divergence Vertex Detection & Relative Bearings** | [RouteDivergenceDetectorTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/routes/RouteDivergenceDetectorTest.kt) | **PASSED** | Verifies detection of divergence coordinate ($> 40\text{ m}$ separation), countdown distance $D_{\text{fork}}$, $300\text{ m}$ alert trigger window, and relative directional classification (Left, Straight, Right). |
| **AC-3: Autonomous Snapping & Lifecycle Repository** | [ForkNavigationRepositoryTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/routes/ForkNavigationRepositoryTest.kt) | **PASSED** | Verifies auto-binding when travelling $\ge 50\text{ m}$ past fork with $< 25\text{ m}$ cross-track error, 1-tap manual route binding, prompt dismissal on deviation ($> 50\text{ m}$), and manual dismissal. |
| **AC-4: Cockpit HUD Decision Card Contract** | [ForkDecisionCardTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/ForkDecisionCardTest.kt) | **PASSED** | Verifies direction resource IDs, formatting of route names, distances, elevation gains, and branching structure. |
| **AC-5: 100% 9-Language Localization Audit** | [TranslationParityTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt) | **PASSED** | 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT with matching format specifiers and zero missing entries. |
| **AC-6: Requirement & Test Governance** | `python3 tools/verify_requirement_governance.py` | **PASSED** | `REQ-MAP-031` and `TST-MAP-033` marked `Verified` with zero governance violations. |
| **AC-7: Full Clean-Room Test Suite Regression** | `./gradlew testDebugUnitTest` | **PASSED** | Clean-room test suite executed with 100% pass rate. |

---

## 3. Key Implementation Highlights

### 1. Divergence Vertex & Branch Bearing Detection (`RouteDivergenceDetector.kt`)
```kotlin
val junctionDist = if (firstDivergenceDist > 0.0) firstDivergenceDist else (confirmedDivergenceDist - SAMPLE_STEP_METERS).coerceAtLeast(d0)
val divergencePoint = pointAtDistance(refRoute.path, junctionDist)
val distanceToFork = (junctionDist - d0).coerceAtLeast(0.0)

// Must be within alert window (0m < distanceToFork <= 300m)
if (distanceToFork <= ALERT_WINDOW_MIN_METERS || distanceToFork > ALERT_WINDOW_MAX_METERS) {
    return null
}
```

### 2. Autonomous Route Binding on Branch Traversal (`ForkNavigationRepository.kt`)
```kotlin
val chosenRoute = candidates.firstOrNull { route ->
    val proj = ForkRouteMatcher.projectOntoPolyline(currentPos, route.path)
    val forkProj = ForkRouteMatcher.projectOntoPolyline(activeState.divergenceCoordinate, route.path)
    val distancePastFork = proj.distanceAlongRouteMeters - forkProj.distanceAlongRouteMeters

    distancePastFork >= AUTO_BIND_MIN_DISTANCE_PAST_FORK_METERS &&
            proj.crossTrackDistanceMeters < AUTO_BIND_MAX_CROSS_TRACK_CHOSEN_METERS
}

if (chosenRoute != null) {
    val otherCandidates = candidates.filter { it.summary.id != chosenRoute.summary.id }
    val alternativesFar = otherCandidates.isEmpty() || otherCandidates.all { other ->
        val projOther = ForkRouteMatcher.projectOntoPolyline(currentPos, other.path)
        projOther.crossTrackDistanceMeters > AUTO_BIND_MIN_CROSS_TRACK_ALTERNATIVE_METERS
    }

    if (alternativesFar) {
        routesRepository.setActiveNavigatedRoute(chosenRoute.summary.id)
        _forkDecisionState.value = null
        return
    }
}
```

---

## 4. Verification Evidence & Test Execution Results

```
> Task :app:testDebugUnitTest

ForkRouteMatcherTest > projectOntoPolyline_calculatesAccurateDistanceAndCrossTrack PASSED
ForkRouteMatcherTest > findCandidates_sharedDepartureCorridor_returnsMatchingRoutes PASSED
ForkRouteMatcherTest > findCandidates_divergedOrReverseRoute_filtersOutRoute PASSED
ForkRouteMatcherTest > findCandidates_insufficientSharedPrefix_returnsEmpty PASSED

RouteDivergenceDetectorTest > classifyRelativeDirection_mapsAngleRangesAccurately PASSED
RouteDivergenceDetectorTest > pointAtDistance_interpolatesAccurately PASSED
RouteDivergenceDetectorTest > detectDivergence_twoSplittingRoutes_findsAccurateCoordinateAndDistance PASSED
RouteDivergenceDetectorTest > alertWindow_beyond300Meters_returnsNull PASSED

ForkNavigationRepositoryTest > onLocationChanged_approachingFork_triggersAlertState PASSED
ForkNavigationRepositoryTest > onLocationChanged_pastForkOnBranchA_autoBindsRouteA PASSED
ForkNavigationRepositoryTest > onLocationChanged_deviatesFromBoth_dismissesPrompt PASSED
ForkNavigationRepositoryTest > selectRouteManually_bindsRouteImmediatelyAndClearsState PASSED
ForkNavigationRepositoryTest > dismissPrompt_clearsAlertWithoutBinding PASSED

ForkDecisionCardTest > forkDirection_hasValidStringResources PASSED
ForkDecisionCardTest > forkDecisionState_containsAllBranchDetails PASSED

TranslationParityTest > verifyTranslationParity PASSED
```

---

## 5. Artifacts and Traceability

- **Specification Deliverable**: [ATT-1955_req_test_spec.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/test_specs/ATT-1955_test_spec.md)
- **Plan Deliverable**: [ATT-1955_plan.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/plans/ATT-1955_plan.md)
- **Walkthrough Deliverable**: [ATT-1955_walkthrough.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/walkthroughs/ATT-1955_walkthrough.md)
- **Git Commit**: Feature branch `feature/ATT-1955` ready for integration into `sprint/2026-40.16`.
