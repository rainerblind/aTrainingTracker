# Stage 3: Implementation Plan - ATT-6131: Backfill test evidence for Maps and Navigation requirements

**Ticket**: [ATT-6131](https://rainerblind.atlassian.net/browse/ATT-6131)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-081`, `REQ-MAP-037`  
**Test Mapping**: `TST-PRO-081`, `TST-MAP-038`, `TST-MAP-039`  
**Branch**: `subagent-Lane-3-Agent---ATT-6131-self-93a50f27`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background

Remediation ID: A-08.5 under Epic ATT-6119 (ASPICE SWE.6 BP4, SWE.1 BP5). The Maps and Navigation domain (`docs/requirements/req_maps_navigation.md` and `docs/tests/tst_maps_navigation.md`) requires test evidence backfill, elimination of dangling reference `REQ-MAP-037`, restoration of unmapped test specification `TST-MAP-038`, bidirectional citation alignment, metadata attribute population (`Priority`, `Type`, `Verification Method` per `REQ-PRO-077`), status grounding for manual claims (`REQ-MAP-001`, `REQ-MAP-002` downgraded to `[Implemented]`), header count reconciliation, and test code tag linkage for `TST-MAP-039`.

---

## 2. Traceability & Requirements Mapping

* **Process Requirement**: `REQ-PRO-081` (*Maps and Navigation Test Evidence Backfill & Bidirectional Traceability Governance*)
* **Process Test Spec**: `TST-PRO-081` (*Maps and Navigation Test Evidence Backfill & Traceability Verification Suite*)
* **Restored Requirement**: `REQ-MAP-037` (*Route Waypoint Marker Badge Diameter Optimization & Glanceability Proportional Scaling*)
* **Restored Test Spec**: `TST-MAP-038` (*Known Locations: Heim-Basis Redundant Badge Excision & Start-Count Home Resolution Alignment Verification*)
* **Domain REQs**: `REQ-MAP-001` through `REQ-MAP-067`, `REQ-GEO-001`..`002`, `REQ-NAV-043`, `REQ-NAV-056`, `REQ-RAD-006`, `REQ-CLM-008`..`011` (Total: 75)
* **Domain TSTs**: `TST-NAV-001`..`009`, `TST-MAP-010`..`070`, `TST-GEO-001`..`002`, `TST-NAV-045`, `TST-NAV-056`, `TST-RAD-006`, `TST-CLM-008`..`011` (Total: 70)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites and test cases continue to pass cleanly.
2. **Master Index Mathematical Parity**: Master table parity across all 9 domain shards in `docs/requirements.md` and `docs/tests.md` remains 100% synchronized.
3. **Traceability Standards**: 100% of `maps_navigation` REQs cite valid TSTs, zero dangling references in domain, zero ungrounded Verified statuses, and 100% bidirectional citation for mapped TSTs.
4. **Zero Production Runtime Risk**: Changes are strictly confined to living documentation, test tags, verifier allowlist pruning, and process governance records.

---

## 4. Proposed Architectural Changes

### Component 1: Test Code Annotation (`WaypointBadgeMarkerTest.kt`)
* Annotate class header with `(REQ-MAP-037, TST-MAP-039 / ATT-2628)` so `verify_requirement_governance.py --coverage` resolves `TST-MAP-039` to active test code.

### Component 2: Living Requirement Shard (`docs/requirements/req_maps_navigation.md`)
* Correct shard header to `Total Requirements: 75`.
* Restore `REQ-MAP-037` with full specification and attributes, citing `TST-MAP-039`.
* Downgrade `REQ-MAP-001` and `REQ-MAP-002` to `[Implemented]` per `REQ-PRO-073`.
* Harmonize test citations: `REQ-MAP-055` (`TST-MAP-070`), `REQ-MAP-025` (`TST-MAP-027`, `TST-MAP-070`), `REQ-MAP-027` (`TST-MAP-029`, `TST-MAP-070`), `REQ-GEO-002` (`TST-GEO-002`, `TST-NAV-056`), `REQ-MAP-007` (`TST-UNT-010`), `REQ-MAP-016` (`TST-MAP-010`), `REQ-MAP-004` (`TST-MAP-044`), `REQ-MAP-036` (`TST-MAP-036`, `TST-MAP-038`).
* Backfill `Priority`, `Type`, and `Verification Method` on all 75 requirements per `REQ-PRO-077`.

### Component 3: Living Test Specification Shard (`docs/tests/tst_maps_navigation.md`)
* Correct shard header to `Total Tests: 70`.
* Restore `TST-MAP-038` specification covering `REQ-MAP-036`.
* Harmonize requirement back-links: `TST-MAP-070` (`REQ-MAP-055`, `REQ-MAP-027`, `REQ-MAP-025`), `TST-MAP-056` (`REQ-MAP-053`), `TST-MAP-044` (`REQ-MAP-042`, `REQ-MAP-004`).

### Component 4: Verifier Tooling (`tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`)
* Remove `REQ-MAP-037` from `LEGACY_DANGLING_REQ_REFS`.

### Component 5: Process Stability Shards (`req_process_stability.md`, `tst_process_stability.md`)
* Register `REQ-PRO-081` and `TST-PRO-081` with complete ASPICE SWE.1/SWE.6 metadata.

### Component 6: Master Index Tables (`docs/requirements.md`, `docs/tests.md`)
* Recalculate and synchronize master tables via `verify_requirement_governance.py --sync-indexes`.

### UI Consistency
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Annotate WaypointBadgeMarkerTest.kt
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/WaypointBadgeMarkerTest.kt`
* Changes: Add test tag `TST-MAP-039` and requirement reference `REQ-MAP-037` in Javadoc.
* Verification: `python3 -c "from tools.verify_requirement_governance import extract_test_code_tags; print('TST-MAP-039' in extract_test_code_tags())"`

### Step 2: Update req_maps_navigation.md
* Files: `docs/requirements/req_maps_navigation.md`
* Changes: Header total to 75. Insert `REQ-MAP-037`. Downgrade `REQ-MAP-001`, `REQ-MAP-002` to `[Implemented]`. Harmonize test citations. Backfill attributes for all 75 REQs.
* Verification: `python3 tools/verify_requirement_governance.py --check-attributes`

### Step 3: Update tst_maps_navigation.md
* Files: `docs/tests/tst_maps_navigation.md`
* Changes: Header total to 70. Insert `TST-MAP-038`. Align requirement back-links.
* Verification: `python3 tools/verify_requirement_governance.py --check-links`

### Step 4: Prune Verifier Legacy Allowlist
* Files: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`
* Changes: Remove `REQ-MAP-037` from `LEGACY_DANGLING_REQ_REFS`.
* Verification: `python3 tools/test_verify_requirement_governance.py`

### Step 5: Register REQ-PRO-081 and TST-PRO-081
* Files: `docs/requirements/req_process_stability.md`, `docs/tests/tst_process_stability.md`
* Changes: Append `REQ-PRO-081` and `TST-PRO-081`.
* Verification: `python3 tools/verify_requirement_governance.py --check-links`

### Step 6: Synchronize Master Indexes & Recalculate
* Files: `docs/requirements.md`, `docs/tests.md`
* Changes: Execute `python3 tools/verify_requirement_governance.py --sync-indexes`.
* Verification: `git diff docs/requirements.md docs/tests.md`

### Step 7: Full Governance & Test Execution
* Commands:
  - `python3 tools/verify_requirement_governance.py --coverage`
  - `python3 tools/verify_requirement_governance.py --check-links`
  - `python3 tools/verify_requirement_governance.py --check-evidence`
  - `python3 tools/verify_requirement_governance.py --check-attributes`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.WaypointBadgeMarkerTest" --tests "com.atrainingtracker.trainingtracker.routes.HomeLocationResolverTest"`
