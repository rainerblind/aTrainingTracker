# Stage 2: Requirement & Test Specification - ATT-6122: Report Bidirectional REQ-TST Coverage and TST-to-Test-Code Existence

**Ticket**: [ATT-6122](https://rainerblind.atlassian.net/browse/ATT-6122)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.4` | **Branch**: `sprint/2026-46.4`  
**Requirement Mapping**: `REQ-PRO-071` | **Test Spec ID**: `TST-PRO-071`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-PRO-071)

### 1.1 Problem Statement & Rationale
ASPICE SWE.6 BP4 (Verify Software Unit) and SWE.4 (Software Unit Verification) require bidirectional traceability between software requirements, test specifications, and verified test code. Coverage reporting identifies gaps and allows CI ratcheting so progress can be monitored and gated across 4 distinct gap categories.

### 1.2 Functional & Architectural Requirements
1. **Category 1: REQs Without TST Citation (`REQ-PRO-071.1`)**: `tools/verify_requirement_governance.py` SHALL scan all `docs/requirements/*.md` shards and identify all requirements whose `Tests` field contains no valid test citation (empty, None, or absent).
2. **Category 2: TSTs Without Test Code (`REQ-PRO-071.2`)**: `tools/verify_requirement_governance.py` SHALL scan all `docs/tests/*.md` shards and identify all `### TST-*` headings that have no corresponding test code tag in `app/src/test/`.
3. **Category 3: Code Tags Without Spec (`REQ-PRO-071.3`)**: `tools/verify_requirement_governance.py` SHALL scan `app/src/test/` for all test code tags (`TST-*`) and identify any tagged base test IDs that do not exist as valid headings in `docs/tests/*.md`.
4. **Category 4: TSTs Not Cited Back by Their REQ (`REQ-PRO-071.4`)**: `tools/verify_requirement_governance.py` SHALL analyze cross-citations and identify every test specification (`TST-*`) whose cited requirement does not cite that test back in its `Tests` field.
5. **Structured Report Generation & Configurable Threshold Gates (`REQ-PRO-071.5`)**: `tools/verify_requirement_governance.py` SHALL support generating structured Markdown and JSON reports detailing counts, totals, and percentages for all 4 categories. The verifier SHALL support configurable threshold ratchets (`--min-req-tst-coverage`, `--min-tst-code-coverage`, `--max-code-without-spec`, `--min-bidirectional-coverage`) to enforce CI quality gates.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* `--coverage` is invoked on the repository,
  * *When* `verify_requirement_governance.py` runs,
  * *Then* it outputs a Markdown matrix report of the 4 gap categories with counts, totals, and percentages.
* *Given* `--coverage-json` is specified,
  * *When* `verify_requirement_governance.py` runs,
  * *Then* it outputs structured JSON containing exact metric numbers and category item listings.
* *Given* a coverage threshold is configured and repository telemetry falls below that threshold,
  * *When* `verify_requirement_governance.py` runs,
  * *Then* it exits with code 1 and outputs a descriptive gate violation error.
* *Given* all coverage metrics meet or exceed configured thresholds,
  * *When* `verify_requirement_governance.py` runs,
  * *Then* it outputs a PASS diagnostic and exits with code 0.

### 1.4 System Invariants
Pure Python 3 standard library; zero modifications to Android runtime code in `app/src/main/`; existing governance checks (master table parity, archaeology, uniqueness, dangling references, Composable islands, repo encapsulation) remain 100% intact.

---

## 2. Test Specification (TST-PRO-071)

### Test Case 1: Category 1 Calculation - REQs Without TST (`TST-PRO-071.1`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py`).
* **Assertions**: `calculate_traceability_coverage()` correctly identifies REQs citing no TST, calculating exact count and percentage against total REQs.

### Test Case 2: Category 2 Calculation - TSTs Without Test Code (`TST-PRO-071.2`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py`).
* **Assertions**: `calculate_traceability_coverage()` accurately identifies TST headings lacking corresponding test code tags in test directories, handling sub-test method tag conventions (`TST-XXX-NNN_M`).

### Test Case 3: Category 3 Calculation - Code Tags Without Spec (`TST-PRO-071.3`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py`).
* **Assertions**: `calculate_traceability_coverage()` flags test code tags whose base TST IDs have no heading in living documentation.

### Test Case 4: Category 4 Calculation - TSTs Not Cited Back by REQs (`TST-PRO-071.4`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py`).
* **Assertions**: Identifies TSTs whose cited REQs omit the TST in their `Tests` field.

### Test Case 5: Report Output Formatting (`TST-PRO-071.5`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py`).
* **Assertions**: Verifies Markdown matrix layout and JSON structured payload contain all 4 category counts, totals, percentages, and item details.

### Test Case 6: Threshold Gate Ratcheting (`TST-PRO-071.6`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py`).
* **Assertions**: Verifies that threshold violations trigger validation failure (`success = False`, exit code 1) and that compliant metrics succeed (`success = True`, exit code 0).

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-PRO-071.1]` | Unit | `test_coverage_category_1_reqs_without_tst` | `REQ-PRO-071.1` | Specified |
| `[TST-PRO-071.2]` | Unit | `test_coverage_category_2_tsts_without_code` | `REQ-PRO-071.2` | Specified |
| `[TST-PRO-071.3]` | Unit | `test_coverage_category_3_code_tags_without_spec` | `REQ-PRO-071.3` | Specified |
| `[TST-PRO-071.4]` | Unit | `test_coverage_category_4_tsts_not_cited_back` | `REQ-PRO-071.4` | Specified |
| `[TST-PRO-071.5]` | Unit | `test_coverage_report_formatting_markdown_and_json` | `REQ-PRO-071.5` | Specified |
| `[TST-PRO-071.6]` | Unit | `test_coverage_threshold_ratchets_pass_and_fail` | `REQ-PRO-071.5` | Specified |
