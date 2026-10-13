# Stage 4 Implementation Report: ATT-2964 - Suppress in-ride fork decision alerts when all candidate routes share the same direction

**Ticket**: [ATT-2964](https://atrainingtracker.atlassian.net/browse/ATT-2964)  
**Sub-task**: [ATT-3027](https://atrainingtracker.atlassian.net/browse/ATT-3027) (`[Implementation]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2964`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Executive Summary

This deliverable concludes Stage 4 (Software Construction & Implementation) for [ATT-2964](https://atrainingtracker.atlassian.net/browse/ATT-2964) in accordance with requirement `REQ-NAV-043` and test specification `TST-NAV-045`.

We updated `RouteDivergenceDetector.kt` to inspect the relative direction diversity across all candidate route branches. When all candidate branches resolve to the exact same relative heading (e.g. all `STRAIGHT`), `detectDivergence()` returns `null`, suppressing distracting in-ride fork decision alerts when no directional steering decision is required.

---

## 2. Implemented Changes

### 2.1 Navigation Logic (`RouteDivergenceDetector.kt`)
* In `RouteDivergenceDetector.detectDivergence()`:
  - Following the computation and sorting of `branchOptions`:
  ```kotlin
  // If all candidate routes share the exact same relative direction (e.g. all STRAIGHT),
  // there is no directional fork decision for the athlete to make. Suppress the alert (REQ-NAV-043, ATT-2964).
  val distinctDirections = branchOptions.map { it.direction }.distinct()
  if (distinctDirections.size < 2) {
      return null
  }
  ```
  - Preserves all spatial thresholds, lookahead windows, approach bearing vectors, and direction classification logic.

### 2.2 Unit & Contract Tests (`RouteDivergenceDetectorTest.kt`)
* Added `testDetectDivergence_suppressesAlertWhenAllBranchesShareSameDirection`:
  - Validates that when two candidate routes separate by $> 40\text{ m}$ but both continue heading straight relative to the approach corridor (`ForkDirection.STRAIGHT`), `detectDivergence()` returns `null`.
* Added `testDetectDivergence_activatesAlertWhenBranchesHaveDistinctDirections`:
  - Validates that when candidate routes branch into at least two distinct directions (`STRAIGHT` and `RIGHT`), `detectDivergence()` returns a non-null `ForkDecisionState` with 2 branches.
* Validated preservation of existing tests.

---

## 3. Verification & Targeted Test Suite

Targeted unit tests executed cleanly:
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.routes.RouteDivergenceDetectorTest"
```
Result: **BUILD SUCCESSFUL in 16s** (All tests green).
