# Stage 1 Analysis Report: ATT-2964 - Suppress in-ride fork decision alerts when all candidate routes share the same direction

**Ticket**: [ATT-2964](https://atrainingtracker.atlassian.net/browse/ATT-2964)  
**Sub-task**: [ATT-3024](https://atrainingtracker.atlassian.net/browse/ATT-3024) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2964`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Statement & Forensic Root Cause Investigation

During workout navigation with route candidate matching, `RouteDivergenceDetector.detectDivergence()` evaluates upcoming candidate route polylines within a 600m lookahead window. When the spatial cross-track separation between candidate routes exceeds `DIVERGENCE_SEPARATION_THRESHOLD_METERS` (40m), a divergence is confirmed and `detectDivergence()` constructs a `ForkDecisionState` if the fork point is within the 300m alert window.

Forensic field testing identified that in scenarios where multiple candidate routes travel through an intersection or diverge only slightly (such as following parallel road corridors, separate cycle paths beside a main road, or minor geometry splits), all candidate routes resolve to the **exact same relative direction** (e.g. all routes classify as `ForkDirection.STRAIGHT`, or all turn `ForkDirection.RIGHT`).

At such junctions, the athlete has no directional choice to make—continuing along their current trajectory satisfies all candidate routes equally. Displaying a modal "Gabelung voraus" ("Fork Ahead") alert in this scenario represents a false positive that:
1. Causes unnecessary cognitive load and distraction while riding.
2. Clutters the Cockpit HUD when no steering decision is required.
3. Diminishes athlete trust in in-ride fork navigation alerts.

---

## 2. Chesterton's Fence Requirement Archaeology

1. **Original Requirement ID & Target**:
   - `REQ-NAV-028` (*In-Ride Fork Route Candidate Matching and Divergence Detection*, Sprint 2026-41.2, ATT-2481).
2. **Historical Origin & Commit Trace**:
   - Implemented in `RouteDivergenceDetector.kt` to alert athletes before branching routes split, preventing accidental wrong turns onto alternative saved routes.
3. **Root Reason for Existing Formulation**:
   - The original algorithm checked strictly spatial separation (`maxSep > 40.0m`). Any physical separation > 40m was presumed to constitute a meaningful route fork without evaluating whether the routes diverged into distinct angular directions relative to the approach corridor.
4. **Preservation of Core Invariants**:
   - **Genuine Fork Alerting**: Whenever candidate routes branch into at least two distinct directions (e.g. `STRAIGHT` vs `RIGHT`, `LEFT` vs `STRAIGHT`, or `LEFT` vs `RIGHT`), `detectDivergence()` MUST continue to return the full `ForkDecisionState` within the 300m alert window.
   - **Spatial Separation Thresholds**: Lookahead distance (600m), initiation threshold (15m), and confirmation separation threshold (40m) MUST NOT be altered.
   - **Direction Classification Math**: `classifyRelativeDirection(deltaAngle)` angular boundaries (`[-20, 20]` for `STRAIGHT`, `> 20` for `RIGHT`, `< -20` for `LEFT`) remain unchanged.

---

## 3. Scope Bounding & Invariants Enforcement

### 3.1 In Scope
* In `RouteDivergenceDetector.kt`:
  - After computing `branchOptions`:
    ```kotlin
    val distinctDirections = branchOptions.map { it.direction }.distinct()
    if (distinctDirections.size < 2) {
        return null
    }
    ```
  - Suppress fork decision generation when all candidate routes share the identical relative direction.
* In `RouteDivergenceDetectorTest.kt`:
  - Add unit tests verifying that parallel/slight divergence where all candidate routes head `STRAIGHT` returns `null`.
  - Add unit tests verifying that branching routes with distinct directions (`STRAIGHT` vs `RIGHT`) return a non-null `ForkDecisionState`.

### 3.2 Out of Scope
* Changing lookahead or spatial separation constants (`LOOKAHEAD_METERS`, `SAMPLE_STEP_METERS`, `DIVERGENCE_SEPARATION_THRESHOLD_METERS`).
* Modifying `ForkDecisionCard.kt` UI layout (handled separately in `ATT-2962`).
* Modifying `ForkNavigationRepository.kt` state machine.

---

## 4. Proposed Architecture & Verification Strategy

### 4.1 Direction Diversity Gating
```
Candidate Routes (>= 2)
  │
  ▼
Corridor Lookahead & Spatial Separation Check
  │
  ├─> Separation <= 40m ───────────────────────> Return null (No divergence)
  │
  ▼ Separation > 40m confirmed
Classify Branch Relative Directions
  │
  ├─> Distinct Directions < 2 (e.g. all STRAIGHT) ──> Return null (Alert Suppressed)
  │
  ▼ Distinct Directions >= 2 (e.g. STRAIGHT + RIGHT)
Return ForkDecisionState (Alert Active)
```

### 4.2 Verification & Test Strategy
1. Unit tests in `RouteDivergenceDetectorTest.kt`:
   - `testDetectDivergence_suppressesAlertWhenAllBranchesShareSameDirection`
   - `testDetectDivergence_activatesAlertWhenBranchesHaveDistinctDirections`
2. Full clean-room unit regression suite (`./gradlew testDebugUnitTest`).
