# Stage 5: Walkthrough & Verification - ATT-6122: Report Bidirectional REQ-TST Coverage and TST-to-Test-Code Existence

**Ticket**: [ATT-6122](https://rainerblind.atlassian.net/browse/ATT-6122)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.4` | **Branch**: `sprint/2026-46.4`  
**Requirement Mapping**: `REQ-PRO-071` | **Test Mapping**: `TST-PRO-071`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-12  

---

## 1. Executive Summary & Verification Overview
Implemented bidirectional REQ-TST traceability and test code existence reporting in `tools/verify_requirement_governance.py`. Added `--coverage` and `--coverage-json` modes calculating 4 distinct gap categories:
1. **Category 1 (REQs without TST)**: Requirements citing no test specification in their `Tests` field (521/1067 covered, 48.83%).
2. **Category 2 (TSTs without test code)**: Test specifications with no corresponding test code tag in `app/src/test/` (669/1057 covered, 63.29%).
3. **Category 3 (Code tags without spec)**: Test code tags whose base TST IDs lack specification headings in living docs (11 unique unmapped tags: `TST-MAP-038`, `TST-SET-067`, `TST-SET-070`, `TST-SPT-002`, `TST-SPT-003`, `TST-SPT-008`, `TST-WKO-035..039`).
4. **Category 4 (TSTs not cited back by REQs)**: Test specifications whose referenced requirements do not cite the test back in their `Tests` field (434/1057 covered, 41.06%).

Supported configurable threshold ratchets (`--min-req-tst-coverage`, `--min-tst-code-coverage`, `--max-code-without-spec`, `--min-bidirectional-coverage`) to enforce CI quality gates with deterministic exit codes. Added 7 unit test scenarios (37 total tests in `test_verify_requirement_governance.py` passing 100%), verified live repository CLI modes, and registered `REQ-PRO-071` and `TST-PRO-071` as `[Verified]` in living documentation with 100% master index table parity.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-071.1` | `TST-PRO-071.1` | Unit: Category 1 REQ-without-TST identification (`test_scenario_31`) | **PASSED** | `Verified` |
| `REQ-PRO-071.2` | `TST-PRO-071.2` | Unit: Category 2 TST-without-code and sub-tag matching (`test_scenario_32`) | **PASSED** | `Verified` |
| `REQ-PRO-071.3` | `TST-PRO-071.3` | Unit: Category 3 code tags without spec identification (`test_scenario_33`) | **PASSED** | `Verified` |
| `REQ-PRO-071.4` | `TST-PRO-071.4` | Unit: Category 4 reverse citation verification (`test_scenario_34`) | **PASSED** | `Verified` |
| `REQ-PRO-071.5` | `TST-PRO-071.5` | Unit: Markdown matrix and JSON serialization validation (`test_scenario_35`) | **PASSED** | `Verified` |
| `REQ-PRO-071.5` | `TST-PRO-071.6` | Unit: Configurable threshold ratchet pass and fail gates (`test_scenario_36`) | **PASSED** | `Verified` |
| `REQ-PRO-071` | `TST-PRO-071` | Regression: Authentic live repository coverage integration (`test_scenario_37`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tooling Unit Test Suite (`python3 -m unittest tools/test_verify_requirement_governance.py`)
```text
Ran 37 tests in 5.037s
OK (100% pass rate: scenarios 1–37 all green)
```

### Coverage Report CLI Execution (`python3 tools/verify_requirement_governance.py --coverage`)
```text
# ASPICE Traceability & Verification Coverage Report (REQ-PRO-071)

| Category | Metric Description | Covered / Compliant | Total | Gap Count | Coverage % |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Category 1** | REQs with TST Citation | 521 | 1067 | 546 | 48.83% |
| **Category 2** | TSTs with Test Code | 669 | 1057 | 388 | 63.29% |
| **Category 3** | Code Tags with Valid Spec | 669 | 680 | 11 | 98.38% |
| **Category 4** | TSTs Cited Back by REQs | 434 | 1057 | 623 | 41.06% |

### Category Summaries:
* **Category 1 (REQs without TST)**: 546 requirements cite no test specification in their 'Tests' field.
* **Category 2 (TSTs without test code)**: 388 test specifications have no matching test method in the test directory.
* **Category 3 (Code tags without spec)**: 11 unique test tags in test code have no living document heading: TST-MAP-038, TST-SET-067, TST-SET-070, TST-SPT-002, TST-SPT-003, TST-SPT-008, TST-WKO-035, TST-WKO-036, TST-WKO-037, TST-WKO-038, TST-WKO-039.
* **Category 4 (TSTs not cited back)**: 623 test specifications are not cited back by their referenced requirements.

PASS: Coverage calculated successfully (no thresholds configured).
```

### Threshold Gate Ratcheting Verification
* **Satisfied Threshold (`--min-req-tst-coverage=45 --min-tst-code-coverage=60`)**:
  `PASS: REQ-TST coverage 48.83% meets threshold 45.00%.`
  `PASS: TST-to-code coverage 63.29% meets threshold 60.00%.`
  (Exit code 0)
* **Violated Threshold (`--min-req-tst-coverage=90`)**:
  `ERROR: Gate violated: REQ-TST coverage 48.83% is below threshold 90.00% (Category 1: 521/1067).`
  (Exit code 1)

---

## 4. Hardware / UI Verification
* Developer tooling and governance verifier enhancement; pure Python 3 standard library; zero runtime APK bytecode in `app/src/main/`.
* No Android UI or string resources added; emulator and physical device deployment not required.

---

## 5. Invariant & Governance Verification
1. **Python 3 Standard Library Invariant**: Zero third-party pip dependencies; uses only `re`, `os`, `sys`, `json`, `argparse`, `unittest`.
2. **Deterministic Exit Codes**: 0 on compliant/satisfied runs, 1 on threshold violations or syntax failures.
3. **Non-Breaking Default Execution**: `--coverage` reports findings informatively without failing CI unless explicit thresholds are provided.
4. **Living Documentation Synchronized**: `REQ-PRO-071` and `TST-PRO-071` registered as `[Verified]`; master index tables match domain shards with 100% parity.
