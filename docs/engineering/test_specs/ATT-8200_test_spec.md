# Stage 2: Requirement & Test Specification - ATT-8200: Fix silent ERROR gate-bypass, broken coverage JSON keys, and tag-path lookup in generate_release_verification_summary.py

**Ticket**: [ATT-8200](https://rainerblind.atlassian.net/browse/ATT-8200) | **Sub-task**: Pending creation (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` (ID `10372`) | **Active Sprint**: `2026-11-18_02_Spät` (ID `460`)  
**Requirement Mapping**: `REQ-PRO-074` (`REQ-PRO-074.1`, `.3`, `.4`, `.5`) | **Test Spec ID**: `TST-PRO-074`  
**Branch**: `feature/ATT-8200` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-18

## 1. Requirement Specification (`REQ-PRO-074` Refined `[Planned]`)

### 1.1 Problem Statement, Attributes & `SYS.2`/`SYS.3` Impact
`tools/generate_release_verification_summary.py` drops `"ERROR: Gate violated: ..."` diagnostics, reads stale JSON keys for `cat3`/`cat4`, and treats canonical `V<ver>__<code>` CI tags as dossier directories.  
- **Priority**: High | **Type**: Non-Functional | **Verification Method**: Unit / Review  
- **SYS.2/SYS.3 Impact**: None (`REQ-PRO-131.4` — Python CLI & release docs only; zero sensor, RF, FGS, or external API changes).

### 1.2 Functional & Architectural Requirements
1. **`REQ-PRO-074.1` (Tag Normalization)**: `tools/generate_release_verification_summary.py` SHALL normalize `V<versionName>__<versionCode>` (e.g. `V5.0.0__273`) to dossier directory `docs/releases/V<versionName>/` (unless `docs/releases/<raw_tag>/` explicitly exists) for `collect_sys4_summary()`, `collect_sys5_summary()`, and default `--out`, while recording raw release tag, `versionCode`, and dossier path in the output header.
2. **`REQ-PRO-074.2` (JUnit Archive Compatibility)**: `collect_unit_test_summary()` SHALL check raw-tag `build-evidence/junit-test-results-<raw_tag>.zip` first (matching `.github/workflows/android-release.yml`) with fallback to `build-evidence/junit-test-results-<dossier_tag>.zip`.
3. **`REQ-PRO-074.3` (JSON Keys & Subprocess Fallback)**: `collect_coverage_summary()` SHALL read `category_3_code_tags_without_spec` and `category_4_tsts_not_cited_back`, and in subprocess fallback (`--coverage-json`) parse `stdout` JSON even when `verify_requirement_governance.py` exits `1` on threshold failure.
4. **`REQ-PRO-074.5` (`ERROR:`/`FAIL:` Gate Enforcement)**: `collect_coverage_summary()` SHALL capture both `"ERROR:"` and `"FAIL:"` diagnostics (plus fallback deviation whenever `gate_ok` is `False`), setting `[ FAIL ]`, `BLOCKED / REQUIRES HUMAN APPROVAL`, and non-zero exit with `--fail-on-deviations`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-074` (`REQ-PRO-074.1`, `.3`, `.4`, `.5`) targeting `tools/generate_release_verification_summary.py`, `tools/test_generate_release_verification_summary.py`, and `docs/releases/README.md`.
* **Historical Origin & Commit Trace**: `dc7b3d61` (`ATT-6138`, Sprint `2026-46.4`), updated by `1750ab64` (`ATT-8157`, `REQ-PRO-112`) and sharded in `f55c9fee` (`ATT-8206`).
* **Root Reason for Existing Formulation**: Originally assumed `FAIL:` diagnostic prefixes, `category_3_code_without_spec`/`category_4_tst_without_req_citation` keys, and `docs/releases/<tag>/` matching raw Git tags rather than `V<versionName>`.
* **Preservation of Core Invariants**: Preserves `REQ-PRO-112` non-gating simulator rules, pure Python 3 stdlib, raw-tag `junit-test-results-<raw_tag>.zip` CI lookup, `ReleaseManagerSystemEvidenceContractTest.kt`, and zero `app/src/main/` changes.

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* **AC-1**: *Given* `verify_coverage_thresholds()` emitting `"ERROR: Gate violated: ..."` or non-zero `category_3_code_tags_without_spec` / `category_4_tsts_not_cited_back`, *When* `generate_release_verification_summary.py --fail-on-deviations` runs (direct or subprocess fallback), *Then* `overall_status` is `BLOCKED / REQUIRES HUMAN APPROVAL`, exit code is non-zero, and `cat3`/`cat4` gap data is populated.
* **AC-2**: *Given* `--tag V5.0.0__273` and `docs/releases/V5.0.0/`, *When* `generate_release_verification_summary.py` runs, *Then* it reads `docs/releases/V5.0.0/sys_verification_record.md`, writes `docs/releases/V5.0.0/verification_summary.md` with full tag + `versionCode`, and locates `junit-test-results-V5.0.0__273.zip` if present.
* **Invariants**: Zero `app/src/main/` changes; `Localization Parity: N/A - no UI strings`; Rule 3 Human Gate intact.

## 2. Test Specification (`TST-PRO-074` `[Planned]`)
* **`TST-PRO-074.1` (`test_normalize_release_tag_*`, `tools/test_generate_release_verification_summary.py`)**: Verify `V5.0.0__273` normalizes to `docs/releases/V5.0.0/`, prefers existing `docs/releases/<raw_tag>/`, and records raw tag + `versionCode` in rendered Markdown.
* **`TST-PRO-074.2` (`test_collect_unit_test_summary_raw_tag_zip_priority`)**: Verify raw-tag `junit-test-results-V5.0.0__273.zip` is discovered before fallback `junit-test-results-V5.0.0.zip`.
* **`TST-PRO-074.3` (`test_collect_coverage_summary_error_prefix_and_cat3_cat4_keys`, `test_collect_coverage_summary_subprocess_nonzero_json`)**: Verify `"ERROR: Gate violated: ..."` triggers `gate_passed=False`, `[ FAIL ]`, `BLOCKED`, and exit `1` under `--fail-on-deviations`; verify non-zero `category_3_code_tags_without_spec` / `category_4_tsts_not_cited_back` extraction and subprocess exit-`1` JSON parsing.
* **`TST-PRO-074.4` (`test_end_to_end_canonical_tag_with_version_code`)**: Verify end-to-end execution with `--tag V5.0.0__273` against `docs/releases/V5.0.0/sys_verification_record.md`.
* **`TST-PRO-074.5` (Governance & Contract Parity)**: `python3 -m unittest tools/test_generate_release_verification_summary.py`, `python3 tools/verify_requirement_governance.py --repo-root .`, `python3 tools/shard_living_docs.py --check-size --strict`, and `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseManagerSystemEvidenceContractTest"`.

## 3. Traceability Matrix

| Test Case | Scope | Method / Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-074.1` | Unit | `normalize_release_tag`, `render_markdown_summary` | `REQ-PRO-074.1` | Specified |
| `TST-PRO-074.2` | Unit | `collect_unit_test_summary` | `REQ-PRO-074.2` | Specified |
| `TST-PRO-074.3` | Unit | `collect_coverage_summary`, `main` (`--fail-on-deviations`) | `REQ-PRO-074.3`, `REQ-PRO-074.5` | Specified |
| `TST-PRO-074.4` | Integration (Python) | `generate_release_verification_summary` (`V5.0.0__273`) | `REQ-PRO-074.1`, `REQ-PRO-074.4` | Specified |
| `TST-PRO-074.5` | Governance & Contract | `verify_requirement_governance.py`, `ReleaseManagerSystemEvidenceContractTest` | `REQ-PRO-074`, `REQ-PRO-112` | Specified |
