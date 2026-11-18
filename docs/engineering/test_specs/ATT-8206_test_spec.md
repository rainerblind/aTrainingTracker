# Stage 2: Requirement & Test Specification - ATT-8206

**DOC-ID**: `DOC-TST-ATT-8206` | **Ticket**: [ATT-8206](https://rainerblind.atlassian.net/browse/ATT-8206) (`2 SP`) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)  
**Target Release**: `V5.0.0` | **Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8206`  
**Requirement Mapping**: `REQ-PRO-127` (`[Planned]`, refining `REQ-PRO-071`, `REQ-PRO-087`, `REQ-PRO-093.3/.4`, `REQ-PRO-120`) | **Test Spec ID**: `TST-PRO-127` (`[Planned]`)  
**Localization parity**: `N/A - no UI strings` | **Date**: 2026-11-18

---

## 1. Requirement Specification (`REQ-PRO-127` `[Planned]`)
* **Attributes**: Priority: `High` | Type: `Non-Functional` | Verification Method: `Unit / Review`
* **Implementation**: `tools/verify_requirement_governance.py`, `tools/shard_living_docs.py`, `tools/test_*.py`, `docs/{requirements,tests}.md`, `docs/{requirements,tests}/*.md`, `docs/architecture.md`, `.github/workflows/android-{branch-build,release}.yml`, `NonFunctionalRequirementsContractTest.kt`, `SystemRequirementsSpecificationContractTest.kt`, `SystemArchitectureDesignContractTest.kt`
* **REQ-PRO-127.1 (Symmetric `REQ <-> TST` Edge Equality & 15-Orphan Baseline Ratchet)**: `find_traceability_orphans()` / `verify_traceability_orphans()` SHALL enforce symmetric edge parity (`(req, tst) in req_to_tsts <=> (req, tst) in tst_to_reqs`), failing on any unbaselined asymmetric `REQ->TST` or `TST->REQ` link while continuing to tolerate the `15` legacy orphan requirements in `tools/traceability_baseline.json` (`|B_{t+1}| <= |B_t|`). Live shards SHALL resolve the `37` `TST->REQ` + `35` `REQ->TST` mismatches to `0` (`--coverage` Cat 4 `100%`).
* **REQ-PRO-127.2 (`androidTest/` & `tools/test_*.py` Tag Scanner Verification)**: `extract_test_code_tags()` SHALL retain scanning of `app/src/test/`, `app/src/androidTest/`, and `tools/test_*.py` (auditing `L2954-2957` prefix allowlists and `5` Cat-3 tags), covered by offline unit tests in `tools/test_verify_requirement_governance.py`.
* **REQ-PRO-127.3 (Oversized Shard Splitting, `DOC-*` Headers & Contract Alignment)**: `tools/shard_living_docs.py` SHALL dynamically discover `docs/{requirements,tests}/*.md` (`*_partN.md`) and emit unique `DOC-*` IDs, semver `Version`, `Status`, `Approver`, and `Change History` on every sub-shard so `AspiceSweAuditContractTest` (`L127-211,284-361`) passes `100%`; all `5` shards `> 4,000` lines (`REQ-PRO-093.3`) SHALL split `<= 4,000` lines with `100%` ID uniqueness; `docs/{requirements,tests}.md` and `docs/architecture.md` Sec 2 SHALL list all sub-shards and active prefixes (`RAD`, `HRV`, `WKO`, `CLM`; prune unused `PERF`); and `NonFunctionalRequirementsContractTest`, `SystemRequirementsSpecificationContractTest`, and `SystemArchitectureDesignContractTest` SHALL scan `req_process_stability*.md` / `tst_process_stability*.md`.
* **REQ-PRO-127.4 (CI `--check-size --strict` Enforcement)**: `tools/verify_requirement_governance.py` SHALL expose `--check-size`, and `.github/workflows/android-branch-build.yml` + `android-release.yml` SHALL run `python3 tools/shard_living_docs.py --check-size --strict` (`4000` line limit).
* **Given-When-Then Acceptance Criteria**:
  * *Given* `tempfile` shards with asymmetric `REQ->TST` / `TST->REQ` edges outside `tools/traceability_baseline.json`, *When* `verify_traceability_orphans()` runs, *Then* it fails deterministically; *When* run on live repo, *Then* `15` baseline orphans are tolerated and Cat-4 gaps == `0`.
  * *Given* `tools/shard_living_docs.py --check-size --strict`, `AspiceSweAuditContractTest`, and the 3 NFR/SYS contract tests, *When* executed after splitting all 5 oversized shards, *Then* every shard is `<= 4,000` lines, every sub-shard carries unique `DOC-*` + `Change History`, and all tests pass `100%`.
* **System Invariants**: Zero `app/src/main/` edits; `tools/traceability_baseline.json` never exceeds `15` orphans; `100%` `REQ`/`TST` ID uniqueness; `Localization parity: N/A - no UI strings`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-PRO-127` (`.1`–`.4`), refining `REQ-PRO-071`, `REQ-PRO-087` (`ATT-7561`), `REQ-PRO-093.3/.4` (`ATT-7565`), `REQ-PRO-120` (`ATT-8164`).
* **Historical Origin & Commit Trace**: `ATT-7561` (`traceability_baseline.json`, `15` orphans), `ATT-7565` (`DEFAULT_MAX_SHARD_LINES = 4000`), `ATT-8143` (`f9d9b9e5`, `androidTest` + `tools/test_*.py` scanning), `ATT-8631` (`a3508e6b`).
* **Root Reason for Existing Formulation**: One-way set subtraction and a static 9-shard list prevented premature breakage during initial sharding while shards grew past `4,000` lines.
* **Preservation of Core Invariants**: Preserves `15`-orphan non-increasing baseline ratchet (`|B_{t+1}| <= |B_t|`), `REQ-PRO-120` work-product headers, `100%` ID uniqueness/master parity (`AspiceSweAuditContractTest`), and zero runtime code edits.

---

## 2. Test Specification (`TST-PRO-127` `[Planned]`)
* **`TST-PRO-127.1` (`tools/test_verify_requirement_governance.py`)**: Unit tests for symmetric `REQ<->TST` parity (fail on asymmetric `REQ->TST` or `TST->REQ`, pass on symmetric + `15` baselined orphans) and live `--coverage` Cat 4 == `0` gaps.
* **`TST-PRO-127.2` (`tools/test_verify_requirement_governance.py`)**: Unit tests proving `extract_test_code_tags()` scans `app/src/androidTest/` and `tools/test_*.py` under `L2954-2957` prefix allowlists and verifies `5` Cat-3 tags cleanly.
* **`TST-PRO-127.3` (`tools/test_shard_living_docs.py` & Gradle contracts)**: Verify all split shards `<= 4,000` lines (`--check-size --strict`), unique `DOC-*` + `Change History` per sub-shard, `docs/{requirements,tests}.md` prefix/count parity, and `100%` pass of `AspiceSweAuditContractTest`, `NonFunctionalRequirementsContractTest`, `SystemRequirementsSpecificationContractTest`, `SystemArchitectureDesignContractTest`.
* **`TST-PRO-127.4` (`tools/verify_requirement_governance.py` & CI workflows)**: Verify `--check-size` flag, CI workflow `--check-size --strict` wiring in `android-branch-build.yml` & `android-release.yml`, `ReleaseCiAndLintConfigContractTest`, and `verify_requirement_governance.py` exit `0` (`Localization parity: N/A - no UI strings`).

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-127.1` | Python Unit / Gov | `find_traceability_orphans()` & `--coverage` Cat 4 (`15` baseline preserved) | `REQ-PRO-127.1`, `REQ-PRO-087` | Planned |
| `TST-PRO-127.2` | Python Unit | `extract_test_code_tags()` (`androidTest` & `tools/test_*.py`) | `REQ-PRO-127.2`, `REQ-PRO-071` | Planned |
| `TST-PRO-127.3` | Python + Gradle Contract | `shard_living_docs.py`, `AspiceSweAuditContractTest`, 3 NFR/SYS contract tests | `REQ-PRO-127.3`, `REQ-PRO-093.3/.4`, `REQ-PRO-120` | Planned |
| `TST-PRO-127.4` | Governance + CI Gate | `verify_requirement_governance.py --check-size` & `.github/workflows/*.yml` | `REQ-PRO-127.4`, `REQ-PRO-090` | Planned |
