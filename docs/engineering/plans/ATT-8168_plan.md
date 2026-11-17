# Stage 3: Implementation Plan - ATT-8168: Correct sprint metrics statistics and suspend unsupported CL3 and CL4 capability claims

**Ticket**: [ATT-8168](https://rainerblind.atlassian.net/browse/ATT-8168)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-17_03_Nacht`  
**Requirement Mapping**: `REQ-PRO-122` (`REQ-PRO-122.1`–`REQ-PRO-122.6`)  
**Test Mapping**: `TST-PRO-122` (`TST-PRO-122.1`–`TST-PRO-122.6`)  
**Branch**: `feature/ATT-8168` | **Author**: AI Agent 1 (Implementer, `flash`) | **Date**: 2026-11-17  

---

## 1. Problem Description & Background
Remediates ASPICE 4.0 Audit Finding `Observation (PA 4.x)`: `tools/sprint_metrics.py` emits `UCL > 100%` (`116.58%`/`118.01%`), mixes incompatible process eras, conflates `human_acceptance_rate_percent` with `completion_rate_percent`, fabricates `p = 0.02` escape floors (`2.37e-08`), masks Gate 1–5 plausibility warnings with Gate 6 bounces, and frames CL3–CL5 as operational achievements before CL2 attainment.

---

## 2. Traceability & Requirements Mapping
* **`REQ-PRO-122.1` $\rightarrow$ `TST-PRO-122.1`**: Binomial/p-chart `[0.0, 100.0]` bounded SPC limits & `process_version` era baseline resets.
* **`REQ-PRO-122.2` $\rightarrow$ `TST-PRO-122.2`**: Decouple terminal `human_acceptance_rate_percent` (`Erledigt`/`Done`/`Closed`) from `completion_rate_percent`.
* **`REQ-PRO-122.3` $\rightarrow$ `TST-PRO-122.3`**: Remove synthetic `p = 0.02` floor; report strictly empirical escape metrics.
* **`REQ-PRO-122.4` $\rightarrow$ `TST-PRO-122.4`**: Evaluate Gate 1–5 plausibility warning strictly on Gate 1–5 revisions (`rev6`/`bounces6` excluded).
* **`REQ-PRO-122.5` $\rightarrow$ `TST-PRO-122.5`**: Full statistical/plausibility parity in `tools/hoechstmass/tools/sprint_metrics.py`.
* **`REQ-PRO-122.6` $\rightarrow$ `TST-PRO-122.6`**: Mark CL3–CL5 `[Target / Not Yet Claimed]` while preserving `HoechstmassCoreFrameworkContractTest.kt` pins & `REQ-PRO-120` headers.

---

## 3. System Invariants & Preserved Behavior
1. **Code Lock & Scope**: Zero edits in `app/src/main/`; `ANT-Android-SDKs` untouched; `Localization Parity: N/A - no UI strings`.
2. **JSONL & Alert Compatibility**: Preserve `verify_sprint_metrics()` required keys (`sprint_id`, `sprint_name`, `throughput`, `quality_gates`, `problem_trends`) and `TST-PRO-107.3` rubber-stamping alert.
3. **Contract & Header Invariants**: Retain `"Level 1: Performed"`..`"Level 5: Innovating"` and `"PA 1.1"`..`"PA 5.2"` pins; apply `REQ-PRO-120` patch bumps (`Reviewed`, `Pending PO Sign-Off`, `Change History`).
4. **Offline Tests (`Rule 5`)**: Zero live Jira API calls in Python unit tests.

---

## 4. Proposed Architectural Changes
### Component 1: Bounded Statistical Engine (`tools/sprint_metrics.py`, `tools/jira_util.py`, `tools/hoechstmass/tools/`)
* Extract shared `_compute_percentage_spc_bounds()` helper: compute pooled binomial p-chart control limits ($\bar{p} \pm 2\sqrt{\bar{p}(1-\bar{p})/\bar{n}}$ when sample denominators `total_subtasks_evaluated`/`total_tickets` exist, else clamped $\mu \pm 2\sigma$) strictly bounded to `[0.0, 100.0]`; clamp throughput LCL to `>= 0.0`.
* Add `ACTIVE_PROCESS_VERSION` metadata (`process_version` field on emitted metrics) and filter `calculate_spc_limits()` history to the active `process_version` (resetting baseline on era transitions).
* Separate `accepted_tickets` (`erledigt`/`closed`/`done`) for `human_acceptance_rate_percent` from `completed_tickets` (review-ready + terminal) for `completion_rate_percent`.
* Remove `0.02` nominal barrier substitution in `swiss_cheese` (`empirical_cumulative_escape_rate` / non-fabricated product or empirical downstream escape ratio); evaluate Gate 1–5 plausibility check using Gate 1–5 revisions only.

### Component 2: Honest CL3–CL5 Framing (`docs/project_protocol.md`, `tools/hoechstmass/`)
* Annotate CL3, CL4, and CL5 rows/headers in `docs/project_protocol.md` (`v1.0.1` $\rightarrow$ `v1.0.2`), `tools/hoechstmass/docs/project_protocol.md`, `tools/hoechstmass/README.md`, and `sprint_metrics.py` docstrings as `[Target / Not Yet Claimed]` (pending CL2 attainment) while keeping pinned level/PA strings intact.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`REQ-PRO-122.1..122.4`)**: Update `tools/sprint_metrics.py` (shared bounded p-chart SPC helper, `process_version` baseline reset, terminal `human_acceptance_rate_percent`, remove `0.02` floor, unmask Gate 1–5 plausibility warning, honest docstring) and ensure `tools/jira_util.py` passes/displays updated fields cleanly.
2. **Step 2 (`REQ-PRO-122.5`)**: Synchronize `tools/hoechstmass/tools/sprint_metrics.py` (and `tools/hoechstmass/tools/jira_util.py` if needed) with identical statistical, `process_version`, and plausibility logic.
3. **Step 3 (`REQ-PRO-122.6`)**: Update `docs/project_protocol.md` (`Version: 1.0.2`, `Change History`), `tools/hoechstmass/docs/project_protocol.md`, and `tools/hoechstmass/README.md` to mark CL3–CL5 `[Target / Not Yet Claimed]`; add contract assertion in `HoechstmassCoreFrameworkContractTest.kt`.
4. **Step 4 (`TST-PRO-122.1..122.6`)**: Add offline unit tests in `tools/test_sprint_metrics.py` and `tools/hoechstmass/tools/test_hoechstmass_tools.py`; run `python3 -m unittest tools.test_sprint_metrics tools.test_jira_accounts tools.hoechstmass.tools.test_hoechstmass_tools`, `./gradlew :app:testDebugUnitTest --tests "*.HoechstmassCoreFrameworkContractTest"`, and `python3 tools/verify_requirement_governance.py --check-doc-headers --check-commit-hygiene --check-conflict-markers`.

---

## 6. Verification & Rollback Plan
* **Verification**: All Python unit suites (`test_sprint_metrics`, `test_jira_accounts`, `test_hoechstmass_tools`), `HoechstmassCoreFrameworkContractTest`, and `verify_requirement_governance.py` exit `0`.
* **Rollback**: `git checkout feature/ATT-8168 -- tools/ docs/ app/` reverts edits cleanly on the isolated branch.
