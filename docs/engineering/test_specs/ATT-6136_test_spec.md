# Stage 2: Requirement & Test Specification - ATT-6136: Add priority, type and verification-method attributes to requirements

**Ticket**: [ATT-6136](https://rainerblind.atlassian.net/browse/ATT-6136)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-077` (*Requirement Priority, Type, and Verification Method Specification & Governance*)  
**Test Spec ID**: `TST-PRO-077`  
**Branch**: `feature/ATT-6136`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-PRO-077)

### 1.1 Problem Statement & Rationale
Under ASPICE SWE.1 BP2 (Structure software requirements), requirements must not only be categorized and structured, but also prioritized and mapped to verification methods. Historically, requirements across living documentation shards lacked explicit metadata for **Priority**, **Type** (Functional vs. Non-Functional), and **Verification Method**. This prevented automated verification planning, systematic prioritization, and architectural classification between functional capabilities and non-functional constraints.

### 1.2 Functional & Architectural Requirements
1. **Requirement Template Standardization (`REQ-PRO-077.1`)**:
   - The Stage 2 engineering lifecycle skill (`.agents/skills/stage2-req-test-spec/SKILL.md`), test specification deliverable template (`templates/test_spec_template.md`), and living requirements developer guidelines (`docs/requirements.md`) SHALL define and enforce mandatory presence of three core attributes in the requirement header metadata:
     * **Priority**: `Critical`, `High`, `Medium`, or `Low`.
     * **Type**: `Functional` or `Non-Functional`.
     * **Verification Method**: `Unit`, `Integration`, `On-Device Manual`, `Review`, `Formal Analysis` (or composite list thereof).
2. **Automated Attribute Verification Tooling (`REQ-PRO-077.2`)**:
   - `tools/verify_requirement_governance.py` SHALL implement `verify_requirement_attributes()` to parse requirement sections across all domain shards (`docs/requirements/*.md`) and inspect for presence of `- **Priority**:`, `- **Type**:`, and `- **Verification Method**:`, validating values against their permitted domain vocabularies.
3. **Legacy Requirement Warning Tolerance & Diff Enforcement (`REQ-PRO-077.3`)**:
   - In default verification mode, existing legacy requirements that have not yet had attributes backfilled SHALL emit warning diagnostics without failing the process.
   - Any newly added or modified requirement identified in the git diff against the baseline branch SHALL strictly mandate valid Priority, Type, and Verification Method attributes; missing or invalid attributes SHALL immediately fail verification with exit code 1.
4. **Strict Attribute Enforcement CLI Flag (`REQ-PRO-077.4`)**:
   - `tools/verify_requirement_governance.py` SHALL support `--enforce-attributes` / `--fail-on-missing-attributes` command-line flags causing verification to exit with code 1 if ANY requirement in the repository lacks valid Priority, Type, or Verification Method attributes.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Diff Enforcement on New/Modified REQs)**:
  * *Given* a newly added or modified requirement in the git diff,
  * *When* `verify_requirement_governance.py` runs,
  * *Then* it fails with exit code 1 if `Priority`, `Type`, or `Verification Method` is missing or invalid.
* **Criterion 2 (Legacy Warning Tolerance)**:
  * *Given* an existing legacy requirement without attributes not modified in the diff,
  * *When* `verify_requirement_governance.py` runs in default mode,
  * *Then* it outputs a warning diagnostic and passes.
* **Criterion 3 (Strict CLI Enforcement)**:
  * *Given* `--enforce-attributes` or `--fail-on-missing-attributes` is passed,
  * *When* `verify_requirement_governance.py` runs,
  * *Then* it fails with exit code 1 if any requirement in the repository lacks valid attributes.
* **Criterion 4 (Template Standard)**:
  * *Given* `.agents/skills/stage2-req-test-spec/SKILL.md` and `templates/test_spec_template.md`,
  * *When* inspected by developers or AI agents,
  * *Then* the requirement template contains `- **Priority**:`, `- **Type**:`, and `- **Verification Method**:`.

### 1.4 System Invariants
- Zero external runtime dependencies; pure Python 3 standard library.
- 100% mathematical parity across living documentation master index tables.
- No regression in existing governance verifier rules (`verify_unique_ids`, `verify_dangling_references`, `verify_system_software_traceability`, etc.).
- Zero Android production runtime code changes in `app/src/main/`.

---

## 2. Test Specification (TST-PRO-077)

### Test Case 1: Requirement Attribute Presence & Domain Validation Unit Test (`TST-PRO-077.1`)
* **Scope**: Tooling Unit Test (`tools/test_verify_requirement_governance.py`)
* **Preconditions**: Mock requirement shards with complete, missing, and invalid attribute lines.
* **Action**: Invokes `verify_requirement_attributes()`.
* **Expected Result**: Clean pass for complete valid attributes; failure for missing attributes or invalid attribute values.

### Test Case 2: Git Diff Enforcement & Legacy Tolerance Unit Test (`TST-PRO-077.2`)
* **Scope**: Tooling Unit Test (`tools/test_verify_requirement_governance.py`)
* **Preconditions**: Mock git diff containing newly added requirement without attributes alongside untouched legacy requirements.
* **Action**: Invokes `verify_requirement_attributes(diff_text=..., enforce_attributes=False)`.
* **Expected Result**: Fails for new requirement in diff; logs warning for legacy requirement without failing.

### Test Case 3: Strict CLI Enforcement Flag Test (`TST-PRO-077.3`)
* **Scope**: Tooling Unit Test (`tools/test_verify_requirement_governance.py`)
* **Preconditions**: Mock shard with legacy missing attributes and `enforce_attributes=True`.
* **Action**: Invokes `verify_requirement_attributes(enforce_attributes=True)`.
* **Expected Result**: Fails with exit status False and detailed diagnostics.

### Test Case 4: Template & Living Documentation Parity Audit (`TST-PRO-077.4`)
* **Scope**: Integration & Verification Test (`tools/verify_requirement_governance.py`)
* **Preconditions**: Updated `stage2-req-test-spec/SKILL.md`, `test_spec_template.md`, and living docs.
* **Action**: Executes `python3 tools/verify_requirement_governance.py`.
* **Expected Result**: 100% PASS with zero ungrounded requirements and 100% master index table parity.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-077.1` | Tooling Unit | `test_verify_requirement_governance.py:test_requirement_attributes_validation` | `REQ-PRO-077.2` | Specified |
| `TST-PRO-077.2` | Tooling Unit | `test_verify_requirement_governance.py:test_diff_attribute_enforcement_and_legacy_tolerance` | `REQ-PRO-077.3` | Specified |
| `TST-PRO-077.3` | Tooling Unit | `test_verify_requirement_governance.py:test_strict_attribute_enforcement_flag` | `REQ-PRO-077.4` | Specified |
| `TST-PRO-077.4` | Integration / Parity | `tools/verify_requirement_governance.py:verify_governance` | `REQ-PRO-077.1`, `REQ-PRO-077.2` | Specified |
