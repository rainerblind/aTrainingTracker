# Stage 3 Implementation Plan: ATT-2964 - Suppress in-ride fork decision alerts when all candidate routes share the same direction

**Ticket**: [ATT-2964](https://atrainingtracker.atlassian.net/browse/ATT-2964)  
**Sub-task**: [ATT-3026](https://atrainingtracker.atlassian.net/browse/ATT-3026) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2964`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Executive Summary & Architecture

This implementation plan defines the atomic construction steps for suppressing in-ride fork decision alerts when all candidate routes share the identical relative direction ([ATT-2964](https://atrainingtracker.atlassian.net/browse/ATT-2964)) in accordance with `REQ-NAV-043` and `TST-NAV-045`.

The changes introduce direction diversity gating in `RouteDivergenceDetector.kt`:
1. Following branch relative direction classification (`classifyRelativeDirection`), inspect the set of distinct directions across all candidate route branches.
2. If `distinctDirections.size < 2` (e.g. all branches are `STRAIGHT`, all `LEFT`, or all `RIGHT`), return `null` immediately.
3. If `distinctDirections.size >= 2`, construct and return `ForkDecisionState`.

---

## 2. Atomic Implementation Steps

### Step 1: Direction Diversity Gating in `RouteDivergenceDetector.kt`
* In `RouteDivergenceDetector.detectDivergence()`:
  - Immediately following `val branchOptions = validCandidates.map { ... }.sortedBy { it.bearingDiffDegrees }`:
  ```kotlin
  val distinctDirections = branchOptions.map { it.direction }.distinct()
  if (distinctDirections.size < 2) {
      return null
  }
  ```
* *Verification*: Compiles cleanly without side effects on caller contracts.

### Step 2: Unit Tests in `RouteDivergenceDetectorTest.kt`
* Add targeted tests:
  1. `testDetectDivergence_suppressesAlertWhenAllBranchesShareSameDirection`:
     - Construct two candidate routes where Route 1 and Route 2 separate by $> 40\text{ m}$ (e.g. 50m separation at 1500m), but both follow a trajectory within $[-20^\circ, 20^\circ]$ of the approach corridor (`ForkDirection.STRAIGHT`).
     - Assert `RouteDivergenceDetector.detectDivergence(listOf(route1, route2), athletePos)` returns `null`.
  2. `testDetectDivergence_activatesAlertWhenBranchesHaveDistinctDirections`:
     - Construct two candidate routes where Route 1 continues `STRAIGHT` and Route 2 turns `RIGHT` ($> 20^\circ$).
     - Assert `detectDivergence()` returns a non-null `ForkDecisionState` with 2 branches.
  3. Validate existing tests continue to pass.
* Run targeted tests via `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.routes.RouteDivergenceDetectorTest"`.

### Step 3: Clean-Room Full Suite Regression
* Run `./gradlew testDebugUnitTest` verifying 100% test pass rate across all test suites.

---

## 3. Invariants & Governance

1. **Chesterton's Fence Preservation**:
   - `LOOKAHEAD_METERS` (600m), `SAMPLE_STEP_METERS` (20m), `DIVERGENCE_SEPARATION_THRESHOLD_METERS` (40m), and `ALERT_WINDOW_MAX_METERS` (300m) remain unchanged.
   - Relative direction classification angles (`classifyRelativeDirection`) remain strictly preserved.
   - Active route restriction (`REQ-MAP-041` / `ATT-2942`) is preserved.
2. **Clean-Room Test Pass Rate**:
   - 100% full clean-room unit test pass rate.

---

## 4. Gate 3 Readiness Checklist

- [x] All requirements mapped to atomic implementation steps.
- [x] Direction diversity logic is deterministic and null-safe.
- [x] Unit test specifications defined.
- [x] Invariants and backward-compatibility verified.
