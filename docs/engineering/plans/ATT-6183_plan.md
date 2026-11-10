# Stage 3: Implementation Plan - ATT-6183: Write Software Integration Verification Strategy

**Ticket**: [ATT-6183](https://rainerblind.atlassian.net/browse/ATT-6183)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6183`  
**Requirement Mapping**: `REQ-PRO-045` (`REQ-PRO-045.1`..`REQ-PRO-045.3`)  
**Test Mapping**: `TST-PRO-045` (`TST-PRO-045.1`..`TST-PRO-045.4`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
Independent ASPICE pre-assessment finding C-09 highlighted that aTrainingTracker lacks a formal Software Integration Verification Strategy (ASPICE SWE.5 BP1). Integration testing was historically conflated with unit testing during the Sprint Integration Gate without a defined bottom-up integration order across architectural interfaces (`IF-SWE-01`..`IF-SWE-08`), structured test levels (Robolectric / Emulator / Hardware & Simulators), explicit entry/exit criteria, or structured regression tiers. This plan defines the construction of `docs/architecture/integration_strategy.md`, its summary integration into `docs/architecture.md` Section 6, and automated contract testing in `AspiceSweAuditContractTest.kt`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-045` (*ASPICE SWE.5 Software Integration Verification Strategy*)
  * `REQ-PRO-045.1`: Create `docs/architecture/integration_strategy.md` defining bottom-up sequence (`IF-SWE-01`..`IF-SWE-08`), 3 test levels, entry/exit criteria, and two-tier regression selection.
  * `REQ-PRO-045.2`: Integrate Section 6 Software Integration Verification Strategy into `docs/architecture.md`.
  * `REQ-PRO-045.3`: Automate integration strategy contract verification in `AspiceSweAuditContractTest.kt`.
* **Test Mapping**: `TST-PRO-045` (`TST-PRO-045.1`..`TST-PRO-045.4`)
  * `TST-PRO-045.1`: Strategy file existence & core section completeness (Sequence, Levels, Criteria, Regression).
  * `TST-PRO-045.2`: Complete interface seam grounding (`IF-SWE-01` through `IF-SWE-08`).
  * `TST-PRO-045.3`: Architecture master document Section 6 integration & link verification.
  * `TST-PRO-045.4`: Targeted unit test execution (`AspiceSweAuditContractTest`).

---

## 3. System Invariants & Preserved Behavior
1. **SWE.2 & SWE.5 Integrity**: Interface specifications (`docs/architecture/interfaces.md`) and living documentation structures remain 100% intact.
2. **Continuous SWE.2 Maintenance**: `docs/architecture.md` is updated with Section 6 in lockstep with the strategy file.
3. **Zero Production Code Impact**: No changes to Android source code in `app/src/main/` or database schemas.

---

## 4. Proposed Architectural Changes
* **New Strategy Document (`docs/architecture/integration_strategy.md`)**:
  * Define bottom-up sequence: Persistence (`IF-SWE-06`) $\to$ Sensors/BANALService (`IF-SWE-04`, `03`) $\to$ Domain/TrackerService (`IF-SWE-05`) $\to$ Repositories/Exporters/Cloud (`IF-SWE-02`, `07`, `08`) $\to$ ViewModels/UI (`IF-SWE-01`).
  * Define verification levels: Level 1 (JVM/Robolectric), Level 2 (Instrumented/Emulator), Level 3 (Hardware & Simulators: BLE HR/Power, fake GPS, replay workout, kill simulation).
  * Detail formal Entry/Exit criteria and Two-Tier regression selection (Tier 1 targeted, Tier 2 Sprint Gate) with git bisection.
* **Architecture Master Document (`docs/architecture.md`)**:
  * Add Section 6 "Software Integration and Verification Strategy (SWE.5.BP1)" summarizing integration sequence and linking to `integration_strategy.md`.
* **Contract Test Suite Expansion (`AspiceSweAuditContractTest.kt`)**:
  * Add `@Test fun testArchitectureDocument_softwareIntegrationStrategy()` covering `TST-PRO-045.1`..`TST-PRO-045.3`.
* **UI Consistency (Rule 23)**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Author `docs/architecture/integration_strategy.md`)**: Write comprehensive strategy document covering integration sequence, 3 verification levels, entry/exit criteria, and regression selection (`REQ-PRO-045.1`, `TST-PRO-045.1`, `TST-PRO-045.2`).
2. **Step 2 (Integrate Section 6 in `docs/architecture.md`)**: Add Section 6 summary table and link to `integration_strategy.md` (`REQ-PRO-045.2`, `TST-PRO-045.3`).
3. **Step 3 (Expand `AspiceSweAuditContractTest.kt`)**: Implement `testArchitectureDocument_softwareIntegrationStrategy()` validating presence of strategy file, required sections, all 8 interfaces, and Section 6 linkage (`REQ-PRO-045.3`, `TST-PRO-045.1`..`TST-PRO-045.3`).
4. **Step 4 (Targeted Test Execution)**: Run targeted test suite:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"
   ```
   Confirm 100% PASS across all audit contract tests (`TST-PRO-045.4`).

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit test suite `com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest`.
* **Rollback Plan**: Changes are isolated to `feature/ATT-6183`; revert via `git checkout -- .` and `git clean -fd`.
