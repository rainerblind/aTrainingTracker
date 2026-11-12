# Stage 1 Analysis: ATT-6155 - Record cross-vendor independent reviews as QA evidence

**Ticket**: [ATT-6155](https://rainerblind.atlassian.net/browse/ATT-6155)  
**Sub-task**: `[Analysis]` (Created on submission)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.3`  
**Branch**: `feature/ATT-6155`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
Under ASPICE SUP.1 (Quality Assurance) and GP 2.2 (Process & Work Product Evidence), QA activities must demonstrate adequate independence from the work product author. Currently, in-sprint `agent1` and `agent2` operate on the same vendor framework (different Gemini model tiers). While independent Human Sprint Reviews are conducted on every sprint ticket during Ceremony 2 before `Erledigt`, and ad-hoc cross-vendor AI reviews (Layer 5) may occur, neither leaves a persistent, structured QA review record in repository configuration management.
Resolving ASPICE 4.0 Pre-Assessment Finding B-10, ATT-6155 introduces a structured independent review record mechanism in `docs/engineering/reviews/`, supported by a `record-review` CLI helper in `tools/jira_util.py`, and validated via `verify_requirement_governance.py --check-reviews`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  * `docs/engineering/` lacks a registered, canonical directory for independent review records (`reviews/`).
  * `tools/jira_util.py` lacks a CLI helper (`record-review`) to capture traceable reviewer metadata (Human or external AI vendor + model/version), reviewed ticket(s)/commit(s), scope, and findings.
  * `tools/verify_requirement_governance.py` validates gates, metrics, and QA audits, but lacks a check for independent review records (`--check-reviews`).
* **Independent Hypothesis Verification**:
  * Audit of `ALLOWED_ENGINEERING_ENTRIES` in `tools/verify_requirement_governance.py` and `hoechstmass_config.json`: `"reviews"` is currently absent; creating `docs/engineering/reviews/` would trigger governance violations.
  * Audit of `sprint-planner/SKILL.md` Ceremony 2: Step 2 (Joint Ticket-by-Ticket Review) guides human review and acceptance, but does not mandate recording structured review evidence into the repository.
  * **Phase 2 Non-Blocking Invariant**: Autonomous Phase 2 (`Stage 5 -> Final Review`) must NEVER be blocked on independent review presence, as Human review occurs strictly *after* tickets reach `Final Review (Human)` during Ceremony 2.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Add `"reviews"` to `ALLOWED_ENGINEERING_ENTRIES` in `tools/verify_requirement_governance.py` and `tools/hoechstmass/hoechstmass_config.json`.
  2. Implement `record-review` CLI command in `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py` supporting both Human Sprint Reviews (Ceremony 2) and Cross-Vendor AI Reviews (Layer 5).
  3. Define structured independent review record format in `docs/engineering/reviews/<filename>.md` capturing traceable metadata: Reviewer (`Human` or external AI vendor), Model/Version, Date, Ticket(s)/Commit(s), Scope, and Verdict/Findings.
  4. Implement `verify_independent_reviews(sprint_name=None, repo_root=None)` and `--check-reviews [SPRINT]` in `tools/verify_requirement_governance.py` and Höchstmaß mirror.
  5. Update `.agents/skills/sprint-planner/SKILL.md` Ceremony 2 to mandate recording independent reviews prior to sprint completion.
  6. Define formal `REQ-PRO-062` and `TST-PRO-062` in living documentation with 100% mathematical parity.
  7. Author comprehensive unit tests in `tools/test_verify_requirement_governance.py`, `tools/test_jira_accounts.py`, and `tools/hoechstmass/tools/test_hoechstmass_tools.py`.
* **Out-of-Scope Non-Goals**:
  * Zero modifications to Android production code in `app/src/main/`.
  * Never block unattended Phase 2 execution into `Final Review`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Net-new requirement `REQ-PRO-062` (*Independent QA & Cross-Vendor Review Evidence Persistence*). Targets `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `docs/engineering/reviews/`, and `tools/hoechstmass/`.
* `Historical Origin & Commit Trace`: Introduced in Sprint 2026-46.3 via ATT-6155 under Epic ATT-6141 to resolve ASPICE pre-assessment finding B-10.
* `Root Reason for Existing Formulation`: In-sprint agents operate on the same vendor stack; repository lacked persistent evidence of independent human / cross-vendor verification.
* `Preservation of Core Invariants`: Zero changes to Android runtime code; pure Python 3 standard library; unattended Phase 2 autonomy strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution
1. **Directory Allow-List Registration**: Register `"reviews"` in `ALLOWED_ENGINEERING_ENTRIES` and `hoechstmass_config.json`.
2. **Review Recording CLI (`record-review`)**:
   Implement CLI supporting parameters `--sprint`, `--ticket`, `--reviewer` (`human` or vendor name), `--vendor`, `--model`, `--version`, `--scope`, `--verdict`, and `--findings`. Serializes Markdown report to `docs/engineering/reviews/<sprint>.md` (or ticket-specific review file).
3. **Governance Verifier (`--check-reviews`)**:
   Implement `verify_independent_reviews()` asserting review file existence and required metadata fields (Reviewer, Date, Scope, Findings/Verdict).
4. **Living Doc & Ceremony 2 Governance**:
   Document `REQ-PRO-062` in `docs/requirements/req_process_stability.md` and sync master tables. Update Ceremony 2 checklist in `sprint-planner/SKILL.md`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Pure Python standard library; zero production code impact; autonomous Phase 2 unblocked.
* **Risk Rating**: **LOW** — Developer tooling and ASPICE process evidence capture enhancement.
