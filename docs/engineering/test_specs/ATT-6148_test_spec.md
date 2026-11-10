# Stage 2: Requirement & Test Specification - ATT-6148: Move release keystore password out of working tree

**Ticket**: [ATT-6148](https://rainerblind.atlassian.net/browse/ATT-6148)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6148`  
**Requirement Mapping**: `REQ-STP-008` (*Externalized Secure Keystore Password Provider Chain*)  
**Test Spec ID**: `TST-STP-008` (*Keystore Password Decoupling & Secure Provider Chain Contract Test*)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-STP-008)

### 1.1 Problem Statement & Rationale
Plaintext signing passwords stored in workspace `release.properties` violate ASPICE SUP.8 (Finding B-03) and pose credential leakage risks. Passwords must be decoupled from the working tree via a prioritized external provider chain (CI env vars $\to$ OS keyring $\to$ `~/.config/atrainingtracker/release.properties`) while keeping non-secret release metadata in workspace properties.

### 1.2 Functional & Architectural Requirements
1. **Workspace Password Elimination (`REQ-STP-008.1`)**:
   - `app/build.gradle` SHALL NOT read password properties from working-tree `release.properties`.
2. **Prioritized Provider Chain (`REQ-STP-008.2`)**:
   - Passwords SHALL resolve via: (1) Env vars (`KEYSTORE_PASSWORD`, `KEY_ALIAS_PASSWORD`), (2) OS keyring (`secret-tool`), (3) External file `~/.config/atrainingtracker/release.properties` (`0600`).
3. **CI Direct Secret Injection (`REQ-STP-008.3`)**:
   - `.github/workflows/android-release.yml` SHALL supply secrets directly as Gradle env vars without writing them to disk in the repo.
4. **Preservation of Invariants**:
   - Non-secret properties (`keyStore`, `keyAlias`) remain supported in workspace `release.properties`.
   - Zero changes to application runtime code in `app/src/main/`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Provider Chain Resolution)**:
  * *Given* `app/build.gradle`,
  * *When* resolving release signing passwords,
  * *Then* passwords resolve in priority order: env vars $\to$ `secret-tool` $\to$ `~/.config/atrainingtracker/release.properties`.
* **Criterion 2 (CI Workflow Hardening)**:
  * *Given* `.github/workflows/android-release.yml`,
  * *When* executing the release build,
  * *Then* signing secrets are supplied via env vars without creating a local `release.properties` file with passwords.
* **Criterion 3 (Workspace Credential Purity)**:
  * *Given* workspace `release.properties`,
  * *When* audited by contract tests,
  * *Then* zero occurrences of `keyStorePassword` or `keyAliasPassword` are detected.

### Requirement Archaeology & Chesterton's Fence Audit
* Net-new requirement only (`REQ-STP-008`). No existing requirements modified or relaxed.

---

## 2. Test Specification (TST-STP-008)

### Test Case 1: Gradle Signing & CI Workflow Contract Test (`TST-STP-008.1`)
* **Scope**: `ReleaseCiAndLintConfigContractTest.kt`.
* **Assertions**: Assert `app/build.gradle` queries env vars, `secret-tool`, and external config; assert `.github/workflows/android-release.yml` passes env vars directly without file-based password writes.

### Test Case 2: Workspace Secret Absence Audit (`TST-STP-008.2`)
* **Scope**: `ReleaseCiAndLintConfigContractTest.kt`.
* **Assertions**: Assert workspace `release.properties` (if present) contains zero password keys.

### Test Case 3: Tier 1 Build Contract Regression (`TST-STP-008.3`)
* **Scope**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Assertions**: 100% pass across all build and architecture contract tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-STP-008.1]` | Contract | Assert `app/build.gradle` provider chain & CI workflow env injection | `REQ-STP-008.1`, `REQ-STP-008.2`, `REQ-STP-008.3` | Specified |
| `[TST-STP-008.2]` | Contract | Assert absence of password keys in workspace `release.properties` | `REQ-STP-008.1` | Specified |
| `[TST-STP-008.3]` | Regression | Tier 1 build contract suite (`build.*`) | `REQ-PRO-001` | Specified |
