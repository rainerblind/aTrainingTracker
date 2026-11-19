# Stage 2: Requirement & Test Specification - ATT-8848

**DOC-ID**: `DOC-TST-ATT-8848` | **Ticket**: [ATT-8848](https://rainerblind.atlassian.net/browse/ATT-8848) (`1 SP`) | **Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-19_01_Früh` | **Branch**: `feature/ATT-8848`  
**Requirement Mapping**: `REQ-TRK-087`, `REQ-PRO-099.3`, `REQ-PRO-101` | **Test Spec ID**: `TST-TRK-087` (existing at `docs/tests/tst_tracking_cockpit.md:3445-3459`) | **Date**: 2026-11-19

---

## 1. Requirement & Traceability Specification (Existing `REQ-TRK-087` / `REQ-PRO-099.3`)
- **Attributes**: Priority: `Medium` (Ticket) / `Low` (`REQ-TRK-087`) | Type: `Functional` | Verification Method: `Unit` | System Req: `SYSREQ-TRK-006` (`SYS.2`/`SYS.3` = no impact per `REQ-PRO-131.4`)
- **Implementation Target**: Test-code KDoc only (`0` `app/src/main/` edits; `0` new `REQ-*`/`TST-*` IDs reserved):
  1. `app/src/test/java/com/atrainingtracker/trainingtracker/zones/HrvDfaA1RangeSliderGestureTest.kt`: Add KDoc tags `TST-TRK-087.1` (class KDoc and/or methods `testHrvDfaA1SettingsContent_omitsGaugeToggleAndRendersPreviewBar` at `:507` and `testCaptureLightAndDarkHrvZoneDisplayScreenshot_rule85` at `:557`, citing `REQ-TRK-087.1`, `REQ-TRK-087.2`, `ATT-8712`, `ATT-8848`).
  2. `app/src/test/java/com/atrainingtracker/trainingtracker/zones/HrvDfaA1SettingsContractTest.kt`: Add KDoc tag `TST-TRK-087.2` on `testHrvSettings_gaugeToggleAndPreviewBarContracts` (`:292`, citing `REQ-TRK-087.1`, `REQ-TRK-087.2`, `ATT-8712`, `ATT-8848`).
- **Contract Constraints**:
  - `extract_test_code_tags()` (`tools/verify_requirement_governance.py:2977-3070`) MUST detect `TST-TRK-087.1` and `TST-TRK-087.2` and normalize both to base ID `TST-TRK-087`.
  - Behavioral Compose and contract assertions MUST remain unchanged; `HrvDfaA1RangeSliderGestureTest.kt` MUST preserve `0` `app/src/main/` file reads (`REQ-PRO-049.1`); `app/source-reading-baseline.txt` MUST remain untouched.
  - **9-Locale Localization Parity**: `N/A` — zero UI strings added, modified, or removed across `values/`..`values-pt/`.

## 2. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
- **Original Requirement ID & Target**: `REQ-PRO-099.3` (`tools/verify_requirement_governance.py:3525-3621`) and `REQ-TRK-087` / `TST-TRK-087` (`docs/tests/tst_tracking_cockpit.md:3445-3459`).
- **Historical Origin & Commit Trace**: `ATT-8145` / `ATT-8206` (`f55c9fee`) introduced `REQ-PRO-099.3` & `REQ-PRO-101` (`test_scenario_64`); `ATT-8712` (`62c5d410`) added `TST-TRK-087` `[Verified]` and its tests while citing `REQ-TRK-087.1` instead of `TST-TRK-087`.
- **Root Reason for Existing Formulation**: Clean-worktree evidence verification (`REQ-PRO-099.5`) relies on `TST-*` code tags when JUnit XMLs are absent so `[Verified]` claims remain machine-linked to executable tests.
- **Preservation of Core Invariants**: `LEGACY_UNBACKED_VERIFIED_TSTS` ratchet, `REQ-PRO-099.3` gating, `REQ-TRK-087` assertions, `app/source-reading-baseline.txt`, and `0` `app/src/main/` reads in `HrvDfaA1RangeSliderGestureTest.kt` are strictly preserved.

## 3. Acceptance Criteria (Given-When-Then)
- *Given* `docs/tests/tst_tracking_cockpit.md:3445` declares `### TST-TRK-087 ... [Verified]` and KDoc tags `TST-TRK-087.1` / `TST-TRK-087.2` are added to `HrvDfaA1RangeSliderGestureTest.kt` and `HrvDfaA1SettingsContractTest.kt`,
- *When* `python3 tools/verify_requirement_governance.py --repo-root . --check-verified-evidence` and `python3 -m unittest tools/test_verify_requirement_governance.py -k test_scenario_64` execute,
- *Then* `extract_test_code_tags()` maps both files to `TST-TRK-087`, both governance checks exit `0` with `0` unbacked `[Verified]` errors, and `--rerun` of `HrvDfaA1RangeSliderGestureTest` + `HrvDfaA1SettingsContractTest` in Stage 5 passes 100% without altering any assertion or `app/source-reading-baseline.txt`.

## 4. Test Specification (`TST-TRK-087` Verification Plan — Stage 5 Execution)
1. **Governance Tag & Ratchet Check (`TST-TRK-087` / `TST-PRO-101`)**:
   - Run `python3 -m unittest tools/test_verify_requirement_governance.py -k test_scenario_64` and `python3 tools/verify_requirement_governance.py --repo-root . --check-verified-evidence` -> expect exit `0` (`PASS`).
   - Run `python3 tools/verify_requirement_governance.py --base-ref refs/heads/sprint/2026-11-19_01_Früh` -> expect exit `0`.
2. **Incremental Gradle Unit Verification (Stage 5 under `parallel_sprint_lock.py`, `--rerun`)**:
   - Execute `python3 tools/parallel_sprint_lock.py ./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.zones.HrvDfaA1RangeSliderGestureTest" --tests "com.atrainingtracker.trainingtracker.zones.HrvDfaA1SettingsContractTest"` -> expect 100% pass and `0` `app/src/main/` reads added (`REQ-PRO-049.1`).

## 5. Traceability Matrix

| Test Case | Scope | Target File / Check | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-087.1` | Behavioral Compose UI (`KDoc` tag) | `HrvDfaA1RangeSliderGestureTest.kt` (`:507`, `:557`) | `REQ-TRK-087.1`, `REQ-TRK-087.2` | Existing (`Verified` in living doc) |
| `TST-TRK-087.2` | Contract Test (`KDoc` tag) | `HrvDfaA1SettingsContractTest.kt` (`:292`) | `REQ-TRK-087.2`, `REQ-PRO-099.3` | Existing (`Verified` in living doc) |
| `TST-PRO-101` | Python Governance Ratchet | `test_verify_requirement_governance.py` (`test_scenario_64`) | `REQ-PRO-099.3`, `REQ-PRO-101` | Target Pass in Stage 4/5 |
