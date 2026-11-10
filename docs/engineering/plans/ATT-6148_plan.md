# Stage 3: Implementation Plan - ATT-6148: Move release keystore password out of working tree

**Ticket**: [ATT-6148](https://rainerblind.atlassian.net/browse/ATT-6148)  
**Sub-task**: [ATT-6636](https://rainerblind.atlassian.net/browse/ATT-6636) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1`  
**Requirement Mapping**: `REQ-STP-008` (*Externalized Secure Keystore Password Provider Chain*)  
**Test Mapping**: `TST-STP-008` (*Keystore Password Decoupling & Secure Provider Chain Contract Test*)  
**Branch**: `feature/ATT-6148`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
Plaintext release signing credentials stored in workspace `release.properties` violate ASPICE SUP.8 (Finding B-03). This plan specifies decoupling keystore and alias passwords from the working tree via a prioritized provider chain (CI env vars $\to$ OS keyring $\to$ `~/.config/atrainingtracker/release.properties`), updates CI workflows and documentation, and verifies workspace credential hygiene via automated contract tests.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-STP-008` (`REQ-STP-008.1` workspace password prohibition, `REQ-STP-008.2` provider chain resolution, `REQ-STP-008.3` CI direct env injection, `REQ-STP-008.4` contract enforcement).
* **Test Mapping**: `TST-STP-008` (`TST-STP-008.1` Gradle & CI signing audit, `TST-STP-008.2` workspace property absence, `TST-STP-008.3` contract regression suite).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Credential Loss**: Existing host passwords silently migrated to `~/.config/atrainingtracker/release.properties` (`0600`) without printing or leaking values.
2. **Release Build Continuity**: Signed release artifacts (`bundleRelease`, `assembleRelease`) remain functional in both CI and local developer environments.
3. **Purity of Working Tree**: Workspace `release.properties` contains strictly non-secret keys (`keyStore`, `keyAlias`).
4. **Parent Human Gate Invariance**: Advancing parent ticket to `Erledigt` remains reserved exclusively for the human user.

---

## 4. Proposed Architectural Changes
* **Gradle Signing Resolution (`app/build.gradle`)**: Introduce `resolveSigningSecret` helper querying process environment variables, `secret-tool lookup`, and external config `~/.config/atrainingtracker/release.properties`. Disallow reading password properties from workspace `release.properties`.
* **CI Workflow Hardening (`.github/workflows/android-release.yml`)**: Inject `KEYSTORE_PASSWORD` and `KEY_ALIAS_PASSWORD` directly into Gradle build steps via env blocks; eliminate password materialization into workspace files.
* **Documentation & Skills (`docs/ci-secrets.md`, `.agents/skills/release-manager/SKILL.md`)**: Document `secret-tool` registration commands and external config format.
* **Contract Testing (`ReleaseCiAndLintConfigContractTest.kt`)**: Assert provider chain implementation, workflow env injection, and absence of passwords in workspace `release.properties`.
* **UI Consistency (Rule 23)**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement Dynamic Signing Provider Chain in `app/build.gradle`
* Files: `app/build.gradle`
* Action: Add `resolveSigningSecret` closure; configure `signingConfigs.release` to query provider chain for passwords and workspace properties only for non-secret `keyStore` / `keyAlias`.

### Step 2: Hardening CI Release Workflow
* Files: `.github/workflows/android-release.yml`
* Action: Remove password lines from workspace `release.properties` generator; inject secrets as env vars in release assembly step.

### Step 3: Silent Local Credential Migration
* Files: `/home/rainer/AndroidStudioProjects/aTrainingTracker/release.properties` (host file)
* Action: Execute silent Python migration moving password keys to `~/.config/atrainingtracker/release.properties` (`chmod 600`) and stripping them from workspace file without echoing values.

### Step 4: Documentation & Release Skill Update
* Files: `docs/ci-secrets.md`, `.agents/skills/release-manager/SKILL.md`
* Action: Update documentation for `secret-tool` keyring usage and external configuration.

### Step 5: Implement Automated Contract Test & Verification
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`
* Action: Add `testReleaseSigningHygiene_passwordsDecoupledFromWorkingTree()`.
* Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest" && python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted build contract suite and `verify_requirement_governance.py`.
* **Rollback**: `git reset --hard HEAD` on `feature/ATT-6148` cleanly reverts working tree modifications.
