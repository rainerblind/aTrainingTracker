# Stage 3: Implementation Plan - ATT-6132: Backfill test evidence for UI Design System requirements

**Ticket**: [ATT-6132](https://rainerblind.atlassian.net/browse/ATT-6132)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-085`, `REQ-UI-002`, `REQ-UI-008`, `REQ-UI-009`, `REQ-UI-010`, `REQ-UI-062`, `REQ-UI-135`, `REQ-UI-166`, `REQ-UI-289`  
**Test Mapping**: `TST-PRO-085`, `TST-UI-041`, `TST-UI-044`, `TST-INT-007`  
**Branch**: `subagent-Lane-3-Agent---ATT-6132-self-d4f75212`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background

Remediation ID: A-08.6 under Epic ATT-6119 (ASPICE SWE.6 BP4, SWE.1 BP5). The UI Design System domain (`docs/requirements/req_ui_design_system.md` and `docs/tests/tst_ui_design_system.md`) requires test evidence backfill: eliminating 5 dangling TST citations (`TST-UI-041`, `TST-UI-044`, `TST-INT-007`) and 12 dangling REQ citations (restoring `REQ-UI-002`, `REQ-UI-008`, `REQ-UI-009`, `REQ-UI-010`, `REQ-UI-062`, `REQ-UI-135`, `REQ-UI-166`, `REQ-UI-289`), eliminating 41 bidirectional Category 4 citation gaps, backfilling mandatory metadata attributes (`Priority`, `Type`, `Verification Method` per `REQ-PRO-077`) across all 452 requirements, updating shard header counts, and pruning resolved UI items from verifier legacy allowlists.

---

## 2. Traceability & Requirements Mapping

* **Process Requirement**: `REQ-PRO-085` (*UI Design System Test Evidence Backfill & Bidirectional Traceability Governance*)
* **Process Test Spec**: `TST-PRO-085` (*UI Design System Test Evidence Backfill & Bidirectional Traceability Verification Suite*)
* **Restored Requirements**:
  - `REQ-UI-002` (*TimeFormatter Display Formatting*) $\rightarrow$ `TST-UNT-002`
  - `REQ-UI-008` (*Structured Architecture & Elevation Profile Linking*) $\rightarrow$ `TST-STR-001`
  - `REQ-UI-009` (*Session Isolation & Workout Transition UI*) $\rightarrow$ `TST-UI-029`, `TST-INT-004`, `TST-STR-003`
  - `REQ-UI-010` (*Navigation Drawer Architecture & Tab Configuration Layout*) $\rightarrow$ `TST-INT-005`, `TST-INT-006`
  - `REQ-UI-062` (*App Branding & Header Presentation*) $\rightarrow$ `TST-UI-071`
  - `REQ-UI-135` (*Multi-Dimensional Favorite Tracks Filtering & Modal Sheet*) $\rightarrow$ `TST-UI-088`
  - `REQ-UI-166` (*Lieblingsorte UI/UX Harmonization & Favorite Locations Management*) $\rightarrow$ `TST-UI-070`, `TST-UI-118`
  - `REQ-UI-289` (*Context-Aware Route Selection: Start Proximity & In-Ride Corridor Auto-Detection*) $\rightarrow$ `TST-UI-249`
* **Restored Test Specs**:
  - `TST-UI-041` (*Period Peak Markers Audit*) $\rightarrow$ `REQ-UI-033`, `REQ-UI-035`, `REQ-UI-036`
  - `TST-UI-044` (*Mappable Device Items Layout Verification*) $\rightarrow$ `REQ-UI-042`
  - `TST-INT-007` (*Original Sport Icons Visual Identity Verification*) $\rightarrow$ `REQ-UI-114`

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Unit tests in `app/src/test/` continue to pass cleanly.
2. **Master Index Mathematical Parity**: Master table parity across all 9 domain shards in `docs/requirements.md` and `docs/tests.md` remains 100% synchronized in 4-column format.
3. **Traceability Standards**: 100% of `ui_design_system` REQs cite valid TSTs, zero dangling references in domain, zero ungrounded Verified statuses, and 100% bidirectional citation for mapped TSTs.
4. **Zero Production Runtime Risk**: Changes are strictly confined to living documentation, verifier allowlist pruning, and process governance records.

---

## 4. Proposed Architectural Changes

### Component 1: Test Specification Shards (`tst_ui_design_system.md`, `tst_process_stability.md`)
* Add `TST-UI-041` and `TST-UI-044` in `docs/tests/tst_ui_design_system.md`. Correct shard header to `Total Tests: 466`.
* Add `TST-INT-007` in `docs/tests/tst_process_stability.md`.

### Component 2: Living Requirement Shard (`docs/requirements/req_ui_design_system.md`)
* Restore `REQ-UI-002`, `REQ-UI-008`, `REQ-UI-009`, `REQ-UI-010`, `REQ-UI-062`, `REQ-UI-135`, `REQ-UI-166`, `REQ-UI-289`.
* Correct shard header to `Total Requirements: 452`.
* Harmonize reciprocal test citations across all 41 Category 4 gaps.
* Backfill `Priority`, `Type`, and `Verification Method` on all 452 requirements per `REQ-PRO-077`.

### Component 3: Process Stability Shards (`req_process_stability.md`, `tst_process_stability.md`)
* Register `REQ-PRO-085` and `TST-PRO-085` with complete ASPICE SWE.1/SWE.6 metadata.

### Component 4: Verifier Tooling (`tools/verify_requirement_governance.py`)
* Prune resolved UI entries from `LEGACY_DANGLING_TST_REFS` (`TST-UI-041`, `TST-UI-044`, `TST-INT-007`).
* Prune resolved UI entries from `LEGACY_DANGLING_REQ_REFS` (`REQ-UI-002`, `REQ-UI-008`, `REQ-UI-009`, `REQ-UI-010`, `REQ-UI-062`, `REQ-UI-135`, `REQ-UI-166`, `REQ-UI-289`).
* Prune resolved UI entries from `LEGACY_UNGROUNDED_VERIFIED_REQS`.

### Component 5: Master Index Tables (`docs/requirements.md`, `docs/tests.md`)
* Recalculate and synchronize master tables via `verify_requirement_governance.py --sync-indexes`.

### UI Consistency
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Backfill Test Specifications
* Files: `docs/tests/tst_ui_design_system.md`, `docs/tests/tst_process_stability.md`
* Changes: Add `TST-UI-041`, `TST-UI-044` to `tst_ui_design_system.md` and update header total to 466. Add `TST-INT-007` to `tst_process_stability.md`.
* Verification: Check headings exist.

### Step 2: Backfill Requirements & Resolve Traceability in req_ui_design_system.md
* Files: `docs/requirements/req_ui_design_system.md`
* Changes: Restore the 8 omitted REQs (`REQ-UI-002`, `REQ-UI-008`, `REQ-UI-009`, `REQ-UI-010`, `REQ-UI-062`, `REQ-UI-135`, `REQ-UI-166`, `REQ-UI-289`). Update header to 452. Resolve 41 Category 4 reciprocal links. Backfill attributes for all 452 REQs.
* Verification: `python3 tools/verify_requirement_governance.py --check-attributes`

### Step 3: Register REQ-PRO-085 and TST-PRO-085
* Files: `docs/requirements/req_process_stability.md`, `docs/tests/tst_process_stability.md`
* Changes: Register `REQ-PRO-085` and `TST-PRO-085` with full attributes, archaeology, and test cases.

### Step 4: Prune Verifier Legacy Allowlists
* Files: `tools/verify_requirement_governance.py`
* Changes: Remove resolved UI items from `LEGACY_DANGLING_TST_REFS`, `LEGACY_DANGLING_REQ_REFS`, and `LEGACY_UNGROUNDED_VERIFIED_REQS`.
* Verification: `python3 -m unittest tools/test_verify_requirement_governance.py`

### Step 5: Synchronize Master Indexes & Full Governance Audit
* Files: `docs/requirements.md`, `docs/tests.md`
* Command: `python3 tools/verify_requirement_governance.py --sync-indexes`
* Verification: `python3 tools/verify_requirement_governance.py --coverage --enforce-unique --enforce-links --check-evidence --check-attributes`
