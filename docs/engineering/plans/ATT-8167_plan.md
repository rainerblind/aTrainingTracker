# Stage 3: Implementation Plan - ATT-8167: Update the risk register to ASPICE 4.0 MAN.5 with process risks and per-sprint review

**Ticket**: [ATT-8167](https://rainerblind.atlassian.net/browse/ATT-8167)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_03_Nacht`  
**Requirement Mapping**: `REQ-STP-010` (`REQ-STP-010.1`, `REQ-STP-010.2`, `REQ-STP-010.3`, `REQ-STP-010.4`)  
**Test Mapping**: `TST-STP-010` (`TST-STP-010.1`, `TST-STP-010.2`, `TST-STP-010.3`, `TST-STP-010.4`)  
**Branch**: `feature/ATT-8167` | **Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-16  

---

## 1. Problem Description & Background
ASPICE 4.0 Audit Finding `MAN.5 (observation)` against `docs/management/risk_register.md` identified: (1) Outdated reference to ASPICE 3.1 `MAN.3.BP5`; (2) Static risk score for `RISK-002` ($E=15$) despite empirical manifestations; (3) Missing historical `2026-46.6 (Close)` log row, unlinked placeholder for `RISK-003`, and missing tests-only distinction for `ATT-7902`; (4) Absence of key multi-agent process risks (`RISK-008`..`RISK-011`); (5) Lack of contract-enforced Ceremony 2 risk review cadence.

---

## 2. Traceability & Requirements Mapping
* **`REQ-STP-010.1` $\rightarrow$ `TST-STP-010.1`, `TST-STP-010.2`**: Align `risk_register.md` to ASPICE 4.0 `MAN.5` (BP1–BP6) with dual citation of `MAN.3.BP5`, re-score `RISK-002` ($E=16$), resolve `RISK-003` & `ATT-7902`, document `46.6 (Close)`, and register process risks `RISK-008`..`RISK-011`.
* **`REQ-STP-010.2` $\rightarrow$ `TST-STP-010.3`**: Update `sprint-planner/SKILL.md` Ceremony 2 to mandate a risk review row at sprint close (delta update or explicit 'No Change / Risks Monitored & Stable').
* **`REQ-STP-010.3` $\rightarrow$ `TST-STP-010.4`**: Maintain canonical placement of `risk_register.md` in `docs/management/` outside `docs/engineering/`.
* **`REQ-STP-010.4` $\rightarrow$ `TST-STP-010.1..3`**: Expand `ReleaseCiAndLintConfigContractTest.kt` to assert `MAN.5`, `MAN.3.BP5`, `RISK-001`..`RISK-011`, and Ceremony 1 & 2 integration.

---

## 3. System Invariants & Preserved Behavior
1. **Code Lock Integrity**: Zero modifications to production source code in `app/src/main/`.
2. **Backward Compatibility & Dual Citation**: Both `MAN.5` and legacy `MAN.3.BP5` cited so existing and updated contract tests pass.
3. **Log History Preservation**: All historical sprint log entries (`2026-46.1` through `2026-11-16_03_Nacht`) preserved verbatim.
4. **Ceremony Rule Parity**: Preserve `ATT-8153` QA corrective action rules, PO `05:00/15:00/22:00` cutovers, and weekly Kover coupling in `sprint-planner/SKILL.md`.
5. **Submodule Purity**: `ANT-Android-SDKs` preserved at commit `0e8cea714a231fb9565170d4ed01eafa868f9798`.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1 (`REQ-STP-010.1`, `REQ-STP-010.3` / `docs/management/risk_register.md`)**:
  - Update header and Section 1/5 to ASPICE 4.0 `MAN.5` (BP1–BP6) while retaining `MAN.3.BP5` legacy reference.
  - Re-score `RISK-002` ($P=4, I=4 \rightarrow E=16$, High Exposure) with updated mitigations (`ATT-6150`, `ATT-6141`, `ATT-7101`, `ATT-8149`, `ATT-8150`).
  - Update `RISK-003` to `ATT-8084 / Target SDK Maintenance` and clarify `ATT-7902` tests-only scaffolding scope in Section 3.
  - Insert `2026-11-14 | 2026-46.6 (Close)` SKIPPED review documentation in Section 3.
  - Add `RISK-008` (Human Review Bottleneck), `RISK-009` (Gate Rubber-Stamping), `RISK-010` (Rule Churn), and `RISK-011` (AI Provider Limits/Shifts) to Section 4.
* **Step 2 (`REQ-STP-010.2` / `.agents/skills/sprint-planner/SKILL.md`)**:
  - In Ceremony 2 instructions, replace conditional risk register update with mandatory check: require logging a delta update if risks changed or an explicit 'No Change / Risks Monitored & Stable' entry.
* **Step 3 (`REQ-STP-010.4` / `ReleaseCiAndLintConfigContractTest.kt`)**:
  - In `testProjectRiskRegister_existsAndHighExposureMitigated`, assert `"MAN.5"` and `"MAN.3.BP5"`.
  - Expand `requiredRisks` to include `"RISK-001"` through `"RISK-011"`.
  - Assert `2026-46.6` close documentation and Ceremony 2 integration in `sprintPlannerContent`.
* **Step 4 (`TST-STP-010.1..4` / Verification)**:
  - Run `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest.testProjectRiskRegister_existsAndHighExposureMitigated"`.
  - Run `python3 tools/verify_requirement_governance.py`.

---

## 5. Verification & Rollback Plan
* **Verification**: Verify 100% test pass on `ReleaseCiAndLintConfigContractTest` and clean zero-error exit on `verify_requirement_governance.py`.
* **Rollback**: `git checkout feature/ATT-8167 -- docs/ app/ .agents/` cleanly reverts modifications.
