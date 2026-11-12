# Forensic Analysis: Report Bidirectional REQ-TST Coverage and TST-to-Test-Code Existence (ATT-6122)

## 1. Executive Summary & Problem Context
* **Ticket Key**: `ATT-6122`
* **Summary**: `[Verbesserung] Report bidirectional REQ-TST coverage and TST-to-test-code existence`
* **Target Version**: `V5.0.0`
* **Target Branch**: `sprint/2026-46.4`
* **ASPICE Scope**: SWE.6 BP4, SWE.4, SUP.1, GP 2.2
* **Complexity**: 3 SP (Tooling / Traceability Matrix)

### Problem Description & User Directives
1. **Context**: ASPICE CL2 requires evidence integrity and bidirectional traceability across system requirements, software requirements, test specifications, and verified test code execution. While `tools/verify_requirement_governance.py` enforces duplicate ID prevention (ATT-6120) and dangling citation detection (ATT-6121), it cannot currently quantify coverage gaps or verify whether specified tests actually exist in code.
2. **Current State**:
   * Traceability is predominantly one-directional: out of 1056 specified TSTs, 595 (56.3%) are never cited back in any REQ's `Tests` field.
   * Specification-to-code gap: 387 TST headings (36.6%) have no matching test code tag in `app/src/test/`.
   * Code-to-specification gap: 11 base TST IDs tagged across test files do not exist in living doc headings (e.g. `TST-WKO-035..039`, `TST-MAP-038`).
   * Requirements-to-test gap: 546 of 1066 REQs (51.2%) cite no TST in their `Tests` field.
3. **User Directives**:
   * Add `--coverage` mode to `tools/verify_requirement_governance.py`:
     * Category 1: REQs without TST (REQs citing no TST in `Tests` field).
     * Category 2: TSTs without test code (`### TST-*` headings without test code tag in `app/src/test/`).
     * Category 3: Code tags without spec (test code tagged with TST IDs absent from living docs).
     * Category 4: TSTs not cited back by their REQ (TST cites a REQ, but that REQ does not cite the TST back).
   * Produce structured Markdown and JSON reports with counts, totals, and percentages.
   * Provide configurable threshold gates (e.g. `--min-req-tst-coverage`, `--min-tst-code-coverage`, etc.) enabling CI ratcheting.
   * Add comprehensive unit tests in `tools/test_verify_requirement_governance.py`.
   * Register `REQ-PRO-071` and `TST-PRO-071` in `req_process_stability.md` and `tst_process_stability.md`.

---

## 2. Forensic RCA & Independent Hypothesis Verification (4-Augen-Prinzip)
* **Root Cause**: Sharding living docs and incremental development resulted in disconnected traceability islands. Living docs recorded `Tests: None` or omitted citations, while test classes were implemented without back-linking into living documentation or citing orphaned test IDs.
* **Independent Hypothesis Verification**:
  * Scanned 1066 requirements in `docs/requirements/*.md`: 546 REQs lack valid `TST-*` citations (Category 1).
  * Scanned 1056 test specifications in `docs/tests/*.md`: 387 TSTs have no test code tag in `app/src/test/` (Category 2).
  * Scanned 1681 code tags across `app/src/test/`: 11 unique base TST IDs are missing from `docs/tests/` (Category 3).
  * Evaluated reverse cross-citations: 595 TSTs are never cited by any REQ, and 602 TSTs are not cited back by any of their cited REQs (Category 4).
* **Side-Effect Audit (`Rule 68`)**:
  * Default `--coverage` execution must report metrics without failing CI so existing workflows are not broken.
  * Threshold flags (`--min-req-tst-coverage`, `--min-tst-code-coverage`, `--max-code-without-spec`, `--min-bidirectional-coverage`) will fail with exit code 1 only if measured values violate specified ratchets.

---

## 3. Chesterton's Fence Archaeology (`REQ-PRO-022`)
* **Original Requirement**: New requirement `REQ-PRO-071` in `docs/requirements/req_process_stability.md`.
* **Historical Origin**: Remediation item A-03 under Epic `ATT-6119` (ASPICE CL2 – Evidence Integrity & Bidirectional Traceability).
* **Preservation of Core Invariants**: Existing verifier routines (table parity, duplicate IDs, dangling references, Composable islands, repo encapsulation) remain completely intact and unchanged.

---

## 4. User Scope Grounding (`ATT-1250`) & Complexity Split Guardrail
* **In-Scope**:
  * Core coverage calculation engine `calculate_traceability_coverage()` in `tools/verify_requirement_governance.py`.
  * CLI arguments: `--coverage`, `--coverage-json`, `--min-req-tst-coverage`, `--min-tst-code-coverage`, `--max-code-without-spec`, `--min-bidirectional-coverage`.
  * Unit tests in `tools/test_verify_requirement_governance.py` validating 4 gap metrics and threshold checking.
  * Living docs synchronization for `REQ-PRO-071` / `TST-PRO-071`.
* **Out-of-Scope**: Remediating individual missing specs or test code (reserved for A-08.x tickets `ATT-6127..6135`).
* **Complexity Rating**: 3 SP (Tooling / Analysis Matrix; no split required).

---

## 5. Change-Request Impact Analysis (REQ-PRO-064 / ASPICE SUP.10)
* **Affected Requirements**: `REQ-PRO-071` ("Living Documentation Bidirectional REQ-TST Traceability & Code Coverage Reporting").
* **Affected Architecture Elements**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`.
* **Verification Impact & Test Surface**: Unit tests verifying:
  1. Category 1 calculation (REQs without TST).
  2. Category 2 calculation (TSTs without test code).
  3. Category 3 calculation (Code tags without spec).
  4. Category 4 calculation (TSTs not cited back by REQs).
  5. Markdown and JSON report rendering.
  6. Threshold pass/fail evaluations (CI ratcheting).
* **Risk Assessment & Invariants**: LOW risk. Pure Python 3 standard library tooling. Zero Android runtime code touched.

---

## 6. Stage Gate Audit Criteria
- [x] All 4 coverage gap categories identified and grounded in codebase telemetry.
- [x] Structured Markdown and JSON reporting specifications established.
- [x] Configurable threshold ratchet mechanism designed.
- [x] Change-request impact analysis complete across all 4 mandatory dimensions.
- [x] Ready for Stage 1 submission.
