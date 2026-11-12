# aTrainingTracker: Project & Requirement Governance

This document establishes the project governance standards for requirement engineering, lifecycle management, and evidence-based status determination across **aTrainingTracker** in compliance with ASPICE 4.0 (SWE.1, SWE.6 BP3, GP 2.2, REQ-PRO-073).

---

## 1. Evidence-Based Living Requirement Status Model

Living requirements under `docs/requirements/` and test specifications under `docs/tests/` maintain an evidence-backed lifecycle. To prevent status inflation, every requirement status claim must satisfy objective criteria:

| Status | Headings Syntax | Definition & Evidence Rules |
| :--- | :--- | :--- |
| **Planned / Open** | `[Planned]` or `[Open]` | The requirement or test specification is formally articulated and scoped, but code construction or passing automated test evidence has not yet been established. |
| **Implemented** | `[Implemented]` | Production code construction exists in the repository, but automated test evidence (`TST-*`) is pending, incomplete, unintegrated, or failing. |
| **Verified** | `[Verified]` | Requires $\ge 1$ authentic test specification (`TST-*`) with passing automated unit/integration test code in `app/src/test/` or a formal, archived verification record from CI release execution. |

### 1.1 Status Transition Rules
1. **Planned $\rightarrow$ Implemented**: When production code fulfilling the requirement contract is authored and merged, but comprehensive unit test coverage or verification gating is still pending.
2. **Implemented $\rightarrow$ Verified**: When at least one authentic test specification (`TST-*`) is mapped to the requirement in living documentation, and the test specification is backed by passing test code (`app/src/test/`) or archived CI test evidence.
3. **Verified $\rightarrow$ Implemented / Planned**: If test cases are removed, fail in regression suites, or lose traceability, the requirement status must be downgraded to reflect actual evidence.

---

## 2. Master Index Table Parity & 4-Column Summary

Master index documents (`docs/requirements.md` and `docs/tests.md`) summarize the 9 domain-sharded requirement files using a standardized 4-column status table:

| Column | Description |
| :--- | :--- |
| **Total** | Total count of living requirement (`### REQ-*`) or test (`### TST-*`) sections. |
| **Verified** | Count of sections claiming `[Verified]`. |
| **Implemented** | Count of sections claiming `[Implemented]`. |
| **Planned / Open** | Count of sections claiming `[Planned]` or `[Open]`. |

Master table counts must satisfy exact mathematical parity:
$$\text{Total} = \text{Verified} + \text{Implemented} + \text{Planned / Open}$$

This parity is continuously validated by `AspiceSweAuditContractTest.kt` and `tools/verify_requirement_governance.py`. Master index tables are automatically recalculated via `python3 tools/verify_requirement_governance.py --sync-indexes`.

---

## 3. Governance Tooling & Verification Enforcement

The governance verifier (`tools/verify_requirement_governance.py`) enforces:
1. **Heading Syntax**: All living requirement and test headings must terminate with `[Verified]`, `[Implemented]`, `[Planned]`, or `[Open]`.
2. **Evidence Integrity**: A requirement claiming `[Verified]` must cite $\ge 1$ existing, valid test specification (`TST-*`) in its `Tests:` field. Citing zero tests, `None`, or dangling/non-existent test IDs is rejected.
3. **Bidirectional Link Integrity**: All cited `REQ-*` and `TST-*` identifiers must resolve to authentic headings across the 9 living documentation shards (`REQ-PRO-070`).
4. **Coverage Ratchets**: Traceability and test code coverage can be measured and ratcheted via `--coverage` (`REQ-PRO-071`).

---

## 4. System-to-Software Requirement Traceability & Tailoring Governance (SYS.2 BP5 / SWE.1 BP5 / REQ-PRO-076)

Under ASPICE SYS.2 BP5 and SWE.1 BP5, bidirectional traceability must connect the system level to the software level:

1. **System Refinement**: Software requirements governing telemetry acquisition, sensors, GNSS, power, data export, ingestion, cloud sync, and backup trace to parent system requirements (`SYSREQ-TRK-*`, `SYSREQ-DEX-*`) documented in `docs/system/sysreq_live_tracking.md`, `docs/system/sysreq_data_exchange.md`, and `docs/system/system_software_traceability.md`.
2. **Software-Derived Requirements (`[SWE-Derived]`)**: Requirements governing internal Compose UI components, design tokens, mathematical calculation algorithms, user preferences, localization strings, milestones, store assets, and process tooling (`UI`, `UNT`, `PER`, `PERF`, `SPT`, `LOC`, `MON`, `PRI`, `SET`, `REV`, `STP`, `ARC`, `BLD`, `BUG`, `INT`, `LIV`, `LOG`, `MAN`, `NFR`, `PRO`, `REG`, `SEC`, `STB`, `STR`, `TOOL`, `TST`) are classified as software-derived with documented architectural rationale.
3. **Automated Verifier Enforcement**:
   - `tools/verify_requirement_governance.py` validates that 100% of declared `SYSREQ-*` identifiers have verified coverage by at least one software requirement.
   - All cited `SYSREQ-*` identifiers must exist in `docs/system/sysreq_*.md`. Dangling citations fail verification.

