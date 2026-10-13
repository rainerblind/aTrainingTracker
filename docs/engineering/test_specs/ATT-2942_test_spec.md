# Stage 2 Requirement & Test Specification: ATT-2942 - Restrict in-ride fork route candidate matching to active selected routes

**Ticket**: [ATT-2942](https://atrainingtracker.atlassian.net/browse/ATT-2942)  
**Sub-task**: [ATT-2990](https://atrainingtracker.atlassian.net/browse/ATT-2990) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2942`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Requirement Specification (REQ-MAP-041)

### 1.1 Requirement Definition
* **Requirement ID**: `REQ-MAP-041`
* **Title**: Restricting In-Ride Fork Route Candidate Matching to Active Selected Routes & Quiescent Short-Circuit
* **Type**: Functional / Navigation Routing Architecture Specification
* **Target Release**: `V4.9.40`
* **Status**: Specified
* **Amends**: `REQ-MAP-031` (*In-Ride Fork-in-the-Road Route Selection & Decision Alerts*, Clauses 1 & 4) and `REQ-MAP-038` (*In-Ride Fork Route Detection Performance Optimization*)
* **Parent Ticket**: ATT-2942

### 1.2 Description
The system shall restrict in-ride fork-in-the-road route candidate matching in `ForkNavigationRepository.kt` and `ForkRouteMatcher.kt` to only evaluate routes that have been explicitly selected/activated by the athlete (`route.summary.isSelected == true`), and immediately short-circuit candidate matching in $O(1)$ when fewer than 2 active selected routes exist:
1. *Active Selected Route Candidate Filtering (`ForkNavigationRepository.kt`)*:
   - In `ForkNavigationRepository.onLocationChanged()`, candidate route matching shall only evaluate routes where `route.summary.isSelected == true`.
   - Unselected routes in the local database (`route.summary.isSelected == false`) shall be completely ignored, preventing spurious fork decision alerts from unrelated historical routes sharing the departure corridor.
2. *O(1) Quiescent Short-Circuit for Insufficient Candidates*:
   - If fewer than 2 active/selected routes exist in `routesRepository.allRoutes` (`selectedRoutes.size < 2`), `ForkNavigationRepository` shall immediately short-circuit in $O(1)$ without evaluating candidate search throttling, spatial bounding-box checks, or polyline projections.
   - If a fork decision prompt is currently active (`_forkDecisionState.value != null`) when selected routes drop below 2 (e.g. route unselected mid-ride), the alert state shall immediately be cleared (`_forkDecisionState.value = null`).
3. *Parameterized Selection Enforcement in `ForkRouteMatcher.kt`*:
   - `ForkRouteMatcher.findCandidateRoutes()` shall support parameter `requireSelected: Boolean = false`.
   - When `requireSelected == true`, routes with `!route.summary.isSelected` shall be discarded in $O(1)$ before segment projections.
4. *Preservation of System Invariants*:
   - Quiescent tracking throttling (`QUIESCENT_THROTTLE_INTERVAL_MS = 3000L`, `QUIESCENT_THROTTLE_DISTANCE_METERS = 20.0`), unthrottled 1 Hz active alert responsiveness (`REQ-MAP-038`), autonomous route snapping ($\ge 50\text{ m}$ past divergence), 1-tap manual route binding, and 100% test pass rate across the full test suite must be strictly preserved.

### 1.3 Acceptance Criteria (Given-When-Then)

#### Scenario 1: Quiescent Short-Circuit with Fewer Than 2 Selected Routes
* **Given** an active workout tracking session where 0 or 1 route is selected (`summary.isSelected == true`) in the local route database,
* **When** the athlete travels along any corridor or approaches a road intersection,
* **Then** `ForkNavigationRepository` shall short-circuit in $O(1)$ and bypass candidate search,
* **And** `forkDecisionState.value` shall remain `null`.

#### Scenario 2: Active Alert Dismissal on Route Deselection
* **Given** an active fork decision alert displayed in the cockpit between 2 candidate routes,
* **When** the athlete or system unselects one or both routes such that `selectedRoutes.size < 2`,
* **Then** `_forkDecisionState.value` shall immediately be reset to `null` on the next location fix, dismissing the prompt.

#### Scenario 3: Unselected Routes Exclusion from Candidate Matching
* **Given** an active workout tracking session where Route A and Route B are selected (`isSelected == true`), and an unselected Route C (`isSelected == false`) also shares the departure corridor,
* **When** the athlete approaches the divergence vertex ($D_{\text{fork}} \le 300\text{ m}$),
* **Then** `ForkNavigationRepository` and `ForkRouteMatcher` shall only evaluate Route A and Route B,
* **And** `forkDecisionState.value.branches` shall contain exactly Route A and Route B, completely excluding Route C.

#### Scenario 4: Backward Compatibility of `ForkRouteMatcher`
* **Given** a direct caller of `ForkRouteMatcher.findCandidateRoutes()` providing routes without specifying `requireSelected`,
* **When** evaluating candidate routes,
* **Then** `requireSelected` defaults to `false` and geometric matching executes across all provided routes regardless of `isSelected` flag.

---

## 2. Test Specification (TST-MAP-043)

### 2.1 Test Definition
* **Test ID**: `TST-MAP-043`
* **Title**: Active Selected Route Fork Candidate Filtering & Quiescent Short-Circuit Verification
* **Target Requirement**: `REQ-MAP-041`
* **Test Type**: Automated Unit & Regression Tests (`ForkNavigationRepositoryTest.kt`, `ForkRouteMatcherTest.kt`)
* **Status**: Specified

### 2.2 Test Cases

#### Case 1: `onLocationChanged_fewerThanTwoSelectedRoutes_skipsEvaluationAndClearsAlert` (`ForkNavigationRepositoryTest.kt`)
* **Setup**:
  - Provide a repository with only 1 selected route (or 0 selected routes) and multiple unselected routes.
* **Execution**:
  - Call `repository.onLocationChanged(posApproach)`.
* **Assertion**:
  - `repository.forkDecisionState.value` is `null`.
  - Next, artificially prime an active alert state (`_forkDecisionState.value != null`), update repository routes such that only 1 route is selected, and invoke `onLocationChanged`.
  - Assert that `repository.forkDecisionState.value` is immediately cleared to `null`.

#### Case 2: `onLocationChanged_twoSelectedRoutesWithUnselectedCompetitor_onlyConsidersSelectedRoutes` (`ForkNavigationRepositoryTest.kt`)
* **Setup**:
  - Configure Route 1 (`isSelected = true`) and Route 2 (`isSelected = true`) diverging at 1000m.
  - Add Route 3 (`isSelected = false`) sharing the same departure corridor and diverging at 1000m.
* **Execution**:
  - Call `repository.onLocationChanged(posApproach)` approaching the fork.
* **Assertion**:
  - `repository.forkDecisionState.value` is non-null.
  - Exactly 2 branches exist in `forkDecisionState.value.branches` (matching Route 1 and Route 2).
  - Route 3 is not present in branches.

#### Case 3: `findCandidates_requireSelected_filtersOutUnselectedRoutes` (`ForkRouteMatcherTest.kt`)
* **Setup**:
  - Route 1: `isSelected = true`, valid shared corridor.
  - Route 2: `isSelected = false`, valid shared corridor.
* **Execution**:
  - Call `ForkRouteMatcher.findCandidateRoutes(listOf(route1, route2), athletePos, requireSelected = true)`.
* **Assertion**:
  - Exactly 1 candidate route returned (Route 1).
  - Call with `requireSelected = false` returns 2 candidate routes (Route 1 and Route 2).

#### Case 4: Existing Test Suite Regression
* **Setup**:
  - Update `dummySummary` in `ForkNavigationRepositoryTest.kt` to default `isSelected = true`.
* **Execution**:
  - Execute all existing fork navigation unit tests: `onLocationChanged_approachingFork_triggersAlertState`, `onLocationChanged_pastForkOnBranchA_autoBindsRouteA`, `onLocationChanged_deviatesFromBoth_dismissesPrompt`, `selectRouteManually_bindsRouteImmediatelyAndClearsState`, `dismissPrompt_clearsAlertWithoutBinding`, `onLocationChanged_quiescentThrottling_skipsEvaluationWhenUnderTimeAndDistanceThresholds`, `onLocationChanged_activeAlert_evaluatesEveryFixWithoutThrottling`.
* **Assertion**:
  - 100% test pass rate.

---

## 3. Traceability Matrix

| Requirement ID | Test Case ID | Test Class | Verification Scope | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-MAP-041` | `TST-MAP-043` | `ForkNavigationRepositoryTest` | Quiescent short-circuit with < 2 selected routes & alert dismissal | Specified |
| `REQ-MAP-041` | `TST-MAP-043` | `ForkNavigationRepositoryTest` | Unselected route candidate exclusion | Specified |
| `REQ-MAP-041` | `TST-MAP-043` | `ForkRouteMatcherTest` | `requireSelected` parameter enforcement and backward compatibility | Specified |
| `REQ-MAP-041` | `TST-MAP-040` | `ForkNavigationRepositoryTest` | Quiescent throttling & 1 Hz active responsiveness preservation | Specified |

---

## 4. Localization & String Parity Audit

* Zero new user-facing strings or UI elements are introduced by this backend candidate matching optimization.
* All existing decision prompt strings (`nav_fork_title`, `nav_fork_branch_left`, etc.) retain 100% translation parity across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
