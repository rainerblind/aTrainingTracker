# Stage 3: Implementation Plan - ATT-6136: Add priority, type and verification-method attributes to requirements

**Ticket**: [ATT-6136](https://rainerblind.atlassian.net/browse/ATT-6136)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Branch**: `feature/ATT-6136`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Architectural Strategy & Design (SWE.2)

To satisfy ASPICE SWE.1 BP2 (*"Structure software requirements"*), this implementation incorporates Priority, Type, and Verification Method attributes into the requirement lifecycle, lifecycle skills, deliverable templates, and automated CI governance tooling.

### Key Architectural Elements:
1. **Lifecycle Skill & Template Standardization**:
   - `.agents/skills/stage2-req-test-spec/SKILL.md`: Update Stage 2 requirement template with:
     * `- **Priority**: Critical / High / Medium / Low`
     * `- **Type**: Functional / Non-Functional`
     * `- **Verification Method**: Unit / Integration / On-Device Manual / Review / Formal Analysis`
   - `.agents/skills/stage2-req-test-spec/templates/test_spec_template.md`: Add metadata attributes to Section 1.
   - `docs/requirements.md`: Update Developer & AI Agent Guidelines with standard attribute snippet.
2. **Automated Governance Tooling (`tools/verify_requirement_governance.py`)**:
   - Define allowed domain vocabularies:
     * `ALLOWED_PRIORITIES = {"Critical", "High", "Medium", "Low"}`
     * `ALLOWED_TYPES = {"Functional", "Non-Functional", "F", "NFR"}`
     * `ALLOWED_VERIFICATION_METHODS = {"Unit", "Integration", "On-Device Manual", "Review", "Formal Analysis"}` (also recognizing comma- or slash-separated combinations).
   - Implement `find_missing_requirement_attributes()`:
     Parses living requirement headings (`### REQ-*`) and checks for presence and validity of `Priority`, `Type`, and `Verification Method`.
   - Implement `verify_requirement_attributes()`:
     * *Diff-based enforcement*: If `diff_text` is provided, any newly added or modified requirement missing valid attributes immediately fails verification (exit code 1).
     * *Legacy tolerance*: Pre-existing legacy requirements missing attributes emit `WARNING (Legacy missing attributes)` without failing default verification.
     * *Strict enforcement*: When `--enforce-attributes` / `--fail-on-missing-attributes` is passed, any missing or invalid attribute anywhere in the repository fails verification.
   - Add CLI options `--enforce-attributes` and `--fail-on-missing-attributes` to `main()`.
   - Integrate `verify_requirement_attributes()` into `verify_governance()`.
3. **Automated Test Suite (`tools/test_verify_requirement_governance.py`)**:
   - Add comprehensive test scenarios validating valid attributes, missing attributes, invalid domain values, diff enforcement, legacy tolerance, and strict CLI flag.

---

## 2. Step-by-Step Implementation Sequence

| Step | Scope | Target File(s) | Description | Verification Method |
| :--- | :--- | :--- | :--- | :--- |
| **Step 1** | Governance Verification | `tools/jira_util.py` | Verify Gate 3 clearance (`check-gate`) confirming Code Lock release. | `python3 tools/jira_util.py check-gate <SUBTASK_KEY>` |
| **Step 2** | Lifecycle Skills & Templates | `.agents/skills/stage2-req-test-spec/SKILL.md`, `templates/test_spec_template.md`, `docs/requirements.md` | Update requirement template with Priority, Type, and Verification Method attributes. | File inspection & markdown validation |
| **Step 3** | Governance Tooling | `tools/verify_requirement_governance.py` | Implement `find_missing_requirement_attributes()`, `verify_requirement_attributes()`, and CLI flags. | Code review & syntax check |
| **Step 4** | Unit Tests | `tools/test_verify_requirement_governance.py` | Add unit tests covering all attribute verification paths (valid, missing, invalid, diff, strict). | `python3 -m unittest tools/test_verify_requirement_governance.py` |
| **Step 5** | Living Docs Transition | `docs/requirements/req_process_stability.md`, `docs/tests/tst_process_stability.md` | Transition `REQ-PRO-077` and `TST-PRO-077` to `[Verified]`, synchronize master tables. | `python3 tools/verify_requirement_governance.py --sync-indexes` |
| **Step 6** | Full Governance & Integration Gate | `tools/verify_requirement_governance.py` | Run complete governance verification suite and unit tests under parallel sprint lock. | `python3 tools/parallel_sprint_lock.py python3 tools/verify_requirement_governance.py` |

---

## 3. Invariants & Rollback Safety

- **Code Lock Enforcement**: Pure compliance with Rule 4 (`check-gate`) before editing any production or tooling scripts.
- **Python Standard Library Only**: No third-party modules or external dependencies introduced.
- **Non-Breaking CI Compatibility**: Existing 1076 legacy requirements do not break default verification while awaiting backfill batches.
- **Zero Android Production Runtime Code Modification**: Changes strictly isolated to governance tooling, unit tests, and documentation.
- **Master Index Parity**: Living documentation master tables maintain 100% mathematical parity across all 9 domain shards.
