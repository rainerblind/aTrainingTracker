# Stage 3: Implementation Plan - ATT-8200: Fix silent ERROR gate-bypass, broken coverage JSON keys, and tag-path lookup in generate_release_verification_summary.py

**Ticket**: [ATT-8200](https://rainerblind.atlassian.net/browse/ATT-8200) | **Sub-task**: Pending creation (`[Impl-Plan]`)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` (ID `10372`) | **Active Sprint**: `2026-11-18_02_Spät` (ID `460`)  
**Requirement Mapping**: `REQ-PRO-074` (`.1`–`.5`) | **Test Mapping**: `TST-PRO-074` (`.1`–`.5`)  
**Branch**: `feature/ATT-8200` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-18

## 1. Problem Description & Traceability
* **Scope**: Remediate silent `"ERROR: Gate violated:"` threshold bypasses, stale `cat3`/`cat4` JSON keys, and `V<ver>__<code>` dossier path failures in `tools/generate_release_verification_summary.py`, clarify dossier tag resolution in `docs/releases/README.md`, and add regression tests in `tools/test_generate_release_verification_summary.py`.
* **Traceability**: `REQ-PRO-074.1`/`.2`/`.3`/`.4`/`.5` $\leftrightarrow$ `TST-PRO-074.1`/`.2`/`.3`/`.4`/`.5`; zero `SYS.2`/`SYS.3` impact (`REQ-PRO-131.4`).

## 2. System Invariants & Preserved Behavior
1. **Header & Archive Compatibility**: Preserve raw `--tag` and extracted `versionCode` in the rendered Markdown header; check raw-tag `build-evidence/junit-test-results-<raw_tag>.zip` first with fallback to `<dossier_tag>`.
2. **Subprocess Non-Zero JSON Parsing**: Parse `verify_requirement_governance.py --coverage-json` stdout JSON even when exit code is `1` on threshold failure.
3. **Non-Gating Simulator & Contract Rules**: Keep `collect_sys4_summary()` non-blocking (`REQ-PRO-112`) and preserve all `ReleaseManagerSystemEvidenceContractTest.kt` constraints (`--output`, zero `--output-json`).
4. **Zero Android / Side-Tool Changes**: Zero edits to `app/`, `.github/workflows/android-release.yml`, `tools/verify_requirement_governance.py`, or pre-existing `tools/` files (`ble_radar_simulator.py`, `jira_created_vs_resolved.py`, etc.).
5. **Human Decision Gate**: Parent ticket stops at `Final Review (Human)` (`Rule 3.1`).

## 3. Proposed Architectural Changes
* **Tag Resolution Helper**: Pure Python stdlib `normalize_release_tag(tag, repo_root=None) -> (raw_tag, dossier_tag, version_code)` stripping `__\d+$` for `docs/releases/<dossier_tag>/` unless `docs/releases/<raw_tag>/` exists.
* **UI Consistency**: No UI changes (`Localization Parity: N/A - no UI strings`).

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)

| Step | Target File | Requirement / Test | Atomic Changes |
| :--- | :--- | :--- | :--- |
| **1** | `tools/generate_release_verification_summary.py` | `REQ-PRO-074.1`, `.2`, `.4` | Add `normalize_release_tag()`; resolve `docs/releases/<dossier_tag>/` in `collect_sys4_summary()`, `collect_sys5_summary()`, and default `--out`; check raw-tag JUnit zip first with dossier-tag fallback in `collect_unit_test_summary()`; record raw tag, `versionCode`, and dossier path in `render_markdown_summary()`. |
| **2** | `tools/generate_release_verification_summary.py` | `REQ-PRO-074.3`, `.5` | In `collect_coverage_summary()`: parse stdout JSON on non-zero subprocess exit (`check=False`); catch both `FAIL:` and `ERROR:` prefixes (plus fallback deviation when `not gate_ok`); read `category_3_code_tags_without_spec` and `category_4_tsts_not_cited_back`. |
| **3** | `docs/releases/README.md` | `REQ-PRO-074.1` | Clarify in Section 3 that `--tag V<versionName>__<versionCode>` normalizes dossier resolution to `docs/releases/V<versionName>/` per `cm_plan.md` while keeping raw-tag JUnit archive lookup. |
| **4** | `tools/test_generate_release_verification_summary.py` | `TST-PRO-074.1`–`.5` | Add unit/integration tests for `normalize_release_tag`, raw-vs-dossier JUnit zip priority, `ERROR: Gate violated:` blocking (`--fail-on-deviations` exit `1`), non-zero `cat3`/`cat4` key extraction, subprocess exit-`1` JSON parsing, and end-to-end `--tag V5.0.0__273`. |
| **5** | Living Docs (`req_process_stability_part2.md`, `tst_process_stability_part2.md`) | `REQ-PRO-074`, `TST-PRO-074` | Promote `[Planned]` -> `[Implemented]` (Stage 4) and `[Verified]` (Stage 5) after passing verification commands. |

## 5. Verification & Rollback Plan
* **Targeted Tests**:
  * `python3 -m unittest tools.test_generate_release_verification_summary`
  * `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseManagerSystemEvidenceContractTest"`
  * `python3 tools/verify_requirement_governance.py --repo-root .`
* **Rollback**: All edits stay isolated on `feature/ATT-8200` (uncommitted until Stage 6 squash); `git checkout -- ...` or deleting `feature/ATT-8200` reverts cleanly without touching `sprint/2026-11-18_02_Spät`.
