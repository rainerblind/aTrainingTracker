# Stage 1 Analysis: ATT-6182 - Add Automated Architecture Conformance Tests

**Ticket**: [ATT-6182](https://rainerblind.atlassian.net/browse/ATT-6182)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6182`  
**Requirement Mapping**: `REQ-PRO-050` | **Test Mapping**: `TST-PRO-050`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
ASPICE pre-assessment finding C-08 identified that layering rules defined in `docs/architecture.md` lack automated test enforcement, allowing architectural drift to accumulate unnoticed (e.g. direct UI-to-DatabaseManager bindings). To achieve ASPICE SWE.2/SWE.3 architectural compliance, automated architecture conformance testing must be introduced into the test suite, enforcing layer boundaries and dependency directions while ratcheting down existing baseline debt.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  * `docs/architecture.md` specifies a 5-layer model (Presentation -> Domain -> Services -> Data -> Persistence). However, without automated gate checks, legacy screens and ViewModels established direct calls to raw SQLite `*DatabaseManager` singletons instead of routing through repositories.
  * No continuous unit test fails when a newly added Compose screen directly imports or invokes database managers.
* **Independent Peer Review & Verification (ATT-3534)**:
  * *Codebase Audit*: Confirmed 388 direct references to `DatabaseManager` / `DbHelper` across `com.atrainingtracker.trainingtracker.ui`.
  * *Architecture Conformance Feasibility*: Verified that a lightweight JVM AST/source scanner (`ArchitectureConformanceAuditTest` in `com.atrainingtracker.architecture`) avoids heavy third-party plugin overhead (Konsist/ArchUnit) while reliably asserting layer invariants:
    1. Pure Compose UI screens/components must not import or invoke `*DatabaseManager` / `*DbHelper`.
    2. Domain calculators/engines must not import Android UI or Compose dependencies.
  * *Allow-List Ratchet*: Captures existing baseline callers on `sprint/2026-46.1`, ensuring 100% test pass while blocking any net-new layer violations.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail (REQ-PRO-029)
* **In-Scope Goals**:
  * Author `ArchitectureConformanceAuditTest` in `com.atrainingtracker.architecture` checking UI-layer and domain-layer isolation.
  * Maintain allow-list of existing baseline callers with ratcheting tripwire detection.
  * Register `ArchitectureConformanceAuditTest` in `LEGITIMATE_GOVERNANCE_GUARDS` in `SourceTextReadingAuditContractTest.kt`.
  * Define `REQ-PRO-050` in `req_process_stability.md` and `TST-PRO-050` in `tst_process_stability.md`.
* **Out-of-Scope Non-Goals**:
  * Refactoring existing UI database calls (handled in repository migration tickets `ATT-432`, `ATT-6426`, `ATT-6427`).
  * Modifying `docs/architecture.md` (read-only in this ticket to avoid merge collision with concurrent `ATT-3556`).
* **Complexity Rating**: 2 SP (Standard). Bounded strictly to architecture test construction, baseline allow-list, and governance registration.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022, Rule 68)
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-050` (*Automated Architecture Conformance Testing*), refining `REQ-PRO-034` under Epic `ATT-6142`. Targets `ArchitectureConformanceAuditTest.kt`, `SourceTextReadingAuditContractTest.kt`.
* **Historical Origin & Commit Trace**: Pre-assessment finding C-08 (2026-11-08) and ticket ATT-6182.
* **Root Reason for Existing Formulation**: Project relied on architectural documentation without automated CI tripwires, leading to unpoliced layering drift.
* **Preservation of Core Invariants**: Existing UI and background services continue uninterrupted; baseline allow-list prevents false positives.
* **Side-Effect Audit (Rule 68)**: Zero impact on `docs/architecture.md` concurrent work; zero runtime APK bytecode modifications.

---

## 5. Architectural Strategy & High-Level Solution
* Implement `ArchitectureConformanceAuditTest` in `com.atrainingtracker.architecture`:
  * Asserts UI screens/components do not introduce new `DatabaseManager` bindings outside the baseline set.
  * Asserts domain calculators remain pure JVM without Android UI coupling.
  * Contains negative tripwire tests proving layer violations fail the build.
* Register test in `SourceTextReadingAuditContractTest.kt` (`LEGITIMATE_GOVERNANCE_GUARDS`).
* Document in living requirements/tests (`REQ-PRO-050`, `TST-PRO-050`).

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Target Release `V5.0.0`; Active Sprint `2026-46.1`; 100% pass on all 3,660+ unit tests; zero modification to `docs/architecture.md`.
* **Risk Rating**: **LOW**. Test-scope governance guard with recorded baseline preventing build regression.
