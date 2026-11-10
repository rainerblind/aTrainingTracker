# Stage 1 Analysis: ATT-6183 - Write Software Integration Verification Strategy

**Ticket**: [ATT-6183](https://rainerblind.atlassian.net/browse/ATT-6183)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6183`  
**Requirement Mapping**: `REQ-PRO-045` | **Test Mapping**: `TST-PRO-045`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
ASPICE pre-assessment finding C-09 flagged that aTrainingTracker lacks a formal Software Integration Verification Strategy (ASPICE SWE.5 BP1). Currently, integration verification is informally conflated with unit testing during the Sprint Integration Gate. Without a documented integration sequence across architectural interfaces (`IF-SWE-01`..`IF-SWE-08`), structured test levels (Robolectric / Emulator / Hardware), explicit entry/exit criteria, and regression selection protocols, subsystem integration defects risk escaping into release builds.

---

## 2. Root Cause Analysis & Independent Verification (4-Augen-Prinzip)
* **Codebase Interface Grounding**:
  * Unit test suite is mature (~1200+ JVM tests in `app/src/test/`), but `app/src/androidTest/` is not yet formalized in CI.
  * Architectural boundaries (`IF-SWE-01` through `IF-SWE-08`) established in `ATT-6180` provide concrete seams for subsystem integration.
  * Existing desk simulators (`tools/ble_hr_simulator.py`, `tools/ble_power_simulator.py`, `tools/fake_gps.py`, `tools/replay_workout.py`, `tools/simulate_kill.py`) provide Level 3 verification capability but lack formal integration governance.
* **SWE.5 Gap Analysis**:
  * No bottom-up integration order defined for inter-subsystem dependencies.
  * No formalization of verification levels, entry/exit criteria, or regression test selection tiers.
  * `AspiceSweAuditContractTest.kt` audits SWE.2 interfaces but does not yet assert SWE.5 integration strategy presence.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail (REQ-PRO-029)
* **In-Scope Goals**:
  * Author authoritative strategy `docs/architecture/integration_strategy.md` covering bottom-up integration order across `IF-SWE-01`..`IF-SWE-08`, 3 verification levels (JVM/Robolectric, Emulator, Physical/Simulators), entry/exit criteria, regression tiers (Tier 1 targeted vs. Tier 2 Sprint Gate), and defect bisection.
  * Integrate Section 6 into `docs/architecture.md` linking to the integration strategy.
  * Add automated verification in `AspiceSweAuditContractTest.kt` (`REQ-PRO-045`, `TST-PRO-045`).
  * Define `REQ-PRO-045` in `req_process_stability.md` and `TST-PRO-045` in `tst_process_stability.md`.
* **Out-of-Scope Non-Goals**: Implementing new instrumented tests in `androidTest` or altering production application code.
* **Complexity Rating**: 2 SP (Standard). Bounded strictly to architecture documentation, living doc sync, and contract test.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-045` (*ASPICE SWE.5 Software Integration Verification Strategy*), refining `REQ-PRO-034` under Epic `ATT-6142`. Targets `docs/architecture/integration_strategy.md`, `docs/architecture.md`, and `AspiceSweAuditContractTest.kt`.
* **Historical Origin & Commit Trace**: Pre-assessment finding C-09 (2026-11-08) and ticket ATT-6183.
* **Root Reason for Existing Formulation**: High-level test runs were historically considered sufficient; ASPICE Level 2 mandates an explicit integration strategy.
* **Preservation of Core Invariants**: Existing SWE.2 interface definitions (`IF-SWE-01`..`IF-SWE-08`), contract tests, and Two-Tier test governance remain 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution
* **Integration Sequence**: Bottom-up order: Persistence/Databases (`IF-SWE-06`) $\to$ Hardware/HAL & BANALService (`IF-SWE-04`, `IF-SWE-03`) $\to$ Domain Calculators & TrackerService (`IF-SWE-05`) $\to$ Repositories & Exporters/Cloud (`IF-SWE-02`, `IF-SWE-07`, `IF-SWE-08`) $\to$ ViewModels & Jetpack Compose UI (`IF-SWE-01`).
* **Verification Levels**: Level 1 (JVM/Robolectric unit & subsystem integration), Level 2 (Instrumented Android/Emulator), Level 3 (Physical hardware & script simulators).
* **Regression & Governance**: Formalize Tier 1 (targeted in-sprint per ticket) vs Tier 2 (full Sprint Integration Gate) and defect bisection workflow.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: 100% build test pass rate; zero production code modification; existing requirement numbering intact.
* **Risk Rating**: **LOW**. Process & architectural documentation with build contract verification.
