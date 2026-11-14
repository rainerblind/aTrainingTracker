# Stage 5 Walkthrough: ATT-7565

**Ticket:** ATT-7565 (`[Verbesserung] Slim governance rules into normative rules and history archive, cap shard size`)  
**Parent Epic:** ATT-232 (*Process*) | **Sprint:** 2026-46.6 | **Target Release:** `V5.0.0`  
**Requirement Mapping:** `REQ-PRO-093` (`REQ-PRO-093.1`–`REQ-PRO-093.5`) | **Test Mapping:** `TST-PRO-093`  
**Role:** `agent1` (Subagent Flash) | **Branch:** `feat/ATT-7565`  

---

## 1. Executive Summary & Verification Overview

ATT-7565 addresses ASPICE evaluation finding F8 (process drift and rule bloat under PA 3.1) and enforces living documentation shard size governance:
1. **Normative Governance Restructuring (`REQ-PRO-093.1`)**: Streamlined `.agents/rules/aspice_governance.md` and `tools/hoechstmass/.agents/rules/aspice_governance.md` into 9 concise Core Rules with stable normative sub-rule IDs (`Rule 1.1` through `Rule 9.5`), eliminating embedded ticket quotes, retrospective anecdotes, and superseded parentheticals.
2. **Rule History Archive Synchronization (`REQ-PRO-093.2`)**: Updated `docs/engineering/rule_history_archive.md` to host the complete historical mapping of Rules 1–84+, preserving retrospective origins, exact quotes, and cross-references to active normative IDs.
3. **Shard Size Capping & CLI (`REQ-PRO-093.3`)**: Added `DEFAULT_MAX_SHARD_LINES = 4000`, `check_shard_sizes()`, and `--check-size` inspection CLI to `tools/shard_living_docs.py`, flagging any oversized shard with line counts.
4. **Oversized Shard Partitioning Facility (`REQ-PRO-093.4`)**: Implemented `split_oversized_shard()` in `tools/shard_living_docs.py` to programmatically partition oversized requirement or test shards into compliant sub-shards while strictly preserving 100% ID uniqueness, table parity, and bidirectional traceability.
5. **Contract & Governance Invariance (`REQ-PRO-093.5`)**: All governance contract assertions (`AspiceSweAuditContractTest`, `HoechstmassCoreFrameworkContractTest`, `test_agent_emulator.py`, `verify_requirement_governance.py`) pass with 100% success.

---

## 2. Requirement & Test Verification Matrix

| Requirement ID | Test Spec ID | Verification Method | Result | Living Doc Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-093.1` | `TST-PRO-093.1` | Contract Test & Review (`test_agent_emulator.py`, `AspiceSweAuditContractTest`) | **PASSED** | `Verified` |
| `REQ-PRO-093.2` | `TST-PRO-093.2` | Historical Archive Review (`rule_history_archive.md`) | **PASSED** | `Verified` |
| `REQ-PRO-093.3` | `TST-PRO-093.3` | Unit & CLI Test (`tools/test_shard_living_docs.py`) | **PASSED** | `Verified` |
| `REQ-PRO-093.4` | `TST-PRO-093.4` | Automated Partitioning Test (`tools/test_shard_living_docs.py`) | **PASSED** | `Verified` |
| `REQ-PRO-093.5` | `TST-PRO-093.5` | Governance Contract Suite (`verify_requirement_governance.py`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Unit & Contract Test Scope
- **Shard Tooling Unit Tests**:
  - `python3 tools/test_shard_living_docs.py`: **12/12 passed** in 0.65s (0 failures, 0 errors).
- **Process Governance & Device Policy Tests**:
  - `python3 -m unittest tools/test_agent_emulator.py`: **20/20 passed** in 0.03s.
- **Requirement Governance & Master Table Parity**:
  - `python3 tools/verify_requirement_governance.py`: **100% parity** (0 errors).
  - `python3 -m unittest tools/test_verify_requirement_governance.py`: **57/57 passed** in 12.8s.
- **JVM Build Contract Tests**:
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"`: **BUILD SUCCESSFUL** (33/33 tasks up-to-date, contract tests passed).

---

## 4. Hardware & Device Verification Scope
- Non-UI process tooling and engineering governance ticket.
- Zero modifications to Android UI composables or runtime application code in `app/src/main/`. On-device deployment skipped per Rule 7.6.

---

## 5. Invariant & Governance Verification
1. **Zero Runtime Regressions**: 100% unit and contract test success across tooling and build test suites.
2. **Document Triad Parity**: Living documentation synchronized (`REQ-PRO-093` and `TST-PRO-093` to `Verified`).
3. **No Stage 4 Markdown Artifacts**: Stage 4 submitted directly via Jira CLI.
4. **Target Release Alignment**: Verified against active release `V5.0.0` from `tools/jira_util.py versions`.
