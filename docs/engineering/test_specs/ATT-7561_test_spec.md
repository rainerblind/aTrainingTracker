# Stage 2: Requirement & Test Specification - ATT-7561: Enforce Machine-Verified REQ-TST Bidirectional Traceability

**Ticket**: [ATT-7561](https://rainerblind.atlassian.net/browse/ATT-7561)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7561`  
**Requirement Mapping**: `REQ-PRO-087` (*Machine-Verified Bidirectional Traceability & Ratchet Baseline Governance*)  
**Test Spec ID**: `TST-PRO-087`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-13  

---

## 1. Requirement Specification (REQ-PRO-087)

### 1.1 Problem Statement & Rationale
Under ASPICE SWE.1 BP5 and SWE.4 BP5 (Finding F4), requirement-to-test traceability was by convention (same-numbered IDs), not machine-verified. Tooling must verify bidirectional links, parse standard `Verifies: REQ-...` syntax, enforce a shrinking orphan baseline (`tools/traceability_baseline.json`), and resolve legacy unlinked requirements.

### 1.2 Attributes
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review

### 1.3 Functional & Architectural Requirements
1. **Standardized Verification Field (`REQ-PRO-087.1`)**: Test specifications and living test docs SHALL parse and standardize on `Verifies: REQ-...` (backward-compatible with `Requirement(s):` / `Target Requirement:`).
2. **Machine-Verified Bidirectional Traceability (`REQ-PRO-087.2`)**: `tools/verify_requirement_governance.py` SHALL verify every declared REQ has verifying TSTs and every TST cites existing REQs; failing on non-existent targets.
3. **Ratchet Baseline File (`REQ-PRO-087.3`)**: Tolerated orphan REQs/TSTs SHALL be recorded in `tools/traceability_baseline.json`. Adding new orphans or baseline expansion MUST fail CI ($|B_{t+1}| \le |B_t|$).
4. **Historical Orphan Resolution & Syntax Normalization (`REQ-PRO-087.4`)**: Resolve 6 unreferenced REQs (`REQ-CON-001`, `REQ-CON-004`, `REQ-CON-006`, `REQ-EXP-005`, `REQ-EXT-003`, `REQ-FIL-005`) and fix malformed citations in `TST-MIG-001` and `TST-UI-310`.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1**: *Given* a requirement with no verifying test outside baseline *When* governance runs *Then* it fails and lists the orphan ID.
* **Criterion 2**: *Given* a test referencing a non-existent requirement *When* governance runs *Then* it fails identifying the dangling target.
* **Criterion 3**: *Given* `tools/traceability_baseline.json` *When* a new orphan is added *Then* CI fails; when one is resolved the baseline shrinks.

### 1.5 System Invariants
- Python 3 standard library only (zero external pip packages).
- Deterministic exit codes (0 = valid, 1 = failure).
- Existing `--sync-indexes` and CI governance passes remain 100% green; zero Android runtime modifications.

---

## 2. Test Specification (TST-PRO-087)

### Test Case 1: `test_bidirectional_traceability_orphans_and_baseline_ratchet` (`TST-PRO-087.1`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py`)
* **Preconditions**: Mock requirement and test shards with synthesized valid links, missing links, and unknown targets.
* **Action**: Invoke `verify_traceability_orphans()`, `load_traceability_baseline()`, and `verify_baseline_ratchet()`.
* **Expected Result**: Clean pass on matched/baselined links; failure on unbaselined orphans, dangling targets, and baseline inflation.

### Test Case 2: 9-Language Localization Audit (`TST-PRO-087.2`)
* **Scope**: Documentation & Resource Parity Audit
* **Goal**: N/A (Internal developer tooling & governance script; no user-facing Android string resources added or modified).
* **Expected Result**: 100% parity, 0 modified XML resource files.

### Test Case 3: Tier 1 Targeted Regression & Parity Suite (`TST-PRO-087.3`)
* **Command**: `python3 -m unittest tools/test_verify_requirement_governance.py && python3 tools/verify_requirement_governance.py --sync-indexes`
* **Goal**: Verify 100% pass rate across governance unit tests and document index synchronization.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-087.1` | Unit | `tools/test_verify_requirement_governance.py` | `REQ-PRO-087.1`, `REQ-PRO-087.2`, `REQ-PRO-087.3` | Specified |
| `TST-PRO-087.2` | Audit | `res/values*/strings.xml` | `REQ-PRO-087`, `REQ-UI-106` | Specified |
| `TST-PRO-087.3` | Regression | `tools/verify_requirement_governance.py` | `REQ-PRO-087.4`, `REQ-PRO-001` | Specified |
