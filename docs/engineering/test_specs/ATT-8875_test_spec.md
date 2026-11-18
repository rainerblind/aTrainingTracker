# Stage 2: Requirement & Test Specification - ATT-8875: Fix generate_test_evidence skip_kover TypeError & Cross-Tool Import Collision

**Ticket**: [ATT-8875](https://rainerblind.atlassian.net/browse/ATT-8875) | **Sub-task**: Pending creation (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` (ID `10372`) | **Active Sprint**: `2026-11-18_02_Spät` (ID `460`)  
**Requirement Mapping**: `REQ-PRO-100` (`REQ-PRO-100.4`) | **Test Spec ID**: `TST-PRO-100` (`TST-PRO-100.1`)  
**Branch**: `feature/ATT-8875` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-18

## 1. Requirement Specification (`REQ-PRO-100.4` Refined `[Planned]`)

### 1.1 Problem Statement, Attributes & `SYS.2`/`SYS.3` Impact
Bare `from generate_test_evidence import ...` in `tools/jira_util.py:4646` collides with `tools/hoechstmass/tools/generate_test_evidence.py` (which also lacked `skip_kover` from `aec078bc`), causing `TypeError` in `test_export_test_evidence_command_generates_files` during multi-module runs.  
- **Priority**: High | **Type**: Non-Functional | **Verification Method**: Unit / Review  
- **SYS.2/SYS.3 Impact**: None (`REQ-PRO-131.4` — Python CLI & test tooling only; zero Android app, sensor, RF, or external API changes).

### 1.2 Functional & Architectural Requirements (`REQ-PRO-100.4`)
1. **Sibling-Scoped Import Isolation**: Both `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py` `export_test_evidence()` SHALL resolve `generate_test_evidence` strictly from their own sibling directory (`os.path.dirname(os.path.abspath(__file__))`), verifying any cached `sys.modules["generate_test_evidence"]` path before use and loading via `importlib.util` otherwise.
2. **Höchstmaß `skip_kover` Parity**: `tools/hoechstmass/tools/generate_test_evidence.py` and `tools/hoechstmass/tools/jira_util.py` SHALL support `should_skip_kover()` / `skip_kover: bool = False` / `--no-kover` while preserving `HoechstmassToolingContractTest.kt` project-agnostic invariants (zero ATT-proprietary constants).

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-100` (`REQ-PRO-100.4`) targeting `tools/jira_util.py`, `tools/hoechstmass/tools/jira_util.py`, `tools/hoechstmass/tools/generate_test_evidence.py`, `tools/test_jira_accounts.py`, and `tools/hoechstmass/tools/test_hoechstmass_tools.py`.
* **Historical Origin & Commit Trace**: `6bd56da5` (`ATT-8144`, Sprint `2026-46.8`), drifted in `aec078bc` (`skip_kover` added to root `tools/` only), remediated in `ATT-8875`.
* **Root Reason for Existing Formulation**: Bare `from generate_test_evidence import ...` supported both CLI script mode (`python3 tools/jira_util.py`) and package imports, inadvertently sharing `sys.modules["generate_test_evidence"]` across `tools/` and `tools/hoechstmass/tools/`.
* **Preservation of Core Invariants**: Preserves CLI + package import compatibility, `HoechstmassToolingContractTest.kt` project-agnostic rules, `ReleaseCiAndLintConfigContractTest.kt`, and zero `app/src/main/` changes.

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* **AC-1**: *Given* `sys.modules["generate_test_evidence"]` pre-populated by `tools/hoechstmass/tools/generate_test_evidence.py` (or a mock lacking `skip_kover`), *When* `tools/jira_util.py export_test_evidence(...)` runs, *Then* it resolves sibling `tools/generate_test_evidence.py` cleanly without raising `TypeError`.
* **AC-2**: *Given* `tools/hoechstmass/tools/jira_util.py export_test_evidence(..., skip_kover=True)` and `tools/hoechstmass/tools/generate_test_evidence.py --no-kover`, *When* executed in isolation or via `HoechstmassToolingContractTest`, *Then* `coverage_summary["measured"]` is `False` and all project-agnostic contract checks pass.
* **Invariants**: Zero `app/src/main/` changes; `Localization Parity: N/A - no UI strings`; Rule 3 Human Gate intact.

## 2. Test Specification (`TST-PRO-100.1` `[Planned]`)
* **`TST-PRO-100.1a` (`test_export_test_evidence_isolates_cross_directory_sys_modules_cache`, `tools/test_jira_accounts.py`)**: Pre-populate `sys.modules["generate_test_evidence"]` with a stub/module lacking `skip_kover` (and pointing elsewhere), call `jira_util.export_test_evidence(...)`, and assert sibling `tools/generate_test_evidence.py` executes cleanly with zero `TypeError` and restores `sys.modules`.
* **`TST-PRO-100.1b` (`test_export_test_evidence_hoechstmass` / `skip_kover` parity, `tools/hoechstmass/tools/test_hoechstmass_tools.py`)**: Verify `export_test_evidence(..., skip_kover=True)` in Höchstmaß sets `coverage["measured"] == False` and survives cross-module `sys.modules` collision.
* **`TST-PRO-100.1c` (Targeted & Contract Parity)**: Run `python3 -m unittest tools.test_jira_accounts.TestJiraCliTooling.test_export_test_evidence_command_generates_files`, `tools.test_generate_test_evidence`, `tools.hoechstmass.tools.test_hoechstmass_tools`, `HoechstmassToolingContractTest`, and `python3 tools/verify_requirement_governance.py --repo-root .`.

## 3. Traceability Matrix

| Test Case | Scope | Method / Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-100.1a` | Unit | `tools/jira_util.py:export_test_evidence` (`sys.modules` isolation) | `REQ-PRO-100.4` | Specified |
| `TST-PRO-100.1b` | Unit | `tools/hoechstmass/tools/{jira_util,generate_test_evidence}.py` (`skip_kover`) | `REQ-PRO-100.4` | Specified |
| `TST-PRO-100.1c` | Contract & Gov | `HoechstmassToolingContractTest`, `verify_requirement_governance.py` | `REQ-PRO-100`, `REQ-PRO-036` | Specified |
