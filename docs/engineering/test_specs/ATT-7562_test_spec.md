# Stage 2 Test Specification: ATT-7562

**Ticket:** ATT-7562 (`[Verbesserung] Backfill missing attributes of 137 legacy requirements and make the check strict`)  
**Parent Epic:** ATT-232 (*Process*) | **Sprint:** 2026-46.6 | **Target Release:** `V5.0.0` | **Story Points:** 3 SP  
**Role:** `agent1` (Subagent Flash) / Review: `agent2` (Subagent Pro) | **Branch:** `feat/ATT-7562` | **Status:** In Progress (Stage 2)

---

## 1. Traceability Matrix

| Requirement ID | Test Specification ID | Verification Method | Target Component | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-094` | `TST-PRO-094` | Unit / Review | `tools/verify_requirement_governance.py`, `docs/requirements/` | Planned |

---

## 2. Requirement Specification (`REQ-PRO-094`)

### REQ-PRO-094: Legacy Requirement Attribute Backfill and Strict SWE.1 BP2 Enforcement [Planned]
- **Tests**: `TST-PRO-094`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `docs/requirements/req_tracking_cockpit.md`, `docs/requirements/req_maps_navigation.md`, `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`
- **Rationale**: Under ASPICE SWE.1 BP2 and BP3, all software requirements must define Priority, Type, and Verification Method attributes. The legacy tolerance in `tools/verify_requirement_governance.py` permitted 115 legacy requirements to omit these attributes with non-fatal warnings (ASPICE finding F5). Backfilling attributes across all living shards and enabling strict enforcement by default guarantees 100% attribute completeness and prevents future regressions.
- **Description**:
  1. *Legacy Attribute Backfill (`REQ-PRO-094.1`)*: All 115 legacy requirements across `docs/requirements/req_tracking_cockpit.md` and `docs/requirements/req_maps_navigation.md` SHALL define explicit, valid `Priority` (`Critical`, `High`, `Medium`, `Low`), `Type` (`Functional`, `Non-Functional`), and `Verification Method` (`Unit`, `Integration`, `On-Device Manual`, `Review`, `Formal Analysis`) attributes.
  2. *Strict Attribute Enforcement by Default (`REQ-PRO-094.2`)*: `tools/verify_requirement_governance.py` (`verify_requirement_attributes` and `verify_governance`) SHALL enforce valid Priority, Type, and Verification Method attributes by default (`enforce_attributes=True`), treating any missing or invalid attribute in any requirement shard as a fatal verification error.
  3. *Zero Warning Requirement (`REQ-PRO-094.3`)*: Executing `tools/verify_requirement_governance.py` SHALL emit zero legacy-missing-attribute warnings and confirm 100% attribute compliance.
  4. *Regression Test Parity (`REQ-PRO-094.4`)*: `tools/test_verify_requirement_governance.py` SHALL verify that any requirement lacking attributes fails verification unconditionally.

**Acceptance Criteria (Given-When-Then)**:
- *Given* all requirement shards under `docs/requirements/`,
- *When* `tools/verify_requirement_governance.py` executes,
- *Then* zero legacy-missing-attribute warnings are emitted and 100% attribute compliance is certified.
- *Given* any requirement lacking Priority, Type, or Verification Method,
- *When* the governance verifier runs,
- *Then* it fails unconditionally with exit code 1.

**Invariants**: Zero Android runtime logic modifications; master table mathematical parity preserved.

---

## 3. Test Specification (`TST-PRO-094`)

### TST-PRO-094: Legacy Requirement Attribute Backfill and Strict Verification Gate Suite [Planned]
- **Verifies**: `REQ-PRO-094`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `docs/requirements/`
- **Jira**: `ATT-7562`
- **Test Procedures**:
  1. *Missing Attribute Discovery*: Assert `find_missing_requirement_attributes()` returns 0 defective requirements.
  2. *Strict Verifier Execution*: Run `verify_requirement_attributes(enforce_attributes=True)` and assert pass with message `PASS: 100% of living requirements define valid Priority, Type, and Verification Method attributes.`
  3. *Negative Mutation Verification*: In `tools/test_verify_requirement_governance.py`, assert that any requirement without attributes fails verification.
  4. *Governance Parity Verification*: Execute `python3 tools/verify_requirement_governance.py` and confirm 100% parity with zero warnings.
