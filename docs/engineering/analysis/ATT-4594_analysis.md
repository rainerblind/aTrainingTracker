# Stage 1 Analysis: ATT-4594 - Restructure Living Documentation into Domain-Sharded Multi-Line Format

**Ticket**: [ATT-4594](https://rainerblind.atlassian.net/browse/ATT-4594)  
**Sub-task**: Pending creation (`[Analysis]`)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-43.9`  
**Branch**: `feature/ATT-4594`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-10-23  

---

## 1. Problem Statement & Motivation
* **Context**: `docs/requirements.md` (3.0 MB, 927 lines) and `docs/tests.md` (1.3 MB, 903 lines) have ballooned into unmanageable single-line Markdown tables with individual lines reaching 11–17.8 KB (e.g. `REQ-UI-165`, `REQ-UI-421`, `REQ-MIG-036`).
* **Friction Points**:
  1. *Tool Truncation*: Reading 15–20 lines blows past the 46 KB `view_file` buffer, necessitating brittle grep chunking.
  2. *Token & Edit Bloat*: Modifying a single requirement via `replace_file_content` consumes ~7,500 tokens for 30 KB replacements.
  3. *UI Monolith & Prefix Fragmentation*: 47% of requirements (358/762) reside in `REQ-UI-*`, while 13 prefixes have only 1–7 entries.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification (ATT-3534)
* **Independent Hypothesis Audit**:
  * *Single-Line Table Formatting*: Tables enforce pipe-delimited single lines with embedded `<br>` tags; Markdown lacks folding/pagination for table cells. Verified.
  * *Tooling Coupling*: `tools/verify_requirement_governance.py` and `tools/jira_util.py` (`auto_verify_living_docs_on_stage5`) hardcode `docs/requirements.md` and table regexes (`\|\s*\*\*REQ-([A-Z0-9_-]+)\*\*`, `\|\s*Planned\s*\|`). Sharding or changing to heading format without updating tooling would break Gate audits. Verified.
  * *7 Domain Directory Shards*: Sharding by functional domain (`tracking_cockpit`, `aftermath_periods`, `maps_navigation`, `data_migration_export`, `settings_profiles`, `ui_design_system`, `process_stability`) under `docs/requirements/` and `docs/tests/` with master index pointers preserves clean modular navigation while keeping individual files under 300–500 KB. Verified.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Dual-format tooling upgrade in `tools/verify_requirement_governance.py` and `tools/jira_util.py` (supporting both table rows and multi-line heading sections across `docs/requirements/` and `docs/tests/`).
  * Automated migration & verification script ensuring 100% preservation of all 762 REQ IDs, 764 TST IDs, sub-clauses, and status tags.
  * Shard living docs into 7 domain files with `docs/requirements.md` and `docs/tests.md` as concise master indices.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No deletion or modification of normative requirement content or acceptance criteria.
  * No changes to application production code (`app/src/...`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-027` (*Agile Agentic ASPICE Governance* — Pillar 2: Artifact-Driven Infinite Context) and `REQ-PRO-026` (*Document Triad Architecture*).
* **Historical Origin & Commit Trace**: Commit `6925d864` (`ATT-3323`, Document Triad) and `39b3df08` (`ATT-3534`, AAA Infinite Context).
* **Root Reason for Existing Formulation**: Single-file tables were initially created in early sprints for simplicity, but linear accumulation across 43 sprints generated 4.3 MB of table bloat.
* **Preservation of Core Invariants**: 100% bidirectional traceability (`REQ-XXX` $\leftrightarrow$ `TST-XXX`), living documentation verification automation, and zero loss of requirement text.

---

## 5. Architectural Strategy & High-Level Solution
* **Phase 1 (Tooling Generalization)**: Update `verify_requirement_governance.py` and `jira_util.py` to recursively scan `docs/requirements/` and `docs/tests/` and support `### REQ-XXX: ... [Status]` headings.
* **Phase 2 (Automated Migration Tool)**: Implement `tools/shard_living_docs.py` to parse monolithic tables, emit multi-line markdown into 7 domain shards, generate master index files, and run an automated bitwise entity-count parity audit.
* **Phase 3 (Master Index & Living Shards)**: Deploy domain-sharded structure under `docs/requirements/` and `docs/tests/` with lightweight master indices at `docs/requirements.md` and `docs/tests.md`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. 100% ID Parity: All 762 `REQ-` and 764 `TST-` IDs preserved without omission.
  2. Governance Automation: `verify_requirement_governance.py` passes with exit code 0.
  3. Single atomic commit per ticket on `sprint/2026-43.9` (Rule 8).
* **Risk Rating**: **MEDIUM** (large documentation migration; mitigated by automated parity verification and dual-format tooling support).
