# Stage 1 Analysis: ATT-8586 - Fix wrong ATT-8084 citation in RISK-003 mitigation

**Ticket**: [ATT-8586](https://rainerblind.atlassian.net/browse/ATT-8586) | **Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-17_02_Spät`
**Branch**: `feature/ATT-8586` | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-17

---

## 1. Problem Statement & Motivation
During PO review of `ATT-8167`, `docs/management/risk_register.md` (currently **L99**, shifted from L97 by subsequent sprint log rows) was found to cite `Continuous Target SDK Maintenance (ATT-8084)` under `RISK-003` (*Google Play Policy & Foreground Service Restrictions*, Medium exposure $E=12$). In Jira, `ATT-8084` is `[Feature] Request official Garmin FIT SDK Manufacturer ID and register Strava device mapping with barometer support` (`Zu erledigen`), unrelated to Android Target SDK or Play Policy compliance.

---

## 2. Root Cause Analysis & Independent Peer Review
* **RCA**: In `ec57013f` (`ATT-8167`), the placeholder `Planned Target SDK Ticket` in `RISK-003` was replaced with `ATT-8084` without verifying the Jira summary against Android Target SDK maintenance.
* **Independent Peer Review & Jira Audit (`RISK-001`..`RISK-011`)**:
  * **Valid Jira Citations**: `RISK-001` (`ATT-6148` `Erledigt`, `ATT-6152` `Erledigt`); `RISK-002` (`ATT-6150` `Erledigt`, `ATT-6141` `Zu erledigen` Epic); `RISK-007` (`ATT-8151` `Erledigt`); `RISK-008` (`ATT-8162` `Erledigt`); `RISK-009` (`ATT-8150` `Erledigt`, `ATT-8153` `Erledigt`); `RISK-010` (`ATT-8166` `Erledigt`); `RISK-011` (`ATT-8213` `Erledigt`). Inline strategy citations `ATT-6245`, `ATT-5191` (`RISK-004`) and `ATT-4668`, `ATT-4706` (`RISK-005`) are all `Erledigt` and accurate.
  * **Non-Ticket Mitigations**: `RISK-004` (`Continuous Framework Maintenance`), `RISK-005` (`Continuous Maintenance`), and `RISK-006` (`Rule 64 Architectural Invariant`) are accurate non-ticket entries (`E <= 10`, Medium/Low).
  * **Invalid Citation**: `RISK-003` (`Continuous Target SDK Maintenance (ATT-8084)`) -> `ATT-8084` is Garmin FIT SDK Manufacturer ID; Jira search (`project = ATT AND summary ~ "Target SDK"`, plus `SDK`/`targetSdk`/`API 35`/`API 36`) confirms zero open Target SDK maintenance tickets exist (`ATT-8586` is the only Target SDK match).
  * **Contract Test & Spec Audit**: `ReleaseCiAndLintConfigContractTest.kt` L770–841 (`testProjectRiskRegister_existsAndHighExposureMitigated`) enforces section headers (`MAN.5`, `MAN.3.BP5`), `RISK-001`..`RISK-011`, High-exposure links (`ATT-6148`, `ATT-6150`), log baselines (`2026-46.1`, `2026-46.6` `SKIPPED`), and `sprint-planner/SKILL.md` Ceremony 1/2 hooks. It does not reject `ATT-8084` or validate `RISK-003` mitigation text.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**: (1) Replace `Continuous Target SDK Maintenance (ATT-8084)` in `RISK-003` (`docs/management/risk_register.md` L99) with clean, accurate non-misleading text (e.g., `Continuous Target SDK Maintenance (Rule 29 & ManifestSecurityContractTest)` — no unsolicited Jira tickets created); (2) Update `Last Review Date` / `Sprint Review Log`; (3) Optionally add a regression assertion in `ReleaseCiAndLintConfigContractTest.kt` ensuring `ATT-8084` is absent from `risk_register.md`; (4) Run `testProjectRiskRegister_existsAndHighExposureMitigated`.
* **Out-of-Scope**: Production code in `app/src/main/`, creating new Jira backlog tickets, modifying `RISK-001..002`/`004..011` mitigations, or touching `ANT-Android-SDKs`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-STP-010` (`docs/requirements/req_store_presence.md` L181–198) & `TST-STP-010` (`docs/tests/tst_store_presence.md` L167–189), both `[Verified]`.
* **Historical Origin & Commit Trace**: Baseline created via `ATT-6151`; updated to ASPICE 4.0 `MAN.5` in `ec57013f` (`ATT-8167`).
* **Root Reason for Existing Formulation**: `REQ-STP-010.1` requires linked Jira remediation tickets strictly for High-exposure risks ($E \ge 15$: `RISK-001`, `RISK-002`); `TST-STP-010.1` requires `RISK-003` ($E=12$, Medium) to avoid unlinked placeholders.
* **Preservation of Core Invariants**: `REQ-STP-010` and `TST-STP-010` already hold without semantic change; `ATT-6148`/`ATT-6150` High-exposure links, `MAN.5`/`MAN.3.BP5` headers, and `docs/management/` placement outside `docs/engineering/` remain intact.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-STP-010` (`docs/requirements/req_store_presence.md`, invariant-preserving governance check; no requirement semantics altered).
* **Affected Architecture Elements**: `docs/management/risk_register.md` (`RISK-003` row & `Sprint Review Log`), `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt` (optional regression guard).
* **Verification Impact & Test Surface**: `ReleaseCiAndLintConfigContractTest.testProjectRiskRegister_existsAndHighExposureMitigated` (`TST-STP-010.4`), `python3 tools/verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: **LOW** (1 SP doc + unit contract test scope; zero `app/src/main/` changes; `ANT-Android-SDKs` pinned at `0e8cea714a231fb9565170d4ed01eafa868f9798`).

---

## 6. Architectural Strategy & High-Level Solution
* Replace `Continuous Target SDK Maintenance (ATT-8084)` in `RISK-003` (`docs/management/risk_register.md` L99) with non-misleading ongoing-maintenance text citing `Rule 29` / `ManifestSecurityContractTest` (consistent with `RISK-004`..`RISK-006`) and update `Sprint Review Log` / `Last Review Date`.
* Add a lightweight negative regression assertion in `ReleaseCiAndLintConfigContractTest.kt` verifying `risk_register.md` no longer cites `ATT-8084`, and verify `testProjectRiskRegister_existsAndHighExposureMitigated` passes.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: `app/src/main/` untouched; `RISK-001..002` High-exposure links (`ATT-6148`, `ATT-6150`) and `2026-46.1`/`2026-46.6` log baselines preserved; `ANT-Android-SDKs` untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
* **Risk Rating**: **LOW** (1 SP metadata/governance defect fix).
