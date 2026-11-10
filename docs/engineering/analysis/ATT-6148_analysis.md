# Stage 1 Analysis: ATT-6148 - [Verbesserung] Move release keystore password out of working tree

**Ticket**: [ATT-6148](https://rainerblind.atlassian.net/browse/ATT-6148)  
**Sub-task**: [ATT-6631](https://rainerblind.atlassian.net/browse/ATT-6631) (`[Analysis]`)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6148`  
**Requirement Mapping**: `REQ-STP-008` | **Test Mapping**: `TST-STP-008`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
* **Context**: Keystore passwords (`keyStorePassword`, `keyAliasPassword`) historically resided in `release.properties` inside the project root. While gitignored, plaintext secrets in the working tree violate ASPICE SUP.8 (Finding B-03) and risk accidental disclosure or exposure to agentic tooling.
* **Goal**: Completely decouple release signing passwords from the repository workspace by sourcing them via a prioritized secure provider chain: (1) CI environment variables, (2) OS keyring (`secret-tool`), and (3) external user configuration (`~/.config/atrainingtracker/release.properties` with mode `0600`), while reading strictly non-secret keys (`keyStore`, `keyAlias`) from workspace properties.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**: `app/build.gradle` directly read `props.keyStorePassword` and `props.keyAliasPassword` from `rootProject.file("release.properties")`. In CI, `.github/workflows/android-release.yml` materialized passwords into a local `release.properties` file on disk instead of passing environment variables to the Gradle process.
* **Independent Verification**:
  * *Hypothesis Audit*: Sourcing passwords from keyring and CI environment variables preserves automated builds without workspace password exposure.
  * *Provider Chain*: In `app/build.gradle`, resolve passwords with prioritized fallback:
    1. Environment variables (`KEYSTORE_PASSWORD` / `RELEASE_KEYSTORE_PASSWORD`, `KEY_ALIAS_PASSWORD` / `RELEASE_KEY_PASSWORD`).
    2. OS Keyring: `secret-tool lookup service atrainingtracker account keyStorePassword` / `keyAliasPassword`.
    3. User config outside repo: `~/.config/atrainingtracker/release.properties` (`0600`).
  * *Workspace Guard*: Working-tree `release.properties` must NEVER be read for password properties; if present, a contract test asserts their absence.
  * *Silent Migration*: In Stage 4, existing local passwords in host `release.properties` will be silently migrated to `~/.config/atrainingtracker/release.properties` (mode `0600`) without logging values, preserving developer release builds.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Refactor `app/build.gradle` signing config to implement the multi-tier secure provider chain.
  * Update `.github/workflows/android-release.yml` to supply secrets directly via env vars.
  * Document `secret-tool` commands and CI secrets in `docs/ci-secrets.md` and `.agents/skills/release-manager/SKILL.md`.
  * Add contract test in `ReleaseCiAndLintConfigContractTest.kt` enforcing provider chain and workspace password absence.
* **Out-of-Scope Non-Goals**:
  * Zero changes to production code in `app/src/main/`.
  * No removal of non-secret release metadata (`keyStore` path, `keyAlias`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* Net-new requirement only (`REQ-STP-008`). No existing requirements modified.

---

## 5. Architectural Strategy & High-Level Solution
* **Gradle Signing Helper (`app/build.gradle`)**: Add helper method `resolveSigningSecret(envVar, altEnvVar, keyringAccount, extKey)` querying Env $\to$ `secret-tool` $\to$ `~/.config/atrainingtracker/release.properties`.
* **CI Workflow**: Pass `KEYSTORE_PASSWORD` and `KEY_ALIAS_PASSWORD` directly to `./gradlew assembleRelease bundleRelease`.
* **Credential Safety**: Strictly disallow printing, echoing, or committing plaintext credentials.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Unbroken release build capability in both CI and local developer environments.
  2. Zero plaintext passwords in repository working tree or git history.
  3. Parent ticket Human Decision Gate strictly preserved.
* **Risk Rating**: **LOW** — Robust multi-tier provider chain with backward-compatible external config fallback; zero app runtime changes.
