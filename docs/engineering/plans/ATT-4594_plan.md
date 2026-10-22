# Stage 3: Implementation Plan - ATT-4594: Restructure Living Documentation into Domain-Sharded Multi-Line Format

**Ticket**: [ATT-4594](https://rainerblind.atlassian.net/browse/ATT-4594)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V4.9.41` | **Active Sprint**: `2026-43.9` | **Branch**: `feature/ATT-4594`  
**Requirement Mapping**: `REQ-PRO-028` (`REQ-PRO-028.1`–`5`) | **Test Mapping**: `TST-PRO-021` (`TST-PRO-021.1`–`4`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-10-23  

---

## 1. Problem Description & Background
`docs/requirements.md` (3.0 MB) and `docs/tests.md` (1.3 MB) have grown into monolithic single-line tables (lines up to 17.8 KB), causing tool buffer truncation and high token overhead. We shard them into 7 domain-specific files under `docs/requirements/` and `docs/tests/`, switch to multi-line markdown headings (`### REQ-XXX: ... [Status]` / `### TST-XXX: ... [Status]`), and generalize governance tooling to support both table rows and multi-line headings across shards.

---

## 2. Traceability & Requirements Mapping
* `REQ-PRO-028.1` / `TST-PRO-021.1`: 7 domain shard files in `docs/requirements/` and `docs/tests/` with master indices.
* `REQ-PRO-028.2` / `TST-PRO-021.2`: Multi-line markdown section formatting via `tools/shard_living_docs.py`.
* `REQ-PRO-028.3` / `TST-PRO-021.3`: Dual-format scanning in `tools/verify_requirement_governance.py` and `tools/jira_util.py`.
* `REQ-PRO-028.4` / `TST-PRO-021.2`: 100% ID parity validation for all 762 REQ and 764 TST IDs.
* `REQ-PRO-028.5` / `TST-PRO-021.4`: Clean-room test suite and governance pass with exit code 0.

---

## 3. System Invariants & Preserved Behavior
1. **100% ID & Content Preservation**: All existing requirement IDs (`REQ-*-*`) and test IDs (`TST-*-*`) preserved without text omission.
2. **Dual-Format Tooling**: `verify_requirement_governance.py` and `jira_util.py` support both single-file tables and multi-line domain shards.
3. **Rule 4 Code Lock**: Production code in `app/src/...` is locked until Gate 3 is signed off. No production code changes in this ticket.
4. **Clean Git Hygiene**: Single atomic squash commit on `sprint/2026-43.9` with no intermediate commits (Rule 8).

---

## 4. Proposed Architectural Changes
* **Component 1: Sharding Script & Domain Mapping (`tools/shard_living_docs.py`)**:
  - Implements table parsing and markdown section emitter (`### REQ-XXX: <Title> [<Status>]`, description, acceptance criteria, archaeology).
  - Maps prefixes to 7 domain shards: `tracking_cockpit` (`TRK`, `CON`, `FIL`, `SEN`, `FUSION`, `STP`), `aftermath_periods` (`PER`, `PERF`), `maps_navigation` (`MAP`, `NAV`, `GEO`, `ROU`), `data_migration_export` (`DAT`, `MIG`, `EXP`, `EXT`), `settings_profiles` (`SET`, `PRI`), `ui_design_system` (`UI`, `UNT`), `process_stability` (`PRO`, `STB`, `ARC`, `BLD`, `TOOL`, `MAN`, `TST`, `STR`, `SEC`, `REG`, `LIV`, `BUG`, `INT`, `LOG`, `NFR`, `WEA`).
  - Generates lightweight master indices `docs/requirements.md` and `docs/tests.md` linking to all 7 shards.
* **Component 2: Dual-Format Governance Tooling**:
  - `tools/verify_requirement_governance.py`: Update `get_git_diff()` and `parse_diff_requirements()` to inspect `docs/requirements/` directory and match both `| **REQ-XXX**` and `### REQ-XXX:` headers in diff hunks.
  - `tools/jira_util.py` (`auto_verify_living_docs_on_stage5`): Recursively scan `docs/requirements/` and `docs/tests/` (in addition to master indices), updating both table cells and `### ... [Planned]` headers to `[Verified]`.
* **Component 3: Reference Documentation Updates**:
  - Update skill guides and rules (`stage1-analysis`, `stage2-req-test-spec`, `stage5-verification`, `aspice_governance.md`) to reference sharded paths.
* **UI Consistency (Rule 23)**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1: Update Governance Tooling (`REQ-PRO-028.3`)**: Modify `tools/verify_requirement_governance.py` and `tools/jira_util.py` for directory scanning and dual-format regexes. Verify with `tools/test_verify_requirement_governance.py`.
* **Step 2: Implement Sharding Tool (`REQ-PRO-028.2`, `REQ-PRO-028.4`)**: Create `tools/shard_living_docs.py` with AST/table parsing, domain routing, master index generation, and automated entity count & bitwise parity assertions.
* **Step 3: Execute Sharding Migration (`REQ-PRO-028.1`)**: Run `tools/shard_living_docs.py` to produce 7 domain files in `docs/requirements/` and `docs/tests/` and updated master indices.
* **Step 4: Update Skill References & Clean-Room Verification (`REQ-PRO-028.5`)**: Update skill references; run `python3 tools/verify_requirement_governance.py` and `./gradlew testDebugUnitTest`.

---

## 6. Verification & Rollback Plan
* **Verification**: `python3 tools/test_verify_requirement_governance.py`, `python3 tools/shard_living_docs.py --verify-only`, `python3 tools/verify_requirement_governance.py`, and `./gradlew testDebugUnitTest`.
* **Rollback**: Standalone Git branch `feature/ATT-4594`; non-destructive reversible documentation migration.
