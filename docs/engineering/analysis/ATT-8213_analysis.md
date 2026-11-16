# Stage 1 Analysis: ATT-8213 - Mandate and document weekly dual-vendor Gemini and Claude holistic code and architecture reviews

**Ticket**: [ATT-8213](https://rainerblind.atlassian.net/browse/ATT-8213)  
**Sub-task**: [ATT-8226](https://rainerblind.atlassian.net/browse/ATT-8226) (`[Analysis]`)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8`  
**Branch**: `feature/ATT-8213` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-15  

---

## 1. Problem Statement & Motivation
In-sprint Stage 6 Cold Reviews inspect single-ticket diffs in isolation. The broader ~200k+ LoC codebase (`TrackerService`, `BANALService`, SQLite `*DatabaseManager`s, BLE/ANT+ drivers, `WorkoutRepository`, Compose UI, tests) has never undergone a systematic holistic dual-vendor code & architecture audit. Restricting reviews to delta diffs leaves latent architectural debt, concurrency hazards, and resource leaks (238 Lint / 9,518 Detekt suppressions) unexamined. Mandatory weekly cross-vendor reviews by **both Google Gemini and Anthropic Claude** (`SWE.2`, `SWE.3.BP4`, `SWE.4.BP2`, `SUP.1`) lack governance documentation, repository tracking, and automated top-of-backlog scheduling.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Investigation**:
  * `docs/engineering/code_reviews/` does not exist; `ALLOWED_ENGINEERING_ENTRIES` in `tools/verify_requirement_governance.py` lacks `"code_reviews"`.
  * `docs/management/cm_plan.md` Section 2 does not register `docs/engineering/code_reviews/`.
  * `tools/jira_util.py` provides `ensure_weekly_retro_ticket` (ATT-8030) but lacks `ensure-weekly-reviews` automation.
  * Governance docs (`docs/coding_standards.md`, `.agents/rules/aspice_governance.md`, `sprint-planner/SKILL.md`, `release-manager/SKILL.md`) lack full-subsystem dual-vendor review mandates.
* **Independent Peer Review & Verification**:
  * Confirmed: `docs/engineering/reviews/` stores only sprint diff records (`sprint_2026-46.*.md`), missing whole-subsystem audits.
  * Hypothesis verified: Whole-codebase coverage requires an explicit matrix (`coverage_matrix.md`) cycling through 9 package domains (`tracker/`, `banalservice/`, `database/`, `exporter/`, `ui/tracking/`, `ui/aftermath/`, `ui/settings/`, `tools/`, `test/`).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Mandate weekly Dual-Vendor (Gemini + Claude) holistic full-subsystem audits across `docs/coding_standards.md`, `.agents/rules/aspice_governance.md`, `sprint-planner/SKILL.md`, `release-manager/SKILL.md`, and cross-ref `docs/management/qa_plan.md`.
  2. Establish `docs/engineering/code_reviews/` registered in `cm_plan.md`, containing `coverage_matrix.md` and review report template.
  3. Implement `ensure-weekly-reviews` in `tools/jira_util.py` (and Höchstmaß mirror), creating deduplicated review tickets under `ATT-232` ranked `rank-top`.
  4. Enforce release manager gating if weekly review is overdue (> 7 days).
  5. Define living documentation `REQ-PRO-095` and `TST-PRO-095` with 100% mathematical parity.
* **Out-of-Scope Non-Goals**:
  1. Executing the first review cycle within ATT-8213 itself (handled by created review tickets).
  2. Modifying in-sprint Stage 6 Cold Carbon review mechanics (Rule 3.3).
  3. Altering Android runtime code in `app/src/main/`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement only (`REQ-PRO-095`). No existing requirements modified.
* **Historical Origin & Commit Trace**: Extends `REQ-PRO-062` (Sprint 2026-46.3, ATT-6155) which established sprint-scoped review evidence.
* **Root Reason for Existing Formulation**: `REQ-PRO-062` focused strictly on per-sprint diff reviews, omitting whole-subsystem audits and cadence automation.
* **Preservation of Core Invariants**: Extends review evidence without altering Rule 3 human gates, Rule 8 commit hygiene, or Phase 2 autonomy.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-PRO-095` in `docs/requirements/req_process_stability.md`.
* **Affected Architecture Elements**: `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `docs/management/cm_plan.md`, `docs/coding_standards.md`, `.agents/rules/aspice_governance.md`, `.agents/skills/sprint-planner/SKILL.md`, `.agents/skills/release-manager/SKILL.md`, `docs/engineering/code_reviews/coverage_matrix.md`.
* **Verification Impact & Test Surface**: Net-new `TST-PRO-095` in `docs/tests/tst_process_stability.md`; CLI test coverage in `tools/test_jira_accounts.py` and `tools/test_verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: Low risk. Preserves pure Python stdlib, mock fallbacks, and zero runtime impact in `app/src/main/`.

---

## 6. Architectural Strategy & High-Level Solution
* **Subsystem Coverage Matrix**: `docs/engineering/code_reviews/coverage_matrix.md` mapping 9 domains with audit status (`UNREVIEWED` $\to$ `AUDITED`).
* **Backlog Orchestration (`ensure-weekly-reviews`)**: CLI command in `tools/jira_util.py` checks elapsed days ($>7$) and unreviewed domains. If overdue and not already open in Jira, creates `Weekly Dual-Vendor Code Review` under `ATT-232` at `rank-top`.
* **Release Gate**: `release-manager/SKILL.md` verifies weekly review currency prior to release branch cut.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: Zero runtime app code modifications; Rule 3 Human Decision Gate preserved; tool offline resilience.
* **Risk Rating**: **LOW** (developer tooling, documentation, and workflow automation).
