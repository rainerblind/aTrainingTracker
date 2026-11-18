# Stage 5: Walkthrough & Verification - ATT-8875: Fix generate_test_evidence skip_kover TypeError & Cross-Tool Import Collision

**Ticket**: [ATT-8875](https://rainerblind.atlassian.net/browse/ATT-8875) | **Sub-task**: Pending creation (`[Test]`)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` (ID `10372`) | **Active Sprint**: `2026-11-18_02_Spät` (ID `460`)  
**Requirement Mapping**: `REQ-PRO-100` (`REQ-PRO-100.4`) | **Test Mapping**: `TST-PRO-100` (`TST-PRO-100.1`)  
**Branch**: `feature/ATT-8875` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-18

## 1. Executive Summary & Verification Overview
Resolved the cross-directory `sys.modules["generate_test_evidence"]` collision and Höchstmaß `skip_kover` mirror drift that triggered `TypeError: generate_test_evidence() got an unexpected keyword argument 'skip_kover'` in `test_export_test_evidence_command_generates_files`:
* **Sibling-Scoped Import Isolation**: `tools/jira_util.py` (`export_test_evidence`) and `tools/hoechstmass/tools/jira_util.py` verify that any cached `sys.modules["generate_test_evidence"].__file__` matches their own sibling `generate_test_evidence.py`, otherwise loading via package import or `importlib.util.spec_from_file_location`.
* **Höchstmaß `skip_kover` / `--no-kover` Parity**: Added project-agnostic `should_skip_kover()`, `skip_kover: bool = False`, and `--no-kover` to `tools/hoechstmass/tools/generate_test_evidence.py` and `tools/hoechstmass/tools/jira_util.py`.
* **Regression Tests**: Added `test_export_test_evidence_isolates_cross_directory_sys_modules_cache` (`tools/test_jira_accounts.py`) and `test_export_test_evidence_hoechstmass_skip_kover_and_module_isolation` (`tools/hoechstmass/tools/test_hoechstmass_tools.py`).

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Scope & Method | Result | Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-100.4` | `TST-PRO-100.1a` | `python3 -m unittest tools.test_jira_accounts` (incl. `sys.modules` collision test) | **PASSED** (`102/102` OK) | `Verified` |
| `REQ-PRO-100.4` | `TST-PRO-100.1b` | `python3 -m unittest discover -s tools/hoechstmass/tools -p "test_*.py"` | **PASSED** (`62/62` OK) | `Verified` |
| `REQ-PRO-100.4` | `TST-PRO-100.1a/b` | Single-process combo (`TestEpicCommands` + `TestJiraCliTooling`) | **PASSED** (`46/46` OK) | `Verified` |
| `REQ-PRO-100` | `TST-PRO-100.1` | `python3 -m unittest tools.test_generate_test_evidence` | **PASSED** (`8/8` OK) | `Verified` |
| `REQ-PRO-100`, `REQ-PRO-036` | `TST-PRO-100.1c` | Tier 1 Gradle (`HoechstmassToolingContractTest`, Stage 4 evidence) | **PASSED** (`4/4` PASS) | `Verified` |
| `REQ-PRO-100` | `TST-PRO-100.1c` | `python3 tools/verify_requirement_governance.py --repo-root .` | **PASSED** (`exit 0`) | `Verified` |

## 3. Automated Test Evidence (Tier 1 Targeted Scope — Zero Redundant Gradle Re-Runs)
No code changed after Stage 4 (`Rule 7.5`); Stage 4 Gradle/Python results apply identically, supplemented by the single-process cross-directory Python verification:
* `python3 -m unittest tools.test_jira_accounts`: `Ran 102 tests in 4.711s — OK` (`exit 0`).
* `python3 -m unittest discover -s tools/hoechstmass/tools -p "test_*.py" -v`: `Ran 62 tests in 3.388s — OK`.
* `python3 -m unittest tools.hoechstmass.tools.test_hoechstmass_tools.TestEpicCommands tools.test_jira_accounts.TestJiraCliTooling`: `Ran 46 tests in 7.017s — OK` (`exit 0`).
* `python3 -m unittest tools.test_generate_test_evidence`: `Ran 8 tests in 0.017s — OK`.
* `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.HoechstmassToolingContractTest"`: `BUILD SUCCESSFUL in 1m 6s` (`4/4` PASS, `0` skipped; full-repo suite deferred to Tier 2).

## 4. Hardware / Physical Verification & Visual Consistency
* Pure Python tooling & test infrastructure ticket; zero APK runtime code changes in `app/src/main/` and zero UI changes (`Localization Parity: N/A - no UI strings`). Emulator install skipped per `stage5-verification/SKILL.md`.

## 5. Invariant & Governance Verification
1. **Feature Branch Preserved Intact**: Committed cleanly on `feature/ATT-8875` (`sprint/2026-11-18_02_Spät` untouched pending Stage 6 Cold Review per `Rule 3.3` / `Rule 8.2`).
2. **Living Documentation Synchronized**: `REQ-PRO-100` (`v1.0.22`) and `TST-PRO-100` (`v1.0.15`) promoted to `[Verified]`; master indexes synchronized (`v1.0.44`).
3. **Human Decision Gate**: Parent ticket stops at `Final Review (Human)` (`Rule 3.1`).
