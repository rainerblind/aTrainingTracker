# Stage 3: Implementation Plan - ATT-6122: Report Bidirectional REQ-TST Coverage and TST-to-Test-Code Existence

**Ticket**: [ATT-6122](https://rainerblind.atlassian.net/browse/ATT-6122)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.4` | **Branch**: `sprint/2026-46.4`  
**Requirement Mapping**: `REQ-PRO-071` (`REQ-PRO-071.1`–`REQ-PRO-071.5`)  
**Test Mapping**: `TST-PRO-071` (`TST-PRO-071.1`–`TST-PRO-071.6`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Description & Background
ASPICE CL2 assessment identified critical traceability gaps: 581 TSTs never cited back, 273 spec TSTs without test code, and 159 code tags missing from the spec. Current tooling detects duplicates and dangling links, but cannot quantify bidirectional coverage or enforce CI quality ratchets. This plan defines the implementation of a comprehensive coverage analysis engine, Markdown/JSON reporting, and configurable CI threshold gates in `tools/verify_requirement_governance.py`.

---

## 2. Traceability & Requirements Mapping
* **Requirements**:
  * `REQ-PRO-071.1`: Category 1 - REQs without TST citation.
  * `REQ-PRO-071.2`: Category 2 - TSTs without test code.
  * `REQ-PRO-071.3`: Category 3 - Code tags without specification in living docs.
  * `REQ-PRO-071.4`: Category 4 - TSTs not cited back by their REQ.
  * `REQ-PRO-071.5`: Structured Markdown/JSON reports & configurable threshold gates.
* **Test Mapping**:
  * `TST-PRO-071.1`: Unit test verifying Category 1 REQ-without-TST identification and percentages.
  * `TST-PRO-071.2`: Unit test verifying Category 2 TST-without-code identification and method subtag parsing.
  * `TST-PRO-071.3`: Unit test verifying Category 3 code-tags-without-spec detection.
  * `TST-PRO-071.4`: Unit test verifying Category 4 reverse citation verification.
  * `TST-PRO-071.5`: Unit test verifying Markdown and JSON output formatting.
  * `TST-PRO-071.6`: Unit test verifying threshold ratchet evaluations (pass on satisfied, fail on violated).

---

## 3. System Invariants & Preserved Behavior
1. **Pure Python 3 Standard Library**: Zero external package dependencies (`json`, `re`, `os`, `sys`, `argparse`).
2. **Deterministic Exit Codes**: 0 = PASS, 1 = FAIL.
3. **Non-Breaking Default Execution**: `--coverage` without threshold arguments reports metrics without failing CI.
4. **Sub-Test Tag Compatibility**: Correctly associates method tags with underscore or dot suffixes (`TST-XXX-001_1`, `TST-XXX-001.1`) to their parent TST specification.
5. **Code Lock**: Implementation code locked until Gate 3 audit passes (`Rule 4`).

---

## 4. Proposed Architectural Changes
* **Functions in `tools/verify_requirement_governance.py`**:
  * `extract_test_code_tags(test_dir_or_sources, repo_root=None)`:
    * Walks `app/src/test/` (or synthetic dict/file list) scanning `.kt` and `.java` files.
    * Matches `(?<![A-Za-z0-9_-])(TST-[A-Z]+-[0-9]+)`.
    * Returns dict mapping base TST ID to list of `(rel_path, line_no, raw_tag)`.
  * `calculate_traceability_coverage(req_shards=None, tst_shards=None, test_sources=None, repo_root=None)`:
    * Extracts REQ headings and `Tests` citations.
    * Extracts TST headings and `Requirements` citations.
    * Scans test code tags via `extract_test_code_tags`.
    * Computes metrics and item lists for Categories 1, 2, 3, and 4.
  * `format_coverage_markdown(coverage_data)`:
    * Renders GitHub-flavored Markdown summary table with Category, Metric, Count, Total, and Percentage.
  * `verify_coverage_thresholds(coverage_data, min_req_tst_coverage=None, min_tst_code_coverage=None, max_code_without_spec=None, min_bidirectional_coverage=None)`:
    * Compares calculated percentages and counts against threshold parameters.
    * Returns `(success: bool, diagnostics: list[str])`.
* **CLI Arguments**:
  * `--coverage`: Run coverage calculation and print Markdown report.
  * `--coverage-json`: Output results in JSON format.
  * `--min-req-tst-coverage <pct>`: Minimum percentage of REQs citing TSTs.
  * `--min-tst-code-coverage <pct>`: Minimum percentage of TSTs with test code.
  * `--max-code-without-spec <n>`: Maximum allowed code tags lacking spec.
  * `--min-bidirectional-coverage <pct>`: Minimum percentage of TSTs cited back by REQs.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement Coverage Calculation Engine in `tools/verify_requirement_governance.py`
* Implement `extract_test_code_tags()`.
* Implement `calculate_traceability_coverage()` computing the 4 categories.
* Implement `format_coverage_markdown()` and JSON serialization.
* Implement `verify_coverage_thresholds()`.
* Add CLI argument parsing and execution wiring.

### Step 2: Implement Unit Tests in `tools/test_verify_requirement_governance.py`
* Add `TestTraceabilityCoverageGovernance` test suite with scenarios for:
  1. Category 1 calculation.
  2. Category 2 calculation.
  3. Category 3 calculation.
  4. Category 4 reverse citation calculation.
  5. Markdown and JSON report rendering.
  6. Threshold ratchet pass and fail evaluations.
  7. Live repository scan integration check.

### Step 3: Verification & Execution
* Run unit tests: `python3 -m unittest tools/test_verify_requirement_governance.py`.
* Run live verifier `--coverage` and verify output.
* Run live verifier `--coverage-json` and verify valid JSON structure.
* Verify threshold ratcheting with passing and failing parameters.

---

## 6. Verification & Rollback Plan
* **Verification**: `python3 -m unittest tools/test_verify_requirement_governance.py` and `python3 tools/verify_requirement_governance.py --coverage`.
* **Rollback**: Clean worktree isolation allows immediate checkout/reset without affecting sibling branches.
