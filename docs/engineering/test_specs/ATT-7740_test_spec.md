# Stage 2: Requirement & Test Specification - ATT-7740: Morning HRV Cutoff Hour Default 08:00 & Max Bound 11:00

**Ticket**: [ATT-7740](https://rainerblind.atlassian.net/browse/ATT-7740)  
**Sub-task**: [[Req & Test Spec]](https://rainerblind.atlassian.net/browse/ATT-7740)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.1` | **Branch**: `feature/ATT-7740`  
**Requirement Mapping**: `REQ-UI-525` (`REQ-UI-525.1`) | **Test Spec ID**: `TST-UI-525` (`TST-UI-525.1`, `TST-UI-525.6`)  
**Author**: AI Agent 2 (`agent2`, `subagent_flash`) | **Date**: 2026-11-16

---

## 1. Requirement Specification (`REQ-UI-525.1` Amendment)

### 1.1 Problem Statement & Rationale
In `6267d2c0`, `DEFAULT_MORNING_CHECK_CUTOFF_HOUR` was set to `8` (`08:00`) and `MAX_MORNING_CHECK_CUTOFF_HOUR` to `23` (`23:00`). Following PO revision (*"23:00 ist für eine morgendliche Routine viel zu spät. Die obere Grenze (MAX_MORNING_CHECK_CUTOFF_HOUR) muss bei 11:00 Uhr liegen (Default bleibt 08:00)."*), the upper bound SHALL be capped at `11` (`11:00`) across `TuningPreferencesDataStore.kt` and `MorningCheckSection.kt` (`ATT-8117`).

### 1.2 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
1. **`DEFAULT_MORNING_CHECK_CUTOFF_HOUR == 8` & `MAX_MORNING_CHECK_CUTOFF_HOUR == 11` (`REQ-UI-525.1`)**: `TuningPreferencesDefaults.DEFAULT_MORNING_CHECK_CUTOFF_HOUR` SHALL remain `8` (`08:00 AM`), `MIN_MORNING_CHECK_CUTOFF_HOUR` SHALL remain `6` (`06:00 AM`), and `MAX_MORNING_CHECK_CUTOFF_HOUR` SHALL be `11` (`11:00 AM`).
2. **Legacy Persisted Value Coercion (`REQ-UI-525.1`)**: `TuningPreferencesDataStore` read/write paths SHALL clamp persisted values `> 11` cleanly to `11` via `.coerceIn(6, 11)` without schema migration.
3. **Slider Binding in `MorningCheckSection.kt` & Rule 8.5 PNG**: `MorningCheckSection.kt` SHALL bind `valueRange = 6f..11f` (`steps = 4`) with `%02d:00` formatting, verified via Stage 4 Compose PNG (`docs/engineering/walkthroughs/img/ATT-7740_morning_cutoff.png`).

### 1.4 Acceptance Criteria & Invariants
* *Given* default `TuningConfig`, *When* initialized, *Then* `morningCheckCutoffHour == 8` and `MAX_MORNING_CHECK_CUTOFF_HOUR == 11`.
* *Given* a previously persisted value `> 11` (e.g., `12`, `23`, `25`), *When* read or clamped via `.coerceIn(6, 11)`, *Then* it clamps cleanly to `11`; values `< 6` clamp to `6`.
* *Given* `ControlTrackingViewModel` with default cutoff `08:00`, *When* time is `07:30`, *Then* CTA is visible; *When* `08:00`, *Then* hidden.
* *Invariants*: Zero DB migrations; zero DataStore key changes; zero localization churn; reactive gating (`hour < morningCheckCutoffHour`) intact.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-525` (`REQ-UI-525.1`), targeting `TuningPreferencesDataStore.kt`, `TuningConfig.kt`, `MorningCheckSection.kt`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-7532` (`647ef674d06`), extended to `23:00` in `ATT-7740` (`6267d2c0`), relocated to `MorningCheckSection.kt` in `ATT-8117` (`f628f0a6`), and amended in `ATT-7740` (Sprint `2026-47.1`) per PO revision to cap `MAX_MORNING_CHECK_CUTOFF_HOUR` at `11` (`11:00`) while keeping `DEFAULT_MORNING_CHECK_CUTOFF_HOUR = 8`.
* **Root Reason for Existing Formulation**: `23:00` allowed afternoon testing but contradicted morning-only readiness semantics (PO feedback: *"23:00 ist für eine morgendliche Routine viel zu spät. Die obere Grenze (MAX_MORNING_CHECK_CUTOFF_HOUR) muss bei 11:00 Uhr liegen (Default bleibt 08:00)."*).
* **Preservation of Core Invariants**: `DEFAULT_MORNING_CHECK_CUTOFF_HOUR == 8`, `MIN_MORNING_CHECK_CUTOFF_HOUR == 6`, `.coerceIn(6, 11)` coercion for persisted `> 11` values, reactive gating (`hour < morningCheckCutoffHour`), paired HRM check, and daily single-completion auto-hide remain strictly intact.

---

## 2. Test Specification (`TST-UI-525.1`, `TST-UI-525.6`, `TST-UI-525.7`)

1. **`TST-UI-525.1` (`TuningConfigTest.kt`)**: Assert `DEFAULT_MORNING_CHECK_CUTOFF_HOUR == 8`, `MIN_MORNING_CHECK_CUTOFF_HOUR == 6`, `MAX_MORNING_CHECK_CUTOFF_HOUR == 11`, and `.coerceIn(6, 11)` clamping on `4 -> 6` and `12/23/25 -> 11`.
2. **`TST-UI-525.4` (`ControlTrackingViewModelTest.kt`)**: Verify `isMorningHrvCheckVisible` emits `true` before `08:00` and `false` at/after `08:00` (and `11:00` max boundary).
3. **`TST-UI-525.6` (`MorningCheckCutoffScreenshotTest.kt`, Rule 8.5)**: Capture `docs/engineering/walkthroughs/img/ATT-7740_morning_cutoff.png` (`MorningCheckSection` Light & Dark, `06:00..11:00`, `steps = 4`, default `08:00`) and inspect via `read_file`.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-525.1]` | Unit | `TuningConfigTest.kt`: default `8`, bounds `6..11`, `.coerceIn(6, 11)` on `> 11` | `REQ-UI-525.1` | Planned |
| `[TST-UI-525.4]` | Unit | `ControlTrackingViewModelTest.kt`: `08:00` & `11:00` cutoff gating | `REQ-UI-525.4` | Planned |
| `[TST-UI-525.6]` | Screenshot | `MorningCheckCutoffScreenshotTest.kt` (`Rule 8.5` PNG capture & inspection) | `REQ-UI-525.1` | Planned |
