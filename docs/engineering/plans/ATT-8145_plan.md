# Stage 3: Implementation Plan - ATT-8145: Triage Verified requirements and test specs without executable test evidence

**Ticket**: [ATT-8145](https://rainerblind.atlassian.net/browse/ATT-8145)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-47.1` | **Branch**: `feature/ATT-8145`  
**Requirement Mapping**: `REQ-PRO-101` (`REQ-PRO-101.1`..`REQ-PRO-101.5`)  
**Test Mapping**: `TST-PRO-101` (`TST-PRO-101.1`..`TST-PRO-101.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16

---

## 1. Problem Description & Background
`ATT-8143` (`f9d9b9e5`) added `--check-verified-evidence` with a `405`-ID `LEGACY_UNBACKED_VERIFIED_TSTS` ratchet baseline. Currently `369` active `[Verified]` TSTs across `7` shards lack code tags (`36` baseline IDs already resolved), `332` `[Verified]` REQ headings cite only unbacked TSTs, and `14/15` baselined orphans in `tools/traceability_baseline.json` remain `[Verified]`. This plan implements deterministic triage, test-tag reconciliation, truthful `[Implemented]` downgrades, ratchet reduction, and cross-cutting test parity.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-101` (`REQ-PRO-101.1`..`REQ-PRO-101.5`) in `docs/requirements/req_process_stability.md`
* **Test Mapping**: `TST-PRO-101` (`TST-PRO-101.1`..`TST-PRO-101.5`) in `docs/tests/tst_process_stability.md`

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime & Submodule Impact**: Zero `app/src/main/` edits; `ANT-Android-SDKs` remains `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`.
2. **Evidence Store Compatibility**: `verify_test_evidence()` inspects only `*.json` in `docs/engineering/test_evidence/`, so `ATT-8145_unbacked_triage.csv` passes `--check-test-evidence` and `ALLOWED_ENGINEERING_ENTRIES` cleanly.
3. **Standard Heading Tokens & Parity**: All downgraded headings use `[Implemented]` (`[Verified|Implemented|Planned|Open]` per `AspiceSweAuditContractTest`) with `--sync-indexes` parity.
4. **Monotonic Ratchet & Backlog-Only Follow-Ups**: `LEGACY_UNBACKED_VERIFIED_TSTS` and `tools/traceability_baseline.json` strictly shrink (`|B_{t+1}| <= |B_t|`). Any AC-mandated domain follow-up tickets use `create-issue` in Product Backlog only (never touching `447` `Vom PO Freigegeben`).

---

## 4. Proposed Architectural Changes
* **Governance Triage CLI (`tools/verify_requirement_governance.py`)**: Add `triage_unbacked_verified_tsts()` and `--triage-unbacked` (CSV export to `docs/engineering/test_evidence/ATT-8145_unbacked_triage.csv`), classifying `unit-test-backed`, `UI/manual-only`, `obsolete/duplicate`, `missing test code`, and stale `LEGACY_UNBACKED_VERIFIED_TSTS` entries; reduce `LEGACY_UNBACKED_VERIFIED_TSTS` to `0` (or ticket-referenced residual).
* **Test-Tag Reconciliation (`app/src/test/**`, `tools/test_*.py`)**: Add `TST-*` KDoc/comment tags to the `157` `unit-test-backed` suites preserving `ATT-8143` XML correlation and existing comments.
* **Living-Doc Downgrades (`docs/tests/*.md`, `docs/requirements/*.md`)**: Downgrade unattested `UI/manual-only` (incl. `TST-MIG-006`), `obsolete/duplicate`, `missing test code` TSTs, `14` `[Verified]` orphans, and unbacked `[Verified]` REQs to `[Implemented]`; sync master tables via `--sync-indexes`.
* **Cross-Cutting Tests & Mirror (`tools/hoechstmass/tools/`, `tools/test_verify_requirement_governance.py`)**: Mirror generic `triage_unbacked_verified_tsts()` cleanly (`HoechstmassToolingContractTest`); add `TST-PRO-101.*` tests and parameterize `test_scenario_60`/`61` `baseline_allowlist` fixtures.
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Risk Classification**: **STANDARD (Medium Process Risk, Zero Runtime Risk)** — executed by **`subagent_flash`**.
* **Step 1 (Triage CLI & CSV Export)**: Implement `triage_unbacked_verified_tsts()` + `--triage-unbacked` in `tools/verify_requirement_governance.py` (and generic mirror in `tools/hoechstmass/tools/verify_requirement_governance.py`); generate `docs/engineering/test_evidence/ATT-8145_unbacked_triage.csv`.
* **Step 2 (Existing Test-Suite Tagging)**: Add explicit `TST-*` tags across the `157` `unit-test-backed` files in `app/src/test/**` and `tools/test_*.py` without altering test logic.
* **Step 3 (Downgrades, Ratchet & Index Sync)**: Downgrade remaining unbacked TSTs (incl. `TST-MIG-006`), `14` `[Verified]` orphans, and unbacked `[Verified]` REQs to `[Implemented]`; lower `LEGACY_UNBACKED_VERIFIED_TSTS`; mark `REQ-PRO-101`/`TST-PRO-101` `[Implemented]`; run `--sync-indexes`.
* **Step 4 (Domain Follow-Up Tickets)**: Per `ATT-8145` Scope/ACs, file per-domain Product Backlog tickets (`create-issue ... Verbesserung ATT-8138 --points=<1..3>`) for missing tests without touching `447` or starting implementation.
* **Step 5 (Cross-Cutting Test Maintenance & Verification)**: Add `TST-PRO-101.1`..`101.4` tests and update `test_scenario_60`/`61` in `tools/test_verify_requirement_governance.py` + `tools/hoechstmass/tools/test_hoechstmass_tools.py`; run Tier 1 verification:
  ```bash
  python3 -m unittest discover -s tools -p "test_*.py" && \
  python3 -m unittest discover -s tools/hoechstmass/tools -p "test_*.py" && \
  python3 tools/verify_requirement_governance.py && \
  python3 tools/verify_requirement_governance.py --check-test-evidence && \
  python3 tools/verify_requirement_governance.py --check-verified-evidence && \
  python3 tools/parallel_sprint_lock.py ./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest" --tests "com.atrainingtracker.trainingtracker.build.HoechstmassToolingContractTest" --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"
  ```
  *(Plus targeted Gradle `--tests` for any `.kt`/`.java` unit test classes tagged in Step 2.)*

---

## 6. Verification & Rollback Plan
* **Verification**: All Python `unittest` suites, all governance CLI flags (`--check-evidence`, `--check-orphans`, `--check-test-evidence`, `--check-verified-evidence`, `--verify-sys-trace`), and Gradle contract + tagged unit tests exit `0`.
* **Rollback**: Branch isolation (`feature/ATT-8145`) allows clean revert without impacting `sprint/2026-47.1` or `develop`.
