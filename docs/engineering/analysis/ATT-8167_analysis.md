# Stage 1 Analysis: ATT-8167 - Update the risk register to ASPICE 4.0 MAN.5 with process risks and per-sprint review

**Ticket**: [ATT-8167](https://rainerblind.atlassian.net/browse/ATT-8167)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_03_Nacht`  
**Branch**: `feature/ATT-8167` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-16  

---

## 1. Problem Statement & Motivation
ASPICE 4.0 Audit 2026-11-15 identified Finding `MAN.5 (observation)` against `docs/management/risk_register.md`:
1. **Outdated ASPICE Reference**: Document references ASPICE 3.1 `MAN.3.BP5` (L4, L11, L104); in ASPICE 4.0, Risk Management is a dedicated process `MAN.5` (BP1–BP6).
2. **Uncalibrated Stagnant Scores**: Scores remained unchanged since 2026-11-10 ($E=15$ for `RISK-002`) despite empirical manifestations (unmounted UI islands, doc hallucinations, 34 Stage 6 bounces).
3. **Log & Mitigation Inconsistencies**: Sprint Review Log lacks a `2026-46.6 (Close)` entry; `RISK-003` lists unlinked placeholder 'Planned Target SDK Ticket'; `ATT-7902` is cited as mitigation without noting PO rejection of core production Kotlin migration (ADR-001).
4. **Missing Process Risks**: Multi-agent development risks are uncataloged: human review bottleneck, governance rule churn, rubber-stamp gates, and LLM provider quota/model shifts.
5. **Single-Sided Review Cadence**: `REQ-STP-010` checks Ceremony 1 (Sprint-Start) only; Ceremony 2 lacks contract enforcement requiring an explicit sprint closeout review entry.

---

## 2. Root Cause Analysis & Independent Peer Review
* **RCA 1 (Legacy Taxonomy)**: Baseline created in Sprint 46.1 (`ATT-6151`) used ASPICE 3.1 MAN.3.BP5; not updated during ASPICE 4.0 transition.
* **RCA 2 (Static Scoring)**: Lack of a periodic re-calibration trigger caused `RISK-002` probability to stagnate at $P=3$ despite repeated real-world manifestations.
* **RCA 3 (Process Blindspot)**: Initial register captured technical/hardware risks (`RISK-001`–`RISK-006`); autonomous multi-agent operational risks were omitted.
* **RCA 4 (Unenforced Ceremony 2 Log)**: `sprint-planner/SKILL.md` made Ceremony 2 risk updates optional ("when new risks surface") with zero contract check.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Audited `docs/management/risk_register.md`: Confirmed `MAN.3.BP5` references, missing `46.6 (Close)` row, and placeholder ticket in `RISK-003`.
  * Audited `ReleaseCiAndLintConfigContractTest.kt` L777: Confirms hardcoded assertion on `"MAN.3.BP5"`.
  * Audited `sprint-planner/SKILL.md` L255: Confirms conditional Ceremony 2 risk review without mandatory "no change" entry.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Update `docs/management/risk_register.md` to ASPICE 4.0 `MAN.5` (BP1–BP6), preserving `MAN.3.BP5` legacy alias for contract test backward compatibility.
  2. Re-score `RISK-002` ($P=4, I=4 \rightarrow E=16$, High Exposure), update active mitigations, and clarify `ATT-7902` tests-only scope.
  3. Resolve `RISK-003` placeholder ticket by linking concrete backlog ticket / ongoing maintenance scope.
  4. Add missing `2026-46.6 (Close)` row in Sprint Review Log documenting SKIPPED status (Finding K2).
  5. Register process risks: `RISK-008` (Human Review Bottleneck), `RISK-009` (Rubber-Stamp Gates), `RISK-010` (Rule Churn), and `RISK-011` (LLM Provider Quota/Model Shifts) with owners and tickets (`ATT-8162`, `ATT-8150`, `ATT-8166`, `ATT-8213`).
  6. Extend `REQ-STP-010` / `TST-STP-010` and `sprint-planner/SKILL.md` Ceremony 2 to mandate per-sprint close review entry (update or explicit 'no change' log).
  7. Update `ReleaseCiAndLintConfigContractTest.kt` to verify MAN.5, process risks `RISK-001`..`RISK-011`, and Ceremony 2 integration.
* **Out-of-Scope Non-Goals**: Production code changes in `app/src/main/`; refactoring unrelated governance tools.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-STP-010` (`REQ-STP-010.1` Register Structure & MAN.5, `REQ-STP-010.2` Dual Ceremony 1/2 Review, `REQ-STP-010.4` Contract Test) and `TST-STP-010`.
* **Historical Origin & Commit Trace**: Established in Sprint 2026-46.1 via `ATT-6151` (`03875225`); extended in `ATT-8151` (`RISK-007`).
* **Root Reason for Existing Formulation**: ASPICE 3.1 MAN.3.BP5 was current; initial focus was Sprint-Start baseline creation.
* **Preservation of Core Invariants**: 5x5 matrix ($P \times I$, $E \in [1..25]$); High-exposure mitigation rule ($E \ge 15$); canonical path `docs/management/risk_register.md`; submodule `ANT-Android-SDKs` at `0e8cea71`.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-STP-010` (`req_store_presence.md`); `TST-STP-010` (`tst_store_presence.md`).
* **Affected Architecture Elements**: `docs/management/risk_register.md`, `.agents/skills/sprint-planner/SKILL.md`, `ReleaseCiAndLintConfigContractTest.kt`.
* **Verification Impact & Test Surface**: Contract test `ReleaseCiAndLintConfigContractTest`, `verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: **LOW** (1 SP documentation & contract test update; zero production code modification).

---

## 6. Architectural Strategy & High-Level Solution
* **ASPICE 4.0 MAN.5 Alignment**: Update header, purpose, and mapping to `MAN.5 (BP1–BP6)` with dual citation of legacy `MAN.3.BP5`.
* **Register Calibration & Process Risks**: Re-score `RISK-002` ($E=16$); resolve `RISK-003` link; insert `2026-46.6 (Close)` log; register `RISK-008`..`RISK-011`.
* **Dual-Ceremony Cadence**: Mandate in `sprint-planner/SKILL.md` Ceremony 2 that Coordinator logs risk register review (git diff or explicit 'no change' entry).
* **Contract Test Expansion**: Update `ReleaseCiAndLintConfigContractTest.kt` to assert `MAN.5`, `RISK-001`..`RISK-011`, and Ceremony 2 integration.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: (1) `app/src/main/` strictly untouched; (2) Pure documentation and Kotlin unit test; (3) All historical sprint review logs preserved; (4) `ANT-Android-SDKs` untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
* **Risk Rating**: **LOW** (1 SP process/governance improvement backed by automated contract test).
