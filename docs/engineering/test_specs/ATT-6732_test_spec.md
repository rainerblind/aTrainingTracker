# Stage 2: Requirement & Test Specification - ATT-6732: Replace hallucinated method/DTO signatures and Retrofit claim in interfaces.md with real source contracts

**Ticket**: [ATT-6732](https://rainerblind.atlassian.net/browse/ATT-6732)  
**Sub-task**: [ATT-6888](https://rainerblind.atlassian.net/browse/ATT-6888) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.2`  
**Requirement Mapping**: `REQ-PRO-044` (*ASPICE SWE.2 Software Interface Specification*)  
**Test Spec ID**: `TST-PRO-044`  
**Branch**: `feature/ATT-6732`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-PRO-044)

### 1.1 Problem Statement & Rationale
`docs/architecture/interfaces.md` contained 9 non-existent method signatures across IF-SWE-02, 05, 06, 07, 08, 7 hallucinated class/DTO identifiers (`ClustersDatabaseManager`, `AftermathViewModel`, `TrackingUiState`, `AftermathUiState`, `LocationUiState`, `ExtremaValues`, `WorkoutDetailScreen`), and false claims of `Retrofit` usage (not present in `app/build.gradle`).

### 1.2 Functional & Architectural Requirements
* `docs/architecture/interfaces.md` SHALL replace all 9 hallucinated method signatures and 7 hallucinated classes/DTOs across IF-SWE-01..08 with 100% authentic signatures and types matching `app/src/main/`.
* `docs/architecture/interfaces.md` and `docs/architecture.md` Section 5 SHALL replace `Retrofit` with `OkHttp` and `AftermathViewModel` with `TrackOnMapAftermathViewModel`.
* `AspiceSweAuditContractTest.kt` SHALL dynamically extract documented `fun <name>(` declarations and backticked component/DTO types from `interfaces.md` and verify existence in `app/src/main/` (allowing standard framework types).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Authentic Interface Signatures & Removal of Retrofit)**:
  * *Given* `docs/architecture/interfaces.md` and `docs/architecture.md` Section 5,
  * *When* inspected,
  * *Then* all methods and types match authentic `app/src/main/` source code and `Retrofit` references are completely eliminated.
* **Criterion 2 (Dynamic AST/Regex Grounding Contract Test)**:
  * *Given* `AspiceSweAuditContractTest.kt`,
  * *When* executed against `docs/architecture/interfaces.md`,
  * *Then* all extracted methods and non-framework types resolve to real source declarations in `app/src/main/`.

### 1.4 System Invariants
* Zero modifications to production code in `app/src/main/`.
* 100% unit and contract test pass rate.
* Master requirement and test tables maintain 100% mathematical parity.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-044` (*ASPICE SWE.2 Software Interface Specification IF-SWE-01..IF-SWE-08*), refining `REQ-PRO-034` (*SWE.1/2/4/6 Hardening*) under Epic `ATT-6142`. Targets `docs/architecture/interfaces.md`, `docs/architecture.md` Section 5, and `AspiceSweAuditContractTest.kt`.
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-46.1 under ticket ATT-6180 to address ASPICE pre-assessment finding C-06 (*absent software interface definitions for inter-subsystem boundaries*); refined under ATT-6732.
* **Root Reason for Existing Formulation**: ATT-6180 drafted initial specifications conceptually rather than extracting directly from live sources, resulting in synthetic method names, placeholder DTOs, and assumed Retrofit dependencies.
* **Preservation of Core Invariants**: Correcting interface contracts preserves all SWE.2 architectural boundaries, Mermaid diagrams, and subsystem allocations while eliminating specification drift and guaranteeing zero runtime changes.

---

## 2. Test Specification (TST-PRO-044)

### Test Case 1: `testArchitectureDocument_softwareInterfaceSpecifications` (`TST-PRO-044.1..3`)
* **Scope**: Contract Test (`AspiceSweAuditContractTest.kt`)
* **Action**: Asserts file existence, definitions of `IF-SWE-01`..`08`, mandatory attributes (Provider, Consumer, Operations, Threading, Error Handling), and Section 5 integration with authentic components.
* **Expected Result**: 100% pass rate.

### Test Case 2: `testArchitectureDocument_softwareInterfaceGrounding` (`TST-PRO-044.4`)
* **Scope**: Dynamic Source Grounding Contract Test (`AspiceSweAuditContractTest.kt`)
* **Action**: Extracts `fun <name>(` declarations and backticked component/DTO types from `interfaces.md` and verifies existence in `app/src/main/` (allowing framework types `OutputStream`, `Writer`, `Context`, `LocationListener`, `ExerciseSessionRecord`, etc.).
* **Expected Result**: 0 unresolved methods or types.

### Test Case 3: Governance Parity & Tier 1 Targeted Regression Suite (`TST-PRO-044.5`)
* **Command**: `python3 tools/verify_requirement_governance.py && ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-044.1..3` | Contract | `testArchitectureDocument_softwareInterfaceSpecifications` | `REQ-PRO-044.1`, `REQ-PRO-044.2` | Specified |
| `TST-PRO-044.4` | Contract | `testArchitectureDocument_softwareInterfaceGrounding` | `REQ-PRO-044.1`, `REQ-PRO-044.3` | Specified |
| `TST-PRO-044.5` | Regression | `AspiceSweAuditContractTest` | `REQ-PRO-044`, `REQ-PRO-034` | Specified |
