# Stage 4 Implementation: ATT-2942 - Restrict in-ride fork route candidate matching to active selected routes

**Ticket**: [ATT-2942](https://atrainingtracker.atlassian.net/browse/ATT-2942)  
**Sub-task**: [ATT-2992](https://atrainingtracker.atlassian.net/browse/ATT-2992) (`[Implementation]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2942`  

---

## 1. Summary of Code Changes

1. **`ForkRouteMatcher.kt` (`com.atrainingtracker.trainingtracker.routes`)**:
   - Added parameter `requireSelected: Boolean = false` to `findCandidateRoutes()`.
   - Added active/selected route filter: `if (requireSelected && !route.summary.isSelected) continue`, discarding unselected routes in O(1) before segment projections while preserving backward compatibility for callers defaulting `requireSelected = false`.
2. **`ForkNavigationRepository.kt` (`com.atrainingtracker.trainingtracker.routes`)**:
   - Filtered database routes by user selection state: `val selectedRoutes = allRoutes.filter { it.summary.isSelected }`.
   - Implemented O(1) quiescent short-circuit: if `selectedRoutes.size < 2`, clear any active fork alert (`_forkDecisionState.value = null` if present) and return immediately without evaluating throttling, bounding boxes, or polyline projections.
   - Forwarded `selectedRoutes` to `ForkRouteMatcher.findCandidateRoutes` with `requireSelected = true`.
3. **`ForkRouteMatcherTest.kt`**:
   - Added unit test `findCandidates_requireSelected_filtersOutUnselectedRoutes` validating that `requireSelected = true` filters out unselected routes and `requireSelected = false` evaluates all matching routes.
4. **`ForkNavigationRepositoryTest.kt`**:
   - Updated `dummySummary` default parameter to `isSelected = true`.
   - Added test `onLocationChanged_fewerThanTwoSelectedRoutes_skipsEvaluationAndClearsAlert` validating quiescent short-circuit with 0 and 1 route, and immediate dismissal of active prompts when routes drop below 2.
   - Added test `onLocationChanged_twoSelectedRoutesWithUnselectedCompetitor_onlyConsidersSelectedRoutes` asserting that unselected competitor routes are excluded from decision branches.

---

## 2. Targeted Verification Results

Executed `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.routes.ForkRouteMatcherTest" --tests "com.atrainingtracker.trainingtracker.routes.ForkNavigationRepositoryTest"`:
* `ForkRouteMatcherTest`: 100% PASSED (including new `TST-MAP-043` tests).
* `ForkNavigationRepositoryTest`: 100% PASSED (including new `TST-MAP-043` tests).
* Overall: BUILD SUCCESSFUL in 19s.
