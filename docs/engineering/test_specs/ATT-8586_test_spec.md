# Stage 2: Requirement & Test Specification - ATT-8586: RISK-003 Mitigation Citation Integrity & Contract Guard

**Ticket**: [ATT-8586](https://rainerblind.atlassian.net/browse/ATT-8586) | **Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-17_02_Spät`
**Requirement Mapping**: `REQ-STP-011` (refining `REQ-STP-010`) | **Test Spec ID**: `TST-STP-011`
**Branch**: `feature/ATT-8586` | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-17

---

## 1. Requirement Specification (REQ-STP-011)
* **Attributes**: Priority: `Medium` | Type: `Non-Functional` | Verification Method: `Unit / Review` | Status: `[Planned]`
* **REQ-STP-011.1 (`RISK-003` Citation Correction)**: In `docs/management/risk_register.md`, `RISK-003` (*Google Play Policy & Foreground Service Restrictions*, Medium exposure $E=12$) SHALL replace `Continuous Target SDK Maintenance (ATT-8084)` with `Continuous Target SDK Maintenance (Rule 29 & ManifestSecurityContractTest)`, containing zero misleading Jira keys.
* **REQ-STP-011.2 (`RISK-001`..`RISK-011` Citation Integrity)**: All `Mitigation Ticket` references across `RISK-001`..`RISK-011` SHALL match authentic Jira issues, with High-exposure risks ($E \ge 15$: `RISK-001`, `RISK-002`) linked to `ATT-6148`/`ATT-6152` and `ATT-6150`/`ATT-6141`.
* **REQ-STP-011.3 (Review Log & Date Currency)**: `docs/management/risk_register.md` SHALL record the `Last Review Date` and `Sprint Review Log` update accurately.
* **REQ-STP-011.4 (Anti-Regression Contract Guard)**: `ReleaseCiAndLintConfigContractTest.testProjectRiskRegister_existsAndHighExposureMitigated` MUST retain all existing `REQ-STP-010` assertions and SHALL assert `!riskContent.contains("ATT-8084")`.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines `[Verified]` `REQ-STP-010` (`docs/requirements/req_store_presence.md` L181–198) without modifying `REQ-STP-010` or `TST-STP-010`.
* **Historical Origin & Commit Trace**: `REQ-STP-010` was created in `ATT-6151` and extended to `MAN.5` in `ec57013f` (`ATT-8167`), which introduced the wrong `ATT-8084` citation into `RISK-003`.
* **Root Reason for Existing Formulation**: `REQ-STP-010.1` requires linked Jira tickets strictly for High-exposure risks ($E \ge 15$) and non-placeholder maintenance scope for Medium/Low risks (`RISK-003`, $E=12$).
* **Preservation of Core Invariants**: `[Verified]` `REQ-STP-010`/`TST-STP-010`, `ATT-6148`/`ATT-6150` High-exposure links, `MAN.5`/`MAN.3.BP5` headers, historical `2026-46.1`/`2026-46.6` log rows, and `docs/management/` placement remain untouched.

---

## 3. Acceptance Criteria (Given-When-Then) & Localization Parity
* **Criterion 1 (`RISK-003` Clean Citation)**: *Given* `docs/management/risk_register.md`, *When* `RISK-003` is inspected, *Then* `ATT-8084` is absent and `RISK-003` displays `Continuous Target SDK Maintenance (Rule 29 & ManifestSecurityContractTest)`.
* **Criterion 2 (Register Ticket Parity)**: *Given* `RISK-001`..`RISK-011`, *When* audited against Jira, *Then* all cited Jira keys resolve to matching issues and High-exposure risks ($E \ge 15$) remain linked.
* **Criterion 3 (Contract Test Guard)**: *Given* `ReleaseCiAndLintConfigContractTest.testProjectRiskRegister_existsAndHighExposureMitigated`, *When* executed via Gradle, *Then* `!riskContent.contains("ATT-8084")` and all `REQ-STP-010` assertions pass.
* **Localization Parity**: `N/A - no UI strings`.

---

## 4. Test Specification (TST-STP-011)
* **TST-STP-011.1 (`RISK-003` Inspection)**: Verify `docs/management/risk_register.md` removes `ATT-8084` from `RISK-003` and preserves `Last Review Date` / `Sprint Review Log` integrity.
* **TST-STP-011.2 (`RISK-001`..`011` Jira Audit)**: Confirm all `Mitigation Ticket` keys across `RISK-001`..`RISK-011` resolve cleanly in Jira (`ATT-6148`/`ATT-6152`, `ATT-6150`/`ATT-6141`, `ATT-8151`, `ATT-8162`, `ATT-8150`/`ATT-8153`, `ATT-8166`, `ATT-8213`).
* **TST-STP-011.3 (Contract Unit Test)**: Execute `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest.testProjectRiskRegister_existsAndHighExposureMitigated"` asserting 100% pass including `!riskContent.contains("ATT-8084")`.
* **TST-STP-011.4 (Governance Check)**: Execute `python3 tools/verify_requirement_governance.py` and confirm exit code `0`.

---

## 5. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-STP-011.1` | Review | `docs/management/risk_register.md` | `REQ-STP-011.1`, `REQ-STP-011.3` | Planned |
| `TST-STP-011.2` | Review | `docs/management/risk_register.md` | `REQ-STP-011.2` | Planned |
| `TST-STP-011.3` | Unit | `ReleaseCiAndLintConfigContractTest.kt` | `REQ-STP-011.4` | Planned |
| `TST-STP-011.4` | Governance | `tools/verify_requirement_governance.py` | `REQ-STP-011` | Planned |
