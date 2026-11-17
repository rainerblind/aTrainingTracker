# aTrainingTracker: Project & Requirement Governance

**Document ID**: `DOC-GOV-PROJECT`  
**Version**: 1.0.1  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

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

---

## 5. Defect Severity Classification Rubric & Spot-Check Governance (ASPICE SUP.9 / REQ-PRO-092.6)

Under ASPICE SUP.9 (Problem Resolution Management) and PA 4.1 / PA 4.2 quantitative process evaluation, all defects and bugs must be classified according to the authoritative 4-tier Bug Severity Rubric:

| Severity Level | Operational Impact & Defect Criteria | Release Gate Governance | Tooling & Labeling Standard |
| :--- | :--- | :--- | :--- |
| **Critical** | Startup crash, tracking loop termination, unrecoverable data corruption or permanent data loss, ANR blocking core user flows. | Release-blocking. Blocks release branch cut and PR gate. | `severity-critical`, `**Severity**: Critical` |
| **Major** | Crash or ANR in non-critical screens or peripheral views, primary sensor disconnection recovery failure, abnormal high battery consumption, functional defect without immediate workaround. | Sprint-blocking. Blocks sprint merge unless formal risk sign-off is logged. | `severity-major`, `**Severity**: Major` |
| **Minor** | Non-crashing functional defect with available user workaround, transient UI rendering glitch, localized date or numerical formatting flaw. | Tracked for regular sprint backlog prioritization. | `severity-minor`, `**Severity**: Minor` |
| **Cosmetic** | Purely visual styling quirk, minor margin or padding irregularity, icon tint mismatch, non-truncating text alignment anomaly. | Low priority refinement. | `severity-cosmetic`, `**Severity**: Cosmetic` |

### 5.1 Crash & ANR Severity Invariant
Bugs originating from application crashes, Application Not Responding (ANR) events, unhandled fatal exceptions, `SIGSEGV`, or `NullPointerException` traces **SHALL NOT** be downgraded to `Minor` or `Cosmetic`. `tools/jira_util.py create-issue` enforces that all crash/ANR bugs are assigned at least `Major` or `Critical` severity unless accompanied by documented rationale.

### 5.2 Quality Gate Spot-Check Audits
To prevent gate discrimination blind spots and verify that gate verdicts accurately reflect technical quality, the Coordinator and QA Auditor **SHALL** conduct periodic spot-checks during Ceremony 2 (Sprint Review & Retrospective) sampling completed gate subtasks and recording any verdict discrepancy rates in the retrospective deliverable.

### 5.3 Problem Resolution States, Fix Verification & Process-Escape Records (`SUP.9` / `REQ-PRO-121`)
* **Explicit Bug Resolution States (`jira_util.py resolve-bug`)**: Every `Bug` (`10006`) closed or triaged without code change **SHALL** record an explicit SUP.9 resolution (`Rejected`, `Duplicate` with `--duplicate-of`, `Cannot Reproduce`, or `Fixed`) via `resolution-<state>` labels, structured audit comment, and Jira's `resolution` field when configured (preserving `REQ-PRO-016` Human Decision Gate at `Final Review (Human)`).
* **Pre-Human Fix Verification**: Advancing a `Bug` to `Final Review (Human)` without a non-fix resolution requires explicit fix-verification evidence (reproduction and regression test verification in Stage 5 `[Test]` or `resolve-bug fixed`).
* **Process-Escape Problem Records & Commit/Conflict Hygiene**: Gate `REVISION` escapes (`gate-verdict REVISION --file-escape-bug`) and Tier-2 CI failures (`file-process-escape`) file deduplicated `process-escape` Bugs via Cloud v3 `paged_jql_search`. Post-cutoff commits on `develop`/`sprint/*` MUST reference `ATT-\d+` (enforced by `.githooks/commit-msg` and `verify_requirement_governance.py`) and tracked files MUST contain zero conflict markers (`.githooks/pre-commit` and CI).

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-17 | AI Agent (`ATT-8164`) | `ATT-8164` | Added standardized `DOC-GOV-PROJECT` work-product control header and Change History. |
| `1.0.1` | 2026-11-17 | AI Agent (`ATT-8165`) | `ATT-8165` | Documented SUP.9 bug resolution states, fix-verification gate, commit/conflict hygiene, and `process-escape` problem records (`REQ-PRO-121`). |


