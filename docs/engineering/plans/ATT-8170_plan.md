# Stage 3: Implementation Plan - ATT-8170: Verify tracked default secrets and tighten local credential file permissions

**Ticket**: [ATT-8170](https://rainerblind.atlassian.net/browse/ATT-8170)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-17_03_Nacht`  
**Requirement Mapping**: `REQ-PRO-124` | **Test Mapping**: `TST-PRO-124`  
**Branch**: `feature/ATT-8170` | **Author**: AI Agent 1 (Implementer, `flash`) | **Date**: 2026-11-18

---

## 1. Problem Description & Background
Implements `REQ-PRO-124` (`ATT-8170`) to enforce `0600` local credential file permissions, ignore `.jira_audit.log` in `.gitignore`, redact exposed `BuildConfig.java` credentials in `docs/engineering/walkthroughs/ATT-4004_walkthrough.md:L78-L81` with a PO key-rotation notice, and gate CI builds with an offline secret-pattern scanner.

## 2. Traceability & Requirements Mapping
| Requirement | Scope | Test Mapping | Target Verification |
| :--- | :--- | :--- | :--- |
| `REQ-PRO-124.1` | `0600` local credential permissions + `docs/ci-secrets.md` | `TST-PRO-124.1` | `test_check_secrets_hygiene.py` + live workspace check |
| `REQ-PRO-124.2` | `.gitignore` `.jira_audit.log` + `ATT-4004_walkthrough.md` redaction | `TST-PRO-124.2` | `git check-ignore` + `check_secrets_hygiene.py` + PO notice |
| `REQ-PRO-124.3` | Offline CI secret-pattern scan (`git ls-files`) + allowlists | `TST-PRO-124.3` | `test_check_secrets_hygiene.py` + live repo scan |
| `REQ-PRO-124.4` | Offline Python unit suite + CI contract & governance tests | `TST-PRO-124.4` | `ReleaseCiAndLintConfigContractTest` + governance |

## 3. System Invariants & Preserved Behavior
1. Zero `app/src/main/` modifications (`ANT-Android-SDKs` pinned at `0e8cea71`; `Localization Parity: N/A - no UI strings`).
2. `secrets.defaults.properties` (`REQ-SEC-042`) and `BuildConfigSecretsContractTest` remain intact.
3. Preserved `ReleaseCiAndLintConfigContractTest` pins (`REQ-SEC-042`, `REQ-STP-008`, `REQ-PRO-123`), `REQ-PRO-049.1` (zero `app/src/main/` reads in tests), `Rule 5` (zero live API calls), and Gate 3 Code Lock (`Rule 4.1`: local `chmod 0600` executed only in Stage 4).

## 4. Proposed Architectural Changes
* **Component 1 (`tools/check_secrets_hygiene.py`)**: Stdlib Python CLI with separated modes:
  * *Tracked Secret Scan (default CI mode)*: Scans `git ls-files` for high-confidence patterns (`AIza...`, `AKIA...`, `gh[pousr]_...`, `xox[baprs]-...`, `ATATT3x...`, `Basic <b64>`, PEM private keys, non-placeholder `STRAVA_CLIENT_SECRET`/`MAPS_API_KEY`/`DROPBOX_APP_KEY`/`JIRA_API_TOKEN`) with exact allowlist entries for `secrets.defaults.properties`, `app/build.gradle` (`AIzaSyPlaceholderMapsApiKeyForBuildTestingOnly00`), `docs/engineering/plans/ATT-4004_plan.md`, `StravaHelperTest.kt` (`client_secret=supersecret123`), and `tools/test_jira_accounts.py`.
  * *Local Permission Hygiene (`--check-local-perms`, `--fix-local-perms`, `--workspace-dir`)*: Verifies/enforces `0600` on existing `.env.claude`, `.env.gemini`, `.env.jira*`, `secrets.properties`, `release.properties` (plus `app/google-services.json` / `release.keystore` when present), decoupled from CI dummy `secrets.properties` (`0644` umask).
* **Component 2 (Repo & Documentation Hygiene)**: Add `.jira_audit.log` to `.gitignore`; redact `docs/engineering/walkthroughs/ATT-4004_walkthrough.md:L78-L81` to placeholders + log PO key-rotation notice; document `0600` hygiene & scanner CLI in `docs/ci-secrets.md` (preserving `REQ-PRO-120` header if governed).
* **Component 3 (CI & Contract Tests)**: Wire `python3 tools/check_secrets_hygiene.py` into `.github/workflows/android-branch-build.yml` and `.github/workflows/android-release.yml` prior to assembly; add `tools/test_check_secrets_hygiene.py` and `ReleaseCiAndLintConfigContractTest.kt` assertions (`// Traces to: REQ-PRO-124, TST-PRO-124`).
* **UI Consistency**: No UI changes.

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`REQ-PRO-124.2`, `TST-PRO-124.2`)**: Add `.jira_audit.log` to `.gitignore`; redact `docs/engineering/walkthroughs/ATT-4004_walkthrough.md:L78-L81` and record PO key-rotation notice.
2. **Step 2 (`REQ-PRO-124.1`, `REQ-PRO-124.3`)**: Implement `tools/check_secrets_hygiene.py` (secret scan + allowlist + `--check-local-perms` / `--fix-local-perms`) and update `docs/ci-secrets.md`.
3. **Step 3 (`REQ-PRO-124.1`)**: After Gate 3 `check-gate`, tighten main-repo workstation credential files (`/home/rainer/AndroidStudioProjects/aTrainingTracker/`: `.env.claude`, `.env.gemini`, `secrets.properties`, `release.properties`, `app/google-services.json`, `.env.jira*`) to `0600` via `--fix-local-perms` and verify `--check-local-perms` exits `0`.
4. **Step 4 (`REQ-PRO-124.3`, `REQ-PRO-124.4`)**: Wire `python3 tools/check_secrets_hygiene.py` into `.github/workflows/android-branch-build.yml` and `.github/workflows/android-release.yml` before assembly; add `tools/test_check_secrets_hygiene.py` and extend `ReleaseCiAndLintConfigContractTest.kt`.
5. **Step 5 (`TST-PRO-124.1`–`TST-PRO-124.4`)**: Promote `REQ-PRO-124` / `TST-PRO-124` to `[Implemented]` in living docs (`v1.0.11`) and execute verification commands.

## 6. Verification & Rollback Plan
* **Verification**: `python3 -m unittest tools/test_check_secrets_hygiene.py`, `python3 tools/check_secrets_hygiene.py`, `git check-ignore .jira_audit.log`, `./gradlew :app:testDebugUnitTest --tests "*ReleaseCiAndLintConfigContractTest*"`, and `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Isolated on `feature/ATT-8170` (`git checkout develop` reverts all repo changes); local `0600` modes are strictly tighter owner-only permissions.
