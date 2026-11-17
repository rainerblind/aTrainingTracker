# Stage 3: Implementation Plan - ATT-8584: Autonomous Schicht Retro Write-Allowlist

**Ticket**: [ATT-8584](https://rainerblind.atlassian.net/browse/ATT-8584) | **Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-17_02_Spät`
**Requirement Mapping**: `REQ-PRO-117` (`REQ-PRO-117.1`..`REQ-PRO-117.4`, refining `REQ-PRO-105`, `REQ-STP-010.2`)
**Test Mapping**: `TST-PRO-117` (`TST-PRO-117.1`..`TST-PRO-117.3`)
**Branch**: `feature/ATT-8584` | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-17

---

## 1. Problem Description & Architectural Scope
`.agents/skills/sprint-planner/SKILL.md` (Ceremony 2 Step 5, L257) instructs agents to directly modify `.agents/rules/aspice_governance.md`, skills, `docs/coding_standards.md`, `docs/design_guidelines.md`, and `docs/engineering/rule_history_archive.md` during Retro, even when continuous 3-Schicht closes run autonomously without the PO. This plan restricts autonomous Ceremony 2 writes to evidence/logs and adds an automated enforcement guard.
* **UI Consistency**: `N/A` (governance rules, skill docs, Python verifier, and Kotlin contract tests only; zero UI/app code changes).

---

## 2. Traceability & Requirements Mapping
* **`REQ-PRO-117.1`, `REQ-PRO-117.2` $\rightarrow$ `TST-PRO-117.2`**: Restrict Ceremony 2 autonomous writes in `.agents/rules/aspice_governance.md` and `.agents/skills/sprint-planner/SKILL.md` (L257) to the 7-path evidence/log allowlist, protect rules/skills/standards/tools, and route proposals to `Sprint_Review_and_Retro_<KEY>.md` + rolling `Wochen Review & Retro`.
* **`REQ-PRO-117.3` $\rightarrow$ `TST-PRO-117.1`, `TST-PRO-117.3`**: Add `verify_retro_write_allowlist()` & `--check-retro-allowlist` in `tools/verify_requirement_governance.py` (integrated into default `verify_governance()`), enforcing the allowlist on post-cutoff autonomous `chore(ceremony2)` commits/file sets while exempting ticket-backed, `Interactive-PO:`, and pre-cutoff (`<= 8aa881df`) commits.
* **`REQ-PRO-117.4` $\rightarrow$ `TST-PRO-117.1`, `TST-PRO-117.2`**: Add Python unit tests in `tools/test_verify_requirement_governance.py` and Kotlin contract assertions in `AspiceSweAuditContractTest.kt` while keeping `HoechstmassCoreFrameworkContractTest.kt` green.

---

## 3. System Invariants & Preserved Behavior
1. **Code Lock & Zero Production Edits**: Zero edits to `app/src/main/`; zero edits to `.agents/`, `tools/`, or tests until `check-gate` passes in Stage 4.
2. **Existing Governance & Contract Parity**: Preserve `[Verified]` `REQ-PRO-105` & `REQ-STP-010.2` (`risk_register.md` Sprint Review Log), `ensure-weekly-retro`, `AspiceSweAuditContractTest`, and `HoechstmassCoreFrameworkContractTest` markers (`sprint-metrics --record --post-to-retro`, `PA 4.2`, `PA 5.2`).
3. **Submodule & History Safety**: Keep `ANT-Android-SDKs` untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`; never false-positive on pre-cutoff (`8aa881df`) or PO-approved ticket commits (`ATT-8584`).

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1 (`REQ-PRO-117.1`, `REQ-PRO-117.2` / `.agents/skills/sprint-planner/SKILL.md`, `.agents/rules/aspice_governance.md`)**:
  - Verify Gate 3 sign-off via `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>`.
  - In `.agents/skills/sprint-planner/SKILL.md` L257, replace direct rule/skill/standards self-modification with the explicit 7-path **Autonomous Schicht-End Close Allowlist** (`retros/Sprint_Review_and_Retro_*.md`, `gates/*`, `qa_audits/*`, `reviews/*`, `test_evidence/*`, `metrics/process_metrics.jsonl`, `docs/management/risk_register.md` Sprint Review Log) vs **Protected Paths** (`.agents/rules/**`, `.agents/skills/**`, `docs/coding_standards.md`, `docs/design_guidelines.md`, `docs/project_governance.md`, `docs/project_protocol.md`, `docs/engineering/rule_history_archive.md`, `tools/**`), routing proposed changes to `Sprint_Review_and_Retro_<KEY>.md` and `Wochen Review & Retro` / PO-approved tickets.
  - In `.agents/rules/aspice_governance.md` (`Rule 2.7` under `Rule 2`), codify the identical autonomous Retro write-allowlist, protected paths, and `Interactive-PO:` / dedicated ticket authorization rule without altering `Rule 1`–`Rule 9` anchors.
* **Step 2 (`REQ-PRO-117.3` / `tools/verify_requirement_governance.py`)**:
  - Implement `verify_retro_write_allowlist(commit_ref="HEAD", changed_files=None, commit_message=None, repo_root=None)` + CLI flag `--check-retro-allowlist` (and integrate a post-cutoff `chore(ceremony2)` git-history/HEAD check into `verify_governance()`), matching the 7 allowlisted glob/prefix rules, rejecting any protected/non-allowlisted file in autonomous `chore(ceremony2)` commits, and exempting ticket-backed (`ATT-\d+` when non-retro work or `Interactive-PO:` trailer) and pre-cutoff (`8aa881df` / `2026-11-17T15:30:00`) commits.
* **Step 3 (`REQ-PRO-117.4` / `tools/test_verify_requirement_governance.py`, `AspiceSweAuditContractTest.kt`)**:
  - Add Python unit tests in `tools/test_verify_requirement_governance.py` covering allowlisted `chore(ceremony2)` passes, protected-path rejections, and ticket/`Interactive-PO:`/pre-cutoff exemptions.
  - Add Kotlin contract assertions in `AspiceSweAuditContractTest.kt` verifying `.agents/rules/aspice_governance.md` and `.agents/skills/sprint-planner/SKILL.md` document the autonomous Retro write-allowlist, protected paths, and `Wochen Review & Retro` routing (`REQ-PRO-117`, `TST-PRO-117`).
* **Step 4 (`TST-PRO-117.1..3` / Stage 4 Verification Execution)**:
  - Ensure Gradle symlinks (`local.properties`, `secrets.properties`, `app/google-services.json`) & `git submodule update --init --recursive` without touching `ANT-Android-SDKs`.
  - Run `python3 -m unittest tools/test_verify_requirement_governance.py`, `python3 tools/verify_requirement_governance.py`, and `python3 tools/verify_requirement_governance.py --check-retro-allowlist`.
  - Run `python3 /home/rainer/AndroidStudioProjects/aTrainingTracker/tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --tests "*.AspiceSweAuditContractTest" --tests "*.HoechstmassCoreFrameworkContractTest"`.

---

## 5. Verification & Rollback Plan
* **Verification**: 100% pass on `test_verify_requirement_governance.py`, `verify_requirement_governance.py`, `--check-retro-allowlist`, `AspiceSweAuditContractTest`, and `HoechstmassCoreFrameworkContractTest`.
* **Rollback**: `git checkout -- .agents/ tools/ app/src/test/` cleanly reverts Stage 4 edits.
