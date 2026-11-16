# Stage 1 Analysis: ATT-7740 - Morning HRV Check Cutoff Hour: Default 08:00 & Max Bound 11:00

**Ticket**: [ATT-7740](https://rainerblind.atlassian.net/browse/ATT-7740) | **Sub-task**: `[Analysis]` (Pending `submit-stage`)
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.1` | **Branch**: `feature/ATT-7740`
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16

---

## 1. Problem Statement & Motivation
In Sprint `2026-46.6` (`6267d2c0`), `ATT-7740` set `DEFAULT_MORNING_CHECK_CUTOFF_HOUR = 8` (`08:00`) and extended `MAX_MORNING_CHECK_CUTOFF_HOUR = 23` (`23:00`). On `2026-11-15`, the PO rejected `23:00` (*"23:00 ist für eine morgendliche Routine viel zu spät. Die obere Grenze (MAX_MORNING_CHECK_CUTOFF_HOUR) muss bei 11:00 Uhr liegen (Default bleibt 08:00)."*). Updating the ceiling to `11` (`11:00`) restores morning-only semantics across `TuningPreferencesDataStore.kt` and `MorningCheckSection.kt`.

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**: `TuningPreferencesDefaults.MAX_MORNING_CHECK_CUTOFF_HOUR` (`TuningPreferencesDataStore.kt:369`) is still `23`, while `DEFAULT_MORNING_CHECK_CUTOFF_HOUR = 8` (`line 367`) and `MIN_MORNING_CHECK_CUTOFF_HOUR = 6` (`line 368`).
* **Independent Peer Review & Verification** (audited against `f628f0a6`):
  * *Slider Wiring (`ATT-8117`)*: Morning Check controls moved from `PhysiologyModelsSection.kt` to `MorningCheckSection.kt` (`lines 114-130`), which dynamically binds `valueRange = MIN..MAX` and `steps = MAX - MIN - 1` (`6f..11f`, `steps = 4` for `06:00..11:00`) and formats `%02d:00`.
  * *DataStore & ViewModel Coercion*: `TuningPreferencesDataStore.kt` (`lines 1274-1276, 1361-1373, 1683-1686`) automatically clamps persisted values into `MIN..MAX`, so any previously saved `> 11` safely clamps to `11` without migration. `ControlTrackingViewModel.kt:194` (`timeProvider().hour < tuning.morningCheckCutoffHour`) needs zero logic changes.
  * *Tests & Docs Coupling*: `TuningConfigTest.kt:139, 217` currently asserts `23`; `REQ-UI-525.1` and `TST-UI-525.1` still cite `6..23` and `PhysiologyModelsSection.kt`.
  * *Stage 4 `Rule 8.5` Screenshot*: Changing the slider `steps` from `16` (`6..23`) to `4` (`6..11`) alters the Compose slider stop-dots visually; Stage 4 should capture/verify a `MorningCheckSection` Compose PNG (`docs/engineering/walkthroughs/img/ATT-7740_morning_cutoff.png`) via `read_file`.

## 3. Scope Grounding & Non-Goals (`ATT-1250`, `1 SP`)
* **In-Scope Goals**:
  1. Set `TuningPreferencesDefaults.MAX_MORNING_CHECK_CUTOFF_HOUR = 11` (keeping `DEFAULT = 8`, `MIN = 6`) in `TuningPreferencesDataStore.kt`.
  2. Update `TuningConfigTest.kt` (bounds `6..11`, default `8`, clamping simulation `25 -> 11`) and verify `ControlTrackingViewModelTest.kt` + `AdvancedTuningAccordionTest.kt`.
  3. Update `REQ-UI-525.1` (`docs/requirements/req_ui_design_system.md`) and `TST-UI-525.1` (`docs/tests/tst_ui_design_system.md`) to reflect `6..11` and `MorningCheckSection.kt`, and capture `Rule 8.5` PNG (`ATT-7740_morning_cutoff.png`).
* **Out-of-Scope Non-Goals**: Changing `ControlTrackingViewModel` gating, adding new localization strings, or altering DataStore key names. Complexity verified at **1 SP**.

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-525` (`REQ-UI-525.1`) in `docs/requirements/req_ui_design_system.md` (`lines 17120-17173`) & `TST-UI-525.1` (`docs/tests/tst_ui_design_system.md:8466-8468`).
* **Historical Origin & Commit Trace**: Introduced in `ATT-7532` (`647ef674`, default `11`, range `6..15`), altered in `ATT-7740` (`6267d2c0`, default `8`, range `6..23`), relocated to `MorningCheckSection.kt` in `ATT-8117` (`f628f0a6`).
* **Root Reason for Existing Formulation**: `23:00` was introduced in `6267d2c0` to support afternoon testing, which the PO rejected as inappropriate for a morning-only readiness routine (`<= 11:00`).
* **Preservation of Core Invariants**: `DEFAULT = 8` (`08:00`), `MIN = 6` (`06:00`), `hour < morningCheckCutoffHour` gating, paired HRM check, and DataStore clamping remain 100% intact.

## 5. Change-Request Impact Analysis (SUP.10 / `REQ-PRO-064`)
* **Affected Requirements**: Amend `REQ-UI-525.1` (`6..11`, `MorningCheckSection.kt`) in `docs/requirements/req_ui_design_system.md`.
* **Affected Architecture Elements**: `TuningPreferencesDataStore.kt` (`MAX_MORNING_CHECK_CUTOFF_HOUR`), `MorningCheckSection.kt` (dynamic slider range/steps consumer).
* **Verification Impact & Test Surface**: `TST-UI-525.1` (`TuningConfigTest.kt`), `ControlTrackingViewModelTest.kt`, `AdvancedTuningAccordionTest.kt`, plus `Rule 8.5` Compose PNG screenshot (`ATT-7740_morning_cutoff.png`).
* **Risk Assessment & Invariants**: Very low risk (`1 SP`); persisted values `> 11` safely clamp to `11` via `.coerceIn(6, 11)` on read/write.

## 6. System Invariants & Risk Assessment
* **Core Invariants**: `DEFAULT_MORNING_CHECK_CUTOFF_HOUR == 8`; `MIN_MORNING_CHECK_CUTOFF_HOUR == 6`; DataStore key `KEY_MORNING_CHECK_CUTOFF_HOUR` unchanged; zero localization churn.
* **Risk Rating**: **LOW** (`1 SP`, Standard) — Single constant change + test/doc alignment + `Rule 8.5` slider screenshot check.
