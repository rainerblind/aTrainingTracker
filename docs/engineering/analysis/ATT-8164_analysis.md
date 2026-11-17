# Stage 1 Analysis: ATT-8164 - Add Version, Status & Approval Header to Work Products

**Ticket**: [ATT-8164](https://rainerblind.atlassian.net/browse/ATT-8164) | **Sub-task**: `[Analysis]` | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-17_02_Spät` | **Branch**: `feature/ATT-8164` | **Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-17

---

## 1. Problem Statement & Motivation
Work products across `docs/management/`, `docs/architecture/`, `docs/system/`, `docs/requirements/`, `docs/tests/`, and `.agents/rules/` lack a uniform version/status/approver/change-history header (`GP 2.2.1/2.2.3/2.2.4`, `SUP.8 BP2`). Existing `Approved` labels (e.g., `docs/management/cm_plan.md:5-6,135` bumped by `AI Agent (ATT-8163)`, `docs/system/sysreq_*.md:3-8` by `Antigravity Autonomous Engineering Pair`) are agent-self-declared without human sign-off. Governance documents also contain 5 factual contradictions and diverged `tools/hoechstmass/` copies.

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Header Inventory Gap**:
  * `docs/management/`: `cm_plan.md:3-7`, `qa_plan.md:3-7`, `risk_register.md:3-7` have `Version`/`Status` (`Approved`) but no `Approver` (and `qa_plan.md` lacks Revision History); `release_plan.md:1-10` lacks `Version`, `Status`, `Approver`, and Revision History.
  * `docs/architecture/`: `adr_001` (`Status: Approved`), `adr_002..007` (`Retrospective (Pending PO Sign-Off)`), `integration_strategy.md`, `interfaces.md`, `sensor_units.md`, `detailed_design/*.md`, `docs/architecture.md` lack unified `Document ID`, `Version`, `Approver`, and `Change History`.
  * `docs/system/*.md` (7 files): have `Document Version`/`Status` (`Approved` in 6, `Reclassified` in `sys_integration_tests.md`), lack `Document ID`, `Approver`, and `Change History`.
  * `docs/requirements/*.md` (9), `docs/tests/*.md` (9), `docs/requirements.md`, `docs/tests.md`, `.agents/rules/aspice_governance.md`, `docs/coding_standards.md`, `docs/design_guidelines.md`, `docs/project_*.md`: no version/status/approver header.
* **Independent Peer Review & Contradiction Verification**:
  1. *8 vs 9 Core Rules*: `.agents/rules/aspice_governance.md:3` states `9 Core Rules` (Rules 1–9), whereas `docs/project_protocol.md:10,45`, `tools/jira_util.py:965` (`CORE IN-SPRINT GOVERNANCE REMINDER (8 CORE RULES)`), and `docs/tests/tst_process_stability.md:1876` cite `8 Core Rules`.
  2. *Rules 1–55 vs 1–84+*: `docs/project_protocol.md:14` cites `Rules 1–55`, whereas `aspice_governance.md:6` and `docs/engineering/rule_history_archive.md:1,11,99` archive `Rules 1–84+` (up to Rule 85).
  3. *5 vs 6 Stages*: `docs/project_protocol.md:4,24,42`, `tools/hoechstmass/docs/project_protocol.md:102`, `tools/hoechstmass/README.md:62,82` cite `5-Stage` / `Gates 1–5`, whereas `aspice_governance.md:50-57` defines Stages 1–5 sub-tasks plus Stage 6 Cold Final Review (`Final Review (Agent)`).
  4. *Concurrency 2 vs 3*: `aspice_governance.md:59,81` mandates `2 parallel lanes` (token-conservation directive 2026-11-16, scaling to `3` when budget permits), whereas `.agents/skills/sprint-planner/SKILL.md:113` says `1..3` and `tools/hoechstmass/.agents/rules/aspice_governance.md:80`, `tools/hoechstmass/.agents/skills/sprint-planner/SKILL.md:89`, `REQ-PRO-041` (`docs/requirements/req_process_stability.md:3158-3162`) hardcode `concurrency = 3`.
  5. *Rule 5 vs Rule 83*: `docs/management/risk_register.md:66` mislabels Stage 6 mandatory review questions as `Rule 5` (actually `Rule 83` / `Rule 3.4` per `rule_history_archive.md:97`; `Rule 5` is Ticket Creation Stops in `aspice_governance.md:38`).
  6. *`tools/hoechstmass/` Divergence*: All `.agents/` files and `docs/project_protocol.md` + `tools/` (`jira_util.py` 2666 vs 5815 LOC; `verify_requirement_governance.py` 1138 vs 5390 LOC) diverged. **Blind spot**: deletion breaks `HoechstmassCoreFrameworkContractTest.kt`, `HoechstmassToolingContractTest.kt`, `ReleaseCiAndLintConfigContractTest.kt:1122-1130`, `tools/test_jira_epics.py:287`; must sync/align rather than delete.
* **Contract Test & Guard Coupling**: `AdrCompletenessContractTest.kt:169-172` enforces `Retrospective (Pending PO Sign-Off)`/`Approved` on `ADR-002..007`; `JavaCoreMigrationStrategyContractTest.kt:110-111` asserts `Status: Approved` on `ADR-001`; `ReleaseCiAndLintConfigContractTest.kt:1105` asserts `**Version**: 1.1.0` on `cm_plan.md`; `verify_requirement_governance.py:4239-4333` (`verify_architecture_and_system_specs`) checks `+` lines in `docs/architecture/` & `docs/system/` for backticked code files and `SYSREQ-*` parity; `:3824-3869` (`verify_shard_header_counts`) & `AspiceSweAuditContractTest.kt:101,143` match `^#{2,4}\s+(REQ|TST)-`.

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**: Define canonical work-product header in `docs/coding_standards.md` & `docs/management/cm_plan.md`; add header to `docs/management/`, `docs/architecture/` (+ `docs/architecture.md`), `docs/system/`, `docs/requirements/` + `docs/tests/` (+ master indexes), `.agents/rules/`; enforce version bump + human-only `Approved` guard in `tools/verify_requirement_governance.py`; resolve the 5 contradictions and sync `tools/hoechstmass/` docs/rules.
* **Out-of-Scope**: Modifying `ANT-Android-SDKs` (`0e8cea71`), full code rewrite of `tools/hoechstmass/tools/`, or altering `app/src/main/` production Kotlin/Java code.

## 4. Requirement Archaeology & Chesterton's Fence Audit
* Net-new `REQ-PRO-120` + update/align `REQ-PRO-035` (`tools/hoechstmass/.agents/rules/aspice_governance.md` rule count), `REQ-PRO-041` (`concurrency` wording), `REQ-PRO-119` (`cm_plan.md` version/status), and `TST-ARC-005` / `TST-PRO-*` contract assertions if `ADR-001`/`cm_plan.md` statuses reset from agent-`Approved` to `Reviewed` (full 4-field archaeology audited in Stage 2).

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-PRO-120`, aligned `REQ-PRO-035`, `REQ-PRO-041`, `REQ-PRO-119`, `REQ-ARC-005` (only if `ADR-001` status updates).
* **Affected Architecture Elements**: `tools/verify_requirement_governance.py`, `tools/jira_util.py`, `docs/management/*.md`, `docs/architecture/*.md`, `docs/system/*.md`, `docs/requirements/*.md`, `docs/tests/*.md`, `.agents/rules/aspice_governance.md`, `.agents/skills/sprint-planner/SKILL.md`, `docs/project_protocol.md`, `tools/hoechstmass/`.
* **Verification Impact & Test Surface**: `tools/test_verify_requirement_governance.py`, `AspiceSweAuditContractTest.kt`, `HoechstmassCoreFrameworkContractTest.kt`, `ReleaseCiAndLintConfigContractTest.kt`, `AdrCompletenessContractTest.kt`, `JavaCoreMigrationStrategyContractTest.kt`, `verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: Medium risk of breaking regex-sensitive Kotlin contract tests (`**Version**: 1.1.0`, `Status: Approved`, `8 Core Rules` in `HoechstmassCoreFrameworkContractTest.kt:153-155` checks `Rule 1..8`) or triggering `SYSREQ-*`/`REQ-*` ID false positives in `verify_requirement_governance.py`. Mitigated by `DOC-*` IDs and updating affected contract tests alongside docs.

## 6. Architectural Strategy & High-Level Solution
1. Standardize header block (`**Document ID**` `DOC-*`, `**Version**` semver, `**Status**` `Draft | Reviewed | Approved` [+ ADR `Retrospective (Pending PO Sign-Off)` compatibility], `**Approver**` `Pending PO Sign-Off | <Human Name>`, `**Change History**` table) in `docs/coding_standards.md` & `docs/management/cm_plan.md`; apply across all target docs, resetting agent-only `Approved` claims to `Reviewed` (updating `JavaCoreMigrationStrategyContractTest` / `ReleaseCiAndLintConfigContractTest` as needed).
2. Add `verify_work_product_headers()` to `tools/verify_requirement_governance.py` (invoked in default CI path): (a) verify all governed docs carry valid headers and no `Status: Approved` has AI/Pending `Approver`; (b) inspect `git diff` against `base_ref`: if a governed doc's body changed, enforce monotonic version bump and reject `Status: Approved` (new or retained) unless modifying commit carries explicit `Human-Approved:` / `Interactive-PO:` trailer (since `git log` author is `Rainer Blind` for all agent commits).
3. Resolve contradictions across `aspice_governance.md`, `project_protocol.md`, `tools/jira_util.py:965`, `risk_register.md:66`, `sprint-planner/SKILL.md`, `tools/hoechstmass/` (keeping `Rule 1..8` compatibility, `9 Core Rules`, `Rules 1–85+`, `5-Stage Execution + Stage 6 Cold Final Review`, `2–3` lane concurrency policy, `Rule 83 / Rule 3.4` in `risk_register.md`).

## 7. System Invariants & Risk Assessment
* **Core Invariants**: (1) Zero `app/src/main/` or `ANT-Android-SDKs` edits; (2) `REQ-PRO-117` Ceremony 2 allowlist & `verify_shard_header_counts()` 100% parity preserved; (3) Existing CI (`python3 tools/verify_requirement_governance.py`) passes on clean `develop`/`sprint/*` and rejects unapproved `Approved` or unbumped content edits.
* **Risk Rating**: **MEDIUM** — Broad documentation + governance-script + contract-test surface across 40+ files; mitigated by contract test verification and staged diffs.
