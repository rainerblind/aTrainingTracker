# Stage 2 Requirement & Test Specification: ATT-2964 - Suppress in-ride fork decision alerts when all candidate routes share the same direction

**Ticket**: [ATT-2964](https://atrainingtracker.atlassian.net/browse/ATT-2964)  
**Sub-task**: [ATT-3025](https://atrainingtracker.atlassian.net/browse/ATT-3025) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2964`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Requirement Specification (REQ-NAV-043)

### 1.1 Requirement Definition
* **Requirement ID**: `REQ-NAV-043`
* **Title**: In-Ride Fork Decision Alert Direction-Diversity Gating & False-Positive Suppression
* **Type**: Functional & Navigation Logic Specification
* **Target Release**: `V4.9.39`
* **Status**: Specified
* **Amends/Complements**: Amends `REQ-NAV-028` (*In-Ride Fork Route Candidate Matching and Divergence Detection*, Sprint 2026-41.2, ATT-2481).
* **Parent Ticket**: ATT-2964

### 1.2 Description
The system shall gate in-ride fork decision alerts behind relative direction diversity across candidate routes to eliminate false positives at junctions where all candidate routes follow the same relative directional heading:

1. *Direction Diversity Gating (`RouteDivergenceDetector.kt`)*:
   - In `RouteDivergenceDetector.detectDivergence()`, after calculating `branchOptions` and classifying each branch's relative `ForkDirection` (`LEFT`, `STRAIGHT`, `RIGHT`), the system SHALL compute the distinct directional set:
     ```kotlin
     val distinctDirections = branchOptions.map { it.direction }.distinct()
     ```
   - If `distinctDirections.size < 2`, `detectDivergence()` SHALL immediately return `null`.
2. *Decision Point Activation*:
   - If `distinctDirections.size >= 2` (e.g. `STRAIGHT` and `RIGHT`, `LEFT` and `STRAIGHT`, or `LEFT` and `RIGHT`), `detectDivergence()` SHALL return the populated `ForkDecisionState` within the alert window (`ALERT_WINDOW_MIN_METERS < distanceToFork <= ALERT_WINDOW_MAX_METERS`).
3. *Preservation of System Invariants*:
   - Spatial separation thresholds (`DIVERGENCE_INITIATION_THRESHOLD_METERS = 15.0m`, `DIVERGENCE_SEPARATION_THRESHOLD_METERS = 40.0m`, `LOOKAHEAD_METERS = 600.0m`).
   - Angular relative classification ranges (`deltaAngle in -20.0..20.0 -> STRAIGHT`, `> 20.0 -> RIGHT`, `< -20.0 -> LEFT`).
   - Active route candidate matching restriction (`REQ-MAP-041` / `ATT-2942`).
   - 100% clean-room test pass rate across all unit tests.

---

### 1.3 Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Amends `REQ-NAV-028` (*In-Ride Fork Route Candidate Matching and Divergence Detection*, Sprint 2026-41.2, ATT-2481).
2. **Historical Origin & Commit Trace**: Introduced in Sprint 2026-41.2 (`ATT-2481`).
3. **Root Reason for Existing Formulation**: The original detector relied solely on physical Euclidean separation exceeding 40m without evaluating whether candidate routes actually branched in diverging directions.
4. **Preservation of Core Invariants**: Genuine multi-direction splits, spatial threshold checks, and corridor projection tolerances remain 100% functional.

---

## 2. Acceptance Criteria (Given-When-Then)

* **Scenario 1: Single-Direction Divergence Suppression**:
  - *Given* two or more candidate routes that physically diverge by $> 40\text{ m}$ ahead,
  - *When* all candidate routes evaluate to the exact same `ForkDirection` (e.g. all `STRAIGHT`),
  - *Then* `RouteDivergenceDetector.detectDivergence()` returns `null` and no fork decision alert is raised.

* **Scenario 2: Multi-Direction Divergence Activation**:
  - *Given* candidate routes that diverge ahead into at least two distinct `ForkDirection`s (e.g. one `STRAIGHT` and one `RIGHT`),
  - *When* the athlete is within the alert window ($0 < \text{distanceToFork} \le 300\text{ m}$),
  - *Then* `RouteDivergenceDetector.detectDivergence()` returns a valid `ForkDecisionState` with all branch options.

---

## 3. Test Specification (TST-NAV-045)

### 3.1 Unit Tests (`RouteDivergenceDetectorTest.kt`)
1. `testDetectDivergence_suppressesAlertWhenAllBranchesShareSameDirection`:
   - Setup two candidate routes that separate by $> 40\text{ m}$ but both continue heading `STRAIGHT` (e.g. bearing delta $< 20^\circ$).
   - Assert `detectDivergence()` returns `null`.
2. `testDetectDivergence_activatesAlertWhenBranchesHaveDistinctDirections`:
   - Setup two candidate routes where one branches `STRAIGHT` (or `LEFT`) and one branches `RIGHT` ($> 20^\circ$).
   - Assert `detectDivergence()` returns a non-null `ForkDecisionState`.
3. `testDetectDivergence_existingContractPreserved`:
   - Validate existing two-splitting-route test continues to pass without regression.

### 3.2 Full Regression Suite
* Run `./gradlew testDebugUnitTest` verifying 100% test pass rate.

---

## 4. Traceability Matrix

| Requirement Clause | Test Specification ID | Verification Method | Status |
| :--- | :--- | :--- | :--- |
| `REQ-NAV-043` (Clause 1) | `TST-NAV-045.1` | `RouteDivergenceDetectorTest` | Specified |
| `REQ-NAV-043` (Clause 2) | `TST-NAV-045.2` | `RouteDivergenceDetectorTest` | Specified |
| `REQ-NAV-043` (Clause 3) | `TST-NAV-045.3` | Clean-room full test suite | Specified |
