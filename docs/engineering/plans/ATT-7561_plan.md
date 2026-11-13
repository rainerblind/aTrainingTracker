# Stage 3: Implementation Plan - ATT-7561: Enforce Machine-Verified REQ-TST Bidirectional Traceability

**Ticket**: [ATT-7561](https://rainerblind.atlassian.net/browse/ATT-7561) | **Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7561`  
**Requirement Mapping**: `REQ-PRO-087` (`REQ-PRO-087.1`–`REQ-PRO-087.4`)  
**Test Mapping**: `TST-PRO-087` (`TST-PRO-087.1`–`TST-PRO-087.3`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Problem Description & Background
ASPICE evaluation finding F4 noted requirement-to-test traceability was by convention (assuming same-numbered IDs), not machine-verified. Tooling must verify bidirectional links, parse standard `Verifies: REQ-...` syntax, enforce a shrinking orphan baseline (`tools/traceability_baseline.json`), and reject orphan additions. In Stage 2, historical unreferenced REQs were resolved, leaving 15 legacy unverified REQs. This plan implements the verification engine, baseline ratchet, and unit test suite.

---

## 2. Traceability & Requirements Mapping
* **REQ-PRO-087.1**: `verify_requirement_governance.py` parses machine-readable `Verifies: REQ-...` alongside legacy `Requirement(s):` across living test docs and test specs (`TST-PRO-087.1`).
* **REQ-PRO-087.2**: Bidirectional engine verifies every declared REQ has verifying TSTs and every TST cites authentic REQs, failing on dangling targets (`TST-PRO-087.1`).
* **REQ-PRO-087.3**: Versioned ratchet baseline `tools/traceability_baseline.json` records 15 legacy orphans; new orphans or baseline inflation fail CI ($|B_{t+1}| \le |B_t|$) (`TST-PRO-087.1`).
* **REQ-PRO-087.4**: Historical orphan resolution verified and locked against regression (`TST-PRO-087.3`).

---

## 3. System Invariants & Preserved Behavior
1. **Pure Python 3 Standard Library**: Zero external pip dependencies; cross-platform POSIX compliant.
2. **Deterministic Output & Execution**: Sorted diagnostics, zero set non-determinism, deterministic exit codes (0 = pass, 1 = fail).
3. **Ratchet Monotonicity**: Baseline size $|B_{t+1}| \le |B_t|$; stale baseline entries rejected to force ratchet shrinking.
4. **Backward Compatibility**: Fully preserves existing `Requirements:`, `Requirement(s):`, `Target Requirement:`, and `Requirements Covered:`.
5. **Zero Android Runtime Impact**: Tooling and documentation governance only; no APK code changes.

---

## 4. Proposed Architectural Changes (SWE.2)
* **tools/traceability_baseline.json**:
  - Store JSON structure with metadata and sorted array `orphan_requirements` initialized with the 15 legacy unverified REQs.
* **tools/verify_requirement_governance.py**:
  - Update regex in `find_dangling_references()` and `calculate_traceability_coverage()` to recognize `Verifies:`.
  - Add `load_traceability_baseline(baseline_path, repo_root)` with validation.
  - Implement `find_traceability_orphans(repo_root=None, req_shards=None, tst_shards=None)`.
  - Implement `verify_traceability_orphans(repo_root=None, baseline_path=None, fail_on_orphans=True)`.
  - Wire into `verify_governance()` as an active tripwire and expose `--check-orphans` and `--traceability-baseline` CLI flags.
* **tools/test_verify_requirement_governance.py**:
  - Add unit test suite `TraceabilityOrphansAndBaselineTest` testing all 3 acceptance criteria (unbaselined orphan failure, dangling target failure, ratchet monotonicity, and `Verifies:` field parsing).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Create Traceability Baseline File
* File: `tools/traceability_baseline.json`
* Seed with the 15 verified legacy orphan requirements and metadata.

### Step 2: Implement Bidirectional Verification & Baseline Ratchet
* File: `tools/verify_requirement_governance.py`
* Update regexes to support `Verifies:`; implement `find_traceability_orphans()` and `verify_traceability_orphans()`; wire into `verify_governance()`.

### Step 3: Implement Unit Test Suite (TST-PRO-087.1)
* File: `tools/test_verify_requirement_governance.py`
* Add targeted tests covering clean pass, unbaselined orphan detection, dangling reference detection, ratchet inflation failure, and baseline auto-shrink validation.

### Step 4: Verification & Regression Gate
* Execute: `python3 -m unittest tools/test_verify_requirement_governance.py && python3 tools/verify_requirement_governance.py --sync-indexes`
* Assert 100% test pass rate with zero regressions.

---

## 6. Verification & Rollback Plan
* **Verification**: Unit tests pass and `verify_requirement_governance.py` validates repository cleanly.
* **Rollback**: `git checkout feature/ATT-7561 && git reset --hard 7cbd84f4`
