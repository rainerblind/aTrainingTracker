# Stage 3: Implementation Plan - ATT-8875: Fix generate_test_evidence skip_kover TypeError & Cross-Tool Import Collision

**Ticket**: [ATT-8875](https://rainerblind.atlassian.net/browse/ATT-8875) | **Sub-task**: Pending creation (`[Impl-Plan]`)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` (ID `10372`) | **Active Sprint**: `2026-11-18_02_Spät` (ID `460`)  
**Requirement Mapping**: `REQ-PRO-100.4` | **Test Mapping**: `TST-PRO-100.1`  
**Branch**: `feature/ATT-8875` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-18

## 1. Problem Description & Traceability
* **Scope**: Eliminate cross-directory `sys.modules["generate_test_evidence"]` collision between `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py`, bring `skip_kover` / `--no-kover` parity to `tools/hoechstmass/tools/`, and add regression tests.
* **Traceability**: `REQ-PRO-100.4` $\leftrightarrow$ `TST-PRO-100.1` (`a`–`c`); zero `SYS.2`/`SYS.3` impact (`REQ-PRO-131.4`).

## 2. System Invariants & Preserved Behavior
1. **CLI & Package Execution Parity**: Preserve both `python3 tools/jira_util.py export-test-evidence` and `from tools import jira_util` / `import jira_util`.
2. **Project-Agnostic Höchstmaß Contract**: Preserve `HoechstmassToolingContractTest.kt` (zero `'project = ATT'`, zero proprietary account IDs, `discover -s . -p "test_*.py"` passes from `tools/hoechstmass/tools/`).
3. **Zero Android / Side-Tool Changes**: Zero edits to `app/`, `tools/generate_test_evidence.py`, or the 4 pre-existing `tools/` files (`ble_radar_simulator.py`, `jira_created_vs_resolved.py`, etc.).
4. **Human Decision Gate**: Parent ticket stops at `Final Review (Human)` (`Rule 3.1`).

## 3. Proposed Architectural Changes
* **Sibling-Path Guard**: Resolve `sibling_path = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "generate_test_evidence.py"))`, reuse `sys.modules["generate_test_evidence"]` **only** if its `__file__` matches `sibling_path`, and otherwise load `sibling_path` via `importlib.util.spec_from_file_location` under a distinct internal module name (`_root_generate_test_evidence` / `_hm_generate_test_evidence`).
* **UI Consistency**: No UI changes (`Localization Parity: N/A - no UI strings`).

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)

| Step | Target File | Requirement / Test | Atomic Changes |
| :--- | :--- | :--- | :--- |
| **1** | `tools/jira_util.py` | `REQ-PRO-100.4` | Update `export_test_evidence()` (`L4645-4658`) to verify `sys.modules["generate_test_evidence"].__file__` matches sibling `tools/generate_test_evidence.py` before use, else import `tools.generate_test_evidence` or load sibling via `importlib.util` without colliding. |
| **2** | `tools/hoechstmass/tools/generate_test_evidence.py`, `tools/hoechstmass/tools/jira_util.py` | `REQ-PRO-100.4` | Add project-agnostic `should_skip_kover()`, `skip_kover: bool = False`, and `--no-kover` in Höchstmaß `generate_test_evidence.py` & `jira_util.py` (`L1745-1824`, CLI `L2604-2629`, `USAGE_TEXT`), plus sibling-scoped loader matching Step 1. |
| **3** | `tools/test_jira_accounts.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py` | `TST-PRO-100.1` | Add `test_export_test_evidence_isolates_cross_directory_sys_modules_cache` (pre-populating `sys.modules["generate_test_evidence"]` with a `skip_kover`-less stub and restoring in `try/finally`) and Hoechstmaß `skip_kover=True` + isolation assertions. |
| **4** | Living Docs (`req_process_stability_part2.md`, `tst_process_stability_part2.md`) | `REQ-PRO-100`, `TST-PRO-100` | Promote `[Planned]` -> `[Implemented]` (Stage 4) and `[Verified]` (Stage 5) after verification passes. |

## 5. Verification & Rollback Plan
* **Targeted Tests**:
  * `python3 -m unittest tools.test_jira_accounts.TestJiraCliTooling` (plus full `python3 -m unittest tools.test_jira_accounts`)
  * `python3 -m unittest discover -s tools/hoechstmass/tools -p "test_*.py"`
  * `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.HoechstmassToolingContractTest"`
  * `python3 tools/verify_requirement_governance.py --repo-root .`
* **Rollback**: All edits stay isolated on `feature/ATT-8875` (uncommitted until Stage 6 squash); `git checkout` reverts cleanly without affecting `sprint/2026-11-18_02_Spät`.
