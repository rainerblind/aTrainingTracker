# Stage 1 Analysis: ATT-6139 - Define quantified non-functional requirements

**Ticket**: [ATT-6139](https://rainerblind.atlassian.net/browse/ATT-6139)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Remediation ID**: A-12 | ASPICE: SWE.1 BP1, BP4  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.4`  
**Branch**: `feature/ATT-6139`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
In the independent ASPICE pre-assessment (baseline develop @ sprint/2026-45.11), finding A-12 noted that non-functional requirements (NFRs) in `docs/requirements/req_process_stability.md` (`REQ-NFR-001..014`) exist only as stub headings or `[Planned]` placeholders without numeric target thresholds, test specifications (`TST-NFR-*`), or concrete measurement procedures.
ASPICE SWE.1 BP1 (*Specify software requirements*) and BP4 (*Determine verification criteria*) mandate that software quality characteristics (battery consumption, startup latency, memory utilization, GNSS accuracy, crash-free sessions, and ANR rate) must be formally quantified, verifiable, and bidirectionally linked to corresponding test specifications.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Codebase & Documentation Audit**:
  * *Living Requirements Audit*: Inspected `docs/requirements/req_process_stability.md`. `REQ-NFR-001..014` exist as stubs without Given-When-Then criteria, verification procedures, or active status.
  * *Test Specification Audit*: Inspected `docs/tests/tst_process_stability.md`. The prefix `NFR` is declared in frontmatter, but contains zero `TST-NFR-*` entries.
  * *Architecture Conformance*: Inspected `docs/architecture.md` Section 4.9. 12 budget rows are documented and linked to `REQ-NFR-003..014`, but lack formal requirement definitions and dedicated TSTs.
  * *Quality Characteristics Coverage*:
    1. **Battery Drain**: Screen-off tracking $\le 5.0\%$/h; screen-on tracking $\le 12.0\%$/h (`REQ-NFR-006` / `TST-NFR-006`, Android Studio Energy Profiler / Battery Historian).
    2. **Peak Heap RAM**: Dedicated `TrackerService` heap $\le 40$ MB; total app heap $\le 150$ MB (`REQ-NFR-005` / `TST-NFR-005`, Android Memory Profiler / Heap Dump).
    3. **Cold Start Latency**: Time-to-initial-display $\le 1200$ ms; time-to-full-display $\le 1500$ ms (`REQ-NFR-015` / `TST-NFR-015`, AndroidX Macrobenchmark / Play Vitals).
    4. **GPS Horizontal Accuracy**: 95th percentile horizontal error $\le 5.0$ m; cross-track RMSE $\le 3.0$ m vs reference track (`REQ-NFR-016` / `TST-NFR-016`, Replay Harness / GNSS Analysis).
    5. **Crash-Free Session Rate**: Production crash-free session rate $\ge 99.5\%$ (`REQ-NFR-017` / `TST-NFR-017`, Google Play Vitals / Crashlytics).
    6. **ANR Rate Ceiling**: User-perceived ANR rate $\le 0.05\%$ (`REQ-NFR-018` / `TST-NFR-018`, Google Play Vitals / StrictMode).
    7. **Budget Quantifications**: Formalize `REQ-NFR-001..004` and `REQ-NFR-007..014` covering EKF timing, stationary pinning, tick latency (< 50 ms), CPU (< 5%), sensor dispatch (< 25 ms), failover (3.5 s), WakeLock bounding, DB write (< 30 ms), storage growth (~360 KB/h), frame budget (< 16.6 ms), map polyline update (< 30 ms), and telemetry rate (1 Hz).

---

## 3. Scope Grounding & Non-Goals
* **In-Scope Goals**:
  * Expand `REQ-NFR-001` through `REQ-NFR-018` in `docs/requirements/req_process_stability.md` with Given-When-Then criteria, numeric thresholds, and concrete measurement procedures.
  * Add dedicated `TST-NFR-001` through `TST-NFR-018` test specifications in `docs/tests/tst_process_stability.md`.
  * Update statuses from `[Planned]` to `[Verified]`, backed by automated contract tests, and synchronize master index tables with 100% parity.
  * Implement `NonFunctionalRequirementsContractTest.kt` in `app/src/test/java/com/atrainingtracker/trainingtracker/build/` to programmatically verify all NFR definitions, numeric targets, measurement procedures, and bidirectional TST links.
* **Out-of-Scope Non-Goals**:
  * Modifying Android production runtime code in `app/src/main/` (budgets reflect existing architectural specifications and verified service implementations).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-NFR-001`..`REQ-NFR-014` in `docs/requirements/req_process_stability.md`, refining `REQ-PRO-052` (*Architectural Resource and Timing Budgets Specification*).
* **Historical Origin & Commit Trace**: Stubs introduced in Sprint 2026-46.1 under ATT-6181 and refined under ATT-6735 to establish SWE.2 BP4 traceability.
* **Root Reason for Existing Formulation**: Placeholders preserved IDs while full SWE.1 BP1/BP4 quantification was prioritized for dedicated sprint ticket ATT-6139.
* **Preservation of Core Invariants**: Section 4.9 architectural budget figures, `TrackerService` WakeLock/threading models, and 100% unit test pass rate remain strictly preserved.

---

## 5. Change-Request Impact Analysis
* **Affected Requirements**: `REQ-NFR-001` through `REQ-NFR-018`, `REQ-PRO-052`.
* **Affected Architecture Elements**: `docs/architecture.md` §4.9, `docs/requirements/req_process_stability.md`, `docs/tests/tst_process_stability.md`.
* **Verification Impact & Test Surface**: `NonFunctionalRequirementsContractTest.kt`, `ArchitectureConformanceContractTest.kt`, `tools/verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: `LOW`. Pure documentation and contract test verification; zero runtime code changes; 100% master index parity maintained.
