# Stage 1 Analysis: ATT-5431 - ASPICE Audit Hardening (SWE.1/2/4/6)

**Ticket**: [ATT-5431](https://rainerblind.atlassian.net/browse/ATT-5431)  
**Sub-task**: [ATT-5493](https://rainerblind.atlassian.net/browse/ATT-5493) (`[Analysis]`)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.7` | **Branch**: `feature/ATT-5431`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-05  

---

## 1. Problem Statement & Motivation
An internal ASPICE v3.1/v4.0 assessment revealed 4 compliance vulnerabilities:
1. **SWE.1 & SWE.4/6 Drift**: 565/845 requirements and 647/844 test specs in `docs/requirements.md` and `docs/tests.md` display outdated `Planned` status despite completed implementations.
2. **SWE.2 Architecture Gap**: `docs/architecture.md` (95 lines, 23 REQs) lacks Compose UI, Repositories, ViewModels, Scientific Engines, and dynamic diagrams (`SWE.2.BP4`).
3. **Tooling Automation Gap**: `tools/jira_util.py` (`auto_verify_living_docs_on_stage5`) updates item lines on Stage 5 submission but leaves master summary tables un-synchronized; `verify_requirement_governance.py` lacks counter parity checks.
4. **Skill & Inspection Gap**: Rule 71 omitted `docs/guide/img/` from `read_file` verification (ATT-2275/ATT-5418 root cause); documentation code-grounding and continuous SWE.2 maintenance were uncodified.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  * Legacy format debt: Shards contained non-standard heading brackets (test IDs/acceptance text) parsed as unverified.
  * Architectural stagnation: `docs/architecture.md` was never upgraded from early Java MVC to modern Compose/MVI Clean Architecture.
  * Incomplete Stage 5 automation: `jira_util.py` lacked master index recalculation logic.
* **Independent Peer Review & Verification**:
  * Audited 9 requirement/test shards: 864 REQs and 866 TSTs exist. 556 REQs and 765 TSTs carry unverified status tags despite merged code. Verified genuine open backlog items (`REQ-FIL-007..010` / `ATT-131`) remain cleanly isolated.
  * Audited `docs/architecture.md`: Confirmed 95 lines covering only 23 REQs. Live app spans 45+ packages, 2 foreground services, 10+ engines, and Compose navigation.
  * Audited `jira_util.py` & `verify_requirement_governance.py`: Confirmed missing counter recalculation on Stage 5 and missing CI/governance counter parity assertion.
  * Audited Skills & Rules: Confirmed image inspection, doc code-grounding, and SWE.2 maintenance were missing in `stage1`, `stage3`, `stage4`, `stage5`, and `aspice_governance.md`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Harmonize all completed REQ/TST entries across the 9 shards to `[Verified]`; retain open backlog items as `[Planned]`.
  * Recalculate and synchronize master tables in `docs/requirements.md` and `docs/tests.md`.
  * Modernize `docs/architecture.md` to full ASPICE SWE.2 (Layers, Component Model, Complete REQ Allocation, Mermaid State/Sequence Diagrams, Concurrency/Threading/DB Invariants).
  * Extend `auto_verify_living_docs_on_stage5` in `tools/jira_util.py` to auto-recalculate master tables.
  * Add master counter parity assertions to `tools/verify_requirement_governance.py`.
  * Codify universal `read_file` image audit, documentation code-grounding, and SWE.2 maintenance across skills and `aspice_governance.md`.
* **Out-of-Scope Non-Goals**:
  * No production Kotlin/Java code changes in `app/src/main/`.
  * No modification to product features or open backlog ticket scopes (`ATT-131`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-014`, `REQ-PRO-022`, `REQ-PRO-028`, `REQ-ARC-001`, `REQ-ARC-002`, `REQ-LIV-001`.
* **Historical Origin & Commit Trace**: Established in Sprint `2026-44.1` (`ATT-4594`, sharded living docs) and `2026-45.1` (`ATT-4290`, stage-5 automation).
* **Root Reason for Existing Formulation**: Shard migration initially prioritized file splitting over heading normalization; Stage 5 script did minimal single-line regex replacements.
* **Preservation of Core Invariants**: Retains 100% of requirement semantics, IDs, and traceability links while standardizing status headers and automating counter truth.

---

## 5. Architectural Strategy & High-Level Solution
* Normalize shard headings to `[Verified]` / `[Planned]` and update master index tables.
* Re-author `docs/architecture.md` with static component architecture, dynamic Mermaid flows (Tracking Lifecycle, Sensor Failover, Post-Workout Pipeline), and threading/DB invariants.
* Upgrade `jira_util.py` and `verify_requirement_governance.py` with deterministic table calculation.
* Update governance rules and skill definitions.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero modifications to production source code in `app/src/main/` or existing unit test logic.
  2. `verify_requirement_governance.py` passes with exit code 0.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** (Documentation, governance scripts, and skill guidelines; zero Android runtime risk).
