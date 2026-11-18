# Stage 1 Analysis: ATT-8170 - Verify tracked default secrets and tighten local credential file permissions

**Ticket**: [ATT-8170](https://rainerblind.atlassian.net/browse/ATT-8170)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-17_03_Nacht`  
**Branch**: `feature/ATT-8170` | **Author**: AI Agent 1 (Implementer, `flash`) | **Date**: 2026-11-18

---

## 1. Problem Statement & Motivation
ASPICE 4.0 Audit 2026-11-15 (`Minor (SUP.8)`, `SUP.8 BP2/BP3`) flagged credential and configuration hygiene gaps:
1. Local untracked credential/signing files have group/world-readable modes (`0644`/`0664`) instead of `0600`.
2. `.jira_audit.log` is cited as missing from `.gitignore`.
3. CI lacks an automated secret-pattern scan guarding tracked files against accidental real credential commits.

## 2. Root Cause Analysis & Independent Peer Review
* **RCA 1 (Workstation Modes)**: Local files in `/home/rainer/AndroidStudioProjects/aTrainingTracker/` were created under default `022`/`002` umask without `chmod 0600` automation (`docs/ci-secrets.md:L73` only mentions `chmod 600` for `~/.config/atrainingtracker/release.properties`).
* **RCA 2 (Missing CI Secret Guard)**: Workflows (`.github/workflows/android-branch-build.yml:L144-L157`, `android-release.yml:L155-L170`) run governance and `lint_*.py` safety guards (`ATT-8169`) but perform zero credential-pattern scanning on tracked files.
* **Independent Peer Review & Verification (ATT-3534)**:
  * **`secrets.defaults.properties` (`L5-L8`)**: Confirmed all 4 entries are explicit build placeholders (`AIzaSyPlaceholderMapsApiKeyForBuildTestingOnly00`, `00000`, `placeholder_strava_client_secret_for_build_testing`, `placeholder_dropbox_app_key`).
  * **Blind Spot — Tracked Walkthrough Leak (`docs/engineering/walkthroughs/ATT-4004_walkthrough.md:L78-L81`)**: Full `git ls-files` scan (`8,474` files) disproved the ticket assumption that zero real-looking credentials exist in tracked files: `ATT-4004_walkthrough.md:L78-L81` records concrete `BuildConfig.java` literals (`AIzaSyBVPL...`, Strava ID/secret `344` / `272b5ca4...`, Dropbox `iknmdmr31sf64r0`). Must be redacted in Stage 4 and flagged to PO for key-rotation check.
  * **Benign Matches to Allowlist**: `app/build.gradle:L53` and `docs/engineering/plans/ATT-4004_plan.md:L104` (`AIzaSyPlaceholderMapsApiKeyForBuildTestingOnly00`); `StravaHelperTest.kt:L373` (`client_secret=supersecret123`); `tools/test_jira_accounts.py:L350-L351` (`ATATT3x...`, `Basic dGVzdF91c2VyOnNlY3JldF90b2tlbg==`).
  * **`.jira_audit.log` Check**: `tools/jira_util.py:L42` is `except ImportError:` (`git grep jira_audit` and `git log -S jira_audit -- tools/jira_util.py .gitignore` return 0 matches). Adding `.jira_audit.log` to `.gitignore` (`L1-L16`) remains a safe defensive guard.
  * **Local File Modes (`ls -la`, unchanged in Stage 1)**: `.env.claude` (`0644`), `.env.gemini` (`0644`), `secrets.properties` (`0664`), `release.properties` (`0664`), `app/google-services.json` (`0664`), `local.properties` (`0664`); `.env.jira*` already `0600`. Note CI writes dummy `secrets.properties` (`android-branch-build.yml:L39-L45`, `android-release.yml:L41-L47`) with default umask (`0644`).
  * **CI Contract Pins**: `ReleaseCiAndLintConfigContractTest.kt:L1149-L1179` (`ATT-8169`) and `L613-L677` (`REQ-STP-008`) remain intact.

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Tighten local workstation modes to `0600` on `.env.claude`, `.env.gemini`, `secrets.properties`, `release.properties` (plus `app/google-services.json` check) **only in Stage 4** after Gate 3 `check-gate`.
  2. Add `.jira_audit.log` to `.gitignore` and update `docs/ci-secrets.md` with `0600` permission & secret-scan guidance.
  3. Add an offline Python secret-pattern & permission-hygiene script in `tools/`, unit tests, and CI workflow gating (`android-branch-build.yml` + `android-release.yml`), redacting `ATT-4004_walkthrough.md:L78-L81`.
* **Out-of-Scope Non-Goals**: Rewriting git history (`filter-repo`), modifying `app/src/main/`, or changing `secrets.defaults.properties` keys (`REQ-SEC-042`).

## 4. Requirement Archaeology & Chesterton's Fence Audit
* Net-new requirement `REQ-PRO-124` (`docs/requirements/req_process_stability.md`) extending `REQ-SEC-042` (`ATT-4004`, `secrets.defaults.properties`), `REQ-STP-008` (`ATT-6148`, `release.properties`), and `REQ-PRO-123` (`ATT-8169`, CI custom Python guards). Preserves `BuildConfigSecretsContractTest` (`ALLOWED_BUILD_CONFIG_FIELDS`) and `ReleaseCiAndLintConfigContractTest` pins.

## Change Impact Analysis
* **Affected Requirements**: Net-new `REQ-PRO-124` / `TST-PRO-124` (`req_process_stability.md`, `tst_process_stability.md`); cross-linked to `REQ-SEC-042`, `REQ-STP-008`, `REQ-PRO-123`.
* **Affected Architecture Elements**: `.gitignore`, `docs/ci-secrets.md`, `docs/engineering/walkthroughs/ATT-4004_walkthrough.md`, new `tools/` secret & permission guard + tests, `.github/workflows/android-branch-build.yml`, `.github/workflows/android-release.yml`, `ReleaseCiAndLintConfigContractTest.kt`.
* **Verification Impact & Test Surface**: Python `unittest` for scanner allowlists, secret detection, and `0600` permission checks; `ReleaseCiAndLintConfigContractTest`; `verify_requirement_governance.py`; live scan on all tracked files + local permission verification in Stage 4/5.
* **Risk Assessment & Invariants**: **LOW** (tooling/CI/docs only; zero `app/src/main/` edits; `secrets.defaults.properties` untouched; `ANT-Android-SDKs` untouched).

## 6. Architectural Strategy & High-Level Solution
1. **Deterministic Scanner (`tools/check_secrets_hygiene.py`)**: Pure stdlib Python scanning `git ls-files` for high-confidence credential patterns (`AIza...`, `AKIA...`, `gh[pousr]_...`, `ATATT3x...`, `-----BEGIN ... PRIVATE KEY-----`, high-entropy/non-placeholder `STRAVA_CLIENT_SECRET`/`MAPS_API_KEY`/`Basic ...`) with explicit allowlist for `secrets.defaults.properties`, `app/build.gradle:L53`, `ATT-4004_plan.md`, and synthetic unit test fixtures; plus `--check-local-perms` mode checking `0600` on existing local credential files (`.env.*`, `secrets.properties`, `release.properties`, `app/google-services.json`, `release.keystore`) and `--fix-local-perms` helper for developers.
2. **CI & Repo Hygiene**: Add `.jira_audit.log` to `.gitignore`, redact `ATT-4004_walkthrough.md:L78-L81`, wire `python3 tools/check_secrets_hygiene.py` into both GitHub workflows before assembly, and document in `docs/ci-secrets.md`.

## 7. System Invariants & Risk Assessment
* **Core Invariants**: (1) `app/src/main/` untouched; (2) `secrets.defaults.properties` & `BuildConfigSecretsContractTest` preserved; (3) Existing `ReleaseCiAndLintConfigContractTest` pins pass; (4) `chmod 0600` deferred strictly to Stage 4.
* **Risk Rating**: **LOW** — Offline regex/mode check on `8,474` tracked files is deterministic with verified allowlist and zero build side effects.
