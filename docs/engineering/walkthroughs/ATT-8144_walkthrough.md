# Stage 5 Walkthrough: ATT-8144 - Versioned Test & Coverage Evidence in Git

**Ticket**: [ATT-8144](https://rainerblind.atlassian.net/browse/ATT-8144)  
**Sub-task**: [[Test]](https://rainerblind.atlassian.net/browse/ATT-8144)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8`  
**Requirement Mapping**: `REQ-PRO-100` (`REQ-PRO-100.1`..`REQ-PRO-100.4`), `REQ-PRO-067.3`  
**Test Spec Mapping**: `TST-PRO-100` (`TST-PRO-100.1`..`TST-PRO-100.3`), `TST-PRO-067.3`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16  

---

## 1. Executive Summary & Verification Walkthrough
This ticket remediates ASPICE 4.0 Major Finding `M7` (SWE.4 BP5, SWE.5 BP7, SWE.6 BP5, GP 2.2.3):
1. **Build Symlink Retirement & Canonical Evidence Store**: Volatile `docs/engineering/test_results` symlink into ephemeral `app/build/` retired; canonical `docs/engineering/test_evidence/` established with format `README.md`.
2. **Test & Coverage Evidence Generator (`tools/generate_test_evidence.py`)**: Implemented generator parsing JUnit XMLs (via ATT-8143's `parse_junit_xml_results`) and Kover XML (`reportDebug.xml`), outputting versioned `<sprint|release>.md` and companion `.json` with commit SHA, timestamp, counts, per-TST records, and package coverage breakdown. Handles missing Kover XML gracefully.
3. **Tooling & Workflow Automation**: Added `export-test-evidence` in `tools/jira_util.py` (and Höchstmaß mirror), wired into Ceremony 2 Step 5 in `.agents/skills/sprint-planner/SKILL.md` and release pipeline in `.agents/skills/release-manager/SKILL.md`.
4. **Governance Gating & Contract Tests**: Updated `ALLOWED_ENGINEERING_ENTRIES` and implemented `--check-test-evidence` in `tools/verify_requirement_governance.py`. Updated `ReleaseCiAndLintConfigContractTest.kt`, `cm_plan.md`, and `aspice_governance.md` Rule 8.4.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-100.1` | `TST-PRO-100.3` | Contract Test: Symlink retirement & evidence dir presence | **PASSED** | `Verified` |
| `REQ-PRO-100.2` | `TST-PRO-100.1` | Unit Test: JUnit XML parsing & per-TST correlation | **PASSED** | `Verified` |
| `REQ-PRO-100.3` | `TST-PRO-100.1` | Unit Test: Kover XML parsing & package breakdown | **PASSED** | `Verified` |
| `REQ-PRO-100.4` | `TST-PRO-100.2` | Unit Test: Governance gating `--check-test-evidence` | **PASSED** | `Verified` |
| `REQ-PRO-067.3` | `TST-PRO-067.3` | Contract Test: Volatile build symlink retired | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Unit & Contract Tests
* **Contract Tests**: `ReleaseCiAndLintConfigContractTest` passed **7 / 7 tests PASSED** in Stage 4 execution.
* **Python Test Suites**:
  ```bash
  python3 -m unittest tools/test_generate_test_evidence.py tools/test_verify_requirement_governance.py tools/test_jira_accounts.py tools/hoechstmass/tools/test_hoechstmass_tools.py
  ```
  Result: **158 / 158 tests PASSED** in ~21s across all 4 test suites.
* **Governance & Evidence Checks**:
  * `verify_requirement_governance.py`: **11 / 11 checks PASSED** with 100% parity.
  * `verify_requirement_governance.py --check-verified-evidence`: **PASSED**.
  * `verify_requirement_governance.py --check-test-evidence`: **PASSED**.

---

## 4. Hardware / Physical Verification (Pixel 10)
Pure developer tooling, test evidence generation, and governance infrastructure; zero Android runtime bytecode changes in `app/src/main/`.

---

## 5. System Invariants & Governance Verification
1. **Zero Production Regressions**: Standard library Python 3 and Kotlin contract tests; zero Android runtime bytecode changes.
2. **Evidence Truthfulness & Baseline Control**: Test and coverage results are immutably archived in git configuration management.
3. **Living Documentation Synchronized**: Master index tables match with 100% parity across all 9 domain shards.
