# Stage 5 Walkthrough: ATT-4594 — Restructure Living Documentation into Domain-Sharded Multi-Line Sections

## 1. Executive Summary
Restructured `docs/requirements.md` (2.88 MB) and `docs/tests.md` (1.18 MB) into 7 domain-sharded, multi-line Markdown files under `docs/requirements/` and `docs/tests/` with lightweight master indexes in `docs/requirements.md` and `docs/tests.md`, eliminating single-line table bloat (up to 17.8 KB/line) while preserving 100% of requirement IDs (`763` unique `REQ-*` IDs) and test IDs (`765` unique `TST-*` IDs).

## 2. Key Architectural Changes
| File / Directory | Summary of Changes |
| :--- | :--- |
| `tools/shard_living_docs.py` | Deterministic parser and sharder migrating legacy single-line Markdown table rows into 7 domain-sharded multi-line `### REQ-XXX-NNN` / `### TST-XXX-NNN` sections and generating master index tables. |
| `docs/requirements/req_*.md` & `docs/tests/tst_*.md` | 7 domain-sharded requirement files (`req_tracking_cockpit.md`, `req_aftermath_periods.md`, `req_maps_navigation.md`, `req_data_migration_export.md`, `req_settings_profiles.md`, `req_ui_design_system.md`, `req_process_stability.md`) and 7 corresponding test files (`tst_*.md`). |
| `docs/requirements.md` & `docs/tests.md` | Converted into lightweight master indexes linking to the 7 domain shards with entry counts and prefix mappings. |
| `tools/verify_requirement_governance.py` | Updated parser to load all `docs/requirements/req_*.md` and `docs/tests/tst_*.md` shards (plus legacy table fallback) and validate `REQ-*` / `TST-*` bidirectional traceability, status (`Verified` / `Planned`), and Chesterton's Fence archaeology. |
| `tools/jira_util.py` | Updated `auto_verify_living_docs_on_stage5` to scan `docs/requirements/*.md` and `docs/tests/*.md` and transition multi-line `### REQ-... [Planned]` / `- **Status**: Planned` entries to `Verified` / `Passed` on Stage 5 submission. |
| `tools/test_verify_requirement_governance.py` | Added unit tests covering multi-line domain-sharded governance validation and automatic Stage 5 `Planned` -> `Verified` transitions. |
| `.agents/skills/stage2-req-test-spec/SKILL.md` & `stage5-verification/SKILL.md` | Updated living documentation paths and multi-line section format guidance. |

## 3. Verification & Test Results
* **Resource & Build Gate**: `./gradlew processDebugResources` -> `BUILD SUCCESSFUL`.
* **Governance Unit Tests**: `python3 -m unittest tools/test_verify_requirement_governance.py` -> `Ran 7 tests in 0.048s — OK`.
* **Living Documentation Governance Audit**: `python3 tools/verify_requirement_governance.py` -> `EXIT CODE 0` (100% ID parity: 763 `REQ-*` and 765 `TST-*` entries verified).

## 4. Requirement & Test Traceability
| Requirement ID | Test ID | Status | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-PRO-028` | `TST-PRO-021` | `Verified` | `tools/test_verify_requirement_governance.py` & `tools/verify_requirement_governance.py` |
