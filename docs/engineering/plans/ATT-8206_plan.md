# Stage 3 Implementation Plan: ATT-8206 - Symmetric REQ-TST Parity, Tag Scanner & Shard Splitting

**DOC-ID**: `DOC-PLN-ATT-8206` | **Ticket**: [ATT-8206](https://rainerblind.atlassian.net/browse/ATT-8206) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)  
**Target Release**: `V5.0.0` | **Branch**: `feature/ATT-8206` | **Requirements**: `REQ-PRO-127` (`[Planned]`) | **Tests**: `TST-PRO-127` (`[Planned]`)

---

## 1. Architecture & Component Boundaries (SWE.2)
* **Tooling, Docs, CI & Contract Scope**: Confined to `tools/verify_requirement_governance.py`, `tools/shard_living_docs.py`, `tools/test_verify_requirement_governance.py`, `tools/test_shard_living_docs.py`, living shards/indexes (`docs/{requirements,tests}.md`, `docs/{requirements,tests}/*.md`, `docs/architecture.md`), `.github/workflows/android-{branch-build,release}.yml`, and 3 build contract tests (`NonFunctionalRequirementsContractTest.kt`, `SystemRequirementsSpecificationContractTest.kt`, `SystemArchitectureDesignContractTest.kt`).
* **UI Consistency**: `N/A` (zero UI, Compose, or `strings*.xml` edits; `Localization Parity: N/A - no UI strings`).
* **Isolation & Invariant Boundaries**: Zero `app/src/main/` or `ANT-Android-SDKs` edits; zero live Jira calls in tests (`Rule 5`); `tools/traceability_baseline.json` preserved at `<= 15` orphans (`|B_{t+1}| <= |B_t|`); `100%` `REQ`/`TST` ID uniqueness and `DOC-*` work-product headers (`REQ-PRO-120`) preserved across all sub-shards.

## 2. Atomic Implementation Steps & 1:1 Traceability
| Step | Target Files | Change Summary | REQ / TST |
| :--- | :--- | :--- | :--- |
| **1** | `tools/verify_requirement_governance.py`, `docs/requirements/*.md`, `docs/tests/*.md` | Extend `find_traceability_orphans()` & `verify_traceability_orphans()` (`:1879-2050`) to parse `REQ.Tests` vs `TST.Verifies` and fail on asymmetric `(req, tst)` edges outside `tools/traceability_baseline.json` (`15` orphans); reconcile all `37` `TST->REQ` + `35` `REQ->TST` mismatches in living shards (`--coverage` Cat 4 == `100%`). | `REQ-PRO-127.1` / `TST-PRO-127.1` |
| **2** | `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py` | Audit `extract_test_code_tags()` (`:2871-2964`) allowlists (`L2954-2957`) & `5` Cat-3 tags; add offline `tempfile` unit tests covering symmetric parity, asymmetric `REQ->TST`/`TST->REQ` failures, `15`-orphan baseline tolerance, and `app/src/{test,androidTest}` + `tools/test_*.py` tag extraction. | `REQ-PRO-127.1–.2` / `TST-PRO-127.1–.2` |
| **3** | `tools/shard_living_docs.py`, `tools/test_shard_living_docs.py` | Update `check_shard_sizes()`/`--verify-only` to discover all `docs/{requirements,tests}/*.md` (incl. `*_partN.md`) dynamically; update `split_oversized_shard()` to emit unique `**Document ID**: DOC-...-P{n}`, semver `Version`, `Status`, `Approver`, and `## Change History` per sub-shard (`REQ-PRO-120`); add unit tests. | `REQ-PRO-127.3` / `TST-PRO-127.3` |
| **4** | `docs/{requirements,tests}/*.md`, `docs/{requirements,tests}.md`, `docs/architecture.md` | Split the 5 `> 4,000`-line shards (`req_ui_design_system.md`, `tst_ui_design_system.md`, `req_tracking_cockpit.md`, `req_process_stability.md`, `req_data_migration_export.md`) into `<= 4,000`-line sub-shards; update master tables (`--sync-indexes`), add `RAD`/`HRV`/`WKO`/`CLM` prefixes (prune empty `PERF`), and align `docs/architecture.md` Sec 2 (`Rule 8.8`). | `REQ-PRO-127.3` / `TST-PRO-127.3` |
| **5** | `NonFunctionalRequirementsContractTest.kt`, `SystemRequirementsSpecificationContractTest.kt`, `SystemArchitectureDesignContractTest.kt` | Replace single-file `req_process_stability.md` / `tst_process_stability.md` reads with multi-part shard aggregation across `docs/requirements/req_process_stability*.md` and `docs/tests/tst_process_stability*.md` (preserving `REQ-PRO-055`, `REQ-PRO-056`, `REQ-PRO-113`, `REQ-NFR-003..020` checks & `AspiceSweAuditContractTest` parity). | `REQ-PRO-127.3` / `TST-PRO-127.3` |
| **6** | `tools/verify_requirement_governance.py`, `.github/workflows/android-branch-build.yml`, `.github/workflows/android-release.yml` | Add `--check-size` CLI flag to `verify_requirement_governance.py` delegating to `shard_living_docs.check_shard_sizes(..., max_lines=4000)`; wire `python3 tools/shard_living_docs.py --check-size --strict` into `android-branch-build.yml` & `android-release.yml` before assembly (`ReleaseCiAndLintConfigContractTest`). | `REQ-PRO-127.4` / `TST-PRO-127.4` |

## 3. System Invariants & Contract Safeguards
* **Zero Regressions**: `AspiceSweAuditContractTest` (`L127-211,284-361`), `ReleaseCiAndLintConfigContractTest` (`L1073-1094` `verify_requirement_governance.py` before `assembleDebug`), and `verify_work_product_headers()` (`REQ-PRO-120`) remain 100% green.
* **Traceability & Split Integrity**: Zero duplicate/dropped `REQ`/`TST` IDs across split parts; `tools/traceability_baseline.json` remains `<= 15` orphans.

## 4. Verification Plan (Executed in Stages 4–5, Post-Gate 3)
1. `python3 -m unittest tools/test_verify_requirement_governance.py tools/test_shard_living_docs.py`
2. `python3 tools/shard_living_docs.py --check-size --strict && python3 tools/verify_requirement_governance.py --check-size && python3 tools/verify_requirement_governance.py --coverage && python3 tools/verify_requirement_governance.py`
3. `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest" --tests "com.atrainingtracker.trainingtracker.build.NonFunctionalRequirementsContractTest" --tests "com.atrainingtracker.trainingtracker.build.SystemRequirementsSpecificationContractTest" --tests "com.atrainingtracker.trainingtracker.build.SystemArchitectureDesignContractTest" --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"`
