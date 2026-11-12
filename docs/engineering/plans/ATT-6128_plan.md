# Stage 3: Implementation Plan - ATT-6128: Backfill test evidence for Aftermath Reviews requirements

**Ticket**: [ATT-6128](https://rainerblind.atlassian.net/browse/ATT-6128)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-079`  
**Test Mapping**: `TST-PRO-079`  
**Branch**: `subagent-Lane-3-Agent---ATT-6128-self-3dc7c28c`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background

Remediation ID: A-08.2 under Epic ATT-6119 (ASPICE SWE.6 BP4, SWE.1 BP5). The aftermath reviews domain (`docs/requirements/req_aftermath_reviews.md` and `docs/tests/tst_aftermath_reviews.md`) requires test evidence backfill, bidirectional citation integrity, metadata attribute population (`REQ-PRO-077`), header count reconciliation, and test code tag linkage for `TST-REV-009`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-PRO-079` (*Aftermath Reviews Test Evidence Backfill & Bidirectional Traceability Governance*)
* **Test Mapping**: `TST-PRO-079` (*Aftermath Reviews Test Evidence Backfill & Traceability Verification Suite*)
* **Domain REQs**: `REQ-REV-001` through `REQ-REV-012`
* **Domain TSTs**: `TST-REV-001` through `TST-REV-013`

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites and test cases continue to pass cleanly.
2. **Master Index Mathematical Parity**: Master table parity across all 9 domain shards in `docs/requirements.md` and `docs/tests.md` remains 100% synchronized.
3. **Traceability Standards**: 100% of `aftermath_reviews` REQs cite valid TSTs, 100% of TSTs have test code tags, zero dangling references, and 100% bidirectional citation.
4. **Zero Production Runtime Risk**: Changes are strictly confined to living documentation, test tags, and process governance records.

---

## 4. Proposed Architectural Changes

### Component 1: Test Code Citation (`ZoneDistributionCardTest.kt`)
* Annotate class header with `(REQ-REV-008, TST-REV-009 / ATT-6044)` so `verify_requirement_governance.py --coverage` resolves `TST-REV-009` to active test code.

### Component 2: Living Requirement Shard (`docs/requirements/req_aftermath_reviews.md`)
* Correct shard header to `Total Requirements: 12`.
* Reconcile `REQ-REV-002` Tests field to `- **Tests**: \`TST-REV-002\`, \`TST-REV-005\``.
* Populate `Priority`, `Type`, and `Verification Method` on all 12 `REQ-REV-*` items.

### Component 3: Living Test Specification Shard (`docs/tests/tst_aftermath_reviews.md`)
* Correct shard header to `Total Tests: 13`.

### Component 4: Process Stability Shards (`req_process_stability.md`, `tst_process_stability.md`)
* Register `REQ-PRO-079` and `TST-PRO-079` with complete ASPICE SWE.1/SWE.6 metadata.

### Component 5: Master Index Tables (`docs/requirements.md`, `docs/tests.md`)
* Recalculate and synchronize master tables via `verify_requirement_governance.py --sync-indexes`.

### UI Consistency
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Annotate ZoneDistributionCardTest.kt
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionCardTest.kt`
* Changes: Add test tag `TST-REV-009` and requirement reference `REQ-REV-008` in Javadoc.
* Verification: `python3 -c "from tools.verify_requirement_governance import extract_test_code_tags; print('TST-REV-009' in extract_test_code_tags())"`

### Step 2: Update req_aftermath_reviews.md
* Files: `docs/requirements/req_aftermath_reviews.md`
* Changes: Update header total to 12. Add `TST-REV-005` to `REQ-REV-002`. Backfill `Priority`, `Type`, `Verification Method` for all 12 REQs.
* Verification: `python3 tools/verify_requirement_governance.py --check-attributes`

### Step 3: Update tst_aftermath_reviews.md
* Files: `docs/tests/tst_aftermath_reviews.md`
* Changes: Update header total to 13.
* Verification: `python3 tools/verify_requirement_governance.py --check-links`

### Step 4: Register REQ-PRO-079 and TST-PRO-079
* Files: `docs/requirements/req_process_stability.md`, `docs/tests/tst_process_stability.md`
* Changes: Append `REQ-PRO-079` and `TST-PRO-079`.
* Verification: `python3 tools/verify_requirement_governance.py --check-links`

### Step 5: Synchronize Master Indexes & Recalculate
* Files: `docs/requirements.md`, `docs/tests.md`
* Changes: Execute `python3 tools/verify_requirement_governance.py --sync-indexes`.
* Verification: `git diff docs/requirements.md docs/tests.md`

### Step 6: Governance & Test Execution
* Commands:
  - `python3 tools/verify_requirement_governance.py --coverage`
  - `python3 tools/verify_requirement_governance.py --check-links`
  - `python3 tools/verify_requirement_governance.py --check-evidence`
  - `python3 tools/verify_requirement_governance.py --check-attributes`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionCardTest"`
