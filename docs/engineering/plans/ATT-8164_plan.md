# Stage 3: Implementation Plan - ATT-8164: Work-Product Headers & Governance Reconciliation

**Ticket**: [ATT-8164](https://rainerblind.atlassian.net/browse/ATT-8164) | **Sub-task**: `[Impl-Plan]` | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-17_02_Spät` | **Branch**: `feature/ATT-8164`  
**Requirement Mapping**: `REQ-PRO-120` (`REQ-PRO-120.1`–`.5`) | **Test Mapping**: `TST-PRO-120` (`TST-PRO-120.1`–`.4`)  
**Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-17

---

## 1. Problem Description & Background
Resolve ASPICE 4.0 `GP 2.2` / `SUP.8 BP2` findings by standardizing version/status/approver/history headers across governed work products, enforcing human-only `Approved` transitions and version bumps in `tools/verify_requirement_governance.py`, reconciling 5 governance contradictions, and aligning `tools/hoechstmass/`.

## 2. Traceability & Requirements Mapping
| Requirement | Atomic Steps | Verification / Test Cases | Scope |
| :--- | :--- | :--- | :--- |
| `REQ-PRO-120.1` | Steps 1, 2 | `TST-PRO-120.1` (`AspiceSweAuditContractTest`, `verify_work_product_headers()`) | Canonical `DOC-*` header rollout & reset |
| `REQ-PRO-120.2` | Step 4, 5 | `TST-PRO-120.2` (`test_verify_requirement_governance.py` offline git repos) | Header check + human-only `Approved` & version bump |
| `REQ-PRO-120.3` | Step 3 | `TST-PRO-120.3` (`AspiceSweAuditContractTest`) | Reconcile `9 Core Rules`, `1–85+`, `6 Stages`, `2–3` lanes, `Rule 3.4/83` |
| `REQ-PRO-120.4` | Step 3 | `TST-PRO-120.3` (`HoechstmassCoreFrameworkContractTest`, `HoechstmassToolingContractTest`) | Sync `tools/hoechstmass/` preserving `Rule 1..8`/`Stage 1..5` |
| `REQ-PRO-120.5` | Step 5 | `TST-PRO-120.4` (Python + Gradle build/ADR contract suites) | Offline unit + zero-`src/main` contract tests (`REQ-PRO-049.1`) |

* **UI Consistency**: N/A — no UI changes (`Localization Parity: N/A - no UI strings`).

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime Impact**: Zero edits in `app/src/main/`; `ANT-Android-SDKs` untouched at `0e8cea71`.
2. **Existing Guard & Shard Parity**: Preserve `verify_shard_header_counts()` 100% parity, `verify_architecture_and_system_specs()` `SYSREQ-*`/symbol grounding (use `DOC-*` IDs, no ungrounded `.kt`/`.java`/`.xml` tokens), and `REQ-PRO-117` Ceremony 2 allowlist.
3. **Contract Compatibility**: Preserve `Rule 1..8` / `Stage 1..5` markers in `tools/hoechstmass/` and `ADR-002..007` `Retrospective (Pending PO Sign-Off)`.
4. **Parent Human Gate**: Parent `ATT-8164` ends at `Final Review (Human)`; `Rule 5` zero live Jira calls in tests.

## 4. Proposed Architectural Changes
* **Header Contract**: Block at top of each governed `.md` (`**Document ID**: DOC-...`, `**Version**: X.Y.Z`, `**Status**: Draft | Reviewed | Approved` [ADR `Retrospective (Pending PO Sign-Off)` accepted], `**Approver**: Pending PO Sign-Off | <Human>`, `**Change History**` markdown table).
* **Verifier (`tools/verify_requirement_governance.py`)**: `verify_work_product_headers(diff_text=None, base_ref="develop", repo_root=None)` checks static header completeness + non-AI/non-Pending `Approver` on `Approved` docs, and inspects `git diff` body changes (outside header/Change History, exempting pre-ticket historical commits) for monotonic semver bump and `Human-Approved:`/`Interactive-PO:` commit trailer whenever `Status` is `Approved`.

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Define Canonical Header Standard (`REQ-PRO-120.1`)
* Files: `docs/coding_standards.md`, `docs/management/cm_plan.md` (bump `1.1.0` -> `1.2.0`, `Status: Reviewed`, `Approver: Pending PO Sign-Off`).

### Step 2: Roll Out `DOC-*` Headers & Reset Agent-Self-Declared Approvals (`REQ-PRO-120.1`)
* Files: `docs/management/{qa_plan,release_plan,risk_register}.md`; `docs/architecture.md`, `docs/architecture/{adr_001..007,integration_strategy,interfaces,sensor_units}.md`, `docs/architecture/detailed_design/{banal_service,my_btle_device,tracker_service}.md`; `docs/system/*.md` (7 files); `docs/requirements.md` + 9 `docs/requirements/*.md`; `docs/tests.md` + 9 `docs/tests/*.md`; `.agents/rules/aspice_governance.md`; `docs/{design_guidelines,project_governance,project_protocol}.md`.
* Keep `verify_architecture_and_system_specs()` green (zero `SYSREQ-*` header IDs or ungrounded code filenames) and `verify_shard_header_counts()` green (`**Total Requirements/Tests**` intact).

### Step 3: Reconcile Governance Contradictions & Sync `tools/hoechstmass/` (`REQ-PRO-120.3`, `REQ-PRO-120.4`)
* Files: `.agents/rules/aspice_governance.md`, `.agents/skills/sprint-planner/SKILL.md`, `docs/project_protocol.md` (`9 Core Rules`, `Rules 1–85+`, `6-Stage` execution + Stage 6 Cold Review, `max 3` / `2 active` lanes), `tools/jira_util.py:965` (`9 CORE RULES` printing Rules 1–9), `docs/management/risk_register.md:66` (`Rule 5` -> `Rule 3.4 / Rule 83`), `docs/tests/tst_process_stability.md:1876` (`9 Core Rules` note), and `tools/hoechstmass/{.agents/rules/aspice_governance.md,.agents/skills/sprint-planner/SKILL.md,.agents/skills/stage6-final-review/SKILL.md,docs/project_protocol.md,README.md}` (preserving `Rule 1..8` & `Stage 1..5`).

### Step 4: Implement `verify_work_product_headers()` Guard (`REQ-PRO-120.2`)
* File: `tools/verify_requirement_governance.py` — add `verify_work_product_headers()` + `--check-doc-headers` CLI flag and wire into default `verify_governance()` (unless `--skip-tripwires`).

### Step 5: Offline Unit Tests, Contract Updates & Full Verification (`REQ-PRO-120.5`)
* Files: `tools/test_verify_requirement_governance.py` (isolated `tempfile` git repos per `Rule 5`), `ReleaseCiAndLintConfigContractTest.kt:1105` (semver `>= 1.1.0` + Revision History `1.1.0`), `JavaCoreMigrationStrategyContractTest.kt:110-111` (`Reviewed | Approved` on `ADR-001`), `AspiceSweAuditContractTest.kt` (`TST-PRO-120` header/contradiction assertions; zero `app/src/main/` reads per `REQ-PRO-049.1`).
* Run: `python3 tools/test_verify_requirement_governance.py`, `python3 tools/test_jira_accounts.py`, `python3 tools/test_jira_epics.py`, `python3 tools/verify_requirement_governance.py`, and `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*" --tests "com.atrainingtracker.architecture.JavaCoreMigrationStrategyContractTest"`.

## 6. Verification & Rollback Plan
* **Verification**: 100% pass across Python offline unit suites, `verify_requirement_governance.py`, and Gradle build/ADR contract tests; living docs `REQ-PRO-120`/`TST-PRO-120` updated to `[Verified]` in Stage 5.
* **Rollback**: Isolated on `feature/ATT-8164`; `git revert` / branch drop restores `sprint/2026-11-17_02_Spät` cleanly.
