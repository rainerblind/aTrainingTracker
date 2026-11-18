# Stage 5 Walkthrough: ATT-8206 - Symmetric REQ-TST Parity, Tag Scanner & Shard Splitting

**DOC-ID**: `DOC-WLK-ATT-8206` | **Ticket**: [ATT-8206](https://rainerblind.atlassian.net/browse/ATT-8206) (`2 SP`) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)  
**Target Release**: `V5.0.0` | **Branch**: `feature/ATT-8206` *(kept intact for Stage 6 Cold Review per Rule 3.3)* | **Localization Parity**: `N/A - no UI strings`  
**Requirements / Tests**: `REQ-PRO-127` / `TST-PRO-127` (`[Verified]`) | **Date**: 2026-11-18

---

## 1. Summary of Implemented Changes
1. **Symmetric `REQ <-> TST` Edge Enforcement (`REQ-PRO-127.1`)**: Updated `find_traceability_orphans()` and `verify_traceability_orphans()` in `tools/verify_requirement_governance.py` to enforce symmetric `REQ.Tests <-> TST.Verifies` edge parity while preserving `tools/traceability_baseline.json` (`15` legacy orphan requirements, `|B_{t+1}| <= |B_t|`). Reconciled all `37` `TST->REQ` + `35` `REQ->TST` mismatches across living shards (`--coverage` Cat 4 `1244/1244 = 100.00%`).
2. **Test Tag Scanner Verification (`REQ-PRO-127.2`)**: Verified `extract_test_code_tags()` scans `app/src/test/`, `app/src/androidTest/`, and `tools/test_*.py` under `L2954-2957` allowlists (`5` Cat-3 tags audited cleanly), covered by new offline `tempfile` unit tests in `tools/test_verify_requirement_governance.py`.
3. **Oversized Shard Splitting, Headers & Contract Alignment (`REQ-PRO-127.3`)**: Updated `tools/shard_living_docs.py` (`check_shard_sizes()`, `--verify-only`, `split_oversized_shard()`) for dynamic `*_partN.md` discovery and unique `DOC-*-P{n}` + semver + `Change History` headers (`REQ-PRO-120`). Split all `5` shards `> 4,000` lines (`27/27` shards now `<= 4,000` lines; max `3,993` in `req_ui_design_system_part3.md`), synced `docs/requirements.md` (`16` shards, `1,237` REQs) and `docs/tests.md` (`11` shards, `1,244` TSTs) with `RAD`/`HRV`/`WKO`/`CLM` prefixes, updated `docs/architecture.md` Sec 2, and updated `NonFunctionalRequirementsContractTest.kt`, `SystemRequirementsSpecificationContractTest.kt`, and `SystemArchitectureDesignContractTest.kt` to load `*_process_stability*.md`.
4. **CI Size Gate (`REQ-PRO-127.4`)**: Added `--check-size` (`verify_shard_sizes()`) to `tools/verify_requirement_governance.py` and wired `python3 tools/shard_living_docs.py --check-size --strict` into `.github/workflows/android-branch-build.yml` and `.github/workflows/android-release.yml`.

## 2. Verification Scope & Exact Results
* **Python Test Suites (`Rule 5` offline)**:
  * `python3 -m unittest discover -s tools -p "test_*.py"`: **490/490 OK** (`107.495s`).
  * `python3 -m unittest discover -s tools/hoechstmass/tools -p "test_*.py"`: **56/56 OK** (`2.211s`).
* **Tier-1 Targeted Gradle Build Contract Suite (under `flock`, `--max-workers=2`)**: **113/113 PASS** (`0` failures, `0` errors, `0` skipped across `16` classes: `AdrCompletenessContractTest` `2`, `AspiceSweAuditContractTest` `13`, `DetailedDesignParityContractTest` `3`, `HoechstmassCoreFrameworkContractTest` `7`, `HoechstmassShowcaseAndCommunityContractTest` `3`, `HoechstmassToolingContractTest` `4`, `NonFunctionalRequirementsContractTest` `3`, `ReleaseCiAndLintConfigContractTest` `33`, `SourceTextReadingAuditContractTest` `4`, `SystemArchitectureDesignContractTest` `9`, `SystemContextContractTest` `6`, `SystemDataExchangeRequirementsContractTest` `4`, `SystemIntegrationTestContractTest` `6`, `SystemLiveTrackingRequirementsContractTest` `4`, `SystemRequirementsSpecificationContractTest` `5`, `SystemVerificationProtocolContractTest` `7`).
* **Governance & Coverage Gates (post-`[Verified]` promotion & `--sync-indexes`)**:
  * `python3 tools/verify_requirement_governance.py --check-size --check-doc-headers --check-conflict-markers --check-commit-hygiene --check-sensor-javadocs`: **PASS** (`27/27` shards `<= 4,000` lines, `59` governed docs, `7,857` tracked files, `4` post-cutoff commits, `67` SensorType constants).
  * `python3 tools/verify_requirement_governance.py --coverage`: **PASS** (Cat 1 `1237/1237 = 100.00%`, Cat 2 `1023/1244 = 82.23%`, Cat 3 `1023/1028 = 99.51%`, Cat 4 `1244/1244 = 100.00%` / `0` gaps).
  * `python3 tools/verify_requirement_governance.py` & `python3 tools/shard_living_docs.py --check-size --strict`: **exit 0** (`15` baseline orphans preserved, `0` duplicate/missing `DOC-*` IDs).
* **On-Device / Emulator**: `N/A` (non-UI tooling, CI, and living-docs ticket; zero `app/src/main/` edits).

## 3. System Invariants Confirmed
* Zero `app/src/main/` or `ANT-Android-SDKs` edits; `tools/traceability_baseline.json` unchanged at `15` orphans.
* `feature/ATT-8206` kept intact (NOT merged to `sprint/2026-11-18_01_Früh`); Human Decision Gate preserved.
