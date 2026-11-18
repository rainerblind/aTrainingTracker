# Stage 3: Implementation Plan - ATT-8198: Behavioral Compose UI Tests & Reflection-Only Ratchet

**Ticket**: [ATT-8198](https://rainerblind.atlassian.net/browse/ATT-8198) | **Sub-task**: [ATT-8984](https://rainerblind.atlassian.net/browse/ATT-8984) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-18_02_Spät` | **Branch**: `feature/ATT-8198`  
**Requirement / Test Mapping**: `REQ-PRO-132` (`REQ-PRO-132.1`–`.4`) / `TST-PRO-132` (`TST-PRO-132.1`–`.4`, strengthening `TST-CON-031.5b`, `TST-TRK-062.5`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-18

---

## 1. Architecture & Layering Overview
* **Scope**: Purely test-infrastructure, build-contract, and process-governance changes (`0` edits to `app/src/main/`, `ANT-Android-SDKs`, or `values*/strings*.xml`).
* **UI Consistency (`docs/design_guidelines.md`)**: No production UI modified; `RadarSettingsSectionTest.kt` renders existing `RadarSettingsSection` (`RadarSettingsSection.kt:75`) inside `ATrainingTrackerTheme` + scrollable `Column` using Robolectric `4.14.1` and `createComposeRule()` (`app/build.gradle:120,265,274,309,312`).

---

## 2. Atomic Implementation Steps

| Step | Target Files | Exact Changes | Traces To |
| :--- | :--- | :--- | :--- |
| **1** | `app/ui-reflection-baseline.txt`<br>`app/src/test/.../build/UiReflectionOnlyTestAuditContractTest.kt` | Generate sorted `189`-entry baseline (`178` `trainingtracker/ui/` + `11` `banalservice/ui/`). Implement contract audit scanning `app/src/test/java/` paths matching `(?:^\|/)ui/` for multiline `Class.forName\s*\(\s*"[^"]*Kt"\s*\)` without `createComposeRule`. Enforce `(detected - baseline).isEmpty()`, `(baseline - detected).isEmpty()`, `baseline.size <= 189`, `0` false positives on `build/`, non-`*Kt`, and `createComposeRule()`-backed files, plus synthetic negative tripwire. Zero `src/main/` reads. | `REQ-PRO-132.1`<br>`REQ-PRO-132.2`<br>`TST-PRO-132.1`<br>`TST-PRO-132.2` |
| **2** | `docs/coding_standards.md` (§5.4, v`1.0.1`)<br>`.agents/skills/stage4-implementation/SKILL.md`<br>`.agents/skills/stage5-verification/SKILL.md` | Clarify §5.4: distinguish legitimate `build/` & non-`*Kt` excision/ABI reflection from banned reflection-only UI tests; mandate `createComposeRule()` behavioral assertions (semantics, conditional visibility, callbacks) for new/modified `@Composable`s; bump `docs/coding_standards.md` `Version` to `1.0.1` + Change History (`REQ-PRO-120`). Add `createComposeRule()` mandate to Stage 4/5 skills while preserving all `AspiceSweAuditContractTest` & §1.1–§1.5 markers. | `REQ-PRO-132.3`<br>`TST-PRO-132.3` |
| **3** | `app/src/test/.../ui/settings/radar/RadarSettingsSectionTest.kt` | Add `@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [34])` + `@get:Rule val composeTestRule = createComposeRule()`. Retire stale `:38-129` `RadarLightControlMode`/`TuningConfig` tautologies. Isolate `TuningPreferencesDataStore` (`MutableStateFlow<TuningConfig>`, `coEvery` update stubs) + mockkStatic `TrainingApplication` (`uploadStravaRadarSummary`), `RadarTrafficSimulator`, `RadarAlertManager`. Compose `RadarSettingsSection` and assert: (a) `FULL_SCREEN` hides `radar_overlay_clear_mode` (`assertDoesNotExist()`), `RIGHT` shows it & click calls `updateRadarEdgeSide(RadarEdgeSide.FULL_SCREEN)`; (b) `radarSpatialAudioEnabled` toggles `radar_traffic_orientation_title` & `radar_lane_change_tick_title`; (c) retired `RadarLightControlMode` strings absent while `radar_brake_light_title` is visible & click calls `updateRadarBrakeLightEnabled`; (d) simulator Start button invokes `onSimulationToggled(true)`. Preserve `:131-226` 9-locale XML checks; `0` `src/main/` reads. Verify file is not in `app/ui-reflection-baseline.txt` or `app/source-reading-baseline.txt`. | `REQ-PRO-132.4`<br>`TST-PRO-132.4`<br>`TST-CON-031.5b`<br>`TST-TRK-062.5` |
| **4** | `docs/requirements/req_process_stability_part2.md`<br>`docs/tests/tst_process_stability_part2.md` | Promote `REQ-PRO-132` & `TST-PRO-132` `[Planned]` -> `[Implemented]` in Stage 4 (bump versions & Change History), then `[Verified]` in Stage 5 via `submit-stage ... 5`. | `REQ-PRO-120` |

---

## 3. Targeted Verification Plan (Tier 1 & Governance)
* **`Rule 8.5` Check**: `N/A - no production UI or image assets created/modified` (`RadarSettingsSectionTest` is a Robolectric unit test).
* **Gradle Tier 1 Command** (via `python3 tools/parallel_sprint_lock.py`):
  ```bash
  ./gradlew :app:testDebugUnitTest \
    --tests "com.atrainingtracker.trainingtracker.build.UiReflectionOnlyTestAuditContractTest" \
    --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest" \
    --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest" \
    --tests "com.atrainingtracker.trainingtracker.build.AndroidViewModelReflectionContractTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.settings.radar.RadarSettingsSectionTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.tracking.radar.RadarLightQuickSwitchSheetTest"
  ```
* **Governance Verification**: `python3 tools/verify_requirement_governance.py --sync-indexes && python3 tools/verify_requirement_governance.py --repo-root .` (`exit 0`).

---

## 4. System Invariants & Rollback Safety
1. **Zero Production Changes**: `app/src/main/`, SQLite schemas, `ANT-Android-SDKs`, and `values*/strings*.xml` remain untouched (`N/A - no UI strings`).
2. **Existing Ratchets Intact**: `SourceTextReadingAuditContractTest` keeps `22` governance guards and `105` baseline; legitimate `build/` & non-`*Kt` reflection checks remain unflagged.
3. **Code Lock & Rollback**: Production code edits remain prohibited until `jira_util.py check-gate` confirms Gate 3 `PASS`; pure test/doc changes revert cleanly if needed.
