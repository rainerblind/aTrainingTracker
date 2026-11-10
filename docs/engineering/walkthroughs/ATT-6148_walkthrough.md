# Stage 5 Walkthrough: Move Release Keystore Password Out of Working Tree - ATT-6148

**Ticket**: [ATT-6148](https://rainerblind.atlassian.net/browse/ATT-6148)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Status**: Verified  
**Requirement Mapping**: `REQ-STP-008` | **Test Mapping**: `TST-STP-008`  
**Branch**: `feature/ATT-6148` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Executive Summary

Plaintext signing passwords stored in workspace `release.properties` violated ASPICE SUP.8 (Finding B-03). Under ATT-6148:
- **Dynamic Provider Chain (`REQ-STP-008.2`)**: Implemented `resolveSigningSecret` in `app/build.gradle` querying: (1) Process environment variables (`KEYSTORE_PASSWORD`, `KEY_ALIAS_PASSWORD`), (2) OS Keyring (`secret-tool lookup service atrainingtracker account ...`), and (3) External configuration `~/.config/atrainingtracker/release.properties` (`0600`).
- **Workspace Credential Elimination (`REQ-STP-008.1`)**: Prohibited reading password properties from workspace `release.properties`, keeping strictly non-secret metadata (`keyStore`, `keyAlias`).
- **Silent Migration**: Migrated existing developer passwords from host `release.properties` to `~/.config/atrainingtracker/release.properties` (`0600`) without printing any secret values.
- **CI Secret Decoupling (`REQ-STP-008.3`)**: Hardened `.github/workflows/android-release.yml` to inject signing secrets directly as environment variables into the Gradle build step without file-based materialization.
- **Contract Verification (`REQ-STP-008.4`)**: Implemented `testReleaseSigningHygiene_passwordsDecoupledFromWorkingTree` in `ReleaseCiAndLintConfigContractTest.kt` enforcing provider chain, workflow env injection, and workspace purity without reading `app/src/main/` source code.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Scope & Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-STP-008.1` | `TST-STP-008.1` | Gradle Build Signing Resolution Audit (`ReleaseCiAndLintConfigContractTest.kt`) | **PASSED** | `Verified` |
| `REQ-STP-008.2` | `TST-STP-008.2` | Multi-Tier Keyring / Config Resolution (`ReleaseCiAndLintConfigContractTest.kt`) | **PASSED** | `Verified` |
| `REQ-STP-008.3` | `TST-STP-008.3` | CI Direct Env Injection & Zero File Leaks (`ReleaseCiAndLintConfigContractTest.kt`) | **PASSED** | `Verified` |
| `REQ-STP-008.4` | `TST-STP-008.4` | Tier 1 Targeted Build Contract Suite (`build.*`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

- **Tier 1 Targeted Test Scope (Core Rule 60)**:
  - Command: `python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew testDebugUnitTest --max-workers=2 --tests "com.atrainingtracker.trainingtracker.build.*"`
  - Scope: Targeted build and governance contract suite (`ReleaseCiAndLintConfigContractTest`, `AspiceSweAuditContractTest`, `ArchitectureConformanceContractTest`, etc.).
  - Result: **BUILD SUCCESSFUL** (100% pass rate across all build contract tests).
- **Living Documentation Governance**:
  - Command: `python3 tools/verify_requirement_governance.py --sync-indexes && python3 tools/verify_requirement_governance.py`
  - Result: Master index tables synchronized with 100% parity (`docs/requirements.md`: 990 total, 984 verified, 6 planned; `docs/tests.md`: 992 total, 992 verified).

---

## 4. Hardware / Physical Device Verification

* **Device Execution**: Pure build configuration, CI security, and credential management enhancement; zero Android runtime bytecode changes in `app/src/main/`. Physical device deployment not required.
* **UI Consistency (Rule 23)**: No UI changes.
