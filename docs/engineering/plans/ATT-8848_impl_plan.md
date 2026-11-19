# Stage 3 Implementation Plan: ATT-8848 - Add Missing TST-TRK-087 KDoc Tags

**DOC-ID**: `DOC-PLN-ATT-8848` | **Ticket**: [ATT-8848](https://rainerblind.atlassian.net/browse/ATT-8848) (`1 SP`) | **Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-19_01_Früh` | **Branch**: `feature/ATT-8848`  
**Requirement Mapping**: `REQ-TRK-087` (`REQ-TRK-087.1`, `REQ-TRK-087.2`), `REQ-PRO-099.3`, `REQ-PRO-101` | **Test Mapping**: `TST-TRK-087` (`TST-TRK-087.1`, `TST-TRK-087.2`)

---

## 1. Architectural Scope & SWE.2 / SWE.3 Impact
- **Test Layer Only**: Add KDoc traceability tags (`TST-TRK-087.1`, `TST-TRK-087.2`) to two existing unit test files under `app/src/test/java/com/atrainingtracker/trainingtracker/zones/`.
- **`app/src/main/` & UI**: `0` production files modified; `0` UI/layout changes (`Localization Parity: N/A`).
- **SWE.2 / SWE.3 / `SYS.2` / `SYS.3`**: `N/A` — no production classes, services, DB schemas, or external interfaces changed (`docs/architecture.md`, `docs/architecture/detailed_design/*`, and `docs/system/*` untouched per `REQ-PRO-131.4`).

## 2. System Invariants & Constraints
1. **Zero Production or Assertion Drift**: Behavioral Compose checks and contract assertions in both test files MUST remain functionally identical.
2. **Source-Read Hygiene (`REQ-PRO-049.1`)**: `HrvDfaA1RangeSliderGestureTest.kt` MUST preserve `0` `app/src/main/` file reads; `app/source-reading-baseline.txt` (`#109` for `HrvDfaA1SettingsContractTest.kt`) MUST remain untouched.
3. **Ratchet & Workspace Hygiene**: `LEGACY_UNBACKED_VERIFIED_TSTS` (`REQ-PRO-099.3`) MUST NOT be edited; the 4 user-owned dirty `tools/` files MUST remain untouched (never run `git add -A`).
4. **Incremental Build Discipline (`Rule 7.5`)**: Use Gradle 9.6 `--rerun` for targeted unit test re-execution; NEVER use `--rerun-tasks` or `-PstampGitHash=true`.

## 3. Step-by-Step Implementation Sequence (Stage 4)
### Step 0: Gate 3 Lock Check (`Rule 4`)
- Run `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` and confirm `GATE_PASSED` before editing any test file.

### Step 1: Add `TST-TRK-087.1` KDoc Tags in `HrvDfaA1RangeSliderGestureTest.kt`
- **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/HrvDfaA1RangeSliderGestureTest.kt`
- **Edit**: Update class KDoc (`:87-92`) and add method KDoc on `testHrvDfaA1SettingsContent_omitsGaugeToggleAndRendersPreviewBar` (`:506`) and `testCaptureLightAndDarkHrvZoneDisplayScreenshot_rule85` (`:556`) citing `TST-TRK-087.1`, `REQ-TRK-087.1`, `REQ-TRK-087.2`, `ATT-8712`, and `ATT-8848`.

### Step 2: Add `TST-TRK-087.2` KDoc Tag in `HrvDfaA1SettingsContractTest.kt`
- **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/HrvDfaA1SettingsContractTest.kt`
- **Edit**: Add method KDoc on `testHrvSettings_gaugeToggleAndPreviewBarContracts` (`:291`) (and update class KDoc `:31-34`) citing `TST-TRK-087.2`, `REQ-TRK-087.1`, `REQ-TRK-087.2`, `ATT-8712`, and `ATT-8848`.

### Step 3: Governance & Incremental Unit Verification
- **Governance Checks**:
  - `python3 tools/verify_requirement_governance.py --repo-root . --check-verified-evidence`
  - `python3 -m unittest tools/test_verify_requirement_governance.py -k test_scenario_64`
- **Targeted Gradle Unit Run (`--rerun`, locked)**:
  - `python3 tools/parallel_sprint_lock.py ./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.zones.HrvDfaA1RangeSliderGestureTest" --tests "com.atrainingtracker.trainingtracker.zones.HrvDfaA1SettingsContractTest"`

## 4. Verification & Rollback Plan
- **Verification**: Both Python governance checks exit `0`; both targeted Gradle test classes pass 100% with `--rerun`; `verify_requirement_governance.py --base-ref refs/heads/sprint/2026-11-19_01_Früh` exits `0`.
- **Rollback**: Isolated to `feature/ATT-8848` (`sprint/2026-11-19_01_Früh` untouched until Stage 6 `PASS`).
