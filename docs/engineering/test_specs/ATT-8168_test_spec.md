# Stage 2: Requirement & Test Specification - ATT-8168: Correct sprint metrics statistics and suspend unsupported CL3 and CL4 capability claims

**Ticket**: [ATT-8168](https://rainerblind.atlassian.net/browse/ATT-8168)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-17_03_Nacht`  
**Requirement Mapping**: `REQ-PRO-122` (refining `REQ-PRO-038`, `REQ-PRO-092`)  
**Test Spec ID**: `TST-PRO-122` | **Branch**: `feature/ATT-8168`  
**Author**: AI Agent 1 (Implementer, `flash`) | **Date**: 2026-11-17

---

## 1. Requirement Specification (`REQ-PRO-122`)

### 1.1 Attributes & Rationale
- **Priority**: `High` | **Type**: `Non-Functional` | **Verification Method**: `Unit / Review`
- **Rationale**: Remediates ASPICE 4.0 Audit Finding `Observation (PA 4.x)`: unbounded `UCL > 100%` SPC bands, unpartitioned process eras, `human_acceptance_rate_percent` conflated with `completion_rate_percent`, synthetic `p = 0.02` escape leakage, Gate 6 masking of Gate 1–5 plausibility warnings, and premature CL3–CL5 operational claims prior to CL2 attainment.

### 1.2 Normative Requirements (`REQ-PRO-122.1`–`REQ-PRO-122.6`)
1. **`REQ-PRO-122.1` (Bounded SPC & `process_version` Reset)**: `calculate_spc_limits()` in `tools/sprint_metrics.py` SHALL compute binomial p-chart limits (or sample-bounded `[0.0, 100.0]` percentage limits) strictly within `[0.0, 100.0]` for `overall_ftpr` and `human_acceptance`, and MUST partition `process_metrics.jsonl` by `process_version` so incompatible eras do not pollute active baselines.
2. **`REQ-PRO-122.2` (Truthful Acceptance vs. Completion)**: `parse_sprint_gate_metrics()` SHALL decouple `human_acceptance_rate_percent` (terminal `Erledigt`/`Done`/`Closed` only; excluding `Final Review (Human)`/`Final Review (Agent)`) from `completion_rate_percent`.
3. **`REQ-PRO-122.3` (No Fabricated `p = 0.02` Floor)**: `parse_sprint_gate_metrics()` MUST NOT substitute nominal `0.02` when gate escape rate is `0`, reporting empirical escape metrics honestly.
4. **`REQ-PRO-122.4` (Unmasked Gate 1–5 Plausibility Warning)**: `total_revisions_all` for the Gate 1–5 plausibility check SHALL sum Gate 1–5 revisions only (excluding `rev6` and `bounces6`).
5. **`REQ-PRO-122.5` (Mirror Parity)**: `tools/hoechstmass/tools/sprint_metrics.py` SHALL maintain full parity with `tools/sprint_metrics.py`.
6. **`REQ-PRO-122.6` (Suspend CL3–CL5 Claims)**: `docs/project_protocol.md`, `tools/hoechstmass/docs/project_protocol.md`, `tools/hoechstmass/README.md`, and `sprint_metrics.py` docstrings SHALL mark CL3–CL5 claims as `[Target / Not Yet Claimed]` while preserving `"Level 1: Performed"`..`"Level 5: Innovating"` and `"PA 1.1"`..`"PA 5.2"` strings asserted by `HoechstmassCoreFrameworkContractTest.kt`.

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* **Criterion 1 (SPC Bounds & Reset)**: *Given* high-variance percentage history across mixed `process_version` eras, *When* `calculate_spc_limits()` runs, *Then* `0.0 <= lcl <= ucl <= 100.0` and only matching `process_version` rows form the baseline.
* **Criterion 2 (Acceptance Separation)**: *Given* tickets in `Erledigt`, `Final Review (Human)`, and `Final Review (Agent)`, *When* `parse_sprint_gate_metrics()` runs, *Then* `human_acceptance_rate_percent` reflects only terminal human-accepted tickets.
* **Criterion 3 (Empirical Escape & Plausibility)**: *Given* $\ge 20$ Gate 1–5 subtasks with `0` Gate 1–5 revisions and non-zero Gate 6 bounces, *When* `parse_sprint_gate_metrics()` runs, *Then* no `0.02` floor is applied and `plausibility_warnings` emits `PA 4.1 Plausibility Warning`.
* **Criterion 4 (Mirror & Contract Matrix)**: *Given* updated mirror and protocol docs, *When* unit tests, `HoechstmassCoreFrameworkContractTest`, and `verify_requirement_governance.py` run, *Then* CL3–CL5 entries show `[Target / Not Yet Claimed]`, all matrix assertions pass, and governance exits `0`.
* **Invariants**: `app/src/main/` untouched; `ANT-Android-SDKs` untouched; `Localization Parity: N/A - no UI strings`; backward-compatible JSONL schema; zero live Jira API calls in unit tests (`Rule 5`).

---

## 2. Test Specification (`TST-PRO-122`)

| Test Case ID | Scope & Target File | Procedure (Action) & Expected Outcome |
| :--- | :--- | :--- |
| `TST-PRO-122.1` | Unit (`tools/test_sprint_metrics.py`) | Run `calculate_spc_limits()` on high-variance & mixed `process_version` histories; assert `[0.0, 100.0]` p-chart bounds and era reset. |
| `TST-PRO-122.2` | Unit (`tools/test_sprint_metrics.py`) | Run `parse_sprint_gate_metrics()` with `Final Review (Human)`/`(Agent)` vs `Erledigt` issues; assert truthful `human_acceptance_rate_percent` != `completion_rate_percent`. |
| `TST-PRO-122.3` | Unit (`tools/test_sprint_metrics.py`) | Run `parse_sprint_gate_metrics()` with zero-revision gates; assert no `0.02` floor and empirical escape reporting. |
| `TST-PRO-122.4` | Unit (`tools/test_sprint_metrics.py`) | Run with $\ge 20$ Gate 1–5 subtasks (`0` rev1–5) + Gate 6 bounces; assert `plausibility_warnings` fires. |
| `TST-PRO-122.5` | Mirror Unit (`tools/hoechstmass/tools/test_hoechstmass_tools.py`) | Execute equivalent SPC, acceptance, zero-floor, and plausibility assertions against `tools/hoechstmass/tools/sprint_metrics.py`. |
| `TST-PRO-122.6` | Contract & Gov (`HoechstmassCoreFrameworkContractTest.kt`, `verify_requirement_governance.py`) | Assert `[Target / Not Yet Claimed]` CL3–CL5 framing, `"Level 1..5"`/`"PA 1.1..5.2"` parity, `REQ-PRO-120` version bumps, and exit `0`. |

---

## 3. Traceability Matrix

| Requirement ID | Normative Scope | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-PRO-122.1` | Binomial/clamped `[0, 100]` SPC & `process_version` resets | `TST-PRO-122.1` | Unit Test |
| `REQ-PRO-122.2` | Terminal human acceptance vs completion separation | `TST-PRO-122.2` | Unit Test |
| `REQ-PRO-122.3` | Removal of fabricated `p = 0.02` escape floor | `TST-PRO-122.3` | Unit Test |
| `REQ-PRO-122.4` | Unmasked Gate 1–5 plausibility warning | `TST-PRO-122.4` | Unit Test |
| `REQ-PRO-122.5` | `tools/hoechstmass/tools/sprint_metrics.py` mirror parity | `TST-PRO-122.5` | Unit Test |
| `REQ-PRO-122.6` | CL3–CL5 `[Target / Not Yet Claimed]` + contract parity | `TST-PRO-122.6` | Contract Test / Review |
