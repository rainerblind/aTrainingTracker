# Stage 1 Analysis: ATT-6123 - Resolve duplicate REQ and TST IDs

**Ticket**: [ATT-6123](https://rainerblind.atlassian.net/browse/ATT-6123)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.4`  
**Branch**: `subagent-Lane-3-Agent---ATT-6123-self-2b7f1f9a`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
Prior to ATT-6120, `tools/verify_requirement_governance.py` only checked master-table count parity and Chesterton's Fence archaeology, allowing 11 duplicate heading IDs across living documentation shards to persist undetected:
* 6 duplicate REQ IDs: `REQ-DAT-001`, `REQ-DAT-026`, `REQ-MIG-018`, `REQ-UI-116`, `REQ-UI-123`, `REQ-UI-257`.
* 5 duplicate TST IDs: `TST-DAT-001`, `TST-DAT-021`, `TST-MAP-055`, `TST-MIG-036`, `TST-UI-216`.

Duplicate identifiers violate ASPICE SWE.1 BP5 and SWE.6 BP4 by making bidirectional traceability ambiguous (e.g. citations in tests and code point to multiple conflicting requirements). In ATT-6120, detection of duplicate headings was added and baseline-allowed via `LEGACY_DUPLICATE_IDS`. Ticket ATT-6123 remediates all 11 duplicates by renumbering non-canonical instances to unique sequential IDs, updating all cross-references in code, tests, and living docs, and emptying `LEGACY_DUPLICATE_IDS` so that `tools/verify_requirement_governance.py --enforce-unique` succeeds with 0 duplicates.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**: During historic sprint additions and the `ATT-4594` living documentation sharding refactor, new requirements and tests were authored with colliding IDs (e.g. re-using 001/026, accidental duplicate copy-paste between shards, or mirroring requirement numbers as test numbers without checking current sequence maximums).
* **Independent Hypothesis Verification (4-Augen-Prinzip)**:
  * Inspected all 11 collisions across `docs/requirements/*.md` and `docs/tests/*.md`:
    1. `REQ-DAT-001`: Line 10 (baseline sensor samples & extrema) vs Line 2012 (ATT-3550 try-with-resources cursor lifecycle).
    2. `REQ-DAT-026`: Line 2052 (ATT-3549 single-row resume query) vs Line 2086 (ATT-3551 cluster engine & equipment mappers).
    3. `REQ-MIG-018`: Line 730 (stray duplicate inserted between REQ-DAT-006/007) vs Line 1441 (canonical sequential position between REQ-MIG-017 and REQ-MIG-019).
    4. `REQ-UI-116`: Line 10 (stray duplicate inserted at shard header) vs Line 388 (canonical sequential position between REQ-UI-115 and REQ-UI-117).
    5. `REQ-UI-123`: Line 418 (canonical compose navigation drawer) vs Line 5627 (ATT-565 localized cluster naming dialog).
    6. `REQ-UI-257`: Line 5585 (canonical ATT-2307 reactive equipment sync) vs Line 5649 (ATT-2059 compact equipment card layout).
    7. `TST-DAT-001`: Line 656 (canonical ATT-1114 sensor data schema) vs Line 1305 (ATT-3550 try-with-resources cursor lifecycle).
    8. `TST-DAT-021`: Line 1323 (canonical ATT-3549 resume query) vs Line 1338 (ATT-3551 cluster engine mappers).
    9. `TST-MAP-055`: Line 778 (canonical ATT-4049 batch route import) vs Line 935 (ATT-4033 climb detection & power calibration).
    10. `TST-MIG-036`: Line 629 (canonical ATT-4518 cloud backup) vs Line 1359 (ATT-3860 offline power metrics).
    11. `TST-UI-216`: Line 1159 (canonical ATT-2307 equipment sync) vs Line 3210 (ATT-2059 compact equipment card layout).
  * Audited caller sites in production/test code: verified that test classes (`TryWithResourcesCursorAuditTest.kt`, `CursorUtilsTest.kt`, `EquipmentCardSpacingContractTest.kt`, `ClimbDetectorTest.kt`, `PowerPhysiologyCalculatorTest.kt`) and tooling scripts (`lint_cursor_safety.py`) cite specific instances requiring updated tags.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Inspect Chesterton's Fence archaeology for all 11 duplicate pairs to confirm canonical vs. renumbered instances.
  2. Renumber the 11 non-canonical instances to sequential IDs at the end of each category's sequence.
  3. Update all cross-references in `docs/requirements/`, `docs/tests/`, `docs/requirements.md`, `docs/tests.md`.
  4. Update code and test tags/comments referencing renumbered IDs.
  5. Remove all resolved IDs from `LEGACY_DUPLICATE_IDS` in `tools/verify_requirement_governance.py`.
  6. Verify `python3 tools/verify_requirement_governance.py --enforce-unique` and `test_verify_requirement_governance.py` pass 100%.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to underlying application logic or runtime behavior.
  * No renumbering of unrelated existing valid IDs.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-066` ("Living Documentation Unique Requirement & Test Identifier Governance") and the 11 remediated legacy items (`REQ-DAT-001`, `REQ-DAT-026`, `REQ-MIG-018`, `REQ-UI-116`, `REQ-UI-123`, `REQ-UI-257`, `TST-DAT-001`, `TST-DAT-021`, `TST-MAP-055`, `TST-MIG-036`, `TST-UI-216`).
* **Historical Origin & Commit Trace**: Commit `e45969cd` (ATT-6120) introduced `find_duplicate_heading_ids` and `LEGACY_DUPLICATE_IDS` as an intermediate baseline allowlist pending ATT-6123.
* **Root Reason for Existing Formulation**: `LEGACY_DUPLICATE_IDS` was necessary to prevent CI breakage across parallel sprint lanes while ATT-6120 established the enforcement mechanism.
* **Preservation of Core Invariants**: The semantics, acceptance criteria, test assertions, and implementation mappings of all 11 items remain 100% intact; only colliding identifier tokens are renumbered to achieve strict global uniqueness.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-PRO-066` (enforcing 0 duplicates), `REQ-DAT-043` (was `REQ-DAT-001` #2), `REQ-DAT-044` (was `REQ-DAT-026` #2), `REQ-MIG-051` (was `REQ-MIG-018` #1), `REQ-UI-521` (was `REQ-UI-116` #1), `REQ-UI-522` (was `REQ-UI-123` #2), `REQ-UI-523` (was `REQ-UI-257` #2).
* **Affected Architecture Elements**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `docs/requirements/*.md`, `docs/tests/*.md`, `docs/requirements.md`, `docs/tests.md`, `tools/lint_cursor_safety.py`, `app/src/test/java/...`.
* **Verification Impact & Test Surface**: Unit tests in `test_verify_requirement_governance.py`, `python3 tools/verify_requirement_governance.py --enforce-unique`, and targeted unit tests referencing renumbered tags.
* **Risk Assessment & Invariants**: LOW risk. Zero runtime bytecode changes; documentation and static analysis reference updates only.

---

## 6. Architectural Strategy & High-Level Solution
* **Renumbering Mapping Plan**:
  * `REQ-DAT-001` (Line 2012) $\rightarrow$ `REQ-DAT-043` (Sub-reqs: `.1`, `.2`, `.3`)
  * `REQ-DAT-026` (Line 2086) $\rightarrow$ `REQ-DAT-044` (Sub-reqs: `.1`, `.2`, `.3`, `.4`)
  * `REQ-MIG-018` (Line 730) $\rightarrow$ `REQ-MIG-051`
  * `REQ-UI-116` (Line 10) $\rightarrow$ `REQ-UI-521` (accommodating concurrent `REQ-UI-518` from ATT-7312)
  * `REQ-UI-123` (Line 5627) $\rightarrow$ `REQ-UI-522`
  * `REQ-UI-257` (Line 5649) $\rightarrow$ `REQ-UI-523`
  * `TST-DAT-001` (Line 1305) $\rightarrow$ `TST-DAT-039` (Subtests: `.1`, `.2`, `.3`)
  * `TST-DAT-021` (Line 1338) $\rightarrow$ `TST-DAT-040` (Subtests: `.1`, `.2`, `.3`, `.4`)
  * `TST-MAP-055` (Line 935) $\rightarrow$ `TST-MAP-070` (Subtests: `.1`..`.6`)
  * `TST-MIG-036` (Line 1359) $\rightarrow$ `TST-MIG-049` (Subtests: `.1`, `.2`, `.3`)
  * `TST-UI-216` (Line 3210) $\rightarrow$ `TST-UI-519` (Subtests: `.1`..`.4`, accommodating concurrent `TST-UI-518` from ATT-7312)
* **Tooling & Verifier Update**:
  * Clear `LEGACY_DUPLICATE_IDS = frozenset()` in `tools/verify_requirement_governance.py`.
  * Update `test_verify_requirement_governance.py` to assert zero duplicates and green pass with empty allowlist.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Shard heading counts remain identical (total 1061 REQs and 1061 TSTs across living documentation).
  2. Bidirectional REQ $\leftrightarrow$ TST links remain completely resolved and valid.
  3. CI governance check `verify_requirement_governance.py --enforce-unique` passes with 0 warnings and 0 errors.
* **Risk Rating**: **LOW** (developer tooling & documentation governance only; zero production app bytecode changes).
