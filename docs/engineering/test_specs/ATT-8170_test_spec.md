# Stage 2: Requirement & Test Specification - ATT-8170: Verify tracked default secrets and tighten local credential file permissions

**Ticket**: [ATT-8170](https://rainerblind.atlassian.net/browse/ATT-8170)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-17_03_Nacht`  
**Requirement Mapping**: `REQ-PRO-124` | **Test Spec ID**: `TST-PRO-124`  
**Branch**: `feature/ATT-8170` | **Author**: AI Agent 1 (Implementer, `flash`) | **Date**: 2026-11-18

---

## 1. Requirement Specification (`REQ-PRO-124`)
- **Priority**: Medium | **Type**: Non-Functional | **Verification Method**: Unit / Review

### 1.1 Functional & Architectural Requirements
1. **Local `0600` Credential Permissions (`REQ-PRO-124.1`)**: Local untracked credential/signing files (`.env.claude`, `.env.gemini`, `secrets.properties`, `release.properties`, `.env.jira*`) MUST have `0600` mode, verified via `tools/check_secrets_hygiene.py --check-local-perms` (with `--fix-local-perms` remediation) and documented in `docs/ci-secrets.md` (preserving `REQ-PRO-120` header if governed).
2. **`.gitignore` & Walkthrough Leak Redaction (`REQ-PRO-124.2`)**: `.gitignore` SHALL exclude `.jira_audit.log`. Exposed `BuildConfig.java` keys in `docs/engineering/walkthroughs/ATT-4004_walkthrough.md:L78-L81` SHALL be redacted to safe placeholders, with an explicit PO key-rotation notice logged for the exposed Google Maps, Strava, and Dropbox keys.
3. **Offline CI Secret-Pattern Scan (`REQ-PRO-124.3`)**: `tools/check_secrets_hygiene.py` SHALL scan tracked `git ls-files` in `.github/workflows/android-branch-build.yml` and `.github/workflows/android-release.yml` prior to assembly, failing non-zero on real API keys/tokens/private keys while allowlisting `secrets.defaults.properties` placeholders (`REQ-SEC-042`), `app/build.gradle:L53`, `ATT-4004_plan.md:L104`, `StravaHelperTest.kt:L373`, and `tools/test_jira_accounts.py:L350-L351`.
4. **Offline & Contract Verification (`REQ-PRO-124.4`)**: `tools/test_check_secrets_hygiene.py` (`Rule 5`) and `ReleaseCiAndLintConfigContractTest` (`REQ-PRO-049.1`: zero `app/src/main/` reads) SHALL verify detection, allowlisting, `0600` checks/fixes, and CI wiring while preserving `REQ-SEC-042`, `REQ-STP-008`, and `REQ-PRO-123` pins.

### 1.2 Acceptance Criteria (Given-When-Then)
* **Criterion 1**: *Given* local credential files with `0644`/`0664`, *When* `--check-local-perms` runs, *Then* it exits non-zero; after `--fix-local-perms`, all files are `0600` and `--check-local-perms` exits `0`.
* **Criterion 2**: *Given* tracked files and redacted `ATT-4004_walkthrough.md:L78-L81`, *When* `check_secrets_hygiene.py` runs in CI, *Then* clean tracked files and allowlisted fixtures exit `0` while any real secret exits `1`.
* **Criterion 3**: *Given* `.gitignore` and both CI workflows, *When* `git check-ignore .jira_audit.log` and `ReleaseCiAndLintConfigContractTest` run, *Then* `.jira_audit.log` is ignored and CI gates precede assembly.

### 1.3 System Invariants
* Zero `app/src/main/` edits; `ANT-Android-SDKs` untouched; `secrets.defaults.properties` untouched (`REQ-SEC-042`); `Localization Parity: N/A - no UI strings`; `Rule 5` zero live API calls in tests; `REQ-PRO-049.1` zero `app/src/main/` reads in tests.

---

## 2. Test Specification (`TST-PRO-124`)
* **`TST-PRO-124.1` (Local `0600` Permissions)**: `tools/test_check_secrets_hygiene.py` tests `0644`/`0664` failure and `--fix-local-perms` -> `0600` pass on isolated `tempfile` fixtures + live local workspace check; `docs/ci-secrets.md` documents `0600`.
* **`TST-PRO-124.2` (`.gitignore` & Leak Redaction)**: `git check-ignore .jira_audit.log` exits `0`; `ATT-4004_walkthrough.md:L78-L81` verified redacted with PO key-rotation notice logged.
* **`TST-PRO-124.3` (Offline Secret-Pattern Scan & Allowlist)**: `tools/test_check_secrets_hygiene.py` verifies synthetic leaks (`AIza...`, `AKIA...`, `ghp_...`, `ATATT3x...`, PEM keys, non-placeholder `STRAVA_CLIENT_SECRET`) exit `1` while allowlisted placeholders/test fixtures and `git ls-files` exit `0`.
* **`TST-PRO-124.4` (CI Contract & Governance)**: `./gradlew testDebugUnitTest --tests "*ReleaseCiAndLintConfigContractTest*"` (`REQ-PRO-049.1`) and `python3 tools/verify_requirement_governance.py` exit `0`.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Command | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-124.1` | Unit / Local | `test_check_secrets_hygiene.py` (`--check-local-perms` / `--fix-local-perms`) | `REQ-PRO-124.1` | Specified |
| `TST-PRO-124.2` | Hygiene / Review | `.gitignore` + `ATT-4004_walkthrough.md:L78-L81` + PO notice | `REQ-PRO-124.2` | Specified |
| `TST-PRO-124.3` | Unit / CLI | `test_check_secrets_hygiene.py` + `check_secrets_hygiene.py` | `REQ-PRO-124.3` | Specified |
| `TST-PRO-124.4` | Contract / Gov | `ReleaseCiAndLintConfigContractTest` + `verify_requirement_governance.py` | `REQ-PRO-124.4` | Specified |
