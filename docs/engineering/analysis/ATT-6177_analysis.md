# Stage 1 Analysis: ATT-6177 - Remove Stale Travis CI Configuration

**Ticket**: [ATT-6177](https://rainerblind.atlassian.net/browse/ATT-6177)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-6177`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
The root directory retains a legacy `.travis.yml` configuration (pinned to obsolete `oraclejdk8`, `ANDROID_API=29`, `build-tools-29.0.2`), and `app/build.gradle:35` contains the legacy comment `// Travis update separately`. In contrast, the project's active CI/CD pipeline is exclusively governed by GitHub Actions workflows in `.github/workflows/` (`android-branch-build.yml`, `android-release.yml`) running on Temurin JDK 21 and Android API 37. Retaining stale Travis artifacts violates ASPICE SUP.8 (Configuration Management) and creates confusion regarding the authoritative build environment.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Codebase Verification (4-Augen-Prinzip)**:
  * `.travis.yml`: Confirmed present at repo root (33 lines). Contains unmaintained JDK 8 / API 29 build instructions dating to commit `d42b8c43` (2019-10-26). No active Travis CI service is linked to this repository.
  * `app/build.gradle:35`: Confirmed comment `// Travis update separately` immediately preceding `compileSdkVersion = 37`.
  * `README.md` & `docs/`: Audited via `git grep -i "travis"`. No references or badges exist in `README.md` or living documentation.
  * `.github/workflows/`: Audited `android-branch-build.yml` and `android-release.yml`. Both define the active, modern build/test matrix (JDK 21, API 37, Gradle 8.x).
  * Regression Prevention: `ReleaseCiAndLintConfigContractTest.kt` validates CI workflow and build configuration contracts, making it the ideal host to enforce absence of Travis configuration.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Delete `.travis.yml` from repository root.
  * Remove stale comment `// Travis update separately` from `app/build.gradle`.
  * Define `REQ-PRO-042` in `docs/requirements/req_process_stability.md` establishing GitHub Actions exclusivity and prohibiting legacy CI config.
  * Define `TST-PRO-042` in `docs/tests/tst_process_stability.md`.
  * Extend `ReleaseCiAndLintConfigContractTest.kt` to assert that `.travis.yml` does not exist and `app/build.gradle` contains no references to Travis.
* **Out-of-Scope Non-Goals**:
  * No modifications to `.github/workflows/` workflow steps or job structures.
  * No alterations to Android runtime code in `app/src/main/` or unrelated living doc shards.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-042` (*CI Configuration Integrity & Legacy Travis CI Deprecation*), refining ASPICE SUP.8 under Epic `ATT-6142`.
* **Historical Origin & Commit Trace**: Commit `d42b8c438b1e22d139183171992527fae9e15c38` ("Travis build", 2019-10-26) introduced `.travis.yml` and the `app/build.gradle` comment. GitHub Actions was subsequently adopted as the sole CI platform.
* **Root Reason for Existing Formulation**: Travis CI was the initial open-source CI provider used during early development. Upon transitioning to GitHub Actions, `.travis.yml` was left dormant.
* **Preservation of Core Invariants**: The active build, test, and release verification pipeline in GitHub Actions remains 100% authoritative and unchanged.

---

## 5. Architectural Strategy & High-Level Solution
* **File Removal**: Delete `.travis.yml`.
* **Gradle Cleanup**: Remove `// Travis update separately` in `app/build.gradle`.
* **Contract Test**: Add `@Test fun testCiConfiguration_noStaleTravisArtifacts()` to `ReleaseCiAndLintConfigContractTest.kt` asserting `File(".travis.yml").exists()` is false and `build.gradle` contains no case-insensitive "travis" string.
* **Living Documentation**: Append `REQ-PRO-042` and `TST-PRO-042` to process stability shards.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero impact on build compilation, unit test execution, or GitHub Actions CI workflows. Zero runtime Android code impact.
* **Risk Rating**: **LOW**. Deletion of orphaned configuration file and comment, accompanied by regression-locking contract test.
