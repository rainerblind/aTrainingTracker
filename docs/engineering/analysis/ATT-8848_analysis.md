# Stage 1 Analysis: ATT-8848 - Add Missing TST-TRK-087 KDoc Tag (`--check-verified-evidence`)

**Ticket**: [ATT-8848](https://rainerblind.atlassian.net/browse/ATT-8848) | **Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) | **Severity**: Minor | **SP**: 1  
**Branch**: `feature/ATT-8848` (off `sprint/2026-11-19_01_Früh` @ `c181df18`) | **Author**: Agent 1 | **Date**: 2026-11-19

## 1. Problem Statement & Motivation
Post-merge governance test `test_scenario_64_tst_pro_101_2_3_4_live_repo_ratchet_zero` (`tools/test_verify_requirement_governance.py:2581`) and `python3 tools/verify_requirement_governance.py --repo-root . --check-verified-evidence` fail with exit code 1 because `docs/tests/tst_tracking_cockpit.md:3445` declares `### TST-TRK-087 ... [Verified]` without any matching `TST-TRK-087` code tag in `app/src/test/`.

## 2. Root Cause Analysis & Independent Hypothesis Verification
- **Governance Mechanism**: `check_verified_evidence()` (`tools/verify_requirement_governance.py:3525-3621`) invokes `extract_test_code_tags()` (`:2977-3070`), which scans `app/src/test/**/*.{kt,java}`, `app/src/androidTest/**/*.{kt,java}`, and `tools/test_*.py` for `TST-[A-Z]+-[0-9]+(?:[._-][A-Za-z0-9_-]+)*` and normalizes sub-tags (e.g., `TST-TRK-087.1`, `TST-TRK-087.2`) to base ID `TST-TRK-087`.
- **Missing Code Tag**: In `ATT-8712` (`62c5d410`), `HrvDfaA1RangeSliderGestureTest.kt:506-618` added `testHrvDfaA1SettingsContent_omitsGaugeToggleAndRendersPreviewBar()` (`TST-TRK-087.1`) and `testCaptureLightAndDarkHrvZoneDisplayScreenshot_rule85()` (`TST-TRK-087.1`), and `HrvDfaA1SettingsContractTest.kt:291-314` added `testHrvSettings_gaugeToggleAndPreviewBarContracts()` (`TST-TRK-087.2`). Both cited `REQ-TRK-087.1` / `ATT-7742` / `Rule 8.5` but omitted any `TST-TRK-087*` tag in class or method KDoc.
- **Independent Peer Review & Verification**:
  - Confirmed live reproduction: `python3 -m unittest tools/test_verify_requirement_governance.py -k test_scenario_64` fails on `ERROR: docs/tests/tst_tracking_cockpit.md:3445 [TST-TRK-087] claims [Verified] without test code or passing test evidence (outside ratchet baseline, REQ-PRO-099.3).`
  - Line-number correction vs. ticket hypothesis: current `TST-TRK-087` heading is at `docs/tests/tst_tracking_cockpit.md:3445` (not `:3390`, shifted by `6bb37af4` adding `TST-CON-052`).
  - Confirmed `0` occurrences of `TST-TRK-087` across `app/src/test/` (`EditSensorFieldZoneDisplayTest.kt` and `TrackingTabConfigZoneDisplayTest.kt` only tag `TST-UI-2783.*`).
  - Confirmed `HrvDfaA1RangeSliderGestureTest.kt` has `0` `app/src/main/` file reads (`REQ-PRO-049.1`), while `HrvDfaA1SettingsContractTest.kt` is already grandfathered in `app/source-reading-baseline.txt:109`.

## 3. User Scope Grounding & Non-Goals (ATT-1250)
- **In-Scope**: Add KDoc `TST-TRK-087` / `TST-TRK-087.1` traceability tags in `HrvDfaA1RangeSliderGestureTest.kt` (and `TST-TRK-087.2` in `HrvDfaA1SettingsContractTest.kt` for full `TST-TRK-087` procedure alignment); re-verify `--check-verified-evidence`, `test_scenario_64`, and targeted Gradle unit tests in Stage 5. Complexity confirmed **1 SP** (no split needed).
- **Non-Goals (Out-of-Scope)**: `0` `app/src/main/` changes; no modifications to `REQ-PRO-099.3` or `LEGACY_UNBACKED_VERIFIED_TSTS`; no edits to `ZonesSettingsActivity.kt` or `ZoneDisplayOptionsControls.kt`; `Localization Parity: N/A` (no UI strings added, modified, or removed).

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
- **Original Requirement ID & Target**: `REQ-PRO-099.3` (`tools/verify_requirement_governance.py:3525-3621`) and `REQ-TRK-087` / `TST-TRK-087` (`docs/tests/tst_tracking_cockpit.md:3445`, `HrvDfaA1RangeSliderGestureTest.kt`, `HrvDfaA1SettingsContractTest.kt`).
- **Historical Origin & Commit Trace**: `REQ-PRO-099.3` (`ATT-8145` / `f55c9fee` via `ATT-8206`) enforces zero novel unbacked `[Verified]` TSTs; `ATT-8712` (`62c5d410`) added `TST-TRK-087` `[Verified]` and its Compose/contract tests without KDoc `TST-TRK-087` tags.
- **Root Reason for Existing Formulation**: Clean-worktree safety (`REQ-PRO-099.5`) allows `--check-verified-evidence` to recognize test-code `TST-*` tags when JUnit XML artifacts are not present, preventing stale or unimplemented `[Verified]` claims from slipping past governance.
- **Preservation of Core Invariants**: `REQ-PRO-099.3` ratchet (`LEGACY_UNBACKED_VERIFIED_TSTS`) and `REQ-TRK-087` behavioral/contract assertions remain untouched; `app/source-reading-baseline.txt` debt boundary and `0` `app/src/main/` reads in `HrvDfaA1RangeSliderGestureTest.kt` are strictly preserved.

## 5. Change-Request Impact Analysis (SUP.10 / REQ-PRO-064)
- **Affected Requirements**: No requirement text change needed (`REQ-TRK-087`, `REQ-PRO-099.3`, and `REQ-PRO-101` satisfied by restoring missing test-code tag). **`SYS.2` / `SYS.3` Impact (`REQ-PRO-131.4`)**: No impact — no external sensors, BLE/ANT+ profiles, Android HAL/FGS, REST APIs, or `SYSREQ-*` / `SYS-EL-*` / `SYS-IF-*` elements affected.
- **Affected Architecture Elements**: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/HrvDfaA1RangeSliderGestureTest.kt` (KDoc only) and `app/src/test/java/com/atrainingtracker/trainingtracker/zones/HrvDfaA1SettingsContractTest.kt` (KDoc only). `0` `app/src/main/` files touched.
- **Verification Impact & Test Surface**: `tools/test_verify_requirement_governance.py` (`test_scenario_64`), `python3 tools/verify_requirement_governance.py --repo-root . --check-verified-evidence`, plus Stage 5 Gradle verification of `HrvDfaA1RangeSliderGestureTest` and `HrvDfaA1SettingsContractTest`. `Localization Parity: N/A`.
- **Risk Assessment & Invariants**: **LOW** risk (comment/KDoc-only traceability fix in test sources). Invariants: zero production code changes, zero behavior/assertion changes, `app/source-reading-baseline.txt` unchanged, and 4 dirty `tools/` files untouched.
