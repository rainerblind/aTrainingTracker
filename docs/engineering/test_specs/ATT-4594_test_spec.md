# Stage 2: Requirement & Test Specification - ATT-4594: Restructure living documentation into domain-sharded multi-line sections

**Ticket**: [ATT-4594](https://rainerblind.atlassian.net/browse/ATT-4594)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V4.9.41` | **Active Sprint**: `2026-43.9` | **Branch**: `feature/ATT-4594`  
**Requirement Mapping**: `REQ-PRO-028` (*Living Documentation Sharding, Multi-Line Markdown Section Formatting & Dual-Format Tooling Governance*)  
**Test Spec ID**: `TST-PRO-021`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-10-23  

---

## 1. Requirement Specification (`REQ-PRO-028`)

### 1.1 Problem Statement & Rationale
`docs/requirements.md` (3.0 MB, 927 lines) and `docs/tests.md` (1.3 MB, 903 lines) have grown into monolithic single-line Markdown tables with individual lines reaching 11–17.8 KB. This creates severe friction across sprints: tool output truncation (`view_file` 46 KB cap), token-heavy diffs (~7,500 tokens for single-line edits), and extreme prefix concentration (47% in `REQ-UI-*`). Sharding into 7 domain directories (`docs/requirements/` and `docs/tests/`), adopting multi-line Markdown heading sections (`### REQ-XXX: ... [Status]`), and upgrading governance tooling (`verify_requirement_governance.py` and `jira_util.py`) eliminates single-line bloat while preserving 100% bidirectional traceability.

### 1.2 Functional & Architectural Requirements
1. **Domain Directory Sharding (`REQ-PRO-028.1`)**: Living specifications SHALL be sharded into 7 domain files under `docs/requirements/` and `docs/tests/` (`req_tracking_cockpit.md`, `req_aftermath_periods.md`, `req_maps_navigation.md`, `req_data_migration_export.md`, `req_settings_profiles.md`, `req_ui_design_system.md`, `req_process_stability.md`). `docs/requirements.md` and `docs/tests.md` SHALL serve as lightweight master indices.
2. **Multi-Line Heading & Bullet Format (`REQ-PRO-028.2`)**: Monolithic pipe-delimited table rows SHALL be replaced with structured multi-line Markdown heading sections (`### REQ-XXX: ... [Status]` and `### TST-XXX: ... [Status]`), enabling granular, low-token edits.
3. **Tooling & Verification Parity (`REQ-PRO-028.3`)**: `tools/verify_requirement_governance.py` and `tools/jira_util.py` (`auto_verify_living_docs_on_stage5`) SHALL support dual-format verification, scanning `docs/requirements/` and `docs/tests/` and reliably detecting status transitions (`Planned` -> `Verified`).
4. **100% Migration Parity & ID Preservation (`REQ-PRO-028.4`)**: Automated migration tooling (`tools/shard_living_docs.py`) SHALL guarantee 100% ID and traceability preservation across all 762 `REQ-` and 764 `TST-` IDs without omission.
5. **Preservation of System Invariants (`REQ-PRO-028.5`)**: Zero modification of normative acceptance criteria, human gate governance preservation, and 100% clean-room test suite pass rate.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-028` under Epic `ATT-232` (*Process*), refining `REQ-PRO-026` (*Document Triad Architecture*) and `REQ-PRO-027` (*Agile Agentic ASPICE Governance*). Targets `docs/requirements.md`, `docs/tests.md`, `docs/requirements/`, `docs/tests/`, `tools/verify_requirement_governance.py`, and `tools/jira_util.py`.
* **Historical Origin & Commit Trace**: `REQ-PRO-026` in `ATT-3323` (commit `6925d864`), `REQ-PRO-027` in `ATT-3534` (commit `39b3df08`).
* **Root Reason for Existing Formulation**: Early project phases used single-file Markdown tables for simplicity, which accumulated across 43 sprints to 4.3 MB, causing severe tool truncation and token bloat.
* **Preservation of Core Invariants**: 100% bidirectional traceability, automated requirement governance with exit code 0, and non-destructive documentation migration remain strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC-1 (Domain Shard Directory & Indices)**: *Given* `docs/requirements/` and `docs/tests/`, *When* inspected, *Then* 7 domain-sharded markdown files exist in each directory, and master indices provide links and counts.
* **AC-2 (Multi-Line Format)**: *Given* living specification entries in domain shards, *When* inspected, *Then* entries use multi-line heading format `### REQ-XXX: ... [Status]` and `### TST-XXX: ... [Status]`.
* **AC-3 (Dual-Format Tooling Parity)**: *Given* `tools/verify_requirement_governance.py` and `tools/jira_util.py`, *When* executed against sharded files, *Then* both verify multi-line heading status and diffs with exit code 0.
* **AC-4 (Bitwise Migration Parity)**: *Given* the migration tool `tools/shard_living_docs.py`, *When* executed, *Then* all 762 REQ IDs and 764 TST IDs are preserved with 100% parity.

---

## 2. Test Specification (`TST-PRO-021`)

### Test Cases
* `TST-PRO-021.1` (**Domain Shard Structure & Master Index Verification**): Verify existence of 7 domain files in `docs/requirements/` and `docs/tests/`, and verify master index tables link to all shards.
* `TST-PRO-021.2` (**Bitwise ID & Entity Migration Parity Test**): Verify `tools/shard_living_docs.py` preserves 100% of all 762 `REQ-` and 764 `TST-` IDs, status tags, and sub-clauses.
* `TST-PRO-021.3` (**Dual-Format Governance Verification**): Verify `tools/verify_requirement_governance.py` and `tools/jira_util.py` detect requirements, diffs, archaeology sections, and update status from `[Planned]` to `[Verified]` in domain-sharded multi-line files.
* `TST-PRO-021.4` (**Clean-Room Governance & Full Suite Regression Execution**): Run `python3 tools/verify_requirement_governance.py` asserting exit code 0; execute `./gradlew testDebugUnitTest` verifying 100% test pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-021.1` | Integration | `docs/requirements/`, `docs/tests/`, `docs/requirements.md`, `docs/tests.md` | `REQ-PRO-028.1` | Planned (ATT-4594) |
| `TST-PRO-021.2` | Unit / Script | `tools/shard_living_docs.py` | `REQ-PRO-028.2`, `REQ-PRO-028.4` | Planned (ATT-4594) |
| `TST-PRO-021.3` | Integration | `tools/verify_requirement_governance.py`, `tools/jira_util.py` | `REQ-PRO-028.3` | Planned (ATT-4594) |
| `TST-PRO-021.4` | Regression | Full Test Suite (`./gradlew testDebugUnitTest`), `verify_requirement_governance.py` | `REQ-PRO-028.5`, `REQ-PRO-001` | Planned (ATT-4594) |
