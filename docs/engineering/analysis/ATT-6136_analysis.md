# Stage 1 Analysis: ATT-6136 - Add priority, type and verification-method attributes to requirements

**Ticket**: [ATT-6136](https://rainerblind.atlassian.net/browse/ATT-6136)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Branch**: `feature/ATT-6136`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation

During the independent ASPICE 4.0 pre-assessment (baseline develop @ sprint/2026-45.11, Remediation ID: A-09, ASPICE SWE.1 BP2), software requirements were identified as lacking fundamental architectural attributes:
* Requirements across the 9 living requirement domain shards in `docs/requirements/*.md` currently have no **Priority**, no **Type** (Functional / Non-Functional), and no **Verification Method**.
* Without these attributes, requirements cannot be systematically prioritized for implementation or verification, Non-Functional Requirements (NFRs) cannot be distinguished programmatically from Functional Requirements, and verification methods (Unit, Integration, On-Device Manual, Review, Formal Analysis) cannot be planned or audited.
* To achieve ASPICE CL2 compliance under SWE.1 BP2 (*"Structure software requirements"*), every newly authored or modified requirement must explicitly declare Priority, Type, and Verification Method, supported by automated CI governance tooling.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Root Cause**:
  1. The living requirements section format established in ATT-4594 (`REQ-PRO-028`) defined fields for `- **Tests**:`, `- **Implementation**:`, `- **Rationale**:`, and `- **Description**:`, but omitted structured fields for Priority, Type, and Verification Method.
  2. The Stage 2 engineering lifecycle skill (`.agents/skills/stage2-req-test-spec/SKILL.md` and `templates/test_spec_template.md`) lacks mandatory prompts/templates for Priority, Type, and Verification Method.
  3. `tools/verify_requirement_governance.py` enforces master table parity, unique IDs, reference integrity, and ungrounded Verified status, but lacks an attribute verification function (`verify_requirement_attributes()`).
* **Independent Hypothesis Verification (4-Augen-Prinzip)**:
  - Codebase inspection reveals 1076 living requirements in `docs/requirements/`. A bulk enforcement rule would immediately fail CI across all shards.
  - Therefore, verification tooling must implement a two-tier strategy:
    (a) Newly authored or modified requirements in the git diff MUST mandate all three attributes.
    (b) Legacy requirements without attributes emit warnings (or are tracked via legacy baseline) in default verification mode, while `--enforce-attributes` / `--fail-on-missing-attributes` provides strict repository-wide enforcement.
    (c) Supported attribute values must be strictly validated:
        * **Priority**: `Critical`, `High`, `Medium`, `Low`.
        * **Type**: `Functional`, `Non-Functional` (or `F`, `NFR`).
        * **Verification Method**: `Unit`, `Integration`, `On-Device Manual`, `Review`, `Formal Analysis` (or combinations thereof).

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Update requirement template in `.agents/skills/stage2-req-test-spec/SKILL.md`, `templates/test_spec_template.md`, and `docs/requirements.md` to include:
     - `- **Priority**: Critical / High / Medium / Low`
     - `- **Type**: Functional / Non-Functional`
     - `- **Verification Method**: Unit / Integration / On-Device Manual / Review / Formal Analysis`
  2. Implement `verify_requirement_attributes()` in `tools/verify_requirement_governance.py` with:
     - Diff-based enforcement: fail if any net-new or modified requirement is missing required attributes or contains invalid values.
     - Legacy tolerance/warning mode for pre-existing requirements pending future backfill.
     - CLI options `--enforce-attributes` / `--fail-on-missing-attributes` for strict mode.
  3. Author automated unit tests in `tools/test_verify_requirement_governance.py` verifying detection of missing or invalid attributes, legacy warning tolerance, diff enforcement, and strict CLI mode.
  4. Register `REQ-PRO-077` and `TST-PRO-077` in living docs (`docs/requirements/req_process_stability.md`, `docs/tests/tst_process_stability.md`) with 100% master index parity.
* **Out-of-Scope Non-Goals**:
  - Immediate manual backfilling of all 1076 legacy requirements (scheduled for separate remediation batches).
  - Modifications to Android runtime production code (`app/src/main/`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirements `REQ-PRO-077` and `TST-PRO-077`. Targets `tools/verify_requirement_governance.py`, `.agents/skills/stage2-req-test-spec/SKILL.md`, and living docs.
* **Historical Origin & Commit Trace**: Remediation A-09, ASPICE SWE.1 BP2. Builds upon ATT-4594 (`docs/requirements/` sharding), ATT-6120 (unique heading IDs), and ATT-6126 (evidence status verification).
* **Root Reason for Existing Formulation**: Multi-line requirement sections previously focused on eliminating single-line markdown table bloat. Adding structured metadata attributes operationalizes SWE.1 BP2 without disturbing existing parsing infrastructure.
* **Preservation of Core Invariants**: Existing regex parsers, unique ID verification, dangling link detection, and master table parity calculators remain 100% functional and compatible.

---

## 5. Change-Request Impact Analysis (SUP.10)

* **Affected Requirements**: Net-new `REQ-PRO-077`, `TST-PRO-077`.
* **Affected Architecture Elements**:
  - `.agents/skills/stage2-req-test-spec/SKILL.md`
  - `.agents/skills/stage2-req-test-spec/templates/test_spec_template.md`
  - `docs/requirements.md`
  - `tools/verify_requirement_governance.py`
  - `tools/test_verify_requirement_governance.py`
  - `docs/requirements/req_process_stability.md` & `docs/tests/tst_process_stability.md`
  - `docs/requirements.md` & `docs/tests.md`
* **Verification Impact & Test Surface**:
  - `python3 tools/test_verify_requirement_governance.py`
  - `python3 tools/verify_requirement_governance.py`
* **Risk Assessment & Invariants**: Low risk. Confined to process specifications, developer governance tooling, and unit tests. Zero Android runtime APK risk.

---

## 6. Architectural Strategy & High-Level Solution

1. **Template Standardization**:
   - Add standard metadata fields (`Priority`, `Type`, `Verification Method`) to `stage2-req-test-spec/SKILL.md` and `docs/requirements.md`.
2. **Governance Tooling**:
   - Implement `find_missing_requirement_attributes()` and `verify_requirement_attributes()` in `tools/verify_requirement_governance.py`.
   - Support parsing multi-line requirement blocks, identifying `- **Priority**:`, `- **Type**:`, and `- **Verification Method**:`.
   - Validate field presence and permitted domain values.
   - Connect to `verify_governance()` and add `--enforce-attributes` CLI argument.
3. **Automated Verification**:
   - Add unit tests covering all valid/invalid permutations and CLI switches.

---

## 7. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Master table mathematical parity across all 9 domain shards must remain 100% green.
  2. Pure Python 3 standard library; zero external dependencies.
  3. Non-breaking CI: existing legacy requirements without attributes must not cause default verification failure until scheduled backfills occur.
