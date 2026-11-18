# Stage 1 Analysis: ATT-8875 - Fix generate_test_evidence skip_kover TypeError & Cross-Tool Import Collision

**Ticket**: [ATT-8875](https://rainerblind.atlassian.net/browse/ATT-8875) | **Sub-task**: Pending creation (`[Analysis]`)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` (ID `10372`) | **Active Sprint**: `2026-11-18_02_Spät` (ID `460`)  
**Branch**: `feature/ATT-8875` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-18

## 1. Problem Statement & Motivation
During Sprint 459 Tier-2 verification (`Sprint_Review_and_Retro_ATT-8827.md`), `test_export_test_evidence_command_generates_files` (`tools/test_jira_accounts.py:663`) failed with `TypeError: generate_test_evidence() got an unexpected keyword argument 'skip_kover'` at `tools/jira_util.py:4664`.

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  * **Ticket Hypothesis Check**: `ATT-8875` attributed the crash to a mock `generate_test_evidence` function in `tools/test_jira_accounts.py:683`. Live inspection disproves any mock there: `L683` is the direct call `jira_util.export_test_evidence(...)`, and `tools/generate_test_evidence.py:559` **already accepts** `skip_kover: bool = False` (so `test_export_test_evidence_command_generates_files` passes `1/1` in isolation).
  * **True Root Cause (Module Cache Collision + Mirror Drift)**:
    1. `tools/jira_util.py:4646` imports `from generate_test_evidence import generate_test_evidence as gen_fn` using a bare un-namespaced module name before checking its own sibling directory (`tools/`).
    2. `tools/hoechstmass/tools/jira_util.py:21-23` prepends `tools/hoechstmass/tools` to `sys.path[0]`, and `L1799` likewise executes bare `from generate_test_evidence import ...`.
    3. `tools/hoechstmass/tools/generate_test_evidence.py:296-306` was never updated in `aec078bc` to accept `skip_kover: bool = False`. Whenever `tools/hoechstmass/tools/generate_test_evidence.py` loads into `sys.modules["generate_test_evidence"]` prior to `tools/test_jira_accounts.py:683` (reproduced via `sys.path.insert(0, "tools/hoechstmass/tools")`), `tools/jira_util.py:4646` receives the cached Höchstmaß function and crashes at `L4664-4673` with the exact `TypeError` on `skip_kover`.

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**: Scope `export_test_evidence()`'s `generate_test_evidence` import to its own sibling directory in `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py` (preventing cross-package `sys.modules["generate_test_evidence"]` pollution); sync `skip_kover: bool = False` / `--no-kover` in `tools/hoechstmass/tools/generate_test_evidence.py` & `tools/hoechstmass/tools/jira_util.py`; add a `sys.modules` collision regression test in `tools/test_jira_accounts.py` (and Hoechstmaß parity test); update `REQ-PRO-100` / `TST-PRO-100`.
* **Out-of-Scope Non-Goals**: No Android `app/` changes; no edits to pre-existing unrelated failures (`test_check_static_analysis_baselines.py` `9515!=9516`, `TST-TRK-087`, `test_tst_pro_107_3`); complexity confirmed `1 SP` (no split).

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-100` (`REQ-PRO-100.4`, `docs/requirements/req_process_stability_part2.md:1440-1479`) and `TST-PRO-100` (`TST-PRO-100.1`, `docs/tests/tst_process_stability_part2.md:882-903`).
* **Historical Origin & Commit Trace**: Introduced in `6bd56da5` (`ATT-8144`, Sprint `2026-46.8`); `aec078bc` added `skip_kover` to root `tools/jira_util.py` & `tools/generate_test_evidence.py` without updating `tools/hoechstmass/tools/`.
* **Root Reason for Existing Formulation**: Bare `from generate_test_evidence import ...` supported direct CLI (`python3 tools/jira_util.py`) alongside `from tools import ...` package imports.
* **Preservation of Core Invariants**: Checking sibling `__file__` path before falling back to `importlib.util` preserves both CLI and package execution modes, keeps `tools/` and `tools/hoechstmass/tools/` isolated, and enforces `REQ-PRO-100` Kover/skip-Kover parity.

## Change Impact Analysis
* **Affected Requirements**: `REQ-PRO-100` (`REQ-PRO-100.4`), `TST-PRO-100` (`TST-PRO-100.1`); `SYS.2`/`SYS.3` unaffected (`REQ-PRO-131.4` — Python CLI & test tooling only).
* **Affected Architecture Elements**: `tools/jira_util.py` (`export_test_evidence`), `tools/hoechstmass/tools/jira_util.py` (`export_test_evidence`), `tools/hoechstmass/tools/generate_test_evidence.py` (`should_skip_kover`, `generate_test_evidence`, `--no-kover`).
* **Verification Impact & Test Surface**: `tools/test_jira_accounts.py` (new `sys.modules["generate_test_evidence"]` collision test + `test_export_test_evidence_command_generates_files`), `tools/hoechstmass/tools/test_hoechstmass_tools.py`, `tools/test_generate_test_evidence.py`, `python3 tools/verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: **LOW** (`1 SP`, pure Python 3 stdlib). Invariants: preserve `tools/jira_util.py` CLI + package import modes, Hoechstmaß parity, zero `app/src/main/` changes, and Human Decision Gate (`Rule 3.1`).

## 5. Architectural Strategy & High-Level Solution
* Extract/use a small sibling-module loader in `export_test_evidence()` (`tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py`) that verifies any cached `sys.modules["generate_test_evidence"]` actually resides in `os.path.dirname(os.path.abspath(__file__))`, otherwise loads `tools.generate_test_evidence` (root) or `importlib.util.spec_from_file_location` from the sibling file.
* Mirror `should_skip_kover()`, `skip_kover: bool = False`, and `--no-kover` in `tools/hoechstmass/tools/generate_test_evidence.py` and `tools/hoechstmass/tools/jira_util.py`.
