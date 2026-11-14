# Stage 1 Analysis: ATT-7740 - Morning HRV Check Cutoff Hour: Set Default to 08:00 and Extend Upper Bound

**Ticket**: [ATT-7740](https://rainerblind.atlassian.net/browse/ATT-7740)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.6`  
**Branch**: `feat/ATT-7740` | **Author**: Pro Agent (`agent1` / `agent2`) | **Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
Physical on-device review of `ATT-7532` in Sprint 2026-46.5 revealed two limitations in `morningCheckCutoffHour`:
1. **Late Default Cutoff (11:00 AM)**: The default cutoff of 11:00 AM causes the "Morgendlicher Check" CTA button to linger on the Start screen well into midday, whereas standard waking athletic readiness protocols conclude earlier (08:00 AM).
2. **Restrictive Upper Bound (15:00 / 3 PM)**: Athletes working irregular or night shifts or wishing to test in the late afternoon/evening could not configure a cutoff beyond 15:00.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (Default Value Constant)**: `TuningPreferencesDefaults.DEFAULT_MORNING_CHECK_CUTOFF_HOUR` was initialized to `11` in `ATT-7532`. Changing this constant to `8` updates the default across all consumers (`TuningConfig`, `AdvancedTuningScreen`, `PhysiologyModelsSection`, `ControlTrackingViewModel`).
* **RCA 2 (Slider Upper Bound Constraint)**: `TuningPreferencesDefaults.MAX_MORNING_CHECK_CUTOFF_HOUR` was capped at `15`, with `PhysiologyModelsSection.kt` hardcoding `valueRange = 6.0f..15.0f` and `steps = 8`.
* **Independent Codebase Verification**:
  * Traced `morningCheckCutoffHour` in `TuningPreferencesDataStore.kt` (lines 357–359, 1180, 1585), `PhysiologyModelsSection.kt` (lines 340–352), and `ControlTrackingViewModel.kt` (lines 184–195).
  * Verified `timeProvider().hour` returns `0..23`. Extending `MAX_MORNING_CHECK_CUTOFF_HOUR` to `23` enables continuous selection from 06:00 to 23:00 with `steps = 16` ($23 - 6 - 1$) without overflow or logic breakage.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals (1 SP - Trivial)**:
  1. Change `DEFAULT_MORNING_CHECK_CUTOFF_HOUR` from `11` to `8` (08:00 AM).
  2. Change `MAX_MORNING_CHECK_CUTOFF_HOUR` from `15` to `23` (11:00 PM).
  3. Update `PhysiologyModelsSection.kt` slider range to `6.0f..23.0f` with `steps = 16` and 2-digit zero-padded hour formatting (`%02d:00`).
  4. Update unit tests in `TuningConfigTest.kt` and `ControlTrackingViewModelTest.kt`.
* **Out-of-Scope Non-Goals**:
  * No changes to DataStore keys (`tuning_morning_check_cutoff_hour`).
  * No changes to the 5 gating conditions in `ControlTrackingViewModel.isMorningHrvCheckVisible`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-UI-525.1` (Tuning Preferences Configuration).
* **Historical Origin & Commit Trace**: `ATT-7532` (`647ef674`) introduced the shortcut button with a conservative 11:00 AM cutoff and 15:00 upper bound.
* **Root Reason for Existing Formulation**: 11:00 was chosen as an initial morning cutoff assumption; 15:00 was an arbitrary ceiling.
* **Preservation of Core Invariants**: Time gating logic (`hour < morningCheckCutoffHour`), paired HRM requirement, and single-test daily auto-hide remain strictly intact.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Refines `REQ-UI-525.1`.
* **Affected Architecture Elements**: `TuningPreferencesDefaults.kt`, `PhysiologyModelsSection.kt`, `TuningConfigTest.kt`.
* **Verification Impact & Test Surface**: `TuningConfigTest.kt`, `ControlTrackingViewModelTest.kt`, `verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: Low Risk. Trivial preference default and range constant modification. Zero DB schema impact.

---

## 6. Architectural Strategy & High-Level Solution
* Update constants in `TuningPreferencesDefaults`:
  - `DEFAULT_MORNING_CHECK_CUTOFF_HOUR = 8`
  - `MAX_MORNING_CHECK_CUTOFF_HOUR = 23`
* In `PhysiologyModelsSection.kt`, bind `valueRange` and `steps` dynamically to `MIN_MORNING_CHECK_CUTOFF_HOUR..MAX_MORNING_CHECK_CUTOFF_HOUR` and format hours with `String.format(Locale.US, "%02d:00", hour)`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: Zero regression in morning check scoring, baseline calculation, or workout tracking lifecycle.
* **Risk Rating**: **LOW** (strictly constant and UI slider range adjustment).
