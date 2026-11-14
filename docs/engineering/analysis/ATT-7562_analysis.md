# Stage 1 Analysis: ATT-7562

**Ticket:** ATT-7562 (`[Verbesserung] Backfill missing attributes of 137 legacy requirements and make the check strict`)  
**Parent Epic:** ATT-232 (*Process*) | **Sprint:** 2026-46.6 | **Target Release:** `V5.0.0` | **Story Points:** 3 SP  
**Role:** `agent1` (Subagent Pro) | **Branch:** `feat/ATT-7562` | **Status:** In Progress (Stage 1)

---

## 1. Problem Statement & Motivation
- **ASPICE F5 Finding**: Legacy requirements authored prior to Sprint 2026-46 lack explicit `Priority`, `Type`, and `Verification Method` attributes.
- **Indefinite Tolerance Gap**: `tools/verify_requirement_governance.py` tolerates missing attributes as non-fatal warnings, violating ASPICE SWE.1 BP2 / BP3 (Attribute Completeness and Traceability).

## 2. Root Cause & Independent Hypothesis Verification
- **Codebase Audit**: Running `find_missing_requirement_attributes()` detects exactly 115 remaining legacy requirements lacking attributes:
  - 110 in `docs/requirements/req_tracking_cockpit.md` (e.g. `REQ-CON-001`..`009`, `REQ-FIL-001`..`006`, `REQ-TRK-001`..`003`, `REQ-SEN-027`, `REQ-TRK-053`..`058`, etc.)
  - 5 in `docs/requirements/req_maps_navigation.md` (`REQ-MAP-065`..`067`, `REQ-CLM-008`..`009`)
- **Verifier Gap**: In `tools/verify_requirement_governance.py`, `verify_requirement_attributes()` defaults to `enforce_attributes=False`, printing warnings instead of enforcing compliance.
- **Hypothesis Verification**: Confirmed. Backfilling the 115 requirements and enabling strict enforcement eliminates all warnings, closes finding F5, and prevents future regression.

## 3. User Scope Grounding (ATT-1250)
- **In-Scope**:
  1. Backfill valid `Priority`, `Type`, and `Verification Method` attributes for all 115 remaining legacy requirements in `req_tracking_cockpit.md` and `req_maps_navigation.md`.
  2. Transition `tools/verify_requirement_governance.py` to strict attribute enforcement by default (`enforce_attributes=True`), failing on any missing attribute without tolerance.
  3. Update automated regression tests in `tools/test_verify_requirement_governance.py`.
- **Out-of-Scope**: Altering Android runtime logic in `app/src/main/`.

## 4. Requirement Archaeology & Chesterton's Fence Audit
- **Target**: Net-new requirement `REQ-PRO-094` refining `REQ-PRO-077` (*Requirement Attributes Specification & SWE.1 BP2 Governance*).
- **Historical Origin**: `ATT-6147` / `ATT-6151` introduced attribute checking with a temporary legacy tolerance mechanism.
- **Root Reason for Existing Formulation**: Legacy tolerance served as an intermediate migration bridge during early ASPICE CL2 remediation.
- **Preservation of Core Invariants**: 100% ID uniqueness, master table parity, and clean test pass rate strictly preserved.

## 5. Change-Request Impact Analysis (SUP.10)
- **Affected Requirements**: Net-new requirement `REQ-PRO-094` (`docs/requirements/req_process_stability.md`).
- **Affected Architecture Elements**: `docs/requirements/req_tracking_cockpit.md`, `docs/requirements/req_maps_navigation.md`, `tools/verify_requirement_governance.py`, and `tools/test_verify_requirement_governance.py`.
- **Verification Impact & Test Surface**: `TST-PRO-094`, `tools/test_verify_requirement_governance.py`, and `tools/verify_requirement_governance.py`.
- **Risk Assessment & Invariants**: Low risk. Living specifications and verification tooling only; zero APK runtime impact.

## 6. Architectural Strategy
- Add standard multi-line attribute bullets (`Priority: ...`, `Type: ...`, `Verification Method: ...`) to each of the 115 legacy requirements according to their domain role.
- Configure `verify_requirement_attributes(..., enforce_attributes=True)` as the default mode in `verify_requirement_governance.py`.
- Update `test_verify_requirement_governance.py` to assert that missing attributes fail unconditionally.

## 7. System Invariants & Risk Assessment
- **Core Invariants**: 100% test suite pass rate; master table parity intact; Human Decision Gate inviolable.
- **Risk Rating**: **LOW** (process governance and documentation backfill).
