# Stage 3: Implementation Plan - ATT-8213: Mandate and document weekly dual-vendor Gemini and Claude holistic code and architecture reviews

**Ticket**: [ATT-8213](https://rainerblind.atlassian.net/browse/ATT-8213) | **Sub-task**: [ATT-8230](https://rainerblind.atlassian.net/browse/ATT-8230) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-46.8` | **Branch**: `feature/ATT-8213`  
**Requirement Mapping**: `REQ-PRO-095` | **Test Mapping**: `TST-PRO-095`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-15  

---

## 1. Problem Description & Background
In-sprint Stage 6 Cold Reviews inspect single-ticket diffs in isolation. To systematically remediate latent debt and concurrency/leak hazards across the ~200k+ LoC codebase, this plan operationalizes mandatory weekly dual-vendor (Google Gemini + Anthropic Claude) holistic reviews inspecting full subsystem slices alongside deltas, tracked via `docs/engineering/code_reviews/coverage_matrix.md` and scheduled via `ensure-weekly-reviews` placed at `rank-top`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-095` (*Weekly Dual-Vendor Holistic Code & Architecture Review Governance*)
* **Test Mapping**: `TST-PRO-095` (`TST-PRO-095.1`..`TST-PRO-095.5`) in `docs/tests/tst_process_stability.md`

---

## 3. System Invariants & Preserved Behavior
1. **Zero Production Runtime Code Changes**: `app/src/main/` remains untouched.
2. **Pure Python Stdlib Resilience**: Tooling runs without external pip dependencies and gracefully handles mock/offline modes.
3. **Parent Human Gate Invariance**: Rule 3 (`Erledigt` reserved exclusively for human user) strictly preserved.
4. **Autonomous Phase 2 Invariance**: In-sprint execution is never blocked by pending external reviews.

---

## 4. Proposed Architectural Changes
* **Component 1 (`docs/engineering/code_reviews/`)**: Register canonical directory in `cm_plan.md` and `tools/verify_requirement_governance.py`. Seed `coverage_matrix.md` with 9 domains (`tracker`, `banalservice`, `database`, `exporter`, `ui/tracking`, `ui/aftermath`, `ui/settings`, `tools`, `test`) and review report template.
* **Component 2 (`tools/jira_util.py`)**: Implement `ensure_weekly_review_ticket()` (`ensure-weekly-reviews` CLI) with `coverage_matrix.md` parsing, elapsed-day check (> 7 days), Jira deduplication (`parent = ATT-232`), and `rank_top=True`.
* **Component 3 (Governance & Skills)**: Anchor review policy in `docs/coding_standards.md`, `aspice_governance.md`, `sprint-planner/SKILL.md` (Ceremonies 1 & 2), and `release-manager/SKILL.md` (overdue release gate).
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Risk Classification**: **Standard (Low/Medium)** — zero Android production code/UI changes; executed by **`subagent_flash`**.
* **Step 1: Directory & Matrix Setup**: Create `docs/engineering/code_reviews/coverage_matrix.md` and report template. Update `ALLOWED_ENGINEERING_ENTRIES` in `tools/verify_requirement_governance.py`.
* **Step 2: CLI Automation**: Implement `get_next_review_subsystem()` and `ensure_weekly_review_ticket()` in `tools/jira_util.py`.
* **Step 3: Governance & Skills Alignment**: Update `docs/coding_standards.md`, `docs/management/cm_plan.md`, `.agents/rules/aspice_governance.md`, `sprint-planner/SKILL.md`, and `release-manager/SKILL.md`.
* **Step 4: Unit Testing**: Author test cases in `tools/test_jira_accounts.py` and `tools/test_verify_requirement_governance.py`.
* **Step 5: Living Docs Update**: Update `REQ-PRO-095` and `TST-PRO-095` status to `[Implemented]`.
* **Tier 1 Test Command**:
  ```bash
  python3 -m unittest tools/test_jira_accounts.py tools/test_verify_requirement_governance.py && python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Run targeted Python unit tests and governance verification; verify clean dry-run of `ensure-weekly-reviews`.
* **Rollback**: Atomic feature branch isolation (`feature/ATT-8213`) allows clean rollback via git checkout.
