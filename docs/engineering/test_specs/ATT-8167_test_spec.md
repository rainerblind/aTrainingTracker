# Stage 2: Requirement & Test Specification - ATT-8167: Update the risk register to ASPICE 4.0 MAN.5 with process risks and per-sprint review

**Ticket**: [ATT-8167](https://rainerblind.atlassian.net/browse/ATT-8167)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_03_Nacht`  
**Requirement Mapping**: `REQ-STP-010` (`REQ-STP-010.1`, `REQ-STP-010.2`, `REQ-STP-010.3`, `REQ-STP-010.4`)  
**Test Spec ID**: `TST-STP-010` (`TST-STP-010.1`, `TST-STP-010.2`, `TST-STP-010.3`, `TST-STP-010.4`)  
**Branch**: `feature/ATT-8167` | **Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-16  

---

## 1. Requirement Specification (REQ-STP-010)
* **Attributes**: Priority: `High` | Type: `Non-Functional` | Verification Method: `Unit / Review` | Status: `[Verified]`
* **REQ-STP-010.1 (Authoritative Risk Register & ASPICE 4.0 MAN.5 Alignment)**: `docs/management/risk_register.md` SHALL adhere to ASPICE 4.0 `MAN.5` (BP1–BP6), dual-citing legacy `MAN.3.BP5` for backwards compatibility. It SHALL maintain a 5x5 scoring rubric ($P, I \in [1..5]$, $E = P \times I$), exposure tiers (Low 1–6, Medium 8–12, High 15–25), and mandatory linked remediation tickets for all High-exposure risks ($E \ge 15$). It SHALL calibrate `RISK-002` ($P=4, I=4 \rightarrow E=16$), resolve `RISK-003` to concrete maintenance scope without placeholder strings, frame `ATT-7902` as tests-only scaffolding, close the `2026-46.6 (Close)` log gap, and catalog process risks `RISK-008`..`RISK-011`.
* **REQ-STP-010.2 (Per-Sprint Dual-Ceremony Review Governance)**: `.agents/skills/sprint-planner/SKILL.md` SHALL mandate risk register reviews at BOTH Ceremony 1 (Sprint-Start) and Ceremony 2 (Sprint-Close / Retrospective). Ceremony 2 SHALL require either updating risk scores/mitigations or adding an explicit 'No Change / Risks Monitored & Stable' row for the closing sprint.
* **REQ-STP-010.3 (Canonical Directory Boundaries)**: `docs/management/risk_register.md` SHALL reside strictly in `docs/management/` outside `docs/engineering/` to preserve Rule 67 canonical boundaries.
* **REQ-STP-010.4 (Automated Contract Verification)**: `ReleaseCiAndLintConfigContractTest.kt` SHALL verify presence of `risk_register.md`, section headers (`MAN.5` and `MAN.3.BP5`), all risks `RISK-001` through `RISK-011`, High-exposure tickets (`ATT-6148`, `ATT-6150`), and Ceremony 1 & 2 integration in `sprint-planner/SKILL.md`.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-STP-010` and `TST-STP-010`. Targets `docs/management/risk_register.md`, `.agents/skills/sprint-planner/SKILL.md`, and `ReleaseCiAndLintConfigContractTest.kt`.
* **Historical Origin & Commit Trace**: Established in Sprint 2026-46.1 via `ATT-6151` (`03875225`); extended in `ATT-8151` (`RISK-007`).
* **Root Reason for Existing Formulation**: Authored under ASPICE 3.1 MAN.3.BP5 before MAN.5 became an independent process in ASPICE 4.0; Ceremony 2 review was left informal without contract enforcement.
* **Preservation of Core Invariants**: 5x5 matrix ($P \times I$, $E \in [1..25]$); High-exposure mitigation rule ($E \ge 15$); backward compatibility for `ReleaseCiAndLintConfigContractTest.kt`; historical review logs preserved; `ANT-Android-SDKs` at `0e8cea71`.

---

## 3. Acceptance Criteria (Given-When-Then)
* **Criterion 1 (ASPICE 4.0 MAN.5 Alignment & Dual Citation)**: *Given* `risk_register.md`, *When* inspected, *Then* it references ASPICE 4.0 `MAN.5` (BP1–BP6) while preserving `MAN.3.BP5` legacy reference for contract test compatibility.
* **Criterion 2 (Risk Calibration & Mitigation Accuracy)**: *Given* registered risks in `risk_register.md`, *When* evaluated, *Then* `RISK-002` is re-scored to $P=4, I=4 \rightarrow E=16$ (High), `RISK-003` has a concrete backlog link without placeholder text, `ATT-7902` is framed as tests-only scaffolding, and `2026-46.6 (Close)` SKIPPED review is documented.
* **Criterion 3 (Process Risks Registration)**: *Given* the risk register, *When* audited for process risks, *Then* it includes `RISK-008` (PO Bottleneck), `RISK-009` (Rubber-Stamp Gates), `RISK-010` (Rule Churn), and `RISK-011` (LLM Provider Quota/Model Shifts) with owners and tickets (`ATT-8162`, `ATT-8150`, `ATT-8166`, `ATT-8213`).
* **Criterion 4 (Ceremony 2 Sprint Review Cadence)**: *Given* Ceremony 2 in `sprint-planner/SKILL.md`, *When* executed at Sprint-Close, *Then* it mandates a Sprint Review Log entry in `risk_register.md` (delta update or explicit 'No Change / Risks Monitored & Stable').
* **Criterion 5 (Contract Test Verification)**: *Given* `ReleaseCiAndLintConfigContractTest.kt`, *When* executed, *Then* it asserts `MAN.5`, `MAN.3.BP5`, `RISK-001` through `RISK-011`, High-exposure tickets (`ATT-6148`, `ATT-6150`), and Ceremony 1 & 2 integration with 100% pass rate.

---

## 4. Test Specification (TST-STP-010)
* **TST-STP-010.1 (MAN.5 Structure & Dual-Citation Audit)**: Verify in `ReleaseCiAndLintConfigContractTest.kt` that `risk_register.md` exists and contains headers for ASPICE 4.0 `MAN.5`, `MAN.3.BP5`, scoring rubric, exposure matrix, and Sprint Review Log.
* **TST-STP-010.2 (Process Risks & High-Exposure Mitigations Audit)**: Verify in `ReleaseCiAndLintConfigContractTest.kt` that all risks `RISK-001` through `RISK-011` are registered, and High-exposure risks `RISK-001` ($E=16$) and `RISK-002` ($E=16$) link to active mitigation tickets `ATT-6148` and `ATT-6150`.
* **TST-STP-010.3 (Dual-Ceremony Sprint Governance Integration Audit)**: Verify in `ReleaseCiAndLintConfigContractTest.kt` and `sprint-planner/SKILL.md` that Ceremony 1 and Ceremony 2 mandate Sprint-Start and Sprint-Close risk register review checkpoints.
* **TST-STP-010.4 (Rule 67 Canonical Engineering Boundary Guard)**: Verify that `python3 tools/verify_requirement_governance.py` passes with exit code 0, confirming canonical placement in `docs/management/` and 100% living doc parity.

---

## 5. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-STP-010.1` | Unit/Contract | `docs/management/risk_register.md`, `ReleaseCiAndLintConfigContractTest.kt` | `REQ-STP-010.1` | Specified |
| `TST-STP-010.2` | Unit/Contract | `docs/management/risk_register.md`, `ReleaseCiAndLintConfigContractTest.kt` | `REQ-STP-010.1` | Specified |
| `TST-STP-010.3` | Unit/Contract | `.agents/skills/sprint-planner/SKILL.md`, `ReleaseCiAndLintConfigContractTest.kt` | `REQ-STP-010.2` | Specified |
| `TST-STP-010.4` | Governance | `tools/verify_requirement_governance.py` | `REQ-STP-010.3`, `REQ-STP-010.4` | Specified |
